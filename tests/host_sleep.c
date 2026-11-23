/* Android/Linux integration test. Build with corner_pipe.c and host_sleep.c.
 * The child runs real native threads behind the launcher's command protocol. */
#include <assert.h>
#include <inttypes.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

int corner_connect(void);
int64_t fight_guest_ns(void);
static struct probe {
    atomic_uint work, audio, pads;
    atomic_int active_audio;
    _Atomic int64_t game_time;
} *probe;
void fight_audio_check_route(void){}
void fight_audio_sleep(int sleep){atomic_store(&probe->active_audio,!sleep);}
void fight_trax_route(int route){(void)route;}
int pad_message(const char *s){(void)s;atomic_fetch_add(&probe->pads,1);return 1;}
static void send_cmd(FILE *f,const char *s){assert(fputs(s,f)>=0);assert(!fflush(f));}
static void expect(FILE *f,const char *msg){
    char line[80];assert(fgets(line,sizeof line,f));assert(strcmp(line,msg)==0);
}
static void game(void){
    setvbuf(stdout,NULL,_IONBF,0);
    if(!corner_connect())_exit(4);
    atomic_store(&probe->active_audio,1);
    puts("READY");
    for(;;){
        atomic_fetch_add(&probe->work,1);
        if(atomic_load(&probe->active_audio))atomic_fetch_add(&probe->audio,1);
        atomic_store(&probe->game_time,fight_guest_ns());
        usleep(1000);
    }
}
static void advancing(void){
    unsigned start=atomic_load(&probe->work);
    for(int n=0;n<100 && atomic_load(&probe->work)==start;n++)usleep(2000);
    assert(atomic_load(&probe->work)>start);
}
int main(void){
    alarm(45);
    probe=mmap(NULL,sizeof(*probe),PROT_READ|PROT_WRITE,MAP_SHARED|MAP_ANONYMOUS,-1,0);
    assert(probe!=MAP_FAILED);
    int to[2],from[2];assert(!pipe(to) && !pipe(from));
    pid_t owner=fork();assert(owner>=0);
    if(owner==0){
        assert(dup2(to[0],0)>=0 && dup2(from[1],1)>=0);
        close(to[0]);close(to[1]);close(from[0]);close(from[1]);game();
    }
    close(to[0]);close(from[1]);
    FILE *in=fdopen(to[1],"w"),*out=fdopen(from[0],"r");assert(in && out);
    expect(out,"FNR3/1 HELLO\n");
    send_cmd(in,"SLEEP\n");expect(out,"FNR3/1 ASLEEP\n");
    send_cmd(in,"WAKE\nPING 1\nRUN\n");expect(out,"FNR3/1 AWAKE\n");expect(out,"FNR3/1 PONG 1\n");expect(out,"READY\n");
    advancing();
    for(int n=0;n<24;n++){
        send_cmd(in,"SLEEP\n");expect(out,"FNR3/1 ASLEEP\n");
        unsigned work=atomic_load(&probe->work),audio=atomic_load(&probe->audio);
        int64_t guest=atomic_load(&probe->game_time);
        assert(!atomic_load(&probe->active_audio));
        if(!n)usleep(650000);
        assert(work==atomic_load(&probe->work) && audio==atomic_load(&probe->audio));
        send_cmd(in,"SLEEP\nWAKE\nWAKE\nPAD 00\nPING 2\n");expect(out,"FNR3/1 AWAKE\n");expect(out,"FNR3/1 PONG 2\n");
        assert(atomic_load(&probe->active_audio));
        advancing();assert(atomic_load(&probe->game_time)-guest<200000000);
        assert(atomic_load(&probe->pads)==(unsigned)n+1);
    }
    /* All of these commands may arrive in one parent read. */
    send_cmd(in,"SLEEP\nWAKE\nPING 3\nSLEEP\n");
    expect(out,"FNR3/1 ASLEEP\n");expect(out,"FNR3/1 AWAKE\n");expect(out,"FNR3/1 PONG 3\n");expect(out,"FNR3/1 ASLEEP\n");
    fclose(in);int status;assert(waitpid(owner,&status,0)==owner);
    assert(WIFEXITED(status));assert(fgetc(out)==EOF);fclose(out);
    puts("PASS native sleep: 27 parks, duplicate/rapid wake, frozen work and audio, clock continuity and exit while parked.");
    return 0;
}
