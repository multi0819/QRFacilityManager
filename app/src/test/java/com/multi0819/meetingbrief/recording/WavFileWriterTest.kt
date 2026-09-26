package com.multi0819.meetingbrief.recording

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class WavFileWriterTest {
    @Test
    fun writesLittleEndianPcmWithoutBufferingWholeRecording() {
        val file = tempWav()
        WavFileWriter(file).use { writer ->
            writer.write(shortArrayOf(0x1234, -2, 0x7fff), 3)
            writer.closeAndFinalize()
        }

        val pcm = file.readBytes().copyOfRange(44, 50)
        assertArrayEquals(byteArrayOf(0x34, 0x12, 0xfe.toByte(), 0xff.toByte(), 0xff.toByte(), 0x7f), pcm)
    }

    @Test
    fun finalizesHeaderWithActualDataLength() {
        val file = tempWav()
        WavFileWriter(file).use { writer ->
            writer.write(shortArrayOf(1, 2, 3, 4), 4)
            writer.closeAndFinalize()
        }

        RandomAccessFile(file, "r").use { wav ->
            assertEquals("RIFF", wav.readAscii(4))
            assertEquals(44L, wav.length() - 8)
            assertEquals("WAVE", wav.readAscii(4))
            wav.seek(40)
            assertEquals(8L, wav.readUInt32Le())
            assertEquals(52L, wav.length())
        }
    }

    @Test
    fun resumeAppendsAfterCheckpoint() {
        val file = tempWav()
        WavFileWriter(file).use { writer ->
            writer.write(shortArrayOf(10, 20), 2)
            assertEquals(2L, writer.checkpoint())
            writer.write(shortArrayOf(999), 1) // uncheckpointed tail simulates a crash
        }

        WavFileWriter(file).use { resumed ->
            resumed.write(shortArrayOf(30, 40), 2)
            resumed.closeAndFinalize()
        }

        val values = RandomAccessFile(file, "r").use { wav ->
            wav.seek(44)
            List(4) { wav.readInt16Le() }
        }
        assertEquals(listOf<Short>(10, 20, 30, 40), values)
    }

    @Test
    fun diskWriteFailureKeepsLastValidCheckpoint() {
        val file = tempWav()
        val writer = WavFileWriter(file)
        writer.write(shortArrayOf(7, 8), 2)
        assertEquals(2L, writer.checkpoint())
        writer.close()

        assertThrows(IllegalStateException::class.java) {
            writer.write(shortArrayOf(9), 1)
        }

        WavFileWriter(file).use { recovered -> recovered.closeAndFinalize() }
        RandomAccessFile(file, "r").use { wav ->
            wav.seek(40)
            assertEquals(4L, wav.readUInt32Le())
            assertEquals(48L, wav.length())
        }
    }

    private fun tempWav(): File = Files.createTempFile("meeting-recording-", ".wav").toFile().apply { deleteOnExit() }
}

private fun RandomAccessFile.readAscii(count: Int): String = ByteArray(count).also(::readFully).toString(Charsets.US_ASCII)

private fun RandomAccessFile.readUInt32Le(): Long {
    val b0 = readUnsignedByte().toLong()
    val b1 = readUnsignedByte().toLong()
    val b2 = readUnsignedByte().toLong()
    val b3 = readUnsignedByte().toLong()
    return b0 or (b1 shl 8) or (b2 shl 16) or (b3 shl 24)
}

private fun RandomAccessFile.readInt16Le(): Short {
    val low = readUnsignedByte()
    val high = readUnsignedByte()
    return ((high shl 8) or low).toShort()
}
