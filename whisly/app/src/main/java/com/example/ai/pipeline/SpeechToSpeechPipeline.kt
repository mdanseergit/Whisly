package com.example.ai.pipeline

import com.example.ai.translation.IndicTrans2Engine
import com.example.ai.tts.SystemTextToSpeechEngine
import com.example.domain.models.PipelineErrorType
import com.example.domain.models.PipelineStage
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class EndToEndTurnOutcome(
    val stage: PipelineStage,
    val speechResult: SpeechResult?,
    val translationResult: TranslationResult?,
    val audioResult: AudioResult?,
    val error: PipelineErrorType? = null
)

/**
 * Clean post-reset translation/output coordinator.
 *
 * Speech capture and bundled Whisper STT are owned by [com.example.ai.stt.WhislyOfflineSpeechRecognizer]
 * before this pipeline is called. This coordinator only performs typed ML Kit translation and
 * temporary platform TTS playback.
 */
class SpeechToSpeechPipeline(
    val mtEngine: IndicTrans2Engine,
    val ttsEngine: SystemTextToSpeechEngine,
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) {
    private val inferenceMutex = Mutex()

    private val _currentStage = MutableStateFlow(PipelineStage.IDLE)
    val currentStage: StateFlow<PipelineStage> = _currentStage.asStateFlow()

    fun updateStage(stage: PipelineStage) {
        _currentStage.value = stage
    }

    fun unloadModelsOnMemoryPressure() {
        mtEngine.unloadResidentModel()
        ttsEngine.clearAudioCache()
    }

    suspend fun translateAndSynthesize(
        confirmedText: String,
        sourceLang: String,
        targetLang: String,
        voiceId: String = "system_tts",
        autoPlay: Boolean = true,
        speechSpeed: Float = 1.0f
    ): EndToEndTurnOutcome = withContext(inferenceDispatcher) {
        inferenceMutex.withLock {
            coroutineContext.ensureActive()
            _currentStage.value = PipelineStage.NORMALIZING
            _currentStage.value = PipelineStage.MT_INFERENCE

            val mtResult = mtEngine.translate(confirmedText, sourceLang, targetLang)
            if (!mtResult.success) {
                _currentStage.value = PipelineStage.ERROR
                return@withLock EndToEndTurnOutcome(
                    stage = PipelineStage.ERROR,
                    speechResult = null,
                    translationResult = mtResult,
                    audioResult = null,
                    error = mtResult.error ?: PipelineErrorType.TRANSLATION_FAILURE
                )
            }

            coroutineContext.ensureActive()
            _currentStage.value = PipelineStage.TTS_INFERENCE
            val ttsResult = ttsEngine.synthesize(mtResult.translatedText, targetLang, voiceId)

            if (ttsResult.success && autoPlay) {
                coroutineContext.ensureActive()
                _currentStage.value = PipelineStage.PLAYING_AUDIO
                val played = ttsEngine.speakOutLoud(
                    text = mtResult.translatedText,
                    languageCode = targetLang,
                    speechSpeed = speechSpeed
                )
                if (!played) {
                    _currentStage.value = PipelineStage.ERROR
                    return@withLock EndToEndTurnOutcome(
                        stage = PipelineStage.ERROR,
                        speechResult = null,
                        translationResult = mtResult,
                        audioResult = ttsResult,
                        error = PipelineErrorType.TTS_FAILURE
                    )
                }
            }

            _currentStage.value = PipelineStage.COMPLETED
            EndToEndTurnOutcome(
                stage = PipelineStage.COMPLETED,
                speechResult = null,
                translationResult = mtResult,
                audioResult = ttsResult,
                error = if (ttsResult.success) null else (ttsResult.error ?: PipelineErrorType.TTS_FAILURE)
            )
        }
    }
}
