package com.nexiplay.app.ui.navigation

sealed class Screen(val route: String) {
    // ── Bottom Nav Tabs ──
    data object Home : Screen("home")
    data object Browse : Screen("browse")
    data object Novels : Screen("novels")
    data object Coins : Screen("coins")
    data object Profile : Screen("profile")

    // ── Content Screens ──
    data object ContentDetail : Screen("content/{type}/{slug}") {
        fun createRoute(type: String, slug: String) = "content/$type/$slug"
    }
    data object Watch : Screen("watch/{type}/{slug}") {
        fun createRoute(type: String, slug: String) = "watch/$type/$slug"
    }
    data object EpisodeList : Screen("episodes/{movieId}") {
        fun createRoute(movieId: String) = "episodes/$movieId"
    }

    // ── Novel Screens ──
    data object NovelDetail : Screen("novel/{slug}") {
        fun createRoute(slug: String) = "novel/$slug"
    }
    data object NovelReader : Screen("novel/{slug}/chapter/{chapterSlug}") {
        fun createRoute(slug: String, chapterSlug: String) = "novel/$slug/chapter/$chapterSlug"
    }

    // ── Auth Screens ──
    data object Login : Screen("login")
    data object Register : Screen("register")

    // ── Other Screens ──
    data object Search : Screen("search")
    data object Notifications : Screen("notifications")
    data object Watchlist : Screen("watchlist")
    data object Settings : Screen("settings")
    data object RequestContent : Screen("request_content")
    data object Contact : Screen("contact")
    data object Leaderboard : Screen("leaderboard")
    data object Splash : Screen("splash")
}
