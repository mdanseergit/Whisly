# DIAGNOSIS9 exact-config summary

Source: `C:\dev\Whisly-eval\diagnosis9_exact_results.jsonl` (130 rows).
Verified config: `tail_paddings=1000`, `language=<forced>` or `''` (true auto),
`task='transcribe'`, 16 kHz/80 features, exact Kotlin VAD port, no decode after VAD reject.
English script ratio is corrected here (Latin is the expected script, counted once).

## Per-language variant scores (recomputed with corrected script scoring)

| language | variant | n | mean CER | mean script ratio | flags |
|---|---|---:|---:|---:|---|
| en | app_int8_forced_vad_exact | 5 | 0.0836 | 1.0 | EMPTY=5 |
| en | int8_forced_full_exact | 10 | 0.0995 | 1.0 |  |
| en | int8_auto_vad_exact | 5 | 0.0836 | 1.0 | EMPTY=5 |
| hi | app_int8_forced_vad_exact | 9 | 1.0581 | 0.0 | EMPTY=1, REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| hi | int8_forced_full_exact | 10 | 1.1258 | 0.1 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| hi | int8_auto_vad_exact | 9 | 1.0581 | 0.0 | EMPTY=1, REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| bn | app_int8_forced_vad_exact | 10 | 1.2643 | 0.0 | REPETITION=4, WRONG_SCRIPT_LATIN=8 |
| bn | int8_forced_full_exact | 10 | 1.0858 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=8 |
| bn | int8_auto_vad_exact | 10 | 1.2048 | 0.0 | REPETITION=4, WRONG_SCRIPT_LATIN=7 |
| ta | app_int8_forced_vad_exact | 10 | 0.7304 | 1.0 |  |
| ta | int8_forced_full_exact | 10 | 0.7428 | 1.0 | REPETITION=4 |
| ta | int8_auto_vad_exact | 10 | 0.8226 | 0.6 | REPETITION=3, WRONG_SCRIPT_LATIN=4 |
| te | app_int8_forced_vad_exact | 10 | 0.9357 | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=6 |
| te | int8_forced_full_exact | 10 | 1.0048 | 0.0 | REPETITION=3, TOO_SHORT=2, WRONG_SCRIPT_LATIN=4 |
| te | int8_auto_vad_exact | 10 | 0.9434 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=9 |
| kn | app_int8_forced_vad_exact | 9 | 1.0446 | 0.0 | EMPTY=1, REPETITION=4, WRONG_SCRIPT_LATIN=9 |
| kn | int8_forced_full_exact | 10 | 1.0116 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=10 |
| kn | int8_auto_vad_exact | 9 | 0.9466 | 0.0 | EMPTY=1, REPETITION=5, WRONG_SCRIPT_LATIN=8 |
| ml | app_int8_forced_vad_exact | 10 | 0.9758 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=8 |
| ml | int8_forced_full_exact | 10 | 0.9535 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=9 |
| ml | int8_auto_vad_exact | 10 | 0.9773 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=8 |
| mr | app_int8_forced_vad_exact | 10 | 0.9767 | 0.0 | TOO_SHORT=1, WRONG_SCRIPT_LATIN=9 |
| mr | int8_forced_full_exact | 10 | 1.0124 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| mr | int8_auto_vad_exact | 10 | 1.006 | 0.0 | REPETITION=1, WRONG_SCRIPT_LATIN=9 |
| gu | app_int8_forced_vad_exact | 10 | 0.97 | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| gu | int8_forced_full_exact | 10 | 0.9696 | 0.0 | REPETITION=2, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 |
| gu | int8_auto_vad_exact | 10 | 0.9276 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=8 |
| pa | app_int8_forced_vad_exact | 10 | 1.0169 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 |
| pa | int8_forced_full_exact | 10 | 0.9982 | 0.0 | REPETITION=1, TOO_SHORT=2, WRONG_SCRIPT_LATIN=7 |
| pa | int8_auto_vad_exact | 10 | 0.9922 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=7 |
| ur | app_int8_forced_vad_exact | 10 | 0.5386 | 0.9789 | REPETITION=4, WRONG_SCRIPT_LATIN=1 |
| ur | int8_forced_full_exact | 10 | 0.5456 | 0.9797 | REPETITION=3, WRONG_SCRIPT_LATIN=1 |
| ur | int8_auto_vad_exact | 10 | 0.7642 | 0.4789 | REPETITION=3, WRONG_SCRIPT_LATIN=6 |
| or | app_int8_forced_vad_exact | 0 | None | None | EMPTY=10 |
| or | int8_forced_full_exact | 0 | None | None | EMPTY=10 |
| or | int8_auto_vad_exact | 10 | 0.9722 | 0.0 | REPETITION=1, TOO_SHORT=1, WRONG_SCRIPT_LATIN=7 |
| as | app_int8_forced_vad_exact | 10 | 1.0569 | 0.0 | REPETITION=7, WRONG_SCRIPT_LATIN=7 |
| as | int8_forced_full_exact | 9 | 1.0043 | 0.0 | EMPTY=1, REPETITION=2, WRONG_SCRIPT_LATIN=6 |
| as | int8_auto_vad_exact | 10 | 1.3265 | 0.0 | REPETITION=5, WRONG_SCRIPT_LATIN=8 |

## App-path exact failure classes

| language | class | count | clip IDs |
|---|---|---:|---|
| en | EMPTY | 5 | en_us-04, en_us-05, en_us-06, en_us-08, en_us-10 |
| en | PLAUSIBLE | 5 | en_us-01, en_us-02, en_us-03, en_us-07, en_us-09 |
| en | VAD_OR_TOKEN_REJECT | 5 | en_us-04, en_us-05, en_us-06, en_us-08, en_us-10 |
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

Exact-config app-path garbage/unusable: **125 / 130**

## Forced vs true auto language

| language | forced==auto | auto detected `result.lang` values | auto mean CER |
|---|---:|---|---:|
| en | 10/10 | en, en, en, None, None, None, en, None, en, None | 0.0836 |
| hi | 10/10 | hi, hi, hi, None, hi, hi, hi, hi, hi, hi | 1.0581 |
| bn | 8/10 | bn, bn, bn, bn, hi, bn, hi, bn, bn, bn | 1.2048 |
| ta | 6/10 | ml, si, ml, ta, ta, ta, ml, ta, ta, ta | 0.8226 |
| te | 3/10 | es, id, te, si, ml, ml, te, si, te, hi | 0.9434 |
| kn | 4/10 | si, kn, si, si, ml, si, ml, ml, si, kn | 0.9466 |
| ml | 9/10 | ml, ml, ml, pt, ml, ml, ml, ml, ml, ml | 0.9773 |
| mr | 3/10 | id, si, mr, mr, ml, mr, bo, ml, hi, ur | 1.006 |
| gu | 2/10 | ml, bn, si, gu, hi, bn, bn, bn, si, bn | 0.9276 |
| pa | 0/10 | hi, hi, hi, es, te, ur, hi, ur, hi, zh | 0.9922 |
| ur | 4/10 | ar, hi, ur, ur, hi, ur, hi, ur, hi, hu | 0.7642 |
| or | 0/10 | bn, bn, bn, bn, bn, bn, bn, bn, bn, si | 0.9722 |
| as | 0/10 | bn, bn, hi, es, ja, hi, en, es, ur, bn | 1.3265 |

## Exact VAD decisions

| language | rejected | clip IDs |
|---|---:|---|
| en | 5/10 | en_us-04 (0.005676,0ms), en_us-05 (0.007538,0ms), en_us-06 (0.0047,0ms), en_us-08 (0.006195,0ms), en_us-10 (0.009033,60ms) |
| hi | 1/10 | hi_in-04 (0.007111,0ms) |
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