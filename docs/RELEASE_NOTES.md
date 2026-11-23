# Whisly v1.0.0 release notes

Version note: the GitHub tag and release are named **v1.0.0** as requested. The APK inside
reports `versionName 1.0.7` / `versionCode 7`; this mismatch is intentional and disclosed.

## Highlights

- On-device English speech-to-text (BETA) using the bundled multilingual Whisper Tiny INT8
  model through sherpa-onnx 1.13.5.
- Fail-closed transcript firewall with mandatory user confirmation of what was heard.
- On-device typed translation via ML Kit for `en, hi, bn, ta, te, kn, mr, gu, ur`.
- Android system text-to-speech with a strict installed-offline-voice gate.
- Room + Android Keystore AES-GCM encrypted local history, crash-loop Safe Mode and exit reports.
- Compose UI with the warm brown / peach design system and an animated voice orb.

## Changes in this release line

- Replaced the system recogniser with the bundled on-device engine.
- Added the single immutable pipeline state machine and one capability registry.
- Removed speech auto-detect and all canned/guessed transcript output.
- Added measured English STT evaluation; kept all non-bundled candidate models out of the app.
- Verified clean build: 47 tests passed, 0 lint errors.

## Install

1. Download `Whisly-v1.0.0.apk` from the release page.
2. Allow installation from unknown sources for your browser or file manager.
3. Open the APK. The app requires Android 7.0+ on an arm64-v8a device.

## Known limitations

- **Debug-signed preview build**, not a Play Store build. No upload key exists in this repo.
- English STT is BETA; all 12 Indic STT languages and speech auto-detect are UNAVAILABLE.
- No per-token confidence is available, so every transcript requires confirmation.
- On-device ML Kit translation models need a one-time download.
- Release runtime behaviour after R8 is UNMEASURED; no real-phone measurement was performed.
- Not production-ready.
