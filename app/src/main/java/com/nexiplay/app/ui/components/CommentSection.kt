package com.nexiplay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Comment
import com.nexiplay.app.data.model.CoinBalance
import com.nexiplay.app.data.model.CoinTransaction
import com.nexiplay.app.data.repository.SettingsRepository
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch
import kotlinx.serialization.json.*
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext

@Composable
fun CommentSection(movieId: String) {
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var replies by remember { mutableStateOf<Map<String, List<Comment>>>(emptyMap()) }
    var newCommentText by remember { mutableStateOf("") }
    var replyingToId by remember { mutableStateOf<String?>(null) }
    var replyingToName by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val settings = remember { SettingsRepository(context) }
    val user = SupabaseClient.main.auth.currentUserOrNull()

    var profilesMap by remember { mutableStateOf<Map<String, com.nexiplay.app.data.model.UserProfile>>(emptyMap()) }

    // Fetch comments & commenter profiles
    LaunchedEffect(movieId) {
        try {
            val allComments = SupabaseClient.main.from("comments")
                .select {
                    filter { eq("movie_id", movieId) }
                    order("created_at", order = Order.DESCENDING)
                }
                .decodeList<Comment>()

            val commentUserIds = allComments.map { it.userId }.distinct()
            // Always include current user so their avatar + badge shows in the input area
            val currentUserId = user?.id
            val userIds = if (currentUserId != null) {
                (commentUserIds + currentUserId).distinct()
            } else {
                commentUserIds
            }
            if (userIds.isNotEmpty()) {
                try {
                    val pList = SupabaseClient.main.from("profiles")
                        .select(io.github.jan.supabase.postgrest.query.Columns.list(
                            "id", "display_name", "avatar_url", "vip_badge", "vip_badge_expires"
                        )) { filter { isIn("id", userIds) } }
                        .decodeList<com.nexiplay.app.data.model.UserProfile>()
                    profilesMap = pList.associateBy { it.id }
                    android.util.Log.d("CommentSection", "Fetched ${pList.size} profiles, badges: ${pList.map { "${it.id}=${it.vipBadge}" }}")
                } catch (e: Exception) {
                    android.util.Log.e("CommentSection", "Failed to fetch profiles: ${e.message}")
                }
            }

            val topLevel = allComments.filter { it.parentId == null }
            val reps = allComments.filter { it.parentId != null }.groupBy { it.parentId!! }

            comments = topLevel
            replies = reps
        } catch (e: Exception) {
            e.printStackTrace()
        }
        loading = false
    }

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text("Comments", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary(), fontFamily = InterFont)
        Spacer(Modifier.height(12.dp))

        if (user != null) {
            // Input section
            if (replyingToId != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                    Text("Replying to $replyingToName", color = themeTextTertiary(), fontSize = 12.sp, fontFamily = InterFont)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = {
                        replyingToId = null
                        replyingToName = null
                    }) {
                        Text("Cancel", color = NexiRed, fontSize = 12.sp)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                // Check profiles table first, then auth metadata fallback
                val currentUserProfile = profilesMap[user.id]
                val avatarUrl = currentUserProfile?.avatarUrl
                    ?: user.userMetadata?.get("avatar_url")?.jsonPrimitive?.contentOrNull
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Avatar",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(40.dp).clip(CircleShape).background(themeSurface())
                    )
                } else {
                    Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(themeSurface()))
                }
                Spacer(Modifier.width(12.dp))
                OutlinedTextField(
                    value = newCommentText,
                    onValueChange = { newCommentText = it },
                    placeholder = { Text("Add a comment...", color = themeTextTertiary(), fontSize = 14.sp) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(24.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = themeSurface(),
                        unfocusedContainerColor = themeSurface(),
                        focusedBorderColor = NexiRed,
                        unfocusedBorderColor = Color.Transparent,
                        focusedTextColor = themeTextPrimary(),
                        unfocusedTextColor = themeTextPrimary(),
                    ),
                    trailingIcon = {
                        if (newCommentText.isNotBlank()) {
                            IconButton(onClick = {
                                scope.launch {
                                    try {
                                        val now = System.currentTimeMillis()
                                        val thirtyMins = 30 * 60 * 1000L
                                        
                                        // Reset window if 30 minutes passed
                                        if (now - settings.lastCommentTimeMs > thirtyMins) {
                                            settings.commentCountInWindow = 0
                                        }
                                        
                                        // Rate limit check
                                        if (settings.commentCountInWindow >= 2) {
                                            Toast.makeText(context, "Comment limit reached. Please wait 30 minutes.", Toast.LENGTH_LONG).show()
                                            return@launch
                                        }

                                        val metaName = user.userMetadata?.get("display_name")?.jsonPrimitive?.contentOrNull
                                        val newComment = Comment(
                                            movieId = movieId,
                                            userId = user.id,
                                            email = user.email ?: "",
                                            name = metaName ?: user.email?.split("@")?.get(0) ?: "User",
                                            message = newCommentText,
                                            parentId = replyingToId
                                        )
                                        val inserted = SupabaseClient.main.from("comments").insert(newComment) { select() }.decodeSingle<Comment>()
                                        
                                        if (replyingToId != null) {
                                            val currentReplies = replies[replyingToId!!] ?: emptyList()
                                            replies = replies.toMutableMap().apply {
                                                put(replyingToId!!, listOf(inserted) + currentReplies)
                                            }
                                        } else {
                                            comments = listOf(inserted) + comments
                                        }
                                        newCommentText = ""
                                        replyingToId = null
                                        replyingToName = null

                                        // Update Rate Limit
                                        settings.commentCountInWindow += 1
                                        settings.lastCommentTimeMs = now

                                        // Award 1 Coin
                                        try {
                                            val balanceList = SupabaseClient.main.from("coin_balances").select {
                                                filter { eq("user_id", user.id) }
                                            }.decodeList<CoinBalance>()
                                            
                                            val currentBal = balanceList.firstOrNull()?.balance ?: 0
                                            val currentEarned = balanceList.firstOrNull()?.totalEarned ?: 0
                                            val currentSpent = balanceList.firstOrNull()?.totalSpent ?: 0

                                            val balanceJson = buildJsonObject {
                                                put("user_id", JsonPrimitive(user.id))
                                                put("balance", JsonPrimitive(currentBal + 1))
                                                put("total_earned", JsonPrimitive(currentEarned + 1))
                                                put("total_spent", JsonPrimitive(currentSpent))
                                            }
                                            SupabaseClient.main.from("coin_balances").upsert(balanceJson)
                                            
                                            // Add Transaction
                                            val transaction = CoinTransaction(
                                                userId = user.id,
                                                amount = 1,
                                                type = "EARN",
                                                description = "Earned 1 coin for commenting"
                                            )
                                            SupabaseClient.main.from("coin_transactions").insert(transaction)
                                            
                                            Toast.makeText(context, "Comment posted! +1 Coin", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            // Ignore coin failure quietly
                                            Toast.makeText(context, "Comment posted!", Toast.LENGTH_SHORT).show()
                                        }
                                        
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                        Toast.makeText(context, "Failed to post comment", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Send, contentDescription = "Send", tint = NexiRed)
                            }
                        }
                    }
                )
            }
            Spacer(Modifier.height(24.dp))
        } else {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(themeSurface()).padding(16.dp), contentAlignment = Alignment.Center) {
                Text("Login to add a comment", color = themeTextTertiary(), fontFamily = InterFont)
            }
            Spacer(Modifier.height(24.dp))
        }

        if (loading) {
            CircularProgressIndicator(color = NexiRed, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else if (comments.isEmpty()) {
            Text("No comments yet. Be the first to comment!", color = themeTextTertiary(), fontSize = 14.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        } else {
            comments.forEach { comment ->
                CommentItem(
                    comment = comment,
                    userProfile = profilesMap[comment.userId],
                    onReply = {
                        replyingToId = comment.id
                        replyingToName = comment.name
                    }
                )
                // Replies
                replies[comment.id]?.forEach { reply ->
                    Row {
                        Spacer(Modifier.width(36.dp))
                        CommentItem(
                            comment = reply,
                            userProfile = profilesMap[reply.userId],
                            onReply = {
                                replyingToId = comment.id // replies attach to root comment
                                replyingToName = reply.name
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CommentItem(
    comment: Comment,
    userProfile: com.nexiplay.app.data.model.UserProfile? = null,
    onReply: () -> Unit
) {
    val badge = userProfile?.vipBadge?.lowercase()?.trim() ?: ""
    val isElite = badge.contains("elite") || badge == "gold_vip"
    val isVip = !isElite && badge.contains("vip")

    val cardModifier = if (isElite) {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E1B2E))
            .border(1.dp, Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7))), RoundedCornerShape(12.dp))
            .padding(10.dp)
    } else if (isVip) {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF231F14))
            .border(1.dp, Color(0xFFFFD700).copy(alpha = 0.5f), RoundedCornerShape(12.dp))
            .padding(10.dp)
    } else {
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    }

    Row(modifier = cardModifier) {
        val avatarUrl = userProfile?.avatarUrl
        if (!avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = comment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(themeSurface())
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        if (isElite) Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7)))
                        else if (isVip) Brush.linearGradient(listOf(Color(0xFFFFD700), Color(0xFFFFA500)))
                        else Brush.linearGradient(listOf(themeSurface(), themeCard()))
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = comment.name.take(1).uppercase(),
                    color = if (isElite || isVip) Color.Black else themeTextPrimary(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val nameColor = if (isElite) Color(0xFF7EE8FA) else if (isVip) Color(0xFFFFD700) else themeTextPrimary()

                Text(
                    text = userProfile?.displayName ?: comment.name,
                    fontWeight = FontWeight.Bold,
                    color = nameColor,
                    fontSize = 13.sp,
                    fontFamily = InterFont
                )

                if (isElite) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7))))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "💎 ELITE",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                } else if (isVip) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFFFFD700))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "👑 VIP",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color.Black
                        )
                    }
                }
            }

            Spacer(Modifier.height(4.dp))
            Text(comment.message, color = themeTextSecondary(), fontSize = 14.sp, fontFamily = InterFont)
            Spacer(Modifier.height(4.dp))
            Text("Reply", color = themeTextTertiary(), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.bounceClick { onReply() })
        }
    }
}
