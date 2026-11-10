package com.nexiplay.app.ui.screens.notifications

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.Notice
import com.nexiplay.app.data.model.RecentEpisodeRow
import com.nexiplay.app.data.model.UpdateItem
import com.nexiplay.app.data.models.Notification
import com.nexiplay.app.data.util.EpisodeUpdatesNotificationManager
import com.nexiplay.app.ui.util.UserFacingError
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class NotificationState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val busyIds: Set<String> = emptySet(),
    val isClearing: Boolean = false,
    val isSignedIn: Boolean = false,
    val error: String? = null,
)

class NotificationViewModel(application: Application) : AndroidViewModel(application) {
    private val _state = MutableStateFlow(NotificationState())
    val state: StateFlow<NotificationState> = _state.asStateFlow()
    private val preferences = application.getSharedPreferences("notification_state", Application.MODE_PRIVATE)

    init { fetchNotifications() }

    fun fetchNotifications(refresh: Boolean = false) {
        viewModelScope.launch {
            val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
            val userKey = uid ?: "guest"

            _state.value = _state.value.copy(
                isLoading = !refresh && _state.value.notifications.isEmpty(),
                isRefreshing = refresh,
                isSignedIn = uid != null,
                error = null,
            )

            try {
                val dbNotificationsResultAndMore = coroutineScope {
                    val dbDeferred = async {
                        runCatching {
                            SupabaseClient.main.from("notifications")
                                .select {
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
                                    limit(60)
                                }
                                .decodeList<Notification>()
                        }
                    }

                    val updatesDeferred = async {
                        runCatching {
                            val tableUpdates = runCatching {
                                SupabaseClient.main.from("updates")
                                    .select {
                                        filter { eq("is_active", true) }
                                        order("updated_at", Order.DESCENDING)
                                        limit(40)
                                    }
                                    .decodeList<UpdateItem>()
                            }.getOrDefault(emptyList())

                            val recentEpisodes = runCatching {
                                SupabaseClient.main.from("episodes")
                                    .select(
                                        Columns.raw("id, episode_number, created_at, seasons!inner(season_number, movie_id, movies!inner(id, title, slug, poster_url, type, is_adult))")
                                    ) {
                                        order("created_at", Order.DESCENDING)
                                        limit(25)
                                    }
                                    .decodeList<RecentEpisodeRow>()
                                    .mapNotNull { it.toUpdateItem() }
                            }.getOrDefault(emptyList())

                            (recentEpisodes + tableUpdates)
                                .sortedByDescending { (it.updatedAt ?: it.createdAt).orEmpty() }
                                .distinctBy {
                                    val cleanSlug = it.slug.trim().removePrefix("/").substringAfterLast("/").lowercase()
                                    "${cleanSlug}_${it.updateType}_${it.seasonNumber ?: 0}_${it.episodeNumber ?: 0}"
                                }
                        }
                    }

                    val noticesDeferred = async {
                        runCatching {
                            SupabaseClient.main.from("notices")
                                .select {
                                    filter { eq("is_active", true) }
                                    order("created_at", Order.DESCENDING)
                                    limit(10)
                                }
                                .decodeList<Notice>()
                        }
                    }

                    val runningDeferred = async {
                        runCatching {
                            SupabaseClient.main.from("movies")
                                .select(
                                    Columns.list(
                                        "id", "title", "slug", "poster_url", "banner_url_mobile",
                                        "type", "is_running", "last_episode", "running_status", "created_at"
                                    )
                                ) {
                                    filter { eq("is_running", true) }
                                    order("updated_at", Order.DESCENDING)
                                    limit(20)
                                }
                                .decodeList<Movie>()
                        }
                    }

                    listOf(
                        dbDeferred.await(),
                        updatesDeferred.await(),
                        noticesDeferred.await(),
                        runningDeferred.await()
                    )
                }

                @Suppress("UNCHECKED_CAST")
                val dbNotificationsResult = dbNotificationsResultAndMore[0] as Result<List<Notification>>
                @Suppress("UNCHECKED_CAST")
                val updatesResult = dbNotificationsResultAndMore[1] as Result<List<UpdateItem>>
                @Suppress("UNCHECKED_CAST")
                val noticesResult = dbNotificationsResultAndMore[2] as Result<List<Notice>>
                @Suppress("UNCHECKED_CAST")
                val runningResult = dbNotificationsResultAndMore[3] as Result<List<Movie>>

                val dbList = dbNotificationsResult.getOrDefault(emptyList())
                val runningMovies = runningResult.getOrDefault(emptyList())
                val posterBySlug = runningMovies.associate {
                    it.slug.trim().lowercase() to (it.posterUrl ?: it.bannerUrlMobile)
                }

                if (refresh) {
                    EpisodeUpdatesNotificationManager.checkAndNotifyNewUpdates(getApplication())
                }

                val updateNotifications = updatesResult.getOrDefault(emptyList()).mapIndexedNotNull { index, item ->
                    if (item.id.isBlank() || item.title.isBlank()) return@mapIndexedNotNull null
                    val rawUpdateType = item.updateType?.lowercase()?.trim() ?: "movie"
                    val cleanContentType = item.contentType?.lowercase()?.trim() ?: "anime"
                    val sNum = item.seasonNumber ?: 1
                    val eNum = item.episodeNumber
                    val isEpisode = rawUpdateType == "episode" || eNum != null
                    val isSeason = rawUpdateType == "season"

                    val badge = when {
                        isEpisode -> "S$sNum · EP ${eNum ?: 1}"
                        isSeason -> "SEASON $sNum"
                        cleanContentType == "anime" -> "NEW ANIME"
                        cleanContentType == "series" -> "NEW SERIES"
                        else -> "NEW MOVIE"
                    }

                    val messageText = when {
                        isEpisode -> "Season $sNum · Episode ${eNum ?: 1} is now streaming in HD"
                        isSeason -> "Full Season $sNum is now available to stream in HD"
                        else -> "Now available to stream & download in Full HD"
                    }

                    val cleanSlug = item.slug.trim()
                        .removePrefix("/watch/")
                        .removePrefix("/content/")
                        .removePrefix("/anime/")
                        .removePrefix("/series/")
                        .removePrefix("/movie/")
                        .removePrefix("/")

                    val deepLink = when {
                        isEpisode && cleanSlug.isNotBlank() ->
                            "nexiplay://content/$cleanContentType/$cleanSlug?season=$sNum&episode=${eNum ?: 1}"
                        cleanSlug.isNotBlank() ->
                            "nexiplay://content/$cleanContentType/$cleanSlug"
                        else -> null
                    }

                    Notification(
                        id = "update_${item.id}",
                        userId = null,
                        title = item.title,
                        message = messageText,
                        type = if (isEpisode) "episode" else if (isSeason) "season" else "release",
                        isRead = index >= 6,
                        createdAt = item.createdAt ?: item.updatedAt.orEmpty(),
                        deepLink = deepLink,
                        contentType = cleanContentType,
                        slug = cleanSlug.ifBlank { null },
                        posterUrl = item.posterUrl?.takeIf { it.isNotBlank() } ?: posterBySlug[cleanSlug.lowercase()],
                        badgeText = badge,
                    )
                }

                val existingUpdateSlugs = updateNotifications.mapNotNull { it.slug?.lowercase() }.toSet()

                val runningNotifications = runningMovies.mapIndexedNotNull { index, movie ->
                    val cleanSlug = movie.slug.trim()
                        .removePrefix("/watch/")
                        .removePrefix("/content/")
                        .removePrefix("/anime/")
                        .removePrefix("/series/")
                        .removePrefix("/movie/")
                        .removePrefix("/")
                    if (cleanSlug.isBlank() || cleanSlug.lowercase() in existingUpdateSlugs) return@mapIndexedNotNull null
                    val epNum = movie.lastEpisode ?: 1
                    val cleanType = movie.type.trim().lowercase().ifBlank { "anime" }

                    Notification(
                        id = "running_${movie.id}_ep$epNum",
                        userId = null,
                        title = movie.title,
                        message = "Running ${cleanType.replaceFirstChar { it.uppercase() }} · Episode $epNum is now streaming in Full HD",
                        type = "episode",
                        isRead = index >= 3,
                        createdAt = movie.createdAt.orEmpty(),
                        deepLink = "nexiplay://content/$cleanType/$cleanSlug?season=1&episode=$epNum",
                        contentType = cleanType,
                        slug = cleanSlug,
                        posterUrl = movie.posterUrl ?: movie.bannerUrlMobile,
                        badgeText = "RUNNING · EP $epNum",
                    )
                }

                val noticeNotifications = noticesResult.getOrDefault(emptyList())
                    .filter { notice ->
                        notice.isActive &&
                            notice.content.isNotBlank() &&
                            notice.platform.lowercase() in setOf("all", "app", "both")
                    }
                    .map { notice ->
                        Notification(
                            id = "notice_${notice.id}",
                            userId = null,
                            title = "Nexiplay Official Notice",
                            message = notice.content.trim(),
                            type = "announcement",
                            isRead = false,
                            createdAt = notice.createdAt.orEmpty(),
                            posterUrl = notice.imageUrl,
                            badgeText = notice.type.uppercase().ifBlank { "NOTICE" },
                        )
                    }

                val combined = (dbList + noticeNotifications + updateNotifications + runningNotifications)
                    .distinctBy { it.id }
                    .sortedByDescending { it.createdAt }

                if (combined.isEmpty() && dbNotificationsResult.isFailure && updatesResult.isFailure) {
                    val cause = updatesResult.exceptionOrNull() ?: dbNotificationsResult.exceptionOrNull()
                    _state.value = _state.value.copy(
                        isLoading = false,
                        isRefreshing = false,
                        error = if (cause != null) {
                            UserFacingError.from(cause, "Couldn't load notifications. Pull down to retry.")
                        } else null,
                    )
                    return@launch
                }

                val hidden = preferences.getStringSet(hiddenKey(userKey), emptySet()).orEmpty()
                val locallyRead = preferences.getStringSet(readKey(userKey), emptySet()).orEmpty()

                _state.value = _state.value.copy(
                    notifications = combined
                        .filterNot { it.id in hidden }
                        .map { if (it.id in locallyRead) it.copy(isRead = true) else it },
                    isLoading = false,
                    isRefreshing = false,
                    error = null,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = UserFacingError.from(e, "Couldn't load notifications. Pull down to retry."),
                )
            }
        }
    }

    fun markAsRead(notification: Notification) {
        if (notification.isRead || notification.id.isBlank()) return
        val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
        val userKey = uid ?: "guest"
        val previous = _state.value.notifications
        _state.value = _state.value.copy(
            notifications = previous.map { if (it.id == notification.id) it.copy(isRead = true) else it },
            error = null,
        )
        saveLocalIdSet(readKey(userKey), notification.id)
        viewModelScope.launch {
            if (uid != null && notification.userId == uid) {
                runCatching {
                    SupabaseClient.main.from("notifications")
                        .update(buildJsonObject { put("is_read", true) }) {
                            filter {
                                eq("id", notification.id)
                                eq("user_id", uid)
                            }
                        }
                }
            }
        }
    }

    fun markAllAsRead() {
        val unread = _state.value.notifications.filter { !it.isRead }
        if (unread.isEmpty()) return
        val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
        val userKey = uid ?: "guest"
        val allIds = unread.map { it.id }.filter { it.isNotBlank() }

        _state.value = _state.value.copy(
            notifications = _state.value.notifications.map { it.copy(isRead = true) },
            error = null,
        )
        saveLocalIdSet(readKey(userKey), allIds)

        if (uid != null && unread.any { it.userId == uid }) {
            viewModelScope.launch {
                runCatching {
                    SupabaseClient.main.from("notifications")
                        .update(buildJsonObject { put("is_read", true) }) {
                            filter { eq("user_id", uid) }
                        }
                }
            }
        }
    }

    fun delete(notification: Notification) {
        if (notification.id.isBlank() || notification.id in _state.value.busyIds) return
        val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
        val userKey = uid ?: "guest"
        val previous = _state.value.notifications
        _state.value = _state.value.copy(
            notifications = previous.filterNot { it.id == notification.id },
            busyIds = _state.value.busyIds + notification.id,
            error = null,
        )
        saveLocalIdSet(hiddenKey(userKey), notification.id)
        viewModelScope.launch {
            try {
                if (uid != null && notification.userId == uid) {
                    runCatching {
                        SupabaseClient.main.from("notifications").delete {
                            filter {
                                eq("id", notification.id)
                                eq("user_id", uid)
                            }
                        }
                    }
                }
            } finally {
                _state.value = _state.value.copy(busyIds = _state.value.busyIds - notification.id)
            }
        }
    }

    fun clearAll() {
        if (_state.value.isClearing) return
        val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
        val userKey = uid ?: "guest"
        val previous = _state.value.notifications
        val allIds = previous.map { it.id }.filter { it.isNotBlank() }
        _state.value = _state.value.copy(isClearing = true, error = null)
        saveLocalIdSet(hiddenKey(userKey), allIds)
        viewModelScope.launch {
            try {
                if (uid != null) {
                    runCatching {
                        SupabaseClient.main.from("notifications").delete {
                            filter { eq("user_id", uid) }
                        }
                    }
                }
                _state.value = _state.value.copy(notifications = emptyList(), isClearing = false)
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isClearing = false,
                    error = UserFacingError.from(e, "Couldn't clear notifications. Try again."),
                )
            }
        }
    }

    private fun hiddenKey(userKey: String) = "hidden_$userKey"
    private fun readKey(userKey: String) = "read_$userKey"

    private fun saveLocalIdSet(key: String, vararg ids: String) {
        val updated = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet().apply { addAll(ids) }
        preferences.edit().putStringSet(key, updated).apply()
    }

    private fun saveLocalIdSet(key: String, ids: List<String>) {
        val updated = preferences.getStringSet(key, emptySet()).orEmpty().toMutableSet().apply { addAll(ids) }
        preferences.edit().putStringSet(key, updated).apply()
    }
}
