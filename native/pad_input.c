#include "input/xinput_xbox.h"
#include "usb/ohci.h"
#include "kernel/guest_ptr.h"
#include "kernel/xbox_memory_layout.h"
#include <stdbool.h>
#include <stdio.h>
#include <stdatomic.h>
#include <string.h>
#include <stdlib.h>
#ifdef _WIN32
#include <windows.h>
#include <xinput.h>
#else
#include <signal.h>
#include <sys/mman.h>
#include <unistd.h>
#include "mmio_posix.h"
#endif
typedef struct MCPXAPUState MCPXAPUState;
extern MCPXAPUState *g_apu_state;
extern MCPXAPUState *mcpx_apu_init_standalone(uint8_t *);
extern uint64_t mcpx_apu_mmio_read(MCPXAPUState *,uint64_t,unsigned);
extern void mcpx_apu_mmio_write(MCPXAPUState *,uint64_t,uint64_t,unsigned);
enum { APU_BASE=0xfe800000u, APU_SIZE=0x30000, AC97_BASE=0xfec00000u };

/* Android sends one complete XID state, so the two sticks, analog buttons
   and digital buttons are published as a single sample. */
static XBOX_INPUT_STATE latest;
static atomic_flag lock=ATOMIC_FLAG_INIT;
static atomic_int connected;
#include "menu_dpad.h"
static MenuDpad root_dpad;
enum { ROOT_PLAYLIST_BUTTON=0x20 };
static int playlist_down;
static void set_playlist_button(int down){
    if(playlist_down==down)return;
    playlist_down=down;
    latest.Gamepad.wButtons=(latest.Gamepad.wButtons & ~ROOT_PLAYLIST_BUTTON) | (down?ROOT_PLAYLIST_BUTTON:0);
    latest.dwPacketNumber++;
}
void fight_pad_playlist(int down){
    while(atomic_flag_test_and_set(&lock)){}
    set_playlist_button(down && root_dpad.enabled);
    atomic_flag_clear(&lock);
}

void fight_pad_menu(int enabled){
    while(atomic_flag_test_and_set(&lock)){}
    set_playlist_button(0);
    menu_dpad_reset(&root_dpad,enabled,latest.Gamepad.wButtons);
    atomic_flag_clear(&lock);
}

void fight_pad_suspend_menu(void){
    while(atomic_flag_test_and_set(&lock)){}
    menu_dpad_reset(&root_dpad,root_dpad.enabled,latest.Gamepad.wButtons);
    atomic_flag_clear(&lock);
}
static atomic_uint received_packets, sampled_packet, player_one_packet;

/* Both numbers are cumulative for one fight process. No buttons or stick
   values are recorded. A rising receive count with a stranded sample points
   to the emulated pad, rather than Android dispatch or the input pipe. */
void fight_pad_report(void){
    unsigned sampled=atomic_load_explicit(&sampled_packet,memory_order_relaxed);
    unsigned received=atomic_load_explicit(&received_packets,memory_order_relaxed);
    fprintf(stderr,"[Fight Night] pad received %u sampled %u player1 %u\n",received,sampled,
            atomic_load_explicit(&player_one_packet,memory_order_relaxed));
}

static int nibble(char c){
    if(c>='0'&&c<='9')return c-'0';
    if(c>='a'&&c<='f')return c-'a'+10;
    return -1;
}
int pad_message(const char *line){
    if(strncmp(line,"PAD ",4))return 0;
    const char *p=line+4;
    if(strcspn(p,"\r\n")==3&&!strncmp(p,"off",3)){
        while(atomic_flag_test_and_set(&lock)){}
        set_playlist_button(0);
        atomic_store(&connected,0);
        menu_dpad_reset(&root_dpad,root_dpad.enabled,0);
        atomic_flag_clear(&lock);return 1;
    }
    if(strcspn(p,"\r\n")!=36)return 0;
    unsigned char bytes[18];
    for(unsigned i=0;i<sizeof(bytes);i++){
        int a=nibble(p[2*i]),b=nibble(p[2*i+1]);if(a<0||b<0)return 0;
        bytes[i]=(unsigned char)((a<<4)|b);
    }
    while(atomic_flag_test_and_set(&lock)){}
    _Static_assert(sizeof(XBOX_GAMEPAD)==18,"XID wire layout");
    if(root_dpad.enabled){
        bytes[0]=(bytes[0]&~ROOT_PLAYLIST_BUTTON)|(playlist_down?ROOT_PLAYLIST_BUTTON:0);
        const uint8_t *last=(const uint8_t *)&latest.Gamepad;
        // Never replay navigation across an accept, back, start or stick input.
        if(((last[0]^bytes[0])&0xf0) || memcmp(last+1,bytes+1,17))
            menu_dpad_reset(&root_dpad,1,bytes[0]);
        else menu_dpad_offer(&root_dpad,bytes[0],latest.dwPacketNumber+1);
    }
    memcpy(&latest.Gamepad,bytes,18);latest.dwPacketNumber++;
    atomic_store_explicit(&received_packets,latest.dwPacketNumber,memory_order_relaxed);
    atomic_flag_clear(&lock);atomic_store(&connected,1);return 1;
}
/* The title normally reads the last completed XID transfer. A delayed USB
   completion can leave that report latched even though PAD messages keep
   arriving. Use the live sample on each successful read of the opened P1
   handle. Leave enumeration, other slots, rumble and physical PC pads alone. */
/* QA trace (`adb shell setprop debug.fnr3.pad_trace 1`): every read of the controller the title makes through
 * XInputGetState, whether this hook refreshed it, and the trigger/stick bytes the title ends up with when they
 * change. Off by default; checked every 256 reads. */
#ifdef __ANDROID__
#include <sys/system_properties.h>
#endif
static int pad_trace;
static unsigned trace_reads,trace_skipped_status,trace_skipped_handle;
static void trace_read(uint32_t status,uint32_t handle,uint32_t player_one,uint32_t output,int refreshed){
#ifdef __ANDROID__
    if((trace_reads++&255)==0){
        char v[PROP_VALUE_MAX]={0};
        pad_trace=__system_property_get("debug.fnr3.pad_trace",v)>0 && v[0]=='1';
    }
    if(!pad_trace)return;
    if(!refreshed){if(status)trace_skipped_status++;else trace_skipped_handle++;}
    if(!output)return;
    static uint8_t last[22];static int have;
    const uint8_t *s=XBOX_HOST(output);
    if(!have || memcmp(last+12,s+12,10) || last[4]!=s[4]){
        have=1;memcpy(last,s,22);
        int16_t lx,ly,rx,ry;memcpy(&lx,s+14,2);memcpy(&ly,s+16,2);memcpy(&rx,s+18,2);memcpy(&ry,s+20,2);
        fprintf(stderr,"[Fight Night] pad read %s status=%u handle=%s btn=%02x LT=%u RT=%u L=(%d,%d) R=(%d,%d) skipped=%u/%u\n",
                refreshed?"live":"STALE",status,handle==player_one?"p1":"other",s[4],s[12],s[13],lx,ly,rx,ry,
                trace_skipped_status,trace_skipped_handle);
    }
#else
    (void)status;(void)handle;(void)player_one;(void)output;(void)refreshed;
#endif
}
void fight_pad_read_player_one(uint32_t status,uint32_t handle,uint32_t player_one,uint32_t output){
    if(status || !handle || handle!=player_one || !output || !atomic_load(&connected)){
        trace_read(status,handle,player_one,output,0);
        return;
    }
#ifdef __ANDROID__
    extern void fight_service_menu_cancel(void);
    fight_service_menu_cancel();
#endif
    uint8_t *state=XBOX_HOST(output);
    while(atomic_flag_test_and_set(&lock)){}
    if(atomic_load(&connected)){
        // Retail copies four packet bytes plus 18 gamepad bytes, with no tail padding.
        uint32_t packet=latest.dwPacketNumber;
        memcpy(state+4,&latest.Gamepad,sizeof(latest.Gamepad));
        if(root_dpad.count){
            unsigned direction=menu_dpad_read(&root_dpad,state[4],&packet);
            state[4]=(state[4]&0xf0)|direction;
        }
        memcpy(state,&packet,4);
        atomic_store_explicit(&player_one_packet,latest.dwPacketNumber,memory_order_relaxed);
    }
    atomic_flag_clear(&lock);
    trace_read(status,handle,player_one,output,1);
}
DWORD xbox_InputGetState(DWORD port,XBOX_INPUT_STATE *state){
    if(!state||port!=0)return 1167;
    if(atomic_load(&connected)){
        while(atomic_flag_test_and_set(&lock)){}
        *state=latest;
        atomic_store_explicit(&sampled_packet,latest.dwPacketNumber,memory_order_relaxed);
        atomic_flag_clear(&lock);return 0;
    }
#ifdef _WIN32
    XINPUT_STATE x;
    if(XInputGetState(port,&x)==ERROR_SUCCESS){
        memset(state,0,sizeof(*state));state->dwPacketNumber=x.dwPacketNumber;
        XBOX_GAMEPAD *p=&state->Gamepad;XINPUT_GAMEPAD *g=&x.Gamepad;
        p->wButtons=g->wButtons&255;
        const WORD mask[]={XINPUT_GAMEPAD_A,XINPUT_GAMEPAD_B,XINPUT_GAMEPAD_X,XINPUT_GAMEPAD_Y,XINPUT_GAMEPAD_RIGHT_SHOULDER,XINPUT_GAMEPAD_LEFT_SHOULDER};
        for(int i=0;i<6;i++)p->bAnalogButtons[i]=(g->wButtons&mask[i])?255:0;
        p->bAnalogButtons[6]=g->bLeftTrigger;p->bAnalogButtons[7]=g->bRightTrigger;
        p->sThumbLX=g->sThumbLX;p->sThumbLY=g->sThumbLY;p->sThumbRX=g->sThumbRX;p->sThumbRY=g->sThumbRY;return 0;
    }
#endif
    return 1167;
}
DWORD xbox_InputSetState(DWORD port,const XBOX_VIBRATION *v){
#ifdef _WIN32
    if(v&&!atomic_load(&connected)){XINPUT_VIBRATION vibration={v->wLeftMotorSpeed,v->wRightMotorSpeed};return XInputSetState(port,&vibration);}
#endif
    if(port!=0 || !atomic_load(&connected))return 1167;
    if(v){
        static atomic_uint last;
        unsigned left=v->wLeftMotorSpeed,right=v->wRightMotorSpeed;
        if(atomic_exchange(&last,left|(right<<16))!=(left|(right<<16))){
            printf("FNR3/1 RUMBLE %u %u\n",left,right);fflush(stdout);
        }
    }
    return 0;
}
int xbox_FramebufferKeyDown(int vk){(void)vk;return 0;}

static int controller_address(uintptr_t host,uint32_t *va){
    uintptr_t base=(uintptr_t)xbox_GetMemoryOffset();
    if(host<base||host-base>UINT32_MAX)return 0;
    *va=(uint32_t)(host-base);return 1;
}
#ifdef _WIN32
extern bool apu_hook_handle_mmio(PCONTEXT,uintptr_t,uint32_t,int);
static LONG CALLBACK controller_access(PEXCEPTION_POINTERS e){
    if(e->ExceptionRecord->ExceptionCode!=EXCEPTION_ACCESS_VIOLATION||e->ExceptionRecord->NumberParameters<2)return EXCEPTION_CONTINUE_SEARCH;
    uint32_t va;
    uintptr_t address=e->ExceptionRecord->ExceptionInformation[1];
    if(controller_address(address,&va)){
        if(xbox_OhciOwnsAddress(va)&&xbox_OhciHandleMmio(e->ContextRecord,va))return EXCEPTION_CONTINUE_EXECUTION;
        if(va-APU_BASE<APU_SIZE&&g_apu_state&&apu_hook_handle_mmio(e->ContextRecord,address,va,e->ExceptionRecord->ExceptionInformation[0]==1))
            return EXCEPTION_CONTINUE_EXECUTION;
    }
    return EXCEPTION_CONTINUE_SEARCH;
}
int pad_start(void){
    if(!AddVectoredExceptionHandler(1,controller_access))return 0;
    g_apu_state=mcpx_apu_init_standalone((uint8_t*)xbox_GetMemoryBase());
    if(!g_apu_state)return 0;
    _putenv_s("RECOMP_USB","1");xbox_OhciInit();return 1;
}
#else
static struct sigaction prior;
static uint8_t codec[65536];
static size_t codec_page;
static uint64_t pad_read(uintptr_t a,int size){
    uint32_t va=(uint32_t)(a-xbox_GetMemoryOffset());uint64_t v=0;
    if(va-APU_BASE<APU_SIZE)return mcpx_apu_mmio_read(g_apu_state,va-APU_BASE,size);
    uint32_t off=va-AC97_BASE;
    if(off<codec_page){if(off+(unsigned)size<=codec_page)memcpy(&v,codec+off,size);return v;}
    xbox_OhciMmioAccess(va,0,&v,size);return v;
}
static void pad_write(uintptr_t a,uint64_t value,int size){
    uint32_t va=(uint32_t)(a-xbox_GetMemoryOffset());
    if(va-APU_BASE<APU_SIZE){mcpx_apu_mmio_write(g_apu_state,va-APU_BASE,value,size);return;}
    uint32_t off=va-AC97_BASE;
    if(off<codec_page){
        if(off+(unsigned)size>codec_page)return;
        memcpy(codec+off,&value,size);
        for(unsigned cr=0x10b;cr<0x180;cr+=16)if(off<=cr&&cr<off+(unsigned)size)codec[cr]&=~2u;
        return;
    }
    xbox_OhciMmioAccess(va,1,&value,size);
}
static void controller_access(int sig,siginfo_t *info,void *ctx){
    uint32_t va,insn;
    if(controller_address((uintptr_t)info->si_addr,&va)&&
       (xbox_OhciOwnsAddress(va)||va-APU_BASE<APU_SIZE||va-AC97_BASE<codec_page)&&
       mmio_posix_step(ctx,(uintptr_t)info->si_addr,pad_read,pad_write,&insn))return;
    if(prior.sa_handler==SIG_DFL||prior.sa_handler==SIG_IGN){
        struct sigaction action={0};action.sa_handler=SIG_DFL;sigaction(sig,&action,NULL);raise(sig);return;
    }
    if(prior.sa_flags&SA_SIGINFO)prior.sa_sigaction(sig,info,ctx);else prior.sa_handler(sig);
}
int pad_start(void){
    struct sigaction a={0};a.sa_sigaction=controller_access;a.sa_flags=SA_SIGINFO;sigemptyset(&a.sa_mask);
    if(sigaction(SIGSEGV,&a,&prior)!=0)return 0;
    g_apu_state=mcpx_apu_init_standalone((uint8_t*)xbox_GetMemoryBase());
    if(!g_apu_state)return 0;
    long page=sysconf(_SC_PAGESIZE);
    if(page<=0||(size_t)page>sizeof(codec))return 0;
    codec_page=(size_t)page;codec[0x131]=1;
    if(mprotect(XBOX_HOST(AC97_BASE),codec_page,PROT_NONE)!=0||mprotect(XBOX_HOST(APU_BASE),APU_SIZE,PROT_NONE)!=0)return 0;
    setenv("RECOMP_USB","1",1);xbox_OhciInit();return 1;
}
#endif
