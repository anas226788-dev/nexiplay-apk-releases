package com.nexiplay.app.ui.screens.novels

import androidx.compose.foundation.background
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.NovelChapter
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(navController: NavController, novelId: String, chapterNumber: Int) {
    var chapter by remember { mutableStateOf<NovelChapter?>(null) }
    var totalChapters by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun loadChapter(num: Int) {
        scope.launch {
            loading = true
            error = null
            try {
                chapter = SupabaseClient.novels.from("novel_chapters")
                    .select {
                        filter {
                            eq("novel_id", novelId)
                            eq("chapter_number", num)
                        }
                    }
                    .decodeSingleOrNull<NovelChapter>()

                if (chapter == null) {
                    error = "Chapter not found"
                }

                // Get total chapter count
                val allChapters = SupabaseClient.novels.from("novel_chapters")
                    .select {
                        filter { eq("novel_id", novelId) }
                        order("chapter_number", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<NovelChapter>()
                totalChapters = allChapters.firstOrNull()?.chapterNumber ?: 0
            } catch (e: Exception) {
                error = e.message
            }
            loading = false
            scrollState.scrollTo(0)
        }
    }

    LaunchedEffect(novelId, chapterNumber) {
        loadChapter(chapterNumber)
    }

    var currentChapterNum by remember(chapterNumber) { mutableIntStateOf(chapterNumber) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        chapter?.title ?: "Chapter $currentChapterNum",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = themeTextPrimary(),
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeSurface(),
                ),
            )
        },
        containerColor = themeBg(),
    ) { padding ->
        if (loading) {
            Box(
                Modifier.fillMaxSize().padding(padding)
            ) {
                NovelReaderSkeleton()
            }
            return@Scaffold
        }

        if (error != null || chapter == null) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("😕", fontSize = 40.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(error ?: "Chapter not found", color = themeTextSecondary())
                }
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(scrollState)
        ) {
            // ── Chapter Header ──
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeSurface())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "Chapter $currentChapterNum",
                    fontSize = 12.sp,
                    color = NexiRed,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    chapter!!.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary(),
                    textAlign = TextAlign.Center,
                )
            }

            // ── Chapter Content ──
            val rawContent = chapter!!.content ?: "No content available."
            val parsedContent = android.text.Html.fromHtml(rawContent, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
            
            Text(
                text = parsedContent,
                fontSize = 16.sp,
                color = themeTextPrimary().copy(alpha = 0.92f),
                lineHeight = 28.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
            )

            // ── Navigation Buttons ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(themeSurface())
                    .padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // Previous
                OutlinedButton(
                    onClick = {
                        if (currentChapterNum > 1) {
                            currentChapterNum--
                            loadChapter(currentChapterNum)
                        }
                    },
                    enabled = currentChapterNum > 1,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = themeTextPrimary()),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Icon(Icons.Default.ArrowBack, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Previous", fontWeight = FontWeight.Bold)
                }

                // Next
                Button(
                    onClick = {
                        if (currentChapterNum < totalChapters) {
                            currentChapterNum++
                            loadChapter(currentChapterNum)
                        }
                    },
                    enabled = currentChapterNum < totalChapters,
                    colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f).height(48.dp),
                ) {
                    Text("Next", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }

            // Chapter progress
            Text(
                "Chapter $currentChapterNum of $totalChapters",
                fontSize = 12.sp,
                color = themeTextTertiary(),
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
            )

            Spacer(Modifier.height(16.dp))
        }
    }
}
