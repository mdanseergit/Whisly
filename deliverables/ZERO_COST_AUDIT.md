# Whisly — Zero-Cost Audit and Guardrails

Date: 2026-11-21. Prepared per the Zero-Cost Engineering Plan. No production code changes in this document; it is the required deliverable before further changes. All claims verified against the current working tree.

## 1. Dependency and service cost inventory

| Component | Purpose | Exact version | Licence | Cost | Account/card? | External data? | Verdict |
|---|---|---|---|---|---|---|---|
| com.google.mlkit:translate | On-device MT | 17.0.3 | Apache-2.0 (Google) | FREE | No (Play Services on device) | Models downloaded via Play Services | FREE VERIFIED |
| com.google.mlkit:language-id | Language ID (not wired) | (not in build.gradle) | Apache-2.0 (Google) | FREE | No | Models via Play Services | NOT IMPLEMENTED |
| sherpa-onnx (k2-fsa) | STT/TTS runtime | 1.13.5 (JitPack) | Apache-2.0 | FREE | No | None (local) | FREE VERIFIED (runtime only) |
| Whisper weights (OpenAI) | STT model | int8, exact checkpoint TBD | MIT | FREE | No | None once local | LICENCE UNVERIFIED (exact checkpoint + hash not yet recorded) |
| AI4Bharat IndicConformer | STT model | MIT, gated HF repo | MIT (claimed) | FREE | YES (HF account + contact-info sharing to access gated repo) | None once local | LICENCE UNVERIFIED (access gated; must use verified copy + hash compare) |
| Android TextToSpeech | TTS | Framework | System | FREE | No | Voice packs may be downloaded by user via system | FREE WITH LIMITATIONS |
| Piper engine | TTS engine | GPL | GPL | FREE | No | None | NOT SHIPPED (GPL incompatible with release) |
| MMS-TTS voices | TTS voices | CC-BY-NC-4.0 | CC-BY-NC | FREE | No | None | NOT SHIPPED (non-commercial) |
| Android Room / DataStore / Keystore | Local storage | androidx.room (libs.versions.toml) | Apache-2.0 | FREE | No | None | FREE VERIFIED |
| OkHttp / Retrofit / Moshi | HTTP/JSON (unused for network in offline mode) | okhttp 4.12.0, retrofit (libs), moshi (libs) | Apache-2.0 | FREE | No | None unless called | FREE VERIFIED (no calls in offline mode) |
| Google services plugin | Firebase config (unused) | (plugin) | — | FREE | No | None | FREE WITH LIMITATIONS (plugin present, no firebase deps active) |
| WorkManager | Background work | (transitive) | Apache-2.0 | FREE | No | None | FREE VERIFIED |
| Firebase / Supabase / AWS / Azure / GCP | Cloud | NOT USED | — | — | — | — | NOT IMPLEMENTED (prohibited) |

Transitive dependencies: all from androidx.*, kotlin.*, com.google.mlkit, com.k2fsa.sherpa-onnx. No analytics, crash reporting, or auth SDKs are active (firebase.* lines are commented out in build.gradle.kts).

## 2. Licence findings and source links

- sherpa-onnx: https://github.com/k2-fsa/sherpa-onnx/blob/master/LICENSE — Apache-2.0. Verified 2026-11-21.
- ML Kit Translation: https://developers.google.com/ml-kit/language/translation — on-device, ~30 MB/lang, 59 languages, pivots through English. Free, no key.
- Whisper: https://github.com/openai/whisper — MIT. Exact checkpoint, int8 conversion, size, Android compatibility: UNVERIFIED.
- IndicConformer: AI4Bharat, MIT claimed. Upstream HF repo is gated (requires accepting contact-info sharing). Do NOT accept on user's behalf. Use a verified copy + hash compare.
- Piper: GPL-3.0 engine → not shipped.
- MMS-TTS: CC-BY-NC-4.0 → not shipped.
- Android system TTS voices: license varies per voice; must test offline (airplane mode) per voice.

## 3. Zero-cost architecture diagram (text)

```
[Mic] -> AudioRecord (16kHz mono PCM16) -> VAD (Silero/WebRTC)
  -> STT router: sherpa-onnx (Whisper/IndicConformer, only if model files verified on disk)
     -> editable transcript
  -> Translation router: ML Kit on-device (only if model downloaded via Play Services)
     -> editable translation
  -> TTS router: Android TextToSpeech (only if selected voice is offline-verified)
     -> playback

Storage: Room + DataStore + Keystore (all local). No network in offline mode.
Network: ONLY user-initiated model downloads (ML Kit via Play Services, or local file import).
No backend, no analytics, no crash reporting, no auth, no sync.
```

## 4. Model choice comparison for Tamil STT

| Candidate | License | Size | Android runtime | Status |
|---|---|---|---|---|
| OpenAI Whisper (int8) | MIT | ~base 75 MB / small 244 MB (int8) | sherpa-onnx | UNVERIFIED (no device test) |
| AI4Bharat IndicConformer (per-lang int8) | MIT (gated upstream) | ~? (600M multilingual int8 = 882 MB; per-lang TBD) | sherpa-onnx | UNVERIFIED (access gated, no hash) |
| Android platform on-device recognizer | System | System-managed | Framework (API 31+) | BLOCKED (relies on system packs = system-settings flow, forbidden) |

Selection rule: measured WER on ≥30 real clips (FLEURS/Common Voice/Kathbath) including noisy, plus size/RAM/latency on a real device. Targets: WER ≤25–30%, false-accept <5%, false-reject <5%. Do not lower. Tamil first; do not promise 22 languages.

## 5. Model download / hosting / import recommendation

- First option: LOCAL IMPORT. User selects a model package file; app validates manifest, required files, size, SHA-256; stages; atomically installs only after verification; rejects corrupt packs. No network, no hosting, no account.
- Optional public hosting: best-effort/conditional only. User creates their own public HF repo (I provide steps, do NOT create it). URLs pinned to a commit hash. Upstream LICENSE + attribution included. Compare file hashes with upstream. No SLA, no unlimited bandwidth promise.
- ML Kit translation models: managed by Play Services `RemoteModelManager` (already wired, honest indeterminate progress).

## 6. APK size and device performance risks

- Current debug APK: 68.9 MB (arm64-only) with sherpa-onnx. Above the 40 MB target.
- ML Kit translate_jni.so: 16 MB. sherpa-onnx native libs: ~25 MB. Compose/AndroidX: ~20 MB.
- Mitigation options: make sherpa-onnx optional (dynamic feature module) or accept larger APK; measure release (R8 + shrinkResources) size before deciding. Do not promise 40 MB without measurement.

## 7. One-time distribution fees vs recurring operating costs

- Google Play Console: one-time $25 registration fee (paid by developer, not recurring). Not an operating cost but a real one-time fee. Do not claim "entirely free to publish".
- No recurring Whisly infrastructure cost: no backend, no cloud, no paid APIs, no subscriptions.
- F-Droid / sideloading: $0.

## 8. Remaining blockers and next permitted phase

- S0: audit complete (this document). Real engine audit UNMEASURED (needs device).
- S1: schema export done; v1–v3 schemas missing (blocked on device upgrade test).
- S2: exit report wired; crash not closed (needs your phone data).
- S3: ML Kit downloads honest; custom pack downloader not started (blocked on approved model source).
- S4: STT scaffold only; needs model files + device test.
- S5: language ID not wired; TTS voice offline verification not done.

Next permitted phase: S4 (STT) once (a) you provide model files or approve HF hosting steps, and (b) device test data is available. Do not proceed to backend or redesign until speech works on your phone.

### Step to create HF repo (for your action, not mine)

1. Create a free Hugging Face account (no card).
2. Create a public model repository (e.g. `yourname/whisly-stt-models`).
3. Generate a fine-grained read token (no write needed for the app; you upload via web UI).
4. Upload model files + upstream LICENSE + attribution + SHA-256SUMS.
5. Pin to a commit hash; give me the URL.
6. I will compare file hashes with upstream and record them in MODELS.md + licenses/NOTICE.

I will NOT create the account or upload anything.## Phase S0-S6 implementation update (2026-11-22)

- Debug APK: 72,417,067 bytes (69.06 MB), SHA-256 3A8ED6B44C104DCC7C785E2C9966B68BD68FB9B926F7389CE81CC9746A136001 at deliverables/whisly-latest-debug.apk.
- Unit tests: 39 passing.
- STT: WhislyOfflineSpeechRecognizer awaits a suspending decode; no fixed 400 ms race; errors typed.
- sherpa-onnx 1.13.5 runtime wired via SherpaOnnxSttEngine; user-imported model packages validated by ModelImportManager + SttManifestParser.
- TTS: isSystemVoiceAvailableOffline requires a real non-network Voice.
- LID: auto-detect runs LanguageIdEngine and never translates automatically when ambiguous.
- UI: Settings gained a Speech Models section. Translate banner routes to Settings for missing speech models.
- Orb: idle clock stopped; particles audio-reactive only while recording.
- Still UNMEASURED: WER/latency/RAM, chrF++, zero-network proof, crash/exit-info logs, real device measurement, references.csv review.
## Whisper Tiny integration package (2026-11-22)

- Source: https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2
  (tarball SHA-256 c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1, matches CapabilityRegistry).
- Extracted: tiny-encoder.int8.onnx, tiny-decoder.int8.onnx, tiny-tokens.txt (multilingual, not tiny.en).
- Locally calculated SHA-256:
  - tiny-encoder.int8.onnx  d24fb083ae3b1041fc24e97971d60e280c9342201fbb67b0ab428a8b4a51a434 (12,937,772 bytes)
  - tiny-decoder.int8.onnx  d2fece8dd42771f1df975c6c0445770d0c292bf7547c2cae04a6c0cc57540925 (89,855,401 bytes)
  - tiny-tokens.txt         b34b360dbb493e781e479794586d661700670d65564001f23024971d1f2fa126 (816,730 bytes)
  - LICENSE                 326ea6417b648fe8e262f55c9e9823ee5cc8437de565963ddf8159afd4a36533 (516 bytes)
- Packaged as deliverables/whisly-whisper-tiny-stt.zip with whisly-stt.json manifest (schema v1, packId stt_whisper_tiny_multilingual).
- Licences included: OpenAI Whisper (MIT) + sherpa-onnx (Apache-2.0).
- This is the initial integration/evaluation model. Indian-language accuracy is UNMEASURED.
- No gated terms accepted, no hosting created, no uploads.
