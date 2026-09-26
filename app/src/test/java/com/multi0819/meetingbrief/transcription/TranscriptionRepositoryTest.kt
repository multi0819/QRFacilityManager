package com.multi0819.meetingbrief.transcription

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class TranscriptionRepositoryTest {
    @Test
    fun progressCheckpointsArePersistedAndRecreationResumesLastFrame() = runBlocking {
        val audio = audioFile()
        val store = FakeTranscriptStore(TranscriptRecord(1, audio.absolutePath, checkpointFrame = 320, rawTranscript = "기존"))
        val engine = FakeEngine("새 내용", progress = listOf(TranscriptionProgress(640, 1_000, "새")))

        TranscriptionRepository(store, engine, ::normalizeTranscript).transcribe(1).toList()

        assertEquals(320L, engine.receivedCheckpoint)
        assertTrue(store.history.any { it.checkpointFrame == 640L && it.rawTranscript == "기존 새" })
    }

    @Test
    fun successfulPersistenceHappensBeforeAudioDeletion() = runBlocking {
        val audio = audioFile()
        val store = FakeTranscriptStore(TranscriptRecord(1, audio.absolutePath))

        TranscriptionRepository(store, FakeEngine("전체 회의 내용"), ::normalizeTranscript).transcribe(1).toList()

        assertFalse(audio.exists())
        assertTrue(store.history.any { it.status == TranscriptStatus.COMPLETE && it.audioPath == audio.absolutePath })
        assertEquals("", store.current.audioPath)
        assertEquals("전체 회의 내용.", store.current.correctedTranscript)
    }

    @Test
    fun roomFailureRetainsAudio() = runBlocking {
        val audio = audioFile()
        val store = FakeTranscriptStore(TranscriptRecord(1, audio.absolutePath), failOnComplete = true)

        val states = TranscriptionRepository(store, FakeEngine("내용"), ::normalizeTranscript).transcribe(1).toList()

        assertTrue(audio.exists())
        assertTrue(states.last() is TranscriptionJobState.Failed)
    }

    @Test
    fun engineFailureRetainsAudioAndMarksFailed() = runBlocking {
        val audio = audioFile()
        val store = FakeTranscriptStore(TranscriptRecord(1, audio.absolutePath))

        val states = TranscriptionRepository(store, FakeEngine(error = IllegalStateException("모델 오류")), ::normalizeTranscript).transcribe(1).toList()

        assertTrue(audio.exists())
        assertEquals(TranscriptStatus.FAILED, store.current.status)
        assertTrue(states.last() is TranscriptionJobState.Failed)
    }

    @Test
    fun emptyAudioProducesClearFailure() = runBlocking {
        val file = Files.createTempFile("empty-recording-", ".wav").toFile().apply { writeBytes(ByteArray(44)) }
        val store = FakeTranscriptStore(TranscriptRecord(1, file.absolutePath))

        val states = TranscriptionRepository(store, FakeEngine(""), ::normalizeTranscript).transcribe(1).toList()

        assertEquals("녹음된 음성이 없습니다.", (states.last() as TranscriptionJobState.Failed).message)
        assertTrue(file.exists())
    }

    @Test
    fun cancellationRetainsCheckpointAndAudio() {
        val audio = audioFile()
        val store = FakeTranscriptStore(TranscriptRecord(1, audio.absolutePath, checkpointFrame = 123))
        org.junit.Assert.assertThrows(CancellationException::class.java) {
            runBlocking {
                TranscriptionRepository(store, FakeEngine(error = CancellationException()), ::normalizeTranscript).transcribe(1).toList()
            }
        }
        assertTrue(audio.exists())
        assertEquals(123L, store.current.checkpointFrame)
    }

    private fun audioFile(): File = Files.createTempFile("meeting-audio-", ".wav").toFile().apply {
        writeBytes(ByteArray(46))
        deleteOnExit()
    }
}

private class FakeEngine(
    private val result: String = "",
    private val progress: List<TranscriptionProgress> = emptyList(),
    private val error: Throwable? = null,
) : TranscriptionEngine {
    var receivedCheckpoint: Long = -1
    override suspend fun transcribe(wav: File, checkpointFrame: Long, onProgress: (TranscriptionProgress) -> Unit): String {
        receivedCheckpoint = checkpointFrame
        progress.forEach(onProgress)
        error?.let { throw it }
        return result
    }
}

private class FakeTranscriptStore(
    initial: TranscriptRecord,
    private val failOnComplete: Boolean = false,
) : TranscriptStore {
    var current = initial
    val history = mutableListOf(initial)
    override suspend fun load(meetingId: Long): TranscriptRecord? = current.takeIf { it.meetingId == meetingId }
    override suspend fun save(record: TranscriptRecord) {
        if (failOnComplete && record.status == TranscriptStatus.COMPLETE) error("database full")
        current = record
        history += record
    }
}
