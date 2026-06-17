package com.lucasdpm.cognilink.features.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class AiPromptRequest(
    val prompt: String,
    val model: String? = "default"
)

@Serializable
data class AiResponse(
    val result: String,
    val status: String = "success"
)
