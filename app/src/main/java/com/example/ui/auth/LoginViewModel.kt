package com.example.ui.auth

import android.app.Activity
import android.content.Context
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.R
import com.example.core.BackendGatewayClient
import com.example.core.BackendGatewayResult
import com.example.core.CountryDialCode
import com.example.core.PhoneNumberHelper
import com.example.data.model.OtpChannel
import com.example.data.repository.AuthRepository
import com.example.data.repository.FirebaseSmsOtpProvider
import com.example.data.repository.OtpSendResult
import com.example.data.repository.PhoneAuthErrorMapper
import com.example.data.repository.WhatsAppOtpProvider
import com.google.firebase.Firebase
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.auth
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PhoneOtpStep {
    ENTER_PHONE,
    VERIFY_CODE
}

data class LoginUiState(
    val isLoading: Boolean = false,
    val isCheckingAuto: Boolean = true,
    val errorMessage: String? = null,
    val infoMessage: String? = null,
    val isEmailPasswordEnabled: Boolean = false,
    val authStatus: LoginAuthStatus = LoginAuthStatus.Idle,
    val selectedCountry: CountryDialCode = PhoneNumberHelper.defaultCountry, // Sri Lanka (+94)
    val phoneInput: String = "",
    val e164Preview: String? = null,
    val otpCode: String = "",
    val verificationId: String? = null,
    val otpStep: PhoneOtpStep = PhoneOtpStep.ENTER_PHONE,
    val resendSecondsRemaining: Int = 0,
    val selectedOtpChannel: OtpChannel = OtpChannel.FIREBASE_SMS,
    val whatsappOtpAvailable: Boolean = false,
    val whatsappOptIn: Boolean = false
)

sealed interface LoginAuthStatus {
    data object Idle : LoginAuthStatus
    data object Authenticating : LoginAuthStatus
    data object Authenticated : LoginAuthStatus
    data class Error(val message: String) : LoginAuthStatus
}

class LoginViewModel(
    private val authRepository: AuthRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private var autoCheckStarted = false
    private var resendToken: PhoneAuthProvider.ForceResendingToken? = null
    private var countdownJob: Job? = null

    private val smsOtpProvider by lazy { FirebaseSmsOtpProvider(Firebase.auth) }
    private val whatsappOtpProvider by lazy { WhatsAppOtpProvider(smsOtpProvider) }

    init {
        viewModelScope.launch {
            if (BackendGatewayClient.isConfigured()) {
                val res = BackendGatewayClient.getWhatsAppHealth()
                if (res is BackendGatewayResult.Success &&
                    res.data.configured &&
                    res.data.otpProviderEnabled &&
                    res.data.otpTemplateApproved
                ) {
                    _uiState.update { it.copy(whatsappOtpAvailable = true) }
                }
            }
        }
    }

    fun selectCountry(country: CountryDialCode) {
        _uiState.update { cur ->
            val preview = PhoneNumberHelper.normalizeToE164(country, cur.phoneInput)
            cur.copy(selectedCountry = country, e164Preview = preview, errorMessage = null)
        }
    }

    fun onPhoneInputChanged(raw: String) {
        val trimmed = raw.take(24)
        _uiState.update { cur ->
            val preview = PhoneNumberHelper.normalizeToE164(cur.selectedCountry, trimmed)
            cur.copy(phoneInput = trimmed, e164Preview = preview, errorMessage = null)
        }
    }

    fun onOtpCodeChanged(raw: String) {
        val digits = raw.filter { it.isDigit() }.take(6)
        _uiState.update { it.copy(otpCode = digits, errorMessage = null) }
    }

    fun onSelectOtpChannel(channel: OtpChannel) {
        _uiState.update { it.copy(selectedOtpChannel = channel, errorMessage = null) }
    }

    fun onWhatsAppOptInChanged(enabled: Boolean) {
        _uiState.update { it.copy(whatsappOptIn = enabled) }
    }

    fun editPhoneNumber() {
        countdownJob?.cancel()
        _uiState.update {
            it.copy(
                otpStep = PhoneOtpStep.ENTER_PHONE,
                otpCode = "",
                verificationId = null,
                resendSecondsRemaining = 0,
                errorMessage = null,
                infoMessage = null
            )
        }
    }

    fun sendPhoneOtp(
        activity: Activity?,
        onAuthenticated: () -> Unit,
        isResend: Boolean = false
    ) {
        val state = _uiState.value
        val e164 = PhoneNumberHelper.normalizeToE164(state.selectedCountry, state.phoneInput)
        if (e164 == null) {
            setError("Enter a valid phone number for ${state.selectedCountry.name} (${state.selectedCountry.dialCode}).")
            return
        }
        if (activity == null) {
            setError("Unable to open phone verification right now.")
            return
        }

        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                infoMessage = null,
                e164Preview = e164,
                authStatus = LoginAuthStatus.Authenticating
            )
        }
        viewModelScope.launch {
            val provider = if (state.selectedOtpChannel == OtpChannel.WHATSAPP_TEMPLATE && state.whatsappOtpAvailable) {
                whatsappOtpProvider
            } else {
                smsOtpProvider
            }
            when (val res = provider.requestOtp(activity, e164, if (isResend) resendToken else null)) {
                is OtpSendResult.CodeSent -> {
                    resendToken = res.resendingToken
                    startResendCountdown()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            verificationId = res.verificationId,
                            otpStep = PhoneOtpStep.VERIFY_CODE,
                            otpCode = "",
                            infoMessage = "Verification code sent to ${PhoneNumberHelper.formatReadable(e164)}."
                        )
                    }
                }
                is OtpSendResult.InstantVerified -> {
                    val signRes = authRepository.signInWithPhoneCredential(
                        context = activity,
                        credential = res.credential,
                        country = state.selectedCountry,
                        otpChannel = res.channel,
                        whatsappOptIn = state.whatsappOptIn
                    )
                    signRes.onSuccess {
                        countdownJob?.cancel()
                        _uiState.update {
                            it.copy(
                                isLoading = false,
                                errorMessage = null,
                                authStatus = LoginAuthStatus.Authenticated
                            )
                        }
                        onAuthenticated()
                    }.onFailure { err ->
                        setError(err.localizedMessage ?: "Couldn't complete instant verification.")
                    }
                }
                is OtpSendResult.Error -> {
                    if (res.fallbackToSmsSuggested && state.selectedOtpChannel != OtpChannel.FIREBASE_SMS) {
                        _uiState.update {
                            it.copy(
                                selectedOtpChannel = OtpChannel.FIREBASE_SMS,
                                isLoading = false,
                                infoMessage = res.message
                            )
                        }
                    } else {
                        setError(res.message)
                    }
                }
            }
        }
    }

    fun verifyOtpCode(
        context: Context,
        onAuthenticated: () -> Unit
    ) {
        val state = _uiState.value
        val vId = state.verificationId
        val code = state.otpCode.trim()
        if (vId.isNullOrBlank()) {
            setError("Request a verification code first.")
            return
        }
        if (code.length < 6) {
            setError("Enter the full 6-digit verification code.")
            return
        }
        _uiState.update { it.copy(isLoading = true, errorMessage = null, authStatus = LoginAuthStatus.Authenticating) }
        viewModelScope.launch {
            try {
                val credential = PhoneAuthProvider.getCredential(vId, code)
                val result = authRepository.signInWithPhoneCredential(
                    context = context,
                    credential = credential,
                    country = state.selectedCountry,
                    otpChannel = state.selectedOtpChannel,
                    whatsappOptIn = state.whatsappOptIn
                )
                result.onSuccess {
                    countdownJob?.cancel()
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            errorMessage = null,
                            authStatus = LoginAuthStatus.Authenticated
                        )
                    }
                    onAuthenticated()
                }.onFailure { err ->
                    setError(PhoneAuthErrorMapper.map(context, err))
                }
            } catch (e: Exception) {
                setError(PhoneAuthErrorMapper.map(context, e))
            }
        }
    }

    private fun startResendCountdown() {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            for (sec in 60 downTo 0) {
                _uiState.update { it.copy(resendSecondsRemaining = sec) }
                if (sec > 0) delay(1000L)
            }
        }
    }

    fun checkAutoSignIn(context: Context, onAuthenticated: () -> Unit) {
        if (autoCheckStarted) return
        autoCheckStarted = true
        viewModelScope.launch {
            val signedIn = authRepository.attemptAutoSignIn(context)
            if (signedIn) {
                _uiState.update {
                    it.copy(
                        isCheckingAuto = false,
                        authStatus = LoginAuthStatus.Authenticated
                    )
                }
                onAuthenticated()
            } else {
                _uiState.update {
                    it.copy(
                        isCheckingAuto = false,
                        authStatus = LoginAuthStatus.Idle
                    )
                }
            }
        }
    }

    fun signInWithGoogle(
        activity: Activity?,
        onAuthenticated: () -> Unit,
        localizedContext: Context? = activity
    ) {
        val strCtx = localizedContext ?: activity
        if (activity == null) {
            setError(strCtx?.getString(R.string.auth_activity_unavailable) ?: "Unable to open the sign-in window right now.")
            return
        }
        _uiState.update {
            it.copy(
                isLoading = true,
                errorMessage = null,
                authStatus = LoginAuthStatus.Authenticating
            )
        }
        viewModelScope.launch {
            val result = authRepository.signInInteractive(activity, registration = null, strContext = strCtx)
            result.onSuccess {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = null,
                        authStatus = LoginAuthStatus.Authenticated
                    )
                }
                onAuthenticated()
            }.onFailure { err ->
                val msg = if (err is GetCredentialCancellationException) {
                    strCtx?.getString(R.string.auth_signin_cancelled)
                        ?: "Sign-in was canceled. Tap below whenever you're ready."
                } else {
                    err.localizedMessage
                        ?: strCtx?.getString(R.string.auth_signin_failed)
                        ?: "We couldn't sign you in. Check your connection and try again."
                }
                setError(msg)
            }
        }
    }

    fun clearError() {
        _uiState.update {
            it.copy(
                errorMessage = null,
                authStatus = LoginAuthStatus.Idle
            )
        }
    }

    private fun setError(message: String) {
        _uiState.update {
            it.copy(
                isLoading = false,
                errorMessage = message,
                authStatus = LoginAuthStatus.Error(message)
            )
        }
    }
}
