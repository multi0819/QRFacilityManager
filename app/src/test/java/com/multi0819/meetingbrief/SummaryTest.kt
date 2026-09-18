package com.multi0819.meetingbrief
import org.junit.Assert.*
import org.junit.Test
class SummaryTest {
 @Test fun extractsWorkAndDeadline(){val r=OfflineSummaryEngine.summarize(listOf("김대리는 냉각기 압력을 내일 오전까지 확인하세요"));assertTrue(r.actions.any{it.contains("김대리")});assertTrue(r.deadlines.any{it.contains("내일 오전")})}
 @Test fun recommendsMissingOwnerAndDeadline(){val r=OfflineSummaryEngine.summarize(listOf("냉각기 압력을 확인하세요"));assertTrue(r.recommendations.any{it.contains("담당자")});assertTrue(r.recommendations.any{it.contains("기한")})}
 @Test fun flagsCaution(){assertTrue(OfflineSummaryEngine.summarize(listOf("고압 작업 주의")).cautions.isNotEmpty())}
 @Test fun generalAnnouncementIsNotDuplicatedAsWorkOrDeadline(){
  val r=OfflineSummaryEngine.summarize(listOf("오늘은 외부 공조기 점검이 있겠습니다"))
  assertTrue(r.actions.isEmpty())
  assertTrue(r.deadlines.isEmpty())
  assertTrue(r.recommendations.isEmpty())
 }
 @Test fun uncertainPlanAppearsOnlyAsNotice(){
  val text="오늘은 특별한 내용이었고 내일은 수작업을 해야 될 것 같습니다"
  val r=OfflineSummaryEngine.summarize(listOf(text))
  assertEquals(listOf(text),r.notices);assertTrue(r.actions.isEmpty());assertTrue(r.deadlines.isEmpty())
 }
 @Test fun assignedWorkIsNotRepeatedAndDeadlineContainsOnlyTimePhrase(){
  val text="김대리는 냉각기 압력을 내일 오전까지 확인하세요"
  val r=OfflineSummaryEngine.summarize(listOf(text))
  assertTrue(r.notices.isEmpty());assertEquals(listOf(text),r.actions);assertEquals(listOf("내일 오전까지"),r.deadlines)
 }
 @Test fun formatterUsesClassifiedNoticesInsteadOfAllOriginalLines(){
  val text="김대리는 냉각기 압력을 내일 오전까지 확인하세요";val r=OfflineSummaryEngine.summarize(listOf(text));val out=MeetingFormatter.toShareText("설비회의",listOf(text),r)
  assertTrue(out.substringAfter("[전달사항]").substringBefore("[담당 업무]").contains("• 없음"))
 }
 @Test fun workNounsWithoutTheWordWorkAreClassifiedAndDatesAreNormalized(){
  val r=OfflineSummaryEngine.summarize(listOf("내일은 흡수식 12호기. 추기 작업. 다음주 월요일까지 d동 페인트 작업."))
  assertTrue(r.actions.any{it.contains("흡수식 12호기")&&it.contains("추기")})
  assertTrue(r.actions.any{it.contains("D동")&&it.contains("페인트")})
  assertTrue(r.deadlines.contains("내일"));assertTrue(r.deadlines.contains("다음 주 월요일까지"))
  assertTrue(r.notices.isEmpty())
 }
}
