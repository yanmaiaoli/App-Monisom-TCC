package com.example.monisom.ui.monitoring

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.monisom.MoniSomApplication
import com.example.monisom.audio.RecordingService
import com.example.monisom.audio.RecordingState
import com.example.monisom.data.Monitoring
import com.example.monisom.data.Repository
import com.example.monisom.data.supabase
import com.example.monisom.ui.common.friendlyError
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
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

    private val _state = MutableStateFlow(
        MonitoringUiState(
            phase = RecordingState.phase.value,
            instantLevelDb = RecordingState.instantLevelDb.value,
            elapsedSeconds = RecordingState.elapsedSeconds.value,
            perception = RecordingState.perception.value,
            stress = RecordingState.stress.value,
            error = RecordingState.error.value,
            done = RecordingState.done.value,
        )
    )
    val state: StateFlow<MonitoringUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            RecordingState.phase.collect { phase ->
                _state.update { it.copy(phase = phase) }
            }
        }
        viewModelScope.launch {
            RecordingState.instantLevelDb.collect { db ->
                _state.update { it.copy(instantLevelDb = db) }
            }
        }
        viewModelScope.launch {
            RecordingState.elapsedSeconds.collect { seconds ->
                _state.update { it.copy(elapsedSeconds = seconds) }
            }
        }
        viewModelScope.launch {
            RecordingState.perception.collect { perception ->
                _state.update { it.copy(perception = perception) }
            }
        }
        viewModelScope.launch {
            RecordingState.stress.collect { stress ->
                _state.update { it.copy(stress = stress) }
            }
        }
        viewModelScope.launch {
            RecordingState.error.collect { error ->
                _state.update { it.copy(error = error) }
            }
        }
        viewModelScope.launch {
            RecordingState.done.collect { done ->
                _state.update { it.copy(done = done) }
            }
        }
    }

    fun start(schoolId: String, classId: String, roomId: String) {
        if (RecordingState.isRecording.value) return
        viewModelScope.launch {
            if (!networkMonitor.isOnline()) {
                RecordingState.error.value = "Sem conexão com a internet."
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

                RecordingState.monitoringId.value = id
                RecordingState.schoolId.value = schoolId
                RecordingState.classId.value = classId
                RecordingState.roomId.value = roomId
                RecordingState.phase.value = MonitoringPhase.Recording
                RecordingState.error.value = null

                RecordingService.start(getApplication(), id, schoolId, classId, roomId)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                RecordingState.error.value = friendlyError(e)
            }
        }
    }

    fun finishMeasurement() {
        RecordingService.stop(getApplication())
    }

    fun onPerceptionChange(value: Int) {
        RecordingState.perception.value = value
        RecordingState.error.value = null
    }

    fun onStressChange(value: Int) {
        RecordingState.stress.value = value
        RecordingState.error.value = null
    }

    fun finalize() {
        val perception = RecordingState.perception.value
        val stress = RecordingState.stress.value
        if (perception == null || stress == null) {
            RecordingState.error.value = "Responda as duas perguntas antes de finalizar."
            return
        }
        doFinalize(perception, stress)
    }

    fun finalizeWithoutAnswers() {
        doFinalize(null, null)
    }

    private fun doFinalize(perception: Int?, stress: Int?) {
        val id = RecordingState.monitoringId.value ?: return
        viewModelScope.launch {
            RecordingState.phase.value = MonitoringPhase.Saving
            try {
                Repository.updateMonitoring(id, Instant.now().toString(), perception, stress)
                RecordingState.reset()
                RecordingState.done.value = true
            } catch (e: Exception) {
                RecordingState.phase.value = MonitoringPhase.Questionnaire
                RecordingState.error.value = friendlyError(e)
            }
        }
    }
}
