package com.pwde.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [CalibrationProfile::class, GameProfile::class, ControlSettingsEntity::class],
    version = 1,
    exportSchema = true,
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
