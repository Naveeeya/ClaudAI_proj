package com.pitchslap.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pitchslap.app.ai.FeedbackGenerator
import com.pitchslap.app.ai.WhisperService
import com.pitchslap.app.audio.AudioRecorder
import com.pitchslap.app.audio.TextToSpeechEngine
import com.pitchslap.app.conversation.VoiceConversationManager
import com.pitchslap.app.logic.InterruptLogic
import com.pitchslap.app.logic.VoiceActivityDetection
import com.pitchslap.app.ui.theme.PitchSlapTheme
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.io.File

/**
 * Main Activity - VAD + Interrupt Logic + Audio Recording Testing Interface
 *
 * This temporary UI allows us to:
 * 1. Test Voice Activity Detection
 * 2. Test Interrupt Logic (Barge-In Detection)
 * 3. Test Audio Recording
 * 4. Simulate AI speaking
 * 5. Test VAD + Recording integration
 * 6. Save recordings as WAV files
 * 7. Verify all components work before building full voice UI
 */
class MainActivity : ComponentActivity() {

    private val vad = VoiceActivityDetection()
    private val interruptLogic = InterruptLogic(vad)
    private val audioRecorder = AudioRecorder()
    private val whisperService by lazy { WhisperService(applicationContext) }
    private val feedbackGenerator by lazy { FeedbackGenerator() }
    private val tts by lazy { TextToSpeechEngine(applicationContext, interruptLogic) }
    private val conversationManager by lazy {
        VoiceConversationManager(
            context = applicationContext,
            vad = vad,
            audioRecorder = audioRecorder,
            whisperService = whisperService,
            feedbackGenerator = feedbackGenerator,
            tts = tts,
            interruptLogic = interruptLogic
        )
    }

    // Permission launcher
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted, VAD can now start
            vad.start()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Auto-start VAD for testing (permission already granted via ADB)
        vad.start()

        setContent {
            PitchSlapTheme {
                InterruptTestScreen()
            }
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    fun InterruptTestScreen() {
        val isUserSpeaking by vad.isUserSpeaking.collectAsState()
        val isAiSpeaking by interruptLogic.isAiSpeaking.collectAsState()
        val isVADActive by vad.isVADActive.collectAsState()
        val isRecording by audioRecorder.isRecording.collectAsState()
        val recordedDuration by audioRecorder.recordedDurationMs.collectAsState()

        // Conversation Manager states
        val conversationState by conversationManager.conversationState.collectAsState()
        val currentFeedback by conversationManager.currentFeedback.collectAsState()
        val conversationHistory by conversationManager.conversationHistory.collectAsState()
        val currentTranscript by conversationManager.currentTranscript.collectAsState()

        // Listen for barge-in events
        val scope = rememberCoroutineScope()
        var lastBargeInTime by remember { mutableStateOf<Long?>(null) }
        var totalBargeIns by remember { mutableIntStateOf(0) }

        LaunchedEffect(Unit) {
            scope.launch {
                interruptLogic.bargeInEvent.collect { event ->
                    lastBargeInTime = event.timestamp
                    totalBargeIns = event.bargeInNumber
                }
            }
        }

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Pitch Slap - Interrupt Test") }
                )
            }
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                ) {

                    // Title
                    Text(
                        text = "Interrupt Logic Test",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )

                    // AI Speaking Banner - Animated
                    AnimatedVisibility(
                        visible = isAiSpeaking,
                        enter = slideInVertically() + expandVertically() + fadeIn(),
                        exit = slideOutVertically() + shrinkVertically() + fadeOut()
                    ) {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFF2196F3) // Blue
                            ),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "🤖",
                                    style = MaterialTheme.typography.headlineMedium
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Text(
                                    text = "AI IS TALKING",
                                    style = MaterialTheme.typography.titleLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }

                    // Visual indicator - Large circle that changes color
                    Box(
                        modifier = Modifier
                            .size(180.dp)
                            .background(
                                color = when {
                                    !isVADActive -> Color.Gray
                                    isUserSpeaking -> Color(0xFF4CAF50) // Green
                                    else -> Color(0xFFFF5722) // Red
                                },
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = when {
                                !isVADActive -> "⏸️"
                                isUserSpeaking -> "🗣️"
                                else -> "🤫"
                            },
                            style = MaterialTheme.typography.displayLarge,
                            color = Color.White
                        )
                    }

                    // Status text
                    Text(
                        text = when {
                            !isVADActive -> "VAD Stopped"
                            isUserSpeaking -> "USER SPEAKING"
                            else -> "Listening... (Silent)"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            !isVADActive -> Color.Gray
                            isUserSpeaking -> Color(0xFF4CAF50)
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )

                    Spacer(modifier = Modifier.height(8.dp))

                    // Statistics Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "📊 Statistics",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "VAD Status:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (isVADActive) "Active ✅" else "Stopped ⏸️",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "AI Speaking:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (isAiSpeaking) "Yes 🤖" else "No 🔇",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "User Speaking:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (isUserSpeaking) "Yes 🗣️" else "No 🤫",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Total Barge-Ins:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "$totalBargeIns",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = if (totalBargeIns > 0) Color(0xFFFF5722) else Color.Gray
                                )
                            }

                            lastBargeInTime?.let { time ->
                                val elapsed = (System.currentTimeMillis() - time) / 1000
                                Text(
                                    text = "Last barge-in: ${elapsed}s ago",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Audio Recording Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isRecording) Color(0xFFE91E63) else MaterialTheme.colorScheme.primaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "🎙️ Audio Recording",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Recording Status:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = if (isRecording) "Recording... ⏺️" else "Stopped ⏹️",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Duration:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${recordedDuration / 1000f}s",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            val stats = audioRecorder.getStats()
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "Buffer Size:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${stats.bufferSizeKB.toInt()}KB",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            // Recording control buttons
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { startRecording() },
                                    enabled = !isRecording && isVADActive,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFE91E63)
                                    )
                                ) {
                                    Text("⏺️ Record")
                                }

                                Button(
                                    onClick = { stopRecording() },
                                    enabled = isRecording,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("⏹️ Stop")
                                }
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { saveRecording() },
                                    enabled = !isRecording && stats.bufferSizeBytes > 0,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("💾 Save WAV")
                                }

                                Button(
                                    onClick = { clearRecording() },
                                    enabled = !isRecording,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("🗑️ Clear")
                                }
                            }

                            // VAD Integration Test Button
                            Button(
                                onClick = { testVADRecording() },
                                enabled = !isRecording && isVADActive,
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color(0xFF4CAF50)
                                )
                            ) {
                                Text(
                                    text = "🧪 Test VAD + Recording (Auto)",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Control buttons - VAD
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Button(
                            onClick = { startVAD() },
                            enabled = !isVADActive,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Start VAD")
                        }

                        Button(
                            onClick = { stopVAD() },
                            enabled = isVADActive,
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.error
                            )
                        ) {
                            Text("Stop VAD")
                        }
                    }

                    // AI Simulation Button
                    Button(
                        onClick = { toggleAiSpeaking() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF2196F3) // Blue
                        )
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (isAiSpeaking) "Stop AI Talking" else "🤖 Simulate AI Talking",
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Simulate Speaking Button (for testing when emulator mic doesn't work)
                    Button(
                        onClick = { vad.simulateSpeaking(2000L) },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50) // Green
                        )
                    ) {
                        Text(
                            text = "🗣️ Simulate User Speaking (2s)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // ONE-CLICK TEST: Automatically tests barge-in
                    Button(
                        onClick = { testBargeIn() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFFF9800) // Orange
                        )
                    ) {
                        Text(
                            text = "🧪 AUTO TEST BARGE-IN (One Click!)",
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Conversation Manager Card
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.secondaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "💬 Conversation Manager",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "State:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = conversationState.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when (conversationState) {
                                        VoiceConversationManager.ConversationState.IDLE -> Color.Gray
                                        VoiceConversationManager.ConversationState.LISTENING -> Color(0xFF2196F3)
                                        VoiceConversationManager.ConversationState.RECORDING -> Color(0xFFE91E63)
                                        VoiceConversationManager.ConversationState.PROCESSING -> Color(0xFFFF9800)
                                        VoiceConversationManager.ConversationState.AI_SPEAKING -> Color(0xFF9C27B0)
                                        VoiceConversationManager.ConversationState.ERROR -> Color(0xFFF44336)
                                    }
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "History:",
                                    style = MaterialTheme.typography.bodyMedium
                                )
                                Text(
                                    text = "${conversationHistory.size} turns",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            if (currentTranscript.isNotEmpty()) {
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "Latest Transcript:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "\"$currentTranscript\"",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }

                            currentFeedback?.let { feedback ->
                                Divider(modifier = Modifier.padding(vertical = 4.dp))
                                Text(
                                    text = "Latest Scores:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        "🗣️ ${feedback.pronunciationScore}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    Text("📝 ${feedback.grammarScore}", style = MaterialTheme.typography.bodySmall)
                                    Text("💬 ${feedback.fluencyScore}", style = MaterialTheme.typography.bodySmall)
                                }
                            }

                            Divider(modifier = Modifier.padding(vertical = 4.dp))

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Button(
                                    onClick = { startConversation() },
                                    enabled = conversationState == VoiceConversationManager.ConversationState.IDLE,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFF4CAF50)
                                    )
                                ) {
                                    Text("▶️ Start")
                                }

                                Button(
                                    onClick = { stopConversation() },
                                    enabled = conversationState != VoiceConversationManager.ConversationState.IDLE,
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.error
                                    )
                                ) {
                                    Text("⏹️ Stop")
                                }
                            }

                            Button(
                                onClick = { clearConversation() },
                                enabled = conversationHistory.isNotEmpty(),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🗑️ Clear History (${conversationHistory.size})")
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // Instructions
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🧪 How to Test Interrupt Logic",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "1. Start VAD (grant mic permission)",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "2. Click 'Simulate AI Talking' → Blue banner appears",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "3. Speak into microphone → Banner instantly disappears!",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Text(
                                text = "4. Check Logcat for '🚨 BARGE-IN DETECTED'",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }
        }
    }

    /**
     * Checks permission and starts VAD
     */
    private fun startVAD() {
        when {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
                vad.start()
            }

            else -> {
                // Request permission
                requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    /**
     * Stops VAD
     */
    private fun stopVAD() {
        vad.stop()
    }

    /**
     * Toggle AI speaking state for testing
     */
    private fun toggleAiSpeaking() {
        if (interruptLogic.isAiSpeaking.value) {
            interruptLogic.stopAiSpeech()
        } else {
            interruptLogic.startAiSpeech()
        }
    }

    /**
     * One-click test for barge-in
     * Automatically: starts AI speaking, waits, then simulates user speaking
     */
    private fun testBargeIn() {
        lifecycleScope.launch {
            // Step 1: Start AI speaking
            interruptLogic.startAiSpeech()

            // Step 2: Wait 500ms
            kotlinx.coroutines.delay(500)

            // Step 3: Simulate user speaking (this should trigger barge-in)
            vad.simulateSpeaking(2000L)
        }
    }

    override fun onPause() {
        super.onPause()
        vad.stop()
        interruptLogic.stopAiSpeech()
    }

    /**
     * Start audio recording manually
     */
    private fun startRecording() {
        if (audioRecorder.startRecording()) {
            android.util.Log.i("MainActivity", "✅ Recording started from UI")
        } else {
            android.util.Log.e("MainActivity", "❌ Failed to start recording")
        }
    }

    /**
     * Stop audio recording manually
     */
    private fun stopRecording() {
        audioRecorder.stopRecording()
        android.util.Log.i("MainActivity", "⏹️ Recording stopped from UI")
    }

    /**
     * Save recorded audio to WAV file
     */
    private fun saveRecording() {
        val outputFile = File(getExternalFilesDir(null), "recording_${System.currentTimeMillis()}.wav")
        if (audioRecorder.saveToWAV(outputFile)) {
            android.util.Log.i("MainActivity", "✅ WAV file saved: ${outputFile.absolutePath}")
            // You could also show a Toast or Snackbar here
        } else {
            android.util.Log.e("MainActivity", "❌ Failed to save WAV file")
        }
    }

    /**
     * Clear recorded audio buffer
     */
    private fun clearRecording() {
        audioRecorder.clearBuffer()
        android.util.Log.i("MainActivity", "🗑️ Recording buffer cleared")
    }

    /**
     * Test VAD + Audio Recording Integration
     * Automatically: starts recording, simulates speech, stops recording, saves file
     */
    private fun testVADRecording() {
        lifecycleScope.launch {
            android.util.Log.i("MainActivity", "🧪 Starting VAD + Recording integration test")

            // Step 1: Start recording
            if (audioRecorder.startRecording()) {
                android.util.Log.i("MainActivity", "✅ Step 1: Recording started")

                // Step 2: Wait 500ms
                kotlinx.coroutines.delay(500)

                // Step 3: Simulate user speaking for 2 seconds
                android.util.Log.i("MainActivity", "🗣️ Step 2: Simulating speech")
                vad.simulateSpeaking(2000L)

                // Step 4: Wait for simulation to finish
                kotlinx.coroutines.delay(2500)

                // Step 5: Stop recording
                audioRecorder.stopRecording()
                android.util.Log.i("MainActivity", "⏹️ Step 3: Recording stopped")

                // Step 6: Save to file
                kotlinx.coroutines.delay(500)
                val outputFile = File(getExternalFilesDir(null), "test_recording_${System.currentTimeMillis()}.wav")
                if (audioRecorder.saveToWAV(outputFile)) {
                    android.util.Log.i("MainActivity", "✅ Step 4: Test recording saved to: ${outputFile.absolutePath}")
                    android.util.Log.i("MainActivity", "🎉 Integration test complete!")
                } else {
                    android.util.Log.e("MainActivity", "❌ Failed to save test recording")
                }
            } else {
                android.util.Log.e("MainActivity", "❌ Failed to start recording")
            }
        }
    }

    /**
     * Start conversation session
     */
    private fun startConversation() {
        lifecycleScope.launch {
            try {
                android.util.Log.i("MainActivity", "🚀 Starting conversation session...")

                // Initialize conversation manager
                if (conversationManager.initialize()) {
                    android.util.Log.i("MainActivity", "✅ ConversationManager initialized")

                    // Start conversation
                    conversationManager.startConversation()
                    android.util.Log.i("MainActivity", "✅ Conversation started - Speak to begin!")
                } else {
                    android.util.Log.e("MainActivity", "❌ Failed to initialize ConversationManager")
                }
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "❌ Error starting conversation: ${e.message}", e)
            }
        }
    }

    /**
     * Stop conversation session
     */
    private fun stopConversation() {
        conversationManager.stopConversation()
        android.util.Log.i("MainActivity", "⏹️ Conversation stopped")
    }

    /**
     * Clear conversation history
     */
    private fun clearConversation() {
        conversationManager.clearHistory()
        android.util.Log.i("MainActivity", "🗑️ Conversation history cleared")
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationManager.cleanup()
        vad.cleanup()
        interruptLogic.cleanup()
        audioRecorder.cleanup()
    }
}

