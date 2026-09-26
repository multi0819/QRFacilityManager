package com.multi0819.meetingbrief.recording

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class RecordingLifecycleTest {
    private val session = RecordingSession(7, File("meeting.wav"), 1_000, 160)

    @Test
    fun startCreatesForegroundNotificationAndSessionFile() {
        val lifecycle = RecordingLifecycle()
        val next = lifecycle.start(session)
        assertTrue(next is RecordingState.Recording)
        assertSame(session, (next as RecordingState.Recording).session)
    }

    @Test
    fun pauseResumeUsesSameSession() {
        val lifecycle = RecordingLifecycle()
        lifecycle.start(session)
        val paused = lifecycle.pause(2_000) as RecordingState.Paused
        val resumed = lifecycle.resume(2_500) as RecordingState.Recording
        assertSame(session, paused.session)
        assertSame(session, resumed.session)
    }

    @Test
    fun stopFinalizesFile() {
        val lifecycle = RecordingLifecycle()
        lifecycle.start(session)
        val finalized = session.copy(accumulatedFrames = 320)
        assertEquals(RecordingState.Stopped(finalized), lifecycle.stop(finalized))
    }

    @Test
    fun serviceRestartRestoresSessionMetadata() {
        val lifecycle = RecordingLifecycle()
        val restored = lifecycle.restore(session, wasActive = true)
        assertTrue(restored is RecordingState.Failed)
        assertSame(session, (restored as RecordingState.Failed).session)
    }

    @Test
    fun audioRecordFailurePublishesFailedWithoutDeletingFile() {
        val lifecycle = RecordingLifecycle()
        lifecycle.start(session)
        val failed = lifecycle.fail("마이크 사용 중", session) as RecordingState.Failed
        assertSame(session, failed.session)
        assertEquals("마이크 사용 중", failed.message)
    }
}
