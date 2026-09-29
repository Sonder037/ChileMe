package me.chile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Suggestions only fill the draft; sending remains an explicit user action. */
@Composable internal fun ChatWelcome(onDraft: (String)->Unit) {
    Column(Modifier.fillMaxWidth().padding(top=72.dp,bottom=28.dp),verticalArrangement=Arrangement.spacedBy(24.dp)) {
        Text("吃了什么，\n聊聊就记好。",style=MaterialTheme.typography.headlineLarge)
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
            Suggestion("记运动","刚才运动了",onDraft)
            Suggestion("看近况","总结一下我最近七天的进度",onDraft)
        }
    }
}

@Composable private fun RowScope.Suggestion(label: String,draft: String,onDraft: (String)->Unit) {
    FilledTonalButton(onClick={onDraft(draft)},modifier=Modifier.weight(1f).heightIn(min=52.dp),
        shape=RoundedCornerShape(18.dp),colors=ButtonDefaults.filledTonalButtonColors(
            containerColor=MaterialTheme.colorScheme.primaryContainer,contentColor=MaterialTheme.colorScheme.primary)) {
        Text(label,style=MaterialTheme.typography.labelLarge)
    }
}
