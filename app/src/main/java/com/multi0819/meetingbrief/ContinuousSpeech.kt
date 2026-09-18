package com.multi0819.meetingbrief
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class TranscriptAccumulator {
 private val lines=mutableListOf<String>()
 fun append(raw:String):String?{val clean=SpeechCleanup.clean(raw);if(clean.isBlank()||lines.lastOrNull()==clean)return null;lines+=clean;return clean}
 fun all():List<String> = lines.toList()
 fun completedText():String = lines.joinToString(" ")
 fun reset(){lines.clear()}
}
object SpeechCleanup { fun clean(raw:String):String{var s=raw.trim().replace(Regex("\\s+")," ");s=s.replace(Regex("^(음|어|아|그)\\s+"),"");if(s.isNotBlank()&&!Regex("[.!?。]$").containsMatchIn(s))s+=".";return s} }

class ContinuousSpeech(context:Context,private val onPartial:(String)->Unit,private val onCompleted:(String)->Unit,private val onState:(Boolean)->Unit,private val onError:(String)->Unit):RecognitionListener {
 private val main=Handler(Looper.getMainLooper())
 private val recognizer=SpeechRecognizer.createSpeechRecognizer(context.applicationContext).also{it.setRecognitionListener(this)}
 private val accumulator=TranscriptAccumulator();private var active=false;private var latestPartial=""
 private val intent=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,1);putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1800L);putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1200L)}
 fun start(){if(active)return;accumulator.reset();latestPartial="";active=true;onPartial("");onState(true);listen()}
 fun stop(){if(!active)return;active=false;latestPartial.takeIf{it.isNotBlank()}?.let{accumulator.append(it)};latestPartial="";recognizer.stopListening();onState(false);onPartial("");accumulator.completedText().takeIf{it.isNotBlank()}?.let(onCompleted)}
 fun destroy(){active=false;recognizer.cancel();recognizer.destroy()}
 private fun listen(){if(!active)return;runCatching{recognizer.startListening(intent)}.onFailure{onError("음성인식을 시작할 수 없습니다.");restart(800)}}
 private fun restart(delay:Long=250){if(active)main.postDelayed({listen()},delay)}
 override fun onPartialResults(v:Bundle?){if(!active)return;latestPartial=v?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();onPartial(listOf(accumulator.completedText(),latestPartial).filter{it.isNotBlank()}.joinToString(" "))}
 override fun onResults(v:Bundle?){if(!active)return;val raw=v?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty();accumulator.append(raw);latestPartial="";onPartial(accumulator.completedText());restart()}
 override fun onError(code:Int){if(!active)return;onPartial("");if(code==SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS){active=false;onState(false);onError("마이크 권한이 필요합니다.")}else restart(if(code==SpeechRecognizer.ERROR_RECOGNIZER_BUSY)800 else 300)}
 override fun onReadyForSpeech(v:Bundle?){};override fun onBeginningOfSpeech(){};override fun onRmsChanged(v:Float){};override fun onBufferReceived(v:ByteArray?){};override fun onEndOfSpeech(){};override fun onEvent(type:Int,v:Bundle?){}
}
