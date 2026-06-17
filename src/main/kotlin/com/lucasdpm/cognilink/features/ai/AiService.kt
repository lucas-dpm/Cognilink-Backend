package com.lucasdpm.cognilink.features.ai

import com.lucasdpm.cognilink.features.ai.clients.gemini.*
import com.lucasdpm.cognilink.features.ai.models.*
import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import org.slf4j.LoggerFactory
import java.io.File
import java.util.*

class AiService {
    private val logger = LoggerFactory.getLogger(AiService::class.java)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json(json)
        }
        install(HttpTimeout) {
            requestTimeoutMillis = 60000 // 60 segundos
            connectTimeoutMillis = 10000
            socketTimeoutMillis = 60000
        }
    }

    private val apiKey: String by lazy {
        val properties = Properties()
        val localPropertiesFile = File("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }
        properties.getProperty("GEMINI_API_KEY") ?: ""
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = 3,
        initialDelayMillis: Long = 2000,
        maxDelayMillis: Long = 60000,
        block: suspend () -> T
    ): T {
        var currentDelay = initialDelayMillis
        repeat(maxRetries) { attempt ->
            try {
                return block()
            } catch (e: Exception) {
                val isRateLimit = e.message?.contains("429") == true
                val isServerError = e.message?.contains("500") == true || e.message?.contains("503") == true
                val isTimeout = e is HttpRequestTimeoutException || e.message?.contains("timeout") == true
                
                if ((isRateLimit || isServerError || isTimeout) && attempt < maxRetries - 1) {
                    logger.warn("Gemini API pressure or timeout (attempt ${attempt + 1}). Retrying in ${currentDelay}ms... Error: ${e.message}")
                    delay(currentDelay)
                    currentDelay = (currentDelay * 2).coerceAtMost(maxDelayMillis)
                } else {
                    throw e
                }
            }
        }
        return block()
    }

    private fun cleanJsonResponse(text: String): String {
        return text.trim()
            .replace(Regex("""^```(?:json)?\s*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\s*```$"""), "")
            .trim()
    }

    suspend fun getAiCompletion(request: AiPromptRequest): AiResponse = withRetry {
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        try {
            logger.info("Iniciando chat com Gemini para prompt: ${request.prompt.take(50)}...")
            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(
                    contents = listOf(GeminiContent(
                        parts = listOf(GeminiPart(text = request.prompt))
                    ))
                ))
            }

            if (httpResponse.status != HttpStatusCode.OK) {
                val errorBody = httpResponse.body<String>()
                throw Exception("Gemini API error (${httpResponse.status}): $errorBody")
            }

            val response: GeminiResponse = httpResponse.body()
            val text = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text ?: "Sem resposta"
            AiResponse(result = text)
        } catch (e: Exception) {
            logger.error("Erro ao chamar Gemini: ${e.message}")
            throw e // Repropaga para o withRetry
        }
    }

    suspend fun analyzeDocument(fileBytes: ByteArray, fileName: String): DocumentAnalysisResponse = withRetry {
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-3.5-flash:generateContent?key=$apiKey"
        
        val base64Data = Base64.getEncoder().encodeToString(fileBytes)
        val mimeType = when {
            fileName.endsWith(".pdf", ignoreCase = true) -> "application/pdf"
            fileName.endsWith(".pptx", ignoreCase = true) -> "application/vnd.openxmlformats-officedocument.presentationml.presentation"
            else -> "application/octet-stream"
        }

        try {
            logger.info("Enviando arquivo $fileName ($mimeType) para análise no Gemini")
            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(
                    contents = listOf(GeminiContent(
                        parts = listOf(
                            GeminiPart(text = "Analise este documento e extraia os tópicos e subtópicos principais. Retorne apenas o JSON estruturado."),
                            GeminiPart(inlineData = GeminiInlineData(mimeType, base64Data))
                        )
                    )),
                    generationConfig = GeminiGenerationConfig(
                        responseMimeType = "application/json",
                        responseSchema = GeminiResponseSchema(
                            type = "object",
                            properties = mapOf(
                                "topics" to GeminiResponseSchema(
                                    type = "array",
                                    items = GeminiResponseSchema(
                                        type = "object",
                                        properties = mapOf(
                                            "title" to GeminiResponseSchema(type = "string"),
                                            "subtopics" to GeminiResponseSchema(
                                                type = "array",
                                                items = GeminiResponseSchema(type = "string")
                                            )
                                        )
                                    )
                                )
                            )
                        )
                    )
                ))
            }

            if (httpResponse.status != HttpStatusCode.OK) {
                val errorBody = httpResponse.body<String>()
                throw Exception("Gemini API error (${httpResponse.status}): $errorBody")
            }

            val response: GeminiResponse = httpResponse.body()

            val jsonResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonResponse != null) {
                logger.info("Análise concluída com sucesso para $fileName")
                json.decodeFromString<DocumentAnalysisResponse>(cleanJsonResponse(jsonResponse))
            } else {
                logger.warn("Gemini retornou resposta vazia para $fileName")
                DocumentAnalysisResponse(emptyList())
            }
        } catch (e: Exception) {
            logger.error("Erro na análise do documento $fileName: ${e.message}")
            throw e
        }
    }

    suspend fun generateFlashcards(request: GenerateFlashcardsRequest): FlashcardResponse = withRetry {
        val url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash:generateContent?key=$apiKey"

        val prompt = """
            Gere exatamente ${request.quantity} flashcards sobre os seguintes tópicos: ${request.topics.joinToString(", ")}.
            
            REGRAS DE CONTEÚDO:
            1. Dificuldade: ${if (request.difficulty == "aleatorio") "Misture níveis fácil, médio e difícil" else request.difficulty}.
            2. Tipo de Card: ${if (request.type == "aleatorio") "Misture tipos básico, múltipla escolha e verdadeiro/falso" else request.type}.
            3. Idioma: Responda sempre em Português Brasileiro.
            4. Distribuição: Divida a quantidade de cards entre os tópicos.
            
            REGRAS DE FORMATO (answerOptions):
            - Para 'basico': A lista 'answerOptions' deve ter apenas 1 item (a resposta correta).
            - Para 'verdadeiro_falso': A lista 'answerOptions' deve ter entre 2 e 10 itens (cada um sendo uma afirmação para marcar as corretas), marcando o 'isCorrect' corretamente.
            - Para 'multipla_escolha': A lista 'answerOptions' deve ter entre 2 e 5 itens, with apenas 1 marcado como 'isCorrect: true'.
            
            IMPORTANTE: Retorne APENAS o JSON puro, sem blocos de código Markdown (```json).
            
            FORMATO DE RETORNO (JSON):
            {
              "flashcards": [
                {
                  "question": "texto",
                  "type": "basico | multipla_escolha | verdadeiro_falso",
                  "difficulty": "facil | medio | dificil",
                  "answerOptions": [
                    { "answerText": "texto", "isCorrect": true },
                    { "answerText": "texto", "isCorrect": false }
                  ]
                }
              ]
            }
        """.trimIndent()

        try {
            logger.info("Gerando ${request.quantity} flashcards para os tópicos: ${request.topics.take(3)}...")

            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt))))
                ))
            }

            val responseBody = httpResponse.body<String>()
            logger.info("Gemini HTTP Status: ${httpResponse.status}")
            
            if (httpResponse.status != HttpStatusCode.OK) {
                logger.error("Gemini API error: $responseBody")
                throw Exception("Gemini API error (${httpResponse.status}): $responseBody")
            }

            val response: GeminiResponse = json.decodeFromString(responseBody)

            logger.info("Gemini candidates count: ${response.candidates.size}")
            val jsonResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonResponse != null) {
                logger.info("Raw JSON response from Gemini: $jsonResponse")
                json.decodeFromString<FlashcardResponse>(cleanJsonResponse(jsonResponse))
            } else {
                logger.warn("Gemini returned no candidates or empty parts. Response: $response")
                FlashcardResponse(emptyList())
            }
        } catch (e: Exception) {
            logger.error("Erro ao gerar flashcards: ${e.message}")
            throw e
        }
    }
}
