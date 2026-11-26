#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stdatomic.h>
#include "kernel.h"
#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#endif
#if defined(__x86_64__)
#include <xmmintrin.h>
#endif
#include "recomp_types.h"


recomp_func_t recomp_lookup_manual(uint32_t va) {
    (void)va;
    return NULL;
}

void recomp_icall_fail_log(uint32_t va) {
    fprintf(stderr, "[Fight Night] missing indirect target 0x%08X, ESP 0x%08X\n", va, g_esp);
    exit(40);
}

void recomp_icall_not_code_log(uint32_t va) {
    fprintf(stderr, "[Fight Night] invalid indirect target 0x%08X, ESP 0x%08X\n", va, g_esp);
    if (g_esp >= 0x10000 && g_esp < 0x04000000 - 32) {
        fprintf(stderr, "[Fight Night] guest stack");
        for (unsigned i = 0; i < 8; ++i) fprintf(stderr, " %08X", MEM32(g_esp + i*4));
        fputc('\n', stderr);
        if (MEM32(g_esp) == 0x001F3381u) {
            fprintf(stderr, "[Fight Night] worker sleep call 001F337E, stop %u, object %08X, vtable %08X\n",
                    (unsigned)MEM8(0x005C4A8C), MEM32(0x005C4A84), g_eax);
        }
    }
#ifdef _WIN32
    void *stack[12];
    USHORT count = CaptureStackBackTrace(0, 12, stack, NULL);
    uintptr_t base = (uintptr_t)GetModuleHandleW(NULL);
    fprintf(stderr, "[Fight Night] host return offsets");
    for (USHORT i = 0; i < count; ++i) fprintf(stderr, " %llx", (unsigned long long)((uintptr_t)stack[i] - base));
    fputc('\n', stderr);
#endif
    exit(41);
}

void recomp_unimpl(const char *instruction, uint32_t va) {
    static RECOMP_TLS int masked,old_irql;
    if (!strcmp(instruction,"cli")) {
        if (!masked) {old_irql=xbox_KfRaiseIrql(31);masked=1;}
        return;
    }
    if (!strcmp(instruction,"sti")) {
        if (masked) {masked=0;xbox_KfLowerIrql((KIRQL)old_irql);}
        return;
    }
    /* This retail MMX feature probe toggles EFLAGS.ID, compares the two
       snapshots, and tests CPUID.1:EDX[23]. Its arithmetic flags are dead. */
    static RECOMP_TLS uint32_t probe_flags=0x202;
    /* An EA decoder calls its block refill only after CMP EAX,0xEE
       compared equal. The refill's saved arithmetic flags are replaced
       by a fresh CMP before the next branch. Preserve the guest stack
       and direction flag across that call. */
    if (va == 0x002D340E && !strcmp(instruction, "pushfd")) {
        uint32_t flags = (probe_flags & 0x200000) | (masked ? 0 : 0x200) |
                         (g_df ? 0x400 : 0) | 0x46;
        PUSH32(g_esp, flags);
        return;
    }
    if (va == 0x002D3418 && !strcmp(instruction, "popfd")) {
        uint32_t flags;
        POP32(g_esp, flags);
        g_df = (flags & 0x400) != 0;
        return;
    }
    if ((va==0x00455BD5 || va==0x00455BE1) && !strcmp(instruction,"pushfd")) {
        PUSH32(g_esp,probe_flags);
        return;
    }
    if (va==0x00455BE0 && !strcmp(instruction,"popfd")) {
        uint32_t value;
        POP32(g_esp,value);
        probe_flags=0x202|(value&0x200000);
        return;
    }
    if (va==0x00455BEC && !strcmp(instruction,"cpuid") && g_eax==1) {
        g_eax=0x683;g_ebx=0;g_ecx=0;g_edx=0x0383f9ff;
        return;
    }
    if (!strcmp(instruction, "wbinvd")) {
        /* CPU and software GPU use the same coherent host RAM. */
        atomic_thread_fence(memory_order_seq_cst);
        return;
    }
    /* The encoder's field pin is bit 5 of MCPX GPIO. The other pins keep
       their last programmed value. Never issue host port-I/O instructions. */
    static atomic_uchar gpio;
    if ((uint16_t)g_edx == 0x80c0) {
        if (!strcmp(instruction, "out dx, al")) {
            atomic_store(&gpio, (uint8_t)g_eax);
            return;
        }
        if (!strcmp(instruction, "in al, dx")) {
#ifdef _WIN32
            uint64_t ms = GetTickCount64();
#else
            struct timespec now;
            clock_gettime(CLOCK_MONOTONIC, &now);
            uint64_t ms = (uint64_t)now.tv_sec * 1000 + now.tv_nsec / 1000000;
#endif
            unsigned field = (unsigned)((ms * 60 / 1000) & 1) << 5;
            g_eax = (g_eax & 0xffffff00u) | (atomic_load(&gpio) & 0xdf) | field;
            return;
        }
    }
    /* This revision initializes each Xbox worker's SSE environment here. */
    if (va == 0x002B9348 && !strcmp(instruction, "ldmxcsr dword ptr [esp]") &&
        MEM32(g_esp) == 0x9f80) {
#if defined(__x86_64__)
        _mm_setcsr(0x9f80);
        return;
#elif defined(__aarch64__)
        /* Nearest rounding, masked exceptions, flush tiny outputs. Legacy Arm
           FZ also flushes subnormal inputs; SSE parity there is still open. */
        uint64_t fpcr;
        __asm__ volatile("mrs %0, fpcr" : "=r"(fpcr));
        fpcr &= ~((3ull << 22) | (31ull << 8) | (1ull << 15));
        fpcr |= 1ull << 24;
        __asm__ volatile("msr fpcr, %0\n\tmsr fpsr, xzr\n\tisb" :: "r"(fpcr) : "memory");
        return;
#endif
    }
    fprintf(stderr, "[Fight Night] unsupported %s at 0x%08X\n", instruction, va);
    exit(42);
}
