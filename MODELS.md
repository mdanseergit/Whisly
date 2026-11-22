# Whisly On-Device Models & AI Architecture (MODELS.md)

**App Package:** `com.aistudio.whisly.kxmpzq`  
**Version:** 1.0.6 (Code: 6, Name: "1.0.6")  
**Target:** 100% On-Device, Zero-Cost, Zero Cloud Dependencies  

---

## 1. Architectural Overview

Whisly operates as an autonomous, private, on-device speech-to-speech translator. It does not require any paid APIs, API keys, or cloud servers.

```
       [Mic Hardware: 16kHz Mono PCM16]
                       │
                       ▼
            [AudioProcessor.kt]
         ┌─────────────┴─────────────┐
         ▼                           ▼
[Live RMS Normalizer]       [Continuous VAD Engine]
         │                   - Adaptive Energy Floor
         │                   - Zero-crossing Rate
         │                   - Silence / Clipping Guards
         ▼                           │
[AuraVoiceOrb Canvas]                ▼
  - Peach Radial Glow      [WhislyOfflineSpeechRecognizer.kt]
  - 48 Circular Points       - Silence Fail-Closed Guard
  - 3.2s Sine Breathing      - Filler Word Removal
                             - Initial Capitalization
                                     │
                                     ▼
                          [IndicTrans2Engine.kt]
                             - Typo Pre-Corrector ("begger" -> "beggar")
                             - Reported Speech Clause Normalizer
                             - On-Device Neural Session (ML Kit / ONNX)
                             - Tamil Semantic Post-Processor
                               - "பிச்சைக்காரனைப் போல" (beggar vs alms)
                               - "நான் வளர்ந்த பிறகு" (adulthood vs adults)
                               - "சொன்னார்" (reported speech preservation)
                                     │
                                     ▼
                            [VitsRasaTtsEngine.kt]
                             - Native Vocoder Speech Synthesis
```

---

## 2. On-Device Model Specifications & Manifest

| Task | Model Architecture | Pack ID | Quantization | Size | Runtime / Backend | License & Attribution |
| :--- | :--- | :--- | :---: | :---: | :--- | :--- |
| **STT** | Whisper Tiny Multilingual | `stt_whisper_tiny_multilingual` | Int8 ONNX | 110.8 MB | sherpa-onnx / ONNX Runtime Mobile | MIT (OpenAI / k2-fsa) |
| **MT** | Google ML Kit On-Device Translation | `mlkit_translate_<lang>` | Quantized | ~30 MB / pair | Google ML Kit On-Device | Apache-2.0 / Google |
| **MT** | IndicTrans2 200M Distilled | `mt_indictrans2_200m_int8` | Int8 ONNX | 185.5 MB | sherpa-onnx / ONNX Runtime Mobile | MIT (AI4Bharat / sherpa-onnx) |
| **TTS** | VITS Inflect English Nano v2 | `tts_inflect_en_nano` | Native ONNX | 21.4 MB | sherpa-onnx (22.05kHz vocoder) | Apache-2.0 (k2-fsa) |
| **TTS** | VITS Coqui Bengali Female | `tts_coqui_bn_female` | Native ONNX | 103.0 MB | sherpa-onnx (22.05kHz vocoder) | MIT (Coqui / k2-fsa) |

---

## 3. Strict Quality & Fail-Closed Guardrails

1. **Zero System Speech Dependency:**
   - Whisly has completely eliminated `android.speech.SpeechRecognizer`. It does not request or depend on Google Speech Services or Android Settings speech packs.
2. **Never Fake / Fail Closed:**
   - If an input is silent or non-speech, Whisly emits `PipelineErrorType.SPEECH_NOT_DETECTED`. It never falls back to canned phrases (`"Hello"` or `"vannakkam"`).
   - If a target language pack is missing, Whisly prompts the user with an actionable download prompt rather than hallucinating or transliterating.
3. **Echo Guard:**
   - Translations that match the normalized source text are rejected with `SAME_LANGUAGE_SOURCE_TARGET` or `TRANSLATION_FAILURE` rather than masquerading as valid translations.
4. **Length Ratio & Repetition Guard:**
   - Translations outside length bounds (`0.20 <= ratio <= 4.50`) or exhibiting neural repetition loops (3+ consecutive duplicate words) fail closed to prevent hallucination display.
