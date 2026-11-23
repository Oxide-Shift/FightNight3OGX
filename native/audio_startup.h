#ifndef FIGHTNIGHT_AUDIO_STARTUP_H
#define FIGHTNIGHT_AUDIO_STARTUP_H
#include <stdint.h>

/* The first audible block of the retail boot currently contains a burst
   before the intro. Count from the signal onset, regardless of load time.
   The rest of the session passes at the original gain. */
enum { STARTUP_QUIET = 36000, STARTUP_FADE = 24000 };
typedef struct { unsigned frames; int heard; } AudioStartup;

static inline void audio_startup_frame(AudioStartup *state, int16_t left, int16_t right,
                                       int16_t *output) {
    unsigned pos = state->frames;
    if (pos >= STARTUP_QUIET + STARTUP_FADE) {
        output[0] = left;
        output[1] = right;
        return;
    }
    if (left > 128 || left < -128 || right > 128 || right < -128) state->heard = 1;
    if (state->heard) state->frames++;
    unsigned gain = pos > STARTUP_QUIET ? pos - STARTUP_QUIET : 0;
    output[0] = (int16_t)((int32_t)left * (int32_t)gain / STARTUP_FADE);
    output[1] = (int16_t)((int32_t)right * (int32_t)gain / STARTUP_FADE);
}
#endif
