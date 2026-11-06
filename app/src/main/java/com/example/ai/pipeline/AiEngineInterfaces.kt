package com.example.ai.pipeline

import com.example.domain.models.ModelTaskType
import com.example.domain.models.PipelineErrorType

/**
 * Development-only audio & VAD diagnostic telemetry.
 * Logged in debug builds only; never shown to end users in the UI (Phase 10).
 */
data class AudioDiagnostics(
    val audioRecordInitialized: Boolean = true,
    val sampleRateHz: Int = 16000,
    val channels: Int = 1,
    val pcmEncoding: String = "PCM_16BIT",
    val totalSamples: Int = 0,
    val audioDurationMs: Long = 0L,
    val voicedSegmentDurationMs: Long = 0L,
    val peakAmplitude: Float = 0f,
    val rmsEnergy: Float = 0f,
    val noiseFloorRms: Float = 0f,
    val silencePercentage: Float = 100f,
    val clippingDetected: Boolean = false,
    val tooQuietDetected: Boolean = false,
    val preRollFramesAttached: Int = 0,
    val totalFrames: Int = 0,
    val voicedFrames: Int = 0,
    val vadSpeechDetected: Boolean = false,
    val vadSpeechProbability: Float = 0f,
    val sttConfidence: Float? = null,
    val benchmarkWer: Double? = null,
    val benchmarkCer: Double? = null
) {
    val sampleRate: Int get() = sampleRateHz
    val framesRead: Int get() = totalFrames
    val speechSegmentDurationMs: Long get() = voicedSegmentDurationMs
}

data class AudioInput(
    val pcm16Samples: ShortArray,
    val normalizedFloatSamples: FloatArray = FloatArray(0),
    val sampleRateHz: Int = 16000,
    val durationMs: Long,
    val voicedDurationMs: Long = 0L,
    val peakAmplitude: Float,
    val rmsEnergy: Float,
    val silencePercentage: Float = 100f,
    val vadSpeechDetected: Boolean,
    val vadSpeechProbability: Float = 0f,
    val clippingDetected: Boolean = false,
    val tooQuietDetected: Boolean = false,
    val diagnostics: AudioDiagnostics = AudioDiagnostics(),
    val recognizedSpeechText: String? = null,
    val recognizedConfidence: Float? = null,
    val audioLidCandidates: Map<String, Float> = emptyMap()
)

data class SpeechResult(
    val success: Boolean,
    val transcript: String,
    val language: String,
    val confidence: Float?,
    val latencyMs: Long,
    val engineUsed: String,
    val vadProbability: Float = 0f,
    val isLanguageAmbiguous: Boolean = false,
    val ambiguousLanguageCandidates: List<String> = emptyList(),
    val codeSwitchingDetected: Boolean = false,
    val error: PipelineErrorType? = null
)

data class TranslationResult(
    val success: Boolean,
    val sourceText: String,
    val normalizedSourceText: String,
    val translatedText: String,
    val tokenizedPreview: String,
    val sourceLang: String,
    val targetLang: String,
    val sourceIndicTag: String,
    val targetIndicTag: String,
    val latencyMs: Long,
    val engineUsed: String,
    val routingMethod: String = "DIRECT",
    val pivotLang: String? = null,
    val error: PipelineErrorType? = null
)

data class AudioResult(
    val success: Boolean,
    val pcm16Audio: ShortArray,
    val sampleRateHz: Int = 22050,
    val durationMs: Long,
    val language: String,
    val voiceId: String,
    val latencyMs: Long,
    val engineUsed: String,
    val usedSystemFallback: Boolean = false,
    val error: PipelineErrorType? = null
)

data class ModelInfo(
    val packId: String,
    val languageCode: String,
    val taskType: ModelTaskType,
    val version: String,
    val fileSizeBytes: Long,
    val sha256Checksum: String,
    val localPath: String,
    val isVerified: Boolean
)

interface SpeechRecognizer {
    suspend fun recognize(
        audio: AudioInput,
        language: String
    ): SpeechResult
}

interface TranslationEngine {
    suspend fun translate(
        text: String,
        source: String,
        target: String
    ): TranslationResult
}

interface SpeechSynthesizer {
    suspend fun synthesize(
        text: String,
        language: String,
        voiceId: String
    ): AudioResult
}

interface ModelManager {
    suspend fun install(packId: String)
    suspend fun remove(packId: String)
    suspend fun getInstalledModels(): List<ModelInfo>
}
