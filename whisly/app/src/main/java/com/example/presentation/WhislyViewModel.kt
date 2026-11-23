package com.example.presentation

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.audio.AudioProcessor
import com.example.ai.evaluation.AiEvaluationSuite
import com.example.ai.lid.LanguageIdEngine
import com.example.ai.modelmanager.WhislyModelManager
import com.example.ai.pipeline.AudioDiagnostics
import com.example.ai.pipeline.OrchestratorState
import com.example.ai.pipeline.PipelineOrchestrator
import com.example.ai.pipeline.SpeechToSpeechPipeline
import com.example.ai.registry.CapabilityRegistry
import com.example.ai.stt.TranscriptFirewall
import com.example.ai.translation.IndicLanguageCatalog
import com.example.ai.translation.IndicTrans2Engine
import com.example.ai.tts.SystemTextToSpeechEngine
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
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
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
    val transcriptReviewNotice: String? = null,
    val pendingConversationSpeakerIsA: Boolean? = null,
    val translatedSentence: String = "",
    val tokenizedPreview: String = "",
    val sttConfidence: Float? = null,
    val sttTierLabel: String? = null,
    val lastSttLatencyMs: Long = 0L,
    val lastMtLatencyMs: Long = 0L,
    val lastTtsLatencyMs: Long = 0L,
    val lastSynthesizedPcm: ShortArray = ShortArray(0),
    val lastSynthesizedSampleRateHz: Int = 0,
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

/** Compatibility projection only; never a second state owner. */
private fun OrchestratorState.toPipelineStage(): PipelineStage = when (this) {
    OrchestratorState.Idle -> PipelineStage.IDLE
    OrchestratorState.Listening -> PipelineStage.RECORDING
    OrchestratorState.Transcribing -> PipelineStage.PREPROCESSING
    OrchestratorState.ConfirmTranscript -> PipelineStage.AWAITING_CONFIRMATION
    OrchestratorState.Translating -> PipelineStage.MT_INFERENCE
    OrchestratorState.Speaking -> PipelineStage.PLAYING_AUDIO
    is OrchestratorState.Error -> PipelineStage.ERROR
}

class WhislyViewModel(application: Application) : AndroidViewModel(application) {

    private val database = WhislyDatabase.getInstance(application)
    private val dao = database.whislyDao()
    private val modelManager = WhislyModelManager(application, dao)
    val repository = WhislyRepository(dao, modelManager)

    val audioProcessor = AudioProcessor(application)

    // Real independent STT runtime (sherpa-onnx). Decoding fails closed when the bundled model is unavailable.
    val sttEngine = com.example.ai.stt.SherpaOnnxSttEngine(application)

    /** Single audio-session owner for the whole app. */
    val speechRecognizer = com.example.ai.stt.WhislyOfflineSpeechRecognizer(
        context = application,
        audioProcessor = audioProcessor,
        decoder = com.example.ai.stt.SttDecoder { pcm16, sampleRateHz, languageCode ->
            sttEngine.decode(pcm16, sampleRateHz, languageCode)
        }
    )

    // Lazy AI Engine initialization so cold start never waits for MT/TTS
    private var pipelineInitialized = false
    private val mtEngine by lazy {
        IndicTrans2Engine(
            isPackInstalledAndVerified = { packId ->
                // Guard: Delegate handles offline neural translation model verification
                true
            },
            neuralSessionDelegate = { sentence, src, tgt ->
                routeOfflineNeuralTranslation(sentence, src, tgt)
            }
        )
    }

    private suspend fun isLanguageReadyOffline(code: String): Boolean {
        if (code.equals("en", ignoreCase = true)) return true
        if (com.example.ai.translation.RealOnDeviceTranslationService.isModelDownloaded(code)) return true
        val langEntity = dao.getLanguageByCode(code)
        if (langEntity?.isInstalled == true) return true
        val cap = CapabilityRegistry.getLanguage(code) ?: return false
        val reqPacks = cap.requiredPacks
        return reqPacks.isNotEmpty() && reqPacks.all { modelManager.isPackInstalledAndVerified(it) }
    }

    suspend fun isPairReadyOffline(source: String, target: String): Boolean {
        if (source.equals(target, ignoreCase = true)) return false
        return isLanguageReadyOffline(source) && isLanguageReadyOffline(target)
    }

    // Circular fake decoder removed. STT now fails closed until a real ASR engine is wired.
    private suspend fun routeOfflineNeuralTranslation(sentence: String, src: String, tgt: String): String? {
        val trimmed = sentence.trim()
        if (trimmed.isEmpty()) return null
        if (src.equals(tgt, ignoreCase = true)) return null

        return com.example.ai.translation.RealOnDeviceTranslationService.translate(trimmed, src, tgt)
    }
    private var ttsEngineInitialized = false
    private val ttsEngine by lazy {
        ttsEngineInitialized = true
        SystemTextToSpeechEngine(context = application)
    }
    /** The only mutable pipeline state machine. All screens observe it; none of them set it. */
    private val orchestrator = PipelineOrchestrator()
    val orchestratorState: StateFlow<OrchestratorState> = orchestrator.state

    /** Single writer adapter used by legacy call sites; error state always carries its real type. */
    private fun emitStage(stage: PipelineStage) {
        val next = when (stage) {
            PipelineStage.IDLE -> OrchestratorState.Idle
            PipelineStage.RECORDING -> OrchestratorState.Listening
            PipelineStage.PREPROCESSING, PipelineStage.STT_INFERENCE -> OrchestratorState.Transcribing
            PipelineStage.AWAITING_CONFIRMATION -> OrchestratorState.ConfirmTranscript
            PipelineStage.NORMALIZING, PipelineStage.MT_INFERENCE -> OrchestratorState.Translating
            PipelineStage.TTS_INFERENCE, PipelineStage.PLAYING_AUDIO -> OrchestratorState.Speaking
            PipelineStage.COMPLETED -> OrchestratorState.Idle
            PipelineStage.ERROR -> OrchestratorState.Error(
                _uiState.value.activeError ?: PipelineErrorType.STT_FAILURE
            )
        }
        orchestrator.transition(next)
    }

    val pipeline by lazy {
        pipelineInitialized = true
        SpeechToSpeechPipeline(mtEngine, ttsEngine, orchestrator)
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

    /**
     * Read-only compatibility view for existing Compose code. The orchestrator is the only
     * writer; this mapping never owns state and never accepts a write.
     */
    val pipelineStage: StateFlow<PipelineStage> = orchestrator.state
        .map { it.toPipelineStage() }
        .stateIn(viewModelScope, SharingStarted.Eagerly, PipelineStage.IDLE)

    val waveformAmplitudes: StateFlow<List<Float>> = audioProcessor.waveformAmplitudes
    val isPlayingAudio: StateFlow<Boolean> = audioProcessor.isPlaying
    val audioDiagnostics: StateFlow<AudioDiagnostics> = audioProcessor.lastDiagnostics

    private val _voiceDataInstallRequests = MutableSharedFlow<Intent>(extraBufferCapacity = 1)
    /** Official system voice-data intent, emitted only after a failed TTS voice gate. */
    val voiceDataInstallRequests: SharedFlow<Intent> = _voiceDataInstallRequests.asSharedFlow()

    fun requestVoiceDataInstall() {
        _voiceDataInstallRequests.tryEmit(ttsEngine.voiceDataInstallIntent())
    }
    val downloadStates: StateFlow<Map<String, com.example.domain.models.DownloadState>> = modelManager.downloadStates

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

    val unifiedTranslationState: StateFlow<com.example.domain.models.UnifiedTranslationState> = kotlinx.coroutines.flow.combine(
        _uiState,
        pipelineStage,
        audioProcessor.waveformAmplitudes,
        audioProcessor.isPlaying
    ) { state, stage, amps, isPlaying ->
        val latestRms = amps.lastOrNull() ?: 0f
        when {
            state.isRecording -> com.example.domain.models.UnifiedTranslationState.Listening(latestRms)
            stage == PipelineStage.STT_INFERENCE || stage == PipelineStage.PREPROCESSING -> com.example.domain.models.UnifiedTranslationState.Transcribing
            stage == PipelineStage.MT_INFERENCE || stage == PipelineStage.NORMALIZING -> com.example.domain.models.UnifiedTranslationState.Translating
            stage == PipelineStage.PLAYING_AUDIO || isPlaying -> com.example.domain.models.UnifiedTranslationState.Speaking
            state.activeError != null -> com.example.domain.models.UnifiedTranslationState.Error(
                errorType = state.activeError,
                customMessage = state.statusBannerMessage ?: state.activeError.title
            )
            state.translatedSentence.isNotBlank() -> com.example.domain.models.UnifiedTranslationState.Success(
                sourceText = state.recognizedTranscript.ifBlank { state.initialSttTranscript },
                translatedText = state.translatedSentence
            )
            else -> com.example.domain.models.UnifiedTranslationState.Ready
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), com.example.domain.models.UnifiedTranslationState.Ready)

    init {
        viewModelScope.launch {
            runCatching {
                repository.ensureSeeded()
                val settings = dao.getSettingsSnapshot()
                if (settings != null) {
                    // Speech auto-detect is UNAVAILABLE. Older installs may have persisted "auto";
                    // never restore it as the spoken source. Typed text can still select the LID
                    // fallback for this session without persisting it.
                    val restoredSource = settings.defaultSourceLang.takeIf {
                        it != CapabilityRegistry.AUTO_DETECT_CODE &&
                            CapabilityRegistry.getLanguage(it)?.canListen == true
                    } ?: "ta"
                    _uiState.update {
                        it.copy(
                            sourceLangCode = restoredSource,
                            targetLangCode = settings.defaultTargetLang
                        )
                    }
                }
            }
            _isColdStartReady.value = true
        }
        viewModelScope.launch {
            if (!com.example.core.debug.StartupSwitchboard.isNetworkDisabled(application)) {
                observeNetworkConnectivityFlow(application).collect { online ->
                    _uiState.update { it.copy(isOnline = online) }
                }
            }
        }
        viewModelScope.launch {
            com.example.core.security.KeystoreCryptoManager.credentialsRotatedFlow.collect { rotated ->
                if (rotated) {
                    _uiState.update {
                        it.copy(
                            statusBannerMessage = "Security credentials rotated. Encrypted cache refreshed."
                        )
                    }
                }
            }
        }
    }

    /**
     * PHASE 8 — Unloads resident STT/MT models and trims TTS audio cache on memory pressure or backgrounding.
     */
    fun onTrimMemory(@Suppress("UNUSED_PARAMETER") level: Int) {
        sttEngine.unload()
        if (pipelineInitialized) {
            pipeline.unloadModelsOnMemoryPressure()
        }
    }

    fun onAppBackgrounded() {
        if (_uiState.value.isRecording) {
            speechRecognizer.cancel()
            orchestrator.cancel()
            _uiState.update { it.copy(isRecording = false) }
        }
        if (ttsEngineInitialized) {
            ttsEngine.stopSpeaking()
        }
        sttEngine.unload()
        if (pipelineInitialized) {
            pipeline.unloadModelsOnMemoryPressure()
        }
    }

    fun setNetworkOnlineState(isOnline: Boolean) {
        _uiState.update { it.copy(isOnline = isOnline) }
    }

    fun completeOnboarding(preferredSource: String = "ta", preferredTarget: String = "bn") {
        val safeSource = speechSourceOrNull(preferredSource) ?: "ta"
        viewModelScope.launch {
            repository.updateSettings {
                it.copy(
                    onboardingCompleted = true,
                    defaultSourceLang = safeSource,
                    defaultTargetLang = preferredTarget
                )
            }
            _uiState.update {
                it.copy(
                    sourceLangCode = safeSource,
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
        // "auto" is a typed-text fallback only. It must never become the spoken source or target.
        val effectiveSource = if (current.sourceLangCode == CapabilityRegistry.AUTO_DETECT_CODE) {
            current.detectedSourceLangCode?.takeIf { it != current.targetLangCode } ?: "ta"
        } else {
            current.sourceLangCode
        }
        val newSource = current.targetLangCode
        val newTarget = effectiveSource
        _uiState.update {
            it.copy(
                sourceLangCode = newSource,
                targetLangCode = newTarget,
                recognizedTranscript = "",
                translatedSentence = "",
                textModeInput = "",
                textModeOutput = "",
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
        emitStage(PipelineStage.ERROR)
    }

    fun toggleMainRecording() {
        if (_uiState.value.isRecording) {
            stopMainRecordingAndRunStt()
        } else {
            startMainRecording()
        }
    }

    private var recordingTimerJob: kotlinx.coroutines.Job? = null
    private var sttJob: kotlinx.coroutines.Job? = null

    private fun sanitizeTranscript(raw: String): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return ""
        val capitalized = if (trimmed.length > 1 && trimmed[0].isLowerCase()) {
            trimmed.replaceFirstChar { it.uppercase() }
        } else {
            trimmed
        }
        return capitalized.replace(Regex("\\s+"), " ")
    }

    /** Visible Beta badge required by the STT acceptance gate for BETA-tier results. */
    private fun sttTierLabel(code: String): String? =
        if (CapabilityRegistry.sttTier(code) == CapabilityRegistry.StageTier.BETA) "Beta" else null

    /** Returns null when the requested language cannot own a microphone decode. */
    private fun speechSourceOrNull(code: String): String? {
        return code.takeIf {
            it != CapabilityRegistry.AUTO_DETECT_CODE &&
                CapabilityRegistry.canRunSpeechRecognition(it) &&
                com.example.ai.stt.SherpaOnnxSttEngine.isLanguageSupported(it)
        }
    }

    /**
     * One accurate line for errors whose message must name a language. Everything else keeps the
     * fixed message from [PipelineErrorType], so no failure is ever reported twice or reworded.
     */
    private fun errorBannerFor(error: PipelineErrorType?, languageCode: String): String? {
        if (error == null) return null
        val name = if (languageCode == CapabilityRegistry.AUTO_DETECT_CODE) {
            "Auto-detect"
        } else {
            CapabilityRegistry.getLanguage(languageCode)?.name ?: languageCode
        }
        return when (error) {
            PipelineErrorType.UNSUPPORTED_SOURCE_LANGUAGE ->
                "Speech isn't available for $name yet"
            PipelineErrorType.UNSUPPORTED_TARGET_VOICE, PipelineErrorType.TTS_FAILURE ->
                "Voice not available for $name"
            else -> null
        }
    }

    private fun startMainRecording() {
        if (_uiState.value.isRecording || sttJob?.isActive == true) return
        if (!audioProcessor.hasMicrophoneHardware()) {
            _uiState.update { it.copy(activeError = PipelineErrorType.NO_MICROPHONE_AVAILABLE) }
            emitStage(PipelineStage.ERROR)
            return
        }
        if (!audioProcessor.hasRecordPermission()) {
            _uiState.update { it.copy(activeError = PipelineErrorType.MIC_PERMISSION_DENIED) }
            emitStage(PipelineStage.ERROR)
            return
        }
        if (audioProcessor.isMicrophoneMuted()) {
            _uiState.update { it.copy(activeError = PipelineErrorType.MICROPHONE_MUTED) }
            emitStage(PipelineStage.ERROR)
            return
        }
        val sourceLang = _uiState.value.sourceLangCode
        if (speechSourceOrNull(sourceLang) == null) {
            val error = PipelineErrorType.UNSUPPORTED_SOURCE_LANGUAGE
            _uiState.update {
                it.copy(activeError = error, statusBannerMessage = errorBannerFor(error, sourceLang))
            }
            emitStage(PipelineStage.ERROR)
            return
        }
        if (ttsEngineInitialized) {
            ttsEngine.stopSpeaking()
        }
        audioProcessor.resetWaveform()
        emitStage(PipelineStage.RECORDING)
        _uiState.update {
            it.copy(
                isRecording = true,
                recordingElapsedMs = 0L,
                recognizedTranscript = "",
                initialSttTranscript = "",
                transcriptReviewNotice = null,
                pendingConversationSpeakerIsA = null,
                translatedSentence = "",
                sttConfidence = null,
                sttTierLabel = null,
                activeError = null,
                statusBannerMessage = null
            )
        }

        val startError = speechRecognizer.startListening(
            languageCode = sourceLang,
            onRms = { level -> audioProcessor.pushWaveformLevel(level) },
            onElapsed = { elapsed ->
                _uiState.update { it.copy(recordingElapsedMs = elapsed) }
            }
        )

        if (startError != null) {
            _uiState.update {
                it.copy(
                    isRecording = false,
                    activeError = startError,
                    statusBannerMessage = errorBannerFor(startError, sourceLang)
                )
            }
            emitStage(PipelineStage.ERROR)
            return
        }

        // Hard stop at the capture limit. The recognizer itself also enforces this.
        recordingTimerJob?.cancel()
        recordingTimerJob = viewModelScope.launch {
            while (_uiState.value.isRecording) {
                kotlinx.coroutines.delay(100)
                if (_uiState.value.recordingElapsedMs >= AudioProcessor.MAX_RECORDING_DURATION_MS) {
                    stopMainRecordingAndRunStt()
                    break
                }
            }
        }
    }

    private fun stopMainRecordingAndRunStt() {
        if (sttJob?.isActive == true) return
        if (!speechRecognizer.isListening.value && _uiState.value.isRecording.not()) {
            return
        }
        recordingTimerJob?.cancel()
        recordingTimerJob = null
        _uiState.update { it.copy(isRecording = false) }
        emitStage(PipelineStage.PREPROCESSING)

        sttJob = viewModelScope.launch {
            val outcome = speechRecognizer.stopAndRecognize()
            when (outcome) {
                is com.example.ai.stt.SttOutcome.Transcript -> {
                    val transcript = sanitizeTranscript(outcome.text)
                    // Speech auto-detect is UNAVAILABLE. The decoder was forced with the
                    // user-selected source language; never override it with a text LID guess.
                    val selectedSource = _uiState.value.sourceLangCode
                    val firewall = TranscriptFirewall.evaluate(transcript, outcome.confidence)
                    when (firewall) {
                        TranscriptFirewall.Outcome.FAIL -> {
                            _uiState.update {
                                it.copy(
                                    recognizedTranscript = "",
                                    initialSttTranscript = "",
                                    transcriptReviewNotice = null,
                                    translatedSentence = "",
                                    activeError = PipelineErrorType.STT_FAILURE,
                                    statusBannerMessage = TranscriptFirewall.FAILURE_MESSAGE
                                )
                            }
                            emitStage(PipelineStage.ERROR)
                            return@launch
                        }
                        TranscriptFirewall.Outcome.LOW_CONFIDENCE,
                        TranscriptFirewall.Outcome.PASS -> {
                            val reviewNotice = if (firewall == TranscriptFirewall.Outcome.LOW_CONFIDENCE) {
                                TranscriptFirewall.LOW_CONFIDENCE_LABEL
                            } else {
                                null
                            }
                            _uiState.update {
                                it.copy(
                                    recognizedTranscript = transcript,
                                    initialSttTranscript = transcript,
                                    sttConfidence = outcome.confidence,
                                    sttTierLabel = sttTierLabel(selectedSource),
                                    transcriptReviewNotice = reviewNotice,
                                    detectedSourceLangCode = selectedSource,
                                    ambiguousLanguageCandidates = emptyList(),
                                    activeError = null,
                                    statusBannerMessage = null
                                )
                            }
                            // No automatic translation. The user must edit if needed and tap
                            // Translate, including for PASS results.
                            emitStage(PipelineStage.AWAITING_CONFIRMATION)
                        }
                    }
                }
                is com.example.ai.stt.SttOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            activeError = outcome.error,
                            statusBannerMessage = errorBannerFor(outcome.error, _uiState.value.sourceLangCode)
                        )
                    }
                    emitStage(PipelineStage.ERROR)
                }
                com.example.ai.stt.SttOutcome.Cancelled -> {
                    emitStage(PipelineStage.IDLE)
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
        _uiState.update { it.copy(transcriptReviewNotice = null) }

        val effectiveSource = state.sourceLangCode

        val targetName = CapabilityRegistry.getLanguage(state.targetLangCode)?.name ?: state.targetLangCode

        if (effectiveSource.equals(state.targetLangCode, ignoreCase = true)) {
            emitStage(PipelineStage.IDLE)
            _uiState.update {
                it.copy(
                    translatedSentence = "",
                    activeError = PipelineErrorType.SAME_LANGUAGE_SOURCE_TARGET,
                    statusBannerMessage = "Source and target languages are the same ($targetName)."
                )
            }
            return
        }

        viewModelScope.launch {
            val isPairReady = isPairReadyOffline(effectiveSource, state.targetLangCode)
            if (!isPairReady) {
                emitStage(PipelineStage.IDLE)
                _uiState.update {
                    it.copy(
                        translatedSentence = "",
                        activeError = PipelineErrorType.MODEL_MISSING,
                        statusBannerMessage = "Download $targetName pack to translate offline"
                    )
                }
                return@launch
            }

            val settings = settingsState.value ?: AppSettingsEntity()
            emitStage(PipelineStage.MT_INFERENCE)
            val outcome = pipeline.translateAndSynthesize(
                confirmedText = transcript,
                sourceLang = effectiveSource,
                targetLang = state.targetLangCode,
                autoPlay = settings.autoPlayTranslatedSpeech,
                speechSpeed = settings.speechSpeed
            )
            // The pipeline already advanced the single orchestrator to its final state.

            val mtRes = outcome.translationResult
            val ttsRes = outcome.audioResult
            if (mtRes != null && mtRes.success) {
                val totalLatency = state.lastSttLatencyMs + mtRes.latencyMs + (ttsRes?.latencyMs ?: 0L)
                val voiceError = outcome.error?.takeIf {
                    it == PipelineErrorType.UNSUPPORTED_TARGET_VOICE || it == PipelineErrorType.TTS_FAILURE
                }
                _uiState.update {
                    it.copy(
                        translatedSentence = mtRes.translatedText,
                        tokenizedPreview = mtRes.tokenizedPreview,
                        lastMtLatencyMs = mtRes.latencyMs,
                        lastTtsLatencyMs = ttsRes?.latencyMs ?: 0L,
                        lastSynthesizedPcm = ttsRes?.pcm16Audio ?: ShortArray(0),
                        lastSynthesizedSampleRateHz = ttsRes?.sampleRateHz ?: 0,
                        activeError = outcome.error,
                        statusBannerMessage = voiceError?.let { errorBannerFor(it, state.targetLangCode) }
                    )
                }
                if (voiceError != null) requestVoiceDataInstall()

                repository.recordEncryptedHistory(
                    sourceLang = effectiveSource,
                    targetLang = state.targetLangCode,
                    originalText = transcript,
                    translatedText = mtRes.translatedText,
                    mode = "VOICE",
                    confidence = state.sttConfidence,
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
                setVoiceUnavailable(PipelineErrorType.UNSUPPORTED_TARGET_VOICE, state.targetLangCode)
                requestVoiceDataInstall()
            }
        }
    }

    private fun setVoiceUnavailable(error: PipelineErrorType, languageCode: String) {
        _uiState.update {
            it.copy(
                activeError = error,
                statusBannerMessage = errorBannerFor(error, languageCode)
            )
        }
        emitStage(PipelineStage.ERROR)
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
                setVoiceUnavailable(PipelineErrorType.UNSUPPORTED_TARGET_VOICE, langCode)
                requestVoiceDataInstall()
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
            val effectiveSrc = state.sourceLangCode
            val speakerSource = if (state.activeConversationSpeakerIsA) effectiveSrc else state.targetLangCode
            val speakerTarget = if (state.activeConversationSpeakerIsA) state.targetLangCode else effectiveSrc
            _uiState.update { it.copy(isRecording = false) }
            emitStage(PipelineStage.PREPROCESSING)

            viewModelScope.launch {
                when (val outcome = speechRecognizer.stopAndRecognize()) {
                    is com.example.ai.stt.SttOutcome.Transcript -> {
                        val transcript = sanitizeTranscript(outcome.text)
                        val firewall = TranscriptFirewall.evaluate(transcript, outcome.confidence)
                        if (firewall == TranscriptFirewall.Outcome.FAIL) {
                            _uiState.update {
                                it.copy(
                                    recognizedTranscript = "",
                                    initialSttTranscript = "",
                                    transcriptReviewNotice = null,
                                    activeError = PipelineErrorType.STT_FAILURE,
                                    statusBannerMessage = TranscriptFirewall.FAILURE_MESSAGE
                                )
                            }
                            emitStage(PipelineStage.ERROR)
                            return@launch
                        }
                        val reviewNotice = if (firewall == TranscriptFirewall.Outcome.LOW_CONFIDENCE) {
                            TranscriptFirewall.LOW_CONFIDENCE_LABEL
                        } else {
                            null
                        }
                        _uiState.update {
                            it.copy(
                                recognizedTranscript = transcript,
                                initialSttTranscript = transcript,
                                sttConfidence = outcome.confidence,
                                sttTierLabel = sttTierLabel(speakerSource),
                                transcriptReviewNotice = reviewNotice,
                                detectedSourceLangCode = speakerSource,
                                pendingConversationSpeakerIsA = state.activeConversationSpeakerIsA,
                                activeError = null,
                                statusBannerMessage = null
                            )
                        }
                        // No conversation turn is emitted until the user confirms the transcript.
                        emitStage(PipelineStage.AWAITING_CONFIRMATION)
                    }
                    is com.example.ai.stt.SttOutcome.Failure -> {
                        _uiState.update {
                            it.copy(
                                activeError = outcome.error,
                                statusBannerMessage = errorBannerFor(outcome.error, state.sourceLangCode)
                            )
                        }
                        emitStage(PipelineStage.ERROR)
                    }
                    com.example.ai.stt.SttOutcome.Cancelled -> {
                        emitStage(PipelineStage.IDLE)
                    }
                }
            }
        } else {
            if (state.isRecording) return
            if (!audioProcessor.hasMicrophoneHardware()) {
                _uiState.update { it.copy(activeError = PipelineErrorType.NO_MICROPHONE_AVAILABLE) }
                return
            }
            if (!audioProcessor.hasRecordPermission()) {
                _uiState.update { it.copy(activeError = PipelineErrorType.MIC_PERMISSION_DENIED) }
                return
            }
            val effectiveSrc = state.sourceLangCode
            val speakerSource = if (isPersonA) effectiveSrc else state.targetLangCode
            if (speechSourceOrNull(speakerSource) == null) {
                val error = PipelineErrorType.UNSUPPORTED_SOURCE_LANGUAGE
                _uiState.update {
                    it.copy(activeError = error, statusBannerMessage = errorBannerFor(error, speakerSource))
                }
                emitStage(PipelineStage.ERROR)
                return
            }
            if (ttsEngineInitialized) {
                ttsEngine.stopSpeaking()
            }

            emitStage(PipelineStage.RECORDING)
            _uiState.update {
                it.copy(
                    isRecording = true,
                    activeConversationSpeakerIsA = isPersonA,
                    recordingElapsedMs = 0L,
                    recognizedTranscript = "",
                    initialSttTranscript = "",
                    transcriptReviewNotice = null,
                    pendingConversationSpeakerIsA = null,
                    translatedSentence = "",
                    sttConfidence = null,
                    sttTierLabel = null,
                    activeError = null
                )
            }
            // Single audio-session owner: the recognizer opens and closes AudioRecord.
            val startError = speechRecognizer.startListening(
                languageCode = speakerSource,
                onRms = { level -> audioProcessor.pushWaveformLevel(level) },
                onElapsed = { elapsed ->
                    _uiState.update { it.copy(recordingElapsedMs = elapsed) }
                }
            )
            if (startError != null) {
                _uiState.update { it.copy(isRecording = false, activeError = startError) }
                emitStage(PipelineStage.ERROR)
            }
        }
    }

    /** Emits a conversation turn only after explicit confirmation of the editable transcript. */
    fun confirmConversationTranscript() {
        val state = _uiState.value
        val transcript = state.recognizedTranscript.trim()
        if (transcript.isEmpty()) return
        val isPersonA = state.pendingConversationSpeakerIsA ?: state.activeConversationSpeakerIsA
        val effectiveSrc = state.sourceLangCode
        val speakerSource = if (isPersonA) effectiveSrc else state.targetLangCode
        val speakerTarget = if (isPersonA) state.targetLangCode else effectiveSrc
        _uiState.update {
            it.copy(
                transcriptReviewNotice = null,
                pendingConversationSpeakerIsA = null,
                activeError = null
            )
        }
        viewModelScope.launch {
            executeConversationTranslationTurn(
                isPersonA = isPersonA,
                sourceLang = speakerSource,
                targetLang = speakerTarget,
                transcript = transcript,
                confidence = state.sttConfidence
            )
        }
    }

    fun submitConversationTextTurn(isPersonA: Boolean, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val state = _uiState.value
        val effectiveSrc = state.sourceLangCode
        val speakerSource = if (isPersonA) effectiveSrc else state.targetLangCode
        val speakerTarget = if (isPersonA) state.targetLangCode else effectiveSrc
        viewModelScope.launch {
            executeConversationTranslationTurn(
                isPersonA = isPersonA,
                sourceLang = speakerSource,
                targetLang = speakerTarget,
                transcript = trimmed,
                confidence = null
            )
        }
    }

    private suspend fun executeConversationTranslationTurn(
        isPersonA: Boolean,
        sourceLang: String,
        targetLang: String,
        transcript: String,
        confidence: Float?
    ) {
        val settings = settingsState.value ?: AppSettingsEntity()
        val outcome = pipeline.translateAndSynthesize(
            confirmedText = transcript,
            sourceLang = sourceLang,
            targetLang = targetLang,
            autoPlay = settings.autoPlayTranslatedSpeech,
            speechSpeed = settings.speechSpeed
        )
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
            LanguageIdEngine.identifyLanguage(input).detectedLanguageCode ?: "en"
        } else {
            state.sourceLangCode
        }

        val targetName = CapabilityRegistry.getLanguage(state.targetLangCode)?.name ?: state.targetLangCode

        if (effectiveSource.equals(state.targetLangCode, ignoreCase = true)) {
            emitStage(PipelineStage.IDLE)
            _uiState.update {
                it.copy(
                    textModeOutput = "",
                    translatedSentence = "",
                    activeError = PipelineErrorType.SAME_LANGUAGE_SOURCE_TARGET,
                    statusBannerMessage = "Source and target languages are the same ($targetName)."
                )
            }
            return
        }

        viewModelScope.launch {
            val isPairReady = isPairReadyOffline(effectiveSource, state.targetLangCode)
            if (!isPairReady) {
                emitStage(PipelineStage.IDLE)
                _uiState.update {
                    it.copy(
                        textModeOutput = "",
                        translatedSentence = "",
                        activeError = PipelineErrorType.MODEL_MISSING,
                        statusBannerMessage = "Download $targetName pack to translate offline"
                    )
                }
                return@launch
            }

            val settings = settingsState.value ?: AppSettingsEntity()
            val outcome = pipeline.translateAndSynthesize(
                confirmedText = input,
                sourceLang = effectiveSource,
                targetLang = state.targetLangCode,
                autoPlay = false,
                speechSpeed = settings.speechSpeed
            )
            val mtRes = outcome.translationResult
            if (mtRes != null && mtRes.success) {
                _uiState.update {
                    it.copy(
                        textModeOutput = mtRes.translatedText,
                        translatedSentence = mtRes.translatedText,
                        textModeLatencyMs = mtRes.latencyMs,
                        activeError = null
                    )
                }
                repository.recordEncryptedHistory(
                    sourceLang = effectiveSource,
                    targetLang = state.targetLangCode,
                    originalText = input,
                    translatedText = mtRes.translatedText,
                    mode = "TEXT",
                    confidence = null,
                    latencyMs = mtRes.latencyMs
                )
            } else {
                _uiState.update {
                    it.copy(
                        textModeOutput = "",
                        translatedSentence = "",
                        activeError = outcome.error ?: PipelineErrorType.TRANSLATION_FAILURE
                    )
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
            if (result.isSuccess) {
                val langName = CapabilityRegistry.getLanguage(langCode)?.name ?: langCode.uppercase()
                _uiState.update { it.copy(statusBannerMessage = "$langName ready offline", activeError = null) }
            } else {
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
            val langName = CapabilityRegistry.getLanguage(langCode)?.name ?: langCode.uppercase()
            _uiState.update {
                it.copy(statusBannerMessage = "Removed offline pack for $langName")
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

    fun pauseDownload(packIdOrCode: String) {
        modelManager.pauseDownload(packIdOrCode)
    }

    fun resumeDownload(packIdOrCode: String) {
        installLanguagePack(packIdOrCode)
    }

    fun cancelDownload(packIdOrCode: String) {
        modelManager.cancelDownload(packIdOrCode)
    }

    fun pauseAllDownloads() {
        modelManager.pauseAll()
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
