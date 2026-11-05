package com.nexiplay.app.ui.screens.livetv

import android.annotation.SuppressLint
import android.net.Uri
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.model.LiveChannel
import com.nexiplay.app.data.model.RadioStation
import com.nexiplay.app.data.repository.WebFeaturesRepository
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.launch
import java.net.URLEncoder

private data class CountryOption(
    val code: String,
    val name: String,
    val flag: String,
    val featured: Boolean = false
)

private val COUNTRIES = listOf(
    CountryOption("bd", "Bangladesh", "🇧🇩", true),
    CountryOption("in", "India", "🇮🇳", true),
    CountryOption("pk", "Pakistan", "🇵🇰", true),
    CountryOption("us", "United States", "🇺🇸", true),
    CountryOption("gb", "United Kingdom", "🇬🇧", true),
    CountryOption("sa", "Saudi Arabia", "🇸🇦", true),
    CountryOption("ae", "UAE", "🇦🇪", true),
    CountryOption("ca", "Canada", "🇨🇦", true),
    CountryOption("tr", "Turkey", "🇹🇷"),
    CountryOption("my", "Malaysia", "🇲🇾"),
    CountryOption("id", "Indonesia", "🇮🇩"),
    CountryOption("au", "Australia", "🇦🇺"),
    CountryOption("de", "Germany", "🇩🇪"),
    CountryOption("fr", "France", "🇫🇷"),
    CountryOption("jp", "Japan", "🇯🇵"),
    CountryOption("kr", "South Korea", "🇰🇷"),
    CountryOption("qa", "Qatar", "🇶🇦"),
    CountryOption("es", "Spain", "🇪🇸"),
    CountryOption("br", "Brazil", "🇧🇷"),
    CountryOption("it", "Italy", "🇮🇹"),
    CountryOption("eg", "Egypt", "🇪🇬"),
    CountryOption("sg", "Singapore", "🇸🇬")
)

private data class TvCategoryFilter(
    val id: String,
    val label: String
)

private val TV_CATEGORIES = listOf(
    TvCategoryFilter("all", "All"),
    TvCategoryFilter("sports", "Sports"),
    TvCategoryFilter("news", "News"),
    TvCategoryFilter("entertainment", "Entertainment"),
    TvCategoryFilter("music", "Music"),
    TvCategoryFilter("kids", "Kids")
)

private val AVATAR_PALETTES = listOf(
    Color(0xFF211836) to Color(0xFFB09AFF), // Violet Afterglow (Primary)
    Color(0xFF122B27) to Color(0xFF57D6C0), // Accent Mint
    Color(0xFF2D2118) to Color(0xFFFFB878), // Accent Apricot
    Color(0xFF172336) to Color(0xFF60A5FA), // Info Blue
    Color(0xFF2E1724) to Color(0xFFFF7185), // Coral Rose
    Color(0xFF1B1D36) to Color(0xFF818CF8)  // Indigo Glow
)

private fun channelInitials(name: String): String {
    val words = name.trim()
        .replace(Regex("[^A-Za-z0-9\\s\\-]"), "")
        .split(Regex("[\\s\\-]+"))
        .filter { it.isNotBlank() }
    return when {
        words.size >= 2 -> "${words[0].first().uppercaseChar()}${words[1].first().uppercaseChar()}"
        words.size == 1 && words[0].length >= 2 -> words[0].take(2).uppercase()
        words.size == 1 -> words[0].uppercase()
        else -> "TV"
    }
}

private fun matchesCategory(group: String?, categoryId: String): Boolean {
    if (categoryId == "all") return true
    val g = (group ?: "").lowercase()
    return when (categoryId) {
        "sports" -> g.contains("sport")
        "news" -> g.contains("news") || g.contains("business") || g.contains("weather")
        "entertainment" -> g.contains("entertainment") || g.contains("movie") || g.contains("series") || g.contains("general") || g.contains("drama") || g.contains("comedy")
        "music" -> g.contains("music")
        "kids" -> g.contains("kids") || g.contains("animation") || g.contains("family")
        else -> true
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LiveTvScreen(navController: NavController) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // ── NexiPlay Website & App "Violet Afterglow" Palette ──
    val pageBg = DarkBg                       // #0D0B12
    val surfaceBg = DarkSurface               // #17141F
    val surfaceElevated = DarkSurface2        // #211D2B
    val borderSubtle = Color(0xFF2D263E)      // Violet-tinted dark border
    val primaryViolet = NexiRed               // #9273F5
    val primaryVioletLight = NexiRedLight     // #B09AFF
    val primaryVioletDark = NexiRedDark       // #5C46B2
    val activeRowBg = Color(0xFF1D1630)       // Deep Violet active card surface
    val activeRowBorder = Color(0xFF5C46B2)   // Violet active border
    val liveRed = Color(0xFFEF4444)           // Live broadcast badge red
    val liveAccent = NexiRed                  // #9273F5 primary brand accent

    var activeMode by remember { mutableStateOf("tv") } // "tv" | "radio"
    var activeCountry by remember { mutableStateOf("bd") }
    var activeCategory by remember { mutableStateOf("all") }
    var searchQuery by remember { mutableStateOf("") }
    var showWorldDropdown by remember { mutableStateOf(false) }

    // TV State
    var channels by remember { mutableStateOf<List<LiveChannel>>(emptyList()) }
    var activeChannel by remember { mutableStateOf<LiveChannel?>(null) }
    var tvLoading by remember { mutableStateOf(true) }
    var tvPlayerMode by remember { mutableStateOf("hls") } // "hls" | "youtube"
    var tvStreamError by remember { mutableStateOf(false) }
    var tvPlaying by remember { mutableStateOf(true) }
    var tvMuted by remember { mutableStateOf(false) }

    // Radio State
    var stations by remember { mutableStateOf<List<RadioStation>>(emptyList()) }
    var activeStation by remember { mutableStateOf<RadioStation?>(null) }
    var radioLoading by remember { mutableStateOf(false) }
    var radioPlaying by remember { mutableStateOf(false) }
    var radioMuted by remember { mutableStateOf(false) }
    var radioError by remember { mutableStateOf(false) }

    val selectedCountryName = remember(activeCountry) {
        COUNTRIES.firstOrNull { it.code.equals(activeCountry, ignoreCase = true) }?.name ?: activeCountry.uppercase()
    }

    // ExoPlayer for TV & Radio
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = true
        }
    }

    DisposableEffect(Unit) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (activeMode == "tv") {
                    tvPlaying = isPlaying
                } else {
                    radioPlaying = isPlaying
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (activeMode == "tv") {
                    val ch = activeChannel
                    if (ch?.youtubeLiveUrl != null && tvPlayerMode == "hls") {
                        tvPlayerMode = "youtube"
                        tvStreamError = false
                    } else {
                        tvStreamError = true
                    }
                } else {
                    radioError = true
                    radioPlaying = false
                }
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.release()
        }
    }

    // Load TV Channels when country changes in TV mode
    LaunchedEffect(activeCountry, activeMode) {
        if (activeMode == "tv") {
            tvLoading = true
            tvStreamError = false
            val list = WebFeaturesRepository.fetchLiveChannels(activeCountry)
            channels = list
            if (list.isNotEmpty()) {
                val first = list.first()
                activeChannel = first
                tvPlayerMode = "hls"
            } else {
                activeChannel = null
            }
            tvLoading = false
        } else {
            radioLoading = true
            radioError = false
            val list = WebFeaturesRepository.fetchRadioStations(activeCountry)
            stations = list
            if (list.isNotEmpty() && activeStation == null) {
                activeStation = list.first()
            }
            radioLoading = false
        }
    }

    // Play active TV channel or Radio station in ExoPlayer
    LaunchedEffect(activeMode, activeChannel, tvPlayerMode, activeStation) {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        if (activeMode == "tv") {
            val ch = activeChannel ?: return@LaunchedEffect
            if (tvPlayerMode == "hls" && ch.streamUrl.isNotBlank()) {
                tvStreamError = false
                exoPlayer.volume = if (tvMuted) 0f else 1f
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(ch.streamUrl)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        } else {
            val st = activeStation ?: return@LaunchedEffect
            val url = st.urlResolved.ifBlank { st.url }
            if (url.isNotBlank()) {
                radioError = false
                exoPlayer.volume = if (radioMuted) 0f else 1f
                exoPlayer.setMediaItem(MediaItem.fromUri(Uri.parse(url)))
                exoPlayer.prepare()
                exoPlayer.playWhenReady = true
            }
        }
    }

    val filteredChannels = remember(channels, activeCategory, searchQuery) {
        channels.filter { ch ->
            val matchCat = matchesCategory(ch.group, activeCategory)
            val matchSearch = searchQuery.isBlank() ||
                ch.name.contains(searchQuery, ignoreCase = true) ||
                ch.group.contains(searchQuery, ignoreCase = true)
            matchCat && matchSearch
        }
    }

    val filteredStations = remember(stations, searchQuery) {
        if (searchQuery.isBlank()) stations
        else stations.filter {
            it.name.contains(searchQuery, ignoreCase = true) ||
                it.tags.contains(searchQuery, ignoreCase = true)
        }
    }

    Scaffold(
        containerColor = pageBg,
        topBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(pageBg)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back Arrow + Title & "42 CHANNELS ON AIR" Subtitle
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "Back",
                        tint = TextWhite,
                        modifier = Modifier
                            .size(22.dp)
                            .clickable { navController.popBackStack() }
                    )
                    Column {
                        Text(
                            text = if (activeMode == "tv") "Live TV" else "Live Radio",
                            color = TextWhite,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = InterFont,
                            letterSpacing = (-0.4).sp
                        )
                        val countText = if (activeMode == "tv") {
                            "${channels.size} CHANNELS ON AIR"
                        } else {
                            "${stations.size} STATIONS ON AIR"
                        }
                        Text(
                            text = countText,
                            color = TextGray,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFont,
                            letterSpacing = 1.1.sp
                        )
                    }
                }

                // Right: Segmented Pill Switcher ("TV" | "Radio")
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(13.dp))
                        .background(surfaceBg)
                        .border(1.dp, borderSubtle, RoundedCornerShape(13.dp))
                        .padding(3.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val isTv = activeMode == "tv"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isTv) primaryViolet else Color.Transparent)
                            .clickable { activeMode = "tv" }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "TV",
                            color = if (isTv) TextWhite else TextGray,
                            fontSize = 12.5.sp,
                            fontWeight = if (isTv) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontFamily = InterFont
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (!isTv) primaryViolet else Color.Transparent)
                            .clickable { activeMode = "radio" }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Radio",
                            color = if (!isTv) TextWhite else TextGray,
                            fontSize = 12.5.sp,
                            fontWeight = if (!isTv) FontWeight.ExtraBold else FontWeight.SemiBold,
                            fontFamily = InterFont
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(pageBg)
                .padding(padding)
        ) {
            // ── 1. Sticky Top Player Card (Violet Afterglow Palette) ──
            if (activeMode == "tv") {
                val ch = activeChannel
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .aspectRatio(16f / 9.6f)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2B1F4A),
                                    surfaceBg,
                                    pageBg
                                ),
                                center = Offset(300f, 180f),
                                radius = 450f
                            )
                        )
                        .border(1.dp, borderSubtle, RoundedCornerShape(20.dp))
                ) {
                    if (ch == null) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            if (tvLoading) {
                                CircularProgressIndicator(color = primaryViolet, strokeWidth = 2.5.dp, modifier = Modifier.size(32.dp))
                            } else {
                                Text("Select a channel to start watching", color = TextGray, fontSize = 13.sp, fontFamily = InterFont)
                            }
                        }
                    } else if (tvPlayerMode == "youtube" && !ch.youtubeLiveUrl.isNullOrBlank()) {
                        LiveYoutubeEmbedView(embedUrl = ch.youtubeLiveUrl)
                    } else if (!tvStreamError) {
                        AndroidView(
                            factory = { ctx ->
                                PlayerView(ctx).apply {
                                    player = exoPlayer
                                    useController = false
                                    resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                                    layoutParams = FrameLayout.LayoutParams(
                                        ViewGroup.LayoutParams.MATCH_PARENT,
                                        ViewGroup.LayoutParams.MATCH_PARENT
                                    )
                                }
                            },
                            update = { pv ->
                                pv.player = exoPlayer
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Subtle Top & Bottom Vignette Gradients
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f),
                                        Color.Transparent,
                                        Color(0xFF0D0B12).copy(alpha = 0.78f)
                                    )
                                )
                            )
                    )

                    if (ch != null) {
                        // Top-Left Premium Glass "● LIVE" Broadcast Pill Badge
                        PremiumPlayerLiveBadge(
                            label = "LIVE",
                            isOffline = tvStreamError,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(14.dp)
                        )

                        // Center Circular Play/Pause Button (or Error Retry)
                        if (tvStreamError) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(horizontal = 20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Stream temporarily offline",
                                    color = TextWhite,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFont
                                )
                                Spacer(Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (!ch.youtubeLiveUrl.isNullOrBlank()) {
                                        Box(
                                            modifier = Modifier
                                                .clip(RoundedCornerShape(50))
                                                .background(primaryViolet)
                                                .clickable {
                                                    tvStreamError = false
                                                    tvPlayerMode = "youtube"
                                                }
                                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                        ) {
                                            Text("YouTube Backup", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                                        }
                                    }
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(50))
                                            .background(surfaceElevated.copy(alpha = 0.85f))
                                            .border(1.dp, primaryVioletLight.copy(alpha = 0.35f), RoundedCornerShape(50))
                                            .clickable {
                                                val idx = filteredChannels.indexOfFirst { it.id == ch.id }
                                                if (filteredChannels.size > 1) {
                                                    activeChannel = filteredChannels[(idx + 1) % filteredChannels.size]
                                                    tvPlayerMode = "hls"
                                                    tvStreamError = false
                                                }
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text("Next Channel \u2192", color = TextWhite, fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                                    }
                                }
                            }
                        } else if (tvPlayerMode == "hls") {
                            Box(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF17141F).copy(alpha = 0.65f))
                                    .border(1.dp, primaryVioletLight.copy(alpha = 0.35f), CircleShape)
                                    .clickable {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (tvPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = "Play/Pause",
                                    tint = TextWhite,
                                    modifier = Modifier.size(26.dp)
                                )
                            }
                        }

                        // Bottom Overlay Bar: Channel Title + "720p · Entertainment" on Left, Mute + Fullscreen on Right
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = ch.name,
                                    color = TextWhite,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = InterFont,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "720p \u00B7 ${ch.group.ifBlank { "Entertainment" }}",
                                    color = TextGray,
                                    fontSize = 11.5.sp,
                                    fontWeight = FontWeight.Medium,
                                    fontFamily = InterFont,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            Row(
                                horizontalArrangement = Arrangement.spacedBy(14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = if (tvMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                    contentDescription = "Mute",
                                    tint = TextWhite.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable {
                                            tvMuted = !tvMuted
                                            exoPlayer.volume = if (tvMuted) 0f else 1f
                                        }
                                )
                                Icon(
                                    imageVector = Icons.Default.Fullscreen,
                                    contentDescription = "Fullscreen",
                                    tint = TextWhite.copy(alpha = 0.9f),
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable {
                                            if (ch.streamUrl.isNotBlank()) {
                                                exoPlayer.pause()
                                                val encoded = URLEncoder.encode(ch.streamUrl, "UTF-8")
                                                navController.navigate("player/$encoded")
                                            }
                                        }
                                )
                            }
                        }
                    }
                }
            } else {
                // Radio Player Deck in Violet Afterglow aesthetic
                val st = activeStation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(
                            Brush.radialGradient(
                                colors = listOf(
                                    Color(0xFF2B1F4A),
                                    surfaceBg,
                                    pageBg
                                ),
                                center = Offset(260f, 100f),
                                radius = 400f
                            )
                        )
                        .border(1.dp, borderSubtle, RoundedCornerShape(20.dp))
                        .padding(16.dp)
                ) {
                    if (st == null) {
                        Text(
                            text = if (radioLoading) "Loading radio stations..." else "Select a radio station below to start listening",
                            color = TextGray,
                            fontSize = 13.sp,
                            fontFamily = InterFont
                        )
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                ChannelLogoBox(
                                    logoUrl = st.favicon,
                                    name = st.name,
                                    isSelected = true,
                                    fallbackBg = primaryViolet,
                                    fallbackFg = TextWhite,
                                    sizeDp = 48
                                )

                                Column(modifier = Modifier.weight(1f)) {
                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        PremiumPlayerLiveBadge(
                                            label = if (radioError) "OFFLINE" else if (radioPlaying) "LIVE" else "PAUSED",
                                            isOffline = radioError
                                        )
                                        Text(
                                            text = "${st.bitrate} kbps",
                                            color = TextGray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = InterFont
                                        )
                                    }
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = st.name,
                                        color = TextWhite,
                                        fontSize = 15.5.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = InterFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = st.tags.ifBlank { selectedCountryName },
                                        color = TextGray,
                                        fontSize = 11.5.sp,
                                        fontFamily = InterFont,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                IconButton(
                                    onClick = {
                                        radioMuted = !radioMuted
                                        exoPlayer.volume = if (radioMuted) 0f else 1f
                                    },
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(surfaceElevated)
                                ) {
                                    Icon(
                                        imageVector = if (radioMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                        contentDescription = "Mute",
                                        tint = TextWhite,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                IconButton(
                                    onClick = {
                                        if (exoPlayer.isPlaying) exoPlayer.pause() else exoPlayer.play()
                                    },
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(primaryViolet)
                                ) {
                                    Icon(
                                        imageVector = if (radioPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                        contentDescription = "Play/Pause",
                                        tint = TextWhite,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 2. Country Pills Row ("BD Bangladesh", "IN India", "PK Pakistan", ...) ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(COUNTRIES.filter { it.featured }, key = { it.code }) { c ->
                    val selected = activeCountry == c.code
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) primaryViolet else surfaceBg)
                            .border(
                                1.dp,
                                if (selected) primaryVioletLight else borderSubtle,
                                RoundedCornerShape(50)
                            )
                            .clickable {
                                activeCountry = c.code
                                activeCategory = "all"
                            }
                            .padding(horizontal = 15.dp, vertical = 9.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = c.code.uppercase(),
                                color = if (selected) Color(0xFFE9E2FF) else TextGray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = InterFont
                            )
                            Text(
                                text = c.name,
                                color = TextWhite,
                                fontSize = 13.5.sp,
                                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                                fontFamily = InterFont
                            )
                        }
                    }
                }

                item {
                    Box {
                        val nonFeaturedSelected = COUNTRIES.firstOrNull { !it.featured && it.code == activeCountry }
                        val isSel = nonFeaturedSelected != null
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (isSel) primaryViolet else surfaceBg)
                                .border(
                                    1.dp,
                                    if (isSel) primaryVioletLight else borderSubtle,
                                    RoundedCornerShape(50)
                                )
                                .clickable { showWorldDropdown = true }
                                .padding(horizontal = 15.dp, vertical = 9.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(5.dp)
                            ) {
                                Text(
                                    text = nonFeaturedSelected?.code?.uppercase() ?: "GL",
                                    color = if (isSel) Color(0xFFE9E2FF) else TextGray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontFamily = InterFont
                                )
                                Text(
                                    text = nonFeaturedSelected?.name ?: "More",
                                    color = TextWhite,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFont
                                )
                                Icon(
                                    Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    tint = if (isSel) TextWhite else TextGray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        DropdownMenu(
                            expanded = showWorldDropdown,
                            onDismissRequest = { showWorldDropdown = false },
                            modifier = Modifier.background(surfaceElevated)
                        ) {
                            COUNTRIES.filter { !it.featured }.forEach { c ->
                                DropdownMenuItem(
                                    text = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = c.code.uppercase(),
                                                color = primaryVioletLight,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                fontFamily = InterFont
                                            )
                                            Text(
                                                text = c.name,
                                                color = TextWhite,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                fontFamily = InterFont
                                            )
                                        }
                                    },
                                    onClick = {
                                        activeCountry = c.code
                                        activeCategory = "all"
                                        showWorldDropdown = false
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 3. Underline Category Tabs ("All 42", "Sports 1", "News 6", "Entertainment 21") ──
            if (activeMode == "tv") {
                Column(modifier = Modifier.fillMaxWidth()) {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        items(TV_CATEGORIES, key = { it.id }) { cat ->
                            val selected = activeCategory == cat.id
                            val count = remember(channels, cat.id) {
                                if (cat.id == "all") channels.size
                                else channels.count { matchesCategory(it.group, cat.id) }
                            }
                            Column(
                                modifier = Modifier
                                    .clickable { activeCategory = cat.id }
                                    .width(IntrinsicSize.Max),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.padding(bottom = 8.dp)
                                ) {
                                    Text(
                                        text = cat.label,
                                        color = if (selected) TextWhite else TextGray,
                                        fontSize = 14.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontFamily = InterFont
                                    )
                                    Text(
                                        text = "$count",
                                        color = if (selected) primaryVioletLight else TextDarkGray,
                                        fontSize = 13.sp,
                                        fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        fontFamily = InterFont
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(2.5.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(if (selected) primaryViolet else Color.Transparent)
                                )
                            }
                        }
                    }
                    HorizontalDivider(
                        color = borderSubtle,
                        thickness = 1.dp,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(12.dp))
            }

            // ── 4. Sleek Dark Search Input ("Search channels") ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(46.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(surfaceBg)
                    .border(1.dp, borderSubtle, RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(18.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = if (activeMode == "tv") "Search channels" else "Search radio stations",
                                color = TextGray,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = InterFont
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { q ->
                                searchQuery = q
                                if (activeMode == "radio" && q.length >= 2) {
                                    scope.launch {
                                        radioLoading = true
                                        stations = WebFeaturesRepository.fetchRadioStations(activeCountry, q)
                                        radioLoading = false
                                    }
                                }
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextWhite,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = InterFont
                            ),
                            cursorBrush = SolidColor(primaryVioletLight),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = TextGray,
                            modifier = Modifier
                                .size(16.dp)
                                .clickable {
                                    searchQuery = ""
                                    if (activeMode == "radio") {
                                        scope.launch {
                                            radioLoading = true
                                            stations = WebFeaturesRepository.fetchRadioStations(activeCountry, "")
                                            radioLoading = false
                                        }
                                    }
                                }
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // ── 5. Channel / Station List (Violet Afterglow Active Cards + Monogram Squircles) ──
            if (activeMode == "tv") {
                if (tvLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryViolet, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                    }
                } else if (filteredChannels.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No channels found", color = TextGray, fontSize = 13.5.sp, fontFamily = InterFont)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredChannels, key = { _, ch -> "${ch.id}_${ch.streamUrl}" }) { index, ch ->
                            val isSelected = activeChannel?.id == ch.id
                            val (avatarBg, avatarFg) = if (isSelected) {
                                primaryViolet to TextWhite
                            } else {
                                AVATAR_PALETTES[index % AVATAR_PALETTES.size]
                            }
                            val rowShape = RoundedCornerShape(16.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .background(
                                        if (isSelected) activeRowBg else Color.Transparent
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.dp, activeRowBorder, rowShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable {
                                        activeChannel = ch
                                        tvPlayerMode = "hls"
                                        tvStreamError = false
                                    }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Original Channel Logo Squircle (with Monogram Fallback)
                                    ChannelLogoBox(
                                        logoUrl = ch.logo,
                                        name = ch.name,
                                        isSelected = isSelected,
                                        fallbackBg = avatarBg,
                                        fallbackFg = avatarFg,
                                        sizeDp = 46
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = ch.name,
                                            color = TextWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = InterFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${ch.group.ifBlank { "Entertainment" }} \u00B7 $selectedCountryName",
                                            color = if (isSelected) primaryVioletLight else TextGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = InterFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                // Right: Premium Animated Equalizer / Glass LIVE Badge
                                PremiumRowLiveBadge(isSelected = isSelected)
                            }
                        }
                    }
                }
            } else {
                // Radio Stations List in matching Violet Afterglow style
                if (radioLoading) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = primaryViolet, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
                    }
                } else if (filteredStations.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No radio stations found", color = TextGray, fontSize = 13.5.sp, fontFamily = InterFont)
                    }
                } else {
                    LazyColumn(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(filteredStations, key = { _, st -> st.stationuuid }) { index, st ->
                            val isSelected = activeStation?.stationuuid == st.stationuuid
                            val (avatarBg, avatarFg) = if (isSelected) {
                                primaryViolet to TextWhite
                            } else {
                                AVATAR_PALETTES[index % AVATAR_PALETTES.size]
                            }
                            val rowShape = RoundedCornerShape(16.dp)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(rowShape)
                                    .background(
                                        if (isSelected) activeRowBg else Color.Transparent
                                    )
                                    .then(
                                        if (isSelected) {
                                            Modifier.border(1.dp, activeRowBorder, rowShape)
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { activeStation = st }
                                    .padding(horizontal = 12.dp, vertical = 11.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(13.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Original Radio Station Logo Squircle (with Monogram Fallback)
                                    ChannelLogoBox(
                                        logoUrl = st.favicon,
                                        name = st.name,
                                        isSelected = isSelected,
                                        fallbackBg = avatarBg,
                                        fallbackFg = avatarFg,
                                        sizeDp = 46
                                    )

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = st.name,
                                            color = TextWhite,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.ExtraBold,
                                            fontFamily = InterFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Spacer(Modifier.height(2.dp))
                                        Text(
                                            text = "${st.bitrate} kbps \u00B7 $selectedCountryName",
                                            color = if (isSelected) primaryVioletLight else TextGray,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Medium,
                                            fontFamily = InterFont,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                PremiumRowLiveBadge(isSelected = isSelected)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChannelLogoBox(
    logoUrl: String?,
    name: String,
    isSelected: Boolean,
    fallbackBg: Color,
    fallbackFg: Color,
    sizeDp: Int = 46
) {
    val cleanUrl = remember(logoUrl) {
        val trimmed = logoUrl?.trim().orEmpty()
        if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed
        } else {
            ""
        }
    }
    var logoLoaded by remember(cleanUrl) { mutableStateOf(false) }
    var logoFailed by remember(cleanUrl) { mutableStateOf(false) }

    val shape = RoundedCornerShape(12.dp)
    val showImage = cleanUrl.isNotEmpty() && !logoFailed

    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(shape)
            .background(if (logoLoaded) Color(0xFF13101D) else fallbackBg)
            .border(
                width = 1.dp,
                color = if (isSelected) Color(0xFFB09AFF).copy(alpha = 0.55f) else Color(0xFF2D263E),
                shape = shape
            ),
        contentAlignment = Alignment.Center
    ) {
        if (!logoLoaded) {
            Text(
                text = channelInitials(name),
                color = fallbackFg,
                fontSize = 14.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = InterFont,
                letterSpacing = 0.3.sp
            )
        }
        if (showImage) {
            AsyncImage(
                model = cleanUrl,
                contentDescription = name,
                contentScale = ContentScale.Fit,
                onSuccess = { logoLoaded = true },
                onError = {
                    logoLoaded = false
                    logoFailed = true
                },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(6.dp)
            )
        }
    }
}

@Composable
private fun PremiumPlayerLiveBadge(
    label: String = "LIVE",
    isOffline: Boolean = false,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_scale"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.45f,
        targetValue = 0.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_alpha"
    )

    val dotColor = if (isOffline) Color(0xFF9CA3AF) else Color(0xFFFF2E54)
    val pillShape = RoundedCornerShape(50)

    Row(
        modifier = modifier
            .clip(pillShape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xE01A0B14),
                        Color(0xE0141022)
                    )
                )
            )
            .border(
                width = 1.dp,
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        dotColor.copy(alpha = 0.65f),
                        Color(0xFF9273F5).copy(alpha = 0.45f)
                    )
                ),
                shape = pillShape
            )
            .padding(horizontal = 10.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.size(10.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isOffline) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .scale(pulseScale)
                        .background(dotColor.copy(alpha = pulseAlpha), CircleShape)
                )
            }
            Box(
                modifier = Modifier
                    .size(5.5.dp)
                    .background(dotColor, CircleShape)
            )
        }
        Text(
            text = label,
            color = TextWhite,
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Black,
            fontFamily = InterFont,
            letterSpacing = 1.3.sp
        )
    }
}

@Composable
private fun PremiumRowLiveBadge(isSelected: Boolean) {
    val pillShape = RoundedCornerShape(50)
    if (isSelected) {
        val infiniteTransition = rememberInfiniteTransition(label = "eq_anim")
        val bar1 by infiniteTransition.animateFloat(
            initialValue = 5f,
            targetValue = 13f,
            animationSpec = infiniteRepeatable(
                animation = tween(420, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b1"
        )
        val bar2 by infiniteTransition.animateFloat(
            initialValue = 13f,
            targetValue = 6f,
            animationSpec = infiniteRepeatable(
                animation = tween(360, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b2"
        )
        val bar3 by infiniteTransition.animateFloat(
            initialValue = 7f,
            targetValue = 14f,
            animationSpec = infiniteRepeatable(
                animation = tween(480, easing = FastOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "b3"
        )

        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(Color(0xFF9273F5).copy(alpha = 0.16f))
                .border(1.dp, Color(0xFFB09AFF).copy(alpha = 0.45f), pillShape)
                .padding(horizontal = 9.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                modifier = Modifier.height(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(bar1.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(NexiRedLight)
                )
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(bar2.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(NexiRedLight)
                )
                Box(
                    modifier = Modifier
                        .width(2.5.dp)
                        .height(bar3.dp)
                        .clip(RoundedCornerShape(1.5.dp))
                        .background(NexiRedLight)
                )
            }
            Text(
                text = "ON AIR",
                color = Color(0xFFE9E2FF),
                fontSize = 9.sp,
                lineHeight = 9.sp,
                fontWeight = FontWeight.Black,
                fontFamily = InterFont,
                letterSpacing = 0.9.sp
            )
        }
    } else {
        Row(
            modifier = Modifier
                .clip(pillShape)
                .background(Color(0xFF17141F))
                .border(1.dp, Color(0xFF2D263E), pillShape)
                .padding(horizontal = 8.5.dp, vertical = 4.5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(SuccessGreen, CircleShape)
            )
            Text(
                text = "LIVE",
                color = Color(0xFFD6D1E0),
                fontSize = 9.5.sp,
                lineHeight = 9.5.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = InterFont,
                letterSpacing = 1.0.sp
            )
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun LiveYoutubeEmbedView(embedUrl: String) {
    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                }
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                loadUrl(embedUrl)
            }
        },
        update = { wv ->
            if (wv.url != embedUrl) {
                wv.loadUrl(embedUrl)
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}
