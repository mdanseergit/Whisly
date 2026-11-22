# Whisly — Diagnosis 8 (verified against repo, 2026-11-21)

## 0. Build evidence

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 51s
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 32s  (33 tests, 0 failures)
```

- JDK: `C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2` (JDK 17; Robolectric SDK 36 needs JDK 21 — UNMEASURED for instrumented tests).
- Fresh debug APK: `C:\Users\danse\AppData\Local\Temp\whisly_build\app\outputs\apk\debug\app-debug.apk`,
  size 52,514,842 bytes, SHA-256 `C7BA62C74ADFD2E4EF44236824B98768999DE8717CD219C17DB34887E0781E2C`.
- Source version: versionCode 6 / 1.0.6.

## 1. Engines that actually run today

| Capability | Engine actually used | Notes |
|---|---|---|
| STT | `WhislyOfflineSpeechRecognizer` (AudioRecord + VAD) + a **circular** `acousticDecoder` that reads its own `latestTranscript` → always `SPEECH_NOT_DETECTED`. No ASR model in APK. | `IndicSttEngine` is wired but its `acousticRecognizerDelegate` is the same circular function. |
| MT (typed + voice) | Google ML Kit on-device Translation (`com.google.mlkit:translate:17.0.3`) via `RealOnDeviceTranslationService`. | Requires Play Services model download; works offline afterwards. Not IndicTrans2. |
| TTS | `VitsRasaTtsEngine` → Android `TextToSpeech.synthesizeToFile` if an offline voice exists, else `MODEL_MISSING`/`UNSUPPORTED_TARGET_VOICE`. | No neural VITS delegate wired. |
| Language ID | Not used in main path. | ML Kit Language Identification not wired. |
| Packs | `WhislyModelManager.installLanguagePacksWithProgress` fakes 5 MB/30 MB, 1.5 MB/s and writes phantom `InstalledModelEntity` rows. Real HTTP/SHA code (`install()`) is dead. | `isPackInstalledAndVerified` then fails because the phantom file path does not exist. |

## 2. Network in offline mode

- `AndroidManifest.xml` declares `INTERNET` + `ACCESS_NETWORK_STATE` (needed for ML Kit model download and future online mode).
- No `models.whisly.ai`, `expectedPayload`, or seeded-hash strings remain (verified by grep + APK DEX scan of v1.0.6 debug).
- Airplane-mode traffic capture: UNMEASURED (no device connected). Steps: install APK, enable airplane mode, run `adb shell tcpdump` or use Android Studio Network Profiler, speak a sentence, confirm zero packets.

## 3. Root causes (priority order)

1. **P0 crash candidate (stale APK):** `deliverables/whisly-v1.0.6-debug.apk` and the fresh build both contain `RenderEffect` strings (from androidx.compose.ui.graphics) and `SpeechRecognizer` references (from manifest `<queries>` + libraries), but NO `android.speech.SpeechRecognizer` usage in Kotlin. The older root APKs (`Whisly-v1.0.0/1.0.1-debug.apk`, versionCode 1) still use the system recognizer and are the likely cause of the reported "opens Home then exits" and "install Google speech pack" messages. **Must confirm which APK is on the phone.**
2. **STT is circular** (see §1). → UNAVAILABLE for speech until a real ASR runtime is added.
3. **Conversation mode starts the mic twice** (`speechRecognizer.startListening` + `audioProcessor.startRecording`) and the stop path reads an already-consumed buffer → empty audio. (Not touched this round; still present.)
4. **Hardcoded Tamil rewrites** in `IndicTrans2Engine.preprocessSourceText`/`postprocessTargetText` (sentence-specific canned text) — **removed this round**; conservative typo correction (`begger`→`beggar`, `childhoood`→`childhood`) retained.
5. **Readiness from Room `isInstalled`** made English pairs look "not downloaded" forever — fixed in `TranslateScreen` (English treated as ready).
6. **`swapLanguages()` swapped transcript/translation** (echo) — fixed: now clears both.
7. **Text mode showed a voice error** after a successful translation — fixed: `activeError = null` on MT success in text mode.
8. **Error banner concatenated title + body** — fixed: shows userMessage or title only.
9. **Aura orb clock restarted on every mic sample** — fixed: `micLevel` removed from `LaunchedEffect` key via `rememberUpdatedState`.
10. **Home orb clipped** (96 dp box around a 168 dp orb) — fixed: box + orb sized 168 dp.
11. **Crash-loop guard counted normal quick exits** as crashes — fixed: only rapid relaunches (≤30 s) count.
12. **Destructive migration fallback** enabled — removed; migrations 1→4 remain.

## 4. What I changed this round (code)

- `IndicTrans2Engine.kt`: removed sentence-specific rewrites; kept conservative typo correction; postprocess is identity.
- `WhislyViewModel.kt`: `swapLanguages()` clears transcripts; text-mode success no longer propagates TTS error.
- `TranslateScreen.kt`: readiness treats English as ready; error banner no longer duplicates title+body.
- `HomeScreen.kt`: orb container 168 dp.
- `AuraVoiceOrb.kt`: clock no longer restarts per mic sample.
- `CrashProtectionManager.kt`: rapid-relaunch gating.
- `WhislyDatabase.kt`: no destructive fallback.
- `TranslationRegressionLockTest.kt` and `OfflineZeroNetworkGuardTest.kt`: updated to reflect identity postprocess + typo correction; added explanation comments. No fake expectations added.

## 5. Still broken / UNAVAILABLE (honest)

- **Speech recognition (STT): UNAVAILABLE** — no ASR model/runtime in the APK. Requires a hosting + license decision (sherpa-onnx + Whisper/IndicConformer) and ~110 MB download. Not started.
- **Online mode: not implemented** — needs backend + billing decision (STOP AND ASK).
- **Real pack download manager: still fake** — `installLanguagePacksWithProgress` fabricates progress and phantom rows. Needs ML Kit `RemoteModelManager` + verified manifest. Not started.
- **TTS: platform TTS only** — neural VITS not wired.
- **Language ID: not wired.**
- **Conversation mode mic contention** (start twice / empty buffer) — not fixed.
- **Robolectric tests** require JDK 21; run with JDK 17 only exercises JVM tests. UNMEASURED.

## 6. Blocking questions for you

1. Which APK is installed on the phone (versionCode from `adb shell dumpsys package com.aistudio.whisly.kxmpzq | grep versionCode`)? Please install the fresh debug APK above and send `adb logcat -b crash -d` + `adb shell dumpsys activity exit-info com.aistudio.whisly.kxmpzq`.
2. Approve a hosting source for model weights (Hugging Face pinned commit URL or GitHub Release) and confirm licenses (Whisper MIT, IndicConformer, VITS/Piper) are acceptable for your Play Store release.
3. Online mode needs billing: approve creating a GCP project + Cloud Run + Secret Manager, or pick a free-tier host. I will not create anything until you say so.
4. Provide 20–30 more reference sentence pairs (source + expected target) for the regression lock.
5. OK to let Gradle auto-download JDK 21 for Robolectric instrumented tests?

## 7. Evidence pointers

- Build/test output: see §0.
- Unit tests: `app/build/test-results/testDebugUnitTest` (33 tests, 0 failures) — report path under the temp build dir.
- No new native libraries added; APK still ships `libtranslate_jni.so` (ML Kit) only.
