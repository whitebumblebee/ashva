# Install Ashva on your Android phone from the MacBook

Written for a OnePlus Nord CE 4 Lite (OxygenOS). Other Android 8+ phones work the same way; menu names may differ slightly. Your progress is kept on every update.

## 1. Turn on USB debugging (once)

1. **Settings → About device → Version**: tap **Build number** 7 times and enter your PIN ("You are now a developer").
2. **Settings → Additional settings → Developer options**: turn on **USB debugging**.

## 2. Connect the phone

Plug the phone into the MacBook with a USB-C **data** cable. On the phone:
- Tap **Allow** on "Allow USB debugging?" and tick *Always allow from this computer*.
- If a USB-mode prompt appears, pick **File transfer**.

Check that the Mac sees it:

```bash
$HOME/Library/Android/sdk/platform-tools/adb devices -l
```

The phone should be listed with `device`. If it says `unauthorized`, unlock the phone and accept the prompt. Lines starting with `emulator-` are emulators, not your phone.

## 3. Build (only after code changes) and install

From the project folder:

```bash
cd /Users/shishirjha/Projects/smols/chessapp
```

Build the APK (skip this if nothing changed since the last build):

```bash
ANDROID_HOME=$HOME/Library/Android/sdk ./gradlew :androidApp:assembleDebug
```

Install it. Use the phone's serial from `adb devices` in place of `<serial>`; `-s` is needed only when emulators are running too:

```bash
$HOME/Library/Android/sdk/platform-tools/adb -s <serial> install -r androidApp/build/outputs/apk/debug/androidApp-debug.apk
```

If OxygenOS shows "Install this app?" on the phone, tap **Install**. The install is done when the Mac prints `Success`. Open **Ashva** from the app drawer. The first launch after an install or update takes about 20–30 s while it checks the bundled course and puzzles once.

## Other ways to install (no cable)

- **Copy the APK:** send `androidApp/build/outputs/apk/debug/androidApp-debug.apk` to the phone (Google Drive, Quick Share or USB file transfer). Open it in **Files** and allow **Install unknown apps** for that app when asked.
- **Wi-Fi debugging:** go to Developer options → **Wireless debugging** → *Pair device with pairing code*. Then run these two commands, each with its own ip:port as shown on the phone:

```bash
$HOME/Library/Android/sdk/platform-tools/adb pair <ip:pairing-port>
```

```bash
$HOME/Library/Android/sdk/platform-tools/adb connect <ip:port>
```

## Rules for updates (keep your progress)

- **Update the same way:** install the new APK with `install -r`, or by opening the new APK. **Never uninstall Ashva or clear its storage/data.** That deletes your study history, Woodpecker cycles and review cards. Android backup is disabled for the app.
- **Install from this Mac:** the APK is signed with this Mac's debug key `~/.android/debug.keystore`. Updates must be signed with the same key, or Android refuses to install them over the existing app. Build updates on this Mac, or back up that keystore file somewhere safe.
- **Debug builds run slower than release builds.** An optimised release APK signed with the same key installs over the debug one without losing data. Ask the agent to build one (zipalign plus apksigner with the debug keystore).

## Troubleshooting

| Problem | Fix |
| --- | --- |
| Phone not listed by `adb devices` | Try another cable (some are charge-only), re-plug, and toggle USB debugging off and on. |
| `INSTALL_FAILED_UPDATE_INCOMPATIBLE` | The installed app was signed with a different key. Do **not** uninstall (that loses progress); build and sign with the original key instead. |
| `INSTALL_FAILED_INSUFFICIENT_STORAGE` | Free about 300 MB on the phone. |
| "Course/puzzles failed their checks" in the app | Reinstall the same APK with `install -r`. Progress is kept. |

See [GUIDE_0.18.md](GUIDE_0.18.md) for how to use the app.
