package com.pitchslap.app.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pitchslap.app.conversation.VoiceConversationManager
import com.pitchslap.app.data.PronunciationFeedback
import com.pitchslap.app.data.toFeedbackLevel
import com.pitchslap.app.data.toEmoji

/**
 * Practice Screen - Main voice interaction interface
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PracticeScreen(
    conversationState: VoiceConversationManager.ConversationState,
    currentFeedback: PronunciationFeedback?,
    currentTranscript: String,
    isUserSpeaking: Boolean,
    onStartPractice: () -> Unit,
    onStopPractice: () -> Unit,
    onBack: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Practice Session") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", style = MaterialTheme.typography.headlineMedium)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {

            // State indicator
            Text(
                text = when (conversationState) {
                    VoiceConversationManager.ConversationState.IDLE -> "Ready to Practice"
                    VoiceConversationManager.ConversationState.LISTENING -> "Listening... Speak now!"
                    VoiceConversationManager.ConversationState.RECORDING -> "Recording your speech..."
                    VoiceConversationManager.ConversationState.PROCESSING -> "Analyzing..."
                    VoiceConversationManager.ConversationState.AI_SPEAKING -> "AI is speaking..."
                    VoiceConversationManager.ConversationState.ERROR -> "Error occurred"
                },
                style = MaterialTheme.typography.titleLarge,
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

            // Large microphone button
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .background(
                        color = when {
                            conversationState == VoiceConversationManager.ConversationState.IDLE -> Color.Gray
                            isUserSpeaking -> Color(0xFF4CAF50)
                            else -> Color(0xFFFF5722)
                        },
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = when {
                        conversationState == VoiceConversationManager.ConversationState.IDLE -> ""
                        isUserSpeaking -> "️"
                        else -> ""
                    },
                    style = MaterialTheme.typography.displayLarge,
                    color = Color.White
                )
            }

            // Transcript display
            AnimatedVisibility(visible = currentTranscript.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.secondaryContainer
                    )
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "You said:",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "\"$currentTranscript\"",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Feedback card
            AnimatedVisibility(
                visible = currentFeedback != null,
                enter = slideInVertically() + expandVertically() + fadeIn(),
                exit = slideOutVertically() + shrinkVertically() + fadeOut()
            ) {
                currentFeedback?.let { feedback ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                text = "💬 Feedback",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )

                            // Scores
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceEvenly
                            ) {
                                ScoreBadge("️", "Pronunciation", feedback.pronunciationScore)
                                ScoreBadge("", "Grammar", feedback.grammarScore)
                                ScoreBadge("", "Fluency", feedback.fluencyScore)
                            }

                            HorizontalDivider()

                            // Feedback text
                            Text(
                                text = feedback.feedback,
                                style = MaterialTheme.typography.bodyLarge,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Corrections
                            if (feedback.corrections.isNotEmpty()) {
                                Text(
                                    text = "💡 Tips:",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                feedback.corrections.forEach { correction ->
                                    Text(
                                        text = "• $correction",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Control buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                if (conversationState == VoiceConversationManager.ConversationState.IDLE) {
                    Button(
                        onClick = onStartPractice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF4CAF50)
                        )
                    ) {
                        Text(
                            text = "▶️ Start Practice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onStopPractice,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Text(
                            text = "⏹️ Stop Practice",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ScoreBadge(emoji: String, label: String, score: Int) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(
            text = emoji,
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = "$score",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = when {
                score >= 90 -> Color(0xFF4CAF50)  // EXCELLENT
                score >= 75 -> Color(0xFF8BC34A)  // GOOD
                score >= 60 -> Color(0xFFFF9800)  // FAIR
                else -> Color(0xFFF44336)         // NEEDS_WORK
            }
        )
        Text(
            text = when {
                score >= 90 -> ""
                score >= 75 -> "✅"
                score >= 60 -> ""
                else -> ""
            },
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer
        )
    }
}
