# Fix (d) — single orchestrator, single registry, fail-closed TTS gate

Date: 2026-11-23. Branch `voice-reset`. No push, no remote.

## Scope actually implemented

| Sub-step | Commit | Change |
|---|---|---|
| D1 | `390757b` | One immutable `PipelineOrchestrator` owns every transition; `SpeechToSpeechPipeline` no longer holds a second stage; error banners are set before `PipelineStage.ERROR`; `onAppBackgrounded()` cancels to `Idle`; mic orb derives from `OrchestratorState`, not status-banner text. |
| D2 | `2948e0e` | One `CapabilityRegistry.StageProfile` owns STT model id/code, ML Kit translation tag, ML Kit LID tag, TTS locale/voice, script blocks, and per-stage tiers. Translation, STT, LID, and TTS files delegate to it instead of owning language maps. |
| D3 | this step | TTS gate from `docs/TTS_OFFICIAL_API_EVIDENCE.md`; exact error text; official voice-data intent. |

## Exact build outputs (pasted)

D1 (`deliverables/fix_d_orchestrator/production_build.log`):

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 4m 47s
```
JUnit: `<testsuite name="com.example.ExampleUnitTest" tests="23" skipped="0" failures="0" errors="0" ...>`

D2 (`deliverables/fix_d_registry/production_build.log`):

```
BUILD SUCCESSFUL
```
JUnit: `<testsuite name="com.example.ExampleUnitTest" tests="24" skipped="0" failures="0" errors="0" timestamp="2026-11-23T06:08:20.597Z" ...>`

D3 (`deliverables/fix_d_tts_gate/production_build.log`), exact command
`$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"; .\gradlew.bat clean assembleDebug testDebugUnitTest`:

```
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 2m 24s
51 actionable tasks: 31 executed, 20 from cache
Configuration cache entry reused.
```
JUnit: `<testsuite name="com.example.ExampleUnitTest" tests="25" skipped="0" failures="0" errors="0" timestamp="2026-11-23T06:15:23.272Z" hostname="MOHAMMEDDANSEER" time="9.053">`

Evidence: `deliverables/fix_d_orchestrator/`, `deliverables/fix_d_registry/`, `deliverables/fix_d_tts_gate/` (full logs, JUnit XML, source diffs).

## TTS gate detail (evidence-backed, fails closed)

- Exact `Locale` comes only from `CapabilityRegistry.ttsLocale(code)`.
- `isLanguageAvailable(locale)` accepts **only** `LANG_COUNTRY_AVAILABLE` (1) or
  `LANG_COUNTRY_VAR_AVAILABLE` (2). `LANG_AVAILABLE` (0), `LANG_MISSING_DATA` (-1),
  and `LANG_NOT_SUPPORTED` (-2) are rejected — see `docs/TTS_OFFICIAL_API_EVIDENCE.md` §1.
- `getVoices()` must contain a locale-matching voice without `KEY_FEATURE_NOT_INSTALLED`
  (`"notInstalled"`) and with `isNetworkConnectionRequired() == false` (§2).
- `setVoice()` must succeed and the active `getVoice()` locale must still match; any engine
  substitution to a different voice is rejected (no wrong-language or Latin-script fallback).
- Spoken text must have ≥95% of its letters inside the target language's registry script blocks
  (digits/punctuation neutral). Non-Latin text is never handed to a Latin voice.
- Missing voice shows exactly `Voice not available for <language>` and offers
  `TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA`. The forbidden offline *speech*-pack string is
  never used.
- TTS failure now keeps the translated text for editing and moves the single orchestrator to
  `Error(UNSUPPORTED_TARGET_VOICE | TTS_FAILURE)` instead of silently going `Idle`.
- No GPL, non-commercial, bundled, or network voice is introduced. The temporary system TTS
  decision is unchanged.

## Language coverage table (registry truth, 2026-11-23)

`STT tier` is measured; `MT`, `LID`, `TTS` tiers are `UNAVAILABLE` until device/acceptance
evidence exists. `ML Kit` columns are official support tags, not quality claims.

| Code | Script block(s) | STT model / tier | ML Kit MT tag | ML Kit LID tag | TTS locale / voice | Voice data on device |
|---|---|---|---|---|---|---|
| en | LATN | `sherpa_onnx_whisper_tiny_int8` / **BETA** | en | en | en-IN / `system_tts` | UNMEASURED |
| hi | DEVA | none / UNAVAILABLE | hi | hi | hi-IN / `system_tts` | UNMEASURED |
| bn | BENG | none / UNAVAILABLE | bn | bn | bn-IN / `system_tts` | UNMEASURED |
| ta | TAML | none / UNAVAILABLE | ta | ta | ta-IN / `system_tts` | UNMEASURED |
| te | TELU | none / UNAVAILABLE | te | te | te-IN / `system_tts` | UNMEASURED |
| kn | KNDA | none / UNAVAILABLE | kn | kn | kn-IN / `system_tts` | UNMEASURED |
| ml | MLYM | none / UNAVAILABLE | NONE | ml | ml-IN / `system_tts` | UNMEASURED |
| mr | DEVA | none / UNAVAILABLE | mr | mr | mr-IN / `system_tts` | UNMEASURED |
| gu | GUJR | none / UNAVAILABLE | gu | gu | gu-IN / `system_tts` | UNMEASURED |
| pa | GURU | none / UNAVAILABLE | NONE | pa | pa-IN / `system_tts` | UNMEASURED |
| ur | ARAB | none / UNAVAILABLE | ur | ur | ur-IN / `system_tts` | UNMEASURED |
| or | ORYA | none / UNAVAILABLE (no tokenizer token) | NONE | NONE | or-IN / `system_tts` | UNMEASURED |
| as | BENG | none / UNAVAILABLE | NONE | NONE | as-IN / `system_tts` | UNMEASURED |
| sat | OLCK | none / UNAVAILABLE (no tokenizer token) | NONE | NONE | sat-IN / `system_tts`, legacy `canSpeak=false` | UNMEASURED, deferred |

Additional registry-only codes `ne, sa, sd, brx, doi, ks, kok, mai, mni` remain typed-only with
all measured stages `UNAVAILABLE`; they are not additional FLEURS-measured languages.

## Still UNMEASURED / not claimed

- On-device WER/CER, garbage rate, latency, RAM, cold start, ABI/Android/device model.
- Clean-install airplane-mode proof for typed ML Kit translation.
- Per-device TTS voice availability, exact-locale `isLanguageAvailable` return codes,
  `getVoices()` contents, `notInstalled` and network flags, and spoken-output quality.
- Play Console compressed download size (no console account in scope).
- Acoustic language ID (speech auto-detect remains `UNAVAILABLE`).
- Santali speech (deferred by user; no further access attempts).

Known remaining reconciliation: the legacy `LanguageCapabilityEntry` rows (`canListen`,
`canSpeak`) still live in the same registry file beside `StageProfile`. Production gating now
uses `StageProfile` tiers, but the duplicate row fields will be collapsed in fix (e).

## Questions only you can answer

1. Can you connect an Android device and paste the outputs from `docs/DEVICE_EVIDENCE_STEPS.md`,
   including the TTS voice dump (`getVoices()`, locale, `notInstalled`, network flag) for at
   least `en-IN`, `hi-IN`, and `ta-IN`?
2. Do you want the evaluation-only Whisper English bake-off (`base.en`, `small.en`) after fixes
   (c)–(e), or paused until device evidence is available? No candidate will be bundled without a
   separate explicit approval.
3. Do you want the 20-clip CC0 noise corpus kept as the acceptance set, or will you provide your
   own representative real-world room/vehicle/street recordings for an additional rejection pass?
