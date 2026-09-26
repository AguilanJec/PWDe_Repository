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
│   ├── gabai/             GabAiState (sealed state machine), GabAiFlow (transitions),
│   │                      GabAiRepository (resumable sessions, screenshots)
│   ├── media/             TutorialPlayer (ExoPlayer)
│   └── model/             gesture catalog, control tuning, voice options, games
├── sensors/
│   ├── face/              FaceTrackingManager (CameraX + MediaPipe Face Landmarker),
│   │                      GestureClassifier, Cursor/JoystickMapper, OrientationHeadTracker
│   └── voice/             VoiceCommandManager (app-wide), InGameVoiceEngine (gameplay),
│                          ContinuousSpeechRecognizer (shared plumbing), MicArbiter, CommandMatcher,
│                          WakeWordEngine (sherpa-onnx KWS, debug builds only)
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

## Wake word testing (sherpa-onnx KWS)

The Testing Station has a **Wake word** panel that runs [sherpa-onnx](https://github.com/k2-fsa/sherpa-onnx) keyword spotting on-device, so the team can measure how reliably a wake word fires on a real phone before it goes anywhere near gameplay. Type a phrase, press **Add phrase**, press **Start listening**, and say it.

**Any phrase works** — nothing to train, no account. The English GigaSpeech 3.3M model decides in ~320 ms chunks and listens for several phrases at once. "hey pwde" is the default, and it is confirmed representable:

```
▁HE Y ▁P W DE @k0     ▁P OR C U P IN E @k2     ▁START ▁PLAY ING @k3
```

The panel names any phrase the model can't spell instead of ignoring it silently (`Can't spot: "…"`). Worth knowing: the model matches *sounds*, so "PWDE" becomes the pieces `P W DE` — say it as written rather than spelling the letters out loud.

**How it works.** Phrases are converted to the model's tokens **on the device** by `KeywordList` + `SentencePieceUnigramTokenizer` in `sensors/voice/KeywordTokenizer.kt`, reproducing upstream's Python `sherpa-onnx-cli text2token` step. That is what allows a text field instead of a fixed keyword list.
- Every generated token is validated against the model's `tokens.txt` first: the native spotter **terminates the process** on an unknown token rather than returning an error.
- The keyword list is read from a file at construction, so the model is copied out of assets into `filesDir` once and `keywords.txt` is written next to it.
- `keywordsScore` (3.5) and `keywordsThreshold` (0.05) are pushed well past upstream's 1.5 / 0.25 so the spotter is as sensitive as it can be, catching even marginal utterances at the cost of more false alarms. The same tuned numbers back each phrase's Normal/High/Max preset.

**Every number is editable by hand.** The panel is not limited to the three presets. Open **Tune** beside a phrase to step its own `boost` and `threshold` (0.5 and 0.05 per tap), and the **Spotter defaults** steppers expose the four spotter-wide values — sherpa-onnx's `keywordsScore`, `keywordsThreshold`, `numTrailingBlanks` and `maxActivePaths` — that an untuned phrase inherits. **Apply & restart** pushes both halves into the engine; **Reset** puts every phrase back on the eager preset the app ships with.

- A null per-phrase value is the *inherit* state: the keyword line then carries no `:boost #threshold` at all and uses the spotter's pair, which is what **Normal** selects. Stepping any field turns the pair into explicit numbers seeded from the spotter defaults.
- Steppers, not text fields: every tap is already a valid number, so nothing has to be parsed on the way in. `stepTuningValue` also snaps each move back onto the step grid, so repeated taps can't leave `0.15000001` in the keywords file and stepping away and back restores a preset exactly, letting the toggle light up again.
- Hand-edited values are clamped by `WakeWordSpotterTuning.clamped()` to the range the native spotter accepts (score 0–10, threshold 0–1, 0–10 trailing blanks, 1–32 active paths), so a number typed or stepped out of range can't produce a config the model rejects.
- Applying a change reloads ~6 MB of weights, so edits are deliberately batched behind one button rather than restarting the spotter per tap.
- `WakeWordTuningTest` pins the suffix format, the clamp ranges, the preset lookup and the step grid.

**Cost, and why it stays in debug.** The spotter needs sherpa-onnx's prebuilt libraries (arm64-v8a only — `libonnxruntime.so` is 22 MB, plus ~10 MB of sherpa JNI) and the ~6 MB model. All of it lives in `src/debug/` (assets, `jniLibs`, engine), so a release APK carries none of it, and `src/release/` provides a no-op twin of `createWakeWordEngine`. `build.gradle.kts` adds `noCompress += "onnx"` so the weights load straight from the APK, and `packaging { jniLibs { useLegacyPackaging = true } }` because the libraries load with `System.loadLibrary` and are extracted rather than mmapped.

`KeywordTokenizerTest` and `GigaSpeechTokenizerTest` pin the tokenizer to upstream's reference output, so a bad model copy or a tokenizer regression fails the tests instead of failing silently on the device.

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

**In-game voice (swappable).** During gameplay only, voice goes through the `InGameVoiceEngine` interface. It recognizes just the active game profile's commands plus back/pause/menu, resume, select and recenter.
- The current implementation, `SpeechRecognizerInGameVoiceEngine`, is a working placeholder: the same Android `SpeechRecognizer` plumbing, scoped to those commands.
- To use a dedicated low-latency engine, change the one binding in `di/AppContainer.kt` (it's commented). `GameplayViewModel` and everything above it depend only on the interface.
- `MicArbiter` guarantees one listener at a time: while a game (or the Testing Station's wake word engine) holds the mic, the app-wide `VoiceCommandManager` stands down.
- `BaseInGameVoiceEngine` carries the contract every engine inherits: scoped matching, the user's match and activation modes, and the typed fallback when the mic is unavailable.

**Wake word (debug tooling).** `WakeWordEngine` is the interface; the debug source set implements it with sherpa-onnx keyword spotting, the release source set with a no-op. Only the Testing Station's **Wake word** panel reaches it.
- `start()` takes the microphone through `MicArbiter` so the app-wide recognizer stands down instead of fighting it for the mic; `stop()` gives the mic back and frees the native engine.
- A missing mic permission, or a native library that won't load on the device, surfaces as `WakeWordState.availability`, never as a crash.

**GabAI** is a scripted, resumable state machine, not a chatbot. `GabAiState` is a sealed hierarchy (Welcome, then the calibration branch, then the game-profile branch), and `GabAiFlow` holds the pure transitions.
- Every step is saved to Room (`gabai_sessions`) with the form data entered so far. Backing out, or force-closing the app, resumes on the same step from **GabAI → Continue Existing**; the Dashboard card shows where you stopped.
- The calibration steps reuse the Prompt 2 camera, pointer and joystick UI and apply live. Saving creates a `CalibrationProfile`.
- For a game profile, you pick a game and a calibration, choose a screenshot, and mark its buttons: tap, drag, or say "place" to drop a button at your head pointer. You name each button by typing or by voice, then choose how to press it (voice phrase, head gesture or joystick direction). The result is saved as a `GameProfile`, editable later from the Profile or game screen.

**Testing Station** is a development tool, so only debug builds have it. Its code lives in `src/debug/`, and `src/release/` provides a no-op twin. A release build has neither the Dashboard card nor the route, and no Testing Station classes are in the APK. Its **Wake word** panel runs the sherpa-onnx keyword spotter: type any phrase, start it, and watch detections land.

## Real vs. placeholder in this build

| Area | Status |
|---|---|
| Splash, Welcome, Sign in / Create account / Reset password | Real (sign-in needs Firebase config) |
| Setup: needs, appearance (live preview), input mode | Real, saved to DataStore on Continue. The "Try it now" pointer is live head tracking |
| Voice tutorial, including TTS read-aloud and speed | Real (Android TextToSpeech), voice-controllable |
| Input mode (Controls) | Real; switches the tracking output (pointer / joystick) live |
| Gestures + per-gesture sensitivity | Real: 25 gestures plus all 52 MediaPipe blendshapes, saved to Room, live "try it" meter |
| Cursor speed, joystick | Real: live camera, live pointer/joystick; settings saved to Room |
| Voice configuration | Real: live mic level, on/off, matching and activation modes, command list |
| Testing Station (debug builds only) | Real: live face, gesture, voice, cursor and joystick readouts, plus a sherpa-onnx wake word panel for any phrase |
| Watch Tutorial | Real player with a placeholder video (`res/raw/tutorial_placeholder.mp4`) |
| GabAI | Real: calibration and game-profile flows, resumable after a force-close |
| Games, game detail | Real: play, edit or create profiles per game; voice-selectable |
| Playing view | Live overlay over your game screenshot (or a simulated arena). Mapped buttons are pressed by voice (in-game engine), gesture or joystick |
| Profile | Real: profile lists with rename/delete, "Use now" for calibrations, Play/Edit for game profiles, sync status |

## Known limitations

- **No real game control.** PWDe doesn't launch or press buttons in Clash Royale or Mobile Legends. There's no accessibility service, by design. The playing view shows which mapped button would be pressed, over your screenshot.
- **Buttons are placed by hand.** GabAI's button mapping is manual (tap, drag, or "place" at the head pointer). There's no ML button detection in this build.
- **In-game voice is the baseline recognizer.** Until a dedicated engine is chosen, gameplay uses Android `SpeechRecognizer`. Latency and restart beeps depend on the phone's speech service.
- **Cloud sync is a stub.** Signing in never blocks or deletes anything, but profiles only live on this phone for now.
- **Direction and side checks.** Head-pose signs and MediaPipe's left/right blendshape sides were checked in theory and by unit tests. If a direction reads backwards on a device, there's one switch in each: `HeadPose.kt` and `Blendshapes.SIDES_SWAPPED`.
- **Tracking speed.** Face tracking runs on the CPU at roughly 15–30 fps depending on the phone.
- **Release signing.** No release signing config is set up. `assembleRelease` produces an unsigned APK to sign with your own key.

Gesture actions like Notifications, All apps and Touch & hold act inside PWDe's overlay only. PWDe has no accessibility service or system-level control.
