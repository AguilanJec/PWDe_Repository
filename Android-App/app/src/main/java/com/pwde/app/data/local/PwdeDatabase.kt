package com.pwde.app.data.local

import android.content.Context
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CalibrationProfile::class, GameProfile::class, ControlSettingsEntity::class],
    version = 2,
    exportSchema = true,
    autoMigrations = [
        // v2 (Prompt 2): cursor/joystick tuning and per-gesture sensitivity.
        AutoMigration(from = 1, to = 2),
    ],
)
abstract class PwdeDatabase : RoomDatabase() {
    abstract fun calibrationProfileDao(): CalibrationProfileDao
    abstract fun gameProfileDao(): GameProfileDao
    abstract fun controlSettingsDao(): ControlSettingsDao

    companion object {
        fun create(context: Context): PwdeDatabase =
            Room.databaseBuilder(context, PwdeDatabase::class.java, "pwde.db").build()
    }
}
