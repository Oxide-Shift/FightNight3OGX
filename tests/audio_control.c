#include <assert.h>
#include <stdatomic.h>
#include <time.h>
#include "../native/audio_control.h"

static atomic_int waking,release_audio,ticket_seen,checks;
static void wait_for(atomic_int *value,int target){
    struct timespec delay={0,1000000};
    for(int n=0;n<3000 && atomic_load(value)!=target;n++)nanosleep(&delay,NULL);
    assert(atomic_load(value)==target);
}
void fight_audio_sleep(int sleeping){
    if(!sleeping){atomic_store(&waking,1);wait_for(&release_audio,1);}
}
void fight_audio_check_route(void){atomic_fetch_add(&checks,1);}
static void audio_parked(char ticket){atomic_store(&ticket_seen,ticket);}

int main(void){
    queue_audio_control(1,'A');wait_for(&ticket_seen,'A');
    queue_audio_control(0,0);wait_for(&waking,1);
    // Hold the simulated Android audio service inside openStream. The command
    // reader must remain available for the next controller packet and lid cycle.
    for(int i=0;i<1000;i++)queue_audio_control(-1,0);
    queue_audio_control(1,'B');
    assert(atomic_load(&ticket_seen)=='A');
    atomic_store(&release_audio,1);wait_for(&ticket_seen,'B');
    queue_audio_control(0,0);queue_audio_control(-1,0);wait_for(&checks,1);
    puts("Blocked audio reopen kept the command reader available and acknowledged the next lid cycle");
}
