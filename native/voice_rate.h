#ifndef FIGHT_VOICE_RATE_H
#define FIGHT_VOICE_RATE_H
/* The APU asks for 32 output frames at a time. Preserve fractional source
 * position across callbacks and fetch only as far as interpolation needs. */
typedef struct { float frame[2][2]; double phase; unsigned count; } FightVoiceRate;
typedef int (*FightReadVoice)(void *, float (*)[2], int);

static int fight_voice_resample(FightVoiceRate *s, void *voice, FightReadVoice read,
                                float (*out)[2], int requested, double step) {
    if (!(step > 0 && step <= 256) || requested < 1) return 0;
    if (step == 1.0 && !s->count && !s->phase)
        return read(voice, out, requested);
    float block[64][2];
    unsigned at = 0, end = 0;
    int produced = 0;
    while (produced < requested) {
        while (s->count < 2 || s->phase >= 1) {
            if (s->count == 2) {
                s->frame[0][0] = s->frame[1][0]; s->frame[0][1] = s->frame[1][1];
                --s->count; s->phase -= 1;
            }
            if (at == end) {
                unsigned need = (unsigned)(s->phase + (requested - produced - 1) * step) + 2 - s->count;
                int n = read(voice,block,need > 64 ? 64 : (int)need);
                if (n < 1) {
                    if (s->count == 1 && s->phase < 1) {
                        while(produced < requested && s->phase < 1) {
                            out[produced][0]=s->frame[0][0];out[produced++][1]=s->frame[0][1];
                            s->phase+=step;
                        }
                    }
                    return produced;
                }
                at = 0; end = (unsigned)n;
            }
            s->frame[s->count][0]=block[at][0];s->frame[s->count++][1]=block[at++][1];
        }
        for (int ch=0;ch<2;ch++)
            out[produced][ch]=(float)(s->frame[0][ch]+s->phase*(s->frame[1][ch]-s->frame[0][ch]));
        ++produced;s->phase += step;
    }
    return produced;
}
#endif
