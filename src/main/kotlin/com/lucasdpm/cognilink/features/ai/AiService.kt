package com.lucasdpm.cognilink.features.ai

import com.lucasdpm.cognilink.features.ai.clients.gemini.*
import com.lucasdpm.cognilink.features.ai.models.*
import com.lucasdpm.cognilink.plugins.RedisConfig
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

    private val personas = listOf(
        FeynmanPersona(
            "Criança de 5 anos",
            "Você é uma criança curiosa de 5 anos que não sabe nada sobre o assunto. Você faz perguntas simples e quer analogias com brinquedos ou coisas do dia a dia.",
            "Oi! Eu sou o Leo. Minha professora falou sobre isso, mas eu não entendi nada. Você pode me explicar o que é %s como se eu tivesse 5 anos?"
        ),
        FeynmanPersona(
            "Avó Gentil",
            "Você é uma avó muito gentil e paciente, mas que não entende nada de tecnologia ou conceitos modernos. Você gosta de histórias e comparações com a vida no campo ou culinária.",
            "Olá, querido(a)! Eu estava lendo sobre esse tal de %s, mas minha cabeça ficou toda confusa. Você teria paciência de explicar para essa velhinha o que é isso?"
        ),
        FeynmanPersona(
            "Amigo Leigo",
            "Você é um amigo inteligente, mas que trabalha em uma área completamente diferente e nunca ouviu falar desse tema. Você é cético e quer entender a utilidade prática.",
            "E aí! Cara, vi você estudando %s e fiquei curioso. O que é isso afinal? Tenta me explicar sem usar termos muito técnicos, por favor."
        )
    )

    private data class FeynmanPersona(
        val name: String,
        val description: String,
        val initialMessageTemplate: String
    )

    private val apiKey: String by lazy {
        val properties = Properties()
        val localPropertiesFile = File("local.properties")
        if (localPropertiesFile.exists()) {
            localPropertiesFile.inputStream().use { properties.load(it) }
        }
        properties.getProperty("GEMINI_API_KEY") ?: ""
    }

    private suspend fun <T> withRetry(
        maxRetries: Int = 2,
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
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
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
            throw e
        }
    }

    suspend fun analyzeDocument(fileBytes: ByteArray, fileName: String): DocumentAnalysisResponse = withRetry {
        // Usando v1 conforme sua recomendação e o modelo 1.5-flash original para arquivos
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
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
                            GeminiPart(text = """
                                Você é um assistente especializado em análise profunda de documentos acadêmicos e técnicos.
                                Seu objetivo é realizar uma leitura exaustiva do documento fornecido e extrair uma estrutura detalhada de tópicos.

                                INSTRUÇÕES DE ANÁLISE:
                                1. Identifique o tema central (main-theme).
                                2. Extraia TODOS os conceitos, definições, metodologias e pontos-chave discutidos no documento.
                                3. Não se limite apenas aos títulos; procure por conceitos importantes dentro do conteúdo.
                                4. Retorne uma lista abrangente de tópicos (busque extrair o máximo de tópicos relevantes possível).
                                5. Mantenha a lista como um array de strings simples.

                                REGRAS DE FORMATAÇÃO:
                                1. Retorne APENAS o JSON puro, sem blocos de código Markdown (```json).
                                2. NÃO use formatação Markdown (**, _, #, `) dentro dos valores do JSON.
                                
                                FORMATO DE RETORNO (JSON):
                                {
                                  "main-theme": "Título Detalhado do Documento",
                                  "topics": ["Tópico Detalhado 1", "Conceito 2", "Definição 3", "..."]
                                }
                            """.trimIndent()),
                            GeminiPart(inlineData = GeminiInlineData(mimeType, base64Data))
                        )
                    ))
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
                DocumentAnalysisResponse()
            }
        } catch (e: Exception) {
            logger.error("Erro na análise do documento $fileName: ${e.message}")
            throw e
        }
    }

    suspend fun generateFlashcards(request: GenerateFlashcardsRequest): FlashcardResponse = withRetry {
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val topicsDescription = if (request.topics.isEmpty()) "o tema principal" else "os seguintes tópicos: ${request.topics.joinToString(", ")}"
        val prompt = """
            Gere exatamente ${request.quantity} flashcards sobre $topicsDescription.
            O contexto geral/tema principal é: "${request.mainTheme}".
            
            REGRAS DE CONTEÚDO:
            ${if (request.topics.isNotEmpty()) "- Foco: Gere cards baseados nos tópicos fornecidos, usando o tema principal como contexto." else ""}
            - Dificuldade: ${if (request.difficulty == "RANDOM") "Misture níveis fácil, médio e difícil" else request.difficulty}.
            - Tipo de Card: ${if (request.type == "RANDOM") "Misture tipos básico, múltipla escolha e verdadeiro/falso" else request.type}.
            - Idioma e Adaptação: 
               - O idioma principal deve ser Português Brasileiro.
               - IMPORTANTE: Se o tema principal ("${request.mainTheme}") ou os tópicos indicarem o estudo de uma língua estrangeira (ex: Inglês, Espanhol), adapte o idioma dos cards para essa língua de forma pedagógica (ex: perguntas em Português e respostas na língua estrangeira, ou vice-versa).
               - Mantenha conceitos técnicos ou termos sem tradução direta no idioma original.
            - Distribuição: Divida a quantidade de cards de forma equilibrada entre os assuntos identificados.
            - Dicas Progressivas (hints): Para cada flashcard, gere EXATAMENTE 3 dicas que ajudem o usuário a lembrar a resposta:
               - Dica 1: Orientação conceitual geral (sem revelar a resposta).
               - Dica 2: Direcionamento mais específico sobre o termo ou conceito.
               - Dica 3: Quase uma revelação da resposta, sendo muito direta.
               - Tom: Mantenha um tom sempre encorajador e educativo.
            
            REGRAS DE FORMATAÇÃO:
            - Retorne APENAS o JSON puro, sem blocos de código Markdown (```json).
            - NÃO use nenhuma formatação Markdown (como asteriscos para negrito ou sublinhados) dentro dos campos de texto (question, answerText, etc).
            
            FORMATO DE RETORNO (JSON):
            {
              "flashcards": [
                {
                  "question": "texto",
                  "type": "BASIC | MULTIPLE_CHOICE | TRUE_OR_FALSE",
                  "difficulty": "EASY | MEDIUM | HARD",
                  "hints": [
                    "Orientação conceitual geral (sem revelar a resposta)...",
                    "Direcionamento mais específico sobre o termo ou conceito...",
                    "Quase uma revelação da resposta, sendo muito direta..."
                  ],
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

            if (httpResponse.status != HttpStatusCode.OK) {
                val errorBody = httpResponse.body<String>()
                throw Exception("Gemini API error (${httpResponse.status}): $errorBody")
            }

            val response: GeminiResponse = httpResponse.body()
            val jsonResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonResponse != null) {
                json.decodeFromString<FlashcardResponse>(cleanJsonResponse(jsonResponse))
            } else {
                FlashcardResponse(emptyList())
            }
        } catch (e: Exception) {
            logger.error("Erro ao gerar flashcards: ${e.message}")
            throw e
        }
    }

    suspend fun compareAnswer(request: CompareAnswerRequest): CompareAnswerResponse = withRetry {
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"

        val prompt = """
            Você é um professor avaliando a resposta de um aluno para um flashcard.
            Questão: ${request.question}
            Resposta Esperada (Gabarito): ${request.correctAnswer}
            Resposta do Usuário: ${request.userAnswer}

            REGRAS DE AVALIAÇÃO:
            1. Seja flexível: Se a resposta do usuário for semanticamente equivalente ao gabarito, considere correta (isCorrect: true).
            2. Ignore erros menores: Não penalize por erros de digitação leves, falta de acentuação ou diferenças de maiúsculas/minúsculas.
            3. Dica Pedagógica (tip):
               - Se estiver correta: Reforce o conhecimento com uma curiosidade curta ou uma técnica de mnemônica (ex: acrônimos, rimas, associações visuais) para ajudar na memorização a longo prazo.
               - Se estiver incorreta: Explique brevemente o porquê do erro de forma encorajadora e forneça a mnemônica para ajudar a não esquecer novamente.

            REGRAS DE FORMATAÇÃO:
            1. Retorne APENAS o JSON puro, sem blocos de código Markdown.
            2. NÃO use NENHUMA formatação Markdown (como **negrito**, _itálico_, `código` ou # títulos) dentro das strings do JSON. O texto deve ser plano/puro.

            FORMATO DE RETORNO (JSON):
            {
              "isCorrect": boolean,
              "tip": "string"
            }
        """.trimIndent()

        try {
            logger.info("Comparando resposta para a questão: ${request.question.take(50)}...")

            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(
                    contents = listOf(GeminiContent(parts = listOf(GeminiPart(text = prompt)))),
                ))
            }

            if (httpResponse.status != HttpStatusCode.OK) {
                val errorBody = httpResponse.body<String>()
                throw Exception("Gemini API error (${httpResponse.status}): $errorBody")
            }

            val response: GeminiResponse = httpResponse.body()
            val jsonResponse = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            if (jsonResponse != null) {
                json.decodeFromString<CompareAnswerResponse>(cleanJsonResponse(jsonResponse))
            } else {
                CompareAnswerResponse(false, "Não foi possível validar a resposta no momento.")
            }
        } catch (e: Exception) {
            logger.error("Erro ao comparar resposta: ${e.message}")
            throw e
        }
    }

    suspend fun startFeynmanChat(request: FeynmanStartRequest): FeynmanChatResponse {
        val sessionId = request.sessionId ?: UUID.randomUUID().toString()
        val persona = personas.random()
        val initialMessage = persona.initialMessageTemplate.format(request.theme)
        
        val initialState = listOf(FeynmanChatMessage("model", initialMessage))
        
        RedisConfig.pool.resource.use { jedis ->
            val key = RedisConfig.getSessionKey(sessionId)
            jedis.set(key, json.encodeToString(initialState))
            jedis.expire(key, 1800) // 30 minutos
        }
        
        return FeynmanChatResponse(
            reply = initialMessage,
            isFinished = false,
            personaName = persona.name
        )
    }

    suspend fun processFeynmanMessage(request: FeynmanMessageRequest): FeynmanChatResponse = withRetry {
        val url = "https://generativelanguage.googleapis.com/v1/models/gemini-2.5-flash:generateContent?key=$apiKey"
        
        val key = RedisConfig.getSessionKey(request.sessionId)
        val historyJson = RedisConfig.pool.resource.use { it.get(key) }
            ?: throw Exception("Sessão não encontrada ou expirada.")
            
        val history = json.decodeFromString<List<FeynmanChatMessage>>(historyJson)

        val systemPrompt = """
            Você está em uma sessão de "Técnica de Feynman". 
            O usuário está tentando te explicar um conceito para verificar se ele realmente entendeu.
            
            SUA PERSONA: 
            ${history.firstOrNull { it.role == "model" }?.content?.let { firstMsg ->
                personas.find { it.initialMessageTemplate.substringBefore("%s") in firstMsg }?.description 
            } ?: "Você é um aprendiz curioso que não conhece o assunto."}
            
            REGRAS DE INTERAÇÃO:
            1. Atue estritamente como sua persona.
            2. Se a explicação do usuário for confusa, faça perguntas clarificadoras simples.
            3. Se ele usar termos técnicos (jargões), peça para ele explicar esses termos de forma simples.
            4. Se ele der uma ótima explicação com analogias, elogie e finalize a sessão.
            
            REGRAS DE FINALIZAÇÃO:
            - Quando você sentir que o usuário explicou o conceito de forma clara, simples e sem buracos no conhecimento, você deve finalizar a sessão.
            - Na última resposta, defina 'isFinished' como true e atribua uma nota de qualidade SM2 (0 a 5) baseada na clareza da explicação.
            
            REGRAS DE FORMATAÇÃO:
            1. Retorne APENAS o JSON puro, sem blocos de código Markdown.
            2. NÃO use formatação Markdown dentro das strings do JSON.

            FORMATO DE RETORNO (JSON):
            {
              "reply": "sua resposta aqui",
              "isFinished": boolean,
              "sm2Quality": number (0-5, preencher apenas se isFinished for true)
            }
        """.trimIndent()

        val geminiHistory = history.map {
            GeminiContent(
                role = it.role,
                parts = listOf(GeminiPart(text = it.content))
            )
        } + GeminiContent(
            role = "user",
            parts = listOf(GeminiPart(text = request.message))
        )

        try {
            logger.info("Processando mensagem de Feynman para sessão: ${request.sessionId}")

            val httpResponse = client.post(url) {
                contentType(ContentType.Application.Json)
                setBody(GeminiRequest(
                    contents = geminiHistory,
                    systemInstruction = GeminiContent(parts = listOf(GeminiPart(text = systemPrompt)))
                ))
            }

            if (httpResponse.status != HttpStatusCode.OK) {
                val errorBody = httpResponse.body<String>()
                throw Exception("Gemini API error (${httpResponse.status}): $errorBody")
            }

            val response: GeminiResponse = httpResponse.body()
            val geminiText = response.candidates.firstOrNull()?.content?.parts?.firstOrNull()?.text
            
            if (geminiText != null) {
                val feynmanResponse = json.decodeFromString<FeynmanChatResponse>(cleanJsonResponse(geminiText))
                
                if (feynmanResponse.isFinished) {
                    RedisConfig.pool.resource.use { it.del(key) }
                } else {
                    val updatedHistory = history + 
                        FeynmanChatMessage("user", request.message) + 
                        FeynmanChatMessage("model", feynmanResponse.reply)
                    
                    RedisConfig.pool.resource.use { jedis ->
                        jedis.set(key, json.encodeToString(updatedHistory))
                        jedis.expire(key, 1800)
                    }
                }
                
                feynmanResponse
            } else {
                FeynmanChatResponse("Desculpe, não consegui processar sua explicação agora.", false)
            }
        } catch (e: Exception) {
            logger.error("Erro no chat de Feynman: ${e.message}")
            throw e
        }
    }
}
