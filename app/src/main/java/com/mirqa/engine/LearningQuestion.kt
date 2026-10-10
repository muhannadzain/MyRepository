package com.mirqa.engine

data class LearningQuestion(
    val id: String,
    val conceptId: String,
    val conceptTitle: String,
    val prompt: String,
    val options: List<String>,
    val correctOption: Int,
    val explanation: String
)

data class LearningStep(
    val title: String,
    val subtitle: String,
    val color: androidx.compose.ui.graphics.Color
)
