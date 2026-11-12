package com.nexiplay.app.ui.screens.home

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.R
import com.nexiplay.app.data.model.Category
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.Upcoming
import com.nexiplay.app.data.model.UpdateItem
import com.nexiplay.app.ui.components.MovieCard
import com.nexiplay.app.ui.components.NexiSvgIcons
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.components.rememberPosterImageRequest
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.screens.browse.BrowseViewModel
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val DEFAULT_TELEGRAM_URL = "https://t.me/+WNS6y-6tB94xNDVl"

private data class WebsiteTypeTab(
    val label: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

private data class WebsiteDrawerLink(
    val label: String,
    val subtitle: String,
    val icon: ImageVector,
    val accent: Color,
    val onClick: () -> Unit
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    navController: NavController,
    vm: HomeViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    var showWebsiteMenuSheet by remember { mutableStateOf(false) }

    LaunchedEffect(state.updates) {
        if (state.updates.isNotEmpty()) {
            com.nexiplay.app.data.util.EpisodeUpdatesNotificationManager.checkAndNotifyWithItems(context, state.updates)
        }
    }

    val openTelegram = {
        val url = state.telegramUrl ?: DEFAULT_TELEGRAM_URL
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {}
    }

    val navigateToBrowseTab: (Int) -> Unit = { tabIndex ->
        BrowseViewModel.navigateWithFilter(tab = tabIndex, categorySlug = null)
        navController.navigate(Screen.Browse.route) {
            launchSingleTop = true
        }
    }

    val navigateToCategory: (String?) -> Unit = { slug ->
        BrowseViewModel.navigateWithFilter(tab = 0, categorySlug = slug)
        navController.navigate(Screen.Browse.route) {
            launchSingleTop = true
        }
    }

    // Website Hamburger Navigation Sheet (`Header.tsx` mobile menu)
    if (showWebsiteMenuSheet) {
        ModalBottomSheet(
            onDismissRequest = { showWebsiteMenuSheet = false },
            containerColor = themeSurface(),
            contentColor = themeTextPrimary(),
            dragHandle = { BottomSheetDefaults.DragHandle(color = themeBorderStrong()) }
        ) {
            WebsiteNavigationDrawerContent(
                telegramText = state.telegramText,
                onDismiss = { showWebsiteMenuSheet = false },
                onNavigateRoute = { route ->
                    showWebsiteMenuSheet = false
                    navController.navigate(route) { launchSingleTop = true }
                },
                onNavigateBrowseTab = { tab ->
                    showWebsiteMenuSheet = false
                    navigateToBrowseTab(tab)
                },
                onOpenTelegram = {
                    showWebsiteMenuSheet = false
                    openTelegram()
                }
            )
        }
    }

    PullToRefreshBox(
        isRefreshing = state.isRefreshing,
        onRefresh = { vm.loadHome(isRefresh = true) },
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── Website Sticky Glass Header (`src/components/Header.tsx`) ──
            WebsiteTopHeader(
                onSearchClick = { navController.navigate(Screen.Search.route) },
                onNotificationsClick = { navController.navigate(Screen.Notifications.route) },
                onTelegramClick = openTelegram,
                onMenuClick = { showWebsiteMenuSheet = true }
            )

            if (state.isLoading && state.trending.isEmpty() && state.latestAdditions.isEmpty()) {
                HomeLoadingSkeleton()
            } else if (state.error != null && state.trending.isEmpty() && state.latestAdditions.isEmpty()) {
                HomeErrorView(
                    message = state.error ?: "Couldn't load content.",
                    onRetry = { vm.loadHome(isRefresh = false) }
                )
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 28.dp)
                ) {
                    // 1. Website Full-Bleed Hero Slider (`src/components/HeroSlider.tsx`)
                    if (state.trending.isNotEmpty()) {
                        item(key = "hero_slider") {
                            WebsiteHeroSlider(
                                movies = state.trending.take(6),
                                onMovieClick = { movie ->
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            )
                        }
                    }

                    // 2. Website Neumorphic Inset Search Bar (`src/app/(site)/page.tsx` line 183)
                    item(key = "inset_search_bar") {
                        WebsiteInsetSearchBar(
                            onClick = { navController.navigate(Screen.Search.route) }
                        )
                    }

                    // 3. Website Segmented TypeTabs Bar (`src/app/(site)/page.tsx` lines 69-105)
                    item(key = "website_type_tabs") {
                        WebsiteTypeTabsBar(
                            onSelectBrowseTab = navigateToBrowseTab,
                            onNavigate = { route ->
                                navController.navigate(route) { launchSingleTop = true }
                            }
                        )
                    }

                    // 4. Website CategoryMenu Genre Chips (`src/components/CategoryMenu.tsx`)
                    if (state.categories.isNotEmpty()) {
                        item(key = "website_category_menu") {
                            WebsiteCategoryMenuRow(
                                categories = state.categories,
                                onCategoryClick = navigateToCategory
                            )
                        }
                    }

                    // 5. Website Feature Hub (Live TV, Vibe Check, Games)
                    item(key = "website_feature_banners") {
                        WebsiteFeatureBannersSection(
                            onLiveTvClick = { navController.navigate(Screen.LiveTv.route) },
                            onVibeCheckClick = { navController.navigate(Screen.VibeCheck.route) },
                            onGamesClick = { navController.navigate(Screen.Games.route) }
                        )
                    }

                    // 6. Latest Updates Section (`src/components/LatestUpdates.tsx`)
                    if (state.updates.isNotEmpty()) {
                        item(key = "updates_section") {
                            WebsiteLatestUpdatesSection(
                                updates = state.updates,
                                onUpdateClick = { update ->
                                    val rawSlug = update.slug.trim().removePrefix("/")
                                    if (rawSlug.isNotBlank()) {
                                        val (resolvedType, resolvedSlug) = if (rawSlug.contains("/")) {
                                            val parts = rawSlug.split("/", limit = 2)
                                            parts[0].ifBlank { update.contentType ?: "movie" } to parts[1]
                                        } else {
                                            (update.contentType ?: "movie") to rawSlug
                                        }

                                        if (resolvedSlug.isNotBlank()) {
                                            navController.navigate(
                                                Screen.ContentDetail.createRoute(resolvedType, resolvedSlug)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }

                    // 7. Upcoming Releases Section (`src/components/UpcomingSlider.tsx`)
                    if (state.upcoming.isNotEmpty()) {
                        item(key = "upcoming_section") {
                            Spacer(modifier = Modifier.height(22.dp))
                            WebsiteSectionHeader(title = "Upcoming Releases")
                            Spacer(modifier = Modifier.height(10.dp))
                            LazyRow(
                                contentPadding = PaddingValues(horizontal = 16.dp),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(state.upcoming, key = { it.id }) { item ->
                                    WebsiteUpcomingCard(item = item)
                                }
                            }
                        }
                    }

                    // 8. Trending Now Section
                    if (state.trending.isNotEmpty()) {
                        item(key = "trending_section") {
                            WebsiteMovieRowSection(
                                title = "Trending Now",
                                movies = state.trending,
                                onSeeAll = { navigateToBrowseTab(0) },
                                onMovieClick = { movie ->
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            )
                        }
                    }

                    // Non-intrusive Home Banner Ad (Start.io)
                    item(key = "home_banner_ad") {
                        com.nexiplay.app.ui.components.AppBannerAd(
                            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp)
                        )
                    }

                    // 9. Currently Airing / Simulcast Section
                    if (state.running.isNotEmpty()) {
                        item(key = "running_section") {
                            WebsiteMovieRowSection(
                                title = "Currently Airing",
                                movies = state.running,
                                onSeeAll = { navigateToBrowseTab(4) },
                                onMovieClick = { movie ->
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            )
                        }
                    }

                    // 10. Latest Additions Section (`page.tsx` line 231)
                    if (state.latestAdditions.isNotEmpty()) {
                        item(key = "latest_additions_section") {
                            WebsiteMovieRowSection(
                                title = "Latest Additions",
                                movies = state.latestAdditions,
                                onSeeAll = { navigateToBrowseTab(0) },
                                onMovieClick = { movie ->
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            )
                        }
                    }

                    // 11. Latest Movies Section (`page.tsx` line 248)
                    if (state.recentMovies.isNotEmpty()) {
                        item(key = "latest_movies_section") {
                            WebsiteMovieRowSection(
                                title = "Latest Movies",
                                movies = state.recentMovies,
                                onSeeAll = { navigateToBrowseTab(1) },
                                onMovieClick = { movie ->
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            )
                        }
                    }

                    // 12. Latest Series Section (`page.tsx` line 264)
                    if (state.recentSeries.isNotEmpty()) {
                        item(key = "latest_series_section") {
                            WebsiteMovieRowSection(
                                title = "Latest Series",
                                movies = state.recentSeries,
                                onSeeAll = { navigateToBrowseTab(3) },
                                onMovieClick = { movie ->
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            )
                        }
                    }

                    // Start.io Native / MREC Ad (only rendered when Native Ads toggle is enabled)
                    item(key = "home_native_ad") {
                        com.nexiplay.app.ui.components.AppNativeAd(
                            modifier = Modifier.padding(top = 10.dp)
                        )
                    }

                    // 13. Latest Anime Section (`page.tsx` line 280)
                    if (state.recentAnime.isNotEmpty()) {
                        item(key = "latest_anime_section") {
                            WebsiteMovieRowSection(
                                title = "Latest Anime",
                                movies = state.recentAnime,
                                onSeeAll = { navigateToBrowseTab(2) },
                                onMovieClick = { movie ->
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Matches `src/components/Header.tsx` 1:1:
 * - Left: Emblem with radial `#9273F5` glow + `NEXIPLAY` (`PLAY` in `#9273F5`)
 * - Right: Telegram pill (`#229ED9/15`), Search circle, Bell circle, Hamburger Menu circle
 */
@Composable
private fun WebsiteTopHeader(
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onTelegramClick: () -> Unit,
    onMenuClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(themeBg().copy(alpha = 0.94f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo + NEXIPLAY wordmark (`Header.tsx` lines 65-77)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        NexiRed.copy(alpha = 0.35f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.ic_emblem_clean),
                        contentDescription = "Nexiplay",
                        modifier = Modifier.size(34.dp),
                        contentScale = ContentScale.Fit
                    )
                }

                Text(
                    text = buildAnnotatedString {
                        withStyle(SpanStyle(color = themeTextPrimary(), fontWeight = FontWeight.ExtraBold)) {
                            append("NEXI")
                        }
                        withStyle(SpanStyle(color = NexiRed, fontWeight = FontWeight.ExtraBold)) {
                            append("PLAY")
                        }
                    },
                    fontFamily = InterFont,
                    fontSize = 18.sp,
                    letterSpacing = (-0.5).sp
                )
            }

            // Right Action Controls (`Header.tsx` lines 105-168)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp)
            ) {
                // Website Telegram Pill (`bg-[#229ED9]/15 border-[#229ED9]/30 text-[#229ED9]`)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0xFF229ED9).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFF229ED9).copy(alpha = 0.32f), RoundedCornerShape(50))
                        .bounceClick(onClick = onTelegramClick)
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        Icon(
                            imageVector = NexiSvgIcons.Telegram,
                            contentDescription = "Telegram",
                            tint = Color(0xFF229ED9),
                            modifier = Modifier.size(13.dp)
                        )
                        Text(
                            text = "Telegram",
                            color = Color(0xFF229ED9),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFont
                        )
                    }
                }

                // Search Button
                HeaderCircleIconButton(
                    icon = NexiSvgIcons.Search,
                    contentDescription = "Search",
                    onClick = onSearchClick
                )

                // Notification Bell Button with Mint Dot
                Box {
                    HeaderCircleIconButton(
                        icon = NexiSvgIcons.Bell,
                        contentDescription = "Notifications",
                        onClick = onNotificationsClick
                    )
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = (-2).dp, y = 2.dp)
                            .size(7.dp)
                            .background(NexiRed, CircleShape)
                            .border(1.dp, themeBg(), CircleShape)
                    )
                }

                // Hamburger Menu Button (Opens Full Website Navigation Drawer)
                HeaderCircleIconButton(
                    icon = NexiSvgIcons.Menu,
                    contentDescription = "Menu",
                    onClick = onMenuClick
                )
            }
        }

        // Subtle bottom hairline border (`border-b border-white/[0.06]`)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(themeBorder())
        )
    }
}

@Composable
private fun HeaderCircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(themeCard())
            .border(1.dp, themeBorder(), CircleShape)
            .bounceClick(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = themeTextSecondary(),
            modifier = Modifier.size(15.dp)
        )
    }
}

/**
 * Matches `src/components/HeroSlider.tsx` 1:1:
 * - Full-bleed edge-to-edge backdrop with smooth bottom & left `#0D0B12` gradients
 * - Left & Right circular SVG chevron buttons (`NexiSvgIcons.ChevronLeft` / `ChevronRight`)
 * - Type badge (`#9273F5/20`), `18+` badge (`#FFB878/20`), Release Year
 * - Bold title + 2-line synopsis
 * - `Watch Now` `.np-btn-primary` clay button + `Details` `.np-btn-ghost` button
 * - Bottom pagination pills (`28.dp` active `#9273F5` pill)
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WebsiteHeroSlider(
    movies: List<Movie>,
    onMovieClick: (Movie) -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { movies.size })
    val coroutineScope = rememberCoroutineScope()

    // Auto-advance every 6000ms matching `HeroSlider.tsx`
    LaunchedEffect(movies.size) {
        if (movies.size > 1) {
            while (true) {
                delay(6000)
                if (!pagerState.isScrollInProgress) {
                    val next = (pagerState.currentPage + 1) % movies.size
                    pagerState.animateScrollToPage(next)
                }
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(420.dp)
            .background(themeBg())
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize()
        ) { page ->
            val movie = movies[page]
            val bannerUrl = movie.bannerUrlMobile?.takeIf { it.isNotBlank() }
                ?: movie.bannerUrlDesktop?.takeIf { it.isNotBlank() }
                ?: movie.posterUrl

            val heroRequest = rememberPosterImageRequest(bannerUrl, 780, 560)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clickable { onMovieClick(movie) }
            ) {
                if (heroRequest != null) {
                    AsyncImage(
                        model = heroRequest,
                        contentDescription = movie.title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }

                // Bottom-to-top gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colorStops = arrayOf(
                                    0.0f to themeBg().copy(alpha = 0.25f),
                                    0.45f to themeBg().copy(alpha = 0.45f),
                                    0.82f to themeBg().copy(alpha = 0.92f),
                                    1.0f to themeBg()
                                )
                            )
                        )
                )

                // Left-to-right gradient
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colorStops = arrayOf(
                                    0.0f to themeBg().copy(alpha = 0.82f),
                                    0.55f to themeBg().copy(alpha = 0.28f),
                                    1.0f to Color.Transparent
                                )
                            )
                        )
                )

                // Hero Content Overlay (`HeroSlider.tsx` lines 77-121)
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .padding(horizontal = 18.dp)
                        .padding(bottom = 32.dp)
                ) {
                    // Metadata Badges Row
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .background(NexiRed.copy(alpha = 0.22f), RoundedCornerShape(6.dp))
                                .border(1.dp, NexiRed.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 9.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = movie.type.ifBlank { "MOVIE" }.uppercase(),
                                color = NexiRedLight,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.8.sp,
                                fontFamily = InterFont
                            )
                        }

                        if (movie.isAdult == true) {
                            Box(
                                modifier = Modifier
                                    .background(CoinGold.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                                    .border(1.dp, CoinGold.copy(alpha = 0.42f), RoundedCornerShape(6.dp))
                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                            ) {
                                Text(
                                    text = "18+",
                                    color = CoinGold,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFont
                                )
                            }
                        }

                        if (movie.releaseYear != null) {
                            Text(
                                text = movie.releaseYear.toString(),
                                color = TextGray,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = InterFont
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Title
                    Text(
                        text = movie.title,
                        color = TextWhite,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = InterFont,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        lineHeight = 31.sp
                    )

                    if (!movie.description.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = movie.description,
                            color = TextGray,
                            fontSize = 12.5.sp,
                            fontFamily = InterFont,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            lineHeight = 18.sp
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Action Buttons (`Watch Now` + `Details`)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // `.np-btn-primary` Watch Now Button
                        Box(
                            modifier = Modifier
                                .shadow(
                                    elevation = 12.dp,
                                    shape = RoundedCornerShape(12.dp),
                                    ambientColor = NexiRed,
                                    spotColor = NexiRed
                                )
                                .clip(RoundedCornerShape(12.dp))
                                .background(
                                    Brush.linearGradient(
                                        colors = listOf(NexiRed, NexiRedDark)
                                    )
                                )
                                .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(12.dp))
                                .bounceClick { onMovieClick(movie) }
                                .padding(horizontal = 18.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = NexiSvgIcons.PlayFilled,
                                    contentDescription = "Watch Now",
                                    tint = TextWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Watch Now",
                                    color = TextWhite,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = InterFont
                                )
                            }
                        }

                        // `.np-btn-ghost` Details Button
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF17141F).copy(alpha = 0.85f))
                                .border(1.dp, Color.White.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
                                .bounceClick { onMovieClick(movie) }
                                .padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(7.dp)
                            ) {
                                Icon(
                                    imageVector = NexiSvgIcons.InfoOutline,
                                    contentDescription = "Details",
                                    tint = TextWhite,
                                    modifier = Modifier.size(15.dp)
                                )
                                Text(
                                    text = "Details",
                                    color = TextWhite,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    fontFamily = InterFont
                                )
                            }
                        }
                    }
                }
            }
        }

        // Left & Right Circular Navigation Arrows (`HeroSlider.tsx` lines 125-152)
        if (movies.size > 1) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF17141F).copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .bounceClick {
                        coroutineScope.launch {
                            val prev = if (pagerState.currentPage == 0) movies.size - 1 else pagerState.currentPage - 1
                            pagerState.animateScrollToPage(prev)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NexiSvgIcons.ChevronLeft,
                    contentDescription = "Previous Slide",
                    tint = TextWhite,
                    modifier = Modifier.size(16.dp)
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 10.dp)
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF17141F).copy(alpha = 0.65f))
                    .border(1.dp, Color.White.copy(alpha = 0.12f), CircleShape)
                    .bounceClick {
                        coroutineScope.launch {
                            val next = (pagerState.currentPage + 1) % movies.size
                            pagerState.animateScrollToPage(next)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NexiSvgIcons.ChevronRight,
                    contentDescription = "Next Slide",
                    tint = TextWhite,
                    modifier = Modifier.size(16.dp)
                )
            }

            // Bottom Pagination Indicator Dots (`HeroSlider.tsx` lines 155-169)
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(movies.size) { index ->
                    val isSelected = pagerState.currentPage == index
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (isSelected) 26.dp else 6.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) NexiRed
                                else Color.White.copy(alpha = 0.22f)
                            )
                            .clickable {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(index)
                                }
                            }
                    )
                }
            }
        }
    }
}

/**
 * Matches `src/app/(site)/page.tsx` line 183 Neumorphic Inset Search Bar
 */
@Composable
private fun WebsiteInsetSearchBar(onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(top = 12.dp, bottom = 10.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(themeCard())
            .border(1.dp, themeBorder(), RoundedCornerShape(14.dp))
            .bounceClick(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 13.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp)
            ) {
                Icon(
                    imageVector = NexiSvgIcons.Search,
                    contentDescription = "Search",
                    tint = themeTextSecondary(),
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Search movies, anime, or series...",
                    color = themeTextSecondary(),
                    fontSize = 13.sp,
                    fontFamily = InterFont
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(NexiRed.copy(alpha = if (isDark) 0.14f else 0.1f))
                    .border(0.5.dp, NexiRed.copy(alpha = 0.3f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text(
                    text = "SEARCH",
                    color = if (isDark) NexiRedLight else NexiRed,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont
                )
            }
        }
    }
}

/**
 * Matches `TypeTabs` in `src/app/(site)/page.tsx` lines 69-105:
 * Segmented glass container (`bg-[#17141F]/70 border-white/[0.07] rounded-2xl p-1.5`)
 */
@Composable
private fun WebsiteTypeTabsBar(
    onSelectBrowseTab: (Int) -> Unit,
    onNavigate: (String) -> Unit
) {
    val tabs = remember {
        listOf(
            WebsiteTypeTab("All", NexiSvgIcons.Grid) {},
            WebsiteTypeTab("Movies", NexiSvgIcons.Movies) { onSelectBrowseTab(1) },
            WebsiteTypeTab("Series", NexiSvgIcons.Series) { onSelectBrowseTab(3) },
            WebsiteTypeTab("Anime", NexiSvgIcons.Anime) { onSelectBrowseTab(2) },
            WebsiteTypeTab("Novels", NexiSvgIcons.Novels) { onNavigate(Screen.Novels.route) },
            WebsiteTypeTab("Live TV", NexiSvgIcons.LiveTv) { onNavigate(Screen.LiveTv.route) },
            WebsiteTypeTab("Games", NexiSvgIcons.Gamepad) { onNavigate(Screen.Games.route) }
        )
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (isDark) Color(0xFF17141F).copy(alpha = 0.78f) else Color.White)
            .border(1.dp, themeBorder(), RoundedCornerShape(16.dp))
            .padding(6.dp)
    ) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            items(tabs.size) { index ->
                val tab = tabs[index]
                val isSelected = index == 0
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(11.dp))
                        .then(
                            if (isSelected) {
                                Modifier
                                    .background(
                                        Brush.linearGradient(
                                            colors = listOf(NexiRed, NexiRedDark)
                                        )
                                    )
                                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(11.dp))
                            } else {
                                Modifier.background(Color.Transparent)
                            }
                        )
                        .bounceClick(onClick = tab.onClick)
                        .padding(horizontal = 13.dp, vertical = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (isSelected) TextWhite else themeTextSecondary(),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = tab.label,
                            color = if (isSelected) TextWhite else themeTextSecondary(),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            fontFamily = InterFont
                        )
                    }
                }
            }
        }
    }
}

/**
 * Matches `src/components/CategoryMenu.tsx` 1:1
 */
@Composable
private fun WebsiteCategoryMenuRow(
    categories: List<Category>,
    onCategoryClick: (String?) -> Unit
) {
    LazyRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp, bottom = 4.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item(key = "cat_all") {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(
                        Brush.linearGradient(
                            colors = listOf(NexiRed, NexiRedDark)
                        )
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.22f), RoundedCornerShape(10.dp))
                    .bounceClick { onCategoryClick(null) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = "All",
                    color = TextWhite,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont
                )
            }
        }

        items(categories, key = { it.id }) { cat ->
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(themeCard())
                    .border(1.dp, themeBorder(), RoundedCornerShape(10.dp))
                    .bounceClick { onCategoryClick(cat.slug) }
                    .padding(horizontal = 14.dp, vertical = 7.dp)
            ) {
                Text(
                    text = cat.name,
                    color = themeTextSecondary(),
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFont
                )
            }
        }
    }
}

/**
 * Unified Premium Entertainment Hub:
 * Replaces old vertically-stacked banners with a single, ultra-premium 3-tile deck:
 * 1. Live TV (Sports, News, Bangladesh & Global Channels) - Mint
 * 2. Vibe Check (AI-powered Mood Recommendations) - Royal Violet
 * 3. Arcade Games (1,000+ Instant Free HTML5 Games) - Arcade Blue
 */
@Composable
private fun WebsiteFeatureBannersSection(
    onLiveTvClick: () -> Unit,
    onVibeCheckClick: () -> Unit,
    onGamesClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(top = 12.dp, bottom = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp)
    ) {
        WebsiteFeatureHubCard(
            title = "Live TV",
            subtitle = "Sports & News, streaming now",
            badge = "LIVE",
            ctaText = "Watch now",
            icon = NexiSvgIcons.LiveTv,
            baseBgTop = Color(0xFF0C1819),
            baseBgBottom = Color(0xFF0A0E14),
            glowColor = Color(0xFF14B8A6),
            iconBoxTop = Color(0xFF2DD4BF),
            iconBoxBottom = Color(0xFF0D9488),
            iconTint = Color(0xFF04221C),
            badgeBg = Color(0xFF3B1D24).copy(alpha = 0.78f),
            badgeBorder = Color(0xFFF87171).copy(alpha = 0.45f),
            badgeTextColor = Color(0xFFFEE2E2),
            ctaBg = Color(0xFF1DE9B6),
            ctaTextColor = Color(0xFF04221C),
            onClick = onLiveTvClick,
            showLiveIndicator = true
        )

        WebsiteFeatureHubCard(
            title = "Vibe Check",
            subtitle = "AI Mood Match, picks for how you feel",
            badge = "AI",
            ctaText = "Try it",
            icon = NexiSvgIcons.VibeSparkles,
            baseBgTop = Color(0xFF171126),
            baseBgBottom = Color(0xFF0B0A13),
            glowColor = Color(0xFFA855F7),
            iconBoxTop = Color(0xFFB366FF),
            iconBoxBottom = Color(0xFF8B5CF6),
            iconTint = Color(0xFF190730),
            badgeBg = Color(0xFF2A1C42).copy(alpha = 0.80f),
            badgeBorder = Color(0xFFC084FC).copy(alpha = 0.42f),
            badgeTextColor = Color(0xFFF3E8FF),
            ctaBg = Color(0xFFC4B5FD),
            ctaTextColor = Color(0xFF1D0B36),
            onClick = onVibeCheckClick
        )

        WebsiteFeatureHubCard(
            title = "Arcade",
            subtitle = "1,000+ Games, instant play",
            badge = "PLAY",
            ctaText = "Play now",
            icon = NexiSvgIcons.Gamepad,
            baseBgTop = Color(0xFF0F192B),
            baseBgBottom = Color(0xFF090D16),
            glowColor = Color(0xFF3B82F6),
            iconBoxTop = Color(0xFF60A5FA),
            iconBoxBottom = Color(0xFF3B82F6),
            iconTint = Color(0xFF071936),
            badgeBg = Color(0xFF192845).copy(alpha = 0.80f),
            badgeBorder = Color(0xFF60A5FA).copy(alpha = 0.42f),
            badgeTextColor = Color(0xFFDBEAFE),
            ctaBg = Color(0xFF93C5FD),
            ctaTextColor = Color(0xFF081D3A),
            onClick = onGamesClick
        )
    }
}

@Composable
private fun RowScope.WebsiteFeatureHubCard(
    title: String,
    subtitle: String,
    badge: String,
    ctaText: String,
    icon: ImageVector,
    baseBgTop: Color,
    baseBgBottom: Color,
    glowColor: Color,
    iconBoxTop: Color,
    iconBoxBottom: Color,
    iconTint: Color,
    badgeBg: Color,
    badgeBorder: Color,
    badgeTextColor: Color,
    ctaBg: Color,
    ctaTextColor: Color,
    onClick: () -> Unit,
    showLiveIndicator: Boolean = false
) {
    val shape = RoundedCornerShape(18.dp)

    Box(
        modifier = Modifier
            .weight(1f)
            .height(164.dp)
            .shadow(
                elevation = 10.dp,
                shape = shape,
                ambientColor = glowColor.copy(alpha = 0.35f),
                spotColor = glowColor.copy(alpha = 0.45f)
            )
            .clip(shape)
            .background(
                Brush.verticalGradient(
                    colors = listOf(baseBgTop, baseBgBottom)
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.32f),
                        glowColor.copy(alpha = 0.10f),
                        Color.Transparent
                    ),
                    center = Offset(240f, 35f),
                    radius = 230f
                )
            )
            .background(
                Brush.radialGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.22f),
                        Color.Transparent
                    ),
                    center = Offset(45f, 55f),
                    radius = 160f
                )
            )
            .border(
                1.dp,
                Brush.verticalGradient(
                    colors = listOf(
                        glowColor.copy(alpha = 0.55f),
                        glowColor.copy(alpha = 0.18f),
                        glowColor.copy(alpha = 0.38f)
                    )
                ),
                shape
            )
            .bounceClick(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 11.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Row: Solid Neon Squircle Icon + Outlined Pill Badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .shadow(
                            elevation = 8.dp,
                            shape = RoundedCornerShape(11.dp),
                            ambientColor = glowColor,
                            spotColor = glowColor
                        )
                        .clip(RoundedCornerShape(11.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(iconBoxTop, iconBoxBottom)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = iconTint,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(badgeBg)
                        .border(1.dp, badgeBorder, RoundedCornerShape(50))
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (showLiveIndicator) {
                        Box(
                            modifier = Modifier
                                .size(5.dp)
                                .background(Color(0xFFEF4444), CircleShape)
                        )
                    }
                    Text(
                        text = badge,
                        color = badgeTextColor,
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = InterFont,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Middle Column: Bold Title + 2-Line Description
            Column(
                modifier = Modifier.padding(top = 4.dp)
            ) {
                Text(
                    text = title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = InterFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    letterSpacing = (-0.2).sp
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = subtitle,
                    color = Color(0xFF94A3B8),
                    fontSize = 9.5.sp,
                    lineHeight = 12.5.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = InterFont,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Bottom Solid Pill CTA Button ("Watch now ->", "Try it ->", "Play now ->")
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(ctaBg)
                    .padding(horizontal = 10.dp, vertical = 5.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Text(
                    text = "$ctaText \u2192",
                    color = ctaTextColor,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = InterFont,
                    maxLines = 1
                )
            }
        }
    }
}

/**
 * Matches `.section-title` in `src/app/globals.css` + `View All` button in `src/app/(site)/page.tsx`:
 * - Left: `4.dp x 20.dp` vertical `#9273F5` pill bar + ExtraBold title
 * - Right: Optional `View All` `.np-btn-ghost` pill with `NexiSvgIcons.ChevronRight`
 */
@Composable
private fun WebsiteSectionHeader(
    title: String,
    onSeeAll: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(NexiRed)
            )
            Text(
                text = title,
                color = themeTextPrimary(),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = InterFont,
                letterSpacing = (-0.3).sp
            )
        }

        if (onSeeAll != null) {
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(9.dp))
                    .background(themeCard())
                    .border(1.dp, themeBorder(), RoundedCornerShape(9.dp))
                    .bounceClick(onClick = onSeeAll)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "View All",
                    color = if (isDark) NexiRedLight else NexiRed,
                    fontSize = 11.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = InterFont
                )
                Icon(
                    imageVector = NexiSvgIcons.ChevronRight,
                    contentDescription = "View All",
                    tint = if (isDark) NexiRedLight else NexiRed,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
private fun WebsiteMovieRowSection(
    title: String,
    movies: List<Movie>,
    onSeeAll: () -> Unit,
    onMovieClick: (Movie) -> Unit
) {
    Spacer(modifier = Modifier.height(22.dp))
    WebsiteSectionHeader(title = title, onSeeAll = onSeeAll)
    Spacer(modifier = Modifier.height(10.dp))
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = movies,
            key = { "${title}_${it.id}" },
            contentType = { "movie_card" }
        ) { movie ->
            MovieCard(
                movie = movie,
                onClick = { onMovieClick(movie) }
            )
        }
    }
}

/**
 * Matches `src/components/LatestUpdates.tsx` 1:1:
 * - Section Header: Vertical `#9273F5` bar + "Latest Updates" + "LIVE" badge + Left/Right circular scroll buttons
 * - Vertical `2:3` poster cards (`142.dp` wide) with top-left "NEW" badge, top-right contentType badge,
 *   bottom-left update status badge (`S1 EP 5 ADDED` / `EP 5 ADDED` / `SEASON 2 ADDED` / `NEW RELEASE`),
 *   and 2-line title below poster.
 */
@Composable
private fun WebsiteLatestUpdatesSection(
    updates: List<UpdateItem>,
    onUpdateClick: (UpdateItem) -> Unit
) {
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    Spacer(modifier = Modifier.height(18.dp))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .width(3.5.dp)
                    .height(20.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(NexiRed)
            )
            Text(
                text = "Latest Updates",
                color = TextWhite,
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = InterFont
            )
            Box(
                modifier = Modifier
                    .background(NexiRed.copy(alpha = 0.16f), RoundedCornerShape(6.dp))
                    .border(1.dp, NexiRed.copy(alpha = 0.32f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "LIVE",
                    color = NexiRedLight,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = InterFont,
                    letterSpacing = 0.6.sp
                )
            }
        }

        // Scroll Left / Right circular buttons from `LatestUpdates.tsx`
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .clickable {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex - 2).coerceAtLeast(0)
                            listState.animateScrollToItem(target)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NexiSvgIcons.ChevronLeft,
                    contentDescription = "Scroll Left",
                    tint = TextGray,
                    modifier = Modifier.size(14.dp)
                )
            }
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.05f))
                    .border(1.dp, Color.White.copy(alpha = 0.10f), CircleShape)
                    .clickable {
                        scope.launch {
                            val target = (listState.firstVisibleItemIndex + 2)
                                .coerceAtMost((updates.size - 1).coerceAtLeast(0))
                            listState.animateScrollToItem(target)
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NexiSvgIcons.ChevronRight,
                    contentDescription = "Scroll Right",
                    tint = TextGray,
                    modifier = Modifier.size(14.dp)
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(12.dp))

    LazyRow(
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        items(
            items = updates,
            key = { it.id },
            contentType = { "update_card" }
        ) { update ->
            WebsiteUpdateCard(
                update = update,
                onClick = { onUpdateClick(update) }
            )
        }
    }
}

private fun getWebsiteUpdateBadgeText(item: UpdateItem): String {
    val ut = (item.updateType ?: "").lowercase()
    return when {
        ut == "new_episode" && item.episodeNumber != null -> {
            val s = if (item.seasonNumber != null) "S${item.seasonNumber} " else ""
            "${s}EP ${item.episodeNumber} ADDED"
        }
        ut == "new_season" && item.seasonNumber != null -> {
            "SEASON ${item.seasonNumber} ADDED"
        }
        item.episodeNumber != null -> {
            val s = if (item.seasonNumber != null) "S${item.seasonNumber} " else ""
            "${s}EP ${item.episodeNumber} ADDED"
        }
        item.seasonNumber != null -> {
            "SEASON ${item.seasonNumber} ADDED"
        }
        else -> "NEW RELEASE"
    }
}

private val UpdatePosterShape = RoundedCornerShape(14.dp)
private val UpdateBottomGradient = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color.Black.copy(alpha = 0.22f),
        Color.Black.copy(alpha = 0.85f)
    )
)

@Composable
private fun WebsiteUpdateCard(
    update: UpdateItem,
    onClick: () -> Unit
) {
    val badgeText = remember(update) { getWebsiteUpdateBadgeText(update) }
    val imageRequest = rememberPosterImageRequest(update.posterUrl, 300, 450)

    Column(
        modifier = Modifier
            .width(142.dp)
            .bounceClick(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(206.dp)
                .clip(UpdatePosterShape)
                .background(themeCard())
                .border(1.dp, themeBorder(), UpdatePosterShape)
        ) {
            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = update.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.Movies,
                        contentDescription = null,
                        tint = TextGray,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }

            // Bottom gradient overlay (`bg-gradient-to-t from-black/80 via-black/20 to-transparent`)
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(UpdateBottomGradient)
            )

            // Top-left "NEW" violet badge
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .background(Color(0xFF7C3AED).copy(alpha = 0.92f), RoundedCornerShape(5.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    text = "NEW",
                    color = Color.White,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = InterFont,
                    letterSpacing = 0.4.sp
                )
            }

            // Top-right content_type dark glass badge
            if (!update.contentType.isNullOrBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(5.dp))
                        .border(0.5.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(5.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = update.contentType.uppercase(),
                        color = Color(0xFFD1D5DB),
                        fontSize = 8.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFont
                    )
                }
            }

            // Bottom-left update status badge (`S1 EP 5 ADDED` / `NEW RELEASE`)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .background(Color(0xFF6D28D9).copy(alpha = 0.92f), RoundedCornerShape(6.dp))
                    .border(0.5.dp, Color(0xFFA78BFA).copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                    .padding(horizontal = 7.dp, vertical = 3.dp)
            ) {
                Text(
                    text = badgeText,
                    color = Color.White,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(7.dp))

        Text(
            text = update.title,
            color = themeTextPrimary(),
            fontSize = 12.5.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = InterFont,
            maxLines = 2,
            lineHeight = 16.sp,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 2.dp)
        )
    }
}

/**
 * Matches `src/components/UpcomingSlider.tsx` card styling with SVG Calendar icon
 */
@Composable
private fun WebsiteUpcomingCard(item: Upcoming) {
    val imageRequest = rememberPosterImageRequest(item.posterUrl, 480, 280)
    Box(
        modifier = Modifier
            .width(240.dp)
            .height(138.dp)
            .clip(UpdatePosterShape)
            .background(themeCard())
            .border(1.dp, themeBorder(), UpdatePosterShape)
    ) {
        if (imageRequest != null) {
            AsyncImage(
                model = imageRequest,
                contentDescription = item.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            themeBg().copy(alpha = 0.92f)
                        )
                    )
                )
        )

        // Top-left type pill
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(8.dp)
                .background(Color(0xFF111116).copy(alpha = 0.85f), RoundedCornerShape(6.dp))
                .border(0.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(6.dp))
                .padding(horizontal = 7.dp, vertical = 3.dp)
        ) {
            Text(
                text = item.type.uppercase(),
                color = NexiRedLight,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFont
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = item.title,
                color = TextWhite,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = InterFont,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!item.releaseDate.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.Calendar,
                        contentDescription = null,
                        tint = CoinGold,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = item.releaseDate,
                        color = CoinGold,
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFont
                    )
                }
            }
        }
    }
}

/**
 * Website Hamburger Menu Drawer (`src/components/Header.tsx` lines 199-245)
 * Includes all website navigation links + app features with 100% SVG icons.
 */
@Composable
private fun WebsiteNavigationDrawerContent(
    telegramText: String,
    onDismiss: () -> Unit,
    onNavigateRoute: (String) -> Unit,
    onNavigateBrowseTab: (Int) -> Unit,
    onOpenTelegram: () -> Unit
) {
    val links = remember {
        listOf(
            WebsiteDrawerLink("Home", "Featured & latest streams", NexiSvgIcons.Home, NexiRed) {
                onDismiss()
            },
            WebsiteDrawerLink("Movies", "Full HD & 4K movies", NexiSvgIcons.Movies, Color(0xFF8B5CF6)) {
                onNavigateBrowseTab(1)
            },
            WebsiteDrawerLink("Series", "Web series & TV shows", NexiSvgIcons.Series, Color(0xFFA855F7)) {
                onNavigateBrowseTab(3)
            },
            WebsiteDrawerLink("Anime", "Subbed & dubbed anime", NexiSvgIcons.Anime, Color(0xFFEC4899)) {
                onNavigateBrowseTab(2)
            },
            WebsiteDrawerLink("Live TV", "Sports, news & radio", NexiSvgIcons.LiveTv, SuccessGreen) {
                onNavigateRoute(Screen.LiveTv.route)
            },
            WebsiteDrawerLink("Games", "1,000+ HTML5 arcade games", NexiSvgIcons.Gamepad, InfoBlue) {
                onNavigateRoute(Screen.Games.route)
            },
            WebsiteDrawerLink("Vibe Check", "AI mood movie matcher", NexiSvgIcons.VibeSparkles, NexiRedLight) {
                onNavigateRoute(Screen.VibeCheck.route)
            },
            WebsiteDrawerLink("Novels", "Read web novels & fiction", NexiSvgIcons.Novels, Color(0xFFF472B6)) {
                onNavigateRoute(Screen.Novels.route)
            },
            WebsiteDrawerLink("VIP & Coins", "Rewards, spin wheel & VIP", NexiSvgIcons.Crown, CoinGold) {
                onNavigateRoute(Screen.Coins.route)
            },
            WebsiteDrawerLink("Nexi AI Bot", "Ask AI for recommendations", NexiSvgIcons.Bot, Color(0xFF818CF8)) {
                onNavigateRoute(Screen.Chatbot.route)
            },
            WebsiteDrawerLink("Leaderboard", "Top community members", NexiSvgIcons.Trophy, CoinGold) {
                onNavigateRoute(Screen.Leaderboard.route)
            },
            WebsiteDrawerLink("Watchlist", "Saved movies & series", NexiSvgIcons.Bookmark, SuccessGreen) {
                onNavigateRoute(Screen.Watchlist.route)
            },
            WebsiteDrawerLink("Downloads", "Offline video library", NexiSvgIcons.Download, InfoBlue) {
                onNavigateRoute("downloads")
            },
            WebsiteDrawerLink("Request Title", "Ask us to upload content", NexiSvgIcons.PlusCircle, NexiRed) {
                onNavigateRoute(Screen.RequestContent.route)
            },
            WebsiteDrawerLink("Settings", "App preferences & account", NexiSvgIcons.Settings, TextGray) {
                onNavigateRoute(Screen.Settings.route)
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
            .padding(bottom = 20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Navigation Menu",
                    color = themeTextPrimary(),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = InterFont
                )
                Text(
                    text = "All Nexiplay sections & tools",
                    color = themeTextSecondary(),
                    fontSize = 11.5.sp,
                    fontFamily = InterFont
                )
            }

            // Telegram CTA Button matching `Header.tsx` line 219
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF229ED9).copy(alpha = 0.16f))
                    .border(1.dp, Color(0xFF229ED9).copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                    .bounceClick(onClick = onOpenTelegram)
                    .padding(horizontal = 12.dp, vertical = 7.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.Telegram,
                        contentDescription = "Telegram",
                        tint = Color(0xFF229ED9),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = telegramText,
                        color = Color(0xFF229ED9),
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFont
                    )
                }
            }
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.heightIn(max = 460.dp)
        ) {
            items(links, key = { it.label }) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(themeCard())
                        .border(1.dp, themeBorder(), RoundedCornerShape(14.dp))
                        .bounceClick(onClick = item.onClick)
                        .padding(11.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(item.accent.copy(alpha = if (isDark) 0.16f else 0.12f))
                            .border(0.8.dp, item.accent.copy(alpha = if (isDark) 0.32f else 0.25f), RoundedCornerShape(10.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.label,
                            tint = item.accent,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.label,
                            color = themeTextPrimary(),
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = item.subtitle,
                            color = themeTextSecondary(),
                            fontSize = 10.sp,
                            fontFamily = InterFont,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeLoadingSkeleton() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 24.dp)
    ) {
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .height(380.dp),
            cornerRadius = 0
        )
        Spacer(modifier = Modifier.height(14.dp))
        ShimmerBox(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .height(46.dp),
            cornerRadius = 14
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            repeat(3) {
                ShimmerBox(
                    modifier = Modifier
                        .width(140.dp)
                        .height(210.dp),
                    cornerRadius = 14
                )
            }
        }
    }
}

@Composable
private fun HomeErrorView(
    message: String,
    onRetry: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = message,
                color = TextWhite,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = InterFont
            )
            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                shape = RoundedCornerShape(10.dp)
            ) {
                Text(
                    text = "Retry",
                    color = TextWhite,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont
                )
            }
        }
    }
}
