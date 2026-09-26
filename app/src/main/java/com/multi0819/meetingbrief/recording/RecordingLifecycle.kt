package com.multi0819.meetingbrief.recording

class RecordingLifecycle {
    private var current: RecordingState = RecordingState.Idle

    fun start(session: RecordingSession): RecordingState =
        RecordingState.Recording(session, elapsedMs = framesToMillis(session.accumulatedFrames), level = 0f).also { current = it }

    fun pause(elapsedMs: Long): RecordingState {
        val session = requireSession()
        return RecordingState.Paused(session, elapsedMs).also { current = it }
    }

    fun resume(elapsedMs: Long): RecordingState {
        val session = requireSession()
        return RecordingState.Recording(session, elapsedMs, level = 0f).also { current = it }
    }

    fun update(session: RecordingSession, elapsedMs: Long, level: Float): RecordingState =
        RecordingState.Recording(session, elapsedMs, level.coerceIn(0f, 1f)).also { current = it }

    fun stop(session: RecordingSession = requireSession()): RecordingState =
        RecordingState.Stopped(session).also { current = it }

    fun fail(message: String, session: RecordingSession? = sessionOrNull()): RecordingState =
        RecordingState.Failed(session, message).also { current = it }

    fun restore(session: RecordingSession, wasActive: Boolean): RecordingState =
        if (wasActive) fail("녹음이 비정상적으로 중단되었습니다. 저장된 음성을 다시 변환할 수 있습니다.", session)
        else RecordingState.Stopped(session).also { current = it }

    private fun requireSession(): RecordingSession =
        sessionOrNull() ?: error("recording session is not active")

    private fun sessionOrNull(): RecordingSession? = when (val value = current) {
        is RecordingState.Recording -> value.session
        is RecordingState.Paused -> value.session
        is RecordingState.Stopped -> value.session
        is RecordingState.Failed -> value.session
        RecordingState.Idle -> null
    }

    private fun framesToMillis(frames: Long): Long = frames * 1_000L / 16_000L
}
