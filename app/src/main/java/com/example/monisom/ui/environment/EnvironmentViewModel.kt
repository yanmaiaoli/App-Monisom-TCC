package com.example.monisom.ui.environment

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monisom.data.Monitoring
import com.example.monisom.data.Repository
import com.example.monisom.data.Room
import com.example.monisom.data.supabase
import com.example.monisom.ui.common.friendlyError
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EnvironmentUiState(
    val isLoading: Boolean = true,
    val monitorings: List<Monitoring> = emptyList(),
    val rooms: List<Room> = emptyList(),
    val error: String? = null,
)

class EnvironmentViewModel : ViewModel() {

    private val _state = MutableStateFlow(EnvironmentUiState())
    val state: StateFlow<EnvironmentUiState> = _state.asStateFlow()

    private var schoolId: String? = null
    private var classId: String? = null

    fun load(schoolId: String, classId: String) {
        this.schoolId = schoolId
        this.classId = classId
        refresh()
    }

    fun refresh() {
        val sid = schoolId ?: return
        val cid = classId ?: return
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val teacherId = supabase.auth.currentUserOrNull()?.id
                    ?: error("Sessão não encontrada")
                val monitorings = Repository.getMonitorings(cid, teacherId)
                val rooms = Repository.getRoomsForSchool(sid)
                _state.value = EnvironmentUiState(
                    isLoading = false,
                    monitorings = monitorings,
                    rooms = rooms,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = friendlyError(e))
            }
        }
    }
}
