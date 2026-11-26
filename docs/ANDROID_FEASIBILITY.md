# Handheld test

The Android launcher requires a verified Xbox disc before it enables **Launch game**. A personal APK can exercise these checks on an ARM64 device with Android 11 or newer.

| Check | Expected observation |
| --- | --- |
| Setup | **Fight setup** opens on the primary screen. Graphics, resolution and music choices persist after relaunch. The original instrumental loops in setup. |
| Import | **Choose Xbox ISO** opens the document picker. Copy and install progress appears. The original ISO remains in its original location. Reserve 10.3 GB on the device for the tested full-size image. |
| Save | **Import Xbox save** selects a ZIP for title ID `45410086`. After restart the selected slot is retained. **Use default save** selects the default slot. Reading a real career save still needs a gameplay test. |
| Secondary display | **Corner card** appears on the secondary presentation display. Touch scores a round and **Undo** reverses it. The app offers the card on the primary display when only one display is available. |
| Native session | **Launch game** starts the ARM64 host. Runtime status should report kernel imports, guest threads and video submissions. The Windows test reached Gatti vs. Ward in the Warehouse ring and advanced the round clock. |
| Playability | Reach the retail main menu, enter an exhibition, use both sticks to box, complete a round, and verify game audio. This is the outstanding acceptance test. |

The **Runtime** tab measures a live connection to the host. Frame submissions and the guest's last reported event show game progress independently of that connection.
