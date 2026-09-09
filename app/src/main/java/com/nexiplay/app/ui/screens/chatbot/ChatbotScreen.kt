package com.nexiplay.app.ui.screens.chatbot

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.nexiplay.app.R
import com.nexiplay.app.data.model.ChatContentResult
import com.nexiplay.app.data.model.ChatMessage
import com.nexiplay.app.data.model.ChatStreamStep
import com.nexiplay.app.data.model.ChatTMDBInfo
import com.nexiplay.app.ui.navigation.Screen
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatbotScreen(
    navController: NavController,
    vm: ChatbotViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val keyboardController = LocalSoftwareKeyboardController.current

    var inputText by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }

    // Rotating subtext
    val headerTexts = listOf("Ask me anything ✨", "Powered by AI 🧠", "Any language 🌍", "Download help 📥", "Movies & Anime 🎬")
    var textIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            textIndex = (textIndex + 1) % headerTexts.size
        }
    }

    // Scroll to bottom when new messages or steps arrive
    LaunchedEffect(state.messages.size, state.currentSteps.size, state.isTyping) {
        val totalCount = state.messages.size + (if (state.isTyping) 1 else 0)
        if (totalCount > 0) {
            listState.animateScrollToItem(totalCount - 1)
        }
    }

    Scaffold(
        topBar = {
            // Header with Red Gradient
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(NexiRedDark, NexiRed)
                        )
                    )
                    .statusBarsPadding()
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Back Button
                        IconButton(
                            onClick = { navController.popBackStack() },
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }

                        // Bot Avatar — App Icon
                        Image(
                            painter = painterResource(id = R.mipmap.ic_launcher),
                            contentDescription = "NexiPlay",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .border(1.5.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                        )

                        // Bot Title & Status
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = state.settings.botName ?: "NexiBot AI",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    fontFamily = InterFont
                                )
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "SMART AI",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        letterSpacing = 0.5.sp
                                    )
                                }
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .background(SuccessGreen, CircleShape)
                                )
                                val subText = if (state.activeAgent != null) {
                                    "Connected: ${vm.formatAgentName(state.activeAgent)}"
                                } else {
                                    headerTexts[textIndex]
                                }
                                Text(
                                    text = subText,
                                    fontSize = 11.sp,
                                    color = Color.White.copy(alpha = 0.9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // Actions: Delete Chat
                    IconButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Clear Chat", tint = Color.White.copy(alpha = 0.9f))
                    }
                }
            }
        },
        containerColor = Color(0xFF0F0F1A)
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // ── Messages Area ──
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.messages) { msg ->
                    ChatMessageItem(
                        message = msg,
                        vm = vm,
                        onContentClick = { type, slug ->
                            navController.navigate(Screen.ContentDetail.createRoute(type, slug))
                        },
                        onContactClick = {
                            navController.navigate(Screen.Contact.route)
                        }
                    )
                }

                // Streaming Step Status Indicator
                if (state.isTyping) {
                    item {
                        ChatStepsIndicator(steps = state.currentSteps)
                    }
                }
            }

            // ── Input Area ──
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF161626),
                tonalElevation = 8.dp,
                shadowElevation = 8.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .navigationBarsPadding()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF0F0F1A), RoundedCornerShape(16.dp))
                            .border(1.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            placeholder = {
                                Text(
                                    "Ask anything or search content...",
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.4f)
                                )
                            },
                            singleLine = true,
                            enabled = !state.isTyping,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = {
                                if (inputText.isNotBlank() && !state.isTyping) {
                                    val text = inputText
                                    inputText = ""
                                    keyboardController?.hide()
                                    vm.sendMessage(text)
                                }
                            }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedContainerColor = Color.Transparent,
                                unfocusedContainerColor = Color.Transparent,
                                focusedBorderColor = Color.Transparent,
                                unfocusedBorderColor = Color.Transparent,
                                disabledBorderColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = {
                                if (inputText.isNotBlank() && !state.isTyping) {
                                    val text = inputText
                                    inputText = ""
                                    keyboardController?.hide()
                                    vm.sendMessage(text)
                                }
                            },
                            enabled = inputText.isNotBlank() && !state.isTyping,
                            modifier = Modifier
                                .size(40.dp)
                                .background(
                                    if (inputText.isNotBlank() && !state.isTyping) NexiRed else Color.White.copy(alpha = 0.1f),
                                    RoundedCornerShape(10.dp)
                                )
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "Send",
                                tint = if (inputText.isNotBlank() && !state.isTyping) Color.White else Color.White.copy(alpha = 0.3f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(4.dp))

                    Text(
                        text = "⚡ AI can make mistakes. Please verify information.",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontFamily = InterFont,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                }
            }
        }
    }

    // Clear History Dialog
    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            containerColor = Color(0xFF1E1E2E),
            title = { Text("Clear Chat History", color = Color.White, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete your chat history?", color = Color.White.copy(alpha = 0.8f)) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteChat()
                    showDeleteConfirmDialog = false
                }) {
                    Text("Clear", color = NexiRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Cancel", color = Color.White.copy(alpha = 0.6f))
                }
            }
        )
    }
}

// ── Single Chat Message Component ──
@Composable
private fun ChatMessageItem(
    message: ChatMessage,
    vm: ChatbotViewModel,
    onContentClick: (String, String) -> Unit,
    onContactClick: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (message.isBot) Alignment.CenterStart else Alignment.CenterEnd
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(0.88f),
            horizontalAlignment = if (message.isBot) Alignment.Start else Alignment.End
        ) {
            Box(
                modifier = Modifier
                    .clip(
                        RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isBot) 4.dp else 16.dp,
                            bottomEnd = if (message.isBot) 16.dp else 4.dp
                        )
                    )
                    .background(
                        if (message.isBot) Color(0xFF1C1C2D) else NexiRed
                    )
                    .border(
                        width = 1.dp,
                        color = if (message.isBot) Color.White.copy(alpha = 0.08f) else Color.Transparent,
                        shape = RoundedCornerShape(
                            topStart = 16.dp,
                            topEnd = 16.dp,
                            bottomStart = if (message.isBot) 4.dp else 16.dp,
                            bottomEnd = if (message.isBot) 16.dp else 4.dp
                        )
                    )
                    .padding(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Main Text
                    if (message.text.isNotBlank()) {
                        Text(
                            text = message.text,
                            fontSize = 14.sp,
                            color = Color.White,
                            lineHeight = 21.sp,
                            fontFamily = InterFont
                        )
                    }

                    // Content Search Results (cards)
                    if (!message.contentResults.isNullOrEmpty()) {
                        Column(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            message.contentResults.forEach { result ->
                                ContentResultCard(result = result, onClick = { onContentClick(result.type, result.slug) })
                            }
                        }
                    }

                    // TMDB Verified Badge
                    if (message.tmdbVerified == true && message.tmdbInfo != null) {
                        TMDBVerifiedBadge(info = message.tmdbInfo)
                    }

                    // Request Submitted Badge
                    if (message.requestSubmitted == true) {
                        RequestSubmittedBadge(onContactClick = onContactClick)
                    }

                    // Not Verified Warning
                    if (message.isNotVerified) {
                        NotVerifiedWarning(onContactClick = onContactClick)
                    }
                }
            }

            // Active Agent Tag under bot message
            if (message.isBot && !message.agent.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(start = 4.dp)
                ) {
                    Box(modifier = Modifier.size(5.dp).background(SuccessGreen, CircleShape))
                    Text(
                        text = "Active Agent: ${vm.formatAgentName(message.agent)}",
                        fontSize = 10.sp,
                        color = Color.White.copy(alpha = 0.4f),
                        fontFamily = InterFont
                    )
                }
            }
        }
    }
}

// ── Content Result Card Component ──
@Composable
private fun ContentResultCard(
    result: ChatContentResult,
    onClick: () -> Unit
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.06f)),
        modifier = Modifier
            .fillMaxWidth()
            .border(0.5.dp, Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            AsyncImage(
                model = result.posterUrl,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(width = 44.dp, height = 62.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF2A2A3D))
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = result.title,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(NexiRed.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = result.type.uppercase(),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = NexiRed
                        )
                    }
                    if (result.releaseYear != null) {
                        Text(
                            text = "${result.releaseYear}",
                            fontSize = 10.sp,
                            color = Color.White.copy(alpha = 0.5f)
                        )
                    }
                }
            }
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = "View",
                tint = NexiRed,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// ── TMDB Verified Badge Component ──
@Composable
private fun TMDBVerifiedBadge(info: ChatTMDBInfo) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E293B).copy(alpha = 0.6f), RoundedCornerShape(10.dp))
            .border(1.dp, Color(0xFF3B82F6).copy(alpha = 0.3f), RoundedCornerShape(10.dp))
            .padding(10.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (!info.poster.isNullOrBlank()) {
                AsyncImage(
                    model = info.poster,
                    contentDescription = info.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(width = 36.dp, height = 50.dp)
                        .clip(RoundedCornerShape(6.dp))
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF3B82F6).copy(alpha = 0.25f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        "✓ VERIFIED",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF60A5FA)
                    )
                }
                Spacer(Modifier.height(4.dp))
                val desc = listOfNotNull(info.title, info.type, info.year).joinToString(" • ")
                Text(
                    text = desc,
                    fontSize = 11.sp,
                    color = Color(0xFF93C5FD),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// ── Request Submitted Badge Component ──
@Composable
private fun RequestSubmittedBadge(onContactClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF451A03).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFF59E0B).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.HourglassTop,
                    contentDescription = null,
                    tint = Color(0xFFFBBF24),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "📥 Request sent to admin",
                    fontSize = 11.sp,
                    color = Color(0xFFFBBF24),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        ContactFallbackText(onContactClick = onContactClick)
    }
}

// ── Not Verified Warning Component ──
@Composable
private fun NotVerifiedWarning(onContactClick: () -> Unit) {
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.padding(top = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF451A1A).copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    Icons.Default.Warning,
                    contentDescription = null,
                    tint = Color(0xFFF87171),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    "⚠️ Could not verify as a real movie/anime/series",
                    fontSize = 11.sp,
                    color = Color(0xFFF87171),
                    fontWeight = FontWeight.Medium
                )
            }
        }
        ContactFallbackText(onContactClick = onContactClick)
    }
}

// ── Contact Page Link Component ──
@Composable
private fun ContactFallbackText(onContactClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF1E3A8A).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .border(0.5.dp, Color(0xFF3B82F6).copy(alpha = 0.2f), RoundedCornerShape(8.dp))
            .padding(8.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🔹 AI আপনার কথা সঠিকভাবে না বুঝলে, ",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
                Text(
                    text = "Contact পেজে",
                    fontSize = 10.sp,
                    color = Color(0xFF60A5FA),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onContactClick() }
                )
                Text(
                    text = " গিয়ে সাবমিট করুন।",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.7f)
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "🔹 If AI didn't understand you, ",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Text(
                    text = "visit Contact page",
                    fontSize = 10.sp,
                    color = Color(0xFF60A5FA),
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clickable { onContactClick() }
                )
                Text(
                    text = " to submit.",
                    fontSize = 10.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
            }
        }
    }
}

// ── Step Indicators Component (while typing) ──
@Composable
private fun ChatStepsIndicator(steps: List<ChatStreamStep>) {
    Box(
        modifier = Modifier
            .fillMaxWidth(0.85f)
            .background(Color(0xFF1C1C2D), RoundedCornerShape(16.dp))
            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
            .padding(12.dp)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (steps.isEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = NexiRed,
                        strokeWidth = 2.dp
                    )
                    Text(
                        "AI is thinking...",
                        fontSize = 12.sp,
                        color = Color.White.copy(alpha = 0.5f)
                    )
                }
            } else {
                steps.forEachIndexed { index, s ->
                    val isDone = index < steps.size - 1
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isDone) {
                            Text("✓", fontSize = 12.sp, color = SuccessGreen, fontWeight = FontWeight.Bold)
                        } else {
                            CircularProgressIndicator(
                                modifier = Modifier.size(12.dp),
                                color = NexiRed,
                                strokeWidth = 2.dp
                            )
                        }
                        Text(
                            text = s.message,
                            fontSize = 12.sp,
                            color = if (isDone) Color.White.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.9f),
                            fontWeight = if (isDone) FontWeight.Normal else FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}
