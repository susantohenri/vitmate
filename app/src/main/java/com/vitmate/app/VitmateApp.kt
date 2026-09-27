package com.vitmate.app

import android.app.Application
import com.vitmate.app.ads.AdMobManager
import com.vitmate.app.data.repository.DownloadRepository
import com.vitmate.app.data.repository.PreferencesRepository
import com.vitmate.app.data.repository.RemoteConfigRepository
import com.vitmate.app.ytdlp.YtDlpHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VitmateApp : Application() {

    lateinit var remoteConfigRepository: RemoteConfigRepository
        private set

    lateinit var downloadRepository: DownloadRepository
        private set

    lateinit var preferencesRepository: PreferencesRepository
        private set

    lateinit var adMobManager: AdMobManager
        private set

    override fun onCreate() {
        super.onCreate()

        remoteConfigRepository = RemoteConfigRepository()
        downloadRepository = DownloadRepository(this)
        preferencesRepository = PreferencesRepository(this)
        adMobManager = AdMobManager(this, remoteConfigRepository)

        // Initialize yt-dlp & ffmpeg asynchronously
        CoroutineScope(Dispatchers.IO).launch {
            YtDlpHelper.init(this@VitmateApp)
            remoteConfigRepository.fetchWhitelist()
            val adsConfigResult = remoteConfigRepository.fetchAdsConfig()
            adsConfigResult.onSuccess { config ->
                if (config.isAdsEnabled) {
                    adMobManager.preloadRewardedAd(config.rewardedAdUnitId)
                }
            }
        }
    }
}
