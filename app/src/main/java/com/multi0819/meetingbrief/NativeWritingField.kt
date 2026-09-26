package com.multi0819.meetingbrief

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

object WritingInputConfig {
 const val inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES or InputType.TYPE_TEXT_FLAG_AUTO_CORRECT
}

class SelectionRequestTracker { private var handled=0;fun consume(request:Int):Boolean{if(request<=handled)return false;handled=request;return true} }

@Composable fun NativeWritingField(value:String,onValueChange:(String)->Unit,modifier:Modifier=Modifier,selectAllRequest:Int=0){
 val latest=rememberUpdatedState(onValueChange)
 val selectionTracker=remember{SelectionRequestTracker()}
 AndroidView(modifier=modifier,factory={context->EditText(context).apply{
  hint="전달사항 입력";inputType=WritingInputConfig.inputType;gravity=Gravity.CENTER_VERTICAL;setTextSize(18f);setTextColor(Color.DKGRAY);setHintTextColor(Color.GRAY);background=ColorDrawable(Color.TRANSPARENT);setPadding(12,0,12,0);minLines=1;maxLines=4
  addTextChangedListener(object:TextWatcher{override fun beforeTextChanged(s:CharSequence?,start:Int,count:Int,after:Int){};override fun onTextChanged(s:CharSequence?,start:Int,before:Int,count:Int){latest.value(s?.toString().orEmpty())};override fun afterTextChanged(s:Editable?) {}})
 }},update={field->if(field.text.toString()!=value)field.setText(value);if(value.isNotBlank()&&selectionTracker.consume(selectAllRequest)){field.post{field.requestFocus();field.selectAll();(field.context.getSystemService(InputMethodManager::class.java))?.showSoftInput(field,InputMethodManager.SHOW_IMPLICIT)}}})
}
