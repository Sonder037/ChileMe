package me.chile.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.rememberTextMeasurer
import kotlin.math.abs

// Display bands chosen by the user, not clinical thresholds.
internal val balanceStops=listOf(-900 to Color(0xFF3264B5),-300 to Color(0xFF279875),
    300 to Color(0xFF279875),600 to Color(0xFFE99738),900 to Color(0xFFD95561))
internal fun balanceColor(value: Int?): Color {
    if(value==null)return Color(0xFF9097A5)
    if(value<=balanceStops.first().first)return balanceStops.first().second
    for((a,b) in balanceStops.zipWithNext()) if(value<=b.first)
        return lerp(a.second,b.second,(value-a.first).toFloat()/(b.first-a.first))
    return balanceStops.last().second
}
internal fun signedKcal(value: Int?)=value?.let {if(it>0)"+$it" else "$it"}?:"—"

// Horizontal endpoint tangents keep each cubic within its two recorded values.
internal data class BalanceSegment(val index: Int,val from: Float,val to: Float) {
    fun valueAt(t: Float)=from+(to-from)*t*t*(3-2*t)
}
internal fun balanceSegments(values: List<Int?>)=values.zipWithNext().mapIndexedNotNull {i,(a,b)->
    if(a==null || b==null)null else BalanceSegment(i,a.toFloat(),b.toFloat())
}

@Composable internal fun balanceAxisWidth(values: List<Int?>): androidx.compose.ui.unit.Dp {
    val limit=maxOf(1000,values.filterNotNull().maxOfOrNull {abs(it)}?:0)
    val measured=rememberTextMeasurer().measure("−$limit",MaterialTheme.typography.labelSmall).size.width
    return maxOf(36.dp,with(LocalDensity.current){measured.toDp()}+4.dp)
}

@Composable internal fun BalanceCurve(values: List<Int?>,description: String) {
    val limit=maxOf(1000,values.filterNotNull().maxOfOrNull {abs(it)}?:0)
    val grid=MaterialTheme.colorScheme.outlineVariant
    val muted=MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.fillMaxWidth()) {
        Column(Modifier.width(balanceAxisWidth(values)).height(156.dp).padding(vertical=6.dp),verticalArrangement=Arrangement.SpaceBetween) {
            listOf("+$limit","0","−$limit").forEach {Text(it,style=MaterialTheme.typography.labelSmall,color=muted,maxLines=1)}
        }
        Canvas(Modifier.weight(1f).height(156.dp).padding(start=8.dp).semantics {contentDescription=description}) {
            val inset=8.dp.toPx()
            fun y(value: Float)=inset+(size.height-2*inset)*(limit-value)/(2*limit)
            fun x(index: Int)=size.width*(index+.5f)/values.size.coerceAtLeast(1)
            drawLine(grid,Offset(0f,y(0f)),Offset(size.width,y(0f)),1.dp.toPx(),pathEffect=PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(),5.dp.toPx())))
            val stops=(listOf(limit to balanceColor(limit))+balanceStops.reversed().filter {it.first in -limit..limit}+listOf(-limit to balanceColor(-limit)))
                .distinctBy {it.first}.map {(value,color)->((limit-value).toFloat()/(2*limit)) to color}.toTypedArray()
            val brush=Brush.verticalGradient(*stops,startY=inset,endY=size.height-inset)
            val path=Path()
            balanceSegments(values).forEach {segment->
                val x0=x(segment.index);val x1=x(segment.index+1);val dx=(x1-x0)/3
                path.moveTo(x0,y(segment.from))
                path.cubicTo(x0+dx,y(segment.from),x1-dx,y(segment.to),x1,y(segment.to))
            }
            drawPath(path,brush,style=Stroke(3.dp.toPx(),cap=StrokeCap.Round))
            values.forEachIndexed {i,value->if(value!=null)drawCircle(balanceColor(value),4.dp.toPx(),Offset(x(i),y(value.toFloat())))}
        }
    }
}
