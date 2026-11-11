package com.nexiplay.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.LiveTv
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nexiplay.app.data.models.Notification
import com.nexiplay.app.data.util.EpisodeUpdatesNotificationManager
import com.nexiplay.app.ui.components.ErrorView
import com.nexiplay.app.ui.components.NotificationsSkeleton
import com.nexiplay.app.ui.navigation.NotificationRouter
import com.nexiplay.app.ui.theme.*
import com.onesignal.OneSignal
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

// ── Sleek Violet Afterglow Palette (Matches GamesScreen & LiveTvScreen 1:1) ──
private val NotifBg = Color(0xFF0D0B12)
private val NotifSurface = Color(0xFF14111D)
private val NotifSurfaceUnread = Color(0xFF191426)
private val NotifBorder = Color(0xFF231E32)
private val NotifBorderUnread = Color(0xFF3A2D66)
private val NotifViolet = Color(0xFF9B7BFF)
private val NotifVioletLight = Color(0xFFB89EFF)
private val NotifBadgeBg = Color(0xFF251D3D)
private val NotifTextSub = Color(0xFF9E98AC)
private val NotifTextDim = Color(0xFF6E6882)

private enum class NotificationFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    RELEASES("Releases"),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(
    navController: NavController,
    viewModel: NotificationViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsState()
    var confirmClearAll by remember { mutableStateOf(false) }
    var activeFilter by remember { mutableStateOf(NotificationFilter.ALL) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    var notificationsEnabled by remember {
        mutableStateOf(EpisodeUpdatesNotificationManager.canPostNotifications(context))
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                notificationsEnabled = EpisodeUpdatesNotificationManager.canPostNotifications(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val unreadCount = remember(state.notifications) {
        state.notifications.count { !it.isRead }
    }
    val releasesCount = remember(state.notifications) {
        state.notifications.count {
            it.type.lowercase() in setOf("episode", "season", "release", "movie", "anime", "series") ||
                !it.slug.isNullOrBlank()
        }
    }

    val filteredNotifications = remember(state.notifications, activeFilter) {
        when (activeFilter) {
            NotificationFilter.ALL -> state.notifications
            NotificationFilter.UNREAD -> state.notifications.filter { !it.isRead }
            NotificationFilter.RELEASES -> state.notifications.filter {
                it.type.lowercase() in setOf("episode", "season", "release", "movie", "anime", "series") ||
                    !it.slug.isNullOrBlank()
            }
        }
    }

    if (confirmClearAll) {
        AlertDialog(
            onDismissRequest = { if (!state.isClearing) confirmClearAll = false },
            title = {
                Text(
                    "Clear notifications?",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                )
            },
            text = {
                Text(
                    "This removes all current notifications from your feed.",
                    color = NotifTextSub,
                )
            },
            confirmButton = {
                TextButton(
                    enabled = !state.isClearing,
                    onClick = {
                        viewModel.clearAll()
                        confirmClearAll = false
                    },
                ) {
                    Text(
                        if (state.isClearing) "Clearing…" else "Clear all",
                        color = Color(0xFFFF5C7C),
                        fontWeight = FontWeight.Bold,
                    )
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { confirmClearAll = false },
                    enabled = !state.isClearing,
                ) {
                    Text("Cancel", color = NotifTextSub)
                }
            },
            containerColor = NotifSurface,
            shape = RoundedCornerShape(20.dp),
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NotifBg)
    ) {
        // ── Clean Single-Line Header (Matches GamesScreen 1:1) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.weight(1f),
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowBack,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier
                        .size(22.dp)
                        .clickable { navController.popBackStack() },
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Notifications",
                        fontWeight = FontWeight.Black,
                        fontSize = 20.sp,
                        color = Color.White,
                        fontFamily = InterFont,
                        letterSpacing = (-0.4).sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (unreadCount > 0) {
                            "$unreadCount UNREAD · LATEST RELEASES"
                        } else {
                            "LATEST EPISODES & RELEASES"
                        },
                        fontSize = 10.sp,
                        color = NotifTextSub,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFont,
                        letterSpacing = 0.8.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }

            // Compact Right Action Icons
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (unreadCount > 0) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NotifBadgeBg)
                            .border(1.dp, NotifBorderUnread, RoundedCornerShape(11.dp))
                            .clickable { viewModel.markAllAsRead() },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DoneAll,
                            contentDescription = "Mark all read",
                            tint = NotifVioletLight,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }

                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(NotifSurface)
                        .border(1.dp, NotifBorder, RoundedCornerShape(11.dp))
                        .clickable(enabled = !state.isRefreshing) {
                            viewModel.fetchNotifications(refresh = true)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.Refresh,
                        contentDescription = "Refresh",
                        tint = Color(0xFFD0C9E8),
                        modifier = Modifier.size(17.dp),
                    )
                }

                if (state.notifications.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(11.dp))
                            .background(NotifSurface)
                            .border(1.dp, NotifBorder, RoundedCornerShape(11.dp))
                            .clickable(enabled = !state.isClearing) {
                                confirmClearAll = true
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.DeleteOutline,
                            contentDescription = "Clear all",
                            tint = Color(0xFFFF7A95),
                            modifier = Modifier.size(17.dp),
                        )
                    }
                }
            }
        }

        // ── 3-Column Underline Tabs (All / Unread / Releases) ──
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
            ) {
                NotificationFilter.entries.forEach { tab ->
                    val isSelected = activeFilter == tab
                    val count = when (tab) {
                        NotificationFilter.ALL -> state.notifications.size
                        NotificationFilter.UNREAD -> unreadCount
                        NotificationFilter.RELEASES -> releasesCount
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { activeFilter = tab }
                            .padding(top = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(bottom = 10.dp),
                        ) {
                            Text(
                                text = tab.label,
                                color = if (isSelected) NotifVioletLight else NotifTextDim,
                                fontSize = 13.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                                maxLines = 1,
                            )
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isSelected) NotifBadgeBg else NotifSurface)
                                    .padding(horizontal = 6.dp, vertical = 1.5.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = count.toString(),
                                    color = if (isSelected) NotifVioletLight else NotifTextDim,
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    maxLines = 1,
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(2.dp)
                                .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                                .background(if (isSelected) NotifViolet else Color.Transparent)
                        )
                    }
                }
            }
            HorizontalDivider(color = Color(0xFF1D1829), thickness = 1.dp)
        }

        if (!notificationsEnabled) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF201633))
                    .border(1.dp, Color(0xFF53399C), RoundedCornerShape(14.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(NotifViolet.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.NotificationsActive,
                        contentDescription = null,
                        tint = NotifVioletLight,
                        modifier = Modifier.size(18.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Turn on push notifications",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Allow alerts to receive new episode & release notifications outside the app.",
                        color = NotifTextSub,
                        fontSize = 11.5.sp,
                        lineHeight = 15.sp,
                    )
                }
                TextButton(
                    onClick = {
                        coroutineScope.launch {
                            runCatching { OneSignal.Notifications.requestPermission(true) }
                            val stillBlocked = !EpisodeUpdatesNotificationManager.canPostNotifications(context)
                            notificationsEnabled = !stillBlocked
                            if (stillBlocked && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                runCatching {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                                        }
                                    )
                                }
                            }
                        }
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "Enable",
                        color = NotifVioletLight,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        // ── Notification Feed ──
        PullToRefreshBox(
            isRefreshing = state.isRefreshing,
            onRefresh = { viewModel.fetchNotifications(refresh = true) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            when {
                state.isLoading -> NotificationsSkeleton()

                state.error != null && state.notifications.isEmpty() -> ErrorView(
                    message = state.error.orEmpty(),
                    onRetry = { viewModel.fetchNotifications(refresh = true) },
                )

                filteredNotifications.isEmpty() -> EmptyNotificationsState(
                    filter = activeFilter,
                    onResetFilter = { activeFilter = NotificationFilter.ALL },
                    onRefresh = { viewModel.fetchNotifications(refresh = true) },
                )

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        start = 14.dp,
                        end = 14.dp,
                        top = 12.dp,
                        bottom = 32.dp,
                    ),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filteredNotifications, key = { it.id }) { notification ->
                        NotificationRowCard(
                            notification = notification,
                            onOpen = {
                                viewModel.markAsRead(notification)
                                val destination = NotificationRouter.resolve(
                                    link = notification.deepLink ?: notification.actionUrl,
                                    contentType = notification.contentType,
                                    slug = notification.slug,
                                )
                                navController.navigate(destination) {
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            onDelete = { viewModel.delete(notification) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NotificationRowCard(
    notification: Notification,
    onOpen: () -> Unit,
    onDelete: () -> Unit,
) {
    val isUnread = !notification.isRead
    val hasActionTarget = !notification.deepLink.isNullOrBlank() ||
        !notification.actionUrl.isNullOrBlank() ||
        !notification.slug.isNullOrBlank()

    val badgeLabel = remember(notification) {
        notification.badgeText?.takeIf { it.isNotBlank() }
            ?: when (notification.type.lowercase()) {
                "episode" -> "NEW EPISODE"
                "season" -> "NEW SEASON"
                "release", "movie", "anime", "series" -> "NEW RELEASE"
                "announcement", "notice" -> "NOTICE"
                else -> "UPDATE"
            }
    }

    val typeIcon = remember(notification.type, notification.contentType) {
        when (notification.contentType?.lowercase() ?: notification.type.lowercase()) {
            "anime", "series", "episode", "season" -> Icons.Default.LiveTv
            "movie", "release" -> Icons.Default.Movie
            "announcement", "notice" -> Icons.Default.Campaign
            else -> Icons.Default.NotificationsActive
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isUnread) NotifSurfaceUnread else NotifSurface)
            .border(
                width = 1.dp,
                color = if (isUnread) NotifBorderUnread else NotifBorder,
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onOpen() }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(11.dp),
    ) {
        // ── Left Poster Thumbnail or Icon Box ──
        if (!notification.posterUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .width(48.dp)
                    .height(64.dp)
                    .clip(RoundedCornerShape(9.dp))
                    .background(Color(0xFF1E192B))
                    .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(9.dp)),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = notification.posterUrl,
                    contentDescription = notification.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                if (isUnread) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(3.dp)
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(NotifViolet)
                    )
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isUnread) NotifBadgeBg else Color(0xFF1B1726))
                    .border(
                        1.dp,
                        if (isUnread) NotifBorderUnread else NotifBorder,
                        RoundedCornerShape(12.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    typeIcon,
                    contentDescription = null,
                    tint = if (isUnread) NotifVioletLight else NotifTextSub,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        // ── Center Info Column (Compact 3-Line Scannable Layout) ──
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            // Line 1: Badge Pill + Type + Date
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(if (isUnread) NotifBadgeBg else Color(0xFF1D182B))
                            .padding(horizontal = 6.dp, vertical = 1.5.dp),
                    ) {
                        Text(
                            text = badgeLabel,
                            color = if (isUnread) NotifVioletLight else NotifTextSub,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.ExtraBold,
                            letterSpacing = 0.3.sp,
                            maxLines = 1,
                        )
                    }

                    if (!notification.contentType.isNullOrBlank()) {
                        Text(
                            text = notification.contentType.uppercase(),
                            color = NotifTextDim,
                            fontSize = 9.5.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                        )
                    }
                }

                Text(
                    text = formatNotificationDate(notification.createdAt),
                    color = NotifTextDim,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }

            // Line 2: Title (Single crisp line)
            Text(
                text = notification.title,
                color = if (isUnread) Color.White else Color(0xFFDAD4E8),
                fontSize = 14.sp,
                fontWeight = if (isUnread) FontWeight.Bold else FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            // Line 3: Subtitle (Single crisp line)
            Text(
                text = notification.message,
                color = NotifTextSub,
                fontSize = 11.5.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        // ── Right Quick Action (Play Icon + Subtle Dismiss) ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            if (hasActionTarget) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(9.dp))
                        .background(if (isUnread) NotifBadgeBg else Color(0xFF1C1728))
                        .border(
                            1.dp,
                            if (isUnread) NotifBorderUnread else NotifBorder,
                            RoundedCornerShape(9.dp),
                        )
                        .clickable { onOpen() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Default.PlayArrow,
                        contentDescription = "Watch",
                        tint = if (isUnread) NotifVioletLight else NotifTextSub,
                        modifier = Modifier.size(16.dp),
                    )
                }
            }

            Box(
                modifier = Modifier
                    .size(26.dp)
                    .clip(CircleShape)
                    .clickable { onDelete() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "Dismiss",
                    tint = NotifTextDim,
                    modifier = Modifier.size(14.dp),
                )
            }
        }
    }
}

@Composable
private fun EmptyNotificationsState(
    filter: NotificationFilter,
    onResetFilter: () -> Unit,
    onRefresh: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(28.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(76.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            colors = listOf(
                                Color(0xFF281E47),
                                Color(0xFF14111D),
                            )
                        )
                    )
                    .border(1.dp, NotifBorderUnread, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Default.NotificationsNone,
                    contentDescription = null,
                    tint = NotifVioletLight,
                    modifier = Modifier.size(36.dp),
                )
            }

            Spacer(Modifier.height(16.dp))

            Text(
                text = when (filter) {
                    NotificationFilter.UNREAD -> "No unread alerts"
                    NotificationFilter.RELEASES -> "No release alerts yet"
                    NotificationFilter.ALL -> "You're all caught up!"
                },
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
            )

            Spacer(Modifier.height(6.dp))

            Text(
                text = when (filter) {
                    NotificationFilter.UNREAD -> "You've checked all your recent episode drops and updates."
                    NotificationFilter.RELEASES -> "New anime episodes, series seasons, and movies will appear here."
                    NotificationFilter.ALL -> "Instant alerts for new episodes and HD movie releases will show up here."
                },
                fontSize = 12.5.sp,
                color = NotifTextSub,
                textAlign = TextAlign.Center,
                lineHeight = 18.sp,
                modifier = Modifier.widthIn(max = 270.dp),
            )

            Spacer(Modifier.height(18.dp))

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(11.dp))
                    .background(NotifBadgeBg)
                    .border(1.dp, NotifBorderUnread, RoundedCornerShape(11.dp))
                    .clickable {
                        if (filter != NotificationFilter.ALL) onResetFilter() else onRefresh()
                    }
                    .padding(horizontal = 16.dp, vertical = 9.dp),
            ) {
                Text(
                    text = if (filter != NotificationFilter.ALL) "Show All Alerts" else "Refresh",
                    color = NotifVioletLight,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun formatNotificationDate(value: String): String {
    if (value.isBlank()) return "Recent"
    return try {
        val instant = Instant.parse(value)
        val now = Instant.now()
        val minutes = Duration.between(instant, now).toMinutes()
        when {
            minutes < 1 -> "Just now"
            minutes < 60 -> "${minutes}m ago"
            minutes < 1440 -> "${minutes / 60}h ago"
            minutes < 10080 -> "${minutes / 1440}d ago"
            else -> DateTimeFormatter.ofPattern("MMM d")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }
    } catch (_: Exception) {
        try {
            val date = LocalDate.parse(value.take(10))
            DateTimeFormatter.ofPattern("MMM d").format(date)
        } catch (_: Exception) {
            value.take(10)
        }
    }
}
