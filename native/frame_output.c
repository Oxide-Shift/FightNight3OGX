/* Three published BGRA slots for Android. The legacy desktop capture format
   keeps a single RGBA image guarded by an even sequence number. */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdatomic.h>
#include <string.h>
#include "xbox_memory_layout.h"
#ifdef _WIN32
#include <windows.h>
#else
#include <fcntl.h>
#include <unistd.h>
#include <sys/mman.h>
#endif
#ifdef __ANDROID__
#include <limits.h>
#include <linux/futex.h>
#include <sys/syscall.h>
#include "android_scanout.h"
#endif

enum { WIDTH = 640, HEIGHT = 480, HEADER = 16, BYTES = HEADER + WIDTH*HEIGHT*4 };
static uint32_t *surface;
static atomic_flag busy = ATOMIC_FLAG_INIT;
/* Android uses three BGRA slots. Width and height live in each slot, since
   software menus and a higher-resolution Vulkan fight can share a session. */
static unsigned slot_bytes, published;
static uint32_t pending_address;
static int pending,published_high;
#ifdef __ANDROID__
extern void fight_shared_finish(unsigned,uint32_t);
#else
static void fight_shared_finish(unsigned token,uint32_t serial){(void)token;(void)serial;}
#endif
static unsigned pending_gpu;
/* Owned by a Vulkan readback slot until frame_output_forget_native returns. */
static const uint8_t *native_reference;

static uint32_t physical_address(uint32_t address) {
    if (address >= 0xf0000000u) return address - 0xf0000000u;
    if (address >= 0x80000000u) return address - 0x80000000u;
    return address;
}

void frame_output_init(void) {
    const char *path = getenv("FNR3_FRAME_PATH");
    if (!path || !*path) return;
    size_t bytes = BYTES;
#ifndef __ANDROID__
    if(getenv("FNR3_FRAME_SLOTS"))
#endif
    {
    const char *requested=getenv("RECOMP_RES");
    int h=requested?atoi(requested):480;
    if(h<480 || h>1080)h=480;
    slot_bytes = (unsigned)(h*4/3)*h*4+8;
    bytes = 16 + (size_t)3*slot_bytes;
    }
#ifdef _WIN32
    HANDLE file = CreateFileA(path, GENERIC_READ|GENERIC_WRITE, FILE_SHARE_READ|FILE_SHARE_WRITE,
                              NULL, CREATE_ALWAYS, FILE_ATTRIBUTE_NORMAL, NULL);
    if (file == INVALID_HANDLE_VALUE) return;
    HANDLE map = CreateFileMappingW(file,NULL,PAGE_READWRITE,0,(DWORD)bytes,NULL);
    if (map) {surface = MapViewOfFile(map,FILE_MAP_WRITE,0,0,bytes); CloseHandle(map);}
    CloseHandle(file);
#else
#ifdef __ANDROID__
    surface=android_scanout(path,bytes);
    if(!surface)
#endif
    {
    int fd = open(path,O_CREAT|O_TRUNC|O_RDWR,0600);
    if (fd < 0) return;
    if (ftruncate(fd,bytes)==0) {
        void *map = mmap(NULL,bytes,PROT_READ|PROT_WRITE,MAP_SHARED,fd,0);
        if (map != MAP_FAILED) surface=map;
    }
    close(fd);
    }
#endif
    if (!surface) {fprintf(stderr,"[Fight Night] cannot map presentation surface\n");return;}
    if(slot_bytes){surface[1]=slot_bytes;surface[2]=3;surface[3]=0x32425246;}
    else{surface[1]=WIDTH;surface[2]=HEIGHT;surface[3]=0x31425246;}
}

/* Write the next slot without exposing it to readers until the Xbox flips. */
static int stage(const uint8_t *src, unsigned w, unsigned h, unsigned bpp) {
    if(!surface || !w || !h || (uint64_t)w*h*4+8>slot_bytes)return 0;
    unsigned seq=published+1;
    if(!seq)seq=1;
    uint32_t *slot=(uint32_t*)((uint8_t*)surface+16+(size_t)(seq%3)*slot_bytes);
    slot[0]=w;slot[1]=h;
    if(bpp==4) memcpy(slot+2,src,(size_t)w*h*4);
    else {
        uint32_t *out=slot+2;
        for(unsigned i=0;i<w*h;i++){
            uint16_t v;memcpy(&v,src+2*i,2);
            out[i]=0xff000000u|(((v>>11)*255/31)<<16)|((((v>>5)&63)*255/63)<<8)|((v&31)*255/31);
        }
    }
    return 1;
}
int frame_output_high_token(const uint8_t *bgra,unsigned w,unsigned h,uint32_t address,const uint8_t *native_pixels,unsigned gpu_token) {
    if(!slot_bytes || atomic_flag_test_and_set_explicit(&busy,memory_order_acquire))return 0;
    fight_shared_finish(pending_gpu,0);pending_gpu=0;
    int staged=stage(bgra,w,h,4);
    if(staged && gpu_token){
        unsigned next=published+1;if(!next)next=1;
        uint32_t *slot=(uint32_t*)((uint8_t*)surface+16+(size_t)(next%3)*slot_bytes);
        slot[0]=w|(gpu_token<<16);pending_gpu=gpu_token;
    }
    pending=staged;published_high=0;
    pending_address=physical_address(address);
    native_reference=staged?native_pixels:NULL;
    atomic_flag_clear_explicit(&busy,memory_order_release);
    return staged;
}
int frame_output_high(const uint8_t *pixels,unsigned w,unsigned h,uint32_t address,const uint8_t *native) {
    return frame_output_high_token(pixels,w,h,address,native,0);
}
void frame_output_forget_native(void) {
    while(atomic_flag_test_and_set_explicit(&busy,memory_order_acquire)){}
    native_reference=NULL;
    atomic_flag_clear_explicit(&busy,memory_order_release);
}
void frame_output_present(uint32_t address, uint32_t pitch) {
    if (!surface || (pitch!=WIDTH*2 && pitch!=WIDTH*4)) return;
    uint32_t physical = physical_address(address);
    if (physical < 0x10000 || (uint64_t)physical+pitch*HEIGHT>g_xbox_total_ram) return;
    if(slot_bytes){
        if(atomic_flag_test_and_set_explicit(&busy,memory_order_acquire))return;
        int high=pending && physical==pending_address;
        /* A held upscale otherwise alternates with its 480-line guest copy.
           A software overlay at the same address must still be published. */
        if(!pending && published_high && native_reference && physical==pending_address &&
                pitch==WIDTH*4 && !memcmp(XBOX_HOST(address),native_reference,WIDTH*HEIGHT*4)){
            atomic_flag_clear_explicit(&busy,memory_order_release);
            return;
        }
        if(high || stage(XBOX_HOST(address),WIDTH,HEIGHT,pitch/WIDTH)){
            published_high=high;
            if(++published==0)published=1;
            fight_shared_finish(pending_gpu,high?published:0);pending_gpu=0;
            atomic_store_explicit((_Atomic uint32_t*)surface,published,memory_order_release);
#ifdef __ANDROID__
            syscall(SYS_futex,surface,FUTEX_WAKE,INT_MAX,NULL,NULL,0);
#endif
        }
        pending=0;
        atomic_flag_clear_explicit(&busy,memory_order_release);
        return;
    }
    if (atomic_flag_test_and_set(&busy)) return;
    _Atomic uint32_t *seq=(_Atomic uint32_t *)surface;
    atomic_fetch_add(seq,1);
    const uint8_t *src=XBOX_HOST(address);
    uint8_t *out=(uint8_t*)surface+HEADER;
    for (int i=0;i<WIDTH*HEIGHT;i++,out+=4) {
        if (pitch==WIDTH*4) {
            out[0]=src[2];out[1]=src[1];out[2]=src[0];src+=4;
        } else {
            uint16_t v=src[0]|((uint16_t)src[1]<<8);
            out[0]=((v>>11)&31)*255/31;out[1]=((v>>5)&63)*255/63;out[2]=(v&31)*255/31;src+=2;
        }
        out[3]=255;
    }
    atomic_fetch_add(seq,1);
    atomic_flag_clear(&busy);
}
