package com.example.monisom.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import com.example.monisom.data.MonitoringReading
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import java.time.Instant
import kotlin.math.exp

class AudioRecorderService(
    private val sampleRate: Int = AudioCalculations.SAMPLE_RATE,
    private val samplesPerBuffer: Int = 2048,
) {

    private val aWeighting = AWeightingFilter(sampleRate)

    private val _instantLevelDb = MutableStateFlow(0.0)
    val instantLevelDb: StateFlow<Double> = _instantLevelDb.asStateFlow()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    @SuppressLint("MissingPermission")
    fun start(monitoringId: String): Flow<MonitoringReading> = flow {
        val bufferSize = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val audioRecord = AudioRecord(
            MediaRecorder.AudioSource.MIC,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT,
            bufferSize
        )

        if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
            audioRecord.release()
            throw IllegalStateException("Falha ao inicializar o microfone")
        }

        _isRecording.value = true

        try {
            audioRecord.startRecording()
            val shortBuffer = ShortArray(samplesPerBuffer)
            val doubleBuffer = DoubleArray(samplesPerBuffer)

            val accumulator = LevelAccumulator()
            var fastMeanSquare = 0.0
            var lastSecondTime = System.currentTimeMillis()

            while (currentCoroutineContext().isActive) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read <= 0) continue

                for (i in 0 until read) {
                    doubleBuffer[i] = shortBuffer[i].toDouble()
                }
                aWeighting.filter(doubleBuffer)

                var sumSquares = 0.0
                for (i in 0 until read) {
                    val v = doubleBuffer[i]
                    sumSquares += v * v
                }

                // nível instantâneo (só exibição), suavizado com ponderação "Fast" (~125 ms)
                val meanSquare = sumSquares / read
                val dt = read.toDouble() / sampleRate
                val alpha = 1.0 - exp(-dt / 0.125)
                fastMeanSquare = if (fastMeanSquare == 0.0) meanSquare
                    else fastMeanSquare * (1.0 - alpha) + meanSquare * alpha
                _instantLevelDb.value =
                    AudioCalculations.dbFsToDbSpl(AudioCalculations.dbFsFromEnergy(fastMeanSquare, 1))

                // acumula energia do segundo
                accumulator.add(sumSquares, read)

                val now = System.currentTimeMillis()
                if (now - lastSecondTime >= 1000) {
                    lastSecondTime = now
                    val reading = MonitoringReading(
                        monitoringId = monitoringId,
                        recordedAt = Instant.now().toString(),
                        laeq = accumulator.dbSpl(),
                    )
                    emit(reading)
                    accumulator.reset()
                }
            }
        } catch (e: SecurityException) {
            throw IllegalStateException("Permissão de microfone não concedida")
        } finally {
            _isRecording.value = false
            audioRecord.release()
        }
    }.flowOn(Dispatchers.IO)
}
