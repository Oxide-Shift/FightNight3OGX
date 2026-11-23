#include "../native/flip_pacing.h"
#include <assert.h>
#include <stdio.h>
int main(void) {
    int64_t now=1000000000, deadline=fight_next_flip(0,now,1);
    for(int i=0;i<12000;i++){
        int64_t late=(i%31==0)?14000000:(i%19==0)?6000000:(i%7==0)?800000:0;
        now=deadline+late;
        int64_t next=fight_next_flip(deadline,now,1);
        assert(next-now>=FIGHT_NTSC_PERIOD_NS-2000000 && next-now<=FIGHT_NTSC_PERIOD_NS);
        assert(next==(late>2000000?now:deadline)+FIGHT_NTSC_PERIOD_NS);
        deadline=next;
    }
    assert(fight_next_flip(deadline,deadline,2)==deadline+2*FIGHT_NTSC_PERIOD_NS);
    assert(fight_next_flip(deadline,deadline+9000000,2)==deadline+9000000+2*FIGHT_NTSC_PERIOD_NS);
    puts("12000 NTSC presentation deadlines including 6 ms and 14 ms overruns");
    return 0;
}
