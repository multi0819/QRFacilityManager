package com.multi0819.meetingbrief.recording

import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class AndroidPcmRecorder(
    context: Context,
    private val onProgress: (frames: Long, level: Float) -> Unit,
) : PcmRecorder {
    private val appContext = context.applicationContext
    private val paused = AtomicBoolean(false)
    private val stopRequested = AtomicBoolean(false)
    private var audioRecord: AudioRecord? = null
    private var session: RecordingSession? = null
    private var frames = 0L

    override suspend fun start(session: RecordingSession, sink: PcmSink) {
        check(audioRecord == null) { "recorder is already running" }
        check((session.file.parentFile?.usableSpace ?: 0L) >= MIN_FREE_BYTES) { "저장 공간이 부족합니다." }
        this.session = session
        frames = session.accumulatedFrames
        paused.set(false)
        stopRequested.set(false)
        val bufferSize = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, FORMAT)
            .coerceAtLeast(SAMPLE_RATE / 2)
        val recorder = AudioRecord.Builder()
            .setAudioSource(preferredAudioSource())
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(FORMAT)
                    .setSampleRate(SAMPLE_RATE)
                    .setChannelMask(CHANNEL)
                    .build(),
            )
            .setBufferSizeInBytes(bufferSize * 2)
            .build()
        check(recorder.state == AudioRecord.STATE_INITIALIZED) { "마이크를 초기화할 수 없습니다." }
        audioRecord = recorder
        val buffer = ShortArray(bufferSize)
        var framesSinceCheckpoint = 0
        try {
            recorder.startRecording()
            while (!stopRequested.get()) {
                val count = recorder.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                check(count >= 0) { "마이크 입력 오류: $count" }
                if (count == 0) continue
                if (paused.get()) continue
                check((session.file.parentFile?.usableSpace ?: 0L) >= MIN_FREE_BYTES) { "저장 공간이 부족하여 녹음을 안전하게 중단했습니다." }
                sink.write(buffer, count)
                frames += count
                framesSinceCheckpoint += count
                if (framesSinceCheckpoint >= CHECKPOINT_FRAMES) {
                    sink.checkpoint()
                    framesSinceCheckpoint = 0
                }
                onProgress(frames, rms(buffer, count))
            }
            sink.checkpoint()
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
            audioRecord = null
        }
    }

    override suspend fun pause() {
        check(audioRecord != null) { "recorder is not running" }
        paused.set(true)
    }

    override suspend fun resume() {
        check(audioRecord != null) { "recorder is not running" }
        paused.set(false)
    }

    override suspend fun stop(): RecordingSession {
        val current = checkNotNull(session) { "recorder is not running" }
        stopRequested.set(true)
        return current.copy(accumulatedFrames = frames)
    }

    private fun preferredAudioSource(): Int {
        val manager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        return if (manager.getProperty(AudioManager.PROPERTY_SUPPORT_AUDIO_SOURCE_UNPROCESSED) == "true") {
            MediaRecorder.AudioSource.UNPROCESSED
        } else {
            MediaRecorder.AudioSource.VOICE_RECOGNITION
        }
    }

    private fun rms(samples: ShortArray, count: Int): Float {
        var sum = 0.0
        repeat(count) { index ->
            val normalized = samples[index] / 32768.0
            sum += normalized * normalized
        }
        return sqrt(sum / count).toFloat().coerceIn(0f, 1f)
    }

    companion object {
        const val SAMPLE_RATE = 16_000
        private const val CHECKPOINT_FRAMES = SAMPLE_RATE * 5
        private const val MIN_FREE_BYTES = 64L * 1024L * 1024L
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val FORMAT = AudioFormat.ENCODING_PCM_16BIT
    }
}
