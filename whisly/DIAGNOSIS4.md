# DIAGNOSIS 4: Translation, Speech Recognition & Navigation Architecture Root Cause Analysis

**Date:** 2026-11-07  
**Target:** Whisly Android App (`com.aistudio.whisly.kxmpzq` / `com.example`)  
**Scope:** Translation engine, Speech-to-Text engine, Santali auto-install bug, Download Manager collapsed UI, and navigation back/exit arrow coverage.

---

## 1. Executive Summary & Core Root Causes

| Issue | Reported Symptom | Root Cause Discovered | Exact File & Line | Status |
|---|---|---|---|---|
| **E1 & E3** | "I need water" -> Tamil outputs "நான் நஎஎட தண்ணீர்" with broken grammar and letter-by-letter transliteration. | `WhislyViewModel` delegates translation to `IndicNeuralTranslationDecoder.decode()`. This object contains a hardcoded `WORD_MAP` (which hits "I" -> "நான்" and "water" -> "தண்ணீர்") and a fallback `transliterateWord()` that letter-by-letter phonetic-maps "need" (`n`=ந, `e`=எ, `e`=எ, `d`=ட -> "நஎஎட"). Zero neural inference is executed. | `IndicNeuralTranslationDecoder.kt:219-230`, `332-353`, `410-416`<br>`WhislyViewModel.kt:126-134` | **PROVED** |
| **E2** | Voice input always returns "Hello" or "வணக்கம்" ("vannakkam") regardless of speech or silence. | `WhislyViewModel.recognizeSpeechOnDevice` is a hardcoded stub that does not run acoustic neural inference. It checks peak amplitude and returns a hardcoded string: `"ta" -> "வணக்கம்"`, `"bn" -> "নমস্কার"`, `"hi" -> "नमस्ते"`, else `"Hello"`. | `WhislyViewModel.kt:105-124` | **PROVED** |
| **B1** | Santali ("sat") is automatically added/marked installed on download. | In `WhislyModelManager.cleanupOrphanedStagingAndReverifyDiskTruth`, `diskVerified` is calculated as `sharedMtVerified && sttOk && ttsOk`. Santali has `ttsPackId = null` (so `ttsOk` is `true`), and shares `stt_whisper_tiny_multilingual` and `shared_mt_indic_indic`. When any language pack using the shared models is verified, Santali's verification check passes 100%, causing Santali to be marked `isInstalled = true` and `DownloadState.Installed` without user consent. | `WhislyModelManager.kt:140-145`<br>`CapabilityRegistry.kt:351-369` | **PROVED** |
| **B2** | Download Manager UI appears collapsed when visiting language model page. | When `DownloadManagerScreen` has no active/queued/paused/completed downloads, all download sections evaluate to empty, rendering only the storage card with huge blank space. Furthermore, the button to open Download Manager in `LanguagesScreen` has no label when downloads are 0 (`16.dp` empty icon button), and navigation back points to `Languages` rather than `Home`. | `DownloadManagerScreen.kt:101-115`, `158-162`<br>`LanguagesScreen.kt:164-180` | **PROVED** |
| **B3** | Sub-pages lack exit arrows to exit back to Home. | `TranslateScreen`, `ConversationScreen`, `ProfileScreen`, and `SavedPhrasesScreen` have no back/exit button leading to `HomeScreen`. Other screens pop back to `TranslateScreen` or previous stacks instead of exiting to `Home`. | `WhislyTopBar.kt:451-501`<br>`WhislyNavigation.kt:320-348` | **PROVED** |

---

## 2. Translation Diagnosis (Phase 0 Items a, b, c)

### a) Reproduction of "I need water" -> Tamil & Execution Trace
- **Input sentence:** `"I need water"`
- **Source:** `"en"` (English)
- **Target:** `"ta"` (Tamil)
- **Engine invoked:** `IndicTrans2Engine` -> calls `neuralSessionDelegate` -> calls `IndicNeuralTranslationDecoder.decode()`.
- **Trace breakdown:**
  1. Token 1: `"I"` -> looks up `WORD_MAP["i"]["ta"]` -> returns `"நான்"`.
  2. Token 2: `"need"` -> misses `WORD_MAP` -> calls `transliterateWord("need", "ta")` -> calls `transliterateToTamil("need")`:
     - `'n'` -> `"ந"`
     - `'e'` -> `"எ"`
     - `'e'` -> `"எ"`
     - `'d'` -> `"ட"`
     - Result: `"நஎஎட"`
  3. Token 3: `"water"` -> looks up `WORD_MAP["water"]["ta"]` -> returns `"தண்ணீர்"`.
  4. Joined result: `"நான் நஎஎட தண்ணீர்"` (Exact character match to user evidence E1).
- **Correct Tamil:** `"எனக்கு தண்ணீர் வேண்டும்"` (Enakku thanneer vendum).

### b) Codebase Audit for Lexicons, Dictionaries, and Fallback Paths
A complete grep across `whisly/app/src/main/` revealed all occurrences:
1. `IndicNeuralTranslationDecoder.kt:21-172`: `PHRASE_DICTIONARY` — hardcoded phrase map (hello, how are you, thank you, welcome, etc.).
2. `IndicNeuralTranslationDecoder.kt:174-217`: `NAME_TRANSLITERATION` — hardcoded names (danseer -> டான்சீர் / দানসীর / दानसीर, mohammed -> முகம்மது, whisly -> விஸ்லி).
3. `IndicNeuralTranslationDecoder.kt:219-230`: `WORD_MAP` — hardcoded dictionary (my, name, is, i, you, what, where, good, friend, water).
4. `IndicNeuralTranslationDecoder.kt:265-285`: Hardcoded regex regex `"my name is <name>"`.
5. `IndicNeuralTranslationDecoder.kt:405-467`: Character-by-character phonetic transliteration tables (`transliterateToTamil`, `transliterateToDevanagari`, `transliterateToBengali`, etc.).
6. `WhislyViewModel.kt:126-134`: `routeOfflineNeuralTranslation` passes inputs directly to `IndicNeuralTranslationDecoder.decode()`.
- **Reachable Flows:**
  - `TranslateScreen` (Voice Translate confirmation -> `confirmTranscriptAndTranslate`)
  - `TextTranslationScreen` (`translateTextModeDirectly`)
  - `ConversationScreen` (`submitConversationTextTurn` and voice turn processing)

### c) Neural Model Verification on Disk & Runtime
- **Model files on disk:** ZERO `.onnx`, `.tflite`, or `.bin` neural models exist on disk in the application bundle or files directory.
- **Dependencies:** `build.gradle.kts` declares ZERO machine learning inference runtime dependencies (no `com.microsoft.onnxruntime`, no `com.k2fsa.sherpa.onnx`, no `mlkit`).
- **Download URLs in Seed:** In `CapabilityRegistry.kt:741`, `PACK_MT_INDIC_INDIC` pointed to `sherpa-onnx-whisper-tiny.tar.bz2` (an ASR archive, not an MT model), proving that neural MT model download was previously placeholder infrastructure.

---

## 3. Speech Recognition Diagnosis (Phase 0 Items d, e)

### d) Speech Recognition Constancy Test & Hardcoded Stub Identification
- **Engine invoked:** `IndicSttEngine` -> calls `acousticRecognizerDelegate` -> calls `WhislyViewModel.recognizeSpeechOnDevice`.
- **Constancy Test Trace:**
  - Input 1: Spoken English audio clip -> Output: `"Hello"`
  - Input 2: Spoken Tamil audio clip -> Output: `"வணக்கம்"`
  - Input 3: Spoken Hindi audio clip -> Output: `"नमस्ते"`
  - Input 4: Spoken Bengali audio clip -> Output: `"নমস্কার"`
  - Input 5: Spoken Kannada audio clip -> Output: `"ನಮಸ್ಕಾರ"`
  - Input 6: 10s of background noise / speech -> Output: `"Hello"` or `"வணக்கம்"` (depending on language parameter)
- **Source of stub:** `WhislyViewModel.kt:105-124`:
  ```kotlin
  val decoded = when (lang.lowercase()) {
      "ta" -> "வணக்கம்"
      "bn" -> "নমস্কার"
      "hi" -> "नमस्ते"
      "te" -> "నమస్కారం"
      "kn" -> "ನಮಸ್ಕಾರ"
      "ml" -> "നമസ്കാരം"
      else -> "Hello"
  }
  return Pair(decoded, conf)
  ```
- **Finding:** No acoustic neural model is loaded or executed. The recognizer is an explicit mock returning canned greeting strings.

### e) Silence & VAD Behavior
`AudioProcessor.preprocessAndRunVad` performs energy/hysteresis VAD correctly in code, but because `WhislyViewModel.recognizeSpeechOnDevice` generates synthetic confidence and text when invoked, the user experience always results in "Hello" or "வணக்கம்".

---

## 4. Santali ("sat") Auto-Add Bug Diagnosis

- **File:** `whisly/app/src/main/java/com/example/ai/modelmanager/WhislyModelManager.kt:140-145`
- **Root Cause:**
  ```kotlin
  val sttOk = lang.sttPackId.isBlank() || isPackInstalledAndVerified(lang.sttPackId)
  val ttsOk = lang.ttsPackId == null || isPackInstalledAndVerified(lang.ttsPackId)
  val diskVerified = sharedMtVerified && sttOk && ttsOk
  ```
  In `CapabilityRegistry.kt:351-369`:
  - `sttPackId = "stt_whisper_tiny_multilingual"` (shared)
  - `mtPackId = PACK_MT_INDIC_INDIC` (shared)
  - `ttsPackId = null`
  Because Santali has `ttsPackId == null`, `ttsOk` is automatically `true`. Whenever the shared multilingual pack is downloaded for any language, Santali's `sttOk` and `sharedMtVerified` become `true`. Consequently, `diskVerified` evaluates to `true` for Santali, silently setting `isInstalled = true` and `DownloadState.Installed` without the user ever requesting Santali.

---

## 5. Download Manager UI & Navigation Diagnosis

1. **Collapsed Download Manager UI:**
   In `DownloadManagerScreen.kt:101-115`, when active, queued, paused, and completed lists are empty, `LazyColumn` renders zero item cards, showing only the device storage box and empty white/dark space. There is no list of downloadable models or empty state indicator.
2. **Missing Exit Arrow across Pages:**
   - `TranslateScreen`: Uses `WhislyTopBar` which has the Whisly logo, connectivity pill, and theme toggle, but no back/home navigation arrow.
   - `ConversationScreen`: Has no back/home navigation arrow.
   - `ProfileScreen`: Has no back/home navigation arrow.
   - `SavedPhrasesScreen`: Has no back/home navigation arrow.
   - `LanguagesScreen`, `DownloadManagerScreen`, `HistoryScreen`, `SettingsScreen`, `AboutWhislyScreen`: Navigate to arbitrary previous backstack entries rather than guaranteeing exit to `HomeScreen`.

---

## 6. Action Plan & Architectural Strategy

1. **Phase 1 — Delete Fake Engines & Enforce Fail-Closed:**
   - Remove `IndicNeuralTranslationDecoder.kt` completely.
   - Eliminate hardcoded greetings in `WhislyViewModel.recognizeSpeechOnDevice`.
   - Update `IndicTrans2Engine` and `IndicSttEngine` to strictly fail closed: return `PipelineErrorType.MODEL_MISSING` / `PipelineErrorType.SPEECH_NOT_DETECTED` with user-friendly messages when models are missing or unverified.
   - Add unit tests verifying no word-list/canned paths exist.
2. **Phase 2 — Real On-Device Translation:**
   - Integrate on-device zero-cost neural translation (Google ML Kit On-Device Translation API / AI4Bharat on-device pipeline).
   - Zero API keys, zero network fees, 100% on-device inference once downloaded.
   - Support English, Tamil, Hindi, Bengali, Marathi, Telugu, Gujarati, Kannada, etc.
   - Reject identity echo and transliteration artifacts.
3. **Phase 3 — Real Speech Recognition:**
   - Implement Android platform on-device SpeechRecognizer engine (`android.speech.SpeechRecognizer` with `EXTRA_PREFER_OFFLINE`) coupled with `AudioProcessor` VAD and audio validation.
   - Constancy test: 5 distinct speech inputs produce distinct transcripts; silence produces zero text.
4. **Phase 4 — Fix Santali & Download Manager Bugs:**
   - Track explicit user installation intent in Room for each language pack (`userRequestedInstall: Boolean`).
   - Guard Santali so it is never auto-installed by shared resource checks.
   - Enhance `DownloadManagerScreen` to display available language models and informative states when queues are empty.
5. **Phase 5 — Navigation Exit Arrow on All Sub-Pages:**
   - Add a standardized Back/Exit arrow to `WhislyTopBar` and all sub-screens (`Translate`, `Conversation`, `Languages`, `DownloadManager`, `History`, `Saved`, `Profile`, `Settings`, `About`) that navigates reliably back to `HomeScreen`.

