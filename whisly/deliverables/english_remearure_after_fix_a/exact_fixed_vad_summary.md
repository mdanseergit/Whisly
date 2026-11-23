# Exact-config STT summary after fix (a)

Source: `C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_results.jsonl` (130 rows).

## Per-language app path

| language | decoded | mean CER | mean script ratio | flags | garbage/unusable |
|---|---:|---:|---:|---|---:|
| en | 10/10 | 0.1021 | 1.0 |  | 1/10 |
| hi | 10/10 | 0.908 | 0.1 | REPETITION=3, WRONG_SCRIPT_LATIN=9 | 10/10 |
| bn | 10/10 | 1.1184 | 0.0 | REPETITION=4, WRONG_SCRIPT_LATIN=8 | 10/10 |
| ta | 10/10 | 0.726 | 1.0 |  | 10/10 |
| te | 10/10 | 0.9229 | 0.0 | REPETITION=4, TOO_SHORT=1, WRONG_SCRIPT_LATIN=6 | 10/10 |
| kn | 9/10 | 1.0467 | 0.0 | EMPTY=1, REPETITION=4, WRONG_SCRIPT_LATIN=9 | 10/10 |
| ml | 10/10 | 0.9759 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=8 | 10/10 |
| mr | 10/10 | 1.0222 | 0.0 | REPETITION=2, WRONG_SCRIPT_LATIN=9 | 10/10 |
| gu | 10/10 | 0.9702 | 0.0 | REPETITION=3, TOO_SHORT=1, WRONG_SCRIPT_LATIN=8 | 10/10 |
| pa | 10/10 | 1.0232 | 0.0 | REPETITION=3, WRONG_SCRIPT_LATIN=9 | 10/10 |
| ur | 10/10 | 0.5166 | 0.9789 | REPETITION=3, WRONG_SCRIPT_LATIN=1 | 10/10 |
| or | 0/10 | None | None | EMPTY=10 | 10/10 |
| as | 10/10 | 1.0584 | 0.0 | REPETITION=7, WRONG_SCRIPT_LATIN=6 | 10/10 |

Total garbage/unusable after fix (a): **121/130**

## VAD decisions

| language | rejected | reason | segment samples (min-max) |
|---|---:|---|---:|
| en | 0/10 |  | 64960-259200 |
| hi | 0/10 |  | 101760-314240 |
| bn | 0/10 |  | 103360-303360 |
| ta | 0/10 |  | 98880-243520 |
| te | 0/10 |  | 55680-241280 |
| kn | 0/10 |  | 84480-405440 |
| ml | 0/10 |  | 110080-762240 |
| mr | 0/10 |  | 86720-266560 |
| gu | 0/10 |  | 100800-221760 |
| pa | 0/10 |  | 68160-379520 |
| ur | 0/10 |  | 99520-273600 |
| or | 0/10 |  | 114880-398720 |
| as | 0/10 |  | 97600-311360 |