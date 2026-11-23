#include <ctype.h>
#include <stdint.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "fight_data.h"
#include "xbox_memory_layout.h"

static uint8_t *locale;
static size_t index_at, strings_at, strings_length;
static unsigned count;
static atomic_int lower_music;
static uint32_t load32(const void *p) { uint32_t v; memcpy(&v,p,4); return v; }
static unsigned load16(const void *p) { uint16_t v; memcpy(&v,p,2); return v; }

void fight_trax_route(int on) { atomic_store(&lower_music,on); }

/* stdTrax draws the EA emblems and narrow sideways wordmark separately.
 * Match the complete BC2 image before suppressing a draw on the upper screen. */
int fight_trax_badge(const uint8_t *blocks, unsigned width) {
    int route=atomic_load_explicit(&lower_music,memory_order_relaxed);
    if(!route)return 0;
    uint64_t expected;
    if (width==16) {
        if (route!=2) return 0;
        if (load32(blocks+512)!=0xd612ffddu) return 0;
        expected=UINT64_C(0x6e9f3cd54673e950);
    } else if (width==64) {
        uint32_t marker=load32(blocks+2604);
        if (marker==0xf5bd0be2u) expected=UINT64_C(0x2fe0d1770b908929);
        else if (marker==0xaafffffdu) expected=UINT64_C(0x35b0d3cafd607c82);
        else return 0;
    } else return 0;
    uint64_t hash=UINT64_C(14695981039346656037);
    for (unsigned i=0;i<width*64;i++) hash=(hash^blocks[i])*UINT64_C(1099511628211);
    return hash==expected;
}

void fight_trax_init(const char *root) {
    const char *paths[]={"LOCALE/xloc_fe.eus","locale/xloc_fe.eus","LOCALE/XLOC_FE.EUS"};
    char path[1024];
    FILE *file=NULL;
    for(unsigned i=0;i<sizeof paths/sizeof *paths && !file;i++)
        if(snprintf(path,sizeof path,"%s/%s",root,paths[i])<(int)sizeof path)file=fopen(path,"rb");
    if(!file)return;
    long len=-1;
    if(!fseek(file,0,SEEK_END))len=ftell(file);
    rewind(file);
    uint8_t *data=len>=48 && len<=4*1024*1024?malloc((size_t)len):NULL;
    int good=data && fread(data,1,(size_t)len,file)==(size_t)len;
    fclose(file);
    if(good){
        size_t length=(size_t)len;
        for(size_t at=0;at+16<=length;at++){
            if(memcmp(data+at,"LOCI",4))continue;
            uint32_t span=load32(data+at+4),entries=load32(data+at+8);
            if(!entries || 16ULL+4ULL*entries>span || (uint64_t)at+span+16>length)continue;
            size_t strings=at+span;
            if(memcmp(data+strings,"LOCL",4) || load32(data+strings+12)!=entries)continue;
            uint32_t size=load32(data+strings+4);
            if(size<16ULL+entries*4ULL || size>length-strings)continue;
            index_at=at+16;strings_at=strings;strings_length=size;count=entries;locale=data;return;
        }
    }
    free(data);
}

/* The title passes localization IDs to both of its EA Trax show methods.
 * Resolve the exact selected artist/title, including the disc's punctuation. */
static int track_field(uint32_t address,char output[513]) {
    uint32_t offset=address;
    size_t limit=g_xbox_map_size?g_xbox_map_size:g_xbox_total_ram;
    if(address>=0x80000000u && address-0x80000000u<g_xbox_total_ram){offset=address-0x80000000u;limit=g_xbox_total_ram;}
    if(!locale || offset<0x10000 || (uint64_t)offset+7>limit)return 0;
    const char *id=XBOX_HOST(address);
    if(id[0]!='$')return 0;
    unsigned key=0,i=1;
    while(i<=5 && id[i]>='0' && id[i]<='9'){key=key*10+id[i]-'0';i++;}
    if(i==1 || id[i] || key>65535)return 0;
    unsigned lo=0,hi=count;
    while(lo<hi){
        unsigned mid=lo+(hi-lo)/2;
        if(load16(locale+index_at+4*mid)<key)lo=mid+1;else hi=mid;
    }
    if(lo==count || load16(locale+index_at+4*lo)!=key)return 0;
    unsigned row=load16(locale+index_at+4*lo+2);
    if(row>=count)return 0;
    size_t at=load32(locale+strings_at+16+4*row);
    if(at<16+4*count || (at&1))return 0;
    static const char hex[]="0123456789ABCDEF";
    for(i=0;i<=128 && at+2<=strings_length;i++,at+=2){
        unsigned ch=load16(locale+strings_at+at);
        if(!ch){output[i*4]=0;return i>0;}
        if(ch<32 || i==128)return 0;
        for(unsigned j=0;j<4;j++)output[i*4+j]=hex[(ch>>(12-4*j))&15];
    }
    return 0;
}

int fight_trax_present(uint32_t artist,uint32_t title) {
    char first[513],second[513];
    if(!track_field(artist,first) || !track_field(title,second))return 0;
    printf("FNR3/1 TRAX %s %s\n",second,first);
    fflush(stdout);
    return atomic_load(&lower_music);
}
