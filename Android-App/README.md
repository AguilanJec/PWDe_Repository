# PWDe (SAWADIKAP) — Android

An accessibility-first gaming companion for people with disabilities: hands-free control through head/face tracking, voice, and a guided setup assistant (GabAI).

This is the build from **Prompt 1 of 3 (architecture foundation)**. Prompt 2 adds camera, ML and mic. Prompt 3 adds GabAI and production hardening.

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
│   └── model/             gesture catalog, voice options, games
└── ui/
    ├── theme/             PwdeTheme + ThemeViewModel (drives the whole app from settings)
    ├── components/        design-system components (buttons, cards, steppers, voice bar…)
    ├── navigation/        Routes, PwdeNavHost, ScreenReaderViewModel
    └── <feature>/         one screen + ViewModel per feature
```

- Composables never touch Room, DataStore, Firebase, TTS or ExoPlayer directly. They observe `StateFlow` from a ViewModel, and the ViewModel talks to a repository or manager.
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

## Real vs. placeholder in this build

| Area | Status |
|---|---|
| Splash, Welcome, Sign in / Create account / Reset password | Real (sign-in needs Firebase config) |
| Setup: needs, appearance (live preview), input mode | Real, saved to DataStore on Continue |
| Voice tutorial, including TTS read-aloud and speed | Real (Android TextToSpeech) |
| Gesture picks per action, voice config | Saved to Room; nothing reacts to them yet (Prompt 2) |
| Input mode (Controls) | Real |
| Cursor speed, joystick | Layout only, inert steppers (Prompt 2) |
| Testing Station | Panels say "No data — sensors not yet connected" (Prompt 2) |
| Watch Tutorial | Real player with a placeholder video (`res/raw/tutorial_placeholder.mp4`) |
| Games, game detail, playing view | Browsable; the playing view is a simulated overlay preview |
| Profile | Real: guest/signed-in state, Room profile lists, sync status |
| GabAI | Welcome screen only; each choice shows "Coming in Prompt 3" |
| Leaderboard | "Planned" notice; no invented scores |
| Voice bar on every screen | Shows its honest Off state; recognition arrives in Prompt 2 |

No camera, ML or microphone code or permissions are included yet.
