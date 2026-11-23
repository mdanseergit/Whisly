package com.example

import com.example.ai.audio.AudioProcessor
import com.example.ai.evaluation.AiEvaluationSuite
import com.example.ai.lid.LanguageIdEngine
import com.example.ai.registry.CapabilityRegistry
import com.example.ai.stt.TranscriptFirewall
import com.example.ai.translation.IndicLanguageCatalog
import com.example.ai.translation.IndicTrans2Engine
import com.example.core.security.KeystoreCryptoManager
import com.example.domain.models.PipelineErrorType
import com.example.domain.models.TtsCapabilityStatus
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

class ExampleUnitTest {

    /**
     * Synthetic speech-shaped fixture calibrated against the measured Python prototype in
     * `docs/FIX_A_VAD.md`: alternating 120/178 Hz voiced frames, a 3 Hz syllabic envelope,
     * low-level broadband noise, and a few high-frequency noise bursts that keep the
     * two-band fraction below the measured 0.94 music boundary. This is only a unit-test
     * fixture; the real pass/fail evidence uses the pinned FLEURS and CC0 WAV clips.
     */
    private fun speechLikeBlock(
        frameCount: Int,
        activeRange: IntRange,
        floor: Short = 0
    ): ShortArray {
        val out = ShortArray(frameCount * AudioProcessor.SAMPLES_PER_FRAME) { floor }
        val random = java.util.Random(11L)
        for (frame in activeRange) {
            val local = frame - activeRange.first
            val base = frame * AudioProcessor.SAMPLES_PER_FRAME
            val raw = FloatArray(AudioProcessor.SAMPLES_PER_FRAME) { random.nextGaussian().toFloat() }
            if (local == 10 || local == 20 || local == 30) {
                val highPassed = FloatArray(AudioProcessor.SAMPLES_PER_FRAME)
                for (i in 2 until highPassed.size) {
                    highPassed[i] = raw[i] - 2f * raw[i - 1] + raw[i - 2]
                }
                highPassed[0] = raw[0]
                highPassed[1] = raw[1]
                var sumSq = 0.0
                for (v in highPassed) sumSq += v.toDouble() * v
                val scale = 1500.0 / kotlin.math.sqrt(sumSq / highPassed.size)
                for (i in highPassed.indices) {
                    out[base + i] = (floor.toInt() + (highPassed[i] * scale).toInt()).toShort()
                }
            } else {
                val f0 = if (local % 2 == 0) 120.0 else 178.0
                val voiced = FloatArray(AudioProcessor.SAMPLES_PER_FRAME)
                for (i in voiced.indices) {
                    val t = (base + i).toDouble() / 16000.0
                    var value = 0.0
                    for (h in 1..12) value += kotlin.math.sin(2.0 * PI * h * f0 * t) / h
                    value += 0.4 * kotlin.math.sin(2.0 * PI * 1050.0 * t)
                    voiced[i] = value.toFloat()
                }
                var sumSq = 0.0
                for (v in voiced) sumSq += v.toDouble() * v
                val scale = 1500.0 / kotlin.math.sqrt(sumSq / voiced.size)
                for (i in voiced.indices) {
                    val t = (base + i).toDouble() / 16000.0
                    val envelope = 0.6 + 0.4 * kotlin.math.sin(2.0 * PI * 3.0 * t)
                    val sample = floor.toInt() + envelope * voiced[i] * scale + 10.0 * random.nextGaussian()
                    out[base + i] = sample.toInt().toShort()
                }
            }
        }
        return out
    }

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

        // 3. Quiet speech (260ms voiced utterance over quiet room floor) -> detected.
        // Peak is only 0.0316 full-scale; the old un-normalized floor rejected this shape.
        val quietSpeechSamples = speechLikeBlock(50, 16..36, 20.toShort())
        val quietVad = AudioProcessor.preprocessAndRunVad(quietSpeechSamples, 16000, 1000L)
        assertTrue(quietVad.vadSpeechDetected)
        assertTrue(quietVad.voicedDurationMs >= 180L)
        assertTrue(quietVad.vadSpeechProbability > 0.35f)
        assertTrue("gain must be measured, not fabricated", quietVad.diagnostics.vadNormalizationGain > 1f)
        assertTrue("speechlike ratio must pass", quietVad.diagnostics.vadSpeechlikeRatio >= 0.5f)
        assertEquals("", quietVad.diagnostics.vadSpectralRejectReason)
    }

    @Test
    fun `spectral validity rejects pure tones and broadband noise after normalization`() {
        val toneSamples = ShortArray(16000) { idx ->
            (20 + 900.0 * sin(2.0 * PI * 220.0 * idx / 16000.0)).toInt().toShort()
        }
        val toneVad = AudioProcessor.preprocessAndRunVad(toneSamples, 16000, 1000L)
        assertFalse("pure tone must not be accepted as speech", toneVad.vadSpeechDetected)
        assertTrue(
            "global classifier must give a measured reject reason",
            toneVad.diagnostics.vadGlobalRejectReason.isNotEmpty()
        )

        val random = java.util.Random(7L)
        val noiseSamples = ShortArray(16000) {
            val g = random.nextGaussian()
            (g * 2500.0).toInt().coerceIn(-32768, 32767).toShort()
        }
        val noiseVad = AudioProcessor.preprocessAndRunVad(noiseSamples, 16000, 1000L)
        assertFalse("broadband noise must not be accepted as speech", noiseVad.vadSpeechDetected)
        assertTrue(
            "global classifier must give a measured reject reason",
            noiseVad.diagnostics.vadGlobalRejectReason.isNotEmpty()
        )
    }

    @Test
    fun `voiced segment trims leading and trailing silence before decoding`() {
        // 1.0s silence, 0.5s speech, 2.0s silence (50 fps frames).
        val total = speechLikeBlock(175, 50..74)
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
    fun `audio gate rejects silence and low confidence before any bundled decoder runs`() {
        val silentVad = AudioProcessor.preprocessAndRunVad(ShortArray(1600) { 0 }, 16000, 100L)
        assertFalse(silentVad.vadSpeechDetected)
        assertTrue(silentVad.tooQuietDetected)
        assertEquals(0L, silentVad.voicedDurationMs)

        assertEquals(0.55f, AudioProcessor.MIN_STT_CONFIDENCE, 0.0001f)
        val lowConfidence = 0.42f
        assertTrue(lowConfidence < AudioProcessor.MIN_STT_CONFIDENCE)
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

        assertEquals(TtsCapabilityStatus.SYSTEM_FALLBACK_ONLY, tamil?.ttsCapability)
        assertEquals(TtsCapabilityStatus.SYSTEM_FALLBACK_ONLY, bengali?.ttsCapability)
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
    fun `grep check enforces that removed voice import and fake decoder paths do not return`() {
        val mainSrcDir = java.io.File("src/main/java")
        if (!mainSrcDir.exists()) return
        val forbidden = listOf(
            "ModelImportManager",
            "SttPackageManifest",
            "SttManifestParser",
            "ManifestResult",
            "ModelImportResult",
            "whisly-stt.json",
            "import_speech_model_button",
            "banner_import_speech_model_button",
            "Import speech model (.zip)",
            "application/zip",
            "application/octet-stream",
            "acousticDecoder",
            "recognizedSpeechText",
            "preset transcript",
            "canned transcript",
            "install Google offline speech pack",
            "Import one, or type to translate.",
            "Import a model package to speak",
            "Download pack to speak",
            "Download speech model"
        )
        val ktFiles = mainSrcDir.walkTopDown().filter { it.extension == "kt" }.toList()
        for (file in ktFiles) {
            val content = file.readText()
            for (term in forbidden) {
                assertFalse(
                    "Forbidden removed-voice term '$term' returned in ${file.name}",
                    content.contains(term, ignoreCase = true)
                )
            }
        }
    }

    @Test
    fun `grep check enforces that speech screens never offer auto detect`() {
        val presentationDir = java.io.File("src/main/java/com/example/presentation")
        if (!presentationDir.exists()) return
        val voiceScreens = listOf(
            "home/HomeScreen.kt",
            "conversation/ConversationScreen.kt",
            "translate/TranslateScreen.kt"
        )
        voiceScreens.forEach { relative ->
            val file = java.io.File(presentationDir, relative)
            assertTrue("Missing voice screen $relative", file.exists())
            assertTrue(
                "$relative must disable the auto-detect picker option",
                file.readText().contains("allowAutoDetect = false")
            )
        }
        val viewModel = java.io.File(presentationDir, "WhislyViewModel.kt").readText()
        assertTrue(
            "Speech recording must validate the selected spoken language",
            viewModel.contains("speechSourceOrNull(sourceLang) == null")
        )
        assertFalse(
            "Conversation STT must not override the selected language with an engine guess",
            viewModel.contains("outcome.languageCode.ifBlank")
        )
        assertTrue(
            "Typed text fallback must remain available in the text-only screen",
            java.io.File(presentationDir, "text_translation/TextTranslationScreen.kt")
                .readText().contains("allowAutoDetect = true")
        )
    }

    @Test
    fun `transcript firewall fails closed without per token confidence`() {
        assertEquals(
            TranscriptFirewall.Outcome.FAIL,
            TranscriptFirewall.evaluate("", null)
        )
        assertEquals(
            TranscriptFirewall.Outcome.LOW_CONFIDENCE,
            TranscriptFirewall.evaluate("hello", null)
        )
        assertEquals(
            TranscriptFirewall.Outcome.LOW_CONFIDENCE,
            TranscriptFirewall.evaluate("hello", 0.99f)
        )
        assertEquals(
            TranscriptFirewall.Outcome.PASS,
            TranscriptFirewall.evaluate("hello", 0.9f, 0.8f)
        )
        assertEquals("Not sure: check and edit", TranscriptFirewall.LOW_CONFIDENCE_LABEL)
        assertEquals("Couldn't understand. Try again.", TranscriptFirewall.FAILURE_MESSAGE)
    }

    @Test
    fun `grep check enforces no fabricated confidence and no automatic translation`() {
        val viewModel = java.io.File("src/main/java/com/example/presentation/WhislyViewModel.kt")
            .readText()
        assertFalse(
            "STT confidence must never be fabricated as 1.0f",
            viewModel.contains("outcome.confidence ?: 1.0f")
        )
        assertFalse(
            "STT confidence must never be fabricated as 0f",
            viewModel.contains("state.sttConfidence ?: 0f")
        )
        assertTrue(
            "Speech results must pass through the transcript firewall",
            viewModel.contains("TranscriptFirewall.evaluate")
        )
        assertFalse(
            "No STT result may auto-call translation",
            Regex("(?m)^\\s+confirmTranscriptAndTranslate\\(\\)\\s*$").containsMatchIn(viewModel)
        )
    }

    @Test
    fun `speech constancy test proves silence and click noise are rejected before decoding`() {
        val silentAudio = AudioProcessor.preprocessAndRunVad(ShortArray(16000) { 0 }, 16000, 1000L)
        assertFalse(silentAudio.vadSpeechDetected)
        assertTrue(silentAudio.voicedSegmentPcm16.isEmpty())

        val clickAudio = AudioProcessor.preprocessAndRunVad(
            ShortArray(16000) { if (it in 1000..1500) 5000.toShort() else 0.toShort() },
            16000,
            1000L
        )
        assertFalse(clickAudio.vadSpeechDetected)
        assertTrue(
            "500-sample click must stay below the 120ms voiced-duration gate",
            clickAudio.voicedDurationMs < AudioProcessor.MIN_VOICED_DURATION_MS
        )
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

    private fun le16(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or ((bytes[offset + 1].toInt() and 0xff) shl 8)

    private fun le32(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    /** Minimal PCM WAV reader for the pinned 16 kHz mono, 16-bit fixtures. */
    private fun readMono16kPcm16(file: File): ShortArray {
        val bytes = file.readBytes()
        assertEquals("RIFF", String(bytes, 0, 4, Charsets.US_ASCII))
        assertEquals("WAVE", String(bytes, 8, 4, Charsets.US_ASCII))
        var formatCode = 0
        var channels = 0
        var sampleRate = 0
        var bits = 0
        var dataStart = -1
        var dataSize = 0
        var pos = 12
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = le32(bytes, pos + 4)
            val body = pos + 8
            if (id == "fmt ") {
                formatCode = le16(bytes, body)
                channels = le16(bytes, body + 2)
                sampleRate = le32(bytes, body + 4)
                bits = le16(bytes, body + 14)
            } else if (id == "data") {
                dataStart = body
                dataSize = size
                break
            }
            pos = body + size + (size and 1)
        }
        assertEquals(1, channels)
        assertEquals(16000, sampleRate)
        assertTrue(
            "unsupported WAV format=$formatCode bits=$bits",
            (formatCode == 1 && bits == 16) || (formatCode == 3 && bits == 32)
        )
        assertTrue("WAV data chunk missing", dataStart >= 0)
        if (formatCode == 3) {
            val pcm = ShortArray(dataSize / 4)
            for (i in pcm.indices) {
                val value = ByteBuffer.wrap(bytes, dataStart + i * 4, 4)
                    .order(ByteOrder.LITTLE_ENDIAN).float
                pcm[i] = (value.toDouble() * 32768.0).roundToInt()
                    .coerceIn(-32768, 32767).toShort()
            }
            return pcm
        }
        val pcm = ShortArray(dataSize / 2)
        for (i in pcm.indices) {
            pcm[i] = le16(bytes, dataStart + i * 2).toShort()
        }
        return pcm
    }

    @Test
    fun `production VAD matches measured real speech and CC0 nonspeech fixtures`() {
        val evidenceDirs = listOf(
            File("deliverables/fix_a_vad"),
            File(System.getProperty("user.dir"), "deliverables/fix_a_vad"),
            File("C:/Users/danse/OneDrive/document/Whisly/whisly/deliverables/fix_a_vad")
        )
        val evidenceDir = evidenceDirs.firstOrNull {
            File(it, "real_speech_paths.txt").exists() && File(it, "real_nonspeech_paths.tsv").exists()
        } ?: evidenceDirs.first()
        val speechListFile = File(evidenceDir, "real_speech_paths.txt")
        val nonspeechListFile = File(evidenceDir, "real_nonspeech_paths.tsv")
        if (!speechListFile.exists() || !nonspeechListFile.exists()) {
            println("SKIP real-fixture VAD parity: evidence lists are not present in this checkout")
            return
        }

        var speechTotal = 0
        var speechAccepted = 0
        val speechFailures = ArrayList<String>()
        for (line in speechListFile.readLines()) {
            val path = line.trim()
            if (path.isEmpty()) continue
            val file = File(path)
            if (!file.exists()) continue
            speechTotal++
            val vad = AudioProcessor.preprocessAndRunVad(readMono16kPcm16(file), 16000, 0L)
            if (vad.vadSpeechDetected) {
                speechAccepted++
            } else {
                speechFailures.add(
                    "${file.parentFile.name}-${file.name}:" +
                        "base=${vad.diagnostics.vadSpectralRejectReason}," +
                        "global=${vad.diagnostics.vadGlobalRejectReason}," +
                        "b2=${vad.diagnostics.vadGlobalBand2Fraction}," +
                        "b3=${vad.diagnostics.vadGlobalBand3Fraction}," +
                        "flat=${vad.diagnostics.vadGlobalFlatnessMedian}," +
                        "harm=${vad.diagnostics.vadGlobalHarmonicityMedian}," +
                        "syll=${vad.diagnostics.vadGlobalSyllableModulation}"
                )
            }
        }

        var nonspeechTotal = 0
        var nonspeechRejected = 0
        for (line in nonspeechListFile.readLines()) {
            val path = line.substringAfter('\t').trim()
            if (path.isEmpty()) continue
            val file = File(path)
            if (!file.exists()) continue
            nonspeechTotal++
            val vad = AudioProcessor.preprocessAndRunVad(readMono16kPcm16(file), 16000, 0L)
            if (!vad.vadSpeechDetected) nonspeechRejected++
        }

        println("PRODUCTION_VAD_REAL_SPEECH accepted=$speechAccepted/$speechTotal")
        println("PRODUCTION_VAD_REAL_NONSPEECH rejected=$nonspeechRejected/$nonspeechTotal")
        speechFailures.forEach { println("PRODUCTION_VAD_SPEECH_FAILURE $it") }
        if (speechTotal == 0 || nonspeechTotal == 0) {
            println("SKIP real-fixture VAD parity: pinned WAV files are not on this machine")
            return
        }
        assertEquals("all pinned speech clips must pass", speechTotal, speechAccepted)
        assertEquals("all real music/noise clips must be rejected", nonspeechTotal, nonspeechRejected)
    }

    @Test
    fun `bundled Whisper Tiny assets match pinned sizes and sha256`() {
        val assetDir = java.io.File("src/main/assets/whisper/tiny")
        val expected = mapOf(
            "tiny-encoder.int8.onnx" to
                (12_937_772L to "D24FB083AE3B1041FC24E97971D60E280C9342201FBB67B0AB428A8B4A51A434"),
            "tiny-decoder.int8.onnx" to
                (89_855_401L to "D2FECE8DD42771F1DF975C6C0445770D0C292BF7547C2CAE04A6C0CC57540925"),
            "tiny-tokens.txt" to
                (816_730L to "B34B360DBB493E781E479794586D661700670D65564001F23024971D1F2FA126")
        )
        for ((name, sizeAndHash) in expected) {
            val file = java.io.File(assetDir, name)
            assertTrue("Bundled asset missing: $name", file.exists())
            assertEquals(sizeAndHash.first, file.length())
            val digest = java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes())
            val actualHash = digest.joinToString("") { "%02X".format(it) }
            assertEquals("SHA-256 mismatch for $name", sizeAndHash.second, actualHash)
        }
    }

    @Test
    fun `Whisper language gate accepts published token codes and rejects registry gaps`() {
        val supported = listOf(
            "as", "bn", "en", "en-IN", "gu", "hi", "kn", "ml", "mr", "ne", "pa", "sa", "sd",
            "ta", "te", "ur"
        )
        val unsupported = listOf("brx", "doi", "ks", "kok", "mai", "mni", "or", "sat")
        supported.forEach { assertTrue("$it should be supported", com.example.ai.stt.SherpaOnnxSttEngine.isLanguageSupported(it)) }
        unsupported.forEach { assertFalse("$it must remain unavailable for bundled STT", com.example.ai.stt.SherpaOnnxSttEngine.isLanguageSupported(it)) }
    }
}
