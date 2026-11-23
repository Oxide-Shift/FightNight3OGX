"""Generate Fight Night's private C translation from a verified original Xbox disc."""
import argparse
import hashlib
import json
import re
from pathlib import Path
import subprocess
import sys
import os
import tempfile


XBE_SHA256 = "cfaf2d2c1c338a6e4e7135466c8dcb80fdf7da63682e71cfecdcba65b3682064"


def route_trax(gen):
    # Only this shared call displays the game's now-playing animation. Preserve
    # its caller's stack cleanup and the original call on a single display.
    label = "loc_00162B55: ;"
    call = "PUSH32(esp, 0x00162B5Au); RECOMP_ABI_CALL(0x0022DF26u, sub_0022DF26);"
    for path in sorted(gen.glob("recomp_*.c")):
        source = path.read_text(encoding="utf-8")
        if label not in source:
            continue
        if source.count(call) != 1:
            raise ValueError("Unexpected EA Trax display call in the retail translation")
        if "extern int fight_trax_present" not in source:
            source = source.replace(label, label + "\n    extern int fight_trax_present(uint32_t, uint32_t);"
                "\n    if (fight_trax_present(MEM32(esp + 16), MEM32(esp + 20))) goto loc_00162B5A;")
            path.write_text(source,encoding="utf-8")
        return path
    raise ValueError("Missing EA Trax display call in the retail translation")


def observe_career_menus(directory):
    # APT computes this case-insensitive FNV hash before assigning a property.
    # Only menu transitions, trainer prices and the trophy cursor cross the
    # native boundary. The observer checks the entire name and value type.
    def apt_hash(name):
        value = 0x811c9dc5
        for byte in name.lower().encode("ascii"):
            value = ((value ^ byte) * 0x1000193) & 0xffffffff
        return value & 0xffff or 0x4567

    watched = ("m_sCurrentScreenId", "m_trophyId", "m_trainerActualCost")
    guard = " || ".join(f"ecx == 0x{apt_hash(name):04x}u" for name in watched)
    hooks = {
        "loc_0023709E: ;\n    eax = MEM32(edi);\n    ecx = ZX16(MEM16(eax + 6));":
            f"extern void fight_career_property(uint32_t, uint32_t);\n    if ({guard}) fight_career_property(eax, ebx);",
        # GetActualRatings and GetEstimatedRatings expose eight adjacent signed
        # shorts, in the same order as the retail APT training screen.
        "loc_0015302E: ;\n    ecx = (uint32_t)(int32_t)SMEM16(esp + 0x10);":
            "extern void fight_training_ratings(uint32_t, int);\n    fight_training_ratings(esp + 0x10, 0);",
        "loc_00152DA5: ;\n    ecx = (uint32_t)(int32_t)SMEM16(esp + 0x14);":
            "extern void fight_training_ratings(uint32_t, int);\n    fight_training_ratings(esp + 0x14, 1);\n    extern void fight_training_choice(unsigned);\n    fight_training_choice(edi);",
        "loc_0014E6C8: ;":
            "extern void fight_career_offer(uint32_t);\n    fight_career_offer(esp);",
        "loc_0019CC5C: ;\n    esi = MEM32(esi + 0x24);\n    eax = (uint32_t)(int32_t)SMEM16(esi);\n    eax = (uint32_t)((int32_t)eax * (int32_t)0x64);":
            "extern void fight_store_price(int32_t);\n    fight_store_price((int32_t)eax);",
    }
    # GetTrophyStatus emits unlocked0 through unlocked13 in this order.
    for slot in range(14):
        address = 0x19d549 + slot * 0x5d
        hooks[f"loc_{address:08X}: ;"] = (
            f"extern void fight_trophy_status(unsigned, unsigned);\n    fight_trophy_status({slot}, eax);"
        )
    for path in sorted(directory.glob("recomp_0*.c")):
        source = path.read_text(encoding="utf-8")
        updated = source
        for anchor, observer in list(hooks.items()):
            if anchor not in source:
                continue
            if source.count(anchor) != 1:
                raise ValueError(f"Ambiguous career menu observer in {path.name}")
            if observer not in source:
                previous = observer.split("\n    extern void fight_training_choice")[0]
                if previous != observer and previous in source:
                    updated = updated.replace(previous, observer)
                else:
                    updated = updated.replace(anchor, anchor + "\n    " + observer)
            del hooks[anchor]
        if updated != source:
            path.write_text(updated, encoding="utf-8")
        if not hooks:
            return
    raise ValueError("Missing retail career menu observer")


def observe_fight_cinematics(directory):
    # Retail resolves the sequence index at entry, before selecting its data source.
    label = "loc_0011A360: ;"
    for path in sorted(directory.glob("recomp_0*.c")):
        source = path.read_text(encoding="utf-8")
        if label not in source:
            continue
        callback = "fight_cinematic(MEM32(esp + 4))"
        if callback not in source:
            source = source.replace(label, label + "\n    extern void fight_cinematic(uint32_t);\n    " + callback + ";")
            temporary = path.with_suffix(".writing")
            temporary.write_text(source, encoding="utf-8")
            temporary.replace(path)
        return path
    raise ValueError("Missing retail cinematic dispatcher")


def route_voice_completion(directory):
    # An APU idle notification can arrive after DirectSound frees the voice.
    # In slot zero, a cleared pointer can otherwise pass the retail voice-ID check.
    load = "loc_0046A902: ;\n    ecx = MEM32(ecx + eax * 4 + 0x88);"
    guarded = load + "\n    if (!ecx) goto loc_0046A924; /* voice already released */"
    for path in sorted(directory.glob("recomp_0*.c")):
        source = path.read_text(encoding="utf-8")
        if guarded in source:
            return path
        if load in source and "loc_0046A924: ;" in source and source.count(load) == 1:
            path.write_text(source.replace(load, guarded), encoding="utf-8")
            return path
    raise ValueError("Missing retail voice-completion lookup")


def route_controller_poll(directory):
    """Refresh the retail player-one report after XInputGetState releases its IRQL."""
    original = "loc_00473FD3: ;\n    eax = esi;\n    POP32(esp, esi);\n    POP32(esp, ebx);\n    esp += 12; return; /* ret 8 */"
    # On return the two stdcall arguments are the open handle and the output buffer.
    # Both retail polling loops keep the first port's opened handle at 0x5CEE78.
    call = "fight_pad_read_player_one(eax, MEM32(esp + 4), MEM32(0x5CEE78), MEM32(esp + 8));"
    replacement = original.replace("    esp += 12;", "    extern void fight_pad_read_player_one(uint32_t, uint32_t, uint32_t, uint32_t);\n    " + call + "\n    esp += 12;")
    for path in sorted(directory.glob("recomp_0*.c")):
        source = path.read_text(encoding="utf-8")
        if replacement in source:
            return path
        if original in source and "void sub_00473F67(void)" in source:
            if source.count(original) != 1:
                raise ValueError("Ambiguous retail XInputGetState return")
            path.write_text(source.replace(original, replacement), encoding="utf-8")
            return path
    raise ValueError("Missing retail XInputGetState return")


def observe_ring_events(directory):
    # RefCountDown's count switch and MatSfx_Stumble's dispatch branch.
    sites = {
        # The two HUD paths format the first time and subsequent clock changes.
        "loc_000C88CD: ;": ("void fight_hud_time(uint32_t)", "fight_hud_time(edi)"),
        "loc_000C8FE9: ;": ("void fight_hud_time(uint32_t)", "fight_hud_time(eax)"),
        "loc_0017F8AB: ;": ("void fight_ref_count(uint32_t)", "fight_ref_count(MEM32(esp + 0x2C))"),
        "loc_0016C39A: ;": ("void fight_stumble(void)", "fight_stumble()"),
    }
    touched = []
    for path in sorted(directory.glob("recomp_0*.c")):
        source = path.read_text(encoding="utf-8")
        original = source
        for label in list(sites):
            if label not in source:
                continue
            declaration, call = sites.pop(label)
            if source.count(label) != 1:
                raise ValueError("Ambiguous retail event " + label)
            hook = label + "\n    extern " + declaration + ";\n    " + call + ";"
            if hook not in source:
                source = source.replace(label, hook)
            touched.append(path)
        if source != original:
            temporary = path.with_suffix(".writing")
            temporary.write_text(source, encoding="utf-8")
            temporary.replace(path)
        if not sites:
            return touched
    raise ValueError("Missing retail event " + ", ".join(sites))


def route_texture_blits(directory):
    """Use the retail lookup path inline in the two high-volume texel blitters."""
    header = (Path(__file__).resolve().parents[1] / "native" / "texel_offset.h")
    (directory / header.name).write_bytes(header.read_bytes())
    sites = "RECOMP_ABI_CALL(0x000FD000u, sub_000FD000)"
    fast = "RECOMP_ABI_CALL(0x000FD000u, fight_texel_offset)"
    for chunk in sorted(directory.glob("recomp_0*.c")):
        source = chunk.read_text(encoding="utf-8")
        if '#include "texel_offset.h"' in source:
            return chunk
        if "void sub_000FD190(void)\n{" not in source:
            continue
        for address in ("000FD190", "000FD580"):
            begin = source.index("void sub_" + address + "(void)\n{")
            end = source.index("\n}", begin)
            function = source[begin:end]
            if function.count(sites) != 6:
                raise ValueError("Unexpected texel lookup sites in " + address)
            source = source[:begin] + function.replace(sites, fast) + source[end:]
        chunk.write_text(source.replace('#include "recomp_funcs.h"',
                          '#include "recomp_funcs.h"\n#include "texel_offset.h"', 1), encoding="utf-8")
        return chunk
    raise ValueError("Missing retail texture blitters")


def route_career_format(directory):
    """Keep the career contract formatter's intra-function jump in its stack frame."""
    original = "g_seh_ebp = ebp; RECOMP_ITAIL(MEM32(edx * 4 + 0x136488)); return;"
    routed = ("if (MEM32(edx * 4 + 0x136488) != 0x135CEC) { " + original + " }"
              " /* career formatter continues at the next translated instruction */")
    for chunk in sorted(directory.glob("recomp_0*.c")):
        source = chunk.read_text(encoding="utf-8")
        if routed in source:
            return chunk
        if original in source:
            start = source.index(original)
            if "edx = MEM32(eax);" not in source[start:start+220]:
                raise ValueError("Unexpected career formatter continuation")
            chunk.write_text(source.replace(original,routed,1),encoding="utf-8")
            return chunk
    raise ValueError("Missing retail career contract formatter")



def preserve_stream_cursor(directory):
    """Keep the retail audio walker's callee-saved cursor across a stream update."""
    label = "loc_002C422E: ;"
    call = "PUSH32(esp, 0x002C4234u); RECOMP_ABI_CALL(0x002C8BD7u, sub_002C8BD7);"
    wrapped = ("{ const uint32_t voice = esi, argument = esp, saved_edi = edi, saved_ebx = ebx;\n"
               "      " + call + "\n"
               "      esi = voice; edi = saved_edi; ebx = saved_ebx; esp = argument;\n"
               "      g_ebp = g_seh_ebp = ebp; }")
    for chunk in sorted(directory.glob("recomp_0*.c")):
        source = chunk.read_text(encoding="utf-8")
        if label not in source:
            continue
        if wrapped in source:
            return chunk
        if source.count(call) != 1 or not (0 < source.index(call) - source.index(label) < 250):
            raise ValueError("Unexpected streamed-voice callback in audio list walker")
        temporary = chunk.with_suffix(".writing")
        temporary.write_text(source.replace(call, wrapped), encoding="utf-8")
        temporary.replace(chunk)
        return chunk
    raise ValueError("Missing retail audio list walker")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("image", type=Path, help="Original Xbox ISO, or the installed default.xbe with --imported-xbe")
    parser.add_argument("--imported-xbe", action="store_true",
                        help="Translate the executable already extracted and verified by the Android disc importer")
    parser.add_argument("--disc-layout", type=Path, help="Release address map for this exact executable")
    parser.add_argument("--jobs", type=int, default=1, choices=range(1, 6))
    parser.add_argument("toolkit", type=Path, help="DouglasHalse/xboxrecomp android-port source")
    parser.add_argument("private_dir", type=Path, help="private workspace outside the project")
    args = parser.parse_args()
    work = args.private_dir.resolve()
    if work.is_relative_to(Path(__file__).resolve().parents[1]):
        parser.error("Choose a private output directory outside the source project.")
    toolkit = args.toolkit.resolve()
    disc = args.image.absolute()
    work.mkdir(parents=True, exist_ok=True)
    xbe = work / "default.xbe"
    env = dict(os.environ, PYTHONUTF8="1", PYTHONIOENCODING="utf-8")

    def run(module, *arguments):
        print(module, flush=True)
        with (work / (module + ".log")).open("w", encoding="utf-8") as log:
            with subprocess.Popen([sys.executable, "-u", "-m", "tools." + module, *arguments],
                                  cwd=toolkit, env=env, stdout=subprocess.PIPE, stderr=subprocess.STDOUT,
                                  text=True, encoding="utf-8", errors="replace") as job:
                for line in job.stdout:
                    log.write(line)
                    if module == "recomp" and re.fullmatch(r"Translating [0-9]+ functions\.\.\.", line.strip()):
                        print(line.strip(), flush=True)
                if job.wait():
                    raise subprocess.CalledProcessError(job.returncode, job.args)

    # Both entry points verify exactly the bytes the translator will read.
    with tempfile.TemporaryDirectory(dir=work) as staging:
        candidate = Path(staging) / "default.xbe"
        if args.imported_xbe:
            with disc.open("rb") as source:
                image = source.read(5750784 + 1)
            if len(image) != 5750784 or hashlib.sha256(image).hexdigest() != XBE_SHA256:
                raise SystemExit("Unsupported executable revision. The previous translation is intact.")
            candidate.write_bytes(image)
        else:
            run("xiso", "get", disc, "default.xbe", "-o", candidate)
            if hashlib.sha256(candidate.read_bytes()).hexdigest() != XBE_SHA256:
                raise SystemExit("Unsupported executable revision. The previous translation is intact.")
        candidate.replace(xbe)
    disasm, symbols, abi = [work / name for name in ("disasm", "symbols", "abi")]
    seeds = work / "observed_functions.json"
    seeds.write_bytes((Path(__file__).resolve().parents[1] / "native" / "observed_functions.json").read_bytes())
    if args.disc_layout:
        subprocess.run([sys.executable, "-u", Path(__file__).with_name("translate_disc.py"),
                        xbe, args.disc_layout.resolve(), toolkit, work,
                        "--jobs", str(args.jobs)], check=True, env=env)
    else:
        run("xbe_parser", xbe, "--json", work / "fnr3_analysis.json")
        run("disasm", xbe, "--force", "--seed-functions", seeds, "-o", disasm)
        run("func_id", xbe, "--functions", disasm / "functions.json",
            "--strings", disasm / "strings.json", "--xrefs", disasm / "xrefs.json", "-o", symbols)
        run("abi_analysis", xbe, "--functions", disasm / "functions.json",
            "--identified", symbols / "identified_functions.json", "--output-dir", abi)
        run("recomp", xbe, "--all", "--game-name", "Fight Night Round 3",
            "--exclude-manual", (Path(__file__).resolve().parents[1] / "native" / "title_hooks.c").resolve(),
            "--disasm-dir", disasm, "--func-id-dir", symbols, "--abi-dir", abi,
            "--split", "160", "--gen-dir", work / "gen", "-o", work / "translation")
    print("Applying game fixes and checking generated functions", flush=True)
    preserve_stream_cursor(work / "gen")
    route_voice_completion(work / "gen")
    route_controller_poll(work / "gen")
    route_trax(work / "gen")
    observe_fight_cinematics(work / "gen")
    observe_career_menus(work / "gen")
    observe_ring_events(work / "gen")
    route_career_format(work / "gen")
    route_texture_blits(work / "gen")
    dispatch = (work / "gen" / "recomp_dispatch.c").read_text(encoding="utf-8")
    exported = {int(va, 16) for va in re.findall(r"\{\s*0x([0-9a-fA-F]+)u,\s*\(recomp_func_t\)", dispatch)}
    missing = [entry["start"] for entry in json.loads(seeds.read_text())
               if int(entry["start"], 16) not in exported]
    if missing:
        raise SystemExit("Recompiler omitted required callbacks: " + ", ".join(missing))
    result = json.loads((work / "translation" / "summary.json").read_text())
    print(f"{result['translated']} functions, {result['num_chunks']} chunks, "
          f"{result['unresolved_stubs']} unresolved call targets")


if __name__ == "__main__":
    main()
