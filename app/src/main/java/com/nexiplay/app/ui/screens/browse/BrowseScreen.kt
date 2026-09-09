package com.nexiplay.app.ui.screens.browse

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nexiplay.app.ui.components.MovieCardGrid
import com.nexiplay.app.ui.components.ShimmerBox
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*

private val tabs = listOf("All", "Movies", "Anime", "Series", "Running")

@Composable
fun BrowseScreen(
    navController: NavController,
    vm: BrowseViewModel = viewModel()
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val state by vm.state.collectAsState()
    val gridState = rememberLazyGridState()

    // Infinite scroll detection
    LaunchedEffect(gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index) {
        val lastVisible = gridState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
        val totalItems = gridState.layoutInfo.totalItemsCount
        if (lastVisible >= totalItems - 4 && state.hasMore && !state.isLoading) {
            vm.loadMore()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        // ── Top Bar ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Browse",
                fontSize = 24.sp,
                fontWeight = FontWeight.Black,
                color = themeTextPrimary(),
                fontFamily = InterFont,
            )
            IconButton(onClick = { navController.navigate(Screen.Search.route) }) {
                Icon(Icons.Default.Search, "Search", tint = themeTextPrimary())
            }
        }

        // ── Tab Row with animated pill indicator ──
        ScrollableTabRow(
            selectedTabIndex = state.selectedTab,
            containerColor = themeBg(),
            contentColor = NexiRed,
            edgePadding = 16.dp,
            divider = {},
            indicator = {},
        ) {
            tabs.forEachIndexed { index, title ->
                val selected = state.selectedTab == index

                // Animated pill padding for smooth size transition
                val hPad by animateDpAsState(
                    targetValue = if (selected) 20.dp else 16.dp,
                    animationSpec = spring(stiffness = 300f),
                    label = "tab_hPad"
                )
                val vPad by animateDpAsState(
                    targetValue = if (selected) 10.dp else 8.dp,
                    animationSpec = spring(stiffness = 300f),
                    label = "tab_vPad"
                )

                Tab(
                    selected = selected,
                    onClick = { vm.selectTab(index) },
                    modifier = Modifier.padding(end = 8.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(
                                if (selected) NexiRed else themeCard(),
                                RoundedCornerShape(20.dp)
                            )
                            .padding(horizontal = hPad, vertical = vPad)
                    ) {
                        Text(
                            text = title,
                            fontSize = 13.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = if (selected) themeTextPrimary() else themeTextSecondary(),
                            fontFamily = InterFont,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        // ── Content Grid ──
        if (state.isLoading && state.items.isEmpty()) {
            // Shimmer loading grid: 3 columns × 4 rows
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
                                .aspectRatio(0.68f),
                            cornerRadius = 12,
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
                Text("No content found", color = themeTextSecondary())
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
                    items(state.items, key = { it.id }) { movie ->
                        MovieCardGrid(
                            movie = movie,
                            onClick = {
                                com.nexiplay.app.data.util.AdManager.showInterstitialAd(context) {
                                    navController.navigate(Screen.ContentDetail.createRoute(movie.type, movie.slug))
                                }
                            }
                        )
                    }

                    // Shimmer loading indicator at bottom (replaces CircularProgressIndicator)
                    if (state.isLoading && state.items.isNotEmpty()) {
                        items(3) {
                            Column {
                                ShimmerBox(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.68f),
                                    cornerRadius = 12,
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
