package com.nexiplay.app.ui.navigation

import android.net.Uri
import com.nexiplay.app.ui.screens.detail.DetailViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Holds a push/deep-link destination until the Compose NavHost is ready to consume it. */
object NotificationRouter {
    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute = _pendingRoute.asStateFlow()

    fun open(
        link: String? = null,
        screen: String? = null,
        contentType: String? = null,
        slug: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ) {
        val hasTarget = !link.isNullOrBlank() ||
            !screen.isNullOrBlank() ||
            !slug.isNullOrBlank()
        if (!hasTarget) return

        val target = resolve(link, screen, contentType, slug, seasonNumber, episodeNumber)
        _pendingRoute.value = target
    }

    fun consume() {
        _pendingRoute.value = null
    }

    fun resolve(
        link: String?,
        screen: String? = null,
        contentType: String? = null,
        slug: String? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
    ): String {
        val uri = link?.trim()?.takeIf { it.isNotEmpty() }?.let { runCatching { Uri.parse(it) }.getOrNull() }
        val uriSeason = uri?.getQueryParameter("season")?.toIntOrNull()
            ?: uri?.getQueryParameter("s")?.toIntOrNull()
            ?: uri?.getQueryParameter("season_number")?.toIntOrNull()
        val uriEpisode = uri?.getQueryParameter("episode")?.toIntOrNull()
            ?: uri?.getQueryParameter("ep")?.toIntOrNull()
            ?: uri?.getQueryParameter("episode_number")?.toIntOrNull()

        val resolvedSeason = seasonNumber ?: uriSeason
        val resolvedEpisode = episodeNumber ?: uriEpisode

        if (resolvedSeason != null || resolvedEpisode != null) {
            DetailViewModel.setPendingWatchTarget(resolvedSeason, resolvedEpisode)
        }

        val host = uri?.host?.lowercase()
        val explicitScreen = screen?.trim()?.lowercase()

        // Only open Watch player directly if explicitly requested via screen="watch" or nexiplay://watch/...
        // Otherwise default to ContentDetail so clicking a notification opens the Content Page!
        val isExplicitWatch = explicitScreen == "watch" ||
            (uri?.scheme == "nexiplay" && host == "watch") ||
            link?.startsWith("nexiplay://watch/") == true

        // 1. Direct slug provided (even if contentType is missing, infer it from slug/link or default to "anime")
        if (!slug.isNullOrBlank()) {
            val rawSlug = slug.trim()
            val inferredType = when {
                !contentType.isNullOrBlank() -> contentType.trim().lowercase()
                rawSlug.contains("/anime/") || link?.contains("/anime/") == true -> "anime"
                rawSlug.contains("/series/") || link?.contains("/series/") == true -> "series"
                rawSlug.contains("/movie/") || link?.contains("/movie/") == true -> "movie"
                else -> "anime"
            }
            val cleanSlug = rawSlug
                .substringBefore("?")
                .removePrefix("/watch/")
                .removePrefix("/content/")
                .removePrefix("/anime/")
                .removePrefix("/series/")
                .removePrefix("/movie/")
                .removePrefix("/")
                .substringAfterLast("/")
                .trim()

            if (cleanSlug.isNotBlank()) {
                return if (isExplicitWatch) {
                    Screen.Watch.createRoute(inferredType, cleanSlug)
                } else {
                    Screen.ContentDetail.createRoute(inferredType, cleanSlug)
                }
            }
        }

        // 2. Direct named screen provided
        when (explicitScreen) {
            "home" -> return Screen.Home.route
            "browse" -> return Screen.Browse.route
            "novels" -> return Screen.Novels.route
            "coins" -> return Screen.Coins.route
            "profile" -> return Screen.Profile.route
            "downloads" -> return "downloads"
            "notifications" -> return Screen.Notifications.route
            "live_tv", "livetv" -> return Screen.LiveTv.route
            "games", "arcade" -> return Screen.Games.route
            "vibe_check", "vibecheck" -> return Screen.VibeCheck.route
        }

        // 3. Parse URI path segments (supports nexiplay://content/anime/slug, nexiplay://anime/slug, https://.../anime/slug, etc.)
        val segments = uri?.pathSegments.orEmpty()
        val routeParts = when {
            uri?.scheme == "nexiplay" && host in setOf("home", "browse", "novels", "coins", "profile", "downloads", "notifications", "live_tv", "games", "vibe_check") -> {
                return when (host) {
                    "browse" -> Screen.Browse.route
                    "novels" -> Screen.Novels.route
                    "coins" -> Screen.Coins.route
                    "profile" -> Screen.Profile.route
                    "downloads" -> "downloads"
                    "notifications" -> Screen.Notifications.route
                    "live_tv" -> Screen.LiveTv.route
                    "games" -> Screen.Games.route
                    "vibe_check" -> Screen.VibeCheck.route
                    else -> Screen.Home.route
                }
            }
            uri?.scheme == "nexiplay" && host == "content" -> segments
            uri?.scheme == "nexiplay" && host == "watch" -> listOf("watch") + segments
            uri?.scheme == "nexiplay" && !host.isNullOrBlank() -> listOf(host) + segments
            else -> segments
        }

        if (routeParts.isNotEmpty()) {
            val kind = routeParts.first().lowercase()
            val type = when (kind) {
                "movie", "movies" -> "movie"
                "series", "tv" -> "series"
                "anime" -> "anime"
                else -> null
            }
            if (type != null && routeParts.size >= 2) {
                val targetSlug = routeParts[1].substringBefore("?").trim()
                return if (isExplicitWatch) {
                    Screen.Watch.createRoute(type, targetSlug)
                } else {
                    Screen.ContentDetail.createRoute(type, targetSlug)
                }
            }
            if (kind == "content" && routeParts.size >= 3) {
                val resolvedType = routeParts[1].lowercase()
                val targetSlug = routeParts[2].substringBefore("?").trim()
                return Screen.ContentDetail.createRoute(resolvedType, targetSlug)
            }
            if (kind == "watch" && routeParts.size >= 3) {
                val resolvedType = routeParts[1].lowercase()
                val targetSlug = routeParts[2].substringBefore("?").trim()
                return Screen.Watch.createRoute(resolvedType, targetSlug)
            }
            if (kind == "novel" && routeParts.size >= 2) {
                return Screen.NovelDetail.createRoute(routeParts[1])
            }
            when (kind) {
                "home" -> return Screen.Home.route
                "browse" -> return Screen.Browse.route
                "novels" -> return Screen.Novels.route
                "coins" -> return Screen.Coins.route
                "profile" -> return Screen.Profile.route
                "notifications" -> return Screen.Notifications.route
                "live-tv", "live_tv" -> return Screen.LiveTv.route
                "games" -> return Screen.Games.route
                "mood", "vibe_check" -> return Screen.VibeCheck.route
            }
        }
        return Screen.Home.route
    }
}
