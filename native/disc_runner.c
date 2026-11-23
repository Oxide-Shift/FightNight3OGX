#include <dlfcn.h>
#include <stdio.h>

/* The Android launcher supplies the module it built from the verified disc. */
int main(int argc, char **argv) {
    if (argc < 5) return 2;
    void *game = dlopen(argv[1], RTLD_NOW | RTLD_LOCAL);
    if (!game) {
        fprintf(stderr, "Opening prepared game: %s\n", dlerror());
        return 2;
    }
    int (*entry)(int, char **) = (int (*)(int, char **))dlsym(game, "fightnight_main");
    if (!entry) {
        fprintf(stderr, "Missing game entry: %s\n", dlerror());
        return 2;
    }
    argv[1] = argv[0];
    return entry(argc - 1, argv + 1);
}
