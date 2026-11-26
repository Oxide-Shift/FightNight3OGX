"""Build the general-purpose on-device GLSL and SPIR-V specializer."""
import json
from pathlib import Path
import subprocess


def build(ndk, reference, clang, out, objects=None):
    shaderc = ndk / "sources/third_party/shaderc"
    source = (reference / "src/vk/shaders/xbox_vsh.vert").read_text()
    source = source.replace("f[vbase + ((w >> 6) & 15u)]", "input_at((w >> 6) & 15u)")
    fetch = source.index("vec4 src(uint w, uint vbase)")
    loop = source.index("    for (uint s = 0u; s < 136u; s++)")
    op = source.index("        uint ctl = i0.x;", loop)
    end = source.index("        if (((ctl >> 26)", op)
    finish = source.index("    vec4 pos = R[12];", end)
    sections = dict(prefix=source[:fetch], init=source[fetch:loop], op=source[op:end], finish=source[finish:])
    header = out / "live_vertex_template.h"
    header.write_text("".join(f"static const char vertex_{name}[] = {json.dumps(code)};\n"
                              for name, code in sections.items()), encoding="ascii")
    if objects is None:
        objects = out / "shader-obj"
        prefix_map = "-ffile-prefix-map=" + ndk.as_posix() + "=ndk"
        subprocess.run([ndk / "ndk-build.cmd", "-j8", "NDK_PROJECT_PATH=null",
                        "APP_BUILD_SCRIPT=" + str(shaderc / "Android.mk"), "APP_MODULES=shaderc", "APP_CFLAGS="+prefix_map,
                        "NDK_OUT=" + str(objects), "NDK_LIBS_OUT=" + str(out / "shader-libs"),
                        "APP_ABI=arm64-v8a", "APP_PLATFORM=android-30", "APP_STL=c++_static"], check=True)
    archives = sorted((objects / "local/arm64-v8a").glob("*.a"))
    archives = [a for a in archives if a.name != "libshaderc_combined.a"]
    native = Path(__file__).resolve().parents[1] / "native"
    subprocess.run([clang.with_name("clang++.exe"), "--target=aarch64-linux-android30",
                    "-ffile-prefix-map="+str(native)+"=native", "-ffile-prefix-map="+native.as_posix()+"=native",
                    "-shared", "-fPIC", "-O2", "-fvisibility=hidden", "-fno-exceptions", "-fno-rtti",
                    "-static-libstdc++", "-Wl,--gc-sections,--exclude-libs,ALL,-z,defs,-z,max-page-size=16384",
                    "-Wl,-soname,libfight_shader.so", "-I", out, "-I", shaderc / "libshaderc/include",
                    "-I", shaderc / "third_party/spirv-tools/include", native / "live_shader.cpp",
                    "-Wl,--start-group", *archives, "-Wl,--end-group", "-llog", "-o", out / "libfight_shader.so"], check=True)
    subprocess.run([clang.with_name("llvm-strip.exe"), "--strip-unneeded", out / "libfight_shader.so"], check=True)
