package com.multi0819.meetingbrief

import android.app.Application
import android.content.Intent
import android.os.Bundle
import android.speech.RecognizerIntent
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.room.withTransaction
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

private val KakaoYellow=Color(0xFFFEE500);private val ChatBg=Color(0xFFB2C7D9);private val Ink=Color(0xFF202124)
class MainActivity:ComponentActivity(){override fun onCreate(b:Bundle?){super.onCreate(b);setContent{MaterialTheme(colorScheme=lightColorScheme(primary=KakaoYellow,background=Color(0xFFF6F6F6),surface=Color.White,onPrimary=Ink)){MeetingApp()}}}}

class MeetingVm(app:Application):AndroidViewModel(app){
 private val db=(app as MeetingBriefApp).db;private val dao=db.dao();private val query=MutableStateFlow("")
 val all=dao.all().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 val results=query.flatMapLatest{if(it.isBlank())dao.all() else dao.search(it)}.stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
 fun setQuery(v:String){query.value=v}
 fun detail(id:Long)=dao.detail(id)
 fun create(topic:String,done:(Long)->Unit)=viewModelScope.launch{done(dao.insert(Meeting(topic=topic.ifBlank{"제목 없는 회의"})))}
 fun add(id:Long,text:String)=viewModelScope.launch{if(text.isNotBlank()){dao.add(MeetingMessage(meetingId=id,text=text.trim()));dao.touch(id,System.currentTimeMillis())}}
 fun edit(v:MeetingMessage,text:String)=viewModelScope.launch{if(text.isNotBlank()){dao.updateMessage(v.copy(text=text.trim()));dao.touch(v.meetingId,System.currentTimeMillis())}}
 fun deleteMessage(v:MeetingMessage)=viewModelScope.launch{dao.deleteMessage(v);dao.touch(v.meetingId,System.currentTimeMillis())}
 fun summarize(detail:MeetingWithMessages)=viewModelScope.launch{val s=OfflineSummaryEngine.summarize(detail.messages.map{it.text});dao.update(detail.meeting.copy(summaryText=MeetingFormatter.toShareText(detail.meeting.topic,detail.messages.map{it.text},s),recommendationsText=s.recommendations.joinToString("\n"),updatedAt=System.currentTimeMillis()))}
 fun finish(m:Meeting)=viewModelScope.launch{dao.update(m.copy(endedAt=System.currentTimeMillis(),updatedAt=System.currentTimeMillis()))}
 fun delete(m:Meeting,done:()->Unit)=viewModelScope.launch{dao.delete(m);done()}
 fun export(done:(String)->Unit)=viewModelScope.launch{done(BackupCodec.encode(dao.snapshot()))}
 fun restore(raw:String,done:(Boolean)->Unit)=viewModelScope.launch{runCatching{val rows=BackupCodec.decode(raw);db.withTransaction{dao.clearAll();rows.forEach{(m,msgs)->val id=dao.insert(m);msgs.forEach{dao.add(it.copy(meetingId=id))}}}}.onSuccess{done(true)}.onFailure{done(false)}}
}

@Composable fun MeetingApp(vm:MeetingVm=viewModel()){
 var page by remember{mutableStateOf("home")};var current by remember{mutableLongStateOf(0L)}
 when(page){"home"->HomeScreen(vm,onNew={page="new"},onOpen={current=it;page="meeting"});"new"->NewMeeting{topic->vm.create(topic){current=it;page="meeting"}};else->MeetingScreen(vm,current,onBack={page="home"})}
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun HomeScreen(vm:MeetingVm,onNew:()->Unit,onOpen:(Long)->Unit){
 var q by remember{mutableStateOf("")};val list by vm.results.collectAsState();val context=androidx.compose.ui.platform.LocalContext.current;var pendingBackup by remember{mutableStateOf("")};var notice by remember{mutableStateOf("")}
 val createBackup=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")){uri->uri?.let{context.contentResolver.openOutputStream(it)?.bufferedWriter()?.use{w->w.write(pendingBackup)}}}
 val openBackup=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()){uri->uri?.let{val raw=context.contentResolver.openInputStream(it)?.bufferedReader()?.use{r->r.readText()}.orEmpty();vm.restore(raw){notice=if(it)"복원되었습니다" else "올바른 백업 파일이 아닙니다"}}}
 Scaffold(topBar={TopAppBar(title={Text("회의 전달사항",fontWeight=FontWeight.Bold)},actions={IconButton(onClick={vm.export{pendingBackup=it;createBackup.launch("회의기록-백업.json")}}){Icon(Icons.Default.FileUpload,"백업")};IconButton(onClick={openBackup.launch(arrayOf("application/json","text/plain"))}){Icon(Icons.Default.Restore,"복원")};IconButton(onClick=onNew){Icon(Icons.Default.Add,"새 회의")}})},floatingActionButton={ExtendedFloatingActionButton(onClick=onNew,containerColor=KakaoYellow,text={Text("새 회의")},icon={Icon(Icons.Default.Add,null)})}){p->
  Column(Modifier.padding(p).fillMaxSize().padding(16.dp)){if(notice.isNotBlank())Text(notice,color=Color(0xFF287D3C));OutlinedTextField(q,{q=it;vm.setQuery(it)},modifier=Modifier.fillMaxWidth(),leadingIcon={Icon(Icons.Default.Search,null)},placeholder={Text("날짜·주제·담당자·내용 찾기")},singleLine=true,shape=RoundedCornerShape(24.dp));Spacer(Modifier.height(12.dp));if(list.isEmpty())Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){Text(if(q.isBlank())"새 회의를 시작하세요" else "검색 결과가 없습니다",color=Color.Gray)}else LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){items(list,key={it.id}){m->Card(Modifier.fillMaxWidth().clickable{onOpen(m.id)}){Column(Modifier.padding(16.dp)){Text(m.topic,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(time(m.startedAt)+(m.endedAt?.let{" ~ "+clock(it)}?:""),color=Color.Gray);if(m.summaryText.isNotBlank())Text(m.summaryText.lineSequence().firstOrNull{it.startsWith("• ")}?.removePrefix("• ")?:"요약 완료",maxLines=1)}}}}}
 }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun NewMeeting(done:(String)->Unit){var topic by remember{mutableStateOf("")};Scaffold(topBar={TopAppBar(title={Text("새 회의")})}){p->Column(Modifier.padding(p).padding(20.dp)){Text("회의 주제",fontWeight=FontWeight.Bold);OutlinedTextField(topic,{topic=it},modifier=Modifier.fillMaxWidth(),placeholder={Text("예: 시설 점검 전달사항")});Spacer(Modifier.height(16.dp));Button(onClick={done(topic)},modifier=Modifier.fillMaxWidth().height(56.dp),colors=ButtonDefaults.buttonColors(containerColor=KakaoYellow,contentColor=Ink)){Text("회의 시작",fontWeight=FontWeight.Bold)}}}}

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun MeetingScreen(vm:MeetingVm,id:Long,onBack:()->Unit){
 val context=androidx.compose.ui.platform.LocalContext.current;val detail by vm.detail(id).collectAsState(initial=null);var input by remember{mutableStateOf("")};var showSummary by remember{mutableStateOf(false)};var confirmDelete by remember{mutableStateOf(false)};var editing by remember{mutableStateOf<MeetingMessage?>(null)};var editText by remember{mutableStateOf("")};var speechError by remember{mutableStateOf("")}
 val speech=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){r->val text=SpeechInput.result(r.data);if(text.isNotBlank())vm.add(id,text)}
 val share=rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()){}
 val d=detail
 fun close(){d?.meeting?.let{vm.finish(it)};onBack()};BackHandler{close()}
 Scaffold(topBar={TopAppBar(title={Column{Text(d?.meeting?.topic?:"회의",fontWeight=FontWeight.Bold);Text(d?.meeting?.startedAt?.let(::time)?:"",style=MaterialTheme.typography.labelSmall)}},navigationIcon={IconButton(onClick={close()}){Icon(Icons.Default.ArrowBack,"뒤로")}},actions={IconButton(onClick={d?.let{row->val txt=row.meeting.summaryText.ifBlank{MeetingFormatter.toShareText(row.meeting.topic,row.messages.map{it.text},OfflineSummaryEngine.summarize(row.messages.map{it.text}))};share.launch(Intent.createChooser(Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,txt)},"공유"))}}){Icon(Icons.Default.Share,"공유")};IconButton(onClick={confirmDelete=true}){Icon(Icons.Default.Delete,"삭제")}})}){p->
  Column(Modifier.padding(p).fillMaxSize().background(ChatBg)){if(speechError.isNotBlank())Text(speechError,Modifier.background(Color.White).fillMaxWidth().padding(8.dp),color=Color.Red);LazyColumn(Modifier.weight(1f).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){d?.messages?.let{msgs->items(msgs,key={it.id}){m->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){Surface(shape=RoundedCornerShape(14.dp,2.dp,14.dp,14.dp),color=KakaoYellow,modifier=Modifier.widthIn(max=310.dp).clickable{editing=m;editText=m.text}){Text(m.text,Modifier.padding(12.dp),color=Ink)}}}};if(showSummary&&d?.meeting?.summaryText?.isNotBlank()==true)item{SummaryCard(d.meeting.summaryText)}}
   Row(Modifier.background(Color.White).padding(8.dp),verticalAlignment=Alignment.CenterVertically){IconButton(onClick={val i=Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply{putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);putExtra(RecognizerIntent.EXTRA_LANGUAGE,"ko-KR");putExtra(RecognizerIntent.EXTRA_PROMPT,"전달사항을 말씀하세요")};if(i.resolveActivity(context.packageManager)!=null){speechError="";speech.launch(i)}else speechError="이 휴대폰에는 음성인식 기능이 없습니다. 글로 입력하세요."}){Icon(Icons.Default.Mic,"음성")};OutlinedTextField(input,{input=it},modifier=Modifier.weight(1f),placeholder={Text("전달사항 입력")},maxLines=4);IconButton(onClick={vm.add(id,input);input=""},enabled=input.isNotBlank()){Icon(Icons.Default.Send,"보내기")};Button(onClick={d?.let{vm.summarize(it);showSummary=true}},colors=ButtonDefaults.buttonColors(containerColor=KakaoYellow,contentColor=Ink),contentPadding=PaddingValues(horizontal=12.dp)){Text("요약")}}
  }
 }
 if(confirmDelete)AlertDialog(onDismissRequest={confirmDelete=false},title={Text("회의 기록 삭제")},text={Text("이 기록을 삭제할까요?")},confirmButton={TextButton(onClick={d?.meeting?.let{vm.delete(it,onBack)}}){Text("삭제",color=Color.Red)}},dismissButton={TextButton(onClick={confirmDelete=false}){Text("취소")}})
 editing?.let{m->AlertDialog(onDismissRequest={editing=null},title={Text("전달사항 수정")},text={OutlinedTextField(editText,{editText=it})},confirmButton={TextButton(onClick={vm.edit(m,editText);editing=null}){Text("저장")}},dismissButton={Row{TextButton(onClick={vm.deleteMessage(m);editing=null}){Text("삭제",color=Color.Red)};TextButton(onClick={editing=null}){Text("취소")}}})}
}

@Composable private fun SummaryCard(text:String){Card(colors=CardDefaults.cardColors(containerColor=Color.White),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("회의 요약",fontWeight=FontWeight.Bold);Spacer(Modifier.height(8.dp));Text(text)}}}
private fun time(v:Long)=SimpleDateFormat("yyyy-MM-dd HH:mm",Locale.KOREA).format(Date(v))
private fun clock(v:Long)=SimpleDateFormat("HH:mm",Locale.KOREA).format(Date(v))
