package com.vitmate.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class AppThemeMode {
    SYSTEM,
    LIGHT,
    DARK
}

enum class AppLanguage {
    SYSTEM,
    INDONESIAN,
    ENGLISH
}

class PreferencesRepository(private val context: Context) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("vitmate_prefs", Context.MODE_PRIVATE)

    companion object {
        const val CURRENT_TERMS_VERSION = 1
        private const val KEY_TERMS_ACK_VERSION = "key_terms_ack_version"
        private const val KEY_THEME_MODE = "key_theme_mode"
        private const val KEY_LANGUAGE = "key_language"
    }

    private val _themeModeFlow = MutableStateFlow(getThemeMode())
    val themeModeFlow: StateFlow<AppThemeMode> = _themeModeFlow.asStateFlow()

    private val _languageFlow = MutableStateFlow(getLanguage())
    val languageFlow: StateFlow<AppLanguage> = _languageFlow.asStateFlow()

    private val _termsAcknowledgedFlow = MutableStateFlow(isTermsAcknowledged())
    val termsAcknowledgedFlow: StateFlow<Boolean> = _termsAcknowledgedFlow.asStateFlow()

    fun isTermsAcknowledged(): Boolean {
        val ackVersion = prefs.getInt(KEY_TERMS_ACK_VERSION, 0)
        return ackVersion >= CURRENT_TERMS_VERSION
    }

    fun setTermsAcknowledged(acknowledged: Boolean) {
        val version = if (acknowledged) CURRENT_TERMS_VERSION else 0
        prefs.edit().putInt(KEY_TERMS_ACK_VERSION, version).apply()
        _termsAcknowledgedFlow.value = acknowledged
    }

    fun getThemeMode(): AppThemeMode {
        val modeStr = prefs.getString(KEY_THEME_MODE, AppThemeMode.SYSTEM.name)
        return try {
            AppThemeMode.valueOf(modeStr ?: AppThemeMode.SYSTEM.name)
        } catch (e: Exception) {
            AppThemeMode.SYSTEM
        }
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _themeModeFlow.value = mode
    }

    fun getLanguage(): AppLanguage {
        val langStr = prefs.getString(KEY_LANGUAGE, AppLanguage.SYSTEM.name)
        return try {
            AppLanguage.valueOf(langStr ?: AppLanguage.SYSTEM.name)
        } catch (e: Exception) {
            AppLanguage.SYSTEM
        }
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _languageFlow.value = language
    }
}
