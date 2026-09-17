package com.multi0819.qrfacility

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors

@Composable fun QrScanner(onFound:(String)->Unit){
 val ctx=LocalContext.current; var allowed by remember{mutableStateOf(ContextCompat.checkSelfPermission(ctx,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
 val ask=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){allowed=it}
 LaunchedEffect(Unit){if(!allowed)ask.launch(Manifest.permission.CAMERA)}
 Column{Header("QR 스캔","설비 QR을 화면 중앙에 맞추세요");if(allowed) CameraScanner(onFound) else Text("카메라 권한이 필요합니다.",Modifier.padding(20.dp))}
}
@Composable private fun CameraScanner(onFound:(String)->Unit){
 val ctx=LocalContext.current;val owner=LocalLifecycleOwner.current;val executor=remember{Executors.newSingleThreadExecutor()};var sent by remember{mutableStateOf(false)}
 DisposableEffect(Unit){onDispose{executor.shutdown()}}
 AndroidView(factory={c->PreviewView(c).apply{scaleType=PreviewView.ScaleType.FILL_CENTER}},modifier=Modifier.fillMaxSize(),update={view->
  val future=ProcessCameraProvider.getInstance(ctx);future.addListener({val provider=future.get();val preview=Preview.Builder().build().also{it.surfaceProvider=view.surfaceProvider};val analysis=ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build();val scanner=BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build());analysis.setAnalyzer(executor){proxy->val media=proxy.image;if(media==null){proxy.close();return@setAnalyzer};scanner.process(InputImage.fromMediaImage(media,proxy.imageInfo.rotationDegrees)).addOnSuccessListener{codes->if(!sent){codes.firstNotNullOfOrNull{it.rawValue?.let(QrCodec::parse)}?.let{sent=true;onFound(it)}}}.addOnCompleteListener{proxy.close()}};provider.unbindAll();provider.bindToLifecycle(owner,CameraSelector.DEFAULT_BACK_CAMERA,preview,analysis)},ContextCompat.getMainExecutor(ctx))
 })
}
