#include <assert.h>
#include "../native/gpu_mailbox.h"

int main(void){
    FightGpuMailbox pool={0};
    int first=gpu_reserve(&pool,0),second=gpu_reserve(&pool,0),third=gpu_reserve(&pool,0);
    assert(first>=0 && second>=0 && third>=0 && gpu_reserve(&pool,0)<0);
    gpu_publish(&pool,first,UINT32_MAX);
    assert(!gpu_claim(&pool,first,1) && gpu_claim(&pool,first,UINT32_MAX));
    gpu_publish(&pool,second,1);
    assert(gpu_reserve(&pool,1)<0);
    gpu_publish(&pool,third,2);
    assert(gpu_reserve(&pool,2)==second && !gpu_claim(&pool,second,1));
    atomic_store(&pool.state[first],GPU_FREE);
    assert(gpu_reserve(&pool,2)==first);
    assert(gpu_claim(&pool,third,2));
    return 0;
}
