package me.chile.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.chile.app.AppViewModel
import me.chile.app.domain.*
import kotlin.math.roundToInt

@Composable internal fun ProfileOnboarding(vm: AppViewModel,onDone: ()->Unit) {
    val existing=vm.data.profiles.firstOrNull()
    var sex by rememberSaveable {mutableStateOf(existing?.sex?:"unspecified")}
    var age by rememberSaveable {mutableIntStateOf(existing?.age?:25)}
    var height by rememberSaveable {mutableIntStateOf(existing?.height?.roundToInt()?:170)}
    var weight by rememberSaveable {mutableIntStateOf(existing?.weight?.roundToInt()?:60)}
    var activity by rememberSaveable {mutableStateOf(existing?.activity)}
    var step by rememberSaveable {mutableIntStateOf(0)}
    var reached by rememberSaveable {mutableIntStateOf(if(existing!=null)4 else 0)}
    val entrance=remember {Animatable(1f)}
    LaunchedEffect(step){entrance.snapTo(0f);entrance.animateTo(1f,tween(220))}
    BackHandler(step>0 && !vm.saving){step--}
    val labels=listOf("性别","年龄","身高","体重","活动")
    val values=listOf(if(sex=="male")"男" else if(sex=="female")"女" else "待选","$age 岁","$height cm","$weight kg",activityLevels.firstOrNull {it.id==activity}?.label?:"待选")
    val profile=Profile(sex,age,height.toDouble(),weight.toDouble(),activity=activity)
    Column(Modifier.fillMaxSize(),verticalArrangement=Arrangement.spacedBy(16.dp)) {
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            Spacer(Modifier.height(4.dp))
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                Text("先认识一下你",style=MaterialTheme.typography.headlineLarge)
                Text("用来估算每天的热量需要",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed {index,label->
                    val color by animateColorAsState(if(step==index)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,label="步骤卡片")
                    Surface(onClick={step=index},enabled=index<=reached&&!vm.saving,
                        modifier=Modifier.weight(1f),shape=RoundedCornerShape(18.dp),color=color) {
                        Column(Modifier.padding(vertical=14.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Text(label,style=MaterialTheme.typography.labelLarge,color=if(step==index)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(if(index<reached || existing!=null || (index==4 && activity!=null))values[index] else "0${index+1}",style=MaterialTheme.typography.labelMedium)
                        }
                    }
                }
            }
            Surface(shape=RoundedCornerShape(32.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
                Column(Modifier.padding(24.dp).graphicsLayer {alpha=entrance.value;translationY=(1f-entrance.value)*12.dp.toPx()},
                    horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(20.dp)) {
                    Text(labels[step],style=MaterialTheme.typography.titleMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    if(step==0) {
                        Text("选择你的性别",style=MaterialTheme.typography.headlineSmall)
                        Row(horizontalArrangement=Arrangement.spacedBy(12.dp),modifier=Modifier.padding(vertical=24.dp)) {
                            listOf("male" to "男","female" to "女").forEach {(id,label)->
                                val selected=sex==id
                                val color by animateColorAsState(if(selected)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.background,label="性别卡片")
                                Surface(onClick={sex=id;step=1;reached=maxOf(reached,1)},enabled=!vm.saving,selected=selected,shape=RoundedCornerShape(24.dp),color=color,modifier=Modifier.weight(1f)) {
                                    Box(Modifier.height(120.dp),contentAlignment=Alignment.Center) {
                                        Text(label,fontSize=32.sp,fontWeight=FontWeight.Medium,color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                                    }
                                }
                            }
                        }
                    }else if(step==4) {
                        ActivityOptions(activity) {activity=it}
                        profile.totalEnergy()?.let {Text("全天约 $it kcal",style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.primary)}
                    }else {
                        Text(values[step],fontFamily=RoundedNumbers,fontWeight=FontWeight.Bold,fontSize=44.sp,color=MaterialTheme.colorScheme.primary)
                        key(step) {
                            val wheelStep=step
                            ProfileWheel("${labels[step]}整数",when(step){1->1..120;2->80..250;else->15..350},
                                when(step){1->age;2->height;else->weight},Modifier.fillMaxWidth(),{}) {
                                when(wheelStep){1->age=it;2->height=it;else->weight=it}
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
        }
        if(step>0)Row(Modifier.fillMaxWidth().padding(bottom=16.dp),horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically) {
            TextButton(onClick={step--},enabled=!vm.saving,modifier=Modifier.heightIn(min=52.dp)){Text("上一步")}
            PrimaryButton(onClick={
                if(step<4){step++;reached=maxOf(reached,step)}else vm.saveProfile(profile,onDone)
            },enabled=!vm.saving&&sex!="unspecified"&&profile.valid()&&(step!=4||activity!=null),modifier=Modifier.weight(1f).heightIn(min=56.dp)) {
                Text(if(vm.saving)"保存中…" else if(step==4)"连接 AI" else "继续")
            }
        }
    }
}
