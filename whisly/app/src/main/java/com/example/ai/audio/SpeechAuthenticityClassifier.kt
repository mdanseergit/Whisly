package com.example.ai.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

/**
 * Global speech-vs-nonspeech authentication for the measured VAD fix.
 *
 * Every threshold below comes from the measured distributions in
 * `docs/FIX_A_VAD.md`: 130 pinned FLEURS speech clips, 10 CC0 music clips,
 * 10 CC0 environmental-noise clips, 10 silent clips, 10 white-noise clips and
 * 10 tonal proxies. This object does not invent an STT confidence score; it
 * only decides whether captured audio is plausible human speech before STT.
 */
object SpeechAuthenticityClassifier {
    private const val SAMPLE_RATE_HZ = 16000
    private const val FFT_SIZE = 512
    private const val HZ_PER_BIN = SAMPLE_RATE_HZ.toFloat() / FFT_SIZE.toFloat()
    private const val ACTIVE_RMS_FRACTION = 0.25f

    private const val MAX_FLATNESS_MEDIAN = 0.14f
    private const val MAX_BAND3_FRACTION = 0.60f
    private const val MAX_BAND2_FRACTION = 0.94f
    private const val MAX_HIGH_RATIO_MEDIAN = 0.25f
    private const val MAX_CENTROID_HZ_MEDIAN = 2000f

    private const val MIN_HARMONICITY_MEDIAN = 0.35f
    private const val MIN_SYLLABLE_MODULATION = 0.25f
    private const val BURST_FLUX_MEDIAN = 500f
    private const val BURST_BAND3_FRACTION = 0.20f
    private const val MIN_PITCH_CHANGE_RATE = 0.10f
    private const val PITCH_HARMONICITY_MIN = 0.50f
    private const val MIN_ACTIVE_FRAMES_FOR_CHANGE = 200
    private const val MIN_ACTIVE_FRAMES = 30
    private const val SHORT_HARMONICITY_MIN = 0.60f

    private const val MIN_BAND2_FRACTION = 0.15f
    private const val HIGH_FLATNESS = 0.006f
    private const val NOISE_FLATNESS = 0.025f
    private const val LOW_FLATNESS = 0.020f
    private const val LOW_ZCR = 0.02f

    private const val PITCH_LAG_MIN = 53  // 16000 / 300 Hz
    private const val PITCH_LAG_MAX = 200 // 16000 / 80 Hz
    private const val MIN_NORMALIZED_AUTOCORRELATION = 0.30f
    private const val ONE_SEMITONE = 1.0

    private val totalBand = lowerBin(150f) until upperExclusiveBin(5000f)
    private val highBand = lowerBin(4000f) until upperExclusiveBin(8000f)
    private val formantBands = arrayOf(
        lowerBin(250f) until upperExclusiveBin(800f),
        lowerBin(800f) until upperExclusiveBin(2000f),
        lowerBin(2000f) until upperExclusiveBin(4000f)
    )
    private val frequencies = FloatArray(FFT_SIZE / 2 + 1) { it * HZ_PER_BIN }

    data class Result(
        val accepted: Boolean,
        val rejectedReason: String = "",
        val activeFrames: Int = 0,
        val band2Fraction: Float = 0f,
        val band3Fraction: Float = 0f,
        val flatnessMedian: Float = 0f,
        val zcrMedian: Float = 0f,
        val centroidMedianHz: Float = 0f,
        val highRatioMedian: Float = 0f,
        val fluxMedian: Float = 0f,
        val syllableModulation: Float = 0f,
        val voicedFrameRatio: Float = 0f,
        val pitchChangeRate: Float = 0f,
        val harmonicityMedian: Float = 0f
    )

    /**
     * @param normalizedSamples peak-normalized 16 kHz mono samples
     * @param frameRms RMS per 20 ms frame, already computed on the normalized signal
     * @param frameZcr zero-crossing rate per frame
     * @param frameFlatness spectral flatness per frame on the 150-5000 Hz band
     * @param frameSpectra power spectra (257 bins) per frame
     */
    fun evaluate(
        normalizedSamples: FloatArray,
        frameRms: FloatArray,
        frameZcr: FloatArray,
        frameFlatness: FloatArray,
        frameSpectra: List<FloatArray>
    ): Result {
        val frameCount = minOf(frameRms.size, frameZcr.size, frameFlatness.size, frameSpectra.size)
        if (frameCount == 0 || normalizedSamples.isEmpty()) {
            return Result(accepted = false, rejectedReason = "NO_ACTIVE_SPEECH_FRAMES")
        }

        var maxRms = 0f
        for (v in frameRms) if (v > maxRms) maxRms = v
        val activeCut = ACTIVE_RMS_FRACTION * maxRms
        val activeFrames = ArrayList<Int>(frameCount)
        for (f in 0 until frameCount) {
            if (frameRms[f] >= activeCut) activeFrames.add(f)
        }
        if (activeFrames.isEmpty()) {
            return Result(accepted = false, rejectedReason = "NO_ACTIVE_SPEECH_FRAMES")
        }

        var band2Hits = 0
        var band3Hits = 0
        val flatValues = FloatArray(activeFrames.size)
        val zcrValues = FloatArray(activeFrames.size)
        val centroidValues = FloatArray(activeFrames.size)
        val highValues = FloatArray(activeFrames.size)
        val fluxValues = FloatArray(activeFrames.size)
        val flux = FloatArray(frameCount)

        for (f in 1 until frameCount) {
            val current = frameSpectra[f]
            val previous = frameSpectra[f - 1]
            var sum = 0.0
            for (k in current.indices) sum += abs(current[k].toDouble() - previous[k].toDouble())
            flux[f] = sum.toFloat()
        }

        for (i in activeFrames.indices) {
            val f = activeFrames[i]
            val spectrum = frameSpectra[f]
            var totalPower = 1e-20
            var centroidNumerator = 0.0
            for (k in totalBand) {
                val p = spectrum[k].toDouble()
                totalPower += p
                centroidNumerator += frequencies[k].toDouble() * p
            }
            var bandCount = 0
            for (band in formantBands) {
                var bandPower = 0.0
                for (k in band) bandPower += spectrum[k].toDouble()
                if (bandPower >= 0.06 * totalPower) bandCount++
            }
            if (bandCount >= 2) band2Hits++
            if (bandCount >= 3) band3Hits++
            flatValues[i] = frameFlatness[f]
            zcrValues[i] = frameZcr[f]
            centroidValues[i] = (centroidNumerator / totalPower).toFloat()
            var highPower = 0.0
            for (k in highBand) highPower += spectrum[k].toDouble()
            highValues[i] = (highPower / totalPower).toFloat()
            fluxValues[i] = flux[f]
        }

        val band2Fraction = band2Hits.toFloat() / activeFrames.size
        val band3Fraction = band3Hits.toFloat() / activeFrames.size
        val flatnessMedian = median(flatValues)
        val zcrMedian = median(zcrValues)
        val centroidMedian = median(centroidValues)
        val highMedian = median(highValues)
        val fluxMedian = median(fluxValues)
        val syllableModulation = syllableModulation(frameRms, frameCount)

        val pitch = pitchFeatures(normalizedSamples, activeFrames, frameCount)

        val spectralOk = flatnessMedian <= MAX_FLATNESS_MEDIAN &&
            band3Fraction <= MAX_BAND3_FRACTION &&
            band2Fraction <= MAX_BAND2_FRACTION &&
            highMedian <= MAX_HIGH_RATIO_MEDIAN &&
            centroidMedian <= MAX_CENTROID_HZ_MEDIAN
        val burst = fluxMedian >= BURST_FLUX_MEDIAN && band3Fraction >= BURST_BAND3_FRACTION
        val temporalOk = syllableModulation >= MIN_SYLLABLE_MODULATION || burst ||
            (pitch.changeRate >= MIN_PITCH_CHANGE_RATE &&
                pitch.harmonicityMedian >= PITCH_HARMONICITY_MIN &&
                activeFrames.size >= MIN_ACTIVE_FRAMES_FOR_CHANGE)
        val lengthOk = activeFrames.size >= MIN_ACTIVE_FRAMES || burst ||
            pitch.harmonicityMedian >= SHORT_HARMONICITY_MIN

        val rejectReason = when {
            !spectralOk -> "SPECTRAL_GLOBAL"
            pitch.harmonicityMedian < MIN_HARMONICITY_MEDIAN -> "HARMONICITY"
            !temporalOk -> "TEMPORAL"
            !lengthOk -> "SHORT_NON_SPEECH"
            band2Fraction < MIN_BAND2_FRACTION && flatnessMedian >= HIGH_FLATNESS -> "MUSIC_BAND2"
            flatnessMedian >= NOISE_FLATNESS && band3Fraction >= BURST_BAND3_FRACTION &&
                fluxMedian < BURST_FLUX_MEDIAN && zcrMedian < LOW_ZCR -> "NOISE_FLAT_BAND3"
            flatnessMedian >= LOW_FLATNESS && zcrMedian < LOW_ZCR &&
                fluxMedian < BURST_FLUX_MEDIAN -> "NOISE_FLAT_ZCR"
            else -> ""
        }

        return Result(
            accepted = rejectReason.isEmpty(),
            rejectedReason = rejectReason,
            activeFrames = activeFrames.size,
            band2Fraction = band2Fraction,
            band3Fraction = band3Fraction,
            flatnessMedian = flatnessMedian,
            zcrMedian = zcrMedian,
            centroidMedianHz = centroidMedian,
            highRatioMedian = highMedian,
            fluxMedian = fluxMedian,
            syllableModulation = syllableModulation,
            voicedFrameRatio = pitch.voicedRatio,
            pitchChangeRate = pitch.changeRate,
            harmonicityMedian = pitch.harmonicityMedian
        )
    }

    private data class PitchFeatures(
        val voicedRatio: Float,
        val changeRate: Float,
        val harmonicityMedian: Float
    )

    private fun pitchFeatures(
        normalizedSamples: FloatArray,
        activeFrames: List<Int>,
        frameCount: Int
    ): PitchFeatures {
        val f0s = ArrayList<Float>(activeFrames.size)
        val strengths = ArrayList<Float>(activeFrames.size)
        for (f in activeFrames) {
            val start = f * AudioProcessor.SAMPLES_PER_FRAME
            if (start >= normalizedSamples.size) continue
            val end = min(start + AudioProcessor.SAMPLES_PER_FRAME, normalizedSamples.size)
            val count = end - start
            val frame = FloatArray(AudioProcessor.SAMPLES_PER_FRAME)
            var mean = 0.0
            for (i in 0 until count) mean += normalizedSamples[start + i].toDouble()
            mean /= count
            var energy = 0.0
            for (i in 0 until count) {
                val v = normalizedSamples[start + i] - mean.toFloat()
                frame[i] = v
                energy += v.toDouble() * v
            }
            energy += 1e-20
            val corr = autocorrelation(frame)
            var bestLag = 0
            var bestValue = 0f
            val maxLag = min(PITCH_LAG_MAX, frame.size - 2)
            for (lag in PITCH_LAG_MIN until maxLag) {
                val value = (corr[lag].toDouble() / energy).toFloat()
                if (value > bestValue) {
                    bestValue = value
                    bestLag = lag
                }
            }
            if (bestValue >= MIN_NORMALIZED_AUTOCORRELATION && bestLag > 0) {
                f0s.add(SAMPLE_RATE_HZ.toFloat() / bestLag.toFloat())
                strengths.add(bestValue)
            }
        }

        var changeRate = 0f
        if (f0s.size >= 2) {
            val semitones = FloatArray(f0s.size) { (12.0 * ln(f0s[it].toDouble()) / ln(2.0)).toFloat() }
            var changes = 0
            for (i in 1 until semitones.size) {
                if (abs(semitones[i].toDouble() - semitones[i - 1].toDouble()) > ONE_SEMITONE) changes++
            }
            changeRate = changes.toFloat() / (semitones.size - 1)
        }
        return PitchFeatures(
            voicedRatio = f0s.size.toFloat() / max(1, activeFrames.size),
            changeRate = changeRate,
            harmonicityMedian = if (strengths.isEmpty()) 0f else median(strengths.toFloatArray())
        )
    }

    /** Linear autocorrelation via a zero-padded radix-2 FFT; index is lag in samples. */
    private fun autocorrelation(frame: FloatArray): FloatArray {
        var size = 1
        while (size < frame.size * 2) size *= 2
        val re = FloatArray(size)
        val im = FloatArray(size)
        for (i in frame.indices) re[i] = frame[i]
        fft(re, im, inverse = false)
        for (i in 0 until size) {
            val power = re[i].toDouble() * re[i] + im[i].toDouble() * im[i]
            re[i] = power.toFloat()
            im[i] = 0f
        }
        fft(re, im, inverse = true)
        for (i in 0 until size) re[i] /= size.toFloat()
        return re
    }

    private fun fft(re: FloatArray, im: FloatArray, inverse: Boolean) {
        val n = re.size
        var j = 0
        for (i in 1 until n) {
            var bit = n shr 1
            while (j and bit != 0) {
                j = j xor bit
                bit = bit shr 1
            }
            j = j xor bit
            if (i < j) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }
        var len = 2
        while (len <= n) {
            val angle = (if (inverse) 2.0 else -2.0) * PI / len
            val wLenRe = cos(angle).toFloat()
            val wLenIm = sin(angle).toFloat()
            var base = 0
            while (base < n) {
                var wr = 1f
                var wi = 0f
                val half = len / 2
                for (k in 0 until half) {
                    val a = base + k
                    val b = a + half
                    val vr = re[b] * wr - im[b] * wi
                    val vi = re[b] * wi + im[b] * wr
                    val ur = re[a]
                    val ui = im[a]
                    re[a] = ur + vr; im[a] = ui + vi
                    re[b] = ur - vr; im[b] = ui - vi
                    val nextWr = wr * wLenRe - wi * wLenIm
                    wi = wr * wLenIm + wi * wLenRe
                    wr = nextWr
                }
                base += len
            }
            len *= 2
        }
    }

    /** Fraction of 2-8 Hz energy in the RMS envelope, sampled at 50 frames/s. */
    private fun syllableModulation(frameRms: FloatArray, frameCount: Int): Float {
        if (frameCount < 2) return 0f
        var maxRms = 0f
        for (v in frameRms) if (v > maxRms) maxRms = v
        if (maxRms <= 0f) return 0f
        val normalized = FloatArray(frameCount) { frameRms[it] / maxRms }
        var mean = 0.0
        for (v in normalized) mean += v.toDouble()
        mean /= frameCount
        val windowed = FloatArray(frameCount)
        for (i in 0 until frameCount) {
            val w = 0.5 - 0.5 * cos(2.0 * PI * i / (frameCount - 1).toDouble())
            windowed[i] = ((normalized[i].toDouble() - mean) * w).toFloat()
        }

        val binHz = 50.0 / frameCount
        var signalPower = 0.0
        var modulationPower = 0.0
        val maxBin = frameCount / 2
        for (k in 1..maxBin) {
            val frequencyHz = k * binHz
            var re = 0.0
            var im = 0.0
            for (i in 0 until frameCount) {
                val angle = -2.0 * PI * k * i / frameCount
                val sample = windowed[i].toDouble()
                re += sample * cos(angle)
                im += sample * sin(angle)
            }
            val power = re * re + im * im
            signalPower += power
            if (frequencyHz >= 2.0 - 1e-9 && frequencyHz <= 8.0 + 1e-9) modulationPower += power
        }
        return (modulationPower / (signalPower + 1e-20)).toFloat()
    }

    private fun median(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        val sorted = values.copyOf()
        sorted.sort()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 1) {
            sorted[middle]
        } else {
            (sorted[middle - 1].toDouble() + sorted[middle].toDouble()).toFloat() / 2f
        }
    }

    private fun lowerBin(hz: Float): Int =
        max(0, min(FFT_SIZE / 2, ceil((hz / HZ_PER_BIN).toDouble() - 1e-9).toInt()))

    private fun upperExclusiveBin(hz: Float): Int =
        max(1, min(FFT_SIZE / 2 + 1, ceil((hz / HZ_PER_BIN).toDouble() - 1e-9).toInt()))
}
