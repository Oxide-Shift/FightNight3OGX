#ifndef FIGHT_AUDIO_RING_H
#define FIGHT_AUDIO_RING_H
#include <stdatomic.h>
#include <stdint.h>
#include <string.h>
#include "audio_startup.h"

enum { RING_FRAMES=16384, FADE_FRAMES=192, PRIME_FRAMES=2560 };
typedef struct {
    int16_t pcm[RING_FRAMES][2];
    atomic_uint head,tail,underruns,drops,refills;
    unsigned prime,gain,stable,shortCallbacks;
    int playing;
    unsigned crossfade;
    int16_t last[2];
    int16_t last_read[2]; /* Consumer-owned sample for a short fade on starvation. */
    AudioStartup startup;
} AudioRing;

/* One mixer producer, one real-time consumer. Storage gets published only
   after a complete stereo block has been written. */
static inline int ring_write(AudioRing *q,const int16_t *src,unsigned frames) {
    unsigned w=atomic_load_explicit(&q->head,memory_order_relaxed);
    unsigned used=w-atomic_load_explicit(&q->tail,memory_order_acquire);
    if(!frames || !src)return 0;
    if(frames>RING_FRAMES-used){
        atomic_fetch_add(&q->drops,1);q->crossfade=FADE_FRAMES;return 0;
    }
    for(unsigned i=0;i<frames;i++){
        int16_t *sample=q->pcm[(w+i)&(RING_FRAMES-1)];
        audio_startup_frame(&q->startup,src[i*2],src[i*2+1],sample);
        if(q->crossfade){
            int blend=(int)q->crossfade;
            for(int ch=0;ch<2;ch++)sample[ch]=(int16_t)((sample[ch]*(FADE_FRAMES-blend)+q->last[ch]*blend)/FADE_FRAMES);
            if(!--q->crossfade)memcpy(q->last,sample,sizeof q->last);
        }
    }
    if(!q->crossfade)memcpy(q->last,q->pcm[(w+frames-1)&(RING_FRAMES-1)],sizeof q->last);
    atomic_store_explicit(&q->head,w+frames,memory_order_release);
    return 1;
}

/* After a material scheduling stall, recover only useful audio reserve. Four 256-frame
   blocks give the consumer 21 ms while the game and mixer resume ordinary pacing. */
static inline int64_t ring_recover_clock(unsigned queued,int64_t now,int64_t period){
    unsigned missing=queued<PRIME_FRAMES ? PRIME_FRAMES-queued : 0;
    unsigned blocks=(missing+255)/256;
    if(blocks>4)blocks=4;
    return now-(int64_t)blocks*period;
}

static inline void ring_read(AudioRing *q,int16_t *out,unsigned frames){
    unsigned r=atomic_load_explicit(&q->tail,memory_order_relaxed);
    unsigned available=atomic_load_explicit(&q->head,memory_order_acquire)-r;
    memset(out,0,(size_t)frames*2*sizeof(*out));
    if(!q->playing){
        if(available<q->prime){
            for(unsigned i=0;i<frames && q->gain;i++){
                --q->gain;
                for(int ch=0;ch<2;ch++)out[2*i+ch]=(int16_t)(q->last_read[ch]*(int)q->gain/FADE_FRAMES);
            }
            return;
        }
        q->playing=1;q->gain=0;atomic_fetch_add(&q->refills,1);
    }
    unsigned count=available<frames?available:frames;
    for(unsigned i=0;i<count;i++){
        const int16_t *pair=q->pcm[(r+i)&(RING_FRAMES-1)];
        if(count<frames && count-i<=FADE_FRAMES){if(q->gain)--q->gain;}
        else if(q->gain<FADE_FRAMES)++q->gain;
        out[2*i]=(int16_t)(pair[0]*(int)q->gain/FADE_FRAMES);
        out[2*i+1]=(int16_t)(pair[1]*(int)q->gain/FADE_FRAMES);
    }
    if(count)memcpy(q->last_read,q->pcm[(r+count-1)&(RING_FRAMES-1)],sizeof q->last_read);
    for(unsigned i=count;i<frames && q->gain;i++){
        --q->gain;
        for(int ch=0;ch<2;ch++)out[2*i+ch]=(int16_t)(q->last_read[ch]*(int)q->gain/FADE_FRAMES);
    }
    atomic_store_explicit(&q->tail,r+count,memory_order_release);
    if(count<frames){
        /* Use the next burst immediately after an isolated short callback.
           Rebuild reserve after consecutive shortages or complete starvation. */
        if(++q->shortCallbacks>=2 || count==0){
            q->playing=0;
            q->shortCallbacks=0;
            if(q->prime<4608)q->prime+=512;
        }
        q->stable=0;atomic_fetch_add(&q->underruns,1);
    }else{
        q->shortCallbacks=0;
        if((q->stable+=count)<48000*10)return;
        if(q->prime>PRIME_FRAMES)q->prime-=256;
        q->stable=0;
    }
}
#endif
