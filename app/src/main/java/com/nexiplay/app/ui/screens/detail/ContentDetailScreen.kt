package com.nexiplay.app.ui.screens.detail

import android.content.Intent
import android.net.Uri
import org.json.JSONObject
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.*
import com.nexiplay.app.ui.components.*
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.launch
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth

@Composable
fun ContentDetailScreen(
    navController: NavController,
    type: String,
    slug: String,
    vm: DetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(type, slug) { vm.loadContent(type, slug) }

    val showContent = !state.isLoading && state.error == null && state.movie != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        if (state.isLoading) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Box(Modifier.fillMaxWidth().height(280.dp).shimmerEffect())
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ShimmerBox(
                            modifier = Modifier.width(80.dp).height(120.dp).offset(y = (-40).dp),
                            cornerRadius = 12
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            ShimmerBox(Modifier.fillMaxWidth(0.8f).height(24.dp), 8)
                            Spacer(Modifier.height(8.dp))
                            ShimmerBox(Modifier.fillMaxWidth(0.5f).height(16.dp), 4)
                        }
                    }
                    Spacer(Modifier.height((-24).dp))
                    
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        ShimmerBox(Modifier.weight(1f).height(44.dp), 12)
                        ShimmerBox(Modifier.width(100.dp).height(44.dp), 12)
                    }
                    Spacer(Modifier.height(24.dp))
                    
                    ShimmerBox(Modifier.width(100.dp).height(20.dp), 4)
                    Spacer(Modifier.height(8.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(14.dp), 4)
                    Spacer(Modifier.height(4.dp))
                    ShimmerBox(Modifier.fillMaxWidth().height(14.dp), 4)
                    Spacer(Modifier.height(4.dp))
                    ShimmerBox(Modifier.fillMaxWidth(0.6f).height(14.dp), 4)
                }
            }
        } else if (state.error != null || state.movie == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(state.error ?: "Not found", color = themeTextSecondary(), fontFamily = InterFont)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .background(NexiRed, RoundedCornerShape(12.dp))
                            .bounceClick { navController.popBackStack() }
                            .padding(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text("Go Back", color = Color.White, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                    }
                }
            }
        }

        AnimatedVisibility(
            visible = showContent,
            modifier = Modifier.fillMaxSize()
        ) {
            state.movie?.let { movie ->
                var expandedDesc by remember { mutableStateOf(false) }
                var selectedSeasonIdx by remember { mutableIntStateOf(0) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    // ── Hero Banner ──
                    Box(Modifier.fillMaxWidth().height(280.dp)) {
                        AsyncImage(
                            model = movie.bannerUrlMobile ?: movie.bannerUrlDesktop ?: movie.posterUrl,
                            contentDescription = movie.title,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(listOf(Color.Transparent, themeBg()), startY = 150f)
                            )
                        )
                        // Back button
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
                        ) {
                            Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                        }
                    }

                    Column(Modifier.padding(horizontal = 16.dp)) {
                        // ── Title & Meta ──
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            // Poster thumbnail
                            AsyncImage(
                                model = movie.posterUrl,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.width(80.dp).height(120.dp).offset(y = (-40).dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(movie.title, fontSize = 20.sp, fontWeight = FontWeight.Black, color = themeTextPrimary(), maxLines = 3, fontFamily = InterFont)
                                Spacer(Modifier.height(4.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    if (movie.releaseYear != null) {
                                        Text("${movie.releaseYear}", fontSize = 12.sp, color = themeTextSecondary(), fontFamily = InterFont)
                                    }
                                    Text("•", color = themeTextTertiary())
                                    Text(movie.type.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                                    if (movie.isRunning == true) {
                                        Text("•", color = themeTextTertiary())
                                        Text("RUNNING", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = SuccessGreen, fontFamily = InterFont)
                                    }
                                }
                                if (movie.language != null) {
                                    Text("Language: ${movie.language}", fontSize = 11.sp, color = themeTextTertiary(), fontFamily = InterFont)
                                }
                            }
                        }

                        Spacer(Modifier.height((-24).dp))

                        // ── App Specific Streaming ON/OFF Control (App Watch Switch from Admin Panel) ──
                        val isAppStreamingEnabled = movie.appStreamingEnabled != false
                        val hasStreamingData = movie.tmdbId != null || !movie.streamingUrl.isNullOrBlank() || state.streamingRow != null
                        val firstSeason = state.seasons.firstOrNull()
                        val firstEpisode = if (firstSeason != null) state.episodes[firstSeason.id]?.firstOrNull() else null
                        val hasAvailableServers = if (hasStreamingData) getAvailableServers(
                            type, state.streamingRow, movie,
                            if (type == "series" || type == "anime") firstEpisode else null,
                            firstSeason?.seasonNumber ?: 1
                        ).isNotEmpty() else false
                        val isWatchAvailable = isAppStreamingEnabled && movie.streamingUrl != "disabled" && hasStreamingData && hasAvailableServers

                        // ── Action Buttons ──
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                            // Watch Button (Only show if streaming is available, like web)
                            if (isWatchAvailable) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(44.dp)
                                        .background(NexiRed, RoundedCornerShape(12.dp))
                                        .bounceClick { 
                                            com.nexiplay.app.data.util.AdManager.showInterstitialAd(context) {
                                                navController.navigate("watch/$type/$slug")
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(18.dp), tint = Color.White)
                                        Spacer(Modifier.width(4.dp))
                                        Text("Watch", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color.White, fontFamily = InterFont)
                                    }
                                }
                            }

                            // Watchlist Button
                            Box(
                                modifier = Modifier
                                    .height(44.dp)
                                    .border(1.dp, if (state.isInWatchlist) CoinGold else themeTextSecondary(), RoundedCornerShape(12.dp))
                                    .bounceClick { vm.toggleWatchlist() }
                                    .padding(horizontal = 16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        if (state.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                        null, modifier = Modifier.size(18.dp),
                                        tint = if (state.isInWatchlist) CoinGold else themeTextSecondary()
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    Text(
                                        if (state.isInWatchlist) "Saved" else "Save", 
                                        fontSize = 13.sp, 
                                        color = if (state.isInWatchlist) CoinGold else themeTextSecondary(),
                                        fontFamily = InterFont
                                    )
                                }
                            }

                            // Trailer Button
                            if (movie.trailerUrl != null) {
                                Box(
                                    modifier = Modifier
                                        .height(44.dp)
                                        .border(1.dp, themeTextSecondary(), RoundedCornerShape(12.dp))
                                        .bounceClick {
                                            val encodedUrl = java.net.URLEncoder.encode(movie.trailerUrl, "UTF-8")
                                            navController.navigate("webview/$encodedUrl")
                                        }
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Default.Movie, null, modifier = Modifier.size(16.dp), tint = themeTextSecondary())
                                        Spacer(Modifier.width(4.dp))
                                        Text("Trailer", fontSize = 13.sp, color = themeTextSecondary(), fontFamily = InterFont)
                                    }
                                }
                            }
                        }

                        Spacer(Modifier.height(16.dp))

                        // ── Description ──
                        if (!movie.description.isNullOrBlank()) {
                            Text("Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                            Spacer(Modifier.height(6.dp))
                            Text(
                                text = movie.description,
                                fontSize = 13.sp, color = themeTextSecondary(), lineHeight = 20.sp,
                                maxLines = if (expandedDesc) Int.MAX_VALUE else 4,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = InterFont
                            )
                            Text(
                                text = if (expandedDesc) "Show Less" else "Read More",
                                fontSize = 12.sp, color = NexiRed, fontWeight = FontWeight.Bold, fontFamily = InterFont,
                                modifier = Modifier
                                    .bounceClick { expandedDesc = !expandedDesc }
                                    .padding(vertical = 4.dp),
                            )
                            Spacer(Modifier.height(16.dp))
                        }

                        // ── Info Tags ──
                        if (movie.source != null || movie.format != null || movie.subtitle != null) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                movie.source?.let { InfoChip("Source: $it") }
                                movie.format?.let { InfoChip("Format: $it") }
                                movie.subtitle?.let { InfoChip("Sub: $it") }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // Banner Ad inside Content Detail
                        com.nexiplay.app.ui.components.AppBannerAd()
                        Spacer(Modifier.height(16.dp))

                        // ── 📢 Per-Content Important Notice (like web) ──
                        if (movie.noticeEnabled == true && !movie.noticeText.isNullOrBlank()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Brush.horizontalGradient(
                                            listOf(
                                                NexiRed.copy(alpha = 0.2f),
                                                Color(0xFFFF6B00).copy(alpha = 0.15f)
                                            )
                                        ),
                                        RoundedCornerShape(16.dp)
                                    )
                                    .border(1.dp, NexiRed.copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .padding(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text("📢", fontSize = 24.sp)
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "IMPORTANT NOTICE",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.Black,
                                            color = NexiRed,
                                            letterSpacing = 2.sp,
                                            fontFamily = InterFont
                                        )
                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            movie.noticeText!!,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = themeTextPrimary(),
                                            lineHeight = 22.sp,
                                            fontFamily = InterFont
                                        )
                                    }
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        if (state.downloads.isNotEmpty()) {
                            Text("📥 Downloads", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                            Spacer(Modifier.height(8.dp))
                            state.downloads.forEach { dl ->
                                DownloadLinkCard(dl, movie, context, navController)
                                Spacer(Modifier.height(8.dp))
                            }
                            Spacer(Modifier.height(16.dp))
                        }

                        // ── Seasons & Episodes (Series/Anime) ──
                        if (state.seasons.isNotEmpty()) {
                            Text("📺 Seasons & Episodes", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                            Spacer(Modifier.height(8.dp))

                            // Season tabs
                            ScrollableTabRow(
                                selectedTabIndex = selectedSeasonIdx,
                                containerColor = themeCard(),
                                contentColor = NexiRed,
                                edgePadding = 0.dp,
                                divider = {},
                                indicator = {},
                                modifier = Modifier.clip(RoundedCornerShape(12.dp)),
                            ) {
                                state.seasons.forEachIndexed { idx, season ->
                                    val sel = selectedSeasonIdx == idx
                                    Tab(selected = sel, onClick = { selectedSeasonIdx = idx }) {
                                        Box(
                                            Modifier.background(
                                                if (sel) NexiRed else Color.Transparent,
                                                RoundedCornerShape(10.dp)
                                            ).padding(horizontal = 14.dp, vertical = 8.dp)
                                        ) {
                                            Text(
                                                season.seasonTitle ?: "Season ${season.seasonNumber}",
                                                fontSize = 13.sp, fontWeight = if (sel) FontWeight.Bold else FontWeight.Normal,
                                                color = if (sel) Color.White else themeTextSecondary(),
                                                fontFamily = InterFont
                                            )
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(10.dp))

                            // Episodes for selected season
                            val selectedSeason = state.seasons.getOrNull(selectedSeasonIdx)
                            val episodes = selectedSeason?.let { state.episodes[it.id] } ?: emptyList()

                            if (episodes.isEmpty()) {
                                Text("No episodes found", color = themeTextTertiary(), fontSize = 13.sp, fontFamily = InterFont)
                            } else {
                                episodes.forEach { ep ->
                                    var expanded by remember { mutableStateOf(false) }
                                    
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(themeCard(), RoundedCornerShape(10.dp))
                                            .bounceClick { expanded = !expanded }
                                            .padding(12.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            Box(
                                                Modifier.size(36.dp).background(NexiRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                                                contentAlignment = Alignment.Center,
                                            ) {
                                                Text("${ep.episodeNumber}", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                                            }
                                            Spacer(Modifier.width(12.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(
                                                    ep.episodeTitle ?: "Episode ${ep.episodeNumber}",
                                                    fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = themeTextPrimary(),
                                                    fontFamily = InterFont
                                                )
                                            }
                                            Icon(
                                                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                                contentDescription = "Expand",
                                                tint = themeTextSecondary(),
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                        
                                        // Download section
                                        if (expanded) {
                                            Spacer(Modifier.height(12.dp))
                                            HorizontalDivider(color = themeTextTertiary().copy(alpha = 0.5f))
                                            Spacer(Modifier.height(12.dp))

                                            val epDls = state.episodeDownloads[ep.id] ?: emptyList()
                                            if (epDls.isNotEmpty()) {
                                                Text("Downloads", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                                                Spacer(Modifier.height(8.dp))
                                                val seasonNum = state.seasons.getOrNull(selectedSeasonIdx)?.seasonNumber
                                                epDls.forEach { dl ->
                                                    EpisodeDownloadLinkCard(dl, movie, ep, seasonNum, context, navController)
                                                    Spacer(Modifier.height(6.dp))
                                                }
                                            } else {
                                                Text("No downloads available", fontSize = 13.sp, color = themeTextSecondary(), fontFamily = InterFont)
                                            }
                                        }
                                    }
                                    Spacer(Modifier.height(6.dp))
                                }
                            }
                            
                            Spacer(Modifier.height(16.dp))
                            // Native Banner Ad at the bottom of the download screen
                            com.nexiplay.app.ui.components.AppNativeAd()
                            Spacer(Modifier.height(32.dp))
                            Spacer(Modifier.height(16.dp))
                        }

                        // 🔹 Cast 🔹
                        if (!movie.castMembers.isNullOrBlank()) {
                            Text("Cast", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
                            Spacer(Modifier.height(4.dp))
                            Text(movie.castMembers, fontSize = 13.sp, color = themeTextSecondary(), lineHeight = 18.sp, fontFamily = InterFont)
                            Spacer(Modifier.height(16.dp))
                        }
                        
                        // Related Content
                        if (state.relatedMovies.isNotEmpty()) {
                            Spacer(Modifier.height(24.dp))
                            Text(
                                "More Like This",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = themeTextPrimary(),
                                fontFamily = InterFont
                            )
                            Spacer(Modifier.height(12.dp))
                        }
                    }

                    if (state.relatedMovies.isNotEmpty()) {
                        androidx.compose.foundation.lazy.LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            contentPadding = PaddingValues(horizontal = 16.dp)
                        ) {
                            items(state.relatedMovies, key = { it.id }) { rMovie ->
                                com.nexiplay.app.ui.components.MovieCard(
                                    movie = rMovie,
                                    onClick = {
                                        navController.navigate("content/${rMovie.type}/${rMovie.slug}")
                                    },
                                    modifier = Modifier.width(110.dp)
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }

                    // Comments Section
                    Spacer(Modifier.height(24.dp))
                    com.nexiplay.app.ui.components.CommentSection(movieId = movie.id)

                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String) {
    Box(
        Modifier.background(themeCard(), RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text, fontSize = 11.sp, color = themeTextSecondary(), fontFamily = InterFont)
    }
}

data class StreamServer(val id: String, val name: String, val url: String, val color: Color = NexiRed)

fun getAvailableServers(
    type: String,
    streamingRow: StreamingRow?,
    movie: Movie,
    episode: Episode? = null,
    seasonNumber: Int = 1
): List<StreamServer> {
    val servers = mutableListOf<StreamServer>()
    val isSeries = type == "series" || type == "anime"

    // 1. ToonPlay Server
    val tpUrl = if (isSeries) episode?.streamingUrlToonplay else streamingRow?.streamingUrlToonplay ?: streamingRow?.toonplayUrl
    if (!tpUrl.isNullOrBlank()) {
        servers.add(StreamServer("toonplay", "Nexiplay Private Server", tpUrl, Color(0xFFE53935)))
    }

    // 2. AnimeRulz Server
    val arUrl = if (isSeries) episode?.streamingUrlAnimerulz else streamingRow?.streamingUrlAnimerulz ?: streamingRow?.animerulzUrl
    if (!arUrl.isNullOrBlank()) {
        servers.add(StreamServer("animerulz", "AnimeR Server", arUrl, Color(0xFFFB8C00)))
    }

    // 3. Custom / Multi-Scraper Servers
    val customUrl = if (isSeries) episode?.streamingUrl else movie.streamingUrl
    if (!customUrl.isNullOrBlank()) {
        val trimmedUrl = customUrl.trim()
        if (trimmedUrl.startsWith("{")) {
            try {
                val jsonObj = JSONObject(trimmedUrl)
                val keys = jsonObj.keys()
                val ignoredKeys = setOf("single", "separate", "episode", "is_disabled", "multi_scraper_config")
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (ignoredKeys.contains(key.lowercase())) continue

                    val url = jsonObj.optString(key)
                    if (url.isNotBlank() && url != "null") {
                        when (key.lowercase()) {
                            "toonplay" -> {
                                if (tpUrl.isNullOrBlank()) {
                                    servers.add(StreamServer("toonplay", "Nexiplay Private Server", url, Color(0xFFE53935)))
                                }
                            }
                            "animerulz" -> {
                                if (arUrl.isNullOrBlank()) {
                                    servers.add(StreamServer("animerulz", "AnimeR Server", url, Color(0xFFFB8C00)))
                                }
                            }
                            "animeworld" -> servers.add(StreamServer("animeworld", "AnimeWorld Server", url, Color(0xFF4CAF50)))
                            "animixstream" -> servers.add(StreamServer("animixstream", "Nexiplay Ani Server", url, Color(0xFF00BCD4)))
                            "toonstream" -> servers.add(StreamServer("toonstream", "Nexiplay T Server", url, Color(0xFF9C27B0)))
                            "rareanimes" -> servers.add(StreamServer("rareanimes", "RR Nexiplay Server", url, Color(0xFFE91E63)))
                            "custom" -> servers.add(StreamServer("custom", "Server Nexiplay", url, Color(0xFFFDD835)))
                            "legacy" -> servers.add(StreamServer("custom_legacy", "Server Nexiplay Legacy", url, Color(0xFFFDD835)))
                            else -> servers.add(StreamServer(key, key.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() } + " Server", url, Color(0xFF9E9E9E)))
                        }
                    }
                }
            } catch (_: Exception) {
                servers.add(StreamServer("custom", "Server Nexiplay", trimmedUrl, Color(0xFFFDD835)))
            }
        } else if (trimmedUrl != "disabled") {
            servers.add(StreamServer("custom", "Server Nexiplay", trimmedUrl, Color(0xFFFDD835)))
        }
    }

    // 4. TMDB Embed Servers (VidSrc)
    val tmdb = streamingRow?.tmdbId ?: movie.tmdbId
    if (!tmdb.isNullOrBlank()) {
        if (isSeries && episode != null) {
            val sNum = seasonNumber
            val eNum = episode.episodeNumber
            servers.add(StreamServer("vidsrc_to", "VidSrc (Pro)", "https://vidsrc.pm/embed/tv/$tmdb/$sNum/$eNum", Color(0xFF8E24AA)))
            servers.add(StreamServer("vidsrc_me", "VidSrc.me", "https://vidsrc.in/embed/tv?tmdb=$tmdb&season=$sNum&episode=$eNum", Color(0xFF3949AB)))
        } else if (!isSeries) {
            servers.add(StreamServer("vidsrc_to", "VidSrc (Pro)", "https://vidsrc.pm/embed/movie/$tmdb", Color(0xFF8E24AA)))
            servers.add(StreamServer("vidsrc_me", "VidSrc.me", "https://vidsrc.in/embed/movie?tmdb=$tmdb", Color(0xFF3949AB)))
        }
    }

    // 5. Deduplicate by Server ID & URL
    val uniqueServers = servers.distinctBy { it.id to it.url }

    // 6. Admin Panel Streaming Server Filter (via app_enabled_servers only — no socialBarCode fallback)
    val rawConfig = com.nexiplay.app.data.util.AdManager.appEnabledServers

    if (rawConfig != null) {
        val trimmed = rawConfig.trim()
        if (trimmed.isEmpty() || trimmed.equals("none", ignoreCase = true)) {
            return emptyList()
        }
        val enabledIds = trimmed.lowercase().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        
        return uniqueServers.filter { server ->
            val id = server.id.lowercase()
            if (id == "custom_legacy" || id == "legacy") {
                enabledIds.contains("custom")
            } else {
                enabledIds.contains(id)
            }
        }
    }

    return uniqueServers
}

// Sites that need Chrome's full download UI
private val CHROME_DOWNLOAD_HOSTS = listOf("mega.nz", "mega.co.nz", "mega.io", "pixeldrain.com", "pixeldrain.dev", "pcloud.com", "pcloud.link")

private fun needsChromeDownload(url: String): Boolean {
    val lowerUrl = url.lowercase()
    if (lowerUrl.contains("pcloud.") || lowerUrl.contains("pixeldrain.") || lowerUrl.contains("mega.")) {
        return true
    }
    val host = try { java.net.URI(lowerUrl).host ?: "" } catch (_: Exception) { "" }
    return CHROME_DOWNLOAD_HOSTS.any { host.contains(it) }
}

private fun openLink(url: String, context: android.content.Context, navController: NavController) {
    if (needsChromeDownload(url)) {
        // Open in Chrome — user downloads there, files will appear in My Downloads scanner
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } else {
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        navController.navigate("webview/$encodedUrl")
    }
}

private fun trackDownload(context: android.content.Context, movie: Movie, provider: String, resolution: String, episodeId: String? = null, seasonNumber: Int? = null, episodeNumber: Int? = null) {
    val user = SupabaseClient.main.auth.currentUserOrNull() ?: return
    kotlinx.coroutines.GlobalScope.launch(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val event = UserEvent(
                userId = user.id,
                eventType = "download",
                movieId = movie.id,
                episodeId = episodeId,
                contentType = movie.type,
                contentTitle = movie.title,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                provider = provider,
                resolution = resolution,
                metadata = EventMetadata(
                    title = movie.title,
                    slug = movie.slug,
                    posterUrl = movie.posterUrl,
                    type = movie.type,
                    source = "android_app"
                )
            )
            SupabaseClient.main.from("user_events").insert(event)
            // Record download for daily coins
            com.nexiplay.app.data.util.CoinRewardHelper.recordDownload(context)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
private fun DownloadLinkCard(dl: DownloadLink, movie: Movie, context: android.content.Context, navController: NavController) {
    val links = listOfNotNull(
        dl.megaLink?.let { "Mega" to it },
        dl.gdriveLink?.let { "GDrive" to it },
        dl.mediafireLink?.let { "MediaFire" to it },
        dl.teraboxLink?.let { "TeraBox" to it },
        dl.pcloudLink?.let { "PCloud" to it },
        dl.youtubeLink?.let { "YouTube" to it },
        dl.pixeldrainLink?.let { "Pixeldrain" to it },
    )
    if (links.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeCard(), RoundedCornerShape(12.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dl.resolution, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
            if (dl.fileSize != null) {
                Spacer(Modifier.width(8.dp))
                Text("(${dl.fileSize})", fontSize = 12.sp, color = themeTextTertiary(), fontFamily = InterFont)
            }
        }
        Spacer(Modifier.height(8.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            links.forEach { (name, url) ->
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(NexiRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .bounceClick { 
                            com.nexiplay.app.data.util.AdManager.showInterstitialAd(context) {
                                trackDownload(context, movie, name, dl.resolution)
                                openLink(url, context, navController) 
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                }
            }
        }
    }
}

@Composable
private fun EpisodeDownloadLinkCard(dl: EpisodeDownloadLink, movie: Movie, ep: Episode, seasonNumber: Int?, context: android.content.Context, navController: NavController) {
    val links = listOfNotNull(
        dl.megaLink?.let { "Mega" to it },
        dl.gdriveLink?.let { "GDrive" to it },
        dl.mediafireLink?.let { "MediaFire" to it },
        dl.teraboxLink?.let { "TeraBox" to it },
        dl.pcloudLink?.let { "PCloud" to it },
        dl.youtubeLink?.let { "YouTube" to it },
    )
    if (links.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeBg(), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(dl.resolution, fontSize = 13.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
            if (dl.fileSize != null) {
                Spacer(Modifier.width(8.dp))
                Text("(${dl.fileSize})", fontSize = 11.sp, color = themeTextTertiary(), fontFamily = InterFont)
            }
        }
        Spacer(Modifier.height(8.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            links.forEach { (name, url) ->
                Box(
                    modifier = Modifier
                        .height(30.dp)
                        .background(NexiRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                        .bounceClick { 
                            com.nexiplay.app.data.util.AdManager.showInterstitialAd(context) {
                                trackDownload(context, movie, name, dl.resolution, ep.id, seasonNumber, ep.episodeNumber)
                                openLink(url, context, navController) 
                            }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(name, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NexiRed, fontFamily = InterFont)
                }
            }
        }
    }
}
