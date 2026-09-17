package com.multi0819.meetingbrief
import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent

object SpeechInput {
 const val REQUEST=4102
 fun start(activity:Activity){
  val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{
   putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
   putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR")
   putExtra(RecognizerIntent.EXTRA_PROMPT,"전달사항을 말씀하세요")
  }
  activity.startActivityForResult(i,REQUEST)
 }
 fun result(data:Intent?)=data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.trim().orEmpty()
}
