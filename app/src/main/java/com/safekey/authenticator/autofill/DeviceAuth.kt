package com.safekey.authenticator.autofill

import android.app.KeyguardManager
import android.content.Context
import android.os.Build
import androidx.biometric.BiometricManager

/**
 * Whether this device can verify the user's identity (fingerprint/face or
 * screen lock). Mirrors the availability rules already used by MainActivity:
 * BIOMETRIC_STRONG everywhere, with a device-credential fallback.
 */
object DeviceAuth {

    fun canAuthenticate(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= 30) {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            ) == BiometricManager.BIOMETRIC_SUCCESS
        } else {
            BiometricManager.from(context).canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG
            ) == BiometricManager.BIOMETRIC_SUCCESS ||
                context.getSystemService(KeyguardManager::class.java)?.isDeviceSecure == true
        }
}
