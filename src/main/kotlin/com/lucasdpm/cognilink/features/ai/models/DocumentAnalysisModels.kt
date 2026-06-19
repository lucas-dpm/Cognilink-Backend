package com.lucasdpm.cognilink.features.ai.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class DocumentAnalysisResponse(
    @SerialName("main-theme") val mainTheme: String = "",
    val topics: List<String> = emptyList()
)
