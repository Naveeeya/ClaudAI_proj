package com.pitchslap.app.conversation

import android.content.Context
import android.util.Log
import com.pitchslap.app.ai.FeedbackGenerator
import com.pitchslap.app.ai.WhisperService
import com.pitchslap.app.audio.AudioRecorder
import com.pitchslap.app.audio.TextToSpeechEngine
import com.pitchslap.app.data.ConversationTurn
import com.pitchslap.app.data.PronunciationFeedback
import com.pitchslap.app.logic.InterruptLogic
import com.pitchslap.app.logic.VoiceActivityDetection
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File

/**
 * Voice Conversation Manager - Orchestrates the complete voice conversation pipeline
 *
 * This class manages the full conversation flow:
 * 1. User speaks (detected by VAD)
 * 2. Audio is recorded
 * 3. Speech is transcribed to text
 * 4. LLM generates pronunciation feedback
 * 5. TTS speaks the feedback
 * 6. Process repeats (with barge-in support)
 *
 * State Machine:
 * IDLE → LISTENING → RECORDING → PROCESSING → AI_SPEAKING → IDLE
 *   ↑                                                      ↓
 *   └──────────────── BARGE_IN (anytime) ─────────────────┘
 *
 * @param context Application context
 * @param vad Voice Activity Detection instance
 * @param audioRecorder Audio recorder instance
 * @param whisperService Speech-to-text service
 * @param feedbackGenerator LLM feedback generator
 * @param tts Text-to-speech engine
 * @param interruptLogic Interrupt/barge-in logic
 */
class VoiceConversationManager(
    private val context: Context,
    private val vad: VoiceActivityDetection,
    private val audioRecorder: AudioRecorder,
    private val whisperService: WhisperService,
    private val feedbackGenerator: FeedbackGenerator,
    private val tts: TextToSpeechEngine,
    private val interruptLogic: InterruptLogic
) {
    companion object {
        private const val TAG = "PitchSlap_ConvoManager"
    }

    /**
     * Conversation states
     */
    enum class ConversationState {
        IDLE,           // Not active
        LISTENING,      // VAD active, waiting for user speech
        RECORDING,      // User speaking, recording audio
        PROCESSING,     // Transcribing + generating feedback
        AI_SPEAKING,    // AI providing feedback
        ERROR           // Error occurred
    }

    // Public state
    private val _conversationState = MutableStateFlow(ConversationState.IDLE)
    val conversationState: StateFlow<ConversationState> = _conversationState.asStateFlow()

    private val _currentFeedback = MutableStateFlow<PronunciationFeedback?>(null)
    val currentFeedback: StateFlow<PronunciationFeedback?> = _currentFeedback.asStateFlow()

    private val _conversationHistory = MutableStateFlow<List<ConversationTurn>>(emptyList())
    val conversationHistory: StateFlow<List<ConversationTurn>> = _conversationHistory.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _currentTranscript = MutableStateFlow<String>("")
    val currentTranscript: StateFlow<String> = _currentTranscript.asStateFlow()

    // Coroutine management
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var conversationJob: Job? = null
    private var vadMonitorJob: Job? = null
    private var bargeInMonitorJob: Job? = null

    // State
    private var isActive = false
    private var turnStartTime = 0L

    /**
     * Initialize all components
     */
    suspend fun initialize(): Boolean {
        return try {
            Log.i(TAG, "🔧 Initializing VoiceConversationManager...")

            // Initialize Whisper service
            if (!whisperService.initialize()) {
                Log.e(TAG, "❌ WhisperService initialization failed")
                return false
            }

            // Initialize Feedback Generator
            if (!feedbackGenerator.initialize()) {
                Log.e(TAG, "❌ FeedbackGenerator initialization failed")
                return false
            }

            // Initialize TTS
            var ttsReady = false
            tts.initialize(
                onReady = {
                    ttsReady = true
                    Log.i(TAG, "✅ TTS initialized")
                },
                onError = { error ->
                    Log.e(TAG, "❌ TTS initialization failed: $error")
                }
            )

            // Wait for TTS (with timeout)
            withTimeoutOrNull(5000) {
                while (!ttsReady) {
                    delay(100)
                }
            }

            if (!ttsReady) {
                Log.e(TAG, "❌ TTS initialization timeout")
                return false
            }

            Log.i(TAG, "✅ VoiceConversationManager initialized successfully")
            true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Initialization failed: ${e.message}", e)
            false
        }
    }

    /**
     * Start the conversation session
     */
    suspend fun startConversation() {
        if (isActive) {
            Log.w(TAG, "⚠️ Conversation already active")
            return
        }

        try {
            Log.i(TAG, "🚀 Starting conversation session...")

            isActive = true
            _conversationState.value = ConversationState.LISTENING

            // Start VAD
            vad.start()

            // Start monitoring VAD for speech
            startVADMonitoring()

            // Start monitoring for barge-ins
            startBargeInMonitoring()

            Log.i(TAG, "✅ Conversation session started - Ready for user input")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start conversation: ${e.message}", e)
            _conversationState.value = ConversationState.ERROR
            _errorMessage.value = e.message
        }
    }

    /**
     * Monitor VAD for user speech
     */
    private fun startVADMonitoring() {
        vadMonitorJob = scope.launch {
            vad.isUserSpeaking.collect { isSpeaking ->
                if (!isActive) return@collect

                when {
                    isSpeaking && _conversationState.value == ConversationState.LISTENING -> {
                        // User started speaking
                        handleUserStartedSpeaking()
                    }

                    !isSpeaking && _conversationState.value == ConversationState.RECORDING -> {
                        // User stopped speaking
                        handleUserStoppedSpeaking()
                    }
                }
            }
        }
    }

    /**
     * Handle user starting to speak
     */
    private fun handleUserStartedSpeaking() {
        Log.i(TAG, "🗣️ User started speaking")

        _conversationState.value = ConversationState.RECORDING
        turnStartTime = System.currentTimeMillis()

        // Start recording
        audioRecorder.startRecording()
    }

    /**
     * Handle user stopped speaking
     */
    private fun handleUserStoppedSpeaking() {
        scope.launch {
            try {
                Log.i(TAG, "🔇 User stopped speaking - Processing...")

                // Stop recording
                audioRecorder.stopRecording()

                // Get audio buffer
                val audioData = audioRecorder.getAudioBuffer()

                if (audioData.isEmpty()) {
                    Log.w(TAG, "⚠️ No audio data captured")
                    _conversationState.value = ConversationState.LISTENING
                    return@launch
                }

                // Save to temporary file for transcription
                val tempFile = File(context.cacheDir, "temp_audio_${System.currentTimeMillis()}.wav")
                audioRecorder.saveToWAV(tempFile)

                // Process the audio
                processUserSpeech(tempFile)

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error processing user speech: ${e.message}", e)
                _conversationState.value = ConversationState.ERROR
                _errorMessage.value = e.message
            }
        }
    }

    /**
     * Process user speech through the AI pipeline
     */
    private suspend fun processUserSpeech(audioFile: File) {
        try {
            _conversationState.value = ConversationState.PROCESSING

            Log.i(TAG, "🎙️ Step 1: Transcribing speech...")

            // Transcribe audio to text
            val transcript = whisperService.transcribe(audioFile)
            _currentTranscript.value = transcript

            Log.i(TAG, "✅ Transcribed: \"$transcript\"")

            if (transcript.isBlank()) {
                Log.w(TAG, "⚠️ Empty transcript - returning to listening")
                _conversationState.value = ConversationState.LISTENING
                audioRecorder.clearBuffer()
                audioFile.delete()
                return
            }

            Log.i(TAG, "🤖 Step 2: Generating AI feedback...")

            // Generate feedback
            val feedbackResult = feedbackGenerator.generateFeedback(transcript)

            if (feedbackResult.isFailure) {
                Log.e(TAG, "❌ Feedback generation failed: ${feedbackResult.exceptionOrNull()?.message}")
                _conversationState.value = ConversationState.ERROR
                _errorMessage.value = "Failed to generate feedback"
                audioFile.delete()
                return
            }

            val feedback = feedbackResult.getOrNull()
            if (feedback == null || !feedback.isValid()) {
                Log.w(TAG, "⚠️ Invalid feedback - using fallback")
                _currentFeedback.value = PronunciationFeedback.createFallback(transcript)
            } else {
                _currentFeedback.value = feedback
                Log.i(
                    TAG,
                    "✅ Feedback generated - Scores: P=${feedback.pronunciationScore}, G=${feedback.grammarScore}, F=${feedback.fluencyScore}"
                )
            }

            // Create conversation turn
            val turn = ConversationTurn(
                userTranscript = transcript,
                aiFeedback = _currentFeedback.value!!,
                durationMs = System.currentTimeMillis() - turnStartTime
            )

            // Add to history
            _conversationHistory.value = _conversationHistory.value + turn
            Log.i(TAG, "📝 Turn added to history (total: ${_conversationHistory.value.size})")

            // Speak the feedback
            speakFeedback(_currentFeedback.value!!)

            // Clean up
            audioRecorder.clearBuffer()
            audioFile.delete()

        } catch (e: CancellationException) {
            Log.i(TAG, "⏹️ Processing cancelled (likely barge-in)")
            _conversationState.value = ConversationState.LISTENING
            audioRecorder.clearBuffer()
            audioFile.delete()
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error in processUserSpeech: ${e.message}", e)
            _conversationState.value = ConversationState.ERROR
            _errorMessage.value = e.message
            audioFile.delete()
        }
    }

    /**
     * Speak AI feedback using TTS
     */
    private fun speakFeedback(feedback: PronunciationFeedback) {
        _conversationState.value = ConversationState.AI_SPEAKING

        Log.i(TAG, "🗣️ Step 3: Speaking feedback...")

        tts.speak(
            text = feedback.feedback,
            onStart = {
                Log.d(TAG, "🔊 TTS started")
            },
            onDone = {
                Log.i(TAG, "✅ Feedback spoken - Returning to listening")
                _conversationState.value = ConversationState.LISTENING
            },
            onError = { error ->
                Log.e(TAG, "❌ TTS error: $error")
                _conversationState.value = ConversationState.LISTENING
            }
        )
    }

    /**
     * Monitor for barge-in events
     */
    private fun startBargeInMonitoring() {
        bargeInMonitorJob = scope.launch {
            interruptLogic.bargeInEvent.collect { event ->
                Log.i(TAG, "🚨 BARGE-IN DETECTED at ${event.timestamp}")
                handleBargeIn()
            }
        }
    }

    /**
     * Handle barge-in interrupt
     */
    private fun handleBargeIn() {
        try {
            Log.i(TAG, "⚡ Handling barge-in...")

            // Stop TTS immediately
            tts.stop()

            // Cancel any ongoing processing
            conversationJob?.cancel()

            // Return to listening
            _conversationState.value = ConversationState.LISTENING

            Log.i(TAG, "✅ Barge-in handled - Ready for new input")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error handling barge-in: ${e.message}", e)
        }
    }

    /**
     * Stop the conversation session
     */
    fun stopConversation() {
        if (!isActive) {
            Log.w(TAG, "⚠️ Conversation not active")
            return
        }

        try {
            Log.i(TAG, "🛑 Stopping conversation session...")

            isActive = false

            // Cancel monitoring jobs
            vadMonitorJob?.cancel()
            bargeInMonitorJob?.cancel()
            conversationJob?.cancel()

            // Stop all components
            audioRecorder.stopRecording()
            audioRecorder.clearBuffer()
            tts.stop()
            vad.stop()

            // Reset state
            _conversationState.value = ConversationState.IDLE

            Log.i(TAG, "✅ Conversation session stopped")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping conversation: ${e.message}", e)
        }
    }

    /**
     * Clear conversation history
     */
    fun clearHistory() {
        _conversationHistory.value = emptyList()
        _currentFeedback.value = null
        _currentTranscript.value = ""
        Log.i(TAG, "🗑️ Conversation history cleared")
    }

    /**
     * Get conversation statistics
     */
    fun getStats(): ConversationStats {
        val history = _conversationHistory.value

        return ConversationStats(
            totalTurns = history.size,
            averagePronunciationScore = history.map { it.aiFeedback.pronunciationScore }.average().toInt(),
            averageGrammarScore = history.map { it.aiFeedback.grammarScore }.average().toInt(),
            averageFluencyScore = history.map { it.aiFeedback.fluencyScore }.average().toInt(),
            totalDurationMs = history.sumOf { it.durationMs },
            currentState = _conversationState.value
        )
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            Log.i(TAG, "🧹 Cleaning up VoiceConversationManager...")

            stopConversation()

            scope.cancel()
            tts.shutdown()
            whisperService.cleanup()
            audioRecorder.cleanup()

            Log.i(TAG, "✅ Cleanup complete")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during cleanup: ${e.message}", e)
        }
    }

    /**
     * Conversation statistics data class
     */
    data class ConversationStats(
        val totalTurns: Int,
        val averagePronunciationScore: Int,
        val averageGrammarScore: Int,
        val averageFluencyScore: Int,
        val totalDurationMs: Long,
        val currentState: ConversationState
    ) {
        val totalDurationSeconds: Long
            get() = totalDurationMs / 1000

        val averageOverallScore: Int
            get() = (averagePronunciationScore + averageGrammarScore + averageFluencyScore) / 3
    }
}
