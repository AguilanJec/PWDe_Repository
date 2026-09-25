package com.pwde.app.data.remote

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

sealed interface SyncStatus {
    /** Guest: everything lives on this device. */
    data object LocalOnly : SyncStatus

    /** Signed in, but cloud sync isn't implemented in this build. Local data is untouched. */
    data object NotAvailable : SyncStatus
}

/**
 * Cloud sync of calibration/game profiles, keyed by Firebase UID. Only meaningful when signed in.
 * Real Firestore push/pull is optional future polish; this build ships the no-op below.
 */
interface SyncRepository {
    val status: Flow<SyncStatus>
    suspend fun syncNow(): SyncStatus
}

class NoOpSyncRepository(private val authRepository: AuthRepository) : SyncRepository {
    override val status: Flow<SyncStatus> = authRepository.authState.map { it.toStatus() }

    override suspend fun syncNow(): SyncStatus = authRepository.authState.value.toStatus()

    private fun AuthState.toStatus(): SyncStatus = when (this) {
        AuthState.Guest -> SyncStatus.LocalOnly
        is AuthState.SignedIn -> SyncStatus.NotAvailable
    }
}
