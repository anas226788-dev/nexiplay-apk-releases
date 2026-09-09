package com.nexiplay.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.History
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
import com.nexiplay.app.data.model.UserEvent
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(navController: NavController) {
    var history by remember { mutableStateOf<List<UserEvent>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetchHistory() {
        val user = SupabaseClient.main.auth.currentUserOrNull()
        if (user != null) {
            loading = true
            errorMessage = null
            scope.launch {
                try {
                    val data = SupabaseClient.main.from("user_events")
                        .select {
                            filter {
                                eq("user_id", user.id)
                                eq("event_type", "watch")
                                eq("deleted_by_user", false)
                            }
                            order("created_at", order = Order.DESCENDING)
                        }
                        .decodeList<UserEvent>()
                    history = data
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Failed to load history"
                }
                loading = false
            }
        } else {
            loading = false
        }
    }

    fun deleteHistoryItem(id: String?) {
        if (id == null) return
        scope.launch {
            try {
                val updateJson = buildJsonObject {
                    put("deleted_by_user", JsonPrimitive(true))
                }
                SupabaseClient.main.from("user_events")
                    .update(updateJson) { filter { eq("id", id) } }
                history = history.filter { it.id != id }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchHistory()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Watch History", fontFamily = InterFont, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = themeBg(),
                    titleContentColor = themeTextPrimary(),
                    navigationIconContentColor = themeTextPrimary()
                )
            )
        },
        containerColor = themeBg(),
    ) { padding ->
        if (loading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                com.nexiplay.app.ui.components.HistorySkeleton()
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                com.nexiplay.app.ui.components.ErrorView(
                    message = errorMessage!!,
                    onRetry = { fetchHistory() }
                )
            }
        } else if (history.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.History, null, tint = themeTextTertiary(), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Watch History", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                    Spacer(Modifier.height(4.dp))
                    Text("Content you watch will appear here", fontSize = 13.sp, color = themeTextSecondary())
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(history, key = { it.id ?: it.hashCode() }) { event ->
                    HistoryItem(
                        event = event,
                        onClick = {
                            val type = event.metadata?.type ?: event.contentType ?: "movie"
                            val slug = event.metadata?.slug ?: ""
                            if (slug.isNotEmpty()) {
                                if (type == "novel") {
                                    navController.navigate("novel_detail/$slug")
                                } else {
                                    navController.navigate(Screen.ContentDetail.createRoute(type, slug))
                                }
                            }
                        },
                        onDelete = { deleteHistoryItem(event.id) }
                    )
                }
            }
        }
    }
}

@Composable
fun HistoryItem(event: UserEvent, onClick: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(themeSurface())
            .clickable(onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = event.metadata?.posterUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(60.dp)
                .height(90.dp)
                .clip(RoundedCornerShape(4.dp))
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                event.metadata?.title ?: event.contentTitle ?: "Unknown Title",
                color = themeTextPrimary(),
                fontFamily = InterFont,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(4.dp))
            val typeStr = (event.metadata?.type ?: event.contentType ?: "movie").replaceFirstChar { it.uppercase() }
            val epDetails = buildString {
                append(typeStr)
                if (event.seasonNumber != null || event.episodeNumber != null) {
                    append(" • ")
                    if (event.seasonNumber != null) append("Season ${event.seasonNumber}")
                    if (event.seasonNumber != null && event.episodeNumber != null) append(", ")
                    if (event.episodeNumber != null) append("Episode ${event.episodeNumber}")
                }
            }
            Text(
                epDetails,
                color = themeTextSecondary(),
                fontFamily = InterFont,
                fontWeight = FontWeight.Medium,
                fontSize = 12.sp
            )
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = themeTextTertiary())
        }
    }
}
