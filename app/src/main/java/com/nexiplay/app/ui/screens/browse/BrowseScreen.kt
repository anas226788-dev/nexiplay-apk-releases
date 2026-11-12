package com.nexiplay.app.ui.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nexiplay.app.ui.components.MovieCardGrid
import com.nexiplay.app.ui.components.NexiSvgIcons
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*

private data class BrowseTabDef(
    val index: Int,
    val label: String,
    val icon: ImageVector
)

private data class SortOptionDef(
    val key: String,
    val label: String,
    val icon: ImageVector
)

private val browseTabs = listOf(
    BrowseTabDef(0, "All", NexiSvgIcons.Grid),
    BrowseTabDef(1, "Movies", NexiSvgIcons.Movies),
    BrowseTabDef(2, "Anime", NexiSvgIcons.Anime),
    BrowseTabDef(3, "Series", NexiSvgIcons.Series),
    BrowseTabDef(4, "Airing", NexiSvgIcons.LiveTv),
    BrowseTabDef(5, "Pinned", NexiSvgIcons.Pin)
)

private val sortOptions = listOf(
    SortOptionDef("latest", "Latest", NexiSvgIcons.Flame),
    SortOptionDef("trending", "Trending", NexiSvgIcons.Trophy),
    SortOptionDef("year", "Year", NexiSvgIcons.Calendar),
    SortOptionDef("az", "A-Z", NexiSvgIcons.Sort)
)

@Composable
fun BrowseScreen(
    navController: NavController,
    vm: BrowseViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val filterSignal by BrowseViewModel.filterSignal.collectAsState()
    val gridState = rememberLazyGridState()

    // Consume deep-linked filter from Home screen or BottomNav ("Movies", "Anime", or Genre pill)
    LaunchedEffect(filterSignal) {
        vm.consumePendingFilterOrLoad()
    }

    // Infinite scroll detection (wrapped in derivedStateOf so scrolling doesn't recompose the whole screen)
    val shouldLoadMore by remember {
        derivedStateOf {
            val layoutInfo = gridState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = layoutInfo.totalItemsCount
            totalItems > 0 && lastVisible >= totalItems - 4
        }
    }
    LaunchedEffect(shouldLoadMore, state.hasMore, state.isLoading) {
        if (shouldLoadMore && state.hasMore && !state.isLoading) {
            vm.loadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        // ── Movies Screen Top Bar (Matches Image 2) ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(
                    text = when (state.selectedTab) {
                        1 -> "Movies"
                        2 -> "Anime"
                        3 -> "Series"
                        else -> "Movies"
                    },
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontFamily = InterFont,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = "HD MOVIES · ANIME · SERIES",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = 1.sp,
                    color = Color.White.copy(alpha = 0.55f),
                    fontFamily = InterFont,
                )
            }

            // Capsule Vibe Check Button
            Box(
                modifier = Modifier
                    .bounceClick { navController.navigate(Screen.VibeCheck.route) }
                    .clip(RoundedCornerShape(50))
                    .background(Color(0xFF261D3B))
                    .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.35f), RoundedCornerShape(50))
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.VibeSparkles,
                        contentDescription = "AI Vibe Check",
                        tint = Color(0xFFDDD6FE),
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = "Vibe Check",
                        color = Color(0xFFDDD6FE),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = InterFont
                    )
                }
            }
        }

        // ── Search Bar Trigger ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 10.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFF13111A))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                .clickable { navController.navigate(Screen.Search.route) }
                .padding(horizontal = 14.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = NexiSvgIcons.Search,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.5f),
                    modifier = Modifier.size(17.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "Search movies, anime or series",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    fontFamily = InterFont,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // ── Segmented Control Bar (All / Movies / Anime / Series) ──
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF13111A))
                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                .padding(3.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth()) {
                val primaryTabs = browseTabs.take(4) // All (0), Movies (1), Anime (2), Series (3)
                primaryTabs.forEach { tab ->
                    val isSelected = state.selectedTab == tab.index
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isSelected) Color(0xFF8B5CF6) else Color.Transparent)
                            .clickable { vm.selectTab(tab.index) }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.label,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (isSelected) Color(0xFF0F0B1E) else Color.White.copy(alpha = 0.65f),
                            fontFamily = InterFont
                        )
                    }
                }
            }
        }

        // ── Category / Genre Chips ──
        if (state.categories.isNotEmpty()) {
            Spacer(modifier = Modifier.height(10.dp))
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "cat_all") {
                    val isAllSelected = state.selectedCategorySlug == null
                    WebsiteGenreChip(
                        label = "All",
                        selected = isAllSelected,
                        onClick = { vm.selectCategory(null) }
                    )
                }

                items(state.categories, key = { it.id }) { category ->
                    val isSelected = state.selectedCategorySlug == category.slug
                    WebsiteGenreChip(
                        label = category.name,
                        selected = isSelected,
                        onClick = { vm.selectCategory(category.slug) }
                    )
                }
            }
        }

        // ── Sort Tabs & Reset Action ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                sortOptions.forEach { option ->
                    val active = state.sortBy == option.key
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.clickable { vm.selectSort(option.key) }
                    ) {
                        Text(
                            text = option.label,
                            fontSize = 13.5.sp,
                            fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                            color = if (active) Color.White else Color.White.copy(alpha = 0.55f),
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.height(5.dp))
                        if (active) {
                            Box(
                                modifier = Modifier
                                    .width(28.dp)
                                    .height(2.5.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0xFF8B5CF6))
                            )
                        } else {
                            Spacer(Modifier.height(2.5.dp))
                        }
                    }
                }
            }

            Text(
                text = "Reset",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFF87171),
                fontFamily = InterFont,
                modifier = Modifier
                    .clickable {
                        vm.selectCategory(null)
                        vm.selectTab(0)
                        vm.selectSort("latest")
                    }
                    .padding(vertical = 4.dp, horizontal = 2.dp)
            )
        }

        // ── Content Grid ──
        if (state.isLoading && state.items.isEmpty()) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                userScrollEnabled = false,
            ) {
                items(12) {
                    Column {
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.67f),
                            cornerRadius = 14,
                        )
                        Spacer(Modifier.height(6.dp))
                        ShimmerBox(
                            modifier = Modifier
                                .fillMaxWidth(0.8f)
                                .height(14.dp),
                            cornerRadius = 4,
                        )
                    }
                }
            }
        } else if (state.items.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "No titles match this filter",
                        color = themeTextPrimary(),
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = InterFont
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Try clearing the genre filter or switching tabs",
                        color = themeTextSecondary(),
                        fontSize = 12.sp,
                        fontFamily = InterFont
                    )
                    Spacer(Modifier.height(14.dp))
                    Button(
                        onClick = {
                            vm.selectCategory(null)
                            vm.selectTab(0)
                            vm.selectSort("latest")
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Show All Content", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        } else {
            AnimatedVisibility(
                visible = state.items.isNotEmpty(),
                enter = fadeIn(),
                exit = fadeOut(),
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    state = gridState,
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(
                        items = state.items,
                        key = { it.id },
                        contentType = { "movie_grid_card" }
                    ) { movie ->
                        MovieCardGrid(
                            movie = movie,
                            onClick = {
                                com.nexiplay.app.data.util.AdManager.openContentDetail(navController, movie.type, movie.slug)
                            }
                        )
                    }

                    if (state.isLoading && state.items.isNotEmpty()) {
                        items(3) {
                            Column {
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.67f),
                                    cornerRadius = 14,
                                )
                                Spacer(Modifier.height(6.dp))
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth(0.8f)
                                        .height(14.dp),
                                    cornerRadius = 4,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WebsiteGenreChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF13111A))
            .border(
                if (selected) 1.5.dp else 1.dp,
                if (selected) Color(0xFF8B5CF6) else Color.White.copy(alpha = 0.08f),
                RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            fontSize = 12.5.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
            color = if (selected) Color.White else Color.White.copy(alpha = 0.85f),
            fontFamily = InterFont
        )
    }
}
