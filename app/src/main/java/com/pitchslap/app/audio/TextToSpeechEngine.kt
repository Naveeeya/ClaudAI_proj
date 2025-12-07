package com.pitchslap.app.audio

import android.content.Context
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.pitchslap.app.logic.InterruptLogic
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.*
import kotlin.coroutines.resume

/**
 * Text-to-Speech Engine with Barge-In Support
 *
 * This class provides AI voice output using Android's native TTS,
 * with integration to InterruptLogic for barge-in interruption support.
 *
 * Key Features:
 * - Natural voice synthesis
 * - Immediate interrupt support (barge-in)
 * - State tracking for UI integration
 * - Callbacks for lifecycle events
 * - Integration with InterruptLogic
 *
 * @param context Application context
 * @param interruptLogic Optional InterruptLogic for barge-in detection
 */
class TextToSpeechEngine(
    private val context: Context,
    private val interruptLogic: InterruptLogic? = null
) {

    companion object {
        private const val TAG = "PitchSlap_TTS"
        private const val DEFAULT_SPEECH_RATE = 1.0f
        private const val DEFAULT_PITCH = 1.0f
    }

    // TTS State
    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _isInitialized = MutableStateFlow(false)
    val isInitialized: StateFlow<Boolean> = _isInitialized.asStateFlow()

    // TTS Components
    private var textToSpeech: TextToSpeech? = null
    private var currentUtteranceId: String? = null

    // Callbacks for current utterance
    private var currentOnStart: (() -> Unit)? = null
    private var currentOnDone: (() -> Unit)? = null
    private var currentOnError: ((String) -> Unit)? = null

    // Coroutine scope for barge-in monitoring
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var bargeInMonitorJob: Job? = null

    /**
     * Initialize the TTS engine
     *
     * This should be called before any speak() operations.
     * Initialization is asynchronous and completion is signaled via the callback.
     *
     * @param onReady Callback invoked when TTS is ready to use
     * @param onError Callback invoked if initialization fails
     */
    fun initialize(
        onReady: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (_isInitialized.value) {
            Log.w(TAG, "TTS already initialized")
            onReady()
            return
        }

        try {
            textToSpeech = TextToSpeech(context) { status ->
                when (status) {
                    TextToSpeech.SUCCESS -> {
                        setupTTS()
                        _isInitialized.value = true
                        Log.i(TAG, "✅ TTS initialized successfully")
                        onReady()
                    }

                    else -> {
                        val errorMsg = "TTS initialization failed with status: $status"
                        Log.e(TAG, "❌ $errorMsg")
                        onError(errorMsg)
                    }
                }
            }
        } catch (e: Exception) {
            val errorMsg = "TTS initialization exception: ${e.message}"
            Log.e(TAG, "❌ $errorMsg", e)
            onError(errorMsg)
        }
    }

    /**
     * Setup TTS configuration
     */
    private fun setupTTS() {
        textToSpeech?.apply {
            // Set language to US English
            val result = setLanguage(Locale.US)

            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                Log.w(TAG, "⚠️ Language not fully supported")
            }

            // Set default speech parameters
            setSpeechRate(DEFAULT_SPEECH_RATE)
            setPitch(DEFAULT_PITCH)

            // Set up progress listener
            setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.d(TAG, "🗣️ TTS started: $utteranceId")
                    _isSpeaking.value = true
                    currentOnStart?.invoke()
                }

                override fun onDone(utteranceId: String?) {
                    Log.d(TAG, "✅ TTS completed: $utteranceId")
                    _isSpeaking.value = false
                    currentUtteranceId = null

                    // Notify InterruptLogic that AI stopped speaking
                    interruptLogic?.stopAiSpeech()

                    currentOnDone?.invoke()
                    clearCallbacks()
                }

                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "❌ TTS error: $utteranceId")
                    _isSpeaking.value = false
                    currentUtteranceId = null
                    interruptLogic?.stopAiSpeech()

                    currentOnError?.invoke("TTS Error")
                    clearCallbacks()
                }

                override fun onStop(utteranceId: String?, interrupted: Boolean) {
                    Log.d(TAG, "⏹️ TTS stopped: $utteranceId (interrupted: $interrupted)")
                    _isSpeaking.value = false
                    currentUtteranceId = null
                    interruptLogic?.stopAiSpeech()
                    // onStop doesn't trigger onDone/onError callbacks by design
                    clearCallbacks()
                }
            })
        }
    }

    private fun clearCallbacks() {
        currentOnStart = null
        currentOnDone = null
        currentOnError = null
    }

    /**
     * Speak text with callbacks
     *
     * This is the main method for synthesizing speech. It integrates with
     * InterruptLogic to enable barge-in interruption.
     *
     * @param text Text to speak
     * @param onStart Callback when speech starts
     * @param onDone Callback when speech completes
     * @param onError Callback if speech fails
     * @param speechRate Speech rate (0.5 = slow, 1.0 = normal, 2.0 = fast)
     * @param pitch Voice pitch (0.5 = low, 1.0 = normal, 2.0 = high)
     */
    fun speak(
        text: String,
        onStart: () -> Unit = {},
        onDone: () -> Unit = {},
        onError: (String) -> Unit = {},
        speechRate: Float = DEFAULT_SPEECH_RATE,
        pitch: Float = DEFAULT_PITCH
    ) {
        if (!_isInitialized.value) {
            val error = "TTS not initialized"
            Log.e(TAG, "❌ $error")
            onError(error)
            return
        }

        if (text.isBlank()) {
            val error = "Cannot speak empty text"
            Log.w(TAG, "⚠️ $error")
            onError(error)
            return
        }

        try {
            // Stop any ongoing speech
            stop()

            // Generate unique utterance ID
            currentUtteranceId = "utterance_${System.currentTimeMillis()}"

            // Store callbacks
            currentOnStart = onStart
            currentOnDone = onDone
            currentOnError = onError

            // Configure TTS parameters
            textToSpeech?.apply {
                setSpeechRate(speechRate)
                setPitch(pitch)

                // Speak the text
                val result = speak(
                    text,
                    TextToSpeech.QUEUE_FLUSH,  // Clear queue and speak immediately
                    null,
                    currentUtteranceId
                )

                if (result == TextToSpeech.SUCCESS) {
                    Log.i(TAG, "🗣️ Speaking: \"${text.take(50)}${if (text.length > 50) "..." else ""}\"")

                    // Notify InterruptLogic that AI started speaking
                    interruptLogic?.startAiSpeech()

                    // Start monitoring for barge-ins
                    startBargeInMonitoring()

                    // onStart will be called by listener
                } else {
                    val error = "TTS speak failed with result: $result"
                    Log.e(TAG, "❌ $error")
                    onError(error)
                    clearCallbacks()
                }
            }
        } catch (e: Exception) {
            val error = "TTS speak exception: ${e.message}"
            Log.e(TAG, "❌ $error", e)
            onError(error)
            clearCallbacks()
        }
    }

    /**
     * Speak text and suspend until completion
     *
     * This is a suspend version of speak() that waits for completion.
     *
     * @param text Text to speak
     * @return true if successful, false otherwise
     */
    suspend fun speakAndWait(text: String): Boolean = suspendCancellableCoroutine { continuation ->
        speak(
            text = text,
            onStart = {},
            onDone = {
                if (continuation.isActive) {
                    continuation.resume(true)
                }
            },
            onError = {
                if (continuation.isActive) {
                    continuation.resume(false)
                }
            }
        )

        // Handle cancellation
        continuation.invokeOnCancellation {
            stop()
        }
    }

    /**
     * Stop speaking immediately (for barge-in interruption)
     *
     * This method provides immediate cutoff of AI speech when
     * the user starts speaking (barge-in).
     */
    fun stop() {
        try {
            if (_isSpeaking.value) {
                textToSpeech?.stop()
                Log.i(TAG, "⏹️ TTS stopped (barge-in or manual)")
                _isSpeaking.value = false
                currentUtteranceId = null

                // Notify InterruptLogic
                interruptLogic?.stopAiSpeech()

                // Stop monitoring
                stopBargeInMonitoring()
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping TTS: ${e.message}")
        }
    }

    /**
     * Start monitoring for barge-in events
     *
     * Listens to InterruptLogic for user interruptions and
     * automatically stops TTS when detected.
     */
    private fun startBargeInMonitoring() {
        if (interruptLogic == null) return

        bargeInMonitorJob = scope.launch {
            interruptLogic.bargeInEvent.collect { event ->
                Log.i(TAG, "🚨 Barge-in detected! Stopping TTS immediately")
                stop()
            }
        }
    }

    /**
     * Stop monitoring for barge-in events
     */
    private fun stopBargeInMonitoring() {
        bargeInMonitorJob?.cancel()
        bargeInMonitorJob = null
    }

    /**
     * Set speech rate
     *
     * @param rate Speech rate (0.5 = slow, 1.0 = normal, 2.0 = fast)
     */
    fun setSpeechRate(rate: Float) {
        textToSpeech?.setSpeechRate(rate)
        Log.d(TAG, "Speech rate set to: $rate")
    }

    /**
     * Set voice pitch
     *
     * @param pitch Voice pitch (0.5 = low, 1.0 = normal, 2.0 = high)
     */
    fun setPitch(pitch: Float) {
        textToSpeech?.setPitch(pitch)
        Log.d(TAG, "Pitch set to: $pitch")
    }

    /**
     * Check if TTS is currently speaking
     */
    fun isSpeaking(): Boolean {
        return textToSpeech?.isSpeaking == true
    }

    /**
     * Get available voices
     */
    fun getAvailableVoices(): Set<android.speech.tts.Voice>? {
        return textToSpeech?.voices
    }

    /**
     * Set specific voice
     *
     * @param voice Voice object from getAvailableVoices()
     */
    fun setVoice(voice: android.speech.tts.Voice) {
        textToSpeech?.voice = voice
        Log.d(TAG, "Voice set to: ${voice.name}")
    }

    /**
     * Cleanup resources
     *
     * This should be called when TTS is no longer needed (e.g., in onDestroy)
     */
    fun shutdown() {
        try {
            stop()
            stopBargeInMonitoring()

            textToSpeech?.shutdown()
            textToSpeech = null

            _isInitialized.value = false
            Log.i(TAG, "TTS shutdown complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error during shutdown: ${e.message}")
        }
    }

    /**
     * Get TTS engine info
     */
    fun getEngineInfo(): Map<String, Any> {
        return mapOf(
            "initialized" to _isInitialized.value,
            "speaking" to _isSpeaking.value,
            "engine" to (textToSpeech?.defaultEngine ?: "unknown"),
            "hasBargeIn" to (interruptLogic != null)
        )
    }
}

/**
 * Extension functions for convenience
 */

/**
 * Quick speak (uses default parameters)
 */
fun TextToSpeechEngine.quickSpeak(text: String) {
    speak(text)
}

/**
 * Speak slowly (for beginners)
 */
fun TextToSpeechEngine.speakSlowly(text: String) {
    speak(text, speechRate = 0.8f)
}

/**
 * Speak quickly
 */
fun TextToSpeechEngine.speakFast(text: String) {
    speak(text, speechRate = 1.3f)
}
