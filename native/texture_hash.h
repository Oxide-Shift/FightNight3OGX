#ifndef FIGHT_TEXTURE_HASH_H
#define FIGHT_TEXTURE_HASH_H
#include <stdint.h>
#include <string.h>

/* Four independent lanes let an ARM core process successive words in parallel.
 * Include the entire tail, including textures smaller than a machine word. */
static inline uint64_t fight_texture_hash(const unsigned char *p, size_t size, uint64_t seed) {
    const uint64_t prime = UINT64_C(0x9e3779b185ebca87);
    uint64_t a = seed ^ size, b = ~seed, c = prime, d = size;
    while (size >= 32) {
        uint64_t words[4];
        memcpy(words,p,32);
        a = (a ^ words[0]) * prime; b = (b ^ words[1]) * prime;
        c = (c ^ words[2]) * prime; d = (d ^ words[3]) * prime;
        p += 32; size -= 32;
    }
    uint64_t h = (((a ^ (b >> 31 | b << 33)) * prime + c) * prime) ^ d;
    while (size >= 8) {
        uint64_t word; memcpy(&word,p,8);
        h = (h ^ word) * prime; p += 8; size -= 8;
    }
    while (size--) h = (h ^ *p++) * prime;
    return h ^ (h >> 31);
}
/* Large textures are rechecked in full every frame. Use four independent
 * AES mixing lanes where supported. Keys are process-local texture IDs. */
#if defined(__aarch64__) && defined(__ANDROID__)
#include <arm_neon.h>
#include <asm/hwcap.h>
#include <sys/auxv.h>
typedef uint8x16_t FightHashLane;
#define FIGHT_HASH_FAST __attribute__((target("aes")))
#define FIGHT_HASH_LOAD(p) vld1q_u8((const uint8_t *)(p))
#define FIGHT_HASH_STEP(x,k) veorq_u8(vaesmcq_u8(vaeseq_u8((x),vdupq_n_u8(0))),(k))
#define FIGHT_HASH_STORE(p,x) vst1q_u8((uint8_t *)(p),(x))
static inline int fight_has_fast_hash(void) {
    static int available=-1;
    if(available<0)available=(getauxval(AT_HWCAP)&HWCAP_AES)!=0;
    return available;
}
#elif defined(__x86_64__) && !defined(__ANDROID__)
#include <wmmintrin.h>
typedef __m128i FightHashLane;
#define FIGHT_HASH_FAST __attribute__((target("aes")))
#define FIGHT_HASH_LOAD(p) _mm_loadu_si128((const __m128i *)(p))
#define FIGHT_HASH_STEP(x,k) _mm_aesenc_si128((x),(k))
#define FIGHT_HASH_STORE(p,x) _mm_storeu_si128((__m128i *)(p),(x))
static inline int fight_has_fast_hash(void) {return __builtin_cpu_supports("aes");}
#endif
#ifdef FIGHT_HASH_FAST
static FIGHT_HASH_FAST uint64_t fight_wide_hash(const uint8_t *p,size_t size,uint64_t seed) {
    uint64_t salt[2]={seed^UINT64_C(0x9e3779b185ebca87),size^UINT64_C(0x165667b19e3779f9)};
    FightHashLane key=FIGHT_HASH_LOAD(salt);
    FightHashLane a=key,b=FIGHT_HASH_STEP(a,key),c=FIGHT_HASH_STEP(b,key),d=FIGHT_HASH_STEP(c,key);
    while(size>=64){
        a=FIGHT_HASH_STEP(a,FIGHT_HASH_LOAD(p));b=FIGHT_HASH_STEP(b,FIGHT_HASH_LOAD(p+16));
        c=FIGHT_HASH_STEP(c,FIGHT_HASH_LOAD(p+32));d=FIGHT_HASH_STEP(d,FIGHT_HASH_LOAD(p+48));
        p+=64;size-=64;
    }
    a=FIGHT_HASH_STEP(FIGHT_HASH_STEP(FIGHT_HASH_STEP(a,b),c),d);
    while(size>=16){a=FIGHT_HASH_STEP(a,FIGHT_HASH_LOAD(p));p+=16;size-=16;}
    uint8_t tail[16]={0};
    memcpy(tail,p,size);
    a=FIGHT_HASH_STEP(FIGHT_HASH_STEP(FIGHT_HASH_STEP(a,FIGHT_HASH_LOAD(tail)),key),key);
    uint64_t out[2];FIGHT_HASH_STORE(out,a);
    return out[0]^(out[1]>>31|out[1]<<33);
}
#endif
static inline uint64_t fight_checked_texture(const uint8_t *p,size_t bytes,uint64_t seed) {
#ifdef FIGHT_HASH_FAST
    if(bytes>=256 && fight_has_fast_hash())return fight_wide_hash(p,bytes,seed);
#endif
    return fight_texture_hash(p,bytes,seed);
}

#endif
