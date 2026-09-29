package me.chile.app

import me.chile.app.data.buildChatContext
import me.chile.app.domain.ChatMessage
import me.chile.app.domain.FoodItem
import me.chile.app.domain.Meal
import org.junit.Assert.*
import org.junit.Test

class ChatContextTest {
    private val original=ChatMessage(id="before",role="user",text="饭前",photo="original.jpg")
    private val meal=Meal(id="meal",photo="original.jpg",items=listOf(FoodItem("米饭",100.0,130)))
    private val receipt=ChatMessage(id="receipt",role="assistant",text="估算",mealId="meal")
    private val latest=ChatMessage(id="new",role="user",text="又拍了一张",photo="new.jpg")

    @Test fun newerUnassociatedPhotoIsNotSkippedForAnOlderMeal() {
        val intervening=ChatMessage(id="intervening",role="user",text="这份呢",photo="intervening.jpg")
        val context=buildChatContext(listOf(original,receipt,intervening,latest),emptyList(),mapOf(meal.id to meal),emptyMap(),latest)
        assertEquals(listOf("intervening","new"),context.images.map {it.id})
    }

    @Test fun repeatedLeftoversKeepOriginalPhotoWhenLatestReceiptLinksToIt() {
        val leftover=ChatMessage(id="leftover",role="user",text="剩饭",photo="leftover.jpg")
        val update=receipt.copy(id="updated",text="已对比剩饭")
        val context=buildChatContext(listOf(original,receipt,leftover,update,latest),emptyList(),mapOf(meal.id to meal),emptyMap(),latest)
        assertEquals(listOf("before","new"),context.images.map {it.id})
    }

    @Test fun originalPhotoSurvivesHistoryWindowWithoutDuplicatingCurrentMessage() {
        val conversation=listOf(original,receipt)+(1..30).map {ChatMessage(id="chat-$it",role="user",text="普通聊天 $it")}+latest
        val context=buildChatContext(conversation,listOf(meal),mapOf(meal.id to meal),mapOf(meal.id to "confirmed"),latest)
        assertEquals(listOf("before","new"),context.images.map {it.id})
        assertEquals(listOf("before")+conversation.takeLast(20).map {it.id},context.messages.map {it.id})
        assertEquals(1,context.messages.count {it.id==latest.id})
        assertTrue(context.messages.first().text.contains("照片关联的餐次ID：meal"))
    }

    @Test fun deletedCardRetainsHistoricalStatusInsteadOfClaimingItIsStillConfirmed() {
        val context=buildChatContext(listOf(original,receipt,latest),emptyList(),mapOf(meal.id to meal),mapOf(meal.id to "deleted"),latest)
        val historical=context.messages.single {it.id==receipt.id}
        assertTrue(historical.text.contains("状态：deleted"))
        assertFalse(historical.text.contains("状态：confirmed"))
    }

    @Test fun followUpKeepsNewestUploadEvenWhenAnOldCardWasMentionedAfterIt() {
        val followUp=ChatMessage(id="follow",role="user",text="不修改，新加截图里的")
        val context=buildChatContext(listOf(original,latest,receipt,followUp),emptyList(),mapOf(meal.id to meal),emptyMap(),followUp)
        assertEquals(listOf("new"),context.images.map {it.id})
    }

    @Test fun textFollowUpDoesNotResendPhotosOutsideRecentWindow() {
        val history=listOf(original)+(1..21).map {ChatMessage(role="user",text="聊天 $it")}
        assertTrue(buildChatContext(history,emptyList(),emptyMap(),emptyMap(),history.last()).images.isEmpty())
    }
}
