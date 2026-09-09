package com.nexiplay.app.ui.screens.notifications

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.NotificationsNone
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.nexiplay.app.ui.theme.*
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.DeleteOutline
import com.nexiplay.app.data.models.Notification
import java.time.format.DateTimeFormatter
import java.time.LocalDateTime
import java.time.ZonedDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationsScreen(navController: NavController, viewModel: NotificationViewModel = viewModel()) {
    val state = viewModel.state.collectAsState().value

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Notifications", fontWeight = FontWeight.Bold, color = themeTextPrimary()) },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, "Back", tint = themeTextPrimary())
                    }
                },
                actions = {
                    if (state.notifications.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearAll() }) {
                            Icon(Icons.Default.DeleteOutline, "Clear All", tint = themeTextPrimary())
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = themeSurface()),
            )
        },
        containerColor = themeBg(),
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding)) {
                com.nexiplay.app.ui.components.NotificationsSkeleton()
            }
        } else if (state.notifications.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.NotificationsNone, null, tint = themeTextTertiary(), modifier = Modifier.size(64.dp))
                    Spacer(Modifier.height(16.dp))
                    Text("No Notifications", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = themeTextPrimary())
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "You'll see new episode alerts,\ncoin rewards, and updates here",
                        fontSize = 13.sp, color = themeTextSecondary(), textAlign = TextAlign.Center,
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                items(state.notifications, key = { it.id }) { notification ->
                    NotificationCard(notification, onRead = { viewModel.markAsRead(it) })
                }
            }
        }
    }
}

@Composable
fun NotificationCard(notification: Notification, onRead: (String) -> Unit) {
    val isUnread = !notification.isRead
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isUnread) themeSurface().copy(alpha = 0.8f) else themeBg()
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isUnread) 4.dp else 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (isUnread) {
                    Box(modifier = Modifier.size(8.dp).background(NexiRed, RoundedCornerShape(50)))
                    Spacer(Modifier.width(8.dp))
                }
                Text(
                    text = notification.title,
                    fontWeight = FontWeight.Bold,
                    color = themeTextPrimary(),
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f)
                )
                
                // Format date (simplified)
                val dateStr = notification.createdAt.take(10)
                Text(
                    text = dateStr,
                    color = themeTextTertiary(),
                    fontSize = 12.sp
                )
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = notification.message,
                color = themeTextSecondary(),
                fontSize = 14.sp
            )
            
            if (isUnread) {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = { onRead(notification.id) },
                    colors = ButtonDefaults.buttonColors(containerColor = themeSurface()),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("Mark as Read", fontSize = 12.sp, color = themeTextPrimary())
                }
            }
        }
    }
}
