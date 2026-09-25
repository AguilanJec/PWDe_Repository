# PWDe (SAWADIKAP) — Android

An accessibility-first gaming companion for people with disabilities: hands-free control through head/face tracking, voice, and a guided setup assistant (GabAI).


## Build

- Open this folder in Android Studio, or run `./gradlew assembleDebug`. The APK is written to `app/build/outputs/apk/debug/`.
- Run the tests with `./gradlew testDebugUnitTest` (JVM + Robolectric; no device needed).
- Toolchain: AGP 9.4 with built-in Kotlin 2.2, KSP 2.3 for Room, compileSdk 37, minSdk 24.

## Architecture (MVVM, manual DI)

```
com.pwde.app
├── di/AppContainer        singletons, reached via PwdeApplication.container
├── data/
│   ├── prefs/             SettingsRepository (DataStore → Flow<UserSettings>)
│   ├── local/             Room: CalibrationProfile, GameProfile, ControlSettings
│   │                      + ProfileRepository, ControlsRepository
│   ├── remote/            AuthRepository (Firebase or guest-only), SyncRepository (no-op)
│   ├── speech/            SpeechOutput (Android TextToSpeech)
│   ├── media/             TutorialPlayer (ExoPlayer)
│   └── model/             gesture catalog, control tuning, voice options, games
├── sensors/
│   ├── face/              FaceTrackingManager (CameraX + MediaPipe Face Landmarker),
│   │                      GestureClassifier, Cursor/JoystickMapper, OrientationHeadTracker
│   └── voice/             VoiceCommandManager (Android SpeechRecognizer), CommandMatcher
└── ui/
    ├── theme/             PwdeTheme + ThemeViewModel (drives the whole app from settings)
    ├── components/        design-system components (buttons, cards, steppers, voice bar…)
    ├── navigation/        Routes, PwdeNavHost, ScreenReaderViewModel
    └── <feature>/         one screen + ViewModel per feature
```

- Composables never touch Room, DataStore, Firebase, TTS, ExoPlayer, CameraX or SpeechRecognizer directly. They observe `StateFlow` from a ViewModel, and the ViewModel talks to a repository or manager.
- **Adaptive UI is app-wide.** `MainActivity` wraps the `NavHost` in `PwdeTheme(settings)`:
  - Color scheme: Default, High contrast, Light or Color-safe.
  - Text size: scales every `sp` through `LocalDensity`, on top of the system font scale.
  - Layout mode: Standard, Compact, or Easy reach (content moves to the lower half of the screen).
- ViewModels are created with `pwdeViewModel { container -> … }`.

## Optional Firebase sign-in

The app runs fully as a **guest** with no Firebase config. When no config is found, `AuthRepository` falls back to guest-only mode. To turn on email/password sign-in, add these to `local.properties`:

```
pwde.firebase.apiKey=...
pwde.firebase.appId=...
pwde.firebase.projectId=...
```

Signing in only adds (future) cloud sync. It never gates features and never deletes local data. `SyncRepository` is a no-op stub in this build, and the Profile screen says so.

## Input pipelines

**Head & face tracking.** `FaceTrackingManager` runs the front camera through MediaPipe's Face Landmarker with blendshapes and the facial transformation matrix. This is modelled on [Google Project GameFace](https://github.com/google/project-gameface); see `NOTICE`.
- Head pose (yaw/pitch/roll) comes from the transformation matrix.
- Gestures are thresholds on blendshape scores, each with a 1–10 sensitivity. Tilt and nod come from head pose instead.
- Yaw and pitch move the pointer (relative movement, per-direction speed, smoothing). Roll and pitch drive an 8-way joystick with a dead zone and a saved center.
- The camera runs only while a screen is showing tracking. It stops a few seconds after that screen leaves the foreground.
- The model ships in `app/src/main/assets/face_landmarker.task`, so builds work offline.

**Voice commands.** `VoiceCommandManager` wraps Android `SpeechRecognizer` and only listens while PWDe is on screen; there is no system-wide listening.
- Matching is exact phrase or word-anywhere. Activation is right away (on partial results) or after you finish.
- Standard commands work everywhere: back, home, next, skip, settings, menu, close. So do your shortcuts (cursor mode, joystick mode, switch profile).
- Each screen adds its own commands, usually the names on its cards and buttons.

**Fallbacks (never a crash, never a block).**
- No camera permission or no front camera: the phone's motion sensors stand in for your head. Every place this is active shows **"Demo Mode: Simulated Head Tracking"**.
- No mic permission or no recognition service: the voice bar's keyboard button takes typed commands. They go through the same matching and command catalog.

**Testing Station** is a development tool, so only debug builds have it. Its code lives in `src/debug/`, and `src/release/` provides a no-op twin. A release build has neither the Dashboard card nor the route, and no Testing Station classes are in the APK.

## Real vs. placeholder in this build

| Area | Status |
|---|---|
| Splash, Welcome, Sign in / Create account / Reset password | Real (sign-in needs Firebase config) |
| Setup: needs, appearance (live preview), input mode | Real, saved to DataStore on Continue. The "Try it now" pointer is live head tracking |
| Voice tutorial, including TTS read-aloud and speed | Real (Android TextToSpeech), voice-controllable |
| Input mode (Controls) | Real; switches the tracking output (pointer / joystick) live |
| Gestures + per-gesture sensitivity | Real: saved to Room, with a live "try it" meter |
| Cursor speed, joystick | Real: live camera, live pointer/joystick; settings saved to Room |
| Voice configuration | Real: live mic level, on/off, matching and activation modes, command list |
| Testing Station (debug builds only) | Real: live face, gesture, voice, cursor and joystick readouts |
| Watch Tutorial | Real player with a placeholder video (`res/raw/tutorial_placeholder.mp4`) |
| Games, game detail | Real, voice-selectable |
| Playing view | Live PWDe overlay (voice, gestures, pointer/joystick, actions) over a **simulated** game background |
| Profile | Real: guest/signed-in state, profile lists with rename/delete, sync status |
| GabAI | Welcome screen only; each choice shows "Coming in Prompt 3" |

Gesture actions like Notifications, All apps and Touch & hold act inside PWDe's overlay only. PWDe has no accessibility service or system-level control.
