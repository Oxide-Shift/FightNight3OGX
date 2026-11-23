#include "../native/voice_rate.h"
#include <assert.h>
#include <math.h>
#include <stdio.h>

typedef struct { unsigned at, length; } Source;
static int read_ramp(void *ptr,float (*out)[2],int count){
    Source *src=ptr;
    int i=0;
    while(i<count && src->at<src->length){
        out[i][0]=(float)src->at;out[i][1]=-(float)src->at;
        ++src->at;++i;
    }
    return i;
}
static void verify(double step){
    FightVoiceRate voice={0};Source src={0,10000};unsigned i=0;
    for(int block=0;block<20;block++){
        float out[32][2];int n=fight_voice_resample(&voice,&src,read_ramp,out,32,step);
        assert(n==32);
        for(int j=0;j<n;j++,i++){
            assert(fabs(out[j][0]-i*step)<.001);assert(fabs(out[j][1]+i*step)<.001);
        }
    }
}
int main(void){
    verify(1);verify(.5);verify(2);verify(44100.0/48000);verify(3.125);verify(8);
    FightVoiceRate voice={0};Source src={0,2};float out[12][2];
    assert(fight_voice_resample(&voice,&src,read_ramp,out,12,.5)==4);
    assert(out[3][0]==1);puts("Voice rate, pitch, stereo and sample tail passed");
}
