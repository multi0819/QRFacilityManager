package com.multi0819.qrfacility

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

private val Navy=Color(0xFF071525); private val Card=Color(0xFF10243A); private val Cyan=Color(0xFF5BD6FF); private val Good=Color(0xFF49D17D); private val Bad=Color(0xFFFF6262)
class MainActivity:ComponentActivity(){ override fun onCreate(b:Bundle?){super.onCreate(b);setContent{ MaterialTheme(colorScheme=darkColorScheme(primary=Cyan,background=Navy,surface=Card)){ FacilityRoot() } }} }

class FacilityVm(app:android.app.Application):AndroidViewModel(app){
 private val dao=(app as FacilityApp).db.dao(); val equipment=dao.equipment().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000), emptyList())
 fun save(name:String,code:String,type:String,building:String,floor:String,room:String){viewModelScope.launch{dao.saveEquipment(Equipment(name=name,code=code,type=type,building=building,floor=floor,room=room))}}
 fun addItem(eid:String,label:String,type:String,min:String,max:String){viewModelScope.launch{dao.addCheckItem(CheckItem(equipmentId=eid,label=label,inputType=type,min=min.toDoubleOrNull(),max=max.toDoubleOrNull()))}}
 fun inspect(eid:String,item:CheckItem,value:String){viewModelScope.launch{ val status=if(item.inputType=="NUMBER") InspectionEvaluator.evaluate(value.toDoubleOrNull(),item.min,item.max).name else if(value=="정상") "NORMAL" else "ABNORMAL"; dao.addInspection(Inspection(equipmentId=eid,itemId=item.id,value=value,status=status)) }}
 fun maintain(eid:String,work:String,worker:String){viewModelScope.launch{dao.addMaintenance(Maintenance(equipmentId=eid,work=work,worker=worker))}}
 fun items(id:String)=dao.checkItems(id); fun maintenance(id:String)=dao.maintenance(id)
}

enum class Page{HOME,LIST,ADD,SCAN,DETAIL}
@Composable fun FacilityRoot(vm:FacilityVm=viewModel()){
 var page by remember{mutableStateOf(Page.HOME)}; var selected by remember{mutableStateOf<Equipment?>(null)}
 Scaffold(bottomBar={NavigationBar(containerColor=Card){ listOf(Page.HOME to "홈",Page.LIST to "설비",Page.SCAN to "QR",Page.ADD to "등록").forEach{(p,t)->NavigationBarItem(selected=page==p,onClick={page=p},icon={Icon(if(p==Page.SCAN)Icons.Default.QrCodeScanner else if(p==Page.ADD)Icons.Default.Add else if(p==Page.LIST)Icons.Default.List else Icons.Default.Home,null)},label={Text(t)})}}}){pad-> Box(Modifier.padding(pad).fillMaxSize()){when(page){Page.HOME->Home(vm){page=Page.SCAN};Page.LIST->EquipmentList(vm){selected=it;page=Page.DETAIL};Page.ADD->AddEquipment(vm){page=Page.LIST};Page.SCAN->QrScanner{ id-> selected=vm.equipment.value.firstOrNull{it.id==id}; if(selected!=null)page=Page.DETAIL};Page.DETAIL->selected?.let{EquipmentDetail(vm,it)} ?: Text("설비를 찾을 수 없습니다")}}}
}
@Composable fun Header(title:String,sub:String=""){Column(Modifier.fillMaxWidth().padding(20.dp)){Text(title,style=MaterialTheme.typography.headlineMedium,fontWeight=FontWeight.Bold);if(sub.isNotBlank())Text(sub,color=Color.LightGray)}}
@Composable fun Home(vm:FacilityVm,onScan:()->Unit){val list by vm.equipment.collectAsState();Column{Header("QR 설비관리","현장 설비를 빠르고 정확하게") ;Row(Modifier.padding(horizontal=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)){Stat("전체 설비",list.size.toString(),Modifier.weight(1f));Stat("오늘 점검","QR로 시작",Modifier.weight(1f))};Spacer(Modifier.height(18.dp));Button(onClick=onScan,modifier=Modifier.padding(16.dp).fillMaxWidth().height(64.dp)){Icon(Icons.Default.QrCodeScanner,null);Spacer(Modifier.width(10.dp));Text("QR 스캔",style=MaterialTheme.typography.titleLarge)}}}
@Composable fun Stat(t:String,v:String,m:Modifier){Card(m){Column(Modifier.padding(18.dp)){Text(t,color=Color.LightGray);Text(v,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.Bold)}}}
@Composable fun EquipmentList(vm:FacilityVm,onOpen:(Equipment)->Unit){val list by vm.equipment.collectAsState();Column{Header("설비 목록","동 · 층 · 구역별 관리");LazyColumn{items(list){e->Card(onClick={onOpen(e)},modifier=Modifier.padding(horizontal=14.dp,vertical=5.dp).fillMaxWidth()){Column(Modifier.padding(16.dp)){Text(e.name,fontWeight=FontWeight.Bold,style=MaterialTheme.typography.titleMedium);Text(listOf(e.building,e.floor,e.room).filter{it.isNotBlank()}.joinToString("  ›  "),color=Color.LightGray);Text(e.type,color=Cyan)}}}}}}
@Composable fun AddEquipment(vm:FacilityVm,done:()->Unit){var name by remember{mutableStateOf("")};var code by remember{mutableStateOf("")};var type by remember{mutableStateOf("")};var b by remember{mutableStateOf("")};var f by remember{mutableStateOf("")};var r by remember{mutableStateOf("")};Column{Header("설비 등록","종류와 위치를 자유롭게 입력");LazyColumn(Modifier.padding(horizontal=16.dp)){item{Field("설비명",name){name=it};Field("관리번호",code){code=it};Field("설비 종류",type){type=it};Field("동",b){b=it};Field("층",f){f=it};Field("구역 / 실",r){r=it};Button(enabled=name.isNotBlank(),onClick={vm.save(name,code,type,b,f,r);done()},modifier=Modifier.fillMaxWidth().height(56.dp)){Text("등록하고 QR 만들기")}}}}}
@Composable fun Field(label:String,v:String,set:(String)->Unit){OutlinedTextField(v,set,label={Text(label)},modifier=Modifier.fillMaxWidth().padding(vertical=5.dp),singleLine=true)}
@Composable fun EquipmentDetail(vm:FacilityVm,e:Equipment){val checks by vm.items(e.id).collectAsState(initial=emptyList());val maint by vm.maintenance(e.id).collectAsState(initial=emptyList());var label by remember{mutableStateOf("")};var min by remember{mutableStateOf("")};var max by remember{mutableStateOf("")};var work by remember{mutableStateOf("")};var worker by remember{mutableStateOf("")};LazyColumn{item{Header(e.name,listOf(e.building,e.floor,e.room).filter{it.isNotBlank()}.joinToString(" › "));Card(Modifier.padding(16.dp).fillMaxWidth()){Column(Modifier.padding(16.dp)){Text("설비정보",fontWeight=FontWeight.Bold);Text("관리번호  ${e.code.ifBlank{"-"}}");Text("종류  ${e.type.ifBlank{"-"}}");Text("QR  ${QrCodec.encode(e.id)}",style=MaterialTheme.typography.bodySmall,color=Cyan)}};Text("오늘 점검",Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold)};items(checks){c->CheckRow(c){vm.inspect(e.id,c,it)}};item{Card(Modifier.padding(16.dp).fillMaxWidth()){Column(Modifier.padding(14.dp)){Text("점검항목 추가",fontWeight=FontWeight.Bold);Field("항목명",label){label=it};Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){OutlinedTextField(min,{min=it},label={Text("최소")},modifier=Modifier.weight(1f));OutlinedTextField(max,{max=it},label={Text("최대")},modifier=Modifier.weight(1f))};Button(enabled=label.isNotBlank(),onClick={vm.addItem(e.id,label,"NUMBER",min,max);label=""},modifier=Modifier.fillMaxWidth()){Text("숫자 점검항목 추가")}}};Text("정비이력",Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);Card(Modifier.padding(horizontal=16.dp).fillMaxWidth()){Column(Modifier.padding(14.dp)){Field("작업내용",work){work=it};Field("작업자",worker){worker=it};Button(enabled=work.isNotBlank(),onClick={vm.maintain(e.id,work,worker);work=""},modifier=Modifier.fillMaxWidth()){Text("정비 기록 저장")}}}};items(maint){m->ListItem(headlineContent={Text(m.work)},supportingContent={Text(m.worker.ifBlank{"작업자 미입력"})})}}
}
@Composable fun CheckRow(c:CheckItem,save:(String)->Unit){var v by remember{mutableStateOf("")};Card(Modifier.padding(horizontal=16.dp,vertical=5.dp).fillMaxWidth()){Column(Modifier.padding(14.dp)){Text(c.label,fontWeight=FontWeight.Bold);if(c.inputType=="NUMBER"){Text("정상범위 ${c.min?:"-"} ~ ${c.max?:"-"}",color=Color.LightGray);Row(verticalAlignment=Alignment.CenterVertically){OutlinedTextField(v,{v=it},label={Text("측정값")},modifier=Modifier.weight(1f));Spacer(Modifier.width(8.dp));Button(onClick={save(v)},enabled=v.toDoubleOrNull()!=null){Text("저장")}}}else Row{Button(onClick={save("정상")}){Text("정상")};Spacer(Modifier.width(8.dp));Button(onClick={save("이상")}){Text("이상")}}}}}
