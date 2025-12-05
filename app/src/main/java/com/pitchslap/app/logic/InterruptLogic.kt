package com.pitchslap.app.logic

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*

/**
 * Interrupt Logic - Traffic Controller for Voice Interactions
 *
 * Manages turn-taking between user and AI by detecting "barge-in" events
 * (when user interrupts AI while it's speaking).
 *
 * Core Logic:
 * - Monitors VAD for user speech
 * - Tracks AI speaking state
 * - Detects conflicts (both speaking simultaneously)
 * - Emits barge-in events to cancel AI audio/generation
 *
 * This enables natural conversation flow where users can interrupt AI responses.
 */
class InterruptLogic(
    private val vad: VoiceActivityDetection
) {
    companion object {
        private const val TAG = "PitchSlap_Interrupt"
    }

    // AI State Management
    private val _isAiSpeaking = MutableStateFlow(false)
    val isAiSpeaking: StateFlow<Boolean> = _isAiSpeaking.asStateFlow()

    // Barge-In Event Stream
    private val _bargeInEvent = MutableSharedFlow<BargeInEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val bargeInEvent: SharedFlow<BargeInEvent> = _bargeInEvent.asSharedFlow()

    // Coroutine Management
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private var monitoringJob: Job? = null

    // Statistics (for debugging)
    private var bargeInCount = 0

    init {
        startMonitoring()
        Log.i(TAG, "✅ InterruptLogic initialized")
    }

    /**
     * Starts monitoring for barge-in events
     * Continuously watches for user speech while AI is talking
     */
    private fun startMonitoring() {
        monitoringJob = coroutineScope.launch {
            Log.i(TAG, "🎯 Barge-in monitoring started")

            vad.isUserSpeaking.collect { isUserSpeaking ->
                if (isUserSpeaking && _isAiSpeaking.value) {
                    // CONFLICT DETECTED: Both user and AI are speaking!
                    handleBargeIn()
                }
            }
        }
    }

    /**
     * Handles barge-in event when user interrupts AI
     *
     * Actions:
     * 1. Immediately stop AI speech state
     * 2. Emit barge-in event for other components to react
     * 3. Log the interruption
     */
    private suspend fun handleBargeIn() {
        Log.w(TAG, "🚨 BARGE-IN DETECTED! CUTTING AI AUDIO.")

        // Stop AI immediately
        _isAiSpeaking.value = false

        // Increment counter
        bargeInCount++

        // Emit event
        val event = BargeInEvent(
            timestamp = System.currentTimeMillis(),
            bargeInNumber = bargeInCount
        )
        _bargeInEvent.emit(event)

        Log.i(TAG, "🛑 AI speech interrupted. Total barge-ins: $bargeInCount")
    }

    /**
     * Marks AI as speaking
     * Call this when AI starts generating/playing audio
     */
    fun startAiSpeech() {
        _isAiSpeaking.value = true
        Log.i(TAG, "🤖 AI started speaking")
    }

    /**
     * Marks AI as not speaking
     * Call this when AI finishes or is interrupted
     */
    fun stopAiSpeech() {
        if (_isAiSpeaking.value) {
            _isAiSpeaking.value = false
            Log.i(TAG, "🤖 AI stopped speaking (normal completion)")
        }
    }

    /**
     * Get total number of barge-ins detected (for analytics/debugging)
     */
    fun getBargeInCount(): Int = bargeInCount

    /**
     * Reset barge-in counter
     */
    fun resetBargeInCount() {
        bargeInCount = 0
        Log.i(TAG, "📊 Barge-in counter reset")
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        Log.i(TAG, "Cleaning up InterruptLogic...")
        monitoringJob?.cancel()
        coroutineScope.cancel()
        _isAiSpeaking.value = false
        Log.i(TAG, "✅ InterruptLogic cleanup complete")
    }
}

/**
 * Data class representing a barge-in event
 *
 * @property timestamp When the barge-in occurred (System.currentTimeMillis())
 * @property bargeInNumber Sequential number of this barge-in (for tracking)
 */
data class BargeInEvent(
    val timestamp: Long,
    val bargeInNumber: Int
)
