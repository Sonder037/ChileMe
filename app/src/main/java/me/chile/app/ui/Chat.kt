package me.chile.app.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.foundation.interaction.DragInteraction
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.chile.app.AppViewModel
import me.chile.app.domain.*

@Composable internal fun Photo(vm: AppViewModel,name: String,modifier: Modifier,maxEdge: Int=800) {
    val bitmap by produceState<android.graphics.Bitmap?>(null,name,maxEdge) {value=withContext(Dispatchers.IO){vm.photos.preview(name,maxEdge)}}
    bitmap?.let {Image(it.asImageBitmap(),"食物照片",modifier.clip(RoundedCornerShape(16.dp)),contentScale=androidx.compose.ui.layout.ContentScale.Crop)}
}
@Composable internal fun ChatScreen(vm: AppViewModel,bottomSpace: androidx.compose.ui.unit.Dp=80.dp) {
    val focus=LocalFocusManager.current
    val today=rememberToday()
    val state=rememberLazyListState()
    var positioned by rememberSaveable {mutableStateOf(false)}
    var followReply by remember {mutableStateOf(true)}
    LaunchedEffect(vm.busy) {if(vm.busy)followReply=true}
    LaunchedEffect(state) {
        state.interactionSource.interactions.collect {if(it is DragInteraction.Start)followReply=false}
    }
    LaunchedEffect(state) {
        snapshotFlow {state.isScrollInProgress to state.canScrollForward}.collect {(scrolling,hasMore)->
            if(!scrolling && !hasMore)followReply=true
        }
    }
    var historySpace by remember {mutableStateOf(0.dp)}
    val density=androidx.compose.ui.platform.LocalDensity.current
    fun loadHistory() {
        if(vm.loadingOlder || !vm.data.hasOlderMessages)return
        // A short conversation has empty space below it. Retain that space while
        // prepending history, otherwise the list clamps the anchor to its bottom.
        val layout=state.layoutInfo
        val end=layout.visibleItemsInfo.lastOrNull()
        if(end!=null && !state.canScrollForward && !state.canScrollBackward)
            historySpace=with(density){(layout.viewportEndOffset-end.offset-end.size).coerceAtLeast(0).toDp()}
        val anchor=state.layoutInfo.visibleItemsInfo.firstOrNull {item->vm.data.messages.any {it.id==item.key}}
        vm.loadOlderMessages {
            anchor?.let {item->
                val index=vm.data.messages.indexOfFirst {it.id==item.key}
                if(index>=0)state.requestScrollToItem(index+1,-item.offset)
            }
        }
    }
    LaunchedEffect(vm.loading,vm.data.messages.lastOrNull()?.id,vm.busy){
        if(!vm.loading && vm.data.messages.isNotEmpty()) {
            historySpace=0.dp
            if(!positioned){state.scrollToItem(vm.data.messages.size+1);positioned=true}
            else if(followReply)state.animateScrollToItem(vm.data.messages.size+1)
        }
    }
    LaunchedEffect(state,positioned) {
        if(positioned)snapshotFlow {state.isScrollInProgress && state.firstVisibleItemIndex<=1}
            .distinctUntilChanged().filter {it}.collect {loadHistory()}
    }
    LaunchedEffect(state,vm.busy) {
        if(vm.busy)snapshotFlow {
            val layout=state.layoutInfo
            layout.totalItemsCount to layout.visibleItemsInfo.lastOrNull()?.let {Triple(it.index,it.size,layout.viewportEndOffset-layout.afterContentPadding)}
        }.collect {
            if(followReply && vm.streamingText.isNotEmpty() && !state.isScrollInProgress) {
                val layout=state.layoutInfo
                val last=layout.visibleItemsInfo.lastOrNull()
                if(last!=null && last.index>=layout.totalItemsCount-2)
                    state.scrollToItem(layout.totalItemsCount-1,(last.size-layout.viewportEndOffset+layout.afterContentPadding).coerceAtLeast(0))
            }
        }
    }
    // Reserve a real viewport above the floating composer. Content padding alone
    // only protects the final item; earlier messages otherwise scroll behind it.
    LazyColumn(modifier=Modifier.fillMaxSize().padding(bottom=bottomSpace).clipToBounds(),
        state=state,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(top=16.dp,bottom=12.dp+historySpace)) {
        item(key="history-start") {
            if(vm.data.hasOlderMessages)TextButton(onClick=::loadHistory,enabled=!vm.loadingOlder,modifier=Modifier.fillMaxWidth()) {Text(if(vm.loadingOlder)"正在加载…" else "更早消息")}
            else if(vm.data.messages.isEmpty())ChatWelcome(vm::updateDraft)
        }
        items(vm.data.messages,key={it.id}) {m->Column(Modifier.fillMaxWidth().animateItem(fadeInSpec=tween(220),placementSpec=spring(dampingRatio=1f,stiffness=500f),fadeOutSpec=tween(120)).padding(start=if(m.role=="user")40.dp else 0.dp,end=if(m.role=="user")0.dp else 16.dp),horizontalAlignment=if(m.role=="user")Alignment.End else Alignment.Start) {
            Card(Modifier,shape=if(m.role=="user")RoundedCornerShape(24.dp,24.dp,8.dp,24.dp) else RoundedCornerShape(8.dp,24.dp,24.dp,24.dp),border=paperEdge(0.15625.dp),elevation=CardDefaults.cardElevation(defaultElevation=0.dp),colors=CardDefaults.cardColors(containerColor=if(m.role=="user")MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
                Column(Modifier.padding(horizontal=17.dp,vertical=13.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                    m.photo?.let {Photo(vm,it,Modifier.fillMaxWidth().height(200.dp))}
                    if(m.text.isNotBlank()) {if(m.role=="assistant")MarkdownText(assistantDisplayText(m.text)) else Text(m.text)}
                    if(m.role=="assistant" && m.options.isNotEmpty())Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                        m.options.forEach {option->
                            FilledTonalButton(onClick={focus.clearFocus();vm.answerChoice(m,option)},
                                enabled=!vm.busy && !vm.saving && vm.data.messages.lastOrNull()?.id==m.id,
                                shape=RoundedCornerShape(16.dp),modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {Text(option)}
                        }
                    }
                    vm.data.changes[m.id]?.let {change->
                        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                        Text(change.summary,style=MaterialTheme.typography.bodyMedium)
                        val status=vm.data.changeStates[m.id]?:"pending"
                        if(status=="pending")Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                            TextButton(onClick={vm.resolveChange(m.id,false)},enabled=!vm.busy&&!vm.saving){Text("取消")}
                            PrimaryButton(onClick={vm.resolveChange(m.id,true)},enabled=!vm.busy&&!vm.saving){Text("确认修改")}
                        }else Text(if(status=="confirmed")"已修改" else "已取消",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
                    }
                    m.exerciseId?.let {ExerciseChatCard(vm,it)}
                    m.mealId?.let {id->vm.data.cards[id]?.let {MealChatCard(vm,it,vm.data.cardStates[id]?:"draft")}}
                }
            }
            chatTimeLabel(m.createdAt,today)?.let {time->Text(time,modifier=Modifier.padding(horizontal=6.dp,vertical=4.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            if(m.photo!=null && vm.retryMessage?.id==m.id && !vm.busy)Row {if(vm.retryMessage?.id==m.id)TextButton(onClick={vm.send(true)}){Text("重试")};TextButton(onClick={vm.manualCard(m)},enabled=!vm.saving){Text("手动填写")}}
        }}
        // Keep the same lazy-list identity when streamed text becomes a saved message.
        item(key=vm.streamingMessageId?.takeUnless {id->vm.data.messages.any {it.id==id}}?:"chat-tail") {if(vm.retryMessage?.photo==null && vm.retryMessage!=null && !vm.busy)TextButton(onClick={vm.send(true)}){Text("重试上一条")};if(vm.busy || vm.streamingText.isNotBlank()) {
            if(vm.streamingText.isBlank())TypingIndicator()
            else Surface(shape=RoundedCornerShape(8.dp,24.dp,24.dp,24.dp),color=MaterialTheme.colorScheme.surface,border=paperEdge(0.15625.dp),modifier=Modifier.padding(end=16.dp)) {
                Column(Modifier.padding(horizontal=17.dp,vertical=13.dp)){MarkdownText(assistantDisplayText(vm.streamingText));if(!vm.busy)Text("回复已中断",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            }
        }}
    }
}
@Composable private fun MealChatCard(vm: AppViewModel,meal: Meal,status: String) {
    var editing by rememberSaveable(meal.id) {mutableStateOf(false)}
    val largeText=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    Column(Modifier.animateContentSize(tween(240)),verticalArrangement=Arrangement.spacedBy(10.dp)) {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {Text(meal.title,style=MaterialTheme.typography.titleMedium);Text("${meal.date} ${meal.time.orEmpty()}",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text("${meal.kcal} kcal",style=MaterialTheme.typography.titleLarge,fontWeight=androidx.compose.ui.text.font.FontWeight.SemiBold,maxLines=1,softWrap=false)
        }
        if(meal.replacesId!=null && status=="draft") {
            vm.data.meals.firstOrNull{it.id==meal.replacesId}?.let {old->Text("原记录：${old.date} ${old.title} · ${old.kcal} kcal\n${old.items.joinToString("、"){it.name}}",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
            Text("确认后更新原记录",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
        }
        if(editing && status=="draft") {
            SoftField(meal.title,{vm.updateCard(meal.copy(title=it))},"餐次")
            SoftField(meal.date,{vm.updateCard(meal.copy(date=it))},"日期 · YYYY-MM-DD")
            SoftField(meal.time.orEmpty(),{vm.updateCard(meal.copy(time=it.ifBlank {null}))},"时间 · HH:mm")
            FoodGrid(meal.items,enabled=!vm.saving&&!vm.busy){vm.updateCard(meal.copy(items=it))}
        }else meal.items.forEach {food->
            val portion="${if(food.amount!=null)food.portion()+" · " else ""}${food.kcal} kcal"
            if(largeText)Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(food.name)
                if(food.category!="其他")Text(food.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                Text(portion,style=MaterialTheme.typography.bodyMedium)
            }else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                Column(Modifier.weight(1f)){Text(food.name);if(food.category!="其他")Text(food.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
                Text(portion,style=MaterialTheme.typography.bodyMedium)
            }
        }
        if(status=="draft")Row(verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={editing=!editing}){Text(if(editing)"收起" else "编辑")}
            Spacer(Modifier.weight(1f));PrimaryButton(onClick={vm.confirmCard(meal)},enabled=meal.valid()&&!vm.saving){Text(if(meal.replacesId!=null)"确认修改" else "确认记录")}
        }else Text(if(status=="superseded")"已由实际摄入记录替代" else if(status=="deleted")"记录已删除" else "已记录 · ${meal.date}",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.primary)
    }
}
