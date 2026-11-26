"""Align two Android screenrecord clips on their device frame clock for visual review."""
import argparse
import json
import subprocess
from pathlib import Path

from thor_recording import frame_clock


def video_clock(path, ffprobe):
    info = subprocess.check_output([
        ffprobe, "-v", "error", "-select_streams", "v:0", "-show_entries",
        "stream=nb_frames,start_time", "-of", "json", path,
    ])
    video = json.loads(info)["streams"][0]
    clock = frame_clock(path)
    if int(video["nb_frames"]) != clock["frames"]:
        raise ValueError(f"{path.name}: video and Android frame counts differ")
    return clock


def align(upper, lower, destination, ffmpeg, ffprobe, from_elapsed_ns=None, duration=None):
    clocks = [video_clock(path, ffprobe) for path in (upper, lower)]
    first = max(clock["first_ns"] for clock in clocks)
    last = min(clock["last_ns"] for clock in clocks)
    if from_elapsed_ns is not None:
        first = max(first, from_elapsed_ns)
    if duration is not None:
        last = min(last, first + round(duration * 1e9))
    seconds = (last - first) / 1e9
    if seconds <= 0:
        raise ValueError("These displays have no common recorded interval")
    command = [ffmpeg, "-hide_banner", "-loglevel", "error", "-n"]
    for path, clock in zip((upper, lower), clocks):
        command.extend(["-ss", f"{(first - clock['first_ns']) / 1e9:.9f}", "-i", path])
    command.extend([
        "-filter_complex",
        "[0:v]fps=24,scale=-2:360,setsar=1[u];[1:v]fps=24,scale=-2:360,setsar=1[l];[u][l]hstack=shortest=1",
        "-t", f"{seconds:.9f}", "-an", "-c:v", "libx264", "-preset", "veryfast", "-crf", "20",
        "-movflags", "+faststart", destination,
    ])
    subprocess.run(command, check=True)
    return {"sources": [upper.name, lower.name], "common_seconds": seconds, "review_start_elapsed_ns": first,
            "start_difference_ms": (clocks[1]["first_ns"] - clocks[0]["first_ns"]) / 1e6,
            "clocks": clocks, "review": destination.name}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("upper", "lower", "destination"):
        parser.add_argument(arg, type=Path)
    parser.add_argument("--ffmpeg", default="ffmpeg")
    parser.add_argument("--ffprobe", default="ffprobe")
    parser.add_argument("--from-elapsed-ns", type=int, help="Start at a device elapsed-time event from the QA scene log")
    parser.add_argument("--seconds", type=float, help="Limit the review to this many seconds of shared footage")
    args = parser.parse_args()
    print(json.dumps(align(args.upper, args.lower, args.destination, args.ffmpeg, args.ffprobe,
                           args.from_elapsed_ns, args.seconds), indent=2))
