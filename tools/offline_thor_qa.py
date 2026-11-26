"""Run the Thor health or controller journey against a USB-only QA installation."""
import argparse
import os
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("journey", choices=("music", "health"))
    parser.add_argument("output", type=Path)
    parser.add_argument("--seconds", type=int, default=600)
    args = parser.parse_args()
    adb = os.environ["FNR3_QA_ADB"]

    def read(*command):
        return subprocess.check_output([adb, "-d", *command], text=True, timeout=15)

    serial = read("get-serialno").strip()
    if "thor" not in read("shell", "getprop", "ro.product.model").lower():
        parser.error("Connect one USB Thor.")
    package = read("shell", "dumpsys", "package", "org.oxideshift.corner")
    version = next((line.split("=", 1)[1] for line in package.splitlines()
                    if line.strip().startswith("versionName=")), None)
    if not version or "android.permission.INTERNET" in package:
        parser.error("Install the signed --offline-qa build before running hardware tests.")
    scripts = Path(__file__).resolve().parent
    if args.journey == "music":
        script = scripts / "thor_music_qa.py"
        options = ["--serial", serial, "--version", version, "--controller-open"]
    else:
        script = scripts / "record_thor_health.py"
        options = ["--seconds", str(args.seconds)]
    # The health sampler starts several ADB clients. Pin all of them to this USB device.
    env = dict(os.environ, ANDROID_SERIAL=serial)
    return subprocess.call([sys.executable, script, "--adb", adb, "--out", args.output, *options], env=env)


if __name__ == "__main__":
    raise SystemExit(main())
