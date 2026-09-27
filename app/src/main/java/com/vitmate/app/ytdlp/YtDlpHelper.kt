package com.vitmate.app.ytdlp

import android.content.Context
import android.os.Environment
import com.vitmate.app.data.model.MediaFormatType
import com.vitmate.app.data.model.QualityOption
import com.vitmate.app.data.model.VideoMetadata
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLException
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoFormat
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object YtDlpHelper {

    private var isInitialized = false

    fun init(context: Context) {
        if (isInitialized) return
        try {
            YoutubeDL.getInstance().init(context.applicationContext)
            FFmpeg.getInstance().init(context.applicationContext)
            isInitialized = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun fetchMetadata(url: String): Result<VideoMetadata> = withContext(Dispatchers.IO) {
        try {
            val videoInfo: VideoInfo = YoutubeDL.getInstance().getInfo(url)
            val title = videoInfo.title ?: "Media_${System.currentTimeMillis()}"
            val sanitized = sanitizeFilename(title)
            val duration = videoInfo.duration.toLong()
            val thumbnail = videoInfo.thumbnail

            // Extract qualities
            val qualities = mutableListOf<QualityOption>()
            val rawFormats: List<VideoFormat>? = videoInfo.formats

            if (!rawFormats.isNullOrEmpty()) {
                val seenResolutions = mutableSetOf<String>()
                // Filter and sort video formats
                rawFormats
                    .filter { it.height > 0 || !it.formatNote.isNullOrBlank() }
                    .sortedByDescending { it.height }
                    .forEach { fmt ->
                        val res = if (fmt.height > 0) "${fmt.height}p" else (fmt.formatNote ?: "Standard")
                        if (!seenResolutions.contains(res)) {
                            seenResolutions.add(res)
                            qualities.add(
                                QualityOption(
                                    formatId = fmt.formatId ?: "best",
                                    label = res,
                                    resolution = res,
                                    ext = fmt.ext ?: "mp4",
                                    estimatedBytes = fmt.fileSize
                                )
                            )
                        }
                    }
            }

            if (qualities.isEmpty()) {
                qualities.add(
                    QualityOption(
                        formatId = "best",
                        label = "Best Available",
                        resolution = "Best",
                        ext = "mp4"
                    )
                )
            }

            val estimatedTotalBytes = qualities.firstOrNull()?.estimatedBytes ?: 0L

            Result.success(
                VideoMetadata(
                    originalUrl = url,
                    title = title,
                    sanitizedFilename = sanitized,
                    thumbnailUrl = thumbnail,
                    durationSeconds = duration,
                    estimatedSizeBytes = estimatedTotalBytes,
                    videoQualities = qualities,
                    hasAudioOnlyOption = true
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun sanitizeFilename(input: String): String {
        val clean = input
            .replace(Regex("[\\\\/:*?\"<>|\\p{Cntrl}]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
        val finalName = if (clean.length > 80) clean.take(80).trim() else clean
        return if (finalName.isBlank()) "vitmate_download_${System.currentTimeMillis()}" else finalName
    }

    fun getDownloadDir(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.filesDir, "downloads")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    suspend fun executeDownload(
        context: Context,
        downloadId: String,
        url: String,
        title: String,
        formatType: MediaFormatType,
        qualityId: String?,
        onProgress: (progress: Int, etaSeconds: Long) -> Unit
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val downloadDir = getDownloadDir(context)
            val ext = if (formatType == MediaFormatType.MP3) "mp3" else "mp4"
            val sanitized = sanitizeFilename(title)
            val outputFile = File(downloadDir, "$sanitized.$ext")

            val request = YoutubeDLRequest(url)
            request.addOption("-o", outputFile.absolutePath)

            if (formatType == MediaFormatType.MP3) {
                request.addOption("-x")
                request.addOption("--audio-format", "mp3")
            } else {
                if (!qualityId.isNullOrBlank() && qualityId != "best") {
                    request.addOption("-f", "$qualityId+bestaudio/best")
                } else {
                    request.addOption("-f", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]/best")
                }
            }

            // Real progress callback
            YoutubeDL.getInstance().execute(request, downloadId) { progress, etaInSeconds, _ ->
                onProgress(progress.toInt().coerceIn(0, 100), etaInSeconds)
            }

            if (outputFile.exists()) {
                Result.success(outputFile)
            } else {
                // Look for file with matched base name if extension changed by yt-dlp
                val matched = downloadDir.listFiles()?.firstOrNull { it.name.startsWith(sanitized) }
                if (matched != null && matched.exists()) {
                    Result.success(matched)
                } else {
                    Result.failure(Exception("Downloaded file not found on disk"))
                }
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun cancelDownload(downloadId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(downloadId)
        } catch (e: Exception) {
            // Ignore
        }
    }
}
