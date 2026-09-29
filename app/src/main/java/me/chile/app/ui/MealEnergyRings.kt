package me.chile.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlin.math.*
import me.chile.app.domain.NutrientProgress

private val mealColors=mapOf("早餐" to Color(0xFFFFB347),"午餐" to Color(0xFF4D94F5),
    "晚餐" to Color(0xFF9A79EF),"零食" to Color(0xFFEF83AE),"夜宵" to Color(0xFF6C83E9),"其他" to Color(0xFF91A5C2))

@Composable internal fun MealEnergyRings(parts: List<Pair<String,Int>>,activity: Int?,active: Boolean,
    nutrients: List<NutrientProgress> = emptyList(), dailyReference: Int? = null,
    center: @Composable BoxScope.()->Unit) {
    var selected by remember(parts,nutrients,active,dailyReference,activity) {mutableStateOf<String?>(null)}
    val reveal=remember {Animatable(0f)}
    LaunchedEffect(active) {if(active)reveal.animateTo(1f,tween(420,easing=FastOutSlowInEasing)) else reveal.snapTo(0f)}
    val activityParts=listOfNotNull(dailyReference?.let {"日常参考" to it},activity?.let {"已记运动" to it})
    val activityTotal=activityParts.sumOf {it.second}
    val dailyColor=Color(0xFF8C7AF0)
    val ceiling=maxOf(parts.sumOf {it.second},activityTotal,1).toFloat()
    val intake by animateFloatAsState(parts.sumOf {it.second}/ceiling,tween(300),label="摄入更新")
    val exercise by animateFloatAsState(activityTotal/ceiling,tween(300),label="运动更新")
    val focus by animateFloatAsState(if(selected==null)0f else 1f,tween(160),label="餐次放大")
    val shown=parts.filter {it.second>0}
    val total=shown.sumOf {it.second}.coerceAtLeast(1).toFloat()
    val nutrientColors=listOf(Color(0xFF4C90F2),Color(0xFFAF83EE),Color(0xFF35BDA5))
    val nutrientFill=nutrients.map {animateFloatAsState((it.ratio?:0.0).coerceIn(0.0,1.0).toFloat(),tween(350),label="营养素填充").value}
    fun nutrientText(n: NutrientProgress): String {
        fun number(v: Double?)=v?.let {"%.1f".format(java.util.Locale.ROOT,it)}?:"—"
        val range=if(n.minimum!=null&&n.target!=null)"${number(n.minimum)}–${number(n.target)}" else "—"
        return "${n.name} ${number(n.grams)}g · 参考 ${range}g" + if(n.incomplete)" · 部分记录" else ""
    }
    val ringStep=33.dp
    val haptic=LocalHapticFeedback.current
    val track=MaterialTheme.colorScheme.surfaceContainerHighest
    Box(Modifier.fillMaxWidth().height(190.dp),contentAlignment=Alignment.BottomCenter) {
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription=(if(nutrients.isEmpty())"" else nutrients.joinToString("；") {nutrientText(it)}+"；")+"摄入：${parts.joinToString {"${it.first} ${it.second} 千卡"}.ifEmpty {"未记录"}}；日常参考 ${dailyReference?.let {"$it 千卡"}?:"暂无"}；运动 ${activity?.let {"$it 千卡"}?:"未记录"}"
            customActions=nutrients.map {n->CustomAccessibilityAction(nutrientText(n)) {selected="nutrient:${n.name}";true}}+activityParts.map {part->CustomAccessibilityAction("${part.first} ${part.second} 千卡") {selected="activity:${part.first}";true}}+shown.map {part->CustomAccessibilityAction("${part.first} ${part.second} 千卡") {selected=part.first;true}}
        }.pointerInput(parts,nutrients,active,dailyReference,activity) {
            fun pick(point: Offset): String? {
                val origin=Offset(size.width/2f,size.height-16.dp.toPx())
                val topRadius=minOf(size.width/2f-20.dp.toPx(),size.height-42.dp.toPx())
                val radius=topRadius-if(nutrients.isEmpty())0f else ringStep.toPx()
                val dx=point.x-origin.x;val dy=point.y-origin.y
                if(dy>10.dp.toPx())return null
                // The round caps extend below the baseline; map them to the arc endpoints.
                val angle=if(dy>=0f)if(dx<0f)0f else 180f else (atan2(dy,dx)*180f/PI.toFloat()+180f).coerceIn(0f,180f)
                if(nutrients.isNotEmpty() && abs(hypot(dx,dy)-topRadius)<14.dp.toPx()) {
                    val index=(angle/60f).toInt().coerceIn(0,nutrients.lastIndex)
                    return "nutrient:${nutrients[index].name}"
                }
                if(abs(hypot(dx,dy)-(radius-ringStep.toPx()))<14.dp.toPx()) {
                    var start=0f
                    for(part in activityParts) {
                        val sweep=180f*exercise*reveal.value*part.second/activityTotal.coerceAtLeast(1)
                        if(sweep>0 && angle in start..(start+sweep))return "activity:${part.first}"
                        start+=sweep
                    }
                    return null
                }
                if(abs(hypot(dx,dy)-radius)>16.dp.toPx())return null
                var start=0f
                for(part in shown) {
                    val sweep=180f*intake*reveal.value*part.second/total
                    if(sweep>0f && angle>=start && angle<=start+sweep)return part.first
                    start+=sweep
                }
                return null
            }
            detectDragGesturesAfterLongPress(onDragStart={point->selected=pick(point);if(selected!=null)haptic.performHapticFeedback(HapticFeedbackType.LongPress)},
                onDragEnd={selected=null},onDragCancel={selected=null},onDrag={change,_->selected=pick(change.position);change.consume()})
        }) {
            val origin=Offset(size.width/2,size.height-16.dp.toPx())
            val nutrientRadius=minOf(size.width/2-20.dp.toPx(),size.height-42.dp.toPx())
            val outer=nutrientRadius-if(nutrients.isEmpty())0f else ringStep.toPx()
            val inner=outer-ringStep.toPx()
            val width=19.dp.toPx()
            fun arc(color: Color,radius: Float,start: Float,sweep: Float,thickness: Float,cap: StrokeCap) {
                drawArc(color,start,sweep,false,Offset(origin.x-radius,origin.y-radius),Size(radius*2,radius*2),style=Stroke(thickness,cap=cap))
            }
            // One filled outline joins the arc and caps without an antialias hairline.
            fun segment(color: Color,radius: Float,start: Float,sweep: Float,thickness: Float,first: Boolean,last: Boolean) {
                val half=thickness/2
                fun circle(center: Offset,r: Float)=Rect(center.x-r,center.y-r,center.x+r,center.y+r)
                fun point(r: Float,angle: Float): Offset {
                    val radians=angle*PI.toFloat()/180f
                    return origin+Offset(cos(radians)*r,sin(radians)*r)
                }
                // Small rounded corners soften internal joins; outer ends keep full round caps.
                val corner=minOf(3.dp.toPx(),thickness*.18f,radius*sweep*PI.toFloat()/180f*.2f)
                val outerTurn=asin((corner/(radius+half)).coerceIn(0f,1f))*180f/PI.toFloat()
                val innerTurn=asin((corner/(radius-half)).coerceIn(0f,1f))*180f/PI.toFloat()
                val outerStart=start+if(first)0f else outerTurn
                val outerEnd=start+sweep-if(last)0f else outerTurn
                val innerStart=start+if(first)0f else innerTurn
                val innerEnd=start+sweep-if(last)0f else innerTurn
                val begin=point(radius+half,outerStart)
                val path=Path().apply {
                    fun round(control: Offset,end: Offset)=quadraticTo(control.x,control.y,end.x,end.y)
                    fun line(point: Offset)=lineTo(point.x,point.y)
                    moveTo(begin.x,begin.y)
                    arcTo(circle(origin,radius+half),outerStart,outerEnd-outerStart,false)
                    if(last)arcTo(circle(point(radius,start+sweep),half),start+sweep,180f,false)
                    else {
                        round(point(radius+half,start+sweep),point(radius+half-corner,start+sweep))
                        line(point(radius-half+corner,start+sweep))
                        round(point(radius-half,start+sweep),point(radius-half,innerEnd))
                    }
                    arcTo(circle(origin,radius-half),innerEnd,innerStart-innerEnd,false)
                    if(first)arcTo(circle(point(radius,start),half),start+180f,180f,false)
                    else {
                        round(point(radius-half,start),point(radius-half+corner,start))
                        line(point(radius+half-corner,start))
                        round(point(radius+half,start),begin)
                    }
                    close()
                }
                drawPath(path,color)
            }
            nutrients.forEachIndexed {index,n->
                val expanded=if(selected=="nutrient:${n.name}")focus else 0f
                val radius=nutrientRadius+expanded*2.dp.toPx()
                val thickness=width+expanded*5.dp.toPx()
                // Trim only internal joins. Outside caps share the other rings' baseline.
                val halfGap=asin(((width+4.dp.toPx())/(2*nutrientRadius)).coerceIn(0f,1f))*180f/PI.toFloat()
                val leading=if(index==0)0f else halfGap
                val trailing=if(index==nutrients.lastIndex)0f else halfGap
                val start=180f+index*60f+leading
                val sweep=60f-leading-trailing
                val color=when(n.level) {2->Color(0xFFE85D67);1->Color(0xFFF29842);else->nutrientColors[index]}
                arc(color.copy(alpha=.12f),radius,start,sweep,thickness,StrokeCap.Round)
                val fill=nutrientFill[index]*reveal.value
                if(fill>0f)arc(color,radius,start,sweep*fill,thickness,StrokeCap.Round)
            }
            arc(track,outer,180f,180f,width,StrokeCap.Round)
            arc(activityColor.copy(alpha=.12f),inner,180f,180f,width,StrokeCap.Round)
            var start=180f
            shown.forEachIndexed {index,part->
                val sweep=180f*intake*reveal.value*part.second/total
                val expanded=if(selected==part.first)focus else 0f
                val gap=minOf(1.4f,sweep*.12f)
                if(sweep>0) {
                    val color=mealColors.getValue(part.first)
                    val radius=outer+expanded*3.dp.toPx()
                    val thickness=width+expanded*7.dp.toPx()
                    val first=index==0
                    val last=index==shown.lastIndex
                    val leading=if(first)0f else gap/2
                    val trailing=if(last)0f else gap/2
                    segment(color,radius,start+leading,sweep-leading-trailing,thickness,first,last)
                }
                start+=sweep
            }
            var activityStart=180f
            activityParts.forEach {part->
                val sweep=180f*exercise*reveal.value*part.second/activityTotal.coerceAtLeast(1)
                val expanded=if(selected=="activity:${part.first}")focus else 0f
                if(sweep>0)segment(if(part.first=="日常参考")dailyColor else activityColor,inner+expanded*2.dp.toPx(),
                    activityStart,sweep,width+expanded*5.dp.toPx(),activityStart==180f,part==activityParts.lastOrNull {it.second>0})
                activityStart+=sweep
            }
        }
        selected?.let {name->
            Text(if(name.startsWith("nutrient:"))nutrients.firstOrNull {it.name==name.substringAfter(":")}?.let {nutrientText(it)}?:"" else if(name.startsWith("activity:"))activityParts.firstOrNull {it.first==name.substringAfter(":")}?.let {"${it.first} ${it.second} kcal"}?:"" else name,Modifier.align(Alignment.TopCenter),style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.onSurface)
        }
        center()
    }
}
