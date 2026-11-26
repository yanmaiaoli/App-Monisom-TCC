package com.example.monisom.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

object AudioCalculations {

    const val SAMPLE_RATE = 44_100

    private const val REFERENCE_DBFS = 32767.0
    private const val EPSILON = 1e-10

    // Offset de calibração dB FS -> dB SPL (estimado comparando com o NoiseCapture)
    var dbToSplOffset: Double = 93.0

    // RMS das amostras, normalizado pelo fundo de escala, em dBFS
    fun dbFsFromEnergy(sumSquares: Double, count: Int): Double {
        if (count <= 0) return -120.0
        val rms = sqrt(sumSquares / count)
        val normalized = rms / REFERENCE_DBFS
        val safe = if (normalized < EPSILON) EPSILON else normalized
        return 20.0 * log10(safe)
    }

    // soma a constante de calibração para obter o nível de pressão sonora
    fun dbFsToDbSpl(dbFs: Double): Double = dbFs + dbToSplOffset

    fun dbSplFromEnergy(sumSquares: Double, count: Int): Double =
        dbFsToDbSpl(dbFsFromEnergy(sumSquares, count))
}

class LevelAccumulator {

    private var sumSquares = 0.0
    private var count = 0

    val samples: Int get() = count

    fun add(bufferSumSquares: Double, bufferSamples: Int) {
        sumSquares += bufferSumSquares
        count += bufferSamples
    }

    fun reset() {
        sumSquares = 0.0
        count = 0
    }

    // converte a energia acumulada em nível de pressão sonora (dB)
    fun dbSpl(): Double = AudioCalculations.dbSplFromEnergy(sumSquares, count)
}

class AWeightingFilter(private val sampleRate: Int) {

    private class Biquad(
        val b0: Double,
        val b1: Double,
        val b2: Double,
        val a1: Double,
        val a2: Double,
    ) {
        private var x1 = 0.0
        private var x2 = 0.0
        private var y1 = 0.0
        private var y2 = 0.0

        fun process(x: Double): Double {
            val y = b0 * x + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
            x2 = x1
            x1 = x
            y2 = y1
            y1 = y
            return y
        }

        fun magnitude(theta: Double): Double {
            val c1 = cos(theta)
            val s1 = sin(theta)
            val c2 = cos(2 * theta)
            val s2 = sin(2 * theta)
            val nRe = b0 + b1 * c1 + b2 * c2
            val nIm = -(b1 * s1 + b2 * s2)
            val dRe = 1.0 + a1 * c1 + a2 * c2
            val dIm = -(a1 * s1 + a2 * s2)
            return sqrt((nRe * nRe + nIm * nIm) / (dRe * dRe + dIm * dIm))
        }
    }

    private val biquads: List<Biquad>
    private val gain: Double

    init {
        val f1 = 20.598997
        val f2 = 107.65265
        val f3 = 737.86223
        val f4 = 12194.217

        fun zp(f: Double): Double {
            val w = 2.0 * sampleRate * tan(PI * f / sampleRate)
            return (2.0 * sampleRate - w) / (2.0 * sampleRate + w)
        }

        val z1 = zp(f1)
        val z2 = zp(f2)
        val z3 = zp(f3)
        val z4 = zp(f4)

        biquads = listOf(
            Biquad(1.0, -2.0, 1.0, -2.0 * z1, z1 * z1),
            Biquad(1.0, 2.0, 1.0, -2.0 * z4, z4 * z4),
            Biquad(1.0, -2.0, 1.0, -(z2 + z3), z2 * z3),
        )

        val theta = 2.0 * PI * 1000.0 / sampleRate
        val mag = biquads.fold(1.0) { acc, b -> acc * b.magnitude(theta) }
        gain = if (mag == 0.0) 1.0 else 1.0 / mag
    }

    fun filter(samples: DoubleArray, length: Int = samples.size): DoubleArray {
        for (i in 0 until length) {
            var y = samples[i]
            for (bq in biquads) {
                y = bq.process(y)
            }
            samples[i] = y * gain
        }
        return samples
    }
}
