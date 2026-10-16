package com.example.monisom.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat
import com.example.monisom.MainActivity
import com.example.monisom.R
import com.example.monisom.data.MonitoringReading
import com.example.monisom.data.Repository
import com.example.monisom.ui.monitoring.MonitoringPhase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class RecordingService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val audioRecorder = AudioRecorderService()

    private var recordingJob: Job? = null
    private var levelJob: Job? = null
    private var timerJob: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    private val bufferMutex = Mutex()
    private val pendingReadings = mutableListOf<MonitoringReading>()

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> stopRecording()
            else -> startRecording(intent)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        recordingJob?.cancel()
        levelJob?.cancel()
        timerJob?.cancel()
        releaseWakeLock()
        scope.cancel()
        super.onDestroy()
    }

    private fun startRecording(intent: Intent?) {
        if (RecordingState.isRecording.value) return

        val monitoringId = intent?.getStringExtra(EXTRA_MONITORING_ID) ?: return
        val schoolId = intent?.getStringExtra(EXTRA_SCHOOL_ID).orEmpty()
        val classId = intent?.getStringExtra(EXTRA_CLASS_ID).orEmpty()
        val roomId = intent?.getStringExtra(EXTRA_ROOM_ID).orEmpty()

        RecordingState.monitoringId.value = monitoringId
        RecordingState.schoolId.value = schoolId
        RecordingState.classId.value = classId
        RecordingState.roomId.value = roomId
        RecordingState.phase.value = MonitoringPhase.Recording
        RecordingState.elapsedSeconds.value = 0
        RecordingState.error.value = null
        RecordingState.isRecording.value = true

        startForegroundWithType(buildNotification(monitoringId, schoolId, classId))
        acquireWakeLock()

        levelJob = scope.launch {
            audioRecorder.instantLevelDb.collect { RecordingState.instantLevelDb.value = it }
        }

        recordingJob = scope.launch {
            audioRecorder.start(monitoringId).collect { reading ->
                addReading(reading)
            }
        }

        timerJob = scope.launch {
            while (isActive) {
                delay(1_000)
                RecordingState.elapsedSeconds.value += 1
            }
        }
    }

    private fun stopRecording() {
        recordingJob?.cancel()
        levelJob?.cancel()
        timerJob?.cancel()
        RecordingState.isRecording.value = false
        RecordingState.phase.value = MonitoringPhase.Questionnaire
        releaseWakeLock()

        scope.launch {
            flushAll()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private suspend fun addReading(reading: MonitoringReading) {
        bufferMutex.withLock {
            pendingReadings.add(reading)
            if (pendingReadings.size >= BUFFER_SIZE) {
                flushLocked()
            }
        }
    }

    private suspend fun flushAll() {
        bufferMutex.withLock {
            flushLocked()
        }
    }

    private suspend fun flushLocked() {
        if (pendingReadings.isEmpty()) return
        val batch = pendingReadings.toList()
        pendingReadings.clear()
        try {
            Repository.insertReadings(batch)
        } catch (e: Exception) {
            pendingReadings.addAll(0, batch)
        }
    }

    private fun startForegroundWithType(notification: Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildNotification(monitoringId: String, schoolId: String, classId: String): Notification {
        createChannel()

        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_SCHOOL_ID, schoolId)
                putExtra(MainActivity.EXTRA_CLASS_ID, classId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopIntent = PendingIntent.getActivity(
            this,
            1,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                putExtra(MainActivity.EXTRA_SCHOOL_ID, schoolId)
                putExtra(MainActivity.EXTRA_CLASS_ID, classId)
                putExtra(MainActivity.EXTRA_STOP_RECORDING, true)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val stopAction = Notification.Action.Builder(
            Icon.createWithResource(this, android.R.drawable.ic_media_pause),
            "Parar",
            stopIntent,
        ).build()

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Monitoramento em andamento")
            .setContentText("Gravando áudio da sala")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(contentIntent)
            .addAction(stopAction)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Monitoramento",
            NotificationManager.IMPORTANCE_LOW,
        )
        channel.description = "Notificação de monitoramento de ruído em andamento"
        manager.createNotificationChannel(channel)
    }

    private fun acquireWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "monisom:recording")
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    companion object {
        const val ACTION_STOP = "com.example.monisom.action.STOP"

        private const val EXTRA_MONITORING_ID = "monitoringId"
        private const val EXTRA_SCHOOL_ID = "schoolId"
        private const val EXTRA_CLASS_ID = "classId"
        private const val EXTRA_ROOM_ID = "roomId"

        private const val CHANNEL_ID = "monitoring"
        private const val NOTIFICATION_ID = 1
        private const val BUFFER_SIZE = 30

        fun start(
            context: Context,
            monitoringId: String,
            schoolId: String,
            classId: String,
            roomId: String,
        ) {
            val intent = Intent(context, RecordingService::class.java).apply {
                putExtra(EXTRA_MONITORING_ID, monitoringId)
                putExtra(EXTRA_SCHOOL_ID, schoolId)
                putExtra(EXTRA_CLASS_ID, classId)
                putExtra(EXTRA_ROOM_ID, roomId)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stop(context: Context) {
            RecordingState.phase.value = MonitoringPhase.Questionnaire
            val intent = Intent(context, RecordingService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }
}
