package me.chile.app.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import me.chile.app.domain.*
import java.time.LocalDate

internal fun requireRecordedDate(value: String) {
    val date=LocalDate.parse(value)
    require(date>=LocalDate.of(1900,1,1) && date<=LocalDate.now()) {"记录日期需在1900年至今天之间，尚未发生的饮食或运动不能计入"}
}

// All calls run on the ViewModel's IO dispatcher. SQLite is the local source of truth.
class LocalStore(context: Context, name: String = "chile.db") : SQLiteOpenHelper(context, name, null, 5) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE exercises (id TEXT PRIMARY KEY, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE kv (key TEXT PRIMARY KEY, value TEXT NOT NULL)")
        db.execSQL("CREATE TABLE profiles (id INTEGER PRIMARY KEY AUTOINCREMENT, body TEXT NOT NULL)")
        db.execSQL("CREATE TABLE meals (id TEXT PRIMARY KEY, body TEXT NOT NULL, date TEXT NOT NULL, time TEXT NOT NULL)")
        db.execSQL("CREATE INDEX meals_date_time ON meals(date DESC,time DESC,id DESC)")
        db.execSQL("CREATE TABLE messages (seq INTEGER PRIMARY KEY AUTOINCREMENT, id TEXT UNIQUE NOT NULL, role TEXT NOT NULL, body TEXT NOT NULL, photo TEXT, mealId TEXT, created_at TEXT)")
        db.execSQL("CREATE INDEX messages_created_at ON messages(created_at)")
        db.execSQL("CREATE TABLE memories (key TEXT PRIMARY KEY, body TEXT NOT NULL, source TEXT NOT NULL)")
    }
    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if(oldVersion < 2) { db.execSQL("ALTER TABLE messages ADD COLUMN photo TEXT"); db.execSQL("ALTER TABLE messages ADD COLUMN mealId TEXT") }
        if(oldVersion<3)db.execSQL("CREATE TABLE exercises (id TEXT PRIMARY KEY, body TEXT NOT NULL)")
        if(oldVersion<4) {
            db.execSQL("ALTER TABLE messages ADD COLUMN created_at TEXT")
            db.execSQL("UPDATE messages SET created_at=(SELECT value FROM kv WHERE key='messageTime:' || messages.id)")
            db.execSQL("CREATE INDEX messages_created_at ON messages(created_at)")
        }
        if(oldVersion<5) {
            db.execSQL("ALTER TABLE meals ADD COLUMN date TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE meals ADD COLUMN time TEXT NOT NULL DEFAULT ''")
            db.rawQuery("SELECT id,body FROM meals",null).use {cursor->
                while(cursor.moveToNext()) {
                    val meal=mealJson(cursor.getString(1))
                    db.update("meals",ContentValues().apply {put("date",meal.date);put("time",meal.time.orEmpty())},"id=?",arrayOf(cursor.getString(0)))
                }
            }
            db.execSQL("CREATE INDEX meals_date_time ON meals(date DESC,time DESC,id DESC)")
        }
    }
    fun get(key: String): String? = readableDatabase.rawQuery("SELECT value FROM kv WHERE key=?",arrayOf(key)).use { if(it.moveToFirst()) it.getString(0) else null }
    fun put(key: String, value: String) { writableDatabase.insertWithOnConflict("kv",null,ContentValues().apply { put("key",key);put("value",value) },SQLiteDatabase.CONFLICT_REPLACE) }
    fun profiles(): List<Profile> = readableDatabase.rawQuery("SELECT body FROM profiles ORDER BY id DESC",null).use { c -> buildList { while(c.moveToNext()) add(profileJson(c.getString(0))) } }
    fun saveProfile(p: Profile) { require(p.valid()); writableDatabase.insertOrThrow("profiles",null,ContentValues().apply { put("body",p.json().toString()) }) }
    fun meals(): List<Meal> = readableDatabase.rawQuery("SELECT body FROM meals ORDER BY rowid DESC",null).use { c -> buildList { while(c.moveToNext()) add(mealJson(c.getString(0))) } }
    fun mealRecords(limit: Int, since: String? = null): List<Meal> {
        require(limit>0)
        val where=if(since==null)"" else "WHERE date >= ?"
        val args=if(since==null)arrayOf(limit.toString()) else arrayOf(since,limit.toString())
        return readableDatabase.rawQuery("SELECT body FROM meals $where ORDER BY date DESC,time DESC,id DESC LIMIT ?",args).use {c->
            buildList {while(c.moveToNext())add(mealJson(c.getString(0)))}
        }
    }
    fun mealCount(): Int = readableDatabase.rawQuery("SELECT COUNT(*) FROM meals",null).use {it.moveToFirst();it.getInt(0)}
    fun saveMeal(meal: Meal, memory: Boolean, clearDraft: Boolean=true) {
        require(meal.valid())
        requireRecordedDate(meal.date)
        val db=writableDatabase; db.beginTransaction()
        try {
            db.insertWithOnConflict("meals",null,ContentValues().apply { put("id",meal.id);put("body",meal.json().toString());put("date",meal.date);put("time",meal.time.orEmpty()) },SQLiteDatabase.CONFLICT_REPLACE)
            db.delete("memories","source=?",arrayOf(meal.id))
            if(memory) saveMemory(Memory("meal:${meal.id}","${meal.date} ${meal.title}：${meal.kcal} kcal",meal.id))
            if(get("card:${meal.id}")!=null) { put("card:${meal.id}",meal.json().toString());put("cardState:${meal.id}","confirmed") }
            cards().values.filter {it.replacesId==meal.id && get("cardState:${it.id}")=="confirmed"}.forEach {card->put("card:${card.id}",meal.copy(id=card.id,replacesId=meal.id).json().toString()) }; if(clearDraft)put("mealDraft",""); db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun stageProposal(meal: Meal,baseline: Meal?) {
        require(meal.valid())
        if(baseline?.id==meal.id) {
            require(get("cardState:${meal.id}")==null && meals().none {it.id==meal.id} && cards()[meal.id]==baseline){"卡片已变化，请重新查询后修改"}
            require(meal.replacesId==baseline.replacesId && meal.replacesDraftId==baseline.replacesDraftId){"修改目标无效"}
        }else if(meal.replacesId!=null || meal.replacesDraftId!=null) {
            require(baseline?.id==(meal.replacesId?:meal.replacesDraftId)){"修改目标无效"}
            put("cardBase:${meal.id}",baseline!!.json().toString())
        }
        put("card:${meal.id}",meal.json().toString())
    }
    fun confirmProposal(proposal: Meal,memory: Boolean) = transaction {
        val meal=proposal.copy(source=if(proposal.source.startsWith("饭前估算"))"照片估算 · 用户确认" else proposal.source.replace("待确认","用户确认"))
        if(get("cardState:${meal.id}")!=null)return@transaction
        require(get("card:${proposal.id}")?.let(::mealJson)==proposal){"卡片已更新，请核对最新内容后再确认"}
        val draftTarget=meal.replacesDraftId
        if(draftTarget!=null) {
            require(get("cardState:$draftTarget")==null && meals().none {it.id==draftTarget}){"原估算已经确认或替换，请重新查询"}
            val base=get("cardBase:${meal.id}")?:error("缺少饭前估算")
            require(cards()[draftTarget]==mealJson(base)){"饭前估算已修改，请重新对比"}
            saveMeal(meal.copy(replacesDraftId=null),memory,false)
            put("cardState:$draftTarget","superseded")
            put("card:${meal.id}",meal.json().toString());put("cardState:${meal.id}","confirmed")
            return@transaction
        }
        val target=meal.replacesId
        if(target!=null) {
            val current=meals().firstOrNull{it.id==target}?:error("原记录已删除，不能覆盖")
            val base=get("cardBase:${meal.id}")?:error("缺少原记录，请重新提出修改")
            require(current==mealJson(base)){"原记录已发生变化，请重新提出修改，避免覆盖新内容"}
            saveMeal(meal.copy(id=target,replacesId=null),memory,false)
            put("card:${meal.id}",meal.json().toString());put("cardState:${meal.id}","confirmed")
        }else saveMeal(meal,memory,false)
    }
    fun deleteMeal(id: String) {
        val db=writableDatabase; db.beginTransaction()
        try { cards().values.filter{it.id==id || it.replacesId==id}.forEach{put("cardState:${it.id}","deleted")}; db.delete("meals","id=?",arrayOf(id));db.delete("memories","source=?",arrayOf(id));db.setTransactionSuccessful() } finally {db.endTransaction()}
    }
    fun messages(): List<ChatMessage> = readableDatabase.rawQuery("""
        SELECT m.id,m.role,m.body,m.photo,m.mealId,t.value,e.value,o.value FROM messages m
        LEFT JOIN kv t ON t.key='messageTime:' || m.id
        LEFT JOIN kv e ON e.key='messageExercise:' || m.id
        LEFT JOIN kv o ON o.key='messageOptions:' || m.id ORDER BY m.seq
    """.trimIndent(),null).use { c -> buildList { while(c.moveToNext()) add(ChatMessage(c.getString(0),c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),storedOptions(c.getString(7)))) } }
    fun hasMessagePhoto(name: String): Boolean = readableDatabase.rawQuery("SELECT 1 FROM messages WHERE photo=? LIMIT 1",arrayOf(name)).use {it.moveToFirst()}
    fun addMessage(m: ChatMessage) { if(m.options.isNotEmpty())put("messageOptions:${m.id}",org.json.JSONArray(m.options).toString()); m.exerciseId?.let {put("messageExercise:${m.id}",it)}; m.createdAt?.let {put("messageTime:${m.id}",it)}; writableDatabase.insertOrThrow("messages",null,ContentValues().apply {put("id",m.id);put("role",m.role);put("body",m.text);put("photo",m.photo);put("mealId",m.mealId);put("created_at",m.createdAt)}) }
    fun cards(): Map<String,Meal> = readableDatabase.rawQuery("SELECT value FROM kv WHERE key LIKE 'card:%'",null).use { c -> buildMap { while(c.moveToNext()) { val meal=mealJson(c.getString(0));put(meal.id,meal) } } }
    fun confirmationStates(): Map<String,String> = readableDatabase.rawQuery("SELECT key,value FROM kv WHERE key LIKE 'cardState:%' OR key LIKE 'changeState:%'",null).use {c->
        buildMap {while(c.moveToNext())put(c.getString(0),c.getString(1))}
    }
    fun transaction(block: ()->Unit) { val db=writableDatabase;db.beginTransaction();try {block();db.setTransactionSuccessful()} finally {db.endTransaction()} }
    fun memories(): List<Memory> = readableDatabase.rawQuery("SELECT key,body,source FROM memories",null).use { c -> buildList { while(c.moveToNext()) add(Memory(c.getString(0),c.getString(1),c.getString(2))) } }
    fun saveMemory(m: Memory) {
        if(get("forgotten:${m.key}")==m.source)return
        writableDatabase.insertWithOnConflict("memories",null,ContentValues().apply { put("key",m.key);put("body",m.text);put("source",m.source) },SQLiteDatabase.CONFLICT_REPLACE)
    }
    fun forget(key: String) {
        memories().firstOrNull {it.key==key}?.let {put("forgotten:$key",it.source)}
        writableDatabase.delete("memories","key=?",arrayOf(key))
    }
}
