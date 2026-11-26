"""Compare a private Alpha 76 translated directory to the optimized guest on Windows."""
import argparse
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
from compact_dispatch import compact_dispatch
from guest_hotpaths import matrix_blend


def check(gen, cc, out, trace):
    def build(files, name):
        exe = out / (name + ".exe")
        subprocess.run([cc, "-O2", "-ffp-contract=off", "-fno-strict-aliasing", "-fwrapv",
                        "-I", gen, "-I", ROOT / "native", *files, "-o", exe], check=True)
        return exe

    subprocess.run([build([ROOT/"tests/matrix_alias.c"], "matrix_alias")], check=True)
    flush = build([ROOT/"native/gpu_flush.c", ROOT/"tests/gpu_flush_handoff.c"], "flush")
    for setting in ([], ["FNR3_PFB_POLL=1"], ["RECOMP_GUEST_LOCK=1"]):
        subprocess.run([flush, *setting], timeout=20, check=True)
    chunk = "recomp_0087.c"
    shutil.copyfile(gen / chunk, out / chunk)
    matrix_blend(out)
    functions = []
    for label, folder in (("baseline", gen), ("candidate", out)):
        source = (folder / chunk).read_text(encoding="utf-8")
        start = source.index("void sub_002AB870(void)\n{")
        end = source.index("\n}", start) + 2
        func = source[start:end].replace("sub_002AB870(void)", f"matrix_{label}(void)")
        header = '#define RECOMP_GENERATED_CODE\n#include "recomp_funcs.h"\n'
        if label == "candidate":
            header += '#include "matrix_stream.h"\n'
        f = out / (label + ".c")
        f.write_text(header + func)
        functions.append(f)
    subprocess.run([build([*functions, ROOT/"tests/matrix_equivalence.c"], "matrix")], check=True)
    name = "recomp_dispatch.c"
    shutil.copyfile(gen/name, out/name)
    compact_dispatch(out)
    tables = []
    for label, folder in (("baseline", gen), ("candidate", out)):
        source = (folder/name).read_text(encoding="utf-8").replace('#include "recomp_funcs.h"', '#include <stdint.h>')
        source = source.replace("(recomp_func_t)xbe_entry_point", "(recomp_func_t)sub_002D8865")
        source, count = re.subn(r"\(recomp_func_t\)sub_([A-Fa-f0-9]{8})", r"(recomp_func_t)(uintptr_t)0x\1u", source)
        if count != 39974:
            raise ValueError(f"Unexpected number of retail targets: {count}")
        for symbol in ("recomp_lookup","recomp_dispatch_init","recomp_get_count","recomp_call_all","recomp_dispatch_flat_bytes"):
            source = source.replace(symbol, label + "_" + symbol)
        f = out / (label + "_lookup.c")
        f.write_text(source)
        tables.append(f)
    subprocess.run([build([*tables, ROOT/"tests/dispatch_equivalence.c"], "lookup"), *([trace] if trace else [])], check=True)


if __name__ == "__main__":
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("original", type=Path)
    p.add_argument("compiler", type=Path)
    p.add_argument("--trace", type=Path)
    args = p.parse_args()
    with tempfile.TemporaryDirectory() as temp:
        check(args.original.resolve(), args.compiler.resolve(), Path(temp), args.trace)

