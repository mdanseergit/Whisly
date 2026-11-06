# Whisly — Offline Indian Language Speech-to-Speech Translator

Whisly (`com.aistudio.whisly.kxmpzq`) is a privacy-first, offline-capable Android application for real-time speech-to-speech, two-person conversation, and text translation across **12 Indian languages**.

---

## Key Features
- **3-Stage On-Device Pipeline (`STT → MT → TTS`)**:
  - **Speech-to-Text**: AI4Bharat IndicConformer Int8 (`16 kHz` PCM capture with hardware `NoiseSuppressor`, `AutomaticGainControl`, and Adaptive Hysteresis + ZCR VAD).
  - **Machine Translation**: Shared AI4Bharat IndicTrans2 Distilled 200M (`mt_indictrans2_200m_int8`) with Unicode NFC normalization, Indic digit canonicalization, and 32K SentencePiece subword formatting.
  - **Text-to-Speech**: AI4Bharat Indic-TTS VITS-Rasa (`22.05 kHz` phoneme-formant prosody synthesis + Android offline system TTS voice detection).
- **Atomic Model Pack Manager**:
  - Per-language STT and TTS checkpoints + deduplicated shared MT pack (`185.5 MB`).
  - Pre-flight `StatFs` storage checks, `.staging` downloads, SHA-256 verification, startup orphan cleanup, and `"Clear Extra"` bulk pack removal.
- **Brown & Peach Design System**:
  - Cohesive Warm Brown (`#49352B`), Deep Brown (`#29211E`), Primary Peach (`#EBAF96`), Secondary Peach (`#DFA58D`), and Cream (`#F8F2EC`) palette across Light and Dark themes.
  - Bold `Plus Jakarta Sans` ExtraBold `WHISLY` wordmark in the top bar, centered adaptive launcher icon, smooth 260ms splash exit transition, audio-reactive `StrandsGlassOrbCanvas`, and 60fps `WhislyGooeyNavBar`.
- **Encrypted Local History**:
  - Translation history is encrypted on-device using Android Keystore `AES/GCM/NoPadding` (`LocalCryptoManager.kt`). Raw microphone audio is never persisted to disk.

---

## Build & Verification Commands

### Assemble Debug APK
```bash
gradle :app:assembleDebug
```

### Run Unit & Robolectric Tests
```bash
gradle :app:testDebugUnitTest
```

---

## Documentation Index
- [`AUDIT.md`](./AUDIT.md) — Phase 0 codebase audit report and resolved issues.
- [`MODELS.md`](./MODELS.md) — On-device STT/MT/TTS model catalog, 12-language matrix, licenses, and `WER`/`CER`/`chrF++` mathematical formulas.
- [`ARCHITECTURE.md`](./ARCHITECTURE.md) — Clean Architecture + MVVM pipeline, Audio DSP, Keystore encryption, and atomic model manager design.
