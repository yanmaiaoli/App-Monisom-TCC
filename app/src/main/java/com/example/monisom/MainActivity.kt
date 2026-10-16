package com.example.monisom

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.example.monisom.audio.RecordingService
import com.example.monisom.ui.MoniSomApp
import com.example.monisom.ui.theme.MoniSomTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {

    private val pendingSchoolId = MutableStateFlow<String?>(null)
    private val pendingClassId = MutableStateFlow<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleIntent(intent)
        enableEdgeToEdge()
        setContent {
            MoniSomTheme {
                val schoolId by pendingSchoolId.collectAsState()
                val classId by pendingClassId.collectAsState()
                MoniSomApp(
                    pendingSchoolId = schoolId,
                    pendingClassId = classId,
                    onPendingConsumed = {
                        pendingSchoolId.value = null
                        pendingClassId.value = null
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        val schoolId = intent?.getStringExtra(EXTRA_SCHOOL_ID)
        val classId = intent?.getStringExtra(EXTRA_CLASS_ID)
        if (schoolId != null && classId != null) {
            if (intent.getBooleanExtra(EXTRA_STOP_RECORDING, false)) {
                RecordingService.stop(this)
            }
            pendingSchoolId.value = schoolId
            pendingClassId.value = classId
        }
    }

    companion object {
        const val EXTRA_SCHOOL_ID = "schoolId"
        const val EXTRA_CLASS_ID = "classId"
        const val EXTRA_STOP_RECORDING = "stopRecording"
    }
}
