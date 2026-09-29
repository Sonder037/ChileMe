package me.chile.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import me.chile.app.domain.*

internal val activityColor=Color(0xFFFF9648)

@Composable fun DailyEnergyCards(meals: List<Meal>,exercises: List<Exercise>,profiles: List<Profile>,active: Boolean=true,overrides: Map<String,String> = emptyMap()) {
    var help by remember {mutableStateOf(false)}
    val now=rememberCurrentTime()
    val today=now.toLocalDate()
    val day=energyProgress(meals,exercises,profiles,today,today,overrides).days.single()
    val movement=day.active.takeIf {exercises.any {it.date==day.date}}
    val profile=profileForDay(profiles,day.date,overrides)
    val reference=dailyActivityReference(profile,now.toLocalTime())
    val activity=if(reference!=null || movement!=null)(reference?:0)+(movement?:0) else null
    val nutrition=nutritionDay(meals,profile,day.date)
    val parts=mealBreakdown(meals,day.date)
    val primary=MaterialTheme.colorScheme.primary
    BoxWithConstraints(Modifier.fillMaxWidth()) {
    val largeText=LocalDensity.current.fontScale>1.3f || maxWidth<328.dp
    Paper {
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text("今日能量",style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold)
            Spacer(Modifier.weight(1f));IconAction(Glyph.HELP,"能量图口径"){help=true}
        }
        MealEnergyRings(parts,movement,active,nutrition.parts,reference) {
            if(!largeText)BalanceNumber(day.balance,Modifier.padding(bottom=3.dp))
        }
        if(largeText)BalanceNumber(day.balance,Modifier.align(Alignment.CenterHorizontally))
        Row(Modifier.fillMaxWidth().padding(top=6.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            RingMetric("摄入",day.intake,primary,Modifier.weight(1f))
            RingMetric("活动",activity,activityColor,Modifier.weight(1f))
        }

    }
    }
    if(help)AlertDialog(
        onDismissRequest={help=false},
        containerColor=MaterialTheme.colorScheme.surface,
        title={Text("能量口径",style=MaterialTheme.typography.titleLarge)},
        text={Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(20.dp)) {
            listOf(
                "查看详情" to "长按圆环查看对应数据",
                "活动" to "截至当前的日常活动估算\n加上已记录的运动",
                "热量差" to "摄入减去全天消耗参考\n运动已计入消耗，不重复累加"
            ).forEach {(heading,body)->
                Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
                    Text(heading,style=MaterialTheme.typography.titleMedium,fontWeight=FontWeight.SemiBold,color=MaterialTheme.colorScheme.onSurface)
                    Text(body,fontSize=16.sp,lineHeight=25.sp,color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }},
        confirmButton={TextButton(onClick={help=false}){Text("知道了",fontSize=16.sp)}}
    )
}

@Composable private fun BalanceNumber(value: Int?,modifier: Modifier=Modifier) {
    Column(modifier,horizontalAlignment=Alignment.CenterHorizontally) {
        Text(value?.let {if(it>0)"+$it" else "$it"}?:"—",fontSize=28.sp,lineHeight=34.sp,fontFamily=RoundedNumbers,fontWeight=FontWeight.SemiBold,maxLines=1)
        Text("热量差 / kcal",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable private fun RingMetric(label: String,value: Int?,color: Color,modifier: Modifier) {
    Column(modifier.background(color.copy(alpha=.08f),RoundedCornerShape(18.dp)).padding(vertical=12.dp,horizontal=4.dp).semantics(mergeDescendants=true){},horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(5.dp)) {
            Canvas(Modifier.size(6.dp)){drawCircle(color)}
            Text(label,style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(value?.toString()?:"—",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
        Text("kcal",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
