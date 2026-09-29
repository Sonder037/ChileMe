package me.chile.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

@Composable fun TypingIndicator() {
    val motion=rememberInfiniteTransition(label="回复等待")
    val dots=(0..2).map {index->motion.animateFloat(.25f,1f,
        infiniteRepeatable(tween(650),RepeatMode.Reverse,StartOffset(index*180)),label="圆点$index")}
    val color=MaterialTheme.colorScheme.primary
    Surface(Modifier.padding(vertical=4.dp).semantics {contentDescription="正在回复"},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.primaryContainer) {
        Canvas(Modifier.padding(horizontal=16.dp,vertical=15.dp).size(36.dp,8.dp)) {
            dots.forEachIndexed {index,alpha->drawCircle(color.copy(alpha=alpha.value),3.dp.toPx(),Offset((4+index*14).dp.toPx(),size.height/2))}
        }
    }
}
