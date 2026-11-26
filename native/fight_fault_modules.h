/* Optional supplement to crash_signal.h. Populate once per child after known modules
 * are loaded, before installing/arming this handler. Keep those modules loaded. */
#pragma once
#include <link.h>
#include <stdint.h>
#include <string.h>
#include <unistd.h>

typedef struct { uintptr_t lo, hi, base; char role; } FightFaultRange;
typedef struct { FightFaultRange ranges[32]; unsigned count; } FightFaultMap;
static int fight_index_text(struct dl_phdr_info *i, size_t size, void *opaque) {
    FightFaultMap *map=opaque;
    const char *name=i->dlpi_name ? i->dlpi_name : "";
    const char *slash=strrchr(name,'/');
    name=slash ? slash+1 : name;
    char role=0;
    if (!strcmp(name,"libfightnight_game.so")) role='G';
    else if (!strcmp(name,"libfight_shader.so")) role='S';
    else if (!strcmp(name,"libdisc_runner.so")) role='R';
    if (!role) return 0;
    for (unsigned n=0;n<i->dlpi_phnum && map->count<32;++n) {
        const ElfW(Phdr) *p=&i->dlpi_phdr[n];
        if (p->p_type!=PT_LOAD || !(p->p_flags&PF_X)) continue;
        uintptr_t lo=i->dlpi_addr+p->p_vaddr;
        if (p->p_memsz>UINTPTR_MAX-lo) continue;
        map->ranges[map->count++]=(FightFaultRange){lo,lo+p->p_memsz,i->dlpi_addr,role};
    }
    return 0;
}
/* No allocation, dladdr, stack unwind or formatted I/O in the signal handler.
 * A return address never goes through this FAULT_PC path. Unknown modules retain
 * the existing EXTERNAL marker; do not expose unknown process addresses. */
static int fight_write_known_pc(const FightFaultMap *map,uintptr_t pc) {
    for(unsigned n=0;n<map->count;++n){
        const FightFaultRange *r=&map->ranges[n];
        if(pc<r->lo || pc>=r->hi) continue;
        char line[]="FNR3/1 MODULE_PC ? 0000000000000000\n";
        char *role=line+sizeof("FNR3/1 MODULE_PC ")-1;
        *role=r->role;
        uint64_t offset=pc-r->base;
        for(int j=15;j>=0;--j){role[2+j]="0123456789abcdef"[offset&15];offset>>=4;}
        (void)write(STDERR_FILENO,line,sizeof(line)-1);
        return 1;
    }
    return 0;
}

