"""Decode every installed MP3 on a USB Thor using the game's Android decoder."""
import argparse
import os
from pathlib import Path
import secrets
import subprocess
import tempfile
import zipfile


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for arg in ("jdk", "build-tools", "platform", "adb"):
        parser.add_argument("--"+arg, type=Path, required=True)
    parser.add_argument("--clean-install", action="store_true", help="Use the isolated fresh QA installation")
    args = parser.parse_args()
    package = "org.oxideshift.corner" + (".freshqa" if args.clean_install else "")
    runner = "org.oxideshift.traxqa"
    adb = [args.adb, "-d"]
    sdk = args.build_tools
    jdk = args.jdk/"bin"
    game = Path(__file__).resolve().parents[1]/"android/src/org/oxideshift/corner"

    def run(*cmd, **options):
        return subprocess.run(cmd, check=True, timeout=300, **options)

    if run(*adb,"shell","pm","list","packages",runner,capture_output=True).stdout.strip():
        parser.error("The test package name is already in use on this device.")
    info = run(*adb,"shell","dumpsys","package",package,capture_output=True).stdout
    if b"versionName=" not in info or b"android.permission.INTERNET" in info:
        parser.error("Install the private offline build before testing.")
    with tempfile.TemporaryDirectory() as temp:
        work = Path(temp)
        classes = work/"classes"
        classes.mkdir()
        manifest = work/"AndroidManifest.xml"
        manifest.write_text(
            f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{runner}">'
            '<uses-sdk android:minSdkVersion="30" android:targetSdkVersion="33"/>'
            f'<queries><package android:name="{package}"/></queries><application/>'
            f'<instrumentation android:name="org.oxideshift.corner.ThorSoundtrackDecode" android:targetPackage="{runner}"/>'
            '</manifest>', encoding="utf8")
        run(jdk/"javac.exe","--release","8","-cp",args.platform,"-d",classes,
            *(game/(name+".java") for name in ("DiscTrax","ExpandedAudio","ExpandedTracks")),
            Path(__file__).with_name("ThorSoundtrackDecode.java"))
        with zipfile.ZipFile(work/"classes.jar","w") as archive:
            for item in classes.rglob("*.class"):
                archive.write(item,item.relative_to(classes).as_posix())
        run(jdk/"java.exe","-cp",sdk/"lib/d8.jar","com.android.tools.r8.D8","--min-api","30",
            "--lib",args.platform,"--output",work,work/"classes.jar")
        unsigned, aligned, apk = (work/(name+".apk") for name in ("unsigned","aligned","test"))
        run(sdk/"aapt2.exe","link","-I",args.platform,"--manifest",manifest,"-o",unsigned)
        with zipfile.ZipFile(unsigned,"a") as archive:
            archive.write(work/"classes.dex","classes.dex")
        run(sdk/"zipalign.exe","4",unsigned,aligned)
        key = work/"test.p12"
        env = dict(os.environ, FNR3_TEST_KEY=secrets.token_hex(16))
        run(jdk/"keytool.exe","-genkeypair","-keystore",key,"-storetype","PKCS12","-storepass:env",
            "FNR3_TEST_KEY","-alias","qa","-keyalg","RSA","-validity","1","-dname","CN=Offline audio QA",env=env)
        run(jdk/"java.exe","-jar",sdk/"lib/apksigner.jar","sign","--ks",key,"--ks-pass","env:FNR3_TEST_KEY",
            "--out",apk,aligned,env=env)
        run(*adb,"install",apk)
        try:
            result = run(*adb,"shell","am","instrument","-w","-e","gamePackage",package,
                         runner+"/org.oxideshift.corner.ThorSoundtrackDecode",capture_output=True,text=True)
            print(result.stdout)
            if "INSTRUMENTATION_CODE: -1" not in result.stdout:
                raise RuntimeError("The installed soundtrack failed Android decode validation")
        finally:
            run(*adb,"uninstall",runner)


if __name__=="__main__":
    main()
