"""Build from the verified disc. Retain completed guest objects across compatible engine updates."""
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys

from bringup import XBE_SHA256
from compile_objects import object_inventory


def workers():
    try:
        count = max(1, len(os.sched_getaffinity(0)))
    except (AttributeError, OSError):
        count = max(1, os.cpu_count() or 1)
    try:
        fields = Path("/proc/meminfo").read_text()
        available = int(re.search(r"^MemAvailable:\s+(\d+)", fields, re.M)[1])
    except (OSError, TypeError, ValueError):
        return min(3, count)
    # Leave memory and at least one CPU for Android and the foreground launcher.
    limit = 7 if available >= 4 * 1024 * 1024 else 6
    return max(1, min(limit, count-1, (available-640*1024)//(400*1024)))


def main():
    xbe, destination, native = map(Path, sys.argv[1:])
    kit = Path(__file__).resolve().parents[1]
    cache = destination.parent.parent / "guest-cache"
    cache.mkdir(exist_ok=True, parents=True)
    with (cache / "build.lock").open("a") as lock:
        fcntl.flock(lock, fcntl.LOCK_EX)
        if destination.is_file():
            return
        print("Verifying imported Xbox executable", flush=True)
        with xbe.open("rb") as stream:
            if hashlib.file_digest(stream, "sha256").hexdigest() != XBE_SHA256:
                raise ValueError("Unsupported Fight Night executable")
        key = (kit / "guest-build.id").read_text().strip()
        if not re.fullmatch("[0-9a-f]{64}", key):
            raise ValueError("Invalid guest compiler identity")
        work = cache / key
        work.mkdir(exist_ok=True)
        out = work / "module-android"
        complete = object_inventory(out) is not None
        # A sealed cache that failed verification must never be reused by the
        # partial-compilation fast path, even if translation.ready still exists.
        if not complete and (out / "inventory.json").exists():
            shutil.rmtree(out)
        translated = work / "translation.ready"
        if complete:
            print("Reusing compiled Xbox game", flush=True)
        elif not translated.exists() or not (work/"gen/recomp_dispatch.c").exists():
            # Remove a partial translation or an incomplete cache before starting over.
            shutil.rmtree(work)
            work.mkdir()
            subprocess.run([sys.executable, "-u", kit/"tools/bringup.py", xbe, kit/"xboxrecomp",
                            work, "--imported-xbe", "--disc-layout", kit/"native/disc_layout.json",
                            "--jobs", str(min(5, workers()))], check=True)
            translated.touch()
        else:
            print("Resuming saved Xbox compilation", flush=True)
        subprocess.run([sys.executable, "-u", kit/"tools/compile_objects.py", work, "--target", "android",
                        "--clang", native/"libllvm.so", "--jobs", str(workers()), "--runtime-archive",
                        kit/"runtime.a", "--device-kit", kit, *(["--relink"] if complete else [])], check=True)
        module = out / "libfightnight_game.so"
        module.chmod(0o400)
        os.replace(module, destination)
        print("Saving prepared game", flush=True)
        # Keep only the small object cache. Remove obsolete inputs after publishing.
        for entry in work.iterdir():
            if entry != out:
                if entry.is_dir(): shutil.rmtree(entry, ignore_errors=True)
                else: entry.unlink(missing_ok=True)
        for entry in out.iterdir():
            if entry.suffix != ".o" and entry.name != "inventory.json":
                entry.unlink(missing_ok=True)
        for entry in cache.iterdir():
            if entry != work and re.fullmatch("[0-9a-f]{64}", entry.name):
                shutil.rmtree(entry, ignore_errors=True)


if __name__ == "__main__":
    main()
