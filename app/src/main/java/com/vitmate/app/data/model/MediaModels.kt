package com.vitmate.app.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class MediaFormatType {
    MP4,
    MP3
}

@Serializable
enum class DownloadStatus {
    QUEUED,
    DOWNLOADING,
    COMPLETED,
    FAILED,
    CANCELLED
}

@Serializable
data class QualityOption(
    val formatId: String,
    val label: String,
    val resolution: String = "",
    val ext: String = "mp4",
    val estimatedBytes: Long = 0L
)

@Serializable
data class VideoMetadata(
    val originalUrl: String,
    val title: String,
    val sanitizedFilename: String,
    val thumbnailUrl: String?,
    val durationSeconds: Long = 0L,
    val estimatedSizeBytes: Long = 0L,
    val videoQualities: List<QualityOption> = emptyList(),
    val hasAudioOnlyOption: Boolean = true
)

@Serializable
data class DownloadItem(
    val id: String,
    val url: String,
    val title: String,
    val sanitizedFilename: String,
    val thumbnailUrl: String? = null,
    val formatType: MediaFormatType = MediaFormatType.MP4,
    val quality: String? = null,
    val qualityId: String? = null,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val progress: Int = 0,
    val etaSeconds: Long = 0L,
    val localFilePath: String? = null,
    val fileSize: Long = 0L,
    val errorMessage: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)

@Serializable
data class WhitelistConfig(
    val version: Int = 1,
    val domains: List<String> = emptyList()
)

@Serializable
data class AdsConfig(
    val appOpenAdUnitId: String = "ca-app-pub-3940256099942544/9257395921",
    val bannerAdUnitId: String = "ca-app-pub-3940256099942544/6300978111",
    val interstitialAdUnitId: String = "ca-app-pub-3940256099942544/1033173712",
    val rewardedAdUnitId: String = "ca-app-pub-3940256099942544/5224354917",
    val nativeAdUnitId: String = "ca-app-pub-3940256099942544/2247696110",
    val isAdsEnabled: Boolean = true
)
