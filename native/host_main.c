#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "kernel.h"
#include "xbox_memory_layout.h"
#include "fight_data.h"
#ifdef __ANDROID__
#include "crash_signal.h"
#endif

extern int recomp_dispatch_init(void);
extern size_t recomp_get_count(void);
extern void xbe_entry_point(void);
extern RECOMP_TLS uint32_t g_esp;
extern ptrdiff_t g_xbox_mem_offset;
int corner_connect(void);
void frame_output_init(void);
int pad_start(void);
#ifdef FNR3_VULKAN
void fight_gpu_init(void);
#endif

static uint32_t read32(const unsigned char *p) {
    uint32_t value;
    memcpy(&value, p, sizeof(value));
    return value;
}

int main(int argc, char **argv) {
    int corner = argc == 5 && !strcmp(argv[4], "--corner");
    if (argc != 4 && !corner) {
        fprintf(stderr, "Usage: fightnight-host <default.xbe> <game-dir> <save-dir> [--corner]\n");
        return 2;
    }
    setvbuf(stderr, NULL, _IONBF, 0);
    setvbuf(stdout, NULL, _IONBF, 0);
    if (corner && !corner_connect()) return 4;
#ifdef __ANDROID__
    if (corner) fight_crash_signals();
#endif
#ifdef _WIN32
    _putenv_s("RECOMP_TITLE_KEVENTS", "1");
    _putenv_s("RECOMP_SAVE_DIR", argv[3]);
    _putenv_s("RECOMP_PB_EXEC", "1");
    _putenv_s("RECOMP_VBLANK", "1");
    _putenv_s("RECOMP_AC97_READY", "1");
#else
    setenv("RECOMP_TITLE_KEVENTS", "1", 1);
    setenv("RECOMP_SAVE_DIR", argv[3], 1);
    setenv("RECOMP_PB_EXEC", "1", 1);
    setenv("RECOMP_VBLANK", "1", 1);
    setenv("RECOMP_AC97_READY", "1", 1);
#endif
    FILE *input = fopen(argv[1], "rb");
    if (!input) { perror("open XBE"); return 2; }
    const size_t size = 5750784;
    unsigned char *image = malloc(size);
    if (!image || fread(image, 1, size, input) != size || fgetc(input) != EOF) {
        fprintf(stderr, "Unexpected or unreadable XBE.\n");
        return 2;
    }
    fclose(input);
    uint32_t base = read32(image + 0x104);
    uint32_t cert = read32(image + 0x118) - base;
    uint32_t thunk = read32(image + 0x158) ^ 0x5B6D40B6u;
    if (memcmp(image, "XBEH", 4) || base != 0x10000 || cert > size - 12 ||
        read32(image + cert + 8) != 0x45410086 || thunk != 0x47AE20) {
        fprintf(stderr, "Unsupported Fight Night executable.\n");
        return 2;
    }
    if (cert <= size - 0xA4) {
        /* The kernel reports a console of the title's region (certificate: US 1, Europe 4). */
        char region[16];
        snprintf(region, sizeof region, "0x%X", read32(image + cert + 0xA0));
#ifdef _WIN32
        _putenv_s("RECOMP_TITLE_REGION", region);
#else
        setenv("RECOMP_TITLE_REGION", region, 1);
#endif
    }
    /* D3D's wait at 0x0043B817 reads *(*(device + 0x30)) and compares it
       with the submitted sequence in device + 0x2c. Register before threads. */
    if (xbox_Nv2aMirrorFence(0x004461B8,0x2c,0x30) < 0) return 3;
    if (!xbox_MemoryLayoutInit(image, size)) return 3;
    g_xbox_mem_offset = xbox_GetMemoryOffset();
    xbox_kernel_init();
    fight_data_init(argv[2]);
    xbox_path_init(argv[2], argv[3]);
    xbox_kernel_set_thunk_address(thunk, 150);
    xbox_kernel_bridge_init();
#ifdef FNR3_FRAME_PROFILE
    fprintf(stderr,"[QATID] guest %lu\n",(unsigned long)GetCurrentThreadId());
#endif
    recomp_dispatch_init();
    if (!pad_start()) return 5;
    g_esp = XBOX_STACK_TOP;
    frame_output_init();
#ifdef FNR3_VULKAN
    fight_gpu_init();
#endif
#ifdef _WIN32
    xbox_WatchdogStart();
#endif
    fprintf(stderr, "[Fight Night] entering XBE at 0x002D8865 with %zu dispatch entries\n",
            recomp_get_count());
    xbe_entry_point();
    fprintf(stderr, "[Fight Night] XBE entry returned\n");
    xbox_kernel_shutdown();
    xbox_MemoryLayoutShutdown();
    free(image);
    return 0;
}
