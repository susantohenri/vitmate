package com.vitmate.app.ui.home

import android.app.Activity
import android.content.Context
import android.webkit.URLUtil
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vitmate.app.R
import com.vitmate.app.ads.AdMobManager
import com.vitmate.app.data.model.DownloadItem
import com.vitmate.app.data.model.DownloadStatus
import com.vitmate.app.data.model.MediaFormatType
import com.vitmate.app.data.model.QualityOption
import com.vitmate.app.data.model.VideoMetadata
import com.vitmate.app.data.repository.DownloadRepository
import com.vitmate.app.data.repository.PreferencesRepository
import com.vitmate.app.data.repository.RemoteConfigRepository
import com.vitmate.app.service.DownloadService
import com.vitmate.app.ytdlp.YtDlpHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

sealed class HomeUiState {
    object Idle : HomeUiState()
    data class LoadingAd(val messageRes: Int = R.string.loading_ad) : HomeUiState()
    data class CheckingWhitelist(val messageRes: Int = R.string.checking_whitelist) : HomeUiState()
    data class ProcessingMetadata(val messageRes: Int = R.string.processing_url) : HomeUiState()
    data class MetadataReady(val metadata: VideoMetadata) : HomeUiState()
    data class Error(val messageRes: Int? = null, val customMessage: String? = null) : HomeUiState()
}

class HomeViewModel(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val adMobManager: AdMobManager
) : ViewModel() {

    private val _urlInput = MutableStateFlow("")
    val urlInput: StateFlow<String> = _urlInput.asStateFlow()

    private val _uiState = MutableStateFlow<HomeUiState>(HomeUiState.Idle)
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private val _selectedFormat = MutableStateFlow(MediaFormatType.MP4)
    val selectedFormat: StateFlow<MediaFormatType> = _selectedFormat.asStateFlow()

    private val _selectedQuality = MutableStateFlow<QualityOption?>(null)
    val selectedQuality: StateFlow<QualityOption?> = _selectedQuality.asStateFlow()

    val isTermsAcknowledged: StateFlow<Boolean> = preferencesRepository.termsAcknowledgedFlow

    private val _isAcknowledgementChecked = MutableStateFlow(preferencesRepository.isTermsAcknowledged())
    val isAcknowledgementChecked: StateFlow<Boolean> = _isAcknowledgementChecked.asStateFlow()

    fun onUrlChanged(newUrl: String) {
        _urlInput.value = newUrl
        if (_uiState.value !is HomeUiState.Idle) {
            _uiState.value = HomeUiState.Idle
        }
    }

    fun onPasteUrl(pasted: String) {
        val trimmed = pasted.trim()
        if (trimmed.isNotEmpty()) {
            _urlInput.value = trimmed
            _uiState.value = HomeUiState.Idle
        }
    }

    fun onClearUrl() {
        _urlInput.value = ""
        _uiState.value = HomeUiState.Idle
    }

    fun onFormatSelected(format: MediaFormatType) {
        _selectedFormat.value = format
    }

    fun onQualitySelected(quality: QualityOption) {
        _selectedQuality.value = quality
    }

    fun onAcknowledgementChanged(checked: Boolean) {
        _isAcknowledgementChecked.value = checked
        if (checked) {
            preferencesRepository.setTermsAcknowledged(true)
        }
    }

    fun startRewardedAdAndProcessFlow(activity: Activity?) {
        val url = _urlInput.value.trim()
        if (url.isBlank() || (!url.startsWith("http://", ignoreCase = true) && !url.startsWith("https://", ignoreCase = true))) {
            _uiState.value = HomeUiState.Error(messageRes = R.string.error_invalid_url)
            return
        }

        _uiState.value = HomeUiState.LoadingAd()

        viewModelScope.launch {
            val adsConfig = remoteConfigRepository.fetchAdsConfig().getOrDefault(com.vitmate.app.data.model.AdsConfig())

            if (!adsConfig.isAdsEnabled || activity == null) {
                // If ads are disabled by remote config or activity is null, proceed directly
                processWhitelistAndMetadata(url)
                return@launch
            }

            adMobManager.showRewardedAd(
                activity = activity,
                adUnitId = adsConfig.rewardedAdUnitId,
                onRewardEarned = {
                    // Reward received! Now proceed to Whitelist check & Metadata
                    processWhitelistAndMetadata(url)
                },
                onAdUnavailable = {
                    _uiState.value = HomeUiState.Error(
                        messageRes = R.string.error_ad_unavailable
                    )
                },
                onAdNotCompleted = {
                    _uiState.value = HomeUiState.Error(
                        messageRes = R.string.error_ad_not_completed
                    )
                }
            )
        }
    }

    private fun processWhitelistAndMetadata(url: String) {
        viewModelScope.launch {
            _uiState.value = HomeUiState.CheckingWhitelist()

            val whitelistResult = remoteConfigRepository.fetchWhitelist()
            val whitelist = whitelistResult.getOrNull()

            if (whitelist == null) {
                _uiState.value = HomeUiState.Error(messageRes = R.string.error_whitelist_unavailable)
                return@launch
            }

            val isAllowed = remoteConfigRepository.isUrlAllowed(url, whitelist)
            if (!isAllowed) {
                _uiState.value = HomeUiState.Error(messageRes = R.string.error_unsupported_domain)
                return@launch
            }

            // Step: Fetch metadata
            _uiState.value = HomeUiState.ProcessingMetadata()
            val metadataResult = YtDlpHelper.fetchMetadata(url)

            metadataResult.fold(
                onSuccess = { meta ->
                    _selectedFormat.value = MediaFormatType.MP4
                    _selectedQuality.value = meta.videoQualities.firstOrNull()
                    _uiState.value = HomeUiState.MetadataReady(meta)
                },
                onFailure = { err ->
                    _uiState.value = HomeUiState.Error(
                        messageRes = R.string.error_metadata_failed,
                        customMessage = err.localizedMessage
                    )
                }
            )
        }
    }

    fun startDownload(context: Context, onSuccess: () -> Unit) {
        val currentState = _uiState.value as? HomeUiState.MetadataReady ?: return
        val meta = currentState.metadata
        val format = _selectedFormat.value
        val quality = _selectedQuality.value

        preferencesRepository.setTermsAcknowledged(true)

        val itemId = UUID.randomUUID().toString()
        val downloadItem = DownloadItem(
            id = itemId,
            url = meta.originalUrl,
            title = meta.title,
            sanitizedFilename = meta.sanitizedFilename,
            thumbnailUrl = meta.thumbnailUrl,
            formatType = format,
            quality = if (format == MediaFormatType.MP4) quality?.label else "MP3 Audio",
            qualityId = quality?.formatId,
            status = DownloadStatus.QUEUED,
            progress = 0
        )

        downloadRepository.addItem(downloadItem)

        DownloadService.startDownload(
            context = context,
            itemId = itemId,
            url = meta.originalUrl,
            title = meta.title,
            formatType = format,
            qualityId = quality?.formatId
        )

        // Reset to idle to allow entering another URL immediately (Section 1 multiple downloads)
        _urlInput.value = ""
        _uiState.value = HomeUiState.Idle
        onSuccess()
    }
}

class HomeViewModelFactory(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val downloadRepository: DownloadRepository,
    private val preferencesRepository: PreferencesRepository,
    private val adMobManager: AdMobManager
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HomeViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return HomeViewModel(
                remoteConfigRepository,
                downloadRepository,
                preferencesRepository,
                adMobManager
            ) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
