"""Verified retail code-generation passes for the graphics handoff and matrix blend."""
import hashlib
from pathlib import Path
import re


def chunk(directory, preferred, *markers):
    """The generated file holding a marker: the known split first, then every chunk (other releases split differently)."""
    first = directory / preferred
    for path in [first] + [p for p in sorted(directory.glob("recomp_*.c")) if p != first]:
        if path.is_file():
            text = path.read_text(encoding="utf-8")
            if any(marker in text for marker in markers):
                return path
    return first


# Verified bodies of the two weighted-transform loops: (US, PAL) by US label. tools/region_variant.py rewrites
# the labels; the PAL bodies (Europe EA13401E) equal the US bodies once their addresses are mapped back.
MATRIX_BODIES = {
    "002AB8E0": ("12bfbd7e87e29c3beb346387c7f1cf60b7c011278364d64026ce849d7a3c9e4e",
                 "9432a2dbc7605db7cc8f38ddace3187f2442d5938d507ca4466f543cc58a2cd1"),
    "002ABA10": ("3ca6da5732e8fe61de2284f9d8a7b203ff185967eebb0d66b21129c99d7a07ee",
                 "545f3d7a08eb823936bc970f6862991217047e0df085fa5d0531e97e469202b0"),
}


def gpu_flush(directory):
    poll = "    if (TEST_NZ(_fa, _fb)) goto loc_0043B540; /* jne: not equal / not zero */"
    path = chunk(directory, "recomp_0241.c", poll, "fight_gpu_flush_wait(&MEM32")
    text = path.read_text(encoding="utf-8")
    if "fight_gpu_flush_wait(&MEM32" in text:
        return
    if text.count(poll) != 1 or "loc_0043B540: ;" not in text:
        raise ValueError("Unexpected D3D PFB flush poll")
    replacement = """    if (TEST_NZ(_fa, _fb)) {
        extern void fight_gpu_flush_wait(volatile uint32_t *);
        fight_gpu_flush_wait(&MEM32(eax + 0x100410));
        goto loc_0043B540;
    }"""
    path.write_text(text.replace(poll, replacement), encoding="utf-8")



def matrix_blend(directory):
    """Keep six SSE values local between the yield and the final matrix store.

    Both loops start by defining all six registers. The verified region has
    internal branches, no guest calls, and a single exit. Publish every register
    at that exit so the next yield and both retail callees see identical state.
    """
    loops = (("002AB8E0", "esi"), ("002ABA10", "edx"))
    path = chunk(directory, "recomp_0087.c", f"loc_{loops[0][0]}: ;\nRECOMP_LOOP_YIELD();", '#include "matrix_stream.h"')
    text = path.read_text(encoding="utf-8")
    if '#include "matrix_stream.h"' in text:
        return
    for label, dest in loops:
        fingerprints = MATRIX_BODIES[label]
        anchor = f"loc_{label}: ;\nRECOMP_LOOP_YIELD();"
        start = text.index(anchor) + len(anchor)
        last = f"XMM_STORE({dest} + 0x10, xmm2); /* movaps */"
        end = text.index(last, start) + len(last)
        body = text[start:end]
        if hashlib.sha256(body.encode()).hexdigest() not in fingerprints:
            raise ValueError(f"Unexpected weighted transform instructions at {label}")
        registers = [f"xmm{i}" for i in range(6)]
        local = re.sub(r"\bxmm([0-5])\b", r"lane\1", body)
        local = local.replace("XMM_MEM(", "fight_matrix_load(").replace("XMM_STORE(", "fight_matrix_store(")
        publish = "\n".join(f"    {r} = lane{i};" for i, r in enumerate(registers))
        text = text[:start] + "\n    {\n    RecompXmm " + ", ".join(f"lane{i}" for i in range(6)) + ";" + local + "\n" + publish + "\n    }" + text[end:]
    text = text.replace('#include "recomp_funcs.h"', '#include "recomp_funcs.h"\n#include "matrix_stream.h"', 1)
    path.write_text(text, encoding="utf-8")

