package com.lucasdpm.cognilink.features.ai.models

import kotlinx.serialization.Serializable

@Serializable
data class DocumentAnalysisResponse(
    val topics: List<Topic> = emptyList()
)

@Serializable
data class Topic(
    val title: String,
    val subtopics: List<String>
)
