package com.nexiplay.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Category
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.RecentEpisodeRow
import com.nexiplay.app.data.model.Upcoming
import com.nexiplay.app.data.model.UpdateItem
import com.nexiplay.app.data.repository.TmdbRepository
import com.nexiplay.app.ui.util.UserFacingError
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TelegramSettingsRow(
    @SerialName("telegram_url") val telegramUrl: String? = null,
    @SerialName("is_active") val isActive: Boolean = false,
    @SerialName("button_text") val buttonText: String? = "Join Telegram"
)

data class HomeState(
    val trending: List<Movie> = emptyList(),
    val updates: List<UpdateItem> = emptyList(),
    val upcoming: List<Upcoming> = emptyList(),
    val categories: List<Category> = emptyList(),
    val latestAdditions: List<Movie> = emptyList(),
    val recentMovies: List<Movie> = emptyList(),
    val recentAnime: List<Movie> = emptyList(),
    val recentSeries: List<Movie> = emptyList(),
    val running: List<Movie> = emptyList(),
    val telegramUrl: String? = null,
    val telegramText: String = "Join Telegram",
    val hideNsfw: Boolean = false,
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val cols = Columns.raw(
        "id, title, slug, poster_url, description, type, release_year, is_trending, trending_rank, trending_badge, is_running, is_adult, banner_url_desktop, banner_url_mobile, last_episode, running_notice, next_episode_date, created_at, admin_note, tmdb_id"
    )

    private fun List<Movie>.withPinnedFlag(): List<Movie> = map { m ->
        if (m.adminNote == "pinned" && m.isPinned != true) m.copy(isPinned = true) else m
    }

    /**
     * Keeps all Pinned items fixed at the front, and shuffles all non-pinned Supabase + TMDB items
     * so every time the user opens or refreshes the app they get a fresh, dynamic catalog!
     */
    private fun mergePinnedAndShuffleFresh(
        localList: List<Movie>,
        pinnedPool: List<Movie>,
        tmdbPool: List<Movie>,
        typeFilter: String? = null,
        maxCount: Int = 18
    ): List<Movie> {
        val relevantPinned = pinnedPool
            .filter { typeFilter == null || it.type.equals(typeFilter, ignoreCase = true) }
            .withPinnedFlag()
        val pinnedIds = relevantPinned.mapTo(HashSet()) { it.id }

        val restLocal = localList.withPinnedFlag().filter { it.id !in pinnedIds }
        val localPinned = restLocal.filter { it.isPinned == true || it.adminNote == "pinned" }
        val allPinned = (relevantPinned + localPinned).distinctBy { it.id }
        val allPinnedIds = allPinned.mapTo(HashSet()) { it.id }

        val localUnpinned = restLocal.filter { it.id !in allPinnedIds }
        val dedupedTmdb = TmdbRepository.deduplicateAgainstLocal(tmdbPool, allPinned + localUnpinned)

        // Interleave & shuffle unpinned local + fresh TMDB items so every app launch shows new content
        val shuffledFreshPool = (localUnpinned.shuffled() + dedupedTmdb.shuffled()).shuffled()

        return (allPinned + shuffledFreshPool).distinctBy { it.id }.take(maxCount)
    }

    init {
        viewModelScope.launch {
            // Yield brief frame window so SplashScreen renders Frame 1 instantly without Ktor/Supabase init contention
            delay(180L)
            loadHome()
        }
    }

    fun loadHome(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh && _state.value.isRefreshing) return@launch
            if (isRefresh) {
                _state.value = _state.value.copy(isRefreshing = true, error = null)
            } else {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }
            try {
                // Run all network I/O and list merging off the Main thread for zero UI lag
                val newState = withContext(Dispatchers.Default) {
                    val db = SupabaseClient.main

                    val hideNsfwDef = async(Dispatchers.IO) {
                        try {
                            val uid = db.auth.currentUserOrNull()?.id ?: return@async false
                            val profile = db.from("profiles").select(Columns.list("hide_nsfw")) {
                                filter { eq("id", uid) }
                            }.decodeSingleOrNull<com.nexiplay.app.data.model.UserProfile>()
                            profile?.hideNsfw == true
                        } catch (_: Exception) {
                            false
                        }
                    }

                    val telegramDef = async(Dispatchers.IO) {
                        try {
                            db.from("telegram_settings").select {
                                limit(1)
                            }.decodeList<TelegramSettingsRow>().firstOrNull()
                        } catch (_: Exception) {
                            null
                        }
                    }

                    // Single unified query for Pinned & Trending highlights
                    val highlightsDef = async(Dispatchers.IO) {
                        try {
                            db.from("movies").select(cols) {
                                filter {
                                    or {
                                        eq("admin_note", "pinned")
                                        eq("is_trending", true)
                                        eq("is_running", true)
                                    }
                                }
                                order("created_at", Order.DESCENDING)
                                limit(48)
                            }.decodeList<Movie>().withPinnedFlag()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    // Single unified query for the latest catalog (replaces 5 separate duplicate queries)
                    val catalogDef = async(Dispatchers.IO) {
                        try {
                            db.from("movies").select(cols) {
                                order("created_at", Order.DESCENDING)
                                limit(90)
                            }.decodeList<Movie>().withPinnedFlag()
                        } catch (_: Exception) {
                            if (isRefresh) _state.value.latestAdditions else emptyList()
                        }
                    }

                    // Latest Updates: ALWAYS keep exact chronological order (never shuffled)
                    val updatesDef = async(Dispatchers.IO) {
                        try {
                            val activeUpdates = try {
                                db.from("updates").select {
                                    filter { eq("is_active", true) }
                                    order("updated_at", Order.DESCENDING)
                                    limit(20)
                                }.decodeList<UpdateItem>()
                            } catch (_: Exception) {
                                emptyList()
                            }
                            if (activeUpdates.isNotEmpty()) {
                                activeUpdates
                            } else {
                                db.from("updates").select {
                                    order("created_at", Order.DESCENDING)
                                    limit(20)
                                }.decodeList<UpdateItem>()
                            }
                        } catch (_: Exception) {
                            if (isRefresh) _state.value.updates else emptyList()
                        }
                    }

                    val recentEpisodesDef = async(Dispatchers.IO) {
                        try {
                            db.from("episodes").select(
                                Columns.raw("id, episode_number, created_at, seasons!inner(season_number, movie_id, movies!inner(id, title, slug, poster_url, type, is_adult))")
                            ) {
                                order("created_at", Order.DESCENDING)
                                limit(25)
                            }.decodeList<RecentEpisodeRow>()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    val upcomingDef = async(Dispatchers.IO) {
                        try {
                            db.from("upcoming").select {
                                order("release_date", Order.ASCENDING)
                                limit(12)
                            }.decodeList<Upcoming>()
                        } catch (_: Exception) {
                            if (isRefresh) _state.value.upcoming else emptyList()
                        }
                    }

                    val categoriesDef = async(Dispatchers.IO) {
                        try {
                            db.from("categories").select {
                                order("name", Order.ASCENDING)
                            }.decodeList<Category>()
                        } catch (_: Exception) {
                            if (isRefresh) _state.value.categories else emptyList()
                        }
                    }

                    // Fetch fresh TMDB Movies, Series & Anime in parallel
                    val tmdbMixDef = async(Dispatchers.IO) {
                        try {
                            TmdbRepository.fetchFreshHomeMix()
                        } catch (_: Exception) {
                            Triple(emptyList(), emptyList(), emptyList())
                        }
                    }

                    val hideNsfw = hideNsfwDef.await()
                    val tg = telegramDef.await()
                    val highlights = highlightsDef.await()
                    val catalog = catalogDef.await()
                    val (tmdbMovies, tmdbSeries, tmdbAnime) = tmdbMixDef.await()

                    fun List<Movie>.filterNsfw() = if (hideNsfw) filter { it.isAdult != true } else this

                    val allLocal = (highlights + catalog).distinctBy { it.id }.filterNsfw()
                    val pinnedList = allLocal.filter { it.isPinned == true || it.adminNote == "pinned" }

                    val dbLatest = catalog.filterNsfw()
                    val dbMovies = allLocal.filter { it.type.equals("movie", ignoreCase = true) }
                    val dbAnime = allLocal.filter { it.type.equals("anime", ignoreCase = true) }
                    val dbSeries = allLocal.filter { it.type.equals("series", ignoreCase = true) }
                    val dbTrending = allLocal
                        .filter { it.isTrending == true }
                        .sortedBy { it.trendingRank ?: 999 }
                    val dbRunning = allLocal.filter { it.isRunning == true }

                    val allTmdbCombined = (tmdbMovies + tmdbSeries + tmdbAnime).filterNsfw()

                    // Merge Pinned first, then shuffle all non-pinned Supabase + TMDB items
                    val rawLatest = mergePinnedAndShuffleFresh(
                        localList = dbLatest,
                        pinnedPool = pinnedList,
                        tmdbPool = allTmdbCombined.take(16),
                        typeFilter = null,
                        maxCount = 18
                    )

                    val rawMovies = mergePinnedAndShuffleFresh(
                        localList = dbMovies,
                        pinnedPool = pinnedList,
                        tmdbPool = tmdbMovies.filterNsfw(),
                        typeFilter = "movie",
                        maxCount = 18
                    )

                    val rawAnime = mergePinnedAndShuffleFresh(
                        localList = dbAnime,
                        pinnedPool = pinnedList,
                        tmdbPool = tmdbAnime.filterNsfw(),
                        typeFilter = "anime",
                        maxCount = 18
                    )

                    val rawSeries = mergePinnedAndShuffleFresh(
                        localList = dbSeries,
                        pinnedPool = pinnedList,
                        tmdbPool = tmdbSeries.filterNsfw(),
                        typeFilter = "series",
                        maxCount = 18
                    )

                    // For Trending / Hero Slider: keep pinned trending first, then shuffle remaining trending + high-backdrop TMDB items
                    val tmdbBannerItems = allTmdbCombined.filter {
                        !it.bannerUrlMobile.isNullOrBlank() || !it.bannerUrlDesktop.isNullOrBlank()
                    }
                    val pinnedTrending = dbTrending.filter { it.isPinned == true || it.adminNote == "pinned" }
                    val unpinnedTrending = dbTrending.filter { !(it.isPinned == true || it.adminNote == "pinned") }
                    val dedupedTmdbTrending = TmdbRepository.deduplicateAgainstLocal(tmdbBannerItems, dbTrending)
                    val rawTrending = (pinnedTrending + (unpinnedTrending + dedupedTmdbTrending.take(8)).shuffled())
                        .distinctBy { it.id }
                        .ifEmpty { rawLatest.take(8) }
                        .take(12)

                    // For Running / Airing: keep pinned first, shuffle rest
                    val pinnedRunning = dbRunning.filter { it.isPinned == true || it.adminNote == "pinned" }
                    val unpinnedRunning = dbRunning.filter { !(it.isPinned == true || it.adminNote == "pinned") }
                    val rawRunning = (pinnedRunning + unpinnedRunning.shuffled())
                        .distinctBy { it.id }
                        .take(16)

                    // Latest Updates: Keep exact chronological order (never shuffle!) + merge live episode additions + enrich missing posters
                    val posterBySlug = allLocal.associate {
                        it.slug.lowercase() to it.posterUrl
                    }
                    val episodeUpdateItems = recentEpisodesDef.await().mapNotNull { it.toUpdateItem(hideNsfw) }
                    val tableUpdateItems = updatesDef.await().map { u ->
                        if (!u.posterUrl.isNullOrBlank()) u
                        else {
                            val cleanSlug = u.slug.trim().removePrefix("/").substringAfterLast("/").lowercase()
                            val fallbackPoster = posterBySlug[cleanSlug]
                            if (fallbackPoster != null) u.copy(posterUrl = fallbackPoster) else u
                        }
                    }

                    val combinedUpdates = (episodeUpdateItems + tableUpdateItems)
                        .sortedByDescending { (it.updatedAt ?: it.createdAt).orEmpty() }

                    val seenContentSlugs = HashSet<String>()
                    val deduplicatedUpdates = buildList {
                        for (item in combinedUpdates) {
                            val cleanSlug = item.slug.trim().removePrefix("/").substringAfterLast("/").lowercase()
                            val key = cleanSlug.ifBlank { item.contentId ?: item.id }
                            if (key.isBlank() || !seenContentSlugs.add(key)) continue
                            add(item)
                            if (size >= 16) break
                        }
                    }

                    val rawUpdates = if (deduplicatedUpdates.size >= 10) {
                        deduplicatedUpdates
                    } else {
                        val supplemental = dbLatest
                            .filter { it.slug.lowercase() !in seenContentSlugs }
                            .take(12 - deduplicatedUpdates.size)
                            .map { m ->
                                UpdateItem(
                                    id = "auto-upd-${m.id}",
                                    contentId = m.id,
                                    title = m.title,
                                    slug = m.slug,
                                    posterUrl = m.posterUrl,
                                    contentType = m.type,
                                    updateType = if (m.lastEpisode != null && m.lastEpisode > 0) "episode" else m.type,
                                    seasonNumber = if (m.type != "movie") 1 else null,
                                    episodeNumber = m.lastEpisode,
                                    isActive = true,
                                    createdAt = m.createdAt,
                                    updatedAt = m.createdAt
                                )
                            }
                        (deduplicatedUpdates + supplemental).sortedByDescending { (it.updatedAt ?: it.createdAt).orEmpty() }
                    }

                    val rawUpcoming = upcomingDef.await()
                    val rawCategories = categoriesDef.await()

                    val allEmpty = rawTrending.isEmpty() && rawUpdates.isEmpty() &&
                        rawMovies.isEmpty() && rawAnime.isEmpty() && rawSeries.isEmpty() && rawLatest.isEmpty()

                    HomeState(
                        trending = rawTrending,
                        updates = rawUpdates,
                        upcoming = rawUpcoming,
                        categories = rawCategories,
                        latestAdditions = rawLatest,
                        recentMovies = rawMovies,
                        recentAnime = rawAnime,
                        recentSeries = rawSeries,
                        running = rawRunning,
                        telegramUrl = if (tg?.isActive == true && !tg.telegramUrl.isNullOrBlank()) tg.telegramUrl else null,
                        telegramText = tg?.buttonText?.takeIf { it.isNotBlank() } ?: "Join Telegram",
                        hideNsfw = hideNsfw,
                        isLoading = false,
                        isRefreshing = false,
                        error = if (allEmpty) "Couldn't load content. Check your connection and pull down to retry." else null,
                    )
                }

                _state.value = newState
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    isRefreshing = false,
                    error = UserFacingError.from(e, "Couldn't refresh content. Please try again."),
                )
            }
        }
    }
}
