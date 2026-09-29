package me.chile.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.*
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.repeatOnLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import me.chile.app.AppViewModel

@Composable internal fun ChatComposer(vm: AppViewModel,keyboard: Boolean) {
    val focus=LocalFocusManager.current
    val ime=LocalSoftwareKeyboardController.current
    val requester=remember {FocusRequester()}
    val lifecycle=LocalLifecycleOwner.current
    var cameraOpen by rememberSaveable {mutableStateOf(false)}
    var focused by remember {mutableStateOf(false)}
    val hints=remember {listOf("发张外卖截图，帮你记餐","拍下这一餐，核对后记录","左滑查看数据","右滑查看记录","记一下刚才的运动","今天按久坐少动计算","把体重改为 65 公斤","看看这七天的进度","这份外卖大概多少热量？","说说今天吃了什么").shuffled()}
    var hintIndex by rememberSaveable {mutableIntStateOf(0)}
    LaunchedEffect(lifecycle,focused,vm.draft.isEmpty(),vm.busy) {
        if(!focused && vm.draft.isEmpty() && !vm.busy)lifecycle.lifecycle.repeatOnLifecycle(Lifecycle.State.STARTED) {
            while(true){delay(5000);hintIndex=(hintIndex+1)%hints.size}
        }
    }
    // animateContentSize clips its bounds. Keep the Surface shadow inside those bounds,
    // with a transparent gutter on both sides so the rounded edge is never cut flat.
    Column(Modifier.animateContentSize(tween(220)).padding(horizontal=8.dp).padding(bottom=12.dp,top=6.dp)) {
        AnimatedContent(vm.data.pendingPhoto,transitionSpec={fadeIn(tween(180))+slideInVertically {it/3} togetherWith fadeOut(tween(120))},label="照片预览") {name->
            if(name!=null)Row(verticalAlignment=Alignment.CenterVertically,modifier=Modifier.padding(bottom=8.dp)) {
                Photo(vm,name,Modifier.size(if(keyboard)48.dp else 80.dp),256)
                IconAction(Glyph.CLOSE,"移除待发送照片",!vm.busy,vm::removePhoto)
            }
        }
        Surface(shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp,border=paperEdge(0.15625.dp)) {
            Row(Modifier.fillMaxWidth().padding(6.dp),verticalAlignment=Alignment.CenterVertically) {
                AnimatedVisibility(vm.draft.isBlank(),enter=fadeIn(tween(200))+expandHorizontally(tween(260,easing=FastOutSlowInEasing),expandFrom=Alignment.Start),exit=fadeOut(tween(160))+shrinkHorizontally(tween(260,easing=FastOutSlowInEasing),shrinkTowards=Alignment.Start)) {
                    IconAction(Glyph.CAMERA,"拍照或选择图片",!vm.busy){focus.clearFocus();ime?.hide();cameraOpen=true}
                }
                Box(Modifier.weight(1f).padding(horizontal=6.dp).heightIn(min=44.dp),contentAlignment=Alignment.CenterStart) {
                    BasicTextField(vm.draft,vm::updateDraft,modifier=Modifier.fillMaxWidth().heightIn(min=42.dp).clipToBounds().focusRequester(requester)
                        .onFocusChanged {focused=it.isFocused}.semantics {contentDescription="消息输入"},maxLines=2,
                        textStyle=MaterialTheme.typography.bodyLarge.copy(color=MaterialTheme.colorScheme.onSurface),cursorBrush=SolidColor(MaterialTheme.colorScheme.primary),
                        decorationBox={inner->Box(contentAlignment=Alignment.CenterStart){if(vm.draft.isEmpty())Crossfade(if(focused)"发消息…" else hints[hintIndex],animationSpec=tween(180),label="输入提示"){Text(it,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1,overflow=TextOverflow.Ellipsis)};inner()}})
                }
                // Keep a fixed slot so the circle grows around one point without a width mask.
                val action=when {
                    vm.busy->"stop"
                    vm.draft.isNotBlank() || vm.data.pendingPhoto!=null->"send"
                    else->"empty"
                }
                AnimatedContent(action,modifier=Modifier.size(48.dp),contentAlignment=Alignment.Center,
                    transitionSpec={
                        (fadeIn(tween(180))+scaleIn(tween(280,easing=FastOutSlowInEasing),initialScale=.08f)) togetherWith
                            (fadeOut(tween(140))+scaleOut(tween(180,easing=FastOutSlowInEasing),targetScale=.08f)) using SizeTransform(clip=false)
                    },label="输入操作切换") {target->
                    when(target) {
                        "send"->GradientIconButton(onClick={focus.clearFocus();ime?.hide();vm.send()},enabled=true,modifier=Modifier.size(48.dp).semantics {contentDescription="发送"}) {GlyphIcon(Glyph.SEND,color=LocalContentColor.current)}
                        "stop"->IconAction(Glyph.CLOSE,"停止",onClick=vm::cancelRequest)
                        else->Spacer(Modifier.size(48.dp))
                    }
                }
            }
        }
    }
    if(cameraOpen)CameraSheet(onClose={cameraOpen=false},onPhoto={uri->cameraOpen=false;vm.attachPhoto(uri)})
}
