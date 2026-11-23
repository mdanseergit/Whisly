# Evaluation results — Whisper English STT bake-off

Date: 2026-11-23. Branch `voice-reset`. Scope approved by the user: **evaluation only**. No model was
added to the app, no bundle changed, no fine-tuning, no paid compute, no upload, no backend/online
mode, no language removed. The bundled Whisper Tiny INT8 assets remain untouched.

## 1. Candidates, pins and license

| Item | Baseline (control) | Candidate A | Candidate B |
|---|---|---|---|
| Model id | `tiny` (multilingual) | `base.en` (English only) | `small.en` (English only) |
| HF repo | `csukuangfj/sherpa-onnx-whisper-tiny` | `csukuangfj/sherpa-onnx-whisper-base.en` | `csukuangfj/sherpa-onnx-whisper-small.en` |
| Pinned commit | bundled, commit not recorded in app | `59eea950fc76df2453efb57e6c0fd334548e8ffe` | `d9533f69affd85061aee349af7fea5cb2996dbbe` |
| Gated / public | n/a | no / yes | no / yes |
| Weight license | MIT (OpenAI Whisper) | MIT (OpenAI Whisper) | MIT (OpenAI Whisper) |
| Export tooling | Apache-2.0 sherpa-onnx | Apache-2.0 sherpa-onnx | Apache-2.0 sherpa-onnx |
| INT8 artifacts total | 103,609,903 B | 160,626,066 B | 375,501,079 B |

Official evidence: sherpa-onnx "Available models" table
(https://k2-fsa.github.io/sherpa/onnx/pretrained_models/whisper/export-onnx.html, states `.en`
models support English only); OpenAI Whisper MIT license
(https://raw.githubusercontent.com/openai/whisper/main/LICENSE); sherpa-onnx Apache-2.0
(https://raw.githubusercontent.com/k2-fsa/sherpa-onnx/master/LICENSE). Full local SHA-256 hashes and
download stdout are in `deliverables/eval_english_bakeoff/MODELS_PINNED.md` and
`download_candidates.log`. All downloaded files are outside the repo in `C:\dev\Whisly-eval\models\`.

## 2. Exact harness (mirrors the production path)

Script: `deliverables/eval_english_bakeoff/run_english_bakeoff.py`.
Raw output: `english_results.jsonl`, `english_report.md`, `english_summary.md`, `run_english_bakeoff.log`.

- Inputs: the 10 pinned FLEURS `en_us` dev clips from `C:\dev\Whisly-eval\fleurs_manifest.json`
  (revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, CC-BY-4.0). No clip was edited.
- Gate: `final_vad_segment()` from the committed fix (a) production VAD port, including the exact
  pre-roll/voiced/hangover PCM segment. No decoder runs when VAD rejects.
- Decoder: sherpa-onnx 1.13.5 `OfflineRecognizer.from_whisper`, INT8 encoder + INT8 decoder,
  `task="transcribe"`, `tail_paddings=1000`, `language="en"` (forced, no auto-detect),
  `num_threads=2`, 16 kHz/80 features, PCM16/32768 scaling.
- Scoring: `jiwer.wer` and `jiwer.cer` against the pinned reference; script and garbage flags reuse
  the same `flags()`/`classify()` code as `DIAGNOSIS9`. Nothing is cleaned before scoring.
- **Parity control**: `tiny` was re-run inside this harness. Its text matched the committed
  `fix_a_vad_stt/exact_fixed_vad_results.jsonl` result **10/10**, so the comparison is apples-to-apples.

## 3. Measured results

Pasted from `deliverables/eval_english_bakeoff/english_summary.md`:

| model | decoded | mean WER | mean CER | mean script ratio | flags | garbage/10 | ys_log_probs present | mean latency ms (PC CPU) | INT8 bytes |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| tiny_int8 | 10/10 | 0.2386 | 0.1021 | 1.0 | PLAIN_MISRECOGNITION=1, PLAUSIBLE=9 | 1/10 | 0/10 | 537.89 | 103,609,903 |
| base.en_int8 | 10/10 | 0.1458 | 0.0625 | 1.0 | PLAIN_MISRECOGNITION=1, PLAUSIBLE=9 | 1/10 | 0/10 | 950.38 | 160,626,066 |
| small.en_int8 | 10/10 | 0.122 | 0.0432 | 1.0 | PLAIN_MISRECOGNITION=1, PLAUSIBLE=9 | 1/10 | 0/10 | 2622.99 | 375,501,079 |

Accuracy improves monotonically with model size: WER `23.86% → 14.58% → 12.20%`, CER
`10.21% → 6.25% → 4.32%`. Nine of ten clips are plausible for every model. The single garbage case
is the same clip (`en_us-04`, CER `0.5 / 0.3571 / 0.2857`) for all three models; the per-clip table is
in `inspect_results.log`. All outputs are Latin script (ratio `1.0`), as required for English.

Latency above is **desktop CPU only**. Phone latency, RAM, cold start, ABI and Android version remain
`UNMEASURED` (no device attached, per your answer).

## 4. Decisive finding: no candidate supplies per-token confidence

For every one of the 30 decodes, `result.ys_log_probs` had length `0`. The complete exposed field set
on the sherpa-onnx 1.13.5 Whisper result is:

```
['durations', 'emotion', 'event', 'lang', 'segment_durations', 'segment_texts',
 'segment_timestamps', 'text', 'timestamps', 'tokens', 'words', 'ys_log_probs']
```

`ys_log_probs` is the only probability-bearing field and it is empty for `tiny`, `base.en` **and**
`small.en`. Therefore no candidate can produce a real per-token probability, no measured acceptance
threshold can be set, and no result from any of these models can reach `PASS` in the production
firewall. Every non-empty result would still be `LOW_CONFIDENCE` → `Not sure: check and edit`, with
explicit Translate tap required. This is not lowered or faked for the bake-off.

## 5. Gate verdicts (thresholds unchanged)

- VERIFIED requires WER ≤ 20–25%, garbage ≤ 1%, noise false-accept ≤ 2%, **and** real per-token
  probability with an explicitly measured threshold.
- BETA requires WER ≤ 40% plus mandatory edit+confirm and a `Beta` label.
- Otherwise UNAVAILABLE.

| Model | WER gate | Garbage gate | Confidence gate | Verdict |
|---|---|---|---|---|
| tiny (current) | 23.86% — inside | 10% — fails | absent — fails | **BETA** (already shipped, confirmed) |
| base.en | 14.58% — inside | 10% — fails | absent — fails | **BETA** (not VERIFIED; no bundling approval) |
| small.en | 12.20% — inside | 10% — fails | absent — fails | **BETA** (not VERIFIED; no bundling approval) |

Noise false-accept is model-independent: the shared `final_vad` gate rejects the retained 20-clip
CC0 corpus `20/20` before any decoder runs, satisfying the ≤2% noise gate for all three.

**Sample-size limitation**: with 10 clips the smallest observable garbage rate is 10%, so the ≤1%
VERIFIED garbage gate cannot be demonstrated on this set even if a model scored 0/10. A VERIFIED
claim needs at least 100 clips (preferably ≥300) per language. This is a measurement limitation, not
a reason to lower the gate.

**Selection**: no model passes VERIFIED, so no smallest-passing-model selection is made. If you later
grant separate explicit bundling approval, `base.en` is the smallest accuracy improvement
(+57,016,163 B over the bundled control); `small.en` costs +271,891,176 B for a further small WER
gain. I have not bundled either.

## 6. Play-size fit (informational; no bundle change)

Recorded baseline: AAB `87,599,467` bytes; bundletool `MIN,MAX` `81881902,82429589`
(arm64-v8a). Official Play base-module compressed download limit is 500 MB. Both candidates would
fit that limit on paper, but actual Play Console compressed size is `UNMEASURED` (no console account
in scope) and no size claim is made until measured.

## 7. Language coverage table

| Code | STT tier (current app) | Bake-off result | Typed ML Kit text | Runtime TTS gate | Change in this step |
|---|---|---|---|---|---|
| en | BETA / mic owner | base.en & small.en improve WER/CER but fail garbage + confidence; none bundled | yes | exact Locale + installed non-network voice | none |
| hi | UNAVAILABLE | not evaluated (English-only candidates) | yes (`hi`) | same gate | none |
| bn | UNAVAILABLE | not evaluated | yes (`bn`) | same gate | none |
| ta | UNAVAILABLE | not evaluated | yes (`ta`) | same gate | none |
| te | UNAVAILABLE | not evaluated | yes (`te`) | same gate | none |
| kn | UNAVAILABLE | not evaluated | yes (`kn`) | same gate | none |
| ml | UNAVAILABLE | not evaluated | no tag (typed fallback only) | same gate | none |
| mr | UNAVAILABLE | not evaluated | yes (`mr`) | same gate | none |
| gu | UNAVAILABLE | not evaluated | yes (`gu`) | same gate | none |
| pa | UNAVAILABLE | not evaluated | no tag (typed fallback only) | same gate | none |
| ur | UNAVAILABLE | not evaluated | yes (`ur`) | same gate | none |
| or | UNAVAILABLE (no Whisper token) | not evaluated | no tag | same gate | none |
| as | UNAVAILABLE | not evaluated | no tag | same gate | none |
| sat | UNAVAILABLE / deferred | not evaluated | no tag | same gate | none |

No Indic language is promoted. `.en` models cannot recognise Indic speech or provide language ID, so
there is no Whisper-vs-ML-Kit LID mismatch to confirm in this evaluation. Speech auto-detect remains
`UNAVAILABLE`.

## 8. Still UNMEASURED / blocked

- Phone WER/CER, garbage rate, latency, RAM, cold start, ABI, Android version, device model.
- Clean-install airplane-mode proof for typed ML Kit translation.
- Per-device TTS voice availability and spoken-output quality.
- Play Console compressed size; acoustic language ID; Santali speech (deferred).
- ≥100-clip acceptance set needed before any VERIFIED claim.

## 9. Questions only you can answer

1. Given that **no candidate supplies per-token confidence**, do you want me to (a) keep the bundled
   `tiny` model unchanged, (b) prepare — but not bundle — a larger ≥100-clip English acceptance set
   to test `base.en` for VERIFIED, or (c) approve bundling `base.en` as an accuracy-only BETA change
   with the same `Not sure: check and edit` flow? (c) requires separate explicit approval.)
2. No device is available, so phone metrics stay `UNMEASURED`. Do you want Step 7 (lint + instrumented
   tests on API 24/30/34/36 + final deliverables + version bump) to proceed with device-dependent
   expectations marked blocked, or wait until a device is available?
3. Should the 20-clip CC0 noise corpus be extended with more clips to try to observe the ≤2% noise
   gate with finer resolution, or stay exactly as measured?

## 10. No-code-change build confirmation

No Kotlin/Gradle file changed in this step. The exact command was still run from `whisly` with
`$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"`, then
`.\gradlew.bat clean assembleDebug testDebugUnitTest`. Full log:
`deliverables/eval_english_bakeoff/no_code_change_build.log`.

```
> Task :app:testDebugUnitTest FROM-CACHE

BUILD SUCCESSFUL in 11s
51 actionable tasks: 22 executed, 28 from cache, 1 up-to-date
Configuration cache entry reused.
```

`<testsuite name="com.example.ExampleUnitTest" tests="27" skipped="0" failures="0" errors="0" timestamp="2026-11-23T06:28:58.020Z" hostname="MOHAMMEDDANSEER" time="7.722">`
