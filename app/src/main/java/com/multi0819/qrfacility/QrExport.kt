package com.multi0819.qrfacility

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

object QrExport {
    fun fileBase(name:String, code:String):String = listOf(name, code).filter{it.isNotBlank()}.joinToString("_") + "_QR"

    fun bitmap(payload:String, size:Int=900):Bitmap {
        val matrix=QRCodeWriter().encode(payload, BarcodeFormat.QR_CODE, size, size)
        return Bitmap.createBitmap(size,size,Bitmap.Config.ARGB_8888).apply {
            for(y in 0 until size) for(x in 0 until size) setPixel(x,y,if(matrix[x,y]) Color.BLACK else Color.WHITE)
        }
    }

    fun pdf(bitmap:Bitmap, title:String, subtitle:String):PdfDocument {
        val doc=PdfDocument(); val page=doc.startPage(PdfDocument.PageInfo.Builder(595,842,1).create())
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.BLACK;textAlign=Paint.Align.CENTER}
        p.textSize=28f; page.canvas.drawText(title,297.5f,80f,p)
        p.textSize=18f; page.canvas.drawText(subtitle,297.5f,115f,p)
        val left=72f; val top=160f; val side=451f
        page.canvas.drawBitmap(bitmap,null,android.graphics.RectF(left,top,left+side,top+side),p)
        doc.finishPage(page); return doc
    }
}

@Composable fun EquipmentQrPanel(e:Equipment){
    val ctx=LocalContext.current
    val payload=QrCodec.encode(e.id)
    val qr=remember(payload){QrExport.bitmap(payload)}
    val location=listOf(e.building,e.floor,e.room).filter{it.isNotBlank()}.joinToString(" › ")
    val base=QrExport.fileBase(e.name,e.code)
    val savePng=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("image/png")){uri->
        uri?.let{ctx.contentResolver.openOutputStream(it)?.use{out->qr.compress(Bitmap.CompressFormat.PNG,100,out)}}
    }
    val savePdf=rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")){uri->
        uri?.let{ctx.contentResolver.openOutputStream(it)?.use{out->QrExport.pdf(qr,e.name,QrLabel.text(e.code,"",location)).use{doc->doc.writeTo(out)}}}
    }
    Column(Modifier.fillMaxWidth().padding(top=14.dp),horizontalAlignment=Alignment.CenterHorizontally){
        Image(qr.asImageBitmap(),contentDescription="${e.name} 설비 QR",modifier=Modifier.size(230.dp))
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            Button(onClick={savePng.launch("$base.png")},modifier=Modifier.weight(1f)){Text("QR 저장")}
            OutlinedButton(onClick={savePdf.launch("$base.pdf")},modifier=Modifier.weight(1f)){Text("QR 인쇄(PDF)")}
        }
    }
}
