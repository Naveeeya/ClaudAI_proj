package com.pitchslap.app.data

import kotlinx.serialization.Serializable

/**
 * Data model for AI-generated pronunciation feedback
 *
 * This data class represents structured feedback from the language coach LLM.
 * It follows a JSON schema that the LLM is prompted to generate.
 *
 * @property transcript The text that the user spoke (transcribed by Whisper/STT)
 * @property pronunciationScore Score from 0-100 for pronunciation quality
 * @property grammarScore Score from 0-100 for grammatical correctness
 * @property fluencyScore Score from 0-100 for speech fluency and naturalness
 * @property feedback Brief encouraging feedback message (1-2 sentences)
 * @property corrections List of specific pronunciation tips or corrections
 * @property exampleSentence A practice sentence for improvement
 */
@Serializable
data class PronunciationFeedback(
    val transcript: String,
    val pronunciationScore: Int,
    val grammarScore: Int,
    val fluencyScore: Int,
    val feedback: String,
    val corrections: List<String>,
    val exampleSentence: String
) {

    /**
     * Overall average score across all dimensions
     */
    val overallScore: Int
        get() = (pronunciationScore + grammarScore + fluencyScore) / 3

    /**
     * Check if all scores are above the threshold (passing grade)
     */
    fun isPassingGrade(threshold: Int = 70): Boolean {
        return pronunciationScore >= threshold &&
                grammarScore >= threshold &&
                fluencyScore >= threshold
    }

    /**
     * Get the weakest area for focused improvement
     */
    fun getWeakestArea(): String {
        val scores = mapOf(
            "Pronunciation" to pronunciationScore,
            "Grammar" to grammarScore,
            "Fluency" to fluencyScore
        )
        return scores.minByOrNull { it.value }?.key ?: "Unknown"
    }

    /**
     * Get the strongest area for positive reinforcement
     */
    fun getStrongestArea(): String {
        val scores = mapOf(
            "Pronunciation" to pronunciationScore,
            "Grammar" to grammarScore,
            "Fluency" to fluencyScore
        )
        return scores.maxByOrNull { it.value }?.key ?: "Unknown"
    }

    /**
     * Validate that all scores are within valid range (0-100)
     */
    fun isValid(): Boolean {
        return pronunciationScore in 0..100 &&
                grammarScore in 0..100 &&
                fluencyScore in 0..100 &&
                transcript.isNotBlank() &&
                feedback.isNotBlank()
    }

    companion object {
        /**
         * Create a default/fallback feedback for error cases
         */
        fun createFallback(transcript: String = ""): PronunciationFeedback {
            return PronunciationFeedback(
                transcript = transcript,
                pronunciationScore = 75,
                grammarScore = 75,
                fluencyScore = 75,
                feedback = "Keep practicing! Every conversation helps you improve.",
                corrections = listOf("Focus on speaking clearly and at a steady pace"),
                exampleSentence = "The quick brown fox jumps over the lazy dog."
            )
        }

        /**
         * Create empty feedback for initialization
         */
        fun empty(): PronunciationFeedback {
            return PronunciationFeedback(
                transcript = "",
                pronunciationScore = 0,
                grammarScore = 0,
                fluencyScore = 0,
                feedback = "",
                corrections = emptyList(),
                exampleSentence = ""
            )
        }
    }
}

/**
 * Feedback level for UI styling
 */
enum class FeedbackLevel {
    EXCELLENT,    // 90-100
    GOOD,         // 75-89
    FAIR,         // 60-74
    NEEDS_WORK    // 0-59
}

/**
 * Extension to get feedback level from score
 */
fun Int.toFeedbackLevel(): FeedbackLevel {
    return when {
        this >= 90 -> FeedbackLevel.EXCELLENT
        this >= 75 -> FeedbackLevel.GOOD
        this >= 60 -> FeedbackLevel.FAIR
        else -> FeedbackLevel.NEEDS_WORK
    }
}

/**
 * Extension to get color for feedback level (for UI)
 */
fun FeedbackLevel.toColorHex(): String {
    return when (this) {
        FeedbackLevel.EXCELLENT -> "#4CAF50"  // Green
        FeedbackLevel.GOOD -> "#8BC34A"       // Light Green
        FeedbackLevel.FAIR -> "#FF9800"       // Orange
        FeedbackLevel.NEEDS_WORK -> "#F44336" // Red
    }
}

/**
 * Extension to get emoji for feedback level
 */
fun FeedbackLevel.toEmoji(): String {
    return when (this) {
        FeedbackLevel.EXCELLENT -> "🎉"
        FeedbackLevel.GOOD -> "✅"
        FeedbackLevel.FAIR -> "👍"
        FeedbackLevel.NEEDS_WORK -> "💪"
    }
}
