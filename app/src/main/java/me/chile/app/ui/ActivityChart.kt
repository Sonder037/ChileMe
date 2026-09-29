package me.chile.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.chile.app.domain.Exercise
import java.time.LocalDate

@Composable fun RecentActivityCard(exercises: List<Exercise>,active: Boolean=true) {
    val reveal=remember {Animatable(0f)}
    LaunchedEffect(active) {if(active)reveal.animateTo(1f,tween(420)) else reveal.snapTo(0f)}
    val today=rememberToday()
    val dates=(6 downTo 0).map {today.minusDays(it.toLong()).toString()}
    val recent=exercises.filter {it.date in dates}.sortedWith(compareByDescending<Exercise> {it.date}.thenByDescending {it.time})
    val daily=recent.groupBy {it.date}
    val maximum=daily.values.maxOfOrNull {rows->rows.sumOf {it.activeKcal}}?.coerceAtLeast(1)?:1
    var all by remember {mutableStateOf(false)}
    Paper {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("近 7 天运动",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f))
            if(recent.size>3)TextButton(onClick={all=true}){Text("全部 ${recent.size} 次")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(24.dp)) {
            ActivityTotal(if(recent.isEmpty())"—" else recent.sumOf {it.minutes}.toString(),"分钟")
            ActivityTotal(if(recent.isEmpty())"—" else recent.sumOf {it.activeKcal}.toString(),"kcal")
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            dates.forEach {date->
                val kcal=daily[date]?.sumOf {it.activeKcal}
                Column(Modifier.weight(1f).semantics(mergeDescendants=true){contentDescription="$date，${kcal?.let {"净活动 $it 千卡"}?:"无运动记录"}"},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    BasicText(kcal?.toString()?:"—",modifier=Modifier.fillMaxWidth(),maxLines=1,overflow=TextOverflow.Ellipsis,
                        autoSize=TextAutoSize.StepBased(minFontSize=7.sp,maxFontSize=11.sp,stepSize=.5.sp),
                        style=MaterialTheme.typography.labelSmall.copy(lineHeight=14.sp,textAlign=TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant))
                    Box(Modifier.width(22.dp).height(64.dp).clip(RoundedCornerShape(8.dp)).background(activityColor.copy(alpha=.09f)),contentAlignment=Alignment.BottomCenter) {
                        if(kcal!=null && kcal>0)Box(Modifier.fillMaxWidth().fillMaxHeight(kcal.toFloat()/maximum*reveal.value).clip(RoundedCornerShape(8.dp)).background(activityColor))
                    }
                    Text(if(date==today.toString())"今" else LocalDate.parse(date).dayOfMonth.toString(),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(recent.isEmpty())Text("运动后在聊天里记一句。",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        else recent.take(3).forEach {ActivityRecord(it)}
    }
    if(all)AlertDialog(onDismissRequest={all=false},title={Text("近 7 天运动")},text={LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp)) {items(recent,key={it.id}){ActivityRecord(it)}}},confirmButton={TextButton(onClick={all=false}){Text("完成")}})
}

@Composable private fun ActivityTotal(value: String,unit: String) {
    Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
        Text(value,style=MaterialTheme.typography.headlineSmall,fontWeight=FontWeight.SemiBold)
        Text(unit,Modifier.padding(bottom=3.dp),style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun ActivityRecord(exercise: Exercise) {
    Row(Modifier.fillMaxWidth().padding(top=4.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Column(Modifier.weight(1f)) {
            Text(exercise.name,style=MaterialTheme.typography.bodyMedium,fontWeight=FontWeight.Medium)
            Text("${exercise.date.substring(5)} ${exercise.time} · ${exercise.minutes} 分钟",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text("${exercise.activeKcal} kcal",style=MaterialTheme.typography.bodyMedium)
    }
}
