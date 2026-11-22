# Whisly — Current State Audit (verified against the repo)

Date: 2026-11-21
Scope: `whisly/` source tree, packaged APKs in the project root, Gradle build.
Method: source reading, `gradlew :app:assembleDebug`, unit-test artefacts, APK inspection with `aapt2`.
No physical device was connected, so nothing below is a real-device claim.

---

## 1. Build status

```
> Task :app:assembleDebug
BUILD SUCCESSFUL in 4m 21s
```

- JDK used: `C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2` (no JDK is on PATH; it is Gradle-provisioned).
- Source version: `versionCode 6`, `versionName 1.0.6` (`app/build.gradle.kts:19-20`).
- Tests were **not** re-run by that command. The last test artefacts are from **2026-11-06** and contain failures (see §6).

## 2. The APKs in the project root are STALE — this is probably why the reported bugs persist

| File | Size | Built | versionCode / versionName inside |
|---|---|---|---|
| `Whisly-v1.0.0-debug.apk` | 20.9 MB | 2026-11-06 17:38 | 1 / 1.0.0 |
| `Whisly-v1.0.1-debug.apk` | 22.7 MB | 2026-11-06 18:23 | **1 / 1.0.0** (filename is wrong) |
| Source tree | — | 2026-11-19 | 6 / 1.0.6 |

DEX strings inside `Whisly-v1.0.1-debug.apk`:

```
RenderEffect: 50
SpeechRecognizer: 3
WhislyOfflineSpeechRecognizer: 0
mlkit: 0
```

That APK is the pre-1.0.5 build: it still uses `android.speech.SpeechRecognizer` and the AGSL `RenderEffect`
code path that the changelog says caused the post-first-frame crash. So the report "app opens Home then exits"
and "install the Google offline speech pack" match the **old APK**, not the current source.

**Conclusion:** before diagnosing the P0 crash again, install a freshly built 1.0.6 APK on the phone and capture
`adb logcat -b crash -d` / `adb shell dumpsys activity exit-info com.aistudio.whisly.kxmpzq`.
If the phone still runs 1.0.0, the crash is already explained by the AGSL bug fixed in 1.0.5.

## 3. Section 11(a) checklist — what actually exists

| Item from the handoff | Reality in source | Verdict |
|---|---|---|
| Fake download code (`expectedPayload`, `models.whisly.ai`, seeded hash) | Gone. No `expectedPayload`, no `models.whisly.ai`, no fake SHA seed. | Fixed |
| Real pack downloads | **No.** Nothing downloads ONNX files. `installLanguagePacksWithProgress` calls **ML Kit** `downloadLanguageModel()` and fakes the rest. | Still broken |
| `recognizedSpeechText` always null | `AudioInput.recognizedSpeechText` is gone, **but the replacement is equally fake** (see §4.1). | Not fixed |
| `neuralSessionDelegate` unset | It is set, but only to `RealOnDeviceTranslationService.translate()` = **Google ML Kit**. There is no ONNX Runtime / sherpa-onnx dependency in Gradle at all. | Partially |
| System `SpeechRecognizer` | Removed from Kotlin code and the old helper is deleted. A `<queries>` entry remains in the manifest (harmless). | Fixed |
| English first-class | Yes, `en` is first in `CapabilityRegistry.allLanguages`, Wave 1. | Fixed |
| Download Manager screen | Exists (`DownloadManagerScreen.kt`), but its data is not real. | UI exists, data fake |
| New splash assets | `whisly_tile_logo.png` (256 KB) + Compose handoff exist. Old `whisly_logo_transparent.png` is gone. | Mostly fixed |
| Aura orb | `AuraVoiceOrb.kt` exists and replaced `PortalVoiceOrb`. It has a frame-loop bug and a clipping bug (§5). | Exists, buggy |
| 4-tab Gooey nav | Equal-weight cells, canvas has no pointer input, route-driven selection. The "Translate opens Profile" bug looks fixed in source. | Fixed in source |
| Voice/speech actually works | **No.** See §4.1. | Still broken |
| "31 tests passing" | Not true today. Artefacts show 4 failures; tests are stale. | Claim wrong |

## 4. Critical defects found (in priority order)

### 4.1 P0 — Speech recognition is still circular / fake

`WhislyViewModel.kt:81-87` creates the recognizer with:

```kotlin
acousticDecoder = { pcm16, sampleRate, lang ->
    recognizeSpeechOnDevice(pcm16, sampleRate, lang)
}
```

and `WhislyViewModel.kt:128-136`:

```kotlin
private fun recognizeSpeechOnDevice(...): Pair<String, Float>? {
    if (pcm16.isEmpty()) return null
    val latest = speechRecognizer.latestTranscript.value?.trim()
    if (!latest.isNullOrBlank()) return Pair(latest, 0.95f)
    return null
}
```

The decoder reads the recognizer's **own** output. Nothing ever sets a transcript before decoding. Every recording
therefore ends with `SPEECH_NOT_DETECTED` ("Couldn't hear that. Try again."). There is no acoustic model in the APK:
no whisper, no Conformer, no ONNX runtime, no `libs/*.aar`. The VAD/audio capture is real DSP, but no speech → text
inference happens.

Also: `IndicSttEngine` (the class that is supposed to do this) is never used for the main mic path.

### 4.2 P0 — Conversation mode starts the microphone twice and throws away the audio

`WhislyViewModel.kt:742-750`:

```kotlin
speechRecognizer.startListening(...)          // this already calls audioProcessor.startRecording()
audioProcessor.startRecording(viewModelScope) // second, competing session
```

Then on stop (`:691-704`):

```kotlin
speechRecognizer.stopListening()  // consumes+clears the PCM buffer internally
val rawInput = audioProcessor.stopRecording() // now empty
```

So Conversation voice turns cannot work (audio contention + empty buffer).

### 4.3 P1 — Translation is ML Kit only; the "neural pack" story is fiction

- `RealOnDeviceTranslationService` is Google ML Kit `com.google.mlkit:translate:17.0.3`.
- No IndicTrans2 model, no ONNX runtime, no tokenizer. `IndicTrans2Engine` is a wrapper that calls the ML Kit delegate.
- `IndicTrans2Engine.preprocessSourceText()` and `postprocessTargetText()` contain **sentence-specific hardcoded
  Tamil rewrites** (`"பிச்சை போல்" → "பிச்சைக்காரனைப் போல"`, `"என் வயது வந்தோர்" → "நான் வளர்ந்த பிறகு"`, etc.).
  That is canned text for the exact reference sentence, not translation. It violates the "never fake / no word lists"
  rule and will behave inconsistently on every other sentence.
- `CapabilityRegistry` marks almost every pair `VERIFIED` with no measurement at all.

ML Kit itself needs Google Play Services and an initial model download per language. It can work offline afterwards,
but it is not the app-owned, hash-verified pack system the UI and docs promise.

### 4.4 P1 — Download manager shows invented progress and fake installed rows

`WhislyModelManager.installLanguagePacksWithProgress()`:

- hardcodes `bytesDone = 5_000_000`, `bytesTotal = 30_000_000`, `speedBytesPerSec = 1_500_000`, `eta = 16`
  (`:260-267`);
- hardcodes the Verifying phase as `28 MB / 30 MB` (`:294-299`);
- writes an `InstalledModelEntity` with `localFilePath = "mlkit/translate/$langCode"`, `verifiedSha256 = "verified_on_device"`,
  `fileSizeBytes = 30_000_000`, `isVerified = true` (`:318-330`) — not a file and not a hash;
- the real `install(packId)` HTTP/Range/SHA-256 code is dead code because the UI never calls it.

The catalogue is also wrong: `PACK_MT_INDIC_INDIC`'s `downloadUrl` is the **Whisper tiny ASR tarball** with the
Whisper SHA-256 (`CapabilityRegistry.kt:733-753`). Most `tts_vits_rasa_*` pack IDs do not exist in the catalogue at all.

UI sizes are therefore wrong: every Indic language shows ~116 MB, English ~138 MB, while the actual ML Kit download
is roughly 30 MB per language.

### 4.5 P1 — Readiness is judged from `LanguageEntity.isInstalled`, so English pairs always look "not downloaded"

`TranslateScreen.kt:99`:
```kotlin
val isPairVerifiedOffline = (isSourceAuto || sourceLang?.isInstalled == true) && (targetLang?.isInstalled == true)
```
`HomeScreen.kt:82` uses the same idea. English never needs a pack, but `en.isInstalled` stays false, so
English→Tamil and Tamil→English show the "Download … pack" banner forever even when translation works.

### 4.6 P2 — `swapLanguages` swaps the transcript and the translation

`WhislyViewModel.kt:367-376`:
```kotlin
recognizedTranscript = it.translatedSentence,
translatedSentence = it.recognizedTranscript,
```
After a swap the original text appears as the translation and vice versa (an echo). It should either clear both or
re-translate, never swap them.

### 4.7 P2 — Aura orb animation loop restarts on every mic sample, and the Home orb is clipped

- `AuraVoiceOrb.kt:166`: `LaunchedEffect(shouldRunClock, state, micLevel)` restarts on every RMS update while
  listening, resetting `lastFrameMs` and stuttering/suppressing the waveform.
- `HomeScreen.kt:168-180` puts a `168dp` orb (rings reach ~128dp, glow ~127dp) inside a `96dp` box with
  `Modifier.fillMaxSize()` → rings/glow are clipped. That is the "flat orange circle" look.

### 4.8 P2 — Successful translation can still show a voice error

Text mode (`WhislyViewModel.kt:877-893`) runs the full TTS stage even with `autoPlay = false`, then sets
`activeError = outcome.error`. On a phone without an offline TTS voice this puts "Voice Not Available" on screen
after a successful translation. The same happens in voice mode (`:612`).

### 4.9 P2 — Error banner still concatenates title + body

`TranslateScreen.kt:191-198` builds `"${err.title}: ${err.userMessage}"`. For `OFFLINE_SPEECH_PACK_MISSING`
that produces the old Google-pack wording (which no longer even applies). `SPEECH_NOT_DETECTED` avoids duplication
only because its `userMessage` is empty.

### 4.10 P2 — Crash-loop guard counts normal quick exits as failures

`CrashProtectionManager.checkAndRecordLaunch()` increments a counter on every launch and only main-thread
`markLaunchSuccessful()` after 5 s resets it. Opening the app and closing it within 5 s twice (normal user behaviour)
triggers Safe Mode on the third launch. Safe Mode then looks like "the app exits by itself" to a user.

### 4.11 P3 — Destructive migration is enabled

`WhislyDatabase.kt:126` uses `.fallbackToDestructiveMigration(true)`. Any future schema miss wipes history,
saved phrases and settings, contrary to the "preserve user data" rule.

## 5. What is genuinely good / reusable

- Real 16 kHz mono `AudioRecord` capture, pre-roll, adaptive-floor VAD, clipping and too-quiet detection
  (`AudioProcessor.kt`).
- Room entities, encrypted history (Keystore AES-GCM), exportable exit report, Safe Mode screen.
- Navigation mapping and the new equal-cell Gooey nav.
- Aura orb drawing code (once the clock key and Home size are fixed).
- Build is currently green.

## 6. Test reality

Last artefacts (`app/build/test-results/testDebugUnitTest`, 2026-11-06):

```
ExampleRobolectricTest: tests=5 failures=1  (app_name/Room seed assertion)
ExampleRobolectricTest: tests=1 failures=1  (Android SDK 36 requires Java 21, have 17)
ExampleUnitTest:        tests=8 failures=0
ExampleUnitTest:        tests=8 failures=2  (expected ONLINE, was OFFLINE_READY; expected STT_LOW_CONFIDENCE, was MODEL_MISSING)
```

`OfflineZeroNetworkGuardTest` injects a canned Tamil string as the "neural delegate" and then asserts translation
succeeded — that is a self-fulfilling test, not evidence of a working engine. The suite must be rewritten once a real
engine exists.

To run unit tests today we need **JDK 21** (Robolectric SDK 36 requirement), not the JDK 17 that Gradle provisioned.
Gradle can fetch it automatically if `org.gradle.java.installations.auto-download=true`, or install Temurin 21.

## 7. Blockers / questions before the next phase

1. **Which APK is on the phone?** Please install the fresh 1.0.6 debug build and send:
   - phone model, Android version, ABI (arm64/armv7);
   - `adb logcat -b crash -d` and `adb shell dumpsys activity exit-info com.aistudio.whisly.kxmpzq`.
   Without this, the P0 crash cannot be closed with evidence.
2. **Real STT engine choice.** To make speech work we must add an on-device runtime + model. The realistic path is
   sherpa-onnx (Apache-2.0 AAR) + a verified Whisper/IndicConformer int8 model downloaded from a pinned URL.
   That changes APK size, adds native code, and needs a hosting decision (Hugging Face resolve URL pinned to a commit,
   or a GitHub Release on the user's repo). Confirm which hosting you approve.
3. **Model/hosting access**: I cannot upload files to GitHub/HF. I can wire downloads to upstream URLs only if the
   license and SHA-256 are verified.
4. **Remove the hardcoded Tamil rewrites?** They currently mask one reference sentence. The honest fix is to remove
   them and improve the engine. Confirm.
5. **JDK 21** for Robolectric tests — OK to let Gradle download it?

## 8. Proposed next steps (small, verifiable first)

1. Build and hand over a fresh 1.0.6 APK; confirm it launches on the phone (kills the stale-APK variable).
2. Fix the cheap correctness bugs while the engine decision is pending:
   - readiness logic (English never needs a pack);
   - `swapLanguages`;
   - orb clock key + Home orb size;
   - text-mode TTS side effect and error-banner duplication;
   - crash-loop counter (only count launches that die inside the window, not normal exits).
3. Delete the hardcoded Tamil post-processing and the self-fulfilling test.
4. Then tackle the real P1: sherpa-onnx STT (isolated `:engine` process), followed by a real pack manifest and
   download manager, then TTS packs.
