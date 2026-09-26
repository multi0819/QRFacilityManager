package com.multi0819.meetingbrief.transcription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.nio.file.Files

class TranscriptWindowingTest {
    @Test
    fun createsThirtySecondWindowsWithTwoSecondOverlap() {
        val wav = wavWithFrames(65 * 16_000)
        val windows = WavWindowReader(wav).windows().toList()
        assertEquals(listOf(0L, 28L * 16_000, 56L * 16_000), windows.map { it.firstFrame })
        assertEquals(listOf(30 * 16_000, 30 * 16_000, 9 * 16_000), windows.map { it.samples.size })
    }

    @Test
    fun emitsRecordingShorterThanOneWindow() {
        val wav = wavWithFrames(800)
        val windows = WavWindowReader(wav).windows().toList()
        assertEquals(1, windows.size)
        assertEquals(800, windows.single().samples.size)
    }

    @Test
    fun removesExactRepeatedKoreanBoundary() {
        assertEquals("내일 오전에 필터를 교체합니다", mergeTranscript("내일 오전에 필터를", "필터를 교체합니다"))
    }

    @Test
    fun keepsSimilarButDifferentBoundary() {
        assertEquals("필터를 확인합니다 필터는 교체합니다", mergeTranscript("필터를 확인합니다", "필터는 교체합니다"))
    }

    @Test
    fun ignoresBlankWindowText() {
        assertEquals("기존 회의 내용", mergeTranscript("기존 회의 내용", "   "))
    }

    @Test
    fun keepsBothTextsWhenThereIsNoRepeatedBoundary() {
        assertEquals("안전모를 착용하세요 출입문을 닫아 주세요", mergeTranscript("안전모를 착용하세요", "출입문을 닫아 주세요"))
    }

    private fun wavWithFrames(frameCount: Int): File {
        val file = Files.createTempFile("windowing-", ".wav").toFile().apply { deleteOnExit() }
        RandomAccessFile(file, "rw").use { wav ->
            wav.write(ByteArray(44))
            repeat(frameCount) { wav.write(0); wav.write(0) }
        }
        assertTrue(file.length() > 44)
        return file
    }
}
