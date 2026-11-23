#include "../native/audio_startup.h"
#include <assert.h>
#include <stdio.h>

int main(void) {
    AudioStartup state = {0};
    int16_t out[2];
    for (int i = 0; i < 96000; i++) audio_startup_frame(&state, 0, 0, out);
    assert(state.frames == 0);
    for (int i = 0; i < STARTUP_QUIET; i++) {
        audio_startup_frame(&state, 32767, -32768, out);
        assert(out[0] == 0 && out[1] == 0);
    }
    int previous = -1;
    for (int i = 0; i < STARTUP_FADE; i++) {
        audio_startup_frame(&state, 32767, -32768, out);
        assert(out[0] >= previous && out[0] - previous <= 2 && out[1] <= 0);
        previous = out[0];
    }
    audio_startup_frame(&state, -23456, 13245, out);
    assert(out[0] == -23456 && out[1] == 13245);
    puts("First burst suppressed, stereo ramp monotonic, subsequent PCM bit-exact.");
}
