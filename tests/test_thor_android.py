"""Create a private Android 13 AVD with the AYN Thor Pro display dimensions."""
import argparse
import os
from pathlib import Path
import subprocess
import time


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--sdk", type=Path, default=Path.home() / "AppData/Local/Android/Sdk")
    parser.add_argument("--directory", type=Path, required=True, help="Local directory for this test device")
    parser.add_argument("--apk", type=Path, help="Install this build and open the launcher")
    args = parser.parse_args()
    sdk = args.sdk.resolve()
    work = args.directory.resolve()
    image = "system-images/android-33/google_apis/x86_64"
    emulator = sdk / "emulator/emulator.exe"
    adb = sdk / "platform-tools/adb.exe"
    if not emulator.is_file() or not adb.is_file() or not (sdk / image / "system.img").is_file():
        parser.error("Install Android Emulator, Android SDK Platform-Tools and the Android 13 Google APIs x86_64 system image in Android Studio's SDK Manager.")
    apk = args.apk.resolve() if args.apk else None
    if apk and not apk.is_file():
        parser.error("The specified APK does not exist")

    avds = work / "avd"
    name = "FightNight_Thor_API33"
    disk = avds / (name + ".avd")
    disk.mkdir(parents=True, exist_ok=True)
    private = work / "android"
    private.mkdir(exist_ok=True)
    env = dict(os.environ, ANDROID_AVD_HOME=str(avds), ANDROID_USER_HOME=str(private),
               ANDROID_EMULATOR_HOME=str(private), ANDROID_SDK_ROOT=str(sdk))
    ini = avds / (name + ".ini")
    if not ini.exists():
        ini.write_text(f"avd.ini.encoding=UTF-8\npath={disk}\ntarget=android-33\n", encoding="utf-8")
    configuration = {
        "AvdId": name, "avd.ini.displayname": "Fight Night — Thor Pro",
        "abi.type": "x86_64", "hw.cpu.arch": "x86_64", "image.sysdir.1": image + "/",
        "tag.id": "google_apis", "hw.lcd.width": 1920, "hw.lcd.height": 1080,
        "hw.lcd.density": 320, "hw.lcd.vsync": 120, "hw.initialOrientation": "landscape",
        "hw.multi_display_window": "no", "showDeviceFrame": "no",
        "hw.gpu.enabled": "yes", "hw.gpu.mode": "auto", "hw.keyboard": "yes",
        "hw.cpu.ncore": 4, "hw.ramSize": 4096, "disk.dataPartition.size": "16G",
    }
    config = disk / "config.ini"
    if not config.exists():
        config.write_text("".join(f"{k}={v}\n" for k, v in configuration.items()), encoding="utf-8")

    def bridge(*cmd, check=True, timeout=12):
        return subprocess.run([adb, *cmd], env=env, capture_output=True, text=True,
                              timeout=timeout, check=check)

    devices = bridge("devices").stdout.splitlines()
    def emulator_name(serial):
        result = bridge("-s", serial, "emu", "avd", "name", check=False)
        return result.stdout.splitlines()[:1] == [name]
    serial = next((line.split()[0] for line in devices if line.startswith("emulator-")
                   and line.endswith("\tdevice") and emulator_name(line.split()[0])), None)
    proc = None
    if serial is None:
        used = {line.split()[0] for line in devices if line.startswith("emulator-")}
        port = next(p for p in range(5560, 5682, 2) if f"emulator-{p}" not in used)
        serial = f"emulator-{port}"
        with (work / "emulator.log").open("w", encoding="utf-8") as log:
            proc = subprocess.Popen([emulator, "-avd", name, "-port", str(port), "-no-snapshot",
                                     "-multidisplay", "1,1240,1080,320,0"],
                                    env=env, stdout=log, stderr=subprocess.STDOUT)
    deadline = time.monotonic() + 240
    while time.monotonic() < deadline:
        if proc and proc.poll() is not None:
            raise SystemExit(f"Emulator exited. See {work / 'emulator.log'}")
        status = bridge("-s", serial, "shell", "getprop", "sys.boot_completed", check=False)
        if status.stdout.strip() == "1" and emulator_name(serial):
            break
        time.sleep(2)
    else:
        raise SystemExit(f"Android boot did not complete. See {work / 'emulator.log'}")
    displays = bridge("-s", serial, "shell", "dumpsys", "display").stdout
    (work / "display.txt").write_text(displays, encoding="utf-8")
    if not any("1240 x 1080" in row and "FLAG_PRESENTATION" in row for row in displays.splitlines()):
        raise SystemExit(f"Android did not expose the lower presentation display. See {work / 'display.txt'}")
    print(serial, bridge("-s", serial, "shell", "getprop", "ro.product.cpu.abilist").stdout.strip())
    if apk:
        bridge("-s", serial, "install", "-r", str(apk), timeout=120)
        print(bridge("-s", serial, "shell", "am", "start", "-W", "-n",
                     "org.oxideshift.corner/.CornerActivity").stdout)


if __name__ == "__main__":
    main()
