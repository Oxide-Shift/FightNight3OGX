"""Build a private, signed Android development APK from an original Xbox disc on Windows."""
import argparse
from pathlib import Path
import subprocess
import sys


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("disc", type=Path)
    parser.add_argument("private_dir", type=Path)
    parser.add_argument("--toolkit", type=Path, required=True, help="Patched xboxrecomp source")
    parser.add_argument("--android-reference", type=Path, required=True, help="SSX3RecompAndroid source")
    parser.add_argument("--clang", type=Path, required=True, help="Windows Android NDK clang.exe")
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--platform", type=Path, required=True, help="Android SDK android.jar")
    parser.add_argument("--build-tools", type=Path, required=True)
    args = parser.parse_args()
    project = Path(__file__).resolve().parent
    work = args.private_dir.resolve()
    if work.is_relative_to(project):
        parser.error("Choose a private build directory outside the repository")
    for path in (args.disc, args.clang, args.platform, args.jdk / "bin/javac.exe",
                 args.build_tools / "aapt2.exe", args.android_reference / "src/mmio_posix.c"):
        if not path.is_file():
            parser.error(f"Missing build input: {path}")
    for patch in ("pop16.patch", "setjmp-frame.patch"):
        check = subprocess.run(["git", "-C", args.toolkit, "apply", "--reverse", "--check", project / "patches" / patch],
                               capture_output=True)
        if check.returncode:
            parser.error(f"Apply {patch} to the pinned toolkit as described in docs/DEVELOPMENT.md")

    def run(script, *arguments):
        subprocess.run([sys.executable, project / "tools" / script, *arguments], check=True)

    run("bringup.py", args.disc, args.toolkit, work)
    run("compile_objects.py", work, "--target", "android", "--clang", args.clang)
    run("build_host.py", work, args.toolkit, "--target", "android", "--clang", args.clang,
        "--android-reference", args.android_reference)
    run("build_apk.py", work / "apps", "--host", work / "host-android/fightnight-host",
        "--jdk", args.jdk, "--platform", args.platform, "--build-tools", args.build_tools)


if __name__ == "__main__":
    main()
