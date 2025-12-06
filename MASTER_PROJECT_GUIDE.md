# 🎯 Pitch Slap - Master Project Guide

**Current Status**: ✅ Phase 6 Complete (Conversation Manager with Silence Nudge)
**Next Step**: Phase 7 (Production UI)

---

## 🚀 Project Overview

**Pitch Slap** is a zero-latency voice language practice application.
- **VAD**: <20ms latency speech detection
- **Barge-in**: Users can interrupt AI instantly
- **Silence Nudge**: AI speaks up if user is silent for 15s
- **Offline-first**: On-device processing

---

## 🛠️ Technical Architecture

```
User Speaks → VAD Detects → AudioRecorder → Whisper STT
    ↓
Transcript → LLM (Language Coach) → JSON Feedback
    ↓
TTS Speaks Feedback
    ↓
(Loop) OR (Barge-in) OR (Silence Timeout -> Nudge)
```

### Key Components (All in `app/src/main/java/com/pitchslap/app/`)

1. **`logic/VoiceActivityDetection.kt`** (✅ Done)
   - Detects speech using RMS amplitude (Threshold: 2000)
   - 500ms silence detection

2. **`logic/InterruptLogic.kt`** (✅ Done)
   - Handles barge-in events
   - Cuts off AI speech immediately

3. **`conversation/VoiceConversationManager.kt`** (✅ Done + Updated)
   - Orchestrates the entire flow
   - **NEW**: 15s silence timeout triggers AI nudge ("Are you still there?")
   - Manages state: `IDLE`, `LISTENING`, `RECORDING`, `PROCESSING`, `AI_SPEAKING`

4. **`ai/` Package** (✅ Done)
   - `WhisperService.kt`: STT (currently Android recognizer fallback)
   - `FeedbackGenerator.kt`: LLM integration
   - `TextToSpeechEngine.kt`: TTS output

---

## 📋 Execution Checklist

### ✅ Phase 1-3: Core Voice Tech
- [x] Voice Activity Detection
- [x] Interrupt Logic
- [x] Basic Architecture

### ✅ Phase 4-5: Audio & AI
- [x] Audio Recording
- [x] Whisper STT Integration
- [x] LLM Feedback Generation
- [x] TTS Output

### ✅ Phase 6: Conversation Orchestration
- [x] Conversation State Machine
- [x] Barge-in Integration
- [x] **Silence Nudge (15s timeout)**

### ⬜ Phase 7: Production UI (Next)
- [ ] Build `PracticeScreen` (Main voice interface)
- [ ] Build `HistoryScreen` (Progress tracking)
- [ ] Build `SettingsScreen`
- [ ] Implement Navigation

### ⬜ Phase 8: Polish & Ship
- [ ] Demo Video
- [ ] Final Testing
- [ ] README Update

---

## 🧪 How to Test the New Silence Feature

1. **Launch App**: `adb shell am start -n com.pitchslap.app/.MainActivityProduction`
2. **Start Conversation**: The app enters `LISTENING` state.
3. **Wait**: Say nothing for 15 seconds.
4. **Observe**:
   - Logs: `⏳ User silent for 15s - Initiating AI nudge`
   - AI Voice: "Are you still there?" or similar.
   - State: Changes to `AI_SPEAKING` -> `LISTENING`.

## 👩‍💻 Developer Quick Reference

**Build & Install:**
```bash
./gradlew installDebug
```

**View Logs:**
```bash
adb logcat -s PitchSlap_ConvoManager PitchSlap_VAD
```

**File Locations:**
- **Logic**: `app/src/main/java/com/pitchslap/app/logic/`
- **Conversation**: `app/src/main/java/com/pitchslap/app/conversation/`
- **UI**: `app/src/main/java/com/pitchslap/app/ui/`

---

*Last Updated: After adding Silence Nudge feature*
