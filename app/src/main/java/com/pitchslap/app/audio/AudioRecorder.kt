package com.pitchslap.app.audio

import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.collections.ArrayList

/**
 * Audio Recorder for capturing user speech
 *
 * Records audio at 16kHz, 16-bit PCM, mono format (Whisper compatible).
 * Uses a circular buffer to continuously capture audio during speech.
 * Can export recorded audio as WAV file for transcription.
 *
 * @see com.pitchslap.app.logic.VoiceActivityDetection for speech detection
 */
class AudioRecorder {

    companion object {
        private const val TAG = "PitchSlap_AudioRecorder"

        // Audio Configuration (matches VAD and Whisper requirements)
        private const val SAMPLE_RATE = 16000 // 16kHz
        private const val CHANNEL_CONFIG = AudioFormat.CHANNEL_IN_MONO
        private const val AUDIO_FORMAT = AudioFormat.ENCODING_PCM_16BIT
        private const val BUFFER_SIZE_MULTIPLIER = 2

        // Buffer Configuration
        private const val MAX_RECORDING_DURATION_SEC = 30 // 30 seconds max
        private const val BYTES_PER_SAMPLE = 2 // 16-bit = 2 bytes
        private const val MAX_BUFFER_SIZE = SAMPLE_RATE * MAX_RECORDING_DURATION_SEC * BYTES_PER_SAMPLE
    }

    // Recording State
    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recordedDurationMs = MutableStateFlow(0L)
    val recordedDurationMs: StateFlow<Long> = _recordedDurationMs.asStateFlow()

    // Audio Components
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val coroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    // Audio Buffer Storage
    private val audioBuffer = ArrayList<ShortArray>()
    private var totalSamplesRecorded = 0
    private var recordingStartTime = 0L

    /**
     * Start recording audio
     *
     * This should be called when speech is detected by VAD.
     * Audio will be stored in a buffer until stopRecording() is called.
     *
     * @return true if recording started successfully, false otherwise
     */
    fun startRecording(): Boolean {
        if (_isRecording.value) {
            Log.w(TAG, "Already recording, ignoring duplicate start")
            return false
        }

        try {
            val bufferSize = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT
            ) * BUFFER_SIZE_MULTIPLIER

            if (bufferSize == AudioRecord.ERROR || bufferSize == AudioRecord.ERROR_BAD_VALUE) {
                Log.e(TAG, "❌ Invalid buffer size: $bufferSize")
                return false
            }

            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                CHANNEL_CONFIG,
                AUDIO_FORMAT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "❌ AudioRecord failed to initialize")
                return false
            }

            // Clear previous recording
            clearBuffer()

            audioRecord?.startRecording()
            _isRecording.value = true
            recordingStartTime = System.currentTimeMillis()

            Log.i(TAG, "✅ Recording STARTED")
            Log.i(TAG, "Sample Rate: $SAMPLE_RATE Hz, Buffer Size: $bufferSize bytes")

            // Start recording loop
            recordingJob = coroutineScope.launch {
                recordAudioLoop(bufferSize)
            }

            return true

        } catch (e: SecurityException) {
            Log.e(TAG, "❌ RECORD_AUDIO permission not granted: ${e.message}")
            return false
        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to start recording: ${e.message}", e)
            return false
        }
    }

    /**
     * Audio recording loop that continuously captures audio
     */
    private suspend fun recordAudioLoop(bufferSize: Int) {
        val readBuffer = ShortArray(bufferSize / 2)

        Log.i(TAG, "🎙️ Audio recording loop started")

        while (coroutineScope.isActive && _isRecording.value) {
            try {
                val readResult = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: 0

                if (readResult > 0) {
                    // Copy data to our buffer
                    val chunk = ShortArray(readResult)
                    System.arraycopy(readBuffer, 0, chunk, 0, readResult)

                    synchronized(audioBuffer) {
                        audioBuffer.add(chunk)
                        totalSamplesRecorded += readResult
                    }

                    // Update duration
                    val durationMs = (totalSamplesRecorded * 1000L) / SAMPLE_RATE
                    _recordedDurationMs.value = durationMs

                    // Check if we've exceeded max duration
                    if (totalSamplesRecorded * BYTES_PER_SAMPLE >= MAX_BUFFER_SIZE) {
                        Log.w(TAG, "⚠️ Max recording duration reached (${MAX_RECORDING_DURATION_SEC}s)")
                        stopRecording()
                        break
                    }

                    // Log progress every second
                    if (totalSamplesRecorded % SAMPLE_RATE < readResult) {
                        Log.d(TAG, "📊 Recorded: ${durationMs / 1000}s (${totalSamplesRecorded} samples)")
                    }
                }

                delay(10) // Small delay to prevent CPU overuse

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error in recording loop: ${e.message}", e)
                delay(100)
            }
        }

        Log.i(TAG, "🛑 Audio recording loop stopped")
    }

    /**
     * Stop recording audio
     *
     * This should be called when speech ends (VAD detects silence).
     * The recorded audio remains in buffer for retrieval.
     */
    fun stopRecording() {
        if (!_isRecording.value) {
            Log.w(TAG, "Not recording, ignoring stop call")
            return
        }

        try {
            _isRecording.value = false
            recordingJob?.cancel()
            recordingJob = null

            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                    release()
                }
            }
            audioRecord = null

            val recordingDuration = System.currentTimeMillis() - recordingStartTime
            val totalSeconds = totalSamplesRecorded.toFloat() / SAMPLE_RATE

            Log.i(TAG, "✅ Recording STOPPED")
            Log.i(TAG, "Duration: ${recordingDuration}ms, Samples: $totalSamplesRecorded (${totalSeconds}s)")

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error stopping recording: ${e.message}", e)
        }
    }

    /**
     * Get the recorded audio as a ByteArray (raw PCM)
     *
     * @return ByteArray containing 16-bit PCM audio samples
     */
    fun getAudioBuffer(): ByteArray {
        synchronized(audioBuffer) {
            if (audioBuffer.isEmpty()) {
                Log.w(TAG, "⚠️ Audio buffer is empty")
                return ByteArray(0)
            }

            // Calculate total size
            val totalBytes = totalSamplesRecorded * BYTES_PER_SAMPLE
            val result = ByteArray(totalBytes)

            // Combine all chunks into single byte array
            var offset = 0
            for (chunk in audioBuffer) {
                val byteBuffer = ByteBuffer.allocate(chunk.size * BYTES_PER_SAMPLE)
                byteBuffer.order(ByteOrder.LITTLE_ENDIAN)

                for (sample in chunk) {
                    byteBuffer.putShort(sample)
                }

                val chunkBytes = byteBuffer.array()
                System.arraycopy(chunkBytes, 0, result, offset, chunkBytes.size)
                offset += chunkBytes.size
            }

            Log.i(TAG, "📦 Audio buffer retrieved: ${result.size} bytes")
            return result
        }
    }

    /**
     * Save recorded audio to WAV file (Whisper compatible)
     *
     * WAV format: 16kHz, 16-bit PCM, mono
     *
     * @param outputFile File where WAV will be saved
     * @return true if save successful, false otherwise
     */
    fun saveToWAV(outputFile: File): Boolean {
        try {
            val audioData = getAudioBuffer()

            if (audioData.isEmpty()) {
                Log.e(TAG, "❌ No audio data to save")
                return false
            }

            FileOutputStream(outputFile).use { fos ->
                // Write WAV header
                writeWavHeader(fos, audioData.size)

                // Write audio data
                fos.write(audioData)
            }

            Log.i(TAG, "✅ WAV file saved: ${outputFile.absolutePath}")
            Log.i(TAG, "File size: ${outputFile.length()} bytes (${outputFile.length() / 1024}KB)")

            return true

        } catch (e: Exception) {
            Log.e(TAG, "❌ Failed to save WAV file: ${e.message}", e)
            return false
        }
    }

    /**
     * Write WAV file header (44 bytes)
     */
    private fun writeWavHeader(fos: FileOutputStream, audioDataSize: Int) {
        val header = ByteBuffer.allocate(44)
        header.order(ByteOrder.LITTLE_ENDIAN)

        // RIFF chunk
        header.put("RIFF".toByteArray())
        header.putInt(36 + audioDataSize) // File size - 8
        header.put("WAVE".toByteArray())

        // fmt chunk
        header.put("fmt ".toByteArray())
        header.putInt(16) // Subchunk1Size (16 for PCM)
        header.putShort(1) // AudioFormat (1 = PCM)
        header.putShort(1) // NumChannels (1 = Mono)
        header.putInt(SAMPLE_RATE) // SampleRate
        header.putInt(SAMPLE_RATE * BYTES_PER_SAMPLE) // ByteRate
        header.putShort(BYTES_PER_SAMPLE.toShort()) // BlockAlign
        header.putShort(16) // BitsPerSample

        // data chunk
        header.put("data".toByteArray())
        header.putInt(audioDataSize) // Subchunk2Size

        fos.write(header.array())
    }

    /**
     * Clear the audio buffer
     */
    fun clearBuffer() {
        synchronized(audioBuffer) {
            audioBuffer.clear()
            totalSamplesRecorded = 0
            _recordedDurationMs.value = 0
            Log.i(TAG, "🗑️ Audio buffer cleared")
        }
    }

    /**
     * Get recording statistics
     */
    fun getStats(): RecordingStats {
        synchronized(audioBuffer) {
            return RecordingStats(
                isRecording = _isRecording.value,
                durationMs = _recordedDurationMs.value,
                totalSamples = totalSamplesRecorded,
                bufferSizeBytes = totalSamplesRecorded * BYTES_PER_SAMPLE,
                chunkCount = audioBuffer.size
            )
        }
    }

    /**
     * Cleanup resources
     */
    fun cleanup() {
        stopRecording()
        clearBuffer()
        coroutineScope.cancel()
        Log.i(TAG, "AudioRecorder cleanup complete")
    }

    /**
     * Data class for recording statistics
     */
    data class RecordingStats(
        val isRecording: Boolean,
        val durationMs: Long,
        val totalSamples: Int,
        val bufferSizeBytes: Int,
        val chunkCount: Int
    ) {
        val durationSeconds: Float
            get() = durationMs / 1000f

        val bufferSizeKB: Float
            get() = bufferSizeBytes / 1024f
    }
}
