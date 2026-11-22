# Whisly Phase 0 Diagnosis Report 2 (`DIAGNOSIS2.md`)

## 1. Executive Summary
An exhaustive diagnosis of the reported user defects S1 through S4 was conducted across the source code, screenshots, and test fixtures.

---

## 2. Problem S2: Input Echoed as Translation & Contradictory Status
### Symptoms (from Screenshot 1):
- Auto-detect $\to$ Tamil selected.
- Input in "Original Speech": `"my name is danseer"`.
- "Translation • தமிழ் (Tamil)" box displays the exact same English text: `"my name is danseer"`.
- Status line displays: `"Translation ready"`.
- Top bar chip simultaneously displays: `"Pack Needed"`.

### Code Path & Root Cause:
1. **Fallback to Target Language on Typed Auto-Detect** ([`WhislyViewModel.kt:408-412`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/presentation/WhislyViewModel.kt#L408-L412)):
   ```kotlin
   val effectiveSource = if (state.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
       state.detectedSourceLangCode ?: "ta"
   } else {
       state.sourceLangCode
   }
   ```
   When the user types text into the input field rather than speaking, `state.detectedSourceLangCode` is `null`. The code hardcoded a fallback to `"ta"`.
   Because the target language was also `"ta"` (Tamil), `effectiveSource` became `"ta"` and `target` became `"ta"`.
2. **Identity Translation Echo Trap** ([`IndicTrans2Engine.kt:77-92`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/translation/IndicTrans2Engine.kt#L77-L92)):
   ```kotlin
   // Identity translation (source == target)
   if (source.equals(target, ignoreCase = true)) {
       return@withContext TranslationResult(
           success = true,
           sourceText = text,
           normalizedSourceText = normalizedInput,
           translatedText = normalizedInput, // <--- ECHOES INPUT DIRECTLY TO OUTPUT
           ...
       )
   }
   ```
   Because `source == "ta"` and `target == "ta"`, `IndicTrans2Engine` returned `translatedText = normalizedInput` with `success = true`.
3. **No Pre-Translation Pack Readiness Gate**:
   Translation was executed without checking whether the required model packs for `source -> target` were installed and verified.
4. **Contradictory Status Display**:
   - `mtRes.success == true` caused [`SpeechToSpeechPipeline.kt:156`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/pipeline/SpeechToSpeechPipeline.kt#L156) to set `_currentStage.value = PipelineStage.COMPLETED`.
   - `PipelineStage.COMPLETED` renders `"Translation ready"` in the status line.
   - Concurrently, [`WhislyBrandHeader.kt:104`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/components/WhislyBrandHeader.kt#L104) checks `isPairVerifiedOffline`. Since Tamil is not installed, the top-bar chip displays `"Pack Needed"`.

---

## 3. Problem S3: English Missing from Language List and Registry
### Symptoms (from Screenshot 2):
- English (`en`) does not appear in Popular or All languages in the Languages screen or selectors.

### Code Path & Root Cause:
1. **Hardcoded Exclusion in Capability Registry** ([`CapabilityRegistry.kt:442`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/registry/CapabilityRegistry.kt#L442)):
   ```kotlin
   val indianLanguages: List<LanguageCapabilityEntry> = wave1Languages.filter { it.code != "en" }
   ```
2. **Database Seeding Only Includes `indianLanguages`** ([`CapabilityRegistry.kt:626`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/registry/CapabilityRegistry.kt#L626)):
   ```kotlin
   fun buildInitialLanguageEntities(preInstalledCodes: Set<String> = emptySet()): List<LanguageEntity> {
       return indianLanguages.map { entry -> ... } // EXCLUDES "en"!
   }
   ```
   Room's `languages` table was seeded only from `indianLanguages`, completely omitting English. Because the UI observes Room's `languages` table, English was never rendered in the Languages screen or language pickers.

---

## 4. Problem S1: Hardcoded Seeded Sizes on Language Cards
### Symptoms (from Screenshot 2):
- Tamil shows `103.3 MB`, Bengali `103.2 MB`, Hindi `105.0 MB`, Kannada `101.8 MB`.
- These values do not reflect real verified files from `whisly_manifest.json` and are non-incremental.

### Code Path & Root Cause:
In [`CapabilityRegistry.kt:156, 175, 194, 213`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/registry/CapabilityRegistry.kt#L156):
- Tamil: `64,487,424 + 43,830,272 = 108,317,696 bytes` $\to$ **103.3 MB**
- Bengali: `63,963,136 + 44,249,088 = 108,212,224 bytes` $\to$ **103.2 MB**
- Hindi: `65,011,712 + 45,088,768 = 110,100,480 bytes` $\to$ **105.0 MB**
- Kannada: `63,700,992 + 43,048,960 = 106,749,952 bytes` $\to$ **101.8 MB**
These are hardcoded arithmetic additions in `CapabilityRegistry` entries, ignoring:
1. Real sizes of hosted assets in `whisly_manifest.json` (`sherpa-onnx-whisper-tiny.tar.bz2` = 116.2 MB; `vits-inflect-en-nano-v2.tar.bz2` = 22.4 MB; `vits-coqui-bn-custom_female.tar.bz2` = 108.1 MB; Android System Voice = 0 MB).
2. The incremental download rule (shared STT model is counted once; subsequent languages needing system TTS require 0 MB incremental download).

---

## 5. Problem S4: UI Layout & String Formatting Defects
### Symptoms (from Screenshot 2):
1. **Malayalam Mid-Word Wrapping**:
   - The Malayalam row renders `"Mal"` on one line and `"ayal"` on the next, breaking card geometry.
   - **Root Cause** ([`LanguagePackCard.kt:107-122`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/components/LanguagePackCard.kt#L107-L122)):
     ```kotlin
     Column {
         Row(verticalAlignment = Alignment.CenterVertically) {
             Text(text = language.nativeName, ...)
             Spacer(modifier = Modifier.width(WhislyTokens.Space8))
             Text(text = language.englishName, ...)
         }
     ```
     Placing `nativeName` ("മലയാളം") and `englishName` ("Malayalam") side-by-side in a horizontal `Row` starves the English name of horizontal width, forcing Android text layout to break "Malayalam" into "Mal / ayal".
2. **Raw Language Code in Snackbar**:
   - Shows: `"Removed offline pack for TA"`.
   - **Root Cause** ([`WhislyViewModel.kt:726`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/presentation/WhislyViewModel.kt#L726)):
     ```kotlin
     it.copy(statusBannerMessage = "Removed offline pack for ${langCode.uppercase()}")
     ```
     Directly interpolated raw uppercase code `TA` instead of resolving the language display name ("Tamil").
3. **Contradictory Chip vs. Status Line**:
   - Status chip reads `"Pack Needed"` while status line reads `"Translation ready"`.
   - Root cause: lack of a single unified UI state driving both components.

---

## 6. Problem S5: Splash Screen Logo Presentation
### Symptoms:
- Logo on splash screen is confined to a small 96dp box with borders, and cold-start XML splash uses mismatched nested shapes.
- **Root Cause**: [`WhislySplashScreen`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/presentation/onboarding/SplashAndOnboardingScreen.kt) restricts `WhislyLogo` to `LogoSizeHero` (96.dp) with small inner padding and card border, rather than a full, prominent, smoothly scaled brand presentation.

