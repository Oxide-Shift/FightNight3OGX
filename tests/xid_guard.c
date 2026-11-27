/* XID report length guard: real lengths pass untouched, garbage is clamped to the report size and logged once per
   value (bounded), and objects outside guest memory are never touched. */
#include <assert.h>
#include <stdio.h>
#include <string.h>
#include "../native/xid_guard.h"

#define OBJ 0x817E2000u
static uint32_t words[0x100 / 4];
static int writes;
static char last[2][200];
static int lines;
static uint32_t rd(uint32_t va) { assert(va >= OBJ && va < OBJ + sizeof words); return words[(va - OBJ) / 4]; }
static void wr(uint32_t va, uint32_t v) { assert(va == OBJ + XID_LEN_OFFSET); writes++;
    /* +0x66 is unaligned: model it as the two halves it spans. */
    words[0x64 / 4] = (words[0x64 / 4] & 0x0000FFFFu) | (v << 16);
    words[0x68 / 4] = (words[0x68 / 4] & 0xFFFF0000u) | (v >> 16); }
static uint32_t len_read(void) { return (words[0x64 / 4] >> 16) | (words[0x68 / 4] << 16); }
static void set_len(uint32_t v) { words[0x64 / 4] = (words[0x64 / 4] & 0xFFFFu) | (v << 16); words[0x68 / 4] = (words[0x68 / 4] & 0xFFFF0000u) | (v >> 16); }
static uint32_t rd_unaligned(uint32_t va) { return va == OBJ + XID_LEN_OFFSET ? len_read() : rd(va); }
static void logline(const char *s) { assert(strlen(s) < 200 && s[strlen(s) - 1] == '\n'); strcpy(last[lines++ % 2], s); }

int main(void) {
    for (unsigned i = 0; i < sizeof words / 4; ++i) words[i] = 0xA5000000u + i;
    /* The real 20-byte report and anything up to the limit pass untouched. */
    for (uint32_t len = 0; len <= XID_LEN_MAX; ++len) {
        set_len(len);
        assert(!xid_guard(OBJ, rd_unaligned, wr, logline) && len_read() == len);
    }
    assert(writes == 0 && lines == 0);
    /* Garbage (the size of the 81.5 smear) is clamped to 0x14 and logged with the object and nearby words. */
    set_len(0x03F00000u);
    assert(xid_guard(OBJ, rd_unaligned, wr, logline) && len_read() == XID_REPORT_LEN && writes == 1);
    assert(lines == 2 && strstr(last[0], "FNR3/1 XIDLEN obj 817E2000 len 03F00000 clamped 1"));
    assert(!strncmp(last[1], "FNR3/1 XIDLEN +54 ", 18));
    /* The clamped value now passes; a repeat of the same bad value clamps again without another log. */
    assert(!xid_guard(OBJ, rd_unaligned, wr, logline));
    set_len(0x03F00000u);
    assert(xid_guard(OBJ, rd_unaligned, wr, logline) && lines == 2 && xid_clamped == 2);
    /* Distinct bad values log, but never more than XID_LOG_MAX events. */
    for (uint32_t i = 0; i < 20; ++i) { set_len(0x1000u + i); assert(xid_guard(OBJ, rd_unaligned, wr, logline)); }
    assert(xid_logged == XID_LOG_MAX && lines == (int)(2 * XID_LOG_MAX));
    /* Objects outside guest RAM / the contiguous window are left alone (no read, no write). */
    int before = writes;
    assert(!xid_guard(0, rd_unaligned, wr, logline) && !xid_guard(0x3FA66666u, rd_unaligned, wr, logline));
    assert(!xid_guard(0x83FFFFF0u, rd_unaligned, wr, logline) && writes == before);
    /* A null logger still clamps. */
    set_len(0xFFFFFFFFu);
    assert(xid_guard(OBJ, rd_unaligned, wr, NULL) && len_read() == XID_REPORT_LEN);
    puts("XID report length guard: pass-through, clamp, bounded logging and address checks passed");
    return 0;
}
