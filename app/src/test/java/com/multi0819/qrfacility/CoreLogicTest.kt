package com.multi0819.qrfacility

import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CoreLogicTest {
 @Test fun rangeInsideIsNormal(){assertEquals(ResultStatus.NORMAL,InspectionEvaluator.evaluate(5.0,1.0,10.0))}
 @Test fun belowRangeIsAbnormal(){assertEquals(ResultStatus.ABNORMAL,InspectionEvaluator.evaluate(0.5,1.0,10.0))}
 @Test fun aboveRangeIsAbnormal(){assertEquals(ResultStatus.ABNORMAL,InspectionEvaluator.evaluate(11.0,1.0,10.0))}
 @Test fun missingValueIsUnchecked(){assertEquals(ResultStatus.UNCHECKED,InspectionEvaluator.evaluate(null,1.0,10.0))}
 @Test fun qrRoundTrip(){val id=UUID.randomUUID().toString();assertEquals(id,QrCodec.parse(QrCodec.encode(id)))}
 @Test fun foreignQrRejected(){assertNull(QrCodec.parse("https://example.com"))}
 @Test fun numericInspectionIsBuiltForCompactLocalStorage(){
  val item=CheckItem(id=7,equipmentId="eq-1",label="온도",inputType="NUMBER",min=10.0,max=20.0)
  val record=InspectionRecordFactory.create("eq-1",item,"15.5")
  assertEquals("eq-1",record.equipmentId)
  assertEquals(7,record.itemId)
  assertEquals("15.5",record.value)
  assertEquals("NORMAL",record.status)
 }
 @Test fun invalidNumericInspectionIsRejected(){
  val item=CheckItem(id=7,equipmentId="eq-1",label="온도",inputType="NUMBER")
  assertNull(InspectionRecordFactory.createOrNull("eq-1",item,"숫자아님"))
 }
}
