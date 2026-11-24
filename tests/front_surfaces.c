#include <assert.h>
#include "../native/front_surfaces.h"

int main(void) {
    FightFrontSurfaces s={0};
    for(unsigned i=1;i<=8;i++) fight_front_present(&s,i);
    assert(s.count==8);
    fight_front_present(&s,1);
    fight_front_present(&s,9);
    assert(fight_front_index(&s,1)<s.count && fight_front_index(&s,2)==s.count);
    for(unsigned i=10;i<4096;i++) {
        assert(fight_front_index(&s,i)==s.count);
        fight_front_present(&s,i);
        fight_front_present(&s,1);
        assert(s.count==8 && fight_front_index(&s,i)<s.count);
        assert(fight_front_index(&s,0x10000)==s.count);
    }
    return 0;
}
