/* Test the original guest accessor against the 16-byte path at RAM and MMIO
 * boundaries. The fixture owns every virtual page used by this test. */
#include <windows.h>
#include "recomp_types.h"
#include "matrix_stream.h"
#include <stdio.h>

ptrdiff_t g_xbox_mem_offset;
size_t g_xbox_total_ram=64u*1024u*1024u;

int main(void) {
    const struct { uint32_t addr; int ram; } cases[] = {
        {0xfff4,0},{0x10000,1},{0x10003,1},{0x03fffff0,1},{0x03fffff1,0},
        {0x80010008,1},{0x83fffff0,1},{0x83fffff4,0},{0xfd100000,0}
    };
    unsigned char *mapping=VirtualAlloc(NULL,UINT64_C(1)<<32,MEM_RESERVE,PAGE_NOACCESS);
    if (!mapping) return 1;
    g_xbox_mem_offset=(ptrdiff_t)mapping;
    for (unsigned i=0; i<sizeof cases/sizeof cases[0]; ++i) {
        uint32_t addr=cases[i].addr;
        unsigned char *data=mapping+addr;
        if (!VirtualAlloc(data-1,18,MEM_COMMIT,PAGE_READWRITE)) return 2;
        memset(data-1,0xad,18);
        for(unsigned j=0;j<16;j++) data[j]=(unsigned char)(17*i+j);
        if (fight_matrix_ram(addr)!=cases[i].ram) return 3;
        RecompXmm before=XMM_MEM(addr), candidate=fight_matrix_load(addr);
        if(memcmp(&before,&candidate,sizeof before))return 4;
        candidate.q[1] ^= UINT64_C(0xfe01234567890123);
        fight_matrix_store(addr,candidate);
        before=XMM_MEM(addr);
        if(memcmp(&before,&candidate,sizeof before) || data[-1]!=0xad || data[16]!=0xad) return 5;
    }
    VirtualFree(mapping,0,MEM_RELEASE);
    puts("RAM, high alias, unaligned, crossing and MMIO-range matrix transfers match.");
    return 0;
}

