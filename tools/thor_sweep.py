"""One finite, offline USB QA run. All mutable Android test data belongs to QA_PACKAGE."""
import argparse
import base64
from datetime import datetime
import hashlib
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import tempfile
import time
from uuid import uuid4

QA_PACKAGE = "org.oxideshift.corner.usbqa"
ACTIVITY = QA_PACKAGE + "/org.oxideshift.corner.UsbSweepCheck"
INSPECT = ACTIVITY + "$Inspect"


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


class Thor:
    def __init__(self, adb, serial, out, kit):
        self.adb = [str(adb), "-s", serial]
        self.out, self.kit = out, kit
        self.events = out / "events.jsonl"
        self.displays = []
        self.capture_sizes={}
        self.results = {}

    def note(self, event, **fields):
        row = dict(time=datetime.now().astimezone().isoformat(), event=event, **fields)
        with self.events.open("a", encoding="utf-8") as log:
            log.write(json.dumps(row) + "\n")
        print(event, *fields.values(), flush=True)

    def fatal(self, exc):
        reason = str(exc) or type(exc).__name__
        self.note("sweep interrupted", error=reason)
        self.results["sweep"] = {"status":"failed", "reason":reason}
        self.save_results()

    def save_results(self):
        pending=self.out/"results.pending.json"
        with pending.open("w",encoding="utf-8") as file:
            json.dump(self.results,file,indent=2)
            file.flush()
            os.fsync(file.fileno())
        pending.replace(self.out/"results.json")

    def call(self, *cmd, timeout=30, check=True):
        r = subprocess.run([*self.adb, *map(str, cmd)], capture_output=True, timeout=timeout)
        if check and r.returncode:
            detail = (r.stderr or r.stdout).decode(errors="replace").strip()[:500]
            raise RuntimeError("adb " + " ".join(map(str,cmd))[:180] + " exited " + str(r.returncode) +
                               (": " + detail if detail else " without diagnostic output"))
        return r.stdout

    def shell(self, *cmd, timeout=30):
        return self.call("shell", *cmd, timeout=timeout).decode(errors="replace").strip()

    def qa(self, command, timeout=30):
        # Every caller supplies a fixed test path or a validated name from the isolated sandbox.
        return self.shell("run-as " + QA_PACKAGE + " sh -c '" + command + "'", timeout=timeout)

    def state(self):
        inspect = "am broadcast --receiver-foreground -n '" + INSPECT + "'"
        for attempt in range(3):
            try:
                raw = self.shell(inspect)
                break
            except (RuntimeError, subprocess.TimeoutExpired) as exc:
                detail=str(exc).lower()
                device_missing = "device '" + self.adb[-1].lower() + "' not found" in detail
                transport = device_missing or any(message in detail for message in (
                    "cannot connect to daemon", "device offline", "transport error",
                    "no devices/emulators found", "error: closed", "connection reset by peer"))
                if not (transport or isinstance(exc,subprocess.TimeoutExpired)) or attempt==2:raise
                # This exact broadcast only reads the isolated activity. A lost
                # reply to an install, controller press or import is never replayed.
                self.note("retrying device observation",attempt=attempt+2)
                if getattr(self,"recover_usb",False):
                    try:
                        subprocess.run([self.adb[0],"start-server"],capture_output=True,timeout=15,check=True)
                        subprocess.run([*self.adb,"wait-for-device"],capture_output=True,timeout=25,check=True)
                    except subprocess.SubprocessError as reconnect:
                        self.note("USB observation recovery pending",reason=str(reconnect))
                else:time.sleep(1+attempt)
        m = re.search(r'data="([A-Za-z0-9+/=]+)"', raw)
        s = json.loads(base64.b64decode(m[1])) if m else {}
        self.observe_rocked(s)
        return s

    def observe_rocked(self, s):
        """Record each distinct ROCKED notice with the lower layout bounds the QA app reported at that moment."""
        if s.get("fightNotice") != "ROCKED!!" or "rockedLane" not in s:
            return
        lane = s.get("rockedLane") or [0, 0, 0, 0]
        # The lane is computed while drawing; a sample taken before the first ROCKED frame reads empty
        # (run 20261125-191325-945807). Wait for a drawn lane instead of judging an empty one.
        if len(lane) != 4 or lane[2] <= lane[0] or lane[3] <= lane[1]:
            return
        events = self.__dict__.setdefault("rocked_events", [])
        at = s.get("noticeUptimeMs")
        if any(e["at"] == at for e in events):
            return
        events.append({"at": at, "round": s.get("round"), "clock": s.get("clock"), "lane": s.get("rockedLane"),
                       "fight_panel": s.get("fightPanel"), "meter_panel": s.get("meterPanel"),
                       "width": s.get("lowerWidth"), "clear": rocked_clear(s)})
        self.note("ROCKED observed", number=len(events), clear=events[-1]["clear"])

    def wants(self, name):
        only = getattr(self, "only", None)
        return not only or name in only

    def launch(self, phase):
        if getattr(self,"recorder",None):self.recorder.resume()
        token = uuid4().hex
        # The transfer can outlive Android's interactive timeout. The same
        # token, delivered to the same activity, makes one interrupted start safe to retry.
        for attempt in range(2):
            self.shell("input","keyevent","KEYCODE_WAKEUP")
            raw=self.shell("am","start","-W","--activity-single-top","-n",ACTIVITY,
                           "--es","phase",phase,"--es","run_id",token,timeout=60)
            if "Exception" in raw or "Error:" in raw:raise RuntimeError(raw)
            try:
                return self.wait(lambda s:s.get("request")==token,45,"activity receipt for "+phase)
            except RuntimeError:
                self.capture_diagnostics(phase)
                if attempt:raise
                self.note("retrying same isolated activity request",phase=phase)
        raise AssertionError("unreachable")
    
    def capture_diagnostics(self, phase):
        parts=[("activity",("dumpsys","activity","activities",QA_PACKAGE)),
               ("exit",("dumpsys","activity","exit-info",QA_PACKAGE)),
               ("power",("dumpsys","power"))]
        uid=self.shell("pm","list","packages","-U",QA_PACKAGE)
        match=re.search(r"uid:(\d+)",uid)
        if match:parts.append(("app-log",("logcat","-d","-t","1500","--uid="+match[1])))
        for label,args in parts:
            try:
                data=self.shell(*args)
                if label=="power":
                    data="\n".join(line for line in data.splitlines() if any(key in line for key in
                                 ("Wakefulness=","Wakefulness:","Display Power:","ScreenTimeoutOverrideFromWindowManager")))
                (self.out/(phase+"-"+label+".txt")).write_text(data,encoding="utf-8")
            except (RuntimeError,OSError,subprocess.SubprocessError):pass

    def wait(self, test, seconds, label):
        end = time.monotonic() + seconds
        previous = None
        s = {}
        while time.monotonic() < end:
            s = self.state()
            if test(s):
                return s
            progress = s.get("setup", "")
            if progress and progress != previous:
                previous = progress
                if seconds > 200:
                    self.note("setup", message=progress)
            time.sleep(1 if seconds < 90 else 3)
        keys=("outcome","busy","active","focused","root","front","menu","scene","serial","cabinet",
              "companionDisplay","displaySelection","savedDisplaySelection","rendererSelection",
              "savedRendererSelection","renderer","quality","renderHeight","targetFps")
        self.note("observation timed out",label=label,state={key:s[key] for key in keys if key in s})
        raise RuntimeError("Timed out waiting for " + label + " (" + str(previous) + ")")

    def transfer(self, source, name):
        assert name in ("usb-disc.iso", "usb-save.zip")
        target = "files/" + name
        size = source.stat().st_size
        self.note("checking USB source", file=name, size=size)
        sha = digest(source)
        def remote_sha(file):
            result=self.qa("test -f " + file + " && sha256sum " + file + " || true",timeout=300).split()
            return result[0] if result else ""
        if remote_sha(target) == sha:
            return
        # adb push uses the Windows file-sync transport. A regular Windows disk
        # handle supplied to adb shell stdin can reach Android as an empty stream.
        chunk_size = 64 * 1024 * 1024
        partial = target + ".partial"
        remote = "/data/local/tmp/fn3-usbqa-" + uuid4().hex + ".chunk"
        self.qa("mkdir -p files; touch " + partial)
        offset = int(self.qa("stat -c %s " + partial))
        with source.open("rb") as stream, tempfile.TemporaryDirectory(prefix="fn3-usb-") as folder:
            # Resume only a matching prefix. This also repairs a write interrupted inside a chunk.
            if offset:
                matched = hashlib.sha256()
                remaining = min(size,offset)
                while remaining:
                    data=stream.read(min(remaining,4*1024*1024))
                    if not data:raise IOError("Source shortened during USB transfer")
                    matched.update(data);remaining-=len(data)
                if offset>size or matched.hexdigest()!=remote_sha(partial):
                    self.qa("truncate -s 0 "+partial);offset=0
                stream.seek(offset)
            # A previous run may already hold most of the ISO. Reserve room for
            # extraction, preparation and the staging chunk in addition to the bytes left.
            if name == "usb-disc.iso":
                df = self.shell("df", "-k", "/data").splitlines()[-1].split()
                if int(df[3]) * 1024 < size - offset + 5 * 1024**3:
                    raise RuntimeError("Insufficient space for the remaining USB copy and disc preparation")
            chunk=Path(folder)/"payload.bin"
            self.note("copying through ADB file sync",file=name, verified_prefix=offset)
            try:
                while offset < size:
                    data=stream.read(min(chunk_size,size-offset))
                    if not data:raise IOError("Source shortened during USB transfer")
                    chunk.write_bytes(data)
                    for attempt in range(2):
                        try:
                            self.call("push",chunk,remote,timeout=300)
                            # The shell reads its own staging file. Only bytes pass through the
                            # pipe into the separately installed test app's private directory.
                            self.shell("cat " + remote + " | run-as " + QA_PACKAGE +
                                       " sh -c 'cat >> " + partial + "'",timeout=120)
                            copied=int(self.qa("stat -c %s "+partial))
                            if copied!=offset+len(data):
                                raise IOError("Device reported %d bytes; expected %d"%(copied,offset+len(data)))
                            break
                        except (OSError,RuntimeError,subprocess.SubprocessError) as err:
                            self.qa("truncate -s "+str(offset)+" "+partial)
                            if attempt:raise
                            self.note("retrying one USB chunk",offset=offset,reason=str(err))
                    offset+=len(data)
                    if offset==size or offset//(512*1024*1024)!=(offset-len(data))//(512*1024*1024):
                        self.note("USB transfer progress",file=name,bytes=offset,total=size)
            finally:
                self.shell("rm","-f",remote)
        received=remote_sha(partial)
        if received!=sha:
            raise RuntimeError("USB file-sync digest mismatch for "+name+"; expected "+sha+", got "+received)
        self.qa("mv "+partial+" "+target)
        self.note("copy-verified",file=name,bytes=size)

    def install(self, name):
        path = self.kit / name
        raw = self.call("install", "-r", path, timeout=360).decode(errors="replace")
        if "Success" not in raw:
            raise RuntimeError(raw)
        self.note("installed", apk=name)

    def foreground(self):
        focus = self.shell("dumpsys window | grep -E 'mCurrentFocus=|mFocusedApp='")
        return any(QA_PACKAGE+"/" in l for l in focus.splitlines() if "mCurrentFocus" in l)

    def capture(self, name):
        if not self.foreground():
            self.note("snapshot skipped", reason="QA app does not own foreground", label=name)
            return
        displays=self.displays
        if getattr(self,"paired",False) and self.state().get("companionDisplay",-1)<=0:
            displays=displays[:1]
        for i, display in enumerate(displays):
            b = self.call("exec-out", "screencap", "-p", "-d", display)
            if b.startswith(b"\x89PNG"):
                if len(b)>=24:
                    self.capture_sizes[display]=struct.unpack(">II",b[16:24])
                (self.out / (name + "-display-" + str(i) + ".png")).write_bytes(b)

    def case(self, name, fn):
        if not self.wants(name):
            return False
        recorder=None
        if getattr(self,"paired",False):
            from thor_recording import CaseRecording
            recorder=self.recorder=CaseRecording(self,name)
            recorder.begin()
        try:
            details = fn()
            self.results[name] = {"status":"passed","evidence":details}
        except Exception as err:
            self.results[name] = {"status":"failed","reason":str(err)}
            self.note("case failed", case=name, reason=str(err))
            try: self.capture(name + "-failed")
            except (OSError, RuntimeError, subprocess.SubprocessError): pass
        finally:
            if recorder:
                errors=recorder.finish()
                self.recorder=None
                if errors:self.results[name]["recording_errors"]=errors
        self.save_results()
        return self.results[name]["status"] == "passed"

    def stop_test_app(self):
        if getattr(self,"recorder",None):self.recorder.pause()
        self.shell("am", "force-stop", QA_PACKAGE)

    def finish_import(self):
        self.wait(lambda s: "busy" in s and not s["busy"], 2700, "disc import and compilation")
        s = self.state()
        if not s["prepared"] or not self.qa("test -f files/retail/game/default.xbe && echo verified || true").endswith("verified"):
            raise RuntimeError("Import did not complete: " + s.get("setup",""))
        return s.get("setup","")

    def app_files(self):
        return self.qa("find files/retail -maxdepth 2 -type f; find files/save-slots -type f | sort")

    def profile_hashes(self):
        return self.qa("if test -d files/save-slots; then find files/save-slots -type f -exec sha256sum {} \\; | sort; fi")

    def prepare_update(self):
        current = self.launch("setup")
        if current.get("prepared"): return "ALREADY_PREPARED"
        res = self.launch("game")
        if res["outcome"] == "PREPARING":
            self.wait(lambda s:not s.get("busy",True), 1800, "game preparation")
        if not self.state().get("prepared"):
            raise RuntimeError("Updated runtime failed to prepare")
        return res["outcome"]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for k in ("adb","kit","iso","out"):
        parser.add_argument("--"+k, required=True, type=Path)
    parser.add_argument("--candidate-first",action="store_true",
                        help="Upgrade the existing isolated package before exercising import recovery")
    parser.add_argument("--reuse-installed-disc", action="store_true",
                        help="Require the existing verified import and never transfer the ISO")
    parser.add_argument("--paired-recordings",action="store_true",help="Record game-owned displays throughout each QA case")
    parser.add_argument("--continue-gameplay", action="store_true",
                        help="Continue the completed 79.5 upgrade and gallery run using the retained isolated installation")
    parser.add_argument("--only", help="Comma-separated case names; run just these gameplay cases (requires --continue-gameplay)")
    args = parser.parse_args()
    if args.continue_gameplay and not (args.candidate_first and args.reuse_installed_disc):
        parser.error("A gameplay continuation requires the installed disc and a verified candidate update")
    if args.only and not args.continue_gameplay:
        parser.error("--only selects gameplay cases and requires --continue-gameplay")
    manifest = json.loads((args.kit/"hashes.json").read_text(encoding="utf-8"))
    for name, sha in manifest.items():
        if digest(args.kit/name) != sha:
            parser.error("Unexpected QA fixture: "+name)
    devices = subprocess.check_output([args.adb,"devices","-l"],text=True,timeout=20)
    rows = [r.split() for r in devices.splitlines() if re.search(r"\bmodel:\S*thor\S*", r, re.I)]
    if len(rows)!=1 or rows[0][1]!="device" or ":" in rows[0][0]:
        parser.error("Connect the authorized Thor over USB.")
    serial = rows[0][0]
    out = args.out / datetime.now().strftime("%Y%m%d-%H%M%S-%f")
    out.mkdir(parents=True)
    usb = Thor(args.adb,serial,out,args.kit)
    usb.paired=args.paired_recordings
    usb.continue_gameplay=args.continue_gameplay
    usb.only={name.strip() for name in args.only.split(",") if name.strip()} if args.only else None
    usb.recover_usb=True
    usb.note("USB sweep", folder=str(out))
    sys.excepthook = lambda kind, exc, tb: usb.fatal(exc)
    usb.displays = re.findall(r"(?m)^\s*Display\s+(\d+)\s+\(",usb.shell("dumpsys","SurfaceFlinger","--display-id"))
    if len(usb.displays) < 2:
        raise RuntimeError("Android did not enumerate both physical displays")
    if not args.reuse_installed_disc and not args.iso.is_file():
        raise RuntimeError("The original Xbox ISO is unavailable: "+str(args.iso))
    usb.shell("input","keyevent","KEYCODE_WAKEUP")
    packages = usb.shell("pm", "list", "packages", QA_PACKAGE).splitlines()
    existed = "package:" + QA_PACKAGE in packages
    if args.continue_gameplay and not existed:
        raise RuntimeError("The previously tested isolated installation is missing")
    # Begin on the earlier build when the isolated package is absent. Never uninstall
    # or downgrade an existing installation to manufacture a fresh-update result.
    if args.candidate_first:
        if existed:
            usb.capture_diagnostics("before-update")
            preserved=usb.profile_hashes()
            old=usb.shell("dumpsys","package",QA_PACKAGE)
        usb.install("candidate.apk")
        if existed:
            new=usb.shell("dumpsys","package",QA_PACKAGE)
            old_code=re.search(r"\bversionCode=(\d+)",old)
            new_code=re.search(r"\bversionCode=(\d+)",new)
            if not old_code or not new_code or usb.profile_hashes()!=preserved:
                raise RuntimeError("Isolated update could not verify version and save preservation")
            usb.note("installed over isolated build",before=old_code[1],after=new_code[1],
                     existing_save_files=len(preserved.splitlines()))
    elif not existed:
        usb.install("prior-runtime.apk")
    if existed:
        # Reuse an earlier isolated run without clearing any package or pretending it was a fresh install.
        usb.note("existing isolated installation", package=QA_PACKAGE)
    version = usb.shell("dumpsys","package",QA_PACKAGE)
    if "android.permission.INTERNET" in version or "DEBUGGABLE" not in version:
        raise RuntimeError("Unexpected private package flags")
    base_version=re.search(r"\bversionCode=(\d+)",version)
    if not base_version:raise RuntimeError("Isolated app version unavailable")
    usb.launch("setup")
    usb.capture("00-setup")
    already = usb.qa("test -f files/retail/game/default.xbe && echo yes || true") == "yes"
    if args.continue_gameplay:
        if not already or not usb.profile_hashes():
            raise RuntimeError("The previous QA game and imported saves are required to continue")
        usb.prepare_update()
        return run_gameplay(usb)
    if not already:
        if args.reuse_installed_disc:
            raise RuntimeError("Existing QA import is missing. No ISO was transferred.")
        usb.transfer(args.iso,"usb-disc.iso")
        usb.launch("disc")
        def interrupt():
            usb.wait(lambda s: s.get("busy") and "Compiling Xbox game:" in s.get("setup",""), 2400, "an active compilation")
            usb.capture("01-compile")
            usb.stop_test_app()
            usb.launch("setup")
            return "Interrupted first compilation of the isolated game"
        did_interrupt = usb.case("interrupt-preparation",interrupt)
        if did_interrupt:
            # First install publishes verified assets before the compiler returns.
            # The interrupt can fall on either side of that atomic rename.
            published = usb.qa("test -f files/retail/game/default.xbe && echo yes || true") == "yes"
            if published:
                usb.note("resuming preparation from the installed disc")
                usb.prepare_update()
            else:
                usb.launch("disc")
        if not usb.case("fresh-import-and-prepare",usb.finish_import):
            return 2
    else:
        usb.note("fresh import already present", action="existing data retained")
    usb.stop_test_app()
    usb.launch("setup")
    usb.transfer(args.kit/"qa-save.zip","usb-save.zip")
    def load_save():
        usb.launch("save")
        s=usb.wait(lambda s: "busy" in s and not s["busy"],120,"normal save import")
        if "Imported save selected" not in s.get("setup",""):
            raise RuntimeError(str(s.get("setup")))
        files=usb.profile_hashes()
        if not files: raise RuntimeError("No restored profile files")
        return len(files.splitlines())
    if not usb.case("save-import",load_save):return 3
    before = usb.profile_hashes()
    def upgrade():
        usb.stop_test_app()
        usb.install("candidate.apk")
        usb.prepare_update()
        usb.stop_test_app()
        if usb.profile_hashes() != before:raise RuntimeError("Restored save changed across runtime updates")
        updated=usb.shell("dumpsys","package",QA_PACKAGE)
        new_version=re.search(r"\bversionCode=(\d+)",updated)
        if not new_version:raise RuntimeError("Updated app version unavailable")
        if int(new_version[1])<=int(base_version[1]):
            return {"before":int(base_version[1]),"after":int(new_version[1]),"reinstall_only":True}
        return {"before":int(base_version[1]),"after":int(new_version[1]),"save_files":len(before.splitlines())}
    if usb.case("runtime-update",upgrade) and usb.results["runtime-update"].get("evidence",{}).get("reinstall_only"):
        usb.results["runtime-update"]["status"]="unverified"
        usb.save_results()
        usb.note("candidate already installed; reinstall preserved saves")
    # An earlier failure may leave the prior runtime installed. Always restore the pinned candidate.
    usb.install("candidate.apk")
    usb.launch("setup")
    def bad_module():
        listing=usb.qa("find files/disc-build -name libfightnight_game.so")
        matches=[p for p in listing.splitlines() if re.fullmatch(r"files/disc-build/[0-9a-f]{64}/libfightnight_game\.so",p)
                 and p.split("/")[2]==(args.kit/"kit-id.txt").read_text().strip()]
        if len(matches)!=1:raise RuntimeError("No unique candidate module in the isolated installation")
        usb.stop_test_app()
        # Prepared modules are read-only. Replacing the directory entry simulates a
        # truncated file without relaxing permissions on the genuine module.
        module=matches[0]
        staged=module+".qa-damaged"
        usb.qa("printf BAD > "+staged+" && mv -f "+staged+" "+module)
        usb.prepare_update()
        if not usb.state().get("prepared"):raise RuntimeError("Damaged isolated module did not recover")
        return "Rejected truncated module and rebuilt from the verified disc"
    usb.case("damaged-module-recovery",bad_module)
    if usb.profile_hashes() != before:usb.note("save hash change before gameplay")
    if usb.state().get("prepared"):
        usb.qa("rm -f files/usb-save.zip" if args.reuse_installed_disc else "rm -f files/usb-disc.iso files/usb-save.zip")
    return run_gameplay(usb)


def rocked_clear(s):
    """True when the ROCKED lane stays out of the band from the fighter panel top to the punch panel bottom
    (clock, names, health and stamina rows, punch counts). None when the app did not report the bounds."""
    lane, fight, meters = s.get("rockedLane"), s.get("fightPanel"), s.get("meterPanel")
    if not lane or len(lane) != 4 or lane[2] <= lane[0] or lane[3] <= lane[1]:
        return False
    if not fight or not meters:
        return None
    top, bottom = fight[1], meters[3]
    return lane[3] <= top or lane[1] >= bottom


def run_gameplay(usb):
    from thor_sweep_game import sweep
    try:
        sweep(usb)
    finally:
        usb.stop_test_app()
        (usb.out/"exits.txt").write_text(usb.shell("dumpsys","activity","exit-info",QA_PACKAGE),encoding="utf-8")
    usb.note("Finished", results=str(usb.out/"results.json"))
    return int(any(v["status"] != "passed" for v in usb.results.values()))


if __name__ == "__main__":
    raise SystemExit(main())
