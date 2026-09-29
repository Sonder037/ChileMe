package me.chile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.chile.app.AppViewModel
import me.chile.app.domain.Exercise

@Composable fun ExerciseChatCard(vm: AppViewModel,id: String) {
    val exercise=vm.data.exercises.firstOrNull {it.id==id}
    var editing by remember {mutableStateOf(false)}
    var deleting by remember {mutableStateOf(false)}
    if(exercise==null){Text("运动记录已删除",style=MaterialTheme.typography.labelMedium);return}
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            Text(exercise.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
            Text("${exercise.activeKcal} kcal",style=MaterialTheme.typography.titleMedium,maxLines=1,softWrap=false)
        }
        Text("${exercise.date} · ${exercise.minutes} 分钟",style=MaterialTheme.typography.bodySmall)
        Text(exercise.source,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row {TextButton(onClick={editing=true},enabled=!vm.busy){Text("编辑")};TextButton(onClick={deleting=true},enabled=!vm.busy){Text("删除")}}
    }
    if(editing)ExerciseEditDialog(exercise,onDismiss={editing=false}) {vm.saveExercise(it);editing=false}
    if(deleting)AlertDialog(onDismissRequest={deleting=false},title={Text("删除运动记录？")},text={Text("这次活动消耗会从统计中移除。")},confirmButton={TextButton(onClick={vm.deleteExercise(exercise);deleting=false}){Text("删除")}},dismissButton={TextButton(onClick={deleting=false}){Text("保留")}})
}

@Composable private fun ExerciseEditDialog(exercise: Exercise,onDismiss: ()->Unit,onSave: (Exercise)->Unit) {
    var name by rememberSaveable {mutableStateOf(exercise.name)}
    var minutes by rememberSaveable {mutableStateOf(exercise.minutes.toString())}
    var kcal by rememberSaveable {mutableStateOf(exercise.activeKcal.toString())}
    var date by rememberSaveable {mutableStateOf(exercise.date)}
    val edited=exercise.copy(name=name,date=date,minutes=minutes.toIntOrNull()?:0,activeKcal=kcal.toIntOrNull()?:-1,source="手动校正 · 净活动消耗")
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),contentAlignment=Alignment.Center) {
            Surface(Modifier.widthIn(max=440.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Text("编辑运动",style=MaterialTheme.typography.titleLarge)
                    Column(Modifier.weight(1f,fill=false).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(10.dp)) {
                        SoftField(name,{name=it.take(100)},"运动名称")
                        SoftField(date,{date=it},"日期 · YYYY-MM-DD")
                        SoftField(minutes,{minutes=it},"时长 · 分钟",keyboard=KeyboardType.Number)
                        SoftField(kcal,{kcal=it},"净活动消耗 · kcal",keyboard=KeyboardType.Number)
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End) {
                        TextButton(onClick=onDismiss){Text("取消")}
                        Spacer(Modifier.width(12.dp))
                        PrimaryButton(onClick={onSave(edited)},enabled=edited.valid() && runCatching {java.time.LocalDate.parse(date)<=java.time.LocalDate.now()}.getOrDefault(false)){Text("保存")}
                    }
                }
            }
        }
    }
}
