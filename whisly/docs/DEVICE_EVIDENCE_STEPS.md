# Device evidence collection steps

These are human/device steps. Until the output is pasted back, every item below is **UNMEASURED** in the reports.

## 1. Connect the phone

Enable USB debugging, connect the phone, and run:

```powershell
adb devices
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.config.low_ram
```

Paste the full output. Expected ABI for the current build is `arm64-v8a`; if it differs, record the real value instead of assuming.

## 2. Install the built APK

After a successful build, locate and install the debug APK:

```powershell
$apk = Get-ChildItem -LiteralPath "C:\dev\Whisly\whisly\app\build\outputs" -Recurse -File -Filter "*.apk" | Select-Object -First 1
$apk.FullName
adb install -r $apk.FullName
```

If no APK is found, paste the `Get-ChildItem` output and mark installation **UNMEASURED** rather than guessing a path.

## 3. Clear logcat and run the test path

```powershell
adb logcat -c
```

On the phone:

1. Open Whisly.
2. Grant the microphone permission when asked.
3. Tap the mic button.
4. Speak the locked English sentence: `i need to know where is the theatre`.
5. Wait for the transcript, translated text, and spoken result.
6. Repeat with a Tamil sentence when Tamil STT is bundled.
7. Open **Settings → Exit Report** and tap **Copy**, then paste the full report text.

## 4. Capture logcat

Run this immediately after the test path:

```powershell
adb logcat -v threadtime com.example:* *:E > "$env:USERPROFILE\Desktop\whisly-logcat.txt"
Get-Content -LiteralPath "$env:USERPROFILE\Desktop\whisly-logcat.txt" -Tail 400
```

Paste the full command output. The required filter `adb logcat -v threadtime com.example:* *:E` is included exactly. If the file is empty, say so; do not invent logs.

## 5. Capture memory and timing evidence

While the app is still open:

```powershell
adb shell dumpsys meminfo com.aistudio.whisly.kxmpzq
adb shell date
```

For latency, use the phone's **Dates and times** screen or the logcat timestamps around mic tap, transcript ready, translate ready, and speech finished. Record raw timestamps; compute deltas only from those timestamps.

## 6. Clean-install airplane-mode proof

Only after a model is bundled:

1. Uninstall Whisly from the phone.
2. Enable airplane mode.
3. Install the built APK or bundle with `adb install -r`.
4. Launch the app and repeat step 3 without connecting to Wi-Fi or mobile data.
5. Capture logcat and Exit Report as in steps 3 and 4.

Until this is pasted, do not claim "all offline from install". ML Kit may contact Google for bug fixes, model updates, accelerator compatibility, and metrics, so the claim needs direct proof.

## 7. Exact error strings to verify

The app must show these strings verbatim where applicable:

- `Microphone permission needed`
- `Microphone is blocked or muted`
- `Speak a little louder or closer`
- `Couldn't hear that. Try again.`
- `Couldn't understand. Try again.`
- `Speech isn't available for <language> yet`

The app must never show `install Google offline speech pack`. If any other wording appears, paste the exact text and the logcat around it.
