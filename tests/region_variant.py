"""The PAL variant tree is complete and points at the PAL executable (tools/region_variant.py).

Fails when a guest address in native/, the translation fixes or the runtime patches has no verified PAL entry in
native/pal_addresses.json, so new US-only addresses are caught before a PAL kit is built.
"""
import json
import sys
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "tools"))
import region_variant  # noqa: E402


def main():
    spec = json.loads((ROOT / "native/pal_addresses.json").read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory() as temp:
        out = region_variant.variant("pal", Path(temp) / "pal")
        bringup = (out / "tools/bringup.py").read_text(encoding="utf-8")
        assert spec["xbe_sha256"] in bringup and spec["us_xbe_sha256"] not in bringup
        assert str(spec["us_xbe_bytes"]) not in bringup and str(spec["xbe_bytes"]) in bringup
        host = (out / "native/host_main.c").read_text(encoding="utf-8")
        assert f"size = {spec['xbe_bytes']}" in host and "thunk != 0x46F8E0" in host
        hot = (out / "tools/guest_hotpaths.py").read_text(encoding="utf-8")
        assert '("002A53B0", "esi")' in hot and "loc_0042ED80" in hot and "002AB8E0" not in hot
        assert '("00128400", "001287F0")' in bringup
        fence = (out / "patches/fight-fence-wait.patch").read_text(encoding="utf-8")
        assert "0x0043AC58u" in fence and "0x004461B8u" not in fence
        status = (out / "patches/fight-thread-status.patch").read_text(encoding="utf-8")
        assert "0x002CEBA3u" in status and "0x002CEBC8u" in status
        seeds = json.loads((out / "native/observed_functions.json").read_text(encoding="utf-8"))
        base = json.loads((ROOT / "native/observed_functions.json").read_text(encoding="utf-8"))
        # Same entries, remapped: the PAL disc layout pins this file by hash, so nothing may be added.
        assert len(seeds) == len(base) and seeds != base and all(s["start"].startswith("0x") for s in seeds)
        assert not list((out / "native").glob("disc_layout.json*")), "US layout copied into the PAL tree"
        # The US sources are unchanged by building a variant.
        assert spec["us_xbe_sha256"] in (ROOT / "tools/bringup.py").read_text(encoding="utf-8")
    print(f"PAL variant: {len(spec['addresses'])} mapped addresses, seeds remapped, hash and patches checked.")


if __name__ == "__main__":
    main()
