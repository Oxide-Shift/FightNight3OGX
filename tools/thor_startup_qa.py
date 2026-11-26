"""Exercise the normal launcher actions through the shell-only private test activity."""
import argparse
from datetime import datetime
from pathlib import Path
import re
import subprocess
import sys
import time
from uuid import uuid4

PACKAGE = "org.oxideshift.corner"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", required=True, type=Path)
    parser.add_argument("--seconds", type=int, default=150)
    args = parser.parse_args()
    device = [args.adb, "-s", args.serial]

    def adb(*parts, timeout=15):
        result = subprocess.run([*device, *parts], capture_output=True, timeout=timeout,
                                encoding="utf-8", errors="replace")
        if result.returncode:
            raise RuntimeError((result.stderr or result.stdout).strip())
        return result.stdout.strip()

    if "thor" not in adb("shell", "getprop", "ro.product.model").lower():
        parser.error("An authorized USB Thor is required.")
    package = adb("shell", "dumpsys", "package", PACKAGE)
    if "versionName=0.4.0-alpha.78.3" not in package or "android.permission.INTERNET" in package:
        parser.error("Install the signed USB QA build first.")
    out = args.out / ("startup-" + datetime.now().strftime("%Y%m%d-%H%M%S-%f"))
    out.mkdir(parents=True)
    print("USB test evidence:", out, flush=True)
    capture = [sys.executable, str(Path(__file__).with_name("thor_presentation.py")), "--adb", args.adb,
               "--serial", args.serial, "--already-running", "--seconds", str(args.seconds), "--out", str(out)]
    token = uuid4().hex
    marker = re.compile(r"\b" + token + r" ([A-Z_]+)\s*$", re.MULTILINE)
    with (out / "actions.txt").open("w", encoding="utf-8", buffering=1) as log:
        def fail(reason):
            print(reason, file=log)
            subprocess.run([*capture, "--snapshot"], timeout=90)
            raise SystemExit(reason)

        adb("shell", "input", "keyevent", "KEYCODE_WAKEUP")
        start = adb("shell", "am", "start", "-W", "-n", PACKAGE + "/.UsbStartupCheck",
                    "--es", "run_id", token, timeout=45)
        print(start, file=log)
        if "Error" in start or "Exception" in start or "Status: timeout" in start:
            fail("Android could not open the USB test activity. See actions.txt.")
        deadline = time.monotonic() + 45
        seen = []
        while time.monotonic() < deadline:
            messages = adb("logcat", "-d", "-v", "brief", "-s", "FightNightUsbQA:I", "*:S")
            for state in marker.findall(messages):
                if state in seen:
                    continue
                seen.append(state)
                print(state, file=log)
                print("Launcher:", state, flush=True)
                if state == "PREPARING":
                    deadline = time.monotonic() + 1200
                elif state in ("GAME", "EXISTING_GAME"):
                    result = subprocess.run(capture).returncode
                    if result:
                        raise SystemExit(result)
                    if not any(out.rglob("frame-*.png")):
                        raise SystemExit("No display frame was captured. See foreground and display diagnostics.")
                    return 0
                else:
                    fail("Launcher returned " + state + ". See the saved snapshot and diagnostics.")
            time.sleep(1)
        fail("Timed out waiting for " + ("disc preparation" if seen else "the Android activity") + ".")


if __name__ == "__main__":
    raise SystemExit(main())
