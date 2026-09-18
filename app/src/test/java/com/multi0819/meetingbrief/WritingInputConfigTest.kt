package com.multi0819.meetingbrief
import android.text.InputType
import org.junit.Assert.assertTrue
import org.junit.Test
class WritingInputConfigTest {
 @Test fun enablesSamsungWritingToolsCompatibleTextInput(){
  val type=WritingInputConfig.inputType
  assertTrue(type and InputType.TYPE_CLASS_TEXT!=0)
  assertTrue(type and InputType.TYPE_TEXT_FLAG_MULTI_LINE!=0)
  assertTrue(type and InputType.TYPE_TEXT_FLAG_CAP_SENTENCES!=0)
 }
}
