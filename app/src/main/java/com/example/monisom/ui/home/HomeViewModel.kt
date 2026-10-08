package com.example.monisom.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.monisom.data.ClassGroup
import com.example.monisom.data.Repository
import com.example.monisom.data.School
import com.example.monisom.data.supabase
import com.example.monisom.ui.common.friendlyError
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SchoolWithClasses(
    val school: School,
    val classes: List<ClassGroup>,
)

data class HomeUiState(
    val isLoading: Boolean = true,
    val teacherName: String = "",
    val schools: List<SchoolWithClasses> = emptyList(),
    val error: String? = null,
)

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeUiState())
    val state: StateFlow<HomeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.value = HomeUiState(isLoading = true)
            try {
                val teacherId = supabase.auth.currentUserOrNull()?.id
                    ?: error("Sessão não encontrada")
                val teacher = Repository.getTeacher(teacherId)
                val schools = Repository.getSchoolsForTeacher(teacherId)
                val result = schools.map { school ->
                    SchoolWithClasses(
                        school = school,
                        classes = Repository.getClassesForTeacherInSchool(teacherId, school.id),
                    )
                }
                _state.value = HomeUiState(
                    isLoading = false,
                    teacherName = teacher?.name.orEmpty(),
                    schools = result,
                )
            } catch (e: Exception) {
                _state.value = HomeUiState(isLoading = false, error = friendlyError(e))
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            runCatching { supabase.auth.signOut() }
        }
    }
}
