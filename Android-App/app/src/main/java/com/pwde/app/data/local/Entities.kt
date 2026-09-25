package com.pwde.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved set of control tunings. Created by GabAI (Prompt 3). */
@Entity(tableName = "calibration_profiles")
data class CalibrationProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val inputMode: String,
    val cursorSpeedUp: Int = 5,
    val cursorSpeedDown: Int = 5,
    val cursorSpeedLeft: Int = 5,
    val cursorSpeedRight: Int = 5,
    val joystickSensitivity: Int = 5,
    val joystickDeadZone: Int = 3,
    val joystickRadius: Int = 5,
    /** JSON map of GestureAction name -> FacialGesture name. */
    val gestureAssignmentsJson: String = "{}",
    val voiceEnabled: Boolean = true,
    val voiceMatchMode: String,
    val voiceActivationMode: String,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteId: String? = null,
    val lastSyncedAt: Long? = null,
)

/** Per-game button mappings, linked to the calibration profile they were made with. */
@Entity(
    tableName = "game_profiles",
    foreignKeys = [
        ForeignKey(
            entity = CalibrationProfile::class,
            parentColumns = ["id"],
            childColumns = ["calibrationProfileId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("calibrationProfileId"), Index("gameId")],
)
data class GameProfile(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val gameId: String,
    val gameName: String,
    val profileName: String,
    val calibrationProfileId: Long?,
    /** JSON list of on-screen button mappings. */
    val buttonMappingsJson: String = "[]",
    val thumbnailPath: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
    val remoteId: String? = null,
    val lastSyncedAt: Long? = null,
)

/**
 * The single working copy of the user's controls (gesture picks, voice options). GabAI
 * snapshots this into a [CalibrationProfile] in Prompt 3. Always row [SINGLETON_ID].
 */
@Entity(tableName = "control_settings")
data class ControlSettingsEntity(
    @PrimaryKey val id: Int = SINGLETON_ID,
    val gestureAssignmentsJson: String,
    val voiceEnabled: Boolean,
    val voiceMatchMode: String,
    val voiceActivationMode: String,
    val voiceShortcutsJson: String,
    val updatedAt: Long,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}
