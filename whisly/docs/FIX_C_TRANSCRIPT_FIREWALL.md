# Fix (c): fail-closed transcript firewall

Status: **IMPLEMENTED / BUILD VERIFIED**. On-device interaction remains `UNMEASURED`; no phone was
attached. No model, language, bundle, or network mode was changed.

Baseline: commit `4ade595` (`Fix B: remove speech auto-detect path`).

## 1. Evidence rule

`sherpa-onnx` 1.13.5's `OfflineRecognizerResult` exposes `text`, `lang`, `tokens`,
`ys_log_probs`, `timestamps`, and `durations`, but `ys_log_probs` is empty on this runtime. There
is no real per-token probability or log-probability for the bundled Whisper Tiny INT8 result.

Therefore:

| Engine evidence | Firewall outcome |
|---|---|
| blank text or engine/VAD failure | `FAIL` |
| bundled Sherpa Whisper Tiny result (`confidence == null`) | `LOW_CONFIDENCE` |
| future engine with real per-token evidence **and** an explicit measured threshold | `PASS` possible |

No VAD value, SNR value, text length, script ratio, or default number is used as confidence.

## 2. What changed

1. Added `ai/stt/TranscriptFirewall.kt` with the three outcomes `PASS`, `LOW_CONFIDENCE`, `FAIL`
   and the exact user-facing strings:
   - `Not sure: check and edit`
   - `Couldn't understand. Try again.`
2. Main speech recording now always stops at `AWAITING_CONFIRMATION`. The automatic
   `confirmTranscriptAndTranslate()` call after STT was removed. The transcript is editable and the
   user must tap **Translate**.
3. `LOW_CONFIDENCE` shows `Not sure: check and edit` above the editable transcript.
4. `FAIL` clears transcript/translation state, sets `STT_FAILURE`, shows only
   `Couldn't understand. Try again.`, and sends nothing to translation, history, or TTS.
5. Conversation speech no longer creates a turn immediately. It stores the pending speaker,
   shows the same editable review row, and emits a turn only after **Translate** is tapped.
6. Removed fabricated confidences:
   - `outcome.confidence ?: 1.0f` removed from conversation STT.
   - `state.sttConfidence ?: 0f` removed from history recording.
   - typed text and typed conversation turns store `null` STT confidence.
7. Room schema moved from version 4 to 5. `translation_history.sttConfidence` is now nullable.
   `MIGRATION_4_5` rebuilds the table, copies every row, and recreates the `createdAt` index. No
   destructive migration fallback was added.
8. Added unit tests for firewall outcomes, exact strings, absence of fabricated confidence, and
   absence of an automatic translation call.

## 3. Exact build output

Command:

```powershell
$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

Raw log: `deliverables/fix_c_transcript_firewall/production_build.log`.

Verbatim final lines:

```text
> Task :app:compileDebugUnitTestKotlin
w: file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/test/java/com/example/ExampleUnitTest.kt:620:24 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.

> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 52s
51 actionable tasks: 31 executed, 20 from cache
Configuration cache entry reused.
```

JUnit XML at `deliverables/fix_c_transcript_firewall/TEST-com.example.ExampleUnitTest.xml`
starts with the exact line:

```text
<testsuite name="com.example.ExampleUnitTest" tests="22" skipped="0" failures="0" errors="0" timestamp="2026-11-23T05:37:35.185Z" hostname="MOHAMMEDDANSEER" time="14.208">
```

The warning is in the pre-existing real-clip parity harness and is not a test failure.

## 4. Evidence files

Under `whisly/deliverables/fix_c_transcript_firewall/`:

- `production_build.log` — verbatim stdout/stderr from the exact required build command.
- `TEST-com.example.ExampleUnitTest.xml` — 22 tests, 0 failures, 0 errors.
- `WhislyDatabase-5.json` — exported Room schema; `sttConfidence` is nullable.
- `transcript_firewall.diff` — exact source diff for this step.

## 5. Language coverage after fix (c)

This fix changes routing, not model quality. The bundled decoder still has no real confidence
signal, so every non-empty current-model transcript is `LOW_CONFIDENCE` regardless of language.

| Language | Current STT firewall outcome | Auto-translate before user tap | Speech output | Typed text |
|---|---|---|---|---|
| English (`en`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Hindi (`hi`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Bengali (`bn`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Tamil (`ta`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Telugu (`te`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Kannada (`kn`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Malayalam (`ml`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Marathi (`mr`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Gujarati (`gu`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Punjabi (`pa`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Urdu (`ur`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Odia (`or`) | no Whisper token; STT fails before transcript firewall | no | UNAVAILABLE | unchanged |
| Assamese (`as`) | `LOW_CONFIDENCE` for every non-empty result | no | UNAVAILABLE | unchanged |
| Santali (`sat`) | `UNMEASURED`; no speech model | no | UNAVAILABLE | unchanged |

## 6. Questions only you can answer

1. Can you connect an Android device and paste the `docs/DEVICE_EVIDENCE_STEPS.md` outputs? The
   review banner and explicit Translate tap are build-verified but not phone-measured.
2. Do you want the evaluation-only Whisper English bake-off (`base.en`, `small.en`) after fixes
   (d)–(e), or paused until device evidence is available? No candidate will be bundled without a
   separate explicit approval.
3. Do you want to keep the 20-clip CC0 noise corpus as the acceptance set, or provide your own
   representative real-world room/vehicle/street recordings for an additional rejection pass?
