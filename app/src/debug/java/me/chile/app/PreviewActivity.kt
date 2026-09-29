package me.chile.app

import android.os.Bundle
import androidx.compose.foundation.layout.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.chile.app.data.*
import me.chile.app.domain.*
import me.chile.app.ui.*

// Internal render fixture. It never opens the user's database or sends a request.
class PreviewActivity: ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Synthetic-only fixture can render above the keyguard; no unlock or real records.
        setShowWhenLocked(true)
        setTurnScreenOn(true)
        val longTitles=intent.getBooleanExtra("longTitles",false)
        val today=java.time.LocalDate.now()
        val chartProfile=Profile("male",30,175.0,70.0,today.minusMonths(2).toString(),"sedentary")
        val meals=(0..27).map {offset->
            val date=today.minusDays(offset.toLong())
            val delta=listOf(-850,-430,-160,120,560,900,210)[date.dayOfWeek.value-1]
            Meal(date=date.toString(),items=listOf(FoodItem("演示餐饮",null,chartProfile.totalEnergy()!!+delta)))
        }
        val fixture="chile-render-test.db"
        if(savedInstanceState==null) {
        deleteDatabase(fixture)
        LocalStore(this,fixture).use {db->
            db.put("onboarded",(!intent.getBooleanExtra("onboarding",false)).toString());db.put("config",ModelConfig(offline=true).json().toString())
            if(intent.getBooleanExtra("onboarding",false))return@use
            val meal=Meal(title=if(longTitles)"周末和朋友一起吃的家常午餐" else "午餐",time="12:15",items=listOf(if(longTitles)FoodItem("香煎鸡胸肉配西兰花和番茄",10000.0,10000,"肉蛋鱼") else FoodItem("糙米饭",150.0,174,"主食",nutrients=Nutrients(3.9,36.0,1.4)),FoodItem("西兰花",100.0,35,"蔬菜",nutrients=Nutrients(2.8,4.0,.4)),FoodItem("纯牛奶",null,155,"奶类",250.0,"ml",Nutrients(8.0,12.0,8.0))))
            db.saveProfile(Profile("male",30,175.0,70.0,activity=if(intent.getBooleanExtra("records",false))"sedentary" else null))
            db.saveMeal(meal,false)
            val exercise=Exercise(date=java.time.LocalDate.now().toString(),time="17:30",name=if(longTitles)"晚饭后沿着公园湖边快走和拉伸" else "快走",minutes=30,activeKcal=140,source="MET 估算 · 净活动消耗",messageId="fixture-exercise")
            db.saveExercise(exercise)
            db.addMessage(ChatMessage(role="user",text="刚快走了30分钟"))
            db.addMessage(ChatMessage(role="assistant",text="记下了，净活动消耗约 **140 kcal**。",exerciseId=exercise.id))
            db.addMessage(ChatMessage(role="user",text="这是我的午餐，帮我记一下。"))
            db.put("card:${meal.id}",meal.json().toString())
            db.addMessage(ChatMessage(role="assistant",text="这份约 **${meal.kcal} kcal**，份量有偏差可以改。",mealId=meal.id))
            if(intent.getBooleanExtra("changes",false)) {
                val t=SettingsTools(db.profiles(),emptyMap(),db.editableSettings(),emptyList(),emptyList(),java.time.LocalDateTime.now())
                t.execute("query_settings","{}")
                t.execute("update_profile","""{"weight":65,"activity":"moderate"}""")
                val msg=ChatMessage(role="assistant",text="核对修改后，点确认即可。")
                db.put("change:${msg.id}",t.proposal!!.json().toString());db.addMessage(msg)
            }
            if(intent.getBooleanExtra("choices",false)) {
                db.addMessage(ChatMessage(role="user",text="这是刚拍的，帮我记一下"))
                db.addMessage(ChatMessage(role="assistant",text="这张照片是一顿新餐，还是刚才那餐的剩余？",options=listOf("新增一餐","上一餐的剩饭","还没吃，只估算热量")))
            }
        }
        }
        val vm=ViewModelProvider(this,viewModelFactory {initializer {AppViewModel(application,SavedStateHandle(),fixture)}})[AppViewModel::class.java]
        setContent {ChileTheme {
            androidx.compose.material3.Surface(modifier=Modifier.fillMaxSize().softBackdrop(),color=androidx.compose.ui.graphics.Color.Transparent) {
                if(intent.getBooleanExtra("profile",false))Box(Modifier.fillMaxSize().padding(16.dp)) {ProfileScreen(vm) {}} else if(intent.getBooleanExtra("records",false))Box(Modifier.fillMaxSize().padding(16.dp)) {RecordsScreen(vm) {}} else if(intent.getBooleanExtra("monthly",false))Box(Modifier.fillMaxSize().safeDrawingPadding().padding(16.dp)) {TrendsScreen(meals,true,profiles=listOf(chartProfile))} else ChileApp(vm)
            }
        }}
    }
}
