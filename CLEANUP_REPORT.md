# CLEANUP_REPORT.md — Whisly folder only (2026-11-23)

Scope: `C:\Users\danse\OneDrive\document\Whisly` and Whisly-specific outputs under
`C:\dev\Whisly-eval`. **No device/adb/emulator/instrumented command was used in this pass**
(an earlier `adb devices` run by this agent at 08:26 today is disclosed in the evidence note
below). Nothing outside this scope was deleted or modified.

## Space before / after

| Measurement | Before | After |
|---|---:|---:|
| C: free | 69.96 GB | 71.34 GB after deletions/builds; **70.19 GB** after the final 573 MB backup |
| C: used | ~405.65 GB (85.3%) | ~405.42 GB after the final backup |
| Whisly folder | 1,127,976,627 B / 2,354 files | 996,064,606 B / 438 files |
| `C:\dev\Whisly-eval` | 4,970.0 MB | 2.8 MB |
| `.git` | 240.1 MB | 232.3 MB |

The user's "~20 GB full" figure did not match the measured drive; the user did not select an
alternative, so measured values are reported. `SIZE_MAP.md` has the full inventory.

## Deletion table

| Path | Size | Class | Why safe | How to restore |
|---|---:|---|---|---|
| `whisly\app\build`, `whisly\build`, `whisly\.gradle`, `whisly\.kotlin` | 65.35 MB | REGENERABLE | Gradle outputs only; no source | `./gradlew assembleDebug` (`.gradle` 8.09 MB was recreated by the final builds and removed again) |
| 7 old APKs at repo root / `deliverables\` (v1.0.0, v1.0.1, latest, step2, v1.0.5/6 debug+release) | 354.07 MB | RE-DOWNLOADABLE / REGENERABLE | Superseded, untracked debug/old release binaries; newest verified v1.0.7 retained | lean backup zip or rebuild |
| 4 tracked `.sha256` sidecars for v1.0.5/v1.0.6 | tiny | obsolete evidence for deleted files | removed with `git rm`; commit `d5836d1` | `git revert d5836d1` |
| `C:\dev\Whisly-eval\bakeoff2\models` | 4,022.9 MB | RE-DOWNLOADABLE | scratch model copies; hashes + source URLs recorded first | re-download per `deliverables/bakeoff_partial/download_models_manifest.json` |
| `C:\dev\Whisly-eval\models` | 511.3 MB | RE-DOWNLOADABLE | `base.en`/`small.en` scratch; hashes in `MODELS_PINNED.md` | re-download per pinned doc |
| `bakeoff2\kathbath_hi`, `clips`, `real_nonspeech`, `adversarial`, `__pycache__` | ~432 MB | RE-DOWNLOADABLE / scratch | manifests copied into repo first | re-prepare from manifests |
| git loose objects → single pack (`git gc --prune=now`) | 7.8 MB smaller | history compaction | no source removed; all commits retained | `git` pack is standard; backup exists |

Exact commands and proof were pasted during each group; after every group `Get-ChildItem`
confirmed the targets were gone and `git status --porcelain` showed no tracked source loss.
Commits: `8a4d830` (warning fixes + partial bake-off results), `d5836d1` (old APK removal),
`88af005` (remaining manifests) on branch `voice-reset`.

## Verified backups (outside OneDrive)

| File | Size | SHA-256 | Verification |
|---|---:|---|---|
| `C:\dev\Whisly-backups\Whisly_lean_20261123_144101.zip` | 326,052,290 B | `2B8DC2B02DCC0D7BB8F84D0F83A1827163E2C2BE3A350B91E839F369C4A03554` | 1,670 entries, 1,282 files test-extracted, 0 exclude violations, key files present, 2 spot-check hashes matched |
| `C:\dev\Whisly-backups\Whisly_final_20261123_150917.zip` | 573,143,076 B | `EE6A571124FDCF31EE4421D4DE1EB4933AF1BC813CD45C2E7F484C745A0B37CC` | 793 entries, 403 files test-extracted, 0 exclude violations, all 7 key files present, 3 hash spot-checks matched |

The final backup snapshot contains this report before the backup-hash line was filled in;
all other files are identical to the verified source tree.

The older 844 MB `Whisly_phase0_20261123_140456.zip` was **not** deleted (per-name confirmation
required). It is outside OneDrive and outside the Whisly folder.

## Build and verification evidence

- `clean assembleDebug testDebugUnitTest lint` → **BUILD SUCCESSFUL in 6m 26s**; JUnit XML
  **TESTS=47 FAILURES=0 ERRORS=0 SKIPPED=0**; lint **0 errors, 105 warnings**
  (UnusedResources 36, GradleDependency 22, UseKtx 16, NewerVersionAvailable 10,
  IconLauncherShape 5, IconLocation 4, UseTomlInstead 4, AndroidGradlePluginVersion 2,
  IconDuplicates 1, OldTargetApi 1, ApplySharedPref 1, RedundantLabel 1,
  IconDuplicatesConfig 1, UnusedAttribute 1). One `ApplySharedPref` warning remains at
  `CrashProtectionManager.kt:109` — not fixed in this pass.
- `assembleRelease bundleRelease` → **BUILD SUCCESSFUL in 5m 27s**;
  `:app:minifyReleaseWithR8` executed.
- `apksigner verify`: release APK verifies; v2 scheme; one signer, certificate
  `CN=Android Debug` (SHA-256 `debb0143…93d7`) — **debug-signed preview, not a real upload key**.
- `aapt2 dump badging`: `com.aistudio.whisly.kxmpzq`, versionCode 7 / 1.0.7,
  minSdk 24, targetSdk 36, permissions: RECORD_AUDIO, VIBRATE, INTERNET, ACCESS_NETWORK_STATE,
  FOREGROUND_SERVICE, FOREGROUND_SERVICE_DATA_SYNC, POST_NOTIFICATIONS,
  DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION; `native-code: 'arm64-v8a'`.
- `apkanalyzer`: release APK 113,232,981 B, download-size estimate 83,476,730 B;
  `manifest debuggable=false`; biggest components: Tiny decoder 49.7 MB,
  `libonnxruntime.so` 20.7 MB, `libtranslate_jni.so` 15.6 MB, Tiny encoder 7.9 MB.
- Bundled Whisper Tiny INT8 decoder/encoder/tokens and arm64-v8a sherpa-onnx/ONNX libs
  confirmed present. No debug/test classes (`com/example/debug`, JUnit, AndroidX Test,
  LeakCanary, Stetho, Flipper: 0 hits) and no secret-like key patterns (0 hits) in the APK.
- URL scan: 171 unique strings. App-specific ones checked in source:
  `api.whisly.ai` is an unused `backendBaseUrl` config value (only one reference in
  `Entities.kt`); `devseer.vercel.app` and `github.com/mdanseergit` are the portfolio/GitHub
  links in `AppConfig.kt` and covered by tests. The rest are library/documentation URLs
  (Google, sherpa-onnx, HuggingFace, ICU/Wikipedia language metadata).
- SHA-256: release APK `D58A6B817DE87255EA53C15702CBCE10EF880D6578243CB7B1A670057E0CC93E`;
  debug APK `D78482AD74FD6D91AB627E494D6B647B4616522D7EBD94C69D9B5488D6B1B724`;
  locally-built AAB `F191E339E992BCECDB2EEFBFE6F7715B77A28E574A181AC882E55F91439C0D58`
  (built but **not delivered**: no real upload key); mapping
  `C470BC92FC5DF9800016C6742AB86CA65FD504984DA59E35BFD67B2D0A110A70`.
- R8 keep rules listed and logged: JNI `native <methods>`, sherpa-onnx consumer rules file
  was empty, ONNX has no explicit keep rule (relying on JNI rule), ML Kit
  `-keep class com.google.mlkit.**`, Room database/entity/DAO rules plus Room consumer rule,
  Moshi rules plus Moshi consumer file, source-file/line-number attributes retained.

### Defender custom scan of `deliverables\FINAL_APK\`

Command: `Start-MpScan -ScanType CustomScan -ScanPath <FINAL_APK>`.
It returned no error. `Get-MpScan` is not available on this system. Operational event log:

- 15:04:49 event 1000 — scan started, `Scan Parameters: Custom Scan`
- 15:04:59 event 1001 — scan finished, `Scan ID {73D8DDB7-A847-44CB-AE53-E5D2AE079738}`,
  `Scan Type: Antimalware`, `Parameters: Custom Scan`, `Scan Time: 0:00:10`
- `Get-MpThreatDetection` count was 10 before and 10 after; **no detection referenced FINAL_APK**.
- `Get-MpComputerStatus`: RealTimeProtectionEnabled True, signatures `1.463.424.0` updated
  23-11-2026 01:12:47. The event message did not report a file count or "clean" verdict;
  only the above observations are claimed.

**Release runtime behaviour remains UNMEASURED** — static checks only, no device run.

## What remains large and why

- `FINAL_APK` 292.8 MB (two APKs + mapping) — requested deliverable; hashes in `SHA256SUMS.txt`.
- `final_v1.0.7` 245.4 MB — verified baseline; needs explicit approval before deletion.
- `.git` pack 232.2 MB — contains large historical blobs; history rewrite not approved.
- Bundled Whisper Tiny ONNX 98.8 MB — app asset, never delete.
- Outer `deliverables\whisly-whisper-tiny-stt.zip` 57.9 MB — UNKNOWN purpose, left untouched.
- `C:\dev\Whisly-eval-venv` 1,960.1 MB — re-creatable Python env, not touched.
- `C:\dev\Whisly-backups` — two zips, one old 844 MB awaiting per-name confirmation.

## Large items outside Whisly — REPORT ONLY

Top C: folders: `C:\games` ~1,400 GB, `C:\Windows` ~39 GB, `C:\Riot Games` ~34 GB,
`C:\Program Files` ~15.6 GB, `C:\Program Files (x86)` ~9.3 GB, `C:\ProgramData` ~5.0 GB,
`C:\dev` ~3.45 GB, then much smaller. One top-level folder could not be enumerated and some
`C:\Users` subtrees are undercounted due to access restrictions. Nothing there was modified.

Defender threat history (all `IsActive=False`) including cracked-game proxy DLLs under
`C:\games\REPO-AnkerGames` and `C:\games\AnkerGames` is evidence and was **not** touched;
it is outside this cleanup scope.

## Evidence kept / not touched

PSReadLine history, `%USERPROFILE%\.android`, Android Studio logs, Gradle daemon logs,
OpenCode logs, Windows event logs, OneDrive content outside Whisly, and all incident-related
evidence were left unaltered. Disclosure: this agent ran `adb devices` at 08:26 today before
the scope was narrowed; that command is in the incident evidence and was not deleted.

## What I could not check

- Any real-phone behaviour, speed, memory, TTS voice quality, native-speaker translation
  quality, airplane-mode proof, or Play Console compressed size — no device/upload was used.
- Full bake-off outcome: 346 rows were preserved, final summary was never produced.
- Full-history secret scan with gitleaks (tool installed via winget but not runnable in the
  build shell); a local regex scan of build inputs found 0 hits, and the APK scan found 0.
- All C: folder sizes (permissions/reparse points), and the unresolved purpose of
  `whisly-whisper-tiny-stt.zip`.
- OneDrive pause was offered again with the exact click path; the user chose **Leave it on**.
  OneDrive sync state was not changed.

## Proposals and decisions (2026-11-23)

None of the following were executed; all were declined by the user:

1. `git filter-repo` to drop the two tracked release binaries from history: pack 232.2 MB →
   roughly 40–50 MB (estimate; would require full backup and a forced refetch by any collaborator).
   **Decision: do nothing now.**
2. `git rm --cached whisly/deliverables/final_v1.0.7/app-release.{apk,aab}` plus `.gitignore`
   strengthening (files would remain on disk and in the lean backup). **Decision: do nothing now.**
3. Delete `whisly/deliverables/final_v1.0.7` (245.4 MB). **Decision: keep it.**
4. Delete the old 844 MB `C:\dev\Whisly-backups\Whisly_phase0_20261123_140456.zip` by name.
   **Decision: keep it.**
5. Pause OneDrive sync: tray icon → OneDrive → Help & settings → Settings → Sync and backup →
   **Pause syncing** → 2/8/24 hours. **Decision: leave sync on.**

The user also confirmed the measured free-space figure (70.19 GB after the final backup)
is the one to use, rather than the earlier "~20 GB full" estimate.
