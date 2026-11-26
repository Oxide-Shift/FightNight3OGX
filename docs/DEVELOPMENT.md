# Building the Android disc importer

Use a Windows machine with Python 3.11 or newer, a JDK, Android SDK platform 34 and build tools 36, and Android NDK r30. The app targets ARM64 Android. The source tree contains the Android launcher, native runtime integration, build tools and tests.

For local APK builds, extract the seven MP3s from the [FN3 Expanded Music source pack](https://github.com/Oxide-Shift/FightNight3OGX/releases/download/v0.4.0-alpha.51/FN3-Expanded-Music.zip) directly into `android/music/expanded/`. The release APK already includes them. The pack SHA-256 is `7f2269a7fdff126855d92cf38434a898e61865efe58a224d9f7c6103e1ad0a1c`.

The Xbox toolkit revision is `c5d3979d8b05381b3a9c63af95b4114ef667c5b2` from [xboxrecomp](https://github.com/DouglasHalse/xboxrecomp). Apply `patches/pop16.patch` and `patches/setjmp-frame.patch` to that checkout. The Android reference revision is `d5b86480d2692aa4f64f1df952fe5043e748cf42` from [SSX3RecompAndroid](https://github.com/DouglasHalse/SSX3RecompAndroid).

Keep the analysis directory and generated address map outside the repository. Set the paths below to your toolchain and private build directory. The current kit builder takes its Android Python, Capstone and LLVM tools from a local FF7Recomp 1.1.41 compiler APK. Provide the matching Capstone license text. Keep the signing folder for subsequent APK upgrades.

```powershell
& $python tools/bringup.py $discIso $toolkit $analysis
& $python tools/record_disc_layout.py $analysis $toolkit "$analysis/disc_layout.json"
& $python tools/build_host.py $engineBuild $toolkit --target android --clang $ndkClang --android-reference $reference --runtime-only
& $python tools/build_disc_kit.py --compiler-apk $compilerApk --disc-layout "$analysis/disc_layout.json" --toolkit $toolkit --runtime "$engineBuild/runtime-android" --ndk $ndk --clang $ndkClang --android-reference $reference --capstone-license $capstoneLicense --output $kit
& $python tools/build_apk.py $apkOut --disc-kit $kit --jdk $jdk --platform "$sdk/platforms/android-34/android.jar" --build-tools "$sdk/build-tools/36.0.0"
```

For a preparation benchmark, add `--benchmark` to the final command. The test package builds the game from scratch on each new app process. The default command produces a package that reuses compatible prepared data.

## Validate a translation change

The handheld reads instruction bytes from the verified, imported executable. The packaged address map describes boundaries, calling conventions and recovered control-flow ranges for this revision. The desktop full-analysis route remains available for regenerating the map.

1. Run `tools/bringup.py` on your own supported disc to produce the reference analysis and generated sources in a private directory.
2. Run `python tools/record_disc_layout.py <analysis-directory> <patched-toolkit> <analysis-directory>/disc_layout.json` after changing the toolkit, seed addresses or game hooks.
3. Extract the resulting kit privately. Run its `tools/bringup.py` on the same executable with `--imported-xbe --disc-layout <kit>/native/disc_layout.json --jobs 4`. Compare the complete generated file inventory and file hashes to the reference. Compile and link the ARM64 output before timing it on a handheld.
4. Run `python tools/audit_disc_apk.py <signed-apk>` to check the APK inventory. Review dependency notices in [third-party](../third-party/) when preparing a distribution.

## Release format

The launcher reads public releases from `Oxide-Shift/FightNight3OGX`. Use a tag such as `v0.4.0-alpha.47` and an asset named `FightNight3OGX-0.4.0-alpha.47.apk`. The launcher checks the version, repository download URL, advertised size and SHA-256, Android package name and signing certificate before installation.

## Thor loading guide and release compatibility

On AYN Thor, the launcher rebuilds the six controller-configuration loading movies in the imported disc. Each page pairs Thor inputs with the actions supplied by the current in-game preset. The purple control-deck diagram and ringside backdrop come from `tools/thor_guide_art.py`. The game supplies the fonts, localized actions and loading indicator.

For the Alpha 47 release, attach a byte-identical copy named `FightNight-Round3-Thor-0.4.0-alpha.47.apk` alongside the primary APK. Alpha 46 matches that exact legacy filename. Alpha 47 and later look for the FightNight3OGX filename.

To measure a full retail setup, start with a fresh install and select the disc. **Disc to ready** covers extraction and compilation, which overlap.

## Unattended Thor testing

From the Windows `Recomps` workspace, run `& .\FightNight3OGX\tools\Start-Thor-Sweep.ps1` with the authorized Thor connected over USB. The script uses the Android SDK under the current Windows account, the local signed QA kit, and the original Xbox ISO. Pass `-Iso` to select another local path. The separately installed `org.oxideshift.corner.usbqa` package owns the test import, boxer profile, achievements and settings.

The runner verifies the disc checksum before import and can resume an interrupted copy. It checks the default launcher, then selects High, 1080p and 60 FPS for the six venue tests. Each run writes its case results, captures and device timing to a new folder under `Thor-Stutter-Captures/Hardware-Sweep`. An unobserved treatment gesture or a fight without an ending remains visible in the results.

The SDK server listens on Windows loopback at `127.0.0.1:5037`. If regular PowerShell lists the Thor and another execution environment receives Windows error 10013 when connecting to this address, live control needs loopback access in that environment. The one-shot sweep can also run directly from regular PowerShell.
