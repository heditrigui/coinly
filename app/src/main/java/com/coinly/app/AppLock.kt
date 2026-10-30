package com.coinly.app

import android.app.KeyguardManager
import android.os.Build
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity

/**
 * Fingerprint, face or the phone's own PIN/pattern/password, through the system prompt.
 * Must be created in the activity's onCreate, as BiometricPrompt requires.
 */
class AppLock(private val activity: FragmentActivity) {
    private var onResult: ((Boolean) -> Unit)? = null

    private val prompt = BiometricPrompt(
        activity,
        ContextCompat.getMainExecutor(activity),
        object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) = finish(true)
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence) = finish(false)
            // A finger that isn't recognised just lets the prompt ask again.
        },
    )

    private fun finish(ok: Boolean) {
        val callback = onResult
        onResult = null
        callback?.invoke(ok)
    }

    /** False when the phone has no screen lock at all, so there's nothing to unlock with. */
    fun available(): Boolean =
        BiometricManager.from(activity).canAuthenticate(AUTHENTICATORS) == BiometricManager.BIOMETRIC_SUCCESS ||
            activity.getSystemService(KeyguardManager::class.java).isDeviceSecure

    fun authenticate(title: String, onResult: (Boolean) -> Unit) {
        if (this.onResult != null) return
        this.onResult = onResult
        val info = BiometricPrompt.PromptInfo.Builder()
            .setTitle(title)
            .setSubtitle("Use your fingerprint, face or screen lock")
            .setAllowedAuthenticators(AUTHENTICATORS)
            .build()
        try {
            prompt.authenticate(info)
        } catch (e: Exception) {
            finish(false)
        }
    }

    /** Keeps the app's content out of the recent-apps screen (and, before Android 13, out of screenshots). */
    fun hideInRecents(hide: Boolean) {
        if (Build.VERSION.SDK_INT >= 33) {
            activity.setRecentsScreenshotEnabled(!hide)
        } else if (hide) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    private companion object {
        const val AUTHENTICATORS = BIOMETRIC_WEAK or DEVICE_CREDENTIAL
    }
}
