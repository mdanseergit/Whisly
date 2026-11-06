package com.example.presentation

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.audio.AudioProcessor
import com.example.ai.evaluation.AiEvaluationSuite
import com.example.ai.modelmanager.WhislyModelManager
import com.example.ai.pipeline.AudioDiagnostics
import com.example.ai.pipeline.SpeechToSpeechPipeline
import com.example.ai.registry.CapabilityRegistry
import com.example.ai.stt.IndicSttEngine
import com.example.ai.translation.IndicLanguageCatalog
import com.example.ai.translation.IndicTrans2Engine
import com.example.ai.tts.VitsRasaTtsEngine
import com.example.components.observeNetworkConnectivityFlow
import com.example.data.local.AppSettingsEntity
import com.example.data.local.InstalledModelEntity
import com.example.data.local.LanguageEntity
import com.example.data.local.ModelPackEntity
import com.example.data.local.SavedPhraseEntity
import com.example.data.local.WhislyDatabase
import com.example.data.repositories.DecryptedHistoryItem
import com.example.data.repositories.WhislyRepository
import com.example.domain.models.ConversationTurn
import com.example.domain.models.PipelineErrorType
import com.example.domain.models.PipelineStage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.UUID

data class TranslateUiState(
    val sourceLangCode: String = "ta",
    val targetLangCode: String = "bn",
    val detectedSourceLangCode: String? = null,
    val ambiguousLanguageCandidates: List<String> = emptyList(),
    val isOnline: Boolean = false,
    val isRecording: Boolean = false,
    val recordingElapsedMs: Long = 0L,
    val recognizedTranscript: String = "",
    val initialSttTranscript: String = "",
    val translatedSentence: String = "",
    val tokenizedPreview: String = "",
    val sttConfidence: Float? = null,
    val lastSttLatencyMs: Long = 0L,
    val lastMtLatencyMs: Long = 0L,
    val lastTtsLatencyMs: Long = 0L,
    val lastSynthesizedPcm: ShortArray = ShortArray(0),
    val lastSynthesizedSampleRateHz: Int = VitsRasaTtsEngine.VITS_NATIVE_SAMPLE_RATE_HZ,
    val activeError: PipelineErrorType? = null,
    val statusBannerMessage: String? = null,
    val textModeInput: String = "",
    val textModeOutput: String = "",
    val textModeLatencyMs: Long = 0L,
    val conversationTurns: List<ConversationTurn> = emptyList(),
    val activeConversationSpeakerIsA: Boolean = true,
    val autoConfirmConversationTranscript: Boolean = true,
    val savedSearchQuery: String = "",
    val savedSelectedCategory: String = "All",
    val languageSearchQuery: String = "",
    val isDarkTheme: Boolean = false,
    val lowEndPerformanceMode: Boolean = false
)

class WhislyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WhislyDatabase.getInstance(application)
    private val dao = database.whislyDao()
    private val modelManager = WhislyModelManager(application, dao)
    val repository = WhislyRepository(dao, modelManager)

    val audioProcessor = AudioProcessor(application)

    // Lazy AI Engine initialization so cold start never waits for STT/MT/TTS
    private var pipelineInitialized = false
    private val sttEngine by lazy {
        IndicSttEngine { packId ->
            modelManager.isPackInstalledAndVerified(packId)
        }
    }
    private val mtEngine by lazy {
        IndicTrans2Engine(
            isPackInstalledAndVerified = { packId ->
                modelManager.isPackInstalledAndVerified(packId)
            }
        )
    }
    private var ttsEngineInitialized = false
    private val ttsEngine by lazy {
        ttsEngineInitialized = true
        VitsRasaTtsEngine(
            context = application,
            audioProcessor = audioProcessor,
            isPackInstalledAndVerified = { packId ->
                modelManager.isPackInstalledAndVerified(packId)
            }
        )
    }
    val pipeline by lazy {
        pipelineInitialized = true
        SpeechToSpeechPipeline(audioProcessor, sttEngine, mtEngine, ttsEngine)
    }

    val languagesState: StateFlow<List<LanguageEntity>> = repository.languagesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val modelPacksState: StateFlow<List<ModelPackEntity>> = repository.modelPacksFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val installedModelsState: StateFlow<List<InstalledModelEntity>> = repository.installedModelsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val savedPhrasesState: StateFlow<List<SavedPhraseEntity>> = repository.savedPhrasesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val historyState: StateFlow<List<DecryptedHistoryItem>> = repository.decryptedHistoryFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val settingsState: StateFlow<AppSettingsEntity?> = repository.settingsFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _pipelineStage = MutableStateFlow(PipelineStage.IDLE)
    val pipelineStage: StateFlow<PipelineStage> = _pipelineStage.asStateFlow()

    val waveformAmplitudes: StateFlow<List<Float>> = audioProcessor.waveformAmplitudes
    val isPlayingAudio: StateFlow<Boolean> = audioProcessor.isPlaying
    val audioDiagnostics: StateFlow<AudioDiagnostics> = audioProcessor.lastDiagnostics

    private val _isColdStartReady = MutableStateFlow(false)
    val isColdStartReady: StateFlow<Boolean> = _isColdStartReady.asStateFlow()

    private val isDeviceLowRam: Boolean = runCatching {
        val am = application.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
        am?.isLowRamDevice == true
    }.getOrDefault(false)

    private val _uiState = MutableStateFlow(
        TranslateUiState(lowEndPerformanceMode = isDeviceLowRam)
    )
    val uiState: StateFlow<TranslateUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            runCatching {
                repository.ensureSeeded()
                val settings = dao.getSettingsSnapshot()
                if (settings != null) {
                    _uiState.update {
                        it.copy(
                            sourceLangCode = settings.defaultSourceLang,
                            targetLangCode = settings.defaultTargetLang
                        )
                    }
                }
            }
            _isColdStartReady.value = true
        }
        viewModelScope.launch {
            observeNetworkConnectivityFlow(application).collect { online ->
                _uiState.update { it.copy(isOnline = online) }
            }
        }
    }

    /**
     * PHASE 8 — Unloads resident STT/MT models and trims TTS audio cache on memory pressure or backgrounding.
     */
    fun onTrimMemory(@Suppress("UNUSED_PARAMETER") level: Int) {
        if (pipelineInitialized) {
            pipeline.unloadModelsOnMemoryPressure()
        }
    }

    fun onAppBackgrounded() {
        if (_uiState.value.isRecording) {
            audioProcessor.stopRecording()
            _uiState.update { it.copy(isRecording = false) }
        }
        if (pipelineInitialized) {
            pipeline.unloadModelsOnMemoryPressure()
        }
    }

    fun setNetworkOnlineState(isOnline: Boolean) {
        _uiState.update { it.copy(isOnline = isOnline) }
    }

    fun completeOnboarding(preferredSource: String = "ta", preferredTarget: String = "bn") {
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    onboardingCompleted = true,
                    defaultSourceLang = preferredSource,
                    defaultTargetLang = preferredTarget
                )
            }
            _uiState.update {
                it.copy(
                    sourceLangCode = preferredSource,
                    targetLangCode = preferredTarget
                )
            }
        }
    }

    fun selectSourceLanguage(code: String) {
        _uiState.update { state ->
            if (code != CapabilityRegistry.AUTO_DETECT_CODE && code == state.targetLangCode) {
                val fallbackTarget = if (state.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
                    if (code == "ta") "bn" else "ta"
                } else {
                    state.sourceLangCode
                }
                state.copy(
                    sourceLangCode = code,
                    targetLangCode = fallbackTarget,
                    ambiguousLanguageCandidates = emptyList(),
                    activeError = null
                )
            } else {
                state.copy(
                    sourceLangCode = code,
                    ambiguousLanguageCandidates = emptyList(),
                    activeError = null
                )
            }
        }
        if (code != CapabilityRegistry.AUTO_DETECT_CODE) {
            viewModelScope.launch {
                repository.updateSettings { it.copy(defaultSourceLang = code) }
            }
        }
    }

    fun confirmAmbiguousLanguage(selectedCode: String) {
        _uiState.update {
            it.copy(
                sourceLangCode = selectedCode,
                detectedSourceLangCode = selectedCode,
                ambiguousLanguageCandidates = emptyList(),
                activeError = null
            )
        }
    }

    fun selectTargetLanguage(code: String) {
        _uiState.update { state ->
            if (code == state.sourceLangCode) {
                state.copy(
                    targetLangCode = code,
                    sourceLangCode = state.targetLangCode,
                    activeError = null
                )
            } else {
                state.copy(targetLangCode = code, activeError = null)
            }
        }
        viewModelScope.launch {
            repository.updateSettings { it.copy(defaultTargetLang = code) }
        }
    }

    fun swapLanguages() {
        val current = _uiState.value
        val effectiveSource = if (current.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
            current.detectedSourceLangCode ?: "ta"
        } else {
            current.sourceLangCode
        }
        val newSource = current.targetLangCode
        val newTarget = effectiveSource
        _uiState.update {
            it.copy(
                sourceLangCode = newSource,
                targetLangCode = newTarget,
                recognizedTranscript = it.translatedSentence,
                translatedSentence = it.recognizedTranscript,
                ambiguousLanguageCandidates = emptyList(),
                activeError = null
            )
        }
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(defaultSourceLang = newSource, defaultTargetLang = newTarget)
            }
        }
    }

    fun updateLanguageSearchQuery(query: String) {
        _uiState.update { it.copy(languageSearchQuery = query) }
    }

    fun onPermissionDenied() {
        _uiState.update {
            it.copy(
                isRecording = false,
                activeError = PipelineErrorType.MIC_PERMISSION_DENIED
            )
        }
        _pipelineStage.value = PipelineStage.ERROR
    }

    fun toggleMainRecording() {
        if (_uiState.value.isRecording) {
            stopMainRecordingAndRunStt()
        } else {
            startMainRecording()
        }
    }

    private fun startMainRecording() {
        if (!audioProcessor.hasMicrophoneHardware()) {
            _uiState.update { it.copy(activeError = PipelineErrorType.NO_MICROPHONE_AVAILABLE) }
            return
        }
        if (ttsEngineInitialized) {
            ttsEngine.stopSpeaking()
        }
        _pipelineStage.value = PipelineStage.RECORDING
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingElapsedMs = 0L,
                activeError = null,
                statusBannerMessage = null
            )
        }
        audioProcessor.startRecording(viewModelScope) { _, elapsed ->
            _uiState.update { it.copy(recordingElapsedMs = elapsed) }
        }
    }

    private fun stopMainRecordingAndRunStt() {
        val rawInput = audioProcessor.stopRecording()
        val sourceLang = _uiState.value.sourceLangCode

        _uiState.update { it.copy(isRecording = false) }

        viewModelScope.launch {
            _pipelineStage.value = PipelineStage.STT_INFERENCE
            val sttResult = pipeline.processRecordedAudioToTranscript(rawInput, sourceLang)
            _pipelineStage.value = pipeline.currentStage.value
            if (!sttResult.success) {
                _uiState.update {
                    it.copy(
                        activeError = sttResult.error ?: PipelineErrorType.STT_FAILURE
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        recognizedTranscript = sttResult.transcript,
                        initialSttTranscript = sttResult.transcript,
                        detectedSourceLangCode = sttResult.language,
                        ambiguousLanguageCandidates = sttResult.ambiguousLanguageCandidates,
                        sttConfidence = sttResult.confidence,
                        lastSttLatencyMs = sttResult.latencyMs,
                        activeError = null
                    )
                }
            }
        }
    }

    fun updateRecognizedTranscript(edited: String) {
        _uiState.update { it.copy(recognizedTranscript = edited, activeError = null) }
    }

    fun confirmTranscriptAndTranslate() {
        val state = _uiState.value
        val transcript = state.recognizedTranscript.trim()
        if (transcript.isEmpty()) return

        val effectiveSource = if (state.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
            state.detectedSourceLangCode ?: "ta"
        } else {
            state.sourceLangCode
        }

        viewModelScope.launch {
            val settings = settingsState.value ?: AppSettingsEntity()
            _pipelineStage.value = PipelineStage.MT_INFERENCE
            val outcome = pipeline.translateAndSynthesize(
                confirmedText = transcript,
                sourceLang = effectiveSource,
                targetLang = state.targetLangCode,
                autoPlay = settings.autoPlayTranslatedSpeech,
                speechSpeed = settings.speechSpeed
            )
            _pipelineStage.value = pipeline.currentStage.value

            val mtRes = outcome.translationResult
            val ttsRes = outcome.audioResult
            if (mtRes != null && mtRes.success) {
                val totalLatency = state.lastSttLatencyMs + mtRes.latencyMs + (ttsRes?.latencyMs ?: 0L)
                _uiState.update {
                    it.copy(
                        translatedSentence = mtRes.translatedText,
                        tokenizedPreview = mtRes.tokenizedPreview,
                        lastMtLatencyMs = mtRes.latencyMs,
                        lastTtsLatencyMs = ttsRes?.latencyMs ?: 0L,
                        lastSynthesizedPcm = ttsRes?.pcm16Audio ?: ShortArray(0),
                        lastSynthesizedSampleRateHz = ttsRes?.sampleRateHz ?: VitsRasaTtsEngine.VITS_NATIVE_SAMPLE_RATE_HZ,
                        activeError = outcome.error
                    )
                }

                repository.recordEncryptedHistory(
                    sourceLang = effectiveSource,
                    targetLang = state.targetLangCode,
                    originalText = transcript,
                    translatedText = mtRes.translatedText,
                    mode = "VOICE",
                    confidence = state.sttConfidence ?: 0f,
                    latencyMs = totalLatency
                )

                if (state.initialSttTranscript.isNotBlank() && state.initialSttTranscript != transcript) {
                    repository.recordUserCorrection(
                        sourceLang = effectiveSource,
                        targetLang = state.targetLangCode,
                        originalStt = state.initialSttTranscript,
                        correctedStt = transcript,
                        modelTranslated = mtRes.translatedText,
                        correctedTranslated = null,
                        consentedToSync = settings.consentToCorrectionSync
                    )
                }
            } else {
                // Phase 7: Keep recognizedTranscript intact even when MT fails!
                _uiState.update {
                    it.copy(activeError = outcome.error ?: PipelineErrorType.TRANSLATION_FAILURE)
                }
            }
        }
    }

    fun updateTranslatedSentenceBeforePlayback(editedTranslation: String) {
        _uiState.update { it.copy(translatedSentence = editedTranslation) }
    }

    fun playCurrentTranslatedVoice() {
        val state = _uiState.value
        if (state.translatedSentence.isBlank()) return
        val speed = settingsState.value?.speechSpeed ?: 1.0f
        viewModelScope.launch {
            val played = ttsEngine.speakOutLoud(
                text = state.translatedSentence,
                languageCode = state.targetLangCode,
                pcmAudio = state.lastSynthesizedPcm,
                sampleRateHz = state.lastSynthesizedSampleRateHz,
                speechSpeed = speed
            )
            if (!played) {
                _uiState.update { it.copy(activeError = PipelineErrorType.UNSUPPORTED_TARGET_VOICE) }
            }
        }
    }

    fun speakArbitraryText(text: String, langCode: String) {
        val speed = settingsState.value?.speechSpeed ?: 1.0f
        viewModelScope.launch {
            val played = ttsEngine.speakOutLoud(
                text = text,
                languageCode = langCode,
                pcmAudio = ShortArray(0),
                speechSpeed = speed
            )
            if (!played) {
                _uiState.update { it.copy(activeError = PipelineErrorType.UNSUPPORTED_TARGET_VOICE) }
            }
        }
    }

    fun stopAudioPlayback() {
        if (ttsEngineInitialized) {
            ttsEngine.stopSpeaking()
        }
    }

    fun toggleConversationTurnRecording(isPersonA: Boolean) {
        val state = _uiState.value
        if (state.isRecording) {
            val rawInput = audioProcessor.stopRecording()
            val effectiveSrc = if (state.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
                state.detectedSourceLangCode ?: "ta"
            } else {
                state.sourceLangCode
            }
            val speakerSource = if (state.activeConversationSpeakerIsA) effectiveSrc else state.targetLangCode
            val speakerTarget = if (state.activeConversationSpeakerIsA) state.targetLangCode else effectiveSrc
            _uiState.update { it.copy(isRecording = false) }

            viewModelScope.launch {
                val sttResult = pipeline.processRecordedAudioToTranscript(rawInput, speakerSource)
                _pipelineStage.value = pipeline.currentStage.value
                if (sttResult.success) {
                    executeConversationTranslationTurn(
                        isPersonA = state.activeConversationSpeakerIsA,
                        sourceLang = sttResult.language.ifBlank { speakerSource },
                        targetLang = speakerTarget,
                        transcript = sttResult.transcript,
                        confidence = sttResult.confidence ?: 0f
                    )
                } else {
                    _uiState.update { it.copy(activeError = sttResult.error) }
                }
            }
        } else {
            if (!audioProcessor.hasMicrophoneHardware()) {
                _uiState.update { it.copy(activeError = PipelineErrorType.NO_MICROPHONE_AVAILABLE) }
                return
            }
            if (ttsEngineInitialized) {
                ttsEngine.stopSpeaking()
            }
            _pipelineStage.value = PipelineStage.RECORDING
            _uiState.update {
                it.copy(
                    isRecording = true,
                    activeConversationSpeakerIsA = isPersonA,
                    recordingElapsedMs = 0L,
                    activeError = null
                )
            }
            audioProcessor.startRecording(viewModelScope) { _, elapsed ->
                _uiState.update { it.copy(recordingElapsedMs = elapsed) }
            }
        }
    }

    fun submitConversationTextTurn(isPersonA: Boolean, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val effectiveSrc = if (state.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
            state.detectedSourceLangCode ?: "ta"
        } else {
            state.sourceLangCode
        }
        val speakerSource = if (isPersonA) effectiveSrc else state.targetLangCode
        val speakerTarget = if (isPersonA) state.targetLangCode else effectiveSrc
        viewModelScope.launch {
            executeConversationTranslationTurn(
                isPersonA = isPersonA,
                sourceLang = speakerSource,
                targetLang = speakerTarget,
                transcript = trimmed,
                confidence = 1.0f
            )
        }
    }

    private suspend fun executeConversationTranslationTurn(
        isPersonA: Boolean,
        sourceLang: String,
        targetLang: String,
        transcript: String,
        confidence: Float
    ) {
        val settings = settingsState.value ?: AppSettingsEntity()
        val outcome = pipeline.translateAndSynthesize(
            confirmedText = transcript,
            sourceLang = sourceLang,
            targetLang = targetLang,
            autoPlay = settings.autoPlayTranslatedSpeech,
            speechSpeed = settings.speechSpeed
        )
        _pipelineStage.value = pipeline.currentStage.value
        val mtRes = outcome.translationResult
        if (mtRes != null && mtRes.success) {
            val tgtInfo = IndicLanguageCatalog.getLanguageInfo(targetLang)
            val turn = ConversationTurn(
                id = UUID.randomUUID().toString(),
                speakerLabel = if (isPersonA) "Person A" else "Person B",
                isPersonA = isPersonA,
                sourceLangCode = sourceLang,
                targetLangCode = targetLang,
                originalText = transcript,
                translatedText = mtRes.translatedText,
                timestamp = System.currentTimeMillis(),
                confidence = confidence,
                ttsAvailable = tgtInfo?.ttsModelId != null
            )
            _uiState.update {
                it.copy(
                    conversationTurns = it.conversationTurns + turn,
                    activeError = outcome.error
                )
            }
            repository.recordEncryptedHistory(
                sourceLang = sourceLang,
                targetLang = targetLang,
                originalText = transcript,
                translatedText = mtRes.translatedText,
                mode = "CONVERSATION",
                confidence = confidence,
                latencyMs = mtRes.latencyMs
            )
        } else {
            _uiState.update { it.copy(activeError = outcome.error) }
        }
    }

    fun clearConversation() {
        _uiState.update { it.copy(conversationTurns = emptyList()) }
    }

    fun updateTextModeInput(text: String) {
        _uiState.update { it.copy(textModeInput = text, activeError = null) }
    }

    fun runTextModeTranslation() {
        val state = _uiState.value
        val input = state.textModeInput.trim()
        if (input.isEmpty()) return

        val effectiveSource = if (state.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
            state.detectedSourceLangCode ?: "ta"
        } else {
            state.sourceLangCode
        }

        viewModelScope.launch {
            val settings = settingsState.value ?: AppSettingsEntity()
            val outcome = pipeline.translateAndSynthesize(
                confirmedText = input,
                sourceLang = effectiveSource,
                targetLang = state.targetLangCode,
                autoPlay = false,
                speechSpeed = settings.speechSpeed
            )
            _pipelineStage.value = pipeline.currentStage.value
            val mtRes = outcome.translationResult
            if (mtRes != null && mtRes.success) {
                _uiState.update {
                    it.copy(
                        textModeOutput = mtRes.translatedText,
                        textModeLatencyMs = mtRes.latencyMs,
                        activeError = outcome.error
                    )
                }
                repository.recordEncryptedHistory(
                    sourceLang = effectiveSource,
                    targetLang = state.targetLangCode,
                    originalText = input,
                    translatedText = mtRes.translatedText,
                    mode = "TEXT",
                    confidence = 1.0f,
                    latencyMs = mtRes.latencyMs
                )
            } else {
                _uiState.update {
                    it.copy(activeError = outcome.error ?: PipelineErrorType.TRANSLATION_FAILURE)
                }
            }
        }
    }

    fun clearTextModeInput() {
        _uiState.update { it.copy(textModeInput = "", textModeOutput = "", textModeLatencyMs = 0L) }
    }

    fun installLanguagePack(langCode: String) {
        viewModelScope.launch {
            val settings = settingsState.value ?: AppSettingsEntity()
            if (settings.downloadOverWifiOnly && !modelManager.isUnmeteredWifiConnected() && _uiState.value.isOnline) {
                _uiState.update {
                    it.copy(statusBannerMessage = "Wi-Fi is required to download packs (change in Settings)")
                }
                return@launch
            }
            val result = modelManager.installLanguagePacksWithProgress(langCode) { _, message ->
                _uiState.update { it.copy(statusBannerMessage = message) }
            }
            if (result.isFailure) {
                val errMsg = result.exceptionOrNull()?.message ?: ""
                val errorType = when {
                    errMsg.contains("LOW_STORAGE") -> PipelineErrorType.LOW_STORAGE
                    errMsg.contains("MODEL_INCOMPATIBLE_RUNTIME") -> PipelineErrorType.MODEL_INCOMPATIBLE_RUNTIME
                    else -> PipelineErrorType.DOWNLOAD_INTERRUPTED
                }
                _uiState.update { it.copy(activeError = errorType) }
            }
        }
    }

    fun removeLanguagePack(langCode: String) {
        viewModelScope.launch {
            modelManager.removeLanguagePacks(langCode)
            _uiState.update {
                it.copy(statusBannerMessage = "Removed offline pack for ${langCode.uppercase()}")
            }
        }
    }

    fun clearAllExtraPacks() {
        viewModelScope.launch {
            val removedBytes = modelManager.clearAllExtraLanguagePacks()
            _uiState.update {
                it.copy(
                    statusBannerMessage = if (removedBytes > 0L) {
                        "Cleared extra downloaded language packs"
                    } else {
                        "Only default Tamil & Bengali packs are installed"
                    }
                )
            }
        }
    }

    fun saveCurrentTranslationToPhrases(category: String = "General") {
        val state = _uiState.value
        val orig = state.recognizedTranscript.ifBlank { state.textModeInput }
        val trans = state.translatedSentence.ifBlank { state.textModeOutput }
        if (orig.isBlank() || trans.isBlank()) return

        viewModelScope.launch {
            repository.savePhrase(
                sourceLang = state.sourceLangCode,
                targetLang = state.targetLangCode,
                originalText = orig,
                translatedText = trans,
                category = category
            )
            _uiState.update { it.copy(statusBannerMessage = "Saved phrase to $category") }
        }
    }

    fun deleteSavedPhrase(id: String) {
        viewModelScope.launch {
            repository.deleteSavedPhrase(id)
        }
    }

    fun updateSavedSearchQuery(query: String) {
        _uiState.update { it.copy(savedSearchQuery = query) }
    }

    fun updateSavedCategory(category: String) {
        _uiState.update { it.copy(savedSelectedCategory = category) }
    }

    fun deleteHistoryItem(id: String) {
        viewModelScope.launch {
            repository.deleteHistoryItem(id)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.clearAllHistory()
            _uiState.update { it.copy(statusBannerMessage = "Local history cleared") }
        }
    }

    fun setOfflineOnlyMode(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(offlineOnlyMode = enabled) }
        }
    }

    fun setSpeechSpeed(speed: Float) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(speechSpeed = speed) }
        }
    }

    fun setAutoPlayTranslatedSpeech(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(autoPlayTranslatedSpeech = enabled) }
        }
    }

    fun setDownloadOverWifiOnly(enabled: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(downloadOverWifiOnly = enabled) }
        }
    }

    fun setConsentToCorrectionSync(consented: Boolean) {
        viewModelScope.launch {
            repository.updateSettings { it.copy(consentToCorrectionSync = consented) }
        }
    }

    fun setDarkTheme(enabled: Boolean) {
        _uiState.update { it.copy(isDarkTheme = enabled) }
    }

    fun toggleDarkTheme() {
        _uiState.update { it.copy(isDarkTheme = !it.isDarkTheme) }
    }

    fun setLowEndPerformanceMode(enabled: Boolean) {
        _uiState.update { it.copy(lowEndPerformanceMode = enabled) }
    }

    fun dismissError() {
        _uiState.update { it.copy(activeError = null, statusBannerMessage = null) }
    }

    fun getRuntimeTelemetry(): AiEvaluationSuite.RuntimeTelemetrySnapshot {
        val s = _uiState.value
        return AiEvaluationSuite.captureRealRuntimeMemory(
            lastSttMs = s.lastSttLatencyMs,
            lastMtMs = s.lastMtLatencyMs,
            lastTtsMs = s.lastTtsLatencyMs
        )
    }

    override fun onCleared() {
        super.onCleared()
        if (ttsEngineInitialized) {
            ttsEngine.shutdown()
        }
    }
}
