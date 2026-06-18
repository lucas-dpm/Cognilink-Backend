package com.lucasdpm.cognilink.features.ai.clients.gemini

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GeminiRequest(
    val contents: List<GeminiContent>,
    @SerialName("generation_config") val generationConfig: GeminiGenerationConfig? = null,
    @SerialName("safety_settings") val safetySettings: List<GeminiSafetySetting>? = null
)

@Serializable
data class GeminiSafetySetting(
    val category: String,
    val threshold: String
)

@Serializable
data class GeminiContent(val parts: List<GeminiPart> = emptyList())

@Serializable
data class GeminiPart(
    val text: String? = null,
    @SerialName("inline_data") val inlineData: GeminiInlineData? = null
)

@Serializable
data class GeminiInlineData(
    @SerialName("mime_type") val mimeType: String,
    val data: String
)

@Serializable
data class GeminiGenerationConfig(
    @SerialName("response_mime_type") val responseMimeType: String? = null,
    @SerialName("response_schema") val responseSchema: GeminiResponseSchema? = null
)

@Serializable
data class GeminiResponseSchema(
    val type: String,
    val properties: Map<String, GeminiResponseSchema>? = null,
    val items: GeminiResponseSchema? = null
)

@Serializable
data class GeminiResponse(
    val candidates: List<GeminiCandidate> = emptyList(),
    @SerialName("prompt_feedback") val promptFeedback: GeminiPromptFeedback? = null
)

@Serializable
data class GeminiPromptFeedback(
    @SerialName("safety_ratings") val safetyRatings: List<GeminiSafetyRating> = emptyList()
)

@Serializable
data class GeminiSafetyRating(
    val category: String,
    val probability: String
)

@Serializable
data class GeminiCandidate(
    val content: GeminiContent? = null,
    @SerialName("finish_reason") val finishReason: String? = null,
    @SerialName("safety_ratings") val safetyRatings: List<GeminiSafetyRating> = emptyList()
)
