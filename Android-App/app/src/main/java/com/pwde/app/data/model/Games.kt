package com.pwde.app.data.model

/** Supported games. [packageName] is the real game's Android app, which PWDe launches and controls. */
enum class Game(val id: String, val displayName: String, val genre: String, val description: String, val packageName: String, val landscape: Boolean) {
    CLASH_ROYALE(
        id = "clash_royale",
        packageName = "com.supercell.clashroyale",
        landscape = false,
        displayName = "Clash Royale",
        genre = "Strategy",
        description = "Real-time card battles. Drag cards onto the arena and defend your towers.",
    ),
    MOBILE_LEGENDS(
        id = "mobile_legends",
        packageName = "com.mobile.legends",
        landscape = true,
        displayName = "Mobile Legends",
        genre = "MOBA",
        description = "5v5 hero battles. Move with a joystick and fire skills from buttons.",
    );

    companion object {
        fun byId(id: String?): Game? = entries.firstOrNull { it.id == id }
    }
}
