package com.vitmate.app.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.vitmate.app.MainActivity
import com.vitmate.app.R
import com.vitmate.app.VitmateApp
import com.vitmate.app.data.model.MediaFormatType
import com.vitmate.app.ytdlp.YtDlpHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class DownloadService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val downloadQueue = Channel<DownloadTask>(Channel.UNLIMITED)
    private val activeTasks = ConcurrentHashMap<String, DownloadTask>()
    private val queuedCount = java.util.concurrent.atomic.AtomicInteger(0)

    companion object {
        const val CHANNEL_ID = "vitmate_downloads_channel"
        const val NOTIFICATION_ID_FOREGROUND = 1001

        const val ACTION_START_DOWNLOAD = "com.vitmate.app.action.START_DOWNLOAD"
        const val ACTION_CANCEL_DOWNLOAD = "com.vitmate.app.action.CANCEL_DOWNLOAD"

        const val EXTRA_ITEM_ID = "extra_item_id"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_FORMAT = "extra_format"
        const val EXTRA_QUALITY = "extra_quality"

        fun startDownload(
            context: Context,
            itemId: String,
            url: String,
            title: String,
            formatType: MediaFormatType,
            qualityId: String?
        ) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_START_DOWNLOAD
                putExtra(EXTRA_ITEM_ID, itemId)
                putExtra(EXTRA_URL, url)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_FORMAT, formatType.name)
                putExtra(EXTRA_QUALITY, qualityId)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun cancelDownload(context: Context, itemId: String) {
            val intent = Intent(context, DownloadService::class.java).apply {
                action = ACTION_CANCEL_DOWNLOAD
                putExtra(EXTRA_ITEM_ID, itemId)
            }
            context.startService(intent)
        }
    }

    data class DownloadTask(
        val itemId: String,
        val url: String,
        val title: String,
        val formatType: MediaFormatType,
        val qualityId: String?
    )

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startWorkerLoop()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_DOWNLOAD -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID) ?: return START_NOT_STICKY
                val url = intent.getStringExtra(EXTRA_URL) ?: return START_NOT_STICKY
                val title = intent.getStringExtra(EXTRA_TITLE) ?: "Media"
                val formatStr = intent.getStringExtra(EXTRA_FORMAT) ?: MediaFormatType.MP4.name
                val formatType = MediaFormatType.valueOf(formatStr)
                val qualityId = intent.getStringExtra(EXTRA_QUALITY)

                val task = DownloadTask(itemId, url, title, formatType, qualityId)
                queuedCount.incrementAndGet()
                startForegroundIfNeeded(task.title)
                serviceScope.launch {
                    downloadQueue.send(task)
                }
            }
            ACTION_CANCEL_DOWNLOAD -> {
                val itemId = intent.getStringExtra(EXTRA_ITEM_ID)
                if (itemId != null) {
                    YtDlpHelper.cancelDownload(itemId)
                    if (activeTasks.remove(itemId) != null) {
                        queuedCount.decrementAndGet()
                    }
                }
            }
        }
        return START_NOT_STICKY
    }

    private fun startWorkerLoop() {
        serviceScope.launch {
            for (task in downloadQueue) {
                activeTasks[task.itemId] = task
                try {
                    processTask(task)
                } finally {
                    activeTasks.remove(task.itemId)
                    val remaining = queuedCount.decrementAndGet()
                    if (remaining <= 0 && activeTasks.isEmpty()) {
                        stopForeground(STOP_FOREGROUND_REMOVE)
                        stopSelf()
                    }
                }
            }
        }
    }

    private suspend fun processTask(task: DownloadTask) {
        val app = application as VitmateApp
        val repo = app.downloadRepository

        // Section 20: Check insufficient storage
        val downloadDir = YtDlpHelper.getDownloadDir(applicationContext)
        if (downloadDir.usableSpace < 25L * 1024L * 1024L) {
            val errorMsg = getString(R.string.error_insufficient_storage)
            repo.markFailed(task.itemId, errorMsg)
            showFailedNotification(task.title, errorMsg)
            return
        }

        repo.updateProgress(task.itemId, 0)
        updateForegroundNotification(task.title, 0)

        var lastProgressUpdateTime = 0L

        val result = YtDlpHelper.executeDownload(
            context = applicationContext,
            downloadId = task.itemId,
            url = task.url,
            title = task.title,
            formatType = task.formatType,
            qualityId = task.qualityId
        ) { progress, etaSeconds ->
            val now = System.currentTimeMillis()
            // Throttle notification & state updates to every 400ms
            if (now - lastProgressUpdateTime > 400 || progress == 100) {
                lastProgressUpdateTime = now
                repo.updateProgress(task.itemId, progress, etaSeconds)
                updateForegroundNotification(task.title, progress)
            }
        }

        result.fold(
            onSuccess = { file ->
                repo.markCompleted(task.itemId, file.absolutePath, file.length())
                showCompletedNotification(task.title, file.name)
            },
            onFailure = { error ->
                val msg = error.message ?: ""
                val errorMsg = when {
                    msg.contains("ENOSPC", ignoreCase = true) || msg.contains("No space left", ignoreCase = true) ->
                        getString(R.string.error_insufficient_storage)
                    msg.contains("unable to connect", ignoreCase = true) ||
                    msg.contains("Connection refused", ignoreCase = true) ||
                    msg.contains("timed out", ignoreCase = true) ||
                    msg.contains("HTTP Error", ignoreCase = true) ->
                        getString(R.string.error_network_failure)
                    else -> getString(R.string.error_download_failed)
                }
                repo.markFailed(task.itemId, errorMsg)
                showFailedNotification(task.title, errorMsg)
            }
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun startForegroundIfNeeded(title: String) {
        val notification = buildProgressNotification(title, 0)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID_FOREGROUND,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification)
        }
    }

    private fun updateForegroundNotification(title: String, progress: Int) {
        val notification = buildProgressNotification(title, progress)
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_FOREGROUND, notification)
    }

    private fun buildProgressNotification(title: String, progress: Int): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_downloading, title))
            .setContentText("$progress%")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setProgress(100, progress, progress == 0)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun showCompletedNotification(title: String, filename: String) {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this,
            title.hashCode(),
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_completed, title))
            .setContentText(filename)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(title.hashCode(), notification)
    }

    private fun showFailedNotification(title: String, reason: String) {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_failed, title))
            .setContentText(reason)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setAutoCancel(true)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(title.hashCode(), notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
