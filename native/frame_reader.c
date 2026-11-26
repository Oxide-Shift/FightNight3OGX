#include <jni.h>
#include <stdatomic.h>
#include <stdint.h>
#include <errno.h>
#include <linux/futex.h>
#include <sys/syscall.h>
#include <time.h>
#include <unistd.h>

/* Pairs with the release publication in frame_output.c across the shared mapping. */
JNIEXPORT jint JNICALL Java_org_oxideshift_corner_GameScreen_sequence(JNIEnv *env,jclass type,jobject mapping) {
    (void)type;
    const _Atomic uint32_t *header=(*env)->GetDirectBufferAddress(env,mapping);
    return header?(jint)atomic_load_explicit(header,memory_order_acquire):0;
}

JNIEXPORT jlong JNICALL Java_org_oxideshift_corner_GameScreen_waitForFrame(
        JNIEnv *env,jclass type,jobject mapping,jint observed) {
    (void)type;
    _Atomic uint32_t *seq=(*env)->GetDirectBufferAddress(env,mapping);
    if (!seq) return -1;
    if (atomic_load_explicit(seq,memory_order_acquire)==(uint32_t)observed) {
        /* Shared across the host and Activity processes. The value comparison
         * in FUTEX_WAIT also covers a publish between the load and the wait. */
        struct timespec timeout={0,150000000};
        if (syscall(SYS_futex,seq,FUTEX_WAIT,(uint32_t)observed,&timeout,NULL,0)<0 &&
            errno!=EAGAIN && errno!=EINTR && errno!=ETIMEDOUT) return -1;
    }
    return (jlong)atomic_load_explicit(seq,memory_order_acquire);
}

#include "corner_camera.h"
JNIEXPORT jint JNICALL Java_org_oxideshift_corner_GameScreen_cameraBytes(JNIEnv *env,jclass type){
    (void)env;(void)type;return sizeof(CornerCamera);
}
JNIEXPORT void JNICALL Java_org_oxideshift_corner_GameScreen_measureCamera(JNIEnv *env,jclass type,
        jobject state,jobject slot,jint w,jint h,jint seq,jint shot,jfloatArray out){
    (void)type;
    CornerCamera *c=(*env)->GetDirectBufferAddress(env,state);
    const uint8_t *p=(*env)->GetDirectBufferAddress(env,slot);
    if(!c || !p || w<=0 || w>1440 || h<=0 || h>1080 ||
       (*env)->GetDirectBufferCapacity(env,slot)<8+(jlong)w*h*4)return;
    camera_step(c,p+9,w,h,4,seq,shot); /* green channel of BGRA */
    jfloat correction[]={c->x/256.f,c->y/144.f};
    (*env)->SetFloatArrayRegion(env,out,0,2,correction);
}

#include "android_gpu_reader.h"
