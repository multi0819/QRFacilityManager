package com.multi0819.meetingbrief.recording

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.multi0819.meetingbrief.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

object RecordingStateBus {
    private val mutableState = MutableStateFlow<RecordingState>(RecordingState.Idle)
    val state: StateFlow<RecordingState> = mutableState.asStateFlow()
    internal fun publish(value: RecordingState) { mutableState.value = value }
}

class MeetingRecordingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lifecycle = RecordingLifecycle()
    private var recordingJob: Job? = null
    private var recorder: AndroidPcmRecorder? = null
    private var writer: WavFileWriter? = null
    private var session: RecordingSession? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        restoreInterruptedSession()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> startRecording(intent.getLongExtra(EXTRA_MEETING_ID, -1L))
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun startRecording(meetingId: Long) {
        if (meetingId <= 0 || recordingJob?.isActive == true) return
        val directory = File(filesDir, "recordings").apply { mkdirs() }
        val created = RecordingSession(
            meetingId = meetingId,
            file = File(directory, "meeting-$meetingId-${System.currentTimeMillis()}.wav"),
            startedAt = System.currentTimeMillis(),
        )
        session = created
        saveSession(created, active = true)
        RecordingStateBus.publish(lifecycle.start(created))
        startForeground(NOTIFICATION_ID, notification("회의 녹음 중"))
        writer = WavFileWriter(created.file)
        recorder = AndroidPcmRecorder(this) { frames, level ->
            val current = created.copy(accumulatedFrames = frames)
            session = current
            saveSession(current, active = true)
            RecordingStateBus.publish(lifecycle.update(current, frames * 1_000L / AndroidPcmRecorder.SAMPLE_RATE, level))
        }
        recordingJob = scope.launch {
            runCatching { recorder!!.start(created, writer!!) }
                .onSuccess {
                    writer?.closeAndFinalize()
                    val stopped = session ?: created
                    saveSession(stopped, active = false)
                    RecordingStateBus.publish(lifecycle.stop(stopped))
                }
                .onFailure { error ->
                    runCatching { writer?.checkpoint() }
                    writer?.close()
                    val recoverable = session ?: created
                    saveSession(recoverable, active = false)
                    RecordingStateBus.publish(lifecycle.fail(error.message ?: "녹음이 중단되었습니다.", recoverable))
                }
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseRecording() {
        scope.launch {
            recorder?.pause()
            val current = session ?: return@launch
            RecordingStateBus.publish(lifecycle.pause(current.accumulatedFrames * 1_000L / AndroidPcmRecorder.SAMPLE_RATE))
        }
    }

    private fun resumeRecording() {
        scope.launch {
            recorder?.resume()
            val current = session ?: return@launch
            RecordingStateBus.publish(lifecycle.resume(current.accumulatedFrames * 1_000L / AndroidPcmRecorder.SAMPLE_RATE))
        }
    }

    private fun stopRecording() {
        scope.launch {
            recorder?.stop()?.let { session = it }
        }
    }

    private fun restoreInterruptedSession() {
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val path = preferences.getString(KEY_PATH, null) ?: return
        val restored = RecordingSession(
            meetingId = preferences.getLong(KEY_MEETING_ID, -1),
            file = File(path),
            startedAt = preferences.getLong(KEY_STARTED_AT, 0),
            accumulatedFrames = preferences.getLong(KEY_FRAMES, 0),
        )
        if (restored.file.exists()) {
            session = restored
            RecordingStateBus.publish(lifecycle.restore(restored, preferences.getBoolean(KEY_ACTIVE, false)))
        }
    }

    private fun saveSession(value: RecordingSession, active: Boolean) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putLong(KEY_MEETING_ID, value.meetingId)
            .putString(KEY_PATH, value.file.absolutePath)
            .putLong(KEY_STARTED_AT, value.startedAt)
            .putLong(KEY_FRAMES, value.accumulatedFrames)
            .putBoolean(KEY_ACTIVE, active)
            .apply()
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "회의 녹음", NotificationManager.IMPORTANCE_LOW))
    }

    private fun notification(title: String): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, MeetingRecordingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle(title)
            .setContentText("중단 버튼을 누를 때까지 계속 녹음합니다.")
            .setContentIntent(open)
            .setOngoing(true)
            .addAction(0, "녹음 중단", stop)
            .build()
    }

    companion object {
        const val ACTION_START = "com.multi0819.meetingbrief.recording.START"
        const val ACTION_PAUSE = "com.multi0819.meetingbrief.recording.PAUSE"
        const val ACTION_RESUME = "com.multi0819.meetingbrief.recording.RESUME"
        const val ACTION_STOP = "com.multi0819.meetingbrief.recording.STOP"
        const val EXTRA_MEETING_ID = "meeting_id"
        private const val CHANNEL_ID = "meeting_recording"
        private const val NOTIFICATION_ID = 260
        private const val PREFERENCES = "recording_session"
        private const val KEY_MEETING_ID = "meeting_id"
        private const val KEY_PATH = "path"
        private const val KEY_STARTED_AT = "started_at"
        private const val KEY_FRAMES = "frames"
        private const val KEY_ACTIVE = "active"
    }
}
