package com.nexiplay.app.ui.screens.novels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Novel
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Count
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 24

@Composable
fun NovelsScreen(navController: NavController) {
    var currentPage by rememberSaveable { mutableIntStateOf(1) }
    var totalCount by rememberSaveable { mutableIntStateOf(0) }
    var novels by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    val scope = rememberCoroutineScope()
    val gridState = rememberLazyGridState()

    fun fetchPage(targetPage: Int, search: String = searchQuery) {
        loading = true
        errorMessage = null
        scope.launch {
            try {
                val q = search.trim()
                val from = (targetPage - 1) * PAGE_SIZE
                val to = from + PAGE_SIZE - 1

                val cols = Columns.raw("id, title, slug, cover_url, author, genre, description, status, created_at")

                val result = SupabaseClient.novels.from("novels").select(cols) {
                    count(Count.EXACT)
                    if (q.isNotEmpty()) {
                        filter {
                            ilike("title", "%$q%")
                        }
                    }
                    order("created_at", Order.DESCENDING)
                    range(from.toLong(), to.toLong())
                }

                val exactCount = result.countOrNull()?.toInt()
                val items = result.decodeList<Novel>()

                novels = items
                if (exactCount != null) {
                    totalCount = exactCount
                } else if (q.isEmpty() && totalCount == 0) {
                    totalCount = 864
                } else if (q.isNotEmpty()) {
                    totalCount = if (items.size < PAGE_SIZE) from + items.size else from + items.size + 1
                }
                currentPage = targetPage
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load novels. Please check your connection."
            }
            loading = false
            try {
                gridState.scrollToItem(0)
            } catch (_: Exception) {}
        }
    }

    LaunchedEffect(Unit) {
        if (novels.isEmpty()) {
            fetchPage(1, search = "")
        }
    }

    val totalPages = if (totalCount > 0) {
        ((totalCount + PAGE_SIZE - 1) / PAGE_SIZE).coerceAtLeast(1)
    } else {
        1
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "📖 Novels",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Black,
                    color = themeTextPrimary(),
                    fontFamily = InterFont
                )
                if (totalCount > 0) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .background(NexiRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (searchQuery.isNotEmpty()) "$totalCount found" else "$totalCount books",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexiRed,
                            fontFamily = InterFont
                        )
                    }
                }
            }
        }

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = {
                searchQuery = it
                searchJob?.cancel()
                searchJob = scope.launch {
                    delay(400)
                    fetchPage(1, search = it)
                }
            },
            placeholder = {
                Text(
                    "Search novels by title...",
                    color = themeTextTertiary(),
                    fontSize = 13.sp,
                    fontFamily = InterFont
                )
            },
            leadingIcon = {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search",
                    tint = if (searchQuery.isNotEmpty()) NexiRed else themeTextSecondary(),
                    modifier = Modifier.size(20.dp)
                )
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = {
                        searchJob?.cancel()
                        searchQuery = ""
                        fetchPage(1, search = "")
                    }) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Clear",
                            tint = themeTextSecondary(),
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = themeCard(),
                unfocusedContainerColor = themeCard(),
                focusedBorderColor = NexiRed,
                unfocusedBorderColor = themeSurface(),
                focusedTextColor = themeTextPrimary(),
                unfocusedTextColor = themeTextPrimary(),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 8.dp)
        )

        // Main Content Area
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            if (loading) {
                NovelGridSkeleton()
            } else if (errorMessage != null) {
                com.nexiplay.app.ui.components.ErrorView(
                    message = errorMessage!!,
                    onRetry = { fetchPage(currentPage) }
                )
            } else if (novels.isEmpty()) {
                if (searchQuery.isNotBlank()) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("🔍", fontSize = 48.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "No novels found",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary(),
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "We couldn't find any novel matching \"$searchQuery\"",
                            fontSize = 13.sp,
                            color = themeTextSecondary(),
                            textAlign = TextAlign.Center,
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = {
                                searchQuery = ""
                                fetchPage(1, search = "")
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Clear Search", fontWeight = FontWeight.Bold, fontFamily = InterFont)
                        }
                    }
                } else {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No novels available yet", color = themeTextSecondary(), fontFamily = InterFont)
                    }
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(novels, key = { it.id }) { novel ->
                        NovelCard(
                            novel = novel,
                            onClick = { navController.navigate(Screen.NovelDetail.createRoute(novel.slug)) }
                        )
                    }
                }
            }
        }

        // Modern Bottom Pagination Bar
        if (!loading && errorMessage == null && novels.isNotEmpty() && totalPages > 1) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                color = themeCard(),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, themeTextTertiary().copy(alpha = 0.15f)),
                shadowElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // First page button
                    IconButton(
                        onClick = { if (currentPage > 1) fetchPage(1) },
                        enabled = currentPage > 1,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.FirstPage,
                            contentDescription = "First Page",
                            tint = if (currentPage > 1) themeTextPrimary() else themeTextTertiary().copy(alpha = 0.3f)
                        )
                    }

                    // Prev page button
                    Button(
                        onClick = { if (currentPage > 1) fetchPage(currentPage - 1) },
                        enabled = currentPage > 1,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentPage > 1) NexiRed.copy(alpha = 0.15f) else Color.Transparent,
                            contentColor = if (currentPage > 1) NexiRed else themeTextTertiary().copy(alpha = 0.3f),
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = themeTextTertiary().copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        elevation = null
                    ) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(2.dp))
                        Text("Prev", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                    }

                    // Page indicator pill
                    Box(
                        modifier = Modifier
                            .background(themeSurface(), RoundedCornerShape(8.dp))
                            .border(1.dp, themeTextTertiary().copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Page $currentPage / $totalPages",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = themeTextPrimary(),
                            fontFamily = InterFont
                        )
                    }

                    // Next page button
                    Button(
                        onClick = { if (currentPage < totalPages) fetchPage(currentPage + 1) },
                        enabled = currentPage < totalPages,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (currentPage < totalPages) NexiRed else Color.Transparent,
                            contentColor = if (currentPage < totalPages) Color.White else themeTextTertiary().copy(alpha = 0.3f),
                            disabledContainerColor = Color.Transparent,
                            disabledContentColor = themeTextTertiary().copy(alpha = 0.3f)
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                        elevation = null
                    ) {
                        Text("Next", fontSize = 12.sp, fontWeight = FontWeight.Bold, fontFamily = InterFont)
                        Spacer(Modifier.width(2.dp))
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, modifier = Modifier.size(16.dp))
                    }

                    // Last page button
                    IconButton(
                        onClick = { if (currentPage < totalPages) fetchPage(totalPages) },
                        enabled = currentPage < totalPages,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Filled.LastPage,
                            contentDescription = "Last Page",
                            tint = if (currentPage < totalPages) themeTextPrimary() else themeTextTertiary().copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NovelCard(novel: Novel, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .bounceClick(onClick = onClick)
            .fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(14.dp))
                .background(themeCard())
                .border(1.dp, themeTextTertiary().copy(alpha = 0.15f), RoundedCornerShape(14.dp))
        ) {
            AsyncImage(
                model = novel.coverUrl,
                contentDescription = novel.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            // Premium Gradient Overlay
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color.Transparent,
                                Color.Transparent,
                                Color.Black.copy(alpha = 0.8f)
                            )
                        )
                    )
            )

            // Status badge
            if (novel.status != null) {
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .align(Alignment.TopStart)
                        .background(
                            if (novel.status == "Ongoing") SuccessGreen else VipPurple,
                            RoundedCornerShape(6.dp)
                        )
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(novel.status.uppercase(), fontSize = 9.sp, fontWeight = FontWeight.Bold, color = themeBg(), letterSpacing = 0.5.sp)
                }
            }

            // Chapter count or Genre inside the gradient
            if (novel.totalChapters != null && novel.totalChapters > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${novel.totalChapters} Chapters",
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFont
                    )
                }
            } else if (!novel.genre.isNullOrBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Icon(Icons.Default.MenuBook, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        novel.genre,
                        fontSize = 11.sp,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFont
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = novel.title,
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = themeTextPrimary(),
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            fontFamily = InterFont,
            lineHeight = 18.sp
        )
        Spacer(Modifier.height(2.dp))
        if (novel.author != null) {
            Text(
                novel.author,
                fontSize = 12.sp,
                color = themeTextSecondary(),
                maxLines = 1,
                fontFamily = InterFont,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
