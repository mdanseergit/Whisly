# Whisly final APK — 2026-11-23

Version **1.0.7** (versionCode 7), package `com.aistudio.whisly.kxmpzq`,
minSdk 24, targetSdk 36, arm64-v8a only.

## Files

| File | Bytes | What it is |
|---|---:|---|
| `Whisly-FINAL.apk` | 113,232,981 | Release variant, R8/minify + resource shrink ON. **Preview, debug-signed, not for Play Store.** |
| `Whisly-FINAL-debug-fallback.apk` | 137,360,017 | Debug variant fallback, debug-signed. |
| `mapping.txt` | 56,382,555 | R8 mapping for the release APK (keep for crash de-obfuscation). |
| `BUILD_LOG.txt` | see file | Full local build, test, lint and static-verification log. |
| `SHA256SUMS.txt` | — | SHA-256 of every file above. |

**No `Whisly-FINAL.aab`:** a real upload keystore was not configured
(`my-upload-key.jks` absent; no `KEYSTORE_PATH`/password variables set), so the
bundle was built only as a local build check and is deliberately **not** delivered.
The release and debug APKs are both signed with the Android debug key
(certificate SHA-256 `debb01439b4cdd03fee1688efa1f9e6d3699d44636b15fd96221fbbb35a693d7`).

## What was actually verified (PC only, no device)

- `clean assembleDebug testDebugUnitTest lint` → **BUILD SUCCESSFUL**; **47 tests, 0 failures**;
  lint **0 errors, 105 warnings** (categories listed in `BUILD_LOG.txt`).
- `assembleRelease bundleRelease` → **BUILD SUCCESSFUL**, `minifyReleaseWithR8` executed.
- `apksigner verify`: release APK verifies, v2 scheme, 1 signer (debug certificate).
- `aapt2 dump badging`: package/version/minSdk/targetSdk, 8 permissions, `native-code: arm64-v8a`.
- `apkanalyzer`: file size 113,232,981 B; download-size estimate 83,476,730 B;
  largest components are the bundled Whisper Tiny INT8 decoder (49.7 MB),
  `libonnxruntime.so` (20.7 MB), `libtranslate_jni.so` (15.6 MB) and the
  Tiny encoder (7.9 MB). Manifest `debuggable=false`; no debug/test classes,
  no secret-like key patterns in the APK.
- Bundled Whisper Tiny assets and arm64-v8a sherpa-onnx/ONNX libraries confirmed present.
- `Start-MpScan` result on this folder: see `CLEANUP_REPORT.md` (appended after the scan).

## Current capabilities (as implemented, not measured here)

- **Typed translation**: ML Kit on-device, en/hi/bn/ta/te/kn/mr/gu/ur; first use needs a
  one-time model download. `ml/pa/or/as/sat` fail closed with
  `Translation isn't available for <language> offline yet`.
- **English speech**: **BETA** — bundled Whisper Tiny INT8 with a mandatory user confirmation step.
- **Indic speech**: **UNAVAILABLE**. Speech language auto-detect: **UNAVAILABLE**.
- **TTS**: uses voices installed on the phone, with exact-locale/installed-voice checks;
  no bundled voice.
- Fail-closed outputs: `Not sure: check and edit` / `Couldn't understand. Try again.`
  No invented confidence scores.

## UNMEASURED — do not infer these from the build

- Any real-phone run or install; device speed, memory use, latency or crashes.
- Native-speaker translation quality; TTS voice quality.
- Release-runtime behaviour after R8 (static checks only; keep rules listed in `BUILD_LOG.txt`).
- Airplane-mode/offline proof.
- Play Console compressed-size check (never uploaded).

**Not production-ready. Not Play Store-ready.** No adb, emulator, device, account login,
upload or Play Console action was used to produce these files.

## Install note (only if you choose to install later)

Copy the APK to the phone only after reading the recorded Defender custom-scan outcome
for this folder in `CLEANUP_REPORT.md`. Allow "install from unknown sources" only for that single
install, then turn the setting off again. The phone must stay unplugged until you decide otherwise.
