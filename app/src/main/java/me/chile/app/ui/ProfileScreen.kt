package me.chile.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.chile.app.AppViewModel
import me.chile.app.domain.*
import kotlin.math.roundToInt

@Composable fun ProfileScreen(vm: AppViewModel,onDone: ()->Unit) {
    if(vm.loading)return
    if(!vm.data.onboarded){ProfileOnboarding(vm,onDone);return}
    val profile=vm.data.profiles.firstOrNull()
    var sex by rememberSaveable {mutableStateOf(profile?.sex?:"unspecified")}
    var age by rememberSaveable {mutableStateOf(profile?.age?.toString()?:"")}
    var height by rememberSaveable {mutableStateOf(profile?.height?.roundToInt()?.toString()?:"")}
    var weight by rememberSaveable {mutableStateOf(profile?.weight?.roundToInt()?.toString()?:"")}
    var activityDialog by rememberSaveable {mutableStateOf(false)}
    var activity by rememberSaveable {mutableStateOf(profile?.activity)}
    var day by rememberSaveable {mutableStateOf(java.time.LocalDate.now().toString())}
    var dayActivity by rememberSaveable {mutableStateOf<String?>(null)}
    var editDay by rememberSaveable {mutableStateOf(false)}
    var editing by rememberSaveable {mutableStateOf<String?>(null)}
    val p=Profile(sex,age.toIntOrNull()?:0,height.toDoubleOrNull()?:0.0,weight.toDoubleOrNull()?:0.0,activity=activity)
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Paper {
            Text("身体资料",style=MaterialTheme.typography.titleLarge)
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                listOf("male" to "男","female" to "女").forEach {(id,label)->
                    val color by animateColorAsState(if(sex==id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,label="性别选择")
                    FilterChip(selected=sex==id,onClick={sex=id},label={Box(Modifier.fillMaxWidth(),contentAlignment=Alignment.Center){Text(label)}},
                        modifier=Modifier.weight(1f).heightIn(min=52.dp),shape=RoundedCornerShape(18.dp),border=null,
                        colors=FilterChipDefaults.filterChipColors(containerColor=color,selectedContainerColor=color))
                }
            }
        }}
        item {ProfileValue("年龄",age,"岁"){editing="年龄"}}
        item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            ProfileValue("身高",height,"cm",Modifier.weight(1f)){editing="身高"}
            ProfileValue("体重",weight,"kg",Modifier.weight(1f)){editing="体重"}
        }}
        item {Paper {TextButton(onClick={activityDialog=true}){Text("默认活动 · ${activityLevels.firstOrNull {it.id==activity}?.label?:"未选择"}")}}}
        item {Paper {
            Text("静息估算",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text("${p.resting()?.toString()?:"—"} kcal / 天",style=MaterialTheme.typography.titleLarge)
            p.totalEnergy()?.let {Text("全天约 $it kcal",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)}
        }}
        item {PrimaryButton(onClick={vm.saveProfile(p,onDone)},enabled=p.valid()&&(profile!=null||sex!="unspecified")&&!vm.saving,modifier=Modifier.fillMaxWidth()) {
            Text(if(vm.data.onboarded)"保存身体数据" else "下一步")
        }}
        item {Paper {
            TextButton(onClick={editDay=!editDay}){Text("单日活动")}
            if(editDay) {
                SoftField(day,{day=it;dayActivity=null},"日期 · YYYY-MM-DD")
                val effective=profileForDay(vm.data.profiles,day,vm.data.activityOverrides)?.activity
                ActivityOptions(dayActivity?:effective){dayActivity=it}
                Row {
                    TextButton(onClick={vm.saveDailyActivity(day,null);dayActivity=null},enabled=!vm.busy&&!vm.saving){Text("恢复默认")}
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick={vm.saveDailyActivity(day,dayActivity)},enabled=dayActivity!=null&&!vm.busy&&!vm.saving){Text("保存当天")}
                }
            }
        }}
        if(vm.data.onboarded&&vm.data.profiles.isNotEmpty())item {Paper {
            Text("身体记录",style=MaterialTheme.typography.titleMedium)
            vm.data.profiles.take(10).forEach {Text("${it.date} · ${it.weight.roundToInt()} kg · ${it.height.roundToInt()} cm",style=MaterialTheme.typography.bodyMedium)}
        }}
    }
    if(activityDialog)AlertDialog(onDismissRequest={activityDialog=false},title={Text("默认活动")},text={ActivityOptions(activity){activity=it}},confirmButton={TextButton(onClick={activityDialog=false}){Text("完成")}})
    editing?.let {field->
        val value=when(field){"年龄"->age;"身高"->height;else->weight}
        ProfileValueDialog(field,value,onDismiss={editing=null}) {newValue->
            when(field){"年龄"->age=newValue;"身高"->height=newValue;else->weight=newValue}
            editing=null
        }
    }
}

@Composable private fun ProfileValue(label: String,value: String,unit: String,modifier: Modifier=Modifier,onClick: ()->Unit) {
    Card(onClick=onClick,modifier=modifier.fillMaxWidth().semantics {contentDescription="调整$label"},
        shape=RoundedCornerShape(24.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=paperEdge()) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
            Text(label,style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            Text(if(value.isBlank())"选择" else value.removeSuffix(".0"),style=MaterialTheme.typography.headlineSmall,color=MaterialTheme.colorScheme.primary)
            Text(unit,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable internal fun ProfileValueDialog(label: String,initial: String,onDismiss: ()->Unit,onConfirm: (String)->Unit) {
    val range=when(label){"年龄"->1..120;"身高"->80..250;else->15..350}
    val unit=when(label){"年龄"->"岁";"身高"->"cm";else->"kg"}
    // Existing decimals are rounded for editing; dismissing never changes stored records.
    val start=initial.toDoubleOrNull()?.takeIf {it.isFinite()}?.roundToInt()?.coerceIn(range)
        ?:when(label){"年龄"->25;"身高"->170;else->60}
    var value by rememberSaveable {mutableStateOf(start.toString())}
    var typing by rememberSaveable {mutableStateOf(false)}
    var moving by remember {mutableStateOf(false)}
    val parsed=value.toIntOrNull()
    val valid=parsed!=null&&parsed in range
    val wheelValue=(parsed?:start).coerceIn(range)
    AlertDialog(onDismissRequest=onDismiss,containerColor=MaterialTheme.colorScheme.surface,title={Text(label)},text={
        Column(Modifier.verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
            Text("$value $unit",style=MaterialTheme.typography.headlineLarge,color=MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(12.dp))
            if(typing)SoftField(value,{value=it},"$label · $unit",keyboard=KeyboardType.Number)
            else ProfileWheel("${label}整数",range,wheelValue,Modifier.fillMaxWidth(),{moving=it}) {value=it.toString()}
            TextButton(onClick={
                if(typing)value=wheelValue.toString()
                typing=!typing;moving=false
            },enabled=!moving) {Text(if(typing)"滚动选择" else "键盘输入")}
            if(!valid)Text("请输入 ${range.first}–${range.last} $unit 的整数",color=MaterialTheme.colorScheme.error)
        }
    },confirmButton={TextButton(onClick={onConfirm(parsed!!.toString())},enabled=valid&&!moving){Text("完成")}},
        dismissButton={TextButton(onClick=onDismiss){Text("取消")}})
}

@Composable internal fun ActivityOptions(selected: String?,onSelect: (String)->Unit) {
    Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        activityLevels.forEach {level->
            val color by animateColorAsState(if(selected==level.id)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,label="活动选择")
            Surface(selected=selected==level.id,onClick={onSelect(level.id)},shape=RoundedCornerShape(18.dp),color=color,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text("${level.label} · ${level.factor}×",style=MaterialTheme.typography.titleSmall)
                    Text(level.detail,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
