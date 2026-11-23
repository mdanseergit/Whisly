# DIAGNOSIS9 measured summary

Source: `C:\dev\Whisly-eval\diagnosis9_results.jsonl` (130 rows).
**Caveat:** the `int8_auto_vad` column in this run is actually forced-English, because
the sherpa-onnx Python wrapper defaults `language="en"`; it is not auto-detection.
True auto-detection, `tail_paddings=1000`, exact ZCR denominator and exact VAD gating
are measured in the separate exact-config run (§5.3).

## Per-language variant scores

| language | variant | n | mean CER | mean WER | mean script ratio | garbage flags |
|---|---|---:|---:|---:|---:|---|
| en | app_int8_forced_vad | 6 | 0.2088 | 0.3545 | 0.5 | EMPTY=4 |
| en | int8_forced_full | 10 | 0.0995 | 0.2356 | 0.5 | none |
| en | fp32_forced_vad | 6 | 0.2088 | 0.3545 | 0.5 | EMPTY=4 |
| en | int8_auto_vad | 6 | 0.2088 | 0.3545 | 0.5 | EMPTY=4 |
| en | openai_forced | 10 | 0.0869 | 0.2489 | 0.5 | none |
| en | openai_auto | 10 | 0.0869 | 0.2489 | 0.5 | none |
| hi | app_int8_forced_vad | 9 | 1.0581 | 1.2484 | 0.0 | EMPTY=1, REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| hi | int8_forced_full | 10 | 1.1258 | 1.2765 | 0.1 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| hi | fp32_forced_vad | 9 | 1.0581 | 1.2484 | 0.0 | EMPTY=1, REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| hi | int8_auto_vad | 9 | 1.1918 | 1.3111 | 0.0 | EMPTY=1, REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| hi | openai_forced | 9 | 0.95 | 1.0033 | 0.0384 | EMPTY=1, WRONG_SCRIPT_LATIN=9 |
| hi | openai_auto | 9 | 1.0085 | 1.0336 | 0.0 | EMPTY=1, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| bn | app_int8_forced_vad | 10 | 1.2643 | 1.5349 | 0.0 | REPETITION=4, WRONG_SCRIPT_LATIN=8 |
| bn | int8_forced_full | 10 | 1.0858 | 1.3037 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=8 |
| bn | fp32_forced_vad | 10 | 1.2643 | 1.5349 | 0.0 | REPETITION=4, WRONG_SCRIPT_LATIN=8 |
| bn | int8_auto_vad | 10 | 1.1609 | 1.5241 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=10 |
| bn | openai_forced | 10 | 0.9851 | 1.0383 | 0.0 | WRONG_SCRIPT_LATIN=10 |
| bn | openai_auto | 10 | 1.2761 | 1.2542 | 0.0 | TOO_SHORT=1, WRONG_SCRIPT_LATIN=10 |
| ta | app_int8_forced_vad | 10 | 0.7304 | 1.0149 | 1.0 | none |
| ta | int8_forced_full | 10 | 0.7428 | 1.008 | 1.0 | REPETITION=4 |
| ta | fp32_forced_vad | 10 | 0.7304 | 1.0149 | 1.0 | none |
| ta | int8_auto_vad | 10 | 0.9327 | 1.2361 | 0.0 | WRONG_SCRIPT_LATIN=10 |
| ta | openai_forced | 10 | 0.7021 | 1.1952 | 0.8936 | WRONG_SCRIPT_LATIN=5 |
| ta | openai_auto | 10 | 0.7966 | 1.1333 | 0.6585 | WRONG_SCRIPT_LATIN=5 |
| te | app_int8_forced_vad | 10 | 0.9357 | 1.1085 | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=6 |
| te | int8_forced_full | 10 | 1.0048 | 1.2169 | 0.0 | REPETITION=3, TOO_SHORT=2, WRONG_SCRIPT_LATIN=4 |
| te | fp32_forced_vad | 10 | 0.9357 | 1.1085 | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=6 |
| te | int8_auto_vad | 10 | 1.2051 | 1.9951 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=10 |
| te | openai_forced | 10 | 1.277 | 2.3236 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| te | openai_auto | 9 | 1.2298 | 1.7497 | 0.0 | EMPTY=1, REPETITION=1, SPECIAL_TOKEN=1, WRONG_SCRIPT_LATIN=9 |
| kn | app_int8_forced_vad | 9 | 1.0446 | 1.8776 | 0.0 | EMPTY=1, REPETITION=4, WRONG_SCRIPT_LATIN=9 |
| kn | int8_forced_full | 10 | 1.0116 | 1.5089 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=10 |
| kn | fp32_forced_vad | 9 | 1.0446 | 1.8776 | 0.0 | EMPTY=1, REPETITION=4, WRONG_SCRIPT_LATIN=9 |
| kn | int8_auto_vad | 10 | 1.113 | 1.6122 | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=10 |
| kn | openai_forced | 9 | 1.0096 | 1.2888 | 0.0 | EMPTY=1, WRONG_SCRIPT_LATIN=9 |
| kn | openai_auto | 9 | 1.0922 | 1.4503 | 0.0 | EMPTY=1, WRONG_SCRIPT_LATIN=9 |
| ml | app_int8_forced_vad | 10 | 0.9758 | 1.1206 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=8 |
| ml | int8_forced_full | 10 | 0.9535 | 1.6972 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=9 |
| ml | fp32_forced_vad | 10 | 0.9758 | 1.1206 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=8 |
| ml | int8_auto_vad | 10 | 1.314 | 2.5503 | 0.0 | REPETITION=4, SPECIAL_TOKEN=1, WRONG_SCRIPT_LATIN=10 |
| ml | openai_forced | 10 | 1.0 | 1.3523 | 0.0 | REPETITION=1, SPECIAL_TOKEN=1, WRONG_SCRIPT_LATIN=10 |
| ml | openai_auto | 10 | 1.16 | 1.6935 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=9 |
| mr | app_int8_forced_vad | 10 | 0.9767 | 1.0292 | 0.0 | TOO_SHORT=1, WRONG_SCRIPT_LATIN=9 |
| mr | int8_forced_full | 10 | 1.0124 | 1.4287 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| mr | fp32_forced_vad | 10 | 0.9767 | 1.0292 | 0.0 | TOO_SHORT=1, WRONG_SCRIPT_LATIN=9 |
| mr | int8_auto_vad | 10 | 1.6973 | 2.5515 | 0.0 | REPETITION=6, WRONG_SCRIPT_LATIN=10 |
| mr | openai_forced | 10 | 0.9704 | 1.0409 | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=10 |
| mr | openai_auto | 9 | 0.9786 | 1.0657 | 0.0 | EMPTY=1, SPECIAL_TOKEN=1, WRONG_SCRIPT_LATIN=8 |
| gu | app_int8_forced_vad | 10 | 0.97 | 1.0818 | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| gu | int8_forced_full | 10 | 0.9696 | 1.0613 | 0.0 | REPETITION=2, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| gu | fp32_forced_vad | 10 | 0.97 | 1.0818 | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| gu | int8_auto_vad | 10 | 1.0588 | 1.2969 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=10 |
| gu | openai_forced | 10 | 0.9958 | 1.0282 | 0.0 | TOO_SHORT=1, WRONG_SCRIPT_LATIN=9 |
| gu | openai_auto | 10 | 0.9337 | 1.0 | 0.0 | WRONG_SCRIPT_LATIN=10 |
| pa | app_int8_forced_vad | 10 | 1.0169 | 1.2993 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| pa | int8_forced_full | 10 | 0.9982 | 1.0154 | 0.0 | REPETITION=1, TOO_SHORT=2, WRONG_SCRIPT_LATIN=7 |
| pa | fp32_forced_vad | 10 | 1.0169 | 1.2993 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| pa | int8_auto_vad | 10 | 0.9448 | 1.0913 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=10 |
| pa | openai_forced | 10 | 1.0916 | 1.3353 | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=9 |
| pa | openai_auto | 9 | 0.9297 | 1.0085 | 0.0 | EMPTY=1, SPECIAL_TOKEN=1, WRONG_SCRIPT_LATIN=8 |
| ur | app_int8_forced_vad | 10 | 0.5386 | 0.8636 | 0.9789 | REPETITION=4, WRONG_SCRIPT_LATIN=1 |
| ur | int8_forced_full | 10 | 0.5456 | 0.8815 | 0.9797 | REPETITION=3, WRONG_SCRIPT_LATIN=1 |
| ur | fp32_forced_vad | 10 | 0.5386 | 0.8636 | 0.9789 | REPETITION=4, WRONG_SCRIPT_LATIN=1 |
| ur | int8_auto_vad | 10 | 1.2068 | 1.281 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=10 |
| ur | openai_forced | 10 | 0.3449 | 0.7344 | 0.9783 | WRONG_SCRIPT_LATIN=1 |
| ur | openai_auto | 10 | 0.7469 | 0.9332 | 0.3786 | WRONG_SCRIPT_LATIN=7 |
| or | app_int8_forced_vad | 0 | None | None | None | EMPTY=10 |
| or | int8_forced_full | 0 | None | None | None | EMPTY=10 |
| or | fp32_forced_vad | 0 | None | None | None | EMPTY=10 |
| or | int8_auto_vad | 10 | 0.9457 | 1.0 | 0.0 | WRONG_SCRIPT_LATIN=10 |
| or | openai_forced | 0 | None | None | None | EMPTY=10 |
| or | openai_auto | 9 | 1.21 | 1.4596 | 0.0 | EMPTY=1, REPETITION=1, WRONG_SCRIPT_LATIN=9 |
| as | app_int8_forced_vad | 10 | 1.0569 | 1.4588 | 0.0 | REPETITION=7, WRONG_SCRIPT_LATIN=7 |
| as | int8_forced_full | 9 | 1.0043 | 1.1313 | 0.0 | EMPTY=1, REPETITION=2, WRONG_SCRIPT_LATIN=6 |
| as | fp32_forced_vad | 10 | 1.0569 | 1.4588 | 0.0 | REPETITION=7, WRONG_SCRIPT_LATIN=7 |
| as | int8_auto_vad | 10 | 1.1625 | 1.3433 | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=10 |
| as | openai_forced | 10 | 1.0518 | 1.2347 | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=10 |
| as | openai_auto | 10 | 0.983 | 1.064 | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=10 |

## App-path failure classes (current bundled engine through app VAD)

| language | class | count | clip IDs |
|---|---|---:|---|
| en | EMPTY | 4 | en_us-04, en_us-05, en_us-06, en_us-08 |
| en | PLAIN_MISRECOGNITION | 1 | en_us-10 |
| en | PLAUSIBLE | 5 | en_us-01, en_us-02, en_us-03, en_us-07, en_us-09 |
| en | VAD_OR_TOKEN_REJECT | 4 | en_us-04, en_us-05, en_us-06, en_us-08 |
| hi | EMPTY | 1 | hi_in-04 |
| hi | REPETITION_LOOP | 3 | hi_in-02, hi_in-05, hi_in-06 |
| hi | VAD_OR_TOKEN_REJECT | 1 | hi_in-04 |
| hi | WRONG_SCRIPT | 9 | hi_in-01, hi_in-02, hi_in-03, hi_in-05, hi_in-06, hi_in-07, hi_in-08, hi_in-09, hi_in-10 |
| bn | PLAIN_MISRECOGNITION | 2 | bn_in-02, bn_in-03 |
| bn | REPETITION_LOOP | 4 | bn_in-01, bn_in-04, bn_in-07, bn_in-08 |
| bn | WRONG_SCRIPT | 8 | bn_in-01, bn_in-04, bn_in-05, bn_in-06, bn_in-07, bn_in-08, bn_in-09, bn_in-10 |
| ta | PLAIN_MISRECOGNITION | 10 | ta_in-01, ta_in-02, ta_in-03, ta_in-04, ta_in-05, ta_in-06, ta_in-07, ta_in-08, ta_in-09, ta_in-10 |
| te | PLAIN_MISRECOGNITION | 3 | te_in-04, te_in-05, te_in-10 |
| te | REPETITION_LOOP | 3 | te_in-01, te_in-02, te_in-09 |
| te | TOO_SHORT | 1 | te_in-06 |
| te | WRONG_SCRIPT | 6 | te_in-01, te_in-02, te_in-03, te_in-06, te_in-07, te_in-08 |
| kn | EMPTY | 1 | kn_in-10 |
| kn | REPETITION_LOOP | 4 | kn_in-01, kn_in-07, kn_in-08, kn_in-09 |
| kn | WRONG_SCRIPT | 9 | kn_in-01, kn_in-02, kn_in-03, kn_in-04, kn_in-05, kn_in-06, kn_in-07, kn_in-08, kn_in-09 |
| ml | PLAIN_MISRECOGNITION | 2 | ml_in-01, ml_in-05 |
| ml | REPETITION_LOOP | 2 | ml_in-06, ml_in-08 |
| ml | WRONG_SCRIPT | 8 | ml_in-02, ml_in-03, ml_in-04, ml_in-06, ml_in-07, ml_in-08, ml_in-09, ml_in-10 |
| mr | TOO_SHORT | 1 | mr_in-10 |
| mr | WRONG_SCRIPT | 9 | mr_in-01, mr_in-02, mr_in-03, mr_in-04, mr_in-05, mr_in-06, mr_in-07, mr_in-08, mr_in-09 |
| gu | PLAIN_MISRECOGNITION | 2 | gu_in-01, gu_in-07 |
| gu | REPETITION_LOOP | 3 | gu_in-04, gu_in-05, gu_in-10 |
| gu | TOO_SHORT | 1 | gu_in-09 |
| gu | WRONG_SCRIPT | 8 | gu_in-02, gu_in-03, gu_in-04, gu_in-05, gu_in-06, gu_in-08, gu_in-09, gu_in-10 |
| pa | PLAIN_MISRECOGNITION | 1 | pa_in-10 |
| pa | REPETITION_LOOP | 3 | pa_in-01, pa_in-02, pa_in-06 |
| pa | WRONG_SCRIPT | 9 | pa_in-01, pa_in-02, pa_in-03, pa_in-04, pa_in-05, pa_in-06, pa_in-07, pa_in-08, pa_in-09 |
| ur | PLAIN_MISRECOGNITION | 5 | ur_pk-03, ur_pk-04, ur_pk-05, ur_pk-07, ur_pk-10 |
| ur | REPETITION_LOOP | 4 | ur_pk-01, ur_pk-02, ur_pk-06, ur_pk-09 |
| ur | WRONG_SCRIPT | 1 | ur_pk-08 |
| or | EMPTY | 10 | or_in-01, or_in-02, or_in-03, or_in-04, or_in-05, or_in-06, or_in-07, or_in-08, or_in-09, or_in-10 |
| or | VAD_OR_TOKEN_REJECT | 10 | or_in-01, or_in-02, or_in-03, or_in-04, or_in-05, or_in-06, or_in-07, or_in-08, or_in-09, or_in-10 |
| as | REPETITION_LOOP | 7 | as_in-01, as_in-03, as_in-04, as_in-06, as_in-07, as_in-09, as_in-10 |
| as | WRONG_SCRIPT | 7 | as_in-02, as_in-04, as_in-05, as_in-06, as_in-07, as_in-08, as_in-09 |

Total app-path clips flagged as garbage/unusable: **125 / 130**

## Exact-output equality between decoders (app VAD segment)

| language | INT8 vs FP32 | INT8 forced vs forced-English | INT8 forced vs OpenAI forced |
|---|---:|---:|---:|
| en | 10/10 | 10/10 | 2/10 |
| hi | 10/10 | 1/10 | 0/10 |
| bn | 10/10 | 0/10 | 0/10 |
| ta | 10/10 | 0/10 | 0/10 |
| te | 10/10 | 0/10 | 0/10 |
| kn | 10/10 | 0/10 | 0/10 |
| ml | 10/10 | 0/10 | 0/10 |
| mr | 10/10 | 0/10 | 0/10 |
| gu | 10/10 | 0/10 | 0/10 |
| pa | 10/10 | 0/10 | 0/10 |
| ur | 10/10 | 0/10 | 0/10 |
| or | 10/10 | 0/10 | 10/10 |
| as | 10/10 | 0/10 | 0/10 |

## VAD decisions on real speech

| language | VAD rejected | clip IDs (peak, voiced ms) |
|---|---:|---|
| en | 5/10 | en_us-04 (0.005676, 0), en_us-05 (0.007538, 0), en_us-06 (0.0047, 0), en_us-08 (0.006195, 0), en_us-10 (0.009033, 60) |
| hi | 1/10 | hi_in-04 (0.007111, 0) |
| bn | 0/10 |  |
| ta | 0/10 |  |
| te | 0/10 |  |
| kn | 0/10 |  |
| ml | 0/10 |  |
| mr | 0/10 |  |
| gu | 0/10 |  |
| pa | 0/10 |  |
| ur | 0/10 |  |
| or | 0/10 |  |
| as | 0/10 |  |