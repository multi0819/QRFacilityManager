package com.multi0819.meetingbrief.recording

import com.multi0819.meetingbrief.transcription.TranscriptionJobState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecordingViewModelTest {
    private val session = RecordingSession(3, File("meeting.wav"), 1_000, 0)

    @Test fun startMapsToRecording() {
        assertEquals(RecordingPhase.RECORDING, RecordingUiReducer.recording(RecordingState.Recording(session, 1_000, .2f)).phase)
    }

    @Test fun pauseMapsToPausedAndResumeMapsToRecording() {
        assertEquals(RecordingPhase.PAUSED, RecordingUiReducer.recording(RecordingState.Paused(session, 2_000)).phase)
        assertEquals(RecordingPhase.RECORDING, RecordingUiReducer.recording(RecordingState.Recording(session, 2_500, .1f)).phase)
    }

    @Test fun stopThenTranscriptionCompletesWithFullText() {
        assertEquals(RecordingPhase.TRANSCRIBING, RecordingUiReducer.transcription(3, TranscriptionJobState.Running(500, 1_000, "절반")).phase)
        val complete = RecordingUiReducer.transcription(3, TranscriptionJobState.Complete("원문", "교정된 전체 문장."))
        assertEquals(RecordingPhase.COMPLETE, complete.phase)
        assertEquals("교정된 전체 문장.", complete.transcript)
    }

    @Test fun emptyAudioFailureIsVisible() {
        val failed = RecordingUiReducer.transcription(3, TranscriptionJobState.Failed("녹음된 음성이 없습니다."))
        assertEquals(RecordingPhase.FAILED, failed.phase)
        assertEquals("녹음된 음성이 없습니다.", failed.error)
    }

    @Test fun recorderFailureKeepsRetryAvailable() {
        val retrySession = session.copy(file = java.nio.file.Files.createTempFile("retry-", ".wav").toFile())
        val failed = RecordingUiReducer.recording(RecordingState.Failed(retrySession, "마이크 입력 오류"))
        assertEquals(RecordingPhase.FAILED, failed.phase)
        assertTrue(failed.canRetry)
    }

    @Test fun transcriptionFailureCanMoveBackToRunningOnRetry() {
        assertEquals(RecordingPhase.FAILED, RecordingUiReducer.transcription(3, TranscriptionJobState.Failed("모델 오류")).phase)
        assertEquals(RecordingPhase.TRANSCRIBING, RecordingUiReducer.transcription(3, TranscriptionJobState.Running(0, 100, "")).phase)
    }

    @Test fun oneHourElapsedTimeDoesNotOverflow() {
        val state = RecordingUiReducer.recording(RecordingState.Recording(session, 3_600_000, .5f))
        assertEquals(3_600_000L, state.elapsedMs)
        assertEquals("01:00:00", formatElapsed(state.elapsedMs))
    }
}
