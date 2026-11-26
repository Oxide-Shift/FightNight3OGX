/* Per GL context, used only by its renderer thread. The current image remains
 * claimed through repeat draws. Return it after the final GL sample completes. */
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include <GLES2/gl2.h>
#include <GLES2/gl2ext.h>
#include <stdlib.h>
#include "android_gpu_socket.h"
typedef struct {
    int fd,phase,received,front,retry,disabled;
    char *path;
    EGLDisplay egl; EGLContext owner; GLint filter;
    FightGpuHello hello;
    FightGpuMailbox *control;
    AHardwareBuffer *buffers[GPU_SLOTS];
    EGLImageKHR images[GPU_SLOTS]; GLuint names[GPU_SLOTS]; EGLSyncKHR fences[GPU_SLOTS];
    PFNEGLGETNATIVECLIENTBUFFERANDROIDPROC client;
    PFNEGLCREATEIMAGEKHRPROC create;
    PFNEGLDESTROYIMAGEKHRPROC destroy;
    PFNGLEGLIMAGETARGETTEXTURE2DOESPROC target;
    PFNEGLCREATESYNCKHRPROC sync;
    PFNEGLCLIENTWAITSYNCKHRPROC wait;
    PFNEGLDESTROYSYNCKHRPROC unsync;
} GpuReader;
#define GPU_JNI(n) Java_org_oxideshift_corner_GameScreen_##n
JNIEXPORT jlong JNICALL GPU_JNI(gpuCreate)(JNIEnv *env,jclass type,jstring file,jboolean linear,jboolean requested) {
    (void)type;if(!requested && !gpu_display_requested())return 0;
    GpuReader *r=calloc(1,sizeof *r);if(!r)return 0;
    r->fd=-1;r->front=-1;r->egl=eglGetCurrentDisplay();r->owner=eglGetCurrentContext();r->filter=linear?GL_LINEAR:GL_NEAREST;
    const char *p=(*env)->GetStringUTFChars(env,file,NULL);if(!p){free(r);return 0;}
    r->path=strdup(p);(*env)->ReleaseStringUTFChars(env,file,p);
    const char *eglExt=eglQueryString(r->egl,EGL_EXTENSIONS),*glExt=(const char *)glGetString(GL_EXTENSIONS);
    if(!r->path || !eglExt || !glExt || !strstr(eglExt,"EGL_ANDROID_image_native_buffer") ||
       !strstr(eglExt,"EGL_KHR_fence_sync") || !strstr(glExt,"GL_OES_EGL_image"))goto fail;
#define LOAD(member,name) r->member=(void*)eglGetProcAddress(#name);if(!r->member)goto fail
    LOAD(client,eglGetNativeClientBufferANDROID);LOAD(create,eglCreateImageKHR);LOAD(destroy,eglDestroyImageKHR);
    LOAD(target,glEGLImageTargetTexture2DOES);LOAD(sync,eglCreateSyncKHR);LOAD(wait,eglClientWaitSyncKHR);LOAD(unsync,eglDestroySyncKHR);
#undef LOAD
    return (jlong)(uintptr_t)r;
fail:
    free(r->path);free(r);return 0;
}
static void gpu_reader_reset(GpuReader *r,int context) {
    context=context && eglGetCurrentContext()==r->owner;
    if(r->fd>=0){close(r->fd);r->fd=-1;}
    if(context && r->received)glFinish();
    for(int i=0;i<GPU_SLOTS;i++){
        if(r->fences[i]){r->unsync(r->egl,r->fences[i]);r->fences[i]=0;}
        if(context && r->names[i])glDeleteTextures(1,&r->names[i]);
        if(r->images[i])r->destroy(r->egl,r->images[i]);
        if(r->buffers[i])AHardwareBuffer_release(r->buffers[i]);
        r->images[i]=0;r->names[i]=0;r->buffers[i]=NULL;
    }
    if(r->control)munmap(r->control,sizeof *r->control);
    r->control=NULL;r->front=-1;r->phase=0;r->received=0;
}
JNIEXPORT void JNICALL GPU_JNI(gpuClose)(JNIEnv *env,jclass cls,jlong handle,jboolean context){
    (void)env;(void)cls;GpuReader *r=(void*)(uintptr_t)handle;if(!r)return;
    gpu_reader_reset(r,context);free(r->path);free(r);
}
static void gpu_reader_open(GpuReader *r) {
    if(r->disabled)return;
    if(r->fd<0){
        if(r->retry++%30)return;
        struct sockaddr_un addr;socklen_t n=gpu_address(&addr,r->path);
        int fd=socket(AF_UNIX,SOCK_SEQPACKET|SOCK_NONBLOCK|SOCK_CLOEXEC,0);
        if(fd<0)return;
        if(connect(fd,(struct sockaddr*)&addr,n) || !gpu_peer(fd)){close(fd);return;}
        r->fd=fd;
    }
    struct pollfd p={r->fd,POLLIN,0};
    if(poll(&p,1,0)<=0)return;
    if(p.revents&(POLLHUP|POLLERR|POLLNVAL)){
        gpu_reader_reset(r,1);r->retry=1;return;
    }
    if(!r->phase){
        int fd=-1;
        if(!gpu_hello(r->fd,&r->hello,&fd,0))goto fail;
        void *map=mmap(NULL,sizeof(FightGpuMailbox),PROT_READ|PROT_WRITE,MAP_SHARED,fd,0);close(fd);
        if(map==MAP_FAILED)goto fail;
        r->control=map;
        if(r->hello.magic!=FIGHT_GPU_MAGIC || !r->hello.w || !r->hello.h || r->hello.w>1440 || r->hello.h>1080)goto fail;
        r->phase=1;
    }
    while(r->received<GPU_SLOTS) {
        p.revents=0;if(poll(&p,1,0)<=0)return;
        int i=r->received;
        if(AHardwareBuffer_recvHandleFromUnixSocket(r->fd,&r->buffers[i]))goto fail;
        AHardwareBuffer_Desc desc;AHardwareBuffer_describe(r->buffers[i],&desc);
        if(desc.width!=r->hello.w || desc.height!=r->hello.h || desc.format!=AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM)goto fail;
        EGLClientBuffer client=r->client(r->buffers[i]);if(!client)goto fail;
        EGLint attrs[]={EGL_IMAGE_PRESERVED_KHR,EGL_TRUE,EGL_NONE};
        r->images[i]=r->create(r->egl,EGL_NO_CONTEXT,EGL_NATIVE_BUFFER_ANDROID,client,attrs);
        if(!r->images[i])goto fail;
        glGenTextures(1,&r->names[i]);glBindTexture(GL_TEXTURE_2D,r->names[i]);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MIN_FILTER,r->filter);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_MAG_FILTER,r->filter);
        glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_S,GL_CLAMP_TO_EDGE);glTexParameteri(GL_TEXTURE_2D,GL_TEXTURE_WRAP_T,GL_CLAMP_TO_EDGE);
        r->target(GL_TEXTURE_2D,r->images[i]);if(glGetError()!=GL_NO_ERROR)goto fail;
        ++r->received;
    }
    if(send(r->fd,"Y",1,MSG_DONTWAIT|MSG_NOSIGNAL)!=1)goto fail;
    r->phase=2;return;
fail:
    gpu_reader_reset(r,1);r->disabled=1;
}
JNIEXPORT jboolean JNICALL GPU_JNI(gpuPoll)(JNIEnv *env,jclass cls,jlong handle){
    (void)env;(void)cls;GpuReader *r=(void*)(uintptr_t)handle;if(!r)return JNI_FALSE;
    if(r->phase!=2){gpu_reader_open(r);return JNI_FALSE;}
    struct pollfd p={r->fd,0,0};
    if(poll(&p,1,0)>0 && (p.revents&(POLLERR|POLLHUP|POLLNVAL))){
        gpu_reader_reset(r,1);return JNI_TRUE;
    }
    for(int i=0;i<GPU_SLOTS;i++)if(r->fences[i]){
        int result=r->wait(r->egl,r->fences[i],0,0);
        if(result==EGL_TIMEOUT_EXPIRED_KHR)continue;
        if(result!=EGL_CONDITION_SATISFIED_KHR)glFinish();
        r->unsync(r->egl,r->fences[i]);r->fences[i]=0;atomic_store(&r->control->state[i],GPU_FREE);
    }
    return JNI_FALSE;
}
static void gpu_retire(GpuReader *r) {
    int i=r->front;if(i<0)return;r->front=-1;
    r->fences[i]=r->sync(r->egl,EGL_SYNC_FENCE_KHR,NULL);
    if(r->fences[i])glFlush();
    else {glFinish();atomic_store(&r->control->state[i],GPU_FREE);}
}
JNIEXPORT jint JNICALL GPU_JNI(gpuSelect)(JNIEnv *env,jclass cls,jlong handle,jint token,jint seq,jintArray dims) {
    (void)cls;GpuReader *r=(void*)(uintptr_t)handle;
    if(!r || r->phase!=2)return 0;
    if(!token){gpu_retire(r);return 0;}
    int i=(token&3)-1;
    if(i<0 || ((unsigned)token>>2)!=r->hello.generation || !gpu_claim(r->control,i,(uint32_t)seq))return 0;
    gpu_retire(r);r->front=i;
    jint wh[]={r->hello.w,r->hello.h};(*env)->SetIntArrayRegion(env,dims,0,2,wh);
    return r->names[i];
}

