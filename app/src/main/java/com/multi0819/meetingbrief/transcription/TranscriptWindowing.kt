package com.multi0819.meetingbrief.transcription

import java.io.File
import java.io.RandomAccessFile

class WavWindowReader(
    private val file: File,
    private val sampleRate: Int = 16_000,
    private val windowSeconds: Int = 30,
    private val overlapSeconds: Int = 2,
) {
    val totalFrames: Long get() = ((file.length() - HEADER_BYTES).coerceAtLeast(0)) / BYTES_PER_FRAME

    fun windows(fromFrame: Long = 0): Sequence<AudioWindow> = sequence {
        require(windowSeconds > overlapSeconds && overlapSeconds >= 0)
        val windowFrames = windowSeconds * sampleRate
        val stepFrames = (windowSeconds - overlapSeconds) * sampleRate
        val total = totalFrames
        var first = (fromFrame / stepFrames) * stepFrames
        RandomAccessFile(file, "r").use { wav ->
            while (first < total) {
                val count = minOf(windowFrames.toLong(), total - first).toInt()
                val samples = FloatArray(count)
                wav.seek(HEADER_BYTES + first * BYTES_PER_FRAME)
                repeat(count) { index ->
                    val low = wav.readUnsignedByte()
                    val high = wav.readUnsignedByte()
                    samples[index] = (((high shl 8) or low).toShort() / 32768f)
                }
                yield(AudioWindow(first, samples))
                if (count < windowFrames) break
                first += stepFrames
            }
        }
    }

    companion object {
        private const val HEADER_BYTES = 44L
        private const val BYTES_PER_FRAME = 2L
    }
}

fun mergeTranscript(previous: String, next: String, maxOverlapChars: Int = 80): String {
    val left = previous.trim().replace(Regex("\\s+"), " ")
    val right = next.trim().replace(Regex("\\s+"), " ")
    if (left.isBlank()) return right
    if (right.isBlank()) return left
    val limit = minOf(maxOverlapChars, left.length, right.length)
    for (length in limit downTo 2) {
        val suffix = left.takeLast(length)
        if (suffix == right.take(length) && suffix.count { it in '가'..'힣' } >= 2) {
            return (left + right.drop(length)).trim()
        }
    }
    return "$left $right"
}
