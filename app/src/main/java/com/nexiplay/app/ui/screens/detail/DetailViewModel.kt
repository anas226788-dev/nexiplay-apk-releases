package com.nexiplay.app.ui.screens.detail

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class DetailState(
    val movie: Movie? = null,
    val streamingRow: StreamingRow? = null,
    val downloads: List<DownloadLink> = emptyList(),
    val seasons: List<Season> = emptyList(),
    val episodes: Map<String, List<Episode>> = emptyMap(),
    val episodeDownloads: Map<String, List<EpisodeDownloadLink>> = emptyMap(),
    val categories: List<Category> = emptyList(),
    val relatedMovies: List<Movie> = emptyList(),
    val isInWatchlist: Boolean = false,
    val isLoading: Boolean = true,
    val error: String? = null,
)

class DetailViewModel : ViewModel() {
    private val _state = MutableStateFlow(DetailState())
    val state = _state.asStateFlow()

    fun loadContent(type: String, slug: String) {
        viewModelScope.launch {
            _state.value = DetailState(isLoading = true)
            try {
                // Refresh server toggles & ad settings from database
                try {
                    com.nexiplay.app.data.util.AdManager.loadConfig(SupabaseClient.main)
                } catch (_: Exception) {}

                // Fetch movie
                val movie = SupabaseClient.main.from("movies")
                    .select { filter { eq("slug", slug); eq("type", type) } }
                    .decodeSingleOrNull<Movie>()

                if (movie == null) {
                    _state.value = DetailState(isLoading = false, error = "Content not found")
                    return@launch
                }

                // Fetch streaming info
                val streamingRow = SupabaseClient.main.from("streaming")
                    .select { filter { eq("movie_id", movie.id) } }
                    .decodeSingleOrNull<StreamingRow>()

                // Fetch downloads
                val downloads = SupabaseClient.main.from("download_links")
                    .select { filter { eq("movie_id", movie.id) } }
                    .decodeList<DownloadLink>()

                // Fetch seasons (for series/anime)
                var seasons = emptyList<Season>()
                val episodesMap = mutableMapOf<String, List<Episode>>()
                val epDownloadsMap = mutableMapOf<String, List<EpisodeDownloadLink>>()

                if (type != "movie") {
                    seasons = SupabaseClient.main.from("seasons")
                        .select { filter { eq("movie_id", movie.id) }
                            order("season_number", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                        }
                        .decodeList<Season>()

                    // Fetch episodes for each season
                    for (season in seasons) {
                        val eps = SupabaseClient.main.from("episodes")
                            .select { filter { eq("season_id", season.id) }
                                order("episode_number", io.github.jan.supabase.postgrest.query.Order.ASCENDING)
                            }
                            .decodeList<Episode>()
                        episodesMap[season.id] = eps

                        // Fetch episode download links
                        val epIds = eps.map { it.id }
                        if (epIds.isNotEmpty()) {
                            val epDls = SupabaseClient.main.from("episode_download_links")
                                .select { filter { isIn("episode_id", epIds) } }
                                .decodeList<EpisodeDownloadLink>()
                            
                            epDls.groupBy { it.episodeId!! }.forEach { (epId, links) ->
                                epDownloadsMap[epId] = links
                            }
                        }
                    }
                }

                // Check watchlist
                val user = SupabaseClient.main.auth.currentUserOrNull()
                var inWatchlist = false
                if (user != null) {
                    try {
                        val wl = SupabaseClient.main.from("watchlist")
                            .select {
                                filter { eq("user_id", user.id); eq("movie_id", movie.id) }
                                limit(1)
                            }
                            .decodeList<Map<String, String>>()
                        inWatchlist = wl.isNotEmpty()
                    } catch (_: Exception) { }
                }

                val related = SupabaseClient.main.from("movies").select {
                    filter { 
                        eq("type", type)
                        neq("id", movie.id)
                    }
                    order("created_at", order = io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    limit(8)
                }.decodeList<Movie>()

                _state.value = DetailState(
                    movie = movie,
                    streamingRow = streamingRow,
                    downloads = downloads,
                    seasons = seasons,
                    episodes = episodesMap,
                    episodeDownloads = epDownloadsMap,
                    relatedMovies = related,
                    isInWatchlist = inWatchlist,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _state.value = DetailState(isLoading = false, error = e.message)
            }
        }
    }

    fun toggleWatchlist() {
        val movie = _state.value.movie ?: return
        viewModelScope.launch {
            val user = SupabaseClient.main.auth.currentUserOrNull() ?: return@launch
            try {
                if (_state.value.isInWatchlist) {
                    SupabaseClient.main.from("watchlist")
                        .delete { filter { eq("user_id", user.id); eq("movie_id", movie.id) } }
                    _state.value = _state.value.copy(isInWatchlist = false)
                } else {
                    SupabaseClient.main.from("watchlist")
                        .insert(mapOf("user_id" to user.id, "movie_id" to movie.id))
                    _state.value = _state.value.copy(isInWatchlist = true)
                }
            } catch (_: Exception) { }
        }
    }
}
