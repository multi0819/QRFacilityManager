package com.multi0819.meetingbrief.recording

import java.io.File

data class RecordingSession(
    val meetingId: Long,
    val file: File,
    val startedAt: Long,
    val accumulatedFrames: Long = 0,
)

sealed interface RecordingState {
    data object Idle : RecordingState
    data class Recording(val session: RecordingSession, val elapsedMs: Long, val level: Float) : RecordingState
    data class Paused(val session: RecordingSession, val elapsedMs: Long) : RecordingState
    data class Stopped(val session: RecordingSession) : RecordingState
    data class Failed(val session: RecordingSession?, val message: String) : RecordingState
}

interface PcmSink {
    fun write(samples: ShortArray, count: Int)
    fun checkpoint(): Long
}

interface PcmRecorder {
    suspend fun start(session: RecordingSession, sink: PcmSink)
    suspend fun pause()
    suspend fun resume()
    suspend fun stop(): RecordingSession
}
