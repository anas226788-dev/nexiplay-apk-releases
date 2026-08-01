package com.nexiplay.app.ui.screens.detail

import android.app.Activity
import android.content.pm.ActivityInfo
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.nexiplay.app.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavController, encodedUrl: String) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val url = remember { URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString()) }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var trackSelector by remember { mutableStateOf<DefaultTrackSelector?>(null) }

    // Player State
    var isPlaying by remember { mutableStateOf(true) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }

    // Settings Menu State
    var showSettings by remember { mutableStateOf(false) }

    // Double Tap Animation States
    val scope = rememberCoroutineScope()
    var showLeftSkip by remember { mutableStateOf(false) }
    var showRightSkip by remember { mutableStateOf(false) }

    // Auto Next State
    var showAutoNextOverlay by remember { mutableStateOf(false) }
    var autoNextCountdown by remember { mutableIntStateOf(5) }

    LaunchedEffect(showAutoNextOverlay) {
        if (showAutoNextOverlay) {
            autoNextCountdown = 5
            while (autoNextCountdown > 0 && showAutoNextOverlay) {
                delay(1000)
                autoNextCountdown--
            }
            if (showAutoNextOverlay) {
                navController.previousBackStackEntry?.savedStateHandle?.set("auto_next_episode", true)
                navController.popBackStack()
            }
        }
    }

    // Lock to landscape when entering Player
    DisposableEffect(Unit) {
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val tSelector = DefaultTrackSelector(context)
        trackSelector = tSelector

        val player = ExoPlayer.Builder(context)
            .setTrackSelector(tSelector)
            .build().apply {
                setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                prepare()
                playWhenReady = true
            }
        exoPlayer = player

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                isPlaying = isPlayingNow
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
                if (state == Player.STATE_READY) {
                    totalTime = player.duration.coerceAtLeast(0L)

                    // Auto-Hindi logic check
                    val tracks = player.currentTracks
                    for (group in tracks.groups) {
                        if (group.type == C.TRACK_TYPE_AUDIO) {
                            for (i in 0 until group.length) {
                                val format = group.getTrackFormat(i)
                                val lang = format.language ?: format.label ?: ""
                                if (lang.contains("hi", ignoreCase = true) || lang.contains("hindi", ignoreCase = true)) {
                                    if (!group.isTrackSelected(i)) {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                    }
                                    break
                                }
                            }
                        }
                    }
                }
                // Track episode watch for daily coin reward & auto-next
                if (state == Player.STATE_ENDED) {
                    com.nexiplay.app.data.util.CoinRewardHelper.recordEpisodeWatch(context)
                    showAutoNextOverlay = true
                }
            }
        }
        player.addListener(listener)

        onDispose {
            player.removeListener(listener)
            player.release()
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Update progress
    LaunchedEffect(isPlaying, playbackState) {
        while (playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
            exoPlayer?.let {
                currentTime = it.currentPosition.coerceAtLeast(0L)
                totalTime = it.duration.coerceAtLeast(0L)
            }
            delay(500)
        }
    }

    // Auto hide controls
    LaunchedEffect(showControls, isPlaying, showSettings) {
        if (showControls && isPlaying && !showSettings) {
            delay(3500)
            showControls = false
        }
    }

    BackHandler {
        navController.popBackStack()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        exoPlayer?.let { player ->
            // 1. Base Video View
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false // We use our custom UI
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            // 2. Double Tap & Single Tap Zones
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Half
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    player.seekTo((player.currentPosition - 10000).coerceAtLeast(0))
                                    scope.launch {
                                        showLeftSkip = true
                                        delay(800)
                                        showLeftSkip = false
                                    }
                                },
                                onTap = { showControls = !showControls }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = showLeftSkip, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(24.dp)
                        ) {
                            Icon(Icons.Rounded.FastRewind, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("-10s", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }

                // Right Half
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(Unit) {
                            detectTapGestures(
                                onDoubleTap = {
                                    player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration))
                                    scope.launch {
                                        showRightSkip = true
                                        delay(800)
                                        showRightSkip = false
                                    }
                                },
                                onTap = { showControls = !showControls }
                            )
                        },
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.animation.AnimatedVisibility(visible = showRightSkip, enter = fadeIn(), exit = fadeOut()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .background(Color.Black.copy(alpha = 0.5f), CircleShape)
                                .padding(24.dp)
                        ) {
                            Icon(Icons.Rounded.FastForward, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                            Text("+10s", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // 3. Custom UI Overlay (Controls)
            AnimatedVisibility(
                visible = showControls,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier.fillMaxSize()
                ) {
                    // Top Gradient Background
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .fillMaxWidth()
                            .height(100.dp)
                            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)))
                    )

                    // Bottom Gradient Background
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(140.dp)
                            .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
                    )

                    // Top Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { navController.popBackStack() }) {
                            Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        val title = if (url.startsWith("file://")) url.substringAfterLast("/") else "NexiPlay Player"
                        Text(
                            text = title,
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(28.dp))
                        }
                    }

                    // Center Controls (Play/Pause, Rewind, FastForward, Skip Next)
                    Row(
                        modifier = Modifier.align(Alignment.Center),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(28.dp)
                    ) {
                        IconButton(onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) }) {
                            Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        if (playbackState == Player.STATE_BUFFERING) {
                            CircularProgressIndicator(color = Color(0xFFE50914), modifier = Modifier.size(64.dp))
                        } else {
                            IconButton(
                                onClick = {
                                    if (isPlaying) player.pause() else player.play()
                                },
                                modifier = Modifier
                                    .size(72.dp)
                                    .background(Color.Black.copy(alpha = 0.3f), CircleShape)
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = Color.White,
                                    modifier = Modifier.size(48.dp)
                                )
                            }
                        }

                        IconButton(onClick = { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration)) }) {
                            Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(36.dp))
                        }

                        IconButton(
                            onClick = {
                                navController.previousBackStackEntry?.savedStateHandle?.set("auto_next_episode", true)
                                navController.popBackStack()
                            }
                        ) {
                            Icon(Icons.Rounded.SkipNext, contentDescription = "Next Episode", tint = Color.White, modifier = Modifier.size(36.dp))
                        }
                    }

                    // Bottom Bar
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 24.dp, start = 32.dp, end = 32.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = formatTime(currentTime),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(Modifier.width(16.dp))
                            Slider(
                                value = if (totalTime > 0) currentTime.toFloat() / totalTime.toFloat() else 0f,
                                onValueChange = { percent ->
                                    val newTime = (percent * totalTime).toLong()
                                    player.seekTo(newTime)
                                    currentTime = newTime
                                },
                                colors = SliderDefaults.colors(
                                    thumbColor = Color(0xFFE50914),
                                    activeTrackColor = Color(0xFFE50914),
                                    inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                                ),
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(Modifier.width(16.dp))
                            Text(
                                text = formatTime(totalTime),
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                }
            }

            // 4. Auto Next Countdown Overlay
            if (showAutoNextOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Next Episode",
                            color = Color.White,
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Playing next episode in $autoNextCountdown seconds...",
                            color = Color.White.copy(alpha = 0.8f),
                            fontSize = 14.sp,
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.height(24.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Button(
                                onClick = { showAutoNextOverlay = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.2f))
                            ) {
                                Text("Cancel", color = Color.White)
                            }
                            Button(
                                onClick = {
                                    showAutoNextOverlay = false
                                    navController.previousBackStackEntry?.savedStateHandle?.set("auto_next_episode", true)
                                    navController.popBackStack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE50914))
                            ) {
                                Text("Play Now ⏭️", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }

            // 5. Settings Modal Bottom Sheet
            if (showSettings) {
                ModalBottomSheet(
                    onDismissRequest = { showSettings = false },
                    containerColor = Color(0xFF1E1E2D),
                    contentColor = Color.White,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                            .fillMaxWidth()
                    ) {
                        // --- QUALITY ---
                        val videoGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                        if (videoGroups.isNotEmpty()) {
                            item {
                                Text("Quality", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                            }
                            item {
                                val hasVideoOverride = videoGroups.any { player.trackSelectionParameters.overrides.containsKey(it.mediaTrackGroup) }
                                val isAuto = !hasVideoOverride
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                .build()
                                            showSettings = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isAuto) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isAuto) Color(0xFFE50914) else Color.Gray
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text("Auto", color = Color.White, fontSize = 16.sp)
                                }
                            }

                            videoGroups.forEach { group ->
                                items(group.length) { i ->
                                    val format = group.getTrackFormat(i)
                                    val res = if (format.height > 0) "${format.height}p" else "Bitrate: ${format.bitrate / 1000}kbps"
                                    val hasOverride = player.trackSelectionParameters.overrides.containsKey(group.mediaTrackGroup)
                                    val isSelected = hasOverride && group.isTrackSelected(i)
                                    
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                    .build()
                                                showSettings = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFE50914) else Color.Gray
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(res, color = Color.White, fontSize = 16.sp)
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        // --- AUDIO ---
                        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                        if (audioGroups.isNotEmpty()) {
                            item {
                                Text("Audio Track", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                            }
                            audioGroups.forEach { group ->
                                items(group.length) { i ->
                                    val format = group.getTrackFormat(i)
                                    val lang = format.language ?: format.label ?: "Track ${i + 1}"
                                    val isSelected = group.isTrackSelected(i)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                    .build()
                                                showSettings = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFE50914) else Color.Gray
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(lang.uppercase(), color = Color.White, fontSize = 16.sp)
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        // --- SUBTITLES ---
                        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                        if (textGroups.isNotEmpty()) {
                            item {
                                Text("Subtitles (CC)", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = Color.White.copy(alpha = 0.5f))
                                Spacer(Modifier.height(8.dp))
                            }
                            item {
                                val isOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            player.trackSelectionParameters = player.trackSelectionParameters
                                                .buildUpon()
                                                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                                .build()
                                            showSettings = false
                                        }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = if (isOff) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                        contentDescription = null,
                                        tint = if (isOff) Color(0xFFE50914) else Color.Gray
                                    )
                                    Spacer(Modifier.width(16.dp))
                                    Text("Off", color = Color.White, fontSize = 16.sp)
                                }
                            }
                            textGroups.forEach { group ->
                                items(group.length) { i ->
                                    val format = group.getTrackFormat(i)
                                    val lang = format.language ?: format.label ?: "Subtitle ${i + 1}"
                                    val isSelected = group.isTrackSelected(i) && !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                player.trackSelectionParameters = player.trackSelectionParameters
                                                    .buildUpon()
                                                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                                    .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                                    .build()
                                                showSettings = false
                                            }
                                            .padding(vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                                            contentDescription = null,
                                            tint = if (isSelected) Color(0xFFE50914) else Color.Gray
                                        )
                                        Spacer(Modifier.width(16.dp))
                                        Text(lang.uppercase(), color = Color.White, fontSize = 16.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun formatTime(ms: Long): String {
    if (ms < 0) return "00:00"
    val totalSeconds = ms / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format("%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format("%02d:%02d", minutes, seconds)
    }
}
