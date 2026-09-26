package com.multi0819.meetingbrief.transcription

import com.multi0819.meetingbrief.MeetingDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import java.io.File

enum class TranscriptStatus { NONE, RECORDING, READY, TRANSCRIBING, COMPLETE, FAILED }

data class TranscriptRecord(
    val meetingId: Long,
    val audioPath: String,
    val checkpointFrame: Long = 0,
    val rawTranscript: String = "",
    val correctedTranscript: String = "",
    val status: TranscriptStatus = TranscriptStatus.READY,
)

sealed interface TranscriptionJobState {
    data class Running(val completedFrames: Long, val totalFrames: Long, val text: String) : TranscriptionJobState
    data class Complete(val rawText: String, val correctedText: String) : TranscriptionJobState
    data class Failed(val message: String) : TranscriptionJobState
}

interface TranscriptStore {
    suspend fun load(meetingId: Long): TranscriptRecord?
    suspend fun save(record: TranscriptRecord)
}

class RoomTranscriptStore(private val dao: MeetingDao) : TranscriptStore {
    override suspend fun load(meetingId: Long): TranscriptRecord? = dao.transcript(meetingId)?.let { meeting ->
        TranscriptRecord(
            meetingId = meeting.id,
            audioPath = meeting.audioPath,
            checkpointFrame = meeting.transcriptCheckpointFrame,
            rawTranscript = meeting.rawTranscript,
            correctedTranscript = meeting.correctedTranscript,
            status = runCatching { TranscriptStatus.valueOf(meeting.transcriptStatus) }.getOrDefault(TranscriptStatus.NONE),
        )
    }

    override suspend fun save(record: TranscriptRecord) {
        dao.updateTranscript(
            id = record.meetingId,
            raw = record.rawTranscript,
            corrected = record.correctedTranscript,
            audioPath = record.audioPath,
            status = record.status.name,
            checkpoint = record.checkpointFrame,
            updatedAt = System.currentTimeMillis(),
        )
    }
}

class TranscriptionRepository(
    private val store: TranscriptStore,
    private val engine: TranscriptionEngine,
    private val correct: (String) -> String = ::normalizeTranscript,
) {
    fun transcribe(meetingId: Long): Flow<TranscriptionJobState> = channelFlow {
        val initial = store.load(meetingId)
        if (initial == null) {
            send(TranscriptionJobState.Failed("회의 기록을 찾을 수 없습니다."))
            return@channelFlow
        }
        val audio = File(initial.audioPath)
        if (!audio.exists() || audio.length() <= 44) {
            val failed = initial.copy(status = TranscriptStatus.FAILED)
            store.save(failed)
            send(TranscriptionJobState.Failed("녹음된 음성이 없습니다."))
            return@channelFlow
        }
        store.save(initial.copy(status = TranscriptStatus.TRANSCRIBING))
        send(TranscriptionJobState.Running(initial.checkpointFrame, 0, initial.rawTranscript))
        val progressChannel = Channel<TranscriptionProgress>(Channel.UNLIMITED)
        val checkpointWriter = launch {
            for (progress in progressChannel) {
                val merged = mergeTranscript(initial.rawTranscript, progress.text)
                store.save(
                    initial.copy(
                        checkpointFrame = progress.completedFrames,
                        rawTranscript = merged,
                        status = TranscriptStatus.TRANSCRIBING,
                    ),
                )
                send(TranscriptionJobState.Running(progress.completedFrames, progress.totalFrames, merged))
            }
        }
        try {
            val decoded = engine.transcribe(audio, initial.checkpointFrame) { progressChannel.trySend(it) }
            progressChannel.close()
            checkpointWriter.join()
            val raw = mergeTranscript(initial.rawTranscript, decoded)
            if (raw.isBlank()) error("녹음된 음성이 없습니다.")
            val corrected = correct(raw)
            val completed = initial.copy(
                checkpointFrame = 0,
                rawTranscript = raw,
                correctedTranscript = corrected,
                status = TranscriptStatus.COMPLETE,
            )
            store.save(completed)
            if (audio.delete()) store.save(completed.copy(audioPath = ""))
            send(TranscriptionJobState.Complete(raw, corrected))
        } catch (cancelled: CancellationException) {
            progressChannel.close()
            checkpointWriter.join()
            throw cancelled
        } catch (error: Throwable) {
            progressChannel.close()
            checkpointWriter.join()
            runCatching {
                val latest = store.load(meetingId) ?: initial
                store.save(latest.copy(status = TranscriptStatus.FAILED))
            }
            send(TranscriptionJobState.Failed(error.message ?: "음성 변환에 실패했습니다."))
        }
    }

    suspend fun retry(meetingId: Long) {
        transcribe(meetingId).collect()
    }
}

fun normalizeTranscript(raw: String): String = raw
    .lineSequence()
    .map { it.trim().replace(Regex("\\s+"), " ") }
    .filter(String::isNotBlank)
    .joinToString("\n") { line -> if (Regex("[.!?。]$").containsMatchIn(line)) line else "$line." }
