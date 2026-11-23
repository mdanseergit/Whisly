# English STT re-measurement after fix (a)

Status: **MEASURED**. This is a local, pinned-clip re-measurement only. No model was downloaded,
bundled, fine-tuned, uploaded, or changed. No on-device claim is made.

Baseline: commit `53f51c4` (`Fix A: measured speech-vs-nonspeech VAD gating`).

## 1. Question

The VAD repaired the false rejects that made `5/10` English clips empty in `DIAGNOSIS9`. The next
question was whether English now meets the `VERIFIED` gate: WER/CER ≤20–25%, garbage rate ≤1%,
silence/noise false-accept ≤2%.

## 2. Method

`deliverables/english_remearure_after_fix_a/rerun_exact_fixed_vad.py` reuses the exact decoder
configuration from `DIAGNOSIS9`:

| Setting | Value |
|---|---|
| Model | bundled Whisper Tiny INT8, unchanged |
| Language | forced from selected language (`en` here); no auto-detect |
| `task` | `transcribe` |
| `tail_paddings` | `1000` |
| Sample rate / features | 16 kHz / 80, PCM16 → float `/32768` |
| VAD | measured `final_vad` gate and pre-roll/voiced/hangover segment from fix (a) |
| Decode after VAD reject | never |

The only difference from `diagnosis9_exact_results.jsonl` is the VAD gate/segment. Garbage
classification is the same function used in `DIAGNOSIS9`: `VAD_OR_TOKEN_REJECT`, `EMPTY`,
`SPECIAL_TOKEN`, `WRONG_SCRIPT`, `REPETITION_LOOP`, `TOO_SHORT`, `LANGUAGE_MISMATCH`, or
`PLAIN_MISRECOGNITION` (CER > 0.25) count as garbage/unusable.

Exact command:

```powershell
$env:PYTHONIOENCODING="utf-8"
& "C:\dev\Whisly-eval-venv\Scripts\python.exe" "C:\dev\Whisly-eval\rerun_exact_fixed_vad.py"
```

Clean rerun result:

```text
PYTHON_EXIT=0
RESULTS=C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_results.jsonl REPORT=C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_report.md SUMMARY=C:\dev\Whisly-eval\fix_a_vad_stt\exact_fixed_vad_summary.md garbage=121/130
```

## 3. Exact result

Source: `deliverables/english_remearure_after_fix_a/exact_fixed_vad_summary.md`.

```text
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
```

VAD decisions: `0/10` rejected for every measured language, including English. The earlier
`5/10` English and `1/10` Hindi VAD rejects are gone.

### 3.1 English per-clip classification

Recomputed from `exact_fixed_vad_results.jsonl` with the same `classify()` function:

```text
en_us-01 garbage= False classes= ['PLAUSIBLE'] cer= 0.025 text= "When you call someone who is thousands of miles away, you're using a satellite."
en_us-02 garbage= False classes= ['PLAUSIBLE'] cer= 0.0754 text= 'Now widely available throughout the Archaeopologno, Japanese cuisine features an array of simply seasoned dishes, the predominant flavorings, the Japanese favorite being peanut, chilies, sugar, especially Japanese coconut sugar, and various aromatic spices.'
en_us-03 garbage= False classes= ['PLAUSIBLE'] cer= 0.0459 text= 'The UN also hopes to finalize a fund to help countries affect it by global warming to cope with the impacts.'
en_us-04 garbage= True classes= ['PLAIN_MISRECOGNITION'] cer= 0.5 text= 'Then, like I say, to delete and sing the passions.'
en_us-05 garbage= False classes= ['PLAUSIBLE'] cer= 0.0385 text= 'The major religion in Wildova is Orthodox Christian.'
en_us-06 garbage= False classes= ['PLAUSIBLE'] cer= 0.0 text= 'The East African Islands are in the Indian Ocean off the eastern coast of Africa.'
en_us-07 garbage= False classes= ['PLAUSIBLE'] cer= 0.0 text= 'Some festivals have special camping areas for families with young children.'
en_us-08 garbage= False classes= ['PLAUSIBLE'] cer= 0.1477 text= "But to begin, but Buddhism is based on the teachings of Buddhism, but we're extended by the Mayana Path of Love and by a lot of techniques from Indian Yoga."
en_us-09 garbage= False classes= ['PLAUSIBLE'] cer= 0.125 text= 'From now, Seetion is relatively easy in Italian since most wars are pronounced exactly how they are written.'
en_us-10 garbage= False classes= ['PLAUSIBLE'] cer= 0.063 text= 'In this dynamic transport shuttle, everyone is sure connected with and supporting a transport system based on private cars.'
```

The reference for `en_us-04` is: `Then, Lakkha Singh took the lead in singing the bhajans.`
This is a real recognition failure, not a VAD failure and not a firewall/fabrication issue.

## 4. Verdict

| Gate | Measured | Threshold | Result |
|---|---:|---:|---|
| English mean CER | 0.1021 | ≤ 0.20–0.25 | PASS |
| English garbage rate | 1/10 = 10% | ≤ 1% | **FAIL** |
| Silence/noise false-accept | 0/20 real CC0 clips | ≤ 2% | PASS for the 20-clip set |
| On-device WER/latency/RAM | UNMEASURED | — | not claimed |

English therefore remains **UNAVAILABLE / not VERIFIED** for speech output until a model passes
the garbage-rate gate. As required, `base.en` and `small.en` must be added to the evaluation-only
English bake-off. No threshold was lowered and no new model has been bundled.

Overall app-path garbage/unusable changed from `125/130` before fix (a) to `121/130` after fix (a).
That improvement is entirely from returning previously rejected clips to decoding; Indic models are
still unusable and remain `UNAVAILABLE` for speech.

## 5. Evidence files

Under `whisly/deliverables/english_remearure_after_fix_a/`:

- `rerun_exact_fixed_vad.py` — exact measurement script.
- `rerun_log.txt` — verbatim stdout/stderr plus `PYTHON_EXIT=0`.
- `exact_fixed_vad_results.jsonl` — all 130 rows with VAD details, raw output, `lang`, and hash.
- `exact_fixed_vad_report.md` — all raw outputs pasted beside references.
- `exact_fixed_vad_summary.md` — exact summary table pasted above.

No audio, model, or dataset was added to the app repo. The pinned FLEURS manifest and licenses are
already recorded in `docs/DIAGNOSIS9.md` and `deliverables/diagnosis9/`.

## 6. Language coverage after re-measurement

| Language | VAD after fix (a) | STT gate result | Bundle decision now | Speech output |
|---|---|---|---|---|
| English (`en`) | 10/10 accepted | CER pass, garbage 10% fail | keep Tiny bundled; add `base.en`/`small.en` to eval-only bake-off | UNAVAILABLE until a model passes |
| Hindi (`hi`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Bengali (`bn`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Tamil (`ta`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Telugu (`te`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Kannada (`kn`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Malayalam (`ml`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Marathi (`mr`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Gujarati (`gu`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Punjabi (`pa`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Urdu (`ur`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Odia (`or`) | 10/10 accepted | no Whisper token; 0/10 decoded | no model bundled | UNAVAILABLE |
| Assamese (`as`) | 10/10 accepted | garbage 10/10 | no Indic model bundled | UNAVAILABLE |
| Santali (`sat`) | UNMEASURED | UNMEASURED | no model bundled | UNAVAILABLE |

## 7. Questions only you can answer

1. Can you connect an Android device and paste the `docs/DEVICE_EVIDENCE_STEPS.md` outputs? The
   10% English garbage rate is measured on pinned desktop clips, not on the phone microphone.
2. Do you want the evaluation-only Whisper English bake-off (`base.en`, `small.en`) run after
   fixes (b)–(e) as planned, or do you want to pause all model evaluation until device evidence
   is available? I will not bundle any candidate without a separate explicit approval.
