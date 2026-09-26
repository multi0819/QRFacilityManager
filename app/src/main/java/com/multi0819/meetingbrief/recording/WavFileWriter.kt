package com.multi0819.meetingbrief.recording

import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile

class WavFileWriter(
    file: File,
    private val sampleRate: Int = 16_000,
    private val channels: Short = 1,
    private val bitsPerSample: Short = 16,
) : PcmSink, Closeable {
    private val bytesPerFrame = channels * (bitsPerSample / 8)
    private val wav = RandomAccessFile(file, "rw")
    private var dataBytes = 0L
    private var finalized = false
    private var closed = false

    init {
        require(sampleRate > 0) { "sampleRate must be positive" }
        require(channels > 0) { "channels must be positive" }
        require(bitsPerSample == 16.toShort()) { "only PCM 16-bit is supported" }
        if (wav.length() >= HEADER_SIZE) {
            dataBytes = readCheckpointedDataLength()
            require(dataBytes >= 0 && HEADER_SIZE + dataBytes <= wav.length()) { "invalid WAV data length" }
            wav.setLength(HEADER_SIZE + dataBytes)
            wav.seek(wav.length())
        } else {
            wav.setLength(0)
            writeHeader(0)
            wav.seek(HEADER_SIZE)
        }
    }

    override fun write(samples: ShortArray, count: Int) {
        checkOpen()
        require(count in 0..samples.size) { "count exceeds sample buffer" }
        repeat(count) { index ->
            val value = samples[index].toInt()
            wav.write(value and 0xff)
            wav.write((value ushr 8) and 0xff)
        }
        dataBytes += count * 2L
    }

    override fun checkpoint(): Long {
        checkOpen()
        updateLengths(dataBytes)
        wav.fd.sync()
        wav.seek(HEADER_SIZE + dataBytes)
        return dataBytes / bytesPerFrame
    }

    fun closeAndFinalize() {
        if (closed) return
        checkpoint()
        finalized = true
        close()
    }

    override fun close() {
        if (closed) return
        closed = true
        wav.close()
    }

    private fun checkOpen() = check(!closed) { "WAV writer is closed" }

    private fun readCheckpointedDataLength(): Long {
        wav.seek(0)
        require(wav.readAscii(4) == "RIFF" && wav.readUInt32Le() >= 36) { "invalid RIFF header" }
        require(wav.readAscii(4) == "WAVE") { "invalid WAVE header" }
        wav.seek(40)
        return wav.readUInt32Le()
    }

    private fun writeHeader(length: Long) {
        wav.seek(0)
        wav.writeBytes("RIFF")
        wav.writeUInt32Le(36 + length)
        wav.writeBytes("WAVEfmt ")
        wav.writeUInt32Le(16)
        wav.writeUInt16Le(1)
        wav.writeUInt16Le(channels.toInt())
        wav.writeUInt32Le(sampleRate.toLong())
        wav.writeUInt32Le(sampleRate.toLong() * bytesPerFrame)
        wav.writeUInt16Le(bytesPerFrame)
        wav.writeUInt16Le(bitsPerSample.toInt())
        wav.writeBytes("data")
        wav.writeUInt32Le(length)
    }

    private fun updateLengths(length: Long) {
        val position = wav.filePointer
        wav.seek(4)
        wav.writeUInt32Le(36 + length)
        wav.seek(40)
        wav.writeUInt32Le(length)
        wav.seek(position)
    }

    companion object {
        private const val HEADER_SIZE = 44L
    }
}

private fun RandomAccessFile.writeUInt16Le(value: Int) {
    write(value and 0xff)
    write((value ushr 8) and 0xff)
}

private fun RandomAccessFile.writeUInt32Le(value: Long) {
    write((value and 0xff).toInt())
    write(((value ushr 8) and 0xff).toInt())
    write(((value ushr 16) and 0xff).toInt())
    write(((value ushr 24) and 0xff).toInt())
}

private fun RandomAccessFile.readUInt32Le(): Long {
    val b0 = readUnsignedByte().toLong()
    val b1 = readUnsignedByte().toLong()
    val b2 = readUnsignedByte().toLong()
    val b3 = readUnsignedByte().toLong()
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

private fun RandomAccessFile.readAscii(count: Int): String =
    ByteArray(count).also(::readFully).toString(Charsets.US_ASCII)
