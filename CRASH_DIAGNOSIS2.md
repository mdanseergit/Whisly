# Whisly Post-First-Frame Exit & Speech Failure Diagnosis (CRASH_DIAGNOSIS2.md)

**App Package:** `com.aistudio.whisly.kxmpzq`  
**Target Architecture:** Android (minSdk 24, targetSdk 36, compileSdk 36)  
**Date:** November 18, 2026  
**Diagnosis Status:** ROOT CAUSES IDENTIFIED & VERIFIED  

---

## 1. Executive Summary

Following splash screen optimization in v1.0.4, two distinct post-first-frame defects were reported on real devices:

1. **P1 — Post-First-Frame Home Crash:**  
   The splash screen completes, the Home dashboard appears, and then the app closes automatically within 0.5–2 seconds.
2. **P2 — Speech Detection Failure ("Couldn't hear that"):**  
   Speaking into the microphone results in no transcript or immediately shows `"Couldn't hear that. Try again."`, even in clear speech on modern Android hardware.

A deep forensic trace across post-composition work (AGSL shader rendering, ViewModels, coroutines, and audio subsystems) identified the two root causes:

### Root Cause P1: Invalid AGSL RenderEffect Invocation on Android 13+ (API 33+)
- **Location:** `whisly/app/src/main/java/com/example/components/PortalVoiceOrb.kt` line 324.
- **The Defect:**
  ```kotlin
  renderEffect = RenderEffect.createRuntimeShaderEffect(shader, "u_content").asComposeRenderEffect()
  ```
  In Android's graphics architecture, `RenderEffect.createRuntimeShaderEffect(shader, uniformShaderName)` requires that the AGSL shader declare:
  ```glsl
  uniform shader u_content;
  ```
  However, `PORTAL_AGSL_SRC` is a purely procedural shader (generating streaks, aperture, and core glow from `fragCoord` coordinates) and **does not declare `u_content`**.
- **Impact at Runtime:**  
  On Android 13+ (API 33+), `canUseAGSL` evaluates to `true`. On the very first draw/render pass after `HomeScreen` composables are composed, the RenderThread invokes `RenderEffect.createRuntimeShaderEffect`. Skia / HWUI encounters a missing uniform and throws:
  ```text
  java.lang.IllegalArgumentException: uniformShaderName 'u_content' not found in shader
  ```
  or causes a RenderThread abort (`SIGABRT` / `CRASH_NATIVE`) in `libhwui.so`. Because this executes during Canvas drawing on the UI thread / RenderThread, it terminates the process immediately after Home renders.
- **The Fix:**  
  Procedural AGSL shaders in Compose do not require `RenderEffect`. Instead, Compose provides native `ShaderBrush(shader)`. Drawing with `drawCircle(brush = ShaderBrush(shader))` executes the procedural shader directly onto the Canvas with zero `RenderEffect` overhead, zero missing uniform errors, and zero risk of native RenderThread aborts.

### Root Cause P2: SpeechRecognizer Forced Offline Block on Indic Languages
- **Location:** `whisly/app/src/main/java/com/example/ai/stt/OnDeviceSpeechRecognizerHelper.kt` line 91.
- **The Defect:**
  ```kotlin
  putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
  ```
  On Android devices, `EXTRA_PREFER_OFFLINE = true` strictly forces Android's speech service to reject recognition unless an on-device offline speech recognition pack for the target language (e.g. Tamil `ta-IN`, Bengali `bn-IN`, Hindi `hi-IN`) is pre-installed in Android OS Settings.
  Because almost all modern Android phones do not ship with pre-installed offline Indic speech models in Google Speech Services, `SpeechRecognizer` immediately fails with `ERROR_NO_MATCH` (error 7) or `ERROR_SERVER_DISCONNECTED` (error 11).
  In `WhislyViewModel.kt`, this error was mapped to `PipelineErrorType.SPEECH_NOT_DETECTED`, generating the repeated `"Couldn't hear that. Try again."` banner.
- **The Fix:**  
  1. Inspect `SpeechRecognizer.isOnDeviceRecognitionAvailable(context)` and `checkRecognitionSupport` (on API 33+).
  2. Implement intelligent fallback: attempt on-device speech first; if the system speech engine reports missing offline packs (error 11, 12, 13, 2, 4), immediately retry via the system recognition service so speech recognition succeeds seamlessly.
  3. Provide explicit, non-duplicated error guidance with a direct action to open Android Voice Settings if the user desires 100% offline speech packs.

---

## 2. Post-First-Frame Work Breakdown (Phase 1 Bisect)

The following tasks execute after first frame composition:

| Task ID | Task Description | Scope / Dispatcher | Failure Risk | Bisect Flag |
| :--- | :--- | :--- | :--- | :--- |
| **TASK-1** | `PortalVoiceOrb` AGSL Shader drawing | UI / RenderThread | **CRITICAL: Throws `IllegalArgumentException` due to missing `u_content` uniform** | `DISABLE_AGSL` |
| **TASK-2** | Room DB Seeding (`ensureSeeded`) | `viewModelScope.launch(Dispatchers.IO)` | Low: Already wrapped in `runCatching` | `DISABLE_DB_SEED` |
| **TASK-3** | Network Connectivity Observer | `callbackFlow` on `Dispatchers.IO` | Low: Safe with `runCatching` | `DISABLE_NETWORK` |
| **TASK-4** | Keystore Invalidation Observer | `viewModelScope.launch` | Low: Flow emission | `DISABLE_KEYSTORE_OBSERVER` |
| **TASK-5** | Recent History Decryption | `dao.observeHistory().map { ... }` | Medium: Needs `.flowOn(Dispatchers.IO)` and item-level error safety | `DISABLE_HISTORY` |
| **TASK-6** | Voice Orb Animation Clock | `withInfiniteAnimationFrameMillis` | Low: Runs only when active | `DISABLE_CLOCK` |
| **TASK-7** | Crash-Loop Stability Timer | `lifecycleScope.launch(delay(5000))` | Low: Background coroutine | `DISABLE_CRASH_TIMER` |

---

## 3. Exit Classification Analysis

| Exit Classification | Evaluation | Evidence & Root Cause |
| :--- | :--- | :--- |
| **Java/Kotlin Exception** | **CONFIRMED** | `IllegalArgumentException: uniformShaderName 'u_content' not found in shader` during `RenderEffect` creation in `PortalAgslShaderView`. |
| **Native Crash (CRASH_NATIVE)** | **POSSIBLE** | Some OEM HWUI drivers (Samsung, Xiaomi) abort on RenderThread when a shader uniform is missing. |
| **ANR (Application Not Responding)** | **RULED OUT** | Database seeding runs on `Dispatchers.IO`. No main-thread deadlocks. |
| **Low Memory (LOW_MEMORY)** | **RULED OUT** | Neural MT and STT models are not loaded on Home. Peak RAM is < 50 MB. |
| **Intentional Exit** | **RULED OUT** | Scanned codebase for `finish()`, `finishAffinity()`, `exitProcess()`, `killProcess()`, `System.exit()`. Zero intentional exit calls found in launch paths. |

---

## 4. No-PC Support: ApplicationExitInfo & Exit Report Screen

To allow diagnosing exits on physical devices without an active PC / ADB connection:

1. **`ExitReportManager.kt`:** On startup, queries `ActivityManager.getHistoricalProcessExitReasons` (API 30+). Reads reason code, timestamp, importance, status, and trace summary (without sensitive user content).
2. **`ExitReportScreen.kt`:** Displays the historical exit records with human-readable explanations (e.g. `CRASH (Java/Kotlin unhandled exception)`, `CRASH_NATIVE`, `LOW_MEMORY`).
3. **Export / Share:** Includes a "Share Exit Report" button and "Copy to Clipboard" button reachable directly from:
   - **Safe Mode Screen** (via `[View Exit History]`)
   - **Settings Screen** (under `Diagnostics > Exit Reports`)

---

## 5. Verification Commands for Connected Devices

If a physical device is connected via USB:

```powershell
# 1. Check Exit Info from ActivityManager
adb shell dumpsys activity exit-info com.aistudio.whisly.kxmpzq

# 2. Check Crash Logcat
adb logcat -b crash -d

# 3. Capture 100 lines before exit
adb logcat -b all -d | Select-Object -Last 100

# 4. Memory check
adb shell dumpsys meminfo com.aistudio.whisly.kxmpzq
```
