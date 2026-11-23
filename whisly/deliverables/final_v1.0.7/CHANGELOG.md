# Changelog — Whisly 1.0.7 (versionCode 7)

Branch `voice-reset`. Baseline `033b53f` tagged `pre-voice-reset`. All changes are evidence-driven;
no language was removed, no model was substituted, and no backend/online mode was introduced.

## Summary

Voice pipeline reset from measured evidence. The bundled Whisper Tiny INT8 model is retained.
English speech remains **BETA** with mandatory edit+confirm; every Indic language remains
**UNAVAILABLE** for speech. Typed ML Kit translation, Room storage, settings, navigation and crash
handling are retained.

## Changes by commit

- `492fa4f` Step 1 — source/evidence map (`docs/APP_STACK_MAP.md`).
- `c2496a2` Step 2 — bundled public sherpa-onnx multilingual Whisper Tiny INT8 assets.
- `e7af38d` — full 6-way diagnosis matrix on 130 pinned FLEURS clips (`docs/DIAGNOSIS9.md`).
- `53f51c4` Fix A — production VAD port; 130/130 real speech accepted, 20/20 real noise rejected.
- `dadfcc3` — English remeasure after VAD fix: 10/10 decoded, CER 0.1021, garbage 1/10 = 10%.
- `4ade595` Fix B — removed speech auto-detect from all recording paths; typed-text LID fallback
  remains only in `TextTranslationScreen`.
- `a1c55a4` Fix C — fail-closed `TranscriptFirewall`; no fabricated confidence; Room 4→5 migration
  with nullable `sttConfidence`.
- `390757b` Fix D1 — single immutable `PipelineOrchestrator`.
- `2948e0e` Fix D2 — single `CapabilityRegistry.StageProfile` language registry.
- `59de126` Fix D3 — fail-closed TTS gate with official voice-data install intent.
- `f0a92d3` Fix E — one shared firewall for both microphone paths, measured registry claims,
  kept-results-on-later-stage-failure.
- `c5582aa` — evaluation-only Whisper English bake-off (`base.en`, `small.en`); nothing bundled.
- Step 7 (this release) — fixed a lint-detected API-24 crash in `ExitReportManager.readTraceSummary`
  with `@RequiresApi(R)`; lint passes with 0 errors; version bumped to 7 / 1.0.7.

## User-visible error strings (unchanged set)

`Microphone permission needed`, `Microphone is blocked or muted`, `Speak a little louder or closer`,
`Couldn't hear that. Try again.`, `Couldn't understand. Try again.`,
`Speech isn't available for <language> yet`, `Voice not available for <language>`,
`Not sure: check and edit`. The string `install Google offline speech pack` is never shown.

## Model and size

Bundled STT: Whisper Tiny INT8 (MIT weights, Apache-2.0 export tooling) — unchanged.
AAB `87,633,723` bytes; release APK `113,232,981` bytes. SHA-256 values in `SHA256SUMS.txt`.
Play Console compressed size is `UNMEASURED` (no console account in scope).

## Known limitations

No Android device/emulator was available, so phone WER/CER, garbage rate, latency, RAM, cold start,
per-device TTS voices, spoken-output quality and clean-install airplane-mode proof are `UNMEASURED`.
Instrumented tests are blocked (`No connected devices!`). No candidate supplies per-token
confidence, so no STT result can reach firewall `PASS`.
