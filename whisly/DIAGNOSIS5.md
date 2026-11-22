# Whisly v1.0.2 — Comprehensive Architectural Diagnosis (DIAGNOSIS5)
**Date:** November 17, 2026  
**Package:** `com.aistudio.whisly.kxmpzq`  
**Status:** PHASE 0 COMPLETE — ROOT CAUSES IDENTIFIED WITH COMMAND-LINE EVIDENCE

---

## 1. Executive Summary

A comprehensive forensic audit of the Whisly codebase was performed to diagnose the five critical defects reported by the user:
1. **Speech Detection Failure (A / D4):** The app returns "Couldn't hear that" or fails to detect speech on physical devices.
2. **Translation Quality & Echoing (B / S2):** Inputs echo back unchanged or translation is blocked with "Pack Needed" while displaying "Translation ready".
3. **Canned Greetings / Canned Speech (C / E2):** Historical stubs and canned phrases ("Hello" / "vannakkam") observed in speech paths.
4. **UI Defects, Navigation & Downloads (D / S1 / S4 / D1 / D2 / D3):** Downloads hanging, UI touch unresponsive, tab routing mismatch (Translate opening Profile), text clipping (e.g. Malayalam wrapping "Mal / ayal").
5. **APK Size (E):** Current debug APK inflated to 88.6 MB (target: < 40 MB base APK).

All 19 automated unit and Robolectric tests currently pass (`BUILD SUCCESSFUL in 1m 9s`, 33 tasks up-to-date). However, deep runtime code analysis revealed severe underlying architectural conflicts between concurrent audio recording, pack gating, and model manifest management.

---

## 2. Forensic Diagnosis & Root Cause Analysis

### Defect A / D4: Speech Detection Fails / "Couldn't hear that" on Physical Phones

#### Root Cause 1: Microphone Hardware Concurrency Collision
In `WhislyViewModel.kt` (`startMainRecording` lines 347-386):
```kotlin
speechRecognizerHelper.startListening(...)
audioProcessor.startRecording(viewModelScope) { _, elapsed -> ... }
```
- `speechRecognizerHelper` starts Android's system `SpeechRecognizer`, which opens an internal `AudioRecord` through `AudioFlinger`.
- Simultaneously, `audioProcessor.startRecording()` opens another `AudioRecord(MediaRecorder.AudioSource.MIC, 16000, ...)`.
- On Android 10+ (API 29+), concurrent audio capture policies prevent two simultaneous recording sessions on the same mic input without specialized system privileges. As a result, either `AudioRecord` reads all zeros / silent frames, or `SpeechRecognizer` fails with `ERROR_AUDIO` (error code 3) or `ERROR_RECOGNIZER_BUSY` (error code 8).

#### Root Cause 2: Double-Check Pipeline Overwrite
In `WhislyViewModel.kt` (`stopMainRecordingAndRunStt` lines 388-427):
- When the user taps to stop recording, `stopMainRecordingAndRunStt()` stops both components.
- It then executes: `pipeline.processRecordedAudioToTranscript(rawInput, sourceLang)`.
- If `AudioRecord` recorded silence due to the mic collision, `AudioProcessor.preprocessAndRunVad()` determines `vadSpeechDetected = false` and returns `PipelineErrorType.SPEECH_NOT_DETECTED`.
- In `domain/models/WhislyModels.kt` (lines 92-95), `SPEECH_NOT_DETECTED` has:
  ```kotlin
  SPEECH_NOT_DETECTED("Couldn't Hear That", "Couldn't hear that. Try again.")
  ```
- If speech *was* detected by `AudioProcessor`, `IndicSttEngine.recognize()` is invoked, which checks:
  ```kotlin
  val sttInstalled = isPackInstalledAndVerified(capability.sttPackId)
  if (!sttInstalled) return SpeechResult(..., error = PipelineErrorType.MODEL_MISSING)
  ```
  Since `stt_whisper_tiny_multilingual` is not downloaded on a fresh install, `sttInstalled` evaluates to `false`. The engine returns `MODEL_MISSING`, completely overwriting any live transcript that `SpeechRecognizer` might have captured.

---

### Defect B / S2: Translation Quality & Echoing Input

#### Root Cause 1: Strict Pack Gate Blocks Translation Prior to Invocation
In `WhislyViewModel.kt` (`confirmTranscriptAndTranslate` lines 433-472) and `TextTranslationScreen.kt` (`runTextModeTranslation` lines 720-760):
```kotlin
val pair = CapabilityRegistry.getDirectedPair(effectiveSource, state.targetLangCode)
val isPairReady = pair != null && pair.requiredMtPackIds.all { modelManager.isPackInstalledAndVerified(it) }
if (!isPairReady) {
    _pipelineStage.value = PipelineStage.IDLE
    _uiState.update {
        it.copy(
            translatedSentence = "",
            activeError = PipelineErrorType.MODEL_MISSING,
            statusBannerMessage = "Download $targetName pack to translate offline"
        )
    }
    return@launch
}
```
- In `CapabilityRegistry.kt`, all pairs require `PACK_MT_INDIC_INDIC` (`"mt_indictrans2_200m_int8"`).
- Because `modelManager.isPackInstalledAndVerified(PACK_MT_INDIC_INDIC)` returns `false` (0 downloaded), `pipeline.translateAndSynthesize` is **never executed**.
- In earlier builds, when translation was blocked or failed, `translatedSentence` was either left untouched (retaining whatever text was in the card) or an input-echo fallback was triggered, displaying the English input in the "Translation - Tamil" box while the status said "Translation ready".

#### Root Cause 2: ML Kit Translate Offline Model Availability
`RealOnDeviceTranslationService.kt` uses `com.google.mlkit:translate:17.0.3`. While ML Kit offers neural on-device translation:
- The models (~30 MB each) are not bundled in the APK; they must be downloaded via Google Play Services / Google ML Kit model manager.
- If the device is offline or the model is not yet downloaded, `translator.downloadModelIfNeeded()` fails or throws when offline, returning `null`.
- To deliver sentence-level natural Indic translations (e.g. "i need to know where is the theatre" -> "திரையரங்கம் எங்கே இருக்கிறது என்று நான் தெரிந்துகொள்ள வேண்டும்") without relying on Google Play Services, quantized ONNX models with verified weights must be hosted and extracted properly.

---

### Defect C / E2: Canned "Hello" / "vannakkam" Output

#### Audit Findings:
- Exhaustive grep across `whisly/app/src/main` confirmed:
  - `IndicNeuralTranslationDecoder.kt` (which contained the letter-by-letter transliteration and hardcoded word lists) was **completely deleted**.
  - `src/main/java` contains **zero** canned greeting maps or word dictionaries.
- The remaining occurrences of "Hello" and "வணக்கம்" are strictly in:
  1. Unit tests (`ExampleUnitTest.kt`, `ExampleRobolectricTest.kt`) asserting that silence/noise **never** returns "Hello" or "வணக்கம்".
  2. Jetpack Compose UI previews (`@Preview` functions in `TranslateScreen.kt`, `ConversationScreen.kt`, `TextTranslationScreen.kt`).
- The root cause of the phone user hearing "vannakkam" / "Hello" in prior builds was the old decoder's fallback and formant prosody synthesizer, which played canned waveforms whenever inference failed.

---

### Defect D: UI Alignment, Tab Routing, and Download Manager

#### Root Cause 1: Bottom Navigation Tab Discrepancy (D3)
In `WhislyGooeyNavBar.kt` lines 123-125 and line 279:
```kotlin
val targetIndex = remember(currentRoute, items) {
    items.indexOfFirst { it.route == currentRoute }.coerceAtLeast(0)
}
...
val isSelected = item.route == currentRoute
```
- In `WhislyNavigation.kt`, `WhislyGooeyNavBar` was passed `currentRoute = activeTabRoute`.
- However, when navigating to sub-destinations like `WhislyRoutes.LANGUAGES` or `WhislyRoutes.SETTINGS`, if `currentRoute` was checked directly in the click handler:
  ```kotlin
  onNavigate = { targetRoute ->
      if (currentRoute != targetRoute) {
          val startDest = navController.graph.findStartDestination()
          navController.navigate(targetRoute) {
              popUpTo(startDest.id) { saveState = !isTargetStart }
              restoreState = !isTargetStart
          }
      }
  }
  ```
- Because `startDestination` was initially `SPLASH` (and was popped on splash finish with `popUpTo(SPLASH) { inclusive = true }`), `navController.graph.findStartDestination()` returned an id that was no longer active on the backstack. This caused `restoreState` to restore the previously visited `Profile` destination instead of switching to `Translate`.

#### Root Cause 2: Malayalam Text Wrapping ("Mal / ayal")
In `LanguagePackCard.kt`:
- Native and English names in a single horizontal row collided with the fixed-width Download button on compact device screens (e.g. 360dp width).
- Solved by stacking `nativeName` above `englishName` in a `Column` with `Modifier.weight(1f, fill = false)`, `maxLines = 1`, and `TextOverflow.Ellipsis`.

#### Root Cause 3: Download Manager Archive & URL Integrity
In `CapabilityRegistry.kt` lines 675-752 and `WhislyModelManager.kt` lines 348-360:
1. HTTP HEAD verification of model URLs confirmed:
   - `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2`: **200 OK** (116,204,861 bytes = 110.8 MB).
   - `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-inflect-en-nano-v2.tar.bz2`: **200 OK** (22,429,639 bytes = 21.4 MB).
   - `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-bn-custom_female.tar.bz2`: **200 OK** (108,053,596 bytes = 103.0 MB).
2. **Defect in `PACK_MT_INDIC_INDIC`:** The URL was mistakenly set to the Whisper Tiny ASR model URL instead of a translation model.
3. **Defect in File Handling:** All URLs download `.tar.bz2` compressed archives. `WhislyModelManager.kt` copied the staged `.tar.bz2` file directly to `${pack.packId}.onnx` without decompressing or extracting the inner files (`tokens.txt`, `encoder.onnx`, `decoder.onnx`). Any engine attempting to load the resulting `.onnx` file crashed with runtime format errors.

---

### Defect E: APK Size Breakdown & Optimization

#### Measured APK Size: 88,601,649 bytes (84.5 MB)
A forensic extraction of `deliverables/whisly-v1.0.2-ai-fixed-debug.apk` revealed:

| Component | Compressed Size | Uncompressed Size | File Count | Notes |
| :--- | :--- | :--- | :--- | :--- |
| **`lib/` (Native C++ Runtime)** | **59.76 MB** | **59.76 MB** | 12 | 4 ABIs bundled uncompressed |
| - `arm64-v8a` | 15.62 MB | 15.62 MB | 3 | Target primary physical phone ABI |
| - `armeabi-v7a` | 11.08 MB | 11.08 MB | 3 | Legacy 32-bit ARM |
| - `x86` | 16.47 MB | 16.47 MB | 3 | 32-bit Emulator |
| - `x86_64` | 16.58 MB | 16.58 MB | 3 | 64-bit Emulator |
| **`root` (DEX bytecode)** | **20.49 MB** | **65.21 MB** | 34 | Debug build, R8 minification disabled |
| **`res/` (Resources & Assets)** | **2.47 MB** | **2.78 MB** | 427 | Drawables, XMLs, fonts |
| **`META-INF/`** | 0.01 MB | 0.02 MB | 84 | Manifests & signatures |
| **Total APK** | **84.5 MB** | **127.8 MB** | 568 | Universal debug build |

#### Conclusion on APK Size:
- Over **70%** (59.76 MB) of the APK size comes from bundling native `.so` libraries for all four CPU architectures simultaneously in debug mode.
- When built with `ndk.abiFilters.add("arm64-v8a")` (or via Android App Bundle split per ABI) and release R8 minification (`isMinifyEnabled = true`, `isShrinkResources = true`), the base APK size drops to **~22-26 MB**, easily beating the ~40 MB ceiling.

---

## 3. Systematic 9-Phase Rebuild Plan

| Phase | Focus | Key Actions |
| :--- | :--- | :--- |
| **Phase 1** | **Fail-Closed Architecture** | Enforce zero-echo policy; missing packs fail closed with explicit, user-friendly error banners. Remove any remaining legacy fallback branches. |
| **Phase 2** | **Speech Pipeline Rebuild** | Eliminate microphone concurrency collision between `AudioRecord` and `SpeechRecognizer`. Implement clean streaming partials and accurate final transcription with VAD pre-roll. |
| **Phase 3** | **Neural Translation Engine** | Ensure proper offline translation model lifecycle. Support direct sentence-level natural translation ("i need to know where is the theatre" -> "திரையரங்கம் எங்கே இருக்கிறது என்று நான் தெரிந்துகொள்ள வேண்டும்"). |
| **Phase 4** | **Speech Synthesis (TTS)** | Clean synthesis pipeline via verified Android TextToSpeech WAV synthesis or VITS ONNX at exact native sample rates without formant stubs. |
| **Phase 5** | **Download Manager & Pack Extraction** | Implement proper `.tar.bz2` archive extraction into individual model components (`model.onnx`, `tokens.txt`). Support pause, resume, cancel, and accurate disk verification. |
| **Phase 6** | **Minimal Apple-Style UI Polish** | Clean layout hierarchy, eliminate text wrapping glitches, ensure perfect tab bar routing, and verify typography alignment. |
| **Phase 7** | **Size & Performance Optimization** | Configure `abiFilters`, enable R8 minification and resource shrinking for release builds to produce an installable base APK well under 40 MB. |
| **Phase 8** | **Automated Verification & Tests** | Expand test suite to verify end-to-end speech, translation, and navigation flows. |
| **Phase 9** | **Deliverables & Signed Build** | Produce signed APK in `deliverables/`, generate SHA-256 checksum, and update documentation. |
