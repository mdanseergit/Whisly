# Whisly App Stack Map — Pre-Voice-Reset Baseline

Date: 2026-11-22. Status: Step 0 deliverable. This document is an evidence map of the current working tree only. It does not claim device measurements, model accuracy, clean-install offline behaviour, or production readiness.

Evidence method: local source files were read/grepped; package and artifact hashes were computed with `Get-FileHash`; library/model licence pages were fetched from primary sources; previous build/test output from `.\gradlew.bat assembleDebug testDebugUnitTest` is recorded in `TEST_REPORT.md` and is being refreshed after this document. Device/network/logcat evidence is **UNMEASURED**.

---

## 1. Plain-language explanation

Whisly is a Jetpack Compose Android app. The UI collects a source and target language, records the microphone, shows a transcript, translates text, and optionally speaks the translated text. There are two overlapping voice paths in the current code:

1. **Main/conversation push-to-talk path** — `WhislyOfflineSpeechRecognizer` owns the microphone, applies VAD, then calls a `SttDecoder`. In `WhislyViewModel`, that decoder is `SherpaOnnxSttEngine.decode()`. It talks to the sherpa-onnx native runtime and needs model files on disk. No speech model is bundled in the app today, so this path fails closed with a model error unless a user has imported a ZIP package.
2. **Legacy pipeline path** — `SpeechToSpeechPipeline` coordinates `IndicSttEngine`, `IndicTrans2Engine`, and `VitsRasaTtsEngine`. Its STT/MT engines are routers with delegates; the delegates are supplied by `WhislyViewModel`. Translation is delegated to Google ML Kit. TTS uses Android's offline `TextToSpeech` synthesis when a non-network voice is installed; no neural TTS model is bundled.

Typed translation uses ML Kit (`com.google.mlkit:translate:17.0.3`, source-visible code in `RealOnDeviceTranslationService.kt`). ML Kit translation itself runs on device, but the base models are downloaded and managed through Google Play Services; Google's ML Kit terms also say the APIs may contact Google servers for model/bug-fix/accelerator information and send performance/utilisation metrics. Live traffic has not been captured.

History is written to Room with AES-GCM encryption via Android Keystore (`WhislyRepository.recordEncryptedHistory`, `KeystoreCryptoManager`). Settings, saved phrases, language catalogue, directed pairs, model-pack rows, and pending correction rows are also Room entities. Crash-loop protection, historical exit reasons, and an in-app Exit Report screen are present in `MainActivity`, `CrashProtectionManager`, `ExitReportManager`, and `ExitReportScreen`.

The planned reset removes all user-import/ZIP model flows and all fake/obsolete speech code, then bundles one real multilingual Whisper Tiny INT8 model inside the app. Until that work and on-device measurements exist, no language is proven accurate and the app must not claim “offline from install” without a clean-install airplane-mode test.

---

## 2. Layer map

| Layer | Files | Observed responsibility | Keep / replace / delete |
|---|---|---|---|
| Entry/lifecycle | `app/src/main/java/com/example/MainActivity.kt` | Splash, Compose host, crash handler, exit-reason capture, `onTrimMemory`, `onStop` | Keep lifecycle/crash hooks; adjust only if voice memory lifecycle changes |
| Navigation | `core/navigation/WhislyNavigation.kt` | Routes: home, conversation, text mode, languages, download manager, saved/history, settings, about, exit report, safe mode | Keep routes; remove import callbacks/UI wiring |
| UI/theme | `presentation/**`, `components/**`, `ui/theme/**`, `core/theme/**` | Existing screens, mic button, transcript/translation cards, voice orb, colours/tokens | Preserve design; functional mic/transcript/error changes only after pipeline works |
| State/wiring | `presentation/WhislyViewModel.kt` | Owns `AudioProcessor`, `WhislyOfflineSpeechRecognizer`, `SherpaOnnxSttEngine`, `ModelImportManager`, lazy legacy pipeline, Room flows, error banners | Keep typed/settings/history behaviour; remove import/ZIP state and simplify voice wiring |
| Audio front end | `ai/audio/AudioProcessor.kt` | `AudioRecord` capture, permission/hardware/muted checks, VAD, voiced segment extraction, `AudioTrack` playback, waveform state | Keep/review; it is the only microphone owner |
| Push-to-talk recognizer | `ai/stt/WhislyOfflineSpeechRecognizer.kt` | One microphone session, 30 s decode timeout, VAD/hardware error ordering, calls `SttDecoder`, returns `SttOutcome` | Keep concept; rebuild around bundled decoder and exact error strings |
| sherpa-onnx decoder | `ai/stt/SherpaOnnxSttEngine.kt` | Reads `whisly-stt.json` from app files, builds `OfflineRecognizer`, greedy decode, one resident recognizer, `unload()` | Rebuild for bundled assets; remove manifest-driven user packages |
| Legacy STT router | `ai/stt/IndicSttEngine.kt` | Capability/pack checks, acoustic delegate, one-resident pack, LID disambiguation | Delete/replace after clean voice module is introduced |
| Legacy MT router | `ai/translation/IndicTrans2Engine.kt` | Directed pair validation, normalisation/token preview, one-resident pack ID, calls delegate | Keep typed translation routing only as needed; no fabricated output |
| ML Kit translation | `ai/translation/RealOnDeviceTranslationService.kt` | `RemoteModelManager.download/isModelDownloaded/delete`, `Translator.downloadModelIfNeeded`, on-device translate | Keep; document one-time model fetch honestly |
| Legacy TTS | `ai/tts/VitsRasaTtsEngine.kt` | 8 MB PCM LRU cache, capability gate, offline system `TextToSpeech` fallback, WAV parse, playback | Delete old VITS/formant naming; rebuild TTS only after STT proof |
| Model import | `ai/stt/ModelImportManager.kt`, `ai/stt/SttPackageManifest.kt`, `presentation/settings/SpeechModelsSection.kt` | User-picked ZIP, manifest/hash verification, atomic install/remove, import UI | Delete entirely; add grep regression guard |
| Model download | `ai/modelmanager/WhislyModelManager.kt`, `WhislyDownloadService.kt`, `presentation/languages/*`, `presentation/settings/SettingsScreen.kt` | ML Kit pack install plus generic OkHTTP chunked downloader, foreground service, pack state/pause/resume/cancel | Remove custom speech-pack downloads; keep only approved model lifecycle |
| Registry/catalogue | `ai/registry/CapabilityRegistry.kt`, `ai/registry/ModelCapability.kt`, `ai/translation/IndicLanguageCatalog.kt` | Language capabilities, directed pairs, seed entities, stale model URLs | Keep language list; replace claimed pack rows/URLs with bundled truth |
| Persistence/security | `data/local/**`, `data/repositories/WhislyRepository.kt`, `core/security/KeystoreCryptoManager.kt` | Room seeded data, encrypted history, saved phrases, settings, SHA-256 helpers | Keep |
| Crash/recovery | `core/crash/**`, `presentation/safemode/**`, `core/debug/StartupSwitchboard.kt` | Handler install, launch stability window, exit reasons, safe mode, debug switches | Keep |
| Backend declarations | `data/remote/WhislyBackendApi.kt`, `data/local/Entities.kt` (`backendBaseUrl`) | Retrofit DTOs/interface for optional control plane; no call site found in current source | Do not enable; user forbids backend/accounts/billing without approval |

---

## 3. Observed voice data flow

### 3.1 Main / conversation microphone turn

1. `WhislyViewModel.startMainRecording()` or `toggleConversationTurnRecording()` checks hardware, permission, muted state, and stops TTS before recording (`WhislyViewModel.kt:461-527`, `733-820`).
2. `WhislyOfflineSpeechRecognizer.startListening()` repeats the hardware/permission/muted checks, checks `CapabilityRegistry.canListen`, resets state, and opens `AudioProcessor.startRecordingDetailed()` with RMS/elapsed callbacks (`WhislyOfflineSpeechRecognizer.kt:102-157`).
3. `AudioProcessor` supplies `AudioInput` with `pcm16Samples`, `vadSpeechDetected`, `voicedSegmentPcm16`, peak/RMS, clipping and too-quiet flags.
4. `stopAndRecognize()` stops capture once, cancels the capture scope, applies a 30 s timeout, and calls `evaluate()` (`WhislyOfflineSpeechRecognizer.kt:167-201`).
5. `evaluate()` checks empty/all-zero/short/clipped audio **before** declaring no speech. Only after real audio with `vadSpeechDetected=false` does it return `SPEECH_NOT_DETECTED` or `AUDIO_TOO_QUIET`. A missing decoder returns `STT_MODEL_MISSING`, never a speech message (`WhislyOfflineSpeechRecognizer.kt:224-310`).
6. The supplied decoder is `SherpaOnnxSttEngine.decode()`: finds a manifest-installed model, converts shorts to floats, creates an `OfflineStream`, calls `acceptWaveform(FloatArray, Int)`, `decode`, and `getResult`; one recognizer is kept resident (`SherpaOnnxSttEngine.kt:83-181`).
7. The ViewModel sanitises transcript whitespace/capitalisation, optionally runs `LanguageIdEngine` for `auto`, shows the transcript for confirmation, then calls `confirmTranscriptAndTranslate()` (`WhislyViewModel.kt:433-459`, `530-688`).
8. `isPairReadyOffline()` checks ML Kit download state / Room language state / verified pack IDs; failure currently shows “Download … pack to translate offline”, which must be reworded for bundled models (`WhislyViewModel.kt:138-151`, `619-631`).
9. `SpeechToSpeechPipeline.translateAndSynthesize()` serialises MT/TTS on `Dispatchers.Default.limitedParallelism(1)` plus a `Mutex`, preserves transcript on MT failure, and checks cancellation between stages (`SpeechToSpeechPipeline.kt:115-165`).
10. `IndicTrans2Engine` validates the directed pair, pack IDs, one resident MT pack ID, normalisation, and delegates the actual translation to `RealOnDeviceTranslationService` (`IndicTrans2Engine.kt:46-249`).
11. `VitsRasaTtsEngine.synthesize()` gates on `CapabilityRegistry.canSpeak`, tries a neural delegate (not attached in production) then a verified offline system voice, caches PCM, and `AudioProcessor.playPcmAudio()` plays it (`VitsRasaTtsEngine.kt:109-305`).
12. Successful voice turns are stored through `repository.recordEncryptedHistory(...)` with mode `VOICE` (`WhislyViewModel.kt:660-668`).

### 3.2 Legacy pipeline path

`SpeechToSpeechPipeline.processRecordedAudioToTranscript()` calls `IndicSttEngine.recognize()`, which checks VAD, capability, verified pack ID, and delegate, then returns `SpeechResult` or a typed error (`SpeechToSpeechPipeline.kt:71-108`, `IndicSttEngine.kt:38-202`). This path is not the one used by the current mic button; the reset should delete it rather than keep two STT owners.

---

## 4. Network evidence table

Live network capture is **UNMEASURED**. The table describes code paths and primary-source documentation only.

| Path | Code evidence | What may leave the device / be fetched | Current trigger | Install-offline truth |
|---|---|---|---|---|
| ML Kit translation model download | `RealOnDeviceTranslationService.kt:31`, `69-81`, `116-118`; `WhislyModelManager.kt:265-298` | Google Play Services-managed base model for `en, ta, hi, bn, mr, te, kn, gu, ur` (map at lines 33-45); models documented as ~30 MB and downloaded on demand | User taps install/download; `translate()` also calls `downloadModelIfNeeded()` | Translation is on-device once the model exists; clean-install airplane-mode translation is **UNMEASURED** |
| ML Kit service traffic | `developers.google.com/ml-kit/terms`, `installation-paths` (fetched 2026-11-22) | May contact Google for bug fixes, updated models, accelerator info; sends performance/utilisation metrics | Managed by Play Services / API lifecycle | Not “zero network ever”; do not claim it |
| Generic model-pack HTTP downloader | `WhislyModelManager.kt:25-26`, `328-389`, `413-454`; URLs in `CapabilityRegistry.kt:816-905` and `assets/whisly_manifest.json` | OkHTTP GET (with Range), SHA-256 verify, copy to `filesDir/whisly_models` | `install(packId)` has no current UI call site; language install uses ML Kit instead | Not part of a clean install; must be removed with custom pack flow |
| Foreground download notification | `WhislyDownloadService.kt`, manifest service entry | No network itself; coordinates pack install progress | `installLanguagePacksWithProgress()` | Remove when custom downloads are removed |
| ZIP speech-model import | `ModelImportManager.kt:62-190`, `SpeechModelsSection.kt:57-61`, `203-222` | No network; reads a user-selected local URI; verifies SHA-256 and writes app files | User picks a ZIP | Must be deleted under reset |
| Backend control-plane API | `data/remote/WhislyBackendApi.kt`; `Entities.kt:165` `https://api.whisly.ai/api/v1` | Declared DTOs/endpoints only; no call site found | Not triggered in observed source | Do not enable without explicit approval |
| Connectivity observation | `components/WhislyBrandHeader.kt:114-135`, `WhislyViewModel.kt:257-263` | Reads local network state only | App start, unless debug `disable_network` | Not an upload |
| About links | `core/config/AppConfig.kt:11-12` | HTTPS links to portfolio/GitHub | User action in About screen | Not automatic model traffic |

### Required clean-install proof (user-only steps)

1. Factory-reset or uninstall Whisly; install the new bundled APK/AAB on the target phone.
2. Enable airplane mode before first launch; leave Wi-Fi and mobile data off.
3. Launch, grant microphone permission, record a short English phrase, then a Tamil phrase; paste the exact transcript/error and `logcat` lines.
4. Type a sentence and translate English→Tamil and Tamil→English; paste the output.
5. Use Settings telemetry to record `lastSttMs`, `lastMtMs`, peak RAM, and app storage; paste screenshots or exported values.
6. Keep airplane mode on for at least 10 minutes; paste whether any ML Kit/download/service notification or error appeared.

---

## 5. Model / runtime evidence table

| Component | Version / source | Licence evidence | Current bundled state | Real measured facts |
|---|---|---|---|---|
| sherpa-onnx Android runtime | `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.5`; dependency at `app/build.gradle.kts:134`; official tag `v1.13.5` | Apache-2.0 from `https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/v1.13.5/LICENSE` fetched 2026-11-22 | Native `.so` files present in prior debug APK: `libonnxruntime.so` 21,684,880 B; `libsherpa-onnx-c-api.so` 4,453,168 B; `libsherpa-onnx-cxx-api.so` 440,744 B; `libsherpa-onnx-jni.so` 4,757,872 B; ABI filter `arm64-v8a` | APK snapshot `deliverables/whisly-latest-debug.apk`: 72,215,111 B, SHA-256 `6E44A80D820FA6F41ED0253974E2B84588055C5D57C865746159D1ADA895625B`; obsolete after reset |
| OpenAI Whisper Tiny multilingual INT8 | Official release `sherpa-onnx-whisper-tiny.tar.bz2`, tag `asr-models`; tarball 116,204,861 B, SHA-256 `c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1` | OpenAI Whisper `LICENSE` is MIT (primary source fetched earlier); sherpa-onnx packaging Apache-2.0 | Not bundled; only referenced by URL in code/manifest | Extracted local files: `tiny-encoder.int8.onnx` 12,937,772 B SHA-256 `d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434`; `tiny-decoder.int8.onnx` 89,855,401 B SHA-256 `d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925`; `tiny-tokens.txt` 816,730 B SHA-256 `b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126` |
| Obsolete ZIP package | `deliverables/whisly-whisper-tiny-stt.zip` | Same model sources, but user-import flow is rejected by reset | 60,746,305 B, SHA-256 `D00EA6ADB3C9E0375B1B4B1E73FDDF084662C91BCE34EEFCD2126D199C0E0C31` | Must be removed from user-facing flow and documentation |
| ML Kit Translate | `com.google.mlkit:translate:17.0.3`, `app/build.gradle.kts:136` | ML Kit Terms of Service (fetched 2026-11-22): on-device input processing; Google may contact servers for fixes/models/accelerator info and metrics | Library bundled; base models downloaded on demand | Live download size, exact endpoint traffic, and offline clean-install behaviour **UNMEASURED** |
| IndicTrans2 200M / M2M100 pack IDs | Registry strings only (`CapabilityRegistry.kt:31-42`, `816-905`) | Declared MIT in code; exact checkpoint licence/source/hash not independently verified in this pass | No files in `assets/` (`whisly_manifest.json` is the only asset) | No real model bytes on disk; translation delegate is ML Kit |
| VITS/Coqui/Inflect TTS pack IDs | Registry strings and stale manifest URLs | Licences declared in code; exact checkpoint hashes not independently verified in this pass | Not bundled | Production TTS currently uses Android offline system voice, not these checkpoints |
| Android system TTS | `android.speech.tts.TextToSpeech` | Device/vendor supplied; only voices with `!isNetworkConnectionRequired` are accepted | Not bundled | Per-device voice availability **UNMEASURED** |
| Gated IndicConformer | Not present | No verified distributable copy | Not bundled | Do not adopt without user approval and verified hash/licence comparison |

Current `app/src/main/assets/` contains only `whisly_manifest.json` (3,349 B, SHA-256 `13CB34049A3F59F687F502A405F9949DC00AC3FC307ABCAB7197897D48DCA8C8`), and that manifest contains stale download URLs and unsupported claims. It must be replaced or deleted, not treated as source of truth.

---

## 6. Capability table per language

Definitions:

- **Real** — production code path calls a real engine with a real local model already bundled/verified for this install. No language qualifies yet.
- **Partial** — real engine code exists, but a required model, clean-install proof, or measurement is absent.
- **Missing** — current code explicitly blocks or cannot route the capability.
- **Fake** — hard-coded/preset/canned transcript, formant “speech”, or circular decoder observed in current source. None observed in current source after prior cleanup; historical fake paths are listed in §7 and must be guarded against.

`en-IN` in the requirement maps to the current `en` registry entry; no literal `en-IN` language record exists today. Voice/LID/TTS availability below is from `CapabilityRegistry` and `RealOnDeviceTranslationService.mapToTranslateLanguage`, not device measurement.

| Code | Language | Typed translation (code evidence) | STT | TTS | Real | Partial | Missing | Fake |
|---|---|---|---|---|---|---|---|---|
| `en` / required `en-IN` | English / Indian English | Real engine via ML Kit when model downloaded; code supports `en` | `canListen=true`; decoder wired but no bundled model → Partial | `canSpeak=true`; offline system voice conditional → Partial | none | STT + TTS + conditional MT | clean-install proof | none observed |
| `ta` | Tamil | ML Kit map includes `ta`; model download required | `canListen=true`; Whisper path unwired-to-bundled → Partial | `canSpeak=true`; offline voice conditional → Partial | none | typed MT + STT + TTS | bundled model, WER, clean-install proof | none observed |
| `bn` | Bengali | ML Kit map includes `bn` | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | typed MT + STT + TTS | bundled model + measurements | none observed |
| `hi` | Hindi | ML Kit map includes `hi` | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | typed MT + STT + TTS | bundled model + measurements | none observed |
| `te` | Telugu | ML Kit map includes `te` | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | typed MT + STT + TTS | bundled model + measurements | none observed |
| `kn` | Kannada | ML Kit map includes `kn` | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | typed MT + STT + TTS | bundled model + measurements | none observed |
| `ml` | Malayalam | Registry declares MT, but ML Kit map has no `ml`; delegate returns null → fails closed | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | STT + TTS framework | real typed MT with current engine | none observed |
| `mr` | Marathi | ML Kit map includes `mr` | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | typed MT + STT + TTS | bundled model + measurements | none observed |
| `gu` | Gujarati | ML Kit map includes `gu` | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | typed MT + STT + TTS | bundled model + measurements | none observed |
| `or` | Odia | Registry declares MT, ML Kit map lacks `or` → fails closed | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | STT + TTS framework | real typed MT with current engine | none observed |
| `as` | Assamese | Registry declares MT, ML Kit map lacks `as` → fails closed | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | STT + TTS framework | real typed MT with current engine | none observed |
| `pa` | Punjabi | Registry declares MT, ML Kit map lacks `pa` → fails closed | `canListen=true`; no bundled model → Partial | `canSpeak=true`; offline voice conditional → Partial | none | STT + TTS framework | real typed MT with current engine | none observed |
| `sat` | Santali | Registry declares MT, ML Kit map lacks `sat` → fails closed | `canListen=true`; no bundled model → Partial | `canSpeak=false` explicitly | none | STT framework | typed MT, TTS | none observed |
| `ur` | Urdu | ML Kit map includes `ur` | `canListen=false` explicitly | `canSpeak=false` explicitly | none | typed MT | STT + TTS | none observed |
| `ne` | Nepali | Registry declares MT, ML Kit map lacks `ne` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `sa` | Sanskrit | Registry declares MT, ML Kit map lacks `sa` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `brx` | Bodo | Registry declares MT, ML Kit map lacks `brx` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `doi` | Dogri | Registry declares MT, ML Kit map lacks `doi` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `ks` | Kashmiri | Registry declares MT, ML Kit map lacks `ks` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `kok` | Konkani | Registry declares MT, ML Kit map lacks `kok` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `mai` | Maithili | Registry declares MT, ML Kit map lacks `mai` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `mni` | Manipuri | Registry declares MT, ML Kit map lacks `mni` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |
| `sd` | Sindhi | Registry declares MT, ML Kit map lacks `sd` → fails closed | `canListen=false` | `canSpeak=false` | none | none | all speech + real typed MT | none observed |

No WER/CER, false accept/reject, native-speaker rating, RAM, latency, cold-start, or logcat result is available. Mark these **UNMEASURED** until the user supplies device and clips using the steps in §8.

---

## 7. Dead code and stale claims to remove in Step 1

### Delete or disable

- `ai/stt/ModelImportManager.kt` — user ZIP import and `removePack`.
- `ai/stt/SttPackageManifest.kt` — user-import manifest/schema.
- `presentation/settings/SpeechModelsSection.kt` — “Import speech model (.zip)” UI and progress/failure/install states.
- `ImportUiState` and `importSpeechModel`, `dismissImportState`, `removeSpeechModel`, `refreshInstalledSttPacks`, `isSpeechModelInstalled`, `installedSttPacks` in `WhislyViewModel.kt` where they only serve import.
- Import callbacks in `core/navigation/WhislyNavigation.kt` and `presentation/settings/SettingsScreen.kt`.
- Custom speech-pack download UI/actions in `presentation/languages/LanguagesScreen.kt`, `DownloadManagerScreen.kt`, and corresponding `WhislyModelManager`/`WhislyDownloadService` paths once no approved model lifecycle needs them.
- `app/src/main/assets/whisly_manifest.json` and model-pack URL rows in `CapabilityRegistry.buildInitialModelPacks()` that imply downloads.
- `deliverables/whisly-whisper-tiny-stt.zip` from user-facing flow/docs; retaining an inert file is not an app feature.
- `data/remote/WhislyBackendApi.kt` should remain unimplemented/dead; do not wire it.
- Stale claims in root `MODELS.md`, `whisly/MODELS.md`, `licenses/NOTICE`, `docs/MODEL_LICENSING_AND_ARCHITECTURE.md`, `deliverables/ZERO_COST_AUDIT.md`: “100% On-Device, Zero-Cost, Zero Cloud Dependencies”, IndicConformer/VITS model tables claimed as verified, “pre-corrector (`begger` -> `beggar`)”, Tamil semantic post-processing, “install Google offline speech pack”, and any “import a model” instruction.

### Historical fake/stub identifiers — must stay absent

`acousticDecoder`, `recognizedSpeechText`, canned/preset transcripts, circular `latestTranscript` decoder, sine/formant “phoneme prosody” TTS. A grep regression test must fail if any returns in `app/src/main` or production test fixtures.

### Keep

ML Kit typed translation and its regression sentences; Room schema/DAO/repository/recovery; encrypted history and Keystore rotation; settings; navigation shell; theme/visual components; crash/exit/safe-mode code; Android permissions needed for microphone and observed network state.

---

## 8. Exact user-only measurement steps still pending

1. **Git safety**: the workspace and `whisly/` subdirectory are not git repositories (`git status` returned `fatal: not a git repository`). Confirm whether to initialise a repo and commit/tag `pre-voice-reset`, create a separate backup, or proceed without a git safety point. No deletion until answered.
2. **Device identity and system logs**: provide phone model, Android version, ABI, and logcat from app launch through one voice turn (`adb logcat -v threadtime com.example:* *:E`) plus in-app Exit Report screenshots.
3. **Speech clips and references**: provide short real clips for English and Tamil (and any later language), plus native-speaker-reviewed `references.csv`. Do not invent references or validated BLEU/chrF++/WER/CER numbers.
4. **Translation regression**: confirm the existing 24 English sentences and the three added sentences (`my mom said I was very cute in my childhood but in my adulthood looks like begger`, `i need to know where is the theatre`, `I need water`) remain the locked inputs; supply reviewed target references if scores are required.
5. **Clean-install airplane-mode proof**: use the six steps in §4.
6. **Binary size decision**: after bundling is measured, approve one option if limits are exceeded: Play Asset Delivery install-time pack, arm64-v8a-only release split, smaller model tier, or optional language packs.
7. **TTS decision**: confirm whether temporary Android offline system voices are acceptable for the first proof, or whether a verified bundlable TTS model must be selected before voice output is enabled.
8. **Visual redesign approval**: confirm when to start the orb/Apple-inspired redesign; no redesign before the pipeline works.

---

## 9. Required user-visible error strings

Keep these exact titles/messages from `domain/models/WhislyModels.kt` or update only with explicit user approval:

- `Microphone permission needed`
- `Microphone is blocked or muted`
- `Speak a little louder or closer`
- `Couldn't hear that. Try again.` — only for analysed audio with no speech
- `Couldn't understand. Try again.` — only for low confidence/decode failure
- `Speech isn't available for <language> yet` — unsupported source language, typing still works

Never show `install Google offline speech pack` or any command that tells the user to import a model.

---

## 10. Cheat sheet

| Item | Value |
|---|---|
| Build command | `.\gradlew.bat assembleDebug testDebugUnitTest` from `whisly/` |
| Java home used earlier | `C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2` |
| Application id | `com.aistudio.whisly.kxmpzq` |
| Namespace / package root | `com.example` |
| ABI filter | `arm64-v8a` |
| Target / compile SDK | 36 / release(36) minor 1 |
| Gradle project | `C:\Users\danse\OneDrive\document\Whisly\whisly` |
| Voice safety tag required before deletion | `pre-voice-reset` |
| Current microphone owner | `WhislyOfflineSpeechRecognizer` via `AudioProcessor` |
| Current decoder | `SherpaOnnxSttEngine` (manifest-installed files only) |
| Current translation engine | `RealOnDeviceTranslationService` (ML Kit 17.0.3) |
| Current TTS engine | `VitsRasaTtsEngine` (offline Android system fallback in production) |
| Bundled Whisper source files | `tiny-encoder.int8.onnx`, `tiny-decoder.int8.onnx`, `tiny-tokens.txt` |
| Required regression grep terms | `acousticDecoder`, `recognizedSpeechText`, preset/canned transcript, ZIP model import |
| Do not enable | cloud STT/TTS, backend, accounts, billing, secrets, uploads, gated weights |

---

## 11. Supplemental read-only inventory

A separate read-only inventory covered 58 production Kotlin files, 9 unit-test files, 2 instrumented-test files, Gradle/config files, `AndroidManifest.xml`, bundled assets, and licence documents. It performed **no live traffic capture, no device run, and no build/test execution**; the build/test result in this document is from the command run after Step 0. The additions below are static source findings.

### 11.1 Additional files and classifications

| Item | Evidence | Mapping decision |
|---|---|---|
| `app/src/main/java/androidx/core/splashscreen/SplashScreen.kt` | Local shim inside the app namespace, not a library class | Keep unless replaced by the real AndroidX artifact; do not confuse with a dependency |
| `components/AuraVoiceOrb.kt` `PortalVoiceOrb` compatibility wrapper at lines 499-511 | No callers found; active orb usage is `MicrophoneButton.kt:98` and `HomeScreen.kt:47,172` | Obsolete shim; remove during dead-code cleanup, not during the functional voice rebuild |
| `ui/theme/Theme.kt` `MyApplicationTheme` at lines 7-12 | Delegates to `WhislyTheme`; no callers found | Obsolete unused wrapper; remove only with visual cleanup approval |
| `ai/lid/LanguageIdEngine.kt` | Unicode-script plus character/word n-gram heuristic, no fastText model | LID is a heuristic, not a measured/verified model; keep `auto` behaviour fail-closed |
| `ai/evaluation/AiEvaluationSuite.kt` | Pure metric calculators only; no benchmark corpus in `src/main` | Keep as measurement utilities; do not treat generated numbers as measured results |
| `data/local/WhislyDatabase.kt` migrations 1→2, 2→3, 3→4 | Non-destructive migration plus resets of fake installed states and English seeding | Keep/recovery code; migration history must survive the reset |
| `data/local/Entities.kt:160,165` | `offlineOnlyMode = true`; `backendBaseUrl = "https://api.whisly.ai/api/v1"` | Keep local setting; backend URL is string-only and must remain unimplemented |
| `AboutWhislyScreen.kt:66-81` | Safe external URL opener for `AppConfig` links | Keep; user-initiated browser action, not model traffic |
| `core/crash/CrashProtectionManager.kt:145-172` | Live destructive model reset used by `SafeModeScreen.kt:188`; preserves history/settings via `WhislyDao.kt:65-72` | Keep, but update its model-path assumptions when bundled assets replace downloaded packs |
| `settings.gradle.kts:17-23` | `https://jitpack.io` repository | Build-time dependency resolution only; not app runtime traffic |

### 11.2 Canned/preset text in previews

Canned-looking transcript or translation strings were found **only in `@Preview` composables**, not in production pipeline code:

- `presentation/translate/TranslateScreen.kt:324-325`, `:360-361`
- `presentation/text_translation/TextTranslationScreen.kt:303-304`, `:327-328`
- `presentation/conversation/ConversationScreen.kt:462-463`
- `presentation/saved/SavedAndHistoryScreens.kt:516-517`, `:542-543`

These still violate the “no preset/canned transcripts” rule if left in source, even though they are preview fixtures. Step 1 should remove or replace them with clearly labelled non-transcript placeholders (or delete the previews) and extend the grep regression guard so it distinguishes preview fixtures from real engine output.

### 11.3 Existing test guards to preserve or replace

| Test | Evidence | Action |
|---|---|---|
| `ExampleUnitTest.kt:336-356` | Source-scan guard forbidding fake translation symbols in `src/main/java` | Keep/extend with the new voice regression terms |
| `ExampleUnitTest.kt:359-371` | Silence/noise must never produce `"வணக்கம்"` or `"Hello"` | Keep |
| `TranslationRegressionLockTest.kt:331-353` | Asserts no canned Tamil rewrites | Keep; references still user-supplied/UNMEASURED |
| `OfflineZeroNetworkGuardTest.kt:29+`, `:97-113` | No `android.speech.SpeechRecognizer`; local typo/postprocess behaviour | Keep but update wording around “zero network” to match ML Kit reality |
| `SpeechModelImportTest.kt:13+` | Pins ZIP import manifest SHA-256/size/LICENSE rules | Delete/replace when ZIP import is removed |
| `LaunchAndRecoveryPreventionTest.kt:156` | Exercises `clearDownloadedModelPacks` | Keep/adapt to bundled model reality |
| `androidTest/RealEngineAuditTest.kt:11-18`, `:51` | Explicitly states device/emulator with Play Services and downloaded models is required; status **UNMEASURED** | Keep as a device-only audit definition; do not promote to measured without a run |

### 11.4 Documentation conflicts

- `whisly/MODELS.md:12,16-17,38-50` contains HTTP-200 and “Unmeasured in CI” claims that are documentation assertions, not observed results.
- `whisly/docs/MODEL_LICENSING_AND_ARCHITECTURE.md:7-12` conflicts with `licenses/NOTICE:10-33` on accepted/rejected libraries. Treat `licenses/NOTICE` as the checked-in notice until primary-source verification updates both.
- Root `NOTICE` points to the full notice; `licenses/NOTICE:3-6` defines the licence gate and `:10-16` rejects Piper/fastText/MADLAD, consistent with the “no GPL/non-commercial engines” rule.

No conclusion in this addendum upgrades any device-dependent or network-capture item from **UNMEASURED**.
