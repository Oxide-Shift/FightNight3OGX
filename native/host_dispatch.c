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
#include "worker_watch.h"
#include "xid_guard.h"


/* Every indirect call asks this first (RECOMP_ICALL*). The retail XPP gamepad report handler is reached only that
   way (its sole reference is the XID type table at 0x471DF4), so wrapping it here guards it without re-translating. */
#if defined(__ANDROID__) || defined(__linux__)
static uint32_t xid_read(uint32_t va) { return MEM32(va); }
static void xid_write(uint32_t va, uint32_t value) { ST32(va, value); }
static void xid_log(const char *line) { fputs(line, stderr); fflush(stderr); }
extern void sub_00476080(void) __attribute__((weak));
static void xid_report_guarded(void) {
    xid_guard(g_ecx, xid_read, xid_write, xid_log);
    sub_00476080();
}
recomp_func_t recomp_lookup_manual(uint32_t va) {
    return va == 0x00476080u && sub_00476080 ? xid_report_guarded : NULL;
}
#else
recomp_func_t recomp_lookup_manual(uint32_t va) {
    (void)va;
    return NULL;
}
#endif

void recomp_icall_fail_log(uint32_t va) {
    fprintf(stderr, "[Fight Night] missing indirect target 0x%08X, ESP 0x%08X\n", va, g_esp);
    exit(40);
}

static uint32_t ww_guest_read(uint32_t va) { return MEM32(va); }
static WorkerWatch worker_watch;
static atomic_uint worker_watch_logged;

/* Once per presented frame (host_video.c). Logs the first few changes as they happen; the crash dump repeats the
   history because the report keeps only the last 40 log lines. */
void fight_worker_watch(uint32_t frame) {
    if (!worker_watch.seen && !ww_readable(MEM32(WW_OBJECT_GLOBAL))) return;   /* worker not created yet */
    if (ww_poll(&worker_watch, frame, ww_guest_read) && atomic_fetch_add(&worker_watch_logged, 1) < 12) {
        fprintf(stderr, "FNR3/1 WORKER f%u obj %08X vt %08X stop %u\n",
                frame, worker_watch.obj, worker_watch.vt, (unsigned)MEM8(WW_STOP_FLAG));
    }
}

#if defined(__ANDROID__) || defined(__linux__)
#include <dlfcn.h>
/* Host return site inside our own modules only, as a module-relative offset (no other process addresses). */
static void crash_return_site(const char *label, void *address) {
    Dl_info info;
    if (!address || !dladdr(address, &info) || !info.dli_fname || !info.dli_fbase) return;
    const char *name = strrchr(info.dli_fname, '/');
    name = name ? name + 1 : info.dli_fname;
    char role = !strcmp(name, "libfightnight_game.so") ? 'G' : !strcmp(name, "libdisc_runner.so") ? 'R' :
                !strcmp(name, "libfight_shader.so") ? 'S' : 0;
    if (role) fprintf(stderr, "FNR3/1 %s %c %08llx\n", label, role,
                      (unsigned long long)((uintptr_t)address - (uintptr_t)info.dli_fbase));
}
#else
static void crash_return_site(const char *label, void *address) { (void)label; (void)address; }
#endif

static atomic_int crash_reporter;

void recomp_icall_not_code_log(uint32_t va) {
    /* Several threads can arrive here together. Every one prints its own two lines; only the first adds the
       context dump, and the others wait briefly so that dump reaches the log before the process exits. */
    int first = atomic_exchange(&crash_reporter, 1) == 0;
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
    crash_return_site("ICALL_FROM", __builtin_return_address(0));
    if (first) {
        char line[200];
        fprintf(stderr, "FNR3/1 CRASHREGS eax %08X ecx %08X edx %08X ebx %08X esi %08X edi %08X esp %08X\n",
                g_eax, g_ecx, g_edx, g_ebx, g_esi, g_edi, g_esp);
        uint32_t obj = MEM32(WW_OBJECT_GLOBAL);
        fprintf(stderr, "FNR3/1 WORKERNOW obj %08X stop %u expect vt %08X\n", obj, (unsigned)MEM8(WW_STOP_FLAG), WW_VTABLE);
        ww_format_words(line, sizeof line, "OBJ", obj, 16, ww_guest_read); fputs(line, stderr);
        ww_format_words(line, sizeof line, "OBJ-40", obj - 0x40u, 16, ww_guest_read); fputs(line, stderr);
        WorkerWatch snapshot = worker_watch;
        ww_format_history(&snapshot, line, sizeof line); fputs(line, stderr);
        fflush(stderr);
    } else {
        fflush(stderr);
#ifdef _WIN32
        Sleep(300);
#else
        struct timespec pause = {0, 300000000L};
        nanosleep(&pause, NULL);
#endif
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
