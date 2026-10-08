package com.example.monisom.ui.monitoring

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MeetingRoom
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.monisom.data.Repository
import com.example.monisom.data.Room
import com.example.monisom.ui.common.friendlyError
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonitoringScreen(
    schoolId: String,
    classId: String,
    onFinished: () -> Unit,
    viewModel: MonitoringViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    var rooms by remember { mutableStateOf<List<Room>?>(null) }
    var roomsError by remember { mutableStateOf<String?>(null) }
    var reloadKey by remember { mutableStateOf(0) }
    var selectedRoomId by remember { mutableStateOf<String?>(null) }
    var showEndDialog by remember { mutableStateOf(false) }
    var showUnansweredDialog by remember { mutableStateOf(false) }

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasPermission = granted }

    LaunchedEffect(schoolId, reloadKey) {
        rooms = null
        roomsError = null
        try {
            rooms = Repository.getRoomsForSchool(schoolId)
        } catch (e: Exception) {
            rooms = emptyList()
            roomsError = friendlyError(e)
        }
    }

    LaunchedEffect(selectedRoomId) {
        if (selectedRoomId != null && !hasPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(hasPermission, selectedRoomId, classId) {
        val roomId = selectedRoomId
        if (hasPermission && roomId != null) viewModel.start(classId, roomId)
    }

    LaunchedEffect(state.done) {
        if (state.done) onFinished()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Monitoramento") },
                navigationIcon = {
                    IconButton(onClick = {
                        val roomId = selectedRoomId
                        if (roomId == null || !hasPermission) {
                            onFinished()
                        } else {
                            when (state.phase) {
                                MonitoringPhase.Recording -> showEndDialog = true
                                MonitoringPhase.Questionnaire -> {
                                    val p = state.perception
                                    val s = state.stress
                                    if (p != null && s != null) viewModel.finalize()
                                    else showUnansweredDialog = true
                                }
                                MonitoringPhase.Saving -> Unit
                            }
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Voltar")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            val roomsList = rooms
            val roomsErrorValue = roomsError
            val roomId = selectedRoomId
            when {
                roomsList == null -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
                roomsErrorValue != null -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center).padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = roomsErrorValue,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(onClick = { reloadKey++ }) { Text("Tentar novamente") }
                    }
                }
                roomId == null -> {
                    if (roomsList.isEmpty()) {
                        Column(
                            modifier = Modifier.align(Alignment.Center).padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                text = "Nenhuma sala cadastrada para esta escola.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        RoomSelectionContent(
                            rooms = roomsList,
                            onSelect = { selectedRoomId = it },
                        )
                    }
                }
                !hasPermission -> {
                    PermissionContent(
                        onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
                    )
                }
                else -> {
                    when (state.phase) {
                        MonitoringPhase.Recording -> RecordingContent(
                            instantLevelDb = state.instantLevelDb,
                            elapsedSeconds = state.elapsedSeconds,
                            error = state.error,
                            onFinish = { showEndDialog = true },
                        )
                        MonitoringPhase.Questionnaire -> QuestionnaireContent(
                            perception = state.perception,
                            stress = state.stress,
                            error = state.error,
                            onPerceptionChange = viewModel::onPerceptionChange,
                            onStressChange = viewModel::onStressChange,
                            onFinalize = viewModel::finalize,
                        )
                        MonitoringPhase.Saving -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally,
                            ) {
                                CircularProgressIndicator()
                                Spacer(Modifier.height(16.dp))
                                Text("Salvando monitoramento...")
                            }
                        }
                    }
                }
            }
        }
    }

    if (showEndDialog) {
        AlertDialog(
            onDismissRequest = { showEndDialog = false },
            title = { Text("Encerrar monitoramento?") },
            text = { Text("As leituras coletadas serão salvas. Deseja encerrar agora?") },
            confirmButton = {
                TextButton(onClick = {
                    showEndDialog = false
                    viewModel.finishMeasurement()
                }) { Text("Encerrar") }
            },
            dismissButton = {
                TextButton(onClick = { showEndDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (showUnansweredDialog) {
        AlertDialog(
            onDismissRequest = { showUnansweredDialog = false },
            title = { Text("Finalizar sem responder?") },
            text = { Text("As perguntas de percepção e estresse ficarão em branco. Deseja finalizar mesmo assim?") },
            confirmButton = {
                TextButton(onClick = {
                    showUnansweredDialog = false
                    viewModel.finalizeWithoutAnswers()
                }) { Text("Finalizar sem responder") }
            },
            dismissButton = {
                TextButton(onClick = { showUnansweredDialog = false }) { Text("Cancelar") }
            },
        )
    }
}

@Composable
private fun RoomSelectionContent(
    rooms: List<Room>,
    onSelect: (String) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text(
            text = "Em qual sala você está?",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Selecione a sala onde o monitoramento será realizado.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(16.dp))
        LazyColumn(
            contentPadding = PaddingValues(bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(rooms, key = { it.id }) { room ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(room.id) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = Icons.Filled.MeetingRoom,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.size(12.dp))
                        Text(
                            text = room.name,
                            style = MaterialTheme.typography.titleMedium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionContent(onRequest: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = "Para medir o ruído do ambiente, o MoniSom precisa de acesso ao microfone.",
            style = MaterialTheme.typography.bodyLarge,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onRequest) { Text("Conceder permissão") }
    }
}

@Composable
private fun RecordingContent(
    instantLevelDb: Double,
    elapsedSeconds: Int,
    error: String?,
    onFinish: () -> Unit,
) {
    val minutes = elapsedSeconds / 60
    val seconds = elapsedSeconds % 60
    val time = "%02d:%02d".format(minutes, seconds)
    val db = (instantLevelDb * 10).roundToInt() / 10.0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Mic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            text = "Medindo ruído do ambiente...",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = time,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.Bold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "$db dB(A)",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
        )
        if (error != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.error,
                contentColor = MaterialTheme.colorScheme.onError,
            ),
        ) {
            Text("Encerrar medição")
        }
    }
}

@Composable
private fun QuestionnaireContent(
    perception: Int?,
    stress: Int?,
    error: String?,
    onPerceptionChange: (Int) -> Unit,
    onStressChange: (Int) -> Unit,
    onFinalize: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
    ) {
        Text(
            text = "Como você avaliaria o ambiente agora?",
            style = MaterialTheme.typography.titleLarge,
        )
        Spacer(Modifier.height(24.dp))

        RatingQuestion(
            title = "Percepção de ruído",
            description = "0 = muito silencioso · 5 = muito barulhento",
            value = perception,
            onSelect = onPerceptionChange,
        )
        Spacer(Modifier.height(24.dp))

        RatingQuestion(
            title = "Nível de estresse",
            description = "0 = nada estressado · 5 = muito estressado",
            value = stress,
            onSelect = onStressChange,
        )

        if (error != null) {
            Spacer(Modifier.height(16.dp))
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
        }

        Spacer(Modifier.weight(1f))
        Button(onClick = onFinalize, modifier = Modifier.fillMaxWidth()) {
            Text("Finalizar monitoramento")
        }
    }
}

@Composable
private fun RatingQuestion(
    title: String,
    description: String,
    value: Int?,
    onSelect: (Int) -> Unit,
) {
    Column {
        Text(text = title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            for (i in 0..5) {
                val selected = value == i
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            if (selected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            }
                        )
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = i.toString(),
                        color = if (selected) {
                            MaterialTheme.colorScheme.onPrimary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
        if (value != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Selecionado: $value",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
