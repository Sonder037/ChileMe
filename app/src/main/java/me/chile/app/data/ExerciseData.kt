package me.chile.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import me.chile.app.domain.Exercise
import org.json.JSONObject

fun Exercise.json(): JSONObject=JSONObject().put("id",id).put("date",date).put("time",time).put("name",name)
    .put("minutes",minutes).put("activeKcal",activeKcal).put("source",source).put("messageId",messageId)
fun exerciseJson(raw: String): Exercise=checkedJsonObject(raw).let {
    Exercise(it.getString("id"),it.getString("date"),it.getString("time"),it.getString("name"),it.getInt("minutes"),it.getInt("activeKcal"),it.getString("source"),it.getString("messageId"))
}
fun LocalStore.exercises(): List<Exercise> = readableDatabase.rawQuery("SELECT body FROM exercises ORDER BY rowid DESC",null).use {c->
    buildList {while(c.moveToNext())add(exerciseJson(c.getString(0)))}
}
fun LocalStore.saveExercise(exercise: Exercise) {
    require(exercise.valid()){"运动数据无效"}
    requireRecordedDate(exercise.date)
    writableDatabase.insertWithOnConflict("exercises",null,ContentValues().apply {put("id",exercise.id);put("body",exercise.json().toString())},SQLiteDatabase.CONFLICT_REPLACE)
}
fun LocalStore.deleteExercise(id: String) {writableDatabase.delete("exercises","id=?",arrayOf(id))}
