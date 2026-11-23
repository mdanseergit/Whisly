# Fix (e) — one firewall path, no stale speech claims, bundled model retained

Date: 2026-11-23. Branch `voice-reset`. No model asset was added, removed, or replaced. No
bundle, fine-tune, upload, backend, or online mode was introduced.

## What changed

1. **One shared firewall.** `TranscriptFirewall.evaluateForLanguage()` is now the only STT
   result gate for both the main microphone and the conversation path. It NFC-normalises, strips
   every measured annotation class (`<|...|>`, `[MUSIC]`, `[SUMMING]`, `[SOMI]`,
   `[speaking in foreign language]`, ...), collapses Unicode whitespace, and applies the registry
   script gate (≥95% of letters in the selected language's script blocks; digits/punctuation
   neutral; Latin only for the Latin language). Wrong-script or annotation-only output is `FAIL`.
2. **No fabricated confidence.** `AudioProcessor.MIN_STT_CONFIDENCE = 0.55f` and the recognizer's
   `confidence < MIN_STT_CONFIDENCE` branch were deleted. Confidence decisions belong only to
   `TranscriptFirewall`, which returns `LOW_CONFIDENCE` for every current Sherpa result because
   `ys_log_probs` is measured empty. `PASS` still requires real per-token evidence and an explicit
   measured threshold.
3. **One local sanitizer removed.** `WhislyViewModel.sanitizeTranscript()` was deleted; both paths
   consume `result.text` from the same firewall result, so there is no divergence.
4. **Kept results on later-stage failure.** MT failure leaves `recognizedTranscript` intact; TTS
   failure leaves `translatedSentence` intact and moves the single orchestrator to
   `Error(UNSUPPORTED_TARGET_VOICE | TTS_FAILURE)`.
5. **Stale speech claims removed from registry APIs.** `sttCapabilityStatus`,
   `isSpeechToSpeechReady`, `toLanguageManifestInfo`, and `buildInitialLanguageEntities` now
   derive STT support from the measured `StageProfile` tier, not the legacy `canListen` row.
   A regression test asserts that manifest codes and stage-profile codes are the same set and
   that English is the only language allowed to own speech.
6. **Bundled model retained.** `assets/whisper/tiny/tiny-encoder.int8.onnx`,
   `tiny-decoder.int8.onnx`, and `tiny-tokens.txt` are unchanged in this step.

## Exact build outputs (pasted)

Command for both steps:
`$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"; .\gradlew.bat clean assembleDebug testDebugUnitTest`

Firewall/paths step (`deliverables/fix_e_firewall_paths/production_build.log`):

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 5m 13s
51 actionable tasks: 30 executed, 21 from cache
Configuration cache entry reused.
```
`<testsuite name="com.example.ExampleUnitTest" tests="26" skipped="0" failures="0" errors="0" timestamp="2026-11-23T06:25:08.072Z" hostname="MOHAMMEDDANSEER" time="9.241">`

Registry reconciliation step (`deliverables/fix_e_registry_reconcile/production_build.log`):

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 2m 8s
51 actionable tasks: 30 executed, 21 from cache
Configuration cache entry reused.
```
`<testsuite name="com.example.ExampleUnitTest" tests="27" skipped="0" failures="0" errors="0" timestamp="2026-11-23T06:28:58.020Z" hostname="MOHAMMEDDANSEER" time="7.722">`

Evidence: `deliverables/fix_e_firewall_paths/`, `deliverables/fix_e_registry_reconcile/` (full logs,
JUnit XML, source diffs).

## Real English garbage rate after the VAD fix

From `docs/ENGLISH_REMEASURE_AFTER_FIX_A.md` §3, exact 10-clip FLEURS `en_us` dev run through the
fixed production VAD and forced `en` decode:

```text
| language | decoded | mean CER | mean script ratio | flags | garbage/unusable |
|---|---:|---:|---:|---|---:|
| en | 10/10 | 0.1021 | 1.0 |  | 1/10 |
```

- Decoded: `10/10` (VAD false rejects eliminated: `0/10`, previously `5/10`).
- Mean CER `0.1021` is inside the ≤20–25% CER band.
- Garbage/unusable `1/10 = 10%`, which is **above** the ≤1% VERIFIED gate.
- Verdict: English STT is **BETA**, with mandatory edit+confirm, `Beta` badge, and no
  auto-translate. It is not VERIFIED and no threshold is lowered to make it pass.
- Across all 13 measured languages the app path garbage rate is `121/130`; every Indic language
  remains far outside the gate (see `docs/DIAGNOSIS9.md` and the remeasure table above).

## Language coverage table

| Code | STT tier / mic owner | Firewall outcome for non-empty result | Typed ML Kit text | Runtime TTS gate |
|---|---|---|---|---|
| en | BETA / yes | `LOW_CONFIDENCE` → editable + `Not sure: check and edit` | yes | exact Locale + installed non-network voice required |
| hi | UNAVAILABLE / no | no result reaches firewall | yes (`hi`) | same gate |
| bn | UNAVAILABLE / no | no result reaches firewall | yes (`bn`) | same gate |
| ta | UNAVAILABLE / no | no result reaches firewall | yes (`ta`) | same gate |
| te | UNAVAILABLE / no | no result reaches firewall | yes (`te`) | same gate |
| kn | UNAVAILABLE / no | no result reaches firewall | yes (`kn`) | same gate |
| ml | UNAVAILABLE / no | no result reaches firewall | no tag (typed fallback only) | same gate |
| mr | UNAVAILABLE / no | no result reaches firewall | yes (`mr`) | same gate |
| gu | UNAVAILABLE / no | no result reaches firewall | yes (`gu`) | same gate |
| pa | UNAVAILABLE / no | no result reaches firewall | no tag (typed fallback only) | same gate |
| ur | UNAVAILABLE / no | no result reaches firewall | yes (`ur`) | same gate |
| or | UNAVAILABLE / no (no Whisper token) | no result reaches firewall | no tag | same gate |
| as | UNAVAILABLE / no | no result reaches firewall | no tag | same gate |
| sat | UNAVAILABLE / no, deferred | no result reaches firewall | no tag | same gate |

Whisper `result.lang` is logged but not used for routing; speech auto-detect is `UNAVAILABLE`, so
there is no Whisper-vs-ML-Kit LID mismatch in the speech path to confirm. Typed-text LID remains a
fallback only in `TextTranslationScreen`. All speech results require an explicit Translate tap,
which is the user confirmation point.

## Still UNMEASURED

- Phone WER/CER, garbage rate, latency, RAM, cold start, ABI/Android/device model.
- Clean-install airplane-mode proof for typed ML Kit translation.
- Per-device TTS voice availability and spoken-output quality.
- Play Console compressed size; acoustic language ID; Santali speech (deferred).

## Questions only you can answer

1. Can you connect an Android device and paste the outputs from `docs/DEVICE_EVIDENCE_STEPS.md`,
   including the exact TTS voice dump, so the BETA badge and voice gate can be measured on a phone?
2. Do you want the evaluation-only Whisper English bake-off (`base.en`, `small.en`) now, or paused
   until device evidence is available? No candidate will be bundled without separate approval.
3. Keep the 20-clip CC0 noise corpus as the acceptance set, or provide your own room/vehicle/street
   recordings for an additional rejection pass?
