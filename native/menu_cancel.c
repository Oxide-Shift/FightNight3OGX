#include <stdint.h>
#include <stdatomic.h>
#include <string.h>
#define RECOMP_GENERATED_CODE
#include "recomp_types.h"

static atomic_int menu_cancel_pending;
static RECOMP_TLS char thread_key;
static _Atomic(uintptr_t) menu_owner;
void fight_bind_menu_thread(void){atomic_store(&menu_owner,(uintptr_t)&thread_key);atomic_store(&menu_cancel_pending,0);}
void fight_request_menu_cancel(void){atomic_store(&menu_cancel_pending,1);}

/* The retail goBackScreen action calls playSound("cancel"). Run its sound dispatch
 * on the same title thread that opened the extension, at its next poll,
 * front-end update or presentation. The Android dialog never sends a Back pad
 * event to the carousel underneath it. */
void fight_service_menu_cancel(void){
    if(!atomic_load_explicit(&menu_cancel_pending,memory_order_relaxed) ||
       atomic_load(&menu_owner)!=(uintptr_t)&thread_key)return;
    if(!atomic_exchange(&menu_cancel_pending,0))return;
    uint32_t manager=MEM32(0x5B25F0);
    recomp_func_t play=recomp_lookup(0x00170930);
    if(!manager || !play)return;
    uint32_t scalar[]={g_eax,g_ecx,g_edx,g_ebx,g_esi,g_edi,g_ebp,g_esp,g_seh_ebp};
    int df=g_df,top=g_fp_top,cmp=g_fp_cmp;
    uint16_t cw=g_fp_control_word,cc=g_fp_cc;
    double fp[8];memcpy(fp,g_fp_stack,sizeof fp);
    RecompXmm xmm[]={g_xmm0,g_xmm1,g_xmm2,g_xmm3,g_xmm4,g_xmm5,g_xmm6,g_xmm7};
    RecompMmx mm[]={g_mm0,g_mm1,g_mm2,g_mm3,g_mm4,g_mm5,g_mm6,g_mm7};
    // The stock Xbox branch at 0x1da482 reads fe_ScreenBack from this event table.
    PUSH32(g_esp,UINT32_MAX);PUSH32(g_esp,UINT32_MAX);
    PUSH32(g_esp,MEM16(0x54EB26));PUSH32(g_esp,0x001DA91E);
    g_ecx=manager;play();
    g_eax=scalar[0];g_ecx=scalar[1];g_edx=scalar[2];g_ebx=scalar[3];g_esi=scalar[4];
    g_edi=scalar[5];g_ebp=scalar[6];g_esp=scalar[7];g_seh_ebp=scalar[8];
    g_df=df;g_fp_top=top;g_fp_cmp=cmp;g_fp_control_word=cw;g_fp_cc=cc;
    memcpy(g_fp_stack,fp,sizeof fp);
    g_xmm0=xmm[0];g_xmm1=xmm[1];g_xmm2=xmm[2];g_xmm3=xmm[3];
    g_xmm4=xmm[4];g_xmm5=xmm[5];g_xmm6=xmm[6];g_xmm7=xmm[7];
    g_mm0=mm[0];g_mm1=mm[1];g_mm2=mm[2];g_mm3=mm[3];
    g_mm4=mm[4];g_mm5=mm[5];g_mm6=mm[6];g_mm7=mm[7];
}
