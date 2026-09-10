package com.nexiplay.app.ui.screens.novels

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Novel
import com.nexiplay.app.data.model.NovelChapter
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch

import androidx.compose.ui.platform.LocalContext

@Composable
fun NovelDetailScreen(navController: NavController, slug: String) {
    val context = LocalContext.current
    var novel by remember { mutableStateOf<Novel?>(null) }
    var chapters by remember { mutableStateOf<List<NovelChapter>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var expandDesc by remember { mutableStateOf(false) }
    var chapterSearch by remember { mutableStateOf("") }
    var sortAscending by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()

    val decodedSlug = remember(slug) {
        try {
            java.net.URLDecoder.decode(slug, "UTF-8")
        } catch (e: Exception) {
            slug
        }
    }

    fun fetchNovel() {
        loading = true
        errorMessage = null
        scope.launch {
            try {
                var fetchedNovel = SupabaseClient.novels.from("novels")
                    .select { filter { eq("slug", decodedSlug) } }
                    .decodeSingleOrNull<Novel>()

                if (fetchedNovel == null && decodedSlug != slug) {
                    fetchedNovel = SupabaseClient.novels.from("novels")
                        .select { filter { eq("slug", slug) } }
                        .decodeSingleOrNull<Novel>()
                }
                novel = fetchedNovel

                if (novel != null) {
                    chapters = SupabaseClient.novels.from("novel_chapters")
                        .select(Columns.raw("id, novel_id, title, slug, chapter_number, created_at")) {
                            filter { eq("novel_id", novel!!.id) }
                            order("chapter_number", Order.ASCENDING)
                        }
                        .decodeList<NovelChapter>()
                }
            } catch (e: Exception) {
                errorMessage = e.localizedMessage ?: "Failed to load novel details"
            }
            loading = false
        }
    }

    LaunchedEffect(decodedSlug) {
        fetchNovel()
    }

    if (loading) {
        NovelDetailSkeleton()
        return
    }

    if (errorMessage != null) {
        Box(Modifier.fillMaxSize().background(themeBg()), contentAlignment = Alignment.Center) {
            com.nexiplay.app.ui.components.ErrorView(
                message = errorMessage!!,
                onRetry = { fetchNovel() }
            )
        }
        return
    }

    if (novel == null) {
        Box(Modifier.fillMaxSize().background(themeBg()), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("📖", fontSize = 40.sp)
                Spacer(Modifier.height(8.dp))
                Text("Novel not found", color = themeTextSecondary())
                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = { navController.popBackStack() },
                    colors = ButtonDefaults.buttonColors(containerColor = NexiRed)
                ) { Text("Go Back") }
            }
        }
        return
    }

    val n = novel!!

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(themeBg())
            .verticalScroll(rememberScrollState())
    ) {
        // ── Hero Cover ──
        Box(Modifier.fillMaxWidth().height(300.dp)) {
            AsyncImage(
                model = n.coverUrl,
                contentDescription = n.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, themeBg().copy(alpha = 0.7f), themeBg())
                    )
                )
            )
            IconButton(
                onClick = { navController.popBackStack() },
                modifier = Modifier.padding(8.dp).align(Alignment.TopStart),
            ) {
                Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
            }
        }

        Column(Modifier.padding(horizontal = 16.dp).offset(y = (-40).dp)) {
            // ── Title & Meta ──
            Text(n.title, fontSize = 24.sp, fontWeight = FontWeight.Black, color = themeTextPrimary())
            Spacer(Modifier.height(6.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (n.author != null) {
                    Text("✍ ${n.author}", fontSize = 13.sp, color = themeTextSecondary())
                }
                if (!n.genre.isNullOrBlank()) {
                    Box(
                        Modifier.background(
                            VipPurple,
                            RoundedCornerShape(6.dp)
                        ).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(n.genre.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                    }
                }
                if (n.status != null) {
                    Box(
                        Modifier.background(
                            if (n.status == "Ongoing") SuccessGreen else Color(0xFFE50914),
                            RoundedCornerShape(6.dp)
                        ).padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(n.status.uppercase(), fontSize = 10.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                    }
                }
                Text("${chapters.size} Chapters", fontSize = 13.sp, color = themeTextTertiary())
            }

            Spacer(Modifier.height(12.dp))

            // ── Start Reading Button ──
            if (chapters.isNotEmpty()) {
                val startChapterNum = chapters.firstOrNull()?.chapterNumber ?: 1
                Button(
                    onClick = {
                        com.nexiplay.app.data.util.AdManager.showAlternatingAd(context) {
                            navController.navigate("novel_reader/${n.id}/$startChapterNum")
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    Icon(Icons.Default.MenuBook, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Start Reading", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
            }

            Spacer(Modifier.height(16.dp))

            // ── Description ──
            if (!n.description.isNullOrBlank()) {
                Text("Description", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                Spacer(Modifier.height(6.dp))
                Text(
                    n.description,
                    fontSize = 14.sp, color = themeTextSecondary(), lineHeight = 22.sp,
                    maxLines = if (expandDesc) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (expandDesc) "Show Less" else "Read More",
                    fontSize = 12.sp, color = NexiRed, fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { expandDesc = !expandDesc }.padding(vertical = 4.dp),
                )
                Spacer(Modifier.height(16.dp))
            }

            // ── Chapter List Header with Search & Sort ──
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("📚 Chapters (${chapters.size})", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())

                if (chapters.size > 1) {
                    TextButton(
                        onClick = { sortAscending = !sortAscending },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(
                            if (sortAscending) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward,
                            null,
                            modifier = Modifier.size(14.dp),
                            tint = NexiRed
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            if (sortAscending) "1 → End" else "End → 1",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexiRed
                        )
                    }
                }
            }

            if (chapters.size > 15) {
                OutlinedTextField(
                    value = chapterSearch,
                    onValueChange = { chapterSearch = it },
                    placeholder = { Text("Search chapter # or title...", fontSize = 12.sp, color = themeTextTertiary()) },
                    singleLine = true,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = themeCard(),
                        unfocusedContainerColor = themeCard(),
                        focusedBorderColor = NexiRed,
                        unfocusedBorderColor = themeBorder(),
                        focusedTextColor = themeTextPrimary(),
                        unfocusedTextColor = themeTextPrimary(),
                    ),
                    leadingIcon = {
                        Icon(Icons.Default.Search, null, tint = themeTextSecondary(), modifier = Modifier.size(16.dp))
                    },
                    trailingIcon = {
                        if (chapterSearch.isNotEmpty()) {
                            IconButton(onClick = { chapterSearch = "" }) {
                                Icon(Icons.Default.Close, null, tint = themeTextSecondary(), modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(8.dp))

            val displayChapters = remember(chapters, chapterSearch, sortAscending) {
                val sorted = if (sortAscending) chapters else chapters.reversed()
                if (chapterSearch.isBlank()) sorted
                else {
                    val q = chapterSearch.trim().lowercase()
                    sorted.filter { it.chapterNumber.toString() == q || it.title.lowercase().contains(q) }
                }
            }

            if (displayChapters.isEmpty()) {
                Text(
                    "No chapters match \"$chapterSearch\"",
                    fontSize = 13.sp,
                    color = themeTextSecondary(),
                    modifier = Modifier.padding(vertical = 12.dp)
                )
            } else {
                displayChapters.forEach { ch ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                            .background(themeCard(), RoundedCornerShape(10.dp))
                            .clickable {
                                com.nexiplay.app.data.util.AdManager.showAlternatingAd(context) {
                                    navController.navigate("novel_reader/${n.id}/${ch.chapterNumber}")
                                }
                            }
                            .padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier.size(36.dp)
                                .background(NexiRed.copy(alpha = 0.15f), RoundedCornerShape(8.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                "${ch.chapterNumber}",
                                fontSize = 14.sp, fontWeight = FontWeight.Bold, color = NexiRed,
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            ch.title,
                            fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = themeTextPrimary(),
                            maxLines = 1, overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        Icon(Icons.Default.ChevronRight, null, tint = themeTextTertiary(), modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}
