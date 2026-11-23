#include <aaudio/AAudio.h>
#include <stdio.h>
#include <pthread.h>
#include "audio_ring.h"

static AAudioStream *stream;
static pthread_mutex_t output_lock=PTHREAD_MUTEX_INITIALIZER;
static int sleeping,requested,retry_pings;
static atomic_int reopen,stream_error;
static void output_error(AAudioStream *source,void *user,aaudio_result_t error){
    (void)source;(void)user;
    atomic_store(&stream_error,error);
}
static AudioRing queue={.prime=PRIME_FRAMES};
static atomic_int device_xruns, online;
static atomic_uint clock_recoveries;
static unsigned submitted,old_under,old_drops,old_refills;

static aaudio_data_callback_result_t render(AAudioStream *source,void *user,void *dest,int32_t frames){
    (void)source;(void)user;
    if(frames>0)ring_read(&queue,dest,(unsigned)frames);
    return AAUDIO_CALLBACK_RESULT_CONTINUE;
}
static int open_output(void){
    if(stream)return 1;
    AAudioStreamBuilder *builder=NULL;
    if(AAudio_createStreamBuilder(&builder)!=AAUDIO_OK)return 0;
    AAudioStreamBuilder_setDirection(builder,AAUDIO_DIRECTION_OUTPUT);
    AAudioStreamBuilder_setFormat(builder,AAUDIO_FORMAT_PCM_I16);
    AAudioStreamBuilder_setChannelCount(builder,2);
    AAudioStreamBuilder_setSampleRate(builder,48000);
    AAudioStreamBuilder_setUsage(builder,AAUDIO_USAGE_GAME);
    AAudioStreamBuilder_setPerformanceMode(builder,AAUDIO_PERFORMANCE_MODE_LOW_LATENCY);
    AAudioStreamBuilder_setErrorCallback(builder,output_error,NULL);
    AAudioStreamBuilder_setDataCallback(builder,render,NULL);
    aaudio_result_t result=AAudioStreamBuilder_openStream(builder,&stream);
    AAudioStreamBuilder_delete(builder);
    if(result!=AAUDIO_OK)return 0;
    int burst=AAudioStream_getFramesPerBurst(stream);
    if(AAudioStream_getSampleRate(stream)==48000 && AAudioStream_getChannelCount(stream)==2 &&
       AAudioStream_getFormat(stream)==AAUDIO_FORMAT_PCM_I16){
        if(burst>0 && burst<=2048)AAudioStream_setBufferSizeInFrames(stream,burst*4);
        if(AAudioStream_requestStart(stream)==AAUDIO_OK){atomic_store(&online,1);return 1;}
    }
    AAudioStream_close(stream);stream=NULL;return 0;
}
static void close_output(void){
    atomic_store(&online,0);
    if(stream){
        AAudioStream_requestStop(stream);
        AAudioStream_close(stream);
        stream=NULL;
    }
    atomic_store(&stream_error,AAUDIO_OK);
    /* No callback or producer can now access the sample storage. */
    atomic_store(&queue.tail,atomic_load(&queue.head));
    queue.prime=PRIME_FRAMES;queue.gain=queue.stable=queue.shortCallbacks=queue.crossfade=0;
    queue.playing=0;
    submitted=0;
}
int xa2_init(void){
    pthread_mutex_lock(&output_lock);requested=1;
    int ok=sleeping || open_output();
    atomic_store(&reopen,!ok);
    pthread_mutex_unlock(&output_lock);return ok;
}
void xa2_shutdown(void){
    pthread_mutex_lock(&output_lock);requested=0;atomic_store(&reopen,0);close_output();
    queue.startup=(AudioStartup){0};pthread_mutex_unlock(&output_lock);
}
void fight_audio_sleep(int value){
    pthread_mutex_lock(&output_lock);
    if(value!=sleeping){
        sleeping=value;
        atomic_store(&reopen,0);
        retry_pings=0;
        if(sleeping)close_output();
        else if(requested && !open_output())atomic_store(&reopen,1);
    }
    pthread_mutex_unlock(&output_lock);
}
/* Some routes become available shortly after unlock. Retry off the game and
 * audio threads, using the existing foreground heartbeat. */
void fight_audio_check_route(void){
    if(!atomic_load(&reopen) && atomic_load(&stream_error)==AAUDIO_OK)return;
    pthread_mutex_lock(&output_lock);
    int error=atomic_load(&stream_error);
    if(stream && error!=AAUDIO_OK){
        fprintf(stderr,"[Fight Night] audio stream error %d; reopening output\n",(int)error);
        close_output();retry_pings=3;atomic_store(&reopen,1);
    }
    if(requested && !sleeping && !stream && ++retry_pings>=4){
        retry_pings=0;
        if(open_output())atomic_store(&reopen,0);
    }
    pthread_mutex_unlock(&output_lock);
}
int xa2_is_active(void){return atomic_load(&online);}
int xa2_submit_samples(const int16_t *pcm,int frames){
    if(frames<=0 || !atomic_load(&online))return 0;
    pthread_mutex_lock(&output_lock);
    int ok=stream && ring_write(&queue,pcm,(unsigned)frames);
    if(ok && (submitted+=(unsigned)frames)>=48000){
        atomic_store(&device_xruns,AAudioStream_getXRunCount(stream));submitted=0;
    }
    pthread_mutex_unlock(&output_lock);
    return ok;
}
int64_t fight_audio_recover_clock(int64_t now,int64_t period){
    atomic_fetch_add(&clock_recoveries,1);
    unsigned w=atomic_load_explicit(&queue.head,memory_order_acquire);
    unsigned r=atomic_load_explicit(&queue.tail,memory_order_acquire);
    return ring_recover_clock(w-r,now,period);
}
void fight_audio_hitch_stats(char *out,size_t size){
    unsigned r=atomic_load_explicit(&queue.tail,memory_order_acquire);
    unsigned w=atomic_load_explicit(&queue.head,memory_order_acquire);
    snprintf(out,size,"queued_frames=%u gaps=%u drops=%u xruns=%d clock_recoveries=%u",
             w-r,atomic_load(&queue.underruns),atomic_load(&queue.drops),
             atomic_load(&device_xruns),atomic_load(&clock_recoveries));
}
int xa2_get_buffer_size(void){return 256;}
int xa2_channels(void){return 2;}
int xa2_queued(void){
    unsigned r=atomic_load_explicit(&queue.tail,memory_order_acquire);
    return (int)((atomic_load_explicit(&queue.head,memory_order_acquire)-r)/256);
}
void xa2_surround_push(const int16_t *samples,int count){(void)samples;(void)count;}
void fight_audio_report(void){
    if(!atomic_load(&online)){fprintf(stderr,"[Fight Night] audio output unavailable\n");return;}
    fprintf(stderr,"[Fight Night] audio gaps %u drops %u xruns %d\n",atomic_load(&queue.underruns),
            atomic_load(&queue.drops),atomic_load(&device_xruns));
}
void xa2_take_stats(int *under,int *drop,int *min,int *waits,int *refill){
    unsigned u=atomic_load(&queue.underruns),d=atomic_load(&queue.drops),f=atomic_load(&queue.refills);
    if(under)*under=(int)(u-old_under);
    if(drop)*drop=(int)(d-old_drops);
    if(refill)*refill=(int)(f-old_refills);
    if(min)*min=xa2_queued();
    if(waits)*waits=0;
    old_under=u;old_drops=d;old_refills=f;
}
