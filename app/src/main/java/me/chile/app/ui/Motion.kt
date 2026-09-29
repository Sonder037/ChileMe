package me.chile.app.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer

// Native Compose clocks respect Android's animator duration scale, including zero.
@Composable fun Modifier.pressMotion(source: MutableInteractionSource,pressedScale: Float=.96f): Modifier {
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed)pressedScale else 1f,spring(dampingRatio=.72f,stiffness=650f),label="按压反馈")
    return graphicsLayer {scaleX=scale;scaleY=scale}
}

// Native view feedback respects the system's touch-feedback preference; no vibrator permission.
@Composable internal fun selectionFeedback(): ()->Unit {
    val view=androidx.compose.ui.platform.LocalView.current
    return {view.performHapticFeedback(android.view.HapticFeedbackConstants.CLOCK_TICK);Unit}
}
