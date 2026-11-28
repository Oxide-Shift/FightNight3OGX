"""Write a region variant of the native sources, translation fixes and runtime patches.

The US executable is the source of truth. A variant tree (native/, tools/, patches/) for another release of the
same game is generated from it by rewriting every guest address through a verified map:

    python tools/region_variant.py pal OUT_DIR

native/<region>_addresses.json lists each US guest address the code uses and its counterpart in that release (each
one referenced by identical code, inside an identical function, or verified by instruction or table pattern; see
D:/Projects/FN3OGX/backlog/pal-support.md). The run fails if any guest-address literal has no entry, so a new US
address cannot silently reach a variant build. Formatting, case, 'u' suffixes and line endings are preserved.
"""
import json
import re
import shutil
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
# Files that name guest addresses. Patches are rewritten on added lines only (context lines are toolkit source).
SOURCES = ["native/*.c", "native/*.h", "native/observed_functions.json", "tools/bringup.py",
           "tools/guest_hotpaths.py", "tools/compact_dispatch.py"]
PATCHES = "patches/*.patch"
LITERAL = re.compile(rb"(?<![0-9A-Za-z_])(?:(0x)([0-9A-Fa-f]{5,8})(u?)|(sub|loc)_([0-9A-Fa-f]{8}))(?![0-9A-Za-z])")
LABEL = re.compile(rb"""(?<=["'])(00[0-5][0-9A-Fa-f]{5})(?=["'])""")
GUEST = (0x11000, 0x5E6360)  # US image: first section to end of image


def load(region):
    spec = json.loads((ROOT / "native" / f"{region}_addresses.json").read_text(encoding="utf-8"))
    addresses = {int(k, 16): int(v, 16) for k, v in spec["addresses"].items()}
    labels = {k.upper(): v.upper() for k, v in spec["labels"].items()}
    skip = {int(k, 16) for k in spec["not_addresses"]}
    return spec, addresses, labels, skip


def rewrite(data, addresses, labels, skip, where, missing, added_lines_only=False):
    def literal(m):
        digits = m.group(2) or m.group(5)
        value = int(digits, 16)
        if not (GUEST[0] <= value < GUEST[1]) or value in skip:
            return m.group(0)
        if value not in addresses:
            missing.add(f"{where} 0x{value:08X}")
            return m.group(0)
        new = (b"%0*X" if digits.upper() == digits else b"%0*x") % (len(digits), addresses[value])
        if len(new) > len(digits):
            raise SystemExit(f"{where}: 0x{addresses[value]:X} does not fit 0x{digits.decode()}")
        if m.group(2):
            return m.group(1) + new + m.group(3)
        return m.group(4) + b"_" + new

    def label(m):
        key = m.group(1).decode().upper()
        if key not in labels:
            value = int(key, 16)
            if GUEST[0] <= value < GUEST[1] and value not in skip:
                missing.add(f"{where} label {key}")
            return m.group(0)
        return labels[key].encode()

    if not added_lines_only:
        return LABEL.sub(label, LITERAL.sub(literal, data))
    out = []
    for line in data.splitlines(keepends=True):
        if line.startswith(b"+") and not line.startswith(b"+++"):
            line = LABEL.sub(label, LITERAL.sub(literal, line))
        out.append(line)
    return b"".join(out)


def variant(region, out):
    spec, addresses, labels, skip = load(region)
    out = Path(out).resolve()
    if out == ROOT or ROOT in out.parents:
        raise SystemExit("Write the variant outside the project")
    if out.exists():
        shutil.rmtree(out)
    for name in ("native", "tools", "patches"):
        # The private US address layout (disc_layout.json*) never belongs in another release's tree.
        shutil.copytree(ROOT / name, out / name, ignore=shutil.ignore_patterns("__pycache__", "disc_layout.json*"))
    missing = set()
    for pattern in SOURCES:
        for path in sorted(out.glob(pattern)):
            if path.name.endswith("_addresses.json"):
                continue
            path.write_bytes(rewrite(path.read_bytes(), addresses, labels, skip, path.name, missing))
    for path in sorted(out.glob(PATCHES)):
        path.write_bytes(rewrite(path.read_bytes(), addresses, labels, skip, path.name, missing, True))
    if missing:
        raise SystemExit("No verified address for: " + ", ".join(sorted(missing)))
    # Executable identity: hash and size checks in the translator and the host.
    us_hash, us_size = spec["us_xbe_sha256"].encode(), str(spec["us_xbe_bytes"]).encode()
    for rel in ("tools/bringup.py", "native/host_main.c"):
        path = out / rel
        data = path.read_bytes().replace(us_hash, spec["xbe_sha256"].encode()).replace(us_size, str(spec["xbe_bytes"]).encode())
        path.write_bytes(data)
    # No extra seeds: a seed becomes a function start and can split its host (a 77-entry switch lost 72 cases). Entry
    # points the other release's sweep misses are recorded as aliases in its disc layout instead (see pal-support.md).
    print(f"{region} variant written to {out}")
    return out


if __name__ == "__main__":
    if len(sys.argv) != 3:
        raise SystemExit("usage: region_variant.py REGION OUT_DIR")
    variant(sys.argv[1], sys.argv[2])
