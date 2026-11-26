"""Summarize a Windows retail exchange captured with per-flip timings and CPU counters."""
import argparse
import csv
import gzip
import json
import math
from pathlib import Path
import re

FIELDS = ["bout", "tick", "thrown", "landed", "interval_us", "work_us", "fence_us", "submit_us", "copy_us"]
FRAME = re.compile(r"^\[FRAMEQA\] " + " ".join([r"(\d+)"] * len(FIELDS)) + r"\s*$", re.M)


def percentile(rows, field, fraction):
    if not rows:
        return None
    values = sorted(row[field] for row in rows)
    return round(values[math.ceil(len(values)*fraction)-1]/1000, 3)


def summarize(directory):
    rate = json.loads((directory/"rates.json").read_text())
    text = (directory/"slice.log").read_text(errors="replace")
    text = re.sub(r"(presentation interval [123] vblank\(s\))\\n", r"\1\n", text)
    rows = [dict(zip(FIELDS, map(int, m.groups()))) for m in FRAME.finditer(text)]
    if not rows:
        raise ValueError("No complete per-flip records in " + str(directory))
    exchanges = [b for a,b in zip(rows,rows[1:]) if a["bout"] == b["bout"] and
                 (a["thrown"], a["landed"]) != (b["thrown"], b["landed"])]
    summary = dict(run=directory.name, completed_frames=rate["frames"], seconds=round(rate["seconds"], 4),
                   fps=round(rate["fps"],3), published_observed=rate["seen"], skipped=rate["skipped"],
                   identical_adjacent=rate["identical_pixels"], observer_p95_ms=round(rate["p95"],3),
                   observer_p99_ms=round(rate["p99"],3), observer_worst_ms=round(rate["max_ms"],3),
                   observer_over20=rate["over20"], observer_over33=rate["over33"], punches=rate.get("punches"),
                   native_samples=len(rows), punch_event_frames=len(exchanges),
                   native_p95_ms=percentile(rows,"interval_us",.95), native_p99_ms=percentile(rows,"interval_us",.99),
                   work_p95_ms=percentile(rows,"work_us",.95), punch_work_p95_ms=percentile(exchanges,"work_us",.95),
                   fence_p95_ms=percentile(rows,"fence_us",.95), submit_p95_ms=percentile(rows,"submit_us",.95),
                   copy_p95_ms=percentile(rows,"copy_us",.95))
    summary.update({f"{name}_cpu_s": rate.get("cpu_seconds", {}).get(name)
                    for name in ("process", "guest", "render")})
    return summary, rows


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runs", nargs="+", type=Path)
    parser.add_argument("--summary", required=True, type=Path)
    parser.add_argument("--frames", required=True, type=Path)
    args = parser.parse_args()
    with args.summary.open("w", newline="", encoding="utf-8") as totals, gzip.open(
            args.frames, "wt", newline="", encoding="utf-8") as frames:
        flip = csv.DictWriter(frames, fieldnames=["run", *FIELDS])
        flip.writeheader()
        writer = None
        for run in args.runs:
            summary, rows = summarize(run)
            if writer is None:
                writer = csv.DictWriter(totals, fieldnames=list(summary))
                writer.writeheader()
            writer.writerow(summary)
            flip.writerows(dict(run=run.name, **row) for row in rows)

