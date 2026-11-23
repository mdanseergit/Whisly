# Build, size, signing and test-count evidence (v1.0.7)

All figures below were produced locally on 2026-11-23. Raw tool output is in
`deliverables/final_v1.0.7/apkanalyzer/`.

## APK / AAB identity

| artifact | bytes | SHA-256 |
|---|---:|---|
| `app-release.apk` | 113,232,981 | `F775B7CF7F0C96C9C3D1B7E59A6A8503E87DE4A8EF4915262534BD8FDD2944DB` |
| `app-release.aab` | 87,633,723 | `C45CB86F9B1A2F5DF9D250F7BF37BA9EB77BFC0564CAFF8141479369CC25C074` |
| `mapping.txt` | 56,347,176 | `5932A0801C27347DE8BF5E969DA2DC234F3BD9568922A76B44E30BD32C388D9A` |

`apkanalyzer manifest debuggable app-release.apk` → `false`. App id `com.aistudio.whisly.kxmpzq`, versionCode 7, versionName 1.0.7, minSdk 24, targetSdk 36.

## APK component sizes (apkanalyzer, download-size column, bytes)

```
DEX code                       2,150,447
META-INF                           9,643
Native libs                   18,406,981
Other assets                      10,358
Other                             58,157
Resources                      2,209,995
STT models (assets/whisper)   60,134,596
```

Model files (download size):
```
370,300   /assets/whisper/tiny/tiny-tokens.txt
8,269,184 /assets/whisper/tiny/tiny-encoder.int8.onnx
51,495,112 /assets/whisper/tiny/tiny-decoder.int8.onnx
```

Raw uncompressed component totals (bytes): DEX 2,155,804; META-INF 8,113; native libs 47,714,920; other assets 10,312; other 57,785; resources 2,462,269; STT models 60,744,877.

## AAB component sizes (zip entry accounting, bytes)

```
AAB_BYTES 87633723
ENTRIES 181
GROUP                          FILES   RAW          COMPRESSED
DEX code                       1       4,473,716    2,155,804
Manifest                       1          11,373        3,001
Native libs                    7      47,714,920   18,449,661
Other / bundle metadata      121      56,865,466    4,105,144
Resources                     48       2,380,139    2,143,418
STT models (base/assets/whisper) 3   103,609,903   60,744,877
```

Largest AAB entries:
```
89,855,401 / 52,068,919  base/assets/whisper/tiny/tiny-decoder.int8.onnx
56,347,176 /  3,909,233  BUNDLE-METADATA/com.android.tools.build.obfuscation/proguard.map
21,684,880 /  7,679,735  base/lib/arm64-v8a/libonnxruntime.so
16,361,048 /  6,779,503  base/lib/arm64-v8a/libtranslate_jni.so
12,937,772 /  8,304,269  base/assets/whisper/tiny/tiny-encoder.int8.onnx
```

`bundletool` earlier measured `MIN,MAX = 81881902,82429589` download bytes for the AAB. Play Console compressed download size remains `UNMEASURED` (no account access). Official limits (support.google.com/googleplay/android-developer/answer/9859372): base module 500 MB, asset pack 1.5 GB, install-time cumulative 4 GB, on-demand/fast-follow 30 GB, total 34 GB.

## Signing statement

`my-upload-key.jks` is **MISSING**. The release `signingConfigs.release` therefore falls back to `debug.keystore` (2,666 bytes), alias `androiddebugkey`, owner `CN=Android Debug, O=Android, C=US`, SHA-256 `DE:BB:01:43:9B:4C:DD:03:FE:E1:68:8E:FA:1F:9E:6D:36:99:D4:46:36:B1:5F:D9:62:21:FB:BB:35:A6:93:D7`. The APK/AAB are consequently **debug-key signed**; `debuggable=false` is a separate manifest flag and does not imply a production signing key. No Play upload key exists in this workspace.

## Test-count explanation ("31 to 27")

`27` was never the whole suite — it was only `ExampleUnitTest`. Current per-class JUnit XML totals (debug unit tests, 2026-11-23):

| class | tests |
|---|---:|
| ExampleUnitTest | 28 |
| ExampleRobolectricTest | 5 |
| LaunchAndRecoveryPreventionTest | 5 |
| NavigationTapTest | 1 |
| OfflineZeroNetworkGuardTest | 5 |
| TranslationRegressionLockTest | 2 |
| **total** | **46** |

Before this work, `ExampleUnitTest` had 27 and the suite had 45. The baseline commit `033b53f` had 40 tests total: `ExampleUnitTest 14`, `ExampleRobolectricTest 5`, `LaunchAndRecoveryPreventionTest 5`, `NavigationTapTest 1`, `OfflineZeroNetworkGuardTest 5`, `SpeechModelImportTest 8`, `TranslationRegressionLockTest 2`.

Removed tests and why:
- `voiced segment trims leading and trailing silence before decoding` — replaced by the committed fix (a) production VAD path and its 130/130 + 20/20 measurements; the old unit assertion tested the superseded gate.
- `stt engine rejects silent audio, rejects low confidence below 0_55, and routes auto LID` — asserted a fabricated `0.55` confidence and auto-LID routing; both were removed under fail-closed rules (no real per-token confidence, speech auto-detect `UNAVAILABLE`).
- `speech constancy test proves silence and noise return no text and never return Hello or vannakkam` — superseded by the real 20-clip CC0 corpus measured 20/20 rejected and the 40-case adversarial set.
- All 8 `SpeechModelImportTest` tests (valid manifest accepted; LICENSE required; path traversal rejected; malformed checksum; unknown engine; missing license; tiny manifest parses; duplicate entries) — removed when ZIP/custom model import was rejected by decision; no import surface remains to test.

`ExampleUnitTest` grew 14 → 27 → 28 with the fail-closed firewall, orchestrator, registry, TTS gate and typed-translation guard tests.

## Typed-translation regression lock run (raw XML)

```
<testsuite name="com.example.TranslationRegressionLockTest" tests="2" skipped="0" failures="0" errors="0" timestamp="2026-11-23T07:31:43.663Z">
  <testcase name="q1 reference sentence typo correction runs, engine output passes through unchanged, no canned rewrites" .../>
  <testcase name="regression lock 20 sentences assert non-echo, target script, zero transliteration, and chrF floor" .../>
</testsuite>
```
