package com.lucasdpm.cognilink.features.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class GenerateFlashcardsRequest(
    val topics: List<String>,
    val difficulty: String, // "facil", "medio", "dificil", "aleatorio"
    val type: String,       // "basico", "multipla_escolha", "verdadeiro_falso", "aleatorio"
    val quantity: Int
)

@Serializable
data class FlashcardResponse(
    val flashcards: List<Flashcard> = emptyList()
)

@Serializable
data class Flashcard(
    val question: String,
    val type: String,
    val difficulty: String,
    val answerOptions: List<AnswerOption>
)

@Serializable
data class AnswerOption(
    val answerText: String,
    val isCorrect: Boolean
)
