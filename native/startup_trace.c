/* Standalone diagnostic. No Xbox threads are started by this harness. */
#include <stdio.h>
#include <stdlib.h>
#define RECOMP_GENERATED_CODE
#include "recomp_funcs.h"

RECOMP_TLS uint32_t g_eax, g_ecx, g_edx, g_esp, g_ebx, g_esi, g_edi;
RECOMP_TLS uint32_t g_seh_ebp, g_ebp, g_fs_base;
ptrdiff_t g_xbox_mem_offset;

static void first_indirect(uint32_t target, uint32_t site) {
    printf("XBE entry 0x002D8865 reached indirect call 0x%08X, thunk value 0x%08X\n",
           (unsigned)site, (unsigned)target);
    if (site != 0x002DA461 || target != 0x800000FF) exit(1);
    puts("Reached PsCreateSystemThreadEx (ordinal 255). Stopped before kernel dispatch.");
    exit(0);
}

#undef RECOMP_ICALL_SAFE_AT
#define RECOMP_ICALL_SAFE_AT(target, saved_sp, site) first_indirect((target), (site))

/* Reaching one of the later branches would falsify this startup checkpoint. */
void sub_002D7F7C(void) { abort(); }
void sub_002DE5B6(void) { abort(); }
void sub_002D71A6(void) { abort(); }
#include "entry.inc"

int main(int argc, char **argv) {
    if (argc != 2) return 2;
    const size_t ram_size = 64u * 1024u * 1024u;
    unsigned char *ram = calloc(1, ram_size);
    FILE *input = fopen(argv[1], "rb");
    if (!ram || !input) return 2;
    size_t count = fread(ram, 1, ram_size, input);
    if (ferror(input) || count < 0x005E0000 || count == ram_size) return 2;
    fclose(input);
    g_xbox_mem_offset = (ptrdiff_t)(uintptr_t)ram;
    g_esp = (uint32_t)ram_size - 256;
    xbe_entry_point();
    return 1;
}
