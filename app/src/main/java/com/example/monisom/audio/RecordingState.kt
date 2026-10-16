package com.example.monisom.audio

import com.example.monisom.ui.monitoring.MonitoringPhase
import kotlinx.coroutines.flow.MutableStateFlow

object RecordingState {

    val instantLevelDb = MutableStateFlow(0.0)
    val isRecording = MutableStateFlow(false)
    val elapsedSeconds = MutableStateFlow(0)

    val monitoringId = MutableStateFlow<String?>(null)
    val schoolId = MutableStateFlow<String?>(null)
    val classId = MutableStateFlow<String?>(null)
    val roomId = MutableStateFlow<String?>(null)

    val phase = MutableStateFlow(MonitoringPhase.Recording)
    val perception = MutableStateFlow<Int?>(null)
    val stress = MutableStateFlow<Int?>(null)
    val error = MutableStateFlow<String?>(null)
    val done = MutableStateFlow(false)

    fun reset() {
        instantLevelDb.value = 0.0
        isRecording.value = false
        elapsedSeconds.value = 0
        monitoringId.value = null
        schoolId.value = null
        classId.value = null
        roomId.value = null
        phase.value = MonitoringPhase.Recording
        perception.value = null
        stress.value = null
        error.value = null
        done.value = false
    }
}
