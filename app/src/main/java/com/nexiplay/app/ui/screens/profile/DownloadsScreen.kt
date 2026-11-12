package com.nexiplay.app.ui.screens.profile

import android.app.DownloadManager
import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.navigation.NavController
import com.nexiplay.app.data.repository.DownloadItem
import com.nexiplay.app.data.repository.DownloadProgress
import com.nexiplay.app.data.repository.DownloadRepository
import com.nexiplay.app.data.repository.DownloadStatus
import com.nexiplay.app.data.repository.SettingsRepository
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LocalVideoFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val durationMs: Long,
    val contentUri: String,
)

private const val MIN_DURATION_MS = 22 * 60 * 1000L

private fun scanDeviceVideos(context: android.content.Context): List<LocalVideoFile> {
    val result = mutableListOf<LocalVideoFile>()
    val collection = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
        android.provider.MediaStore.Video.Media.getContentUri(android.provider.MediaStore.VOLUME_EXTERNAL)
    } else {
        android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    }

    val projection = arrayOf(
        android.provider.MediaStore.Video.Media._ID,
        android.provider.MediaStore.Video.Media.DISPLAY_NAME,
        android.provider.MediaStore.Video.Media.DATA,
        android.provider.MediaStore.Video.Media.SIZE,
        android.provider.MediaStore.Video.Media.DATE_MODIFIED,
        android.provider.MediaStore.Video.Media.DURATION,
    )

    val selection = "${android.provider.MediaStore.Video.Media.DURATION} >= ?"
    val selectionArgs = arrayOf(MIN_DURATION_MS.toString())
    val sortOrder = "${android.provider.MediaStore.Video.Media.DATE_MODIFIED} DESC"

    try {
        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media._ID)
            val nameCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DISPLAY_NAME)
            val dataCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATA)
            val sizeCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.SIZE)
            val dateCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DATE_MODIFIED)
            val durCol = cursor.getColumnIndexOrThrow(android.provider.MediaStore.Video.Media.DURATION)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val name = cursor.getString(nameCol) ?: "Unknown"
                val path = cursor.getString(dataCol) ?: ""
                val size = cursor.getLong(sizeCol)
                val date = cursor.getLong(dateCol) * 1000
                val duration = cursor.getLong(durCol)

                val contentUri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id,
                ).toString()

                result.add(
                    LocalVideoFile(
                        name = name,
                        path = path,
                        size = size,
                        lastModified = date,
                        durationMs = duration,
                        contentUri = contentUri,
                    )
                )
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    // Scan SAF Folder (and its NexiPlay subfolder) if SD Card is enabled
    try {
        val settings = SettingsRepository(context)
        if (settings.sdCardUri != null) {
            val treeUri = Uri.parse(settings.sdCardUri)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            val dirs = listOfNotNull(docFile, docFile?.findFile("NexiPlay")?.takeIf { it.isDirectory })
            dirs.forEach { dir ->
                dir.listFiles().forEach { child ->
                    val name = child.name ?: ""
                    if (child.isFile && (name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true))) {
                        result.add(
                            LocalVideoFile(
                                name = name,
                                path = child.uri.toString(),
                                size = child.length(),
                                lastModified = child.lastModified(),
                                durationMs = 0L,
                                contentUri = child.uri.toString(),
                            )
                        )
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }

    return result
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadsScreen(navController: NavController) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val downloadRepo = remember { DownloadRepository(context) }

    val downloads by downloadRepo.downloadsFlow.collectAsState()
    var legacyProgressMap by remember { mutableStateOf(emptyMap<Long, DownloadProgress>()) }
    var localVideos by remember { mutableStateOf(emptyList<LocalVideoFile>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var isLoadingLocalVideos by remember { mutableStateOf(true) }

    val mediaPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            coroutineScope.launch {
                isLoadingLocalVideos = true
                delay(300)
                try {
                    localVideos = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { scanDeviceVideos(context) }
                } catch (_: Exception) {}
                isLoadingLocalVideos = false
            }
        } else {
            isLoadingLocalVideos = false
        }
    }

    fun requestMediaPermissionAndScan() {
        val permission = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            android.Manifest.permission.READ_MEDIA_VIDEO
        } else {
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (androidx.core.content.ContextCompat.checkSelfPermission(context, permission) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            coroutineScope.launch {
                isLoadingLocalVideos = true
                delay(250)
                try {
                    localVideos = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { scanDeviceVideos(context) }
                } catch (_: Exception) {}
                isLoadingLocalVideos = false
            }
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    LaunchedEffect(Unit) {
        requestMediaPermissionAndScan()
    }

    // Only poll legacy DownloadManager rows if any non-engine items exist
    val hasLegacyItems = remember(downloads) { downloads.any { !it.isEngineManaged && it.downloadManagerId > 0L } }
    LaunchedEffect(hasLegacyItems, downloads.size) {
        if (!hasLegacyItems) {
            legacyProgressMap = emptyMap()
            return@LaunchedEffect
        }
        while (true) {
            val newMap = mutableMapOf<Long, DownloadProgress>()
            downloads.filter { !it.isEngineManaged && it.downloadManagerId > 0L }.forEach { dl ->
                downloadRepo.getDownloadProgress(dl.downloadManagerId)?.let { prog ->
                    newMap[dl.downloadManagerId] = prog
                }
            }
            legacyProgressMap = newMap
            delay(1_000L)
        }
    }

    val resolvedProgressMap = remember(downloads, legacyProgressMap) {
        downloads.associate { item ->
            val prog = if (item.isEngineManaged) {
                downloadRepo.toProgress(item)
            } else {
                legacyProgressMap[item.downloadManagerId] ?: downloadRepo.toProgress(item)
            }
            item.id to prog
        }
    }

    val hasFinishedItems = remember(downloads, resolvedProgressMap) {
        downloads.any { item ->
            val p = resolvedProgressMap[item.id]
            p == null || p.isComplete || p.isFailed
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Download Manager", color = themeTextPrimary(), fontWeight = FontWeight.Bold, fontFamily = InterFont)
                        val activeCount = downloads.count { it.isEngineManaged && it.statusEnum.isActive }
                        if (activeCount > 0) {
                            val totalBps = downloads.filter { it.isEngineManaged && it.statusEnum == DownloadStatus.DOWNLOADING }
                                .sumOf { it.speedBytesPerSec }
                            Text(
                                if (totalBps > 0L) "$activeCount active • ${formatSize(totalBps)}/s (runs in background)"
                                else "$activeCount active in background",
                                color = CoinGold,
                                fontSize = 11.sp,
                                fontFamily = InterFont
                            )
                        }
                    }
                },
                navigationIcon = {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .bounceClick(onClick = { navController.popBackStack() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                actions = {
                    if (selectedTab == 0 && hasFinishedItems) {
                        TextButton(onClick = { downloadRepo.clearFinished() }) {
                            Text("Clear Done", color = NexiRed, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFont)
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .bounceClick(onClick = { requestMediaPermissionAndScan() }),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Refresh, "Refresh", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeBg())
            )
        },
        containerColor = themeBg()
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = themeCard(),
                contentColor = NexiRed,
                divider = {},
                indicator = {},
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = {
                        Text(
                            "App Downloads (${downloads.size})",
                            fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 0) NexiRed else themeTextSecondary(),
                            fontSize = 13.sp,
                            fontFamily = InterFont
                        )
                    }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = {
                        selectedTab = 1
                        requestMediaPermissionAndScan()
                    },
                    text = {
                        Text(
                            "Device Videos (${localVideos.size})",
                            fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal,
                            color = if (selectedTab == 1) NexiRed else themeTextSecondary(),
                            fontSize = 13.sp,
                            fontFamily = InterFont
                        )
                    }
                )
            }

            when (selectedTab) {
                0 -> AppDownloadsTab(
                    downloads = downloads,
                    progressMap = resolvedProgressMap,
                    downloadRepo = downloadRepo,
                    navController = navController,
                )
                1 -> DeviceVideosTab(
                    isLoading = isLoadingLocalVideos,
                    localVideos = localVideos,
                    navController = navController,
                )
            }
        }
    }
}

@Composable
private fun AppDownloadsTab(
    downloads: List<DownloadItem>,
    progressMap: Map<String, DownloadProgress?>,
    downloadRepo: DownloadRepository,
    navController: NavController,
) {
    val context = LocalContext.current
    val activeItems = downloads.filter { it.isEngineManaged && it.statusEnum.isActive }
    val pausedOrRetryable = downloads.filter { it.isEngineManaged && it.statusEnum == DownloadStatus.PAUSED }

    if (downloads.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(24.dp)) {
                Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(64.dp), tint = themeTextTertiary())
                Spacer(Modifier.height(16.dp))
                Text("No downloads yet", color = themeTextPrimary(), fontSize = 16.sp, fontFamily = InterFont, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "Background-safe downloads stay running even when you leave NexiPlay.",
                    color = themeTextTertiary(),
                    fontSize = 12.sp,
                    fontFamily = InterFont,
                )
            }
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (activeItems.isNotEmpty() || pausedOrRetryable.isNotEmpty()) {
            item(key = "batch_controls") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(themeCard(), RoundedCornerShape(12.dp))
                        .border(1.dp, themeBorder(), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Bolt, null, tint = CoinGold, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (activeItems.isNotEmpty()) "Background Engine Active" else "${pausedOrRetryable.size} Paused",
                            color = themeTextPrimary(),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            fontFamily = InterFont
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (activeItems.isNotEmpty()) {
                            SmallActionChip("Pause All", Icons.Default.Pause, CoinGold) {
                                downloadRepo.pauseAll()
                            }
                        }
                        if (pausedOrRetryable.isNotEmpty()) {
                            SmallActionChip("Resume All", Icons.Default.PlayArrow, SuccessGreen) {
                                downloadRepo.resumeAll()
                            }
                        }
                    }
                }
            }
        }

        items(downloads, key = { it.id }) { item ->
            val progress = progressMap[item.id]
            DownloadItemCard(
                item = item,
                progress = progress,
                onPause = { downloadRepo.pauseDownload(item.id) },
                onResume = { downloadRepo.resumeDownload(item.id) },
                onRetry = { downloadRepo.retryDownload(item.id) },
                onOpenMirror = { mirrorUrl ->
                    val encoded = URLEncoder.encode(mirrorUrl, "UTF-8")
                    navController.navigate("webview/$encoded")
                },
                onDelete = { downloadRepo.removeDownload(item) },
                onPlay = { localUri ->
                    try {
                        val encoded = URLEncoder.encode(localUri, "UTF-8")
                        navController.navigate("player/$encoded")
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                },
                onShare = { localUri ->
                    try {
                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                            type = item.mimeType.ifBlank { "video/*" }
                            putExtra(Intent.EXTRA_STREAM, Uri.parse(localUri))
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(shareIntent, "Share Video"))
                    } catch (_: Exception) {}
                }
            )
        }
    }
}

@Composable
private fun SmallActionChip(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(tint.copy(alpha = 0.14f))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(14.dp))
        Spacer(Modifier.width(4.dp))
        Text(label, color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
    }
}

@Composable
private fun DeviceVideosTab(
    isLoading: Boolean,
    localVideos: List<LocalVideoFile>,
    navController: NavController,
) {
    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            repeat(6) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(80.dp), cornerRadius = 12)
            }
        }
    } else {
        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
            if (localVideos.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.VideoLibrary, null, modifier = Modifier.size(64.dp), tint = themeTextTertiary())
                        Spacer(Modifier.height(16.dp))
                        Text("No video files found", color = themeTextTertiary(), fontSize = 16.sp, fontFamily = InterFont, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Videos longer than 22 min or saved to SD Card appear here", color = themeTextTertiary(), fontSize = 12.sp, fontFamily = InterFont)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(localVideos, key = { it.contentUri + it.name }) { video ->
                        LocalVideoCard(video = video, onPlay = {
                            try {
                                val encoded = URLEncoder.encode(video.contentUri, "UTF-8")
                                navController.navigate("player/$encoded")
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        })
                    }
                }
            }
        }
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%d:%02d", minutes, seconds)
    }
}

@Composable
private fun LocalVideoCard(video: LocalVideoFile, onPlay: () -> Unit) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = onPlay)
            .background(themeCard(), RoundedCornerShape(12.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(Color.Black),
            contentAlignment = Alignment.Center
        ) {
            coil3.compose.AsyncImage(
                model = video.contentUri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            if (video.durationMs > 0L) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(4.dp)
                        .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                ) {
                    Text(
                        formatDuration(video.durationMs),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFont
                    )
                }
            }
            Icon(
                Icons.Default.PlayCircle, null,
                tint = Color.White.copy(alpha = 0.8f),
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                video.name,
                color = themeTextPrimary(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                fontFamily = InterFont,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(formatSize(video.size), color = themeTextSecondary(), fontSize = 11.sp, fontFamily = InterFont)
                Text("•", color = themeTextTertiary(), fontSize = 11.sp, fontFamily = InterFont)
                Text(formatter.format(Date(video.lastModified)), color = themeTextTertiary(), fontSize = 11.sp, fontFamily = InterFont)
            }
        }
    }
}

@Composable
private fun DownloadItemCard(
    item: DownloadItem,
    progress: DownloadProgress?,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRetry: () -> Unit,
    onOpenMirror: (String) -> Unit,
    onDelete: () -> Unit,
    onPlay: (String) -> Unit,
    onShare: (String) -> Unit,
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val engineStatus = progress?.engineStatus
    val status = progress?.status
    val isResolving = engineStatus == DownloadStatus.RESOLVING
    val isRunning = engineStatus == DownloadStatus.DOWNLOADING || status == DownloadManager.STATUS_RUNNING
    val isPending = engineStatus == DownloadStatus.QUEUED || status == DownloadManager.STATUS_PENDING
    val isPaused = engineStatus == DownloadStatus.PAUSED || status == DownloadManager.STATUS_PAUSED
    val isComplete = engineStatus == DownloadStatus.COMPLETED || status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed = engineStatus == DownloadStatus.FAILED || engineStatus == DownloadStatus.CANCELLED || status == DownloadManager.STATUS_FAILED

    val animatedProgress by animateFloatAsState(
        targetValue = progress?.progressPercent ?: 0f,
        label = "download_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = {
                if (isComplete && progress?.localUri != null) {
                    onPlay(progress.localUri)
                }
            })
            .background(themeCard(), RoundedCornerShape(14.dp))
            .border(1.dp, themeBorder(), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        when {
                            isComplete -> SuccessGreen.copy(alpha = 0.15f)
                            isFailed -> NexiRed.copy(alpha = 0.15f)
                            isRunning || isPending || isResolving -> CoinGold.copy(alpha = 0.15f)
                            isPaused -> InfoBlue.copy(alpha = 0.15f)
                            else -> themeTextTertiary().copy(alpha = 0.15f)
                        },
                        RoundedCornerShape(10.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    when {
                        isComplete -> Icons.Default.CheckCircle
                        isFailed -> Icons.Default.Error
                        isResolving -> Icons.Default.CloudSync
                        isRunning -> Icons.Default.Downloading
                        isPending -> Icons.Default.HourglassTop
                        isPaused -> Icons.Default.PauseCircle
                        else -> Icons.Default.FileDownload
                    },
                    null,
                    tint = when {
                        isComplete -> SuccessGreen
                        isFailed -> NexiRed
                        isRunning || isPending || isResolving -> CoinGold
                        isPaused -> InfoBlue
                        else -> themeTextTertiary()
                    },
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    item.title,
                    color = themeTextPrimary(),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 14.sp,
                    fontFamily = InterFont,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    formatter.format(Date(item.timestamp)),
                    color = themeTextTertiary(),
                    fontSize = 11.sp,
                    fontFamily = InterFont
                )
            }

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .bounceClick(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Remove", tint = themeTextTertiary(), modifier = Modifier.size(18.dp))
            }
        }

        // Progress bar and controls for active / paused tasks
        if (isResolving || isRunning || isPending || isPaused) {
            Spacer(Modifier.height(10.dp))

            if (isResolving || (isRunning && (progress?.totalBytes ?: -1L) <= 0L)) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isPaused) InfoBlue else CoinGold,
                    trackColor = themeTextTertiary().copy(alpha = 0.25f),
                )
            } else {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = if (isPaused) InfoBlue else NexiRed,
                    trackColor = themeTextTertiary().copy(alpha = 0.25f),
                )
            }

            Spacer(Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val leftStatus = when {
                    isResolving -> "Preparing cloud link..."
                    isPending -> "Queued..."
                    isPaused -> "Paused (${(animatedProgress * 100).toInt()}%)"
                    (progress?.totalBytes ?: -1L) > 0L -> "${(animatedProgress * 100).toInt()}%"
                    else -> "Downloading..."
                }
                Text(
                    leftStatus,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFont,
                    color = if (isPaused) InfoBlue else themeTextSecondary()
                )

                val rightInfo = buildList {
                    if (progress != null) {
                        if (progress.bytesDownloaded > 0L) {
                            if (progress.totalBytes > 0L) {
                                add("${formatSize(progress.bytesDownloaded)} / ${formatSize(progress.totalBytes)}")
                            } else {
                                add(formatSize(progress.bytesDownloaded))
                            }
                        }
                        if (isRunning && progress.speedBytesPerSec > 0L) {
                            add("${formatSize(progress.speedBytesPerSec)}/s")
                        }
                        val eta = if (isRunning) formatEtaShort(progress.etaSeconds) else null
                        if (eta != null) add(eta)
                    }
                }.joinToString(" • ")

                if (rightInfo.isNotBlank()) {
                    Text(
                        rightInfo,
                        fontSize = 11.sp,
                        fontFamily = InterFont,
                        color = themeTextSecondary()
                    )
                }
            }

            if (!progress?.errorMessage.isNullOrBlank() && isRunning) {
                Spacer(Modifier.height(4.dp))
                Text(
                    progress!!.errorMessage!!,
                    fontSize = 11.sp,
                    color = CoinGold,
                    fontFamily = InterFont
                )
            }

            if (item.isEngineManaged) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isPaused) {
                        SmallActionChip("Resume", Icons.Default.PlayArrow, SuccessGreen, onResume)
                    } else {
                        SmallActionChip("Pause", Icons.Default.Pause, CoinGold, onPause)
                    }
                    Spacer(Modifier.width(8.dp))
                    SmallActionChip("Cancel", Icons.Default.Cancel, NexiRed, onDelete)
                }
            }
        }

        if (isComplete) {
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val sizeLabel = if ((progress?.bytesDownloaded ?: 0L) > 0L) {
                    "Complete • ${formatSize(progress!!.bytesDownloaded)}"
                } else {
                    "Download Complete"
                }
                Text(sizeLabel, fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontFamily = InterFont)

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (progress?.localUri != null) {
                        if (progress.localUri.startsWith("content://")) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(themeSurface())
                                    .bounceClick(onClick = { onShare(progress.localUri) }),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Share, "Share", tint = themeTextSecondary(), modifier = Modifier.size(16.dp))
                            }
                        }
                        Box(
                            modifier = Modifier
                                .height(32.dp)
                                .bounceClick(onClick = { onPlay(progress.localUri) })
                                .background(NexiRed, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PlayArrow, null, tint = Color.White, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("Play", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFamily = InterFont)
                            }
                        }
                    }
                }
            }
        }

        if (isFailed) {
            Spacer(Modifier.height(10.dp))
            val reasonText = progress?.errorMessage?.takeIf { it.isNotBlank() } ?: "Download failed"
            Text(reasonText, fontSize = 12.sp, color = NexiRed, fontWeight = FontWeight.Medium, fontFamily = InterFont)
            if (item.isEngineManaged) {
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    val cloudUrl = progress?.cloudFallbackUrl
                    if (!cloudUrl.isNullOrBlank()) {
                        SmallActionChip("Open Mirror", Icons.Default.OpenInNew, InfoBlue) {
                            onOpenMirror(cloudUrl)
                        }
                    } else {
                        SmallActionChip("Retry", Icons.Default.Refresh, CoinGold, onRetry)
                    }
                }
            }
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}

private fun formatEtaShort(seconds: Long): String? {
    if (seconds <= 0L) return null
    val h = seconds / 3600L
    val m = (seconds % 3600L) / 60L
    val s = seconds % 60L
    return when {
        h > 0L -> "${h}h ${m}m left"
        m > 0L -> "${m}m ${s}s left"
        else -> "${s}s left"
    }
}
