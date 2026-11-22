# Whisly Models, Voices, Engines and Licenses (`MODELS.md`)

Single source of truth for third-party notices. `licenses/NOTICE` is generated from the table in section 2 by `tools/generate_notice.ps1`. Do not edit `licenses/NOTICE` by hand.

Evidence date: 2026-11-22. Items marked `UNMEASURED` or `UNVERIFIED` have not been claimed as proven.

## 1. Current status and evidence boundaries

- The source tree contains **one bundled speech model and no bundled neural voice**. The stale `app/src/main/assets/whisly_manifest.json`, `ModelImportManager.kt`, `SttPackageManifest.kt`, `SpeechModelsSection.kt` and their tests remain deleted. Production STT uses `SherpaOnnxSttEngine`, which reads the bundled files directly from app assets and fails closed if they are missing. No ZIP-import or custom speech-pack download path remains.
- Step 2 STT baseline is the public, non-gated **sherpa-onnx Whisper Tiny multilingual INT8** ONNX release. It is not `tiny.en`. Its encoder, decoder and tokens are bundled under `app/src/main/assets/whisper/tiny/`; no user download or ZIP import is allowed.
- Measured local release artifacts on 2026-11-22, built from this Step 2 source tree: release APK `113,216,597` bytes, release AAB `87,599,467` bytes, and bundletool 1.18.3 `get-size total --apks` MIN/MAX `81,881,902` / `82,429,589` bytes. The ABI dimension reports `arm64-v8a,81881902,82429589`. Debug artifacts measure `137,277,559` bytes for the APK, `100,522,182` bytes for the AAB, and MIN/MAX `98,205,404` / `98,887,770` bytes. The three model assets compress from `103,609,903` bytes to `65,099,434` bytes inside the debug APK. These are local measurements, not Play Console reported sizes.
- Measured Whisper Tiny INT8 payload (from the pinned tarball extracted under `C:\Users\danse\AppData\Local\Temp\opencode\whisper`):
  - `tiny-encoder.int8.onnx` = `12,937,772` bytes, SHA-256 `D24FB083AE3B1041FC24E97971D60E280C9342201FBB67B0AB428A8B4A51A434`
  - `tiny-decoder.int8.onnx` = `89,855,401` bytes, SHA-256 `D2FECE8DD42771F1DF975C6C0445770D0C292BF7547C2CAE04A6C0CC57540925`
  - `tiny-tokens.txt` = `816,730` bytes, SHA-256 `B34B360DBB493E781E479794586D661700670D65564001F23024971D1F2FA126`
  - Combined INT8 files = `103,609,903` bytes. This is **not** the final APK/AAB download size.
- Pinned source archive: `https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2`, `116,204,861` bytes, SHA-256 `C46116994E539AA165266D96B325252728429C12535EB9D8B6A2B10F129E66B1`.
- Pinned runtime: `com.github.k2-fsa.sherpa-onnx:sherpa-onnx:1.13.5`. Its JitPack POM declares Apache-2.0 and SCM `https://github.com/k2-fsa/sherpa-onnx.git`. JitPack is retained only for this pinned artifact; this is a recorded supply-chain risk, not an endorsement.
- Official current stable `androidx.core:core-splashscreen` is `1.2.0` per `https://developer.android.com/jetpack/androidx/releases/core` (checked 2026-11-22). The local `androidx/core/splashscreen/SplashScreen.kt` shim was deleted.
- Official Google Play size limits per `https://support.google.com/googleplay/android-developer/answer/9859372?hl=en-GB` (checked 2026-11-22) are based on **compressed download size**: base module `500 MB`, each feature module `500 MB`, each asset pack `1.5 GB`, cumulative modules plus install-time asset packs `4 GB`, cumulative on-demand or fast-follow asset packs `30 GB`, total maximum `34 GB`. Publishing APKs instead of app bundles remains subject to a `100 MB` legacy APK limit. No language or model will be dropped until real bundled AAB/APK sizes are measured and the user is asked again with numbers.
- Default bundling plan remains arm64-only release split plus install-time Asset Delivery for the model payload. Install-time asset packs are immediately available at app launch per `https://developer.android.com/guide/playcore/asset-delivery` (checked 2026-11-22). This is a plan, not a measured Play Console result.
- Typed translation uses `com.google.mlkit:translate:17.0.3`. ML Kit states that input data and outputs are processed on-device and not sent to Google, but the APIs may contact Google servers for bug fixes, updated models, hardware accelerator compatibility information, and performance/utilization metrics (`https://developers.google.com/ml-kit/terms`, checked 2026-11-22). Therefore Whisly does **not** claim "all offline from install" until a clean-install airplane-mode proof is recorded.
- Whisper Tiny's published token list was checked against OpenAI Whisper tag `v20240930` (`https://raw.githubusercontent.com/openai/whisper/v20240930/whisper/tokenizer.py`). Of the target registry, these codes have Whisper language tokens and are enabled for bundled STT: `as,bn,en,gu,hi,kn,ml,mr,ne,pa,sa,sd,ta,te,ur`; `en-IN` maps to `en`. The registry gaps `brx,doi,ks,kok,mai,mni,or,sat` have no Whisper token and fail closed for speech. Typing remains available; typed translation still follows ML Kit's own supported list.
- Current typed ML Kit translation map supports `en,ta,hi,bn,mr,te,kn,gu,ur`. The wider target registry (`as,bn,brx,doi,gu,hi,kn,ks,kok,mai,ml,mni,mr,ne,or,pa,sa,sat,sd,ta,te,ur` plus `en-IN`) does not imply translation availability; unsupported languages fail closed while typing still works.
- Android system `TextToSpeech` is active only through `SystemTextToSpeechEngine.kt` as a temporary, clearly labeled device voice for the first English proof and typed-output playback. It is device-provided, contains no Whisly model weights, and is not bundled. Individual system voices, their offline availability, and their licenses are `UNMEASURED` / `UNVERIFIED`.
- `com.example.ai.lid.LanguageIdEngine` remains only as a labeled fallback. Step 6 will consider ML Kit Language Identification and Whisper language detection.
- `com.example.data.remote.WhislyBackendApi` is declaration-only and unused. No client, call site, account, billing, backend, upload path, secret, gated term, or online mode is added without explicit approval.
- No GPL engine, LGPL engine, non-commercial weight, or share-alike weight is approved for APK distribution. If any is ever proposed, stop and ask the user first.

## 2. Canonical inventory — generator input

<!-- NOTICE-GENERATOR:START -->
| ID | Kind | Name | Version / revision | Source URL | License / terms | Status | Notes |
|---|---|---|---|---|---|---|---|
| `stt-whisper-tiny-int8` | model | OpenAI Whisper Tiny multilingual INT8 ONNX encoder, decoder and tokens | pinned tarball SHA-256 `C46116994E539AA165266D96B325252728429C12535EB9D8B6A2B10F129E66B1` | https://github.com/k2-fsa/sherpa-onnx/releases/download/asr-models/sherpa-onnx-whisper-tiny.tar.bz2 | MIT | ACTIVE_BUNDLED_ASSET | Public non-gated release. Not `tiny.en`. Bundled at app/src/main/assets/whisper/tiny and verified by unit test against the three pinned SHA-256 values. Combined INT8 files measure `103,609,903` bytes. Local release bundletool total measures `81,881,902` to `82,429,589` bytes. Play Console size still UNMEASURED. |
| `runtime-sherpa-onnx` | engine | sherpa-onnx Android AAR runtime | `1.13.5` | https://jitpack.io/com/github/k2-fsa/sherpa-onnx/sherpa-onnx/1.13.5/sherpa-onnx-1.13.5.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Fetched JitPack POM declares Apache-2.0. Source is https://github.com/k2-fsa/sherpa-onnx.git. JitPack is a supply-chain risk and is pinned exactly. |
| `runtime-onnxruntime` | engine | ONNX Runtime native library packaged with the AAR | exact version UNVERIFIED in this pass | https://github.com/microsoft/onnxruntime/blob/main/LICENSE | MIT | TRANSITIVE_NATIVE | `libonnxruntime.so` appears in Android build output. License verified from official repository. Exact embedded version not claimed. |
| `mt-mlkit-translate` | model | Google ML Kit on-device text translation | `17.0.3` | https://dl.google.com/dl/android/maven2/com/google/mlkit/translate/17.0.3/translate-17.0.3.pom | ML Kit Terms of Service | ACTIVE_TYPED_TRANSLATION | On-device inference per Google terms. May contact Google for bug fixes, model updates, accelerator compatibility and metrics. Not Apache-2.0. |
| `tts-android-system` | engine | Android platform TextToSpeech voices via `SystemTextToSpeechEngine.kt` | device-provided | https://developer.android.com/reference/android/speech/tts/TextToSpeech | Device voice licenses UNVERIFIED. AOSP platform code Apache-2.0 | TEMPORARY_DEVICE_ONLY | Active temporary device voice. Not bundled; no Whisly weights. Per-device voice availability and voice licenses UNMEASURED. |
| `lid-unicode-ngram` | engine | Whisly Unicode script and character n-gram fallback LID | project source | https://github.com/k2-fsa/sherpa-onnx | Whisly project code, no third-party model license | FALLBACK_ONLY | Labeled fallback only. No `fastText` dependency. |
| `androidx-core-ktx` | library | AndroidX Core KTX | `1.18.0` | https://dl.google.com/dl/android/maven2/androidx/core/core-ktx/1.18.0/core-ktx-1.18.0.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Declared through version catalog. |
| `androidx-core-splashscreen` | library | AndroidX Core SplashScreen compat | `1.2.0` | https://dl.google.com/dl/android/maven2/androidx/core/core-splashscreen/1.2.0/core-splashscreen-1.2.0.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Official dependency replaces deleted local shim. |
| `androidx-datastore-preferences` | library | AndroidX DataStore Preferences | `1.1.7` | https://dl.google.com/dl/android/maven2/androidx/datastore/datastore-preferences/1.1.7/datastore-preferences-1.1.7.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Declared through version catalog. |
| `androidx-activity-compose` | library | AndroidX Activity Compose | `1.10.1` | https://dl.google.com/dl/android/maven2/androidx/activity/activity-compose/1.10.1/activity-compose-1.10.1.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Declared through version catalog. |
| `androidx-compose` | library | Jetpack Compose BOM, UI, UI graphics, Material3, icons, tooling and test artifacts | BOM `2024.09.00` | https://developer.android.com/jetpack/compose/bom/bom-mapping | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Includes `ui`, `ui-graphics`, `ui-tooling-preview`, `material3`, `material-icons-core`, `material-icons-extended`, plus test and debug tooling artifacts. |
| `androidx-lifecycle` | library | AndroidX Lifecycle runtime KTX, runtime Compose and ViewModel Compose | `2.8.7` | https://dl.google.com/dl/android/maven2/androidx/lifecycle/lifecycle-runtime-ktx/2.8.7/lifecycle-runtime-ktx-2.8.7.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Three artifacts declared through version catalog. |
| `androidx-navigation-compose` | library | AndroidX Navigation Compose | `2.8.9` | https://dl.google.com/dl/android/maven2/androidx/navigation/navigation-compose/2.8.9/navigation-compose-2.8.9.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Declared through version catalog. |
| `androidx-room` | library | AndroidX Room runtime, KTX and compiler | `2.7.0` | https://dl.google.com/dl/android/maven2/androidx/room/room-runtime/2.7.0/room-runtime-2.7.0.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Compiler is KSP-only. Migrations 1 to 2, 2 to 3 and 3 to 4 are retained. |
| `kotlin` | library | Kotlin standard library and Compose compiler plugin | `2.2.10` | https://github.com/JetBrains/kotlin/blob/master/license/LICENSE.txt | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Kotlin JVM toolchain 17. |
| `kotlinx-coroutines` | library | Kotlinx Coroutines Android, core and test | `1.10.2` | https://github.com/Kotlin/kotlinx.coroutines/blob/master/LICENSE | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Test artifact is test-only. |
| `okhttp` | library | Square OkHttp | `4.12.0` | https://repo1.maven.org/maven2/com/squareup/okhttp3/okhttp/4.12.0/okhttp-4.12.0.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | License block verified from Maven Central POM. |
| `retrofit` | library | Square Retrofit and Converter Moshi | `2.12.0` | https://repo1.maven.org/maven2/com/squareup/retrofit2/retrofit/2.12.0/retrofit-2.12.0.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | Declaration-only backend API does not create a call site. |
| `moshi` | library | Square Moshi Kotlin and codegen | `1.15.2` | https://repo1.maven.org/maven2/com/squareup/moshi/moshi-kotlin/1.15.2/moshi-kotlin-1.15.2.pom | Apache-2.0 | ACTIVE_BUNDLED_DEPENDENCY | License block verified from Maven Central POM. |
| `junit4` | library | JUnit 4 | `4.13.2` | https://github.com/junit-team/junit4/blob/main/LICENSE-junit.txt | EPL-1.0 | TEST_ONLY | License verified from official repository. |
| `androidx-test` | library | AndroidX Test core, JUnit ext, runner and Espresso core | core `1.6.1`, JUnit `1.3.0`, runner `1.6.2`, Espresso `3.7.0` | https://developer.android.com/jetpack/androidx/releases/test | Apache-2.0 | TEST_ONLY | Unit and instrumentation test dependencies. |
| `robolectric` | library | Robolectric | `4.16.1` | https://github.com/robolectric/robolectric/blob/master/LICENSE | MIT with some Apache-2.0 files | TEST_ONLY | License verified from official repository. |
| `org-json` | library | JSON-java | `20240303` | https://github.com/stleary/JSON-java/blob/master/LICENSE | Public Domain | TEST_ONLY | License verified from official repository. |
| `compose-ui-test` | library | Compose UI test JUnit4 and test manifest | BOM `2024.09.00` | https://developer.android.com/jetpack/compose/bom/bom-mapping | Apache-2.0 | TEST_ONLY | `ui-test-manifest` is debug-only. |
| `agp` | plugin | Android Gradle Plugin | `9.1.1` | https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/9.1.1/gradle-9.1.1.pom | Apache-2.0 | BUILD_ONLY | License block verified from Google Maven POM. |
| `ksp-plugin` | plugin | Google KSP Gradle Plugin | `2.3.5` | https://repo1.maven.org/maven2/com/google/devtools/ksp/com.google.devtools.ksp.gradle.plugin/2.3.5/com.google.devtools.ksp.gradle.plugin-2.3.5.pom | Apache-2.0 | BUILD_ONLY | License block verified from Maven Central POM. |
| `secrets-plugin` | plugin | Google Maps Platform Secrets Gradle Plugin | `2.0.1` | https://dl.google.com/dl/android/maven2/com/google/android/libraries/mapsplatform/secrets-gradle-plugin/secrets-gradle-plugin/2.0.1/secrets-gradle-plugin-2.0.1.pom | Apache-2.0 | BUILD_ONLY | License block verified from Google Maven POM. Reads `.env` or `.env.example`; no secrets are stored in source. |
| `google-services-plugin` | plugin | Google Services Gradle Plugin | `4.5.0` | https://dl.google.com/dl/android/maven2/com/google/gms/google-services/4.5.0/google-services-4.5.0.pom | Apache-2.0 | BUILD_ONLY | License block verified from Google Maven POM. Missing `google-services.json` is configured as WARN. |
| `font-inter` | asset | Inter TTF files | bundled files in `app/src/main/res/font` | https://github.com/rsms/inter/blob/master/LICENSE.txt | SIL OFL-1.1 | ACTIVE_BUNDLED_ASSET | License verified from official repository. |
| `font-plus-jakarta-sans` | asset | Plus Jakarta Sans TTF files | bundled files in `app/src/main/res/font` | https://github.com/tokotype/PlusJakartaSans/blob/master/OFL.txt | SIL OFL-1.1 | ACTIVE_BUNDLED_ASSET | License verified from official repository. |
| `simple-icons-github` | asset | GitHub vector mark | `app/src/main/res/drawable/ic_github.xml` | https://github.com/simple-icons/simple-icons/blob/develop/LICENSE.md | CC0-1.0 | ACTIVE_BUNDLED_ASSET | License verified from official repository. |
| `excluded-indicconformer` | model | AI4Bharat IndicConformer multilingual STT | not pinned | https://huggingface.co/ai4bharat/indic-conformer-600m-multilingual | MIT but gated | EXCLUDED_GATED | Do not use. Gated HuggingFace terms are out of scope without explicit approval. |
| `excluded-indictrans2` | model | AI4Bharat IndicTrans2 distilled MT | not pinned | https://github.com/AI4Bharat/IndicTrans2 | MIT | EXCLUDED_NOT_BUNDLED | Historical candidate only. Current translation is ML Kit. |
| `excluded-vits-rasa` | model | AI4Bharat VITS-Rasa neural TTS | not pinned | https://github.com/AI4Bharat/Indic-TTS | CONFLICTING_EVIDENCE | EXCLUDED_NOT_BUNDLED | Old docs disagree between MIT and CC-BY-4.0. Not used until verified and explicitly approved. |
| `excluded-kokoro` | model | Kokoro-82M TTS | not pinned | https://huggingface.co/hexgrad/Kokoro-82M | Apache-2.0 claim UNVERIFIED in this pass | EXCLUDED_NOT_BUNDLED | Historical candidate only. Not used. |
| `excluded-m2m100` | model | Meta M2M100 418M MT | not pinned | https://huggingface.co/facebook/m2m100_418M | MIT claim UNVERIFIED in this pass | EXCLUDED_NOT_BUNDLED | Historical fallback only. Not used. |
| `excluded-whisper-tiny-en` | model | OpenAI Whisper Tiny English-only | not pinned | https://huggingface.co/openai/whisper-tiny.en | MIT | EXCLUDED_WRONG_MODEL | Explicitly rejected. Requirement is multilingual. |
| `excluded-fasttext-lid` | model | fastText `lid.176.ftz` | not pinned | https://fasttext.cc/docs/en/language-identification.html | CC-BY-SA-3.0 | EXCLUDED_LICENSE_GATE | Share-alike terms rejected. Replaced by project Unicode and n-gram fallback. |
| `excluded-piper-espeak` | engine | piper1-gpl and espeak-ng TTS | not pinned | https://github.com/OHF-Voice/piper1-gpl | GPL-3.0 | EXCLUDED_LICENSE_GATE | Copyleft engine rejected. No GPL engine may be bundled without explicit approval. |
| `excluded-glotlid` | model | GlotLID v3 | not pinned | https://huggingface.co/cis-lmu/glotlid | Apache-2.0 claim UNVERIFIED in this pass | EXCLUDED_NOT_USED | Historical LID candidate only. Not used. |
| `excluded-silero-vad` | model | Silero VAD v5 | not pinned | https://github.com/snakers4/silero-vad | MIT claim UNVERIFIED in this pass | EXCLUDED_NOT_USED | Historical VAD candidate only. Not used. |
| `excluded-madlad` | model | MADLAD-400 3B/7B/10B MT | not pinned | https://huggingface.co/google/madlad400-3b-mt | Apache-2.0 claim UNVERIFIED in this pass | EXCLUDED_SIZE | Historical candidate only. Excluded from mobile default. |
<!-- NOTICE-GENERATOR:END -->

## 3. License gate

Permitted without additional approval: MIT, Apache-2.0, BSD-3-Clause, CC0-1.0, SIL OFL-1.1, Public Domain, and the ML Kit Terms of Service as declared by Google for the pinned ML Kit artifact.

Blocked from APK distribution without explicit user approval: GPL-2.0, GPL-3.0, LGPL, CC-BY-NC variants, CC-BY-SA variants, gated or terms-acceptance weights, and any model whose license is `CONFLICTING_EVIDENCE` or `UNVERIFIED`.

`EPL-1.0` appears only in test-only JUnit 4 and is not bundled into the shipped app artifact.

## 4. Network and offline claims

- No cloud STT, cloud TTS, backend upload, account, billing, online mode, secret, or external hosting is present.
- Bundled STT model files are read from app assets through the pinned sherpa-onnx AssetManager API. No model download, ZIP import, or "install Google offline speech pack" path is allowed. Install-time asset packs remain a future packaging option, not current source state.
- ML Kit translation processes text on-device but may contact Google as described in section 1. Any "all offline from install" claim requires clean-install airplane-mode evidence.
- Device TTS voice availability is UNMEASURED. A missing Tamil voice is a recorded result, not a failure of the STT proof.

## 5. Measurement register

| Item | Status | Evidence needed |
|---|---|---|
| Whisper Tiny INT8 tarball and per-file sizes/hashes | MEASURED | Values in section 1, recomputed from local pinned archive on 2026-11-22. |
| Bundled Whisper Tiny source-tree files | MEASURED | Unit test `bundled Whisper Tiny assets match pinned sizes and sha256` recomputes all three files on every `testDebugUnitTest`. |
| Whisper target-language token coverage | MEASURED | OpenAI Whisper tag `v20240930` tokenizer `LANGUAGES`, checked 2026-11-22; supported and unsupported codes listed in section 1. |
| Final bundled APK/AAB compressed download size | MEASURED_LOCAL | Release APK `113,216,597` bytes; release AAB `87,599,467` bytes; bundletool 1.18.3 `get-size total` MIN/MAX `81,881,902` / `82,429,589` bytes, ABI `arm64-v8a,81881902,82429589`. Saved logs: `whisly_bundletool_get_size_release_step2.txt` and `whisly_bundletool_get_size_release_abi_step2.txt`. |
| Play Console reported download size | UNMEASURED | Upload to Play Console only with explicit approval; paste App size page output. |
| English STT WER on public data | UNMEASURED | Run FLEURS or Common Voice English subset with real transcripts; paste command and metrics. |
| Tamil STT WER/CER on public data | UNMEASURED | Run FLEURS or Common Voice Tamil subset with real transcripts; paste command and metrics. |
| Translation chrF++ | UNMEASURED | Native-speaker `references.csv` required; never invent references. |
| Clean-install airplane-mode behavior | UNMEASURED | Factory-reset or clean install, airplane mode, run speak/transcribe/translate/speak path; paste logcat and Exit Report. |
| Device model, Android version, ABI, RAM | UNMEASURED | User-run `adb shell getprop` output. |
| RAM, latency, cold start | UNMEASURED | On-device measurement with Dates and times / logcat timestamps. |
| System TTS voice licenses and offline availability | UNMEASURED | Per-device voice list and vendor license evidence. |
