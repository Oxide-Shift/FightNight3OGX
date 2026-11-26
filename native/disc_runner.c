#include <dlfcn.h>
#include <stdio.h>
#include <string.h>
#include <stdlib.h>

/* The Android launcher supplies the module it built from the verified disc. */
int main(int argc, char **argv) {
    if (argc == 2 && !strcmp(argv[1], "--vertex-compiler")) {
        const char *library = getenv("FNR3_SHADER_LIBRARY");
        if (!library) return 2;
        void *shader = dlopen(library, RTLD_NOW | RTLD_LOCAL);
        int (*worker)(void) = shader ? (int (*)(void))dlsym(shader, "fight_vertex_worker") : NULL;
        return worker ? worker() : 2;
    }
    int check = argc == 3 && !strcmp(argv[1], "--check-module");
    if (!check && argc < 5) return 2;
    void *game = dlopen(argv[check ? 2 : 1], RTLD_NOW | RTLD_LOCAL);
    if (!game) {
        fprintf(stderr, "Opening prepared game: %s\n", dlerror());
        return 2;
    }
    int (*entry)(int, char **) = (int (*)(int, char **))dlsym(game, "fightnight_main");
    if (!entry) {
        fprintf(stderr, "Missing game entry: %s\n", dlerror());
        return 2;
    }
    if (check) return 0;
    argv[1] = argv[0];
    return entry(argc - 1, argv + 1);
}
