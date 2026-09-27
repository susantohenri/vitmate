package com.vitmate.app.ui.settings

import android.app.Activity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vitmate.app.ads.AdMobManager
import com.vitmate.app.data.repository.AppLanguage
import com.vitmate.app.data.repository.AppThemeMode
import com.vitmate.app.data.repository.PreferencesRepository
import kotlinx.coroutines.flow.StateFlow

class SettingsViewModel(
    private val preferencesRepository: PreferencesRepository,
    private val adMobManager: AdMobManager
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = preferencesRepository.themeModeFlow
    val language: StateFlow<AppLanguage> = preferencesRepository.languageFlow
    val isPrivacyOptionsRequired: StateFlow<Boolean> = adMobManager.isPrivacyOptionsRequired

    fun onThemeModeChanged(mode: AppThemeMode) {
        preferencesRepository.setThemeMode(mode)
    }

    fun onLanguageChanged(lang: AppLanguage) {
        preferencesRepository.setLanguage(lang)
    }

    fun onShowPrivacyOptions(activity: Activity) {
        adMobManager.showPrivacyOptionsForm(activity)
    }
}

class SettingsViewModelFactory(
    private val preferencesRepository: PreferencesRepository,
    private val adMobManager: AdMobManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(SettingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return SettingsViewModel(preferencesRepository, adMobManager) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
