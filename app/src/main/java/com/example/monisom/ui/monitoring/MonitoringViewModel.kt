package com.example.monisom.ui.monitoring

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.monisom.MoniSomApplication
import com.example.monisom.audio.AudioRecorderService
import com.example.monisom.data.Monitoring
import com.example.monisom.data.MonitoringReading
import com.example.monisom.data.Repository
import com.example.monisom.data.supabase
import com.example.monisom.ui.common.friendlyError
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant

enum class MonitoringPhase { Recording, Questionnaire, Saving }

data class MonitoringUiState(
    val phase: MonitoringPhase = MonitoringPhase.Recording,
    val instantLevelDb: Double = 0.0,
    val elapsedSeconds: Int = 0,
    val perception: Int? = null,
    val stress: Int? = null,
    val error: String? = null,
    val done: Boolean = false,
)

class MonitoringViewModel(application: Application) : AndroidViewModel(application) {

    private val networkMonitor = (application as MoniSomApplication).networkMonitor
    private val audioRecorder = AudioRecorderService()

    private val _state = MutableStateFlow(MonitoringUiState())
    val state: StateFlow<MonitoringUiState> = _state.asStateFlow()

    private var recordingJob: Job? = null
    private var timerJob: Job? = null
    private var monitoringId: String? = null
    private val pendingReadings = mutableListOf<MonitoringReading>()

    init {
        viewModelScope.launch {
            audioRecorder.instantLevelDb.collect { db ->
                _state.update { it.copy(instantLevelDb = db) }
            }
        }
    }

    fun start(classId: String, roomId: String) {
        if (recordingJob?.isActive == true) return
        recordingJob = viewModelScope.launch {
            _state.value = MonitoringUiState()
            if (!networkMonitor.isOnline()) {
                _state.update { it.copy(error = "Sem conexão com a internet.") }
                return@launch
            }
            try {
                val teacherId = supabase.auth.currentUserOrNull()?.id
                    ?: error("Sessão não encontrada")
                val created = Repository.createMonitoring(
                    Monitoring(
                        classId = classId,
                        teacherId = teacherId,
                        roomId = roomId,
                        startedAt = Instant.now().toString(),
                    )
                )
                val id = created.id ?: error("Falha ao criar o monitoramento")
                monitoringId = id

                timerJob = launch {
                    while (isActive) {
                        delay(1000)
                        _state.update { it.copy(elapsedSeconds = it.elapsedSeconds + 1) }
                    }
                }

                audioRecorder.start(id).collect { reading ->
                    pendingReadings.add(reading)
                    if (pendingReadings.size >= BUFFER_SIZE) flushBuffer()
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.update { it.copy(error = friendlyError(e)) }
            }
        }
    }

    fun finishMeasurement() {
        recordingJob?.cancel()
        timerJob?.cancel()
        viewModelScope.launch {
            flushBuffer()
            _state.update { it.copy(phase = MonitoringPhase.Questionnaire) }
        }
    }

    fun onPerceptionChange(value: Int) {
        _state.update { it.copy(perception = value, error = null) }
    }

    fun onStressChange(value: Int) {
        _state.update { it.copy(stress = value, error = null) }
    }

    fun finalize() {
        val current = _state.value
        val perception = current.perception
        val stress = current.stress

        if (perception == null || stress == null) {
            _state.update { it.copy(error = "Responda as duas perguntas antes de finalizar.") }
            return
        }
        doFinalize(perception, stress)
    }

    fun finalizeWithoutAnswers() {
        doFinalize(null, null)
    }

    private fun doFinalize(perception: Int?, stress: Int?) {
        val id = monitoringId ?: return

        viewModelScope.launch {
            _state.update { it.copy(phase = MonitoringPhase.Saving, error = null) }
            try {
                flushBuffer()
                Repository.updateMonitoring(id, Instant.now().toString(), perception, stress)
                _state.update { it.copy(done = true) }
            } catch (e: Exception) {
                _state.update {
                    it.copy(phase = MonitoringPhase.Questionnaire, error = friendlyError(e))
                }
            }
        }
    }

    private suspend fun flushBuffer() {
        if (pendingReadings.isEmpty()) return
        val batch = pendingReadings.toList()
        pendingReadings.clear()
        try {
            Repository.insertReadings(batch)
        } catch (e: Exception) {
            pendingReadings.addAll(0, batch)
        }
    }

    override fun onCleared() {
        super.onCleared()
        recordingJob?.cancel()
        timerJob?.cancel()
    }

    private companion object {
        const val BUFFER_SIZE = 30
    }
}
