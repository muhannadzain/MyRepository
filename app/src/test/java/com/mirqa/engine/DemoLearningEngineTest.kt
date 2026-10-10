package com.mirqa.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DemoLearningEngineTest {
    @Test
    fun questionPackHasUniqueIds() {
        val ids = DemoLearningEngine.questions.map { it.id }
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun eachQuestionHasValidAnswerIndex() {
        DemoLearningEngine.questions.forEach { question ->
            assertTrue(question.options.isNotEmpty())
            assertTrue(question.correctOption in question.options.indices)
        }
    }

    @Test
    fun questionPackIsAvailableOffline() {
        assertTrue(DemoLearningEngine.questions.isNotEmpty())
    }
}
