package me.chile.app.ui

import androidx.compose.foundation.*
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import me.chile.app.domain.*

@Composable fun SoftField(value: String,onChange: (String)->Unit,label: String,modifier: Modifier=Modifier,
    keyboard: KeyboardType=KeyboardType.Text,secret: Boolean=false,minLines: Int=1) {
    var focused by remember {mutableStateOf(false)}
    val colors=MaterialTheme.colorScheme
    val fieldColor by animateColorAsState(if(focused)colors.primaryContainer else colors.background,tween(180),label="输入聚焦")
    BasicTextField(value,onChange,modifier.fillMaxWidth().onFocusChanged {focused=it.isFocused}
        .semantics {contentDescription=label}.background(fieldColor,RoundedCornerShape(16.dp))
        .border(if(focused).5.dp else .25.dp,if(focused)colors.primary.copy(alpha=.3f) else colors.outlineVariant.copy(alpha=.15f),RoundedCornerShape(16.dp))
        .padding(horizontal=14.dp,vertical=11.dp),singleLine=minLines==1,minLines=minLines,maxLines=if(minLines==1)1 else 12,
        keyboardOptions=KeyboardOptions(keyboardType=if(secret)KeyboardType.Password else keyboard),visualTransformation=if(secret)PasswordVisualTransformation() else VisualTransformation.None,
        cursorBrush=SolidColor(colors.primary),textStyle=MaterialTheme.typography.bodyLarge.copy(color=colors.onSurface),
        decorationBox={inner->Column(verticalArrangement=Arrangement.spacedBy(5.dp)) {
            Text(label,style=MaterialTheme.typography.labelSmall,color=colors.onSurfaceVariant)
            inner()
        }})
}

@Composable fun FoodGrid(foods: List<FoodItem>,enabled: Boolean=true,onChange: (List<FoodItem>)->Unit) {
    var selected by rememberSaveable {mutableStateOf<Int?>(null)}
    val feedback=selectionFeedback()
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val columns=if(maxWidth<260.dp || LocalDensity.current.fontScale>1.4f)1 else 2
        Column(verticalArrangement=Arrangement.spacedBy(10.dp)) {
            foods.indices.toList().chunked(columns).forEach {indices->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                    indices.forEach {index->
                        val food=foods[index]
                        val interaction=remember {androidx.compose.foundation.interaction.MutableInteractionSource()}
                        Card(onClick={feedback();selected=index},enabled=enabled,interactionSource=interaction,modifier=Modifier.weight(1f).pressMotion(interaction,.98f),shape=RoundedCornerShape(20.dp),border=paperEdge(),
                            colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),elevation=CardDefaults.cardElevation(defaultElevation=1.dp)) {
                            Column(Modifier.padding(14.dp),verticalArrangement=Arrangement.spacedBy(9.dp)) {
                                Text(food.category,style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                Text(food.name.ifBlank {"新食物"},style=MaterialTheme.typography.titleSmall,maxLines=2,minLines=2,overflow=TextOverflow.Ellipsis)
                                Text(food.amount?.let {food.portion()}?:"—",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                                Row(verticalAlignment=Alignment.Bottom,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                                    Text(food.kcal.takeIf {it>=0}?.toString()?:"—",style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.SemiBold)
                                    Text("kcal",style=MaterialTheme.typography.labelSmall,modifier=Modifier.padding(bottom=3.dp))
                                }
                            }
                        }
                    }
                    if(indices.size<columns)Spacer(Modifier.weight(1f))
                }
            }
            if(foods.size<30)TextButton(onClick={selected=foods.size},enabled=enabled) {
                GlyphIcon(Glyph.PLUS,Modifier.size(18.dp));Spacer(Modifier.width(8.dp));Text("添加食物")
            }
        }
    }
    selected?.let {index->
        val food=foods.getOrNull(index)?:FoodItem("",null,0)
        key(index) {FoodEditDialog(food,onDismiss={selected=null},onSave={updated->onChange(foods.toMutableList().apply {if(index<size)set(index,updated) else add(updated)});selected=null},onDelete={if(index<foods.size)onChange(foods.filterIndexed {i,_->i!=index});selected=null})}
    }
}

@Composable private fun FoodEditDialog(initial: FoodItem,onDismiss: ()->Unit,onSave: (FoodItem)->Unit,onDelete: ()->Unit) {
    var name by rememberSaveable {mutableStateOf(initial.name)}
    var amount by rememberSaveable {mutableStateOf(initial.amount?.let {java.math.BigDecimal.valueOf(it).stripTrailingZeros().toPlainString()}.orEmpty())}
    var kcal by rememberSaveable {mutableStateOf(initial.kcal.toString())}
    var unit by rememberSaveable {mutableStateOf(initial.unit)}
    var category by rememberSaveable {mutableStateOf(initial.category)}
    var protein by rememberSaveable {mutableStateOf(initial.nutrients.proteinG?.toString().orEmpty())}
    var carbs by rememberSaveable {mutableStateOf(initial.nutrients.carbsG?.toString().orEmpty())}
    var fat by rememberSaveable {mutableStateOf(initial.nutrients.fatG?.toString().orEmpty())}
    fun nutrient(text: String): Double?=if(text.isBlank())null else text.toDoubleOrNull()?:-1.0
    val updated=FoodItem(name.trim(),null,kcal.toIntOrNull()?:-1,category,if(amount.isBlank())null else amount.toDoubleOrNull()?:-1.0,unit,Nutrients(nutrient(protein),nutrient(carbs),nutrient(fat)))
    Dialog(onDismissRequest=onDismiss,properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Box(Modifier.fillMaxSize().safeDrawingPadding().imePadding().padding(20.dp),contentAlignment=Alignment.Center) {
            Surface(Modifier.widthIn(max=440.dp).fillMaxWidth(),shape=RoundedCornerShape(28.dp),color=MaterialTheme.colorScheme.surface) {
                Column(Modifier.verticalScroll(rememberScrollState()).padding(20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        Text("编辑食物",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f))
                        IconAction(Glyph.CLOSE,"关闭食物编辑",onClick=onDismiss)
                    }
                    SoftField(name,{name=it.take(100)},"名称")
                    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {
                        SoftField(amount,{amount=it},"份量 · $unit",Modifier.weight(1f),KeyboardType.Decimal)
                        SoftField(kcal,{kcal=it},"热量 · kcal",Modifier.weight(1f),KeyboardType.Number)
                    }
                    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        SoftField(protein,{protein=it},"蛋白质 g",Modifier.weight(1f),KeyboardType.Decimal)
                        SoftField(carbs,{carbs=it},"碳水 g",Modifier.weight(1f),KeyboardType.Decimal)
                        SoftField(fat,{fat=it},"脂肪 g",Modifier.weight(1f),KeyboardType.Decimal)
                    }
                    FoodOptions(updated){unit=it.unit;category=it.category}
                    if(name.isNotBlank() && !updated.valid())Text("请核对份量、热量与营养素",color=MaterialTheme.colorScheme.error,style=MaterialTheme.typography.bodySmall)
                    Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                        IconAction(Glyph.DELETE,"移除食物",onClick=onDelete)
                        Spacer(Modifier.weight(1f))
                        PrimaryButton(onClick={onSave(updated)},enabled=updated.valid(),shape=RoundedCornerShape(16.dp)){Text("完成")}
                    }
                }
            }
        }
    }
}
