#include <assert.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
enum { MCPX_HW_MAX_VOICES=256, NV_PAVS_VOICE_TAR_PITCH_LINK=0, NV_PAVS_VOICE_TAR_PITCH_LINK_NEXT_VOICE_HANDLE=65535 };
typedef struct { unsigned regs[3], link[256]; } MCPXAPUState;
static const struct { unsigned top; } voice_list_regs[]={{0},{1},{2}};
static unsigned voice_get_mask(MCPXAPUState *d,unsigned v,unsigned off,unsigned mask){
    assert(v<256);(void)off;(void)mask;return d->link[v];
}
static void voice_set_mask(MCPXAPUState *d,unsigned v,unsigned off,unsigned mask,unsigned next){
    assert(v<256);(void)off;(void)mask;d->link[v]=next;
}
#include "../native/voice_list.h"
static unsigned count(MCPXAPUState *d,unsigned list){
    VoiceVisits seen={0};unsigned n=0;
    for(unsigned v=d->regs[list];visit_voice(&seen,v);v=d->link[v])++n;
    return n;
}
int main(void){
    MCPXAPUState d;memset(&d,0xff,sizeof d);
    for(unsigned i=0;i<256;i++)relink_voice(&d,i,1,0);
    assert(count(&d,0)==256);
    for(unsigned i=0;i<60000;i++){
        unsigned v=(i*37)%256,which=(i/19)%3;
        relink_voice(&d,v,which+1,0);
        relink_voice(&d,v,which+1,0);
        if(i%7==0)relink_voice(&d,(v+1)%256,0,v);
        relink_voice(&d,v,0,v);
        assert(count(&d,0)+count(&d,1)+count(&d,2)==256);
    }
    d.regs[0]=254;d.link[254]=254;assert(count(&d,0)==1);
    d.link[254]=255;d.link[255]=254;assert(count(&d,0)==2);
    d.link[255]=420;assert(count(&d,0)==2);
    puts("60,000 voice restarts, relocation, cyclic and invalid links passed");
}
