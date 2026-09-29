package me.chile.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.chile.app.AppViewModel
import me.chile.app.domain.*

@Composable fun RecordsScreen(vm: AppViewModel,active: Boolean=true,edit: (Meal)->Unit) {
    val today=rememberToday()
    val feedback=selectionFeedback()
    var deleting by remember {mutableStateOf<Meal?>(null)}
    val list=rememberLazyListState()
    LaunchedEffect(list) {
        snapshotFlow {list.isScrollInProgress && list.layoutInfo.visibleItemsInfo.any {it.key=="older-meals"}}
            .distinctUntilChanged().filter {it}.collect {vm.loadOlderMeals()}
    }
    LazyColumn(state=list,verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {DailyEnergyCards(vm.data.meals,vm.data.exercises,vm.data.profiles,active,vm.data.activityOverrides)}
        item {RecentActivityCard(vm.data.exercises,active)}
        item {Row(Modifier.fillMaxWidth().padding(top=6.dp),verticalAlignment=Alignment.CenterVertically) {Text("吃过的",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.weight(1f));Text("${vm.data.mealCount} 条",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
        if(vm.data.mealRecords.isEmpty())item {Paper {Text(if(vm.data.mealCount>0)"近三天还没有记录" else "还没有饮食记录");if(vm.data.mealCount==0)Text("在聊天中说说吃了什么，或者拍张照片。",style=MaterialTheme.typography.bodySmall)}}
        items(vm.data.mealRecords,key={it.id}) {m->
            val interaction=remember(m.id){androidx.compose.foundation.interaction.MutableInteractionSource()}
            Card(onClick={feedback();edit(m)},interactionSource=interaction,modifier=Modifier.pressMotion(interaction,.985f),shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
                Column(Modifier.fillMaxWidth().padding(start=18.dp,top=14.dp,end=8.dp,bottom=18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment=Alignment.CenterVertically) {
                        Text(if(m.date==today.toString()) "今天 ${m.time.orEmpty()}" else "${m.date} ${m.time.orEmpty()}",
                            Modifier.background(MaterialTheme.colorScheme.background,RoundedCornerShape(8.dp)).padding(horizontal=8.dp,vertical=4.dp),
                            style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.weight(1f));IconAction(Glyph.DELETE,"删除${m.title}"){deleting=m}
                    }
                    Row(Modifier.padding(end=10.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)) {
                            Text(m.title,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis)
                            Text(m.items.joinToString("、"){it.name},style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2,overflow=TextOverflow.Ellipsis)
                        }
                        Column(horizontalAlignment=Alignment.End) {
                            Text("${m.kcal}",style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
                            Text("kcal",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
        if(vm.data.mealRecords.size<vm.data.mealCount)item(key="older-meals") {
            TextButton(onClick={vm.loadOlderMeals()},enabled=!vm.loadingOlderMeals,modifier=Modifier.fillMaxWidth()) {
                Text(if(vm.loadingOlderMeals)"加载中" else "加载更早记录")
            }
        }
    }
    deleting?.let {m->AlertDialog(onDismissRequest={deleting=null},title={Text("删除这条餐次？")},text={Text("对应的自动记忆也会移除。")},confirmButton={TextButton(onClick={vm.deleteMeal(m);deleting=null}){Text("删除")}},dismissButton={TextButton(onClick={deleting=null}){Text("保留")}})}
}
