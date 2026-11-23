"""Link Fight Night's generated translation to the original Xbox kernel runtime."""
import argparse
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
import re
import shutil
import subprocess
import sys
from build_vulkan import prepare


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("private_dir", type=Path)
    parser.add_argument("toolkit", type=Path)
    parser.add_argument("--clang", type=Path, required=True)
    parser.add_argument("--target", choices=("windows", "android", "android-x86_64"), required=True)
    parser.add_argument("--android-reference", type=Path, help="SSX3RecompAndroid checkout containing src/mmio_posix.c")
    parser.add_argument("--runtime-only", action="store_true",
                        help="Build the ARM64 engine archive for first-run linking, without the translated game")
    parser.add_argument("--vulkan-ndk", type=Path, help="Use NDK Vulkan headers and shader compiler for a Windows renderer test")
    args = parser.parse_args()
    if args.runtime_only and args.target != "android":
        parser.error("--runtime-only requires --target android")
    private = args.private_dir.resolve()
    src = args.toolkit.resolve() / "src"
    project = Path(__file__).resolve().parents[1]
    native = project / "native"
    patches = project / "patches"
    cc = args.clang.resolve()
    android = args.target.startswith("android")
    out = private / ("runtime-android" if args.runtime_only else "host-" + args.target)
    out.mkdir(parents=True, exist_ok=True)
    kernel = src / "kernel"
    manifest = (kernel / "CMakeLists.txt").read_text()
    sources = [kernel / name for name in
               re.search(r"set\(KERNEL_SOURCES\s+([^)]*)\)", manifest)[1].split()]
    if android:
        sources.extend([src / "platform" / "win32_compat.c", native / "host_sleep.c"])
        if not args.android_reference:
            parser.error("Supply --android-reference for the Android device-register adapter")
        adapter = args.android_reference.resolve() / "src"
        sources.append(adapter / "mmio_posix.c")
    sources.extend(src / "usb" / name for name in ("ohci.c","usb_gamepad.c"))
    sources.extend(src / "apu" / name for name in ("apu_core.c","apu_vp.c","apu_dsp.c","apu_mmio_hook.c"))
    sources.append(native / ("android_audio.c" if android else "desktop_audio.c"))
    sources.extend(native / name for name in
                   ("host_main.c", "host_dispatch.c", "host_video.c", "frame_output.c", "corner_pipe.c","pad_input.c",
                    "title_hooks.c", "fight_data.c", "fight_trax.c", "fight_timing.c"))
    arch = "x86_64" if args.target == "android-x86_64" else "aarch64"
    platform = [f"--target={arch}-linux-android30", "-fPIC"] if android else []
    flags = [*platform, "-O2", "-fwrapv", "-fno-strict-aliasing", "-gline-tables-only", "-ffunction-sections", "-fno-autolink",
             "-fdata-sections", "-fms-extensions", "-DWIN32_LEAN_AND_MEAN", "-DNOMINMAX",
             "-I", src, "-I", kernel, "-I", src / "nv2a",
             "-I", args.toolkit.resolve() / "templates/runtime" if args.runtime_only else private / "gen"]
    if args.runtime_only:
        for path,name in ((private,"build"),(project,"project"),(args.toolkit.resolve(),"xboxrecomp"),
                          (args.android_reference.resolve(),"reference"),(cc.parents[5],"ndk")):
            for prefix in {str(path),path.as_posix()}:
                flags.append(f"-ffile-prefix-map={prefix}={name}")
    if android:
        flags.extend(["-I",adapter, "-I",src / "platform"])
        subprocess.run([cc, *platform, "-O2", "-shared", native / "frame_reader.c", "-lm",
                        "-o", out / "libframe_reader.so"], check=True)
    gpu_libs = []
    use_vulkan = android or args.vulkan_ndk is not None
    if use_vulkan:
        if not args.android_reference:
            parser.error("Supply --android-reference for the Vulkan backend")
        ndk = args.vulkan_ndk or cc.parents[5]


    # Apply the title runtime fixes to private build copies of the pinned sources.
    patched = out / "runtime"
    patched.mkdir(exist_ok=True)
    names = {"xbox_memory_layout.c", "nv2a_pb_exec.c", "nv2a_pb_scan.c", "apu_core.c", "apu_vp.c", "vkr.c", "kernel_xbox.c", "kernel_hal.c"}
    for original in (kernel / "xbox_memory_layout.c", kernel / "kernel_hal.c", kernel / "kernel_xbox.c", kernel / "nv2a_pb_exec.c", kernel / "nv2a_pb_scan.c", src / "apu/apu_core.c", src / "apu/apu_vp.c", args.android_reference / "src/vk/vkr.c" if use_vulkan else None):
        if original is not None:
            shutil.copyfile(original, patched / original.name)
    (patched / "nv2a_vsh_interp.c").write_bytes((kernel / "nv2a_vsh_interp.c").read_text(encoding="utf-8").encode("utf-8"))
    names.add("nv2a_vsh_interp.c")
    subprocess.run(["git","apply","--no-index","--whitespace=nowarn","-"],cwd=patched,check=True,
                   input=(patches / "fight-vertex-decode.patch").read_text(encoding="utf-8").encode("utf-8"))
    patch_args = [] if use_vulkan else ["--exclude=vkr.c"]
    subprocess.run(["git", "apply", "--no-index", "--unidiff-zero", "--whitespace=nowarn", *patch_args,
                    str(patches / "runtime-update.patch")], cwd=patched, check=True)
    subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn", "-"], cwd=patched, check=True,
                   input=(patches / "fight-voice.patch").read_bytes().replace(b"\r\n", b"\n"))
    if use_vulkan:
        shutil.copyfile(args.android_reference / "src/vk/vkr.h", patched / "vkr.h")
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "renderer-performance.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "bout-renderer.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-textures.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-upload-pass.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-punch.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-high.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-venue.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-shader-constants.patch")], cwd=patched, check=True)
        shutil.copyfile(args.android_reference / "src/vk/shaders/xbox.frag", patched / "fight_full_coverage.frag")
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-coverage.patch")], cwd=patched, check=True)
        subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                        str(patches / "fight-fragments.patch")], cwd=patched, check=True)
        if args.runtime_only:
            subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn", "-"], cwd=patched,
                           input=(patches / "fight-live-shaders.patch").read_bytes().replace(b"\r\n", b"\n"), check=True)
        gpu_src, gpu_flags, gpu_libs = prepare(args.android_reference.resolve(), out, cc, ndk, android,
                                                profiles=not args.runtime_only)
        sources.extend([*gpu_src, native / "fight_gpu.c"])
        flags.extend(gpu_flags)
        flags[:0] = ["-I", patched]
    shutil.copyfile(kernel / "kernel_bridge.c", patched / "kernel_bridge.c")
    subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                    str(patches / "fight-data.patch")], cwd=patched, check=True)
    names.add("kernel_bridge.c")
    shutil.copyfile(src / "usb" / "ohci.c", patched / "ohci.c")
    subprocess.run(["git", "apply", "--no-index", "--whitespace=nowarn",
                    str(patches / "fight-controller.patch")], cwd=patched, check=True)
    names.add("ohci.c")
    flags.extend(["-I", src / "usb"])
    flags.extend(["-I", native, "-I", src / "apu"])
    sources = [patched / s.name if s.name in names else s for s in sources]

    if android:
        original = (src / "platform/win32_compat.c").read_text()
        pair = "struct timespec ts;\n    clock_gettime(CLOCK_MONOTONIC, &ts);"
        start = original.index("ULONGLONG GetTickCount64(void)")
        prefix, functions = original[:start], original[start:]
        if functions.count(pair) != 2:
            raise ValueError("Check Android guest clock functions against pinned toolkit")
        functions = functions.replace(pair, "int64_t ns = fight_guest_ns();")
        functions = functions.replace("(ULONGLONG)ts.tv_sec * 1000ULL + (ULONGLONG)ts.tv_nsec / 1000000ULL", "(ULONGLONG)ns / 1000000ULL")
        functions = functions.replace("(LONGLONG)ts.tv_sec * 1000000000LL + ts.tv_nsec", "(LONGLONG)ns")
        (patched / "win32_compat.c").write_text(prefix + "extern int64_t fight_guest_ns(void);\n" + functions)
        sources = [patched / s.name if s.name == "win32_compat.c" else s for s in sources]

    def compile_one(source):
        obj = out / (source.stem + ".o")
        if source.name == "xbox_memory_layout.c":
            # Upstream couples per-pushbuffer logging to enabling the GPU itself.
            original = source.read_text(encoding="utf-8")
            trace = '|| getenv("RECOMP_PB_EXEC") != NULL;'
            if original.count(trace) != 1:
                raise ValueError("Check NV2A tracing against the pinned kernel revision")
            source = out / source.name
            source.write_text(original.replace(trace, ";"), encoding="utf-8")
        dispatch = ["-include", native / "vulkan_android_dispatch.h"] if android and source.name == "vkr.c" else []
        if args.runtime_only and source.name == "host_main.c":
            dispatch = [*dispatch, "-Dmain=fightnight_main"]
        result = subprocess.run([cc, *flags, *dispatch, "-c", source, "-o", obj], capture_output=True)
        (out / (source.stem + ".log")).write_bytes(result.stderr)
        return source.name, result.returncode

    with ThreadPoolExecutor(max_workers=8) as pool:
        failed = [name for name, code in pool.map(compile_one, sources) if code]
    if failed:
        sys.exit("Compilation failed: " + ", ".join(failed) + f" (see {out})")
    runtime_objects = [out / (source.stem + ".o") for source in sources]
    if args.runtime_only:
        # Build from this explicit list. An old host directory can contain a full guest translation.
        archive = out / "libfightnight_runtime.a"
        temporary = archive.with_suffix(".tmp")
        temporary.unlink(missing_ok=True)
        subprocess.run([cc.with_name("llvm-ar" + cc.suffix), "rcsD", temporary,
                        *(obj.name for obj in runtime_objects)], cwd=out, check=True)
        subprocess.run([cc.with_name("llvm-strip"+cc.suffix),"--strip-debug",temporary],check=True)
        temporary.replace(archive)
        subprocess.run([cc, *platform, "-O2", native / "disc_runner.c", "-ldl",
                        "-o", out / "libdisc_runner.so"], check=True)
        print(archive)
        return
    guest = private / ("objects-" + args.target)
    if android:
        guest_objects = [guest / "fight-night-guest.o"]
    else:
        guest_objects = [guest / (p.stem + ".o") for p in sorted((private / "gen").glob("*.c"))]
    objects = runtime_objects + guest_objects
    (out / "link.rsp").write_text("\n".join('"' + p.as_posix() + '"' for p in objects))
    libs = ["-lm", "-llog", "-ldl","-laaudio","-landroid"] if android else ["-lbcrypt", "-lshell32", "-luser32", "-lwinmm", "-ldbghelp","-lxinput","-lxaudio2_8","-lole32"]
    exe = out / ("fightnight-host" if android else "fightnight-host.exe")
    with (out / "link.log").open("w") as log:
        subprocess.run([cc, *platform, "-Wl,--gc-sections", "@link.rsp", *libs, *gpu_libs, "-o", exe],
                       cwd=out, stderr=log, check=True)
    print(exe)


if __name__ == "__main__":
    main()
