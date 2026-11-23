"""Compile translated Xbox guest sources and merge the Android objects for the native host."""
import argparse
from concurrent.futures import ThreadPoolExecutor, as_completed
import json
from pathlib import Path
import subprocess
import re


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("private_dir", type=Path)
    parser.add_argument("--clang", type=Path, required=True)
    parser.add_argument("--target", choices=("android", "android-x86_64", "windows"), required=True)
    parser.add_argument("--jobs", type=int, default=6)
    parser.add_argument("--runtime-archive", type=Path,
                        help="Link the disc translation with the ARM64 engine into a private loadable game module")
    parser.add_argument("--device-kit", type=Path, help="On-device LLVM driver, headers and linker support")
    parser.add_argument("--relink", action="store_true", help="Use completed objects from the matching device build")
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error("--jobs must be positive")
    if args.runtime_archive and (args.target != "android" or not args.runtime_archive.is_file()):
        parser.error("--runtime-archive requires an existing ARM64 engine archive and --target android")
    if args.device_kit and not args.runtime_archive:
        parser.error("--device-kit requires --runtime-archive")
    work = args.private_dir.resolve()
    sources = sorted((work / "gen").glob("*.c"))
    if not args.relink and (not sources or not (work / "gen" / "recomp_dispatch.c").exists()):
        parser.error("Complete the translation before compiling it.")
    out = work / ("module-android" if args.runtime_archive else "objects-" + args.target)
    out.mkdir(exist_ok=True)
    if args.relink:
        if not args.device_kit:
            parser.error("--relink requires --device-kit")
        inventory = json.loads((out / "inventory.json").read_text())
        if not inventory or "recomp_dispatch.o" not in inventory or any(
                not re.fullmatch(r"recomp_(?:\d+|dispatch|stubs_unresolved)\.o", name) or
                (out / name).stat().st_size != size for name, size in inventory.items()):
            raise ValueError("Incomplete guest object cache")
        sources = [Path(name).with_suffix(".c") for name in sorted(inventory)]
    android = args.target.startswith("android")
    arch = "x86_64" if args.target == "android-x86_64" else "aarch64"
    target_flags = [f"--target={arch}-linux-android30", "-fPIC"] if android else []

    # The guest is linked into the standalone host executable. Static TLS lets
    # Clang address the emulated CPU registers directly from the thread pointer.
    # Tune scheduling for Thor while retaining the normal ARM64 instruction set.
    if args.runtime_archive:
        target_flags.append("-mtune=cortex-x3")
    elif args.target == "android":
        target_flags = ["--target=aarch64-linux-android30", "-fPIE", "-ftls-model=local-exec", "-mtune=cortex-x3"]

    compiler = [args.clang]
    if args.device_kit:
        kit = args.device_kit.resolve()
        compiler.append("clang")
        target_flags.extend(["--sysroot=" + str(kit / "sysroot"), "-resource-dir=" + str(kit / "clang"),
                             "-fintegrated-cc1", "-Wno-parentheses-equality"])

    optimization = "-O2" if args.target == "android" else "-O0"
    flags = [*target_flags, optimization, "-fwrapv", "-fno-strict-aliasing", "-ffunction-sections"]
    pch_flags = []
    if args.device_kit and not args.relink:
        print("Preparing shared compiler headers", flush=True)
        header = out / "guest.h"
        header.write_text('#define RECOMP_GENERATED_CODE\n#include "recomp_funcs.h"\n')
        pch = out / "guest.pch"
        # Rebuild the PCH on process restart. Individual finished objects remain reusable.
        attempt = subprocess.run([*compiler, *flags, "-I", work/"gen", "-x", "c-header", header, "-o", pch],
                                 capture_output=True)
        if attempt.returncode == 0:
            pch_flags = ["-include-pch", pch]

    def compile_one(source):
        obj = out / (source.stem + ".o")
        if args.device_kit and obj.is_file():
            return source.name, 0, False
        # The translated CPU uses integer wraparound and aliases guest memory by width.
        # The x86-64 OHCI fault handler requires standalone MOV instructions.
        # On ARM64 the optimizer still emits separate scalar loads and stores.
        use_pch = pch_flags if re.fullmatch(r"recomp_\d+", source.stem) else []
        pending = obj.with_suffix(".pending")
        job = subprocess.run([*compiler, *flags, *use_pch, "-c", source, "-o", pending], capture_output=True)
        if job.returncode == 0:
            pending.replace(obj)
        if job.stderr or job.returncode or not args.device_kit:
            (out / (source.stem + ".log")).write_bytes(job.stderr)
        crashed = (job.returncode < 0 or b"failed due to signal" in job.stderr
                   or b"out of memory" in job.stderr.lower())
        return source.name, job.returncode, crashed

    with ThreadPoolExecutor(max_workers=args.jobs) as pool:
        results = {}
        retry = []
        scheduled = [] if args.relink else sorted(sources, key=lambda s: -s.stat().st_size)
        if args.device_kit and scheduled:
            print(f"Compiling Xbox game: 0 / {len(sources)} ({args.jobs} workers)", flush=True)
        for future in as_completed([pool.submit(compile_one, s) for s in scheduled]):
            name, code, crashed = future.result()
            results[name] = code
            if args.device_kit and code and crashed:
                retry.append(work / "gen" / name)
            if args.device_kit:
                print(f"Compiling Xbox game: {len(results)} / {len(sources)}", flush=True)
    for source in retry:
        print("Retrying interrupted compiler unit " + source.name, flush=True)
        name, code, _ = compile_one(source)
        results[name] = code
    (out / "results.json").write_text(json.dumps(results, indent=2))
    failed = [name for name, code in results.items() if code]
    if failed:
        print((out / (Path(failed[0]).stem + ".log")).read_text(errors="replace")[-2000:], flush=True)
        raise SystemExit(f"{len(failed)}/{len(sources)} failed: {', '.join(failed)}")
    print(f"Compiled {len(sources)} units for {args.target}.", flush=True)
    if android:
        if args.device_kit and not args.relink:
            inventory = {s.stem+".o": (out/(s.stem+".o")).stat().st_size for s in sources}
            pending = out / "inventory.pending"
            pending.write_text(json.dumps(inventory))
            pending.replace(out / "inventory.json")
        # Input order is deterministic. Keep paths short on Windows.
        (out / "objects.rsp").write_text("\n".join(s.stem + ".o" for s in sources))
        if args.runtime_archive:
            # Keep register TLS module-local and expose only the runner's entry point.
            (out / "exports.map").write_text("{ global: fightnight_main; local: *; };\n")
            module = out / "libfightnight_game.so"
            if args.device_kit:
                print("Linking optimized game and renderer", flush=True)
                syslibs = kit / "sysroot/usr/lib/aarch64-linux-android/30"
                compiler_rt = kit / "clang/lib/linux"
                builtins = compiler_rt / "libclang_rt.builtins-aarch64-android.a"
                subprocess.run([args.clang, "ld.lld", "--threads=2", "-shared", "-z", "defs", "-z", "relro",
                                "-z", "now", "-z", "max-page-size=16384", "--hash-style=gnu", "--eh-frame-hdr",
                                "-Bsymbolic", "--gc-sections", "--version-script=exports.map", "-u", "fightnight_main",
                                "-soname", module.name, "-L"+str(syslibs), syslibs/"crtbegin_so.o", "@objects.rsp",
                                args.runtime_archive.resolve(), "-lm", "-llog", "-ldl", "-laaudio", "-landroid",
                                "-lvulkan", builtins, compiler_rt/"aarch64/libunwind.a", "-lc", "-ldl",
                                builtins, syslibs/"crtend_so.o", "-o", "game.pending.so"], cwd=out, check=True)
            else:
                subprocess.run([args.clang, *target_flags, "-shared", "-Wl,-z,defs,-Bsymbolic,--gc-sections",
                            "-Wl,--version-script=exports.map", "-Wl,-u,fightnight_main",
                            "-Wl,-soname,libfightnight_game.so", "@objects.rsp", args.runtime_archive.resolve(),
                            "-lm", "-llog", "-ldl", "-laaudio", "-landroid", "-lvulkan",
                                "-o", "game.pending.so"], cwd=out, check=True)
            (out / "game.pending.so").replace(module)
            print(f"Linked private disc module: {module}")
            return
        subprocess.run([args.clang, *target_flags, "-nostdlib", "-Wl,-r", "-no-pie",
                        "@objects.rsp", "-o", "fight-night-guest.o"], cwd=out, check=True)
        print("Merged all guest code into fight-night-guest.o")


if __name__ == "__main__":
    main()
