#ifndef FIGHT_FRONT_SURFACES_H
#define FIGHT_FRONT_SURFACES_H
#include <stdint.h>
#include <string.h>

/* Only a completed swap establishes that a surface belongs to the display. */
enum { FIGHT_FRONT_LIMIT = 8 };
typedef struct { uint32_t addresses[FIGHT_FRONT_LIMIT]; unsigned count; } FightFrontSurfaces;

static unsigned fight_front_index(const FightFrontSurfaces *s, uint32_t address) {
    unsigned i = 0;
    while (i < s->count && s->addresses[i] != address) ++i;
    return i;
}

static void fight_front_present(FightFrontSurfaces *s, uint32_t address) {
    unsigned index = fight_front_index(s, address);
    if (index == s->count) {
        if (index < FIGHT_FRONT_LIMIT) ++s->count;
        else --index;
    }
    memmove(s->addresses + 1, s->addresses, index * sizeof(s->addresses[0]));
    s->addresses[0] = address;
}
#endif
