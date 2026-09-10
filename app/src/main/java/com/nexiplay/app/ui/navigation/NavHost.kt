package com.nexiplay.app.ui.navigation

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.nexiplay.app.ui.theme.*
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.components.GlobalNoticeManager
import com.nexiplay.app.ui.components.UpdateDialog
import com.nexiplay.app.ui.viewmodels.GlobalNoticeViewModel
import com.nexiplay.app.data.util.AppUpdateManager
import com.nexiplay.app.data.util.AppUpdateInfo
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import com.nexiplay.app.ui.screens.home.HomeScreen
import com.nexiplay.app.ui.screens.browse.BrowseScreen
import com.nexiplay.app.ui.screens.novels.NovelsScreen
import com.nexiplay.app.ui.screens.novels.NovelDetailScreen
import com.nexiplay.app.ui.screens.novels.NovelReaderScreen
import com.nexiplay.app.ui.screens.coins.CoinCenterScreen
import com.nexiplay.app.ui.screens.profile.ProfileScreen
import com.nexiplay.app.ui.screens.auth.LoginScreen
import com.nexiplay.app.ui.screens.auth.RegisterScreen
import com.nexiplay.app.ui.screens.search.SearchScreen
import com.nexiplay.app.ui.screens.detail.ContentDetailScreen
import com.nexiplay.app.ui.screens.settings.SettingsScreen
import com.nexiplay.app.ui.screens.notifications.NotificationsScreen
import com.nexiplay.app.ui.screens.watchlist.WatchlistScreen
import com.nexiplay.app.ui.screens.request.RequestContentScreen
import com.nexiplay.app.ui.screens.contact.ContactScreen
import com.nexiplay.app.ui.screens.splash.SplashScreen
import com.nexiplay.app.ui.screens.detail.PlayerScreen
import com.nexiplay.app.ui.screens.detail.WebViewScreen
import com.nexiplay.app.ui.screens.profile.DownloadsScreen
import com.nexiplay.app.ui.screens.profile.HistoryScreen
import com.nexiplay.app.ui.screens.profile.CommentsScreen
data class BottomNavItem(
    val screen: Screen,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
)

val bottomNavItems = listOf(
    BottomNavItem(Screen.Home, "Home", Icons.Filled.Home, Icons.Outlined.Home),
    BottomNavItem(Screen.Browse, "Browse", Icons.Filled.Explore, Icons.Outlined.Explore),
    BottomNavItem(Screen.Novels, "Novels", Icons.Filled.AutoStories, Icons.Outlined.AutoStories),
    BottomNavItem(Screen.Coins, "Coins", Icons.Filled.Diamond, Icons.Outlined.Diamond),
    BottomNavItem(Screen.Profile, "Me", Icons.Filled.Person, Icons.Outlined.Person),
)

// Routes that show bottom nav
private val bottomNavRoutes = setOf(
    Screen.Home.route, Screen.Browse.route, Screen.Novels.route,
    Screen.Coins.route, Screen.Profile.route,
)

@Composable
fun NexiPlayNavHost() {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val showBottomBar = currentRoute in bottomNavRoutes
    val context = androidx.compose.ui.platform.LocalContext.current

    val noticeVm: GlobalNoticeViewModel = viewModel()

    // ── In-App Update System ──
    val appUpdateInfo by AppUpdateManager.updateInfo.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }

    // Check for update on first launch
    LaunchedEffect(Unit) {
        AppUpdateManager.checkForUpdate()
    }

    // Re-check update when app comes back to foreground (e.g., after granting install permission)
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                // If update dialog was showing and user left to grant permission, re-trigger
                if (AppUpdateManager.isUpdateAvailable && AppUpdateManager.shouldShowUpdate(context)) {
                    showUpdateDialog = true
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Show dialog only if update is available AND user hasn't dismissed this version
    LaunchedEffect(appUpdateInfo) {
        if (AppUpdateManager.shouldShowUpdate(context)) {
            showUpdateDialog = true
        }
    }

    // Update Dialog
    if (showUpdateDialog && appUpdateInfo != null) {
        UpdateDialog(
            updateInfo = appUpdateInfo!!,
            onDismiss = {
                if (!AppUpdateManager.isForceUpdate) {
                    showUpdateDialog = false
                    AppUpdateManager.dismissUpdatePermanently(context)
                    AppUpdateManager.resetState()
                }
            }
        )
    }

    var showUpsellPopup by remember { mutableStateOf(false) }
    var upsellTitle by remember { mutableStateOf("") }
    var upsellMessage by remember { mutableStateOf("") }
    var upsellBtnText by remember { mutableStateOf("") }
    var upsellAction by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        com.nexiplay.app.data.util.AdManager.onShowUpsellPopup = { title, message, btnText, action ->
            upsellTitle = title
            upsellMessage = message
            upsellBtnText = btnText
            upsellAction = action
            showUpsellPopup = true
        }
    }

    if (showUpsellPopup) {
        com.nexiplay.app.ui.components.CoinUpsellDialog(
            title = upsellTitle,
            message = upsellMessage,
            buttonText = upsellBtnText,
            action = upsellAction,
            onDismiss = { showUpsellPopup = false },
            onConfirmAction = { act ->
                showUpsellPopup = false
                navController.navigate(Screen.Coins.route)
            }
        )
    }

    GlobalNoticeManager(
        currentRoute = currentRoute,
        viewModel = noticeVm
    ) {
    Scaffold(
        containerColor = themeBg(),
        bottomBar = {
            if (showBottomBar) {
                // iOS Style Bottom Navigation
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF14141E).copy(alpha = 0.85f)) // Deep glassy translucent
                        .border(
                            width = 0.5.dp,
                            color = Color.White.copy(alpha = 0.08f) // Subtle top border
                        )
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            bottomNavItems.forEach { item ->
                                val selected = navBackStackEntry?.destination?.hierarchy?.any {
                                    it.route == item.screen.route
                                } == true

                                val itemColor = if (selected) NexiRed else Color(0xFF8E8E93) // Classic iOS unselected grey

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) {
                                            navController.navigate(item.screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = item.label,
                                            tint = itemColor,
                                            modifier = Modifier.size(24.dp)
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = item.label,
                                            fontSize = 10.sp,
                                            fontFamily = InterFont,
                                            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                            color = itemColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
            floatingActionButton = {
                if (showBottomBar) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.padding(bottom = 4.dp, end = 4.dp)
                    ) {
                        // Tooltip badge
                        Box(
                            modifier = Modifier
                                .background(Color.White, RoundedCornerShape(16.dp))
                                .border(0.5.dp, Color.Black.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Need help? Chat with me! 💬",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E1E2E),
                                fontFamily = InterFont
                            )
                        }

                        // FAB Button
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .bounceClick { navController.navigate(Screen.Chatbot.route) }
                                .background(
                                    brush = Brush.linearGradient(
                                        colors = listOf(NexiRed, NexiRedDark)
                                    ),
                                    shape = CircleShape
                                )
                                .border(1.5.dp, Color.White.copy(alpha = 0.3f), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.ChatBubble,
                                contentDescription = "AI Chatbot",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            // Online Green Pulse Indicator
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .align(Alignment.TopEnd)
                                    .background(SuccessGreen, CircleShape)
                                    .border(1.5.dp, NexiRedDark, CircleShape)
                            )
                        }
                    }
                }
            }
        ) { innerPadding ->
            NavHost(
                navController = navController,
                startDestination = Screen.Splash.route,
                modifier = Modifier.padding(innerPadding),
                enterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> (fullWidth * 0.15).toInt() },
                        animationSpec = tween(280),
                    ) + fadeIn(tween(280))
                },
                exitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> -(fullWidth * 0.08).toInt() },
                        animationSpec = tween(280),
                    ) + fadeOut(tween(200))
                },
                popEnterTransition = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> -(fullWidth * 0.15).toInt() },
                        animationSpec = tween(280),
                    ) + fadeIn(tween(280))
                },
                popExitTransition = {
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> (fullWidth * 0.08).toInt() },
                        animationSpec = tween(280),
                    ) + fadeOut(tween(200))
                },
            ) {
                // ── Splash Screen ──
                composable(Screen.Splash.route) { SplashScreen(navController) }

                // ── Bottom Nav Tabs ──
                composable(Screen.Home.route) { HomeScreen(navController) }
                composable(Screen.Browse.route) { BrowseScreen(navController) }
                composable(Screen.Novels.route) { NovelsScreen(navController) }
                composable(Screen.Coins.route) { CoinCenterScreen(navController) }
                composable(Screen.Profile.route) { ProfileScreen(navController) }

                // ── Auth ──
                composable(Screen.Login.route) { LoginScreen(navController) }
                composable(Screen.Register.route) { RegisterScreen(navController) }

                // ── Search ──
                composable(Screen.Search.route) { SearchScreen(navController) }

                // ── Web View ──
                composable(
                    route = "webview/{url}",
                    arguments = listOf(navArgument("url") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedUrl = backStackEntry.arguments?.getString("url") ?: ""
                    WebViewScreen(navController, encodedUrl)
                }

                // ── Content Detail ──
                composable(
                    route = Screen.ContentDetail.route,
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("slug") { type = NavType.StringType },
                    ),
                ) { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type") ?: ""
                    val slug = backStackEntry.arguments?.getString("slug") ?: ""
                    ContentDetailScreen(navController, type, slug)
                }

                // ── Watch Screen ──
                composable(
                    route = "watch/{type}/{slug}",
                    arguments = listOf(
                        navArgument("type") { type = NavType.StringType },
                        navArgument("slug") { type = NavType.StringType }
                    ),
                ) { backStackEntry ->
                    val type = backStackEntry.arguments?.getString("type") ?: ""
                    val slug = backStackEntry.arguments?.getString("slug") ?: ""
                    com.nexiplay.app.ui.screens.detail.WatchScreen(navController, type, slug)
                }

                // ── Novel Detail ──
                composable(
                    route = Screen.NovelDetail.route,
                    arguments = listOf(navArgument("slug") { type = NavType.StringType }),
                ) { backStackEntry ->
                    val rawSlug = backStackEntry.arguments?.getString("slug") ?: ""
                    val slug = try {
                        java.net.URLDecoder.decode(rawSlug, "UTF-8")
                    } catch (e: Exception) {
                        rawSlug
                    }
                    NovelDetailScreen(navController, slug)
                }

                // ── Novel Reader ──
                composable(
                    route = "novel_reader/{novelId}/{chapterNumber}",
                    arguments = listOf(
                        navArgument("novelId") { type = NavType.StringType },
                        navArgument("chapterNumber") { type = NavType.IntType },
                    ),
                ) { backStackEntry ->
                    val novelId = backStackEntry.arguments?.getString("novelId") ?: ""
                    val chapterNum = backStackEntry.arguments?.getInt("chapterNumber") ?: 1
                    NovelReaderScreen(navController, novelId, chapterNum)
                }

                // ── Settings ──
                composable(Screen.Settings.route) { SettingsScreen(navController) }

                // ── Notifications ──
                composable(Screen.Notifications.route) { NotificationsScreen(navController) }

                // ── Watchlist ──
                composable(Screen.Watchlist.route) { WatchlistScreen(navController) }

                // ── Request Content ──
                composable(Screen.RequestContent.route) { RequestContentScreen(navController) }

                // ── Contact Us ──
                composable(Screen.Contact.route) { ContactScreen(navController) }

                // ── Player Screen ──
                composable(
                    route = "player/{url}",
                    arguments = listOf(navArgument("url") { type = NavType.StringType })
                ) { backStackEntry ->
                    val url = backStackEntry.arguments?.getString("url") ?: ""
                    PlayerScreen(navController, url)
                }
                
                // ── History & Comments ──
                composable("history") { HistoryScreen(navController) }
                composable("comments") { CommentsScreen(navController) }

                // ── Downloads Screen ──
                composable("downloads") { DownloadsScreen(navController) }

                // ── Leaderboard ──
                composable(Screen.Leaderboard.route) { com.nexiplay.app.ui.screens.leaderboard.LeaderboardScreen(navController) }

                // ── AI Chatbot ──
                composable(Screen.Chatbot.route) { com.nexiplay.app.ui.screens.chatbot.ChatbotScreen(navController) }
            }
        }
    }
}
