#ifndef FIGHT_VOICE_LIST_H
#define FIGHT_VOICE_LIST_H

/* A physical voice has one next link, even when restarted or moved to another list. */
typedef struct { uint64_t bits[(MCPX_HW_MAX_VOICES + 63) / 64]; } VoiceVisits;

static bool visit_voice(VoiceVisits *seen, unsigned handle)
{
    if (handle >= MCPX_HW_MAX_VOICES) return false;
    uint64_t mask = UINT64_C(1) << (handle % 64);
    uint64_t *word = &seen->bits[handle / 64];
    if (*word & mask) return false;
    *word |= mask;
    return true;
}

static unsigned voice_next(MCPXAPUState *d, unsigned v)
{
    return voice_get_mask(d, v, NV_PAVS_VOICE_TAR_PITCH_LINK,
                          NV_PAVS_VOICE_TAR_PITCH_LINK_NEXT_VOICE_HANDLE);
}

static void set_voice_next(MCPXAPUState *d, unsigned v, unsigned next)
{
    voice_set_mask(d, v, NV_PAVS_VOICE_TAR_PITCH_LINK,
                   NV_PAVS_VOICE_TAR_PITCH_LINK_NEXT_VOICE_HANDLE, next);
}

static void relink_voice(MCPXAPUState *d, unsigned v, unsigned list, unsigned after)
{
    if (v >= MCPX_HW_MAX_VOICES || list > 3) return;
    if (list == 0 && (after >= MCPX_HW_MAX_VOICES || after == v)) return;
    if (list ? d->regs[voice_list_regs[list - 1].top] == v : voice_next(d, after) == v) return;

    for (unsigned i = 0; i < 3; ++i) {
        unsigned top = voice_list_regs[i].top;
        unsigned current = d->regs[top], previous = 0xffff;
        VoiceVisits seen = {0};
        while (visit_voice(&seen, current)) {
            unsigned next = voice_next(d, current);
            if (current == v) {
                if (next == v) next = 0xffff;
                if (previous == 0xffff) d->regs[top] = next;
                else set_voice_next(d, previous, next);
                break;
            }
            previous = current;
            current = next;
        }
    }
    if (list) {
        unsigned top = voice_list_regs[list - 1].top;
        set_voice_next(d, v, d->regs[top]);
        d->regs[top] = v;
    } else {
        set_voice_next(d, v, voice_next(d, after));
        set_voice_next(d, after, v);
    }
}
#endif
