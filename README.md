# Fight Night Round 3 for Android

Fight Gatti–Ward on the top screen and work the corner on the touchscreen. **FightNight3OGX** brings the original Xbox version of *Fight Night Round 3* to ARM64 Android. Import your own disc, pick a boxer and build a career. On a dual-screen handheld like the AYN Thor, you can glance down to see who is landing the cleaner shots, then take over as cutman when the bell rings.

**[Download the Android APK and Windows USB installer](https://github.com/Oxide-Shift/FightNight3OGX/releases)**

| In the ring | Between rounds | In training camp |
| --- | --- | --- |
| [![Gatti and Ward trading punches above a live corner card](https://github.com/user-attachments/assets/90765b19-7ffa-48f5-ae24-0e335ce2af6a)](https://github.com/user-attachments/assets/90765b19-7ffa-48f5-ae24-0e335ce2af6a) | [![Between-round treatment above the cutman touchscreen controls](https://github.com/user-attachments/assets/6eede393-5f04-48f6-b687-21863533cab5)](https://github.com/user-attachments/assets/6eede393-5f04-48f6-b687-21863533cab5) | [![Heavy bag training above a breakdown of potential stat changes](https://github.com/user-attachments/assets/8f05b173-4967-4bf2-8672-9a1fee44b1f7)](https://github.com/user-attachments/assets/8f05b173-4967-4bf2-8672-9a1fee44b1f7) |
| Follow punches landed and thrown for both fighters. | Choose a side and sweep the touchscreen to treat a cut. | See what the heavy bag can add to your boxer’s ratings. |

## At ringside

- **60 FPS target, 1080p and High graphics.** Fight Setup lets you select all three with the Vulkan renderer. High sharpens the image and uses 4× texture filtering. Enable 2× MSAA to smooth the edges, and toggle **VSync** to time frame presentation to the display. Actual frame rate depends on the device and the action in the ring.
- **A second screen for your corner.** Check health, stamina and punch totals during the round. Read the judges’ cards as scores come in. In career mode, swipe through contract offers, compare the purse and the opponent’s strengths, then check your gains after training. Single-screen handhelds keep the game’s prompts on the main display.
- **60 achievements across seven categories.** Get dropped and still win to earn *Back from the canvas*, or take a career boxer to 10 wins without a loss for *Unblemished*. The gallery also tracks the eight classic Xbox 360 career awards worth 1,000 G locally. Open **Achievements** from the in-game main menu to see what you’ve earned and what to chase next. A boxing bell marks each unlock.
- **Controller and touchscreen support.** Use the shoulder buttons to browse achievement categories or tap the prompts on the lower screen. Fight Setup also includes controller rumble and save import and export.

## Install

1. Copy your **North American original Xbox ISO** to the handheld. Allow about **11 GB of additional free space** for extraction and preparation.
2. Install the versioned **FightNight-Round3-Thor** APK from the release page. For a Windows USB install, put the matching installer beside the APK, connect your Thor with USB debugging enabled, and double-click the installer.
3. Open **Fight Night Round 3**, tap **Choose Xbox ISO**, and select your image. Keep Fight Setup open while it verifies the executable, translates the Xbox functions and compiles the game on the handheld.
4. Tap **Enter the ring**. The prepared game and career saves live in the app’s private storage.

The download contains the Android app, runtime and compiler. The executable, textures, fonts, movies, in-game sounds and soundtrack come from your disc. Fight Setup draws its own background before import, then reads the poster and boxer portrait from the disc. The launcher plays the two *Ring Focus* tracks.

## Updates and game preparation

Fight Setup checks this repository’s releases when it is open. A newer matching APK prompts you to download. Choose **Update game** after verification. Install over the existing app to keep the imported disc and career. You can also select **Check for updates** yourself.

Standard builds reuse a prepared game when the build kit is compatible. A runtime change can relink cached game objects. Changes to the translation or compiler inputs can require a full preparation.

A full preparation took **1 minute 41 seconds** on an AYN Thor Pro in a timed run. ISO extraction and device conditions can affect setup time.

A developer timing build deliberately performs an uncached preparation after each fresh app launch. It shows progress during tool extraction, function translation and compilation, then reports translation, compile-and-link and total time. Tap **Time another full build** to repeat the test with the same imported ISO.

[Build and test from source](docs/DEVELOPMENT.md)

Achievement bell adapted from *Boxing Bell 1.wav* by Benboncan. [Recording and license](third-party/BoxingBell.txt)
