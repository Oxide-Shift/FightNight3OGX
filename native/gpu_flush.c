/* Retail D3D's PFB flush is completed by the NV2A register worker. Wake the
 * producer at that exact acknowledgement. DMA GET/PUT and command order stay
 * under the executor's ownership. */
#ifdef _WIN32
#include <windows.h>
#else
#include <pthread.h>
#endif
#include <stdatomic.h>
#include <stdint.h>
#include <stdlib.h>
#include <time.h>

#ifdef _WIN32
static SRWLOCK flush_lock = SRWLOCK_INIT;
static CONDITION_VARIABLE flush_done = CONDITION_VARIABLE_INIT;
static void lock_flush(void) { AcquireSRWLockExclusive(&flush_lock); }
static void unlock_flush(void) { ReleaseSRWLockExclusive(&flush_lock); }
static void wake_flush(void) { WakeAllConditionVariable(&flush_done); }
static void wait_flush(void) { SleepConditionVariableSRW(&flush_done, &flush_lock, 2, 0); }
#else
static pthread_mutex_t flush_lock = PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t flush_done = PTHREAD_COND_INITIALIZER;
static void lock_flush(void) { pthread_mutex_lock(&flush_lock); }
static void unlock_flush(void) { pthread_mutex_unlock(&flush_lock); }
static void wake_flush(void) { pthread_cond_broadcast(&flush_done); }
static void wait_flush(void) {
    struct timespec until;
#ifdef __ANDROID__
    clock_gettime(CLOCK_MONOTONIC, &until);
#else
    clock_gettime(CLOCK_REALTIME, &until);
#endif
    until.tv_nsec += 2000000;
    if (until.tv_nsec >= 1000000000) { ++until.tv_sec; until.tv_nsec -= 1000000000; }
#ifdef __ANDROID__
    pthread_cond_timedwait_monotonic_np(&flush_done, &flush_lock, &until);
#else
    pthread_cond_timedwait(&flush_done, &flush_lock, &until);
#endif
}
#endif
static atomic_uint waiters;

void fight_gpu_flush_ack(void) {
    if (!atomic_load_explicit(&waiters, memory_order_seq_cst)) return;
    lock_flush();
    wake_flush();
    unlock_flush();
}

void fight_gpu_flush_wait(volatile uint32_t *reg) {
    static _Thread_local int enabled;
    if (!enabled) {
        const char *serial = getenv("RECOMP_GUEST_LOCK");
        const char *poll = getenv("FNR3_PFB_POLL");
        enabled = ((serial && *serial == '1') || (poll && *poll == '1')) ? -1 : 1;
    }
    if (enabled < 0 || !(*reg & 0x10000u)) return;
    lock_flush();
    atomic_fetch_add_explicit(&waiters, 1, memory_order_seq_cst);
    if (*reg & 0x10000u) {
        /* Other writers, shutdown and a route through an older runtime must
         * all return to the title's ordinary register and scheduler checks. */
        wait_flush();
    }
    atomic_fetch_sub_explicit(&waiters, 1, memory_order_seq_cst);
    unlock_flush();
}

