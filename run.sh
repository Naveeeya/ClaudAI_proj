#!/bin/bash
# Pitch Slap - Build and Run Script

export JAVA_HOME="/Users/navya/Library/Java/JavaVirtualMachines/jbr-17.0.14/Contents/Home"
export ANDROID_HOME="/opt/homebrew/share/android-commandlinetools"

echo "🔨 Building Pitch Slap..."
./gradlew assembleDebug --no-daemon

if [ $? -eq 0 ]; then
    echo "✅ Build successful!"
    
    # Check for connected devices
    DEVICES=$($ANDROID_HOME/platform-tools/adb devices | grep -v "List" | grep -v "^$" | wc -l)
    
    if [ "$DEVICES" -gt 0 ]; then
        echo "📱 Installing on device..."
        ./gradlew installDebug --no-daemon
        
        if [ $? -eq 0 ]; then
            echo "🚀 Launching Pitch Slap..."
            $ANDROID_HOME/platform-tools/adb shell am start -n com.pitchslap.app/.MainActivity
            echo "✅ App launched! Check your device."
        fi
    else
        echo "⚠️  No device connected."
        echo "📁 APK location: app/build/outputs/apk/debug/app-debug.apk"
        echo ""
        echo "To install:"
        echo "  1. Connect your Android device with USB debugging enabled"
        echo "  2. Run this script again"
        echo ""
        echo "Or manually install the APK on your device."
    fi
else
    echo "❌ Build failed!"
fi
