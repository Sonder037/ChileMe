package me.chile.app.ui

import android.os.SystemClock
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

// Center emphasis and fading edges follow Android-PickerView's wheel pattern.
// Compose owns drag velocity and snapping; value callbacks never reposition a moving list.
@Composable internal fun ProfileWheel(label: String,range: IntRange,value: Int,modifier: Modifier=Modifier,
    onMoving: (Boolean)->Unit,onValue: (Int)->Unit) {
    val state=rememberLazyListState(initialFirstVisibleItemIndex=value.coerceIn(range)-range.first)
    val rowHeight=48.dp*LocalDensity.current.fontScale.coerceAtLeast(1f)
    val scope=rememberCoroutineScope()
    val view=LocalView.current
    val changed by rememberUpdatedState(onValue)
    val moving by rememberUpdatedState(onMoving)
    val selected by remember {
        derivedStateOf {
            val layout=state.layoutInfo
            val center=(layout.viewportStartOffset+layout.viewportEndOffset)/2
            layout.visibleItemsInfo.minByOrNull {abs(it.offset+it.size/2-center)}?.index
                ?: (value.coerceIn(range)-range.first)
        }
    }
    LaunchedEffect(state) {
        var previous=selected
        var lastTick=0L
        snapshotFlow {selected to state.isScrollInProgress}.collect {(index,busy)->
            // Publish the selected value before allowing confirmation again.
            changed(range.first+index)
            moving(busy)
            if(index!=previous && busy) {
                val now=SystemClock.uptimeMillis()
                if(now-lastTick>=65){view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);lastTick=now}
            }
            previous=index
        }
    }
    fun moveTo(index: Int): Boolean {
        if(index !in 0 until range.count() || state.isScrollInProgress)return false
        scope.launch {state.animateScrollToItem(index)}
        return true
    }
    Box(modifier.height(rowHeight*5).clearAndSetSemantics {
        contentDescription=label
        stateDescription=(range.first+selected).toString()
        progressBarRangeInfo=ProgressBarRangeInfo((range.first+selected).toFloat(),range.first.toFloat()..range.last.toFloat(),range.count()-2)
        setProgress {moveTo(it.roundToInt().coerceIn(range)-range.first)}
        customActions=listOf(CustomAccessibilityAction("增加"){moveTo(selected+1)},CustomAccessibilityAction("减少"){moveTo(selected-1)})
    },contentAlignment=Alignment.Center) {
        Box(Modifier.fillMaxWidth().height(rowHeight).background(MaterialTheme.colorScheme.primaryContainer,RoundedCornerShape(16.dp)))
        LazyColumn(state=state,modifier=Modifier.fillMaxSize(),contentPadding=PaddingValues(vertical=rowHeight*2),
            flingBehavior=rememberSnapFlingBehavior(state),horizontalAlignment=Alignment.CenterHorizontally) {
            items(range.count(),key={it}) {index->
                val distance by remember(index) {derivedStateOf {
                    val layout=state.layoutInfo
                    val item=layout.visibleItemsInfo.firstOrNull {it.index==index}
                    if(item==null)2f else abs(item.offset+item.size/2f-(layout.viewportStartOffset+layout.viewportEndOffset)/2f)/item.size
                }}
                Box(Modifier.fillMaxWidth().height(rowHeight).clickable {moveTo(index)},contentAlignment=Alignment.Center) {
                    Text((range.first+index).toString(),fontFamily=RoundedNumbers,fontSize=32.sp,
                        color=if(index==selected)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier=Modifier.graphicsLayer {
                            val edge=distance.coerceIn(0f,2f)/2f
                            scaleX=1f-.28f*edge;scaleY=scaleX;alpha=1f-.78f*edge
                        })
                }
            }
        }
    }
}
