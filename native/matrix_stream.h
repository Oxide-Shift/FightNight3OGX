/* One guest MOVAPS/MOVUPS transfers four lanes. On ordinary Xbox RAM a
 * single unaligned-safe transfer also lets the host compiler keep the lanes
 * in a vector register. Retain the scalar bus operations for device ranges. */
#ifndef FIGHT_MATRIX_STREAM_H
#define FIGHT_MATRIX_STREAM_H
#include <string.h>
extern size_t g_xbox_total_ram;

static inline int fight_matrix_ram(uint32_t address) {
    uint32_t physical = address & ~UINT32_C(0x80000000);
    return physical >= 0x10000 && (uint64_t)physical + 16 <= g_xbox_total_ram;
}
static inline RecompXmm fight_matrix_load(uint32_t address) {
    if (!fight_matrix_ram(address)) return XMM_MEM(address);
    RecompXmm value;
    memcpy(&value, (const void *)XBOX_PTR(address), sizeof value);
    return value;
}
static inline void fight_matrix_store(uint32_t address, RecompXmm value) {
    if (fight_matrix_ram(address)) memcpy((void *)XBOX_PTR(address), &value, sizeof value);
    else XMM_STORE(address, value);
}
#endif

