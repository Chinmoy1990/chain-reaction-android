# Chain Reaction (Android)

A chain-reaction orb strategy game for Android. Play with a friend on **two phones over Bluetooth**, or pass one phone around for **2–8 players**. Built with Kotlin + Jetpack Compose.

## Download

**[⬇ Download the latest APK](https://github.com/Chinmoy1990/chain-reaction-android/releases/latest)** (Android 8.0+)

1. Open the link on your phone and download the `.apk` file under **Assets**.
2. Open it and allow installing from your browser / Files app when asked.
3. Play Protect may warn about an unknown app (it's a test build, not from the Play Store) → **Install anyway**.

New versions install over the old one — no need to uninstall. For Bluetooth games, both phones should run the same version.

## How to play
- Tap an empty cell or one of your own to add an orb.
- A cell bursts when it's full — **2** orbs in a corner, **3** on an edge, **4** in the middle — sending one orb to each neighbour and capturing it. Bursts can set off chain reactions.
- Wipe out every other colour to win.

## Playing over Bluetooth
1. Both phones: Bluetooth on, open the app → *Play over Bluetooth*, allow "Nearby devices".
2. Phone A: **Host a game** (board is 13 rows × 6 columns).
3. Phone B: pick A from *Paired* or tap **Scan for nearby phones**. Accept the pairing prompt if one shows.
4. Host moves first; the starter alternates each round.

On Android 11 and older, Location must also be switched on for scanning.

## Build from source
```bash
export ANDROID_HOME=~/Android/Sdk
./gradlew assembleDebug                       # -> app/build/outputs/apk/debug/app-debug.apk
~/Android/Sdk/platform-tools/adb install -r app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest                   # game-rule and timing tests
python3 tools/make_sounds.py                  # regenerate res/raw/pop.wav
```

## Code map
- `game/GameEngine.kt` – rules; returns every explosion *wave* so the UI can animate it.
- `bt/BtLink.kt` – Bluetooth RFCOMM host/join, line-based messages.
- `GameViewModel.kt` – screens, turns, protocol (`HELLO`, `MOVE`, `RESTART`, `BYE`).
- `ui/BoardView.kt` – board drawing, tumbling orb clusters and the gliding explosion animation (long chains are capped at ~6 s).
- `Sounds.kt` + `tools/make_sounds.py` – the synthesized pop sound and mute setting.
