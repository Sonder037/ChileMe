package me.chile.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.chile.app.AppViewModel

@Composable fun MemoriesScreen(vm: AppViewModel) {
    var editing by remember {mutableStateOf<me.chile.app.domain.Memory?>(null)}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Paper {
            Row(verticalAlignment=Alignment.CenterVertically){Switch(vm.data.memoryEnabled,{vm.toggleMemory()});Text("自动记录明确偏好")}
            Row(verticalAlignment=Alignment.CenterVertically){Switch(vm.includeMemory,vm::setIncludeMemory);Text("聊天附带本地记忆")}
        }}
        items(vm.data.memories,key={it.key}) {memory->Paper {
            Text(memory.text)
            Row {TextButton(onClick={editing=memory}){Text("编辑")};TextButton(onClick={vm.forget(memory.key)}){Text("忘记这条")}}
        }}
        if(vm.data.memories.isEmpty())item {Text("还没有本地记忆",color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
    editing?.let {m->AlertDialog(onDismissRequest={editing=null},title={Text("编辑记忆")},text={SoftField(m.text,{editing=m.copy(text=it.take(1000))},"内容")},confirmButton={TextButton(onClick={vm.updateMemory(m);editing=null},enabled=m.text.isNotBlank()){Text("保存")}},dismissButton={TextButton(onClick={editing=null}){Text("取消")}})}
}
