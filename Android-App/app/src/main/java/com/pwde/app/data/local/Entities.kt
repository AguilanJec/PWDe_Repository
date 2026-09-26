package com.pwde.app.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** A saved set of control tunings. Created by GabAI from the working controls ([ControlSettingsEntity]). */
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
    @ColumnInfo(defaultValue = "7") val cursorSmoothing: Int = 7,
    @ColumnInfo(defaultValue = "0") val joystickCenterPitch: Float = 0f,
    @ColumnInfo(defaultValue = "0") val joystickCenterRoll: Float = 0f,
    /** JSON map of GestureAction name -> FacialGesture name. */
    val gestureAssignmentsJson: String = "{}",
    /** JSON map of FacialGesture name -> sensitivity level 1–10. */
    @ColumnInfo(defaultValue = "{}") val gestureSensitivityJson: String = "{}",
    /** JSON list of FacialGesture names the user performed in the gesture test; null = never tested (all on). */
    val enabledGesturesJson: String? = null,
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
    /** When this profile was last used to play the real game; picks the profile for "play <game>". */
    val lastPlayedAt: Long? = null,
)

/**
 * The single working copy of the user's controls (gesture picks and sensitivities, voice options,
 * cursor and joystick tuning). This is the default / in-progress calibration that tracking reads
 * live; GabAI snapshots it into a [CalibrationProfile]. Always row [SINGLETON_ID].
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
    @ColumnInfo(defaultValue = "{}") val gestureSensitivityJson: String = "{}",
    @ColumnInfo(defaultValue = "5") val cursorSpeedUp: Int = 5,
    @ColumnInfo(defaultValue = "5") val cursorSpeedDown: Int = 5,
    @ColumnInfo(defaultValue = "5") val cursorSpeedLeft: Int = 5,
    @ColumnInfo(defaultValue = "5") val cursorSpeedRight: Int = 5,
    @ColumnInfo(defaultValue = "7") val cursorSmoothing: Int = 7,
    @ColumnInfo(defaultValue = "5") val joystickSize: Int = 5,
    @ColumnInfo(defaultValue = "5") val joystickSensitivity: Int = 5,
    @ColumnInfo(defaultValue = "3") val joystickDeadZone: Int = 3,
    @ColumnInfo(defaultValue = "0") val joystickCenterPitch: Float = 0f,
    @ColumnInfo(defaultValue = "0") val joystickCenterRoll: Float = 0f,
    /** As [CalibrationProfile.enabledGesturesJson]. */
    val enabledGesturesJson: String? = null,
    @ColumnInfo(defaultValue = "NULL") val activeCalibrationProfileId: Long? = null,
) {
    companion object {
        const val SINGLETON_ID = 0
    }
}

/**
 * A GabAI conversation in progress, so leaving (or force-closing) resumes exactly where the user
 * was. [stateJson] is the encoded GabAI state; [formJson] holds everything entered so far.
 */
@Entity(tableName = "gabai_sessions")
data class GabAiSessionEntity(
    @PrimaryKey val sessionId: String,
    val stateJson: String,
    val formJson: String,
    val completed: Boolean,
    val updatedAt: Long,
)
