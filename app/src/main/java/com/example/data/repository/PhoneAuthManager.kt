package com.example.data.repository

import android.app.Activity
import android.content.Context
import android.util.Log
import com.example.core.BackendGatewayClient
import com.example.core.BackendGatewayResult
import com.example.core.PhoneNumberHelper
import com.example.data.model.OtpChannel
import com.google.firebase.FirebaseException
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume

sealed interface OtpSendResult {
    data class CodeSent(
        val verificationId: String,
        val resendingToken: PhoneAuthProvider.ForceResendingToken?,
        val channel: OtpChannel
    ) : OtpSendResult

    data class InstantVerified(
        val credential: PhoneAuthCredential,
        val channel: OtpChannel
    ) : OtpSendResult

    data class Error(
        val message: String,
        val fallbackToSmsSuggested: Boolean = false
    ) : OtpSendResult
}

interface OtpAuthProvider {
    val channel: OtpChannel
    suspend fun requestOtp(
        activity: Activity,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken? = null
    ): OtpSendResult
}

class FirebaseSmsOtpProvider(
    private val auth: FirebaseAuth
) : OtpAuthProvider {
    override val channel: OtpChannel = OtpChannel.FIREBASE_SMS

    override suspend fun requestOtp(
        activity: Activity,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?
    ): OtpSendResult {
        if (!PhoneNumberHelper.isValidE164(phoneE164)) {
            return OtpSendResult.Error("Enter a valid phone number in international format (e.g. +94 77 123 4567).")
        }
        return suspendCancellableCoroutine { cont ->
            val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
                override fun onVerificationCompleted(credential: PhoneAuthCredential) {
                    if (cont.isActive) {
                        cont.resume(OtpSendResult.InstantVerified(credential, channel))
                    }
                }

                override fun onVerificationFailed(e: FirebaseException) {
                    Log.w("PhoneAuthManager", "Phone verification failed: ${e.message}", e)
                    if (cont.isActive) {
                        cont.resume(OtpSendResult.Error(PhoneAuthErrorMapper.map(activity, e)))
                    }
                }

                override fun onCodeSent(
                    verificationId: String,
                    token: PhoneAuthProvider.ForceResendingToken
                ) {
                    if (cont.isActive) {
                        cont.resume(OtpSendResult.CodeSent(verificationId, token, channel))
                    }
                }
            }

            val builder = PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(phoneE164)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
            if (resendToken != null) {
                builder.setForceResendingToken(resendToken)
            }
            PhoneAuthProvider.verifyPhoneNumber(builder.build())
        }
    }
}

class WhatsAppOtpProvider(
    private val smsFallback: FirebaseSmsOtpProvider
) : OtpAuthProvider {
    override val channel: OtpChannel = OtpChannel.WHATSAPP_TEMPLATE

    override suspend fun requestOtp(
        activity: Activity,
        phoneE164: String,
        resendToken: PhoneAuthProvider.ForceResendingToken?
    ): OtpSendResult {
        if (!BackendGatewayClient.isConfigured()) {
            return OtpSendResult.Error(
                message = "WhatsApp OTP requires the Cloud Functions backend and an approved authentication template. Use SMS verification instead.",
                fallbackToSmsSuggested = true
            )
        }
        return when (val health = BackendGatewayClient.getWhatsAppHealth()) {
            is BackendGatewayResult.Success -> {
                val snap = health.data
                if (snap.configured && snap.otpProviderEnabled && snap.otpTemplateApproved) {
                    // Without an external custom-token/OTP-minting endpoint wired on Firebase Auth,
                    // SMS verification remains the authoritative Firebase Phone Auth credential source.
                    smsFallback.requestOtp(activity, phoneE164, resendToken)
                } else {
                    OtpSendResult.Error(
                        message = "WhatsApp authentication template is not approved yet. Please verify with SMS.",
                        fallbackToSmsSuggested = true
                    )
                }
            }
            is BackendGatewayResult.NotConfigured -> OtpSendResult.Error(
                message = "WhatsApp OTP backend is not configured yet. Please verify with SMS.",
                fallbackToSmsSuggested = true
            )
            is BackendGatewayResult.Failure -> OtpSendResult.Error(
                message = "WhatsApp OTP is currently unavailable (${health.message}). Please use SMS.",
                fallbackToSmsSuggested = true
            )
        }
    }
}

object PhoneAuthErrorMapper {
    fun map(context: Context?, error: Throwable): String {
        if (error is FirebaseTooManyRequestsException) {
            return "Too many verification attempts. Please wait a few minutes before requesting another code."
        }
        if (error is FirebaseNetworkException) {
            return "You're offline. Check your internet connection and try again."
        }
        if (error is FirebaseAuthException) {
            return when (error.errorCode) {
                "ERROR_INVALID_PHONE_NUMBER" ->
                    "That phone number isn't valid. Check your country code and digits."
                "ERROR_INVALID_VERIFICATION_CODE" ->
                    "That 6-digit code doesn't match. Check the SMS and try again."
                "ERROR_SESSION_EXPIRED" ->
                    "Your verification code expired. Tap Resend code to get a new one."
                "ERROR_TOO_MANY_REQUESTS", "ERROR_QUOTA_EXCEEDED" ->
                    "SMS verification limit reached temporarily. Try again shortly."
                "ERROR_CREDENTIAL_ALREADY_IN_USE" ->
                    "This phone number is already linked to another DL Chat account."
                "ERROR_PROVIDER_ALREADY_LINKED" ->
                    "A phone number is already linked to this account. Use change phone number instead."
                "ERROR_CAPTCHA_CHECK_FAILED", "ERROR_APP_NOT_AUTHORIZED" ->
                    "Device verification check failed. Ensure Play Services and device integrity checks are configured."
                else -> error.localizedMessage ?: "Couldn't verify that phone number right now."
            }
        }
        return error.localizedMessage ?: "Couldn't complete phone verification. Please try again."
    }
}
