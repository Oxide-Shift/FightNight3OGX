/* Frame timings from the thread completing the Xbox flip. The deliberate
 * NTSC pacing sleep is excluded from work; a Vulkan fence wait is included. */
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include "fight_data.h"
#ifdef _WIN32
#include <windows.h>
#else
#include <time.h>
#endif

int64_t fight_now_ns(void) {
#ifdef _WIN32
    static LARGE_INTEGER frequency;
    LARGE_INTEGER tick;
    if(!frequency.QuadPart)QueryPerformanceFrequency(&frequency);
    QueryPerformanceCounter(&tick);
    return tick.QuadPart/frequency.QuadPart*1000000000LL +
           tick.QuadPart%frequency.QuadPart*1000000000LL/frequency.QuadPart;
#else
    struct timespec ts;
    clock_gettime(CLOCK_MONOTONIC,&ts);
    return ts.tv_sec*1000000000LL+ts.tv_nsec;
#endif
}
static int64_t fence_ns,submit_ns,copy_ns;
void fight_note_scanout(int64_t submit,int64_t copy){submit_ns+=submit;copy_ns+=copy;}
void fight_note_gpu_wait(int64_t duration){fence_ns+=duration;}

enum { WINDOW=360 };
struct Sample {unsigned work,wait,interval,exchange;};
static struct Sample samples[WINDOW];
static unsigned pos,count,until_report,tail,quiet_ticks;
static uint32_t previous_bout,last_thrown,last_landed,last_tick;
static int ordered(const void *a,const void *b) {
    unsigned x=*(const unsigned *)a,y=*(const unsigned *)b;
    return (x>y)-(x<y);
}
static unsigned percentile(unsigned *v,unsigned n){
    if(!n)return 0;
    qsort(v,n,sizeof *v,ordered);
    return v[(95*n-1)/100];
}
static void report(void){
    unsigned work[WINDOW],wait[WINDOW],rest[WINDOW],interval[WINDOW],ne=0,nr=0;
    for(unsigned i=0;i<count;i++){
        const struct Sample *s=&samples[i];
        if(s->exchange){work[ne]=s->work;wait[ne]=s->wait;interval[ne++]=s->interval;}
        else rest[nr++]=s->work;
    }
    if(ne>=6){
        printf("FNR3/1 PACE %u %u %u %u %u %u\n",ne,percentile(work,ne),percentile(wait,ne),
               percentile(interval,ne),nr,percentile(rest,nr));fflush(stdout);
    }
}
void fight_timing_flip(int64_t before,int64_t after,int64_t end) {
    static int64_t previous;
    int64_t work=before-previous+end-after,interval=end-previous,wait=fence_ns;
#ifdef __ANDROID__
    static int64_t last_warning,previous_cpu;
    struct timespec cpu;
    clock_gettime(CLOCK_THREAD_CPUTIME_ID,&cpu);
    int64_t cpu_now=(int64_t)cpu.tv_sec*1000000000+cpu.tv_nsec;
    if(previous && interval>100000000 && end-last_warning>=1000000000){
        extern void fight_audio_hitch_stats(char *,size_t);
        char audio[128];fight_audio_hitch_stats(audio,sizeof audio);
        fprintf(stderr,"[Fight Night] slow flip interval_ms=%lld guest_ms=%lld present_ms=%lld pace_ms=%lld thread_cpu_ms=%lld submit_ms=%lld fence_ms=%lld copy_ms=%lld %s\n",
                (long long)(interval/1000000),(long long)((before-previous)/1000000),
                (long long)((end-after)/1000000),(long long)((after-before)/1000000),
                (long long)((cpu_now-previous_cpu)/1000000),(long long)(submit_ns/1000000),
                (long long)(wait/1000000),(long long)(copy_ns/1000000),audio);
        last_warning=end;
    }
    previous_cpu=cpu_now;
#endif
    previous=end;fence_ns=submit_ns=copy_ns=0;
    uint32_t bout,tick,thrown,landed;
    if(!fight_sample_counters(&bout,&tick,&thrown,&landed)){
        previous_bout=0;return;
    }
    if(previous_bout!=bout || thrown<last_thrown || landed<last_landed){
        pos=count=until_report=tail=quiet_ticks=0;
        previous_bout=bout;last_tick=tick;last_thrown=thrown;last_landed=landed;return;
    }
    int event=thrown!=last_thrown || landed!=last_landed;
    last_thrown=thrown;last_landed=landed;
    if(tick==last_tick && !event){
        if(++quiet_ticks==4)report();
        if(quiet_ticks>=4)return;
    }else quiet_ticks=0;
    last_tick=tick;
    if(interval<=0 || interval>2000000000LL || work<0)return;
    if(event){
        for(unsigned k=1;k<=3 && k<=count;k++)samples[(pos+WINDOW-k)%WINDOW].exchange=1;
        tail=6;
    }
    samples[pos]=(struct Sample){(unsigned)(work/1000),(unsigned)(wait/1000),(unsigned)(interval/1000),tail!=0};
    if(tail)tail--;
    pos=(pos+1)%WINDOW;if(count<WINDOW)count++;
    if(++until_report>=60){until_report=0;report();}
}
