package com.nexiplay.app.ui.screens.profile

import android.app.DownloadManager
import android.os.Environment
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.data.repository.DownloadItem
import com.nexiplay.app.data.repository.DownloadProgress
import com.nexiplay.app.data.repository.DownloadRepository
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.documentfile.provider.DocumentFile
import com.nexiplay.app.data.repository.SettingsRepository
import java.io.File
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// Represents a video file found on device
data class LocalVideoFile(
    val name: String,
    val path: String,
    val size: Long,
    val lastModified: Long,
    val durationMs: Long,
    val contentUri: String, // content:// URI for playback
)

private const val MIN_DURATION_MS = 22 * 60 * 1000L // 22 minutes in milliseconds

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
                val date = cursor.getLong(dateCol) * 1000 // seconds to millis
                val duration = cursor.getLong(durCol)

                val contentUri = android.content.ContentUris.withAppendedId(
                    android.provider.MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id
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
    
    // Scan SAF Folder if SD Card is enabled
    try {
        val settings = SettingsRepository(context)
        if (settings.sdCardUri != null) {
            val treeUri = android.net.Uri.parse(settings.sdCardUri)
            val docFile = DocumentFile.fromTreeUri(context, treeUri)
            if (docFile != null && docFile.isDirectory) {
                docFile.listFiles().forEach { child ->
                    val name = child.name ?: ""
                    if (child.isFile && (name.endsWith(".mp4", ignoreCase = true) || name.endsWith(".mkv", ignoreCase = true))) {
                        result.add(
                            LocalVideoFile(
                                name = name,
                                path = child.uri.toString(),
                                size = child.length(),
                                lastModified = child.lastModified(),
                                durationMs = 0L, // Duration extraction is harder from DocumentFile, defaulting to 0
                                contentUri = child.uri.toString()
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
    
    var downloads by remember { mutableStateOf(emptyList<DownloadItem>()) }
    var progressMap by remember { mutableStateOf(emptyMap<Long, DownloadProgress>()) }
    var localVideos by remember { mutableStateOf(emptyList<LocalVideoFile>()) }
    var selectedTab by remember { mutableIntStateOf(0) }
    var hasMediaPermission by remember { mutableStateOf(false) }

    var isLoadingDownloads by remember { mutableStateOf(true) }
    var isLoadingLocalVideos by remember { mutableStateOf(true) }

    val mediaPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasMediaPermission = granted
        if (granted) {
            coroutineScope.launch {
                isLoadingLocalVideos = true
                delay(400) // Shimmer preview
                try { 
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { scanDeviceVideos(context) }
                    localVideos = result 
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
            android.content.pm.PackageManager.PERMISSION_GRANTED) {
            hasMediaPermission = true
            coroutineScope.launch {
                isLoadingLocalVideos = true
                delay(400) // Shimmer preview
                try { 
                    val result = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { scanDeviceVideos(context) }
                    localVideos = result 
                } catch (_: Exception) {}
                isLoadingLocalVideos = false
            }
        } else {
            mediaPermissionLauncher.launch(permission)
        }
    }

    // Initial load
    LaunchedEffect(Unit) {
        isLoadingDownloads = true
        delay(400) // Shimmer preview
        downloads = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { downloadRepo.getDownloads() }
        isLoadingDownloads = false
        
        requestMediaPermissionAndScan()
    }

    // Poll progress every 1 second for active downloads
    LaunchedEffect(downloads) {
        while (true) {
            val newMap = mutableMapOf<Long, DownloadProgress>()
            for (dl in downloads) {
                val progress = downloadRepo.getDownloadProgress(dl.downloadManagerId)
                if (progress != null) {
                    newMap[dl.downloadManagerId] = progress
                }
            }
            progressMap = newMap
            delay(1000L)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Downloads", color = themeTextPrimary(), fontWeight = FontWeight.Bold, fontFamily = InterFont) },
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
                    // Refresh button to re-scan
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .bounceClick(onClick = {
                                coroutineScope.launch {
                                    isLoadingDownloads = true
                                    delay(400)
                                    downloads = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { downloadRepo.getDownloads() }
                                    isLoadingDownloads = false
                                }
                                requestMediaPermissionAndScan()
                            }),
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
            // Tab selector
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
                            "App Downloads",
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
                            "Device Videos",
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
                    isLoading = isLoadingDownloads,
                    downloads = downloads, 
                    progressMap = progressMap, 
                    downloadRepo = downloadRepo, 
                    navController = navController
                ) { 
                    coroutineScope.launch {
                        isLoadingDownloads = true
                        delay(400)
                        downloads = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { downloadRepo.getDownloads() }
                        isLoadingDownloads = false
                    }
                }
                1 -> DeviceVideosTab(
                    isLoading = isLoadingLocalVideos,
                    localVideos = localVideos, 
                    navController = navController
                )
            }
        }
    }
}

@Composable
private fun AppDownloadsTab(
    isLoading: Boolean,
    downloads: List<DownloadItem>,
    progressMap: Map<Long, DownloadProgress>,
    downloadRepo: DownloadRepository,
    navController: NavController,
    onRefresh: () -> Unit
) {
    if (isLoading) {
        Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(5) {
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(120.dp), cornerRadius = 12)
            }
        }
    } else {
        AnimatedVisibility(visible = true, enter = fadeIn(), exit = fadeOut()) {
            if (downloads.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FileDownload, null, modifier = Modifier.size(64.dp), tint = themeTextTertiary())
                        Spacer(Modifier.height(16.dp))
                        Text("No downloads yet", color = themeTextTertiary(), fontSize = 16.sp, fontFamily = InterFont, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(4.dp))
                        Text("Downloads from WebView will appear here", color = themeTextTertiary(), fontSize = 12.sp, fontFamily = InterFont)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(downloads, key = { it.id }) { item ->
                        val progress = progressMap[item.downloadManagerId]
                        DownloadItemCard(
                            item = item,
                            progress = progress,
                            onDelete = {
                                downloadRepo.removeDownload(item)
                                onRefresh()
                            },
                            onPlay = { localUri ->
                                try {
                                    val encoded = URLEncoder.encode(localUri, "UTF-8")
                                    navController.navigate("player/$encoded")
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DeviceVideosTab(
    isLoading: Boolean,
    localVideos: List<LocalVideoFile>, 
    navController: NavController
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
                        Text("Only videos longer than 22 min are shown", color = themeTextTertiary(), fontSize = 12.sp, fontFamily = InterFont)
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(localVideos, key = { it.path }) { video ->
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
        // Thumbnail
        Box(
            modifier = Modifier
                .width(100.dp)
                .height(60.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(androidx.compose.ui.graphics.Color.Black),
            contentAlignment = Alignment.Center
        ) {
            coil3.compose.AsyncImage(
                model = video.contentUri,
                contentDescription = null,
                contentScale = androidx.compose.ui.layout.ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Duration badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(4.dp)
                    .background(
                        androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.75f),
                        RoundedCornerShape(4.dp)
                    )
                    .padding(horizontal = 5.dp, vertical = 2.dp)
            ) {
                Text(
                    formatDuration(video.durationMs),
                    color = androidx.compose.ui.graphics.Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont
                )
            }
            // Play overlay
            Icon(
                Icons.Default.PlayCircle, null,
                tint = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f),
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
    onDelete: () -> Unit,
    onPlay: (String) -> Unit
) {
    val formatter = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }

    val status = progress?.status
    val isRunning = status == DownloadManager.STATUS_RUNNING
    val isPending = status == DownloadManager.STATUS_PENDING
    val isPaused = status == DownloadManager.STATUS_PAUSED
    val isComplete = status == DownloadManager.STATUS_SUCCESSFUL
    val isFailed = status == DownloadManager.STATUS_FAILED

    val animatedProgress by animateFloatAsState(
        targetValue = progress?.progressPercent ?: 0f,
        label = "download_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .bounceClick(onClick = {
                if (isComplete && progress?.localUri != null) {
                    onPlay(progress.localUri!!)
                }
            })
            .background(themeCard(), RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status icon
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        when {
                            isComplete -> SuccessGreen.copy(alpha = 0.15f)
                            isFailed -> NexiRed.copy(alpha = 0.15f)
                            isRunning || isPending -> CoinGold.copy(alpha = 0.15f)
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
                        isRunning -> Icons.Default.Downloading
                        isPending -> Icons.Default.HourglassTop
                        isPaused -> Icons.Default.Pause
                        else -> Icons.Default.FileDownload
                    },
                    null,
                    tint = when {
                        isComplete -> SuccessGreen
                        isFailed -> NexiRed
                        isRunning || isPending -> CoinGold
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

            // Delete button
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .bounceClick(onClick = onDelete),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Close, "Remove", tint = themeTextTertiary(), modifier = Modifier.size(18.dp))
            }
        }

        // Progress bar (for active downloads)
        if (isRunning || isPending || isPaused) {
            Spacer(Modifier.height(10.dp))

            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = if (isPaused) CoinGold else NexiRed,
                trackColor = themeTextTertiary().copy(alpha = 0.3f),
            )

            Spacer(Modifier.height(6.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    when {
                        isPending -> "Waiting..."
                        isPaused -> "Paused"
                        else -> "${(animatedProgress * 100).toInt()}%"
                    },
                    fontSize = 11.sp,
                    fontFamily = InterFont,
                    color = themeTextSecondary()
                )
                if (progress != null && progress.totalBytes > 0) {
                    Text(
                        "${formatSize(progress.bytesDownloaded)} / ${formatSize(progress.totalBytes)}",
                        fontSize = 11.sp,
                        fontFamily = InterFont,
                        color = themeTextSecondary()
                    )
                }
            }
        }

        // Status label for complete/failed
        if (isComplete) {
            Spacer(Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("✅ Download Complete", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.SemiBold, fontFamily = InterFont)
                if (progress?.localUri != null) {
                    Box(
                        modifier = Modifier
                            .height(32.dp)
                            .bounceClick(onClick = { onPlay(progress.localUri!!) })
                            .background(NexiRed, RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.PlayArrow, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Play", color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp, fontFamily = InterFont)
                        }
                    }
                }
            }
        }
        if (isFailed) {
            Spacer(Modifier.height(8.dp))
            Text("❌ Download Failed", fontSize = 12.sp, color = NexiRed, fontWeight = FontWeight.SemiBold, fontFamily = InterFont)
        }
    }
}

private fun formatSize(bytes: Long): String {
    return when {
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> String.format("%.1f KB", bytes / 1024.0)
        bytes < 1024 * 1024 * 1024 -> String.format("%.1f MB", bytes / (1024.0 * 1024.0))
        else -> String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
    }
}
