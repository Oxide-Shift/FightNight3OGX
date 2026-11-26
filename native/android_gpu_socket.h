#ifndef FIGHT_ANDROID_GPU_SOCKET_H
#define FIGHT_ANDROID_GPU_SOCKET_H
#include <sys/system_properties.h>
static inline int gpu_display_requested(void){
    char value[PROP_VALUE_MAX];
    return __system_property_get("debug.fnr3.gpu_present",value)==1 && value[0]=='1';
}
#include <android/hardware_buffer.h>
#include <android/sharedmem.h>
#include <errno.h>
#include <poll.h>
#include <stddef.h>
#include <stdio.h>
#include <string.h>
#include <sys/mman.h>
#include <sys/socket.h>
#include <sys/un.h>
#include <unistd.h>
#include "gpu_mailbox.h"
typedef struct {uint32_t magic,w,h,generation;} FightGpuHello;
#define FIGHT_GPU_MAGIC 0x31424846u
static inline socklen_t gpu_address(struct sockaddr_un *addr,const char *path) {
    uint64_t hash=14695981039346656037ull;
    for(const char *p=path;*p;p++)hash=(hash^(unsigned char)*p)*1099511628211ull;
    memset(addr,0,sizeof *addr);addr->sun_family=AF_UNIX;
    int len=snprintf(addr->sun_path+1,sizeof(addr->sun_path)-1,"fnr3.%u.%llx",(unsigned)getuid(),(unsigned long long)hash);
    return offsetof(struct sockaddr_un,sun_path)+1+len;
}
static inline int gpu_peer(int fd) {
    struct ucred cred;socklen_t n=sizeof cred;
    return getsockopt(fd,SOL_SOCKET,SO_PEERCRED,&cred,&n)==0 && cred.uid==getuid();
}
static inline int gpu_hello(int fd,FightGpuHello *hello,int *memory,int sending) {
    union {struct cmsghdr align;char bytes[CMSG_SPACE(sizeof(int))];} control={0};
    struct iovec iov={hello,sizeof *hello};
    struct msghdr msg={.msg_iov=&iov,.msg_iovlen=1,.msg_control=control.bytes,.msg_controllen=sizeof control.bytes};
    if(sending) {
        struct cmsghdr *c=CMSG_FIRSTHDR(&msg);c->cmsg_level=SOL_SOCKET;c->cmsg_type=SCM_RIGHTS;c->cmsg_len=CMSG_LEN(sizeof(int));
        memcpy(CMSG_DATA(c),memory,sizeof(int));return sendmsg(fd,&msg,MSG_DONTWAIT|MSG_NOSIGNAL)==sizeof *hello;
    }
    if(recvmsg(fd,&msg,MSG_DONTWAIT|MSG_CMSG_CLOEXEC)!=sizeof *hello)return 0;
    struct cmsghdr *c=CMSG_FIRSTHDR(&msg);
    if(!c || c->cmsg_level!=SOL_SOCKET || c->cmsg_type!=SCM_RIGHTS || c->cmsg_len!=CMSG_LEN(sizeof(int)))return 0;
    memcpy(memory,CMSG_DATA(c),sizeof(int));return 1;
}
#endif

