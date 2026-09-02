package com.amitbharat.phonedialer.utils

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatDelegate

enum class ThemeMode {
    SYSTEM,
    LIGHT,
    DARK,
    AMOLED
}

class PreferencesManager private constructor(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences("dialer_prefs", Context.MODE_PRIVATE)

    fun getThemeMode(): ThemeMode {
        val name = prefs.getString("theme_mode", ThemeMode.SYSTEM.name) ?: ThemeMode.SYSTEM.name
        return try { ThemeMode.valueOf(name) } catch (e: Exception) { ThemeMode.SYSTEM }
    }

    fun setThemeMode(mode: ThemeMode) {
        prefs.edit().putString("theme_mode", mode.name).apply()
    }

    fun isAutoCallRecordingEnabled(): Boolean {
        return prefs.getBoolean("auto_call_recording", false)
    }

    fun setAutoCallRecordingEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_call_recording", enabled).apply()
    }

    fun isVibrationEnabled(): Boolean {
        return prefs.getBoolean("dialpad_vibration", true)
    }

    fun setVibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("dialpad_vibration", enabled).apply()
    }

    fun isDialpadSoundEnabled(): Boolean {
        return prefs.getBoolean("dialpad_sound", true)
    }

    fun setDialpadSoundEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("dialpad_sound", enabled).apply()
    }

    fun getDefaultSim(): Int {
        // 0 = Always Ask, 1 = SIM 1, 2 = SIM 2
        return prefs.getInt("default_sim", 0)
    }

    fun setDefaultSim(sim: Int) {
        prefs.edit().putInt("default_sim", sim).apply()
    }

    companion object {
        @Volatile
        private var instance: PreferencesManager? = null

        fun getInstance(context: Context): PreferencesManager {
            return instance ?: synchronized(this) {
                instance ?: PreferencesManager(context.applicationContext).also { instance = it }
            }
        }
    }
}

object ThemeUtils {
    fun applyTheme(context: Context) {
        val prefs = PreferencesManager.getInstance(context)
        when (prefs.getThemeMode()) {
            ThemeMode.LIGHT -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO)
            ThemeMode.DARK, ThemeMode.AMOLED -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
            ThemeMode.SYSTEM -> AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM)
        }
    }
}
