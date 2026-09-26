package com.multi0819.meetingbrief.recording

import android.app.Application
import android.content.Intent
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.multi0819.meetingbrief.MeetingBriefApp
import com.multi0819.meetingbrief.transcription.KoreanOfflineTranscriber
import com.multi0819.meetingbrief.transcription.RoomTranscriptStore
import com.multi0819.meetingbrief.transcription.TranscriptStatus
import com.multi0819.meetingbrief.transcription.TranscriptionJobState
import com.multi0819.meetingbrief.transcription.TranscriptionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.Locale

enum class RecordingPhase { IDLE, RECORDING, PAUSED, TRANSCRIBING, COMPLETE, FAILED }

data class RecordingUiState(
    val meetingId: Long = 0,
    val phase: RecordingPhase = RecordingPhase.IDLE,
    val elapsedMs: Long = 0,
    val level: Float = 0f,
    val progressPercent: Int = 0,
    val transcript: String = "",
    val error: String? = null,
    val canRetry: Boolean = false,
)

object RecordingUiReducer {
    fun recording(state: RecordingState): RecordingUiState = when (state) {
        RecordingState.Idle -> RecordingUiState()
        is RecordingState.Recording -> RecordingUiState(state.session.meetingId, RecordingPhase.RECORDING, state.elapsedMs, state.level)
        is RecordingState.Paused -> RecordingUiState(state.session.meetingId, RecordingPhase.PAUSED, state.elapsedMs)
        is RecordingState.Stopped -> RecordingUiState(state.session.meetingId, RecordingPhase.TRANSCRIBING, state.session.accumulatedFrames * 1_000L / 16_000L)
        is RecordingState.Failed -> RecordingUiState(state.session?.meetingId ?: 0, RecordingPhase.FAILED, error = state.message, canRetry = state.session?.file?.exists() == true)
    }

    fun transcription(meetingId: Long, state: TranscriptionJobState): RecordingUiState = when (state) {
        is TranscriptionJobState.Running -> RecordingUiState(
            meetingId = meetingId,
            phase = RecordingPhase.TRANSCRIBING,
            progressPercent = if (state.totalFrames <= 0) 0 else ((state.completedFrames * 100 / state.totalFrames).coerceIn(0, 100)).toInt(),
            transcript = state.text,
        )
        is TranscriptionJobState.Complete -> RecordingUiState(meetingId, RecordingPhase.COMPLETE, progressPercent = 100, transcript = state.correctedText)
        is TranscriptionJobState.Failed -> RecordingUiState(meetingId, RecordingPhase.FAILED, error = state.message, canRetry = true)
    }
}

class RecordingViewModel(app: Application) : AndroidViewModel(app) {
    private val application = app
    private val store = RoomTranscriptStore((app as MeetingBriefApp).db.dao())
    private val repository = TranscriptionRepository(store, KoreanOfflineTranscriber(app))
    private val mutableUi = MutableStateFlow(RecordingUiState())
    val ui: StateFlow<RecordingUiState> = mutableUi.asStateFlow()
    private var activeTranscriptionMeetingId = 0L

    init {
        viewModelScope.launch {
            RecordingStateBus.state.collectLatest { state ->
                mutableUi.value = RecordingUiReducer.recording(state)
                when (state) {
                    is RecordingState.Stopped -> prepareAndTranscribe(state.session)
                    is RecordingState.Failed -> {
                        val session = state.session
                        if (session != null) prepareFailedRecording(session)
                    }
                    else -> Unit
                }
            }
        }
    }

    fun start(meetingId: Long) {
        mutableUi.value = RecordingUiState(meetingId, RecordingPhase.RECORDING)
        send(MeetingRecordingService.ACTION_START, meetingId)
    }

    fun pause() = send(MeetingRecordingService.ACTION_PAUSE)
    fun resume() = send(MeetingRecordingService.ACTION_RESUME)
    fun stopAndTranscribe() = send(MeetingRecordingService.ACTION_STOP)

    fun retryTranscription(meetingId: Long) {
        viewModelScope.launch { collectTranscription(meetingId) }
    }

    private suspend fun prepareAndTranscribe(session: RecordingSession) {
        if (activeTranscriptionMeetingId == session.meetingId) return
        val current = store.load(session.meetingId) ?: return
        if (current.status == TranscriptStatus.COMPLETE && current.audioPath.isBlank()) return
        store.save(
            current.copy(
                audioPath = session.file.absolutePath,
                checkpointFrame = 0,
                status = TranscriptStatus.READY,
            ),
        )
        collectTranscription(session.meetingId)
    }

    private suspend fun prepareFailedRecording(session: RecordingSession) {
        val current = store.load(session.meetingId) ?: return
        store.save(current.copy(audioPath = session.file.absolutePath, status = TranscriptStatus.FAILED))
    }

    private suspend fun collectTranscription(meetingId: Long) {
        activeTranscriptionMeetingId = meetingId
        try {
            repository.transcribe(meetingId).collect { mutableUi.value = RecordingUiReducer.transcription(meetingId, it) }
        } finally {
            activeTranscriptionMeetingId = 0
        }
    }

    private fun send(action: String, meetingId: Long = 0) {
        val intent = Intent(application, MeetingRecordingService::class.java).setAction(action)
        if (meetingId > 0) intent.putExtra(MeetingRecordingService.EXTRA_MEETING_ID, meetingId)
        if (action == MeetingRecordingService.ACTION_START) ContextCompat.startForegroundService(application, intent)
        else application.startService(intent)
    }
}

fun formatElapsed(milliseconds: Long): String {
    val seconds = (milliseconds / 1_000).coerceAtLeast(0)
    return String.format(Locale.KOREA, "%02d:%02d:%02d", seconds / 3_600, (seconds / 60) % 60, seconds % 60)
}
