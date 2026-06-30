package com.example.healthmanager.platform

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class HealthVibrationController(context: Context) {
    private val appContext = context.applicationContext

    fun vibrateHeartRateAlert() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 500, 200, 500), -1)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                appContext.getSystemService(VibratorManager::class.java)
                    ?.defaultVibrator
                    ?.vibrate(effect)
            } else {
                legacyVibrator()?.vibrate(effect)
            }
        } else {
            @Suppress("DEPRECATION")
            legacyVibrator()?.vibrate(1000)
        }
    }

    @Suppress("DEPRECATION")
    private fun legacyVibrator(): Vibrator? {
        return appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }
}
