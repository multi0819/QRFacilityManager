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
}
