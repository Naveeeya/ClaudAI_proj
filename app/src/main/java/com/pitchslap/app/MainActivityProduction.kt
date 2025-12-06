package com.pitchslap.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.pitchslap.app.ai.FeedbackGenerator
import com.pitchslap.app.ai.WhisperService
import com.pitchslap.app.audio.AudioRecorder
import com.pitchslap.app.audio.TextToSpeechEngine
import com.pitchslap.app.conversation.VoiceConversationManager
import com.pitchslap.app.logic.InterruptLogic
import com.pitchslap.app.logic.VoiceActivityDetection
import com.pitchslap.app.ui.navigation.Screen
import com.pitchslap.app.ui.screens.HistoryScreen
import com.pitchslap.app.ui.screens.HomeScreen
import com.pitchslap.app.ui.screens.PracticeScreen
import com.pitchslap.app.ui.screens.SettingsScreen
import com.pitchslap.app.ui.theme.PitchSlapTheme
import kotlinx.coroutines.launch

/**
 * Main Activity - Production UI
 *
 * This is the production version with clean navigation and screens.
 * To use this instead of the test UI, rename MainActivity.kt to MainActivityTest.kt
 * and rename this file to MainActivity.kt
 */
class MainActivityProduction : ComponentActivity() {

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

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            // Permission granted
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Request microphone permission if needed
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.RECORD_AUDIO
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        setContent {
            PitchSlapTheme {
                AppNavigation()
            }
        }
    }

    @Composable
    fun AppNavigation() {
        val navController = rememberNavController()
        val scope = rememberCoroutineScope()

        // Collect states
        val conversationState by conversationManager.conversationState.collectAsState()
        val currentFeedback by conversationManager.currentFeedback.collectAsState()
        val currentTranscript by conversationManager.currentTranscript.collectAsState()
        val conversationHistory by conversationManager.conversationHistory.collectAsState()
        val isUserSpeaking by vad.isUserSpeaking.collectAsState()

        // Auto-navigate to Practice screen on launch
        LaunchedEffect(Unit) {
            navController.navigate(Screen.Practice.route) {
                popUpTo(Screen.Home.route) { inclusive = true }
            }
        }

        NavHost(
            navController = navController,
            startDestination = Screen.Home.route
        ) {
            composable(Screen.Home.route) {
                HomeScreen(
                    onStartPractice = {
                        navController.navigate(Screen.Practice.route)
                    },
                    onViewHistory = {
                        navController.navigate(Screen.History.route)
                    },
                    onOpenSettings = {
                        navController.navigate(Screen.Settings.route)
                    }
                )
            }

            composable(Screen.Practice.route) {
                PracticeScreen(
                    conversationState = conversationState,
                    currentFeedback = currentFeedback,
                    currentTranscript = currentTranscript,
                    isUserSpeaking = isUserSpeaking,
                    onStartPractice = {
                        scope.launch {
                            if (conversationManager.initialize()) {
                                conversationManager.startConversation()
                            }
                        }
                    },
                    onStopPractice = {
                        conversationManager.stopConversation()
                    },
                    onBack = {
                        conversationManager.stopConversation()
                        navController.popBackStack()
                    }
                )
            }

            composable(Screen.History.route) {
                HistoryScreen(
                    conversationHistory = conversationHistory,
                    onBack = { navController.popBackStack() },
                    onClearHistory = {
                        conversationManager.clearHistory()
                    }
                )
            }

            composable(Screen.Settings.route) {
                SettingsScreen(
                    onBack = { navController.popBackStack() }
                )
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        conversationManager.cleanup()
    }
}
