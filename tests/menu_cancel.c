/* Check the live retail cancel call ABI at the boundary between Android and Xbox. */
#include <assert.h>
#include <stdio.h>
#include "../native/menu_cancel.c"
static unsigned char memory[0x600000];
ptrdiff_t g_xbox_mem_offset;
RECOMP_TLS uint32_t g_eax,g_ecx,g_edx,g_ebx,g_esi,g_edi,g_ebp,g_esp,g_seh_ebp;
RECOMP_TLS int g_df,g_fp_top,g_fp_cmp;
RECOMP_TLS uint16_t g_fp_control_word,g_fp_cc;
RECOMP_TLS double g_fp_stack[8];
RECOMP_TLS RecompXmm g_xmm0,g_xmm1,g_xmm2,g_xmm3,g_xmm4,g_xmm5,g_xmm6,g_xmm7;
RECOMP_TLS RecompMmx g_mm0,g_mm1,g_mm2,g_mm3,g_mm4,g_mm5,g_mm6,g_mm7;
static int calls;
static void play(void){
    assert(g_ecx==0x28000 && MEM32(g_esp)==0x001DA91E && MEM32(g_esp+4)==0x2b8d);
    assert(MEM32(g_esp+8)==UINT32_MAX && MEM32(g_esp+12)==UINT32_MAX);
    g_esp+=16;g_ecx=42;g_eax=100;g_seh_ebp=4;g_fp_stack[1]=7;calls++;
}
recomp_func_t recomp_lookup(uint32_t address){assert(address==0x00170930);return play;}
int main(void){
    g_xbox_mem_offset=(ptrdiff_t)(uintptr_t)memory;g_esp=0x50000;g_eax=2;g_ecx=3;
    MEM32(0x5b25f0)=0x28000;MEM16(0x54eb26)=0x2b8d;g_fp_stack[1]=9;
    fight_request_menu_cancel();fight_service_menu_cancel();assert(!calls);
    fight_bind_menu_thread();fight_service_menu_cancel();assert(!calls);
    fight_request_menu_cancel();fight_service_menu_cancel();fight_service_menu_cancel();
    assert(calls==1 && g_esp==0x50000 && g_eax==2 && g_ecx==3 && !g_seh_ebp && g_fp_stack[1]==9);
    puts("Retail cancel event, title-thread gate, one-shot delivery and guest registers passed");
    return 0;
}
