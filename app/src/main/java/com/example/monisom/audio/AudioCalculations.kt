package com.example.monisom.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.sin
import kotlin.math.sqrt

object AudioCalculations {

    const val SAMPLE_RATE = 44_100

    private const val REFERENCE_DBFS = 32767.0
    private const val EPSILON = 1e-10

    // Offset de calibração dB FS -> dB SPL (estimado; ajustar por aparelho)
    var dbToSplOffset: Double = 80.0

    fun dbFsFromEnergy(sumSquares: Double, count: Int): Double {
        if (count <= 0) return -120.0
        val rms = sqrt(sumSquares / count)
        val normalized = rms / REFERENCE_DBFS
        val safe = if (normalized < EPSILON) EPSILON else normalized
        return 20.0 * log10(safe)
    }

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
    }

    private val biquads: List<Biquad>
    private val gain: Double

    init {
        val polesHz = doubleArrayOf(20.598997, 107.65265, 737.86223, 12194.217)
        val zps = DoubleArray(polesHz.size)
        biquads = polesHz.mapIndexed { i, f ->
            val w = 2.0 * PI * f
            val zp = (2.0 * sampleRate - w) / (2.0 * sampleRate + w)
            zps[i] = zp
            Biquad(1.0, 0.0, -1.0, -2.0 * zp, zp * zp)
        }
        gain = computeGain(zps)
    }

    private fun computeGain(zps: DoubleArray): Double {
        val theta = 2.0 * PI * 1000.0 / sampleRate
        val sinTheta = sin(theta)
        val cosTheta = cos(theta)
        var magnitude = 1.0
        for (zp in zps) {
            magnitude *= (2.0 * abs(sinTheta)) / (1.0 - 2.0 * zp * cosTheta + zp * zp)
        }
        return if (magnitude == 0.0) 1.0 else 1.0 / magnitude
    }

    fun filter(samples: DoubleArray): DoubleArray {
        for (i in samples.indices) {
            var y = samples[i]
            for (bq in biquads) {
                y = bq.process(y)
            }
            samples[i] = y * gain
        }
        return samples
    }
}
