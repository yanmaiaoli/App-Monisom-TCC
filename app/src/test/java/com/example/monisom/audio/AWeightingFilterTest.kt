package com.example.monisom.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

class AWeightingFilterTest {    

    private val sampleRate = 44_100

    @Test
    fun aWeighting_matchesStandardAtNominalFrequencies() {
        val filter = AWeightingFilter(sampleRate)

        // Valores da Tabela 3 da NBR IEC 61672-1 (ponderação A).
        val expected = mapOf(
            63 to -26.2,
            125 to -16.1,
            250 to -8.6,
            500 to -3.2,
            1000 to 0.0,
            2000 to 1.2,
            4000 to 1.0,
        )

        for ((freq, targetDb) in expected) {
            val measuredDb = measureGainDb(filter, freq)
            val diff = Math.abs(measuredDb - targetDb)
            println(String.format("Ponderacao A - %d Hz: esperado=%.1f dB, obtido=%.2f dB, diferenca=%.2f dB", freq, targetDb, measuredDb, diff))
            assertEquals(
                "Falha em $freq Hz",
                targetDb,
                measuredDb,
                1.0,
            )
        }
    }

    private fun measureGainDb(filter: AWeightingFilter, freq: Int): Double {
        // Gera 1 s de senoide; descarta o primeiro 0,5 s (transitório do filtro).
        val total = sampleRate
        val discard = sampleRate / 2
        val amplitude = 10000.0

        val input = DoubleArray(total) { i ->
            amplitude * sin(2.0 * PI * freq * i / sampleRate)
        }

        val output = filter.filter(input.copyOf())

        var inRms = 0.0
        var outRms = 0.0
        for (i in discard until total) {
            inRms += input[i] * input[i]
            outRms += output[i] * output[i]
        }
        val n = total - discard
        val rmsIn = sqrt(inRms / n)
        val rmsOut = sqrt(outRms / n)

        return 20.0 * kotlin.math.log10(rmsOut / rmsIn)
    }
}
