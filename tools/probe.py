"""Compile and test one translated Fight Night routine in isolation."""
import argparse
from pathlib import Path
import re
import struct
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("private_dir", type=Path, help="directory containing gen/")
    parser.add_argument("--clang", type=Path, required=True, help="desktop LLVM with a host sysroot")
    args = parser.parse_args()
    work = args.private_dir.resolve()
    out = work / "leaf-probe"
    out.mkdir(exist_ok=True)
    function = "sub_00021A90"
    pattern = re.compile(r"^void " + function + r"\(void\)\s*\{.*?^\}", re.M | re.S)
    for chunk in sorted((work / "gen").glob("recomp_[0-9]*.c")):
        match = pattern.search(chunk.read_text(encoding="utf-8"))
        if match:
            break
    else:
        raise SystemExit(f"Missing translated function {function}")
    # Use only the register and memory interface exercised by this function.
    # Four-byte loads have aligned, in-bounds offsets in every fixture.
    source = out / "probe.c"
    source.write_text("""
typedef __UINT32_TYPE__ u32;
typedef u32 uint32_t;
static u32 memory[64], eax, ecx, esp;
#define MEM32(addr) memory[(addr) / 4]
""" + match.group() + """
int run_probe(u32 input) {
    ecx = 32; esp = 160; eax = ~input;
    MEM32(ecx) = 17; MEM32(ecx+4) = ~input; MEM32(ecx+8) = 23;
    MEM32(esp) = 29; MEM32(esp+4) = input;
    sub_00021A90();
    return eax == input && MEM32(36) == input && esp == 168 &&
           ecx == 32 && MEM32(32) == 17 && MEM32(40) == 23 &&
           MEM32(160) == 29 && MEM32(164) == input;
}
#ifdef HOST_TEST
int main(void) {
    const u32 values[] = {0, 1, 0x7fffffffU, 0x80000000U, 0xffffffffU, 0x12345678U};
    for (unsigned i = 0; i < sizeof(values)/sizeof(*values); ++i)
        if (!run_probe(values[i])) return 1;
    return 0;
}
#endif
""", encoding="utf-8")
    host = out / "probe.exe"
    arm = out / "probe-android-arm64.o"
    subprocess.run([args.clang, "-O0", "-DHOST_TEST", source, "-o", host], check=True)
    subprocess.run([host], check=True)
    subprocess.run([args.clang, "--target=aarch64-linux-android30", "-ffreestanding",
                    "-fPIC", "-O0", "-c", source, "-o", arm], check=True)
    data = arm.read_bytes()
    if data[:6] != b"\x7fELF\x02\x01" or struct.unpack_from("<HH", data, 16) != (1, 183):
        raise SystemExit("Expected a little-endian AArch64 ELF64 relocatable object")
    print("Six desktop fixtures passed. Android-target AArch64 ELF object verified.")


if __name__ == "__main__":
    main()
