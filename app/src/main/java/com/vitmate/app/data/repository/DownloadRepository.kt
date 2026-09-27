package com.vitmate.app.data.repository

import android.content.Context
import com.vitmate.app.data.model.DownloadItem
import com.vitmate.app.data.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class DownloadRepository(private val context: Context) {
    private val scope = CoroutineScope(Dispatchers.IO)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
    }

    private val historyFile: File by lazy {
        File(context.filesDir, "downloads_history.json")
    }

    private val _downloadsFlow = MutableStateFlow<List<DownloadItem>>(emptyList())
    val downloadsFlow: StateFlow<List<DownloadItem>> = _downloadsFlow.asStateFlow()

    init {
        loadHistory()
    }

    private fun loadHistory() {
        scope.launch {
            if (historyFile.exists()) {
                try {
                    val content = historyFile.readText()
                    if (content.isNotBlank()) {
                        val items = json.decodeFromString<List<DownloadItem>>(content)
                        // If any were in-progress when killed, mark as failed
                        val adjusted = items.map { item ->
                            if (item.status == DownloadStatus.DOWNLOADING || item.status == DownloadStatus.QUEUED) {
                                item.copy(status = DownloadStatus.FAILED, errorMessage = "Interrupted")
                            } else {
                                item
                            }
                        }
                        _downloadsFlow.value = adjusted
                    }
                } catch (e: Exception) {
                    _downloadsFlow.value = emptyList()
                }
            }
        }
    }

    private fun persistHistory() {
        val currentItems = _downloadsFlow.value
        scope.launch {
            try {
                val content = json.encodeToString(currentItems)
                historyFile.writeText(content)
            } catch (e: Exception) {
                // Ignore file write exceptions
            }
        }
    }

    fun addItem(item: DownloadItem) {
        val list = _downloadsFlow.value.toMutableList()
        // Prepend new item
        list.add(0, item)
        _downloadsFlow.value = list
        persistHistory()
    }

    fun updateProgress(id: String, progress: Int, etaSeconds: Long = 0L) {
        val list = _downloadsFlow.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.DOWNLOADING,
                    progress = progress,
                    etaSeconds = etaSeconds
                )
            } else {
                item
            }
        }
        _downloadsFlow.value = list
        // don't disk-persist every tick to avoid thrashing, only significant updates
    }

    fun markCompleted(id: String, filePath: String, fileSize: Long) {
        val list = _downloadsFlow.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.COMPLETED,
                    progress = 100,
                    localFilePath = filePath,
                    fileSize = fileSize,
                    errorMessage = null
                )
            } else {
                item
            }
        }
        _downloadsFlow.value = list
        persistHistory()
    }

    fun markFailed(id: String, errorMessage: String) {
        val list = _downloadsFlow.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.FAILED,
                    errorMessage = errorMessage
                )
            } else {
                item
            }
        }
        _downloadsFlow.value = list
        persistHistory()
    }

    fun markQueued(id: String) {
        val list = _downloadsFlow.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.QUEUED,
                    progress = 0,
                    errorMessage = null
                )
            } else {
                item
            }
        }
        _downloadsFlow.value = list
        persistHistory()
    }

    fun markCancelled(id: String) {
        val list = _downloadsFlow.value.map { item ->
            if (item.id == id) {
                item.copy(
                    status = DownloadStatus.CANCELLED,
                    errorMessage = null
                )
            } else {
                item
            }
        }
        _downloadsFlow.value = list
        persistHistory()
    }

    fun deleteItem(id: String, deleteFile: Boolean = true) {
        val current = _downloadsFlow.value
        val target = current.find { it.id == id }
        if (deleteFile && target?.localFilePath != null) {
            try {
                val f = File(target.localFilePath)
                if (f.exists()) f.delete()
            } catch (e: Exception) {
                // Ignore
            }
        }
        _downloadsFlow.value = current.filter { it.id != id }
        persistHistory()
    }

    fun getItem(id: String): DownloadItem? {
        return _downloadsFlow.value.find { it.id == id }
    }
}
