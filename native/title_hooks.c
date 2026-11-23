#include "recomp_types.h"

extern int xbox_DspAckAdd(uint32_t address);

/* The guest sends its effects image to the Xbox general-purpose DSP and
   polls the command word at 0x0046A1D5. Register the live mailbox with the
   runtime's DSP service before the guest publishes its first command. */
void sub_0046A06E_enter(void) {
    uint32_t device = MEM32(g_ecx + 8);
    uint32_t descriptor = MEM32(device + 0x10);
    uint32_t image = MEM32(descriptor);
    xbox_DspAckAdd(image + 0x810);
}
