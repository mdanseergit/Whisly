# Expanded bake-off — candidate evidence, licenses and runtime limits

Date: 2026-11-23. Branch `voice-reset`. Evaluation only: every file listed here is in
`C:\dev\Whisly-eval\bakeoff2\models\` (outside the repository). **Nothing was bundled.**

## 1. Candidate families, license, source, pin, size

| Family | Candidate | License | Source / pin | Downloaded bytes (scratch) | Runtime |
|---|---|---|---|---:|---|
| sherpa Whisper INT8 | `tiny` (control) | MIT weights / Apache-2.0 export | `csukuangfj/sherpa-onnx-whisper-tiny`, bundled control | 103,609,903 | sherpa-onnx 1.13.5 |
| sherpa Whisper INT8 | `tiny.en` | same | `d026532c022fa99fd789d6b32446a1df7b6bfc43` | 103,627,191 | sherpa-onnx 1.13.5 |
| sherpa Whisper INT8 | `base` | same | `bb53ee204431c90d314c1cc08d28d23e5b7927cc` | 160,609,290 | sherpa-onnx 1.13.5 |
| sherpa Whisper INT8 | `base.en` | same | `59eea950fc76df2453efb57e6c0fd334548e8ffe` | 160,626,066 | sherpa-onnx 1.13.5 |
| sherpa Whisper INT8 | `small` | same | `8f3c18b358db4d1f2fc1eae49d75cd20989e4309` | 375,485,327 | sherpa-onnx 1.13.5 |
| sherpa Whisper INT8 | `small.en` | same | `d9533f69affd85061aee349af7fea5cb2996dbbe` | 375,501,079 | sherpa-onnx 1.13.5 |
| sherpa Whisper INT8 | `medium` | same | `8c31d28503847560985df21f90e14f0c736e075e` | 946,072,270 | sherpa-onnx 1.13.5 |
| sherpa Whisper INT8 | `medium.en` | same | `251ab4521f354490e8f2c206fbd0b7f3f6b0a7ec` | 946,086,998 | sherpa-onnx 1.13.5 |
| Native Vosk small | en-in 0.4 | Apache-2.0 | versioned archive, SHA-256 in `download_models_manifest.json` | 37,573,330 | native Vosk |
| Native Vosk small | hi 0.22 | Apache-2.0 | versioned archive, SHA-256 in manifest | 44,458,845 | native Vosk |
| Native Vosk small | gu 0.42 | Apache-2.0 | versioned archive, SHA-256 in manifest | 108,054,987 | native Vosk |
| Native Vosk small | te 0.42 | Apache-2.0 | versioned archive, SHA-256 in manifest | 60,544,249 | native Vosk |
| sherpa Zipformer transducer | Bengali, converted from Vosk | Apache-2.0 | `k2-fsa/sherpa-onnx` release `asr-models` asset, SHA-256 in manifest | 94,119,939 | sherpa-onnx 1.13.5 |

Full per-file SHA-256 values are in `C:\dev\Whisly-eval\bakeoff2\download_models_manifest.json`
(13 records). The harness copies them into its JSONL and summary output.

## 2. Runtime and confidence findings (source-verified)

- sherpa-onnx v1.13.5 source tree: **0** `indic` paths, **0** `vosk` paths. IndicConformer and Vosk
  are not first-class offline paths in this pinned Android runtime.
- Whisper result fields are `durations, emotion, event, lang, segment_durations, segment_texts,
  segment_timestamps, text, timestamps, tokens, words, ys_log_probs`. `ys_log_probs` was empty
  (`len=0`) for all Whisper decodes in the English bake-off and in this expanded run.
- Offline transducer C/C++ API exposes `ys_log_probs`; **online** transducer API (the one the Bengali
  streaming Zipformer needs) does **not**. Python exposes `ys_probs` for online transducer, but that
  is not available through the Android C API.
- Native Vosk exposes word-level `conf` in `Recognizer.result()`. This is a real per-word signal,
  but Vosk is not available in sherpa-onnx 1.13.5 and would require a different Android runtime.
- Whisper tokenizer contains a language token for `as` but **not** for `or` or `sat`. Forcing `or`
  calls `std::exit` in `offline-whisper-greedy-search-decoder.cc:58` ("Invalid language: or"). The
  harness therefore fails closed for `or`/`sat` without invoking the decoder.

## 3. Excluded / unavailable candidates

| Candidate | Reason |
|---|---|
| Meta MMS, Seamless | Excluded by user decision (unclear / non-commercial licence profile) |
| IndicConformer per-language / multilingual | MIT, but official HF repos are gated; no verified non-gated copy with matching hash; no INT8 export published; per-language `.nemo` is 523,192,320 B and the multilingual tree sums to 2,556,502,929 B; no `indic` path in sherpa-onnx 1.13.5 |
| IndicWhisper community fine-tunes | MIT, cover `bn gu hi kn ml mr or pa sa ta te ur`, but multi-GB zip archives, not official sherpa exports, no INT8 published; training mixes not fully verified |
| Vosk models for `as`, `or`, `ml`, `pa`, `sat` | No official Vosk model published; only en-in, hi, gu, te (plus bn via a sherpa conversion) available |
| `medium*` / `large*` Whisper | Measured here for completeness; no confidence signal, and size far beyond the current base-module budget |
| GPL / non-commercial engines or voices | Excluded by user decision |
| Kathbath upstream | Gated (`gated: auto`), only `valid` and `train` splits, no official `test`; 12 configs sum to 174,027,666,011 B; non-gated derivative byte-identity with upstream is `UNVERIFIED` |

## 4. Data sets

- 130 pinned FLEURS dev clips (13 languages × 10), revision
  `70bb2e84b976b7e960aa89f1c648e09c59f894dd`, `C:\dev\Whisly-eval\fleurs_manifest.json`. No clip
  was edited. No Santali clip exists in this pin.
- Kathbath Hindi derivative: `Trelis/vistaar-hi-kathbath-test`,
  `data/test-00000-of-00001.parquet`, 336,797,012 B, SHA-256
  `2546229b97146394f69d80bef8412f72da94131101b69423b5114b2be0fe1153`, 1,929 rows; 10 clips
  pinned to 16 kHz mono PCM16 WAV. Upstream `ai4bharat/Kathbath` is gated and hash equality is
  `UNVERIFIED`; no claim of upstream equivalence is made.
- 20 real CC0 non-speech clips (10 music + 10 environmental), 16 kHz mono,
  `C:\dev\Whisly-eval\real_nonspeech\clips\`.
- 40 pinned adversarial cases (10 digital silence, 10 white noise, 10 synthetic tonal music proxy,
  10 FLEURS Tamil clips forced to Hindi), `C:\dev\Whisly-eval\adversarial_results.jsonl`. The tonal
  cases are synthetic; real recorded music is covered by the 20-clip CC0 set.

## 5. Official Google/Android API evidence

- `SpeechRecognizer.isOnDeviceRecognitionAvailable` and `createOnDeviceSpeechRecognizer`: API 31+.
- `checkRecognitionSupport(Intent, Executor, RecognitionSupportCallback)` and
  `triggerModelDownload(Intent)`: API 33+. `RecognitionSupportCallback` method is
  `onSupportResult(RecognitionSupport)`.
- `triggerModelDownload(Intent, Executor, ModelDownloadListener)` and `ModelDownloadListener`: API 34+;
  callbacks `onProgress`, `onSuccess`, `onScheduled`, `onError`.
- All `SpeechRecognizer` calls and listener callbacks must run on the main thread; `destroy()` is
  mandatory. Official docs do **not** promise that no system UI appears during a model download,
  so that remains user-observable / `UNMEASURED`.
- Share sheet: `ACTION_SEND` + `Intent.createChooser()` + `FileProvider.getUriForFile()`, authority
  `<applicationId>.fileprovider`, `@xml/filepaths` with `<cache-path>`. No `QUERY_ALL_PACKAGES`.
- Play limits (support.google.com/googleplay/android-developer/answer/9859372): base module 500 MB
  compressed download, asset pack 1.5 GB, install-time cumulative 4 GB, on-demand/fast-follow 30 GB,
  total 34 GB. Play Console compressed size for this app remains `UNMEASURED`.
