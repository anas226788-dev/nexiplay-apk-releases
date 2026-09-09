package com.nexiplay.app.ui.screens.watchlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class WatchlistEntry(
    @SerialName("movie_id") val movieId: String,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(navController: NavController) {
    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetchWatchlist() {
        val user = SupabaseClient.main.auth.currentUserOrNull()
        if (user != null) {
            loading = true
            errorMessage = null
            scope.launch {
                try {
                    val entries = SupabaseClient.main.from("watchlist")
                        .select { filter { eq("user_id", user.id) } }
                        .decodeList<WatchlistEntry>()

                    if (entries.isNotEmpty()) {
                        val movieIds = entries.map { it.movieId }
                        movies = SupabaseClient.main.from("movies")
                            .select { filter { isIn("id", movieIds) } }
                            .decodeList<Movie>()
                    } else {
                        movies = emptyList()
                    }
                } catch (e: Exception) {
                    movies = emptyList()
                }
                errorMessage = null
                loading = false
            }
        } else {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchWatchlist()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Watchlist", fontWeight = FontWeight.Bold, color = themeTextPrimary()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeSurface()),
            )
        },
        containerColor = themeBg(),
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (loading) {
                com.nexiplay.app.ui.components.WatchlistSkeleton()
            } else if (errorMessage != null) {
                com.nexiplay.app.ui.components.ErrorView(
                    message = errorMessage!!,
                    onRetry = { fetchWatchlist() }
                )
            } else if (movies.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.BookmarkBorder, null, tint = themeTextTertiary(), modifier = Modifier.size(64.dp))
                        Spacer(Modifier.height(16.dp))
                        Text("No Saved Content", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                        Spacer(Modifier.height(4.dp))
                        Text("Tap the bookmark icon on\nany content to save it here", fontSize = 13.sp, color = themeTextSecondary(), textAlign = TextAlign.Center)
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(movies, key = { it.id }) { movie ->
                        MovieCardGrid(
                            movie = movie,
                            onClick = { navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug)) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MovieCardGrid(movie: Movie, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
    ) {
        AsyncImage(
            model = movie.posterUrl,
            contentDescription = movie.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(10.dp))
                .background(themeCard()),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            movie.title,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = themeTextPrimary(),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
