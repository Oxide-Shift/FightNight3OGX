"""Report physical presentation intervals bracketed by live boxing observations."""
import argparse
from collections import defaultdict
import json
from pathlib import Path

from record_thor_health import interval_stats


def rows(path):
    with path.open(encoding="utf-8") as stream:
        for line in stream:
            yield json.loads(line)


def round_windows(observations):
    start = last = None
    for row in observations:
        if row.get("scene") == "ROUND" and row.get("round") and row.get("clock", 0) > 0:
            if last and row["round"] == last["round"] and row["clock"] <= last["clock"] and (
                    row["host_query_ns"][0] - last["host_query_ns"][1] < 3_500_000_000):
                last = row
                continue
            next_start = row
        else:
            next_start = None
        if start is not None and last is not start:
            yield start["host_query_ns"][1], last["host_query_ns"][0], start["round"]
        start = last = next_start
    if start is not None and last is not start:
        yield start["host_query_ns"][1], last["host_query_ns"][0], start["round"]


def live_intervals(observations, polls):
    windows = list(round_windows(observations))
    previous = {}
    intervals = defaultdict(list)
    for poll in polls:
        layer = poll["layer"]
        before, after = poll["host_query_ns"]
        prior = previous.get(layer)
        previous[layer] = (before, after)
        if prior is None or before - prior[1] > 1_500_000_000:
            continue
        # Both consecutive queries must fall strictly inside the observed round.
        # Keep only within-batch intervals, so layer resets and missing samples
        # cannot join two separate segments or mix device and host clocks.
        if not any(a <= prior[0] and after <= b for a, b, _ in windows):
            continue
        timestamps = poll["device_present_ns"]
        intervals[layer].extend(b-a for a,b in zip(timestamps,timestamps[1:]) if b>a)
    return {layer: {"observed_seconds": round(sum(deltas)/1e9, 2),
                    "presentation": interval_stats(deltas)} for layer,deltas in intervals.items() if deltas}


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("run", type=Path)
    parser.add_argument("--out", type=Path)
    args=parser.parse_args()
    results={}
    for scenes in sorted(args.run.glob("scenes-*.jsonl")):
        venue=scenes.stem.removeprefix("scenes-")
        polls=args.run/("performance-"+venue)/"presentation-times.jsonl"
        if polls.is_file():
            results[venue]=live_intervals(rows(scenes),rows(polls))
    output=json.dumps(results,indent=2)+"\n"
    if args.out:
        args.out.write_text(output,encoding="utf-8")
    else:
        print(output,end="")


if __name__=="__main__":
    main()
