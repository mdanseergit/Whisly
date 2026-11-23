# SIZE_MAP.md — Whisly folder only (2026-11-23)

All measurements are from this PC, no device commands. "Before" = Step 1/3 inventory;
"after" = after cleanup and after building `deliverables/FINAL_APK`.

## Drive and folder totals

| Scope | Before | After |
|---|---:|---:|
| C: total | 475.61 GB | 475.61 GB |
| C: free | **69.96 GB** | **71.34 GB** |
| C: used | ~405.65 GB (85.3%) | ~404.27 GB (85.0%) |
| `C:\Users\danse\OneDrive\document\Whisly` | 1,127,976,627 B (1.05 GB), 2,354 files | 996,064,606 B (949.9 MB), 438 files |
| `.git` | 240.1 MB (239.73 MiB loose objects) | 232.3 MB (single 232.22 MiB pack) |

The user's "~20 GB full" figure did not match the measured drive; the user did not answer
which figure to use, so measured values are reported.

## 40 largest folders inside Whisly (after cleanup; recursive)

```
       949.9 MB  .
       653.8 MB  .\whisly
       541.7 MB  .\whisly\deliverables
       292.8 MB  .\whisly\deliverables\FINAL_APK
       245.4 MB  .\whisly\deliverables\final_v1.0.7
       232.3 MB  .\.git
       232.2 MB  .\.git\objects
       232.2 MB  .\.git\objects\pack
       102.2 MB  .\whisly\app
       102.1 MB  .\whisly\app\src
       102.0 MB  .\whisly\app\src\main
        98.8 MB  .\whisly\app\src\main\assets\whisper
        98.8 MB  .\whisly\app\src\main\assets
        98.8 MB  .\whisly\app\src\main\assets\whisper\tiny
        58.1 MB  .\deliverables
         8.1 MB  .\whisly\.gradle
         6.8 MB  .\whisly\.gradle\9.3.1
         6.5 MB  .\whisly\.gradle\9.3.1\executionHistory
         3.6 MB  .\audit
         3.0 MB  .\audit\splash_preview
         2.5 MB  .\whisly\app\src\main\res
         1.5 MB  .\whisly\deliverables\diagnosis9
         1.2 MB  .\whisly\.gradle\configuration-cache
         1.1 MB  .\whisly\public\assets
         1.1 MB  .\whisly\public\assets\aistudio
         1.1 MB  .\whisly\public
         1.1 MB  .\whisly\deliverables\bakeoff_partial
         1.0 MB  .\whisly\app\src\main\res\drawable
         0.9 MB  .\scratch
         0.7 MB  .\whisly\app\src\main\java
         0.7 MB  .\whisly\app\src\main\java\com\example
         0.7 MB  .\whisly\app\src\main\java\com
         0.7 MB  .\whisly\.gradle\configuration-cache\e818cb09-c974-4290-8bc1-1b23d09cb7ca
         0.6 MB  .\audit\splash_tint_candidates
         0.5 MB  .\whisly\.gradle\configuration-cache\b50bc022-db95-4839-90b1-17259b450f83
         0.5 MB  .\whisly\app\src\main\res\drawable-xxxhdpi
         0.4 MB  .\whisly\docs
         0.3 MB  .\whisly\app\src\main\res\font
         0.3 MB  .\whisly\.gradle\9.3.1\fileHashes
         0.3 MB  .\whisly\app\src\main\res\drawable-xxhdpi
```

`.gradle` (8.09 MB) was recreated by the two Gradle builds; it is regenerable and was
removed again after the final build/backup.

## 40 largest files inside Whisly (after cleanup)

```
       232.2 MB  .\.git\objects\pack\pack-5097e3b3866229303e05d9baad59c1684c527c12.pack
       131.0 MB  .\whisly\deliverables\FINAL_APK\Whisly-FINAL-debug-fallback.apk
       108.0 MB  .\whisly\deliverables\FINAL_APK\Whisly-FINAL.apk
       108.0 MB  .\whisly\deliverables\final_v1.0.7\app-release.apk
        85.7 MB  .\whisly\app\src\main\assets\whisper\tiny\tiny-decoder.int8.onnx
        83.6 MB  .\whisly\deliverables\final_v1.0.7\app-release.aab
        57.9 MB  .\deliverables\whisly-whisper-tiny-stt.zip
        53.8 MB  .\whisly\deliverables\FINAL_APK\mapping.txt
        53.7 MB  .\whisly\deliverables\final_v1.0.7\mapping.txt
        12.3 MB  .\whisly\app\src\main\assets\whisper\tiny\tiny-encoder.int8.onnx
         6.5 MB  .\whisly\.gradle\9.3.1\executionHistory\executionHistory.bin
         1.1 MB  .\whisly logo.png
         1.1 MB  .\whisly\public\assets\aistudio\whisly_logo_2acce0a0.png
         0.9 MB  .\scratch\cleaned_full_master.png
         0.8 MB  .\whisly\app\src\main\assets\whisper\tiny\tiny-tokens.txt
         0.8 MB  .\audit\splash_preview\02_cleaned_checkerboard.png
         0.8 MB  .\audit\splash_preview\04_full_on_dark_bg.png
         0.8 MB  .\audit\splash_preview\03_full_on_light_bg.png
         0.5 MB  .\whisly\deliverables\diagnosis9\diagnosis9_results.jsonl
         0.5 MB  .\whisly\deliverables\bakeoff_partial\full_results.jsonl
         0.5 MB  .\whisly\.gradle\configuration-cache\e818cb09-c974-4290-8bc1-1b23d09cb7ca\_app.work.bin
         0.4 MB  .\whisly\deliverables\diagnosis9\diagnosis9_exact_results.jsonl
         0.3 MB  .\whisly\.gradle\configuration-cache\b50bc022-db95-4839-90b1-17259b450f83\_app.work.bin
         0.3 MB  .\whisly\docs\DIAGNOSIS9.md
         0.3 MB  .\audit\splash_preview\08_splash_dark_overlay.png
         0.3 MB  .\audit\splash_preview\07_splash_circle_mask_overlay.png
         0.3 MB  .\whisly\app\src\main\res\drawable-xxxhdpi\whisly_splash_logo.png
         0.3 MB  .\whisly\app\src\main\res\drawable\whisly_splash_logo.png
         0.2 MB  .\whisly\.gradle\9.3.1\fileHashes\fileHashes.bin
         0.2 MB  .\whisly\app\src\main\res\drawable-xxhdpi\whisly_tile_logo.png
         0.2 MB  .\whisly\app\src\main\res\drawable\whisly_launcher_fg.png
         0.2 MB  .\whisly\app\src\main\res\drawable-xxxhdpi\whisly_tile_logo.png
         0.2 MB  .\whisly\app\src\main\res\drawable\whisly_tile_logo.png
         0.2 MB  .\whisly\app\src\main\res\drawable\whisly_logo.png
         0.2 MB  .\whisly\deliverables\english_remearure_after_fix_a\exact_fixed_vad_results.jsonl
         0.2 MB  .\whisly\deliverables\bakeoff_partial\fleurs_manifest.json
         0.2 MB  .\whisly\deliverables\diagnosis9\fleurs_manifest.json
         0.2 MB  .\whisly\app\src\main\res\drawable-xxhdpi\whisly_splash_logo.png
         0.1 MB  .\whisly\deliverables\diagnosis9\diagnosis9_report.md
         0.1 MB  .\whisly\app\src\main\res\drawable-xxhdpi\whisly_tile_logo.png
```

## Build / cache directories

| Path | Before | After |
|---|---:|---:|
| `whisly\app\build` | 37.04 MB | deleted (regenerated during build, then cleaned) |
| `whisly\build` | 4.60 MB | deleted |
| `whisly\.gradle` | 23.72 MB | deleted; 8.09 MB recreated by builds, removed at end |
| `whisly\.kotlin` | 0.00 MB | deleted |
| `whisly\app\.cxx` | absent | absent |
| `__pycache__` under repo | small | deleted |

## Scratch / model folders (outside the repo)

| Path | Before | After |
|---|---:|---:|
| `C:\dev\Whisly-eval` | 4,970.0 MB | **2.8 MB** |
| `C:\dev\Whisly-eval\bakeoff2\models` | 4,022.9 MB | deleted |
| `C:\dev\Whisly-eval\models` | 511.3 MB | deleted |
| `C:\dev\Whisly-eval\bakeoff2\kathbath_hi` | 323.0 MB | deleted |
| `C:\dev\Whisly-eval\clips` | 95.9 MB | deleted |
| other scratch (`real_nonspeech`, `adversarial`, `__pycache__`) | ~14.1 MB | deleted |

Model bytes/SHA-256/source URLs were preserved in
`whisly/deliverables/bakeoff_partial/download_models_manifest.json` and
`whisly/deliverables/eval_english_bakeoff/MODELS_PINNED.md` **before** deletion.

## Duplicate / old release artifacts still present

| Path | Size | Class | Note |
|---|---:|---|---|
| `whisly\deliverables\final_v1.0.7\app-release.apk` | 108.0 MB | PRECIOUS (baseline verified) | kept until explicit approval to delete |
| `whisly\deliverables\final_v1.0.7\app-release.aab` | 83.6 MB | PRECIOUS | same |
| `deliverables\whisly-whisper-tiny-stt.zip` | 57.9 MB | UNKNOWN | purpose unclear — left untouched |
| `C:\dev\Whisly-backups\Whisly_phase0_20261123_140456.zip` | 844 MB | old backup | per-name confirmation required |
| `C:\dev\Whisly-backups\Whisly_lean_20261123_144101.zip` | 310.9 MB | verified lean backup | keep |
| `C:\dev\Whisly-eval-venv` | 1,960.1 MB | re-creatable Python env | left untouched, not a model/scratch output |

## Git history

- `git count-objects -vH` before: `count: 893`, `size: 239.73 MiB`, no packs.
- After `git gc --prune=now`: `count: 0`, `in-pack: 942`, `1 pack`, `size-pack: 232.22 MiB`.
- 30 largest history blobs (MB, object, path):

```
   107.99  fef07c729c  whisly/deliverables/final_v1.0.7/app-release.apk
    85.69  fbe50f90c8  whisly/app/src/main/assets/whisper/tiny/tiny-decoder.int8.onnx
    83.57  2b5eac2bf9  whisly/deliverables/final_v1.0.7/app-release.aab
    53.74  0ee9d68fac  whisly/deliverables/final_v1.0.7/mapping.txt
    12.34  998ecdae08  whisly/app/src/main/assets/whisper/tiny/tiny-encoder.int8.onnx
     1.08  337058ff11  whisly logo.png
     0.90  9417b547e1  scratch/cleaned_full_master.png
     0.78  a4edf0b719  whisly/app/src/main/assets/whisper/tiny/tiny-tokens.txt
     0.77  91d74f4ac6  audit/splash_preview/02_cleaned_checkerboard.png
     0.77  d92ea7c265  audit/splash_preview/04_full_on_dark_bg.png
     0.76  165244a8b0  audit/splash_preview/03_full_on_light_bg.png
     0.54  204f297320  whisly/deliverables/diagnosis9/diagnosis9_results.jsonl
     0.52  2b5684ecb2  whisly/deliverables/bakeoff_partial/full_results.jsonl
     0.36  3e83344057  whisly/deliverables/diagnosis9/diagnosis9_exact_results.jsonl
     0.28  f997ddcc7d  whisly/docs/DIAGNOSIS9.md
     0.27  4e6ea699c3  audit/splash_preview/08_splash_dark_overlay.png
     0.27  131ca2d3b7  audit/splash_preview/07_splash_circle_mask_overlay.png
     0.27  34818332be  whisly/app/src/main/res/drawable-xxxhdpi/whisly_splash_logo.png
     0.24  822f447254  whisly/app/src/main/res/drawable-xxxhdpi/whisly_tile_logo.png
     0.19  360c8b80e3  whisly/deliverables/english_remearure_after_fix_a/exact_fixed_vad_results.jsonl
     0.18  6e501ba3f8  whisly/deliverables/bakeoff_partial/fleurs_manifest.json
     0.15  d7d6ffc416  whisly/app/src/main/res/drawable-xxhdpi/whisly_splash_logo.png
     0.15  22aa33f0be  whisly/deliverables/diagnosis9/diagnosis9_report.md
     0.14  67a868b681  whisly/app/src/main/res/drawable-xxhdpi/whisly_tile_logo.png
     0.14  29ab01773a  audit/splash_tint_candidates/splash_E8DDD5.png
     0.14  e478ecb228  audit/splash_tint_candidates/splash_F5ECE4.png
     0.14  5f58199b8e  audit/splash_tint_candidates/splash_F8F2EC.png
     0.14  9a62c373ef  audit/splash_tint_candidates/splash_F3E9E0.png
     0.10  e2d29909d8  whisly/deliverables/bakeoff_partial/full_run.log
     0.09  fe7384be44  whisly/deliverables/diagnosis9/diagnosis9_exact_report.md
```

Tracked binaries at HEAD: bundled `tiny-decoder.int8.onnx`, `tiny-encoder.int8.onnx`,
`final_v1.0.7/app-release.apk`, `final_v1.0.7/app-release.aab`, `gradle-wrapper.jar`.
The two release binaries are the only large removable history entries; rewriting history
was **not** run (requires explicit approval).

## Classification

- **REGENERABLE**: `build/`, `.gradle/`, `.kotlin/`, `.cxx/`, `__pycache__`, mapping/build logs.
- **RE-DOWNLOADABLE (source + hash recorded)**: scratch bake-off models, clips, Kathbath set.
- **PRECIOUS (never deleted)**: source, `.git`, `local.properties`, docs, result tables,
  `references.csv`, licenses, bundled Whisper Tiny ONNX assets, keystores, verified baseline
  `final_v1.0.7` until approval.
- **UNKNOWN (left alone)**: `deliverables\whisly-whisper-tiny-stt.zip`, unresolved files.

## Top 15 C: folders outside Whisly — REPORT ONLY, nothing deleted

```
 1,40,479.7 MB  C:\games
    39,031.8 MB  C:\Windows
    34,458.4 MB  C:\Riot Games
    15,650.1 MB  C:\Program Files
     9,300.0 MB  C:\Program Files (x86)
     4,991.4 MB  C:\ProgramData
     3,452.7 MB  C:\dev
     1,156.3 MB  C:\$Recycle.Bin
       370.8 MB  C:\XboxGames
       173.4 MB  C:\system.sav
       156.9 MB  C:\Users
       156.9 MB  C:\tmp
       130.5 MB  C:\temp_build
        89.5 MB  C:\pub_cache
        74.7 MB  C:\hp
```

One top-level folder could not be enumerated (`Get-ChildItem: The system cannot find the
file specified`), and access restrictions mean some `C:\Users` subtrees are undercounted.
`C:\dev` includes the now-2.8 MB `Whisly-eval`, the 1,960 MB `Whisly-eval-venv` and the
`Whisly-backups` zips. Nothing outside the Whisly scope was modified.
