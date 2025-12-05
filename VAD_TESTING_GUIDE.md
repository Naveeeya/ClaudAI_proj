# Voice Activity Detection (VAD) - Testing Guide

## 🎤 What We Built

The "Ear" of Pitch Slap - a real-time Voice Activity Detection system that:

- ✅ Streams audio from microphone at 16kHz, 16-bit PCM
- ✅ Calculates RMS (Root Mean Square) amplitude every buffer chunk
- ✅ Detects speech vs silence based on configurable threshold
- ✅ Implements 500ms silence timeout
- ✅ Exposes `StateFlow<Boolean>` for reactive UI
- ✅ Provides detailed Logcat logging for debugging

---

## 🏗️ Architecture

```
VoiceActivityDetection.kt
├── AudioRecord (MediaRecorder.AudioSource.MIC)
├── Background Coroutine (Dispatchers.IO)
├── Audio Buffer Processing Loop
│   ├── Read PCM samples
│   ├── Calculate RMS amplitude
│   └── Update VAD state
└── StateFlow<Boolean> isUserSpeaking
```

---

## 🚀 How to Test

### Step 1: Build and Run

```bash
# In Android Studio:
1. File → Sync Project with Gradle Files
2. Build → Rebuild Project
3. Run on Device/Emulator (physical device recommended for microphone)
```

### Step 2: Grant Microphone Permission

1. App will launch with VAD Test screen
2. Tap **"Start VAD"**
3. Grant microphone permission when prompted
4. VAD will start automatically after permission granted

### Step 3: Observe the UI

**Visual Indicator** (Large Circle):

- ⏸️ **Gray** = VAD stopped
- 🤫 **Red** = VAD active, listening (silent)
- 🗣️ **Green** = Speech detected!

**Try It**:

1. Stay silent → Should show 🤫 Red
2. Speak into microphone → Should turn 🗣️ Green
3. Stop speaking → After 500ms, should return to 🤫 Red

### Step 4: Monitor Logcat (THE IMPORTANT PART!)

This is where the real magic happens. Open Logcat in Android Studio:

1. **Open Logcat**: View → Tool Windows → Logcat
2. **Filter by tag**: Type `PitchSlap_VAD` in the search box
3. **Watch real-time logs**:

```
🎤 Audio stream processing started
RMS: 1234 | Threshold: 2000 | 🤫 SILENT
RMS: 1456 | Threshold: 2000 | 🤫 SILENT
RMS: 3421 | Threshold: 2000 | 🗣️ SPEAKING  ← Speech detected!
🎙️ SPEECH STARTED (RMS: 3421)
RMS: 4567 | Threshold: 2000 | 🗣️ SPEAKING
RMS: 3890 | Threshold: 2000 | 🗣️ SPEAKING
RMS: 1123 | Threshold: 2000 | 🗣️ SPEAKING
RMS: 987 | Threshold: 2000 | 🗣️ SPEAKING
🔇 SPEECH ENDED (Silent for 523ms)  ← Silence detected after 500ms
```

---

## 🔧 Tuning the VAD

If the VAD is too sensitive or not sensitive enough, you can adjust the threshold:

### Edit `VoiceActivityDetection.kt`:

```kotlin
// Line ~31
private const val RMS_THRESHOLD = 2000.0  // ← Adjust this value
```

**Tuning Guide**:

- **Too sensitive** (detects background noise): Increase threshold (try 3000-4000)
- **Not sensitive enough** (misses quiet speech): Decrease threshold (try 1000-1500)
- Watch Logcat RMS values while speaking to find optimal threshold

**Silence Timeout**:

```kotlin
private const val SILENCE_TIMEOUT_MS = 500L  // ← Time before marking as silent
```

- Increase for slower detection (less responsive)
- Decrease for faster detection (more sensitive to pauses)

---

## 📊 What to Look For

### ✅ Good Signs:

- Logcat shows continuous RMS values
- RMS increases significantly when you speak (3000-8000+)
- RMS drops to low values when silent (500-1500)
- "SPEECH STARTED" logs when you begin speaking
- "SPEECH ENDED" logs ~500ms after you stop

### ⚠️ Troubleshooting:

**Problem**: No RMS logs appearing

- **Solution**: Check microphone permission granted
- **Solution**: Check device has working microphone
- **Solution**: Try physical device instead of emulator

**Problem**: Always shows "SPEAKING" (constant green)

- **Cause**: Threshold too low or noisy environment
- **Solution**: Increase `RMS_THRESHOLD` to 3000+

**Problem**: Never detects speech (always red)

- **Cause**: Threshold too high or microphone too quiet
- **Solution**: Decrease `RMS_THRESHOLD` to 1000-1500
- **Solution**: Speak louder or move closer to mic

**Problem**: App crashes on start

- **Cause**: Microphone permission not granted
- **Solution**: Grant permission in app settings, restart app

---

## 🎯 Success Criteria

Your VAD is working correctly when:

1. ✅ Logcat shows real-time RMS values
2. ✅ UI circle turns green when you speak
3. ✅ UI circle turns red when silent
4. ✅ Silence detection works after ~500ms
5. ✅ No crashes or audio errors in Logcat

---

## 🔬 Technical Details

### Audio Configuration:

- **Sample Rate**: 16kHz (optimal for voice)
- **Channel**: Mono
- **Format**: PCM 16-bit signed
- **Buffer**: 2x minimum size for safety

### RMS Calculation:

```
RMS = sqrt(Σ(sample²) / N)
```

- Provides measure of signal power
- Better than peak amplitude for VAD
- Robust to single spike noises

### VAD State Machine:

```
[SILENT] --RMS > threshold--> [SPEAKING]
[SPEAKING] --RMS < threshold for 500ms--> [SILENT]
```

---

## 🧪 Advanced Testing

### Test Different Scenarios:

1. **Normal speech** - Should detect reliably
2. **Whisper** - Might need lower threshold
3. **Shouting** - Should still work
4. **Background music** - Should NOT trigger (if tuned correctly)
5. **Multiple speakers** - Should detect any speech
6. **Coughing/laughing** - Should trigger (it's audio)

### Collect Metrics:

Watch Logcat and note:

- Typical RMS values when speaking: _____
- Typical RMS values when silent: _____
- Optimal threshold for your device: _____

---

## 📱 Device Recommendations

**Best**: Physical Android device with good microphone
**OK**: Emulator with host audio input (may have latency)
**Avoid**: Emulator without audio input

---

## 🎉 Next Steps After VAD Works

Once you verify VAD is working (green circle on speech, red on silence):

1. ✅ VAD implementation complete
2. 🔨 Build interrupt logic (InterruptLogic.kt)
3. 🔨 Integrate with RunAnywhere SDK for AI responses
4. 🔨 Build final Voice UI
5. 🔨 Add pitch evaluation data models

---

## 📞 Debug Commands

If you need to check audio configuration:

```bash
# Check microphone permission
adb shell pm list permissions -d -g

# Check audio devices
adb shell dumpsys audio

# Monitor system logs
adb logcat -s PitchSlap_VAD:* AndroidRuntime:E
```

---

## 🎯 Expected Output (Success)

When working correctly, you should see this pattern:

```
I/PitchSlap_VAD: ✅ VAD Started Successfully
I/PitchSlap_VAD: Sample Rate: 16000 Hz
I/PitchSlap_VAD: Buffer Size: XXXX bytes
I/PitchSlap_VAD: RMS Threshold: 2000.0
I/PitchSlap_VAD: Silence Timeout: 500ms
I/PitchSlap_VAD: 🎤 Audio stream processing started
D/PitchSlap_VAD: RMS: 1234 | Threshold: 2000 | 🤫 SILENT
D/PitchSlap_VAD: RMS: 1456 | Threshold: 2000 | 🤫 SILENT
[... you start speaking ...]
I/PitchSlap_VAD: 🎙️ SPEECH STARTED (RMS: 3421)
D/PitchSlap_VAD: RMS: 3421 | Threshold: 2000 | 🗣️ SPEAKING
[... you stop speaking ...]
I/PitchSlap_VAD: 🔇 SPEECH ENDED (Silent for 523ms)
D/PitchSlap_VAD: RMS: 1123 | Threshold: 2000 | 🤫 SILENT
```

Perfect! Your VAD is ready to be integrated into the full Pitch Slap system! 🎤✨
