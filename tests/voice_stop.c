/* Run the translated poll against the MCPX state the retail idle ISR also checks. */
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include "recomp_types.h"

enum { VOICE=0x30000, OWNER=0x40000, TABLE=0x60000, WAITER=0x0046d78f };
static uint8_t memory[0x500000];
size_t g_xbox_total_ram=sizeof memory,g_xbox_map_size=sizeof memory;
ptrdiff_t g_xbox_mem_offset;
ptrdiff_t xbox_GetMemoryOffset(void){return g_xbox_mem_offset;}
RECOMP_TLS uint32_t g_eax,g_ebx,g_ecx,g_edx,g_esi,g_edi,g_esp,g_ebp,g_seh_ebp;
static unsigned cleanups;
static uint64_t now;
void sub_0046CDD3(void);
void fight_trax_init(const char *root){(void)root;}

void stop_clock(uint32_t site){
    assert(site==0x46cddf);
    uint32_t out=MEM32(g_esp+4);
    MEM32(out)=(uint32_t)now;MEM32(out+4)=(uint32_t)(now>>32);
    g_esp+=8;
}
void sub_0046CCEA(void){
    assert(g_ecx==VOICE && MEM32(g_esp)==0x46ce19);
    ++cleanups;MEM16(VOICE+0x12)&=0x7fff;g_esp+=4;
}
static void poll_stop(uint32_t caller,int done){
    unsigned before=cleanups;
    g_ecx=VOICE;g_esp=0x7100;g_ebp=0x7200;
    MEM32(g_esp)=caller;MEM16(VOICE+0x12)=0x8001;
    sub_0046CDD3();
    assert(g_esp==0x7104 && cleanups-before==(unsigned)done);
}
int main(void){
    g_xbox_mem_offset=(ptrdiff_t)(uintptr_t)memory;
    MEM32(0x471bd0)=TABLE;MEM32(VOICE+8)=OWNER;MEM32(VOICE+0x4c)=0x35000;MEM32(VOICE+0x50)=0x35008;
    MEM8(VOICE+0x64)=2;MEM16(VOICE+0xc)=3;MEM16(VOICE+0xe)=8;
    MEM32(OWNER+0x88+3*4)=VOICE;MEM32(OWNER+0x88+8*4)=VOICE;
    for(unsigned i=0;i<1000;i++){
        MEM32(TABLE+3*128+0x54)=1u<<21;poll_stop(WAITER,0);
        MEM32(TABLE+3*128+0x54)=0;poll_stop(WAITER,1);
    }
    MEM32(TABLE+8*128+0x54)=1u<<21;poll_stop(WAITER,0);MEM32(TABLE+8*128+0x54)=0;
    MEM32(TABLE+8*128+4)=1u<<23;poll_stop(WAITER,0);MEM32(TABLE+8*128+4)=0;
    MEM32(VOICE+0x50)=0;poll_stop(WAITER,0);MEM32(VOICE+0x50)=0x35008;
    poll_stop(0x46bdee,0); /* A background status poll keeps its normal timeout. */
    MEM32(OWNER+0x84)=1;poll_stop(WAITER,0);MEM32(OWNER+0x84)=0;
    MEM32(OWNER+0x88+8*4)=0x35000;poll_stop(WAITER,0);MEM32(OWNER+0x88+8*4)=VOICE;
    MEM32(VOICE+0x4c)=VOICE+0x4c;poll_stop(WAITER,0);MEM32(VOICE+0x4c)=0x35000;
    MEM8(VOICE+0x64)=4;poll_stop(WAITER,0);MEM8(VOICE+0x64)=0;poll_stop(WAITER,0);
    MEM8(VOICE+0x64)=1;MEM16(VOICE+0xc)=256;poll_stop(WAITER,0);MEM16(VOICE+0xc)=3;
    MEM32(TABLE+3*128+0x54)=1u<<21;now=4999999;poll_stop(WAITER,0);
    now=5000000;poll_stop(WAITER,1); /* The title still owns recovery at its deadline. */
    puts("1,000 synchronous idle stops, live channels, slot reuse and retail watchdog passed");
    return 0;
}
