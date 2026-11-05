package com.nexiplay.app.ui.screens.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.ui.components.MovieCardGrid
import com.nexiplay.app.ui.components.ClayIconButton
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.data.repository.TmdbRepository
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SearchScreen(navController: NavController) {
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searched by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current

    var hideNsfw by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
        withContext(Dispatchers.IO) {
            try {
                val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
                if (uid != null) {
                    val p = SupabaseClient.main.from("profiles")
                        .select(Columns.list("hide_nsfw")) { filter { eq("id", uid) } }
                        .decodeSingleOrNull<com.nexiplay.app.data.model.UserProfile>()
                    hideNsfw = p?.hideNsfw ?: false
                }
            } catch (_: Exception) {}
        }
    }

    suspend fun runSearchInternal(cleanQuery: String) {
        loading = true
        searched = true
        errorMessage = null
        try {
            val filtered = withContext(Dispatchers.IO) {
                val combined = coroutineScope {
                    val localDef = async {
                        runCatching {
                            SupabaseClient.main.from("movies").select(
                                Columns.raw("id, title, slug, poster_url, type, release_year, is_adult, admin_note, tmdb_id, created_at")
                            ) {
                                filter { ilike("title", "%$cleanQuery%") }
                                order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                                limit(30)
                            }.decodeList<Movie>()
                        }.getOrDefault(emptyList())
                    }
                    val tmdbDef = async {
                        runCatching {
                            TmdbRepository.searchTmdb(cleanQuery)
                        }.getOrDefault(emptyList())
                    }
                    val localList = localDef.await()
                    val tmdbList = TmdbRepository.deduplicateAgainstLocal(tmdbDef.await(), localList)
                    localList + tmdbList
                }
                if (hideNsfw) combined.filter { it.isAdult != true } else combined
            }
            results = filtered
        } catch (e: Exception) {
            errorMessage = com.nexiplay.app.ui.util.UserFacingError.from(e, "Couldn't complete your search. Please try again.")
        }
        loading = false
    }

    // Debounced live search so results appear smoothly while typing without blocking the UI thread
    LaunchedEffect(query) {
        val clean = query.trim()
        if (clean.length < 2) {
            if (clean.isEmpty()) {
                results = emptyList()
                searched = false
                loading = false
            }
            return@LaunchedEffect
        }
        delay(350)
        runSearchInternal(clean)
    }

    fun doSearch() {
        val clean = query.trim()
        if (clean.isBlank()) return
        focusManager.clearFocus()
        scope.launch {
            runSearchInternal(clean)
        }
    }

    // ── Track focus state for animated border ──
    var isFocused by remember { mutableStateOf(false) }

    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) NexiRed else themeBorder(),
        animationSpec = tween(durationMillis = 300),
        label = "border_color"
    )
    val animatedBorderWidth by animateDpAsState(
        targetValue = if (isFocused) 1.5.dp else 1.dp,
        animationSpec = tween(durationMillis = 300),
        label = "border_width"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        // ── Search Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ClayIconButton(
                icon = Icons.Default.ArrowBack,
                onClick = { navController.popBackStack() },
                size = 42.dp,
                contentDescription = "Back"
            )

            Spacer(modifier = Modifier.width(4.dp))

            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .border(animatedBorderWidth, animatedBorderColor, RoundedCornerShape(14.dp))
                    .background(DarkInset)
            ) {
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = {
                        Text(
                            "Search movies, anime, series...",
                            fontSize = 14.sp,
                            fontFamily = InterFont,
                            color = themeTextTertiary(),
                        )
                    },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { doSearch() }),
                    leadingIcon = {
                        Icon(
                            Icons.Default.Search,
                            contentDescription = null,
                            tint = if (isFocused) NexiRed else themeTextTertiary(),
                            modifier = Modifier.size(20.dp)
                        )
                    },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Clear",
                                    tint = themeTextSecondary(),
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    },
                    textStyle = TextStyle(
                        fontSize = 14.sp,
                        fontFamily = InterFont,
                        color = themeTextPrimary(),
                    ),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        cursorColor = NexiRed,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(focusRequester)
                        .onFocusChanged { isFocused = it.isFocused },
                )
            }
        }

        // ── Results Area ──
        if (loading) {
            // Shimmer grid: 3 columns x 3 rows
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false,
            ) {
                items(9) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.68f),
                            cornerRadius = 12,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .height(12.dp),
                            cornerRadius = 6,
                        )
                        Spacer(modifier = Modifier.height(5.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.55f)
                                .height(10.dp),
                            cornerRadius = 6,
                        )
                    }
                }
            }
        } else if (errorMessage != null) {
            com.nexiplay.app.ui.components.ErrorView(
                message = errorMessage!!,
                onRetry = { doSearch() }
            )
        } else if (searched && results.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "🔍",
                        fontSize = 40.sp,
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No results found for \"$query\"",
                        color = themeTextSecondary(),
                        fontSize = 14.sp,
                        fontFamily = InterFont,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        } else {
            AnimatedVisibility(
                visible = results.isNotEmpty(),
                enter = fadeIn(tween(400)) + slideInVertically(tween(400)) { it / 6 },
                exit = fadeOut(tween(200)),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(results, key = { it.id }) { movie ->
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
