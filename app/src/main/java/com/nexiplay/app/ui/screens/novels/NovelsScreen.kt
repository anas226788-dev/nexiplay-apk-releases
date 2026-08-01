package com.nexiplay.app.ui.screens.novels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Novel
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import com.nexiplay.app.ui.components.bounceClick
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.border
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MenuBook
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@Composable
fun NovelsScreen(navController: NavController) {
    var novels by remember { mutableStateOf<List<Novel>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetchNovels() {
        loading = true
        errorMessage = null
        scope.launch {
            try {
                novels = SupabaseClient.novels.from("novels").select().decodeList<Novel>()
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load novels. Please check your connection."
            }
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchNovels()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
    ) {
        // Header
        Text(
            text = "📖 Novels",
            fontSize = 24.sp,
            fontWeight = FontWeight.Black,
            color = themeTextPrimary(),
            modifier = Modifier.padding(16.dp),
        )

        if (loading) {
            NovelGridSkeleton()
        } else if (errorMessage != null) {
            com.nexiplay.app.ui.components.ErrorView(
                message = errorMessage!!,
                onRetry = { fetchNovels() }
            )
        } else if (novels.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No novels available yet", color = themeTextSecondary())
            }
        } else {
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                items(novels) { novel ->
                    NovelCard(
                        novel = novel,
                        onClick = { navController.navigate(Screen.NovelDetail.createRoute(novel.slug)) }
                    )
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
                        androidx.compose.ui.graphics.Brush.verticalGradient(
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
            
            // Chapter count inside the gradient
            if (novel.totalChapters != null && novel.totalChapters > 0) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(12.dp)
                ) {
                    Icon(androidx.compose.material.icons.Icons.Default.MenuBook, null, tint = Color.White, modifier = Modifier.size(12.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        "${novel.totalChapters} Chapters", 
                        fontSize = 11.sp, 
                        color = Color.White, 
                        fontWeight = FontWeight.Bold
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
