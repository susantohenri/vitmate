package com.henrisusanto.vitmate.ui.home

import android.app.Activity
import android.content.ClipboardManager
import android.content.Context
import com.henrisusanto.vitmate.util.findActivity
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.henrisusanto.vitmate.R
import com.henrisusanto.vitmate.data.model.MediaFormatType
import com.henrisusanto.vitmate.data.model.VideoMetadata
import com.henrisusanto.vitmate.ui.theme.BrandAccent
import com.henrisusanto.vitmate.ui.theme.BrandPrimary
import com.henrisusanto.vitmate.ui.theme.StatusError
import com.henrisusanto.vitmate.ui.theme.StatusWarning
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToDownloads: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val urlInput by viewModel.urlInput.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    val selectedFormat by viewModel.selectedFormat.collectAsState()
    val selectedQuality by viewModel.selectedQuality.collectAsState()
    val isTermsAcknowledged by viewModel.isTermsAcknowledged.collectAsState()
    val isAckChecked by viewModel.isAcknowledgementChecked.collectAsState()

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // App Title Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
            ) {
                Text(
                    text = stringResource(R.string.app_short_name),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.app_tagline),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                )
            }

            // Disclaimer Banner (Section 13)
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = BrandAccent,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = stringResource(R.string.home_disclaimer),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // URL Input Field (Section 2)
            OutlinedTextField(
                value = urlInput,
                onValueChange = { viewModel.onUrlChanged(it) },
                placeholder = { Text(stringResource(R.string.url_input_hint)) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                singleLine = true,
                trailingIcon = {
                    Row {
                        if (urlInput.isNotEmpty()) {
                            IconButton(onClick = { viewModel.onClearUrl() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = stringResource(R.string.clear_button)
                                )
                            }
                        }
                        IconButton(
                            onClick = {
                                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                if (!clip.isNullOrBlank()) {
                                    viewModel.onPasteUrl(clip)
                                }
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = stringResource(R.string.paste_button),
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            // Primary Action Button (Section 3: "Watch Ad & Continue")
            val isLoading = uiState is HomeUiState.LoadingAd ||
                    uiState is HomeUiState.CheckingWhitelist ||
                    uiState is HomeUiState.ProcessingMetadata

            Button(
                onClick = {
                    val act = activity ?: context.findActivity()
                    viewModel.startRewardedAdAndProcessFlow(act)
                },
                enabled = urlInput.isNotBlank() && !isLoading,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandPrimary,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        color = androidx.compose.ui.graphics.Color.White,
                        strokeWidth = 2.5.dp
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    val statusText = when (val state = uiState) {
                        is HomeUiState.LoadingAd -> stringResource(state.messageRes)
                        is HomeUiState.CheckingWhitelist -> stringResource(state.messageRes)
                        is HomeUiState.ProcessingMetadata -> stringResource(state.messageRes)
                        else -> "Processing..."
                    }
                    Text(
                        text = statusText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.PlayCircleOutline,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = stringResource(R.string.watch_ad_and_continue),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Error Display Card
            AnimatedVisibility(visible = uiState is HomeUiState.Error) {
                val errorState = uiState as? HomeUiState.Error
                if (errorState != null) {
                    val errorMessage = if (errorState.messageRes != null) {
                        stringResource(errorState.messageRes)
                    } else {
                        errorState.customMessage ?: stringResource(R.string.error_download_failed)
                    }

                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }
            }

            // Metadata Preview Card (Section 5, 6, 7)
            AnimatedVisibility(visible = uiState is HomeUiState.MetadataReady) {
                val readyState = uiState as? HomeUiState.MetadataReady
                if (readyState != null) {
                    MetadataPreviewCard(
                        metadata = readyState.metadata,
                        selectedFormat = selectedFormat,
                        onFormatSelected = { viewModel.onFormatSelected(it) },
                        selectedQuality = selectedQuality,
                        onQualitySelected = { viewModel.onQualitySelected(it) },
                        isTermsAcknowledged = isTermsAcknowledged,
                        isAcknowledgementChecked = isAckChecked,
                        onAcknowledgementChanged = { viewModel.onAcknowledgementChanged(it) },
                        onDownloadClicked = {
                            viewModel.startDownload(context) {
                                scope.launch {
                                    snackbarHostState.showSnackbar(context.getString(R.string.download_added_to_queue))
                                }
                                onNavigateToDownloads()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MetadataPreviewCard(
    metadata: VideoMetadata,
    selectedFormat: MediaFormatType,
    onFormatSelected: (MediaFormatType) -> Unit,
    selectedQuality: com.henrisusanto.vitmate.data.model.QualityOption?,
    onQualitySelected: (com.henrisusanto.vitmate.data.model.QualityOption) -> Unit,
    isTermsAcknowledged: Boolean,
    isAcknowledgementChecked: Boolean,
    onAcknowledgementChanged: (Boolean) -> Unit,
    onDownloadClicked: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Thumbnail & Title Preview
            if (!metadata.thumbnailUrl.isNullOrBlank()) {
                AsyncImage(
                    model = metadata.thumbnailUrl,
                    contentDescription = metadata.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(10.dp))
                )
            }

            Text(
                text = metadata.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // Duration & Estimated Size
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (metadata.durationSeconds > 0) {
                    val minutes = metadata.durationSeconds / 60
                    val seconds = metadata.durationSeconds % 60
                    Text(
                        text = stringResource(R.string.duration_label, String.format("%02d:%02d", minutes, seconds)),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                    )
                }
                val sizeStr = if (metadata.estimatedSizeBytes > 0) {
                    val mb = metadata.estimatedSizeBytes / (1024f * 1024f)
                    String.format("%.1f MB", mb)
                } else {
                    stringResource(R.string.size_unknown)
                }
                Text(
                    text = stringResource(R.string.estimated_size, sizeStr),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }

            // Format Selection: MP4 vs MP3 (Section 6)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = stringResource(R.string.format_label),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = selectedFormat == MediaFormatType.MP4,
                        onClick = { onFormatSelected(MediaFormatType.MP4) },
                        label = { Text(stringResource(R.string.format_mp4)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                    FilterChip(
                        selected = selectedFormat == MediaFormatType.MP3,
                        onClick = { onFormatSelected(MediaFormatType.MP3) },
                        label = { Text(stringResource(R.string.format_mp3)) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            // Video Quality Selection (only shown for MP4 and dynamically exposes available qualities)
            if (selectedFormat == MediaFormatType.MP4 && metadata.videoQualities.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.quality_label),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(metadata.videoQualities) { quality ->
                            FilterChip(
                                selected = selectedQuality?.formatId == quality.formatId,
                                onClick = { onQualitySelected(quality) },
                                label = { Text(quality.label) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.secondary,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSecondary
                                )
                            )
                        }
                    }
                }
            }

            // First-download Acknowledgement Checkbox (Section 7)
            if (!isTermsAcknowledged) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onAcknowledgementChanged(!isAcknowledgementChecked) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isAcknowledgementChecked,
                        onCheckedChange = { onAcknowledgementChanged(it) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary
                        )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.first_download_ack),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            // Download Button (disabled until acknowledgement checkbox is checked if first-time)
            Button(
                onClick = onDownloadClicked,
                enabled = isTermsAcknowledged || isAcknowledgementChecked,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrandAccent,
                    contentColor = androidx.compose.ui.graphics.Color.White
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.download_button),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}
