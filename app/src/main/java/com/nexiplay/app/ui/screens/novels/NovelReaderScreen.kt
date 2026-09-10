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
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// Shared singleton OkHttpClient with reasonable timeouts
private val r2HttpClient = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NovelReaderScreen(navController: NavController, novelId: String, chapterNumber: Int) {
    var chapter by remember { mutableStateOf<NovelChapter?>(null) }
    var totalChapters by remember { mutableIntStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var currentChapterNum by remember(chapterNumber) { mutableIntStateOf(chapterNumber) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()

    fun loadChapter(num: Int) {
        scope.launch {
            loading = true
            error = null
            try {
                // 1. Fetch metadata from Supabase
                val fetchedChapter = SupabaseClient.novels.from("novel_chapters")
                    .select {
                        filter {
                            eq("novel_id", novelId)
                            eq("chapter_number", num)
                        }
                    }
                    .decodeSingleOrNull<NovelChapter>()

                if (fetchedChapter == null) {
                    error = "Chapter $num not found."
                } else {
                    chapter = fetchedChapter

                    // 2. Fetch full chapter content from Cloudflare R2 CDN if content is empty in Supabase
                    if (fetchedChapter.content.isNullOrBlank()) {
                        var cdnContent: String? = null
                        var cdnError: String? = null

                        withContext(Dispatchers.IO) {
                            try {
                                val r2Url = "https://pub-246be7bb40a14c07b8a8359e2bc8285d.r2.dev/chapters/${novelId}/${num}.json"
                                val req = Request.Builder()
                                    .url(r2Url)
                                    .header("Cache-Control", "no-cache")
                                    .build()
                                val resp = r2HttpClient.newCall(req).execute()
                                if (resp.isSuccessful) {
                                    val bodyStr = resp.body?.string() ?: ""
                                    if (bodyStr.isNotBlank()) {
                                        val json = JSONObject(bodyStr)
                                        cdnContent = json.optString("content", "")
                                    }
                                } else {
                                    cdnError = "CDN returned HTTP ${resp.code}"
                                }
                            } catch (e: Exception) {
                                cdnError = e.localizedMessage ?: "Failed to connect to CDN"
                            }
                        }

                        if (!cdnContent.isNullOrBlank()) {
                            chapter = chapter?.copy(content = cdnContent)
                        } else if (cdnError != null) {
                            error = "Could not load chapter content ($cdnError). Please tap Retry."
                        }
                    }
                }

                // 3. Get total chapter count
                val allChapters = SupabaseClient.novels.from("novel_chapters")
                    .select(Columns.raw("chapter_number")) {
                        filter { eq("novel_id", novelId) }
                        order("chapter_number", Order.DESCENDING)
                        limit(1)
                    }
                    .decodeList<NovelChapter>()
                totalChapters = allChapters.firstOrNull()?.chapterNumber ?: maxOf(num, totalChapters)
            } catch (e: Exception) {
                error = e.localizedMessage ?: "Failed to load chapter details"
            }
            loading = false
            scrollState.scrollTo(0)
        }
    }

    LaunchedEffect(novelId, chapterNumber) {
        currentChapterNum = chapterNumber
        loadChapter(chapterNumber)
    }

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

        if (error != null || chapter == null || chapter?.content.isNullOrBlank()) {
            Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(24.dp)
                ) {
                    Text("📖", fontSize = 48.sp)
                    Spacer(Modifier.height(12.dp))
                    Text(
                        error ?: "No content available for this chapter.",
                        color = themeTextSecondary(),
                        textAlign = TextAlign.Center,
                        fontSize = 14.sp
                    )
                    Spacer(Modifier.height(16.dp))
                    Button(
                        onClick = { loadChapter(currentChapterNum) },
                        colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Retry", fontWeight = FontWeight.Bold)
                    }
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
            val rawContent = chapter!!.content ?: ""
            val parsedContent = remember(rawContent) {
                if (rawContent.isBlank()) ""
                else {
                    var text = rawContent.trim()
                    if (text.contains("<") && text.contains(">")) {
                        text = android.text.Html.fromHtml(text, android.text.Html.FROM_HTML_MODE_LEGACY).toString().trim()
                    }
                    text = text.replace("\r\n", "\n").replace("\r", "\n")
                    val lines = text.split(Regex("\n+")).map { it.trim() }.filter { it.isNotEmpty() }
                    lines.joinToString("\n\n")
                }
            }
            
            Text(
                text = parsedContent,
                fontSize = 16.sp,
                color = themeTextPrimary().copy(alpha = 0.92f),
                lineHeight = 30.sp,
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

            Spacer(Modifier.height(32.dp))
        }
    }
}
