package com.pitchslap.app.ai

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Speech-to-Text Service using Android SpeechRecognizer
 *
 * This service provides speech-to-text transcription functionality.
 * While named WhisperService for consistency with the roadmap,
 * it currently uses Android's native SpeechRecognizer API.
 *
 * **Note**: Android SpeechRecognizer requires internet connection
 * by default. For fully offline operation, consider integrating
 * a dedicated offline STT library like Vosk or Whisper GGML in the future.
 *
 * @param context Application context
 */
class WhisperService(private val context: Context) {

    companion object {
        private const val TAG = "PitchSlap_WhisperService"
    }

    private var speechRecognizer: SpeechRecognizer? = null

    /**
     * Initialize the speech recognition service
     *
     * @return true if initialization successful, false otherwise
     */
    fun initialize(): Boolean {
        return try {
            if (!SpeechRecognizer.isRecognitionAvailable(context)) {
                Log.e(TAG, "❌ Speech recognition not available on this device")
                return false
            }

            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            Log.i(TAG, "✅ WhisperService initialized")
            true
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to initialize: ${e.message}", e)
            false
        }
    }

    /**
     * Transcribe audio file to text
     *
     * **Current Implementation**: Uses live microphone input via SpeechRecognizer.
     * This is a limitation of Android's SpeechRecognizer API which doesn't
     * support transcribing from audio files directly.
     *
     * **Future Enhancement**: Integrate Whisper GGML or Vosk for file-based
     * transcription and fully offline operation.
     *
     * @param audioFile WAV audio file to transcribe (currently not used - limitation)
     * @return Transcribed text
     * @throws IllegalStateException if service not initialized
     * @throws Exception if transcription fails
     */
    suspend fun transcribe(audioFile: File): String {
        Log.w(TAG, "⚠️ Note: Android SpeechRecognizer doesn't support file transcription")
        Log.w(TAG, "⚠️ This will listen to live audio instead. Consider Whisper GGML for file support.")

        return transcribeLive()
    }

    /**
     * Transcribe live audio from microphone
     *
     * This method uses Android's SpeechRecognizer to listen to the microphone
     * and return the transcribed text.
     *
     * @param languageCode Language code (default: en-US)
     * @param maxResults Maximum number of results to return
     * @return Transcribed text
     * @throws IllegalStateException if service not initialized
     * @throws Exception if transcription fails
     */
    suspend fun transcribeLive(
        languageCode: String = "en-US",
        maxResults: Int = 1
    ): String = suspendCancellableCoroutine { continuation ->

        val recognizer = speechRecognizer ?: run {
            continuation.resumeWithException(IllegalStateException("WhisperService not initialized"))
            return@suspendCancellableCoroutine
        }

        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageCode)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, maxResults)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, false)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

        recognizer.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                Log.d(TAG, "🎤 Ready for speech")
            }

            override fun onBeginningOfSpeech() {
                Log.d(TAG, "🗣️ Speech started")
            }

            override fun onRmsChanged(rmsdB: Float) {
                // RMS updates - can be used for visualization
            }

            override fun onBufferReceived(buffer: ByteArray?) {
                // Audio buffer received
            }

            override fun onEndOfSpeech() {
                Log.d(TAG, "🔇 Speech ended")
            }

            override fun onError(error: Int) {
                val errorMessage = when (error) {
                    SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                    SpeechRecognizer.ERROR_CLIENT -> "Client error"
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                    SpeechRecognizer.ERROR_NETWORK -> "Network error"
                    SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                    SpeechRecognizer.ERROR_NO_MATCH -> "No speech match found"
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                    SpeechRecognizer.ERROR_SERVER -> "Server error"
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "No speech detected"
                    else -> "Unknown error: $error"
                }

                Log.e(TAG, "❌ Recognition error: $errorMessage")

                if (continuation.isActive) {
                    continuation.resumeWithException(Exception("Speech recognition failed: $errorMessage"))
                }
            }

            override fun onResults(results: Bundle?) {
                val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)

                if (matches.isNullOrEmpty()) {
                    Log.e(TAG, "❌ No recognition results")
                    if (continuation.isActive) {
                        continuation.resumeWithException(Exception("No transcription results"))
                    }
                    return
                }

                val transcript = matches[0]
                Log.i(TAG, "✅ Transcribed: \"$transcript\"")

                if (continuation.isActive) {
                    continuation.resume(transcript)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                // Partial results - could be used for real-time display
            }

            override fun onEvent(eventType: Int, params: Bundle?) {
                // Recognition events
            }
        })

        // Start listening
        try {
            recognizer.startListening(intent)
            Log.i(TAG, "🎙️ Started listening for speech")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start listening: ${e.message}", e)
            if (continuation.isActive) {
                continuation.resumeWithException(e)
            }
        }

        // Handle cancellation
        continuation.invokeOnCancellation {
            try {
                recognizer.stopListening()
                Log.i(TAG, "🛑 Recognition cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error stopping recognizer: ${e.message}")
            }
        }
    }

    /**
     * Cancel ongoing transcription
     */
    fun cancel() {
        try {
            speechRecognizer?.cancel()
            Log.i(TAG, "🛑 Transcription cancelled")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error cancelling: ${e.message}")
        }
    }

    /**
     * Stop listening (gracefully)
     */
    fun stop() {
        try {
            speechRecognizer?.stopListening()
            Log.i(TAG, "⏹️ Stopped listening")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping: ${e.message}")
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        try {
            speechRecognizer?.destroy()
            speechRecognizer = null
            Log.i(TAG, "WhisperService cleanup complete")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Cleanup error: ${e.message}")
        }
    }

    /**
     * Check if speech recognition is available on this device
     */
    fun isAvailable(): Boolean {
        return SpeechRecognizer.isRecognitionAvailable(context)
    }
}

/**
 * Future enhancement notes:
 *
 * For true offline Whisper integration, consider these options:
 *
 * 1. **Whisper GGML**: Whisper model in GGML format
 *    - Pros: True offline, file transcription, accurate
 *    - Cons: Larger model size (~100MB+), C++ integration required
 *    - GitHub: https://github.com/ggerganov/whisper.cpp
 *
 * 2. **Vosk**: Lightweight offline STT
 *    - Pros: Small models (50MB), easy Android integration, offline
 *    - Cons: Less accurate than Whisper
 *    - Website: https://alphacephei.com/vosk/
 *
 * 3. **WhisperKit** (iOS/Swift) / **Whisper.cpp** (Android/JNI)
 *    - Pros: Official Whisper, best accuracy
 *    - Cons: Complex integration, larger size
 *
 * For this hackathon MVP, Android SpeechRecognizer provides a quick,
 * working solution that demonstrates the full voice pipeline.
 */
