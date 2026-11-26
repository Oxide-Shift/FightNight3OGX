/* Exercise the retail D3D consumer and the patched runtime in owned memory. */
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <windows.h>
#define RECOMP_GENERATED_CODE
#include "recomp_types.h"

enum { DEVICE_PTR=0x18000, DEVICE=0x20000, FENCE=0x24000, REGISTERS=0x30000, STACK=0x3000 };
static uint32_t ram[0x480000/4];
size_t g_xbox_total_ram=sizeof ram, g_xbox_map_size=sizeof ram;
ptrdiff_t g_xbox_mem_offset;
ptrdiff_t xbox_GetMemoryOffset(void){return g_xbox_mem_offset;}
RECOMP_TLS uint32_t g_eax,g_ecx,g_edx,g_edi,g_esi,g_esp;

static void *g_nv2a_memory;
static int async_mode, polls;
static int nv2a_async(void){return async_mode;}
static int fence_readable(uint32_t va,uint32_t size){return va>=STACK && (uint64_t)va+size<=sizeof ram;}
static const int g_fence_mirror_count=1;
static const struct {unsigned device_ptr_va,get_ptr_off,put_off;} g_fence_mirrors[]={{DEVICE_PTR,0x30,0x2c}};
#ifndef XBOX_HOST
#define XBOX_HOST(va) ((uint8_t*)ram+(va))
#endif
#include "gpu_progress_writer.inc"

static unsigned interrupts;
void xbox_Nv2aIdleWake(void){++interrupts;}
enum { NV2A_USER_DMA_PUT=0, NV2A_USER_DMA_GET=4 };
static void simulated_scan(uint32_t last_put,uint32_t put,uint32_t now_ms,uint32_t *regs){
#include "gpu_wait_interrupt.inc"
}

static void blocking_fence(void){
    MEM32(0x4461b8)=DEVICE;
    uint32_t regs[]={17,16};
    assert(!xbox_Nv2aArmFenceWait(DEVICE+4));
    async_mode=1;assert(!xbox_Nv2aArmFenceWait(DEVICE+0x1dcc));async_mode=0;
    assert(xbox_Nv2aArmFenceWait(DEVICE+0x1dcc));
    simulated_scan(17,17,50,regs);assert(!interrupts);
    regs[1]=17;
    simulated_scan(16,17,50,regs);assert(!interrupts);
    regs[0]=18;
    simulated_scan(17,17,50,regs);assert(!interrupts);
    regs[0]=17;
    simulated_scan(17,17,50,regs);assert(interrupts==1);
    simulated_scan(17,17,51,regs);assert(interrupts==1);
    xbox_Nv2aDisarmFenceWait();
    simulated_scan(17,17,75,regs);assert(interrupts==1);
}

static void sub_0043B210(void){abort();}
#undef RECOMP_LOOP_YIELD
#define RECOMP_LOOP_YIELD() do { if(++polls>1)abort(); } while(0)
#include "gpu_progress_consumer.inc"

static void read_position(void){
    polls=0;g_esp=STACK;
    MEM32(STACK+4)=DEVICE;MEM32(STACK+8)=1;
    sub_0043B390();
    assert(polls==1 && g_eax==0x80010010 && g_esp==STACK+12);
}
int main(void){
    g_xbox_mem_offset=(ptrdiff_t)(uintptr_t)ram;
    blocking_fence();
    g_nv2a_memory=&ram[REGISTERS/4];
    MEM32(DEVICE_PTR)=DEVICE;MEM32(DEVICE+0x934)=REGISTERS;
    MEM32(DEVICE+0x30)=FENCE;MEM32(DEVICE+0x1c20)=0x26000;
    MEM32(0x26000+0x44)=0x10010;
    MEM32(DEVICE+0x24)=0x80010000;MEM32(DEVICE+0x28)=0x80010100;
    for(unsigned i=0;i<256;i++){
        uint32_t seq=0xffffff01u+2*i;
        uint32_t marker=0x2468ac01u|((seq&31)<<2);
        if(i)xbox_Nv2aProgressMarker(marker);
        MEM32(DEVICE+0x2c)=seq+2;
        fence_mirrors_tick();
        assert(MEM32(FENCE)==seq+2);
        if(i)assert((MEM32(REGISTERS+0x400b10)&~0x7cu)==(marker&~0x7cu));
        read_position();
        async_mode=1;
        xbox_Nv2aProgressMarker(marker);xbox_Nv2aSemaphoreRelease(seq);
        fence_mirrors_tick();
        assert(MEM32(FENCE)==seq && MEM32(REGISTERS+0x400b10)==marker);
        read_position();async_mode=0;
    }
    puts("Retail ring poll completed on first read before the first marker, across fence wraps and in asynchronous mode");
}
