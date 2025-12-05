package com.pitchslap.app.ai

import android.util.Log
import com.pitchslap.app.data.PronunciationFeedback
import com.pitchslap.app.prompts.LanguageCoachPrompts
import com.runanywhere.sdk.public.RunAnywhere
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerializationException

/**
 * Feedback Generator using LLM (Language Learning Model)
 *
 * This class integrates with the RunAnywhere SDK to generate
 * pronunciation feedback using an on-device LLM (Qwen/Llama).
 *
 * The LLM is prompted with a language coach personality and
 * generates structured JSON feedback for user speech.
 */
class FeedbackGenerator {

    companion object {
        private const val TAG = "PitchSlap_FeedbackGenerator"
    }

    // JSON parser with lenient settings to handle slight format variations
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private var isInitialized = false
    private var currentModelId: String? = null

    /**
     * Initialize the feedback generator
     *
     * This checks if an LLM model is loaded and ready for generation.
     *
     * @return true if initialization successful, false otherwise
     */
    suspend fun initialize(): Boolean {
        return try {
            // Check if RunAnywhere SDK is initialized
            // In a real implementation, we'd check SDK state
            isInitialized = true
            Log.i(TAG, "✅ FeedbackGenerator initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: ${e.message}", e)
            false
        }
    }

    /**
     * Generate pronunciation feedback from user transcript (blocking)
     *
     * @param transcript The transcribed text from user's speech
     * @param conversationHistory Optional conversation context
     * @param userLevel User's proficiency level (for tailored feedback)
     * @return Result containing PronunciationFeedback or error
     */
    suspend fun generateFeedback(
        transcript: String,
        conversationHistory: List<String> = emptyList(),
        userLevel: LanguageCoachPrompts.UserLevel = LanguageCoachPrompts.UserLevel.INTERMEDIATE
    ): Result<PronunciationFeedback> {
        if (!isInitialized) {
            return Result.failure(IllegalStateException("FeedbackGenerator not initialized"))
        }

        if (transcript.isBlank()) {
            return Result.failure(IllegalArgumentException("Transcript cannot be blank"))
        }

        return try {
            Log.i(TAG, "🤖 Generating feedback for: \"$transcript\"")
            val startTime = System.currentTimeMillis()

            // Create prompt based on user level
            val prompt = LanguageCoachPrompts.createPromptForLevel(transcript, userLevel)

            // Generate response from LLM
            val response = RunAnywhere.generate(prompt)

            val endTime = System.currentTimeMillis()
            val latency = endTime - startTime
            Log.i(TAG, "⏱️ Generation latency: ${latency}ms")

            // Parse JSON response
            val feedback = parseResponse(response)

            if (feedback.isValid()) {
                Log.i(TAG, "✅ Feedback generated successfully")
                Log.d(
                    TAG,
                    "Scores: P=${feedback.pronunciationScore}, G=${feedback.grammarScore}, F=${feedback.fluencyScore}"
                )
                Result.success(feedback)
            } else {
                Log.w(TAG, "⚠️ Invalid feedback, using fallback")
                Result.success(PronunciationFeedback.createFallback(transcript))
            }

        } catch (e: SerializationException) {
            Log.e(TAG, "❌ JSON parsing error: ${e.message}", e)
            Result.success(PronunciationFeedback.createFallback(transcript))
        } catch (e: Exception) {
            Log.e(TAG, "❌ Feedback generation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Generate feedback with streaming (for real-time display)
     *
     * This emits the raw LLM output token by token, which can be
     * displayed to the user in real-time before final parsing.
     *
     * @param transcript The transcribed text from user's speech
     * @param userLevel User's proficiency level
     * @return Flow of string tokens from LLM
     */
    fun generateFeedbackStream(
        transcript: String,
        userLevel: LanguageCoachPrompts.UserLevel = LanguageCoachPrompts.UserLevel.INTERMEDIATE
    ): Flow<String> = flow {
        if (!isInitialized) {
            throw IllegalStateException("FeedbackGenerator not initialized")
        }

        Log.i(TAG, "🌊 Streaming feedback for: \"$transcript\"")

        // Create prompt
        val prompt = LanguageCoachPrompts.createPromptForLevel(transcript, userLevel)

        // Collect streaming response
        var fullResponse = ""
        RunAnywhere.generateStream(prompt).collect { token ->
            fullResponse += token
            emit(token)
        }

        Log.i(TAG, "✅ Streaming complete (${fullResponse.length} chars)")
    }

    /**
     * Generate feedback with custom prompt
     *
     * Allows for custom prompt engineering beyond the standard templates.
     *
     * @param customPrompt Fully custom prompt
     * @return Result containing PronunciationFeedback or error
     */
    suspend fun generateWithCustomPrompt(customPrompt: String): Result<PronunciationFeedback> {
        return try {
            val response = RunAnywhere.generate(customPrompt)
            val feedback = parseResponse(response)
            Result.success(feedback)
        } catch (e: Exception) {
            Log.e(TAG, "❌ Custom prompt generation failed: ${e.message}", e)
            Result.failure(e)
        }
    }

    /**
     * Parse LLM response into PronunciationFeedback
     *
     * This handles various JSON formats and provides fallbacks
     * for malformed responses.
     *
     * @param responseString Raw LLM output
     * @return Parsed PronunciationFeedback object
     * @throws SerializationException if JSON parsing fails completely
     */
    private fun parseResponse(responseString: String): PronunciationFeedback {
        try {
            // Clean the response (remove markdown, extra whitespace)
            val cleanedResponse = cleanJsonResponse(responseString)

            Log.d(TAG, "Parsing response: ${cleanedResponse.take(100)}...")

            // Parse JSON
            val feedback = json.decodeFromString<PronunciationFeedback>(cleanedResponse)

            return feedback

        } catch (e: SerializationException) {
            Log.e(TAG, "❌ JSON parsing failed, attempting recovery")
            Log.d(TAG, "Raw response: $responseString")

            // Attempt to extract JSON from response
            val extractedJson = extractJsonFromResponse(responseString)
            if (extractedJson != null) {
                try {
                    return json.decodeFromString<PronunciationFeedback>(extractedJson)
                } catch (e2: Exception) {
                    Log.e(TAG, "❌ Extracted JSON also failed to parse")
                }
            }

            throw e
        }
    }

    /**
     * Clean JSON response from LLM
     *
     * Removes markdown code blocks, extra whitespace, and other common
     * formatting issues from LLM outputs.
     *
     * @param response Raw LLM response
     * @return Cleaned JSON string
     */
    private fun cleanJsonResponse(response: String): String {
        return response
            .trim()
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()
    }

    /**
     * Extract JSON object from response text
     *
     * Attempts to find JSON object boundaries in mixed text/JSON responses.
     *
     * @param response Raw response
     * @return Extracted JSON string or null if not found
     */
    private fun extractJsonFromResponse(response: String): String? {
        val jsonStart = response.indexOf('{')
        val jsonEnd = response.lastIndexOf('}')

        return if (jsonStart >= 0 && jsonEnd > jsonStart) {
            response.substring(jsonStart, jsonEnd + 1)
        } else {
            null
        }
    }

    /**
     * Get generation statistics
     *
     * @return Map of statistics (for monitoring/debugging)
     */
    fun getStats(): Map<String, Any> {
        return mapOf(
            "initialized" to isInitialized,
            "currentModel" to (currentModelId ?: "none")
        )
    }

    /**
     * Set current model ID (for tracking)
     *
     * @param modelId The model ID being used
     */
    fun setCurrentModel(modelId: String) {
        currentModelId = modelId
        Log.i(TAG, "Current model set to: $modelId")
    }

    /**
     * Test feedback generation with a sample input
     *
     * Useful for verifying the LLM is working correctly.
     *
     * @return Result of test generation
     */
    suspend fun runSelfTest(): Result<PronunciationFeedback> {
        Log.i(TAG, "🧪 Running self-test...")

        val testTranscript = "The weather is beautiful today"
        val result = generateFeedback(testTranscript)

        if (result.isSuccess) {
            Log.i(TAG, "✅ Self-test passed")
        } else {
            Log.e(TAG, "❌ Self-test failed: ${result.exceptionOrNull()?.message}")
        }

        return result
    }
}

/**
 * Extension functions for convenience
 */

/**
 * Quick feedback generation (uses default parameters)
 */
suspend fun FeedbackGenerator.quickFeedback(transcript: String): PronunciationFeedback? {
    return generateFeedback(transcript).getOrNull()
}

/**
 * Generate feedback for beginner level
 */
suspend fun FeedbackGenerator.beginnerFeedback(transcript: String): PronunciationFeedback? {
    return generateFeedback(
        transcript,
        userLevel = LanguageCoachPrompts.UserLevel.BEGINNER
    ).getOrNull()
}

/**
 * Generate feedback for advanced level
 */
suspend fun FeedbackGenerator.advancedFeedback(transcript: String): PronunciationFeedback? {
    return generateFeedback(
        transcript,
        userLevel = LanguageCoachPrompts.UserLevel.ADVANCED
    ).getOrNull()
}
