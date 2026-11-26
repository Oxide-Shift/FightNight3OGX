#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <time.h>
typedef void (*target)(void);
target baseline_recomp_lookup(uint32_t), candidate_recomp_lookup(uint32_t);
size_t baseline_recomp_dispatch_flat_bytes(void), candidate_recomp_dispatch_flat_bytes(void);
int baseline_recomp_dispatch_init(void), candidate_recomp_dispatch_init(void);
int main(int argc, char **argv) {
    if (!baseline_recomp_dispatch_init() || !candidate_recomp_dispatch_init()) return 1;
    for (uint32_t i=0; i<0x600000; ++i)
        if (baseline_recomp_lookup(i)!=candidate_recomp_lookup(i)) return 2;
    for (uint32_t i=0;i<65536;i++) {
        uint32_t va=UINT32_MAX-i*32771u;
        if (baseline_recomp_lookup(va)!=candidate_recomp_lookup(va)) return 3;
    }
    printf("Dispatch matches for every address through 0x5fffff plus 65536 high addresses. %zu -> %zu bytes.\n",
           baseline_recomp_dispatch_flat_bytes(),candidate_recomp_dispatch_flat_bytes());
    if (argc>1) {
        FILE *file=fopen(argv[1],"rb"); if(!file)return 4;
        fseek(file,0,SEEK_END); long bytes=ftell(file); rewind(file);
        uint32_t *keys=malloc(bytes); if(!keys || fread(keys,1,bytes,file)!=(size_t)bytes)return 5;
        fclose(file);
        for(unsigned pass=0;pass<4;pass++) {
            target (*fn)(uint32_t)=(pass==1||pass==2)?candidate_recomp_lookup:baseline_recomp_lookup;
            clock_t start=clock(); uintptr_t sum=0;
            for(unsigned j=0;j<8;j++) for(long i=0;i<bytes/4;i++)sum+=(uintptr_t)fn(keys[i]);
            printf("%s %ld recorded calls x8: %.1f ms, checksum %llu\n",pass==1||pass==2?"candidate":"baseline",
                bytes/4,1000.*(clock()-start)/CLOCKS_PER_SEC,(unsigned long long)sum);
        }
        free(keys);
    }
    return 0;
}

