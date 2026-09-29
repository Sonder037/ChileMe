package me.chile.app

import me.chile.app.ui.chatTimeLabel
import me.chile.app.ui.assistantDisplayText
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class ChatPresentationTest {
    @Test fun timeLabelsAreCompactAndDoNotInventMissingTimes() {
        val today=LocalDate.of(2026,9,26)
        assertEquals("12:34",chatTimeLabel("2026-09-26T12:34:56.123",today))
        assertEquals("9月25日 12:34",chatTimeLabel("2026-09-25T12:34",today))
        assertEquals("2025年9月26日 12:34",chatTimeLabel("2025-09-26T12:34",today))
        assertNull(chatTimeLabel(null,today));assertNull(chatTimeLabel("invalid",today))
    }
    @Test fun internalEchoedTimePrefixIsHiddenWithoutRemovingConversationDates() {
        assertEquals("记好了。",assistantDisplayText("[消息时间：2026-09-26T12:34:56] 记好了。"))
        assertEquals("看看卡片。",assistantDisplayText("[消息时间：未知，不可推断间隔]\n看看卡片。"))
        assertEquals("你说的是9月25日午餐。",assistantDisplayText("你说的是9月25日午餐。"))
    }
}
