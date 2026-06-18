package com.lucasdpm.cognilink.features.ai

import com.lucasdpm.cognilink.features.ai.models.AiPromptRequest
import com.lucasdpm.cognilink.features.ai.models.CompareAnswerRequest
import com.lucasdpm.cognilink.features.ai.models.GenerateFlashcardsRequest
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.utils.io.readRemaining
import kotlinx.io.readByteArray

fun Route.aiRouting(aiService: AiService) {
    route("/ai") {
        post("/chat") {
            val request = call.receive<AiPromptRequest>()
            val response = aiService.getAiCompletion(request)
            call.respond(response)
        }

        post("/analyze-document") {
            val multipart = call.receiveMultipart()
            var fileBytes: ByteArray? = null
            var fileName: String? = null

            multipart.forEachPart { part ->
                if (part is PartData.FileItem) {
                    // Ktor 3: Usando provider() com readRemaining() para obter os bytes
                    fileBytes = part.provider().readRemaining().readByteArray()
                    fileName = part.originalFileName
                }
                part.release()
            }

            val currentBytes = fileBytes
            val currentName = fileName

            if (currentBytes != null && currentName != null) {
                val response = aiService.analyzeDocument(currentBytes, currentName)
                call.respond(response)
            } else {
                call.respond(HttpStatusCode.BadRequest, "Arquivo não encontrado na requisição")
            }
        }

        post("/generate-flashcards") {
            val request = call.receive<GenerateFlashcardsRequest>()
            if (request.mainTheme.isEmpty() && request.topics.isEmpty()) {
                call.respond(HttpStatusCode.BadRequest, "Forneça ao menos um tema principal ou tópicos para a geração")
                return@post
            }
            if (request.quantity <= 0) {
                call.respond(HttpStatusCode.BadRequest, "Quantidade de cards deve ser maior que zero")
                return@post
            }

            val response = aiService.generateFlashcards(request)
            call.respond(response)
        }

        post("/compare-answer") {
            try {
                val request = call.receive<CompareAnswerRequest>()
                val response = aiService.compareAnswer(request)
                call.respond(response)
            } catch (e: Exception) {
                call.respond(HttpStatusCode.BadRequest, "Erro ao processar validação de resposta: ${e.message}")
            }
        }
    }
}
