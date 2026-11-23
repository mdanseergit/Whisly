# One-page phone evidence checklist

Everything on this page is **UNMEASURED** until you run it on a real device and paste the raw output back. No device is attached to this workstation and no AVD exists, so the instrumented test items are currently **BLOCKED**, not passed.

## A. Connect the phone (USB or Wireless)

**USB:** Settings → About phone → tap *Build number* 7× → System → Developer options → **USB debugging = on**. Connect by cable. On the phone, accept *Allow USB debugging* and tick *Always allow from this computer*.

**Android 11+ Wireless (no cable):** Developer options → **Wireless debugging = on** → *Pair device with pairing code*. Note the pair code and the port. Then, with the phone on the same Wi-Fi:

```powershell
adb pair <phone-ip>:<pairing-port>
# enter the 6-digit pairing code when prompted
adb connect <phone-ip>:<connection-port>   # the *connection* port, not the pairing port
```

**Confirm the device is attached (both paths):**

```powershell
adb devices -l
adb shell getprop ro.product.model
adb shell getprop ro.build.version.release    # API level target: 24, 30, 34/36
adb shell getprop ro.build.version.sdk
adb shell getprop ro.product.cpu.abi
adb shell getprop ro.product.cpu.abilist
adb shell getprop ro.config.low_ram
```

Paste the full output. `adb devices -l` must show `device`, not `unauthorized` or `offline`.

## B. Install the built debug APK

```powershell
$apk = "C:\dev\Whisly\whisly\app\build\outputs\apk\debug\app-debug.apk"
adb install -r $apk
```

If that path does not exist, paste `Get-ChildItem "C:\dev\Whisly\whisly\app\build\outputs" -Recurse -Filter *.apk` instead. Do not guess a path.

## C. Instrumented tests (exact commands)

From `C:\Users\danse\OneDrive\document\Whisly\whisly`, with `JAVA_HOME` set:

```powershell
$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
.\gradlew.bat clean connectedDebugAndroidTest
.\gradlew.bat connectedDebugAndroidTest --tests "com.example.*"
```

Current blocker (paste this if you see it):

```
com.android.builder.testing.api.DeviceException: No connected devices!
```

**SDK platform gap:** API 24 and API 30 platforms are not installed here (only 34/36/36.1/37). To cover the required API 24, 30, 34/36 matrix you must either attach real devices at those levels or install the SDK platforms and AVDs. Until then those rows stay `UNMEASURED`.

## D. DEBUG-only on-device recognizer probe

Only the **debug** APK has this screen. Open **Settings → On-device recognizer probe (DEBUG)**.

1. Grant `RECORD_AUDIO` when asked.
2. Tap **Refresh support**. Paste the `Availability:` line and the per-language `installed/pending/supported/online` rows for all 14 languages.
3. For a language showing `supported=YES installed=NO`, tap **Download language pack (in-app)** and paste the status text. This calls `triggerModelDownload` from inside the app; it does **not** launch a Settings intent. Whether the system shows its own approval UI is device-dependent and remains user-observable/`UNMEASURED` until you report it.
4. Push the pinned evaluation clips (names must start with the language code, e.g. `hi_*.wav`), 16 kHz mono PCM16:

```powershell
adb shell mkdir -p /sdcard/Android/data/com.aistudio.whisly.kxmpzq/files/eval_clips
adb push C:\dev\Whisly-eval\bakeoff2\kathbath_hi\wav\hi_kathbath_00.wav /sdcard/Android/data/com.aistudio.whisly.kxmpzq/files/eval_clips/
# app-private filesDir is different from the external path above; if the screen reports no clips,
# copy from the external staging dir into the app-private dir via run-as (debuggable builds only):
adb shell run-as com.aistudio.whisly.kxmpzq mkdir -p files/eval_clips
adb shell run-as com.aistudio.whisly.kxmpzq cp /sdcard/Android/data/com.aistudio.whisly.kxmpzq/files/eval_clips/hi_kathbath_00.wav files/eval_clips/
```

5. Tap **Run evaluation clips**, then **Export CSV** and share the CSV back. Paste it verbatim. Every cell is `UNMEASURED` until this CSV exists.

## E. Voice-pipeline evidence (existing locked path)

```powershell
adb logcat -c
# ... on phone: open Whisly, grant mic, tap mic, say "i need to know where is the theatre",
#     wait for transcript -> Translate tap -> spoken output ...
adb logcat -v threadtime com.example:* *:E > "$env:USERPROFILE\Desktop\whisly-logcat.txt"
Get-Content -LiteralPath "$env:USERPROFILE\Desktop\whisly-logcat.txt" -Tail 400
adb shell dumpsys meminfo com.aistudio.whisly.kxmpzq
```

Open **Settings → Diagnostics & Exit Report → Copy** and paste the full report. Record timestamps for mic tap, transcript ready, translation ready, speech finished; compute deltas only from those.

## F. Clean-install airplane-mode proof (only after a model decision)

1. Uninstall Whisly. 2. Enable airplane mode. 3. `adb install -r <apk>`. 4. Repeat section E with no Wi-Fi/mobile data. 5. Paste logcat and Exit Report. Until then the "offline from install" claim stays `UNMEASURED`.

## G. Exact strings to verify

`Microphone permission needed`, `Microphone is blocked or muted`, `Speak a little louder or closer`, `Couldn't hear that. Try again.`, `Couldn't understand. Try again.`, `Speech isn't available for <language> yet`, `Voice not available for <language>`, `Not sure: check and edit`, `Translation isn't available for <language> offline yet`.

The app must never show `install Google offline speech pack`, transliteration, echo, or canned text. If any other wording appears, paste the exact text and surrounding logcat.
