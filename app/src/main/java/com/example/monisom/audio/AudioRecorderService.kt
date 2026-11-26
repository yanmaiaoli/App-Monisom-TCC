package com.example.monisom.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
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

private const val TAG = "MoniSom"

class AudioRecorderService(
    private val sampleRate: Int = AudioCalculations.SAMPLE_RATE,
    private val samplesPerBuffer: Int = 2048,
) {

    private val aWeighting = AWeightingFilter(sampleRate)

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
            Log.e(TAG, "Falha ao inicializar o microfone")
            throw IllegalStateException("Falha ao inicializar o microfone")
        }

        Log.d(TAG, "AudioRecord inicializado (monitoringId=$monitoringId, buffer=$bufferSize)")
        _isRecording.value = true

        var emittedCount = 0

        try {
            audioRecord.startRecording()
            Log.d(TAG, "Gravação iniciada (monitoringId=$monitoringId)")
            val shortBuffer = ShortArray(samplesPerBuffer)
            val doubleBuffer = DoubleArray(samplesPerBuffer)

            val accumulator = LevelAccumulator()
            var lastSecondTime = System.currentTimeMillis()
            var lastHeartbeat = System.currentTimeMillis()

            while (currentCoroutineContext().isActive) {
                val read = audioRecord.read(shortBuffer, 0, shortBuffer.size)
                if (read < 0) {
                    Log.e(TAG, "Erro na leitura do microfone (read=$read)")
                    throw IllegalStateException("Erro na leitura do microfone")
                }
                if (read == 0) continue

                for (i in 0 until read) {
                    doubleBuffer[i] = shortBuffer[i].toDouble()
                }
                aWeighting.filter(doubleBuffer, read)

                var sumSquares = 0.0
                for (i in 0 until read) {
                    val v = doubleBuffer[i]
                    sumSquares += v * v
                }

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
                    emittedCount++
                }

                if (now - lastHeartbeat >= 10000) {
                    lastHeartbeat = now
                    Log.d(TAG, "Gravação ativa (monitoringId=$monitoringId, leituras emitidas=$emittedCount)")
                }
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "Permissão de microfone não concedida", e)
            throw IllegalStateException("Permissão de microfone não concedida")
        } finally {
            _isRecording.value = false
            audioRecord.release()
            Log.d(TAG, "Gravação encerrada (monitoringId=$monitoringId, leituras emitidas=$emittedCount)")
        }
    }.flowOn(Dispatchers.IO)
}
