package com.pwde.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CalibrationProfile::class, GameProfile::class, ControlSettingsEntity::class, GabAiSessionEntity::class],
    version = 3,
    exportSchema = true,
    autoMigrations = [
        // v2 (Prompt 2): cursor/joystick tuning and per-gesture sensitivity.
        AutoMigration(from = 1, to = 2),
        // v3 (Prompt 3): GabAI sessions, joystick center on calibration profiles.
        AutoMigration(from = 2, to = 3),
    ],
)
abstract class PwdeDatabase : RoomDatabase() {
    abstract fun calibrationProfileDao(): CalibrationProfileDao
    abstract fun gameProfileDao(): GameProfileDao
    abstract fun controlSettingsDao(): ControlSettingsDao
    abstract fun gabAiSessionDao(): GabAiSessionDao

    companion object {
        fun create(context: Context): PwdeDatabase =
            Room.databaseBuilder(context, PwdeDatabase::class.java, "pwde.db").build()
    }
}
