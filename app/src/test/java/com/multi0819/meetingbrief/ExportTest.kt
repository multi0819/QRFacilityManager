package com.multi0819.meetingbrief
import org.junit.Assert.*
import org.junit.Test
class ExportTest {
 @Test fun shareTextContainsSections(){val t=MeetingFormatter.toShareText("설비회의",listOf("밸브 점검"),OfflineSummaryEngine.summarize(listOf("밸브 점검")));assertTrue(t.contains("[전달사항]"));assertTrue(t.contains("[추천사항]"))}
 @Test fun duplicateSpeechIsIgnored(){val s=SpeechText();assertEquals("밸브 확인",s.accept(" 밸브 확인 "));assertNull(s.accept("밸브 확인"))}
 @Test fun backupPreservesRawAndCorrectedTranscripts(){
  val meeting=Meeting(topic="회의",rawTranscript="원문",correctedTranscript="교정문")
  val decoded=BackupCodec.decode(BackupCodec.encode(listOf(MeetingWithMessages(meeting,emptyList())))).single().first
  assertEquals("원문",decoded.rawTranscript);assertEquals("교정문",decoded.correctedTranscript)
 }
}
