package com.example.ai.pipeline

import com.example.ai.audio.AudioProcessor
import com.example.ai.stt.IndicSttEngine
import com.example.ai.translation.IndicTrans2Engine
import com.example.ai.tts.VitsRasaTtsEngine
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
 * PHASE 7 — Speech-to-Speech Pipeline Coordinator (`SpeechToSpeechPipeline.kt`).
 *
 * Full pipeline:
 * `AudioRecord -> preprocessing -> VAD -> LID -> STT router -> transcript -> (confirmation) -> MT router -> TTS router -> AudioTrack`
 *
 * - Runs inference on a dedicated single-concurrency dispatcher (`inferenceDispatcher`) guarded by `inferenceMutex`.
 * - Respects structured coroutine cancellation (`coroutineContext.ensureActive()`) between stages.
 * - Preserves the recognized transcript if MT fails, and preserves both transcript and translated text if TTS fails.
 * - Unloads resident STT/MT models and clears the TTS LRU cache on `onTrimMemory()` / background events.
 */
class SpeechToSpeechPipeline(
    val audioProcessor: AudioProcessor,
    val sttEngine: IndicSttEngine,
    val mtEngine: IndicTrans2Engine,
    val ttsEngine: VitsRasaTtsEngine,
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private val inferenceDispatcher: CoroutineDispatcher = Dispatchers.Default.limitedParallelism(1)
) {
    private val inferenceMutex = Mutex()

    private val _currentStage = MutableStateFlow(PipelineStage.IDLE)
    val currentStage: StateFlow<PipelineStage> = _currentStage.asStateFlow()

    fun updateStage(stage: PipelineStage) {
        _currentStage.value = stage
    }

    /**
     * Unloads resident STT and MT models and trims the TTS audio cache when the app is backgrounded
     * or receives an `onTrimMemory` signal from Android OS.
     */
    fun unloadModelsOnMemoryPressure() {
        sttEngine.unloadResidentModel()
        mtEngine.unloadResidentModel()
        ttsEngine.clearAudioCache()
    }

    /**
     * Stage 1: Processes recorded audio through Preprocessing, VAD, LID, and Routed STT,
     * pausing at `AWAITING_CONFIRMATION` in Single Translate mode so the user can review or edit
     * the recognized transcript before translation.
     */
    suspend fun processRecordedAudioToTranscript(
        audioInput: AudioInput,
        sourceLang: String
    ): SpeechResult = withContext(inferenceDispatcher) {
        inferenceMutex.withLock {
            coroutineContext.ensureActive()
            _currentStage.value = PipelineStage.PREPROCESSING

            if (!audioInput.vadSpeechDetected) {
                val vadError = when {
                    audioInput.clippingDetected -> PipelineErrorType.AUDIO_CLIPPING
                    audioInput.tooQuietDetected -> PipelineErrorType.AUDIO_TOO_QUIET
                    else -> PipelineErrorType.SPEECH_NOT_DETECTED
                }
                _currentStage.value = PipelineStage.ERROR
                return@withLock SpeechResult(
                    success = false,
                    transcript = "",
                    language = sourceLang,
                    confidence = null,
                    latencyMs = 0L,
                    engineUsed = "VAD",
                    vadProbability = audioInput.vadSpeechProbability,
                    error = vadError
                )
            }

            coroutineContext.ensureActive()
            _currentStage.value = PipelineStage.STT_INFERENCE
            val sttResult = sttEngine.recognize(audioInput, sourceLang)
            _currentStage.value = if (sttResult.success) {
                PipelineStage.AWAITING_CONFIRMATION
            } else {
                PipelineStage.ERROR
            }
            sttResult
        }
    }

    /**
     * Stage 2: Runs after the user confirms (or edits) the transcript, or directly in Text / Conversation mode.
     * Executes Normalization -> Routed MT -> Routed TTS -> optional AudioTrack playback.
     * Preserves `confirmedText` if MT fails, and preserves `mtResult.translatedText` if TTS fails.
     */
    suspend fun translateAndSynthesize(
        confirmedText: String,
        sourceLang: String,
        targetLang: String,
        voiceId: String = "vits_rasa_default",
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

            if (ttsResult.success && autoPlay && ttsResult.pcm16Audio.isNotEmpty()) {
                coroutineContext.ensureActive()
                _currentStage.value = PipelineStage.PLAYING_AUDIO
                ttsEngine.speakOutLoud(
                    text = mtResult.translatedText,
                    languageCode = targetLang,
                    pcmAudio = ttsResult.pcm16Audio,
                    sampleRateHz = ttsResult.sampleRateHz,
                    speechSpeed = speechSpeed
                )
            }

            _currentStage.value = PipelineStage.COMPLETED
            EndToEndTurnOutcome(
                stage = PipelineStage.COMPLETED,
                speechResult = null,
                translationResult = mtResult,
                audioResult = ttsResult,
                error = ttsResult.error // Non-fatal if UNSUPPORTED_TARGET_VOICE; translated text is preserved
            )
        }
    }
}
