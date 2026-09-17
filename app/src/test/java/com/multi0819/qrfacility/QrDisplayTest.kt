package com.multi0819.qrfacility

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrDisplayTest {
    @Test fun qrPayload_keepsStableEquipmentUri() {
        val id = "22897778-c9e8-4a8c-b913-e5575295050e"
        assertEquals("facilityqr://equipment/$id", QrCodec.encode(id))
    }

    @Test fun qrLabel_hasReadableEquipmentIdentity() {
        val label = QrLabel.text("흡수식1호기", "A1", "E › 지하6 › 2블럭")
        assertTrue(label.contains("흡수식1호기"))
        assertTrue(label.contains("A1"))
        assertTrue(label.contains("E › 지하6 › 2블럭"))
    }
}
