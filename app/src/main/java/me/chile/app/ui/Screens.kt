package me.chile.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.chile.app.AppViewModel
import me.chile.app.domain.*

@Composable private fun FormField(value: String,label: String,keyboard: KeyboardType=KeyboardType.Text,onChange: (String)->Unit) {
    SoftField(value,onChange,label,keyboard=keyboard)
}
@Composable fun ModelScreen(vm: AppViewModel,onboarding: Boolean,onDone: ()->Unit) {
    val config=vm.data.config
    var base by rememberSaveable {mutableStateOf(config.baseUrl)}
    var chat by rememberSaveable {mutableStateOf(config.chatModel)}
    var vision by rememberSaveable {mutableStateOf(config.visionModel)}
    var advanced by rememberSaveable {mutableStateOf(false)}
    // Secret input deliberately does not survive recreation or enter SavedStateHandle.
    var key by remember {mutableStateOf("")}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Paper {
            Text(if(base.trimEnd('/')=="https://api.deepseek.com" && chat=="deepseek-flash") "DeepSeek Flash" else chat,style=MaterialTheme.typography.titleLarge)
            TextButton(onClick={advanced=!advanced}){Text(if(advanced)"收起高级设置" else "高级设置")}
            if(advanced) {
                FormField(base,"服务地址 · HTTPS"){base=it}
                FormField(chat,"聊天模型名称"){chat=it}
                FormField(vision,"视觉模型名称"){vision=it}
            }
            SoftField(key,{key=it},if(vm.data.hasKey)"API Key · 留空保留已有密钥" else "API Key",secret=true)
            Text("发送时，消息、照片和相关记录会直达 ${base}。记录保存在本机，详情见右上角帮助。",style=MaterialTheme.typography.bodySmall)
            if(base.trimEnd('/')!=config.baseUrl.trimEnd('/'))Text("更换地址将清除原密钥，请为新服务填写密钥。",color=MaterialTheme.colorScheme.error)
        }}
        item {PrimaryButton(onClick={vm.saveConfig(config.copy(baseUrl=base.trim(),chatModel=chat.trim(),visionModel=vision.trim(),offline=false),key){key="";onDone()}},enabled=!vm.busy&&!vm.saving,modifier=Modifier.fillMaxWidth()){Text("保存配置")}}
        if(onboarding)item {TextButton(onClick=onDone,modifier=Modifier.fillMaxWidth()){Text("稍后连接，先本地记录")}}
        else item {TextButton(onClick=vm::clearKey,enabled=!vm.busy){Text("删除已保存的密钥")}}
    }
}
@Composable fun MealScreen(vm: AppViewModel,onDone: ()->Unit) {
    var cameraOpen by rememberSaveable {mutableStateOf(false)}
    var uploadConfirm by remember {mutableStateOf(false)}
    val gallery=rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()){uri->uri?.let(vm::importPhoto)}
    val meal=vm.data.mealDraft
    LaunchedEffect(Unit){vm.startMeal()}
    if(meal==null){CircularProgressIndicator();return}
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        item {Paper {
            Row {IconAction(Glyph.CAMERA,"拍照",!vm.busy){cameraOpen=true};IconAction(Glyph.IMAGE,"从相册选择",!vm.busy){gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))}}
            meal.photo?.let {name->
                val bitmap by produceState<android.graphics.Bitmap?>(null,name){value=withContext(Dispatchers.IO){vm.photos.preview(name)}}
                bitmap?.let {Image(it.asImageBitmap(),"待记录的食物照片",Modifier.fillMaxWidth().height(180.dp))}
                OutlinedButton(onClick={uploadConfirm=true},enabled=!vm.busy){Text("识别照片")}
            }
            if(vm.busy){LinearProgressIndicator(Modifier.fillMaxWidth());TextButton(onClick=vm::cancelRequest){Text("停止")}}
            FormField(meal.title,"餐次名称"){vm.updateMeal(meal.copy(title=it))}
            FormField(meal.date,"记录日期 · YYYY-MM-DD"){vm.updateMeal(meal.copy(date=it))}
        }}
        item {FoodGrid(meal.items,enabled=!vm.busy&&!vm.saving){vm.updateMeal(meal.copy(items=it))}}
        item {
            PrimaryButton(onClick={vm.confirmMeal(onDone)},enabled=meal.valid()&&!vm.busy&&!vm.saving,modifier=Modifier.fillMaxWidth()){Text("确认记录 · ${meal.kcal} kcal")}
            TextButton(onClick={vm.discardMeal();onDone()},enabled=!vm.busy){Text("丢弃草稿")}
        }
    }
    if(cameraOpen)CameraSheet(onClose={cameraOpen=false},onPhoto={uri->cameraOpen=false;vm.importPhoto(uri)})
    if(uploadConfirm)AlertDialog(onDismissRequest={uploadConfirm=false},title={Text("识别这张照片")},text={Text("将向 ${vm.data.config.baseUrl} 发送当前压缩照片。模型：${vm.data.config.visionModel.ifBlank {"尚未配置"}}。")},confirmButton={TextButton(onClick={uploadConfirm=false;vm.recognize()}){Text("开始识别")}},dismissButton={TextButton(onClick={uploadConfirm=false}){Text("取消")}})
}
@Composable fun SettingsScreen(vm: AppViewModel,open: (String)->Unit) {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(16.dp),contentPadding=PaddingValues(top=12.dp,bottom=20.dp)) {
        item {Paper {
            SettingRow("连接 AI",Glyph.CHAT){open("model")}
        }}
        item {Paper {
            SettingRow("身体资料",Glyph.TREND){open("profile")}
            SettingRow("本地记忆",Glyph.RECORD){open("memories")}
            SettingRow("手动补记",Glyph.PLUS){open("meal")}
        }}
        item {Text("记录只保存在本机。卸载会清除数据；当前版本尚未提供导出恢复。",Modifier.padding(horizontal=8.dp),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    }
}
@Composable private fun SettingRow(label: String,glyph: Glyph,onClick: ()->Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick=onClick).heightIn(min=52.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(14.dp)) {
        GlyphIcon(glyph);Text(label,Modifier.weight(1f),style=MaterialTheme.typography.bodyLarge)
        GlyphIcon(Glyph.FORWARD,Modifier.size(18.dp),color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
@Composable fun HelpScreen() {
    LazyColumn(verticalArrangement=Arrangement.spacedBy(12.dp),contentPadding=PaddingValues(bottom=20.dp)) {
        items(listOf("左右滑动" to "主页从左到右是记录、聊天、数据。聊天在中间：右滑看记录，左滑看数据。月历内部滑动翻月，其它区域切换页面。", "记录与网络" to "身体与饮食记录在本机。只有发送聊天、识别照片会联网。离线模式阻止这些请求。",
            "发送哪些数据" to "点击发送后，消息、所选照片及用于同餐对比的上一张饭前照片会发送到你配置的服务地址。查询工具会提供相关日期的餐饮、运动和能量汇总，包括身体资料计算的静息估算；开启附带记忆后也会发送选取的本地记忆，用于回答或修改。原始记录保存在本机。",
            "聊天工具" to "可以说‘记录今天早餐’或‘把昨天午餐的米饭改为100克’。助手会查询、准备新增或修改卡片；核对并确认后更新记录。餐次优先使用你说的名称，否则按时间推断，分类和热量只是估算。",
            "照片估算" to "照片生成的卡片可以直接编辑、确认。也可以通过聊天修改，核对后更新同一餐。确认前不计入统计。",
            "运动与进度" to "可以说‘刚快走30分钟’，明确已完成的运动会自动记录，可在聊天卡片编辑或删除。内置估算支持19–59岁有体重资料的散步、快走、慢跑；其他运动可提供设备的活动千卡。问‘最近七天进度’会查询本地完整统计。脂肪数字仅为参考差额÷7700的能量当量，不是实际减脂量。",
            "营养参考" to "按WS/T 578.1—2017第5.1条，蛋白质占总能量10%–15%、碳水50%–65%、脂肪20%–30%，换算为克数范围。总能量按静息×所选全天活动系数计算；旧资料未选活动时保留NASEM成人非活跃参考。长按外环查看范围，填满对应范围上沿；超过后橙色、超过上沿50%红色仅为显示提醒，不是医学危险界限，也不需要吃满。适用于19–78岁一般成年人，孕哺期或特殊疾病不能直接套用。",
            "糖和油" to "《中国居民膳食指南（2022）》建议添加糖每天不超过50克、最好25克以下，烹调油25–30克。碳水不等于糖，烹调油也不等于食物中的全部脂肪；当前没有单独统计添加糖和烹调油。",
            "活动水平" to "久坐少动1.2×；轻度活动1.375×（每周轻运动1–3天）；中等活动1.55×（每周中等运动3–5天）；高度活动1.725×（每周较高强度运动6–7天）。参考ACE的常见健身估算系数，结合静息公式估算，不是统一医学标准；运动频率只是选择提示，还要结合平时走动与劳动量。默认从保存当天起生效；身体资料→单日活动或聊天可只改某一天。记录运动用于回顾，不再次叠加到全天消耗。资料缺失不推算。参考：ACE《Resting Metabolic Rate: Best Ways to Measure It—And Raise It, Too》。",
            "静息消耗" to "Mifflin–St Jeor：10×体重kg + 6.25×身高cm − 5×年龄；男性+5，女性−161。仅19–78岁且提供计算参数时显示。它不是测量结果或每日饮食目标。",
            "隐式记忆" to "仅保存明确偏好和已确认餐次。可在设置的本地记忆管理中关闭、忘记或选择是否附带记忆。",
            "月历与折线" to "空白记录显示 —，不会计为零。月历左右翻页，点击某天看餐次；周折线按自然周展示。")) {(title,body)->Paper {Text(title,style=MaterialTheme.typography.titleMedium);Text(body)}}
    }
}
