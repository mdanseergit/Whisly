# FIX A — measured speech-vs-nonspeech VAD repair

Status: **COMPLETE for fix (a)**. This document covers only the VAD repair required by
`DIAGNOSIS9.md`. It does **not** claim that STT quality, translation, TTS, or on-device behavior is
fixed. Overall voice reset remains in progress.

Baseline: branch `voice-reset`, commit `e7af38d` (`DIAGNOSIS9` measurement and report).
Measured here: production Kotlin VAD plus deterministic JVM parity against the pinned WAV fixtures.

## 1. Defect addressed

`DIAGNOSIS9` measured two distinct VAD failures:

1. **False reject of quiet speech.** The original app path produced `[VAD_NO_SEGMENT]` for
   `5/10` English FLEURS clips even when STT on the full clip produced usable English text.
2. **False accept of non-speech.** Old VAD accepted `20/20` real CC0 music/environment clips.
   The first measured repair (VAD v1) still accepted `17/20`.

The repair must accept quiet speech while rejecting music/noise, without merely lowering a
threshold or inventing STT confidence. It does not create a confidence score and it does not decide
a spoken language.

## 2. What changed in the app

| File | Change |
|---|---|
| `app/src/main/java/com/example/ai/audio/AudioProcessor.kt` | Peak-normalizes a copy of the captured PCM before VAD analysis; keeps the original PCM16 segment for STT. Adds NumPy-matching 150–5000 Hz bin masks, per-frame power spectra, global speech authentication gating, and reject diagnostics. Any classifier rejection forces `vadSpeechDetected=false` and `vadSpeechProbability=0f`. |
| `app/src/main/java/com/example/ai/audio/SpeechAuthenticityClassifier.kt` | New measured classifier. Uses spectral flatness, three formant-band occupancy, high-band ratio, centroid, harmonicity, pitch-change rate, syllabic envelope, flux bursts, and active-frame count. All constants come from the measured distributions in `deliverables/fix_a_vad/vad_feature_separation.txt` and `vad_pitch_features.txt`. |
| `app/src/main/java/com/example/ai/pipeline/AiEngineInterfaces.kt` | Adds debug-only `AudioDiagnostics` fields for global reject reason, active frames, band-2/3 fractions, flatness, harmonicity, and syllable modulation. |
| `app/src/test/java/com/example/ExampleUnitTest.kt` | Replaces the old static-F0 fixture with a deterministic alternating-F0/noise-burst fixture and adds a real-WAV parity harness for 130 FLEURS speech clips and 20 CC0 non-speech clips. |

The VAD `vadSpeechProbability` field is only a legacy VAD/SNR diagnostic. It must **not** be used as
STT confidence. The current sherpa-onnx result still exposes no probability; the fail-closed
language registry/firewall step will mark every current-model STT result `LOW CONFIDENCE`.

## 3. Evidence bundle

All files below are under `whisly/deliverables/fix_a_vad/`:

| File | Contents |
|---|---|
| `prototype_vad_v2.py` | Final Python prototype used for the measured thresholds. |
| `prototype_vad_v2_final.txt` | Verbatim final prototype output: quiet speech, all 130 FLEURS clips, 40 synthetic adversarial cases, 20 real CC0 non-speech clips. |
| `vad_features.json`, `vad_pitch_features.txt`, `vad_feature_separation.txt` | Raw measured feature distributions and separation summary. |
| `real_nonspeech_before_after.txt` | Old VAD vs VAD v1 on the 20 real CC0 clips. |
| `real_nonspeech_manifest.json`, `real_nonspeech_sources.md`, `real_nonspeech_paths.tsv` | Source URLs, authors, CC0 evidence, SHA-256, and local paths. |
| `real_speech_paths.txt` | Pinned FLEURS WAV paths used by the JVM parity harness. |
| `production_build.log` | Exact Gradle command output for the final clean build and tests. |
| `production_test_results.xml`, `production_vad_parity.txt` | Exact JUnit XML summary and production parity lines. |

### 3.1 Python VAD v2 prototype — exact output

Source: `deliverables/fix_a_vad/prototype_vad_v2_final.txt`.

```text
=== 6 previously rejected quiet speech clips ===
en_us-04     passed=True reason=
en_us-05     passed=True reason=
en_us-06     passed=True reason=
en_us-08     passed=True reason=
en_us-10     passed=True reason=
hi_in-04     passed=True reason=
=== all 130 pinned FLEURS clips ===
passed=130/130
=== synthetic adversarial ===
silence_01.wav       passed=False reason=SPECTRAL_GLOBAL
silence_02.wav       passed=False reason=SPECTRAL_GLOBAL
silence_03.wav       passed=False reason=SPECTRAL_GLOBAL
silence_04.wav       passed=False reason=SPECTRAL_GLOBAL
silence_05.wav       passed=False reason=SPECTRAL_GLOBAL
silence_06.wav       passed=False reason=SPECTRAL_GLOBAL
silence_07.wav       passed=False reason=SPECTRAL_GLOBAL
silence_08.wav       passed=False reason=SPECTRAL_GLOBAL
silence_09.wav       passed=False reason=SPECTRAL_GLOBAL
silence_10.wav       passed=False reason=SPECTRAL_GLOBAL
tonal_proxy_01.wav   passed=False reason=TEMPORAL
tonal_proxy_02.wav   passed=False reason=TEMPORAL
tonal_proxy_03.wav   passed=False reason=TEMPORAL
tonal_proxy_04.wav   passed=False reason=TEMPORAL
tonal_proxy_05.wav   passed=False reason=TEMPORAL
tonal_proxy_06.wav   passed=False reason=TEMPORAL
tonal_proxy_07.wav   passed=False reason=TEMPORAL
tonal_proxy_08.wav   passed=False reason=TEMPORAL
tonal_proxy_09.wav   passed=False reason=TEMPORAL
tonal_proxy_10.wav   passed=False reason=TEMPORAL
white_noise_01.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_02.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_03.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_04.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_05.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_06.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_07.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_08.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_09.wav   passed=False reason=SPECTRAL_GLOBAL
white_noise_10.wav   passed=False reason=SPECTRAL_GLOBAL
=== 20 real CC0 non-speech ===
music music_508322_acoustic_guitar_8_wav_16k_mono.wav                        passed=False reason=SHORT_NON_SPEECH
music music_97776_acoustic_guitar_with_non_standard_tuning_phrase__16k_mono.wav passed=False reason=TEMPORAL
music music_211963_acoustic_guitar_rythm_with_maracas_16k_mono.wav           passed=False reason=MUSIC_BAND2
music music_690185_acoustic_guitar_chords_cgamem_mp3_16k_mono.wav            passed=False reason=TEMPORAL
music music_19460_tg_xylophone_4_wav_16k_mono.wav                            passed=False reason=TEMPORAL
music music_501300_toy_xylophone_16k_mono.wav                                passed=False reason=SPECTRAL_GLOBAL
music music_701063_harmonica_east_top_easy_tune_16k_mono.wav                 passed=False reason=SPECTRAL_GLOBAL
music music_792630_harmonica_c_random_stuff_multi_take_16k_mono.wav          passed=False reason=SPECTRAL_GLOBAL
music music_711243_simple_bit_of_beethoven_on_the_piano_16k_mono.wav         passed=False reason=TEMPORAL
music music_735624_sierra_serenade_16k_mono.wav                              passed=False reason=TEMPORAL
noise noise_236292_rain_on_window_wav_16k_mono.wav                           passed=False reason=SPECTRAL_GLOBAL
noise noise_276294_leaves_in_wind_wav_16k_mono.wav                           passed=False reason=SPECTRAL_GLOBAL
noise noise_509035_birds_singing_mp3_16k_mono.wav                            passed=False reason=NOISE_FLAT_BAND3
noise noise_534909_ocean_waves_04_wav_16k_mono.wav                           passed=False reason=SPECTRAL_GLOBAL
noise noise_409403_storm_from_indoors_wav_16k_mono.wav                       passed=False reason=TEMPORAL
noise noise_510233_river_stream_stereo_from_lake_of_glacier_closewa_16k_mono.wav passed=False reason=SPECTRAL_GLOBAL
noise noise_637559_forest_ambience_constant_breeze_flac_16k_mono.wav         passed=False reason=SPECTRAL_GLOBAL
noise noise_482264_zikaden_grillen_summer_atmo_wav_16k_mono.wav              passed=False reason=SPECTRAL_GLOBAL
noise noise_532696_city_street_ambience_with_construction_wav_16k_mono.wav   passed=False reason=NOISE_FLAT_ZCR
noise noise_726522_rushing_stream_river_water_16k_mono.wav                   passed=False reason=SPECTRAL_GLOBAL
```

### 3.2 Real non-speech: old VAD vs VAD v1

Source: `deliverables/fix_a_vad/real_nonspeech_before_after.txt`, final line verbatim:

```text
TOTAL old_false_accepts=20/20 new_false_accepts=17/20
```

VAD v2 then reduced this to `0/20` in the prototype output above and in the production parity test
below.

### 3.3 Production parity and required clean build

Command run from `C:\Users\danse\OneDrive\document\Whisly\whisly`:

```powershell
$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"
$env:PYTHONIOENCODING="utf-8"
.\gradlew.bat clean assembleDebug testDebugUnitTest
```

Final lines of exact output (`deliverables/fix_a_vad/production_build.log`):

```text
> Task :app:packageDebug
> Task :app:assembleDebug
> Task :app:createDebugApkListingFileRedirect
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 3m 19s
51 actionable tasks: 27 executed, 23 from cache, 1 up-to-date
Configuration cache entry reused.
```

JUnit summary and production VAD output (`deliverables/fix_a_vad/production_vad_parity.txt`):

```text
<testsuite name="com.example.ExampleUnitTest" tests="19" skipped="0" failures="0" errors="0" timestamp="2026-11-23T04:50:44.278Z" hostname="MOHAMMEDDANSEER" time="13.053">
<system-out><![CDATA[PRODUCTION_VAD_REAL_SPEECH accepted=130/130
PRODUCTION_VAD_REAL_NONSPEECH rejected=20/20
```

The JVM parity harness converts both 16-bit PCM WAV and 32-bit float WAV fixtures (FLEURS clips are
32-bit float WAV; CC0 clips are PCM16 WAV) into the same 16 kHz PCM16 representation before calling
`AudioProcessor.preprocessAndRunVad`.

## 4. Source and license evidence

| Material | Pin / source | License | Use here |
|---|---|---|---|
| FLEURS dev clips | revision `70bb2e84b976b7e960aa89f1c648e09c59f894dd` | `cc-by-4.0` | 10 pinned speech clips × 13 languages; local evaluation only. |
| Freesound CC0 previews | 20 individual pages and direct previews listed in `real_nonspeech_sources.md` / `real_nonspeech_manifest.json` | `CC0-1.0` | 10 music + 10 environmental-noise clips; local evaluation only. |
| Synthetic silence/white-noise/tonal proxies | generated locally by the pinned prototype script | locally generated | adversarial checks; not speech evidence. |
| Whisper Tiny INT8 model | already bundled, unchanged by this fix | Apache-2.0 (recorded in `MODELS.md`) | STT model under evaluation; not changed here. |

Nothing was uploaded. No model, voice, or dataset was added to the app repo by this fix. The CC0
source file records direct URLs, authors, original byte sizes, and SHA-256 hashes. CC BY-NC and
unknown-license results were excluded.

## 5. Boundaries of this evidence

- **Measured:** JVM production-code parity on pinned WAV files and the exact Gradle build/test run.
- **UNMEASURED:** Android microphone input, device DSP behavior (`NoiseSuppressor` /
  `AutomaticGainControl`), live latency, RAM, cold start, and real-room false accepts/rejects.
- This is a `20`-clip real non-speech corpus, not a universal guarantee for every music/noise source.
- This fix does **not** improve Whisper Indic recognition. It only reduces the chance that music,
  noise, silence, or tonal proxies are sent to STT as speech.
- This fix does **not** establish STT confidence. All current-model STT results remain
  `LOW CONFIDENCE` under the fail-closed plan.
- Santali remains by your earlier decision `UNAVAILABLE` for speech and `UNMEASURED` for speech
  quality; no further access attempts were made.

## 6. Language coverage after fix (a)

VAD parity is measured from the pinned FLEURS clips where available. STT/TTS readiness is unchanged
from `DIAGNOSIS9`; no language was added or removed.

| Language | Pinned VAD speech clips | Production VAD result | STT after fix (a) | Typed ML Kit translation | Speech output |
|---|---:|---|---|---|---|
| English (`en`) | 10 | 10/10 accepted | CER ~0.08–0.10 measured before fix; re-measure with fixed VAD still pending | supported | temporary system TTS only; voice data UNMEASURED |
| Hindi (`hi`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Bengali (`bn`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Tamil (`ta`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Telugu (`te`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Kannada (`kn`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Malayalam (`ml`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | not supported by ML Kit MT | UNAVAILABLE until voice bake-off |
| Marathi (`mr`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Gujarati (`gu`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Punjabi (`pa`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | not supported by ML Kit MT | UNAVAILABLE until voice bake-off |
| Urdu (`ur`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | supported | UNAVAILABLE until voice bake-off |
| Odia (`or`) | 10 | 10/10 accepted | no Whisper tokenizer token; forced STT cannot express Odia | not supported by ML Kit MT/LID | UNAVAILABLE |
| Assamese (`as`) | 10 | 10/10 accepted | garbage/unusable rate measured in `DIAGNOSIS9`; model replacement required | not supported by ML Kit MT/LID | UNAVAILABLE |
| Santali (`sat`) | 0 | UNMEASURED | no licensed speech source used; deferred by your decision | not supported by ML Kit MT/LID | UNAVAILABLE |

Totals: VAD speech parity `130/130` accepted across 13 languages; non-speech parity `20/20`
rejected; Santali `UNMEASURED`. No language removed.

## 7. Questions only you can answer

1. Can you connect an Android device and run/paste the outputs from `docs/DEVICE_EVIDENCE_STEPS.md`?
   This is the only way to replace `UNMEASURED` live-microphone, ABI, Android-version, latency, RAM,
   and clean-install airplane-mode fields.
2. Do you want to keep the current 20-clip CC0 noise corpus as the acceptance set, or provide your
   own representative real-world room/vehicle/street recordings for an additional rejection pass?
   I will not change the acceptance set without your answer.

No further action is taken on fix (a) beyond committing this measured state.
