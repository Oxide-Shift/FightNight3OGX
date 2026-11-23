#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include "../native/frame_output.c"

static uint8_t memory[640 * 480 * 4 + 4096];
size_t g_xbox_total_ram = 64 * 1024 * 1024;

ptrdiff_t xbox_GetMemoryOffset(void) {
    return (ptrdiff_t)(uintptr_t)memory - 0x10000;
}

static const uint32_t *current_slot(void) {
    return (const uint32_t *)((const uint8_t *)surface + HEADER + surface[0] % 3 * slot_bytes);
}

int main(void) {
    static uint32_t high[960 * 720];
    static uint8_t down[640 * 480 * 4];
    slot_bytes = sizeof(high) + 8;
    surface = calloc(1, HEADER + 3 * slot_bytes);
    assert(surface);
    high[0] = 0xaabbccdd;
    frame_output_high((const uint8_t *)high, 960, 720, 0x80010000u,down);
    assert(surface[0] == 0);
    frame_output_present(0xf0010000u, 2560);
    assert(surface[0] == 1 && current_slot()[0] == 960 && current_slot()[1] == 720);
    assert(current_slot()[2] == 0xaabbccdd);
    /* A cinematic holding a frame for two vblanks keeps the original image.
       A guest write to a single pixel at the same address is still visible. */
    frame_output_present(0x10000u,2560);
    assert(surface[0]==1 && current_slot()[0]==960);
    memory[sizeof down-1]=0x5a;
    frame_output_present(0x10000u,2560);
    assert(surface[0]==2 && current_slot()[0]==640);
    assert(((const uint8_t *)(current_slot()+2))[sizeof down-1]==0x5a);
    frame_output_forget_native();
    high[0] = 0x12345678;
    frame_output_high((const uint8_t *)high, 960, 720, 0x80010000u,down);
    assert(surface[0] == 2 && current_slot()[0] == 640);
    memset(memory + 0x100, 0x5a, 4);
    frame_output_present(0x10100u, 2560);
    assert(surface[0] == 3 && current_slot()[0] == 640 && current_slot()[2] == 0x5a5a5a5a);
    frame_output_present(0x10000u, 2560);
    assert(surface[0] == 4 && current_slot()[0] == 640);
    free(surface);
    return 0;
}
