package me.chile.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.animation.*
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.ui.semantics.Role
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import me.chile.app.domain.*
import java.time.LocalDate
import java.time.YearMonth

@Composable fun TrendsScreen(meals: List<Meal>, initiallyMonthly: Boolean=false, exercises: List<Exercise> = emptyList(), profiles: List<Profile> = emptyList(), overrides: Map<String,String> = emptyMap(), today: LocalDate=rememberToday()) {
    var monthly by rememberSaveable {mutableStateOf(initiallyMonthly)}
    var weekAnchor by rememberSaveable {mutableStateOf(today.toString())}
    var selected by rememberSaveable {mutableStateOf(today.toString())}
    val currentMonth=YearMonth.from(today)
    val firstMonth=remember(meals,currentMonth){minOf(meals.minOfOrNull {YearMonth.from(LocalDate.parse(it.date))}?:currentMonth,currentMonth.minusMonths(12))}
    val count=java.time.temporal.ChronoUnit.MONTHS.between(firstMonth,currentMonth).toInt()+1
    val pager=rememberPagerState(initialPage=count-1,pageCount={count})
    var previousToday by rememberSaveable {mutableStateOf(today.toString())}
    LaunchedEffect(today) {
        if(previousToday!=today.toString()) {
            if(selected==previousToday && !pager.isScrollInProgress) {
                if(weekAnchor==previousToday)weekAnchor=today.toString()
                selected=today.toString()
                pager.scrollToPage(count-1)
            }
            previousToday=today.toString()
        }
    }
    val scope=rememberCoroutineScope()
    fun moveMonth(offset: Int) {
        val page=(pager.currentPage+offset).coerceIn(0,count-1)
        val month=firstMonth.plusMonths(page.toLong())
        selected=if(month==currentMonth)today.toString() else month.atDay(1).toString()
        scope.launch {pager.animateScrollToPage(page)}
    }
    val feedback=selectionFeedback()
    val largeText=androidx.compose.ui.platform.LocalDensity.current.fontScale>1.3f
    LaunchedEffect(pager.settledPage) {
        if(monthly) {
            val visible=firstMonth.plusMonths(pager.currentPage.toLong())
            // Backfilled records can shift page indices without changing the visible month.
            if(YearMonth.from(LocalDate.parse(selected))!=visible)
                selected=if(visible==currentMonth)today.toString() else visible.atDay(1).toString()
        }
    }
    val daily=remember(meals,exercises,profiles,overrides){
        val activity=exercises.groupBy {it.date}
        meals.groupBy {it.date}.mapValues {(date,records)->
            val p=profileForDay(profiles,date,overrides)
            EnergyDay(date,records.sumOf {it.kcal},p?.resting(),activity[date]?.sumOf {it.activeKcal}?:0,p?.totalEnergy()).balance
        }
    }
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item(key="period") {PeriodSwitch(monthly) {next->
            if(next!=monthly) {
                if(next) {
                    val month=firstMonth.plusMonths(pager.currentPage.toLong())
                    selected=if(month==currentMonth)today.toString() else month.atDay(1).toString()
                } else selected=weekAnchor
                monthly=next
            }
        }}
        item(key="chart") {
            Paper {
                AnimatedContent(targetState=monthly,transitionSpec={
                    fadeIn(tween(160,delayMillis=80)) togetherWith fadeOut(tween(100))
                },label="时间视图") {showMonth->
                    Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
                        if(showMonth) {
                            val visible=firstMonth.plusMonths(pager.currentPage.toLong())
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
                                IconAction(Glyph.BACK,"上个月",pager.currentPage>0){moveMonth(-1)}
                                Text("${visible.year} 年 ${visible.monthValue} 月",modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.titleMedium)
                                IconAction(Glyph.FORWARD,"下个月",pager.currentPage<count-1){moveMonth(1)}
                            }
                            Row {listOf("一","二","三","四","五","六","日").forEach{Text(it,Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.labelSmall)}}
                            BoxWithConstraints(Modifier.fillMaxWidth()) {
                            val density=androidx.compose.ui.platform.LocalDensity.current
                            // Match Row's pixel-rounded gaps and largest square cell exactly.
                            val cellPixels=with(density){(constraints.maxWidth-4.dp.roundToPx()*6+6)/7}
                            val gapPixels=with(density){5.dp.roundToPx()}
                            fun rows(page: Int): Int {
                                val month=firstMonth.plusMonths(page.toLong())
                                return (month.atDay(1).dayOfWeek.value-1+month.lengthOfMonth()+6)/7
                            }
                            // Follow the same scroll animation/finger position, without a second lagging animation.
                            val position=(pager.currentPage+pager.currentPageOffsetFraction).coerceIn(0f,(count-1).toFloat())
                            val from=position.toInt();val to=(from+1).coerceAtMost(count-1)
                            val visibleRows=rows(from)+(rows(to)-rows(from))*(position-from)
                            val gridHeight=with(density){(visibleRows*cellPixels+(visibleRows-1)*gapPixels).toDp()}
                            HorizontalPager(pager,modifier=Modifier.fillMaxWidth().height(gridHeight),verticalAlignment=Alignment.Top,
                                key={page->firstMonth.plusMonths(page.toLong()).toString()}) {page->
                                val month=firstMonth.plusMonths(page.toLong())
                                val offset=month.atDay(1).dayOfWeek.value-1
                                Column(Modifier.wrapContentHeight(Alignment.Top,unbounded=true),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                                    repeat(rows(page)){row->Row(horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                        repeat(7){column->val day=row*7+column-offset+1
                                            if(day !in 1..month.lengthOfMonth())Spacer(Modifier.weight(1f).aspectRatio(1f))
                                            else {val date=month.atDay(day);val total=daily[date.toString()]
                                                Surface(modifier=Modifier.weight(1f).aspectRatio(1f).semantics {contentDescription="${date}，热量差 ${total?.let {"${signedKcal(it)} 千卡"}?:"暂无数据"}"}.clickable(enabled=date<=today){if(selected!=date.toString())feedback();selected=date.toString()},shape=RoundedCornerShape(10.dp),
                                                    border=if(selected==date.toString())BorderStroke(1.5.dp,balanceColor(total))else null,
                                                    color=if(total!=null)balanceColor(total).copy(alpha=.14f) else MaterialTheme.colorScheme.background,
                                                    contentColor=MaterialTheme.colorScheme.onSurface) {
                                                    Column(Modifier.padding(horizontal=2.dp,vertical=3.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(2.dp,Alignment.CenterVertically)) {
                                                        Text("$day",style=MaterialTheme.typography.labelMedium.copy(fontSize=12.sp,lineHeight=14.sp,platformStyle=androidx.compose.ui.text.PlatformTextStyle(includeFontPadding=false)),maxLines=1)
                                                        if(!largeText)Text(signedKcal(total),style=MaterialTheme.typography.labelSmall.copy(fontSize=10.sp,lineHeight=12.sp,platformStyle=androidx.compose.ui.text.PlatformTextStyle(includeFontPadding=false)),maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                                    }
                                                }
                                            }
                                        }
                                    }}
                                }
                            }
                            }
                        } else {
                            val stats=trend(meals,Period.WEEK,LocalDate.parse(weekAnchor))
                            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){
                                IconAction(Glyph.BACK,"上一周"){weekAnchor=LocalDate.parse(weekAnchor).minusWeeks(1).toString();selected=weekAnchor}
                                Text("${stats.from.monthValue}/${stats.from.dayOfMonth} — ${stats.to.monthValue}/${stats.to.dayOfMonth}",modifier=Modifier.weight(1f),textAlign=androidx.compose.ui.text.style.TextAlign.Center,style=MaterialTheme.typography.titleMedium)
                                IconAction(Glyph.FORWARD,"下一周",stats.to<today){weekAnchor=LocalDate.parse(weekAnchor).plusWeeks(1).toString();selected=weekAnchor}
                            }
                            val values=(0..6).map {daily[stats.from.plusDays(it.toLong()).toString()]}
                            val average=values.filterNotNull().takeIf {it.isNotEmpty()}?.average()?.toInt()
                            Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                                Text(signedKcal(average),style=MaterialTheme.typography.headlineLarge)
                                Text("kcal / 日均热量差",modifier=Modifier.padding(bottom=5.dp),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            BalanceCurve(values,values.mapIndexed {i,value->"${stats.from.plusDays(i.toLong())} 热量差 ${signedKcal(value)} 千卡"}.joinToString())
                            Row(Modifier.fillMaxWidth().padding(start=balanceAxisWidth(values)+8.dp)){stats.buckets.forEachIndexed {i,b->val date=stats.from.plusDays(i.toLong());TextButton(onClick={if(selected!=date.toString())feedback();selected=date.toString()},contentPadding=PaddingValues(0.dp),modifier=Modifier.weight(1f).heightIn(min=48.dp)){Text(b.label)}}}
                        }
                    }
                }
            }
        }
        item(key="day-details") {Paper {
            Text(selected,style=MaterialTheme.typography.titleMedium)
            Text("热量差 ${signedKcal(daily[selected])} kcal",style=MaterialTheme.typography.titleMedium)
            val records=meals.filter{it.date==selected}
            if(records.isEmpty())Text("这一天还没有记录",color=MaterialTheme.colorScheme.onSurfaceVariant)
            else {Text("${records.sumOf{it.kcal}} kcal",style=MaterialTheme.typography.headlineSmall);records.forEach{Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(12.dp)) {Text(it.title,Modifier.weight(1f));Text("${it.kcal} kcal",color=MaterialTheme.colorScheme.primary)}}}
        }}
    }
}

@Composable private fun PeriodSwitch(monthly: Boolean,onChange: (Boolean)->Unit) {
    val feedback=selectionFeedback()
    BoxWithConstraints(Modifier.fillMaxWidth().height(58.dp)
        .background(MaterialTheme.colorScheme.surfaceContainerHighest,RoundedCornerShape(22.dp)).padding(5.dp)) {
        val cellWidth=maxWidth/2
        val offset by animateDpAsState(if(monthly)cellWidth else 0.dp,tween(220),label="时间选项")
        Box(Modifier.offset {IntOffset(offset.roundToPx(),0)}.width(cellWidth).fillMaxHeight()
            .background(MaterialTheme.colorScheme.surface,RoundedCornerShape(18.dp)))
        Row(Modifier.fillMaxSize().selectableGroup()) {
            listOf(false to "本周",true to "本月").forEach {(value,label)->
                val selected=value==monthly
                Box(Modifier.weight(1f).fillMaxHeight().selectable(selected=selected,role=Role.Tab,
                    interactionSource=remember {MutableInteractionSource()},indication=null,onClick={if(!selected)feedback();onChange(value)}),
                    contentAlignment=Alignment.Center) {
                    Text(label,style=MaterialTheme.typography.labelLarge,
                        color=if(selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}
