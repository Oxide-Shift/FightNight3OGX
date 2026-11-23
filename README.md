# FightNight3OGX

Play **Fight Night Round 3** from an original Xbox disc image on an ARM64 Android handheld. The AYN Thor puts the fight on the upper screen and your corner on the touchscreen. On a single display, the game keeps its on-screen prompts.

Supply your own disc image. The game executable, textures, fonts, movies, menu sounds and soundtrack are read from that image. The download includes the Android app, runtime and on-device compiler. Before import, Fight Setup draws its own background. Once the disc is imported, the launcher reads the poster and boxer portrait from the disc on your device. The launcher plays the two *Ring Focus* tracks.

## Install

1. Copy your **North American original Xbox ISO** to the handheld. Allow about **11 GB of additional free space** for extraction and preparation.
2. Download the versioned **FightNight-Round3-Thor** APK from [GitHub Releases](https://github.com/Oxide-Shift/FightNight3OGX/releases) and install it. On Windows, if you have the matching USB installer, place it beside the APK, connect your Thor with USB debugging enabled, and double-click the installer.
3. Open **Fight Night Round 3**, tap **Choose Xbox ISO**, and select the image. Fight Setup verifies the executable, translates its functions and compiles the native game on the handheld. Keep Fight Setup open during preparation.
4. Tap **Enter the ring** when the build finishes. Game preparation and career saves live in the appÃ¢â‚¬â„¢s private storage.

## At ringside

- **Career** includes scouting, training and contract information on the lower screen when those screens appear in the game.
- **In a bout**, the second display shows judging and corner information. Between rounds it offers touchscreen cut treatment.
- **Achievements** opens from the in-game main menu. The gallery reads its artwork and navigation sounds from your imported disc. Tap a category, use left/right or the shoulder buttons to change tabs, and scroll to see more awards. On a dual-screen handheld, the category and Back prompts appear on the lower touchscreen. Press **B** to return. A short boxing bell sounds when a new award appears.
- **Fight Setup** includes graphics, frame pacing, controller rumble, and save import and export.

## Updates and game preparation

Fight Setup checks this repositoryÃ¢â‚¬â„¢s releases when it is open. A newer matching APK prompts you to download. Choose **Update game** after verification. Install over the existing app to keep the imported disc and career. You can also select **Check for updates** yourself.

Standard builds reuse a prepared game when the build kit is compatible. A runtime change can relink cached game objects. Changes to the translation or compiler inputs can require a full preparation.

A full preparation took **1 minute 41 seconds** on an AYN Thor Pro in a timed run. ISO extraction and device conditions can affect setup time.

A developer timing build deliberately performs an uncached preparation after each fresh app launch. It shows progress during tool extraction, function translation and compilation, then reports translation, compile-and-link and total time. Tap **Time another full build** to repeat the test with the same imported ISO.

[Build and test from source](docs/DEVELOPMENT.md)

Achievement bell adapted from *Boxing Bell 1.wav* by Benboncan. [Recording and license](third-party/BoxingBell.txt)
