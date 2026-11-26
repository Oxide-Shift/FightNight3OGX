/* Run the translated poll against the MCPX state the retail idle ISR also checks. */
#include <assert.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include <windows.h>
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
void fight_pad_playlist(int enabled){(void)enabled;}
void fight_pad_menu(int enabled){(void)enabled;}
void fight_trax_init(const char *root){(void)root;}

static int timed;
static unsigned polls;
static uint64_t frequency;

static uint64_t monotonic_ticks(void) {
    LARGE_INTEGER ticks;
    QueryPerformanceCounter(&ticks);
    uint64_t value = (uint64_t)ticks.QuadPart;
    return value / frequency * 10000000 + value % frequency * 10000000 / frequency;
}

static uint64_t thread_cpu_ticks(void) {
    FILETIME created, exited, kernel, user;
    assert(GetThreadTimes(GetCurrentThread(), &created, &exited, &kernel, &user));
    return (((uint64_t)kernel.dwHighDateTime << 32) | kernel.dwLowDateTime)
         + (((uint64_t)user.dwHighDateTime << 32) | user.dwLowDateTime);
}

void stop_clock(uint32_t site){
    if (timed) {
        now = monotonic_ticks();
        ++polls;
    }
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
/* This fixture counts calls to the actual translated stop poll. The guest clock
 * uses a real monotonic time source, and the mixer deliberately misses the stop.
 * Wall and CPU times are measurements, so scheduler noise cannot fail the suite. */
static void benchmark(void) {
    LARGE_INTEGER hz;
    QueryPerformanceFrequency(&hz);
    frequency = (uint64_t)hz.QuadPart;
    MEM8(VOICE+0x64)=2;
    MEM32(TABLE+3*128+0x54)=1u<<21;
    timed = 1;
    uint64_t begin_cpu = thread_cpu_ticks(), elapsed[100];
    unsigned counts[100];
    for (unsigned i=0; i<100; ++i) {
        uint64_t start=monotonic_ticks();
        MEM32(VOICE+0x78)=(uint32_t)start;
        MEM32(VOICE+0x7c)=(uint32_t)(start>>32);
        MEM16(VOICE+0x12)=0x8001;
        unsigned before=cleanups;
        polls=0;
        do {
            g_ecx=VOICE; g_esp=0x7100; g_ebp=0x7200;
            MEM32(g_esp)=WAITER;
            sub_0046CDD3();
        } while (MEM16(VOICE+0x12)&0x8000);
        elapsed[i]=monotonic_ticks()-start; counts[i]=polls;
        assert(cleanups==before+1 && elapsed[i]>=5000);
    }
    uint64_t cpu=thread_cpu_ticks()-begin_cpu;
    for (unsigned i=0; i<100; ++i)
        printf("stop %u wall_ms %.4f polls %u\n", i+1, elapsed[i]/10000.0, counts[i]);
    printf("100 stops, thread CPU ms %.3f\n", cpu/10000.0);
}

int main(int argc, char **argv){
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
    // A 0.5 ms grace (5,000 guest ticks). Exercise consecutive stop requests,
    // clock rollover, and a slot which is reassigned while waiting.
    uint64_t requested=0xfffffff0ull;
    MEM32(TABLE+3*128+0x54)=1u<<21;
    for(unsigned repeat=0;repeat<4;repeat++,requested+=200000){
        MEM32(VOICE+0x78)=(uint32_t)requested;MEM32(VOICE+0x7c)=(uint32_t)(requested>>32);
        now=requested-1;poll_stop(WAITER,0);
        // The mixer can clear ACTIVE during a wait. A new poll sees its new state
        // immediately, including a slot reassigned before the next poll.
        now=requested+2000;poll_stop(WAITER,0);
        MEM32(TABLE+3*128+0x54)=0;poll_stop(WAITER,1);
        MEM32(OWNER+0x88+3*4)=0x35000;poll_stop(WAITER,0);
        MEM32(OWNER+0x88+3*4)=VOICE;MEM32(TABLE+3*128+0x54)=1u<<21;
        now=requested+4999;poll_stop(WAITER,0);
        now++;poll_stop(WAITER,1);poll_stop(0x46bdee,0);
        MEM32(OWNER+0x88+3*4)=0x35000;poll_stop(WAITER,0);MEM32(OWNER+0x88+3*4)=VOICE;
        now=requested+5000000;poll_stop(0x46bdee,1);
    }
    puts("1,000 synchronous idle stops, live channels, slot reuse and retail watchdog passed");
    if (argc==2 && !strcmp(argv[1],"--benchmark")) benchmark();
    return 0;
}
