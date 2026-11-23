/* Run the actual OHCI delivery loop against a guest that pauses its acknowledgements. */
#include <windows.h>
#include <setjmp.h>
#include <assert.h>
static void test_sleep(DWORD ms);
#define Sleep test_sleep
#include "ohci.c"
#undef Sleep
static unsigned char ram[16*1024*1024];
size_t g_xbox_total_ram=sizeof ram, g_xbox_map_size=sizeof ram;
OHCI_TLS uint32_t g_eax,g_ecx,g_edx,g_esp,g_ebx,g_esi,g_edi,g_fs_base;
uint32_t g_xbox_image_lo=0x10000, g_xbox_image_hi=0x100000;
uint32_t xbox_ContiguousAllocatedBytes(void){return 0;}
size_t xbox_GetMappedSize(void){return sizeof ram;}
void *xbox_GetMemoryBase(void){return ram;}
/* The production pad object also contains startup code for the window and APU.
 * This test exercises USB only. Detect any accidental call to those endpoints. */
void *g_apu_state;
void *mcpx_apu_init_standalone(void *base){(void)base;abort();}
_Bool apu_hook_handle_mmio(PCONTEXT c,uintptr_t a,uint32_t va,int op){
    (void)c;(void)a;(void)va;(void)op;abort();
}
static unsigned ticks, recovered, suspended;
static jmp_buf done;
ptrdiff_t xbox_GetMemoryOffset(void){return (ptrdiff_t)(uintptr_t)ram;}
void *xbox_GuestToHost(uint32_t a){return a<sizeof ram?ram+a:NULL;}
int xbox_worker_stack_alloc(void){return 0;}
void xbox_worker_stack_free(int n){(void)n;}
uint32_t xbox_AllocThreadTib(void){return 0x10000;}
uint32_t xbox_GetConnectedInterrupt(uint32_t vector){return vector==OHCI_VECTOR?0x20000:0;}
int xbox_IrqlBlocksInterrupts(void){return 0;}
int xbox_IrqlRaisedCount(void){return 0;}
int xbox_IrqlEnterInterrupt(int n){return n;}
void xbox_IrqlLeaveInterrupt(int n){(void)n;}
enum { HCCA=0x40000, ED=0x41000, TD=0x42000, TAIL=0x42040, PACKET=0x46000 };
static unsigned soak, reports, overflows, masked_ticks;
static unsigned sample;
extern int pad_message(const char *);
extern void sub_00473F67(void);
OHCI_TLS int g_df;
ptrdiff_t g_xbox_mem_offset;

/* Run the title's own poll with the USB handle stuck on its last report. */
enum { POLL_HANDLE=0x61000, POLL_DEVICE=0x61200, POLL_TYPE_DESC=0x61300, POLL_LENGTH=0x61400,
       POLL_OUT=0x62000, POLL_STACK=0x70000, P1_SLOT=0x5cee78 };

static unsigned poll_handle(void){
    memset(ram+POLL_OUT-1,0xcc,24);
    wr32(POLL_STACK+4,POLL_HANDLE);wr32(POLL_STACK+8,POLL_OUT);
    g_esp=POLL_STACK;g_ebx=37;g_esi=42;g_edi=51;
    sub_00473F67();
    assert(g_esp==POLL_STACK+12 && g_ebx==37 && g_esi==42 && g_edi==51);
    assert(ram[POLL_OUT-1]==0xcc && ram[POLL_OUT+22]==0xcc);
    return g_eax;
}
static void stalled_poll(void){
    g_xbox_mem_offset=xbox_GetMemoryOffset();
    wr32(P1_SLOT,POLL_HANDLE);wr32(POLL_HANDLE,POLL_DEVICE);wr32(POLL_HANDLE+8,17);
    wr32(POLL_HANDLE+0xa3,POLL_TYPE_DESC);wr32(POLL_TYPE_DESC+8,POLL_LENGTH);ram[POLL_LENGTH]=18;
    // The USB cache holds a trigger down and the left stick at full right.
    ram[POLL_HANDLE+0x14+9]=255;ram[POLL_HANDLE+0x14+10]=255;ram[POLL_HANDLE+0x14+11]=127;
    assert(!poll_handle() && rd32(POLL_OUT)==17 && ram[POLL_OUT+4+9]==255);
    assert(pad_message("PAD 000000000000000000ffff7f000000000000"));
    assert(!poll_handle() && ram[POLL_OUT+4+9]==255 && ram[POLL_OUT+4+11]==127);
    for(int i=0;i<361;i++)assert(pad_message("PAD 000000000000000000ffff7f000000000000"));
    for(int i=0;i<600;i++)assert(!poll_handle() && ram[POLL_OUT+4+9]==255);
    assert(pad_message("PAD 000000000000000000000000000000000000"));
    assert(!poll_handle() && rd32(POLL_OUT)==363);
    for(int i=0;i<18;i++)assert(!ram[POLL_OUT+4+i]);
    assert(pad_message("PAD 000000000000000000000080000000000000"));
    assert(!poll_handle() && !ram[POLL_OUT+4+9] && ram[POLL_OUT+4+11]==128);
    // Other ports, failed reads and a disconnected host pad still use retail state.
    wr32(P1_SLOT,POLL_HANDLE+0x200);assert(!poll_handle() && rd32(POLL_OUT)==17);
    wr32(P1_SLOT,POLL_HANDLE);ram[POLL_DEVICE+4]=2;
    assert(poll_handle()==0x48f && rd32(POLL_OUT)==17);
    ram[POLL_DEVICE+4]=0;ram[POLL_TYPE_DESC+0x28]=0x10;
    assert(poll_handle()==0x57 && rd32(POLL_OUT)==0xcccccccc);
    ram[POLL_TYPE_DESC+0x28]=0;assert(pad_message("PAD off"));
    assert(!poll_handle() && rd32(POLL_OUT)==17 && ram[POLL_OUT+4+9]==255);
    puts("Retail poll recovered block, release and direction reversal with the USB report frozen");
}
static void fill_td(unsigned addr,unsigned end){
    wr32(TD,TD_DP_IN<<19);wr32(TD+4,addr);wr32(TD+8,TAIL);wr32(TD+12,end);
    wr32(ED,(1u<<7));wr32(ED+4,TAIL);wr32(ED+8,TD);wr32(ED+12,0);
}
static void queue_sample(void){
    char line[41]="PAD ";
    for(unsigned i=0;i<18;i++)snprintf(line+4+2*i,3,"%02x",(sample+i)&255);
    assert(pad_message(line));fill_td(PACKET,PACKET+19);
}
static void transfer_checks(void){
    OhciController *hc=&s_hc[0];uint32_t list=0;
    s_plugged_pads=1;sample=33;queue_sample();
    uint32_t first=0x48ff8,second=0x44000;
    fill_td(first,second+11);memset(ram+first,0xab,24);memset(ram+second,0xab,12);
    assert(ohci_walk_eds(hc,ED,&list)==1 && list==TD);
    for(unsigned i=0;i<20;i++)assert(ram[i<8?first+i:second+i-8]==(i==0?0:i==1?20:sample+i-2));
    assert(ram[first+8]==0xab && rd32(TD+4)==0);
    hc->reg[HcHCCA/4]=HCCA;
    ohci_publish_done(hc,TD);
    uint32_t sampled=hc->reg[HcInterruptStatus/4];
    ohci_write(hc,HcInterruptStatus,INTR_WDH,4);
    ohci_raise(hc,sampled); /* guest ack between delivery snapshot and assertion */
    assert(!(hc->reg[HcInterruptStatus/4]&INTR_WDH) && !rd32(HCCA+HCCA_DONE_HEAD));
}

static void service(void){
    if(soak){
        OhciController *hc=&s_hc[0];
        uint32_t cause=hc->reg[HcInterruptStatus/4];
        if(cause & INTR_FNO)overflows++;
        if(cause & INTR_WDH){
            assert(rd32(HCCA+HCCA_DONE_HEAD)==TD && (rd32(ED+8)&ED_PTR_MASK)==TAIL);
            assert(ram[PACKET]==0 && ram[PACKET+1]==20);
            for(unsigned i=0;i<18;i++)assert(ram[PACKET+2+i]==((sample+i)&255));
            reports++;
        }
        ohci_write(hc,HcInterruptStatus,cause,4);
        if(cause & INTR_WDH){sample++;queue_sample();}
    }else if(ticks>suspended+520){
        ohci_write(&s_hc[0],HcInterruptStatus,INTR_SF,4);recovered++;
    }
    g_eax=1;
}
recomp_func_t recomp_lookup(uint32_t a){return a==0x30000?service:NULL;}
static void test_sleep(DWORD ms){
    if(ms!=OHCI_TICK_MS)return;
    if(soak){
        unsigned elapsed=ticks++*OHCI_TICK_MS;
        OhciController *hc=&s_hc[0];
        if(ticks==1){
            wr32(HCCA,ED);hc->reg[HcHCCA/4]=HCCA;hc->reg[HcControl/4]=0x84;
            hc->reg[HcInterruptEnable/4]=INTR_MIE|INTR_WDH|INTR_FNO;
            s_plugged_pads=1;queue_sample();
        }else{
            assert(ohci_read(hc,HcFmNumber,4)==(elapsed&65535));
            assert(rd32(HCCA+0x80)==(elapsed&65535));
        }
        unsigned phase=ticks%4096;
        if(phase==1700){ohci_write(hc,HcInterruptDisable,INTR_MIE,4);masked_ticks++;}
        if(phase==1712)ohci_write(hc,HcInterruptEnable,INTR_MIE,4);
        if(elapsed>=1800000)longjmp(done,1);
        return;
    }
    if(++ticks==suspended+1){
        s_hc[0].reg[HcControl/4]=0x80;
        s_hc[0].reg[HcInterruptEnable/4]=INTR_SF|INTR_MIE;
    }
    if(ticks>suspended)s_hc[0].reg[HcInterruptStatus/4]|=INTR_SF;
    if(ticks==suspended+820)longjmp(done,1);
}
extern int pad_message(const char *);
int main(int argc,char **argv){
    soak=argc>1 && !strcmp(argv[1],"soak");
    suspended=argc>1?0:1700;
    ohci_reset(&s_hc[0],0,0);wr32(0x20000,0x30000);
    if(soak){stalled_poll();transfer_checks();ohci_reset(&s_hc[0],0,0);}
    if(!setjmp(done)){ohci_thread(NULL);return 2;}
    if(soak){
        assert(overflows==1800000u/32768 && masked_ticks>100 && reports>440000);
        printf("30 simulated minutes: %u verified XID deliveries, %u clock edges, %u IRQ mask/resume cycles\n",reports,overflows,masked_ticks);
        return 0;
    }
    assert(recovered>=275);
    for(int i=0;i<4096;i++){
        char line[41]="PAD ";uint8_t want[18],out[20],get[20];
        for(int k=0;k<18;k++){
            want[k]=(uint8_t)((i*13+k*7)&255);
            snprintf(line+4+2*k,3,"%02x",want[k]);
        }
        assert(pad_message(line));
        assert(!pad_message("PAD qqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqqq"));
        assert(usb_gamepad_report(0,out,20)==20 && !memcmp(out+2,want,sizeof want));
        UsbSetup req={0xa1,1,0x0100,0,20};
        assert(usb_gamepad_control(0,&req,get,20)==20 && !memcmp(out,get,sizeof get));
    }
    printf("%u recovered interrupts, 4096 XID packets through both endpoints\n",recovered);
    return 0;
}
