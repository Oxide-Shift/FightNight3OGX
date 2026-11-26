"""Offline Windows host and Android lower-display regression suite. No device or network needed."""
import argparse
import os
from pathlib import Path
import shutil
import subprocess
import sys
from tempfile import TemporaryDirectory

ROOT = Path(__file__).resolve().parents[1]
TESTS = ROOT / "tests"
JAVA = ROOT / "android/src/org/oxideshift/corner"

def run(args):
    subprocess.run(args, cwd=ROOT, check=True, timeout=120)

def main():
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("jdk", "android-jar", "toolkit", "build", "cc"):
        parser.add_argument("--"+name, type=Path, required=True)
    parser.add_argument("--frames", type=Path, action="append", default=[])
    parser.add_argument("--menu-archive", type=Path, action="append", default=[],
                        help="Test startup on a private retail or previously installed GUI/overlay.viv")
    parser.add_argument("--vulkan-include",type=Path,help="Vulkan headers for the host capability and descriptor fault fixtures")
    parser.add_argument("--bench-voice-stop", action="store_true",
                        help="Measure real thread CPU, wall time and polls for 100 missed stop notifications")
    args=parser.parse_args()
    src=args.toolkit/"src"
    host=args.build/"host-windows"
    with TemporaryDirectory() as tmp:
        out=Path(tmp)
        javac=args.jdk/"bin/javac.exe"
        java=args.jdk/"bin/java.exe"
        java_suites=["GraphicsStartupSmoke","LaunchRecoveryTest","CornerControlsSmoke","FightBroadcastSmoke","RingsidePromptSmoke","CutmanStrokeSmoke",
               "HakuSaveSmoke","ProfileSavesSmoke","DisplayCadenceSmoke","MenuStripSmoke","ControllerInputSmoke","FightEventsSmoke","CommandPipeSmoke","SessionSleepSmoke","ReleaseVersionSmoke","ReleaseAssetSmoke","DiscTraxSmoke","AchievementsSmoke","CalloutRumbleSmoke","BetweenRoundsSmoke","DiagnosticTailSmoke"]
        paths=[TESTS/(name+".java") for name in java_suites+["LinkSmoke","DiscInstallSmoke","RetailMenuStartupSmoke"]]
        run([javac,"--release","8","-encoding","UTF-8","-cp",args.android_jar,"-d",out,*JAVA.glob("*.java"),*paths])
        cp=os.pathsep.join(map(str,(out,args.android_jar)))
        frames=[f for d in args.frames for f in sorted(d.glob("*.raw")) if f.name.startswith(("boxer","main","round","career-central","contract-","editor-"))]
        for name in java_suites:
            run([java,"-cp",cp,"org.oxideshift.corner."+name,*(frames if name=="RingsidePromptSmoke" else [])])
        run([java,"-cp",cp,"org.oxideshift.corner.DiscInstallSmoke",args.build/"default.xbe"])
        for n,archive in enumerate(args.menu_archive):
            run([java,"-cp",cp,"org.oxideshift.corner.RetailMenuStartupSmoke",archive,out/("menu-"+str(n))])
        run([sys.executable,TESTS/"guest_object_cache.py"])
        run([sys.executable,TESTS/"shader_constants.py"])
        run([sys.executable,TESTS/"thor_health.py"])
        run([sys.executable,TESTS/"usb_sweep.py"])
        run([sys.executable,TESTS/"crash_inbox.py"])
        run(["node",TESTS/"scorecard.cjs"])
        # Exercise the original translated ring wait with the same register
        # writer the Android runtime builds. This requires no retail process.
        progress=out/"progress"
        progress.mkdir()
        layout=progress/"xbox_memory_layout.c"
        shutil.copyfile(src/"kernel"/layout.name,layout)
        subprocess.run(["git","apply","--no-index","--include=xbox_memory_layout.c",
                        ROOT/"patches/fight-gpu-progress.patch"],cwd=progress,check=True)
        subprocess.run(["git","apply","--no-index","--include=xbox_memory_layout.c",
                        ROOT/"patches/fight-fence-wait.patch"],cwd=progress,check=True)
        writer=layout.read_text(encoding="utf8")
        start=writer.index("static int have_ring_position;")
        end=writer.index("static int s_nv2a_trace",start)
        (progress/"gpu_progress_writer.inc").write_text(writer[start:end],encoding="utf8")
        start=writer.index("            if (g_lockstep_fence_waiters &&")
        end=writer.index("\n            }",start)+len("\n            }")
        (progress/"gpu_wait_interrupt.inc").write_text(writer[start:end],encoding="utf8")
        translated=(args.build/"gen/recomp_0241.c").read_text(encoding="utf8")
        start=translated.index("void sub_0043B390(void)\n{")
        end=translated.index("\n}",start)+2
        (progress/"gpu_progress_consumer.inc").write_text(translated[start:end],encoding="utf8")
        run([args.cc,"-O2","-I",args.build/"gen","-I",progress,TESTS/"gpu_progress.c","-o",progress/"check.exe"])
        run([progress/"check.exe"])
        # controller_bus includes ohci.c directly. Exercise the controller built by build_host.py.
        shutil.copyfile(src/"usb/ohci.c",out/"ohci.c")
        subprocess.run(["git","apply","--no-index","--whitespace=nowarn",ROOT/"patches/fight-controller.patch"],
                       cwd=out,check=True)
        flags=["-O2","-ffunction-sections","-fdata-sections","-Wl,--gc-sections","-I",out,"-I",host/"runtime",
               "-I",src/"usb","-I",src,"-I",src/"kernel"]
        # Exercise this build's translated XInputGetState without booting the game.
        # The two imported calls just raise/lower IRQL. The retail handle checks,
        # state copy, return status, stack cleanup and bridge hook all run normally.
        signature = "void sub_00473F67(void)\n{"
        for chunk in sorted((args.build/"gen").glob("recomp_0*.c")):
            source = chunk.read_text(encoding="utf-8")
            start = source.find(signature)
            if start >= 0:
                break
        else:
            raise ValueError("Missing translated XInputGetState")
        poll = source[start:source.index("\n}", start)+2]
        private_poll = out/"retail_poll.c"
        private_poll.write_text('#define RECOMP_GENERATED_CODE\n#include "recomp_types.h"\n'
            '#undef RECOMP_ICALL_SAFE_AT\n#define RECOMP_ICALL_SAFE_AT(target,stack,site) (g_esp += 4)\n'
            + poll, encoding="utf-8")
        voice_signature="void sub_0046A8D7(void)\n{"
        for chunk in sorted((args.build/"gen").glob("recomp_0*.c")):
            source=chunk.read_text(encoding="utf-8")
            if voice_signature in source:
                start=source.index(voice_signature)
                break
        else:
            raise ValueError("Missing translated audio completion")
        voice=out/"retail_voice.c"
        voice.write_text('#define RECOMP_GENERATED_CODE\n#include "recomp_types.h"\n'
              'void sub_0046CB0E(void);\n'+source[start:source.index("\n}",start)+2],encoding="utf-8")
        run([args.cc,*flags,"-I",args.build/"gen",TESTS/"voice_completion.c",voice,"-o",out/"voice_completion.exe"])
        run([out/"voice_completion.exe"])
        # Include exactly the stop poll from this disc translation. Apply the same
        # idempotent hook when the fixture predates this release.
        sys.path.insert(0,str(ROOT/"tools"))
        from bringup import route_voice_stop
        signature="void sub_0046CDD3(void)\n{"
        for chunk in sorted((args.build/"gen").glob("recomp_0*.c")):
            source=chunk.read_text(encoding="utf-8")
            if signature in source:
                start=source.index(signature)
                break
        else:
            raise ValueError("Missing translated DirectSound stop poll")
        stop=out/"recomp_0247.c"
        stop.write_text('#define RECOMP_GENERATED_CODE\n#include "recomp_types.h"\n'
            '#undef RECOMP_ICALL_SAFE_AT\n#define RECOMP_ICALL_SAFE_AT(t,s,site) stop_clock(site)\n'
            'void stop_clock(uint32_t);void sub_0046CCEA(void);\n'+source[start:source.index("\n}",start)+2],encoding="utf-8")
        route_voice_stop(out)
        run([args.cc,*flags,"-I",args.build/"gen",TESTS/"voice_stop.c",stop,ROOT/"native/fight_data.c","-o",out/"voice_stop.exe"])
        run([out/"voice_stop.exe", *(["--benchmark"] if args.bench_voice_stop else [])])
        if args.vulkan_include:
            gpu=out/"gpu-compatibility.exe"
            run([args.cc,"-O2","-I",args.vulkan_include,TESTS/"gpu_compatibility.c","-o",gpu])
            run([gpu])
        names=["menu_dpad","flip_pacing","corner_camera","menu_cancel","audio_ring","audio_route","audio_control","audio_startup","voice_list","voice_rate","fight_texture_layout","frame_publish",
               "mesh_cache","front_surfaces","pushbuffer_wrap","texture_hash","controller_bus","ring_events","trax_filter"]
        for name in names:
            exe=out/(name+".exe")
            extra={"audio_control":["-pthread","-static"],"menu_cancel":["-I",args.toolkit/"templates/runtime"],"audio_route":["-I",TESTS/"route_mock","-pthread","-static"],"pushbuffer_wrap":[host/"nv2a_pb_scan.o"],"controller_bus":[host/"usb_gamepad.o",ROOT/"native/pad_input.c",private_poll,"-I",args.build/"gen","-lxinput"]}.get(name,[])
            run([args.cc,*flags,TESTS/(name+".c"),*extra,"-o",exe])
            run([exe])
            if name=="controller_bus":
                run([exe,"immediate"]);run([exe,"soak"])
        peer=out/"corner-link.exe"
        run([args.cc,"-DCORNER_LINK_ONLY",ROOT/"native/corner_pipe.c","-o",peer])
        run([java,"-cp",cp,"org.oxideshift.corner.LinkSmoke",peer])
        print(f"Passed {len(java_suites)} Java suites, {len(names)+3} native suites, scorecard and host link with {len(frames)} menu/ring captures.")

if __name__=="__main__":
    main()
