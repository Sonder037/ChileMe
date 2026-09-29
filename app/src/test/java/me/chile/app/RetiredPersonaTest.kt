package me.chile.app

import me.chile.app.data.*
import org.junit.Assert.*
import org.junit.Test

class RetiredPersonaTest {
    @Test fun oldPersonaFieldsAreDiscardedWithoutChangingConnection() {
        val config=configJson("""{"baseUrl":"https://example.com/v1","chatModel":"chat-test","visionModel":"vision-test","offline":true,"personaMode":"blue_fish","persona":"OLD_NATIVE","fishPersona":"OLD_FISH"}""")
        assertEquals("https://example.com/v1",config.baseUrl)
        assertEquals("chat-test",config.chatModel)
        assertEquals("vision-test",config.visionModel)
        assertTrue(config.offline)
        val saved=config.json()
        for(key in listOf("personaMode","persona","fishPersona"))assertFalse("Retired field: $key",saved.has(key))
    }
}
