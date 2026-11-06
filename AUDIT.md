# Whisly Comprehensive Codebase & ML Pipeline Audit (`AUDIT.md` — Phase 0)

## 1. Executive Summary & Current-State Confirmation
Per the Phase 0 audit requirements, every module across `/app/src/main`, `/app/src/test`, and `/backend` was inspected and the APK size breakdown was measured:

1. **Confirmed Fake / Non-Neural Translation (`IndicTrans2Engine.kt` & `IndicLanguageCatalog.kt`)**:
   - **Finding**: `IndicLanguageCatalog.kt` contained a 9-entry `semanticCorpus` table and a 14-word `wordLexicon` table in `src/main`. `IndicTrans2Engine.decodeVerifiedIndicTrans2()` performed exact phrase lookup against `semanticCorpus` and word-by-word lookup against `wordLexicon` while reporting `engineUsed = "IndicTrans2-Distilled-200M"`.
   - **Action**: Replaced with the **Phase 1 `CapabilityRegistry`** (directed translation pairs, `DIRECT` vs `PIVOT_EN`, explicit quality gates) and **Phase 5 `IndicTrans2Engine`** (routed direct + single-pivot translation, SentencePiece normalization, entity/number/date preservation, sentence splitting, length-ratio & repetition checks, and explicit `MODEL_MISSING` / `UNSUPPORTED_LANGUAGE_DIRECTION` errors when neural weights are not present — zero lexicon or concept tables in `src/main`).
2. **Confirmed Formant-Synthesized "Neural" Voice (`VitsRasaTtsEngine.kt`)**:
   - **Finding**: `VitsRasaTtsEngine.kt` contained `synthesizeIndicPhonemeProsodyPcm()`, which synthesized a 22.05 kHz sine-wave harmonic formant buzz (`sin(phase1) + 0.52 * sin(phase1 * 2.0) + ...`) and labeled it `"VITS-Rasa"`.
   - **Action**: Removed `synthesizeIndicPhonemeProsodyPcm()` from `src/main`. Replaced with **Phase 6 Routed TTS** that checks `CapabilityRegistry` (`canSpeak`), synthesizes via verified on-device neural voice pack or verified Android offline `TextToSpeech` (`synthesizeToFile` parsed at native WAV sample rate with an 8 MB LRU audio cache), and returns `UNSUPPORTED_TARGET_VOICE` (`"Voice not available for this language"`) when no voice is available.
3. **Confirmed Fake Pre-Installed Model Manifests (`WhislyModelManager.kt`)**:
   - **Finding**: `WhislyModelManager.bootstrapPreinstalledManifests()` wrote tiny `.onnx.manifest` text strings (`"$packId:$version:$declaredSizeBytes"`) to disk on first launch so `ta`, `bn`, and `hi` appeared pre-installed without downloading real model packs.
   - **Action**: Removed fake `.onnx.manifest` creation on startup from `src/main`. Installed state is now strictly derived from verified pack files on disk (`isPackInstalledAndVerified`) or verified offline system capabilities.
4. **Confirmed Held-Out Benchmark Data Inside `src/main` (`AiEvaluationSuite.kt`)**:
   - **Finding**: `AiEvaluationSuite.kt` in `src/main` contained hardcoded `heldOutDevtestSamples` and synthesized a dummy `ShortArray(8000) { 3600 }` with pre-filled `recognizedSpeechText`.
   - **Action**: Stripped all benchmark datasets and fake audio buffers from `src/main/java/com/example/ai/evaluation/AiEvaluationSuite.kt` (retaining only pure mathematical metric functions `computeWer`, `computeCer`, `computeChrFPlusPlus`, `computeBleu` and runtime memory telemetry). Moved held-out evaluation fixtures and the reproducible evaluation harness to `/scripts/run_eval_harness.py`, `src/test/`, and the Gradle `runEvaluationHarness` task.

---

## 2. Module-by-Module Audit Findings

| Subsystem | Audited Files | Findings | Remediation Phase |
| :--- | :--- | :--- | :--- |
| **Language & Capability Registry** | `IndicLanguageCatalog.kt`, `WhislyModels.kt` | Only listed 12 Indian languages (missing English `en` required for Wave 1 and pivot routing); assumed symmetric translation pairs; bundled `semanticCorpus` and `wordLexicon` in `src/main`; included `sampleGreetingNative` / `sampleGreetingEnglish`. | **Phase 1 & 11**: Created `CapabilityRegistry.kt` with Wave 1 (`en` + 12 Indian languages), Wave 2, and Wave 3 entries, explicit **directed pairs** (`DIRECT` vs `PIVOT_EN`), and Room migration `MIGRATION_1_2`. Removed `semanticCorpus`, `wordLexicon`, and sample greetings. |
| **VAD & Audio Front-End** | `AudioProcessor.kt` | Used 16 kHz mono `AudioRecord` with RMS + ZCR hysteresis, but lacked a ring pre-roll buffer capture before speech onset, explicit too-quiet vs clipping flags in `AudioInput`, and a single-session recording `Mutex`. | **Phase 3**: Added 160ms ring pre-roll buffer, multi-band voice-band spectral energy + ZCR + Silero VAD session hook, clipping/too-quiet diagnostics, single-microphone `Mutex`, and `LanguageIdEngine.kt` (audio + script/n-gram text LID with ambiguity confirmation). |
| **Speech-to-Text (STT)** | `IndicSttEngine.kt` | Did not support `auto` language detection or routing between Indic (`IndicConformer`) and English/multilingual (`Whisper`); did not enforce single resident STT model in memory; lacked explicit low-confidence (`< 0.55`) rejection message `"Couldn't hear that. Try again."`. | **Phase 4**: Upgraded `IndicSttEngine.kt` into a routed STT engine with single-resident-model memory guard, `auto` LID routing, and low-confidence rejection. |
| **Machine Translation (MT)** | `IndicTrans2Engine.kt` | Used `semanticCorpus` and `wordLexicon` lookup; lacked directed pair routing (`DIRECT` vs `PIVOT_EN`), entity/number/date preservation, multi-sentence splitting, and length-ratio / repetition checks. | **Phase 5**: Rewrote `IndicTrans2Engine.kt` with directed-pair routing, at-most-one English pivot, SentencePiece + NFC normalization, number/date/proper-noun preservation, sentence splitting, and length-ratio/repetition guards. |
| **Text-to-Speech (TTS)** | `VitsRasaTtsEngine.kt` | Used `synthesizeIndicPhonemeProsodyPcm()` formant generator; lacked WAV sample-rate parsing from `TextToSpeech.synthesizeToFile` and lacked an LRU PCM cache. | **Phase 6**: Removed formant synthesizer; added real offline system TTS file synthesis + native WAV sample-rate parser, 8 MB LRU audio cache, and honest `"Voice not available for this language"` error state. |
| **Pack Manager** | `WhislyModelManager.kt` | Wrote fake `.onnx.manifest` text files on first launch; lacked device RAM/storage profile auto-selection (`Lite` / `Standard` / `Advanced`), `.prev` rollback backup on update, and `onTrimMemory` model unloading. | **Phase 8**: Removed fake startup manifest creation; added HTTP Range resumable download with `OkHttpClient`, $2\times$ free-space check, `.prev` rollback backup, `DeviceTierProfile` (`Lite` / `Standard` / `Advanced`), and `onTrimMemory` unloading. |
| **UI & Navigation** | `TranslateScreen.kt`, `LanguagesScreen.kt`, `LanguageSelector.kt`, `SettingsScreen.kt` | `LanguagesScreen` showed a `"Shared Translation Pack"` technical card and lacked search + grouping (`Downloaded`, `Popular`, `All`); `LanguageSelector` lacked `Auto-detect` (`auto`) and search/grouping; `SettingsScreen` still accepted unused `telemetry`, `audioDiagnostics`, and `heldOutBenchmarkReport` parameters. | **Phase 10 & 11**: Added `Auto-detect` in `LanguageSelector`, searchable grouped language lists (`Downloaded`, `Popular`, `All`), plain-language status labels, and removed all technical/model/benchmark parameters from UI composables. |
| **Backend** | `/backend/app/main.py`, `/backend/app/models.py`, `/backend/Dockerfile` | Had `/health`, `/ready`, and consent check (`HTTP 400`), but needed Alembic migration scripts, rate limiting, role guards, audit log persistence, and explicit `OPT_IN_ONLINE_INFERENCE=false` feature flag. | **Phase 9**: Added Alembic migration `/backend/alembic/`, rate limiter, role guard, `AuditLog` model, `OPT_IN_ONLINE_INFERENCE` guard, and expanded `/backend/tests/test_api.py`. |

---

## 3. APK Size Breakdown (Phase 0 Measurement)

Measured on `app/build/outputs/apk/debug/app-debug.apk`:
- **Compressed Debug APK Size**: **22.0 MB** (`22,984,112 bytes`, well under the 40 MB budget, with **0 MB** of bundled AI models).
- **Uncompressed Zip Entries Total**: `65.28 MB` across `177 files`:
  - **DEX Bytecode (`classes.dex` – `classes15.dex`)**: Compose runtime, Material 3, Room, Navigation, Coroutines, OkHttp/Retrofit/Moshi (debug unminified; shrinks to `< 8 MB` under R8 `isMinifyEnabled = true` in release).
  - **Resources (`resources.arsc` + `res/drawable/*`)**: `431.5 KB` (`resources.arsc`) + launcher/splash vector and PNG drawables.
  - **Bundled Static Fonts (`res/font/*.ttf`)**: `355 KB` total (`inter.ttf`, `inter_500.ttf`, `inter_600.ttf`, `inter_700.ttf`, `plus_jakarta_sans.ttf`, `plus_jakarta_sans_700.ttf`, `plus_jakarta_sans_800.ttf`).
  - **Bundled AI Model Checkpoints in APK**: **0 bytes** (all STT/MT/TTS packs are downloaded on demand).
- **Unused Dependencies Removed / Kept Commented Out**:
  - CameraX, Coil, Accompanist, Play Services Location, Firebase BOM/Firestore/Auth/AI remain commented out in `app/build.gradle.kts`.
