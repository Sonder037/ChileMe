package me.chile.app.ui

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun chatTimeLabel(value: String?,today: LocalDate=LocalDate.now()): String? = runCatching {
    val time=LocalDateTime.parse(value?:return null)
    val pattern=when {time.toLocalDate()==today->"HH:mm";time.year==today.year->"M月d日 HH:mm";else->"yyyy年M月d日 HH:mm"}
    time.format(DateTimeFormatter.ofPattern(pattern))
}.getOrNull()

private val echoedTime=Regex("(?m)^\\[消息时间[：:](?:\\d{4}-\\d{2}-\\d{2}T[^]\\r\\n]*|未知，不可推断间隔)\\][ \\t]*\\n?")
fun assistantDisplayText(value: String): String = value.replace(echoedTime,"")
