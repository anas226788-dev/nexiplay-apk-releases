package com.nexiplay.app.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Category
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.repository.TmdbRepository
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class MovieCategoryLinkRow(
    @SerialName("movie_id") val movieId: String = "",
    @SerialName("category_id") val categoryId: String = ""
)

data class BrowseState(
    val items: List<Movie> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedTab: Int = 0, // 0=All, 1=Movies, 2=Anime, 3=Series, 4=Running, 5=Pinned
    val selectedCategorySlug: String? = null,
    val sortBy: String = "latest", // latest | trending | year | az
    val isLoading: Boolean = true,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class BrowseViewModel : ViewModel() {
    companion object {
        var pendingTab: Int? = null
        var pendingCategorySlug: String? = null

        private val _filterSignal = MutableStateFlow(0)
        val filterSignal = _filterSignal.asStateFlow()

        private val _activeTabFlow = MutableStateFlow(0)
        val activeTabFlow = _activeTabFlow.asStateFlow()

        fun navigateWithFilter(tab: Int? = null, categorySlug: String? = null) {
            pendingTab = tab
            pendingCategorySlug = categorySlug
            if (tab != null) {
                _activeTabFlow.value = tab
            }
            _filterSignal.value += 1
        }
    }

    private val _state = MutableStateFlow(BrowseState())
    val state = _state.asStateFlow()

    private val pageSize = 24
    private var tmdbRandomOffset = (0..6).random()

    private val columns = Columns.raw(
        "id, title, slug, poster_url, type, release_year, is_trending, trending_badge, is_running, is_adult, admin_note, created_at, tmdb_id"
    )

    init {
        loadCategories()
        consumePendingFilterOrLoad()
    }

    fun consumePendingFilterOrLoad() {
        val pTab = pendingTab
        val pCat = pendingCategorySlug
        if (pTab != null || pCat != null) {
            pendingTab = null
            pendingCategorySlug = null
            val resolvedTab = pTab ?: 0
            _activeTabFlow.value = resolvedTab
            tmdbRandomOffset = (0..6).random()
            _state.value = _state.value.copy(
                selectedTab = resolvedTab,
                selectedCategorySlug = pCat,
                items = emptyList(),
                page = 0,
                hasMore = true
            )
            loadContent()
        } else if (_state.value.items.isEmpty()) {
            _activeTabFlow.value = _state.value.selectedTab
            loadContent()
        }
    }

    private fun loadCategories() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cats = SupabaseClient.main.from("categories").select {
                    order("name", Order.ASCENDING)
                }.decodeList<Category>()
                _state.value = _state.value.copy(categories = cats)
            } catch (_: Exception) {}
        }
    }

    fun selectTab(index: Int) {
        _activeTabFlow.value = index
        if (index == _state.value.selectedTab && _state.value.items.isNotEmpty()) return
        tmdbRandomOffset = (0..6).random()
        _state.value = _state.value.copy(
            selectedTab = index,
            items = emptyList(),
            page = 0,
            hasMore = true,
            error = null
        )
        loadContent()
    }

    fun selectCategory(slug: String?) {
        val nextSlug = if (_state.value.selectedCategorySlug == slug) null else slug
        tmdbRandomOffset = (0..4).random()
        _state.value = _state.value.copy(
            selectedCategorySlug = nextSlug,
            items = emptyList(),
            page = 0,
            hasMore = true,
            error = null
        )
        loadContent()
    }

    fun selectSort(sort: String) {
        if (sort == _state.value.sortBy) return
        _state.value = _state.value.copy(
            sortBy = sort,
            items = emptyList(),
            page = 0,
            hasMore = true,
            error = null
        )
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tab = _state.value.selectedTab
                val catSlug = _state.value.selectedCategorySlug
                val sort = _state.value.sortBy
                val currentPage = _state.value.page
                val from = currentPage * pageSize
                val to = from + pageSize - 1

                val result = withContext(Dispatchers.IO) {
                    var allowedMovieIds: List<String>? = null
                    if (!catSlug.isNullOrBlank()) {
                        val cat = _state.value.categories.find { it.slug.equals(catSlug, ignoreCase = true) }
                            ?: SupabaseClient.main.from("categories").select {
                                filter { eq("slug", catSlug) }
                                limit(1)
                            }.decodeList<Category>().firstOrNull()

                        if (cat != null && cat.id.isNotBlank()) {
                            val links = SupabaseClient.main.from("movie_categories")
                                .select(Columns.raw("movie_id, category_id")) {
                                    filter { eq("category_id", cat.id) }
                                    limit(300)
                                }.decodeList<MovieCategoryLinkRow>()
                            allowedMovieIds = links.map { it.movieId }.filter { it.isNotBlank() }.distinct()
                        }
                    }

                    // 1. Fetch Pinned items on page 0 (for All, Movies, Anime, Series, Pinned)
                    val pinnedDef = async {
                        if (currentPage == 0 && tab in listOf(0, 1, 2, 3, 5) && allowedMovieIds == null) {
                            try {
                                SupabaseClient.main.from("movies").select(columns) {
                                    filter {
                                        eq("admin_note", "pinned")
                                        when (tab) {
                                            1 -> eq("type", "movie")
                                            2 -> eq("type", "anime")
                                            3 -> eq("type", "series")
                                        }
                                    }
                                    order("created_at", Order.DESCENDING)
                                    limit(12)
                                }.decodeList<Movie>().map { it.copy(isPinned = true) }
                            } catch (_: Exception) {
                                emptyList()
                            }
                        } else emptyList()
                    }

                    // 2. Fetch local database page
                    val localDef = async {
                        if (allowedMovieIds != null && allowedMovieIds.isEmpty()) {
                            emptyList()
                        } else {
                            try {
                                SupabaseClient.main.from("movies").select(columns) {
                                    filter {
                                        when (tab) {
                                            1 -> eq("type", "movie")
                                            2 -> eq("type", "anime")
                                            3 -> eq("type", "series")
                                            4 -> eq("is_running", true)
                                            5 -> eq("admin_note", "pinned")
                                        }
                                        if (allowedMovieIds != null) {
                                            isIn("id", allowedMovieIds)
                                        }
                                    }
                                    when (sort) {
                                        "trending" -> {
                                            order("is_trending", Order.DESCENDING)
                                            order("created_at", Order.DESCENDING)
                                        }
                                        "year" -> {
                                            order("release_year", Order.DESCENDING)
                                            order("created_at", Order.DESCENDING)
                                        }
                                        "az" -> order("title", Order.ASCENDING)
                                        else -> order("created_at", Order.DESCENDING)
                                    }
                                    range(from.toLong(), to.toLong())
                                }.decodeList<Movie>().map { m ->
                                    if (m.adminNote == "pinned" && m.isPinned != true) m.copy(isPinned = true) else m
                                }
                            } catch (_: Exception) {
                                emptyList()
                            }
                        }
                    }

                    // 3. Fetch TMDB On-Demand items (matching website `/movies`, `/series`, `/anime`)
                    val tmdbDef = async {
                        if (tab in listOf(0, 1, 2, 3)) {
                            val tmdbPage = (currentPage + 1 + tmdbRandomOffset).coerceIn(1, 400)
                            try {
                                when (tab) {
                                    1 -> TmdbRepository.fetchTmdbMovies(tmdbPage, catSlug)
                                    2 -> TmdbRepository.fetchTmdbAnime(tmdbPage)
                                    3 -> TmdbRepository.fetchTmdbSeries(tmdbPage, catSlug)
                                    else -> {
                                        val m = TmdbRepository.fetchTmdbMovies(tmdbPage, catSlug)
                                        val a = TmdbRepository.fetchTmdbAnime(tmdbPage)
                                        val s = TmdbRepository.fetchTmdbSeries(tmdbPage, catSlug)
                                        (m.take(8) + a.take(8) + s.take(8)).shuffled()
                                    }
                                }
                            } catch (_: Exception) {
                                emptyList()
                            }
                        } else emptyList()
                    }

                    val pinned = pinnedDef.await()
                    val local = localDef.await()
                    val tmdbRaw = tmdbDef.await()

                    val pinnedIds = pinned.map { it.id }.toSet()
                    val localPinned = local.filter { it.isPinned == true || it.adminNote == "pinned" }
                    val allPinned = (pinned + localPinned).distinctBy { it.id }
                    val allPinnedIds = allPinned.map { it.id }.toSet()

                    val localUnpinned = local.filter { it.id !in allPinnedIds && it.id !in pinnedIds }
                    val existingItems = _state.value.items + allPinned + localUnpinned
                    val freshTmdb = TmdbRepository.deduplicateAgainstLocal(tmdbRaw, existingItems)

                    // Keep Pinned items fixed at top of page 0; shuffle non-pinned + TMDB when sort is "latest"
                    val combinedUnpinned = if (sort == "az") {
                        (localUnpinned + freshTmdb).sortedBy { it.title.lowercase() }
                    } else if (sort == "year") {
                        (localUnpinned + freshTmdb).sortedByDescending { it.releaseYear ?: 0 }
                    } else {
                        (localUnpinned.shuffled() + freshTmdb.shuffled()).shuffled()
                    }

                    if (currentPage == 0) {
                        (allPinned + combinedUnpinned).distinctBy { it.id }
                    } else {
                        combinedUnpinned.distinctBy { it.id }
                    }
                }

                val canLoadMore = if (_state.value.selectedTab in listOf(0, 1, 2, 3)) {
                    result.isNotEmpty()
                } else {
                    result.size >= pageSize
                }

                _state.value = _state.value.copy(
                    items = if (currentPage == 0) result else (_state.value.items + result).distinctBy { it.id },
                    isLoading = false,
                    hasMore = canLoadMore,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't load browsing results. Please try again.")
                )
            }
        }
    }

    fun loadMore() {
        if (!_state.value.hasMore || _state.value.isLoading) return
        _state.value = _state.value.copy(page = _state.value.page + 1)
        loadContent()
    }
}
