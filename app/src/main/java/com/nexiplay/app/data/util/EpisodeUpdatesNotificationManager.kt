package com.nexiplay.app.data.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.nexiplay.app.MainActivity
import com.nexiplay.app.R
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.Notice
import com.nexiplay.app.data.model.RecentEpisodeRow
import com.nexiplay.app.data.model.UpdateItem
import com.nexiplay.app.data.models.Notification
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

object EpisodeUpdatesNotificationManager {
    const val CHANNEL_EPISODES = "nexiplay_episodes_channel"
    const val CHANNEL_RELEASES = "nexiplay_releases_channel"
    const val CHANNEL_ALERTS = "nexiplay_alerts_channel"

    private const val PREFS_NAME = "nexiplay_updates_notifier_v2"
    private const val KEY_INITIALIZED = "notifier_initialized_v2"
    private const val KEY_EPISODES_SEEDED_V3 = "notifier_episodes_seeded_v3"
    private const val KEY_NOTIFIED_UPDATE_IDS = "notified_update_ids"
    private const val KEY_NOTIFIED_MOVIE_IDS = "notified_movie_ids"
    private const val KEY_NOTIFIED_DB_NOTIF_IDS = "notified_db_notif_ids"
    private const val KEY_NOTIFIED_NOTICE_IDS = "notified_notice_ids"
    private const val KEY_RUNNING_EP_PREFIX = "running_ep_"

    private const val NEXI_VIOLET_COLOR = 0xFF9273F5.toInt()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val isChecking = AtomicBoolean(false)
    private var watcherJob: Job? = null

    fun initChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val episodesChannel = NotificationChannel(
                CHANNEL_EPISODES,
                "New Episodes & Running Content",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Instant alerts when a new episode of a running anime or web series drops"
                enableLights(true)
                lightColor = NEXI_VIOLET_COLOR
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 250, 140, 250)
            }

            val releasesChannel = NotificationChannel(
                CHANNEL_RELEASES,
                "New Movies, Anime & Seasons",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when new movies, full seasons, and anime titles arrive on Nexiplay"
                enableLights(true)
                lightColor = NEXI_VIOLET_COLOR
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 220, 120, 220)
            }

            val alertsChannel = NotificationChannel(
                CHANNEL_ALERTS,
                "Official Announcements & Rewards",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Personal account notifications, coin rewards, and official announcements"
                enableLights(true)
                lightColor = NEXI_VIOLET_COLOR
                enableVibration(true)
            }

            notificationManager.createNotificationChannels(
                listOf(episodesChannel, releasesChannel, alertsChannel)
            )
        }
    }

    /**
     * Starts a continuous background watcher while the app is alive so any new episode,
     * running series update, movie addition, or admin push triggers a system notification automatically.
     */
    fun startLiveWatcher(context: Context) {
        val appContext = context.applicationContext
        initChannel(appContext)
        if (watcherJob?.isActive == true) return
        watcherJob = scope.launch {
            while (isActive) {
                syncAllNotificationsInternal(appContext)
                delay(90_000L) // Check every 90 seconds for live updates
            }
        }
    }

    fun checkAndNotifyNewUpdates(context: Context) {
        val appContext = context.applicationContext
        scope.launch {
            syncAllNotificationsInternal(appContext)
        }
    }

    fun checkAndNotifyWithItems(context: Context, items: List<UpdateItem>) {
        if (items.isEmpty()) return
        val appContext = context.applicationContext
        scope.launch {
            try {
                processUpdateAndRunningCatalog(
                    context = appContext,
                    updates = items,
                    runningMovies = emptyList(),
                    latestMovies = emptyList(),
                    dbNotifications = emptyList(),
                    notices = emptyList(),
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private suspend fun syncAllNotificationsInternal(context: Context) {
        if (!isChecking.compareAndSet(false, true)) return
        try {
            val db = SupabaseClient.main
            val uid = runCatching { db.auth.currentUserOrNull()?.id }.getOrNull()

            coroutineScope {
                val updatesDef = async {
                    runCatching {
                        db.from("updates").select {
                            filter { eq("is_active", true) }
                            order("updated_at", Order.DESCENDING)
                            limit(20)
                        }.decodeList<UpdateItem>()
                    }.getOrDefault(emptyList())
                }

                val recentEpisodesDef = async {
                    runCatching {
                        db.from("episodes").select(
                            Columns.raw("id, episode_number, created_at, seasons!inner(season_number, movie_id, movies!inner(id, title, slug, poster_url, type, is_adult))")
                        ) {
                            order("created_at", Order.DESCENDING)
                            limit(25)
                        }.decodeList<RecentEpisodeRow>()
                    }.getOrDefault(emptyList())
                }

                val runningDef = async {
                    runCatching {
                        db.from("movies").select(
                            Columns.list(
                                "id", "title", "slug", "poster_url", "banner_url_mobile",
                                "type", "is_running", "last_episode", "next_episode",
                                "running_status", "created_at"
                            )
                        ) {
                            filter { eq("is_running", true) }
                            order("updated_at", Order.DESCENDING)
                            limit(30)
                        }.decodeList<Movie>()
                    }.getOrDefault(emptyList())
                }

                val latestMoviesDef = async {
                    runCatching {
                        db.from("movies").select(
                            Columns.list(
                                "id", "title", "slug", "poster_url", "banner_url_mobile",
                                "type", "release_year", "language", "is_running",
                                "last_episode", "created_at"
                            )
                        ) {
                            order("created_at", Order.DESCENDING)
                            limit(15)
                        }.decodeList<Movie>()
                    }.getOrDefault(emptyList())
                }

                val dbNotifsDef = async {
                    runCatching {
                        db.from("notifications").select {
                            filter {
                                if (uid != null) {
                                    or {
                                        eq("user_id", uid)
                                        exact("user_id", null)
                                    }
                                } else {
                                    exact("user_id", null)
                                }
                            }
                            order("created_at", Order.DESCENDING)
                            limit(15)
                        }.decodeList<Notification>()
                    }.getOrDefault(emptyList())
                }

                val noticesDef = async {
                    runCatching {
                        db.from("notices").select {
                            filter { eq("is_active", true) }
                            order("created_at", Order.DESCENDING)
                            limit(5)
                        }.decodeList<Notice>()
                    }.getOrDefault(emptyList())
                }

                val episodeUpdates = recentEpisodesDef.await().mapNotNull { it.toUpdateItem() }
                val mergedUpdates = (episodeUpdates + updatesDef.await())
                    .sortedByDescending { (it.updatedAt ?: it.createdAt).orEmpty() }
                    .distinctBy { buildUpdateTrackingKey(it) }

                processUpdateAndRunningCatalog(
                    context = context,
                    updates = mergedUpdates,
                    runningMovies = runningDef.await(),
                    latestMovies = latestMoviesDef.await(),
                    dbNotifications = dbNotifsDef.await(),
                    notices = noticesDef.await(),
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isChecking.set(false)
        }
    }

    private fun buildUpdateTrackingKey(item: UpdateItem): String {
        val slugKey = cleanContentSlug(item.slug).lowercase().ifBlank { item.id }
        val uType = item.updateType?.trim()?.lowercase() ?: "episode"
        val sNum = item.seasonNumber ?: 0
        val eNum = item.episodeNumber ?: 0
        return "${slugKey}_${uType}_s${sNum}_e${eNum}"
    }

    private suspend fun processUpdateAndRunningCatalog(
        context: Context,
        updates: List<UpdateItem>,
        runningMovies: List<Movie>,
        latestMovies: List<Movie>,
        dbNotifications: List<Notification>,
        notices: List<Notice>,
    ) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val isInitialized = prefs.getBoolean(KEY_INITIALIZED, false)
        val isEpisodesSeededV3 = prefs.getBoolean(KEY_EPISODES_SEEDED_V3, false)

        val notifiedUpdateIds = prefs.getStringSet(KEY_NOTIFIED_UPDATE_IDS, emptySet()).orEmpty().toMutableSet()
        val notifiedMovieIds = prefs.getStringSet(KEY_NOTIFIED_MOVIE_IDS, emptySet()).orEmpty().toMutableSet()
        val notifiedDbNotifIds = prefs.getStringSet(KEY_NOTIFIED_DB_NOTIF_IDS, emptySet()).orEmpty().toMutableSet()
        val notifiedNoticeIds = prefs.getStringSet(KEY_NOTIFIED_NOTICE_IDS, emptySet()).orEmpty().toMutableSet()

        val posterLookup = (runningMovies + latestMovies).associate {
            it.slug.trim().lowercase() to (it.posterUrl ?: it.bannerUrlMobile)
        }

        // First launch: seed existing catalog IDs so we don't spam 50 old notifications,
        // and showcase the #1 latest active episode/release update immediately!
        if (!isInitialized) {
            updates.forEach {
                if (it.id.isNotBlank()) notifiedUpdateIds.add(it.id)
                notifiedUpdateIds.add(buildUpdateTrackingKey(it))
            }
            latestMovies.forEach { if (it.id.isNotBlank()) notifiedMovieIds.add(it.id) }
            runningMovies.forEach { m ->
                if (m.id.isNotBlank()) {
                    notifiedMovieIds.add(m.id)
                    prefs.edit().putInt(KEY_RUNNING_EP_PREFIX + m.id, m.lastEpisode ?: 0).apply()
                }
            }
            dbNotifications.forEach { if (it.id.isNotBlank()) notifiedDbNotifIds.add(it.id) }
            notices.forEach { if (it.id.isNotBlank()) notifiedNoticeIds.add(it.id) }

            // Dispatch the newest highlight notification on first initialization so the user sees it working right away
            val spotlightUpdate = updates.firstOrNull()
            if (spotlightUpdate != null) {
                dispatchUpdateNotification(context, spotlightUpdate, posterLookup)
            } else {
                val spotlightRunning = runningMovies.firstOrNull { (it.lastEpisode ?: 0) > 0 }
                if (spotlightRunning != null) {
                    dispatchRunningEpisodeNotification(context, spotlightRunning)
                }
            }

            prefs.edit()
                .putBoolean(KEY_INITIALIZED, true)
                .putBoolean(KEY_EPISODES_SEEDED_V3, true)
                .putStringSet(KEY_NOTIFIED_UPDATE_IDS, notifiedUpdateIds)
                .putStringSet(KEY_NOTIFIED_MOVIE_IDS, notifiedMovieIds)
                .putStringSet(KEY_NOTIFIED_DB_NOTIF_IDS, notifiedDbNotifIds)
                .putStringSet(KEY_NOTIFIED_NOTICE_IDS, notifiedNoticeIds)
                .apply()
            return
        }

        // One-time migration for existing installs upgrading to live `episodes` table tracking:
        // notify the #1 newest episode if unseen, and seed older historical episode keys
        if (!isEpisodesSeededV3 && updates.isNotEmpty()) {
            val newest = updates.first()
            val newestKey = buildUpdateTrackingKey(newest)
            if (newestKey !in notifiedUpdateIds && newest.id !in notifiedUpdateIds) {
                dispatchUpdateNotification(context, newest, posterLookup)
            }
            updates.forEach {
                if (it.id.isNotBlank()) notifiedUpdateIds.add(it.id)
                notifiedUpdateIds.add(buildUpdateTrackingKey(it))
            }
            prefs.edit()
                .putBoolean(KEY_EPISODES_SEEDED_V3, true)
                .putStringSet(KEY_NOTIFIED_UPDATE_IDS, notifiedUpdateIds)
                .apply()
        }

        val newlyNotifiedSlugs = mutableSetOf<String>()

        // 1. Process ALL new `updates` & `episodes` (tracked by composite content+season+episode key)
        val newUpdates = updates.filter { item ->
            if (item.id.isBlank()) return@filter false
            val trackingKey = buildUpdateTrackingKey(item)
            val isEpisodeOrSeason = item.updateType.equals("episode", ignoreCase = true) ||
                item.updateType.equals("season", ignoreCase = true) ||
                item.episodeNumber != null
            if (isEpisodeOrSeason) {
                trackingKey !in notifiedUpdateIds
            } else {
                trackingKey !in notifiedUpdateIds && item.id !in notifiedUpdateIds
            }
        }
        for (item in newUpdates.take(4)) {
            val cleanSlug = cleanContentSlug(item.slug)
            if (cleanSlug.isNotBlank()) newlyNotifiedSlugs.add(cleanSlug.lowercase())
            dispatchUpdateNotification(context, item, posterLookup)
            notifiedUpdateIds.add(item.id)
            notifiedUpdateIds.add(buildUpdateTrackingKey(item))
        }
        // Mark any remaining batch updates as seen
        newUpdates.forEach {
            notifiedUpdateIds.add(it.id)
            notifiedUpdateIds.add(buildUpdateTrackingKey(it))
        }

        // 2. Process Running Content new episodes (`movies` where `is_running == true`)
        for (movie in runningMovies) {
            if (movie.id.isBlank()) continue
            val currentEp = movie.lastEpisode ?: 0
            val savedEp = prefs.getInt(KEY_RUNNING_EP_PREFIX + movie.id, -1)
            val cleanSlug = cleanContentSlug(movie.slug).lowercase()

            if (savedEp == -1) {
                // Newly added running series
                prefs.edit().putInt(KEY_RUNNING_EP_PREFIX + movie.id, currentEp).apply()
                if (currentEp > 0 && cleanSlug !in newlyNotifiedSlugs && movie.id !in notifiedMovieIds) {
                    newlyNotifiedSlugs.add(cleanSlug)
                    dispatchRunningEpisodeNotification(context, movie)
                    notifiedMovieIds.add(movie.id)
                }
            } else if (currentEp > savedEp) {
                // Existing running series got a new episode!
                prefs.edit().putInt(KEY_RUNNING_EP_PREFIX + movie.id, currentEp).apply()
                if (cleanSlug !in newlyNotifiedSlugs) {
                    newlyNotifiedSlugs.add(cleanSlug)
                    dispatchRunningEpisodeNotification(context, movie)
                }
            }
        }

        // 3. Process Brand-New Catalog Additions (`movies` table)
        val newCatalogMovies = latestMovies.filter {
            it.id.isNotBlank() &&
                it.id !in notifiedMovieIds &&
                cleanContentSlug(it.slug).lowercase() !in newlyNotifiedSlugs
        }
        for (movie in newCatalogMovies.take(2)) {
            dispatchNewMovieReleaseNotification(context, movie)
            notifiedMovieIds.add(movie.id)
        }
        newCatalogMovies.forEach { notifiedMovieIds.add(it.id) }

        // 4. Process Personal & Broadcast Database Notifications (`notifications` table)
        val newDbNotifs = dbNotifications.filter {
            it.id.isNotBlank() && !it.isRead && it.id !in notifiedDbNotifIds
        }
        for (notif in newDbNotifs.take(3)) {
            dispatchDatabaseNotification(context, notif)
            notifiedDbNotifIds.add(notif.id)
        }
        newDbNotifs.forEach { notifiedDbNotifIds.add(it.id) }

        // 5. Process Official Admin Notices (`notices` table)
        val newNotices = notices.filter {
            it.id.isNotBlank() &&
                it.isActive &&
                it.content.isNotBlank() &&
                it.platform.lowercase() in setOf("all", "app", "both") &&
                it.id !in notifiedNoticeIds
        }
        for (notice in newNotices.take(2)) {
            dispatchNoticeNotification(context, notice)
            notifiedNoticeIds.add(notice.id)
        }
        newNotices.forEach { notifiedNoticeIds.add(it.id) }

        prefs.edit()
            .putStringSet(KEY_NOTIFIED_UPDATE_IDS, notifiedUpdateIds)
            .putStringSet(KEY_NOTIFIED_MOVIE_IDS, notifiedMovieIds)
            .putStringSet(KEY_NOTIFIED_DB_NOTIF_IDS, notifiedDbNotifIds)
            .putStringSet(KEY_NOTIFIED_NOTICE_IDS, notifiedNoticeIds)
            .apply()
    }

    private fun dispatchUpdateNotification(
        context: Context,
        item: UpdateItem,
        posterLookup: Map<String, String?>,
    ) {
        val cleanType = item.contentType?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "anime"
        val cleanSlug = cleanContentSlug(item.slug)
        val rawUpdateType = item.updateType?.trim()?.lowercase() ?: "episode"
        val sNum = item.seasonNumber ?: 1
        val eNum = item.episodeNumber ?: 1
        val isEpisode = rawUpdateType == "episode" || item.episodeNumber != null
        val isSeason = rawUpdateType == "season"

        val channelId = if (isEpisode) CHANNEL_EPISODES else CHANNEL_RELEASES
        val subText = when {
            isEpisode -> "NEW EPISODE • ${cleanType.uppercase()}"
            isSeason -> "NEW SEASON • ${cleanType.uppercase()}"
            else -> "NEW RELEASE • ${cleanType.uppercase()}"
        }
        val title = when {
            isEpisode -> "🔥 ${item.title} — S$sNum · EP $eNum"
            isSeason -> "✨ ${item.title} — Season $sNum Added"
            else -> "🎬 New ${cleanType.replaceFirstChar { it.uppercase() }}: ${item.title}"
        }
        val message = when {
            isEpisode -> "Season $sNum · Episode $eNum is now streaming in Full HD! Tap to open content page."
            isSeason -> "Full Season $sNum is now available in Full HD on Nexiplay. Tap to explore episodes."
            else -> "${item.title} is now available to stream & download in Full HD on Nexiplay!"
        }

        val resolvedPoster = item.posterUrl?.takeIf { it.isNotBlank() }
            ?: posterLookup[cleanSlug.lowercase()]

        sendRichContentNotification(
            context = context,
            notificationId = ("upd_${buildUpdateTrackingKey(item)}".hashCode() and 0x7FFFFFFF),
            channelId = channelId,
            subText = subText,
            title = title,
            message = message,
            posterUrl = resolvedPoster,
            contentType = cleanType,
            slug = cleanSlug,
            seasonNumber = if (isEpisode || isSeason) sNum else null,
            episodeNumber = if (isEpisode) eNum else null,
        )
    }

    private fun dispatchRunningEpisodeNotification(
        context: Context,
        movie: Movie,
    ) {
        val cleanType = movie.type.trim().lowercase().ifBlank { "anime" }
        val cleanSlug = cleanContentSlug(movie.slug)
        val epNum = movie.lastEpisode ?: 1
        val statusTag = movie.runningStatus?.takeIf { it.isNotBlank() } ?: "RUNNING ${cleanType.uppercase()}"

        sendRichContentNotification(
            context = context,
            notificationId = ("run_${movie.id}_$epNum".hashCode() and 0x7FFFFFFF),
            channelId = CHANNEL_EPISODES,
            subText = "$statusTag • EPISODE $epNum",
            title = "🔥 ${movie.title} — Episode $epNum Out Now!",
            message = "New episode $epNum of ${movie.title} just dropped in Full HD! Tap to view & stream.",
            posterUrl = movie.posterUrl ?: movie.bannerUrlMobile,
            contentType = cleanType,
            slug = cleanSlug,
            seasonNumber = 1,
            episodeNumber = epNum,
        )
    }

    private fun dispatchNewMovieReleaseNotification(
        context: Context,
        movie: Movie,
    ) {
        val cleanType = movie.type.trim().lowercase().ifBlank { "movie" }
        val cleanSlug = cleanContentSlug(movie.slug)
        val metaParts = buildList {
            movie.releaseYear?.let { add(it.toString()) }
            movie.language?.takeIf { it.isNotBlank() }?.let { add(it) }
            add("Full HD")
        }.joinToString(" • ")

        sendRichContentNotification(
            context = context,
            notificationId = ("mov_${movie.id}".hashCode() and 0x7FFFFFFF),
            channelId = CHANNEL_RELEASES,
            subText = "NEW ${cleanType.uppercase()} RELEASE",
            title = "🎬 ${movie.title} is Now on Nexiplay!",
            message = "$metaParts — Tap to view details, stream or download now.",
            posterUrl = movie.posterUrl ?: movie.bannerUrlMobile,
            contentType = cleanType,
            slug = cleanSlug,
            seasonNumber = null,
            episodeNumber = null,
        )
    }

    private fun dispatchDatabaseNotification(
        context: Context,
        notif: Notification,
    ) {
        val cleanSlug = notif.slug?.let { cleanContentSlug(it) }.orEmpty()
        val cleanType = notif.contentType?.trim()?.lowercase()?.takeIf { it.isNotBlank() } ?: "anime"

        sendRichContentNotification(
            context = context,
            notificationId = ("db_${notif.id}".hashCode() and 0x7FFFFFFF),
            channelId = CHANNEL_ALERTS,
            subText = notif.badgeText?.takeIf { it.isNotBlank() } ?: "NEXIPLAY ALERT",
            title = notif.title,
            message = notif.message,
            posterUrl = notif.posterUrl,
            contentType = cleanType.takeIf { cleanSlug.isNotBlank() },
            slug = cleanSlug.takeIf { it.isNotBlank() },
            seasonNumber = null,
            episodeNumber = null,
            customDeepLink = notif.deepLink ?: notif.actionUrl,
        )
    }

    private fun dispatchNoticeNotification(
        context: Context,
        notice: Notice,
    ) {
        sendRichContentNotification(
            context = context,
            notificationId = ("notice_${notice.id}".hashCode() and 0x7FFFFFFF),
            channelId = CHANNEL_ALERTS,
            subText = "OFFICIAL ANNOUNCEMENT",
            title = "📢 Nexiplay Official Update",
            message = notice.content.trim(),
            posterUrl = notice.imageUrl,
            contentType = null,
            slug = null,
            seasonNumber = null,
            episodeNumber = null,
            customDeepLink = "nexiplay://notifications",
        )
    }

    /**
     * Builds & dispatches an industry-grade Android system notification:
     * - Primary tap opens the Content Detail page (`nexiplay://content/{type}/{slug}`)
     * - Action 1 ("View Content") opens the Content Detail page
     * - Action 2 ("▶ Watch Now") opens the Watch Player directly
     */
    private fun sendRichContentNotification(
        context: Context,
        notificationId: Int,
        channelId: String,
        subText: String,
        title: String,
        message: String,
        posterUrl: String?,
        contentType: String?,
        slug: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
        customDeepLink: String? = null,
    ) {
        val hasContentTarget = !contentType.isNullOrBlank() && !slug.isNullOrBlank()
        val querySuffix = buildString {
            if (seasonNumber != null || episodeNumber != null) {
                append("?")
                if (seasonNumber != null) append("season=$seasonNumber")
                if (episodeNumber != null) {
                    if (seasonNumber != null) append("&")
                    append("episode=$episodeNumber")
                }
            }
        }

        // Primary click target: Content Detail Page (so clicking the notification opens that content's page!)
        val contentDeepLink = when {
            hasContentTarget -> "nexiplay://content/$contentType/$slug$querySuffix"
            !customDeepLink.isNullOrBlank() -> customDeepLink
            else -> "nexiplay://notifications"
        }

        val contentPendingIntent = buildTargetPendingIntent(
            context = context,
            requestCode = notificationId,
            deepLink = contentDeepLink,
            screen = if (hasContentTarget) "content" else null,
            contentType = contentType,
            slug = slug,
            seasonNumber = seasonNumber,
            episodeNumber = episodeNumber,
        )

        val posterBitmap = posterUrl?.takeIf { it.isNotBlank() }?.let { loadBitmapFromUrl(it) }

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_stat_onesignal_default)
            .setColor(NEXI_VIOLET_COLOR)
            .setSubText(subText)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(contentPendingIntent)

        if (hasContentTarget) {
            val watchDeepLink = "nexiplay://watch/$contentType/$slug$querySuffix"
            val watchPendingIntent = buildTargetPendingIntent(
                context = context,
                requestCode = notificationId + 1,
                deepLink = watchDeepLink,
                screen = "watch",
                contentType = contentType,
                slug = slug,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
            )

            builder.addAction(
                0,
                "View Details",
                contentPendingIntent
            )
            builder.addAction(
                0,
                if (episodeNumber != null) "▶ Watch Ep $episodeNumber" else "▶ Watch Now",
                watchPendingIntent
            )
        }

        if (posterBitmap != null) {
            builder.setLargeIcon(posterBitmap)
            builder.setStyle(
                NotificationCompat.BigPictureStyle()
                    .bigPicture(posterBitmap)
                    .bigLargeIcon(null as Bitmap?)
                    .setBigContentTitle(title)
                    .setSummaryText(message)
            )
        } else {
            builder.setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(message)
                    .setBigContentTitle(title)
                    .setSummaryText(subText)
            )
        }

        try {
            NotificationManagerCompat.from(context).notify(notificationId, builder.build())
        } catch (_: SecurityException) {
            // POST_NOTIFICATIONS permission not granted yet
        }
    }

    private fun buildTargetPendingIntent(
        context: Context,
        requestCode: Int,
        deepLink: String,
        screen: String?,
        contentType: String?,
        slug: String?,
        seasonNumber: Int?,
        episodeNumber: Int?,
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_VIEW
            data = Uri.parse(deepLink)
            putExtra("deep_link", deepLink)
            putExtra("url", deepLink)
            if (!screen.isNullOrBlank()) putExtra("screen", screen)
            if (!contentType.isNullOrBlank()) putExtra("content_type", contentType)
            if (!slug.isNullOrBlank()) putExtra("slug", slug)
            if (seasonNumber != null) putExtra("season_number", seasonNumber)
            if (episodeNumber != null) putExtra("episode_number", episodeNumber)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP
        }

        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun cleanContentSlug(rawSlug: String): String {
        return rawSlug.trim()
            .substringBefore("?")
            .removePrefix("/watch/")
            .removePrefix("/content/")
            .removePrefix("/anime/")
            .removePrefix("/series/")
            .removePrefix("/movie/")
            .removePrefix("/")
            .substringAfterLast("/")
            .trim()
    }

    private fun loadBitmapFromUrl(urlStr: String): Bitmap? {
        return try {
            val url = URL(urlStr)
            val conn = (url.openConnection() as HttpURLConnection).apply {
                connectTimeout = 6000
                readTimeout = 6000
                instanceFollowRedirects = true
                doInput = true
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Mobile Safari/537.36"
                )
            }
            conn.connect()
            conn.inputStream.use { input ->
                BitmapFactory.decodeStream(input)
            }
        } catch (_: Exception) {
            null
        }
    }
}
