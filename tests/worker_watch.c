/* Worker-watch diagnostics: change history and crash-tail lines against a fake guest. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "../native/worker_watch.h"

static uint32_t global_obj, obj_words[64], base_words[64];
static uint32_t fake_read(uint32_t va) {
    if (va == WW_OBJECT_GLOBAL) return global_obj;
    if (va >= global_obj && va < global_obj + sizeof obj_words) return obj_words[(va - global_obj) / 4];
    if (va >= 0x00100000u && va < 0x00100000u + sizeof base_words) return base_words[(va - 0x00100000u) / 4];
    return 0xDEADBEEFu;
}

int main(void) {
    char line[200];
    WorkerWatch w;
    memset(&w, 0, sizeof w);

    /* Unseen watch formats without history. */
    ww_format_history(&w, line, sizeof line);
    assert(strstr(line, "n=0 unseen"));

    /* The 81.5 report: object 817E25C0 healthy, then its vtable reads zero. */
    global_obj = 0x817E25C0u;
    obj_words[0] = WW_VTABLE;
    assert(!ww_poll(&w, 100, fake_read));             /* first observation is not a change */
    assert(!ww_poll(&w, 101, fake_read));
    obj_words[0] = 0;
    assert(ww_poll(&w, 42470, fake_read));
    assert(!ww_poll(&w, 42471, fake_read));
    assert(w.changes == 1 && w.vt == 0);
    ww_format_history(&w, line, sizeof line);
    assert(!strcmp(line, "FNR3/1 WORKERWATCH n=1 first f100:817E25C0/004D48B8 f42470:817E25C0/00000000\n"));

    /* An unreadable pointer (the global itself zeroed) records a zero vtable without reading it. */
    global_obj = 0;
    assert(ww_poll(&w, 42472, fake_read) && w.obj == 0 && w.vt == 0);

    /* History keeps the latest four, oldest first, and every line stays inside the 200-char tail limit. */
    for (uint32_t i = 0; i < 6; ++i) {
        global_obj = 0x81000000u + i * 0x1000u;
        obj_words[0] = WW_VTABLE + i;
        assert(ww_poll(&w, 50000 + i, fake_read));
    }
    assert(w.changes == 8);
    int n = ww_format_history(&w, line, sizeof line);
    assert(n < 200 && line[strlen(line) - 1] == '\n');
    assert(strstr(line, "f50002:81002000/004D48BA") && strstr(line, "f50005:81005000/004D48BD"));
    assert(!strstr(line, "f50001:") && !strstr(line, "f42470:"));
    assert(strstr(line, "f50002") < strstr(line, "f50005"));

    /* Word dumps: 16 words fit, unreadable bases say so instead of reading. */
    for (int i = 0; i < 16; ++i) base_words[i] = 0x11110000u + (uint32_t)i;
    n = ww_format_words(line, sizeof line, "OBJ-40", 0x00100000u, 16, fake_read);
    assert(n < 200 && !strncmp(line, "FNR3/1 OBJ-40 00100000: 11110000 11110001", 41));
    assert(strstr(line, " 1111000F\n"));
    ww_format_words(line, sizeof line, "OBJ", 0, 16, fake_read);
    assert(!strcmp(line, "FNR3/1 OBJ 00000000: unreadable\n"));
    ww_format_words(line, sizeof line, "OBJ", 0x83FFFFC0u, 16, fake_read);
    assert(strstr(line, "unreadable"));
    assert(ww_readable(0x817E25C0u) && ww_readable(0x01335F30u) && !ww_readable(0x3FA66666u) && !ww_readable(0x8));

    puts("Worker watch history, crash-tail lines and unreadable guards passed");
    return 0;
}
