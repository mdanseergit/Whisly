# Test report — Whisly 1.0.7 (versionCode 7)

Date: 2026-11-23. Environment: Windows, `JAVA_HOME=C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2`,
Gradle wrapper from `whisly`. Evidence logs in this folder.

## 1. Production build + local unit tests (required exact command)

Command: `$env:JAVA_HOME="C:\Users\danse\.gradle\jdks\eclipse_adoptium-17-amd64-windows.2"; .\gradlew.bat clean assembleDebug testDebugUnitTest`

Attempt 1 failed at `:app:clean` because the preceding lint run held locks on lint-cache JARs
(`Unable to delete directory ... lint-cache\lintAnalyzeDebug\migrated-jars`). `.\gradlew.bat --stop`
was run, then attempt 2 succeeded. Exact tail from `production_build.log`:

```
> Task :app:testDebugUnitTest

BUILD SUCCESSFUL in 2m 34s
51 actionable tasks: 35 executed, 16 from cache
Configuration cache entry reused.
```

JUnit XML (`TEST-com.example.ExampleUnitTest.xml`), verbatim header:

```
<testsuite name="com.example.ExampleUnitTest" tests="27" skipped="0" failures="0" errors="0" timestamp="2026-11-23T06:55:13.626Z" hostname="MOHAMMEDDANSEER" time="4.883">
```

Non-fatal compiler warnings (unchanged, no failures): three in `MainActivity.kt`/`CrashProtectionManager.kt`,
one nullable-`File?` warning in `ExampleUnitTest.kt:718`.

## 2. Lint

Command: `.\gradlew.bat lintDebug`. Initial run found **1 error, 106 warnings**:

```
ExitReportManager.kt:69: Error: Call requires API level 30 (current min is 24):
android.app.ApplicationExitInfo#getTraceInputStream [NewApi]
```

Fixed by importing `androidx.annotation.RequiresApi` and annotating `readTraceSummary` with
`@RequiresApi(Build.VERSION_CODES.R)`; the caller already guards with
`if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return`. No lint baseline and no minSdk change.
Exact tail from `lint_passed.log`:

```
> Task :app:lintDebug

BUILD SUCCESSFUL in 1m 30s
31 actionable tasks: 11 executed, 20 up-to-date
Configuration cache entry reused.
```

**0 lint errors; 106 pre-existing warnings remain.**

## 3. Instrumented tests — BLOCKED / UNMEASURED

Command: `.\gradlew.bat connectedDebugAndroidTest`. Exact failure from
`connected_android_test_blocked.log`:

```
> Task :app:connectedDebugAndroidTest FAILED
Execution failed for task ':app:connectedDebugAndroidTest'.
> com.android.builder.testing.api.DeviceException: No connected devices!
BUILD FAILED in 1m 11s
```

`adb devices -l` (`adb_devices.txt`) output, verbatim:

```
List of devices attached

```

`emulator -list-avds` returned no AVDs. Installed SDK platforms: `android-34`, `android-36`,
`android-36.1`, `android-37.0` — API 24 and 30 are not installed.

| API | Instrumented result |
|---|---|
| 24 | BLOCKED — no device/emulator, platform not installed |
| 30 | BLOCKED — no device/emulator, platform not installed |
| 34 | BLOCKED — no device, platform installed |
| 36 | BLOCKED — no device, platform installed |

The instrumented APK was built successfully (`:app:packageDebugAndroidTest`), so the tests
`ExampleInstrumentedTest.useAppContext` and `RealEngineAuditTest.auditRealEngineOutputs` are
compiled and ready to run when a device is attached. They remain `UNMEASURED`.

## 4. Release artifacts

Command: `.\gradlew.bat assembleRelease bundleRelease`. Exact tail from `release_build.log`:

```
> Task :app:bundleRelease
> Task :app:createReleaseBundleListingFileRedirect

BUILD SUCCESSFUL in 3m 13s
57 actionable tasks: 42 executed, 12 from cache, 3 up-to-date
Configuration cache entry stored.
```

A non-fatal KSP AWT thread `NullPointerException` was printed during `:app:processReleaseResources`
but all tasks completed and the build succeeded.

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app-release.aab` | 87,633,723 | `C45CB86F9B1A2F5DF9D250F7BF37BA9EB77BFC0564CAFF8141479369CC25C074` |
| `app-release.apk` | 113,232,981 | `F775B7CF7F0C96C9C3D1B7E59A6A8503E87DE4A8EF4915262534BD8FDD2944DB` |
| `mapping.txt` | 56,347,176 | `5932A0801C27347DE8BF5E969DA2DC234F3BD9568922A76B44E30BD32C388D9A` |

bundletool `MIN,MAX` for this version is `UNMEASURED` (bundletool is not present in this environment).
Recorded v1.0.6 baseline was `81881902,82429589`. Play Console compressed size `UNMEASURED`.

## 5. Desktop evidence (measured)

- VAD fix (a): real speech `130/130` accepted; real CC0 non-speech `20/20` rejected.
- English after VAD fix: `10/10` decoded, mean CER `0.1021`, garbage `1/10 = 10%` → BETA.
- Bake-off: tiny WER `0.2386`, base.en `0.1458`, small.en `0.1220`; `ys_log_probs` empty for all
  30 decodes. Nothing bundled, per user decision. See `EVAL_RESULTS.md`.

## 6. Still UNMEASURED

Phone WER/CER/garbage/latency/RAM/cold start/ABI/device model; per-device TTS voice dump and spoken
output; clean-install airplane-mode translation proof; Play Console compressed size; acoustic LID;
Santali speech (deferred); ≥100-clip English acceptance set.

## 7. Language coverage

| Code | STT tier / mic | Typed ML Kit | Runtime TTS gate | Instrumented |
|---|---|---|---|---|
| en | BETA / yes | yes | exact Locale + installed non-network voice | BLOCKED |
| hi | UNAVAILABLE | yes (`hi`) | same gate | BLOCKED |
| bn | UNAVAILABLE | yes (`bn`) | same gate | BLOCKED |
| ta | UNAVAILABLE | yes (`ta`) | same gate | BLOCKED |
| te | UNAVAILABLE | yes (`te`) | same gate | BLOCKED |
| kn | UNAVAILABLE | yes (`kn`) | same gate | BLOCKED |
| ml | UNAVAILABLE | no tag (typed fallback) | same gate | BLOCKED |
| mr | UNAVAILABLE | yes (`mr`) | same gate | BLOCKED |
| gu | UNAVAILABLE | yes (`gu`) | same gate | BLOCKED |
| pa | UNAVAILABLE | no tag (typed fallback) | same gate | BLOCKED |
| ur | UNAVAILABLE | yes (`ur`) | same gate | BLOCKED |
| or | UNAVAILABLE (no token) | no tag | same gate | BLOCKED |
| as | UNAVAILABLE | no tag | same gate | BLOCKED |
| sat | UNAVAILABLE / deferred | no tag | same gate | BLOCKED |

## 8. Questions only you can answer

1. Can you attach a device (or provide API 24/30 emulators) and paste the outputs from
   `docs/DEVICE_EVIDENCE_STEPS.md`, including the TTS voice dump for `en-IN`, `hi-IN` and `ta-IN`?
2. Do you want bundletool MIN/MAX and Play Console compressed size measured, which requires a local
   bundletool jar / Play account access you would need to provide or approve?
3. Should the ≥100-clip English acceptance set be prepared now (evaluation-only) to retry a VERIFIED
   claim, or stay closed with English BETA?
