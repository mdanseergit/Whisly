package com.example.ai.pipeline

import com.example.ai.translation.IndicTrans2Engine
import com.example.ai.tts.SystemTextToSpeechEngine
import com.example.domain.models.PipelineErrorType
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

data class EndToEndTurnOutcome(
    val state: OrchestratorState,
    val speechResult: SpeechResult?,
    val translationResult: TranslationResult?,
    val audioResult: AudioResult?,
    val error: PipelineErrorType? = null
)

/**
 * Typed translation and temporary platform TTS playback only.
 *
 * Speech capture and bundled Whisper STT are owned by
 * [com.example.ai.stt.WhislyOfflineSpeechRecognizer] before this pipeline is called. This class
 * owns no stage of its own: every transition goes to the single [PipelineOrchestrator]. A failed
 * MT/TTS stage returns the successful translation result so the caller keeps it.
 */
class SpeechToSpeechPipeline(
    val mtEngine: IndicTrans2Engine,
    val ttsEngine: SystemTextToSpeechEngine,
    private val orchestrator: PipelineOrchestrator,
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) {
    private val inferenceMutex = Mutex()

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
            orchestrator.transition(OrchestratorState.Translating)

            val mtResult = mtEngine.translate(confirmedText, sourceLang, targetLang)
            if (!mtResult.success) {
                val error = mtResult.error ?: PipelineErrorType.TRANSLATION_FAILURE
                orchestrator.transition(OrchestratorState.Error(error))
                return@withLock EndToEndTurnOutcome(
                    state = OrchestratorState.Error(error),
                    speechResult = null,
                    translationResult = mtResult,
                    audioResult = null,
                    error = error
                )
            }

            coroutineContext.ensureActive()
            orchestrator.transition(OrchestratorState.Speaking)
            val ttsResult = ttsEngine.synthesize(mtResult.translatedText, targetLang, voiceId)

            if (ttsResult.success && autoPlay) {
                coroutineContext.ensureActive()
                val played = ttsEngine.speakOutLoud(
                    text = mtResult.translatedText,
                    languageCode = targetLang,
                    speechSpeed = speechSpeed
                )
                if (!played) {
                    orchestrator.transition(OrchestratorState.Error(PipelineErrorType.TTS_FAILURE))
                    return@withLock EndToEndTurnOutcome(
                        state = OrchestratorState.Error(PipelineErrorType.TTS_FAILURE),
                        speechResult = null,
                        translationResult = mtResult,
                        audioResult = ttsResult,
                        error = PipelineErrorType.TTS_FAILURE
                    )
                }
            }

            orchestrator.transition(OrchestratorState.Idle)
            EndToEndTurnOutcome(
                state = OrchestratorState.Idle,
                speechResult = null,
                translationResult = mtResult,
                audioResult = ttsResult,
                error = if (ttsResult.success) null else (ttsResult.error ?: PipelineErrorType.TTS_FAILURE)
            )
        }
    }
}
