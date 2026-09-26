package com.pwde.app.data.local

import kotlinx.coroutines.flow.Flow

/** Room-backed store for calibration and game profiles. The only path to those tables. */
class ProfileRepository(
    private val calibrationDao: CalibrationProfileDao,
    private val gameDao: GameProfileDao,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    val calibrationProfiles: Flow<List<CalibrationProfile>> = calibrationDao.observeAll()
    val gameProfiles: Flow<List<GameProfile>> = gameDao.observeAll()

    fun gameProfilesFor(gameId: String): Flow<List<GameProfile>> = gameDao.observeForGame(gameId)

    suspend fun getCalibrationProfile(id: Long): CalibrationProfile? = calibrationDao.getById(id)

    suspend fun getGameProfile(id: Long): GameProfile? = gameDao.getById(id)

    /** The profile to use when the user just says "play <game>": last played, else newest. */
    suspend fun lastPlayedGameProfile(gameId: String): GameProfile? = gameDao.lastPlayedFor(gameId)

    /** Doesn't touch updatedAt, so playing never reorders "newest first" lists. */
    suspend fun markGameProfilePlayed(id: Long) = gameDao.markPlayed(id, clock())

    suspend fun saveCalibrationProfile(profile: CalibrationProfile): Long {
        val now = clock()
        return if (profile.id == 0L) {
            calibrationDao.insert(profile.copy(createdAt = now, updatedAt = now))
        } else {
            calibrationDao.update(profile.copy(updatedAt = now))
            profile.id
        }
    }

    suspend fun saveGameProfile(profile: GameProfile): Long {
        val now = clock()
        return if (profile.id == 0L) {
            gameDao.insert(profile.copy(createdAt = now, updatedAt = now))
        } else {
            gameDao.update(profile.copy(updatedAt = now))
            profile.id
        }
    }

    suspend fun deleteCalibrationProfile(profile: CalibrationProfile) = calibrationDao.delete(profile)

    suspend fun deleteGameProfile(profile: GameProfile) = gameDao.delete(profile)
}
