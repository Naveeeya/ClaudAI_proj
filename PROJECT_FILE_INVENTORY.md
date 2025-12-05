# Project File Inventory

**Last Updated**: December 5, 2025  
**Purpose**: Complete map of all active files and their purpose for AI agent reference

---

## 📂 Active Source Files (DO USE)

### Core Application

#### `app/src/main/java/com/pitchslap/app/PitchSlapApplication.kt`

- **Status**: ✅ Complete
- **Purpose**: SDK initialization, model registration
- **Phase**: 1
- **Lines**: 57
- **Modify**: Only for adding new models
- **Dependencies**: RunAnywhere SDK

#### `app/src/main/java/com/pitchslap/app/MainActivity.kt`

- **Status**: ✅ Complete (Test UI)
- **Purpose**: Test interface for VAD + Interrupt Logic
- **Phase**: 3
- **Lines**: 465
- **Modify**: Will replace with production UI in Phase 7
- **Dependencies**: VoiceActivityDetection, InterruptLogic

---

### Voice Logic (Complete ✅)

#### `app/src/main/java/com/pitchslap/app/logic/VoiceActivityDetection.kt`

- **Status**: ✅ Complete
- **Purpose**: Real-time speech detection using RMS amplitude
- **Phase**: 2
- **Lines**: 230
- **Key Features**:
    - 16kHz audio streaming
    - RMS threshold detection (2000)
    - 500ms silence timeout
    - Sub-20ms latency
- **Modify**: NO (unless bugs found)
- **Dependencies**: Android AudioRecord API
- **Exposes**: `StateFlow<Boolean> isUserSpeaking`

#### `app/src/main/java/com/pitchslap/app/logic/InterruptLogic.kt`

- **Status**: ✅ Complete
- **Purpose**: Barge-in detection and AI cutoff
- **Phase**: 3
- **Lines**: 151
- **Key Features**:
    - Monitors user + AI speaking states
    - Detects conflicts (both speaking)
    - Immediate AI cutoff on interrupt
    - Event emission via SharedFlow
- **Modify**: NO (unless bugs found)
- **Dependencies**: VoiceActivityDetection
- **Exposes**:
    - `StateFlow<Boolean> isAiSpeaking`
    - `SharedFlow<BargeInEvent> bargeInEvent`

---

### UI Theme (Complete ✅)

#### `app/src/main/java/com/pitchslap/app/ui/theme/Color.kt`

- **Status**: ✅ Complete
- **Purpose**: Material 3 color definitions
- **Phase**: 1
- **Modify**: Optional (customize colors)

#### `app/src/main/java/com/pitchslap/app/ui/theme/Theme.kt`

- **Status**: ✅ Complete
- **Purpose**: Material 3 theme configuration
- **Phase**: 1
- **Modify**: Optional (customize theme)

#### `app/src/main/java/com/pitchslap/app/ui/theme/Type.kt`

- **Status**: ✅ Complete
- **Purpose**: Typography definitions
- **Phase**: 1
- **Modify**: Optional (customize fonts)

---

## 📂 To Be Created (Phase 4-8)

### Phase 4: Audio Recording

#### `app/src/main/java/com/pitchslap/app/audio/AudioRecorder.kt`

- **Status**: ❌ TODO
- **Purpose**: Capture user speech audio for transcription
- **Estimated Lines**: ~150
- **Dependencies**: VoiceActivityDetection.kt (✅ exists)
- **Key Features**:
    - AudioRecord API (16kHz, 16-bit PCM, Mono)
    - Circular buffer for continuous recording
    - WAV file export for Whisper
    - Integration with VAD start/stop
- **Exposes**: `suspend fun getAudioBuffer(): ByteArray`

---

### Phase 5: AI Integration

#### `app/src/main/java/com/pitchslap/app/ai/WhisperService.kt`

- **Status**: ❌ TODO
- **Purpose**: Speech-to-text transcription using Whisper model
- **Estimated Lines**: ~100
- **Dependencies**:
    - AudioRecorder.kt (Phase 4)
    - RunAnywhere SDK (✅ exists)
- **Key Features**:
    - Whisper Tiny model integration
    - Audio file to text conversion
    - Sub-500ms transcription latency
- **Exposes**: `suspend fun transcribe(audioFile: File): String`

#### `app/src/main/java/com/pitchslap/app/data/PronunciationFeedback.kt`

- **Status**: ❌ TODO
- **Purpose**: Data model for AI-generated pronunciation feedback
- **Estimated Lines**: ~30
- **Dependencies**: None (standalone data class)
- **Key Features**:
    - Serializable data class
    - JSON schema matching LLM output
- **Data Fields**:
    - transcript: String
    - pronunciation_score: Int (0-100)
    - grammar_score: Int (0-100)
    - fluency_score: Int (0-100)
    - feedback: String
    - corrections: List<String>
    - example_sentence: String

#### `app/src/main/java/com/pitchslap/app/prompts/LanguageCoachPrompts.kt`

- **Status**: ❌ TODO
- **Purpose**: System prompts and JSON schema for language coaching
- **Estimated Lines**: ~80
- **Dependencies**: PronunciationFeedback.kt (same phase)
- **Key Features**:
    - System prompt definition
    - Few-shot examples
    - Prompt builder function
- **Exposes**:
    - `val SYSTEM_PROMPT: String`
    - `fun createPrompt(transcript: String): String`

#### `app/src/main/java/com/pitchslap/app/ai/FeedbackGenerator.kt`

- **Status**: ❌ TODO
- **Purpose**: LLM integration for generating pronunciation feedback
- **Estimated Lines**: ~200
- **Dependencies**:
    - PronunciationFeedback.kt (same phase)
    - LanguageCoachPrompts.kt (same phase)
    - RunAnywhere SDK (✅ exists)
- **Key Features**:
    - Qwen/Llama model loading
    - JSON response parsing
    - Streaming generation support
    - Error handling and retries
- **Exposes**:
    - `suspend fun initialize(): Boolean`
    - `suspend fun generateFeedback(transcript: String): Result<PronunciationFeedback>`
    - `fun generateFeedbackStream(transcript: String): Flow<String>`

#### `app/src/main/java/com/pitchslap/app/audio/TextToSpeechEngine.kt`

- **Status**: ❌ TODO
- **Purpose**: AI voice output using TTS
- **Estimated Lines**: ~150
- **Dependencies**: InterruptLogic.kt (✅ exists)
- **Key Features**:
    - Android native TTS integration
    - Interrupt support (barge-in)
    - Start/stop callbacks
    - Integration with InterruptLogic
- **Exposes**:
    - `fun speak(text: String, onStart: () -> Unit, onDone: () -> Unit)`
    - `fun stop()`
    - `fun shutdown()`

---

### Phase 6: Conversation Manager

#### `app/src/main/java/com/pitchslap/app/data/ConversationTurn.kt`

- **Status**: ❌ TODO
- **Purpose**: Data model for conversation history
- **Estimated Lines**: ~40
- **Dependencies**: PronunciationFeedback.kt (Phase 5)
- **Key Features**:
    - Represents one user-AI exchange
    - Timestamp tracking
    - Feedback storage
- **Data Fields**:
    - id: Long
    - timestamp: Long
    - userTranscript: String
    - aiFeedback: PronunciationFeedback
    - durationMs: Long

#### `app/src/main/java/com/pitchslap/app/conversation/VoiceConversationManager.kt`

- **Status**: ❌ TODO
- **Purpose**: Orchestrates full voice conversation pipeline
- **Estimated Lines**: ~300
- **Dependencies**: ALL above components
    - VoiceActivityDetection.kt (✅ exists)
    - InterruptLogic.kt (✅ exists)
    - AudioRecorder.kt (Phase 4)
    - WhisperService.kt (Phase 5)
    - FeedbackGenerator.kt (Phase 5)
    - TextToSpeechEngine.kt (Phase 5)
- **Key Features**:
    - State machine for conversation flow
    - Coordinates all components
    - Manages conversation history
    - Error handling and recovery
    - Barge-in support
- **States**: IDLE → LISTENING → RECORDING → PROCESSING → AI_SPEAKING
- **Exposes**:
    - `StateFlow<ConversationState> conversationState`
    - `StateFlow<PronunciationFeedback?> currentFeedback`
    - `StateFlow<List<ConversationTurn>> conversationHistory`
    - `suspend fun startConversation()`
    - `fun stopConversation()`

---

### Phase 7: Production UI

#### `app/src/main/java/com/pitchslap/app/ui/screens/HomeScreen.kt`

- **Status**: ❌ TODO
- **Purpose**: Welcome screen with "Start Practice" button
- **Estimated Lines**: ~120
- **Dependencies**: Navigation
- **Key Features**: Session history preview, settings access

#### `app/src/main/java/com/pitchslap/app/ui/screens/PracticeScreen.kt`

- **Status**: ❌ TODO
- **Purpose**: Main practice interface with voice interaction
- **Estimated Lines**: ~250
- **Dependencies**: VoiceConversationManager
- **Key Features**:
    - Microphone button with visual feedback
    - Real-time transcript display
    - Animated feedback cards
    - Score badges

#### `app/src/main/java/com/pitchslap/app/ui/screens/FeedbackScreen.kt`

- **Status**: ❌ TODO
- **Purpose**: Detailed feedback breakdown
- **Estimated Lines**: ~150
- **Dependencies**: PronunciationFeedback data model
- **Key Features**:
    - Score visualization
    - Correction tips
    - Example sentence with audio

#### `app/src/main/java/com/pitchslap/app/ui/screens/HistoryScreen.kt`

- **Status**: ❌ TODO
- **Purpose**: Past session history and progress tracking
- **Estimated Lines**: ~180
- **Dependencies**: SessionRepository
- **Key Features**:
    - Session list
    - Progress charts
    - Statistics

#### `app/src/main/java/com/pitchslap/app/ui/screens/SettingsScreen.kt`

- **Status**: ❌ TODO
- **Purpose**: App settings and model management
- **Estimated Lines**: ~120
- **Dependencies**: PitchSlapApplication
- **Key Features**:
    - Model download/selection
    - Language selection
    - Reset progress

#### `app/src/main/java/com/pitchslap/app/navigation/NavGraph.kt`

- **Status**: ❌ TODO
- **Purpose**: Jetpack Compose navigation setup
- **Estimated Lines**: ~80
- **Dependencies**: All screen files
- **Key Features**: Navigation routes and composable integration

#### `app/src/main/java/com/pitchslap/app/data/SessionRepository.kt`

- **Status**: ❌ TODO (Optional)
- **Purpose**: Database access for session history
- **Estimated Lines**: ~150
- **Dependencies**: Room database
- **Key Features**:
    - CRUD operations for sessions
    - Flow-based data access
    - Statistics calculation

#### `app/src/main/java/com/pitchslap/app/data/SessionDao.kt`

- **Status**: ❌ TODO (Optional)
- **Purpose**: Room DAO for database operations
- **Estimated Lines**: ~50
- **Dependencies**: Room
- **Key Features**: SQL queries for session data

#### `app/src/main/java/com/pitchslap/app/data/AppDatabase.kt`

- **Status**: ❌ TODO (Optional)
- **Purpose**: Room database configuration
- **Estimated Lines**: ~80
- **Dependencies**: Room, SessionDao
- **Key Features**: Database initialization and migrations

---

## 📂 Configuration Files (DO NOT MODIFY)

### Build Configuration

#### `app/build.gradle.kts`

- **Purpose**: App-level Gradle build configuration
- **Contains**: Dependencies, SDK versions, build types
- **Modify**: Only to add new dependencies

#### `build.gradle.kts`

- **Purpose**: Project-level Gradle configuration
- **Modify**: Rarely needed

#### `gradle/libs.versions.toml`

- **Purpose**: Centralized dependency version management
- **Modify**: To update library versions

#### `settings.gradle.kts`

- **Purpose**: Project settings and repository configuration
- **Modify**: Rarely needed

#### `gradle.properties`

- **Purpose**: Gradle build properties
- **Modify**: Rarely needed

#### `local.properties`

- **Purpose**: Local SDK paths (gitignored)
- **Modify**: Automatically managed by Android Studio

---

### Android Configuration

#### `app/src/main/AndroidManifest.xml`

- **Purpose**: App manifest (permissions, activities, etc.)
- **Current Permissions**: RECORD_AUDIO
- **Modify**: To add new permissions or components

---

## 📂 Documentation (REFERENCE ONLY)

### Active Guides (Use These)

#### `PROJECT_ROADMAP.md`

- **Purpose**: Master execution plan (2847 lines)
- **Status**: ACTIVE - Primary reference document
- **Use For**: Understanding all phases, tasks, and technical details

#### `VISUAL_ROADMAP.md`

- **Purpose**: Visual diagrams and timelines
- **Status**: ACTIVE
- **Use For**: Quick overview of project structure and dependencies

#### `ROADMAP_SUMMARY.md`

- **Purpose**: Summary of roadmap enhancements
- **Status**: ACTIVE
- **Use For**: Understanding what was added to roadmap

#### `START_HERE.md`

- **Purpose**: Navigation guide for all documentation
- **Status**: ACTIVE
- **Use For**: Finding the right documentation quickly

#### `README.md`

- **Purpose**: Public-facing documentation
- **Status**: ACTIVE - Will be enhanced in Phase 8
- **Use For**: External viewers and submission

#### `RUNANYWHERE_SDK_COMPLETE_GUIDE.md`

- **Purpose**: Comprehensive SDK API reference (2064 lines)
- **Status**: ACTIVE
- **Use For**: Phase 5 (AI Integration) - Model APIs and usage

#### `VAD_TESTING_GUIDE.md`

- **Purpose**: Testing Voice Activity Detection
- **Status**: ACTIVE
- **Use For**: Debugging VAD issues, understanding VAD behavior

#### `INTERRUPT_LOGIC_TESTING_GUIDE.md`

- **Purpose**: Testing barge-in logic
- **Status**: ACTIVE
- **Use For**: Debugging interrupt issues, testing barge-in

---

### Archived Documentation (Reference Only - In docs/archive/)

#### `docs/archive/REFACTORING_SUMMARY.md`

- **Purpose**: Phase 1 architecture refactoring summary
- **Status**: ARCHIVED - Phase 1 complete ✅
- **Use For**: Historical reference only

#### `docs/archive/VAD_IMPLEMENTATION_SUMMARY.md`

- **Purpose**: Phase 2 VAD implementation summary
- **Status**: ARCHIVED - Phase 2 complete ✅
- **Use For**: Historical reference, understanding VAD design decisions

#### `docs/archive/INTERRUPT_LOGIC_IMPLEMENTATION_SUMMARY.md`

- **Purpose**: Phase 3 interrupt logic implementation summary
- **Status**: ARCHIVED - Phase 3 complete ✅
- **Use For**: Historical reference, understanding interrupt design decisions

---

## 🎯 AI Agent Execution Rules

### Before Creating Any File:

1. ✅ **Check this inventory** - Ensure file doesn't already exist
2. ✅ **Verify dependencies** - All dependencies must be complete
3. ✅ **Check file dependency graph** - Follow correct creation order
4. ✅ **Review similar files** - Use existing patterns (VAD, InterruptLogic)
5. ✅ **Read phase instructions** - In PROJECT_ROADMAP.md

### File Naming Conventions:

- **Classes**: PascalCase (e.g., `VoiceActivityDetection.kt`)
- **Files**: Same as primary class name
- **Packages**: lowercase (e.g., `com.pitchslap.app.audio`)

### Package Structure Rules:

```
com.pitchslap.app/
├── [root]           - Application class, MainActivity
├── logic/           - Core voice logic (VAD, Interrupt)
├── audio/           - Audio I/O (recorder, TTS)
├── ai/              - AI services (Whisper, LLM)
├── prompts/         - System prompts and schemas
├── data/            - Data models and repositories
├── conversation/    - Conversation orchestration
├── ui/              - User interface
│   ├── screens/     - Screen composables
│   ├── navigation/  - Navigation setup
│   └── theme/       - Theme configuration
```

### Code Quality Standards (All Files):

- ✅ KDoc comments on all public functions/classes
- ✅ Meaningful variable names
- ✅ Proper null safety (`?`, avoid `!!`)
- ✅ Coroutine best practices (correct Dispatcher)
- ✅ StateFlow for UI-reactive data
- ✅ Comprehensive error handling (try-catch)
- ✅ Logging with TAG pattern: `private const val TAG = "PitchSlap_[ComponentName]"`

### Modification Guidelines:

| File Status | Can Modify? | When to Modify |
|-------------|-------------|----------------|
| ✅ Complete | ⚠️ AVOID | Only for bug fixes or critical improvements |
| 🔨 In Progress | ✅ YES | During assigned phase |
| ❌ Not Created | ✅ YES | When dependencies are complete |
| 📄 Config Files | ⚠️ CAREFUL | Only when adding dependencies/features |

---

## 📊 Project Statistics

### Current Status (as of Phase 0 completion):

- **Total Source Files**: 6 complete, 15 to create
- **Total Lines of Code**: ~900 (complete), ~2100 (remaining)
- **Completion**: 40% (Phases 1-3 done)
- **Remaining Phases**: 5 (Phases 4-8)
- **Estimated Time**: 33-45 hours

### File Count by Status:

- ✅ **Complete**: 6 files (PitchSlapApplication, MainActivity, VAD, InterruptLogic, Theme files)
- ❌ **To Create**: 15 files (Audio, AI, Conversation, UI, Data)
- 📄 **Config**: 6 files (Gradle, Manifest)
- 📖 **Documentation**: 9 files (4 active guides, 5 archived)

---

## 🔍 Quick File Lookup

**Need to implement audio recording?**
→ Create `app/src/main/java/com/pitchslap/app/audio/AudioRecorder.kt`

**Need to add AI integration?**
→ Start with `app/src/main/java/com/pitchslap/app/ai/WhisperService.kt`

**Need to create data models?**
→ Create `app/src/main/java/com/pitchslap/app/data/PronunciationFeedback.kt`

**Need to orchestrate everything?**
→ Create `app/src/main/java/com/pitchslap/app/conversation/VoiceConversationManager.kt`

**Need to build UI?**
→ Start with `app/src/main/java/com/pitchslap/app/ui/screens/HomeScreen.kt`

**Need to reference existing patterns?**
→ Read `VoiceActivityDetection.kt` or `InterruptLogic.kt`

**Need SDK API reference?**
→ Open `RUNANYWHERE_SDK_COMPLETE_GUIDE.md`

---

## 🚨 Critical Reminders

### NEVER:

- ❌ Skip phases or work out of order
- ❌ Create files before dependencies are complete
- ❌ Modify completed files (unless fixing bugs)
- ❌ Proceed if build fails
- ❌ Use wildcard imports (`import com.example.*`)
- ❌ Leave TODO comments in final code

### ALWAYS:

- ✅ Test after creating each file (`./gradlew build`)
- ✅ Add comprehensive logging
- ✅ Handle errors gracefully
- ✅ Follow existing code patterns
- ✅ Update this inventory if structure changes
- ✅ Check Logcat for runtime verification

---

## 🔄 File Dependency Graph

```
Phase Order: 0 → 4 → 5 → 6 → 7 → 8

Phase 4 Dependencies:
  AudioRecorder.kt → VoiceActivityDetection.kt (✅)

Phase 5 Dependencies:
  WhisperService.kt → AudioRecorder.kt (Phase 4)
  PronunciationFeedback.kt → None
  LanguageCoachPrompts.kt → PronunciationFeedback.kt (same phase)
  FeedbackGenerator.kt → PronunciationFeedback.kt + LanguageCoachPrompts.kt
  TextToSpeechEngine.kt → InterruptLogic.kt (✅)

Phase 6 Dependencies:
  ConversationTurn.kt → PronunciationFeedback.kt (Phase 5)
  VoiceConversationManager.kt → ALL Phase 4 & 5 components

Phase 7 Dependencies:
  All UI Screens → VoiceConversationManager.kt (Phase 6)
  NavGraph.kt → All Screen files
  SessionRepository.kt → ConversationTurn.kt (Phase 6)
```

**Rule**: A file can only be created when ALL its dependencies exist!

---

*This inventory will be updated as the project evolves.*  
*Last Updated: Phase 0 completion*  
*Next Update: After each major phase completion*

