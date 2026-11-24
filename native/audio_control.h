#ifndef FNR3_AUDIO_CONTROL_H
#define FNR3_AUDIO_CONTROL_H
#include <pthread.h>
#include <stdio.h>

/* Android can block in AAudioStream_openStream or close when the lid changes the
 * audio route. The command reader must still deliver PAD and answer heartbeat. */
extern void fight_audio_sleep(int sleeping),fight_audio_check_route(void);
static void audio_parked(char ticket);
static pthread_mutex_t control_lock=PTHREAD_MUTEX_INITIALIZER;
static pthread_cond_t control_ready=PTHREAD_COND_INITIALIZER;
static int control_started,pending_power=-1,check_route;
static char park_ticket;

static void *service_audio_control(void *unused){
    (void)unused;
    for(;;){
        pthread_mutex_lock(&control_lock);
        while(pending_power<0 && !check_route)pthread_cond_wait(&control_ready,&control_lock);
        int power=pending_power,check=check_route;
        char ticket=park_ticket;
        pending_power=-1;check_route=0;
        pthread_mutex_unlock(&control_lock);
        if(power>=0){
            fight_audio_sleep(power);
            if(power && ticket)audio_parked(ticket);
        }
        if(check && power!=1)fight_audio_check_route();
    }
    return NULL;
}

/* A newer lid state supersedes one that the worker has not yet started.
 * The supervisor matches the acknowledgement against the current sleep ticket. */
static void queue_audio_control(int power,char ticket){
    pthread_mutex_lock(&control_lock);
    if(power<0)check_route=1;
    else{pending_power=power;park_ticket=ticket;}
    if(!control_started){
        pthread_t thread;
        if(pthread_create(&thread,NULL,service_audio_control,NULL)==0){
            pthread_detach(thread);control_started=1;
        }else fprintf(stderr,"[Fight Night] audio control worker unavailable\n");
    }
    pthread_cond_signal(&control_ready);
    pthread_mutex_unlock(&control_lock);
}
#endif
