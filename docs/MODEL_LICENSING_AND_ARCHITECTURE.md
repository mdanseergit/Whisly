# Whisly Model Licensing, Runtime Architecture & Verification Guide

## 1. Offline AI Model Stack & Licensing

Whisly separates Speech Recognition (STT), Machine Translation (MT), and Speech Synthesis (TTS) into independent, cryptographically verified model packs:

| Stage | Primary Model | License | Source / Attribution | Target Runtime |
|---|---|---|---|---|
| **STT** | AI4Bharat IndicConformer (CTC / RNN-T) | CC-BY-4.0 / MIT | AI4Bharat, IIT Madras | ONNX Runtime Mobile (16kHz Mono PCM) |
| **STT (Fallback)** | Whisper Tiny Multilingual | MIT | OpenAI / `whisper.cpp` | GGML / ONNX Int8 |
| **MT** | AI4Bharat IndicTrans2 Indic-Indic Distilled 200M | MIT | AI4Bharat, IIT Madras | ONNX Runtime Mobile Int8 (Shared across all language pairs) |
| **TTS** | AI4Bharat VITS-Rasa (13-Language Release) | CC-BY-4.0 | AI4Bharat, IIT Madras | ONNX Runtime Mobile (22.05kHz -> 16kHz PCM) |

---

## 2. VITS-Rasa 13-Language Coverage vs. Scheduled Languages

AI4Bharat's VITS-Rasa neural TTS checkpoint covers **13 Indian languages** (Assamese, Bengali, Bodo, Dogri, English-Indian, Gujarati, Hindi, Kannada, Malayalam, Manipuri, Marathi, Odia, Punjabi, Rajasthani, Sanskrit, Tamil, Telugu — varying by release subset) and does **not** cover all 22 scheduled languages (for example, **Santali (`sat_Olck`)** has STT and IndicTrans2 MT support, but no VITS-Rasa neural TTS checkpoint).

Whisly enforces this distinction in `IndicLanguageCatalog` and `VitsRasaTtsEngine`:
- Languages with verified VITS-Rasa checkpoints are marked `VITS_RASA_VERIFIED`.
- Languages without VITS-Rasa checkpoints (such as Santali `sat`) are explicitly labeled `UNAVAILABLE` (`Offline neural TTS is unavailable`) so the application never fabricates voice support.

---

## 3. Shared Model Deduplication & Atomic Installation

- The quantized `mt_indictrans2_200m_int8` pack (`185.5 MB`) is stored under `files/whisly_models/indic_shared/` and reused across every Indian language pair (`ta ↔ bn`, `ta ↔ hi`, `te ↔ ta`, etc.).
- Per-language STT (`stt_indicconformer_<lang>_v2`) and TTS (`tts_vits_rasa_<lang>_v1`) packs are downloaded into `files/whisly_models_staging/`, verified against their signed manifest SHA-256 digest via `KeystoreCryptoManager`, and atomically promoted only after verification succeeds.
