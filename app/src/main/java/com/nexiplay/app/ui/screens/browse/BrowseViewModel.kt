package com.nexiplay.app.ui.screens.browse

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Movie
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class BrowseState(
    val items: List<Movie> = emptyList(),
    val selectedTab: Int = 0, // 0=All, 1=Movies, 2=Anime, 3=Series, 4=Running
    val isLoading: Boolean = true,
    val page: Int = 0,
    val hasMore: Boolean = true,
    val error: String? = null,
)

class BrowseViewModel : ViewModel() {
    private val _state = MutableStateFlow(BrowseState())
    val state = _state.asStateFlow()

    private val pageSize = 20
    private val columns = Columns.raw("id, title, slug, poster_url, type, release_year, is_running, is_adult, created_at")

    init { loadContent() }

    fun selectTab(index: Int) {
        if (index == _state.value.selectedTab) return
        _state.value = BrowseState(selectedTab = index)
        loadContent()
    }

    fun loadContent() {
        viewModelScope.launch {
            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                val tab = _state.value.selectedTab
                val from = _state.value.page * pageSize
                val to = from + pageSize - 1

                val result = SupabaseClient.main.from("movies").select(columns) {
                    when (tab) {
                        1 -> filter { eq("type", "movie") }
                        2 -> filter { eq("type", "anime") }
                        3 -> filter { eq("type", "series") }
                        4 -> filter { eq("is_running", true) }
                    }
                    order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    range(from.toLong(), to.toLong())
                }.decodeList<Movie>()

                _state.value = _state.value.copy(
                    items = if (_state.value.page == 0) result else _state.value.items + result,
                    isLoading = false,
                    hasMore = result.size >= pageSize,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, error = e.message)
            }
        }
    }

    fun loadMore() {
        if (!_state.value.hasMore || _state.value.isLoading) return
        _state.value = _state.value.copy(page = _state.value.page + 1)
        loadContent()
    }
}
