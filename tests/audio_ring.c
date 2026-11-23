#include "../native/audio_ring.h"
#include <assert.h>
#include <stdio.h>
#include <stdlib.h>
int main(void){
    static AudioRing q={.prime=PRIME_FRAMES};
    static int16_t in[2*RING_FRAMES],out[2*RING_FRAMES];
    q.startup.frames=STARTUP_QUIET+STARTUP_FADE;
    for(int i=0;i<2*RING_FRAMES;i+=2){in[i]=24000;in[i+1]=-24000;}
    assert(ring_write(&q,in,256));ring_read(&q,out,192);
    assert(!out[0] && !q.playing && !q.tail);
    assert(ring_write(&q,in,PRIME_FRAMES-256));ring_read(&q,out,PRIME_FRAMES+17);
    assert(out[500]==24000 && out[501]==-24000 && !out[2*PRIME_FRAMES-2]);
    for(int i=2;i<(PRIME_FRAMES+17)*2;i++)assert(abs(out[i]-out[i-2])<=125);
    assert(q.underruns==1 && q.playing && q.prime==PRIME_FRAMES);
    /* The Thor supplies 960-frame bursts. A single late impact block used to
       mute three more complete callbacks while the queue re-primed. */
    assert(ring_write(&q,in,960));ring_read(&q,out,960);
    assert(out[2*480]==24000 && q.tail==PRIME_FRAMES+960);
    assert(ring_write(&q,in,959));ring_read(&q,out,960);
    assert(q.playing && out[2*959]==0);
    assert(ring_write(&q,in,959));ring_read(&q,out,960);
    assert(!q.playing && q.prime>PRIME_FRAMES);
    assert(ring_write(&q,in,RING_FRAMES));assert(!ring_write(&q,in,256));assert(q.drops==1);
    ring_read(&q,out,512);
    for(int i=0;i<512;i++)in[i]=-in[i];
    assert(ring_write(&q,in,256));
    ring_read(&q,out,RING_FRAMES-512+256);
    unsigned boundary=2*(RING_FRAMES-512);
    for(unsigned i=boundary;i<boundary+2*FADE_FRAMES;i++)assert(abs(out[i]-out[i-2])<=400);
    atomic_store(&q.head,UINT32_MAX-127);atomic_store(&q.tail,UINT32_MAX-127);
    assert(ring_write(&q,in,256));assert(q.head-q.tail==256);
    puts("AAudio ring: startup, callback starvation, reprime, overflow crossfade and counter wrap passed");
}
