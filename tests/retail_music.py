"""Test the Music hierarchy with the production Java session and a Windows retail host.

Supply a prepared private game, a disposable test profile's UDATA directory and
640x480 reference captures of its profile picker and original EA Trax track list.
Requires Pillow, a JDK and the Android SDK. The user's save is copied before launch.
"""
import argparse
from contextlib import ExitStack
import mmap
import os
from pathlib import Path
import shutil
import struct
import subprocess
import tempfile
import time

from PIL import Image, ImageChops, ImageStat

ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "android/src/org/oxideshift/corner"
NEUTRAL = "PAD " + "00" * 18


def frame(path):
    if not path.is_file() or path.stat().st_size < 16:
        return None
    with path.open("rb") as file, mmap.mmap(file.fileno(), 0, access=mmap.ACCESS_READ) as view:
        serial, stride, count, signature = struct.unpack_from("<4I", view)
        if not serial or count != 3 or signature != 0x32425246 or len(view) < 16 + count * stride:
            return None
        offset = 16 + serial % count * stride
        width, height = struct.unpack_from("<2I", view, offset)
        size = 4 * width * height
        if size == 0 or size + 8 > stride:
            return None
        data = view[offset+8:offset+8+size]
        if (struct.unpack_from("<I", view)[0] - serial) & 0xffffffff >= 2:
            return None
    return Image.frombytes("RGB", (width, height), data, "raw", "BGRX").resize((640, 480), Image.Resampling.NEAREST)


def matches(image, reference, box):
    return image is not None and max(ImageStat.Stat(ImageChops.difference(image.crop(box), reference.crop(box))).mean) < 24


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("host", "game", "udata", "profile-screen", "trax-screen", "jdk", "android-jar"):
        parser.add_argument("--" + name, required=True, type=lambda s: Path(s).resolve())
    parser.add_argument("--captures", type=Path, help="Save decoded guest frames for review")
    args = parser.parse_args()
    if args.captures:args.captures.mkdir(exist_ok=True,parents=True)
    profile = Image.open(args.profile_screen).convert("RGB")
    trax = Image.open(args.trax_screen).convert("RGB")
    with tempfile.TemporaryDirectory() as temp, ExitStack() as stack:
        work = Path(temp)
        classes = work / "classes"
        classes.mkdir()
        subprocess.run([args.jdk / "bin/javac", "--release", "8", "-encoding", "UTF-8", "-cp", args.android_jar,
                        "-d", classes, *JAVA.glob("*.java"), ROOT / "tests/RetailMusicNavigation.java"], check=True)
        shutil.copytree(args.udata, work / "saves/UDATA")
        log = work / "route.log"
        scanout = work / "scanout.rgba"
        output = stack.enter_context(log.open("wb"))
        env = dict(os.environ, FNR3_FRAME_SLOTS="1", FNR3_FRAME_PATH=str(scanout), FNR3_CAPTURE_ONLY="1",
                   FNR3_VIDEO_ASPECT="16:9", RECOMP_RES="900", RECOMP_MSAA="2", RECOMP_ANISO="4",
                   RECOMP_AUDIO_WAV=str(work / "test.wav"), RECOMP_AUDIO_CHANNELS="2",
                   FNR3_PIPELINE_CACHE=str(work / "pipelines.bin"))
        proc = subprocess.Popen([args.jdk / "bin/java", "-cp", os.pathsep.join(map(str, (classes, args.android_jar))),
                                 "org.oxideshift.corner.RetailMusicNavigation", args.host, args.game / "default.xbe",
                                 args.game, work / "saves", "--corner"], stdin=subprocess.PIPE, stdout=output,
                                stderr=subprocess.STDOUT, text=True, env=env, cwd=work)
        def send(line):
            if proc.poll() is not None:
                raise RuntimeError(log.read_text(errors="replace")[-1200:])
            proc.stdin.write(line + "\n")
            proc.stdin.flush()

        def press(key):
            packet = bytearray(18)
            slot, value = {"Start": (0, 16), "Down": (0, 2), "Up": (0, 1), "A": (2, 255), "B": (3, 255)}[key]
            packet[slot] = value
            send("PAD " + packet.hex())
            time.sleep(.17)
            send(NEUTRAL)

        def receipts(marker):
            return log.read_text(errors="replace").splitlines().count(marker)

        def wait_for(test, name, seconds=20):
            end = time.monotonic() + seconds
            while time.monotonic() < end:
                if test():
                    return
                if proc.poll() is not None:
                    break
                time.sleep(.08)
            raise AssertionError(name + "\n" + log.read_text(errors="replace")[-1200:])

        try:
            send(NEUTRAL)
            deadline = time.monotonic() + 100
            while not matches(frame(scanout), profile, (155, 142, 460, 331)):
                if time.monotonic() >= deadline:
                    raise AssertionError("Profile picker did not appear")
                press("Start")
                time.sleep(1.8)
            press("Down")
            time.sleep(.65)
            press("A")
            wait_for(lambda: receipts("FRONT -3"), "Root carousel")
            time.sleep(2.5)
            for _ in range(6):
                press("Down")
                time.sleep(.4)
            press("A")
            wait_for(lambda: receipts("MUSIC ENTRY"), "Music parent")
            assert not receipts("FRONT -1")
            send("CLOSE")
            time.sleep(.75)
            assert not receipts("FRONT -1")
            press("A")
            wait_for(lambda: receipts("MUSIC ENTRY") == 2, "Reopened parent")
            def save_capture(image,label):
                if args.captures:image.save(args.captures / (label+".png"))

            start_root=frame(scanout)
            for cycle in range(1,4):
                send("OPEN")
                wait_for(lambda: receipts("FRONT -1")==cycle,"EA Trax guest receipt")
                wait_for(lambda: matches(frame(scanout), trax, (128,108,506,379)), "Original song list",35)
                time.sleep(.5)
                before=frame(scanout)
                save_capture(before,f"cycle-{cycle}-song")
                press("Down")
                # The text stays in place while the selection bar moves between rows.
                def moved():
                    current=frame(scanout)
                    if current is None:return False
                    diff=ImageChops.difference(current.crop((130,120,495,370)),before.crop((130,120,495,370)))
                    return max(ImageStat.Stat(diff).mean)>3
                wait_for(moved,"Song-list selection responded to Down",5)
                time.sleep(.3);save_capture(frame(scanout),f"cycle-{cycle}-down")
                press("Up");time.sleep(.35)
                press("B")
                wait_for(lambda: receipts("MUSIC RETURN")==cycle,"One B returns to Music")
                send("CLOSE")
                wait_for(lambda:matches(frame(scanout),start_root,(100,35,410,105)),"Root title returned",20)
                time.sleep(.55)
                save_capture(frame(scanout),f"cycle-{cycle}-root")
                assert receipts("FRONT -1")==cycle
                if cycle<3:
                    # Retail reconstructs the root at its first row after leaving its options group.
                    # Read the carousel itself before selecting anything in the disposable profile.
                    def music_visible():
                        current=frame(scanout)
                        return matches(current,start_root,(125,145,428,315))
                    for step in range(8):
                        if music_visible():break
                        press("Down");time.sleep(.38)
                        save_capture(frame(scanout),f"cycle-{cycle}-seek-{step}")
                    wait_for(music_visible,"Music row on carousel",5)
                    press("A")
                    wait_for(lambda: receipts("MUSIC ENTRY")==cycle+2,"Root A reopens Music")
            press("Up");time.sleep(.45)
            save_capture(frame(scanout),"root-after-three-returns")
            print("Three retail EA Trax round trips passed, including song navigation and root input.")
        finally:
            if proc.poll() is None:
                send("QUIT")
            proc.stdin.close()
            proc.wait(timeout=15)


if __name__ == "__main__":
    main()

