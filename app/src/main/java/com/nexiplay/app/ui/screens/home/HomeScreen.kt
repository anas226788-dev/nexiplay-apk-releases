package com.nexiplay.app.ui.screens.home

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import android.app.Activity
import androidx.compose.ui.platform.LocalContext
import com.nexiplay.app.data.util.AdManager
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
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
import com.nexiplay.app.ui.components.InlineNotice
import com.nexiplay.app.ui.viewmodels.GlobalNoticeViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.Upcoming
import com.nexiplay.app.data.model.UpdateItem
import com.nexiplay.app.ui.components.MovieCard
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.components.shimmerEffect
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.delay
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.ExperimentalMaterial3Api
import com.nexiplay.app.ui.screens.notifications.NotificationViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    vm: HomeViewModel = viewModel(),
    notifVm: NotificationViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val notifState by notifVm.state.collectAsState()
    val unreadCount = notifState.notifications.count { !it.isRead }
    val activity = LocalContext.current as? Activity

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { vm.loadHome(isRefresh = true) },
        modifier = Modifier.fillMaxSize()
    ) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
            .verticalScroll(rememberScrollState())
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("NexiPlay", fontFamily = InterFont, fontSize = 24.sp, fontWeight = FontWeight.Black, color = NexiRed)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                    Icon(Icons.Default.Search, "Search", tint = themeTextPrimary())
                }
                IconButton(onClick = { navController.navigate(Screen.Notifications.route) }) {
                    if (unreadCount > 0) {
                        BadgedBox(
                            badge = {
                                Badge(containerColor = NexiRed) {
                                    Text(unreadCount.toString(), color = Color.White)
                                }
                            }
                        ) {
                            Icon(Icons.Default.Notifications, "Notifications", tint = themeTextPrimary())
                        }
                    } else {
                        Icon(Icons.Default.Notifications, "Notifications", tint = themeTextPrimary())
                    }
                }
            }
        }

        if (state.isLoading) {
            HomeShimmerLoading()
        } else if (state.error != null) {
            com.nexiplay.app.ui.components.ErrorView(
                message = state.error!!,
                onRetry = { vm.loadHome() }
            )
        } else {
            // ── Inline App Notices (from GlobalNoticeViewModel) ──
            val noticeVm: GlobalNoticeViewModel = viewModel()
            val allNotices by noticeVm.notices.collectAsState()
            val dismissedIds by noticeVm.dismissedNoticeIds.collectAsState()
            val inlineNotices = allNotices.filter { 
                it.id !in dismissedIds && 
                it.type == "inline" && 
                (it.pages == "all" || it.pages == "home")
            }
            if (inlineNotices.isNotEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    inlineNotices.forEach { notice ->
                        InlineNotice(notice = notice, onDismiss = { noticeVm.dismissNotice(notice.id) })
                    }
                }
            }

            // ── Auto-Sliding Hero Banner (like web) ──
            if (state.trending.isNotEmpty()) {
                HeroSlider(
                    movies = state.trending,
                    onMovieClick = { movie ->
                        activity?.let {
                            AdManager.showInterstitialAd(it) {
                                navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                            }
                        } ?: navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                    }
                )
            }

            Spacer(Modifier.height(20.dp))

            // ── Web-Style Latest Updates ──
            if (state.updates.isNotEmpty()) {
                LatestUpdatesSection(
                    updates = state.updates,
                    navController = navController,
                )
            } else if (state.running.isNotEmpty()) {
                // Fallback to running movies if updates is empty
                RunningSection(
                    items = state.running,
                    navController = navController,
                )
            }

            // ── Upcoming Section (like web) ──
            if (state.upcoming.isNotEmpty()) {
                UpcomingSection(
                    items = state.upcoming,
                    navController = navController,
                )
            }

            // ── Trending ──
            if (state.trending.size > 1) {
                ContentRow("🔥 Trending Now", state.trending, navController)
            }

            // ── Recent Movies ──
            if (state.recentMovies.isNotEmpty()) {
                ContentRow("🎬 Recent Movies", state.recentMovies, navController)
            }

            // ── Recent Anime ──
            if (state.recentAnime.isNotEmpty()) {
                ContentRow("⚡ Recent Anime", state.recentAnime, navController)
            }

            // ── Recent Series ──
            if (state.recentSeries.isNotEmpty()) {
                ContentRow("📺 Recent Series", state.recentSeries, navController)
            }

            Spacer(Modifier.height(24.dp))
        }
    }
    }
}

// ═══════════════════════════════════════════════════
// Auto-Sliding Hero Banner — Like the Web HeroSlider
// ═══════════════════════════════════════════════════
@Composable
private fun HeroSlider(movies: List<Movie>, onMovieClick: (Movie) -> Unit) {
    val pagerState = rememberPagerState(pageCount = { movies.size })

    // Auto-slide every 5 seconds
    LaunchedEffect(pagerState) {
        while (true) {
            delay(5000)
            val next = (pagerState.currentPage + 1) % movies.size
            pagerState.animateScrollToPage(next, animationSpec = tween(800))
        }
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxWidth().height(420.dp),
        ) { page ->
            val movie = movies[page]
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .bounceClick { onMovieClick(movie) }
            ) {
                // Parallax Background image
                val pageOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
                
                AsyncImage(
                    model = movie.bannerUrlMobile ?: movie.bannerUrlDesktop ?: movie.posterUrl,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().graphicsLayer {
                        translationX = pageOffset * size.width * 0.3f
                    },
                )

                // Dark gradient from bottom
                val bgGradientEnd = themeBg()
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                bgGradientEnd.copy(alpha = 0.3f),
                                bgGradientEnd.copy(alpha = 0.85f),
                                bgGradientEnd,
                            )
                        )
                    )
                )

                // Radial gradient from left (like web)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.6f),
                                Color.Transparent,
                            ),
                            endX = 600f,
                        )
                    )
                )

                // Content overlay
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(horizontal = 20.dp, vertical = 40.dp)
                ) {
                    // Type badge + Year
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .background(NexiRed, RoundedCornerShape(20.dp))
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                movie.type.uppercase(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                color = themeTextPrimary(),
                                letterSpacing = 1.sp,
                            )
                        }
                        if (movie.isAdult == true) {
                            Box(
                                Modifier
                                    .background(
                                        Brush.horizontalGradient(listOf(NexiRedDark, NexiRed)),
                                        RoundedCornerShape(20.dp)
                                    )
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("🔞 18+", fontSize = 10.sp, fontWeight = FontWeight.Black, color = themeTextPrimary())
                            }
                        }
                        if (movie.releaseYear != null) {
                            Text("${movie.releaseYear}", fontSize = 13.sp, color = themeTextSecondary(), fontWeight = FontWeight.Medium)
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // Title
                    Text(
                        text = movie.title,
                        fontSize = 28.sp,
                        fontWeight = FontWeight.Black,
                        color = themeTextPrimary(),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 34.sp,
                    )

                    // Description
                    if (!movie.description.isNullOrBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = movie.description,
                            fontSize = 13.sp,
                            color = themeTextSecondary(),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp,
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // Action buttons
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = { onMovieClick(movie) },
                            colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Watch Now", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                        OutlinedButton(
                            onClick = { onMovieClick(movie) },
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = themeTextPrimary()),
                            modifier = Modifier.height(44.dp),
                        ) {
                            Icon(Icons.Default.Info, null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Details", fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        // ── Page Indicator (dots like web) ──
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            repeat(movies.size) { idx ->
                val isActive = pagerState.currentPage == idx
                Box(
                    modifier = Modifier
                        .height(8.dp)
                        .width(if (isActive) 24.dp else 8.dp)
                        .clip(CircleShape)
                        .background(if (isActive) NexiRed else themeTextPrimary().copy(alpha = 0.4f))
                        .animateContentSize(animationSpec = tween(300))
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════
// Running / Latest Updates Section (like web)
// ═══════════════════════════════════════════════
@Composable
private fun RunningSection(items: List<Movie>, navController: NavController) {
    val activity = LocalContext.current as? Activity
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(8.dp).clip(CircleShape).background(SuccessGreen)
                )
                Spacer(Modifier.width(8.dp))
                Text("Latest Updates", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            }
            Text("Running", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { movie ->
                RunningCard(
                    movie = movie,
                    onClick = {
                        val act = activity
                        if (act != null) {
                            AdManager.showInterstitialAd(act) {
                                navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                            }
                        } else {
                            navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RunningCard(movie: Movie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .width(150.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(210.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Bottom gradient
            Box(
                Modifier.fillMaxWidth().height(80.dp).align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.9f))))
            )

            // Green "RUNNING" indicator at top
            Row(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
                    .background(SuccessGreen.copy(alpha = 0.9f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(5.dp).clip(CircleShape).background(themeTextPrimary()))
                Spacer(Modifier.width(4.dp))
                Text("RUNNING", fontSize = 8.sp, fontWeight = FontWeight.Black, color = themeTextPrimary())
            }

            // Type badge
            Box(
                Modifier.padding(6.dp).align(Alignment.TopEnd)
                    .background(NexiRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(movie.type.uppercase(), fontSize = 8.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            }

            // Episode info at bottom
            Column(
                modifier = Modifier.align(Alignment.BottomStart).padding(8.dp)
            ) {
                if (movie.lastEpisode != null) {
                    Text("EP ${movie.lastEpisode}", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = CoinGold)
                }
                if (movie.runningNotice != null) {
                    Text(movie.runningNotice, fontSize = 10.sp, color = themeTextSecondary(), maxLines = 1)
                } else if (movie.nextEpisodeDate != null) {
                    Text("Next: ${movie.nextEpisodeDate}", fontSize = 10.sp, color = themeTextSecondary())
                }
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = movie.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeTextPrimary(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
    }
}

// ═══════════════════════════════════════
// Content Row (horizontal scroll)
// ═══════════════════════════════════════
@Composable
private fun ContentRow(title: String, items: List<Movie>, navController: NavController) {
    val activity = LocalContext.current as? Activity
    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Text(
            text = title,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = themeTextPrimary(),
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { movie ->
                MovieCard(
                    movie = movie,
                    onClick = {
                        val act = activity
                        if (act != null) {
                            AdManager.showInterstitialAd(act) {
                                navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                            }
                        } else {
                            navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun HomeShimmerLoading() {
    Column(modifier = Modifier.fillMaxWidth()) {
        // Hero Banner Shimmer
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(420.dp)
                .shimmerEffect()
        )
        Spacer(Modifier.height(20.dp))
        
        // Rows Shimmer
        repeat(3) {
            Column(modifier = Modifier.padding(bottom = 24.dp)) {
                // Title Shimmer
                Box(
                    modifier = Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .width(150.dp)
                        .height(24.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .shimmerEffect()
                )
                
                // Cards Shimmer
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(4) {
                        Column(modifier = Modifier.width(130.dp)) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(190.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .shimmerEffect()
                            )
                            Spacer(Modifier.height(6.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .shimmerEffect()
                            )
                            Spacer(Modifier.height(4.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.5f)
                                    .height(14.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .shimmerEffect()
                            )
                        }
                    }
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// Category & Type Navigation Pills (like web)
// ═══════════════════════════════════════════════════
@Composable
private fun CategoryFilterPills(
    categories: List<com.nexiplay.app.data.model.Category>,
    onCategoryClick: (String) -> Unit,
    onTypeClick: (String) -> Unit
) {
    val types = listOf(
        "All" to "",
        "Movies" to "movies",
        "Series" to "series",
        "Anime" to "anime",
        "Novels" to "novels"
    )

    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items(types) { (label, key) ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(if (key.isEmpty()) NexiRed else themeCard())
                    .clickable { onTypeClick(key) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary()
                )
            }
        }

        items(categories) { cat ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(themeCard())
                    .clickable { onCategoryClick(cat.slug) }
                    .padding(horizontal = 14.dp, vertical = 6.dp)
            ) {
                Text(
                    text = cat.name,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = themeTextSecondary()
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════
// Web-Matching Latest Updates Section
// ═══════════════════════════════════════════════════
@Composable
private fun LatestUpdatesSection(
    updates: List<UpdateItem>,
    navController: NavController
) {
    val activity = LocalContext.current as? Activity

    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Pulsing Live Indicator
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(SuccessGreen)
                )
                Spacer(Modifier.width(8.dp))
                Text("Latest Updates", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            }
            Text("LIVE", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Black, letterSpacing = 1.sp)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(updates) { item ->
                LatestUpdateCard(
                    item = item,
                    onClick = {
                        val rawSlug = item.slug.removePrefix("/")
                        val parts = rawSlug.split("/")
                        val type = if (parts.size >= 2) parts[0] else (item.contentType ?: "movie")
                        val cleanSlug = if (parts.size >= 2) parts[1] else rawSlug
                        val targetRoute = Screen.ContentDetail.createRoute(type, cleanSlug)

                        val act = activity
                        if (act != null) {
                            AdManager.showInterstitialAd(act) {
                                navController.navigate(targetRoute)
                            }
                        } else {
                            navController.navigate(targetRoute)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun LatestUpdateCard(item: UpdateItem, onClick: () -> Unit) {
    val badgeText = when (item.updateType) {
        "episode" -> {
            val season = item.seasonNumber
            val ep = item.episodeNumber
            if (season != null && ep != null) "S$season EP $ep ADDED"
            else if (ep != null) "EP $ep ADDED"
            else "NEW EPISODE"
        }
        "season" -> {
            val season = item.seasonNumber
            if (season != null) "SEASON $season ADDED"
            else "NEW SEASON"
        }
        else -> "NEW RELEASE"
    }

    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(195.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Bottom dark gradient overlay
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(70.dp)
                    .align(Alignment.BottomCenter)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.95f))))
            )

            // Top-Left "NEW" badge
            Box(
                modifier = Modifier
                    .padding(6.dp)
                    .align(Alignment.TopStart)
                    .background(SuccessGreen, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text("NEW", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
            }

            // Top-Right Content Type badge
            Box(
                Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .background(NexiRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    (item.contentType ?: "movie").uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Bottom Update Badge Text (e.g. S1 EP 5 ADDED)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(Color.Black.copy(alpha = 0.75f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    badgeText,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = CoinGold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeTextPrimary(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            lineHeight = 16.sp,
        )
    }
}

// ═══════════════════════════════════════════════════
// Web-Matching Upcoming Section
// ═══════════════════════════════════════════════════
@Composable
private fun UpcomingSection(
    items: List<Upcoming>,
    navController: NavController
) {
    val activity = LocalContext.current as? Activity

    Column(modifier = Modifier.padding(bottom = 20.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("🔥 Upcoming Releases", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            Text("COMING SOON", fontSize = 11.sp, color = NexiRed, fontWeight = FontWeight.Bold)
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items) { item ->
                UpcomingCard(
                    item = item,
                    onClick = {
                        val rawSlug = item.slug.removePrefix("/")
                        val parts = rawSlug.split("/")
                        val type = if (parts.size >= 2) parts[0] else item.type
                        val cleanSlug = if (parts.size >= 2) parts[1] else rawSlug
                        val targetRoute = Screen.ContentDetail.createRoute(type, cleanSlug)

                        val act = activity
                        if (act != null) {
                            AdManager.showInterstitialAd(act) {
                                navController.navigate(targetRoute)
                            }
                        } else {
                            navController.navigate(targetRoute)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun UpcomingCard(item: Upcoming, onClick: () -> Unit) {
    val (statusColor, statusBg) = when (item.status.lowercase()) {
        "confirmed" -> SuccessGreen to SuccessGreen.copy(alpha = 0.2f)
        "delayed" -> NexiRed to NexiRed.copy(alpha = 0.2f)
        else -> Color(0xFF3B82F6) to Color(0xFF3B82F6).copy(alpha = 0.2f)
    }

    Column(
        modifier = Modifier
            .width(135.dp)
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(190.dp)
                .clip(RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = item.posterUrl,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )

            // Top-Right Content Type Badge
            Box(
                Modifier
                    .padding(6.dp)
                    .align(Alignment.TopEnd)
                    .background(NexiRed, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    item.type.uppercase(),
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            // Bottom Status Badge
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .background(statusBg, RoundedCornerShape(6.dp))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            ) {
                Text(
                    item.status.uppercase(),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Black,
                    color = statusColor
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        Text(
            text = item.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeTextPrimary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.releaseDate,
            fontSize = 11.sp,
            color = themeTextSecondary(),
            maxLines = 1,
        )
    }
}

