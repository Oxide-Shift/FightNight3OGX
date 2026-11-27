/* Length guard for the retail XPP gamepad report handler sub_00476080.
 *
 * The handler (this = ECX) reads a 32-bit length at this+0x66, and when it is at least 2 copies length-2 bytes from
 * this+0x34 to this+0x14, then scans eight analog-button bytes. Our emulated pad reports 20 bytes (0x14). In the
 * 81.5 Thor crash (2026-11-26) a garbage length made the copy smear memory forward until it faulted at guest
 * 0x84000000, the end of the contiguous window, taking the worker object at 005C4A84 and other threads with it.
 * Lengths above XID_LEN_MAX are clamped to the real report size so input keeps working and the copy stays inside
 * the object. Each new bad value is logged (up to XID_LOG_MAX events) with the object and the words around the field.
 * Wired in host_dispatch.c through recomp_lookup_manual. Pure: memory goes through callbacks for the host test. */
#pragma once
#include <stdint.h>
#include <stdio.h>

#define XID_LEN_OFFSET 0x66u
#define XID_LEN_MAX    0x40u
#define XID_REPORT_LEN 0x14u
#define XID_LOG_MAX    8u

typedef uint32_t (*XidRead32)(uint32_t va);
typedef void (*XidWrite32)(uint32_t va, uint32_t value);
typedef void (*XidLog)(const char *line);

static unsigned xid_clamped, xid_logged;
static uint32_t xid_last_bad;

/* Guest RAM or the contiguous window, with room for the object. */
static int xid_object_ok(uint32_t obj) {
    return (obj >= 0x00010000u && obj <= 0x04000000u - 0x100u) ||
           (obj >= 0x80000000u && obj <= 0x84000000u - 0x100u);
}

/* Returns 1 when it clamped the length. */
static int xid_guard(uint32_t obj, XidRead32 rd, XidWrite32 wr, XidLog log) {
    if (!xid_object_ok(obj)) return 0;
    uint32_t len = rd(obj + XID_LEN_OFFSET);
    if (len <= XID_LEN_MAX) return 0;
    wr(obj + XID_LEN_OFFSET, XID_REPORT_LEN);
    xid_clamped++;
    if (log && xid_logged < XID_LOG_MAX && len != xid_last_bad) {
        char line[200];
        xid_logged++; xid_last_bad = len;
        snprintf(line, sizeof line, "FNR3/1 XIDLEN obj %08X len %08X clamped %u | +00 %08X %08X %08X %08X +34 %08X %08X\n",
                 obj, len, xid_clamped, rd(obj), rd(obj + 4), rd(obj + 8), rd(obj + 12), rd(obj + 0x34), rd(obj + 0x38));
        log(line);
        snprintf(line, sizeof line, "FNR3/1 XIDLEN +54 %08X %08X %08X %08X %08X %08X %08X %08X\n",
                 rd(obj + 0x54), rd(obj + 0x58), rd(obj + 0x5C), rd(obj + 0x60), rd(obj + 0x64),
                 rd(obj + 0x68), rd(obj + 0x6C), rd(obj + 0x70));
        log(line);
    }
    return 1;
}
