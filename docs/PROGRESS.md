# Development and test history

## 0.4.0-alpha.45

Published the signed APK as the sole release download. The matching Windows installer is in the local Recomps folder. The public README shows the achievement cabinet, contract desk and Fight Store. The release tag points to `91924039bb08b3e9a75db8adc53f7c2d0d17aae6`. GitHub and the local APK report SHA-256 `7c6de8cd655ebc77c73d7152d6b4b4ca0f6f72101a581953203261b16bcb4498`.

The gallery and cutman desk use illustrated controller prompts. Achievement paging uses two quieter samples from the imported front-end bank. Training, contracts and Fight Store use their respective disc backgrounds. A compatible Alpha 44 preparation carries over.

Passed 15 Java and 15 native suites, scorecard and host-link checks, and the APK inventory audit. Android desktop renders covered training, store and contracts at two lower-display sizes and both cabinet layouts. Hands-on Thor feedback is pending.

## 0.4.0-alpha.31

Fight Setup shows 30, 40 and 60 FPS and the Low, Medium and High graphics presets on the main screen. The quality summary follows the selected tier and any edge-smoothing override. Resolution and aspect ratio remain visible. Advanced graphics holds the renderer and anti-aliasing controls. The installed version appears beside the title.

The launcher was rendered at 1920 x 1080 and 1280 x 720. An Android layout test exercised all six preset buttons, both legacy anti-aliasing overrides, renderer switching, saved choices after reopening and settings locks during a bout. The packaged ARM64 game library is byte-identical to alpha.30, whose Thor measurements follow below. Alpha.31 is version code 34. APK SHA-256 `7ee8bf843123744b6bbb5010da20a5ff174b46a39093ae85efccbd72fc985cf4`.

## 0.4.0-alpha.30

The private build specializes seven frequently drawn NV2A fragment shader configurations offline. Vulkan still selects by the full combiner state and coverage mode at pipeline creation. Changing textures, lighting inputs and other draw data remain dynamic. Each of the seven validated SPIR-V modules in the production Android and Windows builds matched the module tested on the Thor byte for byte.

At Windy City the AYN Thor produced 1,635 frame intervals in 27.28 seconds at 1080p High, with 29 punches thrown and 17 landed by the two boxers. The previous build completed 1,125 intervals in 27.12 seconds with 18 thrown and 13 landed. The p95 frame time fell from 26.245 to 16.700 ms, the p99 from 29.612 to 16.726 ms and the longest interval from 31.096 to 17.923 ms. The display settings in both tests were 1440 × 1080 rendered game frames, widescreen, 2× MSAA and 4× anisotropic filtering.

The measurements below are native host frame completions from the physical Adreno 740. Each sample used an isolated Gatti–Ward exhibition with a test-only venue unlock.

| Exhibition venue | Alpha.29 FPS | Alpha.30 FPS | Alpha.30 p95 | Alpha.30 p99 | Longest frame | Punches thrown / landed |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| Windy City Boxing Gym | 41.49 | 59.94 | 16.70 ms | 16.73 ms | 17.92 ms | 29 / 17 |
| Warehouse | 52.23 | 59.94 | 16.70 ms | 16.72 ms | 17.46 ms | 29 / 19 |
| Aragon Ballroom | 47.66 | 59.90 | 16.70 ms | 16.73 ms | 33.47 ms | 26 / 20 |
| Madison Square Garden | 46.12 | 59.91 | 16.70 ms | 16.73 ms | 31.70 ms | 25 / 15 |
| State Palace Theatre | 47.45 | 59.91 | 16.70 ms | 16.73 ms | 30.25 ms | 31 / 20 |
| Staples Center | 47.09 | 59.91 | 16.70 ms | 16.73 ms | 28.57 ms | 27 / 16 |

### Coach's corner and decision camera

A Windy City desktop run crossed the bell without skipping the corner. A 6.8-second coach close-up produced 59.88 publications per second with 407 consecutive frame pairs checked. Every pair changed, and every sampled frame was 1440 × 1080. A separate alpha.28 desktop coach sample produced 59.84 FPS with 418 changed consecutive pairs. Neither capture reproduced the reported handheld jitter.

In a separate Gatti–Ward exhibition, the private test shortened the round clocks and allowed the game to score a majority decision (116–114, 115–115, 117–112). The post-fight camera played without controller input. Across nine seconds of its opening, the desktop host published 59.88 frames per second at 1440 × 1080, and all 537 consecutive pairs changed. The game invoked its cinematic dispatcher, continued uploading boxer and ring geometry, and opened no movie file during the sequence. The disc's startup videos were opened earlier. Distinct complete frames do not establish an individual character's animation rate or physical display cadence.

The pause screen retains the preceding round break's upper-display frame reading. The result screen samples the post-fight opening for eight seconds, then preserves its last reading. Both use the existing frame counter to compare top-screen texture uploads with host publications. Measurements reset at the next bout.

The production ARM64 APK is version code 33 and uses the alpha.29 signing certificate. The game regression suite passed 13 Java suites, 13 native suites, the scorecard and host link with 36 menu and ring captures. The crash-report HTTP handler passed the same request assertions in memory. The sandbox blocked its loopback connection test. APK SHA-256 `9538960c2c3dfaead3851fc473e8f370cfcf8d9fc537fcd1cc56e045e79d13d1`.

## 0.4.0-alpha.29

Fight Setup offers 30, 40 and 60 FPS presentation targets. The 40 FPS option retains its cadence on 60 Hz panels. Career Central shows the purse for a signed bout, and the store and trainers use the career wallet. The lower screen reads the active career page and selected offer. All 32 page layouts rendered at two handheld sizes.

The Vulkan renderer can reject hidden fragments before shading eligible draws. When the game holds an upscaled frame, the presenter keeps its full-resolution image until the game draws again or writes a software overlay.

### Exhibition performance at 1080p High

Gatti and Ward fought in all six selectable venues with widescreen, 2× MSAA and 4× anisotropic filtering. Each timed sample lasted about 25 seconds on Windows and 27 seconds on the AYN Thor. Tests used a private exhibition save and a test-only venue unlock. The desktop column measures completed 1440 × 1080 frame publications on an i9-14900KF and RTX 4070 Ti SUPER. The Thor columns measure native host frame completions on the physical Adreno 740. Android top-screen presentation and career-mode frame times require separate measurements.

| Venue | PC FPS | Thor FPS | Thor p95 frame time | Thor longest frame | Punches thrown on Thor |
| --- | ---: | ---: | ---: | ---: | ---: |
| Warehouse | 59.95 | 52.23 | 22.88 ms | 71.21 ms | 26 |
| Windy City Boxing Gym | 59.95 | 41.49 | 26.24 ms | 31.10 ms | 18 |
| Aragon Ballroom | 59.95 | 47.66 | 22.49 ms | 66.16 ms | 25 |
| Madison Square Garden | 59.96 | 46.12 | 23.21 ms | 49.67 ms | 21 |
| State Palace Theatre | 59.97 | 47.45 | 24.13 ms | 41.53 ms | 25 |
| Staples Center | 59.95 | 47.09 | 23.30 ms | 82.51 ms | 25 |

In Windy City, 159 Thor frames around punch-counter changes had a 25.8 ms p95 interval. The other 966 frames had a 26.3 ms p95. At the end of that bout, the host reported a 20.0 ms p95 wait for completed Vulkan work during exchanges. A 60 FPS frame has 16.7 ms. In a separate Windy City desktop comparison, both alpha.28 and alpha.29 averaged 59.96 FPS and each produced 1,498 consecutive, pixel-distinct frame pairs.

A separate Windows prototype baked seven fragment shader configurations recorded during the Windy City bout. Its shader modules passed SPIR-V validation and rendered a 25-second exchange. These shaders are experimental and are outside the alpha.29 APK.

The game regressions passed 13 Java suites, 13 native suites, the scorecard and host-link checks with 36 menu and ring captures. The separate crash-report HTTP assertions passed in memory. The final sandbox run blocked the localhost socket connection. ARM64 version code 32 uses the alpha.28 signing certificate. APK SHA-256 `0338cdac3177f089eb23449cd929a957cf373dfdeb104e3222581f8c81433b2b`.

## 0.4.0-alpha.28

- The Thor recorded a crash in the native audio thread during a career knockdown. A delayed DirectSound idle notification for voice slot zero could follow a cleared owner pointer. The host now skips that notification. An isolated PC test took the invalid path with the original handler and passed 1,000 release and reuse cycles with the fix. A full career bout on this build remains unverified.
- Fight Setup offers a one-tap private crash report and retries approved reports after a failed submission. A PC child-process fixture verified the exit code, code location, round, render height and count event. Server tests covered invalid payloads, duplicate delivery and private digest access. The production digest was reachable over HTTPS.
- **Export save** writes the selected Fight Night slot in Xbox UDATA and TDATA layout. The upstream hakuX importer recognized a fixture from the exporter and restored its profile, title metadata and title data.
- The PC suite passed 13 Java suites, 13 native suites, scorecard and host-link checks with 36 captured frames. The ARM64 host was rebuilt from the reorganized source tree. Android version code 31. APK SHA-256 `90bb891e3d8dd33ef2bad42a5bfabf108dc62a70a9427b64ecd75de6b6a5a56e`.

## 0.4.0-alpha.27

- The Thor continued to send controller reports during a reported career freeze. The final active sample showed 361 received reports beyond the last virtual USB sample. Fight Night now reads the latest complete Android report when it polls a valid player-one handle. The native diagnostics record the last packet seen by each stage.
- An isolated PC test runs the translated retail input routine with a deliberately frozen USB report. It verifies a held block, release, direction reversal, other controller slots, failed reads and stack cleanup. The separate 30-minute simulated USB run delivered 448,680 reports. A full career bout on the updated Thor build remains unverified.
- Career Central checks the boxer's scheduled-bout flag before showing an opponent. It shows career cash and a compact record-based estimate for two experienced fighters. Menu-specific tips rotate, the song banner slides in and out, and the redundant navigation row is gone.
- The lower-screen splash begins at launch. Fight callouts enter from varying diagonals. Referee counts stay centered. With a single display, the original EA Trax banner and retail prompts remain on the game screen.
- The PC suite passed 13 Java suites, 12 native suites, scorecard and host-link checks, with 36 recorded frames. ARM64 version code 30. APK SHA-256 `84d6e8fb27cb6f1fdaaa70ac0311445a9083adc5ab7830101d3382460247ffc5`.

## 0.4.0-alpha.26

- Fight Setup downloads the selected GitHub APK directly and shows progress. It checks the release digest, package name, signing identity, and version before enabling **Update game**. A completed download survives a launcher restart. Android handles install permission and any confirmation it requires.
- The renderer reuses Xbox indices for point, line, triangle and triangle-strip draws. Specialized vertex shaders upload the constants their instructions can access. Indirect indexing retains the full register range. The 13 captured shader programs produced byte-identical SPIR-V, and the old and new index paths agreed across 380 cases.
- Windows and Android native builds passed. PC checks covered 13 Java suites, 12 native suites, the scorecard and controller link, 36 recorded frames, and shader register addressing. Updater fixtures covered damaged data, invalid packages and signers, offline recovery, duplicate taps, cancellation and reinstallation.
- A sustained 60 FPS at 900p across all six venues is still unverified for this version.

## 0.4.0-alpha.25

- Play Now restores the lower menu when the game returns from fight results, including when the finished round’s card still exists. A recognized menu dismisses the previous result for that bout. A rematch can display its own result.
- Manual healing has a larger, fixed wheel. Sweeps start anywhere on the treatment pad with the same thumb travel as before. The tool labels, guide and instructions stay in place while the player touches the screen.
- PC regressions passed 12 Java suites, 12 native suites, the scorecard and host link with 36 recorded frames. Replaying a captured Play Now menu after a simulated decision restored Select and Help with the round-three card still present. The rendered cutman pad was pixel-identical during touch, swipe and release at 620 × 540 and 1240 × 1080, while stick input registered. Physical handheld testing to date is limited to the AYN Thor. This build was verified on PC.
- ARM64 version code 28. APK SHA-256 `f7e5b51edcfc04e96083c0be16bcb80c4f4afa4106b788368b4f0c30982c9e32`.

## 0.4.0-alpha.24

- The lower clock uses the whole second formatted by the retail round HUD. An offline regression covers the 0:30 to 0:29 rollover, zero, changing rounds, and clearing the previous bout.
- The original Xbox menu badge is suppressed at its sprite draw. The lower-screen EA Trax banner retains its badge. Opening the EA Sports movie starts the new lower-screen slate, and the following movie clears it.
- The cutman footer uses centered stencil labels and three equal touch targets. Fight Setup checks for a new downloadable APK each time the launcher starts.
- The PC tests passed 12 Java suites, 12 native suites, the scorecard, and the host link, using 36 recorded frames. The Windows run captured the introduction and main menu. Live in-ring clock agreement and installation on a physical Thor are unverified.
- ARM64 version code 27. APK SHA-256 `b84ab43045454e1641bfd1830044822eea1e629c6d87f1befa514ca0cca6c5fc`.

## 0.4.0-alpha.23

- Career Central moves the verified **X Retire** caption to the touchscreen. The touch action rechecks the current caption before sending X. Boxer select still offers **X Ratings**.
- Fight Setup checks this repository's releases while the launcher is awake and idle. **Check for updates** also runs on demand. **Update now** opens the matching Thor APK in the Android browser. Alpha.23 uses version code 26 and the same signing certificate as alpha.22.
- Twelve Java and twelve native regression suites passed on PC, plus the scorecard and host link. The 36 recorded frames included Career Central. The Android interface was rendered on PC for five paired screenshots. Installing from GitHub on a physical Thor is pending.
- Local ARM64 APK SHA-256 `6776b4c56a581f9d89ba18eb4cf8a9f9cb06ffcf893713188345aaf6cffbe519`.

## 0.4.0-alpha.22

- The Android session follows the primary display, interactive and lock state, Activity lifecycle and an optional hinge-angle sensor. Sleep releases launcher music, rumble and AAudio output and stops both displays' updates and the session heartbeat. A blocking supervisor stops all Xbox threads and excludes the parked interval from the guest's monotonic clocks before continuing. Returning to setup also parks the fight. The save slot stays in place.
- Career Central now uses a deep red title strip, film-edge detail, warm stencil lettering and gold accents sampled by eye from the retail screen. Its navigation and game prompts occupy a fixed touch area. Four prompts switch to compact captions when space is tight.
- Checked the Android layout on PC from 310 to 620 logical pixels and at the Thor profile of 1240 by 1080 pixels at 320 dpi. At that profile the record, contextual note, music banner and six controls fit without scrolling. On short viewports only the central content can scroll.
- All eleven Java suites and twelve native suites passed, plus host-link and scorecard checks with 35 captured menu/ring frames. Additional PC tests exercised 82 simulated suspends, 80 resumes, 50 simulated AAudio route cycles, a transient reopen failure and parking before launch or exit. The full-system Android emulator could not boot here. Physical lid behavior, in-bout resume and standby battery drain still require hardware measurement.
- Signed ARM64 APK, version code 25. SHA-256 fc3b2fd4a3d3d6efa8fe6f5baa837ee031e0235fc7c49af2282b80b2cb3c0a5d.

## 0.4.0-alpha.21

- Fixed two independently reproducible controller failures in alpha.20. A report split across noncontiguous pages could complete without delivering the input. The USB interrupt worker could also restore an already acknowledged completion bit, preventing the guest from receiving subsequent reports. Corrected page traversal, synchronized interrupt status and unified the USB frame clock. Native input counts are included in the periodic Android timing log.
- Android forwards inputs to the native child on a dedicated writer thread. A blocked pipe no longer holds the Android input dispatcher or the host output readers. The PC regression exercised 50,000 input changes under backpressure and checked that the final neutral state reached the output.
- Career Central shows stencil-printed records, KO wins and the share of wins by knockout. A signed opponent appears next to the player on wide screens or below the player at narrow widths. Solo careers and debuts have separate presentations. Career setup and selections without a record show guidance.
- On PC, 448,680 controller reports matched byte for byte over 30 simulated minutes with 110 interrupt masking cycles. Ten Java and twelve native suites passed, together with scorecard and host-link checks, 35 captured menu/ring frames and a separate 73-snapshot regression. Android layout rendering covered 310, 360 and 620 pixels. Hardware confirmation of the round-three symptom is pending.
- Signed ARM64 APK, version code 24. SHA-256 489a7cd670644de7256bf9af5bf5947952f84a50d3724b63cee043e78d51c263.

## 0.4.0-alpha.20

- Restarting a live audio voice now preserves its neighbor links. The mixer bounds traversal to unique in-range voices in each list. Replaying the same voice-start sequence with two voices produced 256 visits in the old mixer and two in this build. All 4,096 precomputed gain settings matched the original formula. A separate test ran 60,000 restarts and relocations across all three lists.
- With the Thor's recorded 960-frame audio burst, a one-frame shortage followed by steady delivery previously silenced three additional callbacks (60 ms). The same sequence now resumes with the next callback. Sustained starvation still rebuilds the buffer. The Android timing log pairs audio-gap and device-underrun counts with punch work and GPU wait.
- Career Central follows the highlighted selection, supports touch navigation and shows the active career and contract records when available. Empty or invalid slots clear the scouting data. The lower display uses the imported stencil for prompts, music, corner treatment, numbers and judges. The 12-round totals size to their column width. Android layout rendering on PC checked the launcher, career and final card at 360 and 620 pixels with fixture records and a captured Career Central frame.
- The PC regression run passed nine Java and twelve native suites plus scorecard and host-link checks. It included 35 recorded menu and ringside frames from all six venues, 73 retail memory snapshots in a separate replay, and an additional save import round trip. Physical punch timing, sustained 1080p frame rate and coach-camera movement still require a handheld playtest. Version code 23 retains the alpha.19 signing certificate. APK SHA-256 is 83c4c2a311ba4ee762efd232bac06318bb3b13ec86d98728a2132a2c39c97757.

## 0.4.0-alpha.19

- Fighter select and the main menu route the visible Y Help prompt to the touch display. The footer reader handles a partially covered menu ribbon and subpixel changes at 900 lines. The original badge colors matched the Android-rendered EA Trax emblem at three banner widths.
- The fight-end desk draws the matchup, official result, punches landed and thrown, and the three judges' cards using native text. It retains the completed bout after the ring objects go away. Fight Totals also uses native data. The lower display's last live screenshot-copy path has been removed.
- The lower display follows the retail referee count, landed haymakers, player-credited staggers, knockdowns, stoppages and outcomes. Counts 1 through 7 use one pulse and 8 through 10 use two. The launcher offers Off, Light, Medium and Full. Stagger attribution uses the mat stumble cue and a recent unambiguous landed punch.
- Input resets on loss of focus or removal of a device. Independent devices, hats and digital triggers retain their own state. A delayed USB acknowledgement no longer terminates the interrupt worker. The original worker exited on prolonged suspension and after a stalled acknowledgement. Both scenarios recovered on the patched worker, and 4,096 reports per scenario matched on both Xbox input endpoints.
- NTSC presentation starts a fresh frame interval following a material overrun. The old schedule could follow a 14 ms delay with a 2.7 ms interval. Medium + smooth edges retains Medium texture filtering, adds two-sample antialiasing, and performs the final scale at display resolution. The new setting and the coach scene still need physical frame-time measurements.
- The offline regression run passed 9 Java suites, 11 native suites, the scorecard and host-link checks, and 35 menu and ringside captures including all six venues. Separate regressions replayed 73 retail memory snapshots and 60,000 audio voice updates. This build received no fresh live-bout or handheld benchmark. Android ARM64 version code 22 uses the alpha.18 signing certificate. APK SHA-256 is `6a4a9aae4dd6be0d9ca4a569f20c5a0b5fdae39774d9c236ce6695be710aca75`.

## 0.4.0-alpha.18

- In menus the lower display reads the highlighted item and shows original corner advice. Twenty menu entries have specific notes. No upper-screen preview is copied into the lower menu.
- The lower EA Trax strip uses the disc's EA emblem and TRAX wordmark in amber and brass. On the title screen the PC renderer confirmed that it suppressed both the 64-pixel badge and the 16-pixel wordmark when the lower display owned the song.
- The retail healing chooser stays on the upper display. Entering manual treatment opens a full-height cutman desk with a floating right-stick sweep and three touch targets for side and tool selection. Releasing, cancelling, leaving treatment and losing focus release the virtual stick. Recorded chooser and manual frames routed correctly at 480, 720 and 1080 lines in both aspect modes.
- The save importer recognizes six hakuX export layouts. A test career and profile were imported byte-for-byte and the saved Gatti–Medez contract opened on PC.
- The alpha.16 Thor crash record identified a SIGSEGV in the retail streaming-audio list walker at guest address 0x002C4235. The patched walker preserves the current voice and call frame across the stream update and then reads its updated link. A regression test injected the bad return state, reproduced the original invalid read and completed 60,000 voice updates on the patched code. The original source of the corrupted return state remains unconfirmed.
- The pre-fix PC exhibition reached the end of round three. The longer patched PC career and exhibition runs stopped before completion. The signed ARM64 package matches the previous upgrade certificate and both packaged native binaries match their release files. SHA-256 is `e4f21ee54c398808571e01359769831d9b803d31a3181a0e8be3de872db8381c`.

## 0.4.0-alpha.15

- High runs at 1440 × 1080 with its original 2× MSAA, 4× anisotropic filtering and Android presentation sharpening. The Vulkan renderer can resolve the multisampled color attachment inside the scene pass. The Xbox command processor batches inline vertex data, indices and shader uploads. Repeated draws can reuse a matching pipeline key. The ARM64 texture decoder reads both rows of each complete 4 × 2 Morton tile together. In 60 FPS mode the top display wakes on frame publication.
- A 25-second Gatti–Ward punching exchange at the Warehouse exercised the Android three-slot frame transport using the Windows Vulkan release host. Alpha 14 produced 59.97 FPS and alpha 15 produced 59.94 FPS. Both had a 17.68 ms 95th-percentile publication interval. The 99th percentiles were 18.11 and 18.27 ms respectively. Across 27 rolling punch reports per run, the median of the reported 95th-percentile punch work fell from 14.15 to 13.10 ms. The corresponding GPU fence wait fell from 8.81 to 8.09 ms. Each fight used the same inputs and the boxers could react differently.
- A second pair of 25-second tests used the legacy desktop capture surface. Distinct observed frames rose from 54.56 to 56.60 per second. The 95th-percentile observed interval fell from 35.52 to 33.19 ms, and the 99th from 39.81 to 36.02 ms. Neither desktop test measured Android composition, sharpening or physical Thor frame times.
- A separate paired-renderer test sent the same game state and draw calls to both Vulkan backends through the menus and a live punching exchange. All 4,560 compared Xbox-size frames matched byte for byte, as did all 190 sampled 1440 × 1080 frames. The check covered 635,435 indexed draw submissions. Separate tests covered split push buffers, bulk shader state against scalar writes and 80 texture dimensions.
- In setup, the second display shows only “Choose your settings and launch the game.” The game omits the two EA Trax badge sprites while the lower music card is visible. A retail title-screen test switched from lower-screen music to single-screen and back, and observed the native badge disappear, return and disappear again. Fighter-select caption tests also passed with the badge erased.
- Signed ARM64 upgrade with versionCode 18. Both native binaries match the release build, and the signing certificate matches alpha 14. SHA-256 is 387ab87afeadf688a1b3bdb3c66f2f3db430ca45a5f641e4c32a631a95c6f648.

## 0.4.0-alpha.13

- During a paired 480p Gatti-Ward punching exchange at the Warehouse, Vulkan rendered all 1,920 consecutive frames identically to alpha 12 across 386,629 indexed draws. Independent texture uploads now run before the main draw. The same frame went from five render passes to one. Ten common shader configurations warm up at startup. Medium keeps its resolution, filtering and effects.
- The Xbox vertex-program decoder preserves instructions that the game rewrites with the same value. Across 360,000 shader exports during active punching, it spent 60.7% fewer measured desktop CPU cycles in that stage and emitted identical instructions and inputs.
- In separate 25-second 480p Medium desktop tests with repeated punches, alpha 12 presented 59.34 FPS (95th percentile 27.10 ms, 99th 33.61 ms). The signed build's corresponding desktop host presented 59.80 FPS (95th 26.84 ms, 99th 30.17 ms). A second test at an internal 960 x 720 presented 59.93 FPS, with a 25.95 ms 95th percentile and 28.37 ms 99th percentile. An earlier 720p run stalled before the bout and produced no frame-time sample. These are distinct changes in the Windows capture surface. Its slowest frames still exceeded the 16.7 ms frame budget. The Windows Android debugging service could not open its event socket for this release, so a physical Thor lock remains unverified.
- The lower-screen fighter panels read the selected roster entries, native bout clock, health and stamina. A retail pause held both fighters' readings at 0:47. Blue and red punch totals read the same cumulative landed and thrown counters as the retail Fight Totals screen. Arturo Gatti's roster supplied 39 wins, 7 losses, 0 draws and 30 knockout wins. Micky Ward's supplied 38, 13, 0 and 27. The roster supplies a single knockout-win count. The career display derives each fighter's bout count by adding wins, losses and draws.
- Across 53 snapshots of one retail round, all six official score slots were empty as late as 0.78 seconds before the bell. At 0:00 all three judges wrote 9-10. The desk displays the official round and cumulative score for each judge as they are posted. A native replay of those snapshots and 20 corner snapshots also verified the recovery timer from 30 seconds to zero.
- The actual EA Trax announcement supplies localized song and artist text. The dual-display route handled “Never Gonna Get It” by Sean Biggs featuring Akon & Topic. The same event leaves the original upper-screen animation available in single-display play.
- Signed ARM64 upgrade, versionCode 16. Certificate matches alpha 12, both native libraries match the release build and the APK passes archive integrity checks. SHA-256 is `631bb894d9cde7db87a2922bae645f744ea914b8440df4d024d8a01afaff0d09`.

## 0.4.0-alpha.12

- Instrumenting a Warehouse bout exposed repeated uploads of 256 × 512 color textures, reaching 480 updates in a 120-frame window. The Vulkan host now uploads tightly packed linear textures directly, unpacks swizzled color textures into reusable storage, and reuses compatible GPU images after their in-flight references retire. The independent texture-layout test covers 80 rectangular power-of-two sizes.
- A paired Windows Vulkan run sent the same game state, punch inputs and draws to alpha 11 and alpha 12. During a Gatti–Ward Warehouse exchange, all 1,920 consecutive 640 × 480 frames matched byte for byte across 386,087 indexed draws. CPU cycles spent preparing and uploading the two targeted color formats fell 90.74% in that window. This measures a renderer stage on the desktop. Physical Thor frame timing remains unmeasured.
- Boxer nameplates read both selected roster entries and draw text at the lower display's resolution. Selecting a different fighter changed Arturo Gatti to Ray Leonard opposite Micky Ward. Headings and names use the original Gunplay stencil. The corners use Eraser brush lettering and the header adds a sprayed paint edge. The fonts come from the imported disc.
- Arena walkouts and fades previously triggered menu layouts. The desk now checks the game's round state, visible gauges, clock and menu controls before changing layouts. On widescreen captures, 124 walkout samples that previously triggered a menu stayed in the ringside view, and all 119 sampled punch-exchange frames selected the round view. Additional tests cover retail pause, cutman, punch totals, highlights, HUD dropouts, and footer prompts at 480p, 720p and 1080p.
- Signed ARM64 upgrade, versionCode 15. The signing certificate matches alpha 11 and the packaged native binaries match the release build. APK SHA-256 is `8fe490217c0a5bc13bca04fda58ac3718b3815709305f2207cb3c39d8872432b`.

## 0.4.0-alpha.11

- Fighter-select commands are native touch controls. Each uses an Xbox button color, the disc fonts and a checked retail caption. The upper display hides only the commands shown downstairs. Three captured fighter-select poses matched all four captions at 480p, 720p and 1080p. Erasing the Ratings caption in each fixture left X on the upper display. A short transition delay protects the lower layout during fades.
- The lower display uses the disc's still ring artwork, a compact header, and a menu-specific EA Trax panel. During a round it shows both fighters' meters, the clock and posted judges' scores. The pause view reports average FPS and 95th-percentile frame time over up to 180 recent in-round presentation intervals.
- The native renderer verifies vertex and index contents before reusing a prepared mesh. The Vulkan backend can retain that geometry in each in-flight buffer and skip unchanged binding and constant updates. A regression test changes vertex data, immediate values and index contents while retaining the same buffer addresses.
- In the first Gatti-Ward round at the Warehouse on the Windows Vulkan host, 700,000 indexed draws avoided 2,424,074,084 of 5,841,338,570 geometry-upload bytes (41.5%). The renderer issued 212,382 descriptor pushes, down 69.7%. In a paired 480p render, 3,600 consecutive frames matched the alpha 10 Vulkan output pixel for pixel before the judges posted the round. An independent check against the original remapping and packing path compared 14,468 sampled draws and 1,846,244 vertices. All index lists and packed vertex bytes matched. This stage used 39.6% fewer measured CPU cycles in that desktop run. The uninstrumented release host presented 935 distinct frame changes in a separate 15.99-second window during the round (58.47 FPS, 18.42 ms at the 95th percentile).
- ARM64 and x86_64 Android APKs built and passed signature verification. The handheld build has versionCode 14, matches the alpha 10 signing certificate and contains the tested release sources compiled for ARM64. Its SHA-256 is `af4145b1eadf60bca829c7ea0d18fcfa1a4fc4dc9c1f1ddb66459bf7698e53db`. The Windows Android debugging daemon failed to create its event socket, so this build's physical Thor frame rate and on-device UI remain unmeasured.

## 0.4.0-alpha.10

- The launcher reads the original gold Round 3 title image and the EA05_Title and EA05_Body typefaces from the imported disc. The settings panel groups graphics, resolution, renderer and aspect ratio beside the primary launch or resume button. The ringside desk uses the same fonts. Both original images matched an independent decoder at every pixel, and both fonts contained the tested labels and score digits.
- The automatic desk shows each judge's posted round score and cumulative total. In the Gatti–Ward Warehouse bout all three judges posted 9–10 for round one and again for round two. In the final release-host run they awarded round one 10–9 to the opposite corner. Fighter gauges estimate the visible Xbox meters. The same fight capture returned 100% / 91% for blue health / stamina and 77% / 97% for red at 480p, 720p and 1080p. The soundtrack archive read for SEANBIG1 matched the on-screen EA Trax title “Never Gonna Get It.” The lower D-pad and scorecard page switch have been removed.
- The Vulkan renderer streams indexed Xbox triangle strips as 16-bit indices, caches repeated draw state and gathers active vertex streams in batches. Across a game trace, the strip indices required 969,802,752 bytes compared with 5,767,919,520 bytes for the expanded lists. A sampled comparison produced identical input bytes for 2,131,298 vertices. In a separate desktop Warehouse run, alternating old and new packing order over 19,070 sampled draws (2,542,093 vertices) reduced packing time by 66% as measured with the same CPU timestamp counter. This compares the packing step only.
- The final Windows host presented 935 distinct frame changes over 15.98 seconds of a 480p Warehouse round, averaging 58.50 fps with a 95th-percentile frame interval of 18.76 ms. Alpha 9 measured 58.44 fps in its earlier 480p Warehouse sample. At Low and Medium, Android sizes the final presentation surface to the selected render height. At 480p on the Thor’s 1920 × 1080 panel this requests 853 × 480 postprocessing pixels. The High preset retains full-resolution sharpening. Physical Thor frame rate, handheld rendering artifacts and the redesigned Android layouts still need on-device verification. The Android debugging service could not start for this build’s physical or emulator launch.
- ARM64 and x86_64 Android APKs built and verified. The installable ARM64 update has versionCode 13, matches the alpha 9 package name and signing certificate, and contains the release ARM64 host. APK SHA-256 is `f3b7367c1946dd83b70a9654fcb3f4cbc73010653c2a17f23ba348cd133205d8`.

## 0.4.0-alpha.9

- The synchronous Xbox GPU reader now follows the ring's jump and carries unfinished command packets across submissions. The previous reader inferred the ring bounds from the submission addresses it had seen. At wraparound it sometimes lost part of a boxer's bone matrix. A synthetic split-packet and wraparound regression checks every value and destination register using the release command parser.
- In two fighter-selection runs sampled over the same frame-number range at 640 × 480, alpha 8 had six draws with stretched triangles among 3,248 sampled boxer draws. One triangle had an edge over 10,227 pixels. Alpha 9 had zero among 3,024 sampled boxer draws, with a longest measured edge of 56.3 pixels. This comparison includes nondegenerate triangles with a visible vertex and all three vertices in front of the camera. Thin vertical lines in the fighter-selection artwork remain visible.
- The renderer accepts strips of up to 65,536 references. A captured Warehouse strip submitted 4,533 references, exceeding the old 4,096-reference limit.
- The release Windows host entered the Gatti–Ward Warehouse exhibition. During the last 16 seconds of round one, 934 distinct frame changes averaged 58.44 fps on the desktop, with a 95th-percentile interval of 18.56 ms. Both Android hosts compiled. The signed ARM64 update has versionCode 12, matches the alpha 8 signing certificate and contains the verified release host. APK SHA-256 is `451834ed20ea490f759fa49ec07d75efcbb33576648e8a1a6dd99792a88472c4`. Physical Thor frame rate and this build's Android playback are unverified.

## 0.4.0-alpha.8

- The virtual Thor runs Android 13 with separate 1920 x 1080 and 1240 x 1080 displays. The x86_64 test build uses the same Xbox translation, renderer and Android UI as the ARM64 handheld build. The virtual test imported 901 game files, created a profile, upgraded the APK in place and reused the profile and installed disc. Gatti and Ward loaded into the Warehouse using the lower-screen Play Now, fighter and venue controls. On alpha 8 the first-round clock counted from 3:00 to 2:58 on both displays. The lower display showed both named boxer meters automatically. Tapping Pause opened the retail pause menu and switched the desk to Resume, Controller, Settings and Fight totals. The x86_64 virtual session ran visibly below real time. On an earlier virtual run, navigating the paused walkout required repeated taps, and the Resume shortcut did not reliably leave that overlay.
- The runtime sets both Xbox video configuration paths for retail widescreen. Captures confirm the game centres its 4:3 menu artwork inside the wider scene. The lower screen samples the game's actual controls, clock and fighter panels, and the primary screen receives full frames only at completed guest flips.
- The game can add the physical-memory alias to an Xbox ADPCM buffer address. Following that alias fixes the five retail menu effects tested. All 68,976 bytes across the five test effects matched the disc bank. A second trace of the release address calculation captured four of those effects and matched all 62,820 bytes. A separate 151.91-second desktop stereo mix peaked at 26,524 on a signed 16-bit scale and contained zero full-scale samples. Speaker playback and performance on the physical Thor remain unmeasured.
- In the desktop 480p Vulkan exhibition on a Core i9-14900KF and RTX 4070 Ti SUPER, 1,170 distinct frames arrived in 20.03 seconds (58.4 fps). The median frame interval was 16.75 ms and the 95th percentile was 27.22 ms. Frame-publication, scorecard and menu/round recognition tests passed. Some fighter animations still show stretched geometry.
- Alpha 8 replaces the stretched-column fill behind relocated EA Trax cards with a cached strip from a matching retail menu. The GPU regression restored a composited card to the original background pixel for pixel. With routing disabled, the shader preserved the entire card. Java tests checked scene mismatch and frame-buffer reuse at 480p, 720p and 1080p.
- The signed ARM64 APK is 13,268,195 bytes. Its SHA-256 is `cdf817275eef26d49b151c630f6ba2a4b3d9212251fa04a9713e1d3ab351ecbc`. A copy read back from the handheld's Download folder matched.

## 0.3.0-alpha.1

The Vulkan backend ran the Gatti–Ward warehouse exhibition on a Windows RTX 4070 Ti SUPER. In a 20-second window beginning when the first-round nameplates appeared, the 0.2 build displayed 309 distinct frame changes (15.43 fps) and the Vulkan build displayed 1,172 (58.58 fps). Median intervals were 66.45 ms and 16.66 ms respectively. The 95th-percentile intervals were 70.66 ms and 26.60 ms. Both tests used the same 480p scene, save settings, capture workload and input route. The timed runs were sequential. The frame rate is capped at Xbox NTSC refresh.

At 720p a separate desktop run accepted stick and button input, showed the end-of-round highlights and corner, and entered round two. The shared Android presentation format produced at least eight distinct nonblank frames at each of 640 × 480, 960 × 720 and 1440 × 1080 on the desktop GPU. Physical Thor frame rate and speaker quality are unmeasured. Windows enumerated the authorized USB interface, but this work session could not open it.

The Android audio callback now reserves about 53 ms before starting, can increase the reserve after a starvation, fades at empty boundaries, and crossfades after overflow. Native tests cover those paths and ring-counter wrap. The session page reports mixer gaps, discarded buffers and AAudio device xruns. A separate desktop test verified the disc poster's decoded RGB pixels against a reference and exercised the saved scorecard and runtime heartbeat.

## Earlier bring-up

The Windows native host renders the retail intro, accepts a controller, creates an Xbox profile on disk, and reloads it on a fresh boot. The exhibition test navigated **Play Now → Select corner → Select boxer → Select venue** with Xbox input packets. Gatti and Ward entered the Warehouse ring. The game credited the player controlling Gatti with **4 of 7 jabs landed**. Ward landed both of his punches. The test opened Fight Totals from the pause menu and resumed the fight. Some animation frames show stretched fighter geometry.

| Check | Result |
| --- | --- |
| Supported original Xbox disc | North American release, certificate version 2, title ID `45410086`. The 5,750,784-byte executable has SHA-256 `cfaf2d2c1c338a6e4e7135466c8dcb80fdf7da63682e71cfecdcba65b3682064`. |
| Import integrity | The original ISO and installed ISO copy had identical SHA-256 digests before and after import. The original modification time was preserved. The activity's importer installed 901 files totaling 2,403,279,877 bytes. |
| Save files | The ZIP importer accepted a title-specific save, rejected a traversal entry, and preserved the previous slot. The guest created and reloaded its own profile through the Xbox save APIs in a separate desktop test. |
| Controller and video | Physical input is mapped to Xbox Controller S reports. The desktop test supplied timed reports through the same native pipe used by Android. The renderer produced 640 × 480 RGBA frames from the game, including the loading animation, EA Sports logo, menus and fighter models. |
| Corner display | Scorecard tests cover saved totals, reload, undo, the 12-round limit and reset. The Java session test saw 150/150 resolved kernel imports, a live heartbeat and multiple guest-submitted frames. |
| Android package | The native host links against the Android ARM64 API 30 runtime and the SDK verifies the private APK's v3 signature. Physical display routing, frame pacing, sound and a completed bout on Android still require device testing. |
| Audio | Desktop testing captured the 48 kHz stereo mix privately with the host's playback muted. Startup conditioning suppresses the first 750 ms after signal onset and fades up over the next 500 ms. A native regression test checks the gain envelope and bit-exact output afterward. Android uses a buffered AAudio callback with short fades when recovering from underruns. |
| Round transition | The game saved and reloaded Easy difficulty and one-minute rounds. Gatti and Ward completed round one at the Warehouse. The round highlights played and the controller selected Auto Heal in the corner. Round two started at 1:00. Fight Totals showed Gatti had landed four of five jabs in this run. |
| Desktop frame rate | One 30-second in-ring capture recorded 713 distinct frames, or 23.8 FPS. The sample used the software renderer on Windows. Thor frame rate still needs a device measurement. |

## Runtime work

The two toolkit patches cover 16-bit POP and the guest frame restored by CRT longjmp. The latter was exercised by the retail loader probing a TGA file as PNG. Functions in `observed_functions.json` supplement the original Xbox recompiler's recovered entry points. The DSP hook registers the guest's DirectSound command word with the toolkit, using the address from the guest's live effects object.

An earlier run reached a knockdown and stopped when the guest called its recovery routine. The latest build includes that routine. Completing the get-up sequence still needs a dedicated test.

The 0.2 build used a software rasterizer and unoptimized guest code. Version 0.3 uses Vulkan for the main game surface and optimized ARM64 guest code, with software rendering available in setup. A universal binary that builds the title-specific native code on the handheld and a validated 120 fps simulation remain open.

## 0.4 Thor test

| Windows validation | 0.3 | 0.4 |
| --- | ---: | ---: |
| Gatti–Ward at the Warehouse, distinct frames per second over 20 seconds at 480p | 58.10 | 58.68 |
| Median GPU time across eight 120-frame fight windows | 2.30 ms | 0.71 ms |
| 95th percentile interval between distinct frames | 24.07 ms | 25.45 ms |

The host used an i9-14900KF and RTX 4070 Ti SUPER. Capture counted changes to completed frames. The game clock runs at 59.94 Hz. The Android driver and handheld frame rate still need a device measurement.

The GPU path retains shared mesh vertices, applies Xbox face culling, accepts compressed DXT textures and their original mip levels when supported, and puts pre-transformed overlays in draw order with the fighters. The personal build specializes 13 observed vertex programs. A 720p run delivered a 960 × 720 fight frame through the Android-format mailbox.

An unattended bout paused, moved the selection, resumed, completed round one and reached live punches in round two. One earlier Windows profiling run exited with an access violation. Isolated geometry artifacts still appear in some fight frames. The device's menu-effect sound quality needs another listening test. The voice-rate regression covers changing sample rates and short stereo tails. A captured pause-menu segment contained 48 kHz stereo PCM without clipped samples.

The lower display reads the user's game frames. Archived main menu, boxer selection, two different pause selections, rounds one and two, recovery and broadcast frames exercised the scene detector. The Android window requests immersive fullscreen on both displays. Auto widescreen uses the active 16:9 area in letterboxed fights and switches to the full 4:3 canvas for pause menus.

## 0.4.0-alpha.2

The launcher waits for its window to attach before entering fullscreen. The app uses an adaptive red-glove icon and opens the secondary scorecard on demand. The new corner desk shows live bout photography, boxer meters, punch totals, injury readouts and the game's EA Trax announcement. Touch controls feed the same Xbox pad packet as the handheld controls. Pause shortcuts follow the visible cursor and stop when the game leaves the pause menu.

Desktop tests checked cue routing against nine retail captures at 480p and scaled 720p and 1080p. The presentation shaders compiled and linked with sharpening both on and off. Five offscreen draws checked unmodified single-screen output, the two reframed camera views, the printed menu ribbon and the pause selection glyph. Manual Heal reached the cutman in a native desktop bout. Android startup and touch routing on the Thor still need a device check.

## 0.4.0-alpha.3

The pause menu briefly ignores input while it opens. Shortcuts now allow the overlay to settle and retry a direction only while the same row remains selected. In a live Gatti–Ward bout the corner desk's controller opened Pause, navigated to Fight Totals, opened the actual stat sheet and resumed play. The same route also passed from an already-paused bout.
