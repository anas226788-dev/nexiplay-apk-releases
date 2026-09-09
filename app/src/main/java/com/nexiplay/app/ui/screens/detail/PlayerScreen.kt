package com.nexiplay.app.ui.screens.detail

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import com.nexiplay.app.ui.theme.InterFont
import com.nexiplay.app.ui.theme.NexiRed
import com.nexiplay.app.util.findActivity
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import kotlin.math.roundToInt

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(navController: NavController, encodedUrl: String) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val scope = rememberCoroutineScope()
    val url = remember { URLDecoder.decode(encodedUrl, StandardCharsets.UTF_8.toString()) }

    var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }
    var trackSelector by remember { mutableStateOf<DefaultTrackSelector?>(null) }

    // Playback State
    var isPlaying by remember { mutableStateOf(true) }
    var currentTime by remember { mutableLongStateOf(0L) }
    var totalTime by remember { mutableLongStateOf(0L) }
    var showControls by remember { mutableStateOf(true) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var isLocked by remember { mutableStateOf(false) }

    // Aspect Ratio / Resize Modes: FIT (0), FILL (3), ZOOM (4)
    var resizeMode by remember { mutableIntStateOf(AspectRatioFrameLayout.RESIZE_MODE_FIT) }
    var resizeModeLabel by remember { mutableStateOf("Fit") }
    var showAspectBadge by remember { mutableStateOf(false) }

    // Playback Speed
    var currentSpeed by remember { mutableFloatStateOf(1.0f) }
    var showSpeedModal by remember { mutableStateOf(false) }

    // Double Tap Skip States
    var showLeftSkip by remember { mutableStateOf(false) }
    var showRightSkip by remember { mutableStateOf(false) }

    // Settings Modal
    var showSettings by remember { mutableStateOf(false) }

    // Audio & Brightness Gestures
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    val maxVolume = remember { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1) }
    var currentVolumePercent by remember {
        mutableFloatStateOf(audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / maxVolume)
    }
    var showVolumeHud by remember { mutableStateOf(false) }

    var currentBrightness by remember {
        mutableFloatStateOf(activity?.window?.attributes?.screenBrightness?.takeIf { it >= 0f } ?: 0.5f)
    }
    var showBrightnessHud by remember { mutableStateOf(false) }

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

    // Auto-hide controls timer
    LaunchedEffect(showControls, isPlaying, showSettings, isLocked, showSpeedModal) {
        if (showControls && isPlaying && !showSettings && !showSpeedModal) {
            delay(4000)
            showControls = false
        }
    }

    // Lock to landscape & setup ExoPlayer
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

                    // Auto-Hindi audio track check
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
            // Reset screen brightness override
            val lp = activity?.window?.attributes
            if (lp != null) {
                lp.screenBrightness = -1.0f
                activity.window.attributes = lp
            }
        }
    }

    // Time ticker
    LaunchedEffect(isPlaying, playbackState) {
        while (playbackState != Player.STATE_ENDED && playbackState != Player.STATE_IDLE) {
            exoPlayer?.let {
                currentTime = it.currentPosition.coerceAtLeast(0L)
                totalTime = it.duration.coerceAtLeast(0L)
            }
            delay(500)
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
            // ── 1. Video Surface Layer ──
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        this.player = player
                        useController = false
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                    }
                },
                update = { pv ->
                    pv.resizeMode = resizeMode
                },
                modifier = Modifier.fillMaxSize()
            )

            // ── 2. Screen Gesture Touch Zones (Brightness, Volume & Taps) ──
            Row(modifier = Modifier.fillMaxSize()) {
                // Left Half (Brightness & -10s Double Tap)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(isLocked) {
                            if (isLocked) {
                                detectTapGestures(onTap = { showControls = !showControls })
                            } else {
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
                            }
                        }
                        .pointerInput(isLocked) {
                            if (!isLocked) {
                                detectVerticalDragGestures(
                                    onDragStart = { showBrightnessHud = true },
                                    onDragEnd = {
                                        scope.launch {
                                            delay(1200)
                                            showBrightnessHud = false
                                        }
                                    },
                                    onDragCancel = { showBrightnessHud = false }
                                ) { change, dragAmount ->
                                    change.consume()
                                    // Drag up decreases Y (negative delta), so subtract to increase brightness
                                    val delta = -dragAmount / 600f
                                    val newBrightness = (currentBrightness + delta).coerceIn(0.01f, 1.0f)
                                    currentBrightness = newBrightness

                                    val lp = activity?.window?.attributes
                                    if (lp != null) {
                                        lp.screenBrightness = newBrightness
                                        activity.window.attributes = lp
                                    }
                                    showBrightnessHud = true
                                }
                            }
                        }
                )

                // Right Half (Volume & +10s Double Tap)
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .pointerInput(isLocked) {
                            if (isLocked) {
                                detectTapGestures(onTap = { showControls = !showControls })
                            } else {
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
                            }
                        }
                        .pointerInput(isLocked) {
                            if (!isLocked) {
                                detectVerticalDragGestures(
                                    onDragStart = { showVolumeHud = true },
                                    onDragEnd = {
                                        scope.launch {
                                            delay(1200)
                                            showVolumeHud = false
                                        }
                                    },
                                    onDragCancel = { showVolumeHud = false }
                                ) { change, dragAmount ->
                                    change.consume()
                                    val delta = -dragAmount / 600f
                                    val newVol = (currentVolumePercent + delta).coerceIn(0f, 1.0f)
                                    currentVolumePercent = newVol
                                    val targetStreamVol = (newVol * maxVolume).roundToInt().coerceIn(0, maxVolume)
                                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetStreamVol, 0)
                                    showVolumeHud = true
                                }
                            }
                        }
                )
            }

            // ── 3. Double Tap Skip Indicators (in BoxScope) ──
            androidx.compose.animation.AnimatedVisibility(
                visible = showLeftSkip,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Replay10, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("-10s", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                    }
                }
            }

            androidx.compose.animation.AnimatedVisibility(
                visible = showRightSkip,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 80.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                        .border(1.5.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Rounded.Forward10, contentDescription = null, tint = Color.White, modifier = Modifier.size(44.dp))
                        Spacer(Modifier.height(4.dp))
                        Text("+10s", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                    }
                }
            }

            // ── 4. HUD Overlays (Brightness & Volume Indicators) ──
            // Brightness HUD (Left side)
            androidx.compose.animation.AnimatedVisibility(
                visible = showBrightnessHud && !isLocked,
                enter = fadeIn() + slideInHorizontally { -it },
                exit = fadeOut() + slideOutHorizontally { -it },
                modifier = Modifier.align(Alignment.CenterStart).padding(start = 40.dp)
            ) {
                GestureHudPill(
                    icon = when {
                        currentBrightness > 0.66f -> Icons.Rounded.WbSunny
                        currentBrightness > 0.33f -> Icons.Rounded.LightMode
                        else -> Icons.Rounded.Nightlight
                    },
                    label = "Brightness",
                    percentage = (currentBrightness * 100).roundToInt()
                )
            }

            // Volume HUD (Right side)
            androidx.compose.animation.AnimatedVisibility(
                visible = showVolumeHud && !isLocked,
                enter = fadeIn() + slideInHorizontally { it },
                exit = fadeOut() + slideOutHorizontally { it },
                modifier = Modifier.align(Alignment.CenterEnd).padding(end = 40.dp)
            ) {
                GestureHudPill(
                    icon = when {
                        currentVolumePercent == 0f -> Icons.Rounded.VolumeOff
                        currentVolumePercent > 0.5f -> Icons.Rounded.VolumeUp
                        else -> Icons.Rounded.VolumeDown
                    },
                    label = "Volume",
                    percentage = (currentVolumePercent * 100).roundToInt()
                )
            }

            // Aspect Ratio Toast Badge
            androidx.compose.animation.AnimatedVisibility(
                visible = showAspectBadge,
                enter = fadeIn() + scaleIn(),
                exit = fadeOut() + scaleOut(),
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 70.dp)
            ) {
                Box(
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(20.dp))
                        .border(1.dp, NexiRed.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        "Aspect Ratio: $resizeModeLabel",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFont
                    )
                }
            }

            // ── 5. Screen Lock Indicator (When Locked) ──
            if (isLocked) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.CenterStart).padding(start = 28.dp)
                ) {
                    IconButton(
                        onClick = { isLocked = false },
                        modifier = Modifier
                            .size(56.dp)
                            .background(Color.Black.copy(alpha = 0.65f), CircleShape)
                            .border(1.5.dp, NexiRed, CircleShape)
                    ) {
                        Icon(Icons.Rounded.Lock, contentDescription = "Unlock Controls", tint = NexiRed, modifier = Modifier.size(28.dp))
                    }
                }
            }

            // ── 6. Main Interactive Controls (Unlocked) ──
            if (!isLocked) {
                androidx.compose.animation.AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(animationSpec = tween(250)),
                    exit = fadeOut(animationSpec = tween(250)),
                    modifier = Modifier.fillMaxSize()
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        // Top & Bottom Gradients
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopCenter)
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.9f), Color.Transparent)))
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(140.dp)
                                .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))
                        )

                        // ── Top Bar ──
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 20.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { navController.popBackStack() },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(24.dp))
                            }

                            Spacer(Modifier.width(14.dp))

                            val title = if (url.startsWith("file://")) url.substringAfterLast("/") else "NexiPlay Streaming"
                            Text(
                                text = title,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = InterFont,
                                modifier = Modifier.weight(1f)
                            )

                            Spacer(Modifier.width(10.dp))

                            // Speed Button
                            Surface(
                                onClick = { showSpeedModal = true },
                                shape = RoundedCornerShape(16.dp),
                                color = Color.White.copy(alpha = 0.15f),
                                modifier = Modifier.padding(end = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Speed, contentDescription = "Speed", tint = Color.White, modifier = Modifier.size(16.dp))
                                    Spacer(Modifier.width(4.dp))
                                    Text("${currentSpeed}x", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                                }
                            }

                            // Aspect Ratio Button
                            IconButton(
                                onClick = {
                                    val (newMode, label) = when (resizeMode) {
                                        AspectRatioFrameLayout.RESIZE_MODE_FIT -> Pair(AspectRatioFrameLayout.RESIZE_MODE_ZOOM, "Zoom 16:9")
                                        AspectRatioFrameLayout.RESIZE_MODE_ZOOM -> Pair(AspectRatioFrameLayout.RESIZE_MODE_FILL, "Stretch Fill")
                                        else -> Pair(AspectRatioFrameLayout.RESIZE_MODE_FIT, "Original Fit")
                                    }
                                    resizeMode = newMode
                                    resizeModeLabel = label
                                    showAspectBadge = true
                                    scope.launch {
                                        delay(1500)
                                        showAspectBadge = false
                                    }
                                },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.AspectRatio, contentDescription = "Aspect Ratio", tint = Color.White, modifier = Modifier.size(22.dp))
                            }

                            Spacer(Modifier.width(6.dp))

                            // Settings / Track Button
                            IconButton(
                                onClick = { showSettings = true },
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.White.copy(alpha = 0.12f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Settings, contentDescription = "Settings", tint = Color.White, modifier = Modifier.size(22.dp))
                            }
                        }

                        // ── Lock Button (Floating on Left) ──
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .padding(start = 24.dp)
                        ) {
                            IconButton(
                                onClick = { isLocked = true; showControls = false },
                                modifier = Modifier
                                    .size(48.dp)
                                    .background(Color.Black.copy(alpha = 0.6f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.LockOpen, contentDescription = "Lock Screen", tint = Color.White, modifier = Modifier.size(24.dp))
                            }
                        }

                        // ── Center Media Controls ──
                        Row(
                            modifier = Modifier.align(Alignment.Center),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(28.dp)
                        ) {
                            // Rewind 10s
                            IconButton(
                                onClick = { player.seekTo((player.currentPosition - 10000).coerceAtLeast(0)) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Replay10, contentDescription = "Rewind 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            // Play / Pause / Buffering Indicator
                            if (playbackState == Player.STATE_BUFFERING) {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .background(Color.Black.copy(alpha = 0.5f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(
                                        color = NexiRed,
                                        strokeWidth = 3.5.dp,
                                        modifier = Modifier.size(52.dp)
                                    )
                                }
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(76.dp)
                                        .background(
                                            Brush.radialGradient(
                                                listOf(NexiRed, Color(0xFFB00610))
                                            ),
                                            CircleShape
                                        )
                                        .border(2.dp, Color.White.copy(alpha = 0.3f), CircleShape)
                                        .clickable {
                                            if (isPlaying) player.pause() else player.play()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = Color.White,
                                        modifier = Modifier.size(46.dp)
                                    )
                                }
                            }

                            // Forward 10s
                            IconButton(
                                onClick = { player.seekTo((player.currentPosition + 10000).coerceAtMost(player.duration)) },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.Forward10, contentDescription = "Forward 10s", tint = Color.White, modifier = Modifier.size(32.dp))
                            }

                            // Next Episode Button
                            IconButton(
                                onClick = {
                                    navController.previousBackStackEntry?.savedStateHandle?.set("auto_next_episode", true)
                                    navController.popBackStack()
                                },
                                modifier = Modifier
                                    .size(54.dp)
                                    .background(Color.Black.copy(alpha = 0.45f), CircleShape)
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), CircleShape)
                            ) {
                                Icon(Icons.Rounded.SkipNext, contentDescription = "Next Episode", tint = Color.White, modifier = Modifier.size(32.dp))
                            }
                        }

                        // ── Bottom Scrubber & Time Bar ──
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(bottom = 18.dp, start = 28.dp, end = 28.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = formatTime(currentTime),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFont
                                )

                                Spacer(Modifier.width(12.dp))

                                Slider(
                                    value = if (totalTime > 0) currentTime.toFloat() / totalTime.toFloat() else 0f,
                                    onValueChange = { percent ->
                                        val newTime = (percent * totalTime).toLong()
                                        player.seekTo(newTime)
                                        currentTime = newTime
                                    },
                                    colors = SliderDefaults.colors(
                                        thumbColor = NexiRed,
                                        activeTrackColor = NexiRed,
                                        inactiveTrackColor = Color.White.copy(alpha = 0.25f)
                                    ),
                                    modifier = Modifier.weight(1f)
                                )

                                Spacer(Modifier.width(12.dp))

                                Text(
                                    text = formatTime(totalTime),
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = InterFont
                                )
                            }
                        }
                    }
                }
            }

            // ── 7. Speed Selector Dialog / Modal ──
            if (showSpeedModal) {
                AlertDialog(
                    onDismissRequest = { showSpeedModal = false },
                    containerColor = Color(0xFF161622),
                    title = {
                        Text("Playback Speed", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                    },
                    text = {
                        val speeds = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
                        ) {
                            items(speeds) { sp ->
                                val isSel = currentSpeed == sp
                                Surface(
                                    onClick = {
                                        currentSpeed = sp
                                        player.setPlaybackSpeed(sp)
                                        showSpeedModal = false
                                    },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isSel) NexiRed else Color.White.copy(alpha = 0.1f),
                                    border = if (isSel) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.15f))
                                ) {
                                    Text(
                                        text = "${sp}x",
                                        color = Color.White,
                                        fontWeight = if (isSel) FontWeight.Bold else FontWeight.Normal,
                                        fontFamily = InterFont,
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showSpeedModal = false }) {
                            Text("Done", color = NexiRed, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            // ── 8. Auto Next Countdown Overlay ──
            if (showAutoNextOverlay) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.88f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Next Episode",
                            color = Color.White,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Black,
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
                            OutlinedButton(
                                onClick = { showAutoNextOverlay = false },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.4f)),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Cancel", color = Color.White, fontFamily = InterFont)
                            }
                            Button(
                                onClick = {
                                    showAutoNextOverlay = false
                                    navController.previousBackStackEntry?.savedStateHandle?.set("auto_next_episode", true)
                                    navController.popBackStack()
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Play Now ⏭️", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                            }
                        }
                    }
                }
            }

            // ── 9. Settings Modal Bottom Sheet ──
            if (showSettings) {
                ModalBottomSheet(
                    onDismissRequest = { showSettings = false },
                    containerColor = Color(0xFF14141E),
                    contentColor = Color.White,
                    dragHandle = { BottomSheetDefaults.DragHandle(color = Color.White.copy(alpha = 0.3f)) }
                ) {
                    LazyColumn(
                        modifier = Modifier
                            .padding(bottom = 32.dp, start = 24.dp, end = 24.dp)
                            .fillMaxWidth()
                    ) {
                        item {
                            Text("Playback Settings", fontSize = 18.sp, fontWeight = FontWeight.Black, color = Color.White, fontFamily = InterFont)
                            Spacer(Modifier.height(16.dp))
                        }

                        // Quality
                        val videoGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_VIDEO }
                        if (videoGroups.isNotEmpty()) {
                            item {
                                Text("Quality", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                                Spacer(Modifier.height(8.dp))
                            }
                            item {
                                val hasVideoOverride = videoGroups.any { player.trackSelectionParameters.overrides.containsKey(it.mediaTrackGroup) }
                                val isAuto = !hasVideoOverride
                                SettingItemRow(title = "Auto (Best Quality)", isSelected = isAuto) {
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                        .build()
                                    showSettings = false
                                }
                            }
                            videoGroups.forEach { group ->
                                items(group.length) { i ->
                                    val format = group.getTrackFormat(i)
                                    val res = if (format.height > 0) "${format.height}p" else "Bitrate: ${format.bitrate / 1000}kbps"
                                    val hasOverride = player.trackSelectionParameters.overrides.containsKey(group.mediaTrackGroup)
                                    val isSelected = hasOverride && group.isTrackSelected(i)
                                    SettingItemRow(title = res, isSelected = isSelected) {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .clearOverridesOfType(C.TRACK_TYPE_VIDEO)
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        showSettings = false
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        // Audio Track
                        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
                        if (audioGroups.isNotEmpty()) {
                            item {
                                Text("Audio Track", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                                Spacer(Modifier.height(8.dp))
                            }
                            audioGroups.forEach { group ->
                                items(group.length) { i ->
                                    val format = group.getTrackFormat(i)
                                    val lang = format.language ?: format.label ?: "Track ${i + 1}"
                                    val isSelected = group.isTrackSelected(i)
                                    SettingItemRow(title = lang.uppercase(), isSelected = isSelected) {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        showSettings = false
                                    }
                                }
                            }
                            item { Spacer(Modifier.height(16.dp)) }
                        }

                        // Subtitles
                        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
                        if (textGroups.isNotEmpty()) {
                            item {
                                Text("Subtitles (CC)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                                Spacer(Modifier.height(8.dp))
                            }
                            item {
                                val isOff = player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                                SettingItemRow(title = "Off", isSelected = isOff) {
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                                        .build()
                                    showSettings = false
                                }
                            }
                            textGroups.forEach { group ->
                                items(group.length) { i ->
                                    val format = group.getTrackFormat(i)
                                    val lang = format.language ?: format.label ?: "Subtitle ${i + 1}"
                                    val isSelected = group.isTrackSelected(i) && !player.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
                                    SettingItemRow(title = lang.uppercase(), isSelected = isSelected) {
                                        player.trackSelectionParameters = player.trackSelectionParameters
                                            .buildUpon()
                                            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                                            .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, i))
                                            .build()
                                        showSettings = false
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

// ── Gesture HUD Pill Component ──
@Composable
private fun GestureHudPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    percentage: Int
) {
    Box(
        modifier = Modifier
            .background(Color.Black.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = null, tint = NexiRed, modifier = Modifier.size(32.dp))
            Spacer(Modifier.height(8.dp))
            Text(label, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp, fontFamily = InterFont)
            Spacer(Modifier.height(4.dp))
            Text("$percentage%", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
            Spacer(Modifier.height(8.dp))
            // Mini progress bar
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Color.White.copy(alpha = 0.2f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = (percentage / 100f).coerceIn(0f, 1f))
                        .background(NexiRed)
                )
            }
        }
    }
}

// ── Setting Row Component ──
@Composable
private fun SettingItemRow(
    title: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
            contentDescription = null,
            tint = if (isSelected) NexiRed else Color.Gray,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(14.dp))
        Text(
            text = title,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontFamily = InterFont
        )
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
