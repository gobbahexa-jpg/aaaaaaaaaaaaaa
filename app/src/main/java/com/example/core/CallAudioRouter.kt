package com.example.core

import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Rational

enum class CallAudioRoute(val label: String) {
    EARPIECE("Earpiece"),
    SPEAKER("Speaker"),
    BLUETOOTH("Bluetooth")
}

object CallAudioRouter {
    fun applyAudioRoute(context: Context, route: CallAudioRoute, muted: Boolean = false): Result<CallAudioRoute> {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return Result.failure(IllegalStateException("AudioManager unavailable."))
        return try {
            audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
            audioManager.isMicrophoneMute = muted

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val desiredType = when (route) {
                    CallAudioRoute.SPEAKER -> AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    CallAudioRoute.EARPIECE -> AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    CallAudioRoute.BLUETOOTH -> AudioDeviceInfo.TYPE_BLUETOOTH_SCO
                }
                val device = audioManager.availableCommunicationDevices.firstOrNull {
                    it.type == desiredType ||
                        (route == CallAudioRoute.BLUETOOTH &&
                            (it.type == AudioDeviceInfo.TYPE_BLE_HEADSET || it.type == AudioDeviceInfo.TYPE_HEARING_AID))
                }
                if (device != null) {
                    audioManager.setCommunicationDevice(device)
                } else if (route == CallAudioRoute.EARPIECE) {
                    audioManager.clearCommunicationDevice()
                }
            } else {
                @Suppress("DEPRECATION")
                when (route) {
                    CallAudioRoute.SPEAKER -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = true
                    }
                    CallAudioRoute.EARPIECE -> {
                        audioManager.stopBluetoothSco()
                        audioManager.isBluetoothScoOn = false
                        audioManager.isSpeakerphoneOn = false
                    }
                    CallAudioRoute.BLUETOOTH -> {
                        audioManager.isSpeakerphoneOn = false
                        audioManager.startBluetoothSco()
                        audioManager.isBluetoothScoOn = true
                    }
                }
            }
            Result.success(route)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun releaseAudioFocus(context: Context) {
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                audioManager.clearCommunicationDevice()
            } else {
                @Suppress("DEPRECATION")
                audioManager.isSpeakerphoneOn = false
                @Suppress("DEPRECATION")
                audioManager.stopBluetoothSco()
            }
            audioManager.isMicrophoneMute = false
            audioManager.mode = AudioManager.MODE_NORMAL
        } catch (_: Exception) {
        }
    }

    fun isPipSupported(context: Context): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
    }

    fun enterCallPipMode(activity: Activity): Result<Unit> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O || !isPipSupported(activity)) {
            return Result.failure(IllegalStateException("Picture-in-Picture not supported on this device."))
        }
        return try {
            val params = PictureInPictureParams.Builder()
                .setAspectRatio(Rational(9, 16))
                .build()
            activity.enterPictureInPictureMode(params)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
