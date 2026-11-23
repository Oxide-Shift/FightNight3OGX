#ifndef FIGHT_FLIP_PACING_H
#define FIGHT_FLIP_PACING_H
#include <stdint.h>
#define FIGHT_NTSC_PERIOD_NS INT64_C(16683333)

/* Preserve NTSC phase through ordinary scheduler noise. A materially late
 * presentation starts a fresh period so it is never followed by a burst. */
static inline int64_t fight_next_flip(int64_t target, int64_t presented) {
    int64_t base=(!target || presented>target+2000000) ? presented : target;
    return base+FIGHT_NTSC_PERIOD_NS;
}
#endif
