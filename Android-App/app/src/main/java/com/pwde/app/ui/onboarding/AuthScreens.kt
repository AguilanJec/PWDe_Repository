package com.pwde.app.ui.onboarding

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.MarkEmailRead
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pwde.app.data.remote.GuestOnlyAuthRepository
import com.pwde.app.ui.components.ButtonStyle
import com.pwde.app.ui.components.InfoNote
import com.pwde.app.ui.components.PwdeButton
import com.pwde.app.ui.components.PwdeScreen
import com.pwde.app.ui.components.PwdeTextField

/** Shown whenever the build has no Firebase config: sign-in is unavailable, guest keeps working. */
@Composable
private fun CloudUnavailableNote() {
    InfoNote(GuestOnlyAuthRepository.NOT_CONFIGURED_MESSAGE, icon = Icons.Outlined.CloudOff)
}

@Composable
private fun ErrorNote(message: String?) {
    if (message != null) {
        InfoNote(message, icon = Icons.Outlined.ErrorOutline, modifier = Modifier.semantics { liveRegion = LiveRegionMode.Assertive })
    }
}

/** A3 Sign in. "Use as guest" is a full-size button, not a small link. */
@Composable
fun SignInScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onSignedIn: () -> Unit,
    onForgotPassword: () -> Unit,
    onCreateAccount: () -> Unit,
    onUseAsGuest: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onSignedIn() }

    PwdeScreen(
        title = "Sign in",
        subtitle = "Optional — signing in only adds cloud sync for your profiles.",
        onBack = onBack,
        voiceHint = "Say \"sign in\" or a field name",
        footer = {
            PwdeButton(
                if (state.busy) "Signing in…" else "Sign in",
                viewModel::signIn,
                enabled = state.cloudAvailable && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (!state.cloudAvailable) CloudUnavailableNote()
        PwdeTextField("Email", state.email, viewModel::onEmailChange, keyboardType = KeyboardType.Email, enabled = state.cloudAvailable)
        PwdeTextField("Password", state.password, viewModel::onPasswordChange, isPassword = true, enabled = state.cloudAvailable)
        ErrorNote(state.error)
        PwdeButton("Forgot password?", onForgotPassword, style = ButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
        PwdeButton("Create an account", onCreateAccount, style = ButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
        PwdeButton("Use as guest", onUseAsGuest, style = ButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
    }
}

/** A4 Reset password. */
@Composable
fun ForgotPasswordScreen(viewModel: AuthViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    PwdeScreen(
        title = "Reset password",
        subtitle = "We'll email you a link to set a new password.",
        onBack = onBack,
        voiceHint = "Say \"send link\"",
        footer = {
            PwdeButton(
                if (state.busy) "Sending…" else "Send reset link",
                viewModel::sendReset,
                icon = Icons.Outlined.MailOutline,
                enabled = state.cloudAvailable && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (!state.cloudAvailable) CloudUnavailableNote()
        PwdeTextField(
            "Email",
            state.email,
            viewModel::onEmailChange,
            helper = "Use the email you signed up with.",
            keyboardType = KeyboardType.Email,
            enabled = state.cloudAvailable,
        )
        InfoNote("No rush — the link stays valid for a while, so take your time.")
        ErrorNote(state.error)
        state.info?.let { InfoNote(it, icon = Icons.Outlined.MarkEmailRead) }
    }
}

/** A5 Create account. Requirements are shown up front, before any error. */
@Composable
fun CreateAccountScreen(
    viewModel: AuthViewModel,
    onBack: () -> Unit,
    onCreated: () -> Unit,
    onHaveAccount: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(state.completed) { if (state.completed) onCreated() }

    PwdeScreen(
        title = "Create your account",
        subtitle = "Three fields. Your guest settings carry over.",
        onBack = onBack,
        voiceHint = "Say \"next\" when you're done",
        footer = {
            PwdeButton(
                if (state.busy) "Creating…" else "Create account",
                viewModel::createAccount,
                enabled = state.cloudAvailable && !state.busy,
                modifier = Modifier.fillMaxWidth(),
            )
        },
    ) {
        if (!state.cloudAvailable) CloudUnavailableNote()
        PwdeTextField("Email", state.email, viewModel::onEmailChange, keyboardType = KeyboardType.Email, enabled = state.cloudAvailable)
        PwdeTextField(
            "Password",
            state.password,
            viewModel::onPasswordChange,
            isPassword = true,
            helper = "At least ${AuthUiState.MIN_PASSWORD} characters.",
            enabled = state.cloudAvailable,
        )
        PwdeTextField(
            "Confirm password",
            state.confirmPassword,
            viewModel::onConfirmChange,
            isPassword = true,
            isError = state.confirmPassword.isNotEmpty() && !state.passwordsMatch,
            helper = if (state.confirmPassword.isNotEmpty() && !state.passwordsMatch) "Passwords don't match yet." else null,
            enabled = state.cloudAvailable,
        )
        ErrorNote(state.error)
        PwdeButton("I already have an account", onHaveAccount, style = ButtonStyle.SECONDARY, modifier = Modifier.fillMaxWidth())
    }
}
