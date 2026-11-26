/* Three external images, each with a single writer or one display reader.
 * A 64-bit ticket prevents an old CPU frame from claiming a recycled image. */
#ifndef FIGHT_GPU_MAILBOX_H
#define FIGHT_GPU_MAILBOX_H
#include <stdatomic.h>
#include <stdint.h>
enum { GPU_SLOTS=3, GPU_FREE=0, GPU_WRITE=1, GPU_READY=2, GPU_READ=3 };
typedef struct { _Atomic uint64_t state[GPU_SLOTS]; } FightGpuMailbox;
static inline uint64_t gpu_ticket(uint32_t serial,unsigned phase) {return ((uint64_t)serial<<2)|phase;}
static inline int gpu_reserve(FightGpuMailbox *m,uint32_t latest) {
    for(unsigned pass=0;pass<2;pass++) for(int i=0;i<GPU_SLOTS;i++) {
        uint64_t old=atomic_load_explicit(&m->state[i],memory_order_acquire);
        if(pass ? (old&3)!=GPU_READY || (uint32_t)(old>>2)==latest : old!=GPU_FREE)continue;
        if(atomic_compare_exchange_strong(&m->state[i],&old,GPU_WRITE))return i;
    }
    return -1;
}
static inline void gpu_publish(FightGpuMailbox *m,int i,uint32_t seq) {
    atomic_store_explicit(&m->state[i],gpu_ticket(seq,GPU_READY),memory_order_release);
}
static inline int gpu_claim(FightGpuMailbox *m,int i,uint32_t seq) {
    uint64_t expected=gpu_ticket(seq,GPU_READY);
    return atomic_compare_exchange_strong(&m->state[i],&expected,gpu_ticket(seq,GPU_READ));
}
#endif

