package com.pwde.app.ui.navigation

/** Every destination in the app. Order mirrors the Figma flow (A Account → H Profile). */
object Routes {
    // A · Account
    const val SPLASH = "splash"
    const val WELCOME = "welcome"
    const val SIGN_IN = "sign_in"
    const val FORGOT_PASSWORD = "forgot_password"
    const val CREATE_ACCOUNT = "create_account"

    // B · Setup + voice tutorial
    const val SETUP = "setup?appearanceOnly={appearanceOnly}"
    fun setup(appearanceOnly: Boolean = false) = "setup?appearanceOnly=$appearanceOnly"
    const val VOICE_TUTORIAL = "voice_tutorial"

    // D · Play
    const val DASHBOARD = "dashboard"
    const val GAMES = "games"
    const val GAME_DETAIL = "games/{gameId}"
    fun gameDetail(gameId: String) = "games/$gameId"
    const val PLAYING = "playing/{gameId}"
    fun playing(gameId: String) = "playing/$gameId"
    const val FILTER = "filter"

    // E · Controls
    const val CONTROLS = "controls"
    const val CONTROLS_INPUT = "controls/input"
    const val CONTROLS_GESTURES = "controls/gestures"
    const val CHOOSE_GESTURE = "controls/gestures/{action}"
    fun chooseGesture(action: String) = "controls/gestures/$action"
    const val CONTROLS_CURSOR = "controls/cursor"
    const val CONTROLS_JOYSTICK = "controls/joystick"
    const val CUSTOM_BUTTONS = "controls/custom_buttons"
    const val VOICE_CONFIG = "voice_config"

    // F · Testing, tutorial
    const val TESTING_STATION = "testing_station"
    const val WATCH_TUTORIAL = "watch_tutorial"

    // G · GabAI
    const val GABAI = "gabai"
    const val GABAI_COMING = "gabai/{choice}"
    fun gabaiComing(choice: String) = "gabai/$choice"

    // H · Profile
    const val PROFILE = "profile"

    /** Spoken screen names for the read-aloud option. */
    fun spokenTitle(route: String?): String? = when (route) {
        SPLASH -> null
        WELCOME -> "Welcome to PWDe"
        SIGN_IN -> "Sign in"
        FORGOT_PASSWORD -> "Reset password"
        CREATE_ACCOUNT -> "Create your account"
        SETUP -> "Setup"
        VOICE_TUTORIAL -> "Voice tutorial"
        DASHBOARD -> "Play"
        GAMES -> "Games"
        GAME_DETAIL -> "Game details"
        PLAYING -> "Playing"
        FILTER -> "Filter games"
        CONTROLS -> "Controls"
        CONTROLS_INPUT -> "Input mode"
        CONTROLS_GESTURES -> "Gestures"
        CHOOSE_GESTURE -> "Choose a gesture"
        CONTROLS_CURSOR -> "Cursor speed"
        CONTROLS_JOYSTICK -> "Joystick"
        CUSTOM_BUTTONS -> "Custom buttons"
        VOICE_CONFIG -> "Voice"
        TESTING_STATION -> "Testing station"
        WATCH_TUTORIAL -> "Tutorial video"
        GABAI -> "GabAI setup"
        GABAI_COMING -> "GabAI"
        PROFILE -> "Profile"
        else -> null
    }
}
