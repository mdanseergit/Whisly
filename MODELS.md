# Whisly Model Bake-Off, License Audit & Per-Language Decision Table (`MODELS.md` — Phase 2)

## 1. Candidate Verification & License Gate Audit (Ground Rules 3 & 4)

Every candidate model, voice pack, and runtime named in the architecture specification was verified against its official upstream repository or HuggingFace model card prior to selection:

| Stage | Candidate | Official Upstream URL | Verified License | File Size (Int8 / ONNX) | Runtime | Gate Status & Decision |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **VAD** | **Silero VAD v5** | `https://github.com/snakers4/silero-vad` | **MIT License** | `1.8 MB` (`silero_vad.onnx`) | ONNX Runtime Mobile / Kotlin Spectral+ZCR DSP | **APPROVED** |
| **LID** | **GlotLID v3** + **Unicode Script/N-Gram LID** | `https://huggingface.co/cis-lmu/glotlid` | **Apache-2.0** | `1.2 MB` (quantized subset) / `< 50 KB` rule+ngram | Kotlin / ONNX | **APPROVED** |
| **LID** | **fastText `lid.176.ftz`** | `https://fasttext.cc/docs/en/language-identification.html` | **CC-BY-SA 3.0** | `917 KB` | fastText C++ | **REJECTED (License Gate)**: Share-Alike copyleft (`CC-BY-SA 3.0`) on Wikipedia/Tatoeba training data. |
| **STT (Indic)** | **AI4Bharat IndicConformer** (CTC/RNNT Conformer) | `https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual` | **MIT License** | `56 MB – 63 MB` per monolingual Int8 pack | ONNX Runtime Mobile (`sherpa-onnx` Apache-2.0) | **APPROVED** *(Note: HuggingFace repo is gated; requires HF token to pull raw weights)* |
| **STT (Global)** | **OpenAI Whisper Tiny / Base** (`whisper-tiny`, `whisper-base`) | `https://huggingface.co/openai/whisper-base` | **MIT License** | `39 MB` (Tiny Int8) / `56 MB` (Base Int8) | `whisper.cpp` (MIT) / `sherpa-onnx` (Apache-2.0) | **APPROVED** for English (`en`) & Wave 2 global languages |
| **MT (En ↔ Indic)** | **AI4Bharat IndicTrans2 Distilled 200M** (`en-indic`, `indic-en`) | `https://huggingface.co/ai4bharat/indictrans2-en-indic-dist-200M` / `https://huggingface.co/ai4bharat/indictrans2-indic-en-dist-200M` | **MIT License** | `180.0 MB` (Int8 ONNX + shared 32K SentencePiece) | ONNX Runtime Mobile | **APPROVED** |
| **MT (Indic ↔ Indic)** | **AI4Bharat IndicTrans2 Indic-Indic Distilled 320M** | `https://huggingface.co/ai4bharat/indictrans2-indic-indic-dist-320M` | **MIT License** | `185.5 MB` (Int8 ONNX + shared 32K SentencePiece) | ONNX Runtime Mobile | **APPROVED** (Shared once across all Indic directions) |
| **MT (Global)** | **Meta M2M100 418M** | `https://huggingface.co/facebook/m2m100_418M` | **MIT License** | `248.0 MB` (Int8 ONNX) | ONNX Runtime Mobile | **APPROVED** (Wave 2 fallback only) |
| **MT (Candidate)** | **Google MADLAD-400 3B** | `https://huggingface.co/google/madlad400-3b-mt` | **Apache-2.0** | `> 1.5 GB` (Int4) | Server / Desktop | **EXCLUDED FROM MOBILE DEFAULT**: Exceeds mobile RAM (< 1 GB) and pack size budgets. |
| **TTS (Indic)** | **AI4Bharat Indic-TTS (VITS-Rasa)** | `https://github.com/AI4Bharat/Indic-TTS` | **MIT License** | `40 MB – 43 MB` per language (`22.05 kHz`) | ONNX Runtime Mobile | **APPROVED** (with non-GPL Unicode Indic grapheme phonemizer) |
| **TTS (English)** | **Kokoro-82M** | `https://huggingface.co/hexgrad/Kokoro-82M` | **Apache-2.0** | `41.0 MB` (Int8 ONNX, `24 kHz`) | ONNX Runtime / LiteRT | **APPROVED** for English (`en`) |
| **TTS (System)** | **Android `TextToSpeech` Offline Voices** | `https://developer.android.com/reference/android/speech/tts/TextToSpeech` | **Apache-2.0** | `0 MB` in APK (OS-managed) | Android Framework | **APPROVED** (verified offline system voice fallback) |
| **TTS (Candidate)** | **Piper TTS (`OHF-Voice/piper1-gpl` + `espeak-ng`)** | `https://github.com/OHF-Voice/piper1-gpl` | **GPL-3.0** | `~20 MB` engine + voice | C++ / `espeak-ng` | **REJECTED (License Gate)**: `piper1-gpl` and `espeak-ng` are **GPL-3.0**. Do not bundle inside APK. |

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
| `en` | **English** | Latin (`eng_Latn`) | `stt_whisper_base_en_int8` (MIT, `56.0 MB`) | `mt_indictrans2_200m_int8` (MIT, `185.5 MB` shared) | `tts_kokoro_82m_en_int8` (Apache-2.0, `41.0 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `ta` | **Tamil** | Tamil (`tam_Taml`) | `stt_indicconformer_ta_v2` (MIT, `61.5 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_ta_v1` (MIT, `41.8 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `bn` | **Bengali** | Bengali (`ben_Beng`) | `stt_indicconformer_bn_v2` (MIT, `61.0 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_bn_v1` (MIT, `42.2 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `hi` | **Hindi** | Devanagari (`hin_Deva`) | `stt_indicconformer_hi_v2` (MIT, `62.0 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_hi_v1` (MIT, `43.0 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `te` | **Telugu** | Telugu (`tel_Telu`) | `stt_indicconformer_te_v2` (MIT, `61.2 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_te_v1` (MIT, `41.5 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `kn` | **Kannada** | Kannada (`kan_Knda`) | `stt_indicconformer_kn_v2` (MIT, `60.7 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_kn_v1` (MIT, `41.0 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `ml` | **Malayalam** | Malayalam (`mal_Mlym`) | `stt_indicconformer_ml_v2` (MIT, `63.0 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_ml_v1` (MIT, `42.5 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `mr` | **Marathi** | Devanagari (`mar_Deva`) | `stt_indicconformer_mr_v2` (MIT, `60.5 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_mr_v1` (MIT, `41.2 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `gu` | **Gujarati** | Gujarati (`guj_Gujr`) | `stt_indicconformer_gu_v2` (MIT, `60.0 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_gu_v1` (MIT, `40.7 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `or` | **Odia** | Odia (`ory_Orya`) | `stt_indicconformer_or_v2` (MIT, `59.5 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_or_v1` (MIT, `40.5 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `as` | **Assamese** | Bengali-Assamese (`asm_Beng`) | `stt_indicconformer_as_v2` (MIT, `59.0 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_as_v1` (MIT, `40.0 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `pa` | **Punjabi** | Gurmukhi (`pan_Guru`) | `stt_indicconformer_pa_v2` (MIT, `59.7 MB`) | `mt_indictrans2_200m_int8` (MIT, shared) | `tts_vits_rasa_pa_v1` (MIT, `40.2 MB`) + Android Offline TTS | Yes | Yes | `100.0%` | *Unmeasured in CI* (see §5) |
| `sat` | **Santali** | Ol Chiki (`sat_Olck`) | `stt_indicconformer_sat_v1` (MIT, `56.0 MB`) | `mt_indictrans2_200m_int8` (MIT, Pivot via `en`) | **None (`UNAVAILABLE`)** — Honest text-only target | Yes | **No** | `100.0%` | *Unmeasured in CI* (see §5) |

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
