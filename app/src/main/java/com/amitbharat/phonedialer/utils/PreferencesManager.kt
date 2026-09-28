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

    fun getDefaultSim(): Int = prefs.getInt("default_sim", -1)
    fun setDefaultSim(sim: Int) = prefs.edit().putInt("default_sim", sim).apply()

    fun isBlockUnknownCallsEnabled(): Boolean = prefs.getBoolean("block_unknown_calls", false)
    fun setBlockUnknownCallsEnabled(enabled: Boolean) = prefs.edit().putBoolean("block_unknown_calls", enabled).apply()

    fun isBlockSpamCallsEnabled(): Boolean = prefs.getBoolean("block_spam_calls", true)
    fun setBlockSpamCallsEnabled(enabled: Boolean) = prefs.edit().putBoolean("block_spam_calls", enabled).apply()

    fun isAutoFormatNumbersEnabled(): Boolean = prefs.getBoolean("auto_format_numbers", true)
    fun setAutoFormatNumbersEnabled(enabled: Boolean) = prefs.edit().putBoolean("auto_format_numbers", enabled).apply()

    fun isKeepAliveEnabled(): Boolean = prefs.getBoolean("keep_alive_background", true)
    fun setKeepAliveEnabled(enabled: Boolean) = prefs.edit().putBoolean("keep_alive_background", enabled).apply()

    // SMS Message Preferences
    fun isSmsDeliveryReportsEnabled(): Boolean = prefs.getBoolean("sms_delivery_reports", true)
    fun setSmsDeliveryReportsEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_delivery_reports", enabled).apply()

    fun isSmsNotificationsEnabled(): Boolean = prefs.getBoolean("sms_notifications", true)
    fun setSmsNotificationsEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_notifications", enabled).apply()

    fun isSmsSoundEnabled(): Boolean = prefs.getBoolean("sms_sound", true)
    fun setSmsSoundEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_sound", enabled).apply()

    fun isSmsVibrationEnabled(): Boolean = prefs.getBoolean("sms_vibration", true)
    fun setSmsVibrationEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_vibration", enabled).apply()

    fun isAutoRetrieveMmsEnabled(): Boolean = prefs.getBoolean("sms_auto_mms", true)
    fun setAutoRetrieveMmsEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_auto_mms", enabled).apply()

    fun isGroupMessagingEnabled(): Boolean = prefs.getBoolean("sms_group_messaging", true)
    fun setGroupMessagingEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_group_messaging", enabled).apply()

    fun isAutoDeleteOldMessagesEnabled(): Boolean = prefs.getBoolean("sms_auto_delete_old", false)
    fun setAutoDeleteOldMessagesEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_auto_delete_old", enabled).apply()

    // Advanced Message Settings
    fun isUseSimpleCharactersEnabled(): Boolean = prefs.getBoolean("sms_use_simple_chars", false)
    fun setUseSimpleCharactersEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_use_simple_chars", enabled).apply()

    fun isShowIphoneReactionsEnabled(): Boolean = prefs.getBoolean("sms_show_iphone_reactions", true)
    fun setShowIphoneReactionsEnabled(enabled: Boolean) = prefs.edit().putBoolean("sms_show_iphone_reactions", enabled).apply()

    // Advanced Call Settings
    fun getCallRecordingMode(): String = prefs.getString("call_record_mode", "ALL") ?: "ALL"
    fun setCallRecordingMode(mode: String) = prefs.edit().putString("call_record_mode", mode).apply()

    fun isFlipToSilenceEnabled(): Boolean = prefs.getBoolean("flip_to_silence", true)
    fun setFlipToSilenceEnabled(enabled: Boolean) = prefs.edit().putBoolean("flip_to_silence", enabled).apply()

    fun isCallerIdAnnouncementEnabled(): Boolean = prefs.getBoolean("caller_id_announce", false)
    fun setCallerIdAnnouncementEnabled(enabled: Boolean) = prefs.edit().putBoolean("caller_id_announce", enabled).apply()

    fun isAssistedDialingEnabled(): Boolean = prefs.getBoolean("assisted_dialing", true)
    fun setAssistedDialingEnabled(enabled: Boolean) = prefs.edit().putBoolean("assisted_dialing", enabled).apply()

    fun getBlockedNumbers(): Set<String> = prefs.getStringSet("blocked_numbers", emptySet()) ?: emptySet()
    fun addBlockedNumber(number: String) {
        val set = getBlockedNumbers().toMutableSet()
        set.add(number)
        prefs.edit().putStringSet("blocked_numbers", set).apply()
    }
    fun removeBlockedNumber(number: String) {
        val set = getBlockedNumbers().toMutableSet()
        set.remove(number)
        prefs.edit().putStringSet("blocked_numbers", set).apply()
    }

    fun getQuickResponses(): List<String> {
        val set = prefs.getStringSet("sms_quick_responses", null)
        return if (set != null && set.isNotEmpty()) {
            set.toList()
        } else {
            listOf(
                "Can't talk right now. What's up?",
                "I'll call you right back.",
                "In a meeting, will text you later.",
                "On my way, see you soon!"
            )
        }
    }

    fun setQuickResponses(responses: List<String>) {
        prefs.edit().putStringSet("sms_quick_responses", responses.toSet()).apply()
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
