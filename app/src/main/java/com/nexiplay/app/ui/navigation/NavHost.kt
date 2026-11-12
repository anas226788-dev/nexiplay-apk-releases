package com.nexiplay.app.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexiplay.app.ui.components.NexiSvgIcons
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.screens.auth.LoginScreen
import com.nexiplay.app.ui.screens.auth.RegisterScreen
import com.nexiplay.app.ui.screens.browse.BrowseScreen
import com.nexiplay.app.ui.screens.browse.BrowseViewModel
import com.nexiplay.app.ui.screens.chatbot.ChatbotScreen
import com.nexiplay.app.ui.screens.coins.CoinCenterScreen
import com.nexiplay.app.ui.screens.contact.ContactScreen
import com.nexiplay.app.ui.screens.detail.ContentDetailScreen
import com.nexiplay.app.ui.screens.detail.PlayerScreen
import com.nexiplay.app.ui.screens.detail.WatchScreen
import com.nexiplay.app.ui.screens.detail.WebViewScreen
import com.nexiplay.app.ui.screens.games.GamesScreen
import com.nexiplay.app.ui.screens.home.HomeScreen
import com.nexiplay.app.ui.screens.home.HomeViewModel
import com.nexiplay.app.ui.screens.leaderboard.LeaderboardScreen
import com.nexiplay.app.ui.screens.livetv.LiveTvScreen
import com.nexiplay.app.ui.screens.notifications.NotificationsScreen
import com.nexiplay.app.ui.screens.novels.NovelDetailScreen
import com.nexiplay.app.ui.screens.novels.NovelReaderScreen
import com.nexiplay.app.ui.screens.novels.NovelsScreen
import com.nexiplay.app.ui.screens.profile.CommentsScreen
import com.nexiplay.app.ui.screens.profile.DownloadsScreen
import com.nexiplay.app.ui.screens.profile.HistoryScreen
import com.nexiplay.app.ui.screens.profile.ProfileScreen
import com.nexiplay.app.ui.screens.request.RequestContentScreen
import com.nexiplay.app.ui.screens.search.SearchScreen
import com.nexiplay.app.ui.screens.settings.SettingsScreen
import com.nexiplay.app.ui.screens.splash.SplashScreen
import com.nexiplay.app.ui.screens.vibecheck.VibeCheckScreen
import com.nexiplay.app.ui.screens.watchlist.WatchlistScreen
import com.nexiplay.app.ui.theme.*
import com.nexiplay.app.util.findActivity
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay

/**
 * Matches `src/components/MobileBottomNav.tsx` from nexiplay-web-main 1:1
 */
private data class WebsiteBottomNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val route: String,
    val browseTab: Int? = null
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NexiPlayNavHost(
    navController: NavHostController = rememberNavController()
) {
    val homeViewModel: HomeViewModel = viewModel()
    val globalNoticeViewModel: com.nexiplay.app.ui.viewmodels.GlobalNoticeViewModel = viewModel()
    val pendingNotificationRoute by NotificationRouter.pendingRoute.collectAsState()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val activeBrowseTab by BrowseViewModel.activeTabFlow.collectAsState()

    val appUpdateInfo by com.nexiplay.app.data.util.AppUpdateManager.updateInfo.collectAsState()
    var showUpsellPopup by remember { mutableStateOf(false) }
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        com.nexiplay.app.data.util.AdManager.onShowUpsellPopup = { _, _, _, _ ->
            showUpsellPopup = true
        }
    }

    // Run non-essential startup tasks ONLY after leaving SplashScreen so launch frames stay 100% lag-free
    var postSplashInitialized by remember { mutableStateOf(false) }
    LaunchedEffect(currentRoute) {
        if (!postSplashInitialized && currentRoute != null && currentRoute != Screen.Splash.route) {
            postSplashInitialized = true
            globalNoticeViewModel.ensureLoaded()
            com.nexiplay.app.data.util.AppUpdateManager.checkForUpdate(context)
            if (com.nexiplay.app.data.util.AppUpdateManager.isUpdateAvailable &&
                !com.nexiplay.app.data.util.AppUpdateManager.isUpdateReadyToInstall.value
            ) {
                com.nexiplay.app.data.util.AppUpdatePrefetchWorker.enqueue(context)
            }
            com.nexiplay.app.data.util.AdManager.markStartupReadyForAds(
                context.findActivity() ?: return@LaunchedEffect
            )
        }
    }

    // Exact 7 items from `src/components/MobileBottomNav.tsx`
    val websiteBottomNavItems = remember {
        listOf(
            WebsiteBottomNavItem(
                id = "home",
                label = "Home",
                icon = NexiSvgIcons.Home,
                route = Screen.Home.route
            ),
            WebsiteBottomNavItem(
                id = "movies",
                label = "Movies",
                icon = NexiSvgIcons.Movies,
                route = Screen.Browse.route,
                browseTab = 1
            ),
            WebsiteBottomNavItem(
                id = "anime",
                label = "Anime",
                icon = NexiSvgIcons.Anime,
                route = Screen.Browse.route,
                browseTab = 2
            ),
            WebsiteBottomNavItem(
                id = "coins",
                label = "Coins",
                icon = NexiSvgIcons.Coin,
                route = Screen.Coins.route
            ),
            WebsiteBottomNavItem(
                id = "vibe",
                label = "Vibe",
                icon = NexiSvgIcons.VibeSparkles,
                route = Screen.VibeCheck.route
            ),
            WebsiteBottomNavItem(
                id = "novels",
                label = "Novels",
                icon = NexiSvgIcons.Novels,
                route = Screen.Novels.route
            ),
            WebsiteBottomNavItem(
                id = "me",
                label = "Me",
                icon = NexiSvgIcons.User,
                route = Screen.Profile.route
            )
        )
    }

    val routesWithBottomBar = remember {
        setOf(
            Screen.Home.route,
            Screen.Browse.route,
            Screen.VibeCheck.route,
            Screen.Novels.route,
            Screen.LiveTv.route,
            Screen.Coins.route,
            Screen.Games.route,
            Screen.Profile.route
        )
    }

    val showBottomBar = currentRoute in routesWithBottomBar

    LaunchedEffect(pendingNotificationRoute, currentRoute) {
        val route = pendingNotificationRoute
        if (!route.isNullOrBlank() && currentRoute != null && currentRoute != Screen.Splash.route) {
            if (route != currentRoute) {
                navController.navigate(route) {
                    launchSingleTop = true
                    restoreState = true
                }
            }
            NotificationRouter.consume()
        }
    }

    // Only major / force updates interrupt the user with an install popup.
    // Minor updates download silently in the background via AppUpdatePrefetchWorker.
    if (appUpdateInfo != null && com.nexiplay.app.data.util.AppUpdateManager.shouldAutoPromptUpdate() && currentRoute != Screen.Splash.route) {
        com.nexiplay.app.ui.components.UpdateDialog(
            updateInfo = appUpdateInfo!!,
            onDismiss = {
                com.nexiplay.app.data.util.AppUpdateManager.resetState()
            }
        )
    }

    if (showUpsellPopup) {
        com.nexiplay.app.ui.components.CoinUpsellDialog(
            title = com.nexiplay.app.data.util.AdManager.coinPopupTitle,
            message = com.nexiplay.app.data.util.AdManager.coinPopupMessage,
            buttonText = com.nexiplay.app.data.util.AdManager.coinPopupButtonText,
            action = com.nexiplay.app.data.util.AdManager.coinPopupAction,
            onDismiss = { showUpsellPopup = false },
            onConfirmAction = { action ->
                showUpsellPopup = false
                val cleanAction = action.trim().lowercase()
                if (cleanAction.isBlank() || cleanAction == "coins" || cleanAction == "buy_vip" || cleanAction == "vip" || cleanAction == "elite" || cleanAction == "shop") {
                    navController.navigate(Screen.Coins.route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                } else {
                    NotificationRouter.open(link = action)
                }
            }
        )
    }

    com.nexiplay.app.ui.components.GlobalNoticeManager(
        currentRoute = currentRoute,
        viewModel = globalNoticeViewModel
    ) {
        Scaffold(
            containerColor = themeBg(),
            bottomBar = {
                if (showBottomBar) {
                    // Floating Stadium Bottom Navigation Dock (Matches Mockup 1:1)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .navigationBarsPadding()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        val dockShape = RoundedCornerShape(30.dp)
                        val bottomBarBg = if (isDark) Color(0xFF15121E) else Color.White
                        val bottomBarBorder = if (isDark) Color(0xFF2B253A) else Color(0xFFE2DCED)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .shadow(
                                    elevation = 18.dp,
                                    shape = dockShape,
                                    ambientColor = if (isDark) Color.Black else Color(0x334A2E80),
                                    spotColor = if (isDark) Color.Black else Color(0x244A2E80)
                                )
                                .clip(dockShape)
                                .background(bottomBarBg)
                                .border(1.dp, bottomBarBorder, dockShape)
                                .padding(horizontal = 6.dp, vertical = 6.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                websiteBottomNavItems.forEach { item ->
                                    val isSelected = when {
                                        item.browseTab != null -> {
                                            currentRoute == Screen.Browse.route && activeBrowseTab == item.browseTab
                                        }
                                        else -> currentRoute == item.route
                                    }

                                    WebsiteBottomNavTab(
                                        item = item,
                                        selected = isSelected,
                                        onClick = {
                                            if (item.browseTab != null) {
                                                BrowseViewModel.navigateWithFilter(tab = item.browseTab, categorySlug = null)
                                                if (currentRoute != Screen.Browse.route) {
                                                    navController.navigate(Screen.Browse.route) {
                                                        popUpTo(navController.graph.findStartDestination().id) {
                                                            saveState = true
                                                        }
                                                        launchSingleTop = true
                                                        restoreState = true
                                                    }
                                                }
                                            } else if (currentRoute != item.route) {
                                                navController.navigate(item.route) {
                                                    popUpTo(navController.graph.findStartDestination().id) {
                                                        saveState = true
                                                    }
                                                    launchSingleTop = true
                                                    restoreState = true
                                                }
                                            } else if (item.route == Screen.Home.route) {
                                                homeViewModel.loadHome(isRefresh = true)
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            var showChatTooltip by remember { mutableStateOf(true) }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                NavHost(
                    navController = navController,
                    startDestination = Screen.Splash.route,
                    modifier = Modifier.fillMaxSize()
                ) {
                    composable(Screen.Splash.route) {
                        SplashScreen(navController = navController)
                    }
                    composable(Screen.Home.route) {
                        HomeScreen(navController = navController, vm = homeViewModel)
                    }
                    composable(Screen.Browse.route) {
                        BrowseScreen(navController = navController)
                    }
                    composable(Screen.Novels.route) {
                        NovelsScreen(navController = navController)
                    }
                    composable(Screen.Coins.route) {
                        CoinCenterScreen(navController = navController)
                    }
                    composable(Screen.Profile.route) {
                        ProfileScreen(navController = navController)
                    }
                    composable(Screen.Login.route) {
                        LoginScreen(navController = navController)
                    }
                    composable(Screen.Register.route) {
                        RegisterScreen(navController = navController)
                    }
                    composable(Screen.Search.route) {
                        SearchScreen(navController = navController)
                    }
                    composable(Screen.Notifications.route) {
                        NotificationsScreen(navController = navController)
                    }
                    composable(Screen.Watchlist.route) {
                        WatchlistScreen(navController = navController)
                    }
                    composable(Screen.Settings.route) {
                        SettingsScreen(navController = navController)
                    }
                    composable(Screen.RequestContent.route) {
                        RequestContentScreen(navController = navController)
                    }
                    composable(Screen.Contact.route) {
                        ContactScreen(navController = navController)
                    }
                    composable(Screen.Leaderboard.route) {
                        LeaderboardScreen(navController = navController)
                    }
                    composable(Screen.Chatbot.route) {
                        ChatbotScreen(navController = navController)
                    }
                    composable(Screen.VibeCheck.route) {
                        VibeCheckScreen(navController = navController)
                    }
                    composable(Screen.LiveTv.route) {
                        LiveTvScreen(navController = navController)
                    }
                    composable(Screen.Games.route) {
                        GamesScreen(navController = navController)
                    }
                    composable("comments") {
                        CommentsScreen(navController = navController)
                    }
                    composable("downloads") {
                        DownloadsScreen(navController = navController)
                    }
                    composable("history") {
                        HistoryScreen(navController = navController)
                    }
                    composable(
                        route = Screen.ContentDetail.route,
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType },
                            navArgument("slug") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val type = backStackEntry.arguments?.getString("type").orEmpty()
                        val slug = backStackEntry.arguments?.getString("slug").orEmpty()
                        ContentDetailScreen(navController = navController, type = type, slug = slug)
                    }
                    composable(
                        route = Screen.Watch.route,
                        arguments = listOf(
                            navArgument("type") { type = NavType.StringType },
                            navArgument("slug") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val type = backStackEntry.arguments?.getString("type").orEmpty()
                        val slug = backStackEntry.arguments?.getString("slug").orEmpty()
                        WatchScreen(navController = navController, type = type, slug = slug)
                    }
                    composable(
                        route = Screen.NovelDetail.route,
                        arguments = listOf(
                            navArgument("slug") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val slug = backStackEntry.arguments?.getString("slug").orEmpty()
                        NovelDetailScreen(navController = navController, slug = slug)
                    }
                    composable(
                        route = "novel_detail/{slug}",
                        arguments = listOf(
                            navArgument("slug") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val slug = backStackEntry.arguments?.getString("slug").orEmpty()
                        NovelDetailScreen(navController = navController, slug = slug)
                    }
                    composable(
                        route = Screen.NovelReader.route,
                        arguments = listOf(
                            navArgument("slug") { type = NavType.StringType },
                            navArgument("chapterSlug") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val slug = backStackEntry.arguments?.getString("slug").orEmpty()
                        val chapterNum = backStackEntry.arguments?.getString("chapterSlug")?.toIntOrNull() ?: 1
                        NovelReaderScreen(navController = navController, novelId = slug, chapterNumber = chapterNum)
                    }
                    composable(
                        route = "novel_reader/{novelId}/{chapterNumber}",
                        arguments = listOf(
                            navArgument("novelId") { type = NavType.StringType },
                            navArgument("chapterNumber") { type = NavType.IntType }
                        )
                    ) { backStackEntry ->
                        val novelId = backStackEntry.arguments?.getString("novelId").orEmpty()
                        val chapterNumber = backStackEntry.arguments?.getInt("chapterNumber") ?: 1
                        NovelReaderScreen(navController = navController, novelId = novelId, chapterNumber = chapterNumber)
                    }
                    composable(
                        route = "player/{encodedUrl}",
                        arguments = listOf(
                            navArgument("encodedUrl") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val encodedUrl = backStackEntry.arguments?.getString("encodedUrl").orEmpty()
                        PlayerScreen(navController = navController, encodedUrl = encodedUrl)
                    }
                    composable(
                        route = "webview/{encodedUrl}",
                        arguments = listOf(
                            navArgument("encodedUrl") { type = NavType.StringType }
                        )
                    ) { backStackEntry ->
                        val encodedUrl = backStackEntry.arguments?.getString("encodedUrl").orEmpty()
                        WebViewScreen(navController = navController, encodedUrl = encodedUrl)
                    }
                }

                // Website Floating AI Chatbot Button (`src/components/Chatbot.tsx` lines 380-440)
                if (showBottomBar && currentRoute != Screen.Chatbot.route) {
                    WebsiteFloatingChatbotFab(
                        showTooltip = showChatTooltip,
                        onDismissTooltip = { showChatTooltip = false },
                        onClick = {
                            navController.navigate(Screen.Chatbot.route) {
                                launchSingleTop = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .padding(end = 14.dp, bottom = 10.dp)
                    )
                }
            }
        }
    }
}

/**
 * Matches `src/components/Chatbot.tsx` Floating Action Button + "Chat with me!" Glass Tooltip
 */
@Composable
private fun WebsiteFloatingChatbotFab(
    showTooltip: Boolean,
    onDismissTooltip: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        if (showTooltip) {
            Row(
                modifier = Modifier
                    .shadow(10.dp, RoundedCornerShape(12.dp))
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isDark) {
                            Brush.horizontalGradient(
                                listOf(
                                    Color(0xFF17141F).copy(alpha = 0.96f),
                                    Color(0xFF201A2C).copy(alpha = 0.96f)
                                )
                            )
                        } else {
                            Brush.horizontalGradient(
                                listOf(
                                    Color.White.copy(alpha = 0.98f),
                                    Color(0xFFF7F5FA).copy(alpha = 0.98f)
                                )
                            )
                        }
                    )
                    .border(
                        1.dp,
                        if (isDark) Color.White.copy(alpha = 0.14f) else themeBorder(),
                        RoundedCornerShape(12.dp)
                    )
                    .clickable(onClick = onClick)
                    .padding(start = 9.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Icon(
                    imageVector = NexiSvgIcons.VibeSparkles,
                    contentDescription = null,
                    tint = NexiRedLight,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = "Chat with me!",
                    color = themeTextPrimary(),
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = InterFont
                )
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFEBE7F3))
                        .clickable(onClick = onDismissTooltip),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.Close,
                        contentDescription = "Dismiss",
                        tint = themeTextSecondary(),
                        modifier = Modifier.size(9.dp)
                    )
                }
            }
        }

        // 42.dp Circular 3D Violet Gradient NexiBot Button + Emerald Online Dot
        Box(
            modifier = Modifier
                .size(46.dp)
                .bounceClick(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .shadow(
                        elevation = 12.dp,
                        shape = CircleShape,
                        ambientColor = NexiRed,
                        spotColor = NexiRed
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(
                                Color(0xFFB09AFF),
                                Color(0xFF9273F5),
                                Color(0xFF5C46B2)
                            )
                        )
                    )
                    .border(1.5.dp, Color.White.copy(alpha = 0.32f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NexiSvgIcons.Bot,
                    contentDescription = "NexiBot AI Assistant",
                    tint = Color.White,
                    modifier = Modifier.size(21.dp)
                )
            }

            // Pulsing Emerald Online Indicator Dot (`#10B981`)
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 1.dp, end = 1.dp)
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF10B981))
                    .border(1.5.dp, themeBg(), CircleShape)
            )
        }
    }
}

/**
 * Matches uploaded Bottom Navigation mockup 1:1:
 * - Active squircle: `Color(0xFF271F43)` with `RoundedCornerShape(22.dp)`
 * - Active top indicator pill: `20.dp x 3.5.dp` in `Color(0xFF9B7BFF)` at `Alignment.TopCenter`
 * - Active icon & text tint: `Color(0xFFB39BFF)`
 * - Inactive icon & text tint: `Color(0xFFA09AAE)`
 */
@Composable
private fun RowScope.WebsiteBottomNavTab(
    item: WebsiteBottomNavItem,
    selected: Boolean,
    onClick: () -> Unit
) {
    val pillShape = RoundedCornerShape(22.dp)
    val activeBg = if (isDark) Color(0xFF271F43) else Color(0xFFEDE8FA)
    val activeContentColor = if (isDark) Color(0xFFB39BFF) else NexiRed
    val inactiveContentColor = if (isDark) Color(0xFFA09AAE) else themeTextSecondary()

    Box(
        modifier = Modifier
            .weight(1f)
            .fillMaxHeight()
            .clip(pillShape)
            .then(
                if (selected) {
                    Modifier.background(activeBg, pillShape)
                } else {
                    Modifier
                }
            )
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        // Top glowing violet indicator bar on selected item
        if (selected) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .width(20.dp)
                    .height(3.5.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 2.dp,
                            topEnd = 2.dp,
                            bottomStart = 4.dp,
                            bottomEnd = 4.dp
                        )
                    )
                    .background(Color(0xFF9B7BFF))
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(top = 2.dp)
        ) {
            Icon(
                imageVector = item.icon,
                contentDescription = item.label,
                tint = if (selected) activeContentColor else inactiveContentColor,
                modifier = Modifier.size(21.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.label,
                fontSize = 10.5.sp,
                lineHeight = 11.sp,
                fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Bold,
                color = if (selected) activeContentColor else inactiveContentColor,
                fontFamily = InterFont,
                maxLines = 1,
                overflow = TextOverflow.Clip
            )
        }
    }
}
