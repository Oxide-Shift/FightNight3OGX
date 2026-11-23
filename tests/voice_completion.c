/* Exercise the translated DirectSound idle notification with an empty slot zero,
 * the voice owner and link state from the Thor crash, and live control cases. */
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include "recomp_types.h"

static uint8_t memory[0x500000];
ptrdiff_t g_xbox_mem_offset;
RECOMP_TLS uint32_t g_eax,g_ebx,g_ecx,g_edx,g_esi,g_edi,g_esp;
static int unlinked;
void sub_0046A8D7(void);
void sub_0046CB0E(void){
    assert(g_ecx==0x30000);assert(MEM32(g_esp+4)==1);
    unlinked++;g_esp+=8;
}
static void notify(unsigned slot){
    g_ecx=0x10000;g_esp=0x7000;MEM32(g_esp+4)=slot;sub_0046A8D7();
    assert(g_esp==0x7008);
}
int main(void){
    g_xbox_mem_offset=(ptrdiff_t)(uintptr_t)memory;
    MEM32(0x471bd0)=0x20000;
    for(unsigned i=0;i<1000;i++){
        MEM32(0x10088)=0;notify(0);assert(unlinked==(int)i);
        MEM32(0x10088)=0x30000;MEM32(0x3004c)=0x40000;
        MEM8(0x30064)=1;MEM16(0x3000c)=0;
        notify(0);assert(unlinked==(int)i+1);
        MEM32(0x3004c)=0x3004c;notify(0);assert(unlinked==(int)i+1);
        MEM32(0x10088)=0;notify(0);notify(256);assert(unlinked==(int)i+1);
    }
    puts("1,000 released/reused audio voice cycles passed through the retail notification handler");
    return 0;
}
