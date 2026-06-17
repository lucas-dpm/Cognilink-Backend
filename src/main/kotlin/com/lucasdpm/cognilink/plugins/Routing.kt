package com.lucasdpm.cognilink.plugins

import com.lucasdpm.cognilink.features.ai.AiService
import com.lucasdpm.cognilink.features.ai.aiRouting
import com.lucasdpm.cognilink.features.firebase.firebaseRouting
import io.ktor.server.application.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Application.configureRouting() {
    val aiService = AiService()

    routing {
        get("/") {
            call.respondText("Backend Cognilink Ativo!")
        }
        
        // Registrar as rotas de IA
        aiRouting(aiService)
        
        // Registrar as rotas de Firebase
        firebaseRouting()
    }
}
