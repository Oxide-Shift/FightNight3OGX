"""Observe a USB gameplay session without changing game settings, saves, or log buffers."""
import argparse
from contextlib import contextmanager
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime, timezone
import json
import math
import shlex
from pathlib import Path
import re
import subprocess
import time

PACKAGE = "org.oxideshift.corner"


def samples(text):
    # SurfaceFlinger rows contain desired, actual and ready timestamps in nanoseconds.
    result = set()
    for line in text.splitlines():
        fields = line.split()
        if len(fields) == 3 and all(f.isdecimal() for f in fields):
            actual = int(fields[1])
            if 0 < actual < 2**63 - 1:
                result.add(actual)
    return sorted(result)


def interval_stats(deltas):
    if not deltas:
        return None
    ordered = sorted(deltas)
    def rank(p):
        return round(ordered[math.ceil(p * len(ordered)) - 1] / 1e6, 2)
    return {"intervals": len(ordered), "fps": round(len(ordered)*1e9/sum(ordered),2),
            "p50_ms": rank(.5), "p95_ms": rank(.95), "p99_ms": rank(.99),
            "worst_ms": round(ordered[-1]/1e6, 2), "over_33ms": sum(n > 33_333_333 for n in ordered)}


def sustained_windows(deltas):
    # Use elapsed time so a slower final stretch covers as much play as the first.
    window=30_000_000_000
    if sum(deltas)<2*window:return None
    def collect(intervals):
        chunk=[];elapsed=0
        for duration in intervals:
            chunk.append(duration);elapsed+=duration
            if elapsed>=window:break
        return interval_stats(chunk)
    return {"first_30s":collect(deltas),"last_30s":collect(reversed(deltas))}


def thread_ticks(text):
    """Decode /proc/task/stat including names containing spaces or parentheses."""
    result = []
    for row in text.splitlines():
        match = re.fullmatch(r"(\d+) \((.*)\) (\S+) (.*)", row)
        if not match:
            continue
        fields = match[4].split()  # Starts at proc field 4, ppid.
        try:
            result.append(dict(tid=int(match[1]), name=match[2], user_ticks=int(fields[10]),
                               system_ticks=int(fields[11]), start_ticks=int(fields[18])))
        except (IndexError, ValueError):
            continue
    return result


def battery_state(text):
    values = {}
    for key, value in re.findall(r"^\s*([\w ]+):\s*(\S+)\s*$", text, re.M):
        if key in ("AC powered", "USB powered", "Wireless powered", "Dock powered") and value in ("true","false"):
            values[key.lower().replace(" ", "_")] = value == "true"
        elif key in ("temperature","voltage","level","scale","Charge counter") and re.fullmatch(r"-?\d+", value):
            name = {"temperature":"temperature_tenths_c","voltage":"millivolts",
                    "Charge counter":"charge_counter_uah"}.get(key,key)
            values[name] = int(value)
    return values


def process_usage(before,after,ticks_per_second):
    if not ticks_per_second or before.get("process_start_ticks")!=after.get("process_start_ticks"):
        return None
    fields=("process_ticks","sample_monotonic_ns","process_start_ticks")
    if not all(k in before and k in after for k in fields):return None
    elapsed=after["sample_monotonic_ns"]-before["sample_monotonic_ns"]
    ticks=after["process_ticks"]-before["process_ticks"]
    if elapsed<=0 or ticks<0:return None
    return round(100*ticks*1e9/(ticks_per_second*elapsed),1)


def thermal_sensors(dump):
    _,heading,current=dump.partition("Current temperatures from HAL:")
    if not heading:return []
    types=("cpu","gpu","battery","skin","usb_port")
    sensors=[]
    for line in current.strip().splitlines():
        if not line.strip().startswith("Temperature{"):break
        match=re.fullmatch(r"Temperature\{mValue=([-+]?\d+(?:\.\d*)?(?:[eE][-+]?\d+)?), mType=(\d+), mName=([^,{}]+), mStatus=(\d+)\}",line.strip())
        if not match:continue
        kind=int(match[2])
        if kind<len(types):
            sensors.append(dict(sensor=match[3],role=types[kind],celsius=float(match[1]),status=int(match[4])))
    return sensors


@contextmanager
def timing_stream(adb_path, start, path, selector=("-d",), pid=None):
    # adb streams directly to the PC. A handheld reboot can erase its log buffers.
    # If its clock isn't available, start at the most recent matching log entry.
    with path.open("wb") as output:
        reader = subprocess.Popen([adb_path, *selector, "logcat", *(["--pid="+pid] if pid else []), "-T", start or "1", "-v",
                                   "threadtime", "FightNightTiming:I", "*:S"],
                                  stdout=output, stderr=subprocess.STDOUT)
        try:
            yield reader
        finally:
            if reader.poll() is None:
                reader.terminate()
            try:
                reader.wait(timeout=3)
            except subprocess.TimeoutExpired:
                reader.kill()
                reader.wait()


class SurfaceHistory:
    def __init__(self):
        self.previous = None
        self.deltas = []
        self.gaps = 0

    def update(self, values):
        if not values:
            return
        old = self.previous
        fresh = []
        if old is not None and values[-1] > old:
            if old in values:
                fresh = values[values.index(old):]
            else:
                self.gaps += 1
                fresh = [t for t in values if t > old]
            self.deltas.extend(b-a for a,b in zip(fresh, fresh[1:]))
        # The first window can predate capture or belong to a recreated surface.
        if old is None or values[-1] > old:
            self.previous = values[-1]
        return fresh


def health_snapshot(adb, ticks, package=PACKAGE):
    thermal = re.compile(r"Thermal Status:\s*(\d+)", re.I)
    pss = re.compile(r"TOTAL PSS:\s*([\d,]+)")
    rss = re.compile(r"TOTAL RSS:\s*([\d,]+)")
    started = datetime.now(timezone.utc).isoformat()
    temperatures=adb("shell", "dumpsys", "thermalservice")
    status = thermal.search(temperatures)
    processes = {}
    for line in adb("shell", "ps", "-A", "-o", "PID,ARGS").splitlines():
        fields = line.strip().split(maxsplit=1)
        if len(fields)!=2 or not fields[0].isdecimal():
            continue
        argv = fields[1].split()
        cmd = argv[0]
        # Some Android builds show the runner's basename in argv[0]. Its
        # first argument still identifies the app-private translated game.
        private_game = len(argv) > 1 and f"/{package}/files/disc-build/" in argv[1]
        if cmd != package and not ((package in cmd and cmd.endswith("/libdisc_runner.so"))
                                   or (cmd == "libdisc_runner.so" and private_game)):
            continue
        mem = adb("shell", "dumpsys", "meminfo", fields[0])
        values = {}
        proc_stat=thread_ticks(adb("shell", f"cat /proc/{fields[0]}/stat 2>/dev/null"))
        if len(proc_stat)==1 and str(proc_stat[0]["tid"])==fields[0]:
            stat=proc_stat[0]
            values.update(process_ticks=stat["user_ticks"]+stat["system_ticks"],
                          process_start_ticks=stat["start_ticks"],sample_monotonic_ns=time.monotonic_ns())
        for field, regex in (("pss_kib", pss), ("rss_kib", rss)):
            match = regex.search(mem)
            if match:
                values[field] = int(match[1].replace(",", ""))
        if cmd != package:
            # Android builds differ in shell access to per-thread stats.
            # Missing counters are omitted. Compare a TID only within one start time.
            rows = thread_ticks(adb("shell", f"cat /proc/{fields[0]}/task/*/stat 2>/dev/null"))
            if rows:
                values["threads"] = rows
        processes[fields[0]] = dict(role="launcher" if cmd==package else "native", **values)
    # Qualcomm kernels expose different read-only utilization counters. Keep their units explicit.
    busy = adb("shell", "cat /sys/class/kgsl/kgsl-3d0/gpu_busy_percentage 2>/dev/null").strip().rstrip("%").strip()
    gpu = {"busy_percent": int(busy)} if busy.isdecimal() and int(busy) <= 100 else {}
    if not gpu:
        pair = adb("shell", "cat /sys/class/kgsl/kgsl-3d0/gpubusy 2>/dev/null").split()
        if len(pair) == 2 and all(n.isdecimal() for n in pair) and 0 < int(pair[1]) and int(pair[0]) <= int(pair[1]):
            gpu = {"busy_ticks": int(pair[0]), "total_ticks": int(pair[1])}
    return dict(at_utc=started, thermal_status=int(status[1]) if status else None,
                thermal_sensors=thermal_sensors(temperatures),
                battery=battery_state(adb("shell", "dumpsys", "battery")),
                gpu=gpu, ticks_per_second=int(ticks) if ticks.isdecimal() else None, processes=processes)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", default="adb")
    parser.add_argument("--stop-file", type=Path, help="Finish and flush when the specified file appears")
    parser.add_argument("--serial", help="Capture the selected authorized USB device")
    parser.add_argument("--package", choices=(PACKAGE, PACKAGE+".usbqa"), default=PACKAGE)
    parser.add_argument("--seconds", type=int, default=600)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    if args.seconds < 1:
        parser.error("--seconds must be positive")

    selector = ("-s", args.serial) if args.serial else ("-d",)
    def adb(*parts, required=False):
        try:
            result = subprocess.run([args.adb, *selector, *parts], capture_output=True, encoding="utf-8",
                                    errors="replace", timeout=12, check=required)
        except subprocess.TimeoutExpired:
            if required:
                raise
            return ""
        return result.stdout if result.returncode == 0 else ""

    adb("get-state", required=True)
    package = adb("shell", "dumpsys", "package", args.package, required=True)
    version = re.search(r"versionName=(\S+)", package)
    if not version:
        parser.error("FightNight3OGX is not installed on the connected device")
    args.out.mkdir(parents=True, exist_ok=False)
    start = adb("shell", "date", "+%m-%d\\ %H:%M:%S.000").strip()
    # Use device time with -T to include only the current capture.
    if not re.fullmatch(r"\d\d-\d\d \d\d:\d\d:\d\d\.000", start):
        start = ""
    ticks = adb("shell", "getconf", "CLK_TCK").strip()
    end = time.monotonic() + args.seconds
    next_health = next_discovery = 0.
    histories, active = {}, set()
    captured = datetime.now(timezone.utc).isoformat()
    print(f"Recording {version[1]} for {args.seconds}s. Continue playing. Ctrl+C finishes early.", flush=True)
    with timing_stream(args.adb, start, args.out/"timing.txt", selector, adb("shell", "pidof", "-s", args.package).strip() or None) as reader, \
            (args.out/"samples.jsonl").open("w", encoding="utf-8") as stream, \
            (args.out/"presentation-times.jsonl").open("w", encoding="utf-8") as frame_stream, \
            ThreadPoolExecutor(max_workers=1) as health_worker:
        pending = None
        previous_processes={}
        def write_health():
            nonlocal previous_processes
            row = pending.result()
            for pid,process in row["processes"].items():
                usage=process_usage(previous_processes.get(pid,{}),process,row["ticks_per_second"])
                if usage is not None:process["cpu_percent_one_core"]=usage
            previous_processes=row["processes"]
            row["presentation_layers"] = sorted(active)
            row["presentation_layer"] = next(iter(active)) if len(active)==1 else ""
            stream.write(json.dumps(row)+"\n")
            stream.flush()
        try:
            while time.monotonic() < end and not (args.stop_file and args.stop_file.is_file()):
                now = time.monotonic()
                if pending is not None and pending.done():
                    write_health()
                    pending = None
                if pending is None and now >= next_health:
                    pending = health_worker.submit(health_snapshot, adb, ticks, args.package)
                    next_health = now + 10
                if now >= next_discovery:
                    names = adb("shell", "dumpsys", "SurfaceFlinger", "--list").splitlines()
                    found = {s.strip() for s in names if args.package+"/" in s and "SurfaceView" in s
                             and "(BLAST)" in s and "Background for" not in s}
                    for name in active - found:
                        histories[name].previous = None
                        histories[name].gaps += 1
                    for name in found - active:
                        histories.setdefault(name, SurfaceHistory())
                    active = found
                    next_discovery = now + 3
                for name in sorted(active):
                    started_ns=time.monotonic_ns()
                    values = adb("shell", "dumpsys SurfaceFlinger --latency " + shlex.quote(name))
                    finished_ns=time.monotonic_ns()
                    fresh=histories[name].update(samples(values))
                    if fresh and len(fresh)>1:
                        # Keep the anchor and the actual present times. The first item repeats
                        # across contiguous windows. Never invent an interval across a gap.
                        frame_stream.write(json.dumps(dict(layer=name,host_query_ns=[started_ns,finished_ns],
                                                           device_present_ns=fresh))+"\n")
                        frame_stream.flush()
                time.sleep(.45)
        except KeyboardInterrupt:
            pass
        finally:
            if pending is not None:
                write_health()
            per_layer = {name: dict(timing=interval_stats(h.deltas), disconnected_windows=h.gaps,
                                    sustained=sustained_windows(h.deltas) if h.gaps==0 else None)
                         for name,h in histories.items()}
            report = dict(version=version[1], started_utc=captured, ended_utc=datetime.now(timezone.utc).isoformat(),
                          presented_surfaces=per_layer, disconnected_windows=sum(h.gaps for h in histories.values()),
                          presented_surface_frames=next(iter(per_layer.values()))["timing"] if len(per_layer)==1 else None,
                          timing_stream_exit_before_end=reader.poll())
            (args.out/"summary.json").write_text(json.dumps(report,indent=2),encoding="utf-8")
    history = adb("shell", "dumpsys", "activity", "exit-info", args.package)
    (args.out/"exit-info.txt").write_text(history,encoding="utf-8")
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()

