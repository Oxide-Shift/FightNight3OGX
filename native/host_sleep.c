/* The launcher owns the command pipe. Its direct child waits in poll while the
 * entire Xbox process is stopped, including guest, USB, GPU and mixer threads. */
#include <errno.h>
#include <poll.h>
#include <signal.h>
#include <stdatomic.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/prctl.h>
#include <sys/socket.h>
#include <sys/wait.h>
#include <time.h>
#include <unistd.h>

static _Atomic int64_t *parked_ns;
static int64_t monotonic_ns(void){
    struct timespec t;
    clock_gettime(CLOCK_MONOTONIC,&t);
    return (int64_t)t.tv_sec*1000000000+t.tv_nsec;
}
int64_t fight_guest_ns(void){
    if(!parked_ns)return monotonic_ns();
    int64_t offset,now;
    do{offset=atomic_load(parked_ns);now=monotonic_ns();}while(offset!=atomic_load(parked_ns));
    return now-offset;
}
static int send_line(int fd,const char *s,size_t n){
    while(n){
        ssize_t k=send(fd,s,n,MSG_NOSIGNAL);
        if(k<0 && errno==EINTR)continue;
        if(k<=0)return 0;
        s+=k;n-=(size_t)k;
    }
    return 1;
}
static int reap(pid_t child){
    int status;
    while(waitpid(child,&status,0)<0){if(errno!=EINTR)return 1;}
    return WIFEXITED(status)?WEXITSTATUS(status):128+WTERMSIG(status);
}
static int supervise(pid_t child,int fd){
    struct pollfd io[]={{STDIN_FILENO,POLLIN,0},{fd,POLLIN,0}};
    int64_t stopped=0;
    char packet[1024],line[96];
    size_t used=0;
    char ticket='A';
    for(;;){
        int ready=poll(io,2,-1);
        if(ready<0 && errno==EINTR)continue;
        if(ready<0)break;
        if(io[1].revents&(POLLHUP|POLLERR))return reap(child);
        if(io[1].revents&POLLIN){
            char ack;
            if(recv(fd,&ack,1,0)<=0)return reap(child);
        }
        if(!io[0].revents)continue;
        ssize_t n=read(STDIN_FILENO,packet,sizeof packet);
        if(n<0 && errno==EINTR)continue;
        if(n<=0)break;
        for(ssize_t i=0;i<n;i++){
            if(used==sizeof(line)-1)goto finish;
            line[used++]=packet[i];
            if(packet[i]!='\n')continue;
            line[used]=0;
            if(!strcmp(line,"SLEEP\n")){
                if(!stopped){
                    char request[]="SLEEP A\n";request[6]=ticket;
                    if(!send_line(fd,request,sizeof(request)-1))goto finish;
                    /* Give AAudio time to park its output before stopping all
                     * threads. A stuck audio service must not prevent sleep. */
                    struct pollfd response={fd,POLLIN,0};
                    int64_t deadline=monotonic_ns()+350000000;
                    for(;;){
                        int64_t ms=(deadline-monotonic_ns()+999999)/1000000;
                        if(ms<=0 || poll(&response,1,(int)ms)<=0 || !(response.revents&POLLIN))break;
                        char ack;
                        if(recv(fd,&ack,1,0)<=0)goto finish;
                        if(ack==ticket)break;
                    }
                    ticket=ticket=='Z'?'A':ticket+1;
                    if(kill(child,SIGSTOP)<0)goto finish;
                    int status;
                    pid_t observed;
                    do{observed=waitpid(child,&status,WUNTRACED);}while(observed<0 && errno==EINTR);
                    if(observed<0)return 1;
                    if(!WIFSTOPPED(status))return WIFEXITED(status)?WEXITSTATUS(status):128+WTERMSIG(status);
                    stopped=monotonic_ns();
                    puts("FNR3/1 ASLEEP");fflush(stdout);
                }
            }else if(!strcmp(line,"WAKE\n")){
                if(stopped){
                    atomic_fetch_add(parked_ns,monotonic_ns()-stopped);
                    stopped=0;
                    if(kill(child,SIGCONT)<0 || !send_line(fd,line,used))goto finish;
                }
            }else if(!send_line(fd,line,used))goto finish;
            used=0;
        }
    }
finish:
    kill(child,SIGKILL);
    return reap(child);
}

/* Called before initializing graphics, audio or creating any worker threads. */
void fight_sleep_supervisor(void){
    int channel[2];
    parked_ns=mmap(NULL,sizeof(*parked_ns),PROT_READ|PROT_WRITE,MAP_SHARED|MAP_ANONYMOUS,-1,0);
    if(parked_ns==MAP_FAILED || socketpair(AF_UNIX,SOCK_STREAM,0,channel)<0)_exit(4);
    atomic_init(parked_ns,0);
    pid_t owner=getpid(),child=fork();
    if(child<0)_exit(4);
    if(child==0){
        close(channel[0]);
        if(prctl(PR_SET_PDEATHSIG,SIGKILL)<0 || getppid()!=owner || dup2(channel[1],STDIN_FILENO)<0)_exit(4);
        close(channel[1]);return;
    }
    close(channel[1]);
    _exit(supervise(child,channel[0]));
}
