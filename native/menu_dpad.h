#ifndef FNR3_MENU_DPAD_H
#define FNR3_MENU_DPAD_H
#include <stdint.h>

/* D-pad transitions on the root carousel only. Keep two complete taps between
   player-one reads. If the game stalls longer, drop the backlog and catch up. */
typedef struct {
    uint32_t packets[4];
    unsigned char states[4],head,count,last,enabled;
} MenuDpad;

static inline void menu_dpad_reset(MenuDpad *q,int enabled,unsigned buttons){
    q->count=q->head=0;q->last=buttons&15;q->enabled=enabled!=0;
}
static inline void menu_dpad_offer(MenuDpad *q,unsigned buttons,uint32_t packet){
    unsigned char direction=buttons&15;
    if(!q->enabled || direction==q->last)return;
    if(q->count==4)q->count=q->head=0;
    unsigned tail=(q->head+q->count)&3;
    q->states[tail]=direction;q->packets[tail]=packet;
    q->count++;q->last=direction;
}
static inline unsigned menu_dpad_read(MenuDpad *q,unsigned buttons,uint32_t *packet){
    if(!q->count)return buttons&15;
    *packet=q->packets[q->head];
    unsigned state=q->states[q->head];q->head=(q->head+1)&3;q->count--;
    return state;
}
#endif
