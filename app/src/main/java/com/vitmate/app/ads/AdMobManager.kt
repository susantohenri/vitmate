package com.vitmate.app.ads

import android.app.Activity
import android.content.Context
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
                if (consentInformation.canRequestAds()) {
                    initializeMobileAds()
                }
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
        MobileAds.initialize(context) {
            preloadRewardedAd()
        }
    }

    fun showPrivacyOptionsForm(activity: Activity, onDismissed: () -> Unit = {}) {
        UserMessagingPlatform.showPrivacyOptionsForm(activity) { formError ->
            onDismissed()
        }
    }

    fun preloadRewardedAd(adUnitIdOverride: String? = null) {
        if (rewardedAd != null || isAdLoading) return
        if (!consentInformation.canRequestAds()) return

        isAdLoading = true
        val adUnitId = adUnitIdOverride ?: "ca-app-pub-3940256099942544/5224354917"
        val adRequest = AdRequest.Builder().build()

        RewardedAd.load(
            context,
            adUnitId,
            adRequest,
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewardedAd = ad
                    isAdLoading = false
                }

                override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                    rewardedAd = null
                    isAdLoading = false
                }
            }
        )
    }

    fun showRewardedAd(
        activity: Activity,
        adUnitId: String,
        onRewardEarned: () -> Unit,
        onAdFailed: (String) -> Unit
    ) {
        if (rewardedAd == null) {
            // Try loading and show once loaded, or fail
            isAdLoading = true
            val adRequest = AdRequest.Builder().build()
            RewardedAd.load(
                activity,
                adUnitId,
                adRequest,
                object : RewardedAdLoadCallback() {
                    override fun onAdLoaded(ad: RewardedAd) {
                        rewardedAd = ad
                        isAdLoading = false
                        presentRewardedAd(activity, ad, onRewardEarned, onAdFailed)
                    }

                    override fun onAdFailedToLoad(loadAdError: LoadAdError) {
                        rewardedAd = null
                        isAdLoading = false
                        onAdFailed(loadAdError.message)
                    }
                }
            )
            return
        }

        val ad = rewardedAd ?: return onAdFailed("Ad not ready")
        presentRewardedAd(activity, ad, onRewardEarned, onAdFailed)
    }

    private fun presentRewardedAd(
        activity: Activity,
        ad: RewardedAd,
        onRewardEarned: () -> Unit,
        onAdFailed: (String) -> Unit
    ) {
        var rewardEarned = false

        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewardedAd = null
                preloadRewardedAd()
                if (rewardEarned) {
                    onRewardEarned()
                } else {
                    onAdFailed("Rewarded ad not completed")
                }
            }

            override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                rewardedAd = null
                preloadRewardedAd()
                onAdFailed(adError.message)
            }
        }

        ad.show(activity) { rewardItem ->
            rewardEarned = true
        }
    }
}
