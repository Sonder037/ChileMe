package me.chile.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

// Quiet static light fields, shared across pages; no moving decoration behind messages.
fun Modifier.softBackdrop(): Modifier = drawWithCache {
    val base=Brush.verticalGradient(listOf(Color(0xFFFCFDFF),Color(0xFFF5F7FC)))
    val sky=Brush.radialGradient(listOf(Color(0xFFE3F3FF).copy(alpha=.65f),Color.Transparent),
        center=androidx.compose.ui.geometry.Offset(size.width*1.04f,size.height*.12f),radius=size.width*.9f)
    val lilac=Brush.radialGradient(listOf(Color(0xFFEDE7FF).copy(alpha=.5f),Color.Transparent),
        center=androidx.compose.ui.geometry.Offset(-size.width*.12f,size.height*.7f),radius=size.width*.95f)
    onDrawBehind {drawRect(base);drawRect(sky);drawRect(lilac)}
}

private val actionGradient=Brush.linearGradient(listOf(Color(0xFF4966CF),Color(0xFF6376D7)))

@Composable fun PrimaryButton(onClick: ()->Unit,modifier: Modifier=Modifier,enabled: Boolean=true,
    shape: Shape=RoundedCornerShape(18.dp),content: @Composable RowScope.()->Unit) {
    val interaction=remember {MutableInteractionSource()}
    Button(onClick=onClick,enabled=enabled,shape=shape,interactionSource=interaction,
        modifier=modifier.heightIn(min=48.dp).pressMotion(interaction).clip(shape)
            .then(if(enabled)Modifier.background(actionGradient) else Modifier),
        colors=ButtonDefaults.buttonColors(containerColor=Color.Transparent,contentColor=Color.White),content=content)
}

@Composable fun GradientIconButton(onClick: ()->Unit,enabled: Boolean,modifier: Modifier=Modifier,content: @Composable ()->Unit) {
    val interaction=remember {MutableInteractionSource()}
    FilledIconButton(onClick=onClick,enabled=enabled,interactionSource=interaction,
        modifier=modifier.pressMotion(interaction).clip(CircleShape).then(if(enabled)Modifier.background(actionGradient) else Modifier),
        colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.Transparent,contentColor=Color.White),content=content)
}
