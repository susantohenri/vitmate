package com.vitmate.app.ads

import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.ump.ConsentInformation
import com.google.android.ump.ConsentRequestParameters
import com.google.android.ump.UserMessagingPlatform
import com.vitmate.app.data.repository.RemoteConfigRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.atomic.AtomicBoolean

class AdMobManager(
    private val context: Context,
    private val remoteConfigRepository: RemoteConfigRepository
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val consentInformation: ConsentInformation =
        UserMessagingPlatform.getConsentInformation(context)

    private val isMobileAdsInitializeCalled = AtomicBoolean(false)

    private var rewardedAd: RewardedAd? = null
    private var isAdLoading = false

    private val _isPrivacyOptionsRequired = MutableStateFlow(false)
    val isPrivacyOptionsRequired: StateFlow<Boolean> = _isPrivacyOptionsRequired.asStateFlow()

    init {
        // Check initial privacy options status
        _isPrivacyOptionsRequired.value =
            consentInformation.privacyOptionsRequirementStatus ==
                    ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED
    }

    /**
     * Request UMP consent info and show form if required.
     */
    fun requestConsentAndInit(activity: Activity, onComplete: () -> Unit = {}) {
        val params = ConsentRequestParameters.Builder()
            .setTagForUnderAgeOfConsent(false)
            .build()

        consentInformation.requestConsentInfoUpdate(
            activity,
            params,
            {
                UserMessagingPlatform.loadAndShowConsentFormIfRequired(activity) { formError ->
                    _isPrivacyOptionsRequired.value =
                        consentInformation.privacyOptionsRequirementStatus ==
                                ConsentInformation.PrivacyOptionsRequirementStatus.REQUIRED

                    if (consentInformation.canRequestAds()) {
                        initializeMobileAds()
                    }
                    onComplete()
                }
            },
            { requestConsentError ->
                Log.w("AdMobManager", "Consent update error: ${requestConsentError.message}")
                initializeMobileAds()
                onComplete()
            }
        )

        // If consent is already obtained in prior sessions
        if (consentInformation.canRequestAds()) {
            initializeMobileAds()
        }
    }

    private fun initializeMobileAds() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) return
        mainHandler.post {
            MobileAds.initialize(context) { status ->
                Log.d("AdMobManager", "MobileAds initialized: $status")
                preloadRewardedAd()
            }
        }
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            if (formError != null) {
                Log.w("AdMobManager", "Privacy options form error: ${formError.message}")
            }
            onDismissed()
        }
    }

    fun preloadRewardedAd(adUnitIdOverride: String? = null) {
        mainHandler.post {
            if (rewardedAd != null || isAdLoading) return@post

            isAdLoading = true
            val resolvedAdUnitId = adUnitIdOverride
                ?: remoteConfigRepository.getCachedAdsConfig()?.rewardedAdUnitId
                ?: "ca-app-pub-3940256099942544/5224354917"
            val adRequest = AdRequest.Builder().build()

            Log.d("AdMobManager", "Preloading rewarded ad: $resolvedAdUnitId")
            RewardedAd.load(
                context,
                resolvedAdUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        Log.d("AdMobManager", "Rewarded ad successfully preloaded")
                        rewardedAd = ad
                        isAdLoading = false
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        Log.e("AdMobManager", "Rewarded ad failed to preload: ${loadAdError.message} (code ${loadAdError.code})")
                        rewardedAd = null
                        isAdLoading = false
                    }
                }
            )
        }
    }

    fun showRewardedAd(
        activity: Activity,
        adUnitId: String,
        onRewardEarned: () -> Unit,
        onAdUnavailable: () -> Unit,
        onAdNotCompleted: () -> Unit
    ) {
        mainHandler.post {
            if (!isMobileAdsInitializeCalled.get()) {
                initializeMobileAds()
            }

            val currentAd = rewardedAd
            if (currentAd == null) {
                // Attempt to load once if not cached, or report unavailable
                isAdLoading = true
                val adRequest = AdRequest.Builder().build()
                Log.d("AdMobManager", "Loading rewarded ad on demand: $adUnitId")
                RewardedAd.load(
                    activity,
                    adUnitId,
                    adRequest,
                    object : RewardedAdLoadCallback() {
                        override fun onAdLoaded(ad: RewardedAd) {
                            Log.d("AdMobManager", "On-demand rewarded ad loaded successfully")
                            rewardedAd = ad
                            isAdLoading = false
                            presentRewardedAd(activity, ad, adUnitId, onRewardEarned, onAdUnavailable, onAdNotCompleted)
                        }

                        override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                            Log.e("AdMobManager", "On-demand rewarded ad failed to load: ${loadAdError.message} (code: ${loadAdError.code})")
                            rewardedAd = null
                            isAdLoading = false
                            onAdUnavailable()
                        }
                    }
                )
                return@post
            }

            presentRewardedAd(activity, currentAd, adUnitId, onRewardEarned, onAdUnavailable, onAdNotCompleted)
        }
    }

    private fun presentRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        adUnitId: String,
        onRewardEarned: () -> Unit,
        onAdUnavailable: () -> Unit,
        onAdNotCompleted: () -> Unit
    ) {
        var rewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                Log.d("AdMobManager", "Rewarded ad dismissed. Reward earned: $rewardEarned")
                rewardedAd = null
                preloadRewardedAd(adUnitId)
                if (rewardEarned) {
                    onRewardEarned()
                } else {
                    onAdNotCompleted()
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                Log.e("AdMobManager", "Rewarded ad failed to show: ${adError.message}")
                rewardedAd = null
                preloadRewardedAd(adUnitId)
                onAdUnavailable()
            }
        }

        ad.show(activity) { rewardItem ->
            Log.d("AdMobManager", "User earned reward: ${rewardItem.amount} ${rewardItem.type}")
            rewardEarned = true
        }
    }
}
