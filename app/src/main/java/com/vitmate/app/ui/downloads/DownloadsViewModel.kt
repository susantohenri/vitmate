package com.vitmate.app.ui.downloads

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.vitmate.app.R
import com.vitmate.app.data.model.DownloadItem
import com.vitmate.app.data.model.DownloadStatus
import com.vitmate.app.data.model.MediaFormatType
import com.vitmate.app.data.repository.DownloadRepository
import com.vitmate.app.service.DownloadService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

class DownloadsViewModel(
    private val downloadRepository: DownloadRepository
) : ViewModel() {

    val downloads: StateFlow<List<DownloadItem>> = downloadRepository.downloadsFlow

    private val _playingItem = MutableStateFlow<DownloadItem?>(null)
    val playingItem: StateFlow<DownloadItem?> = _playingItem.asStateFlow()

    fun onPlay(item: DownloadItem) {
        if (item.status == DownloadStatus.COMPLETED && !item.localFilePath.isNullOrBlank()) {
            _playingItem.value = item
        }
    }

    fun onDismissPlayer() {
        _playingItem.value = null
    }

    fun onShare(context: Context, item: DownloadItem) {
        val path = item.localFilePath ?: return
        val file = File(path)
        if (!file.exists()) return

        try {
            val uri = FileProvider.getUriForFile(
                context,
                "com.vitmate.app.fileprovider",
                file
            )

            val mimeType = if (item.formatType == MediaFormatType.MP3) "audio/*" else "video/*"

            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = mimeType
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }

            val chooserTitle = context.getString(R.string.share_media_title, item.title)
            val chooser = Intent.createChooser(shareIntent, chooserTitle)
            chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun onDownloadAgain(context: Context, item: DownloadItem) {
        downloadRepository.markQueued(item.id)
        DownloadService.startDownload(
            context = context,
            itemId = item.id,
            url = item.url,
            title = item.title,
            formatType = item.formatType,
            qualityId = item.qualityId ?: item.quality
        )
    }

    fun onDelete(item: DownloadItem) {
        downloadRepository.deleteItem(item.id, deleteFile = true)
    }
}

class DownloadsViewModelFactory(
    private val downloadRepository: DownloadRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(DownloadsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return DownloadsViewModel(downloadRepository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
