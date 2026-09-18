package com.multi0819.meetingbrief
import org.junit.Assert.*
import org.junit.Test
class TranscriptAccumulatorTest {
 @Test fun longSpeechSegmentsAreAppendedWithoutOverwriting(){
  val a=TranscriptAccumulator()
  assertEquals("첫 번째 전달사항입니다.",a.append("첫 번째 전달사항입니다"))
  assertEquals("두 번째 전달사항입니다.",a.append("두 번째 전달사항입니다"))
  assertEquals(listOf("첫 번째 전달사항입니다.","두 번째 전달사항입니다."),a.all())
 }
 @Test fun duplicateRecognitionResultIsIgnored(){val a=TranscriptAccumulator();assertNotNull(a.append("설비를 확인하세요"));assertNull(a.append("설비를 확인하세요"))}
 @Test fun partialResultNeverReplacesFinalText(){val a=TranscriptAccumulator();a.append("확정된 문장");assertEquals(listOf("확정된 문장."),a.all())}
}
