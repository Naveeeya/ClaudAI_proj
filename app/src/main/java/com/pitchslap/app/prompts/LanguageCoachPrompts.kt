package com.pitchslap.app.prompts

/**
 * Language Coach System Prompts and JSON Schema
 *
 * This object defines the AI personality, behavior, and output format
 * for the pronunciation feedback system.
 */
object LanguageCoachPrompts {

    /**
     * Core system prompt defining the AI's personality and behavior
     */
    val SYSTEM_PROMPT = """
        You are an expert pronunciation coach AND a friendly conversational partner.
        Your role is to:
        1. Reply to the user's message naturally (like a human friend).
        2. Provide instant, encouraging feedback on their pronunciation/grammar.
        
        **Your Personality**:
        - Friendly, human-like, and conversational
        - Encouraging and supportive
        - Specific and actionable in feedback
        
        **Scoring Guidelines**:
        - Pronunciation (0-100): Clarity, accent, sound production
        - Grammar (0-100): Sentence structure, word choice, tense usage
        - Fluency (0-100): Speaking rhythm, naturalness, confidence
        
        **Response Style**:
        - ALWAYS start by replying to what the user said conversationally.
        - THEN provide brief pronunciation/grammar feedback.
        - Ask a follow-up question to keep the conversation going.
        - Keep total response brief (3-4 sentences maximum).
        
        **Output Format**:
        You MUST respond ONLY with valid JSON in this exact format:
        {
            "transcript": "what the user said",
            "pronunciationScore": 0-100,
            "grammarScore": 0-100,
            "fluencyScore": 0-100,
            "feedback": "Conversational reply + feedback + follow-up question",
            "corrections": ["specific tip 1", "specific tip 2"],
            "exampleSentence": "a practice sentence for improvement"
        }
        
        Do NOT include any text outside the JSON object.
        Do NOT use markdown formatting or code blocks.
        ONLY output the raw JSON.
    """.trimIndent()

    /**
     * Few-shot examples to improve consistency
     */
    private val FEW_SHOT_EXAMPLES = """
        Example 1:
        User said: "I go to store yesterday"
        Your response:
        {"transcript": "I go to store yesterday", "pronunciationScore": 85, "grammarScore": 60, "fluencyScore": 75, "feedback": "Oh, did you buy anything nice? Quick tip: use 'went' for past tense. Say 'I went to the store'.", "corrections": ["Use 'went' instead of 'go' for past tense", "Add article 'the' before 'store'"], "exampleSentence": "I went to the store yesterday to buy groceries."}
        
        Example 2:
        User said: "The weather is beautiful today"
        Your response:
        {"transcript": "The weather is beautiful today", "pronunciationScore": 95, "grammarScore": 100, "fluencyScore": 90, "feedback": "It really is! I love sunny days. Your pronunciation was perfect. Do you have plans to go outside?", "corrections": [], "exampleSentence": "The weather is beautiful, so let's go outside and enjoy it."}
        
        Example 3:
        User said: "She don't like pizza"
        Your response:
        {"transcript": "She don't like pizza", "pronunciationScore": 90, "grammarScore": 70, "fluencyScore": 85, "feedback": "Really? Who doesn't like pizza? Just remember: say 'doesn't' for 'she'. Try saying 'She doesn't like pizza'.", "corrections": ["Change 'don't' to 'doesn't' with singular subjects (she, he, it)"], "exampleSentence": "She doesn't like pizza, but she loves pasta."}
    """.trimIndent()

    /**
     * Create a complete prompt for the LLM
     *
     * @param userTranscript The transcribed text from the user's speech
     * @param conversationHistory Optional list of previous exchanges for context
     * @return Complete prompt ready for LLM generation
     */
    fun createPrompt(
        userTranscript: String,
        conversationHistory: List<String> = emptyList()
    ): String {
        return buildString {
            // System prompt
            appendLine(SYSTEM_PROMPT)
            appendLine()

            // Few-shot examples
            appendLine("Here are examples of correct responses:")
            appendLine(FEW_SHOT_EXAMPLES)
            appendLine()

            // Conversation history (if any)
            if (conversationHistory.isNotEmpty()) {
                appendLine("Previous conversation:")
                conversationHistory.takeLast(3).forEach { exchange ->
                    appendLine(exchange)
                }
                appendLine()
            }

            // Current user input
            appendLine("User said: \"$userTranscript\"")
            appendLine()
            appendLine("Your response (JSON only):")
        }
    }

    /**
     * Create a simplified prompt for faster responses (fewer examples)
     *
     * @param userTranscript The transcribed text from the user's speech
     * @return Simplified prompt
     */
    fun createQuickPrompt(userTranscript: String): String {
        return buildString {
            appendLine(SYSTEM_PROMPT)
            appendLine()
            appendLine("User said: \"$userTranscript\"")
            appendLine()
            appendLine("Your response (JSON only):")
        }
    }

    /**
     * Create a prompt focused on a specific area
     *
     * @param userTranscript The transcribed text from the user's speech
     * @param focusArea The area to focus on (pronunciation, grammar, or fluency)
     * @return Focused prompt
     */
    fun createFocusedPrompt(
        userTranscript: String,
        focusArea: FocusArea
    ): String {
        val focusInstruction = when (focusArea) {
            FocusArea.PRONUNCIATION -> "Pay special attention to pronunciation and clarity. Give detailed phonetic feedback."
            FocusArea.GRAMMAR -> "Focus primarily on grammatical correctness. Explain grammar rules clearly."
            FocusArea.FLUENCY -> "Evaluate the naturalness and flow of speech. Suggest rhythm improvements."
        }

        return buildString {
            appendLine(SYSTEM_PROMPT)
            appendLine()
            appendLine("SPECIAL FOCUS: $focusInstruction")
            appendLine()
            appendLine("User said: \"$userTranscript\"")
            appendLine()
            appendLine("Your response (JSON only):")
        }
    }

    /**
     * Create a prompt for beginner-level users (more encouraging)
     *
     * @param userTranscript The transcribed text from the user's speech
     * @return Beginner-friendly prompt
     */
    fun createBeginnerPrompt(userTranscript: String): String {
        return buildString {
            appendLine(SYSTEM_PROMPT)
            appendLine()
            appendLine("IMPORTANT: This is a beginner learner. Be extra encouraging and provide simple, clear feedback. Focus on one major improvement only.")
            appendLine()
            appendLine("User said: \"$userTranscript\"")
            appendLine()
            appendLine("Your response (JSON only):")
        }
    }

    /**
     * Create a prompt for advanced-level users (more detailed)
     *
     * @param userTranscript The transcribed text from the user's speech
     * @return Advanced-level prompt
     */
    fun createAdvancedPrompt(userTranscript: String): String {
        return buildString {
            appendLine(SYSTEM_PROMPT)
            appendLine()
            appendLine("IMPORTANT: This is an advanced learner. Provide nuanced feedback on subtle pronunciation differences, advanced grammar, and idiomatic expressions.")
            appendLine()
            appendLine("User said: \"$userTranscript\"")
            appendLine()
            appendLine("Your response (JSON only):")
        }
    }

    /**
     * Focus areas for targeted feedback
     */
    enum class FocusArea {
        PRONUNCIATION,
        GRAMMAR,
        FLUENCY
    }

    /**
     * User proficiency levels
     */
    enum class UserLevel {
        BEGINNER,      // A1-A2
        INTERMEDIATE,  // B1-B2
        ADVANCED       // C1-C2
    }

    /**
     * Helper function to select appropriate prompt based on user level
     *
     * @param userTranscript The transcribed text
     * @param userLevel The user's proficiency level
     * @return Appropriate prompt for the user level
     */
    fun createPromptForLevel(
        userTranscript: String,
        userLevel: UserLevel
    ): String {
        return when (userLevel) {
            UserLevel.BEGINNER -> createBeginnerPrompt(userTranscript)
            UserLevel.INTERMEDIATE -> createPrompt(userTranscript)
            UserLevel.ADVANCED -> createAdvancedPrompt(userTranscript)
        }
    }

    /**
     * JSON schema documentation for reference
     */
    const val JSON_SCHEMA = """
    {
        "type": "object",
        "required": ["transcript", "pronunciationScore", "grammarScore", "fluencyScore", "feedback", "corrections", "exampleSentence"],
        "properties": {
            "transcript": {
                "type": "string",
                "description": "The text that the user spoke"
            },
            "pronunciationScore": {
                "type": "integer",
                "minimum": 0,
                "maximum": 100,
                "description": "Score for pronunciation quality"
            },
            "grammarScore": {
                "type": "integer",
                "minimum": 0,
                "maximum": 100,
                "description": "Score for grammatical correctness"
            },
            "fluencyScore": {
                "type": "integer",
                "minimum": 0,
                "maximum": 100,
                "description": "Score for speech fluency"
            },
            "feedback": {
                "type": "string",
                "description": "Brief encouraging feedback (2-3 sentences max)"
            },
            "corrections": {
                "type": "array",
                "items": {
                    "type": "string"
                },
                "description": "List of specific corrections or tips"
            },
            "exampleSentence": {
                "type": "string",
                "description": "A practice sentence for improvement"
            }
        }
    }
    """
}
