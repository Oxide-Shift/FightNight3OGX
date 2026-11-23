"""Check the file inventory of the single-app disc-import release before distribution."""
import argparse
import io
from pathlib import Path, PurePosixPath
import re
import zipfile

from build_disc_kit import NATIVES


def audit(apk):
    required = {"AndroidManifest.xml", "classes.dex", "resources.arsc", "res/raw/corner_unlock.wav",
                "assets/achievement-badges.webp", "assets/crash-inbox.pem", "assets/thor-controls.dxt3", "assets/corner.html", "assets/ring-focus-loop.ogg", "assets/BoxingBell.txt",
                "assets/disc-kit.zip", "assets/disc-kit.id"}
    required.update({"res/drawable-nodpi-v4/corner_glove.png","res/mipmap-xxxhdpi-v4/fight_night.png","res/mipmap-anydpi-v26/fight_night.xml"})
    required.update("lib/arm64-v8a/" + name for name in NATIVES)
    signatures = {"META-INF/CORNER.RSA", "META-INF/CORNER.SF", "META-INF/MANIFEST.MF"}
    with zipfile.ZipFile(apk) as app:
        names = set(app.namelist())
        if len(names) != len(app.infolist()) or not required <= names or names - required - signatures - {"assets/benchmark-preparation"}:
            raise ValueError("Unexpected files in the game APK: " + repr(names ^ required))
        if not re.fullmatch(rb"[0-9a-f]{64}\s*", app.read("assets/disc-kit.id")):
            raise ValueError("Invalid build kit identifier")
        with zipfile.ZipFile(io.BytesIO(app.read("assets/disc-kit.zip"))) as kit:
            exact = {"runtime.a", "guest-build.id", "tools/device_build.py", "tools/bringup.py", "tools/compile_objects.py",
                     "native/texel_offset.h", "native/title_hooks.c", "native/observed_functions.json",
                     "native/disc_layout.json", "tools/translate_disc.py"}
            entries = set(kit.namelist())
            if not exact <= entries or len(entries) != len(kit.infolist()):
                raise ValueError("Build kit is incomplete or contains duplicate files")
            for name in entries:
                path = PurePosixPath(name)
                if path.is_absolute() or ".." in path.parts or "\\" in name:
                    raise ValueError("Invalid kit path: " + name)
                if name not in exact and not name.startswith(("py/lib/python3.14/", "site/capstone/", "clang/",
                        "sysroot/usr/", "licenses/", "xboxrecomp/tools/", "xboxrecomp/templates/runtime/")):
                    raise ValueError("Unexpected kit component: " + name)
                if path.suffix.lower() in {".xbe", ".xiso", ".iso", ".chd", ".bik"} or re.fullmatch(
                        r"(?:fight-night-guest\.o|libfightnight_(?:host|game)\.so|recomp_[0-9]+\.[co])", path.name):
                    raise ValueError("Private game content in release: " + name)
            if kit.testzip() is not None:
                raise ValueError("Corrupt build kit")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("apk", type=Path)
    audit(parser.parse_args().apk)
    print("Disc-import APK file inventory passed.")
