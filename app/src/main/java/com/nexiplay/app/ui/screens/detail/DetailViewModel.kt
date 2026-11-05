package com.nexiplay.app.ui.screens.detail

import android.content.Context
import android.widget.Toast
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Category
import com.nexiplay.app.data.model.DownloadLink
import com.nexiplay.app.data.model.Episode
import com.nexiplay.app.data.model.EpisodeDownloadLink
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.MovieScreenshot
import com.nexiplay.app.data.model.Season
import com.nexiplay.app.data.model.StreamingRow
import com.nexiplay.app.data.repository.TmdbRepository
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject

@Serializable
private data class MovieCategoryJoin(
    @SerialName("category_id") val categoryId: String,
    val categories: Category? = null,
)

@Serializable
private data class WatchlistCheckRow(
    @SerialName("id") val id: String? = null,
    @SerialName("movie_id") val movieId: String? = null,
)

data class DetailUiState(
    val isLoading: Boolean = true,
    val movie: Movie? = null,
    val resolvedType: String = "movie",
    val streamingRow: StreamingRow? = null,
    val categories: List<Category> = emptyList(),
    val downloads: List<DownloadLink> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val episodes: Map<String, List<Episode>> = emptyMap(), // seasonId -> episodes
    val episodeDownloads: Map<String, List<EpisodeDownloadLink>> = emptyMap(), // episodeId -> links
    val screenshots: List<MovieScreenshot> = emptyList(),
    val relatedMovies: List<Movie> = emptyList(),
    val isInWatchlist: Boolean = false,
    val watchlistBusy: Boolean = false,
    val ratingScore: String = "8.5",
    val duration: String = "HD",
    val studioName: String = "Original",
    val statusText: String = "Released",
    val tagline: String? = null,
    val error: String? = null,
)

class DetailViewModel : ViewModel() {

    companion object {
        @Volatile
        private var pendingWatchSeason: Int? = null

        @Volatile
        private var pendingWatchEpisode: Int? = null

        private val detailCache = ConcurrentHashMap<String, DetailUiState>()
        private val WHITESPACE_REGEX = Regex("""\s+""")

        private fun cacheKey(slug: String): String = slug.trim().lowercase()

        fun setPendingWatchTarget(seasonNumber: Int?, episodeNumber: Int?) {
            pendingWatchSeason = seasonNumber
            pendingWatchEpisode = episodeNumber
        }

        fun consumePendingWatchTarget(): Pair<Int?, Int?> {
            val s = pendingWatchSeason
            val e = pendingWatchEpisode
            pendingWatchSeason = null
            pendingWatchEpisode = null
            return Pair(s, e)
        }
    }

    private val _state = MutableStateFlow(DetailUiState())
    val state: StateFlow<DetailUiState> = _state.asStateFlow()

    private suspend fun checkIsInWatchlist(movieId: String): Boolean {
        val user = SupabaseClient.main.auth.currentUserOrNull() ?: return false
        val inUserWl = try {
            SupabaseClient.main.from("user_watchlist")
                .select(Columns.list("movie_id")) {
                    filter {
                        eq("user_id", user.id)
                        eq("movie_id", movieId)
                    }
                }
                .decodeList<WatchlistCheckRow>()
                .isNotEmpty()
        } catch (_: Exception) {
            false
        }
        if (inUserWl) return true

        return try {
            SupabaseClient.main.from("watchlist")
                .select(Columns.list("movie_id")) {
                    filter {
                        eq("user_id", user.id)
                        eq("movie_id", movieId)
                    }
                }
                .decodeList<WatchlistCheckRow>()
                .isNotEmpty()
        } catch (_: Exception) {
            false
        }
    }

    private fun logViewingHistory(movieId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val user = SupabaseClient.main.auth.currentUserOrNull()
                if (user != null) {
                    SupabaseClient.main.from("viewing_history").delete {
                        filter {
                            eq("user_id", user.id)
                            eq("movie_id", movieId)
                        }
                    }
                    SupabaseClient.main.from("viewing_history").insert(
                        buildJsonObject {
                            put("user_id", JsonPrimitive(user.id))
                            put("movie_id", JsonPrimitive(movieId))
                        }
                    )
                }
            } catch (_: Exception) {
                // Ignore history logging errors
            }
        }
    }

    fun loadContent(type: String, slug: String, forceRefresh: Boolean = false) {
        val key = cacheKey(slug)
        if (!forceRefresh) {
            val cached = detailCache[key]
            if (cached != null && cached.movie != null && !cached.isLoading) {
                _state.value = cached
                logViewingHistory(cached.movie.id)
                return
            }
        }

        viewModelScope.launch {
            _state.value = DetailUiState(isLoading = true)
            try {
                val newState = withContext(Dispatchers.IO) {
                    val dbType = when (type.lowercase().trim()) {
                        "anime", "animes" -> "anime"
                        "series", "tv", "show", "shows" -> "series"
                        else -> "movie"
                    }

                    // 0. Fast-path for virtual TMDB items (`tmdb-12345`)
                    if (slug.startsWith("tmdb-")) {
                        val tmdbBundle = TmdbRepository.fetchTmdbDetailBundle(dbType, slug)
                        if (tmdbBundle != null) {
                            val isInWl = checkIsInWatchlist(tmdbBundle.movie.id)
                            val actualType = tmdbBundle.movie.type.lowercase().let {
                                if (it == "anime" || it == "series") it else "movie"
                            }
                            return@withContext DetailUiState(
                                isLoading = false,
                                movie = tmdbBundle.movie,
                                resolvedType = actualType,
                                categories = tmdbBundle.categories,
                                seasons = tmdbBundle.seasons,
                                episodes = tmdbBundle.episodes,
                                screenshots = tmdbBundle.screenshots,
                                relatedMovies = tmdbBundle.relatedMovies,
                                isInWatchlist = isInWl,
                                ratingScore = tmdbBundle.ratingScore ?: "8.5",
                                duration = tmdbBundle.duration ?: tmdbBundle.movie.format ?: "HD",
                                studioName = tmdbBundle.studioName ?: "Original",
                                statusText = if (tmdbBundle.movie.isRunning == true) "Running" else (tmdbBundle.status ?: "Released"),
                                tagline = tmdbBundle.tagline
                            )
                        }
                    }

                    // 1. Fetch movie by slug from Supabase (flexible on `type` in case plural or mismatched route type was used)
                    val foundList = try {
                        SupabaseClient.main.from("movies")
                            .select {
                                filter { eq("slug", slug) }
                                limit(5)
                            }
                            .decodeList<Movie>()
                    } catch (_: Exception) {
                        emptyList()
                    }
                    val foundMovie = foundList.firstOrNull { it.type.equals(dbType, ignoreCase = true) }
                        ?: foundList.firstOrNull()

                    // Fallback to TMDB if slug was not found in local Supabase DB
                    if (foundMovie == null) {
                        val tmdbBundle = TmdbRepository.fetchTmdbDetailBundle(dbType, slug)
                        if (tmdbBundle != null) {
                            val isInWl = checkIsInWatchlist(tmdbBundle.movie.id)
                            val actualType = tmdbBundle.movie.type.lowercase().let {
                                if (it == "anime" || it == "series") it else "movie"
                            }
                            return@withContext DetailUiState(
                                isLoading = false,
                                movie = tmdbBundle.movie,
                                resolvedType = actualType,
                                categories = tmdbBundle.categories,
                                seasons = tmdbBundle.seasons,
                                episodes = tmdbBundle.episodes,
                                screenshots = tmdbBundle.screenshots,
                                relatedMovies = tmdbBundle.relatedMovies,
                                isInWatchlist = isInWl,
                                ratingScore = tmdbBundle.ratingScore ?: "8.5",
                                duration = tmdbBundle.duration ?: tmdbBundle.movie.format ?: "HD",
                                studioName = tmdbBundle.studioName ?: "Original",
                                statusText = tmdbBundle.status ?: "Released",
                                tagline = tmdbBundle.tagline
                            )
                        } else {
                            return@withContext DetailUiState(
                                isLoading = false,
                                error = "Content not found."
                            )
                        }
                    }

                    val actualType = when (foundMovie.type.lowercase().trim()) {
                        "anime", "animes" -> "anime"
                        "series", "tv", "show", "shows" -> "series"
                        else -> dbType
                    }

                    // 2. Run local DB queries + parallel TMDB hydration on Dispatchers.IO
                    val categoriesDeferred = async {
                        try {
                            val joins = SupabaseClient.main.from("movie_categories")
                                .select(Columns.raw("category_id, categories(id, name, slug)")) {
                                    filter { eq("movie_id", foundMovie.id) }
                                }
                                .decodeList<MovieCategoryJoin>()
                            joins.mapNotNull { it.categories }
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    val downloadsDeferred = async {
                        try {
                            SupabaseClient.main.from("download_links")
                                .select { filter { eq("movie_id", foundMovie.id) } }
                                .decodeList<DownloadLink>()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    val screenshotsDeferred = async {
                        try {
                            SupabaseClient.main.from("movie_screenshots")
                                .select {
                                    filter { eq("movie_id", foundMovie.id) }
                                    order("created_at", order = Order.ASCENDING)
                                }
                                .decodeList<MovieScreenshot>()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    val streamingDeferred = async {
                        try {
                            SupabaseClient.main.from("streaming")
                                .select {
                                    filter { eq("movie_id", foundMovie.id) }
                                    limit(1)
                                }
                                .decodeList<StreamingRow>()
                                .firstOrNull()
                        } catch (_: Exception) {
                            null
                        }
                    }

                    val seasonsDeferred = async {
                        if (actualType != "movie") {
                            try {
                                val seasonsList = SupabaseClient.main.from("seasons")
                                    .select {
                                        filter { eq("movie_id", foundMovie.id) }
                                        order("season_number", order = Order.ASCENDING)
                                    }
                                    .decodeList<Season>()

                                val seasonIds = seasonsList.map { it.id }
                                val allEpisodes = if (seasonIds.isNotEmpty()) {
                                    seasonIds.chunked(40).map { chunk ->
                                        async {
                                            try {
                                                SupabaseClient.main.from("episodes")
                                                    .select {
                                                        filter { isIn("season_id", chunk) }
                                                        order("episode_number", order = Order.ASCENDING)
                                                    }
                                                    .decodeList<Episode>()
                                            } catch (_: Exception) {
                                                emptyList()
                                            }
                                        }
                                    }.awaitAll().flatten().sortedBy { it.episodeNumber }
                                } else emptyList()

                                val episodesMap = allEpisodes.groupBy { it.seasonId }
                                val epIds = allEpisodes.map { it.id }

                                val allEpDls = if (epIds.isNotEmpty()) {
                                    epIds.chunked(40).map { chunk ->
                                        async {
                                            try {
                                                SupabaseClient.main.from("episode_download_links")
                                                    .select { filter { isIn("episode_id", chunk) } }
                                                    .decodeList<EpisodeDownloadLink>()
                                            } catch (_: Exception) {
                                                emptyList()
                                            }
                                        }
                                    }.awaitAll().flatten()
                                } else emptyList()

                                val epDownloadsMap = allEpDls.filter { it.episodeId != null }.groupBy { it.episodeId!! }
                                Triple(seasonsList, episodesMap, epDownloadsMap)
                            } catch (_: Exception) {
                                Triple(emptyList(), emptyMap(), emptyMap())
                            }
                        } else {
                            Triple(emptyList(), emptyMap(), emptyMap())
                        }
                    }

                    val relatedDeferred = async {
                        try {
                            SupabaseClient.main.from("movies")
                                .select {
                                    filter {
                                        eq("type", actualType)
                                        neq("id", foundMovie.id)
                                        eq("is_adult", false)
                                    }
                                    order("created_at", order = Order.DESCENDING)
                                    limit(8)
                                }
                                .decodeList<Movie>()
                        } catch (_: Exception) {
                            emptyList()
                        }
                    }

                    val watchlistDeferred = async {
                        checkIsInWatchlist(foundMovie.id)
                    }

                    val tmdbEnrichDeferred = async {
                        try {
                            TmdbRepository.fetchTmdbDetailBundle(
                                type = actualType,
                                rawSlug = foundMovie.slug,
                                explicitTmdbId = foundMovie.tmdbId,
                                fallbackTitle = foundMovie.title
                            )
                        } catch (_: Exception) {
                            null
                        }
                    }

                    val localCategories = categoriesDeferred.await()
                    val downloads = downloadsDeferred.await()
                    val localScreenshots = screenshotsDeferred.await()
                    val streamingRow = streamingDeferred.await()
                    val (localSeasons, localEpisodes, epDownloads) = seasonsDeferred.await()
                    val localRelated = relatedDeferred.await()
                    val isInWatchlist = watchlistDeferred.await()
                    val tmdbBundle = tmdbEnrichDeferred.await()

                    // Merge local movie with TMDB enrichment so missing tmdbId, trailer, cast, banner, or seasons are always populated
                    val rawDesc = foundMovie.description?.trim().orEmpty()
                    val isGenericDesc = rawDesc.isEmpty() ||
                        rawDesc.startsWith("Watch ", ignoreCase = true) ||
                        rawDesc.startsWith("Download ", ignoreCase = true) ||
                        rawDesc.startsWith("Stream ", ignoreCase = true) ||
                        rawDesc.contains("~ Nexiplay", ignoreCase = true) ||
                        rawDesc.contains("Multi Audio [", ignoreCase = true)

                    val tmdbYear = tmdbBundle?.movie?.releaseYear
                    val dbYear = foundMovie.releaseYear
                    val resolvedYear = when {
                        tmdbYear != null && (dbYear == null || (dbYear >= 2025 && tmdbYear < dbYear)) -> tmdbYear
                        else -> dbYear ?: tmdbYear
                    }

                    val cleanLanguage = foundMovie.language
                        ?.trim()
                        ?.removePrefix("[")
                        ?.removeSuffix("]")
                        ?.replace("-", ", ")
                        ?.replace(WHITESPACE_REGEX, " ")
                        ?.trim()
                        ?.takeIf { it.isNotBlank() }

                    val tmdbStatus = tmdbBundle?.status?.trim().orEmpty()
                    val isTmdbFinished = tmdbStatus.equals("Ended", ignoreCase = true) ||
                        tmdbStatus.equals("Canceled", ignoreCase = true) ||
                        tmdbStatus.equals("Released", ignoreCase = true)

                    val trulyRunning = if (isTmdbFinished) {
                        false
                    } else {
                        foundMovie.isRunning == true ||
                            tmdbStatus.equals("Returning Series", ignoreCase = true) ||
                            tmdbStatus.equals("In Production", ignoreCase = true)
                    }

                    val statusDisplay = when {
                        isTmdbFinished -> if (actualType == "movie") "Released" else "Completed"
                        trulyRunning -> "Ongoing"
                        !foundMovie.runningStatus.isNullOrBlank() -> foundMovie.runningStatus!!
                        tmdbStatus.isNotBlank() -> tmdbStatus
                        else -> if (actualType == "movie") "Released" else "Completed"
                    }

                    val mergedMovie = foundMovie.copy(
                        type = actualType,
                        tmdbId = foundMovie.tmdbId?.takeIf { it.isNotBlank() } ?: tmdbBundle?.movie?.tmdbId,
                        imdbId = foundMovie.imdbId?.takeIf { it.isNotBlank() } ?: tmdbBundle?.movie?.imdbId,
                        posterUrl = foundMovie.posterUrl?.takeIf { it.isNotBlank() } ?: tmdbBundle?.movie?.posterUrl,
                        bannerUrlDesktop = foundMovie.bannerUrlDesktop?.takeIf { it.isNotBlank() }
                            ?: foundMovie.bannerUrlMobile?.takeIf { it.isNotBlank() }
                            ?: tmdbBundle?.movie?.bannerUrlDesktop,
                        bannerUrlMobile = foundMovie.bannerUrlMobile?.takeIf { it.isNotBlank() }
                            ?: foundMovie.bannerUrlDesktop?.takeIf { it.isNotBlank() }
                            ?: tmdbBundle?.movie?.bannerUrlMobile,
                        description = if (!isGenericDesc) foundMovie.description else (tmdbBundle?.movie?.description?.takeIf { it.isNotBlank() } ?: foundMovie.description),
                        castMembers = foundMovie.castMembers?.takeIf { it.isNotBlank() } ?: tmdbBundle?.movie?.castMembers,
                        trailerUrl = foundMovie.trailerUrl?.takeIf { it.isNotBlank() } ?: tmdbBundle?.movie?.trailerUrl,
                        releaseYear = resolvedYear,
                        language = cleanLanguage ?: foundMovie.language,
                        isRunning = trulyRunning,
                        format = foundMovie.format?.takeIf { it.isNotBlank() } ?: tmdbBundle?.movie?.format ?: "HD"
                    )

                    val finalCategories = if (localCategories.isNotEmpty()) localCategories else (tmdbBundle?.categories ?: emptyList())
                    val finalScreenshots = if (localScreenshots.isNotEmpty()) localScreenshots else (tmdbBundle?.screenshots ?: emptyList())

                    val finalSeasons: List<Season>
                    val finalEpisodes: Map<String, List<Episode>>
                    if (actualType != "movie" && localSeasons.isEmpty() && tmdbBundle != null && tmdbBundle.seasons.isNotEmpty()) {
                        finalSeasons = tmdbBundle.seasons
                        finalEpisodes = tmdbBundle.episodes
                    } else {
                        finalSeasons = localSeasons
                        finalEpisodes = localEpisodes
                    }

                    val combinedRelated = buildList {
                        addAll(localRelated)
                        if (tmdbBundle != null) {
                            val existingIds = localRelated.map { it.id }.toSet()
                            val existingTmdbIds = localRelated.mapNotNull { it.tmdbId }.toSet() + listOfNotNull(mergedMovie.tmdbId)
                            for (rel in tmdbBundle.relatedMovies) {
                                if (rel.id !in existingIds && (rel.tmdbId == null || rel.tmdbId !in existingTmdbIds)) {
                                    add(rel)
                                }
                            }
                        }
                    }.take(10)

                    val durationDisplay = when {
                        !tmdbBundle?.duration.isNullOrBlank() && tmdbBundle?.duration != "HD" -> tmdbBundle!!.duration!!
                        !mergedMovie.format.isNullOrBlank() -> mergedMovie.format!!
                        actualType != "movie" -> "24m / ep"
                        else -> "HD"
                    }

                    DetailUiState(
                        isLoading = false,
                        movie = mergedMovie,
                        resolvedType = actualType,
                        streamingRow = streamingRow,
                        categories = finalCategories,
                        downloads = downloads,
                        seasons = finalSeasons,
                        episodes = finalEpisodes,
                        episodeDownloads = epDownloads,
                        screenshots = finalScreenshots,
                        relatedMovies = combinedRelated,
                        isInWatchlist = isInWatchlist,
                        ratingScore = tmdbBundle?.ratingScore ?: "8.5",
                        duration = durationDisplay,
                        studioName = tmdbBundle?.studioName ?: mergedMovie.source ?: "Original",
                        statusText = statusDisplay,
                        tagline = tmdbBundle?.tagline
                    )
                }

                _state.value = newState
                if (newState.movie != null) {
                    if (detailCache.size > 40) {
                        detailCache.clear()
                    }
                    detailCache[key] = newState
                    detailCache[cacheKey(newState.movie.slug)] = newState
                    logViewingHistory(newState.movie.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                _state.value = DetailUiState(
                    isLoading = false,
                    error = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't load content details. Please try again."),
                )
            }
        }
    }

    fun toggleWatchlist(
        context: Context? = null,
        onRequireLogin: (() -> Unit)? = null
    ) {
        val currentMovie = _state.value.movie ?: return
        if (_state.value.watchlistBusy) return

        val user = SupabaseClient.main.auth.currentUserOrNull()
        if (user == null) {
            context?.let {
                Toast.makeText(it, "Please sign in to save to your watchlist", Toast.LENGTH_SHORT).show()
            }
            onRequireLogin?.invoke()
            return
        }

        val targetState = !_state.value.isInWatchlist
        _state.value = _state.value.copy(isInWatchlist = targetState, watchlistBusy = true)

        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    if (targetState) {
                        val payload = buildJsonObject {
                            put("user_id", JsonPrimitive(user.id))
                            put("movie_id", JsonPrimitive(currentMovie.id))
                        }
                        try {
                            SupabaseClient.main.from("user_watchlist").insert(payload)
                        } catch (_: Exception) {
                            SupabaseClient.main.from("watchlist").insert(payload)
                        }
                    } else {
                        try {
                            SupabaseClient.main.from("user_watchlist").delete {
                                filter {
                                    eq("user_id", user.id)
                                    eq("movie_id", currentMovie.id)
                                }
                            }
                        } catch (_: Exception) {}
                        try {
                            SupabaseClient.main.from("watchlist").delete {
                                filter {
                                    eq("user_id", user.id)
                                    eq("movie_id", currentMovie.id)
                                }
                            }
                        } catch (_: Exception) {}
                    }
                }
                detailCache[cacheKey(currentMovie.slug)] = _state.value.copy(isInWatchlist = targetState, watchlistBusy = false)
                context?.let {
                    Toast.makeText(
                        it,
                        if (targetState) "Added to My List" else "Removed from My List",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            } catch (e: Exception) {
                e.printStackTrace()
                // Revert optimistic state on hard failure
                _state.value = _state.value.copy(isInWatchlist = !targetState)
                context?.let {
                    Toast.makeText(it, "Couldn't update watchlist. Try again.", Toast.LENGTH_SHORT).show()
                }
            } finally {
                _state.value = _state.value.copy(watchlistBusy = false)
            }
        }
    }
}
