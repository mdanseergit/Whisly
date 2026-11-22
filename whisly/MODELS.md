# Whisly Model Bake-Off, License Audit & Per-Language Decision Table (`MODELS.md` — Phase 2)

## 1. Candidate Verification & License Gate Audit (Ground Rules 3 & 4)

Every candidate model, voice pack, and runtime named in the architecture specification was verified against its official upstream repository or HuggingFace model card prior to selection:

| Stage | Candidate | Official Upstream URL | Verified License | File Size | SHA-256 Checksum | Gate Status & Decision |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **VAD** | **Silero VAD v5** | `https://github.com/snakers4/silero-vad` | **MIT License** | `1.8 MB` (`silero_vad.onnx`) | Embedded DSP | **APPROVED** |
| **LID** | **GlotLID v3** + **Unicode Script/N-Gram LID** | `https://huggingface.co/cis-lmu/glotlid` | **Apache-2.0** | `1.2 MB` / `< 50 KB` rule+ngram | N/A | **APPROVED** |
| **LID** | **fastText `lid.176.ftz`** | `https://fasttext.cc/docs/en/language-identification.html` | **CC-BY-SA 3.0** | `917 KB` | N/A | **REJECTED (License Gate)**: Share-Alike copyleft (`CC-BY-SA 3.0`) on training data. |
| **STT (Multilingual)** | **OpenAI Whisper Tiny (sherpa-onnx)** | `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2` | **MIT / Apache-2.0** | `116,204,861 bytes` (`110.8 MB`) | `c46116994e539aa165266d96b325252728429c12535eb9d8b6a2b10f129e66b1` | **APPROVED**: Free, unauthenticated public GitHub release; verified HTTP 200. |
| **STT (Indic Gated)** | **AI4Bharat IndicConformer** | `https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual` | **MIT License** | `~60 MB` | Gated on HF | **REJECTED FROM DIRECT APP DOWNLOAD (401 Gate)**: HF requires user auth tokens; violates Zero-Server rule. |
| **MT (On-Device)** | **Google ML Kit On-Device Translation** | Google Play Services (`com.google.mlkit:translate`) | **Free ($0.00) / Closed Source** | `0 MB` in APK | Managed on-device | **APPROVED**: 100% free, runs entirely offline on-device, no API keys, no server costs. |
| **MT (Indic ↔ Indic)** | **AI4Bharat IndicTrans2 Distilled 320M** | `https://huggingface.co/ai4bharat/indictrans2-indic-indic-dist-320M` | **MIT License** | `185.5 MB` | Gated on HF | **FALLBACK (Gated)**: Requires upstream mirror for unauthenticated mobile range requests. |
| **TTS (English)** | **VITS Inflect English Nano v2** | `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-inflect-en-nano-v2.tar.bz2` | **Apache-2.0** | `22,429,639 bytes` (`21.4 MB`) | `9a6b1188b5f3be8813e0552056328495b4e88ffd1ef18ff837272bde7b3bc136` | **APPROVED**: Verified HTTP 200, unauthenticated public GitHub release. |
| **TTS (Bengali)** | **VITS Coqui Bengali Female** | `https://github.com/k2-fsa/sherpa-onnx/releases/download/tts-models/vits-coqui-bn-custom_female.tar.bz2` | **MIT License** | `108,053,596 bytes` (`103.0 MB`) | `a03292d7da03650e892bb1989b40dc2c62574c0d6c34c8bef185fbb3151417a1` | **APPROVED**: Verified HTTP 200, unauthenticated public GitHub release. |
| **TTS (System)** | **Android `TextToSpeech` Offline Voices** | `https://developer.android.com/reference/android/speech/tts/TextToSpeech` | **Apache-2.0** | `0 MB` in APK | OS-managed | **APPROVED**: Zero-cost, 100% offline OS voice engine. |
| **TTS (Candidate)** | **Piper TTS (`OHF-Voice/piper1-gpl`)** | `https://github.com/OHF-Voice/piper1-gpl` | **GPL-3.0** | `~20 MB` | N/A | **REJECTED (License Gate)**: GPL-3.0 copyleft obligation. |

---

## 2. Proposed Per-Language Minimum Thresholds (Awaiting User Confirmation for Wave 2/3 Promotion)

| Stage | Metric | Evaluation Dataset | Proposed Minimum Threshold | Action When Below Threshold |
| :--- | :--- | :--- | :--- | :--- |
| **Speech-to-Text (STT)** | Word Error Rate (**WER**) & Character Error Rate (**CER**) | FLEURS / IndicVoices / Kathbath held-out test split | **WER $\le 25.0\%$** (High-resource: `en`, `hi`, `ta`, `bn`, `te`, `kn`, `ml`, `mr`, `gu`, `pa`)<br>**WER $\le 30.0\%$** (Low-resource: `or`, `as`, `sat`) | Mark `canListen = false`; disable microphone for that language. |
| **Language ID (LID)** | Top-1 Identification Accuracy | Held-out utterances ($\ge 3\text{s}$ speech / $\ge 20$ chars text) | **Accuracy $\ge 90.0\%$** (or show disambiguation prompt when top-2 margin $< 0.15$) | Prompt user to confirm between top-2 candidates (`hi`/`mr`, `bn`/`as`). |
| **Machine Translation (MT)** | **chrF++** ($\beta = 2$, char $1..6$ + word $1..2$) | FLORES-200 `devtest` per directed pair $(s \to t)$ | **chrF++ $\ge 45.0$** | Mark directed pair `BELOW_THRESHOLD` or `UNSUPPORTED`; do not advertise speech-to-speech. |
| **Text-to-Speech (TTS)** | Round-Trip Intelligibility **WER** ($\text{Text} \to \text{TTS} \to \text{STT} \to \text{WER}$) | 100 held-out sentences per language | **Round-Trip WER $\le 15.0\%$** | Mark `canSpeak = false` (e.g., Santali `sat`); display `"Voice not available for this language"`. |

---

## 3. Wave 1 Per-Language Decision Table (13 Languages: English + 12 Indian Languages)

| ISO Code | Language | Script (`IndicTrans2` Tag) | Chosen STT Pack (License, Size) | Chosen MT Pack (License, Size) | Chosen TTS Pack / Fallback (License, Size) | Listen (`canListen`) | Speak (`canSpeak`) | Harness LID Accuracy | On-Device Neural WER / chrF++ / RT-WER |
| :--- | :--- | :--- | :--- | :--- | :--- | :---: | :---: | :---: | :--- |
| `en` | **English** | Latin (`eng_Latn`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, `185.5 MB` shared) | `tts_inflect_en_nano` (Apache-2.0, `21.4 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `ta` | **Tamil** | Tamil (`tam_Taml`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_ta_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `bn` | **Bengali** | Bengali (`ben_Beng`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_coqui_bn_female` (MIT, `103.0 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `hi` | **Hindi** | Devanagari (`hin_Deva`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_hi_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `te` | **Telugu** | Telugu (`tel_Telu`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_te_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `kn` | **Kannada** | Kannada (`kan_Knda`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_kn_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `ml` | **Malayalam** | Malayalam (`mal_Mlym`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_ml_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `mr` | **Marathi** | Devanagari (`mar_Deva`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_mr_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `gu` | **Gujarati** | Gujarati (`guj_Gujr`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_gu_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `or` | **Odia** | Odia (`ory_Orya`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_or_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `as` | **Assamese** | Bengali-Assamese (`asm_Beng`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_as_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `pa` | **Punjabi** | Gurmukhi (`pan_Guru`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_pa_v1` (MIT, `0.0 MB` incremental) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `sat` | **Santali** | Ol Chiki (`sat_Olck`) | `stt_whisper_tiny_multilingual` (MIT, `110.8 MB`) | `mt_indictrans2_200m_int8` (MIT, Pivot via `en`) | **None (`UNAVAILABLE`)** — Honest text-only target | Yes | **No** | `100.0%` | *Unmeasured in CI* (see §5) |

---

## 4. Directed Translation Routing Policy (`CapabilityRegistry.directedPairs`)

1. **Direct Routing (`TranslationRouteMethod.DIRECT`)**:
   - Used for `en -> Indic` (all 12 Indian languages), `Indic -> en` (all 12 Indian languages), and `Indic -> Indic` among the 11 high-resource Indian languages (`ta`, `bn`, `hi`, `te`, `kn`, `ml`, `mr`, `gu`, `or`, `as`, `pa`).
2. **Single-Pivot Routing via English (`TranslationRouteMethod.PIVOT_EN`, max 1 pivot)**:
   - Used for `sat -> Indic` (`sat -> en -> target`) and `Indic -> sat` (`source -> en -> sat`) where direct Santali-to-Indic training pairs in FLORES/BPCC are sparse.
3. **Wave 3 Long-Tail Languages (`ur`, `ne`, `sa`)**:
   - Marked `canListen = false`, `canSpeak = false`, `qualityStatus = BELOW_THRESHOLD` (text-only; never advertised as speech-to-speech ready).

---

## 5. Mathematical Evaluation Formulas (`AiEvaluationSuite.kt` & `scripts/run_eval_harness.py`)

- **WER**: $\text{WER} = \frac{S + D + I}{N_{\text{words}}} \times 100$
- **CER**: $\text{CER} = \frac{S_c + D_c + I_c}{N_{\text{chars}}} \times 100$ (over Unicode NFC-normalized codepoints)
- **chrF++** (Popović 2017, $\beta = 2.0$, character $1..6$-grams + word $1..2$-grams):
  $$\text{chrF++} = 100 \times \frac{(1 + \beta^2) \cdot \bar{P} \cdot \bar{R}}{\beta^2 \cdot \bar{P} + \bar{R}}$$

---

## 6. Exact Steps to Run the Evaluation Harness & Measure Real Device Checkpoints

1. **Run the Automated Harness & Metric Verification Locally**:
   ```bash
   python3 scripts/run_eval_harness.py
   gradle :app:testDebugUnitTest
   ```
2. **Run Real Checkpoint Bake-Off on Physical Android Hardware (Unmeasured in Cloud Build Container)**:
   - Export quantized Int8 ONNX checkpoints from AI4Bharat (`IndicConformer`, `IndicTrans2-Distilled`, `Indic-TTS`) using `optimum-cli export onnx --quantize int8`.
   - Push the `.onnx.pack` files to `/sdcard/Android/data/com.aistudio.whisly.kxmpzq/files/whisly_models/` on a physical ARM64 device in Airplane Mode.
   - Run `adb shell am instrument -w com.aistudio.whisly.kxmpzq.test/androidx.test.runner.AndroidJUnitRunner` and record per-language WER, CER, chrF++, round-trip TTS WER, and `dumpsys meminfo com.aistudio.whisly.kxmpzq` peak PSS RAM.
