#include <assert.h>
#include "../native/android_audio.c"

int main(void){
    assert(xa2_init() && opens==1 && error_callback && data_callback);
    int16_t signal[512],output[512];
    for(int i=0;i<512;i++)signal[i]=3000;
    for(int i=0;i<250;i++){
        assert(xa2_submit_samples(signal,256));data_callback(stream,NULL,output,256);
    }
    assert(output[510]==3000);
    error_callback(stream,NULL,-899);
    assert(closes==0); /* Recovery must run off the callback thread. */
    unavailable=1;fight_audio_check_route();assert(closes==1 && !xa2_is_active());
    unavailable=0;
    for(int i=0;i<4;i++)fight_audio_check_route();
    assert(opens==2 && xa2_is_active());
    for(int i=0;i<12;i++){assert(xa2_submit_samples(signal,256));data_callback(stream,NULL,output,256);}
    assert(output[510]==3000); /* No repeat of the title-screen startup mute. */
    error_callback(stream,NULL,-899);fight_audio_sleep(1);fight_audio_check_route();
    assert(opens==2 && !xa2_is_active());fight_audio_sleep(0);assert(opens==3);
    xa2_shutdown();fight_audio_check_route();assert(opens==closes && !xa2_is_active());
    puts("AAudio disconnect, failed reopen, speech after recovery, sleep and shutdown passed");
}
