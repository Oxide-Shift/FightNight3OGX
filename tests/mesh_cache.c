#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#define NV2A_VSH_INPUTS 16
typedef struct { uint32_t offset,type,size,stride; } VertexAttr;
typedef struct { uint8_t in_slot[16]; unsigned nin,hash; } Nv2aVshProgram;
static struct {
    VertexAttr attr[16]; uint16_t idx[64];
    float imm_attr[16][4]; unsigned flips,inline_active;
} s_gpu;
static uint8_t ram[0x20000];
static size_t g_xbox_total_ram=sizeof ram,g_xbox_map_size;
#define XBOX_HOST(a) (ram+((uint32_t)(a)&0x0fffffffu))
#include "../native/mesh_cache.h"

int main(void){
    Nv2aVshProgram p={0};
    memset(p.in_slot,0xff,sizeof p.in_slot);
    p.nin=3;p.hash=17;p.in_slot[0]=0;p.in_slot[1]=1;p.in_slot[2]=2;
    s_gpu.attr[0]=(VertexAttr){0x80010000,2,3,16};
    s_gpu.attr[1]=(VertexAttr){0x8001000c,0,4,16};
    uint16_t unique[16];
    float packed[16*3*4]={0};
    for(unsigned i=0;i<64;i++)s_gpu.idx[i]=(uint16_t)(i%16);
    for(unsigned i=0;i<16;i++)unique[i]=(uint16_t)i;
    FightMesh *store=NULL,*hit=NULL;
    assert(!fight_mesh_find(&p,64,&store)&&store);
    fight_mesh_save(store,&p,unique,16,64,packed,s_gpu.idx);
    hit=fight_mesh_find(&p,64,&store);
    assert(hit&&hit->ranges==1&&hit->spans[0].bytes==256);
    uint64_t old=hit->token;
    ram[0x100ff]=1;
    assert(!fight_mesh_find(&p,64,&store)&&!store);
    for(s_gpu.flips=1;s_gpu.flips<8;s_gpu.flips++)assert(!fight_mesh_find(&p,64,&store));
    assert(!fight_mesh_find(&p,64,&store)&&store);
    fight_mesh_save(store,&p,unique,16,64,packed,s_gpu.idx);
    hit=fight_mesh_find(&p,64,&store);assert(hit&&hit->token!=old);
    s_gpu.imm_attr[2][0]=1.f;
    assert(!fight_mesh_find(&p,64,&store));
    s_gpu.flips+=9;
    s_gpu.imm_attr[2][0]=0.f;
    assert(fight_mesh_find(&p,64,&store));
    s_gpu.idx[13]=2;
    assert(!fight_mesh_find(&p,64,&store));
    s_gpu.inline_active=1;
    assert(!fight_mesh_find(&p,64,&store)&&!store);
    unsigned bytes;FightSpan spans[16];
    s_gpu.attr[0].offset=0x8001fff8u;
    assert(!fight_mesh_spans(&p,0,15,spans,&bytes));
    return 0;
}
