package com.nexiplay.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.Comment
import com.nexiplay.app.ui.components.bounceClick
import com.nexiplay.app.ui.theme.*
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentsScreen(navController: NavController) {
    var comments by remember { mutableStateOf<List<Comment>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    fun fetchComments() {
        val user = SupabaseClient.main.auth.currentUserOrNull()
        if (user != null) {
            loading = true
            errorMessage = null
            scope.launch {
                try {
                    val data = SupabaseClient.main.from("comments")
                        .select {
                            filter { eq("user_id", user.id) }
                            order("created_at", order = Order.DESCENDING)
                        }
                        .decodeList<Comment>()
                    comments = data
                } catch (e: Exception) {
                    errorMessage = e.localizedMessage ?: "Failed to load comments"
                }
                loading = false
            }
        } else {
            loading = false
        }
    }

    LaunchedEffect(Unit) {
        fetchComments()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("My Comments", fontFamily = InterFont, fontWeight = FontWeight.Bold) },
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
                com.nexiplay.app.ui.components.CommentsSkeleton()
            }
        } else if (errorMessage != null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                com.nexiplay.app.ui.components.ErrorView(
                    message = errorMessage!!,
                    onRetry = { fetchComments() }
                )
            }
        } else if (comments.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Comment, 
                        contentDescription = null, 
                        modifier = Modifier.size(64.dp), 
                        tint = themeTextTertiary()
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "No Comments Yet", 
                        color = themeTextTertiary(), 
                        fontSize = 16.sp, 
                        fontFamily = InterFont, 
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Your comments on movies/anime will appear here", 
                        color = themeTextTertiary(), 
                        fontSize = 12.sp, 
                        fontFamily = InterFont
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item { Spacer(Modifier.height(8.dp)) }
                items(comments) { comment ->
                    UserCommentItem(comment = comment)
                }
                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}

@Composable
fun UserCommentItem(comment: Comment) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(themeSurface())
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "You commented:",
                color = themeTextTertiary(),
                fontFamily = InterFont,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(4.dp))
            Text(
                comment.message,
                color = themeTextPrimary(),
                fontFamily = InterFont,
                fontSize = 14.sp
            )
            if (comment.createdAt != null) {
                Spacer(Modifier.height(8.dp))
                val date = comment.createdAt.substringBefore("T")
                Text(
                    date,
                    color = themeTextTertiary(),
                    fontFamily = InterFont,
                    fontSize = 11.sp
                )
            }
        }
    }
}
