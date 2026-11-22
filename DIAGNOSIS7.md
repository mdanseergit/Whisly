# Whisly Forensic Diagnosis & Root Cause Analysis (DIAGNOSIS7.md)

**App Package:** `com.aistudio.whisly.kxmpzq`  
**Target Architecture:** Android (minSdk 24, targetSdk 36, compileSdk 36)  
**Date:** November 19, 2026  
**Diagnosis Status:** VERIFIED WITH HARD EVIDENCE  

---

## 1. Problem O1: System SpeechRecognizer Dependency in "Offline" Mode

### Code Inspection & Grep Findings
Searching the repository for speech recognition implementations:
```bash
whisly\app\src\main\java\com\example\ai\stt\OnDeviceSpeechRecognizerHelper.kt:
  - android.speech.SpeechRecognizer
  - android.speech.RecognizerIntent
  - RecognizerIntent.EXTRA_PREFER_OFFLINE = true
  - RecognizerIntent.ACTION_RECOGNIZE_SPEECH
  - SpeechRecognizer.createSpeechRecognizer(context)
  - SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
whisly\app\src\main\java\com\example\presentation\WhislyViewModel.kt:
  - speechRecognizerHelper.startListening(languageCode, ...)
  - 11, 12, 13, 4, 2 -> PipelineErrorType.OFFLINE_SPEECH_PACK_MISSING
  - "Install Google offline speech pack in Android Settings for offline $sourceLangName, or type to translate."
```

### Exact Call Trace for the Voice Button
1. User taps Voice Button in `TranslateScreen` (`HeroMicrophoneControl` / `PortalVoiceOrb`).
2. Invokes `WhislyViewModel.toggleRecording()` -> `startMainRecording()`.
3. `startMainRecording()` delegates to:
   ```kotlin
   speechRecognizerHelper.startListening(
       languageCode = sourceLang,
       onRms = { ... },
       onPartial = { ... },
       onFinal = { ... },
       onError = { errCode -> ... }
   )
   ```
4. `speechRecognizerHelper` is an instance of `OnDeviceSpeechRecognizerHelper`.
5. It binds to the system `SpeechRecognizer` service (usually Google Speech Services `com.google.android.tts` or Samsung Voice) via `Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)`.
6. When airplane mode is enabled (mobile data and Wi-Fi OFF):
   - Android's system `SpeechRecognizer` requires local LPAC/Google offline speech models for the specific locale (e.g. `ta-IN` for Tamil, `bn-IN` for Bengali).
   - Commercial physical Android devices (Samsung, Xiaomi, Pixel, OnePlus, Vivo) **do not bundle Indic offline speech models in Android OS**.
   - The system service immediately aborts with error 11 (`ERROR_CANNOT_CHECK_SUPPORT`), 12, 13, 2 (`ERROR_NETWORK`), or 7 (`ERROR_NO_MATCH`).
   - `WhislyViewModel` maps this to `PipelineErrorType.OFFLINE_SPEECH_PACK_MISSING`, surfacing:
     `"Install Google offline speech pack in Android Settings for offline Tamil, or type to translate."`

### Root Cause Conclusion for O1
The application does not run a truly autonomous, embedded offline speech engine on raw PCM audio. It relies on the external Android system IPC service `android.speech.SpeechRecognizer`, which fails closed whenever the device is offline and lacks OS-level Google speech packs.

---

## 2. Problem Q1: Translation Quality Failure & Dissection of Reference Sentence

### Engine Origin
- **Class:** `RealOnDeviceTranslationService.kt`
- **Model:** Google ML Kit On-Device Translation (`com.google.mlkit:translate:17.0.3`), which downloads small on-device quantized models (~30 MB per language).
- **Execution Path:** `WhislyViewModel.routeOfflineNeuralTranslation()` -> `RealOnDeviceTranslationService.translate()`.

### Sentence Dissection & Parity Breakdown
- **INPUT:**
  `"my mom said I was very cute in my childhood but in my adulthood looks like begger"`
- **WHISLY APP OUTPUT (Current):**
  `"என் அம்மா நான் என் குழந்தை பருவத்தில் மிகவும் அழகாக இருந்தது ஆனால் என் வயது வந்தோர் பிச்சை போல் தெரிகிறது"`
- **GOOGLE TRANSLATE REFERENCE OUTPUT:**
  `"என் அம்மா, நான் சிறுவயதில் மிகவும் அழகாக இருந்ததாகச் சொன்னார்; ஆனால் இப்போது வளர்ந்த பிறகு, பார்ப்பதற்கு ஒரு பிச்சைக்காரனைப் போல இருக்கிறேன்."`

### Line-by-Line Defect Dissection
1. **Reported Speech ("said") Lost:**
   - Input: `"my mom said I was ..."`
   - App: `"என் அம்மா நான் ..."` (Missing `"சொன்னார்"` or `"என்று சொன்னார்"`). The reporting verb is completely omitted.
2. **"adulthood" Mistranslated as "Adults":**
   - Input: `"in my adulthood"`
   - App: `"என் வயது வந்தோர்"` (Literally "my adults / people who have come of age", a plural noun referring to adult people rather than the life stage of adulthood).
   - Correct Tamil: `"வளர்ந்த பிறகு"` or `"முதிர்வயதில்"`.
3. **Misspelled Source Word ("begger" -> "begging"):**
   - Input: `"looks like begger"` (spelling error in input).
   - App: `"பிச்சை போல் தெரிகிறது"` (Translated as "looks like alms / begging", an abstract action, not a person).
   - Correct Tamil: `"பிச்சைக்காரனைப் போல"` (a beggar, a person).
4. **Subject-Verb & Gender Agreement Mismatch:**
   - App: `"அழகாக இருந்தது"` (Neuter singular past: "it was beautiful", applied to a human speaker "I").
   - Correct Tamil: `"அழகாக இருந்தேன்"` (1st person singular) or reported form `"அழகாக இருந்ததாகச்"`.
5. **Punctuation & Clause Structure:**
   - Zero punctuation in current output; two distinct clauses joined by "but" collapsed into ungrammatical run-on sentence.

### Root Cause Conclusion for Q1
1. **Raw Typos Unhandled:** No preprocessing spell-correction exists for common English typographical variants (`begger` -> `beggar`).
2. **Compound Clause Degradation:** The sentence contains reported speech and compound clauses (`said ... but ...`). Without clause-aware punctuation normalization, the model drops the governing verb `"said"`.
3. **Model Capacity:** Standard ML Kit mobile int8 models struggle with long-tail compound sentence syntax without pre-normalization and post-processing formatting.

---

## 3. Problem S1: Poor Speech Recognition & Acoustic Model Analysis

### Acoustic Pipeline Inspection
- Current code in `WhislyViewModel.kt` lines 90-93 & 122-130:
  ```kotlin
  acousticRecognizerDelegate = { pcm16, sampleRate, lang ->
      recognizeSpeechOnDevice(pcm16, sampleRate, lang)
  }
  private fun recognizeSpeechOnDevice(pcm16: ShortArray, sampleRate: Int, lang: String): Pair<String, Float>? {
      if (pcm16.isEmpty()) return null
      val latest = speechRecognizerHelper.latestTranscript.value?.trim()
      if (!latest.isNullOrBlank()) {
          return Pair(latest, 0.95f)
      }
      return null
  }
  ```
- `IndicSttEngine.kt` defined an acoustic delegate interface, but the actual delegate simply read the string cached from `speechRecognizerHelper`!
- **No real acoustic neural network (e.g., Whisper, Conformer, or Vosk/sherpa-onnx) was executing on the PCM audio stream.** Audio recorded by `AudioRecord` was used solely to compute waveform RMS visualization, while speech transcription was offloaded to Android's `SpeechRecognizer`.

### Root Cause Conclusion for S1
The app lacks an embedded on-device speech inference engine (such as `sherpa-onnx` or on-device offline acoustic models). Real acoustic inference was not taking place within the app's process.

---

## 4. Problem D1: Voice Button Flat & Lacking Animation

### Composable Inspection: `PortalVoiceOrb.kt`
Lines 206-207:
```kotlin
val shouldRunClock = !reducedMotion && !StartupSwitchboard.isClockDisabled(context) &&
    (state != PortalOrbState.IDLE || jumpProgress.isRunning || pressScale.value < 1f)
```

### Why the Voice Button Appeared Flat and Static
1. **Idle Frame Clock Completely Stopped:**
   - The condition `(state != PortalOrbState.IDLE ...)` explicitly turned off the animation clock whenever the orb was `IDLE`.
   - On the Home screen and when waiting to speak on Translate, `state == PortalOrbState.IDLE`.
   - Result: `shouldRunClock` evaluated to `false`. The infinite animation frame millis loop never ticked, resulting in 0 animation frames and a completely static, flat circle.
2. **System Animation Scale (`ANIMATOR_DURATION_SCALE`):**
   - In `PortalVoiceOrb.kt`, `reducedMotion` queried:
     ```kotlin
     Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
     ```
   - On devices with "Remove animations" enabled or Battery Saver active, `ANIMATOR_DURATION_SCALE == 0`, disabling all procedural motion.
3. **Design Flaw:**
   - The component had no continuous breathing, no gentle idle halo, and no layered depth when idle.

---

## 5. Summary of Architecture Fixes for Subsequent Phases

| Problem | Root Cause | Target Architecture Solution |
| :--- | :--- | :--- |
| **O1 & S1** | Dependency on system `android.speech.SpeechRecognizer`; no real on-device ASR on PCM. | Replace system recognizer with **embedded offline speech recognition engine** (e.g. `sherpa-onnx` / Whisper / IndicConformer on-device) executing directly on 16kHz PCM16 audio. Completely eliminate external Google Speech pack requirement. |
| **Q1** | Typo vulnerability ("begger"), loss of reported speech ("said"), compound clause collapse. | Add **pre-processing English typo & punctuation normalizer** (`begger` -> `beggar`, clause segmentation), evaluate **IndicTrans2** vs **ML Kit** with beam search & post-processing grammar alignment, and enforce fail-closed output guards. |
| **D1** | `shouldRunClock` stopped during IDLE; flat rendering. | Redesign as the **"Aura" orb** (Phase 4): 88dp primary-orange core, peach glow, thin concentric rings, continuous <=30fps idle breathing, real RMS mic level pulse, rotating gold arc during processing, and zero Canvas allocations. |
