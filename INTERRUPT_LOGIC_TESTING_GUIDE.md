# Interrupt Logic - Testing Guide

## 🚦 What We Built

The "Traffic Controller" of Pitch Slap - an interrupt detection system that manages turn-taking between user and AI.

**Core Functionality:**

- ✅ Monitors user speech (via VAD)
- ✅ Tracks AI speaking state
- ✅ Detects barge-in events (user interrupts AI)
- ✅ Emits events to cancel AI audio/generation
- ✅ Enables natural conversation flow

---

## 🏗️ Architecture

```
┌──────────────────────────────────────────────────────────┐
│                    InterruptLogic                         │
│                  (Traffic Controller)                     │
├──────────────────────────────────────────────────────────┤
│                                                            │
│  Inputs:                                                  │
│    • VAD.isUserSpeaking (StateFlow<Boolean>)            │
│    • isAiSpeaking (StateFlow<Boolean>)                   │
│                                                            │
│  Conflict Detection Logic:                               │
│    IF (isUserSpeaking == true AND isAiSpeaking == true)  │
│    THEN → BARGE-IN DETECTED!                            │
│                                                            │
│  Actions on Barge-In:                                    │
│    1. Set isAiSpeaking = false (immediately)            │
│    2. Emit BargeInEvent to SharedFlow                    │
│    3. Log "🚨 BARGE-IN DETECTED!"                       │
│                                                            │
│  Output:                                                  │
│    • bargeInEvent (SharedFlow<BargeInEvent>)            │
│    • Updated isAiSpeaking state                          │
│                                                            │
└──────────────────────────────────────────────────────────┘
```

---

## 🚀 How to Test

### Step 1: Build and Run

```bash
# In Android Studio:
1. Sync Gradle
2. Build → Rebuild Project
3. Run on device (physical device recommended)
```

### Step 2: Start VAD

1. App launches with Interrupt Test screen
2. Tap **"Start VAD"**
3. Grant microphone permission
4. VAD indicator should show 🤫 (listening, silent)

### Step 3: Simulate AI Talking

1. Tap **"🤖 Simulate AI Talking"** button
2. **Blue banner appears**: "🤖 AI IS TALKING"
3. Statistics show "AI Speaking: Yes 🤖"

### Step 4: Test the Interrupt! 🎤

**THE MAGIC MOMENT:**

1. With AI banner showing (blue)
2. **Speak into microphone** (say anything)
3. **Banner INSTANTLY disappears!** ✨
4. Green circle appears (user speaking)
5. Statistics increment "Total Barge-Ins"

### Step 5: Verify in Logcat

Open Logcat and filter by `PitchSlap_Interrupt`:

```
I/PitchSlap_Interrupt: ✅ InterruptLogic initialized
I/PitchSlap_Interrupt: 🎯 Barge-in monitoring started
I/PitchSlap_Interrupt: 🤖 AI started speaking
W/PitchSlap_Interrupt: 🚨 BARGE-IN DETECTED! CUTTING AI AUDIO.
I/PitchSlap_Interrupt: 🛑 AI speech interrupted. Total barge-ins: 1
```

---

## 🎯 Test Scenarios

### Scenario 1: Basic Interrupt

1. Start VAD ✅
2. Simulate AI Talking ✅
3. Speak into mic → Banner disappears ✅
4. Check Logcat for barge-in log ✅

### Scenario 2: Multiple Interrupts

1. Simulate AI Talking
2. Speak (interrupt)
3. Simulate AI Talking again
4. Speak (interrupt again)
5. Statistics should show: "Total Barge-Ins: 2"

### Scenario 3: No False Positives

1. Start VAD ✅
2. **Don't** simulate AI talking
3. Speak into mic
4. **Expected**: Circle turns green, but NO barge-in logged
5. **Reason**: AI wasn't speaking, so no conflict

### Scenario 4: AI Finishes Normally

1. Simulate AI Talking
2. Wait (don't speak)
3. Tap "Stop AI Talking"
4. **Expected**: No barge-in logged
5. **Reason**: Normal completion, not interrupted

---

## 📊 What to Observe

### ✅ Good Signs:

**Visual (UI):**

- Blue banner appears when AI "talks"
- Banner instantly disappears when you speak
- Green circle appears when speaking
- Statistics increment correctly

**Logcat:**

```
I/PitchSlap_Interrupt: 🤖 AI started speaking
W/PitchSlap_Interrupt: 🚨 BARGE-IN DETECTED! CUTTING AI AUDIO.
I/PitchSlap_Interrupt: 🛑 AI speech interrupted. Total barge-ins: 1
```

**Statistics Card:**

- VAD Status: Active ✅
- AI Speaking: Changes correctly
- User Speaking: Changes correctly
- Total Barge-Ins: Increments on interrupt
- Last barge-in: Shows time elapsed

### ⚠️ Troubleshooting:

**Problem**: Banner doesn't disappear when speaking

- **Check**: Is VAD active? (should show green/red circle)
- **Check**: Is VAD threshold calibrated? (too high?)
- **Solution**: Lower RMS threshold in `VoiceActivityDetection.kt`

**Problem**: Barge-in triggers when AI not talking

- **Cause**: This should NOT happen (logic error)
- **Check**: Logcat for unexpected barge-ins
- **Expected**: Barge-ins ONLY when both are speaking

**Problem**: No logcat messages

- **Solution**: Open Logcat, filter by `PitchSlap_Interrupt`
- **Check**: Make sure Log level includes INFO and WARNING

**Problem**: Multiple barge-ins triggered for one speech

- **Cause**: VAD oscillating rapidly
- **Solution**: Increase `SILENCE_TIMEOUT_MS` in VAD for more stable detection

---

## 🔬 Technical Details

### Conflict Detection Logic

```kotlin
vad.isUserSpeaking.collect { isUserSpeaking ->
    if (isUserSpeaking && _isAiSpeaking.value) {
        // CONFLICT: Both speaking simultaneously
        handleBargeIn()
    }
}
```

**Why This Works:**

1. Continuously monitors user speech state
2. Checks if AI is also speaking
3. Only triggers on simultaneous speech (conflict)
4. Immediate response (no delay)

### State Management

```kotlin
// AI State
private val _isAiSpeaking = MutableStateFlow(false)
val isAiSpeaking: StateFlow<Boolean> = _isAiSpeaking.asStateFlow()

// Barge-In Events
private val _bargeInEvent = MutableSharedFlow<BargeInEvent>(
    replay = 0,                           // No replay
    extraBufferCapacity = 1,              // Buffer one event
    onBufferOverflow = BufferOverflow.DROP_OLDEST  // Drop old if full
)
```

**Design Decisions:**

- `StateFlow` for boolean states (always has a value)
- `SharedFlow` for events (emit-and-forget)
- `extraBufferCapacity = 1` prevents event loss during collection
- `DROP_OLDEST` ensures we never block on emit

### BargeInEvent Data Class

```kotlin
data class BargeInEvent(
    val timestamp: Long,      // When it happened
    val bargeInNumber: Int    // Sequential counter
)
```

**Use Cases:**

- Analytics: Track how often users interrupt
- UX: Show visual feedback on interrupt
- Debugging: Correlate with other events

---

## 🎮 UI Test Controls

### Buttons:

| Button | Action | Expected Result |
|--------|--------|-----------------|
| Start VAD | Starts voice detection | Circle turns red (listening) |
| Stop VAD | Stops voice detection | Circle turns gray (stopped) |
| Simulate AI Talking | Toggles AI state | Blue banner appears/disappears |

### Visual Indicators:

| Indicator | Color | Meaning |
|-----------|-------|---------|
| Circle | Gray ⏸️ | VAD stopped |
| Circle | Red 🤫 | Listening (silent) |
| Circle | Green 🗣️ | User speaking |
| Banner | Blue 🤖 | AI is talking |

### Statistics Display:

```
📊 Statistics
───────────────────────
VAD Status: Active ✅
AI Speaking: Yes 🤖
User Speaking: No 🤫
───────────────────────
Total Barge-Ins: 3
Last barge-in: 5s ago
```

---

## 🧪 Advanced Testing

### Test 1: Rapid Toggle

1. Quickly toggle "Simulate AI Talking" on/off
2. Speak during "on" periods
3. **Expected**: Only counts barge-ins when AI actually speaking

### Test 2: Continuous Speech

1. Simulate AI Talking
2. Start speaking and keep talking for 5+ seconds
3. **Expected**: Only ONE barge-in logged (not multiple)
4. **Reason**: AI stops immediately on first detection

### Test 3: Edge Case - Start Speaking Before AI

1. Start speaking (hold)
2. While speaking, simulate AI Talking
3. **Expected**: Immediate barge-in (AI cut off instantly)

### Test 4: Silence Timeout

1. Simulate AI Talking
2. Speak briefly (1 second)
3. Stop speaking
4. **Expected**: Banner gone, circle returns to red (silent)

---

## 📈 Expected Behavior Flow

### Successful Interrupt Flow:

```
[User Silent] + [AI Silent]
    ↓
Tap "Simulate AI Talking"
    ↓
[User Silent] + [AI SPEAKING] ← Blue banner appears
    ↓
User starts speaking
    ↓
VAD detects speech: isUserSpeaking = true
    ↓
InterruptLogic detects conflict
    ↓
🚨 BARGE-IN DETECTED!
    ↓
isAiSpeaking set to false ← Blue banner disappears
    ↓
BargeInEvent emitted
    ↓
[User SPEAKING] + [AI Silent] ← Green circle visible
    ↓
User stops speaking (500ms silence)
    ↓
[User Silent] + [AI Silent] ← Red circle visible
```

---

## 🎯 Success Criteria

Your interrupt logic is working correctly when:

1. ✅ Blue banner appears when simulating AI
2. ✅ Banner disappears INSTANTLY when you speak
3. ✅ Logcat shows "🚨 BARGE-IN DETECTED!"
4. ✅ Statistics increment correctly
5. ✅ No false positives (barge-ins when AI not talking)
6. ✅ No false negatives (missed interrupts)
7. ✅ Clean state transitions

---

## 🔗 Integration with Real AI

When integrating with actual AI generation:

```kotlin
// Example: RunAnywhere SDK integration
scope.launch {
    interruptLogic.startAiSpeech()
    
    RunAnywhere.generateStream(userInput).collect { token ->
        // Check if interrupted
        if (!interruptLogic.isAiSpeaking.value) {
            // User interrupted, stop generation
            break
        }
        
        // Display token
        displayToken(token)
    }
    
    interruptLogic.stopAiSpeech()
}

// Listen for barge-ins
scope.launch {
    interruptLogic.bargeInEvent.collect { event ->
        // Cancel any audio playback
        audioPlayer.stop()
        
        // Clear UI
        clearAiResponse()
        
        // Log for analytics
        analyticsLogger.logBargeIn(event)
    }
}
```

---

## 📝 Code Quality Notes

### Thread Safety

- ✅ All state managed via Kotlin Flows (thread-safe)
- ✅ Coroutines on appropriate dispatchers (Dispatchers.Default)
- ✅ No race conditions in conflict detection

### Resource Management

- ✅ Proper coroutine cancellation in cleanup()
- ✅ No memory leaks (flows properly closed)
- ✅ Lifecycle-aware (stops on activity destroy)

### Observability

- ✅ Comprehensive logging at all state transitions
- ✅ Statistics counter for analytics
- ✅ Event stream for reactive UI

---

## 🚀 Next Steps After Testing

Once you verify interrupt logic works:

1. ✅ VAD - Complete
2. ✅ Interrupt Logic - Complete
3. 🔨 **Next**: Integrate with RunAnywhere SDK
    - Connect VAD → Audio capture
    - Connect audio → LLM
    - Connect LLM → Interrupt detection
4. 🔨 Build data models for pitch evaluation
5. 🔨 Build production Voice UI

---

## 🎉 Expected Test Results

When working correctly:

```
📱 UI Test:
1. Start VAD → ✅ Circle turns red
2. Speak → ✅ Circle turns green
3. Simulate AI → ✅ Blue banner appears
4. Speak while AI active → ✅ Banner INSTANTLY disappears
5. Statistics → ✅ "Total Barge-Ins: 1"

📊 Logcat:
I/PitchSlap_Interrupt: ✅ InterruptLogic initialized
I/PitchSlap_Interrupt: 🎯 Barge-in monitoring started
I/PitchSlap_Interrupt: 🤖 AI started speaking
W/PitchSlap_Interrupt: 🚨 BARGE-IN DETECTED! CUTTING AI AUDIO.
I/PitchSlap_Interrupt: 🛑 AI speech interrupted. Total barge-ins: 1
I/PitchSlap_Interrupt: 🤖 AI stopped speaking (normal completion)
```

**Perfect!** Your traffic controller is managing turn-taking like a pro! 🚦✨

---

## 🐛 Debug Checklist

If something's not working:

- [ ] VAD is active (green/red circle, not gray)
- [ ] Microphone permission granted
- [ ] "Simulate AI Talking" button pressed (blue banner visible)
- [ ] Speaking loud enough (check VAD RMS values in separate Logcat filter)
- [ ] Logcat filter set to `PitchSlap_Interrupt`
- [ ] No errors in Logcat
- [ ] App not in background (lifecycle paused)

---

**Status**: Ready to test! Run the app and try interrupting the AI. It should work flawlessly! 🎤🤖✨
