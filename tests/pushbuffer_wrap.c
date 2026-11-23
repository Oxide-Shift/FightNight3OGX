/* Link with the Windows host's nv2a_pb_scan.o. No disc data is needed. */
#include <assert.h>
#include <stddef.h>
#include <stdint.h>
#include <stdlib.h>

#define BASE 0x80000000u
static uint32_t ring[128];
static unsigned received;
static const uint32_t methods[] = {0x1ea4, 0xb80, 0xb84, 0xb88, 0xb8c, 0xb80};
static const uint32_t values[] = {140, 0x3f800001, 0x20000, 0x81, 0x400b80, 0x3f000000};

extern uint32_t nv2a_pb_run(uint32_t get, uint32_t put, volatile uint32_t *get_reg);
ptrdiff_t xbox_GetMemoryOffset(void) { return (ptrdiff_t)(uintptr_t)ring - BASE; }
void nv2a_pb_exec_method(uint32_t subchannel, uint32_t method, uint32_t value) {
    assert(subchannel == 0 && received < sizeof methods / sizeof methods[0]);
    assert(method == methods[received] && value == values[received]);
    ++received;
}
void fight_exec_packet(uint32_t subchannel, uint32_t method, int noninc,
                       const uint32_t *values, uint32_t count) {
    for (uint32_t i=0; i<count; i++)
        nv2a_pb_exec_method(subchannel, method + (noninc ? 0 : 4*i), values[i]);
}

/* These diagnostic callbacks belong to the scan-only path. */
void nv2a_pb_exec_report(void) { abort(); }
void nv2a_exec_phase_take(double *a, double *b, double *c) { abort(); }
void nv2a_exec_flush_take(unsigned long *a, unsigned long *b,
                          unsigned long long *c, unsigned long long *d) { abort(); }

int main(void) {
    _putenv_s("RECOMP_PB_EXEC", "1");
    ring[96] = (1u << 18) | 0x1ea4;
    ring[97] = values[0];
    ring[98] = (4u << 18) | 0xb80;
    for (unsigned j = 0; j < 4; ++j) ring[99 + j] = values[1 + j];
    ring[103] = 4u * 16u + 1u;  /* Jump back to the beginning of the ring. */
    ring[16] = (1u << 18) | 0xb80;
    ring[17] = values[5];

    uint32_t partial = BASE + 4u * 101u, final = BASE + 4u * 18u;
    assert(nv2a_pb_run(BASE + 4u * 96u, partial, NULL) == partial);
    assert(received == 3);
    assert(nv2a_pb_run(partial, final, NULL) == final);
    assert(received == 6);
    return 0;
}
