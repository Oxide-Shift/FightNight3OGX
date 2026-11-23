"""Package the ROM-import launcher and its private native game host on Windows."""
import argparse
import hashlib
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("private_dir", type=Path)
    parser.add_argument("--jdk", type=Path, required=True)
    parser.add_argument("--build-tools", type=Path, required=True)
    parser.add_argument("--platform", type=Path, required=True, help="Path to android.jar")
    build = parser.add_mutually_exclusive_group(required=True)
    parser.set_defaults(setup=False)
    if (Path(__file__).resolve().parents[1]/"setup").is_dir():
        build.add_argument("--setup", action="store_true", help="Build the standalone PC downloader")
    build.add_argument("--disc-kit", type=Path, help="Build one game app that prepares its executable from the imported ISO")
    build.add_argument("--host", type=Path, help="Private Android PIE compiled from your own disc")
    parser.add_argument("--emulator", action="store_true", help="Package a private x86-64 Android test host")
    parser.add_argument("--benchmark", action="store_true", help="Time a full uncached disc preparation on each new app process")
    args = parser.parse_args()
    if args.benchmark and not args.disc_kit:
        parser.error("--benchmark requires --disc-kit")
    project = Path(__file__).resolve().parents[1]
    sdk, jar, jdk = args.build_tools.resolve(), args.platform.resolve(), args.jdk.resolve() / "bin"
    private = args.private_dir.resolve()
    if private == project or project in private.parents:
        parser.error("Choose an output folder outside the source repository")
    private.mkdir(parents=True, exist_ok=True)
    app = project / "android"
    java = jdk / "java.exe"
    output = private / "FightNight-Round3-Android-test.apk"
    abi = "x86_64" if args.emulator else "arm64-v8a"
    machine = 62 if args.emulator else 183
    if args.setup:
        if args.emulator:
            parser.error("--emulator requires --host")
        app = project / "setup"
        output = private / "FightNight3OGX-Setup.apk"
    elif args.disc_kit:
        if args.emulator:
            parser.error("The on-device compiler requires ARM64")
        output = private / "FightNight3OGX-DiscImport.apk"
    else:
        with args.host.open("rb") as f:
            hdr = f.read(64)
        if hdr[:6] != b"\x7fELF\x02\x01" or hdr[16:18] != b"\x03\x00" or int.from_bytes(hdr[18:20], "little") != machine:
            parser.error(f"Expected a little-endian {abi} ELF PIE")
    if args.emulator:
        output = private / "FightNight-Round3-Emulator-test.apk"
    with tempfile.TemporaryDirectory(dir=private) as tmp:
        work = Path(tmp)
        classes, dex = work / "classes", work / "dex"
        classes.mkdir(); dex.mkdir()
        def run(*cmd, **opts):
            subprocess.run(cmd, cwd=work, check=True, **opts)
        source = sorted((app / "src").rglob("*.java"))
        if args.setup:
            shared = project / "android/src/org/oxideshift/corner"
            source.extend(shared / name for name in ("PrivateFeed.java", "ReleaseAsset.java", "ReleaseVersion.java"))
        run(jdk / "javac.exe", "--release", "8", "-parameters", "-encoding", "UTF-8", "-classpath", jar,
            "-d", classes, *source)
        compiled = work / "classes.jar"
        with zipfile.ZipFile(compiled, "w") as archive:
            for cls in classes.rglob("*.class"):
                archive.write(cls, cls.relative_to(classes).as_posix())
        run(java, "-cp", sdk / "lib/d8.jar", "com.android.tools.r8.D8", "--min-api", "30",
            "--lib", jar, "--output", dex, compiled)
        unsigned = work / "unsigned.apk"
        manifest = app / "AndroidManifest.xml"
        link = [sdk / "aapt2.exe", "link", "-I", jar, "--manifest", manifest, "-o", unsigned]
        if not args.setup:
            resources = work / "resources.zip"
            res, assets = app / "res", app / "assets"
            if args.disc_kit:
                res, assets = work / "res", work / "assets"
                (res / "values").mkdir(parents=True)
                (res / "raw").mkdir()
                assets.mkdir()
                for name in ("drawable-nodpi/corner_glove.png","mipmap-xxxhdpi/fight_night.png","mipmap-anydpi-v26/fight_night.xml"):
                    (res/name).parent.mkdir(exist_ok=True)
                    shutil.copyfile(app/"res"/name,res/name)
                shutil.copyfile(app / "res/values/colors.xml", res / "values/colors.xml")
                shutil.copyfile(app / "res/raw/corner_unlock.wav", res / "raw/corner_unlock.wav")
                shutil.copyfile(app / "assets/thor-controls.dxt3",assets / "thor-controls.dxt3")
                shutil.copyfile(app / "assets/corner.html", assets / "corner.html")
            run(sdk / "aapt2.exe", "compile", "--dir", res, "-o", resources)
            link.extend(["-A", assets, resources])
        run(*link)
        with zipfile.ZipFile(unsigned, "a", compression=zipfile.ZIP_DEFLATED) as archive:
            archive.write(dex / "classes.dex", "classes.dex")
            if not args.setup:
                if args.disc_kit:
                    if args.benchmark:
                        archive.writestr("assets/benchmark-preparation",b"1")
                    from build_disc_kit import NATIVES
                    for name in NATIVES:
                        archive.write(args.disc_kit / name, f"lib/{abi}/{name}")
                    archive.write(args.disc_kit / "disc-kit.zip", "assets/disc-kit.zip", compress_type=zipfile.ZIP_STORED)
                    archive.write(args.disc_kit / "disc-kit.id", "assets/disc-kit.id")
                else:
                    archive.write(args.host, f"lib/{abi}/libfightnight_host.so")
                    archive.write(args.host.parent / "libframe_reader.so", f"lib/{abi}/libframe_reader.so")
                archive.write(app / "music/ring-focus-loop.ogg", "assets/ring-focus-loop.ogg", compress_type=zipfile.ZIP_STORED)
                archive.write(project / "third-party/BoxingBell.txt", "assets/BoxingBell.txt")
        aligned = work / "aligned.apk"
        run(sdk / "zipalign.exe", "-f", "4", unsigned, aligned)
        keys = private / "signing"
        keys.mkdir(exist_ok=True)
        password = keys / "local.pass"
        if not password.exists():
            with password.open("x") as stream:
                stream.write(secrets.token_urlsafe(32))
        env = dict(os.environ, FNR3_SIGN_PASS=password.read_text().strip())
        store = keys / "local.p12"
        if not store.exists():
            run(jdk / "keytool.exe", "-genkeypair", "-keystore", store, "-storetype", "PKCS12",
                "-storepass:env", "FNR3_SIGN_PASS", "-alias", "corner", "-keyalg", "RSA",
                "-keysize", "2048", "-validity", "3650", "-dname", "CN=Local Development", env=env)
        signer = sdk / "lib/apksigner.jar"
        signed = work / "signed.apk"
        run(java, "-jar", signer, "sign", "--ks", store, "--ks-pass", "env:FNR3_SIGN_PASS",
            "--v4-signing-enabled", "false", "--out", signed, aligned, env=env)
        run(java, "-jar", signer, "verify", "--verbose", signed)
        if args.disc_kit:
            from audit_disc_apk import audit
            audit(signed)
        if args.setup:
            with zipfile.ZipFile(signed) as archive:
                entries = set(archive.namelist())
                required = {"AndroidManifest.xml", "classes.dex"}
                if not required <= entries or entries - required - {"resources.arsc", "META-INF/CORNER.RSA", "META-INF/CORNER.SF", "META-INF/MANIFEST.MF"}:
                    raise ValueError("Unexpected content in setup APK: " + repr(entries))
        signed.replace(output)
    print(output, hashlib.sha256(output.read_bytes()).hexdigest())


if __name__ == "__main__":
    main()
