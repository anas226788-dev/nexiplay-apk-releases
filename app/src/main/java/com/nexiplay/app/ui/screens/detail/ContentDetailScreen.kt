package com.nexiplay.app.ui.screens.detail

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.DownloadLink
import com.nexiplay.app.data.model.Episode
import com.nexiplay.app.data.model.EpisodeDownloadLink
import com.nexiplay.app.data.model.EventMetadata
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.Season
import com.nexiplay.app.data.model.StreamingRow
import com.nexiplay.app.data.model.UserEvent
import com.nexiplay.app.data.repository.WebFeaturesRepository
import com.nexiplay.app.ui.components.CommentSection
import com.nexiplay.app.ui.components.ErrorView
import com.nexiplay.app.ui.components.MovieCard
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.screens.browse.BrowseViewModel
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import org.json.JSONObject

private fun sanitizeDetailImageUrl(raw: String?): String? {
    val trimmed = raw?.trim()?.takeIf { it.isNotEmpty() && it != "null" } ?: return null
    return when {
        trimmed.startsWith("http://", ignoreCase = true) -> "https://" + trimmed.substring(7)
        trimmed.startsWith("//") -> "https:$trimmed"
        else -> trimmed
    }
}

private fun DownloadLink.hasAnyValidLink(): Boolean {
    return !gdriveLink.isNullOrBlank() ||
        !megaLink.isNullOrBlank() ||
        !mediafireLink.isNullOrBlank() ||
        !teraboxLink.isNullOrBlank() ||
        !pcloudLink.isNullOrBlank() ||
        !pixeldrainLink.isNullOrBlank() ||
        !youtubeLink.isNullOrBlank()
}

private fun EpisodeDownloadLink.hasAnyValidLink(): Boolean {
    return !gdriveLink.isNullOrBlank() ||
        !megaLink.isNullOrBlank() ||
        !mediafireLink.isNullOrBlank() ||
        !teraboxLink.isNullOrBlank() ||
        !pcloudLink.isNullOrBlank() ||
        !pixeldrainLink.isNullOrBlank() ||
        !youtubeLink.isNullOrBlank()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ContentDetailScreen(
    navController: NavController,
    type: String,
    slug: String,
    vm: DetailViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedSeasonIdx by remember { mutableIntStateOf(0) }
    var selectedEpisodePageIdx by remember { mutableIntStateOf(0) }
    var expandedEpisodeId by remember { mutableStateOf<String?>(null) }
    var episodeSearchQuery by remember { mutableStateOf("") }
    var isSynopsisExpanded by remember { mutableStateOf(false) }
    var zoomScreenshotUrl by remember { mutableStateOf<String?>(null) }

    // Scoped Fast Direct Download state (keyed by item prefix so episodes don't collide)
    var vaultLoadingKey by remember { mutableStateOf<String?>(null) }
    var vaultErrors by remember { mutableStateOf<Map<String, String>>(emptyMap()) }

    val triggerVaultDownload: (String, Boolean, Int?, Int?, String) -> Unit = { keyPrefix, isTv, sNum, eNum, quality ->
        val movie = state.movie
        if (movie != null && vaultLoadingKey == null) {
            val targetId = movie.tmdbId?.takeIf { it.isNotBlank() }
                ?: movie.imdbId?.takeIf { it.isNotBlank() }
                ?: movie.id.removePrefix("tmdb-")
            val fullKey = "${keyPrefix}_$quality"
            vaultLoadingKey = fullKey
            vaultErrors = vaultErrors - keyPrefix
            Toast.makeText(context, "Generating $quality cloud download link...", Toast.LENGTH_SHORT).show()

            val fallbackUrls = buildList<String> {
                val seasonObj = if (isTv) state.seasons.firstOrNull { it.seasonNumber == (sNum ?: 1) } else null
                val epObj = seasonObj?.let { s ->
                    state.episodes[s.id]?.firstOrNull { it.episodeNumber == (eNum ?: 1) }
                }
                if (isTv) {
                    if (epObj != null) {
                        epObj.streamingUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                        epObj.streamingUrlToonplay?.takeIf { it.isNotBlank() }?.let { add(it) }
                        epObj.streamingUrlAnimerulz?.takeIf { it.isNotBlank() }?.let { add(it) }
                        state.episodeDownloads[epObj.id].orEmpty().forEach { dl ->
                            listOfNotNull(dl.gdriveLink, dl.megaLink, dl.mediafireLink, dl.pixeldrainLink, dl.teraboxLink, dl.pcloudLink)
                                .filter { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }
                                .forEach { add(it) }
                        }
                    }
                    // Also include any codedew/zipper or rareanimes URL from any episode (since relatedData maps all seasons/episodes)
                    state.episodes.values.flatten().firstOrNull {
                        val u = it.streamingUrl.orEmpty()
                        u.contains("codedew.com/zipper", ignoreCase = true) ||
                            u.contains("/zipper/", ignoreCase = true) ||
                            u.contains("rareanimes.", ignoreCase = true) ||
                            u.contains("razorshell.space", ignoreCase = true)
                    }?.streamingUrl?.let { add(it) }
                }
                getAvailableServers(
                    type = if (isTv) "series" else "movie",
                    streamingRow = state.streamingRow,
                    movie = movie,
                    episode = epObj,
                    seasonNumber = sNum ?: 1
                ).forEach { srv ->
                    if (srv.url.isNotBlank()) add(srv.url)
                }
                state.streamingRow?.let { row ->
                    row.streamingUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                    row.streamingUrlToonplay?.takeIf { it.isNotBlank() }?.let { add(it) }
                    row.streamingUrlAnimerulz?.takeIf { it.isNotBlank() }?.let { add(it) }
                }
                movie.streamingUrl?.takeIf { it.isNotBlank() }?.let { add(it) }
                state.downloads.forEach { dl ->
                    listOfNotNull(dl.gdriveLink, dl.megaLink, dl.mediafireLink, dl.pixeldrainLink, dl.teraboxLink, dl.pcloudLink)
                        .filter { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }
                        .forEach { add(it) }
                }
            }.distinct()

            scope.launch {
                val isAnimeContent = type.equals("anime", ignoreCase = true) ||
                    movie.type.equals("anime", ignoreCase = true) ||
                    state.categories.any {
                        it.name.equals("Animation", ignoreCase = true) ||
                            it.name.equals("Anime", ignoreCase = true)
                    }
                val res = WebFeaturesRepository.resolveVaultDownload(
                    isTv = isTv,
                    targetId = targetId,
                    season = sNum,
                    episode = eNum,
                    quality = quality,
                    title = movie.title,
                    mediaType = if (isAnimeContent) "anime" else if (isTv) "tv" else "movie",
                    fallbackStreamUrls = fallbackUrls
                )
                vaultLoadingKey = null
                if (res.success && (!res.directUrl.isNullOrBlank() || !res.url.isNullOrBlank())) {
                    val resolvedQuality = res.quality ?: quality
                    trackDownload(
                        context = context,
                        movie = movie,
                        provider = "FastDirectVault",
                        resolution = resolvedQuality,
                        seasonNumber = sNum,
                        episodeNumber = eNum
                    )
                    val directTarget = res.directUrl?.takeIf { it.isNotBlank() }
                    val proxyOrDirectTarget = res.url?.takeIf { it.isNotBlank() } ?: directTarget
                    val isCloudMirror = res.source.equals("toonplay", ignoreCase = true) ||
                        directTarget?.let { u ->
                            val l = u.lowercase()
                            l.contains("hubcloud.") || l.contains("gdflix.") || l.contains("drive.google.") || l.contains("mega.") || l.contains("mediafire.") || l.contains("terabox.") || l.contains("1024terabox.") || l.contains("pcloud.") || l.contains("fpgo.") || l.contains("adhlinks.") || l.contains("animesalt.") || l.contains("razorshell.")
                        } == true

                    val safeFileName = (res.fileName?.takeIf { it.isNotBlank() }
                        ?: "${movie.title.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_${if (sNum != null && eNum != null) "S${sNum}E${eNum}_" else ""}${resolvedQuality}.${if (proxyOrDirectTarget?.contains(".mp4", true) == true && proxyOrDirectTarget.contains(".mkv", true) == false) "mp4" else "mkv"}")
                    var enqueuedNatively = false

                    if (isCloudMirror) {
                        val cloudUrl = res.url ?: directTarget ?: proxyOrDirectTarget
                        if (!cloudUrl.isNullOrBlank()) {
                            openLink(cloudUrl, context, navController)
                            Toast.makeText(context, "Opening cloud download mirror ($resolvedQuality)...", Toast.LENGTH_SHORT).show()
                        }
                    } else if (directTarget != null && proxyOrDirectTarget != null) {
                        try {
                            val dm = context.getSystemService(android.content.Context.DOWNLOAD_SERVICE) as? android.app.DownloadManager
                            if (dm != null) {
                                // Use the Vercel proxy URL (res.url) so mobile ISPs that block *.workers.dev still download at full speed
                                val request = android.app.DownloadManager.Request(Uri.parse(proxyOrDirectTarget)).apply {
                                    addRequestHeader("Referer", when { proxyOrDirectTarget.contains("animeheaven.", true) -> "https://animeheaven.me/"; proxyOrDirectTarget.contains("hakunaymatata.", true) -> "https://netfilm.world/"; else -> "https://vidvault.to/" })
                                    addRequestHeader(
                                        "User-Agent",
                                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                                    )
                                    setTitle(safeFileName)
                                    setDescription("Downloading ${movie.title} ($resolvedQuality)")
                                    setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                                    setAllowedOverMetered(true)
                                    setAllowedOverRoaming(true)
                                    setDestinationInExternalPublicDir(android.os.Environment.DIRECTORY_DOWNLOADS, safeFileName)
                                }
                                val dlId = dm.enqueue(request); com.nexiplay.app.data.repository.DownloadRepository(context).saveDownloadRecord(dlId, "${movie.title}${if (sNum != null && eNum != null) " S${sNum}E${eNum}" else ""} ($resolvedQuality)", proxyOrDirectTarget)
                                enqueuedNatively = true
                                Toast.makeText(context, "Download started in notification bar ($resolvedQuality)!", Toast.LENGTH_LONG).show()
                            }
                        } catch (_: Exception) {
                            enqueuedNatively = false
                        }
                    }
                    if (!isCloudMirror && !enqueuedNatively && !res.url.isNullOrBlank()) {
                        openLink(res.url, context, navController)
                    }
                } else {
                    val err = res.error ?: "Cloud direct link unavailable for this title/episode."
                    vaultErrors = vaultErrors + (keyPrefix to err)
                    Toast.makeText(context, err, Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    LaunchedEffect(type, slug) {
        vm.loadContent(type, slug)
    }

    // Auto-select matching season if title specifies a single "(Season X)" (ignore ranges like "Season 1-22")
    LaunchedEffect(state.seasons, state.movie?.title) {
        if (state.seasons.isNotEmpty()) {
            val title = state.movie?.title.orEmpty()
            val matchNum = Regex("""(?:season|s)\s*(\d+)(?!\s*[-–to\d])""", RegexOption.IGNORE_CASE)
                .find(title)?.groupValues?.getOrNull(1)?.toIntOrNull()
            val targetIdx = if (matchNum != null) {
                state.seasons.indexOfFirst { it.seasonNumber == matchNum }.takeIf { it >= 0 } ?: 0
            } else {
                0
            }
            selectedSeasonIdx = targetIdx.coerceIn(0, (state.seasons.size - 1).coerceAtLeast(0))
            selectedEpisodePageIdx = 0
            expandedEpisodeId = null
            episodeSearchQuery = ""
        }
    }

    if (state.isLoading) {
        DetailSkeleton()
        return
    }

    val movie = state.movie
    if (movie == null) {
        Box(
            Modifier
                .fillMaxSize()
                .background(themeBg()),
            contentAlignment = Alignment.Center
        ) {
            ErrorView(
                message = state.error ?: "Content not found.",
                onRetry = { vm.loadContent(type, slug) }
            )
        }
        return
    }

    val actualType = state.resolvedType
    val isSeries = actualType == "series" || actualType == "anime"

    // Fullscreen Screenshot Lightbox Dialog
    if (zoomScreenshotUrl != null) {
        Dialog(
            onDismissRequest = { zoomScreenshotUrl = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.92f))
                    .clickable { zoomScreenshotUrl = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = ImageRequest.Builder(context)
                        .data(zoomScreenshotUrl)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Screenshot Preview",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp)
                        .clip(RoundedCornerShape(14.dp))
                )
                IconButton(
                    onClick = { zoomScreenshotUrl = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 42.dp, end = 18.dp)
                        .background(Color.White.copy(alpha = 0.15f), CircleShape)
                ) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                }
            }
        }
    }

    val heroBannerUrl = remember(movie.bannerUrlDesktop, movie.bannerUrlMobile, movie.posterUrl) {
        sanitizeDetailImageUrl(movie.bannerUrlDesktop)
            ?: sanitizeDetailImageUrl(movie.bannerUrlMobile)
            ?: sanitizeDetailImageUrl(movie.posterUrl)
    }
    val posterUrl = remember(movie.posterUrl, movie.bannerUrlMobile) {
        sanitizeDetailImageUrl(movie.posterUrl)
            ?: sanitizeDetailImageUrl(movie.bannerUrlMobile)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
            .verticalScroll(rememberScrollState()),
    ) {
        // ── 1. HERO BANNER + FLOATING TOP BAR + OVERLAPPING POSTER & HEADER ──
        Box(modifier = Modifier.fillMaxWidth()) {
            // Backdrop Image (260.dp)
            val heroBannerRequest = remember(heroBannerUrl) {
                ImageRequest.Builder(context)
                    .data(heroBannerUrl)
                    .size(1080, 620)
                    .crossfade(180)
                    .build()
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(260.dp)
            ) {
                AsyncImage(
                    model = heroBannerRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                // Multi-stop cinematic gradient into themeBg()
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to Color.Black.copy(alpha = 0.45f),
                                    0.45f to Color.Black.copy(alpha = 0.65f),
                                    0.82f to themeBg().copy(alpha = 0.94f),
                                    1.0f to themeBg()
                                )
                            )
                        )
                )
            }

            // Floating Top Navigation & Quick Actions Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { navController.popBackStack() },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                        .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Quick Watchlist Toggle Icon
                    IconButton(
                        onClick = {
                            vm.toggleWatchlist(
                                context = context,
                                onRequireLogin = { navController.navigate(Screen.Login.route) }
                            )
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                if (state.isInWatchlist) NexiRed.copy(alpha = 0.85f) else Color.Black.copy(alpha = 0.55f),
                                CircleShape
                            )
                            .border(
                                1.dp,
                                if (state.isInWatchlist) NexiRed else Color.White.copy(alpha = 0.16f),
                                CircleShape
                            )
                    ) {
                        Icon(
                            imageVector = if (state.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Watchlist",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Native Share Button
                    IconButton(
                        onClick = {
                            val shareUrl = "${WebFeaturesRepository.BASE_WEB_URL}/$actualType/${movie.slug}"
                            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                this.type = "text/plain"
                                putExtra(Intent.EXTRA_SUBJECT, movie.title)
                                putExtra(Intent.EXTRA_TEXT, "Watch & Download ${movie.title} in HD on NexiPlay:\n$shareUrl")
                            }
                            context.startActivity(Intent.createChooser(shareIntent, "Share ${movie.title}"))
                        },
                        modifier = Modifier
                            .size(40.dp)
                            .background(Color.Black.copy(alpha = 0.55f), CircleShape)
                            .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                    ) {
                        Icon(Icons.Default.Share, contentDescription = "Share", tint = Color.White, modifier = Modifier.size(19.dp))
                    }
                }
            }

            // Overlapping Poster + Title + Badges (starts at 135.dp from top of backdrop — zero negative spacer bug!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 135.dp, start = 16.dp, end = 16.dp),
                verticalAlignment = Alignment.Bottom
            ) {
                // Poster Card with glow border
                Box(
                    modifier = Modifier
                        .width(114.dp)
                        .height(168.dp)
                        .shadow(16.dp, RoundedCornerShape(14.dp))
                        .clip(RoundedCornerShape(14.dp))
                        .background(themeCard())
                        .border(
                            1.5.dp,
                            Brush.linearGradient(
                                listOf(
                                    NexiRed.copy(alpha = 0.7f),
                                    Color.White.copy(alpha = 0.2f)
                                )
                            ),
                            RoundedCornerShape(14.dp)
                        )
                ) {
                    val posterImageRequest = remember(posterUrl) {
                        ImageRequest.Builder(context)
                            .data(posterUrl)
                            .size(340, 500)
                            .crossfade(180)
                            .build()
                    }
                    AsyncImage(
                        model = posterImageRequest,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(bottom = 2.dp)
                ) {
                    // Metadata Badges Row (Type, Year, Format, Running Status)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(
                                    Brush.horizontalGradient(listOf(NexiRed, Color(0xFF6D28D9))),
                                    RoundedCornerShape(6.dp)
                                )
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                actualType.uppercase(),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color.White,
                                fontFamily = InterFont,
                            )
                        }

                        if (movie.releaseYear != null) {
                            Surface(
                                color = if (isDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.07f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "${movie.releaseYear}",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = themeTextPrimary(),
                                    fontFamily = InterFont,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }

                        Surface(
                            color = NexiRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                movie.format?.takeIf { it.isNotBlank() && !it.contains("min") } ?: "HD",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = if (isDark) Color(0xFFC4B5FD) else NexiRed,
                                fontFamily = InterFont,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }

                        if (movie.isRunning == true) {
                            Surface(
                                color = Color(0xFF10B981).copy(alpha = 0.18f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "● ONGOING",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF10B981),
                                    fontFamily = InterFont,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        } else if (isSeries) {
                            Surface(
                                color = Color(0xFF06B6D4).copy(alpha = 0.16f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    "✓ COMPLETED",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Black,
                                    color = Color(0xFF06B6D4),
                                    fontFamily = InterFont,
                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                                )
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Title
                    Text(
                        movie.title,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Black,
                        color = themeTextPrimary(),
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 24.sp,
                        fontFamily = InterFont,
                    )

                    if (!state.tagline.isNullOrBlank()) {
                        Spacer(Modifier.height(3.dp))
                        Text(
                            "\"${state.tagline}\"",
                            fontSize = 11.sp,
                            fontStyle = FontStyle.Italic,
                            color = themeTextSecondary(),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = InterFont
                        )
                    }

                    // Clickable Genre Pills (Navigates to Browse with Category filter)
                    if (state.categories.isNotEmpty()) {
                        Spacer(Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            state.categories.take(4).forEach { cat ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(themeCard())
                                        .border(1.dp, themeBorder(), RoundedCornerShape(8.dp))
                                        .bounceClick {
                                            val browseTab = when (actualType) {
                                                "anime" -> 2
                                                "series" -> 1
                                                else -> 0
                                            }
                                            BrowseViewModel.navigateWithFilter(
                                                tab = browseTab,
                                                categorySlug = cat.slug.ifBlank { cat.name.lowercase().replace(" ", "-") }
                                            )
                                            navController.navigate(Screen.Browse.route)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        cat.name,
                                        fontSize = 10.sp,
                                        color = themeTextSecondary(),
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = InterFont,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // ── 2. WEBSITE HERO QUICK STATS BAR (Score ★, Duration, Audio, Studio) ──
        val cleanAudioStat = remember(movie.language) {
            val raw = (movie.language ?: "Multi")
                .replace("[", "")
                .replace("]", "")
                .trim()
            when {
                raw.contains(",") && raw.split(",").size >= 3 -> "MULTI-AUDIO"
                raw.contains("-") && raw.split("-").size >= 3 -> "MULTI-AUDIO"
                raw.length > 14 -> "MULTI-AUDIO"
                else -> raw.uppercase()
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(themeCard())
                .border(1.dp, themeBorder(), RoundedCornerShape(16.dp))
                .padding(vertical = 11.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            QuickStatColumn(
                label = "SCORE",
                value = "★ ${state.ratingScore}",
                valueColor = Color(0xFFFBBF24),
                modifier = Modifier.weight(1f)
            )
            QuickStatDivider()
            QuickStatColumn(
                label = "DURATION",
                value = state.duration,
                valueColor = themeTextPrimary(),
                modifier = Modifier.weight(1f)
            )
            QuickStatDivider()
            QuickStatColumn(
                label = "AUDIO",
                value = cleanAudioStat,
                valueColor = themeTextPrimary(),
                modifier = Modifier.weight(1f)
            )
            QuickStatDivider()
            QuickStatColumn(
                label = "STUDIO",
                value = state.studioName,
                valueColor = themeTextPrimary(),
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(14.dp))

        // ── 3. POWER ACTION BUTTONS ROW (Watch Now, Add to List, Trailer) ──
        val isWatchAvailable = remember(movie, state.streamingRow, state.episodes) {
            val isStreamingDisabled = state.streamingRow?.streamingUrl == "disabled" || movie.appStreamingEnabled == false
            val hasEpisodeStream = state.episodes.values.any { seasonEps ->
                seasonEps.any {
                    (!it.streamingUrl.isNullOrBlank() && it.streamingUrl != "disabled") ||
                        !it.streamingUrlToonplay.isNullOrBlank() ||
                        !it.streamingUrlAnimerulz.isNullOrBlank()
                }
            }
            val hasCustomStream = (!movie.streamingUrl.isNullOrBlank() && movie.streamingUrl != "disabled") ||
                !movie.streamingUrlToonplay.isNullOrBlank() ||
                !movie.streamingUrlAnimerulz.isNullOrBlank() ||
                !movie.toonplayUrl.isNullOrBlank() ||
                !movie.animerulzUrl.isNullOrBlank() ||
                (!state.streamingRow?.streamingUrl.isNullOrBlank() && state.streamingRow?.streamingUrl != "disabled") ||
                !state.streamingRow?.streamingUrlToonplay.isNullOrBlank() ||
                !state.streamingRow?.streamingUrlAnimerulz.isNullOrBlank() ||
                !state.streamingRow?.toonplayUrl.isNullOrBlank() ||
                !state.streamingRow?.animerulzUrl.isNullOrBlank() ||
                !state.streamingRow?.multiScraperConfig.isNullOrBlank() ||
                (movie.scraperSource == "multi" && !movie.scraperUrl.isNullOrBlank())

            val hasBuiltInIds = !movie.tmdbId.isNullOrBlank() ||
                !movie.imdbId.isNullOrBlank() ||
                !state.streamingRow?.tmdbId.isNullOrBlank() ||
                !state.streamingRow?.imdbId.isNullOrBlank()

            !isStreamingDisabled && (hasCustomStream || hasEpisodeStream || hasBuiltInIds)
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (isWatchAvailable) {
                Box(
                    modifier = Modifier
                        .weight(1.35f)
                        .height(48.dp)
                        .shadow(10.dp, RoundedCornerShape(14.dp), spotColor = NexiRed)
                        .clip(RoundedCornerShape(14.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(NexiRed, Color(0xFF6D28D9))
                            )
                        )
                        .bounceClick {
                            if (isSeries && state.seasons.isNotEmpty()) {
                                val chosenSeason = state.seasons.getOrNull(selectedSeasonIdx) ?: state.seasons.first()
                                val firstEp = state.episodes[chosenSeason.id]?.firstOrNull()
                                DetailViewModel.setPendingWatchTarget(
                                    seasonNumber = chosenSeason.seasonNumber,
                                    episodeNumber = firstEp?.episodeNumber ?: 1
                                )
                            }
                            navController.navigate("watch/$actualType/${movie.slug}")
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            Icons.Default.PlayArrow,
                            contentDescription = "Watch",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            if (isSeries) "Watch Ep 1" else "Watch Now",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.White,
                            fontFamily = InterFont
                        )
                    }
                }
            }

            // Watchlist Button ("In My List" / "Add to List")
            val wlBg = if (state.isInWatchlist) {
                NexiRed.copy(alpha = 0.15f)
            } else {
                themeCard()
            }
            val wlBorder = if (state.isInWatchlist) {
                NexiRed.copy(alpha = 0.55f)
            } else {
                themeBorder()
            }
            val wlTextColor = if (state.isInWatchlist) {
                if (isDark) Color(0xFFC4B5FD) else NexiRed
            } else {
                themeTextPrimary()
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(wlBg)
                    .border(1.dp, wlBorder, RoundedCornerShape(14.dp))
                    .bounceClick {
                        vm.toggleWatchlist(
                            context = context,
                            onRequireLogin = { navController.navigate(Screen.Login.route) }
                        )
                    },
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(
                        imageVector = if (state.isInWatchlist) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                        contentDescription = "Watchlist",
                        tint = wlTextColor,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(5.dp))
                    Text(
                        text = if (state.isInWatchlist) "In My List" else "Add to List",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = wlTextColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        fontFamily = InterFont
                    )
                }
            }

            // Trailer Button (opens YouTube / external viewer cleanly)
            if (!movie.trailerUrl.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .height(48.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(themeCard())
                        .border(1.dp, themeBorder(), RoundedCornerShape(14.dp))
                        .bounceClick {
                            val tUrl = movie.trailerUrl.trim()
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(tUrl)))
                            } catch (_: Exception) {
                                val encodedUrl = java.net.URLEncoder.encode(tUrl, "UTF-8")
                                navController.navigate("webview/$encodedUrl")
                            }
                        }
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.OndemandVideo,
                            contentDescription = "Trailer",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(5.dp))
                        Text(
                            "Trailer",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary(),
                            fontFamily = InterFont
                        )
                    }
                }
            }
        }

        // ── 4. ADMIN NOTICE & RUNNING SERIES BANNERS ──
        val noticeText = when {
            movie.noticeEnabled == true && !movie.noticeText.isNullOrBlank() -> movie.noticeText
            !movie.adminNote.isNullOrBlank() -> movie.adminNote
            else -> null
        }

        if (!noticeText.isNullOrBlank()) {
            Spacer(Modifier.height(14.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFFF59E0B).copy(alpha = if (isDark) 0.14f else 0.10f))
                    .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = "Notice",
                    tint = Color(0xFFF59E0B),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "IMPORTANT NOTICE",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black,
                        color = Color(0xFFF59E0B),
                        fontFamily = InterFont
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = noticeText,
                        fontSize = 12.sp,
                        color = themeTextPrimary(),
                        lineHeight = 17.sp,
                        fontFamily = InterFont
                    )
                }
            }
        }

        if (!movie.runningNotice.isNullOrBlank()) {
            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF10B981).copy(alpha = if (isDark) 0.12f else 0.08f))
                    .border(1.dp, Color(0xFF10B981).copy(alpha = 0.35f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF10B981))
                )
                Text(
                    text = movie.runningNotice,
                    fontSize = 12.sp,
                    color = themeTextPrimary(),
                    lineHeight = 17.sp,
                    fontFamily = InterFont
                )
            }
        }

        // ── 5. STORYLINE / SYNOPSIS (Expandable) ──
        if (!movie.description.isNullOrBlank()) {
            Spacer(Modifier.height(16.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(themeCard())
                    .border(1.dp, themeBorder(), RoundedCornerShape(16.dp))
                    .clickable { isSynopsisExpanded = !isSynopsisExpanded }
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Storyline",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Black,
                        color = themeTextPrimary(),
                        fontFamily = InterFont
                    )
                    if (movie.description.length > 140) {
                        Text(
                            if (isSynopsisExpanded) "Show Less" else "Read More",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexiRed,
                            fontFamily = InterFont
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    movie.description,
                    fontSize = 13.sp,
                    color = themeTextSecondary(),
                    lineHeight = 19.sp,
                    maxLines = if (isSynopsisExpanded) Int.MAX_VALUE else 3,
                    overflow = TextOverflow.Ellipsis,
                    fontFamily = InterFont,
                )
            }
        }

        // ── 6. SCREENSHOTS GALLERY (with Tap-to-Zoom Lightbox) ──
        if (state.screenshots.isNotEmpty()) {
            Spacer(Modifier.height(20.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Screenshots & Stills",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black,
                    color = themeTextPrimary(),
                    fontFamily = InterFont
                )
                Text(
                    "Tap to enlarge",
                    fontSize = 11.sp,
                    color = themeTextTertiary(),
                    fontFamily = InterFont
                )
            }
            Spacer(Modifier.height(10.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.screenshots, key = { (it.id ?: it.imageUrl).ifBlank { it.hashCode().toString() } }) { shot ->
                    val shotUrl = remember(shot.imageUrl) {
                        sanitizeDetailImageUrl(shot.imageUrl) ?: shot.imageUrl
                    }
                    val shotRequest = remember(shotUrl) {
                        ImageRequest.Builder(context)
                            .data(shotUrl)
                            .size(640, 360)
                            .crossfade(180)
                            .build()
                    }
                    AsyncImage(
                        model = shotRequest,
                        contentDescription = "Screenshot",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .width(220.dp)
                            .height(124.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeCard())
                            .border(1.dp, themeBorder(), RoundedCornerShape(12.dp))
                            .bounceClick { zoomScreenshotUrl = shotUrl },
                    )
                }
            }
        }

        // ── 7. DOWNLOADS (for Movies) ──
        val validMovieDownloads = remember(state.downloads) {
            state.downloads.filter { it.hasAnyValidLink() }
        }
        if (!isSeries) {
            Spacer(Modifier.height(22.dp))
            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "Download Links",
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Black,
                        color = themeTextPrimary(),
                        fontFamily = InterFont
                    )
                    Surface(
                        color = Color(0xFF10B981).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            "✓ HIGH SPEED",
                            color = Color(0xFF10B981),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                        )
                    }
                }
                Spacer(Modifier.height(10.dp))

                if (validMovieDownloads.isNotEmpty()) {
                    validMovieDownloads.forEach { dl ->
                        DownloadLinkCard(dl, movie, context, navController)
                        Spacer(Modifier.height(10.dp))
                    }
                }
                FastDirectDownloadPanel(
                    title = movie.title,
                    isTv = false,
                    targetId = movie.tmdbId ?: movie.imdbId ?: movie.id.removePrefix("tmdb-"),
                    seasonNumber = null,
                    episodeNumber = null,
                    keyPrefix = "movie",
                    loadingKey = vaultLoadingKey,
                    errorMessage = vaultErrors["movie"],
                    onTriggerQuality = { quality ->
                        triggerVaultDownload("movie", false, null, null, quality)
                    }
                )
            }
        }

        // ── 8. SEASONS & EPISODES (for Series & Anime) ──
        if (isSeries && state.seasons.isNotEmpty()) {
            Spacer(Modifier.height(22.dp))
            val safeSeasonIdx = selectedSeasonIdx.coerceIn(0, state.seasons.size - 1)
            val currentSeason = state.seasons[safeSeasonIdx]
            val allSeasonEpisodes = state.episodes[currentSeason.id] ?: emptyList()

            val filteredEpisodes = remember(allSeasonEpisodes, episodeSearchQuery) {
                if (episodeSearchQuery.isBlank()) {
                    allSeasonEpisodes
                } else {
                    val q = episodeSearchQuery.trim().lowercase()
                    allSeasonEpisodes.filter { ep ->
                        ep.episodeNumber.toString().contains(q) ||
                            (ep.episodeTitle?.lowercase()?.contains(q) == true)
                    }
                }
            }

            Column(modifier = Modifier.padding(horizontal = 16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            "Seasons & Episodes",
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Black,
                            color = themeTextPrimary(),
                            fontFamily = InterFont
                        )
                        Surface(
                            color = NexiRed.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(50)
                        ) {
                            Text(
                                "${allSeasonEpisodes.size} EPS",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = if (isDark) Color(0xFFC4B5FD) else NexiRed,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))

                // Season Selector Tabs
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.seasons.forEachIndexed { idx, season ->
                        val selected = safeSeasonIdx == idx
                        val epCount = state.episodes[season.id]?.size ?: 0
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    if (selected) Brush.horizontalGradient(listOf(NexiRed, Color(0xFF6D28D9)))
                                    else Brush.horizontalGradient(listOf(themeCard(), themeCard()))
                                )
                                .border(
                                    1.dp,
                                    if (selected) NexiRed else themeBorder(),
                                    RoundedCornerShape(12.dp)
                                )
                                .bounceClick {
                                    selectedSeasonIdx = idx
                                    selectedEpisodePageIdx = 0
                                    expandedEpisodeId = null
                                }
                                .padding(horizontal = 14.dp, vertical = 9.dp)
                        ) {
                            Text(
                                "${season.seasonTitle ?: "Season ${season.seasonNumber}"} ($epCount)",
                                fontSize = 12.sp,
                                fontWeight = if (selected) FontWeight.Black else FontWeight.SemiBold,
                                color = if (selected) Color.White else themeTextSecondary(),
                                fontFamily = InterFont,
                            )
                        }
                    }
                }

                // Complete Season Batch ZIP Download Card (if season has zip links)
                val zipLinks = remember(currentSeason) {
                    buildList {
                        currentSeason.zip1080p?.takeIf { it.isNotBlank() }?.let { add("1080p ZIP" to it) }
                        currentSeason.zip720p?.takeIf { it.isNotBlank() }?.let { add("720p ZIP" to it) }
                        currentSeason.zip480p?.takeIf { it.isNotBlank() }?.let { add("480p ZIP" to it) }
                        currentSeason.zip360p?.takeIf { it.isNotBlank() }?.let { add("360p ZIP" to it) }
                        currentSeason.seasonZipLink?.takeIf { it.isNotBlank() }?.let { add("Full Season ZIP" to it) }
                    }
                }

                if (zipLinks.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color(0xFFF59E0B).copy(alpha = if (isDark) 0.12f else 0.08f))
                            .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Default.FolderZip,
                                contentDescription = null,
                                tint = Color(0xFFF59E0B),
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                "Complete Season ${currentSeason.seasonNumber} Batch ZIP",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Black,
                                color = themeTextPrimary(),
                                fontFamily = InterFont
                            )
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            zipLinks.forEach { (label, url) ->
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color(0xFFF59E0B))
                                        .bounceClick {
                                            trackDownload(
                                                context = context,
                                                movie = movie,
                                                provider = "SeasonZip",
                                                resolution = label,
                                                seasonNumber = currentSeason.seasonNumber
                                            )
                                            openLink(url, context, navController)
                                        }
                                        .padding(horizontal = 12.dp, vertical = 7.dp)
                                ) {
                                    Text(
                                        "⬇ $label",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.Black,
                                        fontFamily = InterFont
                                    )
                                }
                            }
                        }
                    }
                }

                // Episode Search Filter (when > 6 episodes)
                if (allSeasonEpisodes.size > 6) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = episodeSearchQuery,
                        onValueChange = { episodeSearchQuery = it },
                        placeholder = {
                            Text(
                                "Search episode number or title...",
                                fontSize = 12.sp,
                                color = themeTextTertiary()
                            )
                        },
                        leadingIcon = {
                            Icon(
                                Icons.Default.Search,
                                contentDescription = null,
                                tint = themeTextTertiary(),
                                modifier = Modifier.size(18.dp)
                            )
                        },
                        trailingIcon = {
                            if (episodeSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { episodeSearchQuery = "" }) {
                                    Icon(
                                        Icons.Default.Close,
                                        contentDescription = "Clear",
                                        tint = themeTextTertiary(),
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = NexiRed,
                            unfocusedBorderColor = themeBorder(),
                            focusedContainerColor = themeCard(),
                            unfocusedContainerColor = themeCard(),
                            focusedTextColor = themeTextPrimary(),
                            unfocusedTextColor = themeTextPrimary()
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))

                if (filteredEpisodes.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeCard())
                            .padding(20.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No matching episodes found.",
                            color = themeTextTertiary(),
                            fontSize = 13.sp,
                            fontFamily = InterFont
                        )
                    }
                } else {
                    val pageChunkSize = 30
                    val episodeChunks = remember(filteredEpisodes) {
                        filteredEpisodes.chunked(pageChunkSize)
                    }
                    val safeEpPageIdx = selectedEpisodePageIdx.coerceIn(0, (episodeChunks.size - 1).coerceAtLeast(0))
                    val visibleEpisodes = if (episodeSearchQuery.isNotBlank()) {
                        filteredEpisodes.take(30)
                    } else {
                        episodeChunks.getOrNull(safeEpPageIdx) ?: emptyList()
                    }

                    if (episodeSearchQuery.isBlank() && episodeChunks.size > 1) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            episodeChunks.forEachIndexed { chunkIdx, chunk ->
                                val firstEpNum = chunk.firstOrNull()?.episodeNumber ?: (chunkIdx * pageChunkSize + 1)
                                val lastEpNum = chunk.lastOrNull()?.episodeNumber ?: ((chunkIdx + 1) * pageChunkSize)
                                val isChunkSelected = safeEpPageIdx == chunkIdx
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(9.dp))
                                        .background(
                                            if (isChunkSelected) NexiRed.copy(alpha = 0.2f) else themeCard()
                                        )
                                        .border(
                                            1.dp,
                                            if (isChunkSelected) NexiRed else themeBorder(),
                                            RoundedCornerShape(9.dp)
                                        )
                                        .bounceClick {
                                            selectedEpisodePageIdx = chunkIdx
                                            expandedEpisodeId = null
                                        }
                                        .padding(horizontal = 11.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        "EP $firstEpNum–$lastEpNum",
                                        fontSize = 11.sp,
                                        fontWeight = if (isChunkSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                        color = if (isChunkSelected) (if (isDark) Color(0xFFC4B5FD) else NexiRed) else themeTextSecondary(),
                                        fontFamily = InterFont
                                    )
                                }
                            }
                        }
                    }

                    visibleEpisodes.forEach { ep ->
                        key(ep.id) {
                        val epDls = remember(state.episodeDownloads, ep.id) {
                            (state.episodeDownloads[ep.id] ?: emptyList()).filter { it.hasAnyValidLink() }
                        }
                        val isExpanded = expandedEpisodeId == ep.id
                        val epKeyPrefix = "ep_${currentSeason.seasonNumber}_${ep.episodeNumber}"

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(themeCard())
                                .border(
                                    1.dp,
                                    if (isExpanded) NexiRed.copy(alpha = 0.5f) else themeBorder(),
                                    RoundedCornerShape(14.dp)
                                )
                                .clickable {
                                    expandedEpisodeId = if (isExpanded) null else ep.id
                                }
                                .padding(12.dp),
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                // Episode Number Badge
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(
                                            if (isExpanded) NexiRed else NexiRed.copy(alpha = 0.14f)
                                        ),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        "${ep.episodeNumber}",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Black,
                                        color = if (isExpanded) Color.White else if (isDark) Color(0xFFC4B5FD) else NexiRed,
                                        fontFamily = InterFont
                                    )
                                }

                                Spacer(Modifier.width(11.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        ep.episodeTitle?.takeIf { it.isNotBlank() } ?: "Episode ${ep.episodeNumber}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = themeTextPrimary(),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        fontFamily = InterFont
                                    )
                                    Text(
                                        if (epDls.isNotEmpty()) "${epDls.size} Cloud Mirror(s) • Direct MKV"
                                        else "Direct MKV Download & Stream",
                                        fontSize = 11.sp,
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.SemiBold,
                                        fontFamily = InterFont
                                    )
                                }

                                // Direct Per-Episode "Watch" Pill Button
                                if (isWatchAvailable) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(9.dp))
                                            .background(NexiRed.copy(alpha = 0.16f))
                                            .border(1.dp, NexiRed.copy(alpha = 0.4f), RoundedCornerShape(9.dp))
                                            .bounceClick {
                                                DetailViewModel.setPendingWatchTarget(
                                                    seasonNumber = currentSeason.seasonNumber,
                                                    episodeNumber = ep.episodeNumber
                                                )
                                                navController.navigate("watch/$actualType/${movie.slug}")
                                            }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                                        ) {
                                            Icon(
                                                Icons.Default.PlayArrow,
                                                contentDescription = "Watch Episode",
                                                tint = if (isDark) Color(0xFFC4B5FD) else NexiRed,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                "Watch",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.ExtraBold,
                                                color = if (isDark) Color(0xFFC4B5FD) else NexiRed,
                                                fontFamily = InterFont
                                            )
                                        }
                                    }
                                    Spacer(Modifier.width(6.dp))
                                }

                                Icon(
                                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                    contentDescription = if (isExpanded) "Collapse" else "Expand",
                                    tint = themeTextSecondary(),
                                )
                            }

                            AnimatedVisibility(visible = isExpanded) {
                                Column(modifier = Modifier.padding(top = 12.dp)) {
                                    if (epDls.isNotEmpty()) {
                                        epDls.forEach { dl ->
                                            EpisodeDownloadLinkCard(
                                                dl = dl,
                                                movie = movie,
                                                ep = ep,
                                                seasonNumber = currentSeason.seasonNumber,
                                                context = context,
                                                navController = navController
                                            )
                                            Spacer(Modifier.height(8.dp))
                                        }
                                    }
                                    FastDirectDownloadPanel(
                                        title = "${movie.title} S${currentSeason.seasonNumber}E${ep.episodeNumber}",
                                        isTv = true,
                                        targetId = movie.tmdbId ?: movie.imdbId ?: movie.id.removePrefix("tmdb-"),
                                        seasonNumber = currentSeason.seasonNumber,
                                        episodeNumber = ep.episodeNumber,
                                        keyPrefix = epKeyPrefix,
                                        loadingKey = vaultLoadingKey,
                                        errorMessage = vaultErrors[epKeyPrefix],
                                        compact = true,
                                        onTriggerQuality = { quality ->
                                            triggerVaultDownload(
                                                epKeyPrefix,
                                                true,
                                                currentSeason.seasonNumber,
                                                ep.episodeNumber,
                                                quality
                                            )
                                        }
                                    )
                                }
                            }
                        }
                        }
                    }
                }
            }
        }

        // ── 9. WEBSITE DETAILS & CAST SECTION CARD ──
        Spacer(Modifier.height(22.dp))
        WebsiteMetaSectionCard(
            movie = movie,
            actualType = actualType,
            studioName = state.studioName,
            statusText = state.statusText
        )

        // ── 10. TELEGRAM COMMUNITY BANNER (Matches page.tsx line 624) ──
        Spacer(Modifier.height(16.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color(0xFF0088CC).copy(alpha = if (isDark) 0.18f else 0.12f),
                            Color(0xFF229ED9).copy(alpha = if (isDark) 0.10f else 0.06f)
                        )
                    )
                )
                .border(1.dp, Color(0xFF0088CC).copy(alpha = 0.35f), RoundedCornerShape(16.dp))
                .bounceClick {
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://t.me/+W8e4f9tD-tU0N2U1")))
                    } catch (_: Exception) {}
                }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF0088CC)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Send,
                        contentDescription = "Telegram",
                        tint = Color.White,
                        modifier = Modifier.size(17.dp)
                    )
                }
                Column {
                    Text(
                        "Join NexiPlay Telegram Community",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        color = themeTextPrimary(),
                        fontFamily = InterFont
                    )
                    Text(
                        "Get instant episode alerts, broken link fixes & requests",
                        fontSize = 11.sp,
                        color = themeTextSecondary(),
                        fontFamily = InterFont
                    )
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Color(0xFF0088CC)
            )
        }

        // ── 11. RELATED CONTENT ("You May Also Like") ──
        if (state.relatedMovies.isNotEmpty()) {
            Spacer(Modifier.height(24.dp))
            Text(
                "You May Also Like",
                fontSize = 17.sp,
                fontWeight = FontWeight.Black,
                color = themeTextPrimary(),
                fontFamily = InterFont,
                modifier = Modifier.padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(12.dp))
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.relatedMovies, key = { it.id }) { related ->
                    MovieCard(
                        movie = related,
                        onClick = {
                            navController.navigate(Screen.ContentDetail.createRoute(related.type, related.slug))
                        },
                        modifier = Modifier.width(130.dp),
                    )
                }
            }
        }

        // ── 12. COMMENTS SECTION ──
        Spacer(Modifier.height(28.dp))
        CommentSection(movieId = movie.id)

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun DetailSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp),
            cornerRadius = 0
        )
        Spacer(Modifier.height(16.dp))
        Row(modifier = Modifier.padding(horizontal = 16.dp)) {
            ShimmerBox(
                modifier = Modifier
                    .width(114.dp)
                    .height(168.dp),
                cornerRadius = 14
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Spacer(Modifier.height(20.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.5f)
                        .height(18.dp),
                    cornerRadius = 6
                )
                Spacer(Modifier.height(12.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .height(24.dp),
                    cornerRadius = 6
                )
                Spacer(Modifier.height(8.dp))
                ShimmerBox(
                    modifier = Modifier
                        .fillMaxWidth(0.7f)
                        .height(16.dp),
                    cornerRadius = 6
                )
            }
        }
        Spacer(Modifier.height(20.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(54.dp),
            cornerRadius = 16
        )
        Spacer(Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ShimmerBox(
                modifier = Modifier
                    .weight(1.3f)
                    .height(48.dp),
                cornerRadius = 14
            )
            ShimmerBox(
                modifier = Modifier
                    .weight(1f)
                    .height(48.dp),
                cornerRadius = 14
            )
        }
        Spacer(Modifier.height(16.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(110.dp),
            cornerRadius = 16
        )
    }
}

@Composable
private fun QuickStatColumn(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Black,
            color = themeTextTertiary(),
            fontFamily = InterFont
        )
        Spacer(Modifier.height(3.dp))
        Text(
            text = value,
            fontSize = 12.sp,
            fontWeight = FontWeight.Black,
            color = valueColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            fontFamily = InterFont
        )
    }
}

@Composable
private fun QuickStatDivider() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(26.dp)
            .background(themeBorder())
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WebsiteMetaSectionCard(
    movie: Movie,
    actualType: String,
    studioName: String,
    statusText: String
) {
    val specs = remember(movie, actualType, studioName, statusText) {
        listOf(
            Triple("🎞️", "FORMAT", movie.format ?: "1080p Full HD"),
            Triple("🗣️", "AUDIO", movie.language ?: "Multi-Audio"),
            Triple("💬", "SUBTITLES", movie.subtitle ?: "English (ESub)"),
            Triple("📅", "RELEASE", movie.releaseYear?.toString() ?: "Latest"),
            Triple("🏢", "STUDIO", studioName),
            Triple("📡", "STATUS", statusText)
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(themeSurface())
            .border(1.dp, themeBorder(), RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "📋 Media Info & Cast",
                color = themeTextPrimary(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Black,
                fontFamily = InterFont
            )
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.15f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "✓ VERIFIED",
                    color = Color(0xFF10B981),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }

        specs.chunked(2).forEach { rowSpecs ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowSpecs.forEach { (icon, label, value) ->
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(themeCard())
                            .border(1.dp, themeBorder(), RoundedCornerShape(12.dp))
                            .padding(10.dp)
                    ) {
                        Text(
                            "$icon  $label",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = themeTextTertiary(),
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.height(3.dp))
                        Text(
                            value,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            fontFamily = InterFont
                        )
                    }
                }
            }
        }

        if (!movie.castMembers.isNullOrBlank()) {
            val castList = remember(movie.castMembers) {
                movie.castMembers.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }
            if (castList.isNotEmpty()) {
                HorizontalDivider(color = themeBorder())
                Text(
                    "🎭 STARRING / VOICE CAST",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = themeTextTertiary(),
                    fontFamily = InterFont
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    castList.forEach { actor ->
                        Surface(
                            color = if (isDark) Color.White.copy(alpha = 0.07f) else Color.Black.copy(alpha = 0.05f),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.border(1.dp, themeBorder(), RoundedCornerShape(8.dp))
                        ) {
                            Text(
                                actor,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = themeTextSecondary(),
                                modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun FastDirectDownloadPanel(
    title: String,
    isTv: Boolean,
    targetId: String,
    seasonNumber: Int?,
    episodeNumber: Int?,
    loadingKey: String?,
    errorMessage: String?,
    compact: Boolean = false,
    keyPrefix: String? = null,
    onTriggerQuality: (String) -> Unit
) {
    val qualities = listOf(
        Triple("1080p", "1080p Full HD", Color(0xFF10B981)),
        Triple("720p", "720p Fast HD", Color(0xFF06B6D4)),
        Triple("480p", "480p Standard", Color(0xFF8B5CF6)),
        Triple("360p", "360p Data Saver", Color(0xFFF59E0B))
    )

    val panelBg = if (isDark) {
        Brush.linearGradient(
            listOf(Color(0xFF064E3B).copy(alpha = 0.35f), Color(0xFF0F172A))
        )
    } else {
        Brush.linearGradient(
            listOf(Color(0xFFECFDF5), Color(0xFFF0FDFA))
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(panelBg)
            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
            .padding(if (compact) 12.dp else 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(Color(0xFF10B981).copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Download,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
                        modifier = Modifier.size(16.dp)
                    )
                }
                Column {
                    Text(
                        "Fast Direct Download",
                        color = if (isDark) Color.White else Color(0xFF064E3B),
                        fontSize = if (compact) 13.sp else 15.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = InterFont
                    )
                    Text(
                        if (isTv) "Instant Direct MKV • S${seasonNumber ?: 1} E${episodeNumber ?: 1}"
                        else "High-Speed Direct MKV Cloud Vault",
                        color = if (isDark) Color(0xFF6EE7B7) else Color(0xFF047857),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Surface(
                color = Color(0xFF10B981).copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    "DIRECT MKV",
                    color = if (isDark) Color(0xFF34D399) else Color(0xFF047857),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }

        qualities.chunked(2).forEach { rowQ ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowQ.forEach { (qCode, qLabel, accent) ->
                    val isBusy = if (keyPrefix != null) {
                        loadingKey == "${keyPrefix}_$qCode"
                    } else {
                        loadingKey?.endsWith("_$qCode") == true
                    }
                    val textCol = if (isDark) Color.White else accent.copy(alpha = 0.95f)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(12.dp))
                            .background(accent.copy(alpha = if (isDark) 0.18f else 0.12f))
                            .border(1.dp, accent.copy(alpha = 0.45f), RoundedCornerShape(12.dp))
                            .bounceClick {
                                if (loadingKey == null) onTriggerQuality(qCode)
                            }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isBusy) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                CircularProgressIndicator(
                                    color = textCol,
                                    modifier = Modifier.size(14.dp),
                                    strokeWidth = 2.dp
                                )
                                Text(
                                    "Generating...",
                                    color = textCol,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    Icons.Default.Download,
                                    contentDescription = null,
                                    tint = textCol,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    qLabel,
                                    color = textCol,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }
        }

        if (!errorMessage.isNullOrBlank()) {
            Text(
                text = errorMessage,
                color = if (isDark) Color(0xFFFCA5A5) else Color(0xFFDC2626),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

data class StreamServer(
    val id: String,
    val name: String,
    val url: String,
    val color: Color = NexiRed,
    val badge: String = "FAST HD",
    val isHindi: Boolean = false
)

private fun extractYouTubeVideoId(urlOrId: String?): String? {
    val input = urlOrId?.trim().orEmpty()
    if (input.isEmpty()) return null
    if (input.matches(Regex("""^[a-zA-Z0-9_-]{11}$"""))) return input
    val regex = Regex("""(?:v=|youtu\.be/|embed/|shorts/|live/)([a-zA-Z0-9_-]{11})""")
    return regex.find(input)?.groupValues?.getOrNull(1)
}

private fun normalizeWebPlayerUrl(url: String): String {
    val cleanUrl = url.trim()
    if (cleanUrl.isEmpty()) return ""
    // Prevent double-proxying or re-extracting already normalized player URLs
    if (cleanUrl.contains("/api/player/") || cleanUrl.contains("nexiplay.vercel.app/api/player/")) {
        return cleanUrl
    }
    val ytId = extractYouTubeVideoId(cleanUrl)
    if (ytId != null) {
        return "https://www.youtube-nocookie.com/embed/$ytId?autoplay=1&rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&fs=1"
    }
    val lower = cleanUrl.lowercase()
    if ((lower.contains("dood") || lower.contains("ds2play") || lower.contains("playmogo")) && (lower.contains("/d/") || lower.contains("/f/"))) {
        return cleanUrl.replace("/d/", "/e/").replace("/f/", "/e/")
    }
    if (lower.contains("streamtape") || lower.contains("streamta.pe")) {
        if (lower.contains("/v/")) {
            return cleanUrl.replace("/v/", "/e/")
        }
        if (!lower.contains("/e/")) {
            val parts = try { java.net.URI(cleanUrl).path.split("/").filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
            val id = parts.lastOrNull()
            if (!id.isNullOrBlank()) return "https://streamtape.com/e/$id"
        }
        return cleanUrl
    }
    if (lower.contains("voe.sx") || lower.contains("voe-network") || lower.contains("ashleyhardat") || lower.contains("shannoncoldalready")) {
        try {
            val uri = java.net.URI(cleanUrl)
            val parts = (uri.path ?: "").split("/").filter { it.isNotBlank() && it != "e" && it != "voe" && it != "api" && it != "player" }
            val id = parts.lastOrNull()
            if (!id.isNullOrBlank() && id != "voe") {
                val playerUrl = "https://voe.sx/e/$id"
                return "https://nexiplay.vercel.app/api/player/voe?url=" + java.net.URLEncoder.encode(playerUrl, "UTF-8")
            }
        } catch (_: Exception) {}
    }
    if (lower.contains("abyss.to") || lower.contains("abyssplayer") || lower.contains("abysscdn") || lower.contains("short.ink")) {
        try {
            val uri = java.net.URI(cleanUrl)
            val parts = (uri.path ?: "").split("/").filter { it.isNotBlank() && it != "abyss" && it != "player" && it != "api" }
            val id = parts.lastOrNull()
            if (!id.isNullOrBlank() && id != "abyss") {
                val playerUrl = "https://player.abyssplayer.com/$id"
                return "https://nexiplay.vercel.app/api/player/abyss?url=" + java.net.URLEncoder.encode(playerUrl, "UTF-8")
            }
        } catch (_: Exception) {}
    }
    return cleanUrl
}
private fun extractToonplaySeriesId(rawTpUrl: String?): String? {
    val trimmed = rawTpUrl?.trim()?.takeIf { it.isNotBlank() } ?: return null
    if (trimmed.startsWith("http", ignoreCase = true)) {
        try {
            val path = java.net.URI(trimmed).path ?: ""
            val lastPart = path.split("/").lastOrNull { it.isNotBlank() }.orEmpty()
            if (lastPart.startsWith("series-", ignoreCase = true) || lastPart.startsWith("anime-", ignoreCase = true)) {
                return lastPart
            }
        } catch (_: Exception) {}
    }
    return trimmed
}

fun getAvailableServers(
    type: String,
    streamingRow: StreamingRow?,
    movie: Movie,
    episode: Episode? = null,
    seasonNumber: Int = 1
): List<StreamServer> {
    val servers = mutableListOf<StreamServer>()
    val cleanType = type.lowercase()
    val isSeries = cleanType == "series" || cleanType == "anime" || cleanType == "tv"
    val epNum = episode?.episodeNumber ?: 1
    val isAnimeContent = cleanType == "anime" ||
        movie.type.equals("anime", ignoreCase = true) ||
        !movie.malId.isNullOrBlank() ||
        movie.scraperSource?.contains("rareanimes", ignoreCase = true) == true ||
        movie.scraperSource?.contains("animekhor", ignoreCase = true) == true ||
        movie.title.contains("anime", ignoreCase = true) ||
        isSeries

    // 1. ToonPlay Private Server (Hindi Dubbed) - Always attach seriesId when present so expired m3u8 URLs auto-refresh
    val tpUrl = if (isSeries) {
        episode?.streamingUrlToonplay ?: streamingRow?.streamingUrlToonplay ?: movie.streamingUrlToonplay
    } else {
        streamingRow?.streamingUrlToonplay ?: streamingRow?.toonplayUrl ?: movie.streamingUrlToonplay ?: movie.toonplayUrl
    }?.trim()?.takeIf { it.isNotBlank() }
    val tpSeriesId = extractToonplaySeriesId(streamingRow?.toonplayUrl ?: movie.toonplayUrl)

    if (!tpSeriesId.isNullOrBlank()) {
        val encodedCached = if (!tpUrl.isNullOrBlank() && tpUrl != tpSeriesId) {
            "?cached=${java.net.URLEncoder.encode(tpUrl, "UTF-8")}"
        } else ""
        servers.add(
            StreamServer(
                id = "toonplay",
                name = "Nexiplay Private Server",
                url = "toonplay://$tpSeriesId/$seasonNumber/$epNum$encodedCached",
                color = Color(0xFFE53935),
                badge = "HINDI DUBBED",
                isHindi = true
            )
        )
    } else if (!tpUrl.isNullOrBlank()) {
        servers.add(
            StreamServer(
                id = "toonplay",
                name = "Nexiplay Private Server",
                url = normalizeWebPlayerUrl(tpUrl),
                color = Color(0xFFE53935),
                badge = "HINDI DUBBED",
                isHindi = true
            )
        )
    }

    // 1.1 Priority Live Anime Servers: RR Nexiplay & Nexi Deshi Servers (Always promoted to the top of anime server lists)
    if (isAnimeContent) {
        val encId = java.net.URLEncoder.encode(movie.id, "UTF-8")
        val encSlug = java.net.URLEncoder.encode(movie.slug ?: "", "UTF-8")
        val encTitle = java.net.URLEncoder.encode(movie.title, "UTF-8")
        val liveRRUrl = "https://nexiplay.vercel.app/embed/rr?id=$encId&slug=$encSlug&season=$seasonNumber&episode=$epNum&apiKey=nxp_live_nexiplay_official_core_prod"
        val liveDesiBase = "https://nexiplay.vercel.app/embed/desidub?id=$encId&slug=$encSlug&title=$encTitle&season=$seasonNumber&episode=$epNum&apiKey=nxp_live_nexiplay_official_core_prod"

        servers.add(StreamServer("rareanimes", "RR Nexiplay (Hindi Dub)", liveRRUrl, Color(0xFFE91E63), "HINDI DUB", true))
        servers.add(StreamServer("desidub", "Nexi Deshi Server", "$liveDesiBase&server=desidub", Color(0xFF10B981), "DESHI DUB", true))
        servers.add(StreamServer("desidub_vidmoly", "Nexi Deshi Server 1 (HD)", "$liveDesiBase&server=desidub_vidmoly", Color(0xFFFF4081), "DESHI HD", true))
        servers.add(StreamServer("desidub_mirror", "Nexi Deshi Server 2 (Mirror)", "$liveDesiBase&server=desidub_mirror", Color(0xFF00E5FF), "DESHI FAST", true))
        servers.add(StreamServer("desidub_ruby", "Nexi Deshi Server 3 (Ruby)", "$liveDesiBase&server=desidub_ruby", Color(0xFFA855F7), "DESHI TURBO", true))
        servers.add(StreamServer("desidub_streamtape", "Nexi Deshi Server 4 (Streamtape)", "$liveDesiBase&server=desidub_streamtape", Color(0xFF38BDF8), "DESHI STREAM", true))
        servers.add(StreamServer("desidub_doodstream", "Nexi Deshi Server 5 (Doodstream)", "$liveDesiBase&server=desidub_doodstream", Color(0xFFFBBF24), "DESHI ULTRA", true))
    }

    // 2. AnimeRulz Server
    val arUrl = if (isSeries) {
        episode?.streamingUrlAnimerulz ?: streamingRow?.streamingUrlAnimerulz ?: movie.streamingUrlAnimerulz
    } else {
        streamingRow?.streamingUrlAnimerulz ?: streamingRow?.animerulzUrl ?: movie.streamingUrlAnimerulz ?: movie.animerulzUrl
    }?.trim()?.takeIf { it.isNotBlank() }
    if (!arUrl.isNullOrBlank()) {
        servers.add(
            StreamServer(
                id = "animerulz",
                name = "AnimeR Server",
                url = normalizeWebPlayerUrl(arUrl),
                color = Color(0xFFFB8C00),
                badge = "FAST ANIME",
                isHindi = false
            )
        )
    }

    // 3 & 4. Merge Multi-Scraper Config with Episode/Movie JSON Streaming URLs
    // (Episode streamingUrl overrides multi_scraper_config per server key, matching WatchPageClient.tsx)
    val mergedStreams = linkedMapOf<String, String>()

    val rawMultiConfig = streamingRow?.multiScraperConfig?.takeIf { it.isNotBlank() }
        ?: if (movie.scraperSource == "multi") movie.scraperUrl else null
    if (!rawMultiConfig.isNullOrBlank() && rawMultiConfig.trim().startsWith("{")) {
        try {
            val configObj = JSONObject(rawMultiConfig.trim())
            val keys = configObj.keys()
            while (keys.hasNext()) {
                val serverKey = keys.next()
                val srvCfg = configObj.optJSONObject(serverKey) ?: continue
                val mode = srvCfg.optString("mode", "single")
                val resolvedConfigUrl = when (mode) {
                    "episode" -> srvCfg.optJSONObject("episodeUrls")?.optString("${seasonNumber}_$epNum")
                    "separate" -> {
                        val urlsObj = srvCfg.optJSONObject("urls")
                        val exactSeasonUrl = urlsObj?.optString("$seasonNumber")?.trim()
                        if (!exactSeasonUrl.isNullOrBlank() && exactSeasonUrl != "null") {
                            exactSeasonUrl
                        } else if (serverKey.equals("rareanimes", ignoreCase = true)) {
                            // Codedew WatchMultiQuality zipper pages include relatedData for all seasons
                            val singleFallback = srvCfg.optString("url").trim().takeIf { it.isNotBlank() && it != "null" }
                            singleFallback ?: urlsObj?.let { u ->
                                val uKeys = u.keys()
                                var firstAvailable: String? = null
                                while (uKeys.hasNext()) {
                                    val candidate = u.optString(uKeys.next()).trim()
                                    if (candidate.isNotBlank() && candidate != "null") {
                                        firstAvailable = candidate
                                        break
                                    }
                                }
                                firstAvailable
                            }
                        } else {
                            exactSeasonUrl
                        }
                    }
                    else -> srvCfg.optString("url")
                }?.trim()

                if (!resolvedConfigUrl.isNullOrBlank() && resolvedConfigUrl != "null") {
                    mergedStreams[serverKey.lowercase()] = resolvedConfigUrl
                }
            }
        } catch (_: Exception) {}
    }

    if (!mergedStreams.containsKey("rareanimes") &&
        movie.scraperSource.equals("rareanimes", ignoreCase = true) &&
        !movie.scraperUrl.isNullOrBlank() &&
        !movie.scraperUrl.trim().startsWith("{")
    ) {
        mergedStreams["rareanimes"] = movie.scraperUrl.trim()
    }

    // Auto-enable Nexi Deshi Live Servers for Anime on demand
    if (movie.type.equals("anime", ignoreCase = true) || isSeries) {
        val encId = java.net.URLEncoder.encode(movie.id, "UTF-8")
        val encSlug = java.net.URLEncoder.encode(movie.slug ?: "", "UTF-8")
        val encTitle = java.net.URLEncoder.encode(movie.title, "UTF-8")
        val liveDesiBase = "https://nexiplay.vercel.app/embed/desidub?id=$encId&slug=$encSlug&title=$encTitle&season=$seasonNumber&episode=$epNum&apiKey=nxp_live_nexiplay_official_core_prod"

        if (servers.none { it.id == "desidub" } && !mergedStreams.containsKey("desidub")) mergedStreams["desidub"] = "$liveDesiBase&server=desidub"
        if (servers.none { it.id == "desidub_vidmoly" } && !mergedStreams.containsKey("desidub_vidmoly")) mergedStreams["desidub_vidmoly"] = "$liveDesiBase&server=desidub_vidmoly"
        if (servers.none { it.id == "desidub_mirror" } && !mergedStreams.containsKey("desidub_mirror")) mergedStreams["desidub_mirror"] = "$liveDesiBase&server=desidub_mirror"
        if (servers.none { it.id == "desidub_ruby" } && !mergedStreams.containsKey("desidub_ruby")) mergedStreams["desidub_ruby"] = "$liveDesiBase&server=desidub_ruby"
        if (servers.none { it.id == "desidub_streamtape" } && !mergedStreams.containsKey("desidub_streamtape")) mergedStreams["desidub_streamtape"] = "$liveDesiBase&server=desidub_streamtape"
        if (servers.none { it.id == "desidub_doodstream" } && !mergedStreams.containsKey("desidub_doodstream")) mergedStreams["desidub_doodstream"] = "$liveDesiBase&server=desidub_doodstream"
    }

    val customUrl = if (isSeries) (episode?.streamingUrl ?: streamingRow?.streamingUrl) else (movie.streamingUrl ?: streamingRow?.streamingUrl)
    if (!customUrl.isNullOrBlank()) {
        val trimmedUrl = customUrl.trim()
        if (trimmedUrl.startsWith("{")) {
            try {
                val jsonObj = JSONObject(trimmedUrl)
                val keys = jsonObj.keys()
                val ignoredKeys = setOf("single", "separate", "episode", "is_disabled", "multi_scraper_config")
                while (keys.hasNext()) {
                    val key = keys.next()
                    val lowerKey = key.lowercase()
                    if (ignoredKeys.contains(lowerKey)) continue
                    val url = jsonObj.optString(key).trim()
                    if (url.isNotBlank() && url != "null" && url != "disabled") {
                        mergedStreams[lowerKey] = url
                    }
                }
            } catch (_: Exception) {
                if (trimmedUrl != "disabled") {
                    mergedStreams["custom"] = trimmedUrl
                }
            }
        } else if (trimmedUrl != "disabled") {
            mergedStreams["custom"] = trimmedUrl
        }
    }

    val disabledMultiIds = setOf("muse_india", "anione_india")
    val isContentHindi = movie.language?.contains("hindi", ignoreCase = true) == true ||
        movie.title.contains("hindi", ignoreCase = true) ||
        movie.scraperSource?.contains("rareanimes", ignoreCase = true) == true ||
        movie.scraperSource?.contains("animekhor", ignoreCase = true) == true ||
        movie.scraperSource?.contains("toonstream", ignoreCase = true) == true ||
        movie.scraperSource?.contains("toonplay", ignoreCase = true) == true

    for ((key, rawSrvUrl) in mergedStreams) {
        if (disabledMultiIds.contains(key)) continue
        val normalizedUrl = if (key == "toonstream" && rawSrvUrl.matches(Regex("""^\d+$"""))) {
            "https://toonstream.vip/?trembed=$rawSrvUrl"
        } else {
            normalizeWebPlayerUrl(rawSrvUrl)
        }

        when (key) {
            "circleftp" -> servers.add(
                StreamServer(
                    id = "circleftp",
                    name = "CircleFTP (BDIX 100M)",
                    url = normalizedUrl,
                    color = Color(0xFF10B981),
                    badge = "BDIX 100M",
                    isHindi = false
                )
            )
            "moviebox" -> servers.add(
                StreamServer(
                    id = "moviebox",
                    name = "MovieBox HD",
                    url = normalizedUrl,
                    color = Color(0xFF6366F1),
                    badge = "DIRECT HLS",
                    isHindi = false
                )
            )
            "animekhor" -> servers.add(
                StreamServer(
                    id = "animekhor",
                    name = "AnimeKhor (Bangla & Hindi Dub)",
                    url = normalizedUrl,
                    color = Color(0xFFEF4444),
                    badge = "BANGLA/HINDI",
                    isHindi = true
                )
            )
            "miruro" -> servers.add(
                StreamServer(
                    id = "miruro",
                    name = "Miruro Anime",
                    url = normalizedUrl,
                    color = Color(0xFF8B5CF6),
                    badge = "ANIME HD",
                    isHindi = false
                )
            )
            "aniclover" -> servers.add(
                StreamServer(
                    id = "aniclover",
                    name = "Aniclover (Sub/Dub)",
                    url = normalizedUrl,
                    color = Color(0xFF10B981),
                    badge = "ANIME API",
                    isHindi = false
                )
            )
            "toonplay" -> {
                if (servers.none { it.id == "toonplay" }) {
                    servers.add(StreamServer("toonplay", "Nexiplay Private Server", normalizedUrl, Color(0xFFE53935), "HINDI DUBBED", true))
                }
            }
            "animerulz" -> {
                if (servers.none { it.id == "animerulz" }) {
                    servers.add(StreamServer("animerulz", "AnimeR Server", normalizedUrl, Color(0xFFFB8C00), "FAST ANIME", false))
                }
            }
            "animeworld" -> servers.add(StreamServer("animeworld", "AnimeWorld Server", normalizedUrl, Color(0xFF4CAF50), "MULTI-AUDIO", false))
            "animixstream" -> servers.add(StreamServer("animixstream", "Nexiplay Ani Server", normalizedUrl, Color(0xFF00BCD4), "MULTI-AUDIO", false))
            "toonstream" -> servers.add(StreamServer("toonstream", "Nexiplay T Server", normalizedUrl, Color(0xFF9C27B0), "HINDI DUBBED", true))
            "rareanimes" -> {
                if (servers.none { it.id == "rareanimes" }) {
                    val targetUrl = normalizedUrl.ifBlank {
                        val encId = java.net.URLEncoder.encode(movie.id, "UTF-8")
                        val encSlug = java.net.URLEncoder.encode(movie.slug ?: "", "UTF-8")
                        "https://nexiplay.vercel.app/embed/rr?id=$encId&slug=$encSlug&season=$seasonNumber&episode=$epNum&apiKey=nxp_live_nexiplay_official_core_prod"
                    }
                    servers.add(StreamServer("rareanimes", "RR Nexiplay (Hindi Dub)", targetUrl, Color(0xFFE91E63), "HINDI DUB", true))
                }
            }
            "doodstream" -> {
                if (servers.none { it.id == "doodstream" }) {
                    servers.add(StreamServer("doodstream", if (isContentHindi) "Nexi Turbo (Hindi Dub)" else "Nexi Turbo Server", normalizedUrl, Color(0xFFFBBF24), if (isContentHindi) "HINDI DUB" else "TURBO FAST", isContentHindi))
                }
            }
            "streamtape" -> {
                if (servers.none { it.id == "streamtape" }) {
                    servers.add(StreamServer("streamtape", if (isContentHindi) "Nexi Pulse (Hindi Dub)" else "Nexi Pulse Server", normalizedUrl, Color(0xFF38BDF8), if (isContentHindi) "HINDI DUB" else "PULSE HD", isContentHindi))
                }
            }
            "voe" -> {
                if (servers.none { it.id == "voe" }) {
                    servers.add(StreamServer("voe", if (isContentHindi) "Nexi Spark (Hindi Dub)" else "Nexi Spark Server", normalizedUrl, Color(0xFFA855F7), if (isContentHindi) "HINDI DUB" else "SPARK PRO", isContentHindi))
                }
            }
            "vidmoly" -> {
                if (servers.none { it.id == "vidmoly" }) {
                    servers.add(StreamServer("vidmoly", if (isContentHindi) "Vidmoly (Hindi Dub)" else "Vidmoly Server", normalizedUrl, Color(0xFFA78BFA), if (isContentHindi) "HINDI DUB" else "VIDMOLY", isContentHindi))
                }
            }
            "abyss" -> {
                if (servers.none { it.id == "abyss" }) {
                    servers.add(StreamServer("abyss", if (isContentHindi) "Nexi Vault (Hindi Dub)" else "Nexi Vault Server", normalizedUrl, Color(0xFF34D399), if (isContentHindi) "HINDI DUB" else "VAULT CDN", isContentHindi))
                }
            }
            "desidub" -> {
                if (servers.none { it.id == "desidub" }) {
                    servers.add(StreamServer("desidub", "Nexi Deshi Server", normalizedUrl, Color(0xFF10B981), "DESHI DUB", true))
                }
            }
            "desidub_vidmoly" -> {
                if (servers.none { it.id == "desidub_vidmoly" }) {
                    servers.add(StreamServer("desidub_vidmoly", "Nexi Deshi Server 1 (HD)", normalizedUrl, Color(0xFFFF4081), "DESHI HD", true))
                }
            }
            "desidub_mirror" -> {
                if (servers.none { it.id == "desidub_mirror" }) {
                    servers.add(StreamServer("desidub_mirror", "Nexi Deshi Server 2 (Mirror)", normalizedUrl, Color(0xFF00E5FF), "DESHI FAST", true))
                }
            }
            "desidub_ruby" -> {
                if (servers.none { it.id == "desidub_ruby" }) {
                    servers.add(StreamServer("desidub_ruby", "Nexi Deshi Server 3 (Ruby)", normalizedUrl, Color(0xFFA855F7), "DESHI TURBO", true))
                }
            }
            "desidub_streamtape" -> {
                if (servers.none { it.id == "desidub_streamtape" }) {
                    servers.add(StreamServer("desidub_streamtape", "Nexi Deshi Server 4 (Streamtape)", normalizedUrl, Color(0xFF38BDF8), "DESHI STREAM", true))
                }
            }
            "desidub_doodstream" -> {
                if (servers.none { it.id == "desidub_doodstream" }) {
                    servers.add(StreamServer("desidub_doodstream", "Nexi Deshi Server 5 (Doodstream)", normalizedUrl, Color(0xFFFBBF24), "DESHI ULTRA", true))
                }
            }
            "desidub_abyss" -> {
                if (servers.none { it.id == "desidub_abyss" }) {
                    servers.add(StreamServer("desidub_abyss", "Nexi Deshi Server (Abyss)", normalizedUrl, Color(0xFF2DD4BF), "DESHI BACKUP", true))
                }
            }
            "desidub_filemoon" -> {
                if (servers.none { it.id == "desidub_filemoon" }) {
                    servers.add(StreamServer("desidub_filemoon", "Nexi Deshi Server (Filemoon)", normalizedUrl, Color(0xFFFF4081), "DESHI BACKUP", true))
                }
            }
            "desidub_earnvids" -> {
                if (servers.none { it.id == "desidub_earnvids" }) {
                    servers.add(StreamServer("desidub_earnvids", "Nexi Deshi Server (Earnvids)", normalizedUrl, Color(0xFF2DD4BF), "DESHI BACKUP", true))
                }
            }
            "custom" -> servers.add(StreamServer("custom", if (isContentHindi) "Server Nexiplay (Hindi)" else "Server Nexiplay", normalizedUrl, Color(0xFFFFB878), if (isContentHindi) "HINDI DUB" else "DIRECT CDN", isContentHindi))
            "legacy" -> servers.add(StreamServer("custom_legacy", if (isContentHindi) "Server Nexiplay Legacy (Hindi)" else "Server Nexiplay Legacy", normalizedUrl, Color(0xFFFFB878), if (isContentHindi) "HINDI DUB" else "DIRECT CDN", isContentHindi))
            else -> {
                val isHindi = isContentHindi || key in listOf("toonstream", "rareanimes", "toonplay", "animekhor", "screenscape", "multiembed", "vixsrc", "twoembed", "anyembed", "atlantic", "desidub", "desidub_abyss", "desidub_filemoon", "desidub_streamtape", "desidub_earnvids", "desidub_doodstream") || key.contains("hindi", ignoreCase = true) || key.contains("desidub", ignoreCase = true)
                servers.add(
                    StreamServer(
                        id = key,
                        name = key.uppercase() + " Server",
                        url = normalizedUrl,
                        color = if (isHindi) Color(0xFFF59E0B) else Color(0xFF8B5CF6),
                        badge = if (isHindi) "HINDI DUBBED" else "MULTI-AUDIO",
                        isHindi = isHindi
                    )
                )
            }
        }
    }

        // 5. All Website Built-In Global Embed Servers (Exact parity with WatchPageClient.tsx BUILT_IN_SERVERS)
    val cleanTmdb = (streamingRow?.tmdbId ?: movie.tmdbId)?.trim()?.removePrefix("tmdb-")?.takeIf { it.isNotBlank() }
    val cleanImdb = (streamingRow?.imdbId ?: movie.imdbId)?.trim()?.takeIf { it.isNotBlank() }
    val fallbackId = movie.id.trim().takeIf { it.isNotBlank() && !it.startsWith("tt") && !it.contains("-") }

    val tmdbTarget = cleanTmdb ?: fallbackId
    val anyTarget = cleanTmdb ?: cleanImdb ?: fallbackId
    val imdbFirstTarget = cleanImdb ?: cleanTmdb ?: fallbackId

    val sNum = seasonNumber
    val eNum = epNum

    // 1. Nexi 4K (Ultra) - Primary Web Parity Server
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "ultra_4k",
                name = "Nexi 4K (Ultra)",
                url = if (isSeries) "https://cinebros.net/watch/tv/$tmdbTarget?s=$sNum&e=$eNum" else "https://cinebros.net/watch/movie/$tmdbTarget",
                color = Color(0xFF10B981),
                badge = "4K ULTRA",
                isHindi = false
            )
        )
    }

    // 2. Luma Stream (HD)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "luma_stream",
                name = "Luma Stream (HD)",
                url = if (isSeries) "https://embed.cinevu.icu/embed/tv/$tmdbTarget/$sNum/$eNum" else "https://embed.cinevu.icu/embed/movie/$tmdbTarget",
                color = Color(0xFF06B6D4),
                badge = "DASH HD",
                isHindi = false
            )
        )
    }

    // 3. Nexi Frame (Ultra)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_frame",
                name = "Nexi Frame (Ultra)",
                url = if (isSeries) "https://framextv.cc/embed/tv/$anyTarget/$sNum/$eNum?no_ads=1&autoplay=1" else "https://framextv.cc/embed/movie/$anyTarget?no_ads=1&autoplay=1",
                color = Color(0xFF6366F1),
                badge = "ULTRA HD",
                isHindi = false
            )
        )
    }

    // 4. Nexi Joy (Pro HD)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_joy",
                name = "Nexi Joy (Pro HD)",
                url = if (isSeries) "https://flaxjoy.app/watch/$tmdbTarget/$sNum/$eNum" else "https://flaxjoy.app/watch/$tmdbTarget",
                color = Color(0xFFF43F5E),
                badge = "PRO HD",
                isHindi = false
            )
        )
    }

    // 5. Nexi Flix (Fast HD)
    if (imdbFirstTarget != null) {
        servers.add(
            StreamServer(
                id = "onlyflix_fast",
                name = "Nexi Flix (Fast HD)",
                url = if (isSeries) "https://vidfast.vc/tv/$imdbFirstTarget/$sNum/$eNum" else "https://vidfast.vc/movie/$imdbFirstTarget",
                color = Color(0xFFF59E0B),
                badge = "FLIX FAST",
                isHindi = false
            )
        )
    }

    // 6. Nexi Soap (Global)
    if (imdbFirstTarget != null) {
        servers.add(
            StreamServer(
                id = "onlyflix_soap",
                name = "Nexi Soap (Global)",
                url = if (isSeries) "https://www.nontongo.win/embed/tv/$imdbFirstTarget/$sNum/$eNum" else "https://www.nontongo.win/embed/movie/$imdbFirstTarget",
                color = Color(0xFF6366F1),
                badge = "SOAP HD",
                isHindi = false
            )
        )
    }

    // 7. Nexi Prime (Balance)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "onlyflix_balance",
                name = "Nexi Prime (Balance)",
                url = if (isSeries) "https://balance.medmedia03.wiki/embed/tv/$tmdbTarget/$sNum/$eNum" else "https://balance.medmedia03.wiki/embed/movie/$tmdbTarget",
                color = Color(0xFF10B981),
                badge = "PRIME 1080P",
                isHindi = false
            )
        )
    }

    // 8. CineSrc (Ultra HD)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "cinesrc",
                name = "CineSrc (Ultra HD)",
                url = if (isSeries) "https://cinesrc.st/embed/tv/$anyTarget?s=$sNum&e=$eNum&color=%2300f2fe" else "https://cinesrc.st/embed/movie/$anyTarget?color=%2300f2fe",
                color = Color(0xFF00F2FE),
                badge = "ULTRA HD",
                isHindi = false
            )
        )
    }

    // 9. CineRuv (Studio 4K)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "cineruv",
                name = "CineRuv (Studio 4K)",
                url = if (isSeries) "https://cineruv.website/watch/tv/$tmdbTarget/$sNum/$eNum" else "https://cineruv.website/watch/movie/$tmdbTarget",
                color = Color(0xFF8B5CF6),
                badge = "STUDIO 4K",
                isHindi = false
            )
        )
    }

    // 10. Vexo (Glass 4K)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "vexo",
                name = "Vexo (Glass 4K)",
                url = if (isSeries) "https://vexo.surf/watch/tv/$tmdbTarget/$sNum/$eNum" else "https://vexo.surf/watch/movie/$tmdbTarget",
                color = Color(0xFF38BDF8),
                badge = "GLASS 4K",
                isHindi = false
            )
        )
    }

    // 11. Nexi Wave (Multi-Audio)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "atlantic",
                name = "Nexi Wave (Multi-Audio)",
                url = if (isSeries) "https://embed.atlantic.st/embed/tv/$tmdbTarget/$sNum/$eNum" else "https://embed.atlantic.st/embed/movie/$tmdbTarget",
                color = Color(0xFFEC4899),
                badge = "MULTI-AUDIO",
                isHindi = true
            )
        )
    }

    // 12. VidBolt (1080p HD)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "vidbolt",
                name = "VidBolt (1080p HD)",
                url = if (isSeries) "https://vidbolt.xyz/tv/$tmdbTarget/$sNum/$eNum" else "https://vidbolt.xyz/movie/$tmdbTarget",
                color = Color(0xFFFBBF24),
                badge = "1080P HD",
                isHindi = false
            )
        )
    }

    // 13. Nexi Velvet (Ultra HLS)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_velvet",
                name = "Nexi Velvet (Ultra HLS)",
                url = if (isSeries) "https://dulo.mov/watch/tv/$tmdbTarget?s=$sNum&e=$eNum" else "https://dulo.mov/watch/movie/$tmdbTarget",
                color = Color(0xFFA855F7),
                badge = "ULTRA HLS",
                isHindi = false
            )
        )
    }

    // 14. Nexi Flow (Multi-Stream)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_flow",
                name = "Nexi Flow (Multi-Stream)",
                url = if (isSeries) "https://watch.rivestream.app/embed?type=tv&id=$tmdbTarget&season=$sNum&episode=$eNum" else "https://watch.rivestream.app/embed?type=movie&id=$tmdbTarget",
                color = Color(0xFF14B8A6),
                badge = "MULTI-STREAM",
                isHindi = false
            )
        )
    }

    // 15. Nexi Love (Pro HD)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_love",
                name = "Nexi Love (Pro HD)",
                url = if (isSeries) "https://player.vidlove.cc/embed/tv/$tmdbTarget/$sNum/$eNum" else "https://player.vidlove.cc/embed/movie/$tmdbTarget",
                color = Color(0xFFF43F5E),
                badge = "PRO HD",
                isHindi = false
            )
        )
    }

    // 16. Nexi Core (Ultra HD)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_core",
                name = "Nexi Core (Ultra HD)",
                url = if (isSeries) "https://vidcore.io/tv/$tmdbTarget/$sNum/$eNum?autoPlay=false" else "https://vidcore.io/movie/$tmdbTarget?autoPlay=false",
                color = Color(0xFF06B6D4),
                badge = "CORE HD",
                isHindi = false
            )
        )
    }

    // 17. Nexi Cinema (CinemaOS)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_cinema",
                name = "Nexi Cinema (CinemaOS)",
                url = if (isSeries) "https://cinemaos.me/player/$tmdbTarget/$sNum/$eNum?autoPlay=false" else "https://cinemaos.me/player/$tmdbTarget?autoPlay=false",
                color = Color(0xFF3B82F6),
                badge = "CINEMA OS",
                isHindi = false
            )
        )
    }

    // 18. Nexi Nerds (Master HD)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "nexi_nerds",
                name = "Nexi Nerds (Master HD)",
                url = if (isSeries) "https://movienerds.site/watch/tv/$tmdbTarget?season=$sNum&episode=$eNum" else "https://movienerds.site/watch/movie/$tmdbTarget",
                color = Color(0xFF10B981),
                badge = "MASTER HD",
                isHindi = false
            )
        )
    }

    // 19. VidRock
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidrock",
                name = "VidRock",
                url = if (isSeries) "https://vidrock.net/tv/$anyTarget/$sNum/$eNum" else "https://vidrock.net/movie/$anyTarget",
                color = Color(0xFF14B8A6),
                badge = "FAST HD",
                isHindi = false
            )
        )
    }

    // 20. Videasy HD
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "videasy",
                name = "Videasy HD",
                url = if (isSeries) "https://player.videasy.to/tv/$anyTarget/$sNum/$eNum" else "https://player.videasy.to/movie/$anyTarget",
                color = Color(0xFF10B981),
                badge = "FAST HD",
                isHindi = false
            )
        )
    }

    // 21. VidLink (Ultra)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidlink",
                name = "VidLink (Ultra)",
                url = if (isSeries) "https://vidlink.pro/tv/$anyTarget/$sNum/$eNum" else "https://vidlink.pro/movie/$anyTarget",
                color = Color(0xFF06B6D4),
                badge = "1080P ULTRA",
                isHindi = false
            )
        )
    }

    // 22. VidSrc (PM)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidsrc_pm",
                name = "VidSrc (PM)",
                url = if (isSeries) "https://vidsrc.buzz/embed/tv/$anyTarget/$sNum/$eNum" else "https://vidsrc.buzz/embed/movie/$anyTarget",
                color = Color(0xFF8E24AA),
                badge = "HD STREAM",
                isHindi = false
            )
        )
    }

    // 23. UpCloud (1080p Fast)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "upcloud",
                name = "UpCloud (1080p Fast)",
                url = if (isSeries) "https://vidup.to/tv/$anyTarget/$sNum/$eNum" else "https://vidup.to/movie/$anyTarget",
                color = Color(0xFF0EA5E9),
                badge = "1080P FAST",
                isHindi = false
            )
        )
    }

    // 24. Flixer (Mythology HLS)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "flixer",
                name = "Flixer (Mythology HLS)",
                url = if (isSeries) "https://flixer.gd/watch/tv/$tmdbTarget/$sNum/$eNum?embed=1" else "https://flixer.gd/watch/movie/$tmdbTarget?embed=1",
                color = Color(0xFFA855F7),
                badge = "1080P HLS",
                isHindi = false
            )
        )
    }

    // 25. VidNest (Ad-Free)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidnest",
                name = "VidNest (Ad-Free)",
                url = if (isSeries) "https://vidnest.fun/tv/$anyTarget/$sNum/$eNum" else "https://vidnest.fun/movie/$anyTarget",
                color = Color(0xFF10B981),
                badge = "AD-FREE 1080P",
                isHindi = false
            )
        )
    }

    // 26. PrimeSrc (MyFlixer)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "primesrc",
                name = "PrimeSrc (MyFlixer)",
                url = if (isSeries) "https://primesrc.me/embed/tv?tmdb=$anyTarget&season=$sNum&episode=$eNum" else "https://primesrc.me/embed/movie?tmdb=$anyTarget",
                color = Color(0xFFEAB308),
                badge = "1080P FAST",
                isHindi = false
            )
        )
    }

    // 27. VidSpark (MegaCloud)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidspark",
                name = "VidSpark (MegaCloud)",
                url = if (isSeries) "https://vidspark.to/tv/$anyTarget/$sNum/$eNum" else "https://vidspark.to/movie/$anyTarget",
                color = Color(0xFF38BDF8),
                badge = "MEGA 1080P",
                isHindi = false
            )
        )
    }

    // 28. VidStuck (Freekz)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidstuck",
                name = "VidStuck (Freekz)",
                url = if (isSeries) "https://vidstuck.cc/embed/tv/$anyTarget/$sNum/$eNum?autoplay=true&nextEpisode=true&autoplayNextEpisode=true&loading=2&back=false" else "https://vidstuck.cc/embed/movie/$anyTarget?autoplay=true&loading=2&back=false",
                color = Color(0xFFEC4899),
                badge = "1080P PRO",
                isHindi = false
            )
        )
    }

    // 29. CinemaOS (NetPrime)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "cinemaos",
                name = "CinemaOS (NetPrime)",
                url = if (isSeries) "https://cinemaos.tech/player/$anyTarget/$sNum/$eNum" else "https://cinemaos.tech/player/$anyTarget",
                color = Color(0xFF3B82F6),
                badge = "MULTI-SRC",
                isHindi = false
            )
        )
    }

    // 30. VidPlay (YTHD)
    if (imdbFirstTarget != null) {
        servers.add(
            StreamServer(
                id = "vidplay",
                name = "VidPlay (YTHD)",
                url = if (isSeries) "https://ythd.org/embed/$imdbFirstTarget/$sNum-$eNum/color-000000" else "https://ythd.org/embed/$imdbFirstTarget",
                color = Color(0xFFF97316),
                badge = "1080P STABLE",
                isHindi = false
            )
        )
    }

    // 31. VidFast
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidfast",
                name = "VidFast",
                url = if (isSeries) "https://vidfast.vc/tv/$anyTarget/$sNum/$eNum" else "https://vidfast.vc/movie/$anyTarget",
                color = Color(0xFFEC4899),
                badge = "1080P FAST",
                isHindi = false
            )
        )
    }

    // 32. VidSrc.to
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidsrc_to",
                name = "VidSrc.to",
                url = if (isSeries) "https://vidsrc.to/embed/tv/$anyTarget/$sNum/$eNum" else "https://vidsrc.to/embed/movie/$anyTarget",
                color = Color(0xFF6366F1),
                badge = "PRO STREAM",
                isHindi = false
            )
        )
    }

    // 33. 2Embed (Multi-Audio)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "twoembed",
                name = "2Embed (Multi-Audio)",
                url = if (isSeries) "https://videm.xyz/embed/tv/$tmdbTarget/$sNum/$eNum" else "https://videm.xyz/embed/movie/$tmdbTarget",
                color = Color(0xFFD97706),
                badge = "MULTI-AUDIO",
                isHindi = true
            )
        )
    }

    // 34. MoviesAPI
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "moviesapi",
                name = "MoviesAPI",
                url = if (isSeries) "https://moviesapi.to/tv/$anyTarget/$sNum/$eNum" else "https://moviesapi.to/movie/$anyTarget",
                color = Color(0xFF3B82F6),
                badge = "HD STREAM",
                isHindi = false
            )
        )
    }

    // 35. VixSrc (FlixBay)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vixsrc",
                name = "VixSrc (FlixBay)",
                url = if (isSeries) "https://vixsrc.to/tv/$anyTarget/$sNum/$eNum" else "https://vixsrc.to/movie/$anyTarget",
                color = Color(0xFF6366F1),
                badge = "1080P FHD",
                isHindi = false
            )
        )
    }

    // 35b. VixSrc (Multi-Lang)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vixsrc_hindi",
                name = "VixSrc (Multi-Lang)",
                url = if (isSeries) "https://vixsrc.to/tv/$anyTarget/$sNum/$eNum?lang=hi" else "https://vixsrc.to/movie/$anyTarget?lang=hi",
                color = Color(0xFFEAB308),
                badge = "MULTI-LANG",
                isHindi = true
            )
        )
    }

    // 36. Nontongo
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "nontongo",
                name = "Nontongo",
                url = if (isSeries) "https://nontongo.win/embed/tv/$anyTarget/$sNum/$eNum" else "https://nontongo.win/embed/movie/$anyTarget",
                color = Color(0xFF3B82F6),
                badge = "ULTRA HD",
                isHindi = false
            )
        )
    }

    // 37. MultiEmbed (Hindi)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "multiembed",
                name = "MultiEmbed (Hindi)",
                url = if (isSeries) "https://multiembed.mov/?video_id=$anyTarget&tmdb=1&s=$sNum&e=$eNum" else "https://multiembed.mov/?video_id=$anyTarget&tmdb=1",
                color = Color(0xFFEA580C),
                badge = "MULTI",
                isHindi = true
            )
        )
    }

    // 38. Nexi Hindi (Dubbed)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "screenscape",
                name = "Nexi Hindi (Dubbed)",
                url = if (isSeries) "https://screenscape.me/embed?tmdb=$tmdbTarget&type=tv&lan=hindi&s=$sNum&e=$eNum" else "https://screenscape.me/embed?tmdb=$tmdbTarget&type=movie&lan=hindi",
                color = Color(0xFFF59E0B),
                badge = "HINDI DUBBED",
                isHindi = true
            )
        )
    }

    // 39. GDrivePlayer
    if (imdbFirstTarget != null) {
        servers.add(
            StreamServer(
                id = "godrive",
                name = "GDrivePlayer",
                url = if (isSeries) "https://database.gdriveplayer.us/player.php?imdb=$imdbFirstTarget&s=$sNum&e=$eNum" else "https://database.gdriveplayer.us/player.php?imdb=$imdbFirstTarget",
                color = Color(0xFF0EA5E9),
                badge = "DIRECT",
                isHindi = false
            )
        )
    }

    // 40. VidAPI
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidapi",
                name = "VidAPI",
                url = if (isSeries) "https://vaplayer.ru/embed/tv/$anyTarget/$sNum/$eNum" else "https://vaplayer.ru/embed/movie/$anyTarget",
                color = Color(0xFF8B5CF6),
                badge = "FAST HD",
                isHindi = false
            )
        )
    }

    // 41. VidCore (Fast HD)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidcore",
                name = "VidCore (Fast HD)",
                url = if (isSeries) "https://vidcore.io/tv/$anyTarget/$sNum/$eNum?autoPlay=true&autoNext=true" else "https://vidcore.io/movie/$anyTarget?autoPlay=true",
                color = Color(0xFF06B6D4),
                badge = "1080P ULTRA",
                isHindi = false
            )
        )
    }

    // 42. VidSrc SU (Multi)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "vidsrc_su",
                name = "VidSrc SU (Multi)",
                url = if (isSeries) "https://vidsrc.su/embed/tv/$anyTarget/$sNum/$eNum" else "https://vidsrc.su/embed/movie/$anyTarget",
                color = Color(0xFFF59E0B),
                badge = "MULTI-AUDIO",
                isHindi = true
            )
        )
    }

    // 43. AnyEmbed (Multi-Audio)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "anyembed",
                name = "AnyEmbed (Multi-Audio)",
                url = if (isSeries) "https://anyembed.xyz/embed/tmdb-tv-$tmdbTarget-$sNum-$eNum" else "https://anyembed.xyz/embed/tmdb-movie-$tmdbTarget",
                color = Color(0xFFD97706),
                badge = "MULTI-AUDIO",
                isHindi = true
            )
        )
    }

    // 43b. AutoEmbed Pro (Multi-Audio)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "autoembed_pro",
                name = "AutoEmbed Pro (Multi-Audio)",
                url = if (isSeries) "https://autoembed.me/embed/tv/$anyTarget/$sNum/$eNum" else "https://autoembed.me/embed/movie/$anyTarget",
                color = Color(0xFFF59E0B),
                badge = "MULTI-AUDIO",
                isHindi = true
            )
        )
    }

    // 43c. SmashyStream (Multi-Audio)
    if (anyTarget != null) {
        servers.add(
            StreamServer(
                id = "smashystream",
                name = "SmashyStream (Multi-Audio)",
                url = if (isSeries) "https://player.smashy.stream/tv/$anyTarget?s=$sNum&e=$eNum" else "https://player.smashy.stream/movie/$anyTarget",
                color = Color(0xFFEA580C),
                badge = "MULTI-AUDIO",
                isHindi = true
            )
        )
    }

    // 43d. Client-Direct Cloud Servers (resolved 100% on the phone via parxstream API — zero DB / zero Vercel load)
    if (tmdbTarget != null) {
        servers.add(
            StreamServer(
                id = "parx_cinepro",
                name = "CinePro (Direct 1080p HLS)",
                url = if (isSeries) "parx://cinepro/tv/$tmdbTarget/$sNum/$eNum" else "parx://cinepro/movie/$tmdbTarget",
                color = Color(0xFF10B981),
                badge = "DIRECT 1080P",
                isHindi = false
            )
        )
    }
    if (movie.type.equals("anime", ignoreCase = true)) {
        servers.add(
            StreamServer(
                id = "parx_animesalt",
                name = "AnimeSalt (Multi-Audio HLS)",
                url = "parx://animesalt/tv/${movie.id}/$sNum/$eNum",
                color = Color(0xFFEC4899),
                badge = "ANIME MULTI",
                isHindi = true
            )
        )
    }
    servers.add(
        StreamServer(
            id = "parx_moviebox",
            name = "MovieBox Cloud (Direct MP4)",
            url = if (isSeries) "parx://movieBoxWeb/tv/${movie.id}/$sNum/$eNum" else "parx://movieBoxWeb/movie/${movie.id}",
            color = Color(0xFF8B5CF6),
            badge = "DIRECT MP4",
            isHindi = true
        )
    )
    servers.add(
        StreamServer(
            id = "parx_4khdhub",
            name = "4KHDHub Cloud (Direct HD)",
            url = if (isSeries) "parx://4khdhub/tv/${movie.id}/$sNum/$eNum" else "parx://4khdhub/movie/${movie.id}",
            color = Color(0xFF0EA5E9),
            badge = "CLOUD 1080P",
            isHindi = true
        )
    )

    // 44. Official Trailer (HD) (Matches WatchPageClient.tsx lines 2090-2101)
    val trailerYtId = extractYouTubeVideoId(movie.trailerUrl)
    if (trailerYtId != null) {
        servers.add(
            StreamServer(
                id = "trailer",
                name = "Official Trailer (HD)",
                url = "https://www.youtube-nocookie.com/embed/$trailerYtId?autoplay=1&rel=0&modestbranding=1&playsinline=1&iv_load_policy=3&fs=1",
                color = Color(0xFFEF4444),
                badge = "OFFICIAL 4K",
                isHindi = false
            )
        )
    }

    // 6. Deduplicate strictly by Server ID
    val uniqueServers = servers.distinctBy { it.id }

    // 7. Admin Panel Streaming Server Filter (Matches WatchPageClient.tsx isServerEnabled)
    val rawConfig = com.nexiplay.app.data.util.AdManager.appEnabledServers
    if (rawConfig != null) {
        val trimmed = rawConfig.trim()
        if (trimmed.isEmpty() || trimmed.equals("none", ignoreCase = true)) {
            return emptyList()
        }
        val tokens = trimmed.lowercase().split(",").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
        val builtInIds = setOf(
            "onlyflix_fast", "onlyflix_soap", "onlyflix_balance",
            "ultra_4k", "luma_stream", "nexi_frame", "nexi_joy", "cinesrc", "cineruv",
            "vexo", "atlantic", "vidbolt", "nexi_velvet", "nexi_flow", "nexi_love",
            "nexi_core", "nexi_cinema", "nexi_nerds", "vidrock", "videasy", "vidlink",
            "vidsrc_pm", "upcloud", "flixer", "vidnest", "primesrc", "vidspark", "vidstuck",
            "cinemaos", "vidplay", "vidfast", "vidsrc_to", "twoembed", "moviesapi", "vixsrc", "vixsrc_hindi",
            "nontongo", "multiembed", "screenscape", "godrive", "vidapi", "vidcore",
            "vidsrc_su", "anyembed", "autoembed_pro", "smashystream", "parx_cinepro", "parx_animesalt", "parx_moviebox", "parx_4khdhub", "trailer"
        )
        return uniqueServers.filter { server ->
            val id = server.id.lowercase()
            val cleanId = id.removePrefix("multi_")
            if (id.startsWith("desidub") || id == "toonplay" || id == "rareanimes" || id == "animerulz") {
                true
            } else if (disabledMultiIds.contains(id) || disabledMultiIds.contains(cleanId)) {
                false
            } else if (tokens.contains("!$id") || tokens.contains("disable_$id") || tokens.contains("!$cleanId") || tokens.contains("disable_$cleanId")) {
                false
            } else if (id == "custom_legacy" || id == "legacy") {
                tokens.contains("custom")
            } else {
                tokens.contains(id) || tokens.contains(cleanId) || tokens.contains("multi_$cleanId") || builtInIds.contains(id)
            }
        }
    }

    return uniqueServers
}
// Sites that need Chrome's full download UI
// Sites that need Chrome's full download UI or native app handler
private val CHROME_DOWNLOAD_HOSTS = listOf(
    "mega.nz", "mega.co.nz", "mega.io",
    "pixeldrain.com", "pixeldrain.dev",
    "pcloud.com", "pcloud.link",
    "mediafire.com",
    "drive.google.com", "docs.google.com",
    "terabox.com", "1024terabox.com", "teraboxapp.com",
    "animesalt.cx"
)

private fun needsChromeDownload(url: String): Boolean {
    val lowerUrl = url.lowercase()
    if (lowerUrl.contains("pcloud.") || lowerUrl.contains("pixeldrain.") || lowerUrl.contains("mega.") || lowerUrl.contains("mediafire.com") || lowerUrl.contains("drive.google.com") || lowerUrl.contains("terabox.") || lowerUrl.contains("1024terabox.") || lowerUrl.contains("animesalt.")) {
        return true
    }
    val host = try { java.net.URI(lowerUrl).host ?: "" } catch (_: Exception) { "" }
    return CHROME_DOWNLOAD_HOSTS.any { host.contains(it) }
}

private fun openLink(url: String, context: android.content.Context, navController: NavController) {
    if (needsChromeDownload(url)) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
        context.startActivity(intent)
    } else {
        val encodedUrl = java.net.URLEncoder.encode(url, "UTF-8")
        navController.navigate("webview/$encodedUrl")
    }
}

private fun trackDownload(
    context: android.content.Context,
    movie: Movie,
    provider: String,
    resolution: String,
    episodeId: String? = null,
    seasonNumber: Int? = null,
    episodeNumber: Int? = null
) {
    com.nexiplay.app.data.util.CoinRewardHelper.recordDownload(context)
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
            // Download coin reward recorded above
            SupabaseClient.main.from("user_events").insert(event)
            // Download coin reward recorded above
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}

@Composable
private fun DownloadLinkCard(
    dl: DownloadLink,
    movie: Movie,
    context: android.content.Context,
    navController: NavController
) {
    val links = listOfNotNull(
        dl.gdriveLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("Google Drive", "gdrive_link", it) },
        dl.megaLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("Mega Cloud", "mega_link", it) },
        dl.mediafireLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("MediaFire", "mediafire_link", it) },
        dl.teraboxLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("TeraBox", "terabox_link", it) },
        dl.pcloudLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("pCloud", "pcloud_link", it) },
        dl.pixeldrainLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("Pixeldrain", "pixeldrain_link", it) },
        dl.youtubeLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("YouTube", "youtube_link", it) },
    )
    if (links.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeCard(), RoundedCornerShape(14.dp))
            .border(1.dp, themeBorder(), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Cloud Mirrors (${dl.resolution})",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = themeTextPrimary(),
                    fontFamily = InterFont
                )
                if (!dl.fileSize.isNullOrBlank()) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "• ${dl.fileSize}",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFA78BFA),
                        fontFamily = InterFont
                    )
                }
            }
            Text(
                "${links.size} servers",
                fontSize = 11.sp,
                color = themeTextTertiary(),
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.height(10.dp))
        @OptIn(ExperimentalLayoutApi::class)
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            links.forEach { (name, statusKey, url) ->
                val isExpired = dl.linkStatus?.get(statusKey) == "EXPIRED"
                Box(
                    modifier = Modifier
                        .height(36.dp)
                        .background(
                            if (isExpired) Color.Gray.copy(alpha = 0.12f)
                            else NexiRed.copy(alpha = 0.16f),
                            RoundedCornerShape(10.dp)
                        )
                        .border(
                            1.dp,
                            if (isExpired) Color.Gray.copy(alpha = 0.25f) else NexiRed.copy(alpha = 0.4f),
                            RoundedCornerShape(10.dp)
                        )
                        .bounceClick {
                            if (!isExpired) {
                                trackDownload(context, movie, name, dl.resolution)
                                openLink(url, context, navController)
                            }
                        }
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpired) Icons.Default.ErrorOutline else Icons.Default.CloudDownload,
                            contentDescription = null,
                            tint = if (isExpired) Color.Gray else NexiRed,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = if (isExpired) "$name (EXPIRED)" else name,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = if (isExpired) Color.Gray else NexiRed,
                            fontFamily = InterFont
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeDownloadLinkCard(
    dl: EpisodeDownloadLink,
    movie: Movie,
    ep: Episode,
    seasonNumber: Int?,
    context: android.content.Context,
    navController: NavController
) {
    val links = listOfNotNull(
        dl.gdriveLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("GDrive", "gdrive_link", it) },
        dl.megaLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("Mega", "mega_link", it) },
        dl.mediafireLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("MediaFire", "mediafire_link", it) },
        dl.teraboxLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("TeraBox", "terabox_link", it) },
        dl.pcloudLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("pCloud", "pcloud_link", it) },
        dl.pixeldrainLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("Pixeldrain", "pixeldrain_link", it) },
        dl.youtubeLink?.takeIf { it.isNotBlank() && !it.contains("fastdlserver.site", ignoreCase = true) }?.let { Triple("YouTube", "youtube_link", it) },
    )
    if (links.isEmpty()) return

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeBg(), RoundedCornerShape(10.dp))
            .border(1.dp, themeBorder(), RoundedCornerShape(10.dp))
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                dl.resolution,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = themeTextPrimary(),
                fontFamily = InterFont
            )
            if (!dl.fileSize.isNullOrBlank()) {
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
            links.forEach { (name, statusKey, url) ->
                val isExpired = dl.linkStatus?.get(statusKey) == "EXPIRED"
                Box(
                    modifier = Modifier
                        .height(32.dp)
                        .background(
                            if (isExpired) Color.Gray.copy(alpha = 0.12f)
                            else NexiRed.copy(alpha = 0.15f),
                            RoundedCornerShape(8.dp)
                        )
                        .bounceClick {
                            if (!isExpired) {
                                trackDownload(context, movie, name, dl.resolution, ep.id, seasonNumber, ep.episodeNumber)
                                openLink(url, context, navController)
                            }
                        }
                        .padding(horizontal = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (isExpired) "$name (EXPIRED)" else name,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isExpired) Color.Gray else NexiRed,
                        fontFamily = InterFont
                    )
                }
            }
        }
    }
}
