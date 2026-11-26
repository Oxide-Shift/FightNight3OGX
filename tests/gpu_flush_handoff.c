/* Exercise the actual Win32 wait implementation with a GPU acknowledge racing
 * both sides of the producer's subscription, plus a signal with the bit set. */
#include <windows.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>

void fight_gpu_flush_wait(volatile uint32_t *);
void fight_gpu_flush_ack(void);

static volatile LONG reg, kick, complete;
static DWORD WINAPI acknowledge(LPVOID ignored) {
    (void)ignored;
    for (LONG cycle = 1; cycle <= 2000; ++cycle) {
        while (InterlockedCompareExchange(&kick,0,0) < cycle) SwitchToThread();
        fight_gpu_flush_ack();  /* A wake alone never changes hardware state. */
        if (cycle % 31 == 0) Sleep(1);
        InterlockedAnd(&reg, ~0x10000);
        fight_gpu_flush_ack();
        InterlockedExchange(&complete, cycle);
    }
    return 0;
}
int main(int argc, char **argv) {
    if (argc > 1) _putenv(argv[1]);
    reg = 0x10003;
    fight_gpu_flush_wait((volatile uint32_t *)&reg);
    if (reg != 0x10003) return 1;
    HANDLE worker = CreateThread(NULL,0,acknowledge,NULL,0,NULL);
    if (!worker) return 2;
    for (LONG cycle = 1; cycle <= 2000; ++cycle) {
        InterlockedExchange(&reg,0x10003);
        InterlockedExchange(&kick,cycle);
        while (InterlockedCompareExchange(&reg,0,0) & 0x10000) {
            fight_gpu_flush_wait((volatile uint32_t *)&reg);
        }
        while (InterlockedCompareExchange(&complete,0,0) < cycle) SwitchToThread();
        if (reg != 3) return 3;
    }
    DWORD status=WaitForSingleObject(worker,5000);
    CloseHandle(worker);
    if (status!=WAIT_OBJECT_0) return 4;
    puts("2000 PFB handoffs, spurious wakes and missing acknowledgement passed.");
    return 0;
}

