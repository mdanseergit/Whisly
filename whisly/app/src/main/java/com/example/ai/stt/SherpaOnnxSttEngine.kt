package com.example.ai.stt

import android.content.Context
import android.util.Log
import com.example.domain.models.PipelineErrorType

/**
 * sherpa-onnx STT adapter.
 *
 * Step 1 reset state: user ZIP import and downloaded model packages are removed. The bundled
 * multilingual Whisper Tiny INT8 assets are added in Step 2; until then decoding fails closed and
 * no speech text is produced.
 */
class SherpaOnnxSttEngine(@Suppress("UNUSED_PARAMETER") private val context: Context) : SttDecoder {

    override suspend fun decode(
        pcm16: ShortArray,
        sampleRateHz: Int,
        languageCode: String
    ): SttDecode {
        if (pcm16.isEmpty()) {
            return SttDecode.Failure(PipelineErrorType.SPEECH_NOT_DETECTED, "empty segment")
        }
        Log.w(TAG, "bundled STT assets are not installed yet; failing closed")
        return SttDecode.Failure(
            PipelineErrorType.STT_MODEL_MISSING,
            "bundled STT assets are not installed yet"
        )
    }

    fun unload() {
        // No resident native recognizer exists in the reset state.
    }

    private companion object {
        const val TAG = "SherpaStt"
    }
}
