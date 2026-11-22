# Whisly Phase 0 Diagnosis Report (`DIAGNOSIS.md`)

## 1. Executive Summary
An exhaustive static and runtime trace of `whisly/app/src/main` and `whisly/backend` was conducted to reproduce and diagnose reported problems P1 through P4.

---

## 2. Problem P1 & P4: Language Pack Download Failure & Fake Installation

### Symptoms:
- Tapping "Download" on a language pack marks the language as "Installed" / "Offline ready" almost instantly without downloading any model file.
- Real model files are never transferred over the network.

### Code Path Trace:
1. **UI Layer**:
   - `LanguagePackCard.kt` (`onInstallClick`) triggers `WhislyViewModel.installLanguagePack(langCode)`.
2. **ViewModel Layer**:
   - [`WhislyViewModel.kt:680`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/presentation/WhislyViewModel.kt#L680) calls `modelManager.installLanguagePacksWithProgress(langCode)`.
3. **Model Manager Layer**:
   - [`WhislyModelManager.kt:234`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/modelmanager/WhislyModelManager.kt#L234) retrieves pack entities from Room (`dao.getPacksForLanguage(langCode, SHARED_MT_PACK_ID)`).
   - Packs were seeded by `CapabilityRegistry.buildInitialModelPacks()`.
   - Download URL: `https://models.whisly.ai/v1/packs/$packId.onnx.pack`.
4. **Network Request & Placeholder DNS Failure**:
   - In `WhislyModelManager.kt:270`:
     ```kotlin
     val downloadedViaHttp = tryDownloadWithHttpRange(pack.downloadUrl, stagedFile, existingOffset)
     ```
   - An HTTP GET/HEAD request to `https://models.whisly.ai/...` fails immediately with DNS resolution error:
     ```text
     https://models.whisly.ai/v1/packs/mt_indictrans2_200m_int8.onnx.pack -> FAILED (URLError): <urlopen error [Errno 11001] getaddrinfo failed>
     https://models.whisly.ai/v1/packs/stt_indicconformer_ta_v2.onnx.pack -> FAILED (URLError): <urlopen error [Errno 11001] getaddrinfo failed>
     ```
   - The domain `models.whisly.ai` is a non-existent placeholder.
5. **Simulated Fallback Bypass (`WhislyModelManager.kt:271-274`)**:
   ```kotlin
   if (!downloadedViaHttp) {
       stagedFile.writeText(expectedPayload)
   }
   ```
   When the HTTP download fails, `stagedFile` is written with `expectedPayload = "$packId:$version:$declaredSizeBytes"` (a 30-byte text string!).
6. **Fake Checksum Match (`CapabilityRegistry.kt:685, 714`)**:
   In `CapabilityRegistry.buildInitialModelPacks()`, `sha256Checksum` was hardcoded to:
   ```kotlin
   sha256Checksum = KeystoreCryptoManager.sha256Hex(sttPayload.toByteArray())
   ```
   The database checksum is literally the SHA-256 of the 30-byte string `$packId:$version:$declaredSizeBytes`.
   Therefore, `stagedSha256.equals(pack.sha256Checksum)` succeeds, promoting the 30-byte text file to `$packId.onnx.manifest`.
7. **Backend Inspection (`whisly/backend/app/main.py:387`)**:
   - Endpoint `/api/v1/model-packs/{pack_id}/download` does not serve any binary file. It returns a JSON object pointing back to `https://models.whisly.ai/...`.

---

## 3. Problem P2: Voice Input Shows "Couldn't hear that" Even When Speaking Clearly

### Symptoms:
- Even when speaking clearly into the microphone, the app consistently outputs `"Couldn't hear that. Try again."`.

### Code Path Trace:
1. **Audio Recording (`AudioProcessor.kt`)**:
   - Captures 16 kHz mono PCM into `pcm16Samples` and computes VAD flags (`vadSpeechDetected`).
   - Builds `AudioInput` where `val recognizedSpeechText: String? = null`.
   - `AudioProcessor.kt` does **not** perform speech recognition and never sets `recognizedSpeechText`.
2. **STT Router (`IndicSttEngine.kt`)**:
   - In [`IndicSttEngine.kt:63`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/stt/IndicSttEngine.kt#L63):
     ```kotlin
     val decodedText = audio.recognizedSpeechText?.trim().orEmpty()
     ```
   - Because `audio.recognizedSpeechText` is null, `decodedText` is `""`.
   - In [`IndicSttEngine.kt:120-130`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/stt/IndicSttEngine.kt#L120):
     ```kotlin
     if (decodedText.isEmpty()) {
         return@withContext SpeechResult(
             success = false,
             transcript = "",
             error = PipelineErrorType.STT_LOW_CONFIDENCE
         )
     }
     ```
   - `IndicSttEngine` does not run any ONNX Runtime or TFLite model on the audio buffer. It expects a pre-filled transcript.
3. **UI Display**:
   - `PipelineErrorType.STT_LOW_CONFIDENCE` maps directly to:
     `"Couldn't hear that. Try again."`.

---

## 4. Problem P3: Translation Fails Both Offline and Online

### Symptoms:
- Translation fails or produces an error for both speech-to-speech and text translation modes.

### Code Path Trace:
1. **Offline Mode (`IndicTrans2Engine.kt`)**:
   - In [`IndicTrans2Engine.kt:153`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/ai/translation/IndicTrans2Engine.kt#L153):
     ```kotlin
     val rawDecoded: String? = neuralSessionDelegate?.invoke(maskedSentence, source.lowercase(), target.lowercase())
     if (rawDecoded.isNullOrBlank()) {
         return@withContext TranslationResult(
             success = false,
             error = PipelineErrorType.TRANSLATION_FAILURE
         )
     }
     ```
   - In [`WhislyViewModel.kt:89-93`](file:///C:/Users/danse/OneDrive/document/Whisly/whisly/app/src/main/java/com/example/presentation/WhislyViewModel.kt#L89):
     `mtEngine` is instantiated with `neuralSessionDelegate = null`.
   - Result: `rawDecoded` is always null, returning `TRANSLATION_FAILURE` on every translation call.
2. **Online Mode (`WhislyBackendApi.kt` & `whisly/backend/app/main.py`)**:
   - `WhislyBackendApi.kt` does not define any translation endpoint (no `POST /api/v1/translate`).
   - `whisly/backend/app/main.py` does not implement any translation endpoint.
   - `WhislyViewModel.kt` only delegates to `mtEngine` without checking for or routing to an online translation service.

---

## 5. Summary of Root Causes

| Problem | Root Cause Location | Technical Cause |
| :--- | :--- | :--- |
| **P1 & P4 (Fake Downloads)** | `WhislyModelManager.kt:270-274`, `CapabilityRegistry.kt:684, 713` | Download URLs are non-existent placeholder domains (`models.whisly.ai`). When HTTP fails, code falls back to writing a 30-byte text payload `$packId:$version:$size` to simulate a downloaded pack. Room checksum is the SHA-256 of that text payload. |
| **P2 ("Couldn't hear that")** | `IndicSttEngine.kt:63, 120`, `AudioProcessor.kt` | `IndicSttEngine` does not run any acoustic speech model; it reads `audio.recognizedSpeechText`. Since `AudioProcessor` leaves `recognizedSpeechText` null, `decodedText` is always empty, triggering `PipelineErrorType.STT_LOW_CONFIDENCE`. |
| **P3 (Translation Fails)** | `IndicTrans2Engine.kt:153`, `WhislyViewModel.kt:89-93`, `backend/app/main.py` | Offline translation delegates to `neuralSessionDelegate`, which is passed as `null` in `WhislyViewModel`. Online translation endpoint (`POST /v1/translate`) does not exist in either the Android Retrofit interface or the FastAPI backend. |

