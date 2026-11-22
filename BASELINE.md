# Whisly Verification Baseline Report (`BASELINE.md` — Phase 0)

## 1. Environment & Toolchain Status
- **Host Operating System**: Windows (AMD64)
- **Application ID**: `com.aistudio.whisly.kxmpzq`
- **Compile SDK**: `release(36) { minorApiLevel = 1 }`
- **Target SDK**: `36`
- **Min SDK**: `24` (Android 7.0+)
- **Build Output Target**: `Whisly-v1.0.0-debug.apk` / `deliverables/whisly-1.0.0-debug-preview.apk`
- **Signing Keystore**: Android Debug Keystore (`CN=Android Debug, O=Android, C=US`)
- **Signature Schemes**: APK Signature Scheme v2 (Block ID `0x7109871a`, Cert Fingerprint `1C082CB9023DF78BDBF59957CD00DD7DE2B1266DC818AC8D9BE945EE681E3D4D`)

## 2. APK Size & Component Breakdown (Measured via Archive Inspection)
- **Compressed File Size**: `20,519,775 bytes` (~20.5 MB)
- **Uncompressed Total**: `65,435,245 bytes` (~65.4 MB)
- **Total Archive Entries**: 177 files
- **DEX Bytecode**: 18 files (`classes.dex` through `classes18.dex`), uncompressed total `64,123,804 bytes`
- **Native Shared Libraries (`.so`)**: 8 binaries across 4 architectures:
  - `lib/arm64-v8a/libandroidx.graphics.path.so` (`10,096 bytes`)
  - `lib/arm64-v8a/libdatastore_shared_counter.so` (`7,112 bytes`)
  - `lib/armeabi-v7a/libandroidx.graphics.path.so` (`7,252 bytes`)
  - `lib/armeabi-v7a/libdatastore_shared_counter.so` (`4,416 bytes`)
  - `lib/x86/libandroidx.graphics.path.so` (`9,284 bytes`)
  - `lib/x86/libdatastore_shared_counter.so` (`5,148 bytes`)
  - `lib/x86_64/libandroidx.graphics.path.so` (`10,760 bytes`)
  - `lib/x86_64/libdatastore_shared_counter.so` (`6,224 bytes`)
- **Bundled Fonts**: 7 TTF files (`res/font/*.ttf`), total `355,356 bytes`:
  - `inter.ttf` (`66,592 bytes`), `inter_500.ttf` (`66,692 bytes`), `inter_600.ttf` (`66,808 bytes`), `inter_700.ttf` (`66,788 bytes`)
  - `plus_jakarta_sans.ttf` (`29,468 bytes`), `plus_jakarta_sans_700.ttf` (`29,500 bytes`), `plus_jakarta_sans_800.ttf` (`29,508 bytes`)
- **Bundled AI Model Weights**: **0 bytes** (strict compliance: all STT/MT/TTS packs download on demand).

## 3. Supported Languages from Registry (`CapabilityRegistry.kt`)

| Code | Name | Script | Wave | Listen (`canListen`) | Speak (`canSpeak`) | Status / Notes |
| :--- | :--- | :--- | :--- | :---: | :---: | :--- |
| `en` | English | Latin | WAVE_1 | **Yes** | **Yes** | STT: Whisper Base Int8 / TTS: Kokoro-82M + System |
| `ta` | Tamil | Tamil | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `bn` | Bengali | Bengali-Assamese | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `hi` | Hindi | Devanagari | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `te` | Telugu | Telugu | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `kn` | Kannada | Kannada | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `ml` | Malayalam | Malayalam | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `mr` | Marathi | Devanagari | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `gu` | Gujarati | Gujarati | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `or` | Odia | Odia | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `as` | Assamese | Bengali-Assamese | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `pa` | Punjabi | Gurmukhi | WAVE_1 | **Yes** | **Yes** | STT: IndicConformer v2 / TTS: VITS-Rasa v1 + System |
| `sat` | Santali | Ol Chiki | WAVE_1 | **Yes** | **No** | Honest target: no TTS pack available (`canSpeak = false`) |
| `ur` | Urdu | Perso-Arabic | WAVE_3 | **No** | **No** | Text-only; marked `BELOW_THRESHOLD` for speech-to-speech |
| `ne` | Nepali | Devanagari | WAVE_3 | **No** | **No** | Text-only; marked `BELOW_THRESHOLD` for speech-to-speech |
| `sa` | Sanskrit | Devanagari | WAVE_3 | **No** | **No** | Text-only; marked `BELOW_THRESHOLD` for speech-to-speech |

## 4. Engine & Model Pipeline Classification

| Subsystem | Class & File | Implementation Nature | Real Model vs. Stand-In Status |
| :--- | :--- | :--- | :--- |
| **Audio DSP & VAD** | `AudioProcessor` (`com.example.ai.audio.AudioProcessor`) | **Real DSP**: 16 kHz Mono AudioRecord, multi-band spectral flux, ZCR, adaptive noise floor, 160ms pre-roll, 240ms hangover, clipping & too-quiet checks. | **REAL DSP / Energy VAD** |
| **Language Identification** | `LanguageIdEngine` (`com.example.ai.lid.LanguageIdEngine`) | **Real Heuristic & LID**: Unicode script ranges, character n-grams, acoustic candidate scoring, top-2 disambiguation margin (0.15 threshold). | **REAL Script/N-Gram LID** |
| **Speech-to-Text (STT)** | `IndicSttEngine` (`com.example.ai.stt.IndicSttEngine`) | **Architectural Router**: Routes between `IndicConformer` and `Whisper`. Requires downloaded pack file on disk (`isPackInstalledAndVerified`). | **ROUTER / REQUIRES DOWNLOADED WEIGHTS** (No fake text generator; returns `STT_LOW_CONFIDENCE` or `UNSUPPORTED_SOURCE_LANGUAGE` when missing). |
| **Machine Translation (MT)** | `IndicTrans2Engine` (`com.example.ai.translation.IndicTrans2Engine`) | **Directed Route MT**: Strictly enforces directed pairs via `CapabilityRegistry`. Supports `DIRECT` and `PIVOT_EN`. Entity masking for numbers/dates/names, length-ratio & repetition checks. | **ROUTER / REQUIRES DOWNLOADED WEIGHTS** (Zero hardcoded lexicons or concept tables; returns `MODEL_MISSING` or `TRANSLATION_FAILURE` when weights absent). |
| **Text-to-Speech (TTS)** | `VitsRasaTtsEngine` (`com.example.ai.tts.VitsRasaTtsEngine`) | **Routed Synthesizer**: Connects to on-device vocoder or Android system offline `TextToSpeech.synthesizeToFile` with native WAV sample rate parsing and 8 MB LRU cache. | **REAL SYSTEM FALLBACK / REQUIRES VOCODER WEIGHTS** (Formant sine buzzer completely removed). |
| **Model Pack Manager** | `WhislyModelManager` (`com.example.ai.modelmanager.WhislyModelManager`) | **Atomic Model Pack Storage**: SHA-256 pre-flight, staging download, `.prev` rollback, orphan cleanup. | **REAL STORAGE & INTEGRITY MANAGER** |
| **Security & Crypto** | `KeystoreCryptoManager` (`com.example.core.security.KeystoreCryptoManager`) | **Hardware Keystore**: `AES/GCM/NoPadding` 256-bit key stored in Android Keystore, 12-byte IV prepended. | **REAL CRYPTO** |

