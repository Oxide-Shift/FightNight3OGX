/* The optional vertex optimizer runs outside the game. A worker handles only
   bounded Xbox shader words and returns SPIR-V over a private socket pair.
   One background queue owns this connection, including shutdown and reaping. */
#include <cerrno>
#include <csignal>
#include <spawn.h>
#include <fcntl.h>
#include <sys/socket.h>
#include <sys/wait.h>

namespace {
constexpr size_t max_vertex_bytes=1024*1024;
bool vertex_transfer(int fd,void *buffer,size_t bytes,bool writing){
    auto p=static_cast<unsigned char*>(buffer);
    while(bytes){
        ssize_t n=writing?send(fd,p,bytes,MSG_NOSIGNAL):recv(fd,p,bytes,0);
        if(n<0 && errno==EINTR)continue;
        if(n<=0)return false;
        p+=n;bytes-=static_cast<size_t>(n);
    }
    return true;
}

class VertexCompiler {
    int socket_=-1;
    pid_t pid_=-1;
    void stop(){
        if(socket_>=0){close(socket_);socket_=-1;}
        if(pid_<=0)return;
        int status=0;
        pid_t reaped;
        do {reaped=waitpid(pid_,&status,WNOHANG);} while(reaped<0 && errno==EINTR);
        if(reaped==0){
            kill(pid_,SIGKILL);
            do {reaped=waitpid(pid_,&status,0);} while(reaped<0 && errno==EINTR);
        }
        pid_=-1;
    }
    bool start(){
        int ends[2];
        if(socketpair(AF_UNIX,SOCK_STREAM|SOCK_CLOEXEC,0,ends)!=0)return false;
        posix_spawn_file_actions_t actions;
        int error=posix_spawn_file_actions_init(&actions);
        if(!error){
            error=posix_spawn_file_actions_addclose(&actions,ends[0]);
            if(!error)error=posix_spawn_file_actions_adddup2(&actions,ends[1],STDIN_FILENO);
            if(!error && ends[1]!=STDIN_FILENO)error=posix_spawn_file_actions_addclose(&actions,ends[1]);
            // Only the game owns the launcher's stdout/stderr pipes.
            if(!error)error=posix_spawn_file_actions_addopen(&actions,STDOUT_FILENO,"/dev/null",O_WRONLY,0);
            if(!error)error=posix_spawn_file_actions_addopen(&actions,STDERR_FILENO,"/dev/null",O_WRONLY,0);
            char exe[]="/proc/self/exe",option[]="--vertex-compiler";
            char *args[]={exe,option,nullptr};
            if(!error)error=posix_spawn(&pid_,exe,&actions,nullptr,args,environ);
            posix_spawn_file_actions_destroy(&actions);
        }
        close(ends[1]);
        if(error){close(ends[0]);pid_=-1;return false;}
        socket_=ends[0];
        const timeval timeout={30,0};
        if(setsockopt(socket_,SOL_SOCKET,SO_RCVTIMEO,&timeout,sizeof(timeout)) ||
           setsockopt(socket_,SOL_SOCKET,SO_SNDTIMEO,&timeout,sizeof(timeout))){stop();return false;}
        return true;
    }
public:
    ~VertexCompiler(){stop();}
    size_t compile(const uint32_t *words,unsigned count,unsigned inputs,uint32_t **out){
        if(!count || count>136 || inputs>16)return 0;
        return request(count,inputs,words,count*32,nullptr,out);
    }
    size_t fragment(const uint32_t *source,size_t bytes,const uint32_t *states,uint32_t **out){
        if(bytes<20 || bytes>max_vertex_bytes || (bytes&3))return 0;
        return request(0,static_cast<uint32_t>(bytes),source,bytes,states,out);
    }
private:
    unsigned failures=0;
    size_t request(uint32_t count,uint32_t argument,const uint32_t *data,size_t length,
                   const uint32_t *states,uint32_t **out){
        if(failures>=2)return 0;
        if(socket_<0 && !start()){++failures;return 0;}
        uint32_t header[]={count,argument},bytes=0;
        if(!vertex_transfer(socket_,header,sizeof(header),true) ||
           !vertex_transfer(socket_,const_cast<uint32_t*>(data),length,true) ||
           (states && !vertex_transfer(socket_,const_cast<uint32_t*>(states),39*sizeof(uint32_t),true)) ||
           !vertex_transfer(socket_,&bytes,sizeof(bytes),false) ||
           bytes>max_vertex_bytes || (bytes && (bytes<20 || (bytes&3)))){
            fprintf(stderr,"[Fight Night] Optional shader worker interrupted; using general shader.\n");
            ++failures;stop();return 0;
        }
        if(!bytes)return 0;
        auto *code=static_cast<uint32_t*>(malloc(bytes));
        if(!code || !vertex_transfer(socket_,code,bytes,false) || code[0]!=0x07230203){
            free(code);++failures;stop();return 0;
        }
        *out=code;return bytes;
    }
};
}

extern "C" __attribute__((visibility("default")))
int fight_vertex_worker(){
    uint32_t header[2],words[136*8];
    while(vertex_transfer(STDIN_FILENO,header,sizeof(header),false)){
        uint32_t *code=nullptr;
        size_t produced=0;
        if(header[0]){
            if(header[0]>136 || header[1]>16 ||
               !vertex_transfer(STDIN_FILENO,words,header[0]*32,false))return 2;
            produced=fight_compile_vertex(words,header[0],header[1],&code);
        }else{
            uint32_t bytes=header[1], states[39];
            if(bytes<20 || bytes>max_vertex_bytes || (bytes&3))return 2;
            std::vector<uint32_t> source(bytes/4);
            if(!vertex_transfer(STDIN_FILENO,source.data(),bytes,false) || source[0]!=0x07230203 ||
               !vertex_transfer(STDIN_FILENO,states,sizeof(states),false))return 2;
            produced=fight_compile_fragment(source.data(),bytes,states,&code);
        }
        uint32_t bytes=produced<=max_vertex_bytes?static_cast<uint32_t>(produced):0;
        bool sent=vertex_transfer(STDIN_FILENO,&bytes,sizeof(bytes),true) &&
                  vertex_transfer(STDIN_FILENO,code,bytes,true);
        free(code);
        if(!sent)return 0;
    }
    return 0;
}
