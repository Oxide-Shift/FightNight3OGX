/* Exchange the Android shared-memory descriptor once, before the first flip.
 * A launcher without the handshake keeps the file-backed capture format. */
#include <android/sharedmem.h>
#include <poll.h>
#include <stddef.h>
#include <sys/socket.h>
#include <sys/un.h>

static uint32_t *android_scanout(const char *path,size_t bytes) {
    if (!getenv("FNR3_FRAME_SOCKET")) return NULL;
    struct sockaddr_un address={.sun_family=AF_UNIX};
    int n=snprintf(address.sun_path,sizeof address.sun_path,"%s.sock",path);
    if(n<=0 || (size_t)n>=sizeof address.sun_path)return NULL;
    int memory=ASharedMemory_create("Fight Night video",bytes);
    if(memory<0)return NULL;
    uint32_t *map=mmap(NULL,bytes,PROT_READ|PROT_WRITE,MAP_SHARED,memory,0);
    int listener=-1,peer=-1,bound=0,accepted=0;
    if(map==MAP_FAILED)goto done;
    /* Existing writer mapping survives this restriction. The Activity only reads. */
    if(ASharedMemory_setProt(memory,PROT_READ)<0)goto done;
    listener=socket(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0);
    if(listener<0 || bind(listener,(struct sockaddr *)&address,sizeof address)<0)goto done;
    bound=1;
    if(listen(listener,1)<0)goto done;
    puts("FNR3/1 FRAMEFD");fflush(stdout);
    struct pollfd ready={listener,POLLIN,0};
    if(poll(&ready,1,2000)<=0)goto done;
    peer=accept4(listener,NULL,NULL,SOCK_CLOEXEC);
    struct ucred credentials;
    socklen_t len=sizeof credentials;
    if(peer<0 || getsockopt(peer,SOL_SOCKET,SO_PEERCRED,&credentials,&len)<0 || credentials.uid!=getuid())goto done;
    char byte='F';
    struct iovec io={&byte,1};
    union {struct cmsghdr align;char buf[CMSG_SPACE(sizeof(int))];} control={0};
    struct msghdr msg={.msg_iov=&io,.msg_iovlen=1,.msg_control=control.buf,.msg_controllen=sizeof control.buf};
    struct cmsghdr *c=CMSG_FIRSTHDR(&msg);
    c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(sizeof(int));
    memcpy(CMSG_DATA(c),&memory,sizeof memory);
    if(sendmsg(peer,&msg,MSG_NOSIGNAL)!=1)goto done;
    ready=(struct pollfd){peer,POLLIN,0};
    accepted=poll(&ready,1,2000)>0 && recv(peer,&byte,1,0)==1 && byte=='Y';
done:
    if(peer>=0)close(peer);
    if(listener>=0)close(listener);
    if(bound)unlink(address.sun_path);
    close(memory);
    if(!accepted && map!=MAP_FAILED)munmap(map,bytes);
    return accepted?map:NULL;
}
