# Fix (b): remove speech auto-detect

Status: **IMPLEMENTED / BUILD VERIFIED**. On-device behaviour remains `UNMEASURED`; no phone was
attached. No model, language, bundle, or account setting was changed.

Baseline: commit `dadfcc3` (`Measure English STT after VAD fix`).

## 1. What was wrong

The speech path still contained legacy branches that treated the selected source as nullable:

- Restoring settings could persist a previously stored `sourceLangCode = "auto"`.
- After STT, `LanguageIdEngine.identifyLanguage(transcript)` could replace the forced decoder
  language with a text guess.
- `confirmTranscriptAndTranslate()` and the conversation path could also recompute a source
  language from `auto` or from `outcome.languageCode`.
- The voice UI still displayed `Auto-detect` if the state was stale.

That contradicted the measured finding that acoustic speech auto-detect is `UNAVAILABLE` and that
the bundled Whisper decoder is forced with the user-selected language.

## 2. What changed

1. `WhislyViewModel.init()` refuses to restore `auto` from older installs. Invalid spoken sources
   fall back to `ta`; typed-text fallback remains session-only.
2. `completeOnboarding()` sanitizes the preferred spoken source through `speechSourceOrNull()`.
3. `swapLanguages()` never lets typed-only `auto` become the spoken source or target.
4. Main recording validates the selected language before opening the microphone. Invalid speech
   sources fail closed with `UNSUPPORTED_SOURCE_LANGUAGE` and the exact banner
   `Speech isn't available for <language> yet`.
5. Conversation recording validates the actual speaker's source language the same way.
6. After STT, the source is always the user-selected language. No text LID call runs on the speech
   result, and no engine-reported language overrides the selection.
7. `TranslateScreen` no longer derives readiness, mic labels, or result labels from `auto`.
8. The auto-detect picker option is disabled in `HomeScreen`, `ConversationScreen`, and
   `TranslateScreen`. It remains enabled only in `TextTranslationScreen`, where it is a typed-text
   fallback and is labelled `Auto (typed text fallback)`.
9. `ExampleUnitTest` gained a source-guard regression test enforcing the voice-screen picker
   flags, the recording validation call, and the absence of `outcome.languageCode.ifBlank`.

## 3. Known limitation, documented

If the user selects the wrong spoken language, the decoder is forced with that wrong language.
The app does **not** detect or silently correct the mistake. The measured consequence from
`DIAGNOSIS9` is garbage or unusable transcript, especially for Indic targets; e.g. auto language
misidentification completely failed for Odia, Assamese, and Punjabi. The user must choose the
language they are actually speaking.

This step does not claim any accuracy improvement. It removes an unsound path and makes the
selected language authoritative.

## 4. Exact build output

Command:

```powershell
$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

Raw log: `deliverables/fix_b_no_speech_autodetect/production_build.log`.

Verbatim final lines:

```text
> Task :app:compileDebugUnitTestKotlin
w: file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/test/java/com/example/ExampleUnitTest.kt:575:24 Only safe (?.) or non-null asserted (!!.) calls are allowed on a nullable receiver of type 'File?'.

> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:processDebugUnitTestJavaRes
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 6m 14s
51 actionable tasks: 31 executed, 20 from cache
Configuration cache entry reused.
```

JUnit XML at `deliverables/fix_b_no_speech_autodetect/TEST-com.example.ExampleUnitTest.xml`
starts with the exact line:

```text
<testsuite name="com.example.ExampleUnitTest" tests="20" skipped="0" failures="0" errors="0" timestamp="2026-11-23T05:25:35.856Z" hostname="MOHAMMEDDANSEER" time="13.612">
```

The warning above is in the pre-existing real-clip parity harness (`file.parentFile.name`) and is
not a test failure.

## 5. Evidence files

Under `whisly/deliverables/fix_b_no_speech_autodetect/`:

- `production_build.log` — verbatim stdout/stderr from the exact required build command.
- `TEST-com.example.ExampleUnitTest.xml` — JUnit result, 20 tests, 0 failures, 0 errors.
- `removed_auto_detect.diff` — exact source diff for this step.

No audio, model, dataset, or new dependency was added.

## 6. Language coverage after fix (b)

Speech availability is unchanged by this fix; it reflects the measured evidence from
`DIAGNOSIS9` and `ENGLISH_REMEASURE_AFTER_FIX_A`.

| Language | User-selected STT now authoritative | STT evidence | Speech output | Typed translation |
|---|---|---|---|---|
| English (`en`) | yes | CER 0.1021 pass, garbage 10% fail | UNAVAILABLE | supported by ML Kit route |
| Hindi (`hi`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Bengali (`bn`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Tamil (`ta`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Telugu (`te`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Kannada (`kn`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Malayalam (`ml`) | yes | garbage 10/10 | UNAVAILABLE; ML Kit translation also unsupported | text route only via existing engine |
| Marathi (`mr`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Gujarati (`gu`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Punjabi (`pa`) | yes | garbage 10/10 | UNAVAILABLE; ML Kit translation unsupported | text route only via existing engine |
| Urdu (`ur`) | yes | garbage 10/10 | UNAVAILABLE | supported by ML Kit route |
| Odia (`or`) | yes | no Whisper token, 0/10 decoded | UNAVAILABLE; ML Kit translation/LID unsupported | text route only via existing engine |
| Assamese (`as`) | yes | garbage 10/10 | UNAVAILABLE; ML Kit translation/LID unsupported | text route only via existing engine |
| Santali (`sat`) | yes | `UNMEASURED` by user decision | UNAVAILABLE; ML Kit translation/LID unsupported | text route only via existing engine |

Auto-detect is now typed-text only in the Text Translation screen; it is not a speech source.

## 7. Questions only you can answer

1. Can you connect an Android device and paste the outputs from `docs/DEVICE_EVIDENCE_STEPS.md`?
   The source-selection code is verified by build and unit tests here, but the on-device flow is
   still `UNMEASURED`.
2. Do you want the evaluation-only Whisper English bake-off (`base.en`, `small.en`) after fixes
   (c)–(e), or paused until device evidence is available? No candidate will be bundled without a
   separate explicit approval.
3. Do you want to keep the 20-clip CC0 noise corpus as the acceptance set, or provide your own
   representative real-world room/vehicle/street recordings for an additional rejection pass?
