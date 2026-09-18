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
}
