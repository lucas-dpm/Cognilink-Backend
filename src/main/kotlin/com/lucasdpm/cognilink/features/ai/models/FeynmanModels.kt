package com.lucasdpm.cognilink.features.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class FeynmanStartRequest(
    val theme: String,
    val sessionId: String? = null
)

@Serializable
data class FeynmanMessageRequest(
    val message: String,
    val sessionId: String,
    val history: List<FeynmanChatMessage> = emptyList()
)

@Serializable
data class FeynmanChatMessage(
    val role: String, // "user" or "model"
    val content: String
)

@Serializable
data class FeynmanChatResponse(
    val reply: String,
    val isFinished: Boolean,
    val sm2Quality: Int? = null,
    val personaName: String? = null
)

@Serializable
data class FeynmanStartResponse(
    val sessionId: String,
    val initialMessage: String,
    val personaName: String? = null
)
