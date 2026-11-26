"""Check a set of six private Windows bout captures and report gameplay and pacing.

Pass a directory containing one subdirectory per venue (an optional common prefix
is accepted). Each capture must include rates.json and retail.log. The expected
input is a full-frame capture with pixel comparisons and punch counters enabled.
"""
import argparse
import json
import math
from pathlib import Path
import re

VENUES = {"warehouse":"ware", "windy":"wcb", "garden":"msg", "palace":"stp", "staples":"sta", "aragon":"ara"}


def check(directory, asset):
    rate = json.loads((directory/"rates.json").read_text())
    log = (directory/"retail.log").read_text(errors="replace").replace("\\", "/").lower()
    if f"/render/enviro/{asset}.viv" not in log or "fnr3/1 card 1" not in log:
        raise ValueError(f"{directory.name}: requested venue or first round missing")
    if rate["punches"] < 1 or rate["seconds"] < 20 or rate["pixel_pairs"] < 1:
        raise ValueError(f"{directory.name}: insufficient exchange or visual data")
    if rate["identical_pixels"] == rate["pixel_pairs"]:
        raise ValueError(f"{directory.name}: frozen image")
    if rate["skipped"] != 0 or rate["pixel_pairs"] != rate["frames"]:
        raise ValueError(f"{directory.name}: incomplete frame observation")
    if not math.isclose(rate["fps"], rate["frames"]/rate["seconds"], rel_tol=1e-6):
        raise ValueError(f"{directory.name}: incompatible counters")
    packets = re.findall(r"^fnr3/1 pace (\d+) (\d+) (\d+) (\d+) (\d+) (\d+)\s*$",log,re.M)
    if not packets:
        raise ValueError(f"{directory.name}: no in-ring CPU and GPU wait measurements")
    return {"fps": round(rate["fps"], 2), "p95_ms": round(rate["p95"], 2),
            "p99_ms": round(rate["p99"], 2), "max_ms": round(rate["max_ms"], 2),
            "thrown": rate["punches"], "frames": rate["frames"],
            "unchanged_pairs": rate["identical_pixels"], "over_33ms": rate["over33"],
            "exchange_work_p95_ms_last_window": int(packets[-1][1])/1000,
            "exchange_gpu_fence_p95_ms_last_window": int(packets[-1][2])/1000}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("directory", type=Path)
    parser.add_argument("--prefix", default="")
    args = parser.parse_args()
    results = {v: check(args.directory/(args.prefix+v), asset) for v,asset in VENUES.items()}
    print(json.dumps(results, indent=2))

