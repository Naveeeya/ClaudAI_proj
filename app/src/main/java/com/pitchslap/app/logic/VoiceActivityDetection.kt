package com.pitchslap.app.logic

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.sqrt

/**
 * Voice Activity Detection (VAD) System
 *
 * Streams audio from microphone and detects when user is speaking based on
 * Root Mean Square (RMS) amplitude analysis.
 */
class VoiceActivityDetection {

    companion object {
        private const val TAG = "PitchSlap_VAD"

        // Audio Configuration
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2 // Safety margin

        // VAD Thresholds - Use 2000 for real devices, lower for emulator
        private const val RMS_THRESHOLD = 2000.0
        private const val SILENCE_TIMEOUT_MS = 500L
    }

    // State Management
    private val _isUserSpeaking = MutableStateFlow(false)
    val isUserSpeaking: StateFlow<Boolean> = _isUserSpeaking.asStateFlow()

    // VAD Active State (for UI observation)
    private val _isVADActive = MutableStateFlow(false)
    val isVADActive: StateFlow<Boolean> = _isVADActive.asStateFlow()

    // Audio Components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // VAD State
    private var lastSpeechDetectedTime = 0L
    private var isInitialized = false

    // Simulation flag - when true, audio loop won't override speaking state
    @Volatile
    private var isSimulating = false

    fun start() {
        if (isInitialized) {
            Log.w(TAG, "VAD already started, ignoring duplicate start call")
            return
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "Invalid buffer size: $bufferSize")
                return
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return
            }

            audioRecord?.startRecording()
            isInitialized = true
            _isVADActive.value = true

            Log.i(TAG, "✅ VAD Started Successfully")
            Log.i(TAG, "RMS Threshold: $RMS_THRESHOLD")

            recordingJob = coroutineScope.launch {
                processAudioStream(bufferSize)
            }

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ RECORD_AUDIO permission not granted: ${e.message}")
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start VAD: ${e.message}", e)
        }
    }

    private suspend fun processAudioStream(bufferSize: Int) {
        val audioBuffer = ShortArray(bufferSize / 2)

        Log.i(TAG, "🎤 Audio stream processing started")

        while (coroutineScope.isActive && isInitialized) {
            try {
                // Skip processing if simulating
                if (isSimulating) {
                    delay(50)
                    continue
                }

                val readResult = audioRecord?.read(audioBuffer, 0, audioBuffer.size) ?: 0

                if (readResult > 0) {
                    val rms = calculateRMS(audioBuffer, readResult)
                    updateVADState(rms)

                    if (System.currentTimeMillis() % 200 < 50) {
                        val speakingIndicator = if (_isUserSpeaking.value) "🗣️ SPEAKING" else "🤫 SILENT"
                        Log.d(TAG, "RMS: ${rms.toInt()} | Threshold: $RMS_THRESHOLD | $speakingIndicator")
                    }
                }

                delay(10)

            } catch (e: Exception) {
                Log.e(TAG, "Error in audio stream: ${e.message}", e)
                delay(100)
            }
        }

        Log.i(TAG, "🛑 Audio stream processing stopped")
    }

    private fun calculateRMS(buffer: ShortArray, readSize: Int): Double {
        var sum = 0.0
        for (i in 0 until readSize) {
            val sample = buffer[i].toDouble()
            sum += sample * sample
        }
        val mean = sum / readSize
        return sqrt(mean)
    }

    private fun updateVADState(rms: Double) {
        // Don't update if simulating
        if (isSimulating) return

        val currentTime = System.currentTimeMillis()

        if (rms > RMS_THRESHOLD) {
            lastSpeechDetectedTime = currentTime
            if (!_isUserSpeaking.value) {
                _isUserSpeaking.value = true
                Log.i(TAG, "🎙️ SPEECH STARTED (RMS: ${rms.toInt()})")
            }
        } else {
            val silenceDuration = currentTime - lastSpeechDetectedTime
            if (_isUserSpeaking.value && silenceDuration > SILENCE_TIMEOUT_MS) {
                _isUserSpeaking.value = false
                Log.i(TAG, "🔇 SPEECH ENDED (Silent for ${silenceDuration}ms)")
            }
        }
    }

    fun stop() {
        if (!isInitialized) {
            Log.w(TAG, "VAD not started, ignoring stop call")
            return
        }

        Log.i(TAG, "Stopping VAD...")

        try {
            isInitialized = false
            _isVADActive.value = false
            isSimulating = false
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
            audioRecord = null
            _isUserSpeaking.value = false

            Log.i(TAG, "✅ VAD Stopped Successfully")

        } catch (e: Exception) {
            Log.e(TAG, "Error stopping VAD: ${e.message}", e)
        }
    }

    fun cleanup() {
        stop()
        coroutineScope.cancel()
        Log.i(TAG, "VAD cleanup complete")
    }

    fun getThreshold(): Double = RMS_THRESHOLD

    fun isActive(): Boolean = isInitialized

    /**
     * Manually simulate user speaking (for testing when mic doesn't work)
     * This sets isSimulating flag to prevent audio loop from overriding
     */
    fun simulateSpeaking(durationMs: Long = 2000L) {
        coroutineScope.launch {
            Log.i(TAG, "🎭 SIMULATING USER SPEAKING (${durationMs}ms)")
            isSimulating = true
            _isUserSpeaking.value = true

            delay(durationMs)

            _isUserSpeaking.value = false
            isSimulating = false
            Log.i(TAG, "🎭 SIMULATION ENDED")
        }
    }
}
