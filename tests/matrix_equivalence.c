/* Run one translated function twice, comparing all RAM, registers, and state
 * visible to the retail consumer and scheduler. Input offsets exercise MOVUPS. */
#include "recomp_types.h"
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

ptrdiff_t g_xbox_mem_offset;
size_t g_xbox_total_ram = 8 * 1024 * 1024;
RECOMP_TLS uint32_t g_eax,g_ecx,g_edx,g_esp,g_ebx,g_esi,g_edi,g_ebp,g_seh_ebp;
RECOMP_TLS RecompXmm g_xmm0,g_xmm1,g_xmm2,g_xmm3,g_xmm4,g_xmm5;
RECOMP_TLS int g_loop_budget;
void matrix_baseline(void), matrix_candidate(void);
struct State {
    uint32_t reg[10];
    RecompXmm lanes[6];
    unsigned char output[12 * 64], stack[112];
};
static struct State observations[2][32];
static unsigned variant, observed;
static void observe(void) {
    if (observed >= 32) abort();
    struct State *s = &observations[variant][observed++];
    uint32_t registers[] = {g_eax,g_ecx,g_edx,g_esp,g_ebx,g_esi,g_edi,g_ebp,g_seh_ebp,(uint32_t)g_loop_budget};
    RecompXmm lanes[] = {g_xmm0,g_xmm1,g_xmm2,g_xmm3,g_xmm4,g_xmm5};
    memcpy(s->reg,registers,sizeof registers);
    memcpy(s->lanes,lanes,sizeof lanes);
    memcpy(s->output,(void *)XBOX_PTR(0x5cd4b0),sizeof s->output);
    memcpy(s->stack,(void *)XBOX_PTR(0x700000-64),sizeof s->stack);
}
void recomp_loop_yield(void) { observe(); g_loop_budget = 0; }
void sub_002AA6F0(void) { observe(); g_esp += 4; }
static uint32_t random_word(uint32_t *x) { return *x = *x * 1664525u + 1013904223u; }
int main(void) {
    unsigned char *reference = malloc(g_xbox_total_ram), *candidate = malloc(g_xbox_total_ram);
    if (!reference || !candidate) return 2;
    for (unsigned trial=0; trial<192; ++trial) {
        uint32_t src=0x80000+(trial%4)*4, palette=0x180000+(trial%3)*4, seed=trial+1;
        int count=trial%12, first=(trial/12)%13, second=(trial*5)%13;
        memset(reference,0x5a,g_xbox_total_ram);
        g_xbox_mem_offset=(ptrdiff_t)reference;
        for (int i=0;i<256*16;i++) MEMF(palette+4*i)=(int32_t)random_word(&seed)/1048576.f;
        for (int i=0;i<count;i++) {
            static const uint32_t weights[]={0,0x80000000u,0x3f800000u,0xbf000000u,0x7fc00000u,0x3eaaaaabu};
            for(int j=0;j<3;j++) MEM32(src+16*i+4*j)=weights[random_word(&seed)%6];
            MEMF(src+16*i+12)=(float)(int32_t)(random_word(&seed)&0xffffff00u);
        }
        uint32_t args[]={0x12345678,src,(uint32_t)count,0x420000,(uint32_t)(first*4),
                         0x450000,(uint32_t)(second*4),palette};
        memcpy(reference+0x700000,args,sizeof args);
        memcpy(candidate,reference,g_xbox_total_ram);
        unsigned counts[2];
        memset(observations,0,sizeof observations);
        for(variant=0;variant<2;variant++) {
            g_xbox_mem_offset=(ptrdiff_t)(variant ? candidate : reference);
            g_eax=3; g_ebx=7; g_ecx=11; g_edx=13; g_esi=17; g_edi=19; g_ebp=23; g_seh_ebp=29;
            g_xmm0=g_xmm1=g_xmm2=g_xmm3=g_xmm4=g_xmm5=XMM_SCALAR_BITS(0xabcdef01);
            g_esp=0x700000; g_loop_budget=0; observed=0;
            (variant ? matrix_candidate : matrix_baseline)();
            observe(); counts[variant]=observed;
        }
        if(counts[0]!=counts[1] || memcmp(observations[0],observations[1],sizeof observations[0]) ||
           memcmp(reference,candidate,g_xbox_total_ram)) { fprintf(stderr,"Matrix mismatch %u\n",trial); return 1; }
    }
    free(reference); free(candidate); puts("192 matrix cases match across RAM, registers, calls and yields."); return 0;
}

