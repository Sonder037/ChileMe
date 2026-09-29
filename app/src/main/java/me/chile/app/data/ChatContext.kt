package me.chile.app.data

import me.chile.app.domain.*

data class ChatContext(val messages: List<ChatMessage>,val images: List<ChatMessage>)
fun buildChatContext(messages: List<ChatMessage>,meals: List<Meal>,cards: Map<String,Meal>,cardStates: Map<String,String>,message: ChatMessage): ChatContext {
            val lastPhotoIndex=messages.indexOfLast {it.id!=message.id && it.photo!=null}
            val lastMealIndex=messages.indexOfLast {it.mealId in cards}
            // A newer, unassociated upload must not be hidden by an older meal.
            // After a meal reply, retain its original photo for repeated leftovers.
            val latestMeal=if(lastMealIndex>lastPhotoIndex)messages[lastMealIndex].mealId?.let {cards[it]} else null
            val previousPhoto=latestMeal?.photo?.let {photo->messages.lastOrNull {it.id!=message.id && it.photo==photo}}
                ?:messages.getOrNull(lastPhotoIndex)
            val recent=messages.takeLast(20)
            // Text follow-ups still refer to the latest upload, not an older card's photo.
            val selectedImages=if(message.photo!=null)listOfNotNull(previousPhoto,message)
                else listOfNotNull(recent.lastOrNull {it.role=="user" && it.photo!=null})
            val contextMessages=(selectedImages.filter {m->recent.none {it.id==m.id}}+recent).distinctBy {it.id}
            val history=contextMessages.map {original->
                val associated=if(original.photo==null)emptyList() else (meals+cards.values).filter {it.photo==original.photo}.distinctBy {it.id}
                val m=if(associated.isEmpty())original else original.copy(text=original.text+"\n照片关联的餐次ID："+associated.joinToString {it.id})
                m.mealId?.let {cards[it]}?.let {meal->m.copy(text=m.text+"\n餐次数据："+meal.json().toString()+" 状态："+(cardStates[meal.id]?:"draft"))}?:m}
    return ChatContext(history,selectedImages)
}
