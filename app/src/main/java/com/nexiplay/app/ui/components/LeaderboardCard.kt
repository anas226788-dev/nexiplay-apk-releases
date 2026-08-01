package com.nexiplay.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.WorkspacePremium
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nexiplay.app.data.model.LeaderboardEntry
import com.nexiplay.app.ui.theme.*

// Colors for Podium Ranks
private val GoldRankColor = Color(0xFFFFD700)
private val SilverRankColor = Color(0xFFE0E0E0)
private val BronzeRankColor = Color(0xFFCD7F32)

@Composable
fun LeaderboardCard(
    entries: List<LeaderboardEntry>,
    currentUserId: String? = null,
    isLoading: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(true) }

    val rank1 = entries.firstOrNull { it.rank == 1 }
    val rank2 = entries.firstOrNull { it.rank == 2 }
    val rank3 = entries.firstOrNull { it.rank == 3 }
    val lowerRanks = entries.filter { it.rank > 3 }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    listOf(
                        Color(0xFFFFD700).copy(alpha = 0.5f),
                        Color(0xFFA855F7).copy(alpha = 0.3f),
                        Color.White.copy(alpha = 0.1f)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFF12111E).copy(alpha = 0.92f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFFFFD700), Color(0xFFFFA500))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.EmojiEvents,
                        contentDescription = "Leaderboard",
                        tint = Color.Black,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Hall of Fame",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontFamily = InterFont
                        )
                        Spacer(Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = Color(0xFFFFD700).copy(alpha = 0.15f),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFFD700).copy(alpha = 0.3f))
                        ) {
                            Text(
                                text = "TOP 15",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                color = Color(0xFFFFD700),
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Real-time user ranking • Auto syncs every 2 days",
                        fontSize = 11.sp,
                        color = Color.White.copy(alpha = 0.6f),
                        fontFamily = InterFont
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle Expand",
                        tint = Color.White.copy(alpha = 0.7f)
                    )
                }
            }

            if (isLoading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = Color(0xFFFFD700), modifier = Modifier.size(32.dp))
                }
            } else if (entries.isEmpty()) {
                Text(
                    text = "Leaderboard will populate shortly...",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally)
                )
            } else if (expanded) {
                Spacer(Modifier.height(20.dp))

                // Top 3 Podium Section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    // 2nd Place (Left)
                    rank2?.let {
                        PodiumUserItem(
                            entry = it,
                            rankColor = SilverRankColor,
                            crownText = "🥈",
                            avatarSize = 54.dp,
                            isWinner = false,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    // 1st Place (Center, Elevated)
                    rank1?.let {
                        PodiumUserItem(
                            entry = it,
                            rankColor = GoldRankColor,
                            crownText = "👑",
                            avatarSize = 68.dp,
                            isWinner = true,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1.1f)
                        )
                    }

                    // 3rd Place (Right)
                    rank3?.let {
                        PodiumUserItem(
                            entry = it,
                            rankColor = BronzeRankColor,
                            crownText = "🥉",
                            avatarSize = 50.dp,
                            isWinner = false,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(20.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
                Spacer(Modifier.height(12.dp))

                // Ranks 4 to 15 List
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    lowerRanks.forEach { entry ->
                        val isSelf = entry.userId != null && entry.userId == currentUserId
                        LeaderboardListRow(entry = entry, isCurrentUser = isSelf)
                    }
                }
            }
        }
    }
}

@Composable
private fun PodiumUserItem(
    entry: LeaderboardEntry,
    rankColor: Color,
    crownText: String,
    avatarSize: androidx.compose.ui.unit.Dp,
    isWinner: Boolean,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    val badgeLower = entry.badgeType.lowercase()
    val isElite = badgeLower.contains("elite") || badgeLower == "gold_vip"
    val isVip = !isElite && badgeLower.contains("vip")

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(horizontal = 2.dp)
    ) {
        // Crown Icon
        Text(text = crownText, fontSize = if (isWinner) 22.sp else 18.sp)
        Spacer(Modifier.height(2.dp))

        // Avatar Container with Glow & Border
        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(avatarSize + 6.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(rankColor, rankColor.copy(alpha = 0.4f))
                        )
                    )
            )

            if (!entry.avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = entry.avatarUrl,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(Color.Black)
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                listOf(Color(0xFF2A2740), Color(0xFF1A1828))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isWinner) 20.sp else 16.sp
                    )
                }
            }

            // Rank Badge Number
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 8.dp)
                    .clip(CircleShape)
                    .background(rankColor)
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "#${entry.rank}",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(14.dp))

        // User Name
        Text(
            text = if (isCurrentUser) "${entry.name} (YOU)" else entry.name,
            fontWeight = FontWeight.Bold,
            fontSize = if (isWinner) 13.sp else 12.sp,
            color = if (isCurrentUser) Color(0xFFFFD700) else Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(2.dp))

        // VIP/Elite Badge Tag
        if (isElite) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color.Transparent,
                modifier = Modifier.background(
                    Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7))),
                    RoundedCornerShape(4.dp)
                )
            ) {
                Text(
                    text = "💎 ELITE",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        } else if (isVip) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = Color(0xFFFFD700)
            ) {
                Text(
                    text = "👑 VIP",
                    fontSize = 8.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.Black,
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                )
            }
        }

        Spacer(Modifier.height(4.dp))

        // Coins Balance
        Text(
            text = "${entry.coins} 🪙",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFFFD700)
        )

        // Watched Count
        Text(
            text = "${entry.watchedCount} Watched",
            fontSize = 9.sp,
            color = Color.White.copy(alpha = 0.5f)
        )
    }
}

@Composable
private fun LeaderboardListRow(
    entry: LeaderboardEntry,
    isCurrentUser: Boolean
) {
    val badgeLower = entry.badgeType.lowercase()
    val isElite = badgeLower.contains("elite") || badgeLower == "gold_vip"
    val isVip = !isElite && badgeLower.contains("vip")

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrentUser) Color(0xFFFFD700).copy(alpha = 0.12f)
                else Color.White.copy(alpha = 0.04f)
            )
            .border(
                width = if (isCurrentUser) 1.dp else 0.5.dp,
                color = if (isCurrentUser) Color(0xFFFFD700).copy(alpha = 0.6f)
                else Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        // Rank Number
        Text(
            text = "#${entry.rank}",
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            color = if (isCurrentUser) Color(0xFFFFD700) else Color.White.copy(alpha = 0.6f),
            modifier = Modifier.width(28.dp)
        )

        // Avatar
        if (!entry.avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = entry.avatarUrl,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.Black)
            )
        } else {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            listOf(Color(0xFF2A2740), Color(0xFF1A1828))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        // Name + Badge
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (isCurrentUser) "${entry.name} (YOU)" else entry.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isCurrentUser) Color(0xFFFFD700) else Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                if (isElite) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(
                                Brush.linearGradient(listOf(Color(0xFF7EE8FA), Color(0xFFA855F7)))
                            )
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("💎 ELITE", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                } else if (isVip) {
                    Spacer(Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFFFFD700))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text("👑 VIP", fontSize = 8.sp, fontWeight = FontWeight.Black, color = Color.Black)
                    }
                }
            }

            Text(
                text = "${entry.watchedCount} Content Watched",
                fontSize = 10.sp,
                color = Color.White.copy(alpha = 0.5f)
            )
        }

        // Coins Count
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "${entry.coins}",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFFFD700)
            )
            Spacer(Modifier.width(3.dp))
            Text(text = "🪙", fontSize = 11.sp)
        }
    }
}
