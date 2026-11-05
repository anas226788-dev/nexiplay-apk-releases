package com.nexiplay.app.ui.screens.games

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.ActivityInfo
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
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
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.model.ArcadeGame
import com.nexiplay.app.data.repository.WebFeaturesRepository
import com.nexiplay.app.ui.components.NexiSvgIcons
import com.nexiplay.app.ui.theme.*
import com.nexiplay.app.util.findActivity

private data class GameCategoryTab(
    val id: String,
    val label: String
)

private val GAME_CATEGORIES = listOf(
    GameCategoryTab("All", "All Genres"),
    GameCategoryTab("Action", "Action & Adventure"),
    GameCategoryTab("Arcade", "Classic Arcade"),
    GameCategoryTab("Puzzles", "Puzzles & Brain"),
    GameCategoryTab("Sports", "Sports & Racing"),
    GameCategoryTab("Strategy", "Strategy"),
    GameCategoryTab("Classics", "Retro Classics"),
    GameCategoryTab("Board", "Board Games"),
    GameCategoryTab("Junior", "Kids & Junior")
)

private val POSTER_FALLBACK_GRADIENTS = listOf(
    listOf(Color(0xFF6347D1), Color(0xFF392878)),
    listOf(Color(0xFFA86428), Color(0xFF593516)),
    listOf(Color(0xFF1F6357), Color(0xFF123831)),
    listOf(Color(0xFF285596), Color(0xFF162F54)),
    listOf(Color(0xFF963849), Color(0xFF541E28))
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GamesScreen(navController: NavController) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val prefs = remember { context.getSharedPreferences("nexiplay_arcade_prefs", Context.MODE_PRIVATE) }

    var allGames by remember { mutableStateOf<List<ArcadeGame>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var activeCategory by remember { mutableStateOf("All") }
    var activeTab by remember { mutableStateOf("all") } // "all" | "favorites" | "recent"
    var sortBy by remember { mutableStateOf("popular") } // "popular" | "featured" | "name"
    var searchQuery by remember { mutableStateOf("") }
    var visibleCount by remember { mutableIntStateOf(60) }

    var favoriteIds by remember {
        mutableStateOf(prefs.getStringSet("favorite_games", emptySet())?.toSet() ?: emptySet())
    }
    var recentIds by remember {
        mutableStateOf(
            prefs.getString("recent_games", "")
                ?.split(",")
                ?.filter { it.isNotBlank() }
                ?: emptyList()
        )
    }

    var activeGame by remember { mutableStateOf<ArcadeGame?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        allGames = WebFeaturesRepository.fetchArcadeGames()
        isLoading = false
    }

    fun toggleFavorite(gameId: String) {
        val next = if (gameId in favoriteIds) favoriteIds - gameId else favoriteIds + gameId
        favoriteIds = next
        prefs.edit().putStringSet("favorite_games", next).apply()
    }

    fun launchGame(game: ArcadeGame) {
        val updatedRecent = (listOf(game.id) + recentIds.filter { it != game.id }).take(24)
        recentIds = updatedRecent
        prefs.edit().putString("recent_games", updatedRecent.joinToString(",")).apply()
        activeGame = game
    }

    val filteredGames = remember(allGames, activeTab, activeCategory, searchQuery, sortBy, favoriteIds, recentIds) {
        val base = when (activeTab) {
            "favorites" -> allGames.filter { it.id in favoriteIds }
            "recent" -> recentIds.mapNotNull { id -> allGames.find { it.id == id } }
            else -> allGames
        }

        val catFiltered = if (activeCategory == "All") {
            base
        } else {
            base.filter { g ->
                g.category.equals(activeCategory, ignoreCase = true) ||
                    g.categories.any { it.equals(activeCategory, ignoreCase = true) }
            }
        }

        val searchFiltered = if (searchQuery.isBlank()) {
            catFiltered
        } else {
            val q = searchQuery.trim().lowercase()
            catFiltered.filter { g ->
                g.title.lowercase().contains(q) ||
                    g.category.lowercase().contains(q) ||
                    g.description.lowercase().contains(q)
            }
        }

        when (sortBy) {
            "featured" -> searchFiltered.sortedWith(
                compareByDescending<ArcadeGame> { it.featured == true }
                    .thenByDescending { it.rkScore ?: 0.0 }
            )
            "name" -> searchFiltered.sortedBy { it.title.lowercase() }
            else -> searchFiltered.sortedByDescending { it.rkScore ?: 0.0 }
        }
    }

    // If a game is currently open in the Fullscreen Game Theater, render the Theater
    activeGame?.let { playingGame ->
        GameTheaterOverlay(
            game = playingGame,
            isFavorite = playingGame.id in favoriteIds,
            onToggleFavorite = { toggleFavorite(playingGame.id) },
            onClose = {
                activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                activeGame = null
            }
        )
        return
    }

    val pageBg = DarkBg
    val surfaceBg = Color(0xFF14111D)
    val borderSubtle = Color(0xFF282238)
    val primaryViolet = Color(0xFF9B7BFF)
    val primaryVioletAccent = NexiRed

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
                // Left: Back Arrow + "Arcade" + "1000+ FREE INSTANT GAMES"
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
                            text = "Arcade",
                            fontWeight = FontWeight.Black,
                            fontSize = 22.sp,
                            color = TextWhite,
                            fontFamily = InterFont,
                            letterSpacing = (-0.5).sp
                        )
                        Text(
                            text = "${allGames.size.coerceAtLeast(1000)}+ FREE INSTANT GAMES",
                            fontSize = 10.sp,
                            color = Color(0xFF9E98AC),
                            fontWeight = FontWeight.Bold,
                            fontFamily = InterFont,
                            letterSpacing = 1.1.sp
                        )
                    }
                }

                // Right: Sort Button ("Popular" / "Featured" / "A-Z")
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(surfaceBg)
                        .border(1.dp, borderSubtle, RoundedCornerShape(14.dp))
                        .clickable {
                            sortBy = when (sortBy) {
                                "popular" -> "featured"
                                "featured" -> "name"
                                else -> "popular"
                            }
                        }
                        .padding(horizontal = 14.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.Sort,
                        contentDescription = "Sort",
                        tint = primaryViolet,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = when (sortBy) {
                            "featured" -> "Featured"
                            "name" -> "A-Z"
                            else -> "Popular"
                        },
                        color = TextWhite,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = InterFont
                    )
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
            // ── 1. Sleek Dark Search Bar ("Search 1,000+ games") ──
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .height(48.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(surfaceBg)
                    .border(1.dp, borderSubtle, RoundedCornerShape(15.dp))
                    .padding(horizontal = 15.dp),
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
                        tint = Color(0xFF8E889E),
                        modifier = Modifier.size(19.dp)
                    )
                    Box(modifier = Modifier.weight(1f)) {
                        if (searchQuery.isEmpty()) {
                            Text(
                                text = "Search 1,000+ games",
                                fontSize = 14.5.sp,
                                color = Color(0xFF8E889E),
                                fontWeight = FontWeight.Medium,
                                fontFamily = InterFont
                            )
                        }
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                visibleCount = 60
                            },
                            singleLine = true,
                            textStyle = TextStyle(
                                color = TextWhite,
                                fontSize = 14.5.sp,
                                fontWeight = FontWeight.Medium,
                                fontFamily = InterFont
                            ),
                            cursorBrush = SolidColor(primaryViolet),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    if (searchQuery.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = Color(0xFF8E889E),
                            modifier = Modifier
                                .size(17.dp)
                                .clickable { searchQuery = "" }
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 2. 3-Column Underline Tabs ("All 1000" | "Saved 0" | "Recent 0") ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    val tabs = listOf(
                        Triple("all", "All", allGames.size.coerceAtLeast(1000)),
                        Triple("favorites", "Saved", favoriteIds.size),
                        Triple("recent", "Recent", recentIds.size)
                    )
                    tabs.forEach { (id, label, count) ->
                        val selected = activeTab == id
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    activeTab = id
                                    visibleCount = 60
                                },
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier.padding(bottom = 10.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) TextWhite else Color(0xFF8E889E),
                                    fontSize = 14.5.sp,
                                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                                    fontFamily = InterFont
                                )
                                Text(
                                    text = "$count",
                                    color = if (selected) primaryViolet else Color(0xFF8E889E),
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.ExtraBold,
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
                HorizontalDivider(color = Color(0xFF221D30), thickness = 1.dp)
            }

            Spacer(Modifier.height(14.dp))

            // ── 3. Rounded Capsule Genre Filter Pills ("All Genres", "Action & Adventure", ...) ──
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(9.dp)
            ) {
                items(GAME_CATEGORIES, key = { it.id }) { cat ->
                    val selected = activeCategory == cat.id
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(if (selected) primaryViolet else surfaceBg)
                            .border(
                                1.dp,
                                if (selected) primaryViolet else borderSubtle,
                                RoundedCornerShape(50)
                            )
                            .clickable {
                                activeCategory = cat.id
                                visibleCount = 60
                            }
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = cat.label,
                            color = if (selected) Color(0xFF0D0B12) else TextWhite,
                            fontSize = 13.5.sp,
                            fontWeight = if (selected) FontWeight.Black else FontWeight.Bold,
                            fontFamily = InterFont
                        )
                    }
                }
            }

            Spacer(Modifier.height(14.dp))

            // ── 4. 3-Column Games Grid with Original Posters ──
            if (isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = primaryVioletAccent)
                        Spacer(Modifier.height(10.dp))
                        Text("Loading Arcade Catalog...", color = TextGray, fontSize = 13.sp, fontFamily = InterFont)
                    }
                }
            } else if (filteredGames.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = when (activeTab) {
                                "favorites" -> "No saved games yet"
                                "recent" -> "No recently played games yet"
                                else -> "No games match your search"
                            },
                            color = TextWhite,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            fontFamily = InterFont
                        )
                    }
                }
            } else {
                val displayedGames = remember(filteredGames, visibleCount) {
                    filteredGames.take(visibleCount)
                }

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(11.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    itemsIndexed(displayedGames, key = { _, g -> g.id }) { index, game ->
                        val isFav = game.id in favoriteIds
                        val ratingText = String.format("%.1f", (3.8 + (game.rkScore ?: 0.75) * 1.2).coerceAtMost(5.0))
                        val posterUrl = remember(game.thumbnailUrl, game.thumbnailUrl100) {
                            game.thumbnailUrl.ifBlank { game.thumbnailUrl100.orEmpty() }.trim()
                        }
                        val fallbackGradient = POSTER_FALLBACK_GRADIENTS[index % POSTER_FALLBACK_GRADIENTS.size]
                        val posterShape = RoundedCornerShape(18.dp)

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { launchGame(game) }
                        ) {
                            // Rounded Poster Box with Original Game Poster Image
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .aspectRatio(0.84f)
                                    .clip(posterShape)
                                    .background(Brush.verticalGradient(fallbackGradient))
                                    .border(1.dp, Color.White.copy(alpha = 0.12f), posterShape)
                            ) {
                                if (posterUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = posterUrl,
                                        contentDescription = game.title,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }

                                // Subtle top & bottom gradient scrims so heart & rating pill stay legible on any poster
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color.Black.copy(alpha = 0.25f),
                                                    Color.Transparent,
                                                    Color.Black.copy(alpha = 0.45f)
                                                )
                                            )
                                        )
                                )

                                // Top-Right Favorite Heart Icon
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(7.dp)
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .clickable { toggleFavorite(game.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = if (isFav) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = "Favorite",
                                        tint = if (isFav) Color(0xFFFF5C77) else Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }

                                // Bottom-Left "★ 4.9" Dark Pill Badge
                                Row(
                                    modifier = Modifier
                                        .align(Alignment.BottomStart)
                                        .padding(8.dp)
                                        .clip(RoundedCornerShape(7.dp))
                                        .background(Color(0xD9120E18))
                                        .padding(horizontal = 6.5.dp, vertical = 3.5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "\u2605 $ratingText",
                                        color = Color(0xFFFFB547),
                                        fontSize = 10.sp,
                                        lineHeight = 10.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontFamily = InterFont
                                    )
                                }
                            }

                            Spacer(Modifier.height(7.dp))

                            // Title & Uppercase Genre Below Poster
                            Text(
                                text = game.title,
                                color = TextWhite,
                                fontSize = 12.5.sp,
                                lineHeight = 15.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = InterFont,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = game.category.ifBlank { "ARCADE" }.uppercase(),
                                color = primaryViolet,
                                fontSize = 9.5.sp,
                                lineHeight = 11.sp,
                                fontWeight = FontWeight.ExtraBold,
                                fontFamily = InterFont,
                                letterSpacing = 0.6.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    if (visibleCount < filteredGames.size) {
                        item(span = { GridItemSpan(3) }) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Button(
                                    onClick = { visibleCount += 60 },
                                    colors = ButtonDefaults.buttonColors(containerColor = primaryVioletAccent),
                                    shape = RoundedCornerShape(14.dp)
                                ) {
                                    Text(
                                        "Load More Games (${filteredGames.size - visibleCount} remaining)",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = InterFont
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun GameTheaterOverlay(
    game: ArcadeGame,
    isFavorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var isLandscape by remember { mutableStateOf(game.orientation == "landscape") }

    LaunchedEffect(isLandscape) {
        activity?.requestedOrientation = if (isLandscape) {
            ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        } else {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            webViewRef?.apply {
                stopLoading()
                loadUrl("about:blank")
                destroy()
            }
            webViewRef = null
        }
    }

    BackHandler {
        onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .statusBarsPadding()
    ) {
        // Compact Top Theater Control Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF0A0A12))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "Exit Game", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = game.title,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.ExtraBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${game.category} • Instant HTML5",
                        color = Color(0xFFA78BFA),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                // Reload Game
                IconButton(
                    onClick = {
                        isLoading = true
                        webViewRef?.reload()
                    },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Reload", tint = Color.White, modifier = Modifier.size(18.dp))
                }

                // Favorite Toggle
                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isFavorite) Color(0xFFF43F5E).copy(alpha = 0.25f) else Color.White.copy(alpha = 0.08f))
                ) {
                    Icon(
                        imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        contentDescription = "Favorite",
                        tint = if (isFavorite) Color(0xFFF43F5E) else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Rotate Orientation Toggle
                IconButton(
                    onClick = { isLandscape = !isLandscape },
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF8B5CF6).copy(alpha = 0.25f))
                ) {
                    Icon(Icons.Default.ScreenRotation, contentDescription = "Rotate", tint = Color(0xFFC4B5FD), modifier = Modifier.size(18.dp))
                }
            }
        }

        // Game Canvas
        Box(modifier = Modifier.fillMaxSize()) {
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
                            databaseEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = true
                            allowContentAccess = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                        }
                        webChromeClient = WebChromeClient()
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                isLoading = false
                            }
                        }
                        loadUrl(game.url)
                        webViewRef = this
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.85f)),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                        Spacer(Modifier.height(12.dp))
                        Text(
                            "Launching ${game.title}...",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}
