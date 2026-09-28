package com.henrisusanto.vitmate.data.repository

import android.net.Uri
import com.henrisusanto.vitmate.data.model.AdsConfig
import com.henrisusanto.vitmate.data.model.WhitelistConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit

class RemoteConfigRepository(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    private var cachedWhitelist: WhitelistConfig? = null
    private var cachedAdsConfig: AdsConfig? = null

    companion object {
        private const val WHITELIST_URL =
            "https://raw.githubusercontent.com/susantohenri/admob-remote-configs/refs/heads/main/vitmate/whitelist.json"
        private const val ADS_CONFIG_URL =
            "https://raw.githubusercontent.com/susantohenri/admob-remote-configs/refs/heads/main/vitmate/ads_config.json"

        // Fallback domains if offline
        private val FALLBACK_DOMAINS = listOf(
            "archive.org",
            "commons.wikimedia.org",
            "wikimedia.org",
            "pexels.com",
            "pixabay.com",
            "mixkit.co",
            "coverr.co",
            "videvo.net"
        )
    }

    suspend fun fetchWhitelist(): Result<WhitelistConfig> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(WHITELIST_URL).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = json.decodeFromString<WhitelistConfig>(body)
                        cachedWhitelist = parsed
                        return@withContext Result.success(parsed)
                    }
                }
            }
            cachedWhitelist?.let { return@withContext Result.success(it) }
            Result.failure(Exception("Whitelist unavailable"))
        } catch (e: Exception) {
            cachedWhitelist?.let { return@withContext Result.success(it) }
            Result.failure(e)
        }
    }

    suspend fun fetchAdsConfig(): Result<AdsConfig> = withContext(Dispatchers.IO) {
        try {
            val request = Request.Builder().url(ADS_CONFIG_URL).build()
            client.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    if (!body.isNullOrBlank()) {
                        val parsed = json.decodeFromString<AdsConfig>(body)
                        cachedAdsConfig = parsed
                        return@withContext Result.success(parsed)
                    }
                }
            }
            val fallback = cachedAdsConfig ?: AdsConfig()
            Result.success(fallback)
        } catch (e: Exception) {
            val fallback = cachedAdsConfig ?: AdsConfig()
            Result.success(fallback)
        }
    }

    fun getCachedAdsConfig(): AdsConfig? = cachedAdsConfig
    fun getCachedWhitelist(): WhitelistConfig? = cachedWhitelist

    /**
     * Proper hostname matching against whitelist:
     * - Hostname matches exact domain (e.g., "archive.org" == "archive.org")
     * - Or hostname is a valid subdomain (e.g., "ia8001.us.archive.org" endsWith ".archive.org")
     * Insecure substring matches like "notarchive.org" will NOT match.
     */
    fun isUrlAllowed(url: String, whitelist: WhitelistConfig): Boolean {
        val uri = try {
            Uri.parse(url)
        } catch (e: Exception) {
            return false
        }
        val host = uri.host?.lowercase() ?: return false

        return whitelist.domains.any { domainPattern ->
            val domain = domainPattern.trim().lowercase()
            if (domain.isBlank()) return@any false
            // Exact match
            if (host == domain) return@any true
            // Subdomain match: host ends with .domain
            if (host.endsWith(".$domain")) return@any true
            false
        }
    }
}
