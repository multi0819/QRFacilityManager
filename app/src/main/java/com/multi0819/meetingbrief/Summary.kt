package com.multi0819.meetingbrief

data class MeetingSummary(val notices:List<String>,val actions:List<String>,val deadlines:List<String>,val cautions:List<String>,val confirmations:List<String>,val recommendations:List<String>)

object OfflineSummaryEngine {
 private val directAction=Regex("하세요|하십시오|바랍니다|해 주세요|확인해 주세요|점검해 주세요|조치해 주세요|보고해 주세요|제출해 주세요|완료해 주세요|교체해 주세요|정리해 주세요|전달해 주세요")
 private val workIntent=Regex("해야|확인|점검|조치|보고|제출|완료|교체|정리|전달")
 private val deadline=Regex("(?:오늘|내일|모레)(?:\\s*(?:오전|오후)(?:\\s*\\d{1,2}시?)?)?(?:까지)?|(?:이번 주|다음 주)(?:\\s*[월화수목금토일]요일)?(?:까지)?|\\d{1,2}[./월-]\\d{1,2}(?:일)?(?:\\s*(?:오전|오후)?\\s*\\d{1,2}시?)?(?:까지)?|(?:오전|오후)\\s*\\d{1,2}시?(?:까지)?")
 private val owner=Regex("[가-힣]{2,4}(님|씨|대리|과장|팀장|부장|차장|주임|담당)")
 fun summarize(lines:List<String>):MeetingSummary {
  val clean=lines.map(String::trim).filter(String::isNotBlank)
  val actions=clean.filter{directAction.containsMatchIn(it)||(owner.containsMatchIn(it)&&workIntent.containsMatchIn(it))}
  val cautions=clean.filterNot(actions::contains).filter{Regex("주의|금지|위험|고장|이상|누전|안전").containsMatchIn(it)}
  val confirmations=clean.filterNot(actions::contains).filterNot(cautions::contains).filter{Regex("확인 필요|미정|추후|모름|검토|아마|예정").containsMatchIn(it)}
  val notices=clean.filterNot(actions::contains).filterNot(cautions::contains).filterNot(confirmations::contains)
  val deadlines=actions.flatMap{deadline.findAll(it).map(MatchResult::value).toList()}.distinct()
  val rec=buildList {
   if(actions.isNotEmpty()&&clean.none{owner.containsMatchIn(it)})add("담당자가 지정되지 않았습니다.")
   if(actions.isNotEmpty()&&deadlines.isEmpty())add("완료 기한이 지정되지 않았습니다.")
   if(actions.isNotEmpty()&&clean.none{it.contains("보고")||it.contains("공유")})add("완료 후 보고 방법을 확인하세요.")
   if(confirmations.isNotEmpty())add("확인이 필요한 내용을 담당자에게 재확인하세요.")
   if(clean.isEmpty())add("전달사항을 먼저 입력하세요.")
  }
  return MeetingSummary(notices,actions,deadlines,cautions,confirmations,rec)
 }
}

object MeetingFormatter {
 fun toShareText(topic:String,lines:List<String>,s:MeetingSummary)=buildString {
  appendLine(topic);appendLine();appendLine("[전달사항]");(s.notices.ifEmpty{listOf("없음")}).forEach{appendLine("• $it")}
  appendLine();appendLine("[담당 업무]");(s.actions.ifEmpty{listOf("없음")}).forEach{appendLine("• $it")}
  appendLine();appendLine("[기한]");(s.deadlines.ifEmpty{listOf("미정")}).forEach{appendLine("• $it")}
  appendLine();appendLine("[주의·확인사항]");(s.cautions+s.confirmations).ifEmpty{listOf("없음")}.forEach{appendLine("• $it")}
  appendLine();appendLine("[추천사항]");(s.recommendations.ifEmpty{listOf("추가 추천사항 없음")}).forEach{appendLine("• $it")}
 }
}

class SpeechText { private var last=""; fun accept(raw:String):String?{val clean=raw.trim();if(clean.isBlank()||clean==last)return null;last=clean;return clean} }
