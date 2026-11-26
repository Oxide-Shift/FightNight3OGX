"""Exercise the original EA Trax on a connected Thor after the Music parent appears."""
import argparse
from io import BytesIO
from pathlib import Path
import re
import subprocess
import threading
import time
import xml.etree.ElementTree as ET
from PIL import Image


def selected_track(png):
    """Find the pair of white arrows surrounding ON/OFF in the retail playlist."""
    with Image.open(BytesIO(png)) as source:
        width, height = source.size
        for viewport in (width, min(width, round(height * 4 / 3))):
            left = (width - viewport) // 2
            img = source.crop((left, 0, left + viewport, height)).convert("RGB").resize((640, 480))
            for row, y in enumerate((141, 216, 291), 1):
                def arrow_at(x):
                    pixels = img.crop((x - 6, y - 7, x + 7, y + 8)).getdata()
                    return sum(min(p) >= 207 and max(p) - min(p) <= 38 for p in pixels) >= 16
                if arrow_at(382) and arrow_at(461):
                    return row
    return None


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--version", default="0.4.0-alpha.78.1")
    parser.add_argument("--controller-open",action="store_true",help="Select EA Trax and open it with the gamepad")
    args = parser.parse_args()
    adb = [str(args.adb), "-s", args.serial]
    out = args.out
    out.mkdir(parents=True, exist_ok=False)
    package = "org.oxideshift.corner"
    def call(*cmd, timeout=15):
        return subprocess.run([*adb, *cmd], capture_output=True, timeout=timeout, check=True).stdout
    def shell(*cmd):
        return call("shell", *cmd).decode("utf8","replace")
    versions = re.findall(r"(?m)^\s*versionName=(\S+)", shell("dumpsys","package",package))
    if "thor" not in shell("getprop","ro.product.model").lower() or args.version not in versions:
        raise SystemExit("This check requires " + args.version + " on the selected Thor.")
    displays=re.findall(r"(?m)^\s*Display\s+(\d+)\s+\(",shell("dumpsys","SurfaceFlinger","--display-id"))
    if len(displays) != 2:
        raise SystemExit("The top and bottom display must both be available.")
    log=subprocess.Popen([*adb,"logcat","-T","1","-v","threadtime","FightNightTiming:I","*:S"],stdout=subprocess.PIPE)
    events=[]
    def monitor():
        with (out/"route.log").open("wb") as file:
            for line in log.stdout:
                file.write(line);file.flush()
                events.append(line.decode("utf8","replace"))
    reader=threading.Thread(target=monitor,daemon=True);reader.start()
    def parent():
        raw=call("exec-out","uiautomator","dump","/dev/tty",timeout=24)
        start=raw.find(b"<hierarchy");end=raw.rfind(b"</hierarchy>")
        if start<0 or end<0:return None
        tree=ET.fromstring(raw[start:end+12])
        for node in tree.iter("node"):
            attr=node.attrib
            if attr.get("package")==package and "ea trax, open playlist" in attr.get("content-desc","").lower():
                box=list(map(int,re.findall(r"\d+",attr.get("bounds",""))))
                if len(box)==4 and box[2]>box[0] and box[3]>box[1]:
                    return ((box[0]+box[2])//2,(box[1]+box[3])//2,attr.get("selected")=="true")
        return None
    def wait_for(fn,seconds,what):
        end=time.monotonic()+seconds
        while time.monotonic()<end:
            result=fn()
            if result:return result
            time.sleep(.35)
        raise RuntimeError("Timed out waiting for "+what)
    def game_visible():
        state=shell("dumpsys","activity","activities")
        return any(package in line and ("topResumedActivity" in line or "mResumedActivity" in line or "ResumedActivity:" in line) for line in state.splitlines())
    def capture(label):
        if not game_visible():raise RuntimeError("Fight Night lost foreground; stopped controller input.")
        for pos,identifier in enumerate(displays):
            blob=call("exec-out","screencap","-p","-d",identifier)
            if not blob.startswith(b"\x89PNG\r\n\x1a\n"):raise RuntimeError("Invalid screenshot for display "+identifier)
            (out/f"{label}-display-{pos}.png").write_bytes(blob)
    def press(key):
        if not game_visible():raise RuntimeError("Fight Night lost foreground.")
        shell("input","gamepad","keyevent","--longpress",str(key))
    def wait_for_row(row, seconds):
        def at_row():
            if not game_visible():
                raise RuntimeError("Fight Night lost foreground.")
            return selected_track(call("exec-out","screencap","-p","-d",displays[0])) == row
        wait_for(at_row, seconds, f"both selection arrows on playlist row {row}")
    try:
        print("In Fight Setup, prepare the game if prompted. Start the game and enter Music once. Then leave the controls alone.",flush=True)
        location=wait_for(parent,420,"Music on the top display")
        for cycle in range(1,4):
            capture(f"{cycle}-music")
            if cycle==1 and any("FNR3/1 FRONT 2" in l for l in events):
                raise RuntimeError("The child opened before the first Open command.")
            offset=len(events)
            if args.controller_open:
                for step in range(3):
                    if location[2]:break
                    if step==2:raise RuntimeError("Controller selection did not reach EA Trax.")
                    press(20)
                    location=wait_for(parent,8,"Music selection")
                press(96)
            else:
                shell("input","touchscreen","tap",*map(str,location[:2]))
            wait_for(lambda:any("FNR3/1 FRONT 2" in l for l in events[offset:]),12,"original EA Trax screen receipt")
            wait_for_row(1, 35)
            if cycle == 1:
                # Let the original first-song preview start. The freeze occurred
                # after loading had completed and the first track was visible.
                time.sleep(12)
            capture(f"{cycle}-songs")
            press(20);wait_for_row(2, 6);capture(f"{cycle}-down")
            press(19);wait_for_row(1, 6)
            press(97)
            location=wait_for(parent,18,"Music after one controller B")
            capture(f"{cycle}-return")
        press(97);time.sleep(3)
        if parent():raise RuntimeError("Second B left the Music parent on screen.")
        capture("last-back")
        print("Three visible Down/Up selections and B returns passed. Inspect the final main menu on both displays.",flush=True)
    except Exception:
        if game_visible():capture("stopped")
        raise
    finally:
        log.terminate()
        try:log.wait(5)
        except subprocess.TimeoutExpired:log.kill();log.wait()
        reader.join(3)
        (out/"game-exits.txt").write_text(shell("dumpsys","activity","exit-info",package),encoding="utf8")


if __name__=="__main__":
    main()

