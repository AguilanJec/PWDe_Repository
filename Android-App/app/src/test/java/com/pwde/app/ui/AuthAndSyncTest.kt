package com.pwde.app.ui

import com.pwde.app.data.remote.AuthResult
import com.pwde.app.data.remote.AuthState
import com.pwde.app.data.remote.GuestOnlyAuthRepository
import com.pwde.app.data.remote.NoOpSyncRepository
import com.pwde.app.data.remote.SyncStatus
import com.pwde.app.ui.onboarding.AuthViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class AuthAndSyncTest {
    @get:Rule
    val mainRule = MainDispatcherRule()

    @Test
    fun guestOnlyRepository_isGuest_andFailsGracefully() = runTest {
        val repo = GuestOnlyAuthRepository()
        assertFalse(repo.isCloudAvailable)
        assertEquals(AuthState.Guest, repo.authState.value)
        assertTrue(repo.signIn("a@b.co", "password") is AuthResult.Error)
    }

    @Test
    fun createAccount_validatesBeforeCallingTheBackend() {
        val vm = AuthViewModel(GuestOnlyAuthRepository())
        vm.onEmailChange("not-an-email")
        vm.createAccount()
        assertEquals("Please enter a valid email address.", vm.state.value.error)

        vm.onEmailChange("juan@example.com")
        vm.onPasswordChange("short")
        vm.createAccount()
        assertTrue(vm.state.value.error!!.contains("at least"))

        vm.onPasswordChange("longenough")
        vm.onConfirmChange("different1")
        vm.createAccount()
        assertEquals("The two passwords don't match.", vm.state.value.error)

        vm.onConfirmChange("longenough")
        vm.createAccount()
        assertEquals(GuestOnlyAuthRepository.NOT_CONFIGURED_MESSAGE, vm.state.value.error)
        assertFalse(vm.state.value.completed)
    }

    @Test
    fun syncStub_reportsLocalOnlyForGuests() = runTest {
        val sync = NoOpSyncRepository(GuestOnlyAuthRepository())
        assertEquals(SyncStatus.LocalOnly, sync.status.first())
        assertEquals(SyncStatus.LocalOnly, sync.syncNow())
    }
}
