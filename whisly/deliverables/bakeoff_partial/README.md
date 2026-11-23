# Bake-off partial results (evaluation only)

Saved 2026-11-23 before cleanup. This is **not** a completed bake-off.

## Run state (honest)
- The full bake-off was stopped mid-run. `full_results.jsonl` contains **346 rows**, ending at
  `whisper_small gu_in-06`. It was written incrementally, so no completed rows were lost.
- `full_summary.md`, `full_report.md` and the final language table were **not produced**.
- The earlier smoke bake-off (`smoke_*`) did complete.

## Files
| File | What it is |
|---|---|
| `full_results.jsonl` | One JSON object per clip/model result, written as each row completed. |
| `full_resume.log` | Console log of the main run (last lines: `whisper_small gu_in-06`). |
| `full_run.log` | Earlier full-run log that timed out at 3600s. |
| `download_models_manifest.json` | Model name, bytes, SHA-256, source URL/pin for every scratch model. |
| `download_models*.log` | Model download logs. |
| `smoke_*` | Completed smoke bake-off outputs and tables. |
| `prepare_kathbath*.log` | Hindi Kathbath test-set preparation logs. |
| `production_vad_nonspeech.log`, `real_nonspeech_rerun.log` | VAD / non-speech false-accept checks. |
| `SHA256SUMS.txt` | SHA-256 of every file in this folder. |

## Scratch models (NOT bundled, NOT in this repo)
The scratch model files live outside the repository under
`C:\dev\Whisly-eval\bakeoff2\models\` and were **not** copied here because of size.
Their source URLs/pins and SHA-256 hashes are recorded in `download_models_manifest.json`.
Nothing here is bundled into the app, fine-tuned, uploaded, or committed as a model.

## Scope note
This folder is text/CSV/JSON only. It exists so the partial measurements can be re-checked
or resumed later without re-downloading results.
