"""Record a live Windows desktop bout's published frames, punch counters and CPU time.

Run against a host built with --frame-profile and FNR3_FRAME_SLOTS=1.
Start after entering the ring. The capture process reads the triple buffer.
"""
import argparse
import ctypes
import json
import math
import mmap
from pathlib import Path
import re
import struct
import time
import zlib


def cpu_times(pid, log):
    kernel = ctypes.WinDLL("kernel32", use_last_error=True)
    openers = [(kernel.OpenProcess, kernel.GetProcessTimes, 0x1000, pid, "process")]
    tids = dict(re.findall(r"\[QATID\] (guest|render) (\d+)",log))
    for role, tid in tids.items():
        openers.append((kernel.OpenThread,kernel.GetThreadTimes,0x0800,int(tid),role))
    totals = {}
    for opener, query, access, object_id, role in openers:
        opener.restype = ctypes.c_void_p
        handle = ctypes.c_void_p(opener(access, False, object_id))
        if not handle:
            continue
        try:
            times = [ctypes.c_uint64() for _ in range(4)]
            if query(handle, *(ctypes.byref(t) for t in times)):
                totals[role] = (times[2].value + times[3].value)/1e7
        finally:
            kernel.CloseHandle(handle)
    return totals


def observe(slots, seconds):
    stamps, dimensions, pairs, equal = [], set(), 0, 0
    previous, last_crc = None, None
    _, stride, count, signature = struct.unpack_from("<4I", slots)
    if count != 3 or signature != 0x32425246 or len(slots) < 16+stride*count:
        raise ValueError("Expected the game's live three-slot BGRA mailbox")
    finish = time.perf_counter() + seconds
    while time.perf_counter() < finish:
        serial, = struct.unpack_from("<I",slots)
        if serial and serial != previous:
            stamp = time.perf_counter()
            offset = 16 + serial % count * stride
            w,h = struct.unpack_from("<2I",slots,offset)
            if w and h and 8+w*h*4 <= stride:
                crc = zlib.crc32(memoryview(slots)[offset+8:offset+8+w*h*4])
                current, = struct.unpack_from("<I",slots)
                if (current-serial) % 2**32 < 2:
                    if previous is not None and (serial-previous) % 2**32 == 1:
                        pairs += 1
                        equal += crc == last_crc
                    stamps.append((serial,stamp))
                    previous,last_crc = serial,crc
                    dimensions.add((w,h))
        time.sleep(.001)
    if len(stamps) < 2:
        raise ValueError("The host published fewer than two observable frames")
    intervals = sorted(1000*(b[1]-a[1]) for a,b in zip(stamps,stamps[1:]))
    frames = (stamps[-1][0]-stamps[0][0]) % 2**32
    seconds = stamps[-1][1]-stamps[0][1]
    return dict(frames=frames,seconds=seconds,fps=frames/seconds,
                p95=intervals[math.ceil(.95*len(intervals))-1],p99=intervals[math.ceil(.99*len(intervals))-1],
                max_ms=intervals[-1],over20=sum(t>20 for t in intervals),over33=sum(t>33.37 for t in intervals),
                seen=len(stamps),skipped=frames+1-len(stamps),pixel_pairs=pairs,identical_pixels=equal,dims=sorted(dimensions))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--pid", type=int, required=True)
    for name in ("log","frame","out"):
        parser.add_argument("--"+name, type=Path, required=True)
    parser.add_argument("--seconds", type=float, default=25)
    args = parser.parse_args()
    if args.seconds <= 0:
        parser.error("--seconds must be positive")
    args.out.mkdir(exist_ok=False, parents=True)
    with args.log.open("rb") as log, args.frame.open("rb") as frame, mmap.mmap(frame.fileno(),0,access=mmap.ACCESS_READ) as slots:
        before = cpu_times(args.pid, log.read().decode(errors="replace"))
        ctypes.windll.winmm.timeBeginPeriod(1)
        try:
            result = observe(slots,args.seconds)
        finally:
            ctypes.windll.winmm.timeEndPeriod(1)
        after = cpu_times(args.pid, args.log.read_text(errors="replace"))
        tail = log.read()
    result["cpu_seconds"] = {key:round(after[key]-v,5) for key,v in before.items() if key in after}
    bouts = [line.split() for line in tail.splitlines() if line.startswith(b"FNR3/1 BOUT ") and len(line.split())==12]
    if len(bouts)>1:
        result["punches"] = sum(int(bouts[-1][i])-int(bouts[0][i]) for i in (9,11))
    (args.out/"slice.log").write_bytes(tail)
    (args.out/"rates.json").write_text(json.dumps(result,indent=2),encoding="utf-8")
    print(json.dumps(result,indent=2))


if __name__ == "__main__":
    main()

