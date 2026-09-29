package me.chile.app.data

import me.chile.app.domain.ChatMessage
import java.time.LocalDate

data class MessagePage(val messages: List<ChatMessage>,val firstSeq: Long?,val hasOlder: Boolean)

// Keyset pagination keeps page boundaries stable when new messages arrive.
fun LocalStore.messagePage(floor: Long?=null,before: Long?=null,older: Boolean=false): MessagePage {
    val args=mutableListOf<String>()
    val where=when {
        older && before!=null->{args.add(before.toString());"m.seq < ?"}
        older->"1=1"
        floor!=null->{args.add(floor.toString());"m.seq >= ?"}
        else->{args.add(LocalDate.now().minusDays(6).toString());"m.seq >= (SELECT MIN(seq) FROM messages WHERE created_at >= ?)"}
    }
    val limit=if(floor==null || older)" LIMIT 40" else ""
    val rows=readableDatabase.rawQuery("""
        SELECT m.seq,m.id,m.role,m.body,m.photo,m.mealId,m.created_at,e.value,o.value
        FROM messages m LEFT JOIN kv e ON e.key='messageExercise:' || m.id
        LEFT JOIN kv o ON o.key='messageOptions:' || m.id
        WHERE $where ORDER BY m.seq DESC $limit
    """.trimIndent(),args.toTypedArray()).use {c->buildList {
        while(c.moveToNext())add(c.getLong(0) to ChatMessage(c.getString(1),c.getString(2),c.getString(3),c.getString(4),c.getString(5),c.getString(6),c.getString(7),storedOptions(c.getString(8))))
    }}.asReversed()
    val first=rows.firstOrNull()?.first
    val hasOlder=readableDatabase.rawQuery("SELECT 1 FROM messages WHERE seq < ? LIMIT 1",arrayOf((first?:before?:Long.MAX_VALUE).toString())).use {it.moveToFirst()}
    return MessagePage(rows.map {it.second},first,hasOlder)
}
