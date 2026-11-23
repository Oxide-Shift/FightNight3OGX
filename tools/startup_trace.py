"""Execute the translated XBE entry and stop on its first kernel request."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import struct
import subprocess

from bringup import XBE_SHA256


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("private_dir", type=Path)
    parser.add_argument("--clang", type=Path, required=True, help="Windows host compiler")
    parser.add_argument("--ndk-clang", type=Path, help="also build the Android diagnostic executable")
    args = parser.parse_args()
    work = args.private_dir.resolve()
    out = work / "startup-trace"
    out.mkdir(exist_ok=True)
    xbe = (work / "default.xbe").read_bytes()
    if hashlib.sha256(xbe).hexdigest() != XBE_SHA256:
        raise SystemExit("Unsupported executable")
    analysis = json.loads((work / "fnr3_analysis.json").read_text())
    image = bytearray(0x10000 + analysis["image_size"])
    header_size = struct.unpack_from("<I", xbe, 0x108)[0]
    image[0x10000:0x10000+header_size] = xbe[:header_size]
    for section in analysis["sections"]:
        va, raw = int(section["virtual_addr"], 16), int(section["raw_addr"], 16)
        size = min(section["raw_size"], section["virtual_size"])
        image[va:va + size] = xbe[raw:raw + size]
    (out / "guest-image.bin").write_bytes(image)
    wanted = {"xbe_entry_point", "sub_002DA428"}
    bodies = {}
    pattern = re.compile(r"^void (xbe_entry_point|sub_002DA428)\(void\)\s*\{.*?^\}", re.M | re.S)
    for unit in sorted((work / "gen").glob("recomp_[0-9]*.c")):
        for match in pattern.finditer(unit.read_text(encoding="utf-8")):
            bodies[match[1]] = match[0]
        if bodies.keys() == wanted:
            break
    if bodies.keys() != wanted:
        raise SystemExit("Missing translated startup routines")
    (out / "entry.inc").write_text("\n".join(bodies.values()), encoding="utf-8")
    trace = (Path(__file__).resolve().parents[1] / "native" / "startup_trace.c").resolve()
    flags = ["-O0", "-I", work / "gen", "-I", out, trace]
    exe = out / "startup-trace.exe"
    subprocess.run([args.clang, *flags, "-o", exe], check=True)
    subprocess.run([exe, out / "guest-image.bin"], check=True)
    if args.ndk_clang:
        subprocess.run([args.ndk_clang, "--target=aarch64-linux-android30", *flags,
                        "-o", out / "startup-trace-android"], check=True)
        print("Linked the Android ARM64 startup trace.")


if __name__ == "__main__":
    main()
