package com.nexiplay.app.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.unit.dp
import com.nexiplay.app.data.model.Notice
import com.nexiplay.app.ui.viewmodels.GlobalNoticeViewModel

/**
 * GlobalNoticeManager renders all non-inline notices as overlays on top of the app content.
 * It is placed at the root level (NavHost) so notices persist across screen navigation.
 *
 * @param currentRoute The current navigation route (e.g., "home", "content/anime/my-slug").
 * @param viewModel The shared GlobalNoticeViewModel instance.
 * @param content The actual app content (NavHost body).
 */
@Composable
fun GlobalNoticeManager(
    currentRoute: String?,
    viewModel: GlobalNoticeViewModel,
    content: @Composable () -> Unit
) {
    val allNotices by viewModel.notices.collectAsState()
    val dismissedIds by viewModel.dismissedNoticeIds.collectAsState()

    // Filter out dismissed notices
    val activeNotices = allNotices.filter { it.id !in dismissedIds }

    // Determine which page context we're in
    val pageContext = resolvePageContext(currentRoute)

    // Filter notices relevant to the current page
    val relevantNotices = activeNotices.filter { notice ->
        when (notice.pages) {
            "all" -> true
            "home" -> pageContext == "home"
            "movie" -> pageContext == "movie"
            "specific" -> {
                // For specific, we need to check movieId against the current route's slug/movie
                // The route format is "content/{type}/{slug}" — we can't easily get movieId from slug here,
                // so we show specific notices on all movie pages (admin can target by movieId if needed)
                pageContext == "movie"
            }
            else -> true
        }
    }

    // Don't show notices on splash or auth screens
    val shouldShowNotices = currentRoute != null &&
            currentRoute != "splash" &&
            currentRoute != "login" &&
            currentRoute != "register"

    // Separate by type
    val topBarNotices = relevantNotices.filter { it.type == "top_bar" }
    val bottomBarNotices = relevantNotices.filter { it.type == "bottom_bar" }
    val popupNotices = relevantNotices.filter { it.type == "popup" }
    val fullscreenNotices = relevantNotices.filter { it.type == "fullscreen" }
    val marqueeTopNotices = relevantNotices.filter { it.type == "marquee" }
    val marqueeBottomNotices = relevantNotices.filter { it.type == "marquee_bottom" }
    val toastNotices = relevantNotices.filter { it.type == "toast" }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── Top overlay notices (Top Bar + Marquee Top) ──
        if (shouldShowNotices) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .zIndex(100f)
            ) {
                topBarNotices.forEach { notice ->
                    TopBarNotice(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) })
                }
                marqueeTopNotices.forEach { notice ->
                    MarqueeNotice(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) })
                }
            }
        }

        // ── Main content ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (shouldShowNotices) ((topBarNotices.size + marqueeTopNotices.size) * 0).dp else 0.dp
                )
        ) {
            content()
        }

        // ── Bottom overlay notices (Bottom Bar + Marquee Bottom) ──
        if (shouldShowNotices) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .zIndex(100f)
            ) {
                marqueeBottomNotices.forEach { notice ->
                    MarqueeNotice(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) }, isBottom = true)
                }
                bottomBarNotices.forEach { notice ->
                    BottomBarNotice(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) })
                }
            }
        }

        // ── Toast notices (bottom area, stacked) ──
        if (shouldShowNotices) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 80.dp)
                    .zIndex(101f)
            ) {
                toastNotices.forEach { notice ->
                    ToastNotice(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) })
                }
            }
        }

        // ── Popup dialogs ──
        if (shouldShowNotices) {
            popupNotices.firstOrNull()?.let { notice ->
                PopupNotice(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) })
            }
        }

        // ── Fullscreen takeover ──
        if (shouldShowNotices) {
            fullscreenNotices.firstOrNull()?.let { notice ->
                FullscreenNotice(notice = notice, onDismiss = { viewModel.dismissNotice(notice.id) })
            }
        }
    }
}

/**
 * Maps the current navigation route to a simple page context string.
 */
private fun resolvePageContext(route: String?): String {
    if (route == null) return "unknown"
    return when {
        route == "home" -> "home"
        route.startsWith("content/") -> "movie"
        route.startsWith("watch/") -> "movie"
        route == "browse" -> "browse"
        route == "novels" -> "novels"
        route.startsWith("novel/") -> "novels"
        route == "coins" -> "coins"
        route == "profile" -> "profile"
        route == "search" -> "search"
        else -> "other"
    }
}
