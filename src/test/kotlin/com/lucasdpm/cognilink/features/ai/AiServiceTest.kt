package com.lucasdpm.cognilink.features.ai

import com.lucasdpm.cognilink.features.ai.models.FeynmanStartRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class AiServiceTest {

    private val aiService = AiService()

    @Test
    fun `test startFeynmanChat returns a persona and initial message`() = runBlocking {
        val request = FeynmanStartRequest(theme = "Buracos Negros")
        val response = aiService.startFeynmanChat(request)
        
        assertNotNull(response.reply)
        assertNotNull(response.personaName)
        assertFalse(response.isFinished)
        assertTrue(response.reply.contains("Buracos Negros"))
    }
}
