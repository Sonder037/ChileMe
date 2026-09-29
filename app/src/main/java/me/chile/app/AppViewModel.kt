package me.chile.app

import android.app.Application
import android.net.Uri
import android.util.Base64
import androidx.compose.runtime.*
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.*
import me.chile.app.data.*
import me.chile.app.domain.*
import java.util.concurrent.Executors

data class AppData(val profiles: List<Profile> = emptyList(), val meals: List<Meal> = emptyList(),
    val mealRecords: List<Meal> = emptyList(),val mealCount: Int=0,
    val messages: List<ChatMessage> = emptyList(),val memories: List<Memory> = emptyList(),
    val config: ModelConfig = ModelConfig(),val hasKey: Boolean = false,val onboarded: Boolean = false,
    val memoryEnabled: Boolean = true,val mealDraft: Meal? = null, val cards: Map<String,Meal> = emptyMap(), val cardStates: Map<String,String> = emptyMap(), val pendingPhoto: String? = null,val exercises: List<Exercise> = emptyList(),val includeMemory: Boolean = false,val hasOlderMessages: Boolean=false,val activityOverrides: Map<String,String> = emptyMap(),val changes: Map<String,PendingChange> = emptyMap(),val changeStates: Map<String,String> = emptyMap())

class AppViewModel(app: Application,private val saved: SavedStateHandle, databaseName: String="chile.db",private val ai: AiClient=AiClient()): AndroidViewModel(app) {
    private val disk=Executors.newSingleThreadExecutor().asCoroutineDispatcher()
    private val store=LocalStore(app,databaseName)
    private val vault by lazy {KeyVault(app)}
    val photos=PhotoStore(app)
    var data by mutableStateOf(AppData()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var notice by mutableStateOf<String?>(null); private set
    var busy by mutableStateOf(false); private set
    var streamingText by mutableStateOf(""); private set
    var streamingMessageId by mutableStateOf<String?>(null); private set
    var saving by mutableStateOf(false); private set
    var draft by mutableStateOf(saved.get<String>("chatDraft") ?: ""); private set
    private var draftRevision=0L
    private var mealDraftRevision=0L
    private var cardRevision=0L
    private val cardEditVersions=mutableMapOf<String,Long>()
    var retryMessage by mutableStateOf<ChatMessage?>(null); private set
    val includeMemory get()=data.includeMemory
    private var requestJob: Job?=null
    private var messageFloor: Long?=null // Accessed only on the serialized disk dispatcher.
    var loadingOlder by mutableStateOf(false); private set
    var loadingOlderMeals by mutableStateOf(false); private set
    private var mealRecordLimit=12
    private var olderMealsRequested=false
    private fun snapshot(): AppData {
        val cards=store.cards()
        val changes=store.pendingChanges()
        val states=store.confirmationStates()
        val page=store.messagePage(floor=messageFloor)
        messageFloor=page.firstSeq
        return AppData(
            profiles=store.profiles(),meals=store.meals(),messages=page.messages,memories=store.memories(),
            mealRecords=store.mealRecords(mealRecordLimit,if(olderMealsRequested)null else java.time.LocalDate.now().minusDays(2).toString()),mealCount=store.mealCount(),
            config=store.get("config")?.let(::configJson)?:ModelConfig(),hasKey=vault.hasKey(),
            onboarded=store.get("onboarded")=="true",memoryEnabled=store.get("memory")!="false",
            mealDraft=store.get("mealDraft")?.takeIf {it.isNotBlank()}?.let(::mealJson),cards=cards,
            cardStates=cards.keys.associateWith {states["cardState:$it"]?:"draft"},
            pendingPhoto=store.get("pendingPhoto")?.takeIf {it.isNotBlank()},exercises=store.exercises(),
            includeMemory=store.get("includeMemory")=="true",hasOlderMessages=page.hasOlder,
            activityOverrides=store.activityOverrides(),changes=changes,
            changeStates=changes.keys.associateWith {states["changeState:$it"]?:"pending"})
    }
    private suspend fun refresh() {
        val mealVersion=mealDraftRevision;val cardsVersion=cardRevision
        val snapshot=withContext(disk) { snapshot() }
        val newerCards=data.cards.filter {(id,_)->(cardEditVersions[id]?:0)>cardsVersion && snapshot.cardStates[id]=="draft"}
        data=snapshot.copy(mealDraft=if(mealVersion==mealDraftRevision)snapshot.mealDraft else data.mealDraft,cards=snapshot.cards+newerCards)
    }
    init { viewModelScope.launch {
        try { withContext(disk) {
            if(store.get("flashMigration")==null) {
                val config=store.get("config")?.let(::configJson)?:ModelConfig()
                if(endpoint(config.baseUrl).host=="api.deepseek.com") {
                    val aliases=setOf("", "deepseek-chat", "deepseek-v4-flash", "deepseek-v4-flash-vision-exp")
                    store.put("config",config.copy(chatModel=if(config.chatModel in aliases) "deepseek-flash" else config.chatModel,visionModel=if(config.visionModel in aliases) "deepseek-flash" else config.visionModel).json().toString())
                };store.put("flashMigration","true")
            }
        };refresh()
            val retryId=withContext(disk){store.get("retryMessage")}
            retryMessage=data.messages.firstOrNull {it.id==retryId}
            if(saved.get<String>("chatDraft")==null) draft=withContext(disk) {store.get("chatDraft") ?: ""}
        }
        catch(e: Exception) {error="无法读取本地记录：${e.javaClass.simpleName}，请勿清除应用数据"}
        finally {loading=false}
    } }
    private fun write(block: ()->Unit) { viewModelScope.launch {
        try {withContext(disk){block()};refresh()} catch(e: Exception){error=e.message ?: "保存失败，请重试"}
    } }
    fun clearError(){error=null}
    fun loadOlderMeals() {
        if(loading || loadingOlderMeals || data.mealRecords.size>=data.mealCount)return
        loadingOlderMeals=true
        val nextLimit=data.mealRecords.size+12
        viewModelScope.launch {try {
            val page=withContext(disk) {
                mealRecordLimit=nextLimit
                olderMealsRequested=true
                store.mealRecords(mealRecordLimit) to store.mealCount()
            }
            data=data.copy(mealRecords=page.first,mealCount=page.second)
        }catch(e: Exception){error="较早饮食读取失败，请重试"}finally{loadingOlderMeals=false}}
    }
    fun loadOlderMessages(onLoaded: ()->Unit={}) {
        if(loading || loadingOlder || !data.hasOlderMessages)return
        loadingOlder=true
        viewModelScope.launch {try {
            withContext(disk) {store.messagePage(before=messageFloor,older=true).firstSeq?.let {messageFloor=it}}
            refresh();onLoaded()
        }catch(e: Exception){error="较早消息读取失败，请重试"}finally{loadingOlder=false}}
    }
    fun updateDraft(text: String){draftRevision++;draft=text.take(4000);saved["chatDraft"]=draft;val value=draft;viewModelScope.launch {try {withContext(disk){store.put("chatDraft",value)}}catch(e: Exception){error="输入草稿保存失败"}}}
    fun saveProfile(profile: Profile,onDone: ()->Unit) {
        if(!profile.valid()){error="请检查性别、年龄、身高与体重";return}
        if(saving)return
        saving=true
        val datedProfile=profile.copy(date=java.time.LocalDate.now().toString())
        viewModelScope.launch {try {withContext(disk){store.saveProfile(datedProfile)};refresh();onDone()}catch(e: Exception){error="身体数据保存失败"}finally{saving=false}}
    }
    fun saveDailyActivity(date: String,level: String?){if(!busy&&!saving)write {store.setDailyActivity(date,level)}}
    fun resolveChange(id: String,confirm: Boolean) {
        if(busy||saving)return
        saving=true
        viewModelScope.launch {try {
            withContext(disk){vault.rollbackOnFailure {store.transaction {
                require(store.get("changeState:$id")==null){"修改已处理"}
                val change=store.get("change:$id")?.let(::changeJson)?:error("修改卡片不存在")
                if(confirm) {
                    if(change.kind=="settings") {
                        val next=editableSettingsJson(checkNotNull(change.after))
                        if(next.config.baseUrl.trimEnd('/')!=store.editableSettings().config.baseUrl.trimEnd('/'))vault.clear()
                    }
                    store.applyChange(change)
                }
                store.put("changeState:$id",if(confirm)"confirmed" else "cancelled")
            }}};refresh()
        }catch(e: Exception){error=e.message?:"修改失败，原数据已保留"}finally{saving=false}}
    }
    fun finishOnboarding(){write{store.put("onboarded","true")}}
    fun saveConfig(config: ModelConfig,key: String,onDone: ()->Unit) {
        if(saving || busy)return
        saving=true
        viewModelScope.launch {try {
            endpoint(config.baseUrl);require(config.chatModel.isNotBlank()){ "请填写聊天模型名称" }
            withContext(disk) {vault.rollbackOnFailure {
                // Never reuse a secret silently with a different destination.
                if(key.isNotBlank())vault.save(key.trim())
                else if(data.config.baseUrl.trimEnd('/')!=config.baseUrl.trimEnd('/')) vault.clear()
                store.put("config",config.json().toString())
            }};refresh();onDone()
        } catch(e: Exception){error=e.message ?: "模型配置保存失败"} finally {saving=false} }
    }
    fun clearKey(){if(!busy)write{vault.clear()}}
    fun setIncludeMemory(enabled: Boolean){write{store.put("includeMemory",enabled.toString())}}
    fun toggleMemory(){val enabled=!data.memoryEnabled;write{store.put("memory",enabled.toString())}}
    fun updateMemory(memory: Memory){if(memory.text.isNotBlank() && memory.text.length<=1000)write {require(store.memories().any {it.key==memory.key}){ "记忆已删除" };store.saveMemory(memory)}}
    fun forget(key: String){write{store.forget(key)}}
    fun startMeal(){if(data.mealDraft==null)write{store.put("mealDraft",Meal(items=listOf(FoodItem("",null,0))).json().toString())}}
    fun editMeal(meal: Meal){write{store.put("mealDraft",meal.json().toString())}}
    fun updateMeal(meal: Meal){mealDraftRevision++;data=data.copy(mealDraft=meal);viewModelScope.launch {try{withContext(disk){store.put("mealDraft",meal.json().toString())}}catch(e:Exception){error="草稿保存失败"}}}
    fun discardMeal(){write{val old=store.get("mealDraft")?.takeIf {it.isNotBlank()}?.let(::mealJson)?.photo;store.put("mealDraft","");releasePhoto(old)}}
    fun saveExercise(exercise: Exercise){if(!busy && !saving)write{store.saveExercise(exercise)}}
    fun deleteExercise(exercise: Exercise){if(!busy && !saving)write{store.deleteExercise(exercise.id)}}
    fun deleteMeal(meal: Meal){write{store.deleteMeal(meal.id)}}
    fun confirmMeal(onDone: ()->Unit){
        val meal=data.mealDraft?:return
        if(!meal.valid()){error="请填写食物名称、有效日期及热量；最多30项，总量不超过30000 kcal";return}
        if(saving||busy)return
        saving=true
        viewModelScope.launch {try {withContext(disk){store.saveMeal(meal,data.memoryEnabled)};refresh();onDone()}catch(e: Exception){error=if(e is IllegalArgumentException)e.message?:"餐次数据无效，草稿已保留" else "餐次保存失败，草稿已保留"}finally{saving=false}}
    }
    fun importPhoto(uri: Uri){if(busy)return
        streamingText="";busy=true;requestJob=viewModelScope.launch {try {
            withContext(disk){
                val previous=store.get("mealDraft")?.takeIf {it.isNotBlank()}?.let(::mealJson)
                val name=photos.import(uri)
                val meal=(previous?:Meal(items=listOf(FoodItem("",null,0)))).copy(photo=name)
                try {store.put("mealDraft",meal.json().toString())} catch(e: Exception){releasePhoto(name);throw e}
                releasePhoto(previous?.photo)
            };refresh()
        } catch(e: CancellationException){refreshAfterCancellation();throw e}catch(e: Exception){error=e.message ?: "无法打开图片"}finally{busy=false}}
    }
    fun recognize(){val meal=data.mealDraft?:return;val photo=meal.photo?:return
        request { config,key ->
            val image=withContext(disk){Base64.encodeToString(photos.file(photo).readBytes(),Base64.NO_WRAP)}
            val result=ai.recognize(config,key,image)
            if(result.items.isEmpty()){notice=result.summary;return@request}
            val items=result.items
            withContext(disk){store.put("mealDraft",meal.copy(items=items,source="AI 估算 · 已校正").json().toString())};refresh()
        }
    }
    private fun request(block: suspend (ModelConfig,String)->Unit){
        if(busy)return
        val config=data.config
        if(config.offline){error="离线模式下不会发送请求，请在模型设置中保存 API Key 配置";return}
        busy=true;error=null;streamingText=""
        val initialDraftRevision=draftRevision
        requestJob=viewModelScope.launch {try {
            val key=withContext(disk){vault.read()}?:error("请先填写 API Key")
            block(config,key)
        }catch(e: CancellationException){refreshAfterCancellation(initialDraftRevision);notice="已停止请求，已发送的数据无法撤回";throw e}
        catch(e: Exception){error=if(e is org.json.JSONException) "模型响应格式不兼容，请检查模型配置" else e.message?:"请求失败，请重试"}
        finally{busy=false} }
    }
    // SQLite/file work can finish after coroutine cancellation. Reconcile committed
    // state before accepting another action, without replacing a newer input draft.
    private suspend fun refreshAfterCancellation(draftVersion: Long?=null) {
        if(!viewModelScope.isActive)return
        withContext(NonCancellable) {
            try {
                refresh()
                val (retryId,storedDraft)=withContext(disk){store.get("retryMessage") to store.get("chatDraft").orEmpty()}
                retryMessage=data.messages.firstOrNull {it.id==retryId}
                if(retryId.isNullOrBlank())streamingText=""
                if(draftVersion!=null && draftRevision==draftVersion){draft=storedDraft;saved["chatDraft"]=storedDraft}
            }catch(e: Exception){error="本地状态同步失败，请重新打开应用；已保存的数据会保留"}
        }
    }
    fun cancelRequest(){requestJob?.cancel()}
    fun attachPhoto(uri: Uri){if(busy)return;streamingText="";busy=true;requestJob=viewModelScope.launch {try {
        withContext(disk){
            val old=store.get("pendingPhoto");val name=photos.import(uri)
            try {store.put("pendingPhoto",name)} catch(e: Exception){releasePhoto(name);throw e}
            releasePhoto(old)
        };refresh()
    }catch(e: CancellationException){refreshAfterCancellation();throw e}catch(e: Exception){error="无法读取照片，请重新选择"}finally{busy=false}}}
    fun removePhoto(){if(!busy)write{val old=store.get("pendingPhoto");store.put("pendingPhoto","");releasePhoto(old)}}
    // Runs on the serialized disk dispatcher, after the reference change is saved.
    private fun releasePhoto(name: String?) {
        if(name.isNullOrBlank())return
        runCatching {
            if(store.get("pendingPhoto")!=name && store.get("mealDraft")?.takeIf {it.isNotBlank()}?.let(::mealJson)?.photo!=name &&
                !store.hasMessagePhoto(name) && store.meals().none {it.photo==name} && store.cards().values.none {it.photo==name})photos.file(name).delete()
        } // A failed reference read keeps the photo; cleanup must not risk a saved record.
    }
    fun updateCard(meal: Meal){cardEditVersions[meal.id]=++cardRevision;data=data.copy(cards=data.cards+(meal.id to meal));viewModelScope.launch {try {withContext(disk){store.put("card:${meal.id}",meal.json().toString())}}catch(e: Exception){error="编辑保存失败"}}}
    fun confirmCard(meal: Meal){if(!meal.valid()||saving)return;saving=true;viewModelScope.launch {try {
        withContext(disk){store.confirmProposal(meal,data.memoryEnabled)};refresh()
    }catch(e: Exception){error=e.message?:"保存失败，识别结果已保留"}finally{saving=false}}}
    fun manualCard(message: ChatMessage){
        if(saving||busy||data.cards.containsKey(message.id))return
        saving=true
        val meal=Meal(id=message.id,photo=message.photo,items=listOf(FoodItem("",null,0)))
        viewModelScope.launch {try {
            withContext(disk){store.transaction {
                if(store.get("card:${meal.id}")==null) {
                    store.put("card:${meal.id}",meal.json().toString())
                    store.addMessage(ChatMessage(role="assistant",text="可以手动补充这一餐。",mealId=meal.id))
                }
                if(store.get("retryMessage")==message.id)store.put("retryMessage","")
            }}
            if(retryMessage?.id==message.id)retryMessage=null
            refresh()
        }catch(e: Exception){error="创建草稿失败，请重试"}finally{saving=false}}
    }
    fun answerChoice(message: ChatMessage,option: String) {
        if(busy || loading || saving || data.messages.lastOrNull()?.id!=message.id || option !in message.options)return
        send(answer="${message.text}\n$option")
    }
    fun send(retry: Boolean=false,answer: String?=null){
        if(loading)return
        val text=answer?:draft.trim();if(!retry && text.isEmpty() && data.pendingPhoto==null)return
        val sentRevision=draftRevision
        val message=if(retry) retryMessage?:return else ChatMessage(role="user",text=text,photo=if(answer==null)data.pendingPhoto else null)
        val memories=if(includeMemory)data.memories else emptyList()
        request {config,key ->
            val responseId=java.util.UUID.randomUUID().toString()
            streamingMessageId=responseId
            if(retry && withContext(disk){store.get("retryMessage")}!=message.id){retryMessage=null;refresh();return@request}
            if(!retry) {
                val remainingDraft=if(answer==null && draftRevision==sentRevision)"" else draft
                withContext(disk) {store.transaction {
                    store.addMessage(message);store.put("retryMessage",message.id);if(answer==null)store.put("pendingPhoto","");store.put("chatDraft",remainingDraft)
                    if(data.memoryEnabled && (text.startsWith("我不吃")||text.startsWith("我喜欢吃")) && text.length<=80)
                        store.saveMemory(Memory("preference:$text",text,message.id))
                }};if(answer==null && draftRevision==sentRevision){draft="";saved["chatDraft"]=""};refresh()
            }
            retryMessage=message
            val context=buildChatContext(data.messages,data.meals,data.cards,data.cardStates,message)
            val selectedImages=context.images
            val history=context.messages
            val images=withContext(disk) {
                selectedImages.associate {it.id to Base64.encodeToString(photos.file(it.photo!!).readBytes(),Base64.NO_WRAP)}
            }
            val drafts=data.cards.values.filter {data.cardStates[it.id]=="draft"}
            val reply=ai.agentChat(config,key,history,memories,data.meals,drafts,images,data.exercises,data.profiles,data.activityOverrides,EditableSettings(data.config,data.memoryEnabled,data.includeMemory),data.memories) {text->
                // Transport callbacks run on OkHttp's worker, Compose state stays on Main.
                viewModelScope.launch(Dispatchers.Main.immediate) {if(busy && requestJob?.isActive==true)streamingText=text}
            }
            val proposal=reply.proposal?.let {if(it.photo==null)it.copy(photo=message.photo?:selectedImages.lastOrNull()?.photo) else it}
            withContext(disk){store.transaction {
                proposal?.let {store.stageProposal(it,reply.baseline)}
                reply.exercise?.let {store.saveExercise(it)}
                val assistant=ChatMessage(id=responseId,role="assistant",text=reply.text,mealId=proposal?.id,exerciseId=reply.exercise?.id,options=reply.options)
                reply.change?.let {store.put("change:${assistant.id}",it.json().toString())}
                store.addMessage(assistant);store.put("retryMessage","")
            }}
            retryMessage=null;refresh();streamingText=""
        }
    }
    override fun onCleared(){
        super.onCleared()
        // Close after queued disk work, without blocking the UI thread.
        disk.executor.execute {store.close()}
        disk.close()
    }
}
