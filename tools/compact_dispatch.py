"""Build the retail guest's read-only indirect-call index at translation time."""
from pathlib import Path
import re


def compact_dispatch(directory: Path):
    path = directory / "recomp_dispatch.c"
    text = path.read_text(encoding="utf-8")
    if "static const uint16_t fight_dispatch_slots[" in text:
        return
    start = text.index("static const uint32_t g_flat_base")
    start = text.rindex("/*", 0, text.index(" * Flat, directly-indexed dispatch.", 0, start))
    end = text.index("/* Get the number of registered functions", start)
    entries = [int(key, 16) for key in re.findall(
        r"^\s*\{\s*0x([0-9a-fA-F]+)u,\s*\(recomp_func_t\)", text[:start], re.MULTILINE)]
    if not entries or len(entries) >= 65536 or entries != sorted(set(entries)):
        raise ValueError("Expected a sorted, unique retail dispatch table with 16-bit indices")
    # Each nonzero slot indexes the original table. Keeping occupancy below half
    # bounds the typical probe length even for closely spaced guest functions.
    size = 1 << (2 * len(entries) - 1).bit_length()
    shift = 32 - (size.bit_length() - 1)
    slots = [0] * size
    for index, address in enumerate(entries, 1):
        slot = (((address ^ (address >> 16)) * 2654435761) & 0xffffffff) >> shift
        while slots[slot]:
            slot = (slot + 1) & (size - 1)
        slots[slot] = index
    data = ",\n".join(",".join(map(str, slots[i:i+32])) for i in range(0, size, 32))
    code = f"""static const uint16_t fight_dispatch_slots[{size}] = {{
{data}
}};

int recomp_dispatch_init(void) {{ return 1; }}
size_t recomp_dispatch_flat_bytes(void) {{ return sizeof fight_dispatch_slots; }}

/* The original entry verifies every hit. Empty slots terminate misses, including
 * non-code addresses that the caller may next resolve as kernel functions. */
recomp_func_t recomp_lookup(uint32_t va) {{
    uint32_t slot = ((va ^ (va >> 16)) * UINT32_C(2654435761)) >> {shift};
    for (;;) {{
        unsigned index = fight_dispatch_slots[slot];
        if (!index) return NULL;
        const recomp_entry_t *entry = &g_recomp_table[index - 1];
        if (entry->xbox_va == va) return entry->func;
        slot = (slot + 1) & {size - 1}u;
    }}
}}

"""
    path.write_text(text[:start] + code + text[end:], encoding="utf-8")

