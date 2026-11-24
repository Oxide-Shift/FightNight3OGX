#include "../native/menu_dpad.h"
#include <assert.h>
#include <stdio.h>

int main(void){
    MenuDpad pad={0};
    const unsigned taps[]={1,0,2,0};
    for(uint32_t first=1;first<40001;first+=4){
        menu_dpad_reset(&pad,1,0);
        for(int i=0;i<4;i++)menu_dpad_offer(&pad,taps[i],first+i);
        for(int i=0;i<4;i++){
            uint32_t packet=first+3;
            assert(menu_dpad_read(&pad,0,&packet)==taps[i] && packet==first+i);
        }
        menu_dpad_offer(&pad,1,first+4);
        menu_dpad_reset(&pad,0,0);
        uint32_t packet=first+5;
        menu_dpad_offer(&pad,2,packet);menu_dpad_offer(&pad,0,packet+1);
        assert(menu_dpad_read(&pad,0,&packet)==0 && packet==first+5);
        menu_dpad_reset(&pad,1,0);
        for(int i=0;i<6;i++)menu_dpad_offer(&pad,(i&1)?0:2,first+i);
        assert(menu_dpad_read(&pad,0,&packet)==2 && packet==first+4);
        assert(menu_dpad_read(&pad,0,&packet)==0 && packet==first+5 && pad.count==0);
    }
    puts("10000 rapid D-pad pairs, packet numbers, page exits and overflow recoveries passed");
}
