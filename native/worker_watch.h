/* Diagnostics for the 001F337E worker crash family (79.4 Warehouse, 81.5 round four).
 *
 * The retail worker at sub_001F31E6 calls slot +0x18 on the object held at guest global 005C4A84 (expected vtable
 * 004D48B8). In both reports that object's vtable read zero while the stop flag at 005C4A8C was still clear, and other
 * threads called garbage at the same moment. These helpers record when the object pointer or its vtable change (polled
 * once per presented frame) and format compact lines for the 40-line crash-report tail (each line under 200 chars).
 * Pure: memory is read through a callback, so the host tests run them against a fake guest. */
#pragma once
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#define WW_OBJECT_GLOBAL 0x005C4A84u
#define WW_STOP_FLAG     0x005C4A8Cu
#define WW_VTABLE        0x004D48B8u
#define WW_HISTORY       4u

typedef uint32_t (*WwRead32)(uint32_t va);
typedef struct { uint32_t frame, obj, vt; } WwSample;
typedef struct {
    int seen;
    uint32_t obj, vt;              /* last observed values */
    unsigned changes;              /* changes after the first observation */
    WwSample first;                /* first observation */
    WwSample recent[WW_HISTORY];   /* ring of the latest changes */
} WorkerWatch;

/* Guest RAM (stack/heap) or the contiguous window, with room to read a few words. */
static int ww_readable(uint32_t va) {
    return (va >= 0x00010000u && va <= 0x04000000u - 0x80u) ||
           (va >= 0x80000000u && va <= 0x84000000u - 0x80u);
}

/* Returns 1 when the object pointer or its vtable changed since the last poll. */
static int ww_poll(WorkerWatch *w, uint32_t frame, WwRead32 rd) {
    uint32_t obj = rd(WW_OBJECT_GLOBAL);
    uint32_t vt = ww_readable(obj) ? rd(obj) : 0;
    WwSample s = {frame, obj, vt};
    if (!w->seen) { w->seen = 1; w->obj = obj; w->vt = vt; w->first = s; return 0; }
    if (obj == w->obj && vt == w->vt) return 0;
    w->recent[w->changes % WW_HISTORY] = s;
    w->changes++;
    w->obj = obj; w->vt = vt;
    return 1;
}

/* "FNR3/1 WORKERWATCH n=<changes> first f<frame>:<obj>/<vt> then f..:../.. (oldest first)" */
static int ww_format_history(const WorkerWatch *w, char *out, size_t size) {
    int n = snprintf(out, size, "FNR3/1 WORKERWATCH n=%u", w->changes);
    if (!w->seen) return n + snprintf(out + n, size - (size_t)n, " unseen\n");
    n += snprintf(out + n, size - (size_t)n, " first f%u:%08X/%08X", w->first.frame, w->first.obj, w->first.vt);
    unsigned kept = w->changes < WW_HISTORY ? w->changes : WW_HISTORY;
    for (unsigned i = 0; i < kept && (size_t)n < size; ++i) {
        const WwSample *s = &w->recent[(w->changes - kept + i) % WW_HISTORY];
        n += snprintf(out + n, size - (size_t)n, " f%u:%08X/%08X", s->frame, s->obj, s->vt);
    }
    if ((size_t)n < size) n += snprintf(out + n, size - (size_t)n, "\n");
    return n;
}

/* "FNR3/1 <label> <base>: w0 w1 ..." for up to 16 words; unreadable ranges print "unreadable". */
static int ww_format_words(char *out, size_t size, const char *label, uint32_t base, unsigned count, WwRead32 rd) {
    if (count > 16) count = 16;
    int n = snprintf(out, size, "FNR3/1 %s %08X:", label, base);
    if (!ww_readable(base) || !ww_readable(base + count * 4u))
        return n + snprintf(out + n, size - (size_t)n, " unreadable\n");
    for (unsigned i = 0; i < count && (size_t)n < size; ++i)
        n += snprintf(out + n, size - (size_t)n, " %08X", rd(base + i * 4u));
    if ((size_t)n < size) n += snprintf(out + n, size - (size_t)n, "\n");
    return n;
}
