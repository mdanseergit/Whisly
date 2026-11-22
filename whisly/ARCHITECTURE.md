# Whisly System Architecture (`ARCHITECTURE.md`)

## 1. High-Level Architecture
Whisly follows **Clean Architecture + MVVM** with unidirectional data flow (`StateFlow` + `collectAsStateWithLifecycle`) and zero main-thread blocking calls:

```
┌─────────────────────────────────────────────────────────────────────────┐
│ Presentation Layer (Jetpack Compose + Material 3 Brown-Peach Theme)     │
│  • WhislyTopBar (Plus Jakarta Sans ExtraBold)                           │
│  • WhislyGooeyNavBar (60fps Liquid Pill Navigation)                     │
│  • StrandsGlassOrbCanvas (Audio-Reactive Voice Orb)                     │
│  • Screens: Home, Translate, History, Profile, Languages, Settings      │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ StateFlow<TranslateUiState>
┌───────────────────────────────────▼─────────────────────────────────────┐
│ Domain & Pipeline Layer (`SpeechToSpeechPipeline`)                      │
│  • AudioProcessor (16kHz AudioRecord + NoiseSuppressor + AGC + VAD)     │
│  • IndicSttEngine (IndicConformer CTC/Attention + Confidence Gate)      │
│  • IndicTrans2Engine (Unicode NFC + Numeral Norm + 32K SentencePiece)   │
│  • VitsRasaTtsEngine (22.05kHz VITS-Rasa Prosody + Android TTS Fallback)│
│  • AiEvaluationSuite (WER, CER, chrF++, Held-Out Benchmark Runner)      │
└───────────────────────────────────┬─────────────────────────────────────┘
                                    │ Suspend / Flow (Dispatchers.IO / Default)
┌───────────────────────────────────▼─────────────────────────────────────┐
│ Data & Persistence Layer (`WhislyRepository` + `WhislyModelManager`)    │
│  • Room Database (`WhislyDatabase`: Languages, Packs, Phrasebook)       │
│  • Android Keystore AES-256-GCM Encrypted History (`LocalCryptoManager`)│
│  • Atomic `.staging` Download + SHA-256 Verification + Orphan Cleanup   │
└─────────────────────────────────────────────────────────────────────────┘
```

---

## 2. Audio Capture, DSP & Voice Activity Detection (`AudioProcessor.kt`)
1. **Capture Configuration**: Uses `AudioRecord` with `MediaRecorder.AudioSource.VOICE_RECOGNITION` at `16,000 Hz` mono `PCM_16BIT`.
2. **Hardware Pre-Processing**: Attaches Android `NoiseSuppressor` and `AutomaticGainControl` when supported by the device DSP.
3. **Pre-Emphasis & Normalization**: Applies a first-order high-pass filter ($y[n] = x[n] - 0.97 x[n-1]$) and peak gain normalization (`targetPeak = 0.88`).
4. **Adaptive Hysteresis VAD**: Combines frame RMS energy against the adaptive noise floor with Zero-Crossing Rate ($0.015 \le \text{ZCR} \le 0.42$) and a 4-frame hangover counter to prevent mid-word clipping.
5. **Audio Focus Playback**: Acquires `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` via `AudioFocusRequest` before playing synthesized speech at `22,050 Hz` (`VITS_NATIVE_SAMPLE_RATE_HZ`) and releases focus on completion or interruption.

---

## 3. Atomic Model Pack Management (`WhislyModelManager.kt`)
- **Startup Self-Healing**: `cleanupOrphanedStagingAndReverifyDiskTruth()` runs on startup to delete any leftover `.staging` files from interrupted downloads and cross-check every `InstalledModelEntity` against its on-disk file and SHA-256 digest.
- **Shared MT Reference Counting**: `canDeleteSharedMtPack()` prevents deleting `mt_indictrans2_200m_int8` while any installed language pack still depends on it.
- **Atomic Promotion**: Downloads stream to `<packId>.onnx.staging`, compute a streaming SHA-256 digest, compare against `ModelPackEntity.sha256Checksum`, and atomically rename to `<packId>.onnx` only on match.

---

## 4. Brand & Design System (`WhislyTheme.kt`)
- **Brown & Peach Palette**:
  - Warm Brown (`#49352B`) — Top bar & primary light-mode brand accent
  - Deep Brown (`#29211E`) — Primary cocoa typography & dark surface anchor
  - Primary Peach (`#EBAF96`) & Secondary Peach (`#DFA58D`) — Interactive pills, dark-mode primary accent, and verified borders
  - Cream Background (`#F8F2EC`) & Warm White (`#FFF9F5`) — Glare-free surfaces
- **Typography**: Locally bundled `Plus Jakarta Sans` (`res/font/plus_jakarta_sans.ttf`) powers the bold `WHISLY` top bar wordmark (`FontWeight.ExtraBold`) and display headers.
