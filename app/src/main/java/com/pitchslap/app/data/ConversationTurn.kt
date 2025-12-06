package com.pitchslap.app.data

import kotlinx.serialization.Serializable

/**
 * Represents a single turn in a conversation
 *
 * A conversation turn includes the user's input, the AI's feedback,
 * and metadata about the exchange.
 *
 * @property id Unique identifier for this turn
 * @property timestamp Unix timestamp when this turn occurred
 * @property userTranscript The text the user spoke
 * @property aiFeedback The feedback provided by the AI
 * @property durationMs How long this turn took (from speech start to feedback end)
 */
@Serializable
data class ConversationTurn(
    val id: Long = System.currentTimeMillis(),
    val timestamp: Long = System.currentTimeMillis(),
    val userTranscript: String,
    val aiFeedback: PronunciationFeedback,
    val durationMs: Long = 0
) {
    /**
     * Get a summary of this turn for display
     */
    fun getSummary(): String {
        return "$userTranscript → ${aiFeedback.overallScore}"
    }

    /**
     * Check if this turn shows improvement over previous turn
     */
    fun isImprovementOver(previousTurn: ConversationTurn?): Boolean {
        if (previousTurn == null) return false
        return aiFeedback.overallScore > previousTurn.aiFeedback.overallScore
    }
}
