# Whisly v1.0 — Release scorecard

Maintained after every phase. Status at: **Phase 0 (safety and baseline), 2026-11-23**.
Branch `voice-reset`; v1.0.7 baseline (`versionCode=7`, `versionName="1.0.7"`).
Nothing here claims production readiness. Verdict is **NO-GO** until the MUST-PASS gates below have
pasted evidence and the user has approved any model/bundle change.

## Verdict history

| Phase | Verdict | Reason |
|---|---|---|
| 0 | NO-GO (expected at this phase) | Device crash evidence, signed release, offline proof, tier gates and visual/legal work not done; Phase 1 stability gate not started. |

## MUST-PASS gates (any failure = NO-GO)

| Gate | Phase | Status | Evidence / blocker |
|---|---|---|---|
| Git clean + tag + outside backup | 0 | PASS | `voice-reset` at `b6ab62e`, clean tree, `pre-voice-reset` at `033b53f`; baseline backup 254,493,504 B (SHA-256 `2ED041F15DF8EC92ACE7A77FD26E4A367DCC36D2AC030A001096F86E9BF8793B`); post-Phase-0 backup `C:\dev\Whisly-backups\Whisly_phase0_20261123_140456.zip` 844,346,113 B SHA-256 `3D6148C6761388216277A8F19C638E0BE5E52A01113DDCB3E17CEB5A0855E132` |
| Secrets absent from repo + history | 0 | PASS with limitation | Full-history scan of 18 commits: `TOTAL_HITS=0`; gitleaks not installed, so regex substitution only; no keystore/.env tracked |
| Real APK/AAB/size measured | 0 | PASS | `docs/BUILD_EVIDENCE.md`, `deliverables/final_v1.0.7/apkanalyzer/`; Play Console size still UNMEASURED |
| Baseline map matches code | 0 | PASS (corrected) | Phase 0 addendum in `docs/APP_STACK_MAP.md` supersedes stale baseline sections |
| Launch crash closed with device evidence | 1 | NOT PASSED / BLOCKED | No device attached; ApplicationExitInfo, adb exit-info, logcat, fresh/upgrade × debug/release × API 24/30/34/36 all UNMEASURED |
| Lazy engine loading, Safe Mode, DB recovery, migrations 1→2→3→4 | 1 | NOT MEASURED | Code paths exist; no build/runtime proof from device; migration tests pending exact commands |
| 30-minute soak + 20 cold starts | 1 | UNMEASURED | Requires phone/emulators |
| No fake/canned/garbage output reaches user | 2/7 | PARTIAL | Unit guards pass; measured app-path garbage 121/130 on bundled Whisper; firewall fails closed to LOW_CONFIDENCE/FAIL; device path UNMEASURED |
| Every advertised language meets measured tier | 2 | NOT PASSED | English STT BETA only; all Indic STT UNAVAILABLE; no VERIFIED language; bake-off running |
| No GPL/non-commercial component shipped | 0/4 | PASS (current tree) | `MODELS.md` / `licenses/NOTICE` reviewed; new candidates gated by user approval |
| Zero network for already-downloaded languages proven | 6/7 | UNMEASURED | Clean-install airplane-mode proof requires user phone |
| Signed release AAB verified | 9 | NOT PASSED | `my-upload-key.jks MISSING`; current AAB is debug-key signed (`debuggable=false` is separate) |
| Lint, tests, release smoke pass | 0/7 | IN PROGRESS | Phase 0 build/lint re-run pending after cleanup; prior lint 0 errors / 106 warnings, JUnit 46 tests |
| Accessibility basics pass | 5/7 | UNMEASURED | No device, no golden screenshots |
| Privacy policy + Data safety match code | 6 | NOT STARTED | Requires actual code audit + user Play Console |
| Phase 2 bake-off + approval gate | 2 | IN PROGRESS | 13 candidate models downloaded outside repo; 130-clip run in progress; STOP required before bundling |

## SHOULD-PASS gates (label if missing)

| Gate | Status | Note |
|---|---|---|
| Native-speaker review sheets | UNMEASURED | User-supplied references only; never invented |
| Visual polish / redesign | NOT STARTED | Phase 5 design plan requires explicit approval before code |
| Neural voices | NOT STARTED | System TTS remains temporary; user approved temporary use only |
| Baseline profile / Macrobenchmark numbers | UNMEASURED | Needs phone |

## UNMEASURED items and exact user steps

1. **Phone identity and logs (Phase 1)**: attach device with USB debugging, run
   `adb devices`, `adb shell dumpsys activity processes | grep com.aistudio.whisly.kxmpzq`,
   `adb shell dumpsys activity exit-info com.aistudio.whisly.kxmpzq`,
   `adb logcat -v threadtime com.example:* *:E > exit_log.txt`, then cold-start, leave on Home 5 min,
   and paste `exit_log.txt` plus the in-app Exit Report.
2. **Fresh install and upgrade**: uninstall/install and install-over-old with the same APK/AAB on API
   24, 30, 34/36 emulators and the phone; paste outcomes.
3. **Airplane-mode proof**: uninstall, enable airplane mode, launch, type English→Hindi and
   English→Tamil, paste outputs and 10-minute notification/log observation.
4. **TTS voice check**: on each device, Settings → Accessibility → Text-to-speech, paste installed
   voices and languages; run the in-app speak action for `en`, `hi`, `ta`.
5. **DEBUG probe screen**: open the DEBUG-only on-device recognizer screen, tap Refresh per language,
   tap a language's download, re-run Refresh, run the clip evaluation, then Share the CSV. Paste CSV.
6. **Signing key**: supply a release keystore path/alias or approve Play App Signing; do not paste the
   password into chat — use `local.properties` or environment variables.

## Next gate

- Phase 1 must be closed with device evidence before any later phase ships.
- Phase 2 ends with a STOP for model bundling approval. No model has been bundled, fine-tuned,
  uploaded or paid for during evaluation.
