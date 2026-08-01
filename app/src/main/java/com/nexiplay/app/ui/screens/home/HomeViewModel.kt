package com.nexiplay.app.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Category
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.Upcoming
import com.nexiplay.app.data.model.UpdateItem
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class HomeState(
    val trending: List<Movie> = emptyList(),
    val updates: List<UpdateItem> = emptyList(),
    val upcoming: List<Upcoming> = emptyList(),
    val categories: List<Category> = emptyList(),
    val recentMovies: List<Movie> = emptyList(),
    val recentAnime: List<Movie> = emptyList(),
    val recentSeries: List<Movie> = emptyList(),
    val running: List<Movie> = emptyList(),
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
)

class HomeViewModel : ViewModel() {
    private val _state = MutableStateFlow(HomeState())
    val state = _state.asStateFlow()

    private val cols = Columns.raw("id, title, slug, poster_url, description, type, release_year, is_trending, trending_rank, is_running, is_adult, banner_url_desktop, banner_url_mobile, last_episode, running_notice, next_episode_date, created_at")

    init { loadHome() }

    fun loadHome(isRefresh: Boolean = false) {
        viewModelScope.launch {
            if (isRefresh) {
                _state.value = _state.value.copy(isRefreshing = true, error = null)
            } else {
                _state.value = _state.value.copy(isLoading = true, error = null)
            }
            try {
                val db = SupabaseClient.main

                val updates = try {
                    db.from("updates").select {
                        filter { eq("is_active", true) }
                        order("updated_at", Order.DESCENDING)
                        limit(10)
                    }.decodeList<UpdateItem>()
                } catch (_: Exception) { emptyList() }

                val upcoming = try {
                    db.from("upcoming").select {
                        order("release_date", Order.ASCENDING)
                        limit(10)
                    }.decodeList<Upcoming>()
                } catch (_: Exception) { emptyList() }

                val categories = try {
                    db.from("categories").select {
                        order("name", Order.ASCENDING)
                    }.decodeList<Category>()
                } catch (_: Exception) { emptyList() }

                val trending = db.from("movies").select(cols) {
                    filter { eq("is_trending", true) }
                    order("trending_rank", Order.ASCENDING)
                    limit(10)
                }.decodeList<Movie>()

                val recentMovies = db.from("movies").select(cols) {
                    filter { eq("type", "movie") }
                    order("created_at", Order.DESCENDING)
                    limit(10)
                }.decodeList<Movie>()

                val recentAnime = db.from("movies").select(cols) {
                    filter { eq("type", "anime") }
                    order("created_at", Order.DESCENDING)
                    limit(10)
                }.decodeList<Movie>()

                val recentSeries = db.from("movies").select(cols) {
                    filter { eq("type", "series") }
                    order("created_at", Order.DESCENDING)
                    limit(10)
                }.decodeList<Movie>()

                val running = db.from("movies").select(cols) {
                    filter { eq("is_running", true) }
                    order("created_at", Order.DESCENDING)
                    limit(10)
                }.decodeList<Movie>()

                _state.value = HomeState(
                    trending = trending,
                    updates = updates,
                    upcoming = upcoming,
                    categories = categories,
                    recentMovies = recentMovies,
                    recentAnime = recentAnime,
                    recentSeries = recentSeries,
                    running = running,
                    isLoading = false,
                    isRefreshing = false,
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(isLoading = false, isRefreshing = false, error = e.message)
            }
        }
    }
}
