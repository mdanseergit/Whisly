# Whisper English bake-off — summary

Source: `C:\dev\Whisly-eval\bakeoff\english_results.jsonl` (10 pinned FLEURS `en_us` dev clips).
Tiny parity with committed fix (a) run: 10/10.

| model | decoded | mean WER | mean CER | mean script ratio | flags | garbage/10 | ys_log_probs present | mean latency ms (PC CPU) | INT8 bytes |
|---|---:|---:|---:|---:|---|---:|---:|---:|---:|
| tiny_int8 | 10/10 | 0.2386 | 0.1021 | 1.0 | PLAIN_MISRECOGNITION=1, PLAUSIBLE=9 | 1/10 | 0/10 | 537.89 | 103,609,903 |
| base.en_int8 | 10/10 | 0.1458 | 0.0625 | 1.0 | PLAIN_MISRECOGNITION=1, PLAUSIBLE=9 | 1/10 | 0/10 | 950.38 | 160,626,066 |
| small.en_int8 | 10/10 | 0.122 | 0.0432 | 1.0 | PLAIN_MISRECOGNITION=1, PLAUSIBLE=9 | 1/10 | 0/10 | 2622.99 | 375,501,079 |

## Verdict rules (not lowered for this bake-off)

- VERIFIED: WER <= 20-25%, garbage <= 1%, noise false-accept <= 2%, AND real per-token probability plus an explicitly measured acceptance threshold.
- BETA: WER <= 40% with mandatory edit+confirm and a `Beta` label.
- Otherwise UNAVAILABLE.
- Noise false-accept is model-independent: the committed 20-clip CC0 corpus is rejected 20/20 by the shared `final_vad` gate before any decoder runs.

