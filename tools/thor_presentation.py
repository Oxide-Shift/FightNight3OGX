"""Capture a bounded, read-only QA session from Fight Night on the USB Thor."""
import argparse
from datetime import datetime
from pathlib import Path
import re
import subprocess
import sys
import time

PACKAGE = "org.oxideshift.corner"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--seconds", type=int, default=120)
    parser.add_argument("--snapshot",action="store_true",help="Inspect the current game without relaunching it")
    parser.add_argument("--already-running", action="store_true", help="Capture the active session without sending another launcher intent")
    args = parser.parse_args()
    out = args.out / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    out.mkdir(parents=True)
    adb = [str(args.adb), "-s", args.serial]

    def call(*command, timeout=15):
        return subprocess.run([*adb, *command], capture_output=True, timeout=timeout)

    def read(*command):
        return call("shell", *command).stdout.decode("utf-8", "replace")

    def save(name, value):
        (out / name).write_text(value, encoding="utf-8")

    if call("get-state").stdout.strip() != b"device" or "thor" not in read("getprop", "ro.product.model").lower():
        sys.exit("The selected authorized Thor is unavailable.")
    version = read("dumpsys", "package", PACKAGE)
    save("version.txt", "\n".join(line.strip() for line in version.splitlines() if "versionCode=" in line or "versionName=" in line))
    if "versionName=" not in version:
        sys.exit("Fight Night is not installed.")
    save("controller-capabilities.txt", read("getevent", "-pl"))
    displays = read("dumpsys", "SurfaceFlinger", "--display-id")
    save("displays.txt", displays)
    ids = re.findall(r"^\s*Display\s+(\d+)\s+\(", displays, re.M)
    if not ids:
        ids = [None]
    # Bring the existing task forward without restarting the game process.
    if not args.snapshot and not args.already_running:
        call("shell", "am", "start", "-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER", "-f", "0x10200000", "-n", PACKAGE + "/.CornerActivity")
    print("Capturing Fight Night. Use the menus or play as usual.", flush=True)
    print(out, flush=True)
    surfaces = read("dumpsys", "SurfaceFlinger", "--list")
    save("game-surfaces.txt", "\n".join(line for line in surfaces.splitlines() if PACKAGE in line))
    save("battery-start.txt", read("dumpsys", "battery"))
    save("thermal-start.txt", read("dumpsys", "thermalservice"))
    def game_processes():
        rows = read("ps", "-A", "-o", "PID,ARGS").splitlines()
        return "\\n".join(line for line in rows if "libdisc_runner.so" in line and
                         "default.xbe" in line and "--check-module" not in line)
    save("native-process-start.txt", game_processes())
    limit = time.monotonic() + args.seconds
    index = 0
    while time.monotonic() < limit:
        focus = read("dumpsys", "window", "windows")
        front = [line for line in focus.splitlines() if "mCurrentFocus=" in line]
        if not front:
            focus = read("dumpsys", "window")
            front = [line for line in focus.splitlines() if "mCurrentFocus=" in line]
        package_focused=any(PACKAGE in row for row in front)
        activity=read("dumpsys","activity","activities") if not package_focused else ""
        package_resumed=any(PACKAGE in row and ("topResumedActivity" in row or "mResumedActivity" in row or "ResumedActivity:" in row) for row in activity.splitlines())
        save("foreground.txt", f"package focused={package_focused} resumed={package_resumed}\n"+
             "\n".join(row.strip() if PACKAGE in row else "other display / system focus" for row in front))
        if package_focused or package_resumed:
            window_display=read("dumpsys","window","displays")
            # A presentation window is intentionally nonfocusable. The upper display's
            # activity can own focus while Android reports the secondary launcher too.
            chunks=re.split(r"(?m)^\s*Display: mDisplayId=",window_display)
            for pos, identifier in enumerate(ids):
                if pos:
                    if not any(chunk[:1]!="0" and PACKAGE in chunk for chunk in chunks[1:]):
                        continue
                options = ["-d", identifier] if identifier else []
                result = call("exec-out", "screencap", "-p", *options)
                if result.stdout.startswith(b"\x89PNG\r\n\x1a\n"):
                    (out / f"frame-{index:03}-display-{pos}.png").write_bytes(result.stdout)
                elif result.stderr or result.stdout:
                    save(f"display-{pos}-capture-error.txt",(result.stderr or result.stdout)[:300].decode("utf-8","replace"))
        if args.snapshot:break
        index += 1
        time.sleep(3)
    save("native-process-end.txt", game_processes())
    save("game-exits.txt",read("dumpsys","activity","exit-info",PACKAGE))
    save("game-frame-stats.txt",read("dumpsys","gfxinfo",PACKAGE,"framestats"))
    save("game-timing.txt", call("logcat", "-d", "-t", "1500", "-v", "threadtime", "FightNightTiming:I", "FightNight:I", "*:S").stdout.decode("utf-8", "replace"))
    save("battery-end.txt", read("dumpsys", "battery"))
    save("thermal-end.txt", read("dumpsys", "thermalservice"))
    save("game-memory.txt", read("dumpsys", "meminfo", PACKAGE))
    print(f"Finished. Captured {len(list(out.glob('frame-*.png')))} display images.", flush=True)


if __name__ == "__main__":
    main()

