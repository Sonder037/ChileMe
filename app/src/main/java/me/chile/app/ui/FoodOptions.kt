package me.chile.app.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import me.chile.app.domain.*

@Composable fun FoodOptions(food: FoodItem,onChange: (FoodItem)->Unit) {
    Row {
        FoodOption("单位 · ${food.unit}",foodUnits) {onChange(food.copy(quantity=food.amount,grams=null,unit=it))}
        FoodOption("分类 · ${food.category}",foodCategories) {onChange(food.copy(category=it))}
    }
}

@Composable private fun FoodOption(label: String,options: List<String>,onPick: (String)->Unit) {
    var expanded by remember {mutableStateOf(false)}
    Box {
        TextButton(onClick={expanded=true}){Text(label)}
        DropdownMenu(expanded=expanded,onDismissRequest={expanded=false}) {
            options.forEach {value->DropdownMenuItem(text={Text(value)},onClick={expanded=false;onPick(value)})}
        }
    }
}
