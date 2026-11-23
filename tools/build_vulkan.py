"""Build the generic Vulkan drawing library from the pinned Android reference checkout."""
from pathlib import Path
import os
import shutil
import subprocess
from build_vertex_shaders import build as build_vertex_programs
from build_fragment_shaders import build as build_fragment_programs


def prepare(reference, out, compiler, ndk, android, profiles=True):
    vk = reference / "src/vk"
    generated = out / "vulkan"
    generated.mkdir(exist_ok=True)
    glslc = ndk / "shader-tools/windows-x86_64/glslc.exe"
    private=out.parent
    count=build_vertex_programs(vk/"shaders/xbox_vsh.vert",generated,glslc,
                                private/"vertex-programs.bin" if profiles else None,private/"vertex-profile.log")
    print(f"Specialized {count} Xbox vertex programs", flush=True)
    shaders = {
        "xbox_vert": ("xbox.vert", []), "xbox_frag": ("xbox.frag", []),
        "fight_full_coverage": (out.resolve() / "runtime/fight_full_coverage.frag", []),
        "xbox_frag_wdepth": ("xbox.frag", ["W_DEPTH"]),
        "fight_depth": ((Path(__file__).resolve().parents[1] / "native" / "fight_depth.frag"), []),
        "fight_depth_w": ((Path(__file__).resolve().parents[1] / "native" / "fight_depth.frag"), ["W_DEPTH"]),
        "xbox_frag_tags": ("xbox.frag", ["TAGS"]),
        "xbox_frag_wdepth_tags": ("xbox.frag", ["W_DEPTH", "TAGS"]),
        "xbox_vsh_vert": ("xbox_vsh.vert", []), "crt_vert": ("crt.vert", []),
        "crt_frag": ("crt.frag", []), "scene_frag": ("scene.frag", []),
        "scene_frag_ms": ("scene.frag", ["MS_DEPTH"]),
    }
    for symbol, (source, defines) in shaders.items():
        spv = generated / (symbol + ".spv")
        subprocess.run([glslc, "--target-env=vulkan1.3", "-O", *["-D" + d for d in defines],
                        vk / "shaders" / source, "-o", spv], check=True)
        data = spv.read_bytes()
        lines = [",".join(str(b) for b in data[i:i + 32]) + "," for i in range(0, len(data), 32)]
        (generated / (symbol + "_spv.h")).write_text(
            "static const unsigned char __attribute__((aligned(4))) " + symbol + "_spv[] = {\n"
            + "\n".join(lines) + "\n};\nstatic const unsigned long " + symbol
            + "_spv_len = sizeof(" + symbol + "_spv);\n")
    count = build_fragment_programs(private / "fragment-programs.bin" if profiles else None, generated, glslc.parent)
    print(f"Specialized {count} Xbox fragment programs", flush=True)
    if android:
        libs = ["-lvulkan"]
    else:
        headers = ndk / "toolchains/llvm/prebuilt/windows-x86_64/sysroot/usr/include"
        for name in ("vulkan", "vk_video"):
            shutil.copytree(headers / name, generated / name, dirs_exist_ok=True)
        dll = Path(os.environ["SystemRoot"]) / "System32/vulkan-1.dll"
        subprocess.run([compiler.parent / "gendef.exe", dll], cwd=generated, check=True)
        lib = generated / "libvulkan.a"
        subprocess.run([compiler.parent / "llvm-dlltool.exe", "-m", "i386:x86-64", "-d",
                        generated / "vulkan-1.def", "-l", lib], check=True)
        libs = [lib]
    return [vk / "vkr.c", reference / "src/log.c"], ["-I", vk, "-I", generated, "-DFNR3_VULKAN"], libs
