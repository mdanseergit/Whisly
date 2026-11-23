# Model inventory — Whisly

Status as of 2026-11-23, branch `voice-reset`. This file records model identity, license, pin, size
and measured stage tier. It does **not** authorise bundling: any model not already in
`app/src/main/assets/` requires separate explicit approval before being added.

## 1. Bundled STT (shipped, unchanged)

| Property | Value |
|---|---|
| Model | OpenAI Whisper Tiny, multilingual, INT8 encoder + INT8 decoder |
| Runtime | sherpa-onnx 1.13.5 (JitPack `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.5`) |
| Assets | `assets/whisper/tiny/tiny-encoder.int8.onnx` (12,937,772 B), `tiny-decoder.int8.onnx` (89,855,401 B), `tiny-tokens.txt` (816,730 B) |
| INT8 total | 103,609,903 B |
| Weights license | MIT — https://raw.githubusercontent.com/openai/whisper/main/LICENSE |
| Export tooling | Apache-2.0 — https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/LICENSE |
| Measured tier | `en` = **BETA** (WER 0.2386, garbage 1/10, no per-token confidence); all 12 Indic languages = **UNAVAILABLE** |
| Confidence | none: `ys_log_probs` measured empty → every result is `LOW_CONFIDENCE` |
| Speech auto-detect | `UNAVAILABLE` (misidentified Odia/Assamese/Punjabi in DIAGNOSIS9) |

Evidence: `docs/DIAGNOSIS9.md`, `docs/ENGLISH_REMEASURE_AFTER_FIX_A.md`,
`docs/FIX_E_FIREWALL_AND_REGISTRY_RECONCILE.md`, `deliverables/eval_english_bakeoff/`.

## 2. Evaluated candidates (NOT bundled)

Evaluation-only artifacts live in `C:\dev\Whisly-eval\models\`, outside the repository. Full hashes,
pins and download logs are in `deliverables/eval_english_bakeoff/MODELS_PINNED.md`.

| Model | Pin | INT8 bytes | WER | CER | Garbage | Per-token logprob | Verdict |
|---|---|---:|---:|---:|---:|---|---|
| `base.en` | `59eea950fc76df2453efb57e6c0fd334548e8ffe` | 160,626,066 | 0.1458 | 0.0625 | 1/10 | absent (0/10) | BETA accuracy, not VERIFIED, not bundled |
| `small.en` | `d9533f69affd85061aee349af7fea5cb2996dbbe` | 375,501,079 | 0.1220 | 0.0432 | 1/10 | absent (0/10) | BETA accuracy, not VERIFIED, not bundled |

Both are English-only (`.en`), public, non-gated, MIT weights exported with Apache-2.0 tooling.
Neither can recognise Indic speech or provide language ID. Neither passes the ≤1% garbage or
per-token-confidence requirements, so no smallest-passing-model selection was made. See
`docs/EVAL_RESULTS.md`.

## 3. Excluded candidates

| Model / class | Reason |
|---|---|
| Meta MMS, Seamless | Excluded by user decision (unclear / non-commercial licence profile) |
| GPL speech engines and non-commercial voices | Excluded by user decision |
| Any gated or unclear-licence model | Not evaluated; clear licence is mandatory |
| `medium*`, `large*` Whisper | Not evaluated; far beyond Play-size fit and no confidence signal at smaller sizes |
| Multilingual Whisper alternatives | Would not fix measured Indic garbage; all Indic STT stays `UNAVAILABLE` |

## 4. Typed translation

ML Kit on-device translation remains the only translation engine. Official support:
`en, hi, bn, ta, te, kn, mr, gu, ur`. Languages without a tag (`ml, pa, or, as, sat`) stay typed-text
fallback only. Clean-install airplane-mode proof is `UNMEASURED`; no offline claim is made.

## 5. Text-to-speech

Temporary Android system TTS only. Runtime gate requires exact `Locale`, `isLanguageAvailable`
country/variant success, installed non-network voice data, exact active-voice locale and ≥95%
target-script text. Missing voice shows `Voice not available for <language>` and opens the official
voice-data install intent. No neural voice or non-system engine is bundled; per-device voice
availability is `UNMEASURED` (no device attached).

## 6. Model-change rule

STOP and ask before bundling any row in section 2, before any fine-tuning or paid compute, before
adding a backend/online mode, or before changing any language's tier. No such change was made in this
step.
