package me.chile.app.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

private val chileColors=run {
    val ink=Color(0xFF252A3A)
    val accent=Color(0xFF4966CF)
    val bg=Color(0xFFF6F7FB)
    val surface=Color.White
    val muted=Color(0xFF6F7485)
    val container=Color(0xFFEEF1FF)
    lightColorScheme(primary=accent,onPrimary=Color.White,
        primaryContainer=container,onPrimaryContainer=ink,secondary=Color(0xFF4966CF),onSecondary=surface,
        secondaryContainer=container,onSecondaryContainer=ink,tertiary=Color(0xFF4966CF),onTertiary=surface,
        tertiaryContainer=container,onTertiaryContainer=ink,background=bg,onBackground=ink,
        surface=surface,onSurface=ink,surfaceVariant=container,onSurfaceVariant=muted,
        surfaceTint=accent,surfaceContainer=surface,surfaceContainerLow=surface,surfaceContainerHigh=container,
        surfaceContainerHighest=container,surfaceContainerLowest=bg,surfaceBright=surface,surfaceDim=bg,
        outline=muted,outlineVariant=Color(0xFFE5E7F0),
        inverseSurface=ink,inverseOnSurface=bg,inversePrimary=accent)
}
internal val RoundedNumbers=FontFamily(android.graphics.Typeface.create("sans-serif-rounded",android.graphics.Typeface.NORMAL))
private val chileType=Typography(
    headlineLarge=TextStyle(fontFamily=RoundedNumbers,fontWeight=FontWeight.Bold,fontSize=34.sp,lineHeight=40.sp,letterSpacing=(-.8).sp),
    headlineMedium=TextStyle(fontWeight=FontWeight.Bold,fontSize=28.sp,lineHeight=36.sp,letterSpacing=(-.5).sp),
    headlineSmall=TextStyle(fontFamily=RoundedNumbers,fontWeight=FontWeight.SemiBold,fontSize=26.sp,lineHeight=32.sp),
    titleLarge=TextStyle(fontWeight=FontWeight.Bold,fontSize=23.sp,lineHeight=30.sp),
    titleMedium=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=17.sp,lineHeight=24.sp),
    titleSmall=TextStyle(fontWeight=FontWeight.SemiBold,fontSize=15.sp,lineHeight=22.sp),
    bodyLarge=TextStyle(fontSize=16.sp,lineHeight=25.sp),
    bodyMedium=TextStyle(fontSize=15.sp,lineHeight=23.sp),
    bodySmall=TextStyle(fontSize=13.sp,lineHeight=20.sp),
    labelLarge=TextStyle(fontWeight=FontWeight.Medium,fontSize=14.sp,lineHeight=20.sp),
    labelMedium=TextStyle(fontWeight=FontWeight.Medium,fontSize=12.sp,lineHeight=17.sp),
    labelSmall=TextStyle(fontSize=11.sp,lineHeight=16.sp)
)
@Composable fun ChileTheme(content: @Composable ()->Unit) {
    MaterialTheme(colorScheme=chileColors,typography=chileType,
        shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),content=content)
}

enum class Glyph { CHAT, RECORD, TREND, CAMERA, SETTINGS, BACK, FORWARD, HELP, SEND, PLUS, DELETE, IMAGE, CLOSE, FLASH, FLASH_OFF, FLASH_AUTO }
@Composable fun GlyphIcon(glyph: Glyph,modifier: Modifier=Modifier,color: Color=MaterialTheme.colorScheme.primary) {
    Canvas(modifier.size(24.dp)) {
        scale(size.width/24f,size.height/24f,Offset.Zero) {
            val stroke=Stroke(1.9f,cap=StrokeCap.Round,join=StrokeJoin.Round)
            fun path(block: Path.()->Unit)=drawPath(Path().apply(block),color,style=stroke)
            fun line(x:Float,y:Float,a:Float,b:Float)=drawLine(color,Offset(x,y),Offset(a,b),1.9f,StrokeCap.Round)
            when(glyph) {
                Glyph.HELP -> {drawCircle(color,9f,Offset(12f,12f),style=stroke);path {moveTo(9.5f,9f);cubicTo(9.5f,5.5f,16f,6f,14f,10f);quadraticTo(12f,11f,12f,13f)};drawCircle(color,1f,Offset(12f,16.5f))}
                Glyph.CHAT -> {path {moveTo(4f,6f);cubicTo(10f,1f,21f,4f,21f,10f);quadraticTo(21f,18f,10f,18f);lineTo(4f,21f);lineTo(5f,16f);quadraticTo(1f,11f,4f,6f)};path {moveTo(8f,10f);quadraticTo(12f,15f,17f,10f)}}
                Glyph.RECORD -> {path {moveTo(5f,3f);lineTo(17f,3f);quadraticTo(20f,3f,20f,6f);lineTo(20f,21f);lineTo(5f,21f);close()};line(3f,8f,7f,8f);line(3f,14f,7f,14f);line(10f,9f,16f,9f);line(10f,14f,14f,14f)}
                Glyph.TREND -> {drawRoundRect(color,Offset(3f,13f),Size(4f,8f),androidx.compose.ui.geometry.CornerRadius(1.5f),style=stroke);drawRoundRect(color,Offset(10f,4f),Size(4f,17f),androidx.compose.ui.geometry.CornerRadius(1.5f),style=stroke);drawRoundRect(color,Offset(17f,9f),Size(4f,12f),androidx.compose.ui.geometry.CornerRadius(1.5f),style=stroke)}
                Glyph.CAMERA -> {path {moveTo(6f,7f);lineTo(8f,7f);lineTo(9.5f,4.5f);lineTo(14.5f,4.5f);lineTo(16f,7f);lineTo(18f,7f);quadraticTo(21f,7f,21f,10f);lineTo(21f,17f);quadraticTo(21f,20f,18f,20f);lineTo(6f,20f);quadraticTo(3f,20f,3f,17f);lineTo(3f,10f);quadraticTo(3f,7f,6f,7f);close()};drawCircle(color,3.5f,Offset(12f,13f),style=stroke)}
                Glyph.FLASH, Glyph.FLASH_OFF, Glyph.FLASH_AUTO -> {
                    path {moveTo(13f,2f);lineTo(5f,13f);lineTo(11f,13f);lineTo(10f,22f);lineTo(18f,10f);lineTo(12f,10f);close()}
                    if(glyph==Glyph.FLASH_OFF)line(3f,3f,21f,21f)
                    if(glyph==Glyph.FLASH_AUTO){line(17f,7f,19.5f,2f);line(19.5f,2f,22f,7f);line(18f,5.5f,21f,5.5f)}
                }
                Glyph.SETTINGS -> {listOf(5f,12f,19f).forEach {line(it,3f,it,21f)};listOf(Offset(5f,8f),Offset(12f,16f),Offset(19f,6f)).forEach {drawCircle(color,2.5f,it,style=stroke)}}
                Glyph.BACK -> {line(4f,12f,20f,12f);line(4f,12f,10f,6f);line(4f,12f,10f,18f)}
                Glyph.FORWARD -> {line(4f,12f,20f,12f);line(20f,12f,14f,6f);line(20f,12f,14f,18f)}
                Glyph.SEND -> {line(12f,4f,12f,21f);line(12f,4f,5f,11f);line(12f,4f,19f,11f)}
                Glyph.PLUS -> {line(4f,12f,20f,12f);line(12f,4f,12f,20f)}
                Glyph.CLOSE -> {line(5f,5f,19f,19f);line(5f,19f,19f,5f)}
                Glyph.DELETE -> {line(4f,6f,20f,6f);path{moveTo(6f,6f);lineTo(7f,21f);lineTo(17f,21f);lineTo(18f,6f)};line(9f,3f,15f,3f);line(10f,10f,10f,17f);line(14f,10f,14f,17f)}
                Glyph.IMAGE -> {path{moveTo(3f,3f);lineTo(21f,3f);lineTo(21f,21f);lineTo(3f,21f);close();moveTo(3f,18f);lineTo(10f,10f);lineTo(16f,17f);lineTo(19f,13f);lineTo(21f,16f)};drawCircle(color,2f,Offset(16f,8f),style=stroke)}
            }
        }
    }
}
// A top highlight and a quieter lower edge keep raised surfaces light.
@Composable fun paperEdge(width: androidx.compose.ui.unit.Dp=0.3125.dp): BorderStroke {
    val strength=(width.value/.3125f).coerceIn(0f,1f)
    return BorderStroke(width,Brush.verticalGradient(listOf(
        Color.White.copy(alpha=.95f*strength),
        MaterialTheme.colorScheme.outlineVariant.copy(alpha=.55f*strength)
    )))
}
@Composable fun Paper(modifier: Modifier=Modifier,content: @Composable ColumnScope.()->Unit) {
    Card(modifier.fillMaxWidth(),shape=RoundedCornerShape(28.dp),elevation=CardDefaults.cardElevation(defaultElevation=0.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}
