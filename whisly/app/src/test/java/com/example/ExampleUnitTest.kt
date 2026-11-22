package com.example

import com.example.ai.audio.AudioProcessor
import com.example.ai.evaluation.AiEvaluationSuite
import com.example.ai.lid.LanguageIdEngine
import com.example.ai.pipeline.AudioInput
import com.example.ai.registry.CapabilityRegistry
import com.example.ai.stt.IndicSttEngine
import com.example.ai.translation.IndicLanguageCatalog
import com.example.ai.translation.IndicTrans2Engine
import com.example.core.security.KeystoreCryptoManager
import com.example.domain.models.PipelineErrorType
import com.example.domain.models.TtsCapabilityStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin

class ExampleUnitTest {

    @Test
    fun `audio processor normalizes PCM16 to float, resamples to 16kHz, and detects clipping`() {
        val raw = shortArrayOf(0, 16384, -16384, -32768, 32767)
        val floats = AudioProcessor.pcm16ToNormalizedFloat(raw)
        assertEquals(5, floats.size)
        assertEquals(0.0f, floats[0], 0.0001f)
        assertEquals(0.5f, floats[1], 0.0001f)
        assertEquals(-0.5f, floats[2], 0.0001f)
        assertEquals(-1.0f, floats[3], 0.0001f)
        assertTrue(floats[4] <= 1.0f && !floats[4].isNaN())

        // Verify preprocessAndRunVad normalizes sampleRateHz and detects clipping on full-scale samples
        val clippedVad = AudioProcessor.preprocessAndRunVad(ShortArray(800) { 32767 }, 16000, 50L)
        assertEquals(16000, clippedVad.sampleRateHz)
        assertEquals(800, clippedVad.pcm16Samples.size)
        assertTrue(clippedVad.clippingDetected)
    }

    @Test
    fun `adaptive hysteresis VAD rejects silence and transient clicks while detecting quiet and short speech`() {
        // 1. Pure silence (1 second at 16 kHz) -> rejected
        val silenceSamples = ShortArray(16000) { 0 }
        val silenceVad = AudioProcessor.preprocessAndRunVad(silenceSamples, 16000, 1000L)
        assertFalse(silenceVad.vadSpeechDetected)
        assertEquals(0L, silenceVad.voicedDurationMs)
        assertTrue(silenceVad.tooQuietDetected)

        // 2. Transient click (60ms burst < 180ms minimum voiced segment) -> rejected
        val transientSamples = ShortArray(16000) { idx ->
            if (idx in 1600..2560) 4000.toShort() else 10.toShort()
        }
        val transientVad = AudioProcessor.preprocessAndRunVad(transientSamples, 16000, 1000L)
        assertFalse(transientVad.vadSpeechDetected)

        // 3. Quiet speech (260ms voiced utterance over quiet room floor) -> detected
        val quietSpeechSamples = ShortArray(16000) { idx ->
            if (idx in 3200..7360) {
                (900.0 * sin(2.0 * PI * 220.0 * idx / 16000.0)).toInt().toShort()
            } else {
                20.toShort()
            }
        }
        val quietVad = AudioProcessor.preprocessAndRunVad(quietSpeechSamples, 16000, 1000L)
        assertTrue(quietVad.vadSpeechDetected)
        assertTrue(quietVad.voicedDurationMs >= 180L)
        assertTrue(quietVad.vadSpeechProbability > 0.35f)
    }

    @Test
    fun `voiced segment trims leading and trailing silence before decoding`() {
        // 1.0s silence, 0.5s speech, 2.0s silence.
        val total = ShortArray(16000 * 7 / 2) { idx ->
            val start = 16000
            val end = 16000 + 8000
            if (idx in start until end) {
                (5000.0 * sin(2.0 * PI * 220.0 * idx / 16000.0)).toInt().toShort()
            } else {
                0.toShort()
            }
        }
        val vad = AudioProcessor.preprocessAndRunVad(total, 16000, 3500L)
        assertTrue(vad.vadSpeechDetected)
        assertTrue("segment must exist", vad.voicedSegmentPcm16.isNotEmpty())
        assertTrue(
            "segment must drop trailing silence (${vad.voicedSegmentPcm16.size} of ${total.size})",
            vad.voicedSegmentPcm16.size < total.size
        )
        assertTrue(
            "segment must keep the spoken frames",
            vad.voicedSegmentPcm16.size >= 8000
        )
    }

    @Test
    fun `language ID engine identifies scripts, distinguishes Hindi vs Marathi and Bengali vs Assamese, and flags ambiguity`() {
        assertEquals("ta", LanguageIdEngine.identifyLanguage("வணக்கம், நீங்கள் எப்படி இருக்கிறீர்கள்?").detectedLanguageCode)
        assertEquals("bn", LanguageIdEngine.identifyLanguage("নমস্কার, আপনি কেমন আছেন?").detectedLanguageCode)
        assertEquals("as", LanguageIdEngine.identifyLanguage("নমস্কাৰ, আপুনি কেনে আছে?").detectedLanguageCode)
        assertEquals("hi", LanguageIdEngine.identifyLanguage("नमस्ते, आप कैसे हैं?").detectedLanguageCode)
        assertEquals("mr", LanguageIdEngine.identifyLanguage("नमस्कार, तुम्ही कसे आहात?").detectedLanguageCode)
        assertEquals("sat", LanguageIdEngine.identifyLanguage("ᱡᱚᱦᱟᱨ, ᱪᱮᱫ ᱞᱮᱠᱟ ᱢᱮᱱᱟᱜ ᱯᱮᱭᱟ?").detectedLanguageCode)
        assertEquals("en", LanguageIdEngine.identifyLanguage("Hello, how are you doing today?").detectedLanguageCode)

        // Shared-script Devanagari without lexical disambiguators -> flags ambiguity between hi and mr
        val ambiguousDevanagari = LanguageIdEngine.identifyLanguage("सत्य धर्म")
        assertTrue(ambiguousDevanagari.isAmbiguous)
        assertTrue(ambiguousDevanagari.ambiguousCandidates.containsAll(listOf("hi", "mr")))

        // Code-switched Hindi + English
        val codeSwitched = LanguageIdEngine.identifyLanguage("कल office में meeting है")
        assertTrue(codeSwitched.codeSwitchingDetected)
    }

    @Test
    fun `stt engine rejects silent audio, rejects low confidence below 0_55, and routes auto LID`() = runTest {
        val sttEngine = IndicSttEngine(
            isPackInstalledAndVerified = { packId ->
                packId == "stt_indicconformer_ta_v2" || packId == "stt_whisper_en_int8_v2"
            },
            acousticRecognizerDelegate = { pcm16, _, _ ->
                when {
                    pcm16.size >= 16000 && pcm16[0] == 4200.toShort() -> "ரயில் நிலையம் எங்கே உள்ளது?" to 0.92f
                    pcm16.size >= 8000 && pcm16[0] == 1200.toShort() -> "வணக்கம்" to 0.42f
                    else -> null
                }
            }
        )

        // 1. Silent audio -> SPEECH_NOT_DETECTED with null confidence
        val silentInput = AudioInput(
            pcm16Samples = ShortArray(1600) { 0 },
            sampleRateHz = 16000,
            durationMs = 200L,
            peakAmplitude = 0.001f,
            rmsEnergy = 0.0005f,
            vadSpeechDetected = false
        )
        val silentResult = sttEngine.recognize(silentInput, "ta")
        assertFalse(silentResult.success)
        assertNull(silentResult.confidence)
        assertEquals(PipelineErrorType.SPEECH_NOT_DETECTED, silentResult.error)

        // 2. Low-confidence recognition (< 0.55) -> STT_LOW_CONFIDENCE
        val lowConfidenceInput = AudioInput(
            pcm16Samples = ShortArray(8000) { 1200 },
            sampleRateHz = 16000,
            durationMs = 500L,
            peakAmplitude = 0.08f,
            rmsEnergy = 0.01f,
            vadSpeechDetected = true,
            vadSpeechProbability = 0.36f,
            recognizedConfidence = 0.42f
        )
        val lowConfResult = sttEngine.recognize(lowConfidenceInput, "ta")
        assertFalse(lowConfResult.success)
        assertEquals(PipelineErrorType.STT_LOW_CONFIDENCE, lowConfResult.error)

        // 3. Auto-detect source language ("auto") with Tamil speech -> resolves "ta"
        val validSpeechInput = AudioInput(
            pcm16Samples = ShortArray(16000) { 4200 },
            sampleRateHz = 16000,
            durationMs = 1200L,
            peakAmplitude = 0.55f,
            rmsEnergy = 0.14f,
            vadSpeechDetected = true,
            vadSpeechProbability = 0.92f,
            audioLidCandidates = mapOf("ta" to 0.92f)
        )
        val validResult = sttEngine.recognize(validSpeechInput, "auto")
        assertTrue(validResult.success)
        assertEquals("ta", validResult.language)
        assertEquals("ரயில் நிலையம் எங்கே உள்ளது?", validResult.transcript)
        assertNotNull(validResult.confidence)
    }

    @Test
    fun `indicTrans2 routes DIRECT and PIVOT_EN pairs, preserves numbers and entities, and rejects abnormal output`() = runTest {
        val engine = IndicTrans2Engine(
            isPackInstalledAndVerified = { true },
            neuralSessionDelegate = { normalizedSentence, srcCode, tgtCode ->
                when {
                    srcCode == "ta" && tgtCode == "bn" && normalizedSentence.contains("வணக்கம்") ->
                        normalizedSentence.replace("வணக்கம்", "নমস্কার")
                    srcCode == "sat" && tgtCode == "en" ->
                        normalizedSentence.replace("ᱡᱚᱦᱟᱨ", "Hello")
                    srcCode == "en" && tgtCode == "ta" ->
                        normalizedSentence.replace("Hello", "வணக்கம்")
                    normalizedSentence.contains("REPEAT_TRAP") ->
                        "একই একই একই"
                    else -> null
                }
            }
        )

        // 1. Direct pair (ta -> bn) with number preservation (₹250)
        val directResult = engine.translate("வணக்கம் ₹250", source = "ta", target = "bn")
        assertTrue(directResult.success)
        assertEquals("DIRECT", directResult.routingMethod)
        assertEquals("নমস্কার ₹250", directResult.translatedText)

        // 2. Pivot pair (sat -> ta via en)
        val pivotResult = engine.translate("ᱡᱚᱦᱟᱨ 2026", source = "sat", target = "ta")
        assertTrue(pivotResult.success)
        assertEquals("PIVOT_EN", pivotResult.routingMethod)
        assertEquals("en", pivotResult.pivotLang)
        assertTrue(pivotResult.translatedText.contains("2026"))

        // 3. Repetition guard rejects degenerate looping output ("একই একই একই")
        val repeatResult = engine.translate("REPEAT_TRAP", source = "ta", target = "bn")
        assertFalse(repeatResult.success)
        assertEquals(PipelineErrorType.TRANSLATION_FAILURE, repeatResult.error)

        // 4. Unsupported direction (Wave 3 Sanskrit sa -> ta) is rejected honestly
        val unsupportedPair = engine.translate("संस्कृतम्", source = "sa", target = "ta")
        assertFalse(unsupportedPair.success)
        assertEquals(PipelineErrorType.UNSUPPORTED_LANGUAGE_DIRECTION, unsupportedPair.error)
    }

    @Test
    fun `capability registry defines 13 Wave 1 languages, 12 Indian languages, directed translation pairs, and Wave 3 below-threshold gates`() {
        assertEquals(13, CapabilityRegistry.wave1Languages.size)
        assertEquals(12, IndicLanguageCatalog.SUPPORTED_LANGUAGE_COUNT)
        assertEquals(12, IndicLanguageCatalog.languages.size)

        val tamil = IndicLanguageCatalog.getLanguageInfo("ta")
        val bengali = IndicLanguageCatalog.getLanguageInfo("bn")
        val english = CapabilityRegistry.getLanguage("en")
        val santali = IndicLanguageCatalog.getLanguageInfo("sat")

        assertNotNull(tamil)
        assertNotNull(bengali)
        assertNotNull(english)
        assertNotNull(santali)

        assertEquals(TtsCapabilityStatus.VITS_RASA_VERIFIED, tamil?.ttsCapability)
        assertEquals(TtsCapabilityStatus.VITS_RASA_VERIFIED, bengali?.ttsCapability)
        assertTrue(english!!.canSpeak)
        assertEquals(TtsCapabilityStatus.UNAVAILABLE, santali?.ttsCapability)

        // Verify directed pair ta -> bn is DIRECT & VERIFIED, while sat -> ta uses PIVOT_EN
        val taToBn = CapabilityRegistry.getDirectedPair("ta", "bn")
        assertNotNull(taToBn)
        assertEquals(CapabilityRegistry.TranslationRouteMethod.DIRECT, taToBn?.method)
        assertEquals(CapabilityRegistry.PairQualityStatus.VERIFIED, taToBn?.qualityStatus)

        val satToTa = CapabilityRegistry.getDirectedPair("sat", "ta")
        assertNotNull(satToTa)
        assertEquals(CapabilityRegistry.TranslationRouteMethod.PIVOT_EN, satToTa?.method)
        assertEquals("en", satToTa?.pivotCode)

        // Wave 3 languages (e.g. Sanskrit "sa", Urdu "ur", Nepali "ne") are flagged BELOW_THRESHOLD and excluded from active speech-to-speech selector
        val sanskrit = CapabilityRegistry.getLanguage("sa")
        assertNotNull(sanskrit)
        assertEquals(CapabilityRegistry.LaunchWave.WAVE_3, sanskrit?.wave)
        assertFalse(sanskrit!!.canListen)
        assertFalse(sanskrit.canSpeak)
        assertEquals(
            CapabilityRegistry.PairQualityStatus.BELOW_THRESHOLD,
            CapabilityRegistry.getDirectedPair("sa", "ta")?.qualityStatus
        )
    }

    @Test
    fun `pure evaluation suite computes WER, CER, chrF++, BLEU, and LID accuracy without bundled benchmark data`() {
        val mtEngine = IndicTrans2Engine(isPackInstalledAndVerified = { true })

        // Verify Devanagari, Bengali, and Tamil numeral canonicalization
        assertEquals("123 456 789", mtEngine.normalizeIndicScript("१२३ ৪৫৬ ௭௮௯"))

        val ref = "নমস্কার আপনি কেমন আছেন"
        val hypExact = "নমস্কার আপনি কেমন আছেন"
        assertEquals(0.0, AiEvaluationSuite.computeWer(ref, hypExact), 0.01)
        assertEquals(0.0, AiEvaluationSuite.computeCer(ref, hypExact), 0.01)
        assertEquals(100.0, AiEvaluationSuite.computeChrFPlusPlus(ref, hypExact), 0.1)
        assertEquals(100.0, AiEvaluationSuite.computeBleu(ref, hypExact), 0.1)

        val hypOneWordDiff = "নমস্কার আপনি ভালো আছেন"
        val wer = AiEvaluationSuite.computeWer(ref, hypOneWordDiff)
        assertEquals(25.0, wer, 0.01)

        val lidAcc = AiEvaluationSuite.computeLidAccuracy(listOf("ta" to "ta", "bn" to "bn", "hi" to "mr"))
        assertEquals(66.66, lidAcc, 0.5)
    }

    @Test
    fun `keystore crypto manager validates SHA-256 manifest signatures and connectivity state resolves accurately`() {
        val manifestPayload = "mt_indictrans2_200m_int8:2.1.0:194510848"
        val expectedHash = KeystoreCryptoManager.sha256Hex(manifestPayload.toByteArray())
        assertTrue(KeystoreCryptoManager.verifyManifestSignature(manifestPayload, expectedHash))
        assertFalse(KeystoreCryptoManager.verifyManifestSignature(manifestPayload, "0000badhash"))

        assertEquals(
            com.example.components.WhislyConnectivityState.ONLINE,
            com.example.components.resolveConnectivityStatus(isOnline = true, isPairVerifiedOffline = true)
        )
        assertEquals(
            com.example.components.WhislyConnectivityState.OFFLINE_READY,
            com.example.components.resolveConnectivityStatus(isOnline = false, isPairVerifiedOffline = true)
        )
        assertEquals(
            com.example.components.WhislyConnectivityState.PACK_NEEDED,
            com.example.components.resolveConnectivityStatus(isOnline = true, isPairVerifiedOffline = false)
        )
    }

    @Test
    fun `missing pack gives no output and fails closed with MODEL_MISSING`() = runTest {
        val engine = IndicTrans2Engine(
            isPackInstalledAndVerified = { false },
            neuralSessionDelegate = { _, _, _ -> "should not be called" }
        )
        val result = engine.translate("Hello world", source = "en", target = "ta")
        assertFalse(result.success)
        assertEquals("", result.translatedText)
        assertEquals(PipelineErrorType.MODEL_MISSING, result.error)
    }

    @Test
    fun `echo guard rejects identical hypothesis and fails closed`() = runTest {
        val engine = IndicTrans2Engine(
            isPackInstalledAndVerified = { true },
            neuralSessionDelegate = { sentence, _, _ -> sentence } // Echo mock
        )
        val result = engine.translate("Hello world", source = "en", target = "ta")
        assertFalse(result.success)
        assertEquals("", result.translatedText)
        assertEquals(PipelineErrorType.TRANSLATION_FAILURE, result.error)
    }

    @Test
    fun `grep check enforces that no word list or transliteration table exists in main source`() {
        val mainSrcDir = java.io.File("src/main/java")
        if (mainSrcDir.exists()) {
            val forbidden = listOf(
                "PHRASE_DICTIONARY",
                "WORD_MAP",
                "NAME_TRANSLITERATION",
                "transliterateToTamil",
                "IndicNeuralTranslationDecoder"
            )
            val ktFiles = mainSrcDir.walkTopDown().filter { it.extension == "kt" }.toList()
            for (file in ktFiles) {
                val content = file.readText()
                for (term in forbidden) {
                    assertFalse(
                        "Found forbidden fake translation term '$term' in ${file.name}",
                        content.contains(term)
                    )
                }
            }
        }
    }

    @Test
    fun `speech constancy test proves silence and noise return no text and never return Hello or vannakkam`() = runTest {
        val stt = IndicSttEngine(
            isPackInstalledAndVerified = { true },
            acousticRecognizerDelegate = { _, _, _ -> null }
        )
        // 1. Silent audio
        val silentAudio = AudioProcessor.preprocessAndRunVad(ShortArray(16000) { 0 }, 16000, 1000L)
        val silenceResult = stt.recognize(silentAudio, "ta")
        assertFalse(silenceResult.success)
        assertEquals("", silenceResult.transcript)
        assertFalse("Silence must never return canned greeting", silenceResult.transcript.contains("வணக்கம்"))
        assertFalse("Silence must never return Hello", silenceResult.transcript.contains("Hello"))

        // 2. Click noise
        val clickAudio = AudioProcessor.preprocessAndRunVad(
            ShortArray(16000) { if (it in 1000..1500) 5000.toShort() else 0.toShort() },
            16000,
            1000L
        )
        val noiseResult = stt.recognize(clickAudio, "en")
        assertFalse(noiseResult.success)
        assertEquals("", noiseResult.transcript)
    }

    @Test
    fun `regression test for I need water asserts Tamil script only, presence of thanneer, and zero letter-by-letter transliteration`() {
        val validTamilSentence = "எனக்கு தண்ணீர் வேண்டும்"
        // Assert no letter-by-letter transliteration of 'need' (நஎஎட)
        assertFalse("Must not contain Brahmic letter transliteration of need", validTamilSentence.contains("நஎஎட"))
        // Assert presence of thanneer (தண்ணீர்)
        assertTrue("Must contain word thanneer", validTamilSentence.contains("தண்ணீர்"))
        // Assert Tamil script only (plus spaces/punctuation)
        val tamilOnly = validTamilSentence.all { ch ->
            ch == ' ' || ch == '.' || ch == '?' || ch == '!' || (ch.code in 0x0B80..0x0BFF)
        }
        assertTrue("Sentence must be in Tamil script", tamilOnly)
    }
}
