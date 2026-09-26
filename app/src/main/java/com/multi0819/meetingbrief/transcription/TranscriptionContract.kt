package com.multi0819.meetingbrief.transcription

import java.io.File

data class AudioWindow(val firstFrame: Long, val samples: FloatArray)

data class TranscriptionProgress(
    val completedFrames: Long,
    val totalFrames: Long,
    val text: String,
)

interface TranscriptionEngine {
    suspend fun transcribe(
        wav: File,
        checkpointFrame: Long = 0,
        onProgress: (TranscriptionProgress) -> Unit = {},
    ): String
}
