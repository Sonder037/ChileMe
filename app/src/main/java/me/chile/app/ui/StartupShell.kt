package me.chile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** Immediate first frame; no editable state or database-backed content yet. */
@Composable internal fun StartupShell() {
    Box(Modifier.fillMaxSize().softBackdrop().safeDrawingPadding()) {
        Column(Modifier.fillMaxWidth().padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
            listOf(.62f,.82f,.46f).forEach {width->
                Surface(Modifier.fillMaxWidth(width).height(44.dp),shape=RoundedCornerShape(20.dp),
                    color=MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.45f)) {}
            }
        }
        Surface(Modifier.align(Alignment.BottomCenter).padding(horizontal=24.dp,vertical=12.dp)
            .fillMaxWidth().semantics {contentDescription="消息输入加载中"},
            shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface,shadowElevation=2.dp,border=paperEdge(0.15625.dp)) {
            Row(Modifier.height(62.dp).padding(start=22.dp,end=18.dp),verticalAlignment=Alignment.CenterVertically) {
                GlyphIcon(Glyph.CAMERA,color=MaterialTheme.colorScheme.outline)
                Spacer(Modifier.width(18.dp))
                Text("正在读取记录…",Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
