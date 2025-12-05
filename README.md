# Pitch Slap - High-Performance Voice Application

A high-performance Android voice application built on the RunAnywhere SDK for on-device AI inference.

## Project Status

✅ **Phase 1: Architecture Refactoring** - Complete  
✅ **Phase 2: Voice Activity Detection** - Complete  
✅ **Phase 3: Interrupt Logic (Barge-In Detection)** - Complete  
🚧 **Phase 4: AI Integration & Data Models** - Next

The project has been refactored from the RunAnywhere SDK chat example into a clean foundation for building the Pitch
Slap voice application.

## Current Architecture

### Package Structure (MVVM)

```
com.pitchslap.app/
├── PitchSlapApplication.kt    # SDK initialization & model registration
├── MainActivity.kt             # Main entry point with placeholder UI
├── ui/                        # Jetpack Compose screens
│   └── theme/                 # Material 3 theme configuration
├── logic/                     # Voice Activity Detection & Interrupt logic
│   ├── VoiceActivityDetection.kt
│   └── InterruptLogic.kt
└── data/                      # Structured output models
    └── Models.kt
```

### Key Components

- **Application ID**: `com.pitchslap.app`
- **Project Name**: PitchSlap
- **SDK**: RunAnywhere SDK v0.1.3-alpha with LlamaCpp module
- **Architecture**: MVVM with Jetpack Compose

## What's Working

✅ RunAnywhere SDK initialization  
✅ Model registration (Qwen 2.5 0.5B Instruct)  
✅ Clean package structure for MVVM  
✅ Voice Activity Detection (VAD) with real-time amplitude monitoring  
✅ Interrupt Logic (Barge-In Detection) for turn-taking  
✅ Test UI with visual feedback for VAD + Interrupts  
✅ Comprehensive logging for debugging  
✅ Event-driven architecture with Kotlin Flows

## Next Steps

The following components need to be implemented:

1. ✅ ~~**Voice Activity Detection**~~ - Real-time audio input processing (DONE!)
2. ✅ ~~**Interrupt Handling**~~ - Smart turn-taking logic (DONE!)
3. **AI Integration** - Connect VAD + Interrupts to RunAnywhere SDK
4. **Data Models** - Structured output models for pitch evaluation
5. **Voice UI** - Custom voice interface for pitch practice

## Quick Start

### 1. Test Voice Activity Detection + Interrupt Logic

```bash
# Open in Android Studio and run on device
# The app will show an Interrupt Test interface
```

**Testing the System:**

1. Grant microphone permission when prompted
2. Tap "Start VAD" button
3. Tap "🤖 Simulate AI Talking" - blue banner appears
4. **Speak into microphone** - banner instantly disappears! (Barge-in detected)
5. Check statistics for barge-in counter

**Logcat Filters:**

- `PitchSlap_VAD` - See real-time RMS amplitude values
- `PitchSlap_Interrupt` - See barge-in detection events

**📖 See [VAD_TESTING_GUIDE.md](VAD_TESTING_GUIDE.md)
and [INTERRUPT_LOGIC_TESTING_GUIDE.md](INTERRUPT_LOGIC_TESTING_GUIDE.md) for detailed instructions**

### 2. Initialize the SDK

The app will automatically:

- Initialize RunAnywhere SDK in development mode
- Register the LlamaCpp service provider
- Register available AI models
- Scan for previously downloaded models

## Technical Details

### SDK Components

- **RunAnywhere Core SDK**: Component architecture and model management
- **LlamaCpp Module**: Optimized llama.cpp inference engine with 7 ARM64 variants
- **Kotlin Coroutines**: For async operations and streaming

### Dependencies

- Kotlin Coroutines for async/streaming
- Ktor for networking
- AndroidX WorkManager for background tasks
- AndroidX Room for local storage
- Jetpack Compose for UI
- Material 3 design system

## Requirements

- Android 7.0 (API 24) or higher
- ~500 MB free storage (for models)
- Internet connection (for downloading models)
- Microphone permission (to be added)

## Development

### Adding Models

Edit `PitchSlapApplication.kt` → `registerModels()` to add new models from HuggingFace.

### Customizing UI

All UI code is in `MainActivity.kt` and the `ui/` package. The current placeholder will be replaced with a custom voice
interface.

### Implementing Voice Logic

- `logic/VoiceActivityDetection.kt` - ✅ VAD implementation complete!
- `logic/InterruptLogic.kt` - ✅ Interrupt logic complete!

**Voice Logic Features:**

**VAD (Voice Activity Detection):**

- Real-time audio streaming at 16kHz
- RMS amplitude calculation
- Threshold-based speech detection (configurable)
- 500ms silence timeout
- StateFlow for reactive UI updates

**Interrupt Logic (Traffic Controller):**

- Monitors user + AI speaking states
- Detects barge-in events (user interrupts AI)
- Immediate AI cutoff on interrupt
- Event stream for reactive cancellation
- Statistics tracking for analytics

## Resources

- [RunAnywhere SDK Repository](https://github.com/RunanywhereAI/runanywhere-sdks)
- [SDK Documentation](https://github.com/RunanywhereAI/runanywhere-sdks/blob/main/CLAUDE.md)
- [Complete SDK Guide](RUNANYWHERE_SDK_COMPLETE_GUIDE.md)

## License

This application follows the license of the RunAnywhere SDK.
