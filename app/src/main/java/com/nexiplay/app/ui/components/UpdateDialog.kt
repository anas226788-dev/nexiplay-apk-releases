package com.nexiplay.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.nexiplay.app.data.util.AppUpdateInfo
import com.nexiplay.app.data.util.AppUpdateManager
import com.nexiplay.app.ui.theme.InterFont
import com.nexiplay.app.ui.theme.NexiRed
import kotlinx.coroutines.launch

@Composable
fun UpdateDialog(
    updateInfo: AppUpdateInfo,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val downloadProgress by AppUpdateManager.downloadProgress.collectAsState()
    val isDownloading by AppUpdateManager.isDownloading.collectAsState()
    val updateState by AppUpdateManager.updateState.collectAsState()
    val errorMessage by AppUpdateManager.errorMessage.collectAsState()
    val downloadedBytes by AppUpdateManager.downloadedBytes.collectAsState()
    val totalBytes by AppUpdateManager.totalBytes.collectAsState()

    val animatedProgress by animateFloatAsState(
        targetValue = if (downloadProgress >= 0f) downloadProgress else 0f,
        animationSpec = tween(300),
        label = "download_progress"
    )

    val isWorking = isDownloading || updateState == AppUpdateManager.UpdateState.INSTALLING
    val hasError = updateState == AppUpdateManager.UpdateState.ERROR

    Dialog(
        onDismissRequest = {
            if (!updateInfo.forceUpdate && !isWorking) {
                onDismiss()
            }
        },
        properties = DialogProperties(
            dismissOnBackPress = !updateInfo.forceUpdate && !isWorking,
            dismissOnClickOutside = !updateInfo.forceUpdate && !isWorking,
            usePlatformDefaultWidth = false
        )
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .wrapContentHeight(),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF1A1A2E)),
            elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // ── Header Icon ──
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .background(
                            brush = if (hasError) Brush.linearGradient(
                                colors = listOf(Color(0xFFFF6B35), Color(0xFFFF4444))
                            ) else Brush.linearGradient(
                                colors = listOf(NexiRed, Color(0xFFFF6B6B))
                            ),
                            shape = RoundedCornerShape(20.dp)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = when {
                            hasError -> Icons.Default.ErrorOutline
                            updateInfo.forceUpdate -> Icons.Default.NewReleases
                            else -> Icons.Default.CloudDownload
                        },
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Spacer(Modifier.height(20.dp))

                // ── Title ──
                Text(
                    text = when {
                        hasError -> "Update Failed"
                        updateInfo.forceUpdate -> "Update Required"
                        else -> "New Update Available"
                    },
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = InterFont,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(6.dp))

                // ── Version Badge ──
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .background(
                            NexiRed.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "v${updateInfo.latestVersionName}",
                        fontSize = 14.sp,
                        color = NexiRed,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFont
                    )
                    if (totalBytes > 0 && !isWorking && !hasError) {
                        Text(
                            text = " • ${AppUpdateManager.formatBytes(totalBytes)}",
                            fontSize = 12.sp,
                            color = NexiRed.copy(alpha = 0.7f),
                            fontFamily = InterFont
                        )
                    }
                }

                Spacer(Modifier.height(18.dp))

                // ── Error Message ──
                AnimatedVisibility(
                    visible = hasError && !errorMessage.isNullOrBlank(),
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Column {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color(0xFFFF4444).copy(alpha = 0.1f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Text(
                                text = errorMessage ?: "Unknown error",
                                fontSize = 13.sp,
                                color = Color(0xFFFF6B6B),
                                fontFamily = InterFont,
                                lineHeight = 18.sp
                            )
                        }
                        Spacer(Modifier.height(16.dp))
                    }
                }

                // ── Release Notes ──
                AnimatedVisibility(
                    visible = !hasError && updateInfo.releaseNotes.isNotBlank() && !isWorking,
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Text(
                            text = "WHAT'S NEW",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.4f),
                            fontFamily = InterFont,
                            letterSpacing = 1.5.sp
                        )
                        Spacer(Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(
                                    Color.White.copy(alpha = 0.05f),
                                    RoundedCornerShape(12.dp)
                                )
                                .padding(14.dp)
                        ) {
                            Text(
                                text = updateInfo.releaseNotes,
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.8f),
                                fontFamily = InterFont,
                                lineHeight = 20.sp
                            )
                        }
                        Spacer(Modifier.height(20.dp))
                    }
                }

                // ── Download Progress ──
                AnimatedVisibility(
                    visible = isWorking || (downloadProgress >= 0f && !hasError),
                    enter = fadeIn(tween(200)),
                    exit = fadeOut(tween(200))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Progress bar
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = when (updateState) {
                                AppUpdateManager.UpdateState.INSTALLING -> Color(0xFF4CAF50)
                                AppUpdateManager.UpdateState.COMPLETE -> Color(0xFF4CAF50)
                                else -> NexiRed
                            },
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )

                        Spacer(Modifier.height(10.dp))

                        // Status text
                        Text(
                            text = when (updateState) {
                                AppUpdateManager.UpdateState.CONNECTING -> "Connecting to server..."
                                AppUpdateManager.UpdateState.DOWNLOADING -> {
                                    val percent = (animatedProgress * 100).toInt()
                                    if (totalBytes > 0) {
                                        "Downloading... $percent% • ${AppUpdateManager.formatBytes(downloadedBytes)} / ${AppUpdateManager.formatBytes(totalBytes)}"
                                    } else {
                                        "Downloading... ${AppUpdateManager.formatBytes(downloadedBytes)}"
                                    }
                                }
                                AppUpdateManager.UpdateState.INSTALLING -> "Opening installer..."
                                AppUpdateManager.UpdateState.COMPLETE -> "Install started!"
                                else -> "Preparing..."
                            },
                            fontSize = 12.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            fontFamily = InterFont,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )

                        Spacer(Modifier.height(18.dp))
                    }
                }

                // ── Buttons ──
                if (!isWorking) {
                    // Primary Action: Update / Retry
                    Button(
                        onClick = {
                            if (hasError) {
                                AppUpdateManager.resetState()
                            }
                            scope.launch {
                                AppUpdateManager.downloadAndInstall(context)
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (hasError) Color(0xFFFF6B35) else NexiRed
                        ),
                        shape = RoundedCornerShape(14.dp),
                        elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
                    ) {
                        Icon(
                            imageVector = if (hasError) Icons.Default.Refresh else Icons.Default.SystemUpdate,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (hasError) "Retry Download" else "Update Now",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color.White,
                            fontFamily = InterFont
                        )
                    }

                    // Maybe Later (only for non-force updates)
                    if (!updateInfo.forceUpdate) {
                        Spacer(Modifier.height(8.dp))
                        TextButton(onClick = onDismiss) {
                            Text(
                                "Maybe Later",
                                color = Color.White.copy(alpha = 0.5f),
                                fontSize = 14.sp,
                                fontFamily = InterFont,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Force update warning
                    if (updateInfo.forceUpdate && !hasError) {
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "This update is required to continue using the app.",
                            fontSize = 12.sp,
                            color = Color(0xFFFF6B6B).copy(alpha = 0.7f),
                            fontFamily = InterFont,
                            textAlign = TextAlign.Center,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }
}
