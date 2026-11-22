# DIAGNOSIS 3: Root Cause Analysis for D1–D5 & Translation Pair Issue

Date: November 6, 2026
App: Whisly (`com.aistudio.whisly.kxmpzq`)

---

## D1: Downloads Stay "Loading" / "Verifying", No Live Progress, No Download Manager

### Root Cause Analysis
1. **Monolithic Blocking Stream Copy Without Chunk Progress**:
   In `WhislyModelManager.kt` lines 322–340:
   ```kotlin
   FileOutputStream(stagedFile, append).use { out ->
       body.byteStream().copyTo(out)
   }
   ```
   `copyTo(out)` copies the entire 116 MB payload in a single synchronous blocking call. While 116 MB downloads over network, zero intermediate byte progress callbacks are dispatched.
2. **Missing Granular State Machine**:
   The Room database table only has `installStatus` (`NOT_INSTALLED`, `DOWNLOADING`, `VERIFYING`, `INSTALLED`, `FAILED`). It lacks `QUEUED`, `PAUSED`, `CANCELLED`, byte counts (`bytesDownloaded`, `totalBytes`), transfer speed, and ETA.
3. **No Background Worker / Process Death Resilience**:
   Downloads run directly inside `viewModelScope.launch` in `WhislyViewModel.kt:763`. When the user navigates away, locks their screen, or when the system reclaims the activity, the coroutine is cancelled mid-download.
   The database remains frozen in `DOWNLOADING` or `VERIFYING`. On app restart, `cleanupOrphanedStagingAndReverifyDiskTruth()` did not reset stale `DOWNLOADING`/`VERIFYING` states to `PAUSED` or `FAILED`, permanently displaying "loading" or "verifying".
4. **Un-instrumented 8 KB SHA-256 Hashing**:
   `KeystoreCryptoManager.sha256File()` used an 8192-byte buffer with no progress emissions, taking multiple seconds on 116 MB flash storage without updating the UI.
5. **No Download Manager UI**:
   There was no dedicated screen or queue manager to pause, resume, cancel, retry, or inspect active downloads and device storage.

---

## D2: UI Stops Responding to Touch During or After Downloads

### Root Cause Analysis
1. **Main Thread Contention & Room Update Storm**:
   Every state update in `WhislyModelManager` executed `dao.updateLanguage()`. Since `WhislyRepository.languagesFlow` emits the entire 13-language list whenever any language row changes, every progress tick triggered a full-tree recomposition of `LanguagesScreen` and `TranslateScreen`.
2. **Unbuffered IO & Lack of StrictMode**:
   Disk reads/writes and crypto calculations lacked strict thread isolation verification. Without StrictMode penaltyLogging in debug mode, disk operations near main-thread callers created transient frame drops (>16ms jank).
3. **Lack of Mutex / Work Isolation**:
   Concurrent download requests competed for staging files and Room DB locks without an orderly queue.

---

## D3: Tapping the Translate Tab Opens Profile Instead

### Root Cause Analysis
1. **Route Mapping Mismatch on Non-Primary Destinations**:
   In `WhislyNavigation.kt` lines 128–134:
   ```kotlin
   val activeTabRoute = when (currentRoute) {
       WhislyRoutes.HOME, WhislyRoutes.CONVERSATION -> WhislyRoutes.HOME
       WhislyRoutes.TRANSLATE, WhislyRoutes.TEXT_MODE -> WhislyRoutes.TRANSLATE
       WhislyRoutes.HISTORY, WhislyRoutes.SAVED -> WhislyRoutes.HISTORY
       WhislyRoutes.PROFILE, WhislyRoutes.LANGUAGES, WhislyRoutes.SETTINGS, WhislyRoutes.ABOUT -> WhislyRoutes.PROFILE
       else -> WhislyRoutes.TRANSLATE
   }
   ```
   When the user navigates to `WhislyRoutes.LANGUAGES` (e.g. from the top bar "Download" prompt), `activeTabRoute` is mapped to `WhislyRoutes.PROFILE`.
2. **Positional `remember` in Loop & Tab Click Handling**:
   In `WhislyGooeyNavBar.kt` lines 278–310:
   `items.forEach` instantiated `remember { MutableInteractionSource() }` without a key.
   When the user is on `Languages` (where Profile appears selected), tapping the Translate tab triggered `onNavigate("translate")`.
3. **`popUpTo(findStartDestination().id)` with `restoreState = true`**:
   In `WhislyNavigation.kt:147-153`:
   When `startDestination` is `TRANSLATE`, `findStartDestination().id` points to `TRANSLATE`.
   Popping up to the start destination while `restoreState = true` restored previous backstack states that were stored when navigating between secondary screens.
   Additionally, tab hit areas needed explicit fixed equal-width cells (`weight(1f)`) with minimum 48dp height that do not follow or depend on the animated canvas pill.

---

## D4: Voice Input Shows "Couldn't Hear That" on Physical Phone

### Root Cause Analysis
1. **Null Acoustic Inference Return in ViewModel**:
   In `WhislyViewModel.kt` lines 105–110:
   ```kotlin
   private fun recognizeSpeechOnDevice(pcm16: ShortArray, sampleRate: Int, lang: String): Pair<String, Float>? {
       if (pcm16.isEmpty()) return null
       // On-device acoustic inference session
       return null // ALWAYS RETURNED NULL!
   }
   ```
   Because `recognizeSpeechOnDevice` unconditionally returned `null`, `IndicSttEngine.kt` never received an acoustic transcript, defaulting to `STT_LOW_CONFIDENCE` which mapped to `"Couldn't hear that. Try again."`.
2. **Microphone Privacy Mute Detection Missing**:
   On Android 12+, if the hardware microphone privacy toggle is toggled off, `AudioRecord.read()` returns all zeros (silence). The app treated this as silence without informing the user that the microphone is muted or blocked.
3. **Missing Debug Diagnostics**:
   No debug logcat tags or WAV export existed to inspect the actual PCM audio buffer, sample rate, peak, RMS, and VAD decision.

---

## D5: Splash Screen Logo Not Centered or Aligned Properly

### Root Cause Analysis
1. **Double Background & Circular Mask Collision (Android 12+)**:
   In `ic_splash_whisly_logo.xml`:
   ```xml
   <item android:width="108dp" android:height="108dp" android:gravity="center">
       <shape android:shape="oval">
           <solid android:color="@color/whisly_warm_white" />
       </shape>
   </item>
   ```
   Android 12+ `SplashScreen` API applies a circular mask to `windowSplashScreenAnimatedIcon`. Having an internal oval shape combined with `android:windowSplashScreenIconBackgroundColor` caused clipping and off-center rendering.
2. **Pre-API 31 Layer-List Mismatch**:
   In `bg_splash_whisly.xml`, a 140dp squircle with 28dp radius was drawn, differing in size and positioning from the 108dp/58dp icon used in the theme.
3. **Compose Handoff Jump**:
   `WhislySplashScreen` had differing padding and vertical arrangement, creating a visible 1-frame visual jump when the system splash transitioned into the first Compose frame.

---

## Translation Pair Issue: "Translation Pair is Not Available"

### Root Cause Analysis
1. **ViewModel Router Returned Null**:
   In `WhislyViewModel.kt` lines 112–122:
   ```kotlin
   private fun routeOfflineNeuralTranslation(sentence: String, src: String, tgt: String): String? {
       ...
       return null // ALWAYS RETURNED NULL!
   }
   ```
2. **`TRANSLATION_FAILURE` User Message Mapped to "This language pair isn't available offline yet."**:
   In `WhislyModels.kt` lines 108–111:
   ```kotlin
   TRANSLATION_FAILURE(
       "Translation Unavailable",
       "This language pair isn't available offline yet."
   )
   ```
   When `routeOfflineNeuralTranslation` returned `null`, `IndicTrans2Engine.kt` fell through to `TRANSLATION_FAILURE`, which showed the exact message `"This language pair isn't available offline yet."`, even for valid pairs like Tamil <-> English!
3. **Long-tail Wave 3 Pairs Marked `BELOW_THRESHOLD`**:
   In `CapabilityRegistry.kt:462`, Wave 3 languages were marked `BELOW_THRESHOLD` and rejected by `IndicTrans2Engine.kt:98`. All language pairs must be supported via direct or pivot routes.

