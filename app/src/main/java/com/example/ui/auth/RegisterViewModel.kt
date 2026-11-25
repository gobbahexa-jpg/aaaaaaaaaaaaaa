package com.example.ui.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.data.repository.AuthRepository
import com.example.data.repository.RegistrationInput
import com.example.ui.theme.AccentColorOptions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class RegisterUiState(
    val displayName: String = "",
    val username: String = "",
    val bio: String = "Connected on DL Chat",
    val avatarColorHex: String = AccentColorOptions.first().second,
    val discoverable: Boolean = true,
    val isLoading: Boolean = false,
    val validationError: String? = null,
    val authError: String? = null,
    val isEmailPasswordEnabled: Boolean = false,
    val registrationComplete: Boolean = false
) {
    fun toRegistrationInput(): RegistrationInput = RegistrationInput(
        username = username,
        displayName = displayName,
        bio = bio,
        avatarColor = avatarColorHex,
        discoverable = discoverable
    )

    val previewHandle: String
        get() = toRegistrationInput().sanitizedUsername().ifBlank { "handle" }

    val previewDisplayName: String
        get() = displayName.trim().ifBlank { "New Operator" }

    val combinedError: String?
        get() = validationError ?: authError
}

class RegisterViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(RegisterUiState())
    val uiState: StateFlow<RegisterUiState> = _uiState.asStateFlow()

    fun onDisplayNameChanged(value: String) {
        _uiState.update {
            it.copy(
                displayName = value.take(60),
                validationError = null,
                authError = null
            )
        }
    }

    fun onUsernameChanged(value: String) {
        _uiState.update {
            it.copy(
                username = value.take(30),
                validationError = null,
                authError = null
            )
        }
    }

    fun onBioChanged(value: String) {
        _uiState.update {
            it.copy(
                bio = value.take(240),
                validationError = null,
                authError = null
            )
        }
    }

    fun onAvatarColorSelected(colorHex: String) {
        _uiState.update { it.copy(avatarColorHex = colorHex) }
    }

    fun onDiscoverableChanged(discoverable: Boolean) {
        _uiState.update { it.copy(discoverable = discoverable) }
    }

    fun clearErrors() {
        _uiState.update { it.copy(validationError = null, authError = null) }
    }

    fun registerWithGoogle(
        activity: Activity?,
        onRegistered: () -> Unit,
        localizedContext: Context? = activity
    ) {
        val strCtx = localizedContext ?: activity
        val current = _uiState.value
        val input = current.toRegistrationInput()
        val validationErr = AuthRepository.validateRegistrationInput(input, strCtx)
        if (validationErr != null) {
            _uiState.update { it.copy(validationError = validationErr, authError = null) }
            return
        }
        if (activity == null) {
            _uiState.update {
                it.copy(
                    authError = strCtx?.getString(R.string.auth_activity_unavailable)
                        ?: "Unable to open the sign-in window right now."
                )
            }
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                validationError = null,
                authError = null
            )
        }
        viewModelScope.launch {
            val result = authRepository.signInInteractive(activity, registration = input, strContext = strCtx)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authError = null,
                        registrationComplete = true
                    )
                }
                onRegistered()
            }.onFailure { err ->
                val msg = if (err is GetCredentialCancellationException) {
                    strCtx?.getString(R.string.auth_register_cancelled)
                        ?: "Setup was canceled. Choose your account when you're ready."
                } else {
                    err.localizedMessage
                        ?: strCtx?.getString(R.string.auth_register_failed)
                        ?: "We couldn't create your account right now. Try again."
                }
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        authError = msg
                    )
                }
            }
        }
    }
}
