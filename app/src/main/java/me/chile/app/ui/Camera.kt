package me.chile.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.DialogWindowProvider
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import java.io.File
import java.util.concurrent.TimeUnit

@Composable fun CameraSheet(onClose: ()->Unit,onPhoto: (Uri)->Unit) {
    val context=LocalContext.current
    val colors=MaterialTheme.colorScheme
    val lifecycle=LocalLifecycleOwner.current
    var granted by remember {mutableStateOf(ContextCompat.checkSelfPermission(context,Manifest.permission.CAMERA)==PackageManager.PERMISSION_GRANTED)}
    var error by remember {mutableStateOf<String?>(null)}
    var ready by remember {mutableStateOf(false)}
    var capturing by remember {mutableStateOf(false)}
    var alive by remember {mutableStateOf(true)}
    var camera by remember {mutableStateOf<Camera?>(null)}
    var flash by remember {mutableIntStateOf(ImageCapture.FLASH_MODE_OFF)}
    var zoom by remember {mutableFloatStateOf(1f)}
    var focusPoint by remember {mutableStateOf<Offset?>(null)}
    var focusTick by remember {mutableIntStateOf(0)}
    val focusAlpha=remember {Animatable(0f)}
    val focusScale=remember {Animatable(1f)}
    val executor=remember(context){ContextCompat.getMainExecutor(context)}
    val view=remember {PreviewView(context).apply {implementationMode=PreviewView.ImplementationMode.COMPATIBLE;scaleType=PreviewView.ScaleType.FILL_CENTER}}
    val preview=remember {Preview.Builder().build()}
    val capture=remember {ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build()}
    fun setZoom(ratio: Float) {
        val bound=camera?:return
        val state=bound.cameraInfo.zoomState.value?:return
        zoom=ratio.coerceIn(state.minZoomRatio,state.maxZoomRatio)
        runCatching {bound.cameraControl.setZoomRatio(zoom)}
    }
    fun focusAt(x: Float,y: Float) {
        if(!ready)return
        focusPoint=Offset(x,y);focusTick++
        val point=view.meteringPointFactory.createPoint(x,y)
        runCatching {camera?.cameraControl?.startFocusAndMetering(FocusMeteringAction.Builder(point,FocusMeteringAction.FLAG_AF or FocusMeteringAction.FLAG_AE).setAutoCancelDuration(3,TimeUnit.SECONDS).build())}
    }
    val scaleDetector=remember {ScaleGestureDetector(context,object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {setZoom(zoom*detector.scaleFactor);return true}
    })}
    val tapDetector=remember {GestureDetector(context,object: GestureDetector.SimpleOnGestureListener() {
        override fun onDown(event: MotionEvent)=true
        override fun onSingleTapUp(event: MotionEvent): Boolean {focusAt(event.x,event.y);return true}
    })}
    LaunchedEffect(focusTick) {if(focusTick>0){focusAlpha.snapTo(1f);focusScale.snapTo(1.2f);focusScale.animateTo(1f,tween(180));delay(700);focusAlpha.animateTo(0f,tween(200))}}
    val permission=rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()){granted=it;if(!it)error="允许相机权限后可以拍照，也可以从相册选择。"}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->if(uri!=null)onPhoto(uri)}
    LaunchedEffect(Unit){if(!granted)permission.launch(Manifest.permission.CAMERA)}
    DisposableEffect(granted) {
        var disposed=false
        var provider: ProcessCameraProvider?=null
        if(granted) {
            val future=ProcessCameraProvider.getInstance(context)
            future.addListener({if(!disposed)try {
                provider=future.get();preview.setSurfaceProvider(view.surfaceProvider)
                camera=provider!!.bindToLifecycle(lifecycle,CameraSelector.DEFAULT_BACK_CAMERA,preview,capture)
                ready=true;error=null
            }catch(e: Exception){error="相机暂时不可用，可以从相册选择。"}},executor)
        }
        onDispose {disposed=true;ready=false;camera=null;provider?.unbind(preview,capture)}
    }
    DisposableEffect(Unit){onDispose{alive=false}}
    Dialog(onDismissRequest=onClose,properties=DialogProperties(usePlatformDefaultWidth=false,decorFitsSystemWindows=false)) {
        val window=(LocalView.current.parent as? DialogWindowProvider)?.window
        SideEffect {window?.let {WindowCompat.getInsetsController(it,it.decorView).apply {isAppearanceLightStatusBars=colors.background.luminance()>.5f;isAppearanceLightNavigationBars=colors.background.luminance()>.5f}}}
        Column(Modifier.fillMaxSize().background(colors.background).safeDrawingPadding().padding(horizontal=16.dp)) {
            Row(Modifier.fillMaxWidth().height(64.dp),verticalAlignment=Alignment.CenterVertically) {
                IconButton(onClick=onClose,modifier=Modifier.size(48.dp).background(colors.surface,CircleShape).semantics{contentDescription="关闭拍照"}) {GlyphIcon(Glyph.CLOSE,color=colors.onSurface)}
                Spacer(Modifier.weight(1f))
                val flashAvailable=camera?.cameraInfo?.hasFlashUnit()==true
                IconButton(onClick={flash=when(flash){ImageCapture.FLASH_MODE_OFF->ImageCapture.FLASH_MODE_AUTO;ImageCapture.FLASH_MODE_AUTO->ImageCapture.FLASH_MODE_ON;else->ImageCapture.FLASH_MODE_OFF};capture.flashMode=flash},enabled=flashAvailable&&!capturing,
                    modifier=Modifier.size(48.dp).background(if(flash==ImageCapture.FLASH_MODE_OFF)colors.surface else colors.primaryContainer,CircleShape).semantics {contentDescription=when(flash){ImageCapture.FLASH_MODE_AUTO->"闪光灯自动";ImageCapture.FLASH_MODE_ON->"闪光灯开启";else->"闪光灯关闭"}}) {
                    Crossfade(flash,animationSpec=tween(180),label="闪光灯状态") {mode->
                        GlyphIcon(when(mode){ImageCapture.FLASH_MODE_AUTO->Glyph.FLASH_AUTO;ImageCapture.FLASH_MODE_ON->Glyph.FLASH;else->Glyph.FLASH_OFF},color=(if(mode==ImageCapture.FLASH_MODE_OFF)colors.onSurfaceVariant else colors.primary).copy(alpha=if(flashAvailable&&!capturing)1f else .38f))
                    }
                }
            }
            BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                val previewWidth=minOf(maxWidth,maxHeight*3f/4f)
                Box(Modifier.width(previewWidth).aspectRatio(3f/4f).clip(RoundedCornerShape(28.dp)).background(colors.surfaceContainerHigh).semantics {contentDescription="相机预览"}) {
                    if(granted)AndroidView(factory={view.apply {
                        setOnTouchListener {v,event->
                            scaleDetector.onTouchEvent(event)
                            if(!scaleDetector.isInProgress)tapDetector.onTouchEvent(event)
                            if(event.action==MotionEvent.ACTION_UP)v.performClick()
                            true
                        }
                    }},modifier=Modifier.fillMaxSize())
                    focusPoint?.let {point->if(focusAlpha.value>0f)Canvas(Modifier.fillMaxSize().semantics {contentDescription="对焦位置"}) {
                        val side=58.dp.toPx()*focusScale.value
                        val x=point.x.coerceIn(side/2,size.width-side/2);val y=point.y.coerceIn(side/2,size.height-side/2)
                        drawRoundRect(Color.White.copy(alpha=focusAlpha.value),Offset(x-side/2,y-side/2),Size(side,side),CornerRadius(12.dp.toPx()),style=Stroke(2.dp.toPx()))
                    }}
                    error?.let {message->Column(Modifier.align(Alignment.Center).padding(24.dp).background(colors.surface,RoundedCornerShape(20.dp)).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(message,color=colors.onSurface)
                        if(!granted)TextButton(onClick={permission.launch(Manifest.permission.CAMERA)}){Text("允许使用相机",color=colors.primary)}
                    }}
                }
            }
            Box(Modifier.fillMaxWidth().padding(top=12.dp),contentAlignment=Alignment.Center) {
                TextButton(onClick={setZoom(if(zoom<1.5f)2f else 1f)},enabled=ready,modifier=Modifier.background(colors.primaryContainer,CircleShape).semantics {contentDescription="切换变焦"}) {Text(String.format(java.util.Locale.ROOT,"%.1f×",zoom),color=colors.primary)}
            }
            Box(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=24.dp)) {
                IconButton(onClick={gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))},enabled=!capturing,modifier=Modifier.align(Alignment.CenterStart).size(56.dp).background(colors.surface,RoundedCornerShape(18.dp)).semantics{contentDescription="从相册选择图片"}) {GlyphIcon(Glyph.IMAGE,color=colors.primary)}
                IconButton(onClick={
                    capturing=true
                    val file=try {File.createTempFile("capture-",".jpg",File(context.cacheDir,"camera").apply {mkdirs()})}catch(e: Exception){capturing=false;error="无法保存照片，请检查空间";null}
                    if(file!=null) {
                        capture.targetRotation=view.display?.rotation?:android.view.Surface.ROTATION_0
                        try {capture.takePicture(ImageCapture.OutputFileOptions.Builder(file).build(),executor,object: ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(result: ImageCapture.OutputFileResults){if(alive){capturing=false;onPhoto(Uri.fromFile(file))}else file.delete()}
                            override fun onError(exception: ImageCaptureException){file.delete();if(alive){capturing=false;error="拍摄失败，请重试或从相册选择"}}
                        })}catch(e: Exception){file.delete();capturing=false;error="拍摄失败，请重试"}
                    }
                },enabled=ready&&!capturing,modifier=Modifier.align(Alignment.Center).size(80.dp).border(2.dp,colors.primary.copy(alpha=if(ready)1f else .3f),CircleShape).padding(7.dp).background(Brush.linearGradient(if(ready)listOf(colors.primary,colors.primary.copy(alpha=.78f)) else listOf(colors.outlineVariant,colors.outlineVariant)),CircleShape).semantics{contentDescription="拍摄照片"}) {
                    if(capturing)CircularProgressIndicator(Modifier.size(26.dp),color=colors.onPrimary,strokeWidth=2.dp)
                }
            }
        }
    }
}
