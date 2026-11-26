"""Assemble the local Thor test kit from compiler components and the Xbox engine."""
import argparse
import hashlib
import io
import re
from pathlib import Path, PurePosixPath
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
TOOLS = ("libllvm.so", "libpython3.14.so", "libcapstone.so")
NATIVES = (*TOOLS, "libpyrun.so", "libdisc_runner.so", "libframe_reader.so", "libfight_shader.so")


def assemble(args):
    out = args.output.resolve()
    out.mkdir(parents=True, exist_ok=True)
    if ROOT in out.parents or out == ROOT:
        raise ValueError("Choose a private kit directory outside the source checkout")
    ndkbin = args.clang.resolve().parent
    prebuilt = ndkbin.parent
    libs = prebuilt / "sysroot/usr/lib/aarch64-linux-android/30"
    archive = args.runtime / "libfightnight_runtime.a"
    members = subprocess.check_output([ndkbin/("llvm-ar"+args.clang.suffix), "t", archive], text=True).splitlines()
    guest_objects = re.compile(r"(?:fight-night-guest|recomp_(?:[0-9]+|dispatch|stubs_unresolved))\.o")
    if "host_main.o" not in members or any(Path(n).name != n or guest_objects.fullmatch(n) for n in members):
        raise ValueError("Expected the runtime-only archive produced by build_host.py")
    marker = out / "disc-kit.id"
    marker.unlink(missing_ok=True)
    digests = {}

    with zipfile.ZipFile(args.compiler_apk) as donor, zipfile.ZipFile(
            io.BytesIO(donor.read("assets/kit.zip"))) as compiler, zipfile.ZipFile(
            out / "disc-kit.zip", "w", zipfile.ZIP_DEFLATED, compresslevel=4) as kit:
        def add(name, data):
            p = PurePosixPath(name)
            if p.is_absolute() or ".." in p.parts or "\\" in name or name in digests:
                raise ValueError("Invalid kit entry: "+name)
            kit.writestr(name, data)
            digests[name] = hashlib.sha256(data).hexdigest()

        for name in TOOLS:
            data = donor.read("lib/arm64-v8a/"+name)
            if data[:6] != b"\x7fELF\x02\x01" or data[18:20] != b"\xb7\x00":
                raise ValueError("Expected an ARM64 Android tool: "+name)
            (out/name).write_bytes(data)
        # Only generic Python, Clang and Capstone components are selected.
        for info in compiler.infolist():
            n = info.filename
            if info.is_dir():
                continue
            if n.startswith(("py/lib/python3.14/", "clang/include/")):
                add(n, compiler.read(info))
            elif n.startswith("lift/site/capstone/") and n.endswith(".py"):
                add(n.removeprefix("lift/"), compiler.read(info))
        for name in ("py/lib/python3.14/os.py", "clang/include/stddef.h", "site/capstone/__init__.py"):
            if name not in digests:
                raise ValueError("Missing compiler component: "+name)
        include = prebuilt/"sysroot/usr/include"
        for p in sorted(include.rglob("*")):
            name = p.relative_to(include).as_posix()
            if p.is_file() and guest_header(name):
                add("sysroot/usr/include/"+name, p.read_bytes())
        for name in ("crtbegin_so.o","crtend_so.o","libm.so","liblog.so","libdl.so","libaaudio.so","libandroid.so","libvulkan.so","libc.so"):
            add("sysroot/usr/lib/aarch64-linux-android/30/"+name, (libs/name).read_bytes())
        rt, = (prebuilt/"lib/clang").glob("*/lib/linux/libclang_rt.builtins-aarch64-android.a")
        add("clang/lib/linux/"+rt.name, rt.read_bytes())
        add("clang/lib/linux/aarch64/libunwind.a", (rt.parent/"aarch64/libunwind.a").read_bytes())
        add("xboxrecomp/tools/__init__.py", (args.toolkit/"tools/__init__.py").read_bytes())
        for sub in ("xbe_parser","disasm","abi_analysis","recomp","func_id"):
            for p in sorted((args.toolkit/"tools"/sub).glob("*.py")):
                if not p.name.startswith("test_"):
                    add("xboxrecomp/tools/"+sub+"/"+p.name, p.read_bytes())
        for name in ("recomp_types.h","kernel_stubs.h","xbox_memory.h"):
            add("xboxrecomp/templates/runtime/"+name,(args.toolkit/"templates/runtime"/name).read_bytes())
        for name in ("tools/device_build.py","tools/compile_objects.py","tools/bringup.py","tools/compact_dispatch.py","tools/guest_hotpaths.py","native/matrix_stream.h","native/texel_offset.h",
                     "native/title_hooks.c","native/observed_functions.json",
                     "tools/translate_disc.py"):
            data=(ROOT/name).read_bytes().replace(b"\r\n",b"\n")
            # Keep the shipped Windows kit's byte representation on every checkout.
            # These four source files were CRLF when the first reusable cache was written.
            if name in {"tools/device_build.py","tools/compile_objects.py","tools/bringup.py","native/observed_functions.json"}:
                data=data.replace(b"\n",b"\r\n")
            add(name,data)
        from record_disc_layout import release_layout
        add("native/disc_layout.json", release_layout(args.disc_layout, args.toolkit))
        # Guest objects depend on the translator, compiler, headers and guest hooks.
        # A replacement rendering engine only needs those same objects relinked.
        add("guest-build.id", guest_identity(digests, out))
        add("runtime.a",archive.read_bytes())
        for p in (args.ndk/"NOTICE",args.ndk/"NOTICE.toolchain",args.toolkit/"LICENSE",args.toolkit/"NOTICE"):
            add("licenses/"+p.parent.name+"/"+p.name,p.read_bytes())
        for p in (args.toolkit/"LICENSES").glob("*"):
            if p.is_file():add("licenses/xboxrecomp/"+p.name,p.read_bytes())
        add("licenses/LLVM.txt",(ROOT/"third-party/LLVM.txt").read_bytes())
        add("licenses/SSX3RecompAndroid-NOTICE.md",(args.android_reference/"NOTICE.md").read_bytes())
        add("licenses/Capstone.txt",args.capstone_license.read_bytes())
        add("licenses/SSX3RecompAndroid.txt",(args.android_reference/"LICENSE").read_bytes())

    # Package our own Python command-line entry point, linked to the standard interpreter.
    subprocess.run([args.clang,"--target=aarch64-linux-android30","-O2",ROOT/"native/disc_python.c",
                    "-L"+str(out),"-lpython3.14","-Wl,-rpath,$ORIGIN","-o",out/"libpyrun.so"],check=True)
    for name in ("libdisc_runner.so","libframe_reader.so"):
        shutil.copyfile(args.runtime/name,out/name)
    from build_live_shaders import build
    build(args.ndk.resolve(), args.android_reference.resolve(), args.clang.resolve(), out)
    for name in NATIVES:
        digests["lib/"+name]=hashlib.sha256((out/name).read_bytes()).hexdigest()
    signature=hashlib.sha256()
    for name,digest in sorted(digests.items()):
        if name in ("tools/device_build.py", "tools/compile_objects.py"):
            digest = OBJECT_COMPATIBILITY.get(digest, digest)
        signature.update(name.encode()+b"\x00"+bytes.fromhex(digest))
    marker.write_text(signature.hexdigest()+"\n",encoding="ascii")
    print("Disc build kit ready in",out)


def guest_header(name):
    # The on-device compiler only builds the generated ARM64 C units.
    # The engine, graphics libraries and compiler itself are already built.
    folder, sep, _ = name.partition("/")
    return (not sep or folder in {"sys", "bits", "android", "aarch64-linux-android", "asm-generic"}
            or name in {"linux/limits.h", "linux/wait.h", "linux/types.h", "linux/posix_types.h", "linux/stddef.h"})


# These validator-only revisions produce the same guest objects as the preceding kit.
# Any other compiler, flag or script revision continues to get its own cache.
OBJECT_COMPATIBILITY = {
    "6eea2ca33be8a6b74fccc222579817f278ee7264ea4592642f534968fdb4efd9": "e1fad5da28dd38baeeaa535bf18df39b9644a71400d30857e4fdb1d8d22a63c4",
    "a816c7840be2f42e860ce6a111e390e05f722ce35867efcb065f6afd90a49657": "ba9c07503b0979b4979ecfa13716508fa7e3fbc6c4c87dd51dd9ed788da8be2c",
}


def guest_identity(digests, out):
    signature = hashlib.sha256()
    for name, digest in sorted(digests.items()):
        if name.startswith("licenses/") or name in ("runtime.a", "guest-build.id"):
            continue
        if name in ("tools/device_build.py", "tools/compile_objects.py"):
            digest = OBJECT_COMPATIBILITY.get(digest, digest)
        signature.update(name.encode()+b"\0"+bytes.fromhex(digest))
    for name in TOOLS:
        with (out/name).open("rb") as stream:
            signature.update(hashlib.file_digest(stream, "sha256").digest())
    return (signature.hexdigest()+"\n").encode("ascii")


if __name__ == "__main__":
    parser=argparse.ArgumentParser(description=__doc__)
    for name in ("compiler-apk","disc-layout","toolkit","runtime","ndk","clang","android-reference","capstone-license","output"):
        parser.add_argument("--"+name,type=Path,required=True)
    assemble(parser.parse_args())
