import base64
from contextlib import redirect_stdout
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import Mock, patch

sys.path.insert(0,str(Path(__file__).resolve().parents[1]/"tools"))
import thor_sweep as sweep
import thor_sweep_game as game


class AndroidFixture:
    def __init__(self, present=False, published_on_interrupt=False):
        self.installed = present
        self.commands = []
        self.package = sweep.QA_PACKAGE
        self.phase = ""
        self.token = ""
        self.prepared = False
        self.disc = False
        self.interrupted = False
        self.published_on_interrupt = published_on_interrupt
        self.polls = 0
        self.kit = "a"*64

    def command(self, owner, *cmd, **opts):
        cmd = list(map(str,cmd))
        self.commands.append(cmd)
        args = cmd[1:]
        r = ""
        if cmd[0] == "install":
            self.installed=True; self.prepared=False; self.version=82 if Path(cmd[-1]).name=="candidate.apk" else 81; r="Success"
        elif args[:2]==["pm","path"] and not self.installed:
            raise RuntimeError("pm path exited 1 without diagnostic output")
        elif args[:3]==["pm","list","packages"]:
            r="package:"+self.package if self.installed else ""
        elif "--display-id" in args:r="Display 100 (HWC display 0)\nDisplay 101 (HWC display 1)"
        elif args[:1]==["df"]:r="/dev/block/dm-0 100000000 2000000 98000000 3% /data"
        elif args[:2]==["dumpsys","package"]:r=f"DEBUGGABLE versionCode={self.version}"
        elif args[:2]==["am","start"]:
            self.phase=args[args.index("phase")+1];self.token=args[-1];self.polls=0
            if self.phase=="disc" and self.interrupted:self.disc=True
            r="Status: ok"
        elif args[:2]==["am","force-stop"]:
            if self.phase=="disc":
                self.interrupted=True
                if self.published_on_interrupt:self.disc=True
        elif args and args[0].startswith("am broadcast"):
            self.polls+=1
            doing=self.phase=="disc" and (not self.interrupted or self.polls==1)
            if self.phase in ("disc","game") and self.polls>1 and not doing:self.prepared=True
            state=dict(request=self.token,busy=doing,prepared=self.prepared,setup="Compiling Xbox game: 2 / 252" if doing
                       else "Imported save selected" if self.phase=="save" else "Xbox game ready",
                       outcome={"game":"PREPARING","save":"IMPORT_SAVE","disc":"IMPORT_DISC"}.get(self.phase,"READY"))
            r='Broadcast completed: result=0, data="'+base64.b64encode(json.dumps(state).encode()).decode()+'"'
        elif args and args[0].startswith("run-as "):
            q=args[0]
            if "find files/disc-build" in q:r=f"files/disc-build/{self.kit}/libfightnight_game.so"
            elif "default.xbe && echo yes" in q:r="yes" if self.disc else ""
            elif "default.xbe && echo verified" in q:r="verified" if self.disc else ""
            elif "save-slots" in q:r="abc file"
            elif "printf BAD" in q:
                if ".qa-damaged && mv -f " not in q:raise RuntimeError("Prepared module is read-only")
                self.prepared=False
        return r.encode()


class SweepTests(unittest.TestCase):
    def test_first_install_through_update(self):
        self.check_first_install(False)

    def test_interrupt_after_assets_are_published(self):
        self.check_first_install(True)

    def check_first_install(self,published):
        before_hook=sys.excepthook
        try:
            with tempfile.TemporaryDirectory() as tmp:
                root=Path(tmp);kit=root/"kit";kit.mkdir()
                (kit/"kit-id.txt").write_text("a"*64)
                (kit/"hashes.json").write_text("{}")
                iso=root/"disc.iso";iso.write_bytes(b"x")
                f=AndroidFixture(published_on_interrupt=published)
                def call(owner,*cmd,**opts):return f.command(owner,*cmd,**opts)
                with patch.object(sys,"argv",["test","--adb","adb","--kit",str(kit),"--iso",str(iso),"--out",str(root/"out")]), \
                     patch.object(subprocess,"check_output",return_value="serial device model:AYN_Thor\n"), \
                     patch.object(sweep.Thor,"call",call),patch.object(sweep.Thor,"capture"),patch.object(sweep.Thor,"transfer"), \
                     patch.object(game,"sweep") as gameplay,patch("time.sleep"),redirect_stdout(io.StringIO()):
                    self.assertEqual(sweep.main(),0)
                gameplay.assert_called_once()
                disc_requests=[args for args in f.commands if "phase" in args and args[args.index("phase")+1]=="disc"]
                self.assertEqual(len(disc_requests),1 if published else 2)
                results=json.loads(next((root/"out").rglob("results.json")).read_text())
                self.assertEqual(set(results),{"interrupt-preparation","fresh-import-and-prepare","save-import","runtime-update","damaged-module-recovery"})
                self.assertTrue(all(r["status"]=="passed" for r in results.values()))
                self.assertTrue(all(c[-1]==sweep.QA_PACKAGE for c in f.commands if c[1:3]==["am","force-stop"]))
                self.assertFalse(any("uninstall" in c or "clear" in c for c in f.commands))
        finally:sys.excepthook=before_hook

    def test_resumed_binary_copy(self):
        import hashlib
        with tempfile.TemporaryDirectory() as tmp:
            root=Path(tmp)
            source=root/"source.iso"
            data=bytes(range(256))*12+b"\x00\xff\r\n\0"
            source.write_bytes(data)
            thor=sweep.Thor("adb","serial",root,root)
            partial=bytearray(data[:417])
            chunk=[]
            final=[]
            def scoped(cmd,**kwargs):
                if "sha256sum" in cmd:
                    if "partial" not in cmd:return ""
                    return hashlib.sha256(partial).hexdigest()+"  files/usb-disc.iso.partial"
                if "stat -c %s" in cmd:return str(len(partial))
                if cmd.startswith("truncate"):del partial[int(cmd.split()[2]):]
                if cmd.startswith("mv"):final.append(bytes(partial))
                return ""
            def push(*args,**kwargs):
                self.assertEqual(args[0],"push")
                chunk.append(Path(args[1]).read_bytes());return b""
            def shell(*args,**kwargs):
                if args[0]=="df":
                    return "/dev/block/dm-0 100000000 2000000 98000000 3% /data"
                if args[0].startswith("cat "):
                    partial.extend(chunk[-1] if len(chunk)>1 else chunk[-1][:3])
                return ""
            with patch.object(thor,"qa",scoped),patch.object(thor,"call",push),patch.object(thor,"shell",shell), \
                 redirect_stdout(io.StringIO()):
                thor.transfer(source,"usb-disc.iso")
            self.assertEqual(final,[data])
            self.assertEqual(chunk,[data[417:],data[417:]])

    def test_manual_treatment_needs_the_visible_lower_pad(self):
        with tempfile.TemporaryDirectory() as tmp:
            usb=Mock(kit=Path(tmp),out=Path(tmp))
            usb.foreground.return_value=True
            journey=game.Journey(usb,None)
            clock=FakeClock()
            start={"treatmentMoves":5,"treatmentReleases":1,"treatmentPad":[0,20,40,80,20]}
            journey.treat("windy",start,clock=clock,sleep=clock.sleep)
            usb.shell.assert_not_called()
            # Unchanged counters and a move without a release do not count. Only a move and a release do.
            usb.state.side_effect=[dict(scene="TREATMENT",treatmentMoves=5,treatmentReleases=1),
                                   dict(scene="TREATMENT",treatmentMoves=7,treatmentReleases=1),
                                   dict(scene="TREATMENT",treatmentMoves=7,treatmentReleases=2)]
            start["treatmentPad"][0]=6
            self.assertEqual("healed",journey.treat("windy",start,clock=clock,sleep=clock.sleep))
            usb.shell.assert_called_once_with("input","touchscreen","-d",6,"swipe",20,40,80,20,320)
            self.assertEqual(journey.healing_venues,{"windy"})

    def test_all_root_destinations_from_both_ends(self):
        with tempfile.TemporaryDirectory() as tmp, patch("time.sleep"):
            usb=Mock(kit=Path(tmp),out=Path(tmp))
            pad=Mock()
            journey=game.Journey(usb,pad)
            for start in (0,len(game.ROOT)-1):
                for target in game.ROOT:
                    selected=start
                    usb.state.side_effect=lambda: {"root":True,"menu":game.ROOT[selected]}
                    usb.wait.side_effect=lambda check,*args: usb.state() if check(usb.state()) else self.fail("Unknown selection")
                    def press(key):
                        nonlocal selected
                        self.assertIn(key,("Down","Up"))
                        selected=max(0,min(len(game.ROOT)-1,selected+(1 if key=="Down" else -1)))
                    pad.press.side_effect=press
                    journey.root(target)
                    self.assertEqual(game.ROOT[selected],target)

    def test_empty_diagnostic(self):
        f=sweep.Thor("adb","serial",Path("."),Path("."))
        with patch("subprocess.run",return_value=subprocess.CompletedProcess([],1,b"",b"")):
            with self.assertRaisesRegex(RuntimeError,"shell pm path.*exited 1"):
                f.shell("pm","path",sweep.QA_PACKAGE)

    def test_checkpoint_survives_an_interrupted_replacement(self):
        with tempfile.TemporaryDirectory() as tmp:
            directory=Path(tmp)
            usb=sweep.Thor("adb","serial",directory,directory)
            usb.results["setup"]={"status":"passed"}
            usb.save_results()
            usb.results["venue"]={"status":"failed"}
            with patch.object(Path,"replace",side_effect=OSError("interrupted")), self.assertRaises(OSError):
                usb.save_results()
            self.assertEqual(list(json.loads((directory/"results.json").read_text())),["setup"])
            usb.save_results()
            self.assertEqual(json.loads((directory/"results.json").read_text()),usb.results)

    def test_fallback_combination_only_uses_the_analog_sticks(self):
        usb=Mock()
        usb.shell.return_value='data="GESTURE"'
        pad=game.Controls(usb)
        with patch("time.sleep"), patch.object(pad,"press") as press:
            pad.combo()
            self.assertIn("isolated.STICK_COMBO",usb.shell.call_args.args[0])
            press.assert_not_called()

    def test_device_observation_recovers_without_replaying_an_action(self):
        with tempfile.TemporaryDirectory() as tmp, patch("time.sleep"):
            usb=sweep.Thor("adb","serial",Path(tmp),Path(tmp))
            with patch.object(usb,"shell",side_effect=[RuntimeError("cannot connect to daemon"),'data="e30="']) as call:
                self.assertEqual(usb.state(),{})
                self.assertEqual(call.call_count,2)
                self.assertEqual(call.call_args_list[0],call.call_args_list[1])

    def test_replug_waits_for_the_original_usb_serial(self):
        with tempfile.TemporaryDirectory() as tmp:
            usb=sweep.Thor("adb","AYN123",Path(tmp),Path(tmp))
            usb.recover_usb=True
            with patch.object(usb,"shell",side_effect=[RuntimeError("adb: device 'AYN123' not found"),'data="e30="']), \
                    patch("subprocess.run") as recover:
                self.assertEqual(usb.state(),{})
                self.assertEqual([c.args[0] for c in recover.call_args_list],
                                 [["adb","start-server"],["adb","-s","AYN123","wait-for-device"]])
            with patch.object(usb,"shell",side_effect=RuntimeError("adb: device unauthorized")), \
                    patch("subprocess.run") as recover:
                with self.assertRaisesRegex(RuntimeError,"unauthorized"):usb.state()
                recover.assert_not_called()


class DisplayOwnershipTest(unittest.TestCase):
    def test_single_panel_capture_uses_only_the_game_display(self):
        with tempfile.TemporaryDirectory() as folder:
            usb=sweep.Thor("adb","test",Path(folder),Path(folder))
            usb.paired=True
            usb.displays=["135","246"]
            with patch.object(usb,"foreground",return_value=True), patch.object(usb,"state",return_value={}), \
                    patch.object(usb,"call",return_value=b"") as capture:
                usb.capture("gallery")
                capture.assert_called_once_with("exec-out","screencap","-p","-d","135")


class FakeClock:
    def __init__(self):self.now=0.0
    def __call__(self):return self.now
    def sleep(self,seconds):self.now+=seconds


class TreatmentUsb:
    """Minimal stand-in for the USB QA session. After heal_after swipes the game counts a move and release."""
    def __init__(self,scene="TREATMENT",heal_after=None,foreground=True):
        self.scene,self.heal_after,self.in_foreground=scene,heal_after,foreground
        self.shells,self.captures,self.notes=[],[],[]
    def foreground(self):return self.in_foreground
    def shell(self,*args,**options):self.shells.append(args);return ""
    def state(self):
        healed=self.heal_after is not None and len(self.shells)>=self.heal_after
        return dict(scene=self.scene,treatmentMoves=1 if healed else 0,treatmentReleases=1 if healed else 0)
    def capture(self,name):self.captures.append(name)
    def note(self,event,**fields):self.notes.append((event,fields))


class TreatmentTests(unittest.TestCase):
    PAD=[1,100,200,300,400]
    def journey(self,usb):
        run=object.__new__(game.Journey)
        run.usb=usb;run.healing_venues=set();run.missed_treatments=[]
        return run
    def window(self,**extra):
        return dict(scene="TREATMENT",round=3,treatmentPad=self.PAD,treatmentMoves=0,treatmentReleases=0,**extra)

    def test_first_gesture_heals_and_marks_venue(self):
        usb=TreatmentUsb(heal_after=1);run=self.journey(usb);clock=FakeClock()
        self.assertEqual("healed",run.treat("staples",self.window(),clock=clock,sleep=clock.sleep))
        self.assertEqual({"staples"},run.healing_venues)
        self.assertEqual(1,len(usb.shells));self.assertEqual(["manual-heal-staples"],usb.captures)
        self.assertEqual([],run.missed_treatments)

    def test_second_gesture_can_heal_inside_the_same_window(self):
        usb=TreatmentUsb(heal_after=2);run=self.journey(usb);clock=FakeClock()
        self.assertEqual("healed",run.treat("windy",self.window(),clock=clock,sleep=clock.sleep))
        self.assertEqual(2,len(usb.shells));self.assertEqual({"windy"},run.healing_venues)

    def test_window_closing_before_the_gesture_registers_is_recorded_not_fatal(self):
        # Alpha 79.5.1 Staples round 3: the bout was healthy but the retail window closed first.
        usb=TreatmentUsb(scene="RINGSIDE");run=self.journey(usb);clock=FakeClock()
        self.assertEqual("missed",run.treat("staples",self.window(),clock=clock,sleep=clock.sleep))
        self.assertEqual(set(),run.healing_venues)
        self.assertEqual([],usb.captures)
        self.assertEqual(1,len(run.missed_treatments))
        miss=run.missed_treatments[0]
        self.assertEqual(("staples",3),(miss["venue"],miss["round"]))
        self.assertEqual("treatment window missed",usb.notes[0][0])

    def test_open_window_that_never_counts_the_gesture_is_a_failure(self):
        usb=TreatmentUsb(scene="TREATMENT",heal_after=None);run=self.journey(usb);clock=FakeClock()
        with self.assertRaisesRegex(RuntimeError,"while the treatment window was open"):
            run.treat("garden",self.window(),clock=clock,sleep=clock.sleep)
        self.assertEqual(2,len(usb.shells))
        self.assertEqual(set(),run.healing_venues);self.assertEqual([],run.missed_treatments)

    def test_unavailable_pad_does_nothing_and_asks_to_retry(self):
        usb=TreatmentUsb();run=self.journey(usb);clock=FakeClock()
        for state in (dict(scene="TREATMENT"),dict(scene="TREATMENT",treatmentPad=[0,1,2,3,4]),
                      dict(scene="TREATMENT",treatmentPad=[1,2,3])):
            self.assertEqual("no-pad",run.treat("palace",state,clock=clock,sleep=clock.sleep))
        self.assertEqual([],usb.shells)

    def test_a_move_without_a_release_is_not_a_heal(self):
        class MoveOnly(TreatmentUsb):
            def state(self):return dict(scene="TREATMENT",treatmentMoves=3,treatmentReleases=0)
        usb=MoveOnly();run=self.journey(usb);clock=FakeClock()
        with self.assertRaisesRegex(RuntimeError,"while the treatment window was open"):
            run.treat("palace",self.window(),clock=clock,sleep=clock.sleep)
        self.assertEqual(set(),run.healing_venues)

    def test_lost_focus_still_raises(self):
        usb=TreatmentUsb(foreground=False);run=self.journey(usb);clock=FakeClock()
        with self.assertRaisesRegex(RuntimeError,"lost focus"):
            run.treat("aragon",self.window(),clock=clock,sleep=clock.sleep)
        self.assertEqual([],usb.shells)


class GameplayRowTests(unittest.TestCase):
    def test_wait_for_view_returns_true_when_a_later_poll_matches(self):
        clock=FakeClock();polls=[]
        def views():
            polls.append(clock.now);return ["blank"] if len(polls)<4 else ["blank","gameplay"]
        self.assertTrue(game.wait_for_view(views,lambda view:view=="gameplay",10,sleep=clock.sleep,clock=clock))
        self.assertEqual(4,len(polls))

    def test_wait_for_view_times_out_without_raising(self):
        clock=FakeClock()
        self.assertFalse(game.wait_for_view(lambda:["blank"],lambda view:False,2,sleep=clock.sleep,clock=clock))
        self.assertGreaterEqual(clock.now,2)

    def test_endurance_return_to_options_uses_the_animation_tolerant_check(self):
        # Alpha 79.5.1 failed here although the game was on the Gameplay row. A mean pixel-difference
        # threshold against a still reference cannot match the animated retail backdrop.
        import inspect
        source=inspect.getsource(game.Journey.endurance_settings)
        self.assertNotIn('wait_picture("options-gameplay-row"',source)
        self.assertEqual(2,source.count("wait_for_view(self.views,gameplay_selected"))
        self.assertIn("row_reference",source)


class RockedTests(unittest.TestCase):
    # Bounds from the 79.5.2 layoutlib replay: fighter panel from y=52, punch panel ends at y=288.
    FIGHT=[12,52,348,229]; METERS=[12,229,348,288]
    def state(self,lane,**extra):
        return dict(fightNotice="ROCKED!!",noticeUptimeMs=1,rockedLane=lane,fightPanel=self.FIGHT,meterPanel=self.METERS,**extra)
    def test_lane_below_the_punch_panel_is_clear(self):
        self.assertTrue(sweep.rocked_clear(self.state([0,291,620,540])))
    def test_header_lane_above_the_fighter_panel_is_clear(self):
        self.assertTrue(sweep.rocked_clear(self.state([227,0,360,52])))
    def test_lane_over_the_stamina_rows_is_not_clear(self):
        self.assertFalse(sweep.rocked_clear(self.state([0,200,360,300])))
    def test_missing_bounds_are_unknown_not_clear(self):
        self.assertIsNone(sweep.rocked_clear(dict(rockedLane=[0,291,620,540])))
        self.assertFalse(sweep.rocked_clear(dict(rockedLane=[0,0,0,0])))
    def test_verdict_never_passes_without_a_real_event(self):
        self.assertEqual("unverified",game.rocked_verdict([])["status"])
        self.assertEqual("unverified",game.rocked_verdict([{"clear":None}])["status"])
        self.assertEqual("failed",game.rocked_verdict([{"clear":True},{"clear":False}])["status"])
        self.assertEqual("passed",game.rocked_verdict([{"clear":True}])["status"])
    def test_each_notice_is_recorded_once(self):
        with tempfile.TemporaryDirectory() as folder:
            usb=sweep.Thor("adb","test",Path(folder),Path(folder))
            # Real note(): run 20261125-182301 crashed on a keyword clash that a stubbed note() hid.
            for _ in range(3):usb.observe_rocked(self.state([0,291,620,540]))
            usb.observe_rocked(dict(fightNotice="STAGGER!",noticeUptimeMs=2))
            self.assertIn("ROCKED observed",(Path(folder)/"events.jsonl").read_text(encoding="utf-8"))
            # An undrawn (empty) lane is not judged; the drawn lane of the same notice is.
            usb.observe_rocked(dict(self.state([0,0,0,0]),noticeUptimeMs=5))
            self.assertEqual(1,len(usb.rocked_events))
            usb.observe_rocked(dict(self.state([0,291,620,540]),noticeUptimeMs=5))
            self.assertEqual(2,len(usb.rocked_events))
            self.assertTrue(all(e["clear"] for e in usb.rocked_events))
    def test_only_filter_skips_unselected_cases(self):
        with tempfile.TemporaryDirectory() as folder:
            usb=sweep.Thor("adb","test",Path(folder),Path(folder))
            usb.only={"fight-staples"}
            ran=[]
            self.assertFalse(usb.case("fight-warehouse",lambda:ran.append("warehouse")))
            self.assertTrue(usb.case("fight-staples",lambda:ran.append("staples") or {}))
            self.assertEqual(["staples"],ran)
            self.assertNotIn("fight-warehouse",usb.results)


class RouteUsb:
    """Launcher stand-in: route requests are saved; after_relaunch is what a cold start reports."""
    def __init__(self,after_relaunch):self.after,self.stops,self.value=after_relaunch,0,None
    def stop_test_app(self):self.stops+=1
    def launch(self,phase):
        if phase=="setup":
            value=self.after if self.stops>=2 else self.value
            return {"setupVisible":True,"focused":True,"busy":False,"rendererSelection":value,"savedRendererSelection":value}
        self.value=0;return {"outcome":"ROUTE_SELECTED"}
    def wait(self,condition,seconds,label):
        state={"setupVisible":True,"focused":True,"busy":False,"outcome":"ROUTE_SELECTED",
               "rendererSelection":self.value,"savedRendererSelection":self.value}
        if not condition(state):raise RuntimeError("timed out: "+label)
        return state


class RouteEvidenceTests(unittest.TestCase):
    def journey(self,usb):
        run=object.__new__(game.Journey);run.usb=usb;return run
    def test_route_returns_checked_values_as_evidence(self):
        evidence=self.journey(RouteUsb(after_relaunch=0)).route("ordinary","rendererSelection",0)
        self.assertEqual({"phase":"ordinary","field":"rendererSelection","selected":0,"saved":0,"after_relaunch":0},evidence)
    def test_route_still_fails_when_the_setting_is_lost_on_relaunch(self):
        with self.assertRaisesRegex(RuntimeError,"did not survive relaunch"):
            self.journey(RouteUsb(after_relaunch=1)).route("ordinary","rendererSelection",0)


if __name__=="__main__":unittest.main()
