# 🤖 Project JARVIS - Autonomous Android AI Assistant

A full-stack, production-ready, zero-cost Android AI assistant inspired by Iron Man's JARVIS. Operates as an autonomous on-device agent featuring real-time natural human voice, hands-free wake word detection, autonomous screen parsing/gestures, and a mobile coding sandbox.

---

## 🌟 Key Features

1. **Apple iOS VisionOS Dark Glassmorphic UI**:
   - Deep OLED Black canvas (`#000000`).
   - Frosted translucent glass surfaces with 1px specular gradient borders.
   - Draggable **Floating Arc Reactor Orb** system overlay that floats over any Android app with magnetic edge snapping.
   - Reactive audio wave animations that pulse with the sound of your voice.

2. **100% Free Natural Human Voice Pipeline**:
   - **Multi-Wake Word Detection**: Hands-free background listening for `"Hey Jarvis"`, `"Suno Jarvis"`, `"O Jarvis"`, and `"Bolo Jarvis"`.
   - **Microsoft Edge Neural Voice**: 0% robotic tone! Natural human breathing and inflection using `hi-IN-MadhurNeural` (Hindi) and `en-US-ChristopherNeural` (Authentic JARVIS).
   - **Barge-In Support**: Interrupt JARVIS at any moment while he is speaking.

3. **Autonomous Phone Control (The Hands & Eyes)**:
   - **Hybrid Screen Element Finder**: Parses Android UI view hierarchies in under 15ms with 0 internet data cost.
   - **Autonomous Gestures**: Clicks buttons, scrolls feeds, inputs text, and launches apps.
   - **Visual Laser Tap Pointer**: Displays an animated neon-green ring on screen showing exact tap coordinates.
   - **Security Guardrail**: Automatically blocks autonomous taps in banking/UPI apps (Google Pay, Paytm, PhonePe) to protect financial privacy.

4. **Mobile Code & Website Sandbox**:
   - Asks JARVIS: *"Jarvis, mere liye portfolio website banao"*.
   - Generates production HTML/Tailwind/JS code directly onto phone storage (`/sdcard/JarvisProjects/`).
   - Instant in-app live preview via embedded WebView.

---

## 🛠️ Architecture Structure

```
app/src/main/java/com/jarvis/ai/
├── JarvisApplication.kt          # Notification channels & lifecycle
├── MainActivity.kt               # Compose edge-to-edge root activity
│
├── ui/
│   ├── theme/                    # Color, Type, Theme (OLED Dark Glass)
│   ├── components/               # GlassModifier, GlassCard, ArcReactorOrb, PillButton
│   └── screens/                  # DashboardScreen, CodeSandboxScreen
│
├── voice/
│   ├── WakeWordEngine.kt         # On-device hotword detector
│   ├── EdgeNeuralTtsEngine.kt    # Free Microsoft Edge neural human voice
│   ├── SpeechToTextManager.kt    # Low-latency speech recognition
│   └── JarvisVoiceCoordinator.kt # Master pipeline orchestrator
│
├── ai/
│   └── GeminiBrainClient.kt      # Google AI Studio Gemini 2.0 Flash client
│
├── control/
│   ├── JarvisAccessibilityService.kt # Android system accessibility service
│   ├── ScreenNodeParser.kt       # Recursive UI hierarchy inspector
│   ├── GestureExecutor.kt        # Native touch & swipe dispatcher
│   ├── VisualTouchFeedbackOverlay.kt # Neon laser pointer overlay
│   ├── HybridElementFinder.kt    # Dual-engine screen element detector
│   └── SecurityGuardrail.kt      # Banking & financial safety filters
│
└── storage/
    └── ProjectFileManager.kt     # Mobile storage project manager
```

---

## 🚀 Building & Running the Project

### Requirements:
- Android SDK 34 (Android 8.0 - Android 14+)
- JDK 17
- Gradle 8.3+

### Build APK via Terminal:
```bash
# Build Debug APK
./gradlew assembleDebug

# Output APK path:
# app/build/outputs/apk/debug/app-debug.apk
```

### Required Permissions Setup (On-Device):
1. **Draw Over Other Apps**: Settings → Apps → JARVIS → Display over other apps (Enable for Floating Arc Orb).
2. **Accessibility Service**: Settings → Accessibility → Downloaded Apps → JARVIS AI (Turn ON for screen control & auto-clicking).
3. **Microphone**: Allow audio recording for hands-free wake word.
