package com.pwde.app.data.model

/** Supported games. Launching the real game is out of scope; PWDe overlays on top of it later. */
enum class Game(val id: String, val displayName: String, val genre: String, val description: String) {
    CLASH_ROYALE(
        id = "clash_royale",
        displayName = "Clash Royale",
        genre = "Strategy",
        description = "Real-time card battles. Drag cards onto the arena and defend your towers.",
    ),
    MOBILE_LEGENDS(
        id = "mobile_legends",
        displayName = "Mobile Legends",
        genre = "MOBA",
        description = "5v5 hero battles. Move with a joystick and fire skills from buttons.",
    );

    companion object {
        fun byId(id: String?): Game? = entries.firstOrNull { it.id == id }
    }
}
