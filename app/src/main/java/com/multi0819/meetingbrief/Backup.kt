package com.multi0819.meetingbrief
import org.json.JSONArray
import org.json.JSONObject

object BackupCodec {
 fun encode(rows:List<MeetingWithMessages>)=JSONObject().put("version",1).put("meetings",JSONArray().apply{rows.forEach{r->put(JSONObject().put("topic",r.meeting.topic).put("startedAt",r.meeting.startedAt).put("endedAt",r.meeting.endedAt?:JSONObject.NULL).put("summary",r.meeting.summaryText).put("recommendations",r.meeting.recommendationsText).put("messages",JSONArray().apply{r.messages.forEach{put(JSONObject().put("text",it.text).put("createdAt",it.createdAt))}}))}}).toString()
 fun decode(raw:String):List<Pair<Meeting,List<MeetingMessage>>>{val root=JSONObject(raw);require(root.getInt("version")==1);val a=root.getJSONArray("meetings");return (0 until a.length()).map{i->val o=a.getJSONObject(i);val m=Meeting(topic=o.getString("topic"),startedAt=o.getLong("startedAt"),endedAt=if(o.isNull("endedAt"))null else o.getLong("endedAt"),summaryText=o.optString("summary"),recommendationsText=o.optString("recommendations"));val ms=o.getJSONArray("messages");m to (0 until ms.length()).map{j->val x=ms.getJSONObject(j);MeetingMessage(meetingId=0,text=x.getString("text"),createdAt=x.getLong("createdAt"))}}
 }
}
