# Chain Reaction (Android)

Two-phone Bluetooth (or pass-and-play) Chain Reaction, Kotlin + Jetpack Compose.

## Build & install
```bash
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug                       # -> app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                   # game-rule tests
```
Install on **both** phones (USB debugging on, or copy the APK over and open it).

## Playing over Bluetooth
1. Both phones: Bluetooth on, open the app → *Play over Bluetooth*, allow "Nearby devices".
2. Phone A: **Host a game** (board is 13 rows × 6 columns).
3. Phone B: pick A from *Paired* or tap **Scan for nearby phones**. Accept the pairing prompt if one shows.
4. Host moves first; the starter alternates each round.
On Android 11 and older, Location must also be switched on for scanning.

## Code map
- `game/GameEngine.kt` – rules; returns every explosion *wave* so the UI can animate it.
- `bt/BtLink.kt` – RFCOMM host/join, line-based messages.
- `GameViewModel.kt` – screens, turns, protocol (`HELLO`, `MOVE`, `RESTART`, `BYE`).
- `ui/BoardView.kt` – board drawing + explosion animation (flash, shock ring, sparks, flying orbs, screen shake).
