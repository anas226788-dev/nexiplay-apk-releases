package com.nexiplay.app.ui.screens.vibecheck

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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
import com.nexiplay.app.data.model.Category
import com.nexiplay.app.data.model.MoodProfile
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.repository.VibeAiResponse
import com.nexiplay.app.data.repository.WebFeaturesRepository
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
private data class MovieCatLink(
    @SerialName("movie_id") val movieId: String,
    @SerialName("category_id") val categoryId: String
)

private val DEFAULT_MOOD_PROFILES = listOf(
    MoodProfile(
        id = "feel-good",
        label = "Feel-good ☀️",
        description = "Light, funny stories for a brighter mood.",
        genres = listOf("comedy", "family", "romance")
    ),
    MoodProfile(
        id = "adventure",
        label = "Adventure ⚡",
        description = "Fast, exciting stories with a sense of discovery.",
        genres = listOf("action", "adventure", "fantasy")
    ),
    MoodProfile(
        id = "thoughtful",
        label = "Thoughtful 🌌",
        description = "Character-led drama, mystery and science fiction.",
        genres = listOf("drama", "mystery", "science-fiction", "sci-fi", "thriller")
    ),
    MoodProfile(
        id = "cozy",
        label = "Cozy 🛋️",
        description = "Warm animation and easygoing stories.",
        genres = listOf("animation", "family", "comedy", "slice-of-life")
    )
)

private val QUICK_EMOTIONS = listOf(
    "💔 Heartbroken & Healing" to "Mon kharap, breakup hoyeche, ektu comforting emotional story chai",
    "😂 Stress Relief & Laughs" to "Very stressed and tired, want to laugh out loud with family or friends",
    "⚡ High Hype & Action" to "Bored out of my mind, give me crazy hype thrilling action and adrenaline",
    "🛋️ Cozy & Comforting" to "Feeling lonely and quiet at night, want a wholesome warm anime or movie",
    "🌌 Mind-Bending Twists" to "Want a deep philosophical mystery with insane plot twists that will blow my mind"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibeCheckScreen(navController: NavController) {
    val scope = rememberCoroutineScope()

    var movies by remember { mutableStateOf<List<Movie>>(emptyList()) }
    var genresByMovie by remember { mutableStateOf<Map<String, List<String>>>(emptyMap()) }
    var isLoadingCatalog by remember { mutableStateOf(true) }

    var activeMoodId by remember { mutableStateOf<String?>(null) }
    var activeFormat by remember { mutableStateOf("all") } // "all" | "movie" | "series" | "anime"
    var surpriseSeed by remember { mutableIntStateOf(0) }

    var askPrompt by remember { mutableStateOf("") }
    var askLoading by remember { mutableStateOf(false) }
    var askError by remember { mutableStateOf<String?>(null) }
    var aiReply by remember { mutableStateOf<VibeAiResponse?>(null) }

    LaunchedEffect(Unit) {
        isLoadingCatalog = true
        try {
            val categories = SupabaseClient.main.from("categories")
                .select()
                .decodeList<Category>()
            val slugById = categories.associate { it.id to it.slug.lowercase() }

            val links = try {
                SupabaseClient.main.from("movie_categories")
                    .select()
                    .decodeList<MovieCatLink>()
            } catch (_: Exception) {
                emptyList()
            }

            val map = mutableMapOf<String, MutableList<String>>()
            for (link in links) {
                val slug = slugById[link.categoryId] ?: continue
                map.getOrPut(link.movieId) { mutableListOf() }.add(slug)
            }
            genresByMovie = map

            movies = SupabaseClient.main.from("movies")
                .select {
                    filter { eq("is_adult", false) }
                    order("created_at", Order.DESCENDING)
                    limit(140)
                }
                .decodeList<Movie>()
        } catch (_: Exception) {
        }
        isLoadingCatalog = false
    }

    fun submitAiVibe(promptText: String) {
        val clean = promptText.trim()
        if (clean.isEmpty() || askLoading) return
        scope.launch {
            askLoading = true
            askError = null
            val res = WebFeaturesRepository.askVibeManager(
                prompt = clean,
                profileId = activeMoodId ?: "auto",
                format = activeFormat
            )
            if (!res.error.isNullOrBlank() && res.message.isBlank()) {
                askError = res.error
            } else {
                aiReply = res
            }
            askLoading = false
        }
    }

    val curatedPicks = remember(movies, genresByMovie, activeMoodId, activeFormat, surpriseSeed) {
        val formatFiltered = movies.filter { m ->
            activeFormat == "all" || m.type.equals(activeFormat, ignoreCase = true)
        }
        val profile = DEFAULT_MOOD_PROFILES.find { it.id == activeMoodId }
        val baseList = if (profile == null) {
            formatFiltered
        } else {
            val matched = formatFiltered.map { m ->
                val mGenres = genresByMovie[m.id].orEmpty()
                val score = mGenres.count { g -> profile.genres.any { pg -> g.contains(pg, ignoreCase = true) } }
                m to score
            }.filter { it.second > 0 }
                .sortedByDescending { it.second }
                .map { it.first }
            matched.ifEmpty { formatFiltered }
        }
        if (surpriseSeed > 0) baseList.shuffled() else baseList.take(18)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("✦", color = Color(0xFFA78BFA), fontSize = 18.sp, fontWeight = FontWeight.Black)
                        Column {
                            Text(
                                "AI Vibe Check",
                                fontWeight = FontWeight.Black,
                                fontSize = 18.sp,
                                color = themeTextPrimary(),
                                fontFamily = InterFont
                            )
                            Text(
                                "Emotional Match & Personal Mix",
                                fontSize = 10.sp,
                                color = Color(0xFFA78BFA),
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeBg())
            )
        },
        containerColor = themeBg()
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // ── 1. Ask Nexiplay AI Hero Card ──
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(22.dp))
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF1E1B4B), Color(0xFF0F172A))
                            )
                        )
                        .border(1.dp, Color(0xFF8B5CF6).copy(alpha = 0.4f), RoundedCornerShape(22.dp))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFF8B5CF6).copy(alpha = 0.25f))
                                .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f), RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("✨", fontSize = 18.sp)
                        }
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    "How Are You Feeling Today?",
                                    color = Color.White,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Black
                                )
                            }
                            Text(
                                "Ask in English, Bangla or Banglish — AI curates your mood mix",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 11.sp
                            )
                        }
                    }

                    OutlinedTextField(
                        value = askPrompt,
                        onValueChange = { askPrompt = it },
                        placeholder = {
                            Text(
                                "e.g. Mon kharap ektu comforting kisu chai / Stressed out, need to laugh...",
                                fontSize = 12.sp,
                                color = Color.White.copy(alpha = 0.45f)
                            )
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color(0xFFA78BFA),
                            unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                            focusedContainerColor = Color.Black.copy(alpha = 0.35f),
                            unfocusedContainerColor = Color.Black.copy(alpha = 0.35f),
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Button(
                        onClick = { submitAiVibe(askPrompt) },
                        enabled = !askLoading && askPrompt.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF8B5CF6)),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        if (askLoading) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(8.dp))
                            Text("Matching Your Vibe...", color = Color.White, fontWeight = FontWeight.ExtraBold)
                        } else {
                            Text("✦ Find My Vibe", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                    }

                    // Quick Mood Starter Chips
                    Text(
                        "QUICK MOOD STARTERS:",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Black
                    )
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(QUICK_EMOTIONS) { (label, prompt) ->
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                    .clickable {
                                        askPrompt = prompt
                                        submitAiVibe(prompt)
                                    }
                                    .padding(horizontal = 11.dp, vertical = 7.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    if (!askError.isNullOrBlank()) {
                        Text(
                            text = askError!!,
                            color = Color(0xFFFCA5A5),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // AI Empathetic Reply Box
                    aiReply?.let { reply ->
                        if (reply.message.isNotBlank()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color.Black.copy(alpha = 0.45f))
                                    .border(1.dp, Color(0xFFA78BFA).copy(alpha = 0.4f), RoundedCornerShape(16.dp))
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text("✨", fontSize = 14.sp)
                                    Text(
                                        "NEXIPLAY VIBE CURATOR",
                                        color = Color(0xFFC4B5FD),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black
                                    )
                                }
                                Text(
                                    text = reply.message,
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    lineHeight = 19.sp
                                )

                                val matchedMovies = remember(reply, movies) {
                                    if (reply.movies.isNotEmpty()) reply.movies
                                    else reply.ids.mapNotNull { id -> movies.find { it.id == id } }
                                }

                                if (matchedMovies.isNotEmpty()) {
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        items(matchedMovies, key = { it.id }) { m ->
                                            VibeMovieCard(
                                                movie = m,
                                                onClick = {
                                                    if (m.slug.isNotBlank()) {
                                                        navController.navigate(Screen.ContentDetail.createRoute(m.type, m.slug))
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // ── 2. Choose a Vibe Profile Grid ──
            item {
                Text(
                    "Or Pick a Vibe Profile",
                    color = themeTextPrimary(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Black
                )
                Spacer(Modifier.height(8.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DEFAULT_MOOD_PROFILES.chunked(2).forEach { rowItems ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowItems.forEach { prof ->
                                val selected = activeMoodId == prof.id
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(
                                            if (selected) Color(0xFF8B5CF6).copy(alpha = 0.2f)
                                            else themeSurface()
                                        )
                                        .border(
                                            1.dp,
                                            if (selected) Color(0xFF8B5CF6) else themeBorder(),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .clickable {
                                            activeMoodId = if (selected) null else prof.id
                                        }
                                        .padding(12.dp)
                                ) {
                                    Text(
                                        text = prof.label,
                                        color = if (selected) Color(0xFFC4B5FD) else themeTextPrimary(),
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    )
                                    Spacer(Modifier.height(4.dp))
                                    Text(
                                        text = prof.description,
                                        color = themeTextSecondary(),
                                        fontSize = 11.sp,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        lineHeight = 15.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // ── 3. Format Filter & Surprise Me Bar ──
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("all" to "All", "movie" to "Movies", "series" to "Series", "anime" to "Anime").forEach { (fmt, label) ->
                            val selected = activeFormat == fmt
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (selected) Color(0xFF8B5CF6) else themeSurface())
                                    .border(1.dp, if (selected) Color(0xFFA78BFA) else themeBorder(), RoundedCornerShape(10.dp))
                                    .clickable { activeFormat = fmt }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = label,
                                    color = if (selected) Color.White else themeTextSecondary(),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.18f))
                            .border(1.dp, Color(0xFF10B981).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .clickable { surpriseSeed++ }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "🎲 Shuffle",
                            color = Color(0xFF34D399),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                }
            }

            // ── 4. Curated Picks Grid ──
            if (isLoadingCatalog) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFF8B5CF6))
                    }
                }
            } else {
                val rows = curatedPicks.chunked(3)
                items(rows) { rowMovies ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        rowMovies.forEach { m ->
                            Box(modifier = Modifier.weight(1f)) {
                                VibeMovieCard(
                                    movie = m,
                                    fillWidth = true,
                                    onClick = {
                                        if (m.slug.isNotBlank()) {
                                            navController.navigate(Screen.ContentDetail.createRoute(m.type, m.slug))
                                        }
                                    }
                                )
                            }
                        }
                        repeat(3 - rowMovies.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun VibeMovieCard(
    movie: Movie,
    fillWidth: Boolean = false,
    onClick: () -> Unit
) {
    Column(
        modifier = (if (fillWidth) Modifier.fillMaxWidth() else Modifier.width(115.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.68f)
                .clip(RoundedCornerShape(12.dp))
                .background(themeCard())
                .border(1.dp, themeBorder(), RoundedCornerShape(12.dp))
        ) {
            AsyncImage(
                model = movie.posterUrl,
                contentDescription = movie.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            movie.releaseYear?.let { yr ->
                Surface(
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(6.dp)
                ) {
                    Text(
                        text = yr.toString(),
                        color = Color.White,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(
            text = movie.title,
            color = themeTextPrimary(),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
