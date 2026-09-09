package com.nexiplay.app.ui.screens.request

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RequestContentScreen(navController: NavController) {
    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("movie") }
    var notes by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var submitted by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current
    val types = listOf("movie" to "Movie", "anime" to "Anime", "series" to "Series")

    val inputColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = NexiRed, unfocusedBorderColor = themeBorder(),
        focusedLabelColor = NexiRed, cursorColor = NexiRed,
        focusedTextColor = themeTextPrimary(), unfocusedTextColor = themeTextPrimary(),
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Request Content", fontWeight = FontWeight.Bold, color = themeTextPrimary()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeSurface()),
            )
        },
        containerColor = themeBg(),
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            if (submitted) {
                // Success state
                Box(
                    modifier = Modifier.fillMaxWidth()
                        .background(SuccessGreen.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                        .padding(32.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.Check, null, tint = SuccessGreen, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(12.dp))
                        Text("Request Submitted!", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                        Text("We'll try to add it soon", fontSize = 13.sp, color = themeTextSecondary())
                        Spacer(Modifier.height(16.dp))
                        Button(
                            onClick = { submitted = false; title = ""; notes = "" },
                            colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                            shape = RoundedCornerShape(12.dp),
                        ) { Text("Request Another") }
                    }
                }
                return@Column
            }

            Text("What do you want to watch?", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
            Spacer(Modifier.height(4.dp))
            Text("Request a movie, anime, or series and we'll try to add it", fontSize = 13.sp, color = themeTextSecondary())
            Spacer(Modifier.height(20.dp))

            if (error != null) {
                Box(
                    Modifier.fillMaxWidth()
                        .background(NexiRed.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) { Text(error!!, fontSize = 13.sp, color = NexiRedLight) }
                Spacer(Modifier.height(12.dp))
            }

            // Title
            OutlinedTextField(
                value = title, onValueChange = { title = it },
                label = { Text("Title *") }, singleLine = true,
                placeholder = { Text("e.g. Attack on Titan") },
                colors = inputColors, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))

            // Type selector
            Text("Type", fontSize = 13.sp, color = themeTextSecondary())
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                types.forEach { (value, label) ->
                    val selected = selectedType == value
                    FilterChip(
                        selected = selected,
                        onClick = { selectedType = value },
                        label = { Text(label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = NexiRed,
                            selectedLabelColor = themeTextPrimary(),
                            containerColor = themeCard(),
                            labelColor = themeTextSecondary(),
                        ),
                    )
                }
            }
            Spacer(Modifier.height(12.dp))

            // Notes
            OutlinedTextField(
                value = notes, onValueChange = { notes = it },
                label = { Text("Notes (optional)") },
                placeholder = { Text("Any specific season, quality, etc.") },
                colors = inputColors, shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(120.dp),
                maxLines = 4,
            )
            Spacer(Modifier.height(24.dp))

            // Submit
            Button(
                onClick = {
                    loading = true
                    error = null
                    android.widget.Toast.makeText(context, "Loading Ad...", android.widget.Toast.LENGTH_SHORT).show()
                    com.nexiplay.app.data.util.AdManager.showRewardedAd(
                        context = context,
                        onRewarded = {
                            scope.launch {
                                val user = SupabaseClient.main.auth.currentUserOrNull()
                                if (user == null) {
                                    error = "Please login first"
                                    loading = false
                                    return@launch
                                }
                                try {
                                    val userMetadata = mapOf(
                                        "user_id" to user.id,
                                        "user_email" to user.email,
                                        "type" to selectedType,
                                        "notes" to notes.trim().ifEmpty { null },
                                        "source" to "android_app"
                                    )
                                    val insertMap = mapOf(
                                        "content_name" to title.trim(),
                                        "status" to "pending",
                                        "scraped_data" to userMetadata
                                    )
                                    SupabaseClient.main.from("content_requests").insert(insertMap)
                                    submitted = true
                                } catch (e: Exception) {
                                    error = e.message ?: "Failed to submit"
                                }
                                loading = false
                            }
                        },
                        onFailed = {
                            loading = false
                            android.widget.Toast.makeText(context, "Failed to load Ad", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    )
                },
                enabled = !loading && title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = NexiRed),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                if (loading) CircularProgressIndicator(color = themeTextPrimary(), modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                else Text("Submit Request", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
    }
}
