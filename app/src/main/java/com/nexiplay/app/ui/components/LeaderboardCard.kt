package com.nexiplay.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.nexiplay.app.data.model.LeaderboardEntry
import com.nexiplay.app.ui.theme.*
import java.text.NumberFormat
import java.util.Locale

private val GoldRankColor = Color(0xFFFFC978)
private val GoldRankDeep = Color(0xFFD98A3A)
private val SilverRankColor = Color(0xFFE2E8F0)
private val SilverRankDeep = Color(0xFF94A3B8)
private val BronzeRankColor = Color(0xFFF5A97F)
private val BronzeRankDeep = Color(0xFFB45309)
private val EliteCyan = Color(0xFF7EE8FA)
private val EliteViolet = Color(0xFF9273F5)

private fun formatCardCoins(coins: Int): String =
    NumberFormat.getIntegerInstance(Locale.US).format(coins)

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
                        GoldRankColor.copy(alpha = 0.55f),
                        EliteViolet.copy(alpha = 0.35f),
                        themeBorder()
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = themeCard()
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Brush.linearGradient(listOf(GoldRankColor, GoldRankDeep))),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EmojiEvents,
                        contentDescription = "Leaderboard",
                        tint = Color.Black,
                        modifier = Modifier.size(23.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hall of Fame",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Black,
                            color = themeTextPrimary(),
                            fontFamily = InterFont
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = GoldRankColor.copy(alpha = 0.15f),
                            border = BorderStroke(1.dp, GoldRankColor.copy(alpha = 0.35f))
                        ) {
                            Text(
                                text = "TOP 15",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Black,
                                letterSpacing = 0.6.sp,
                                color = GoldRankColor,
                                fontFamily = InterFont,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Verified real community rankings",
                        fontSize = 11.sp,
                        color = themeTextSecondary(),
                        fontFamily = InterFont
                    )
                }

                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                        contentDescription = "Toggle Expand",
                        tint = themeTextSecondary()
                    )
                }
            }

            if (isLoading) {
                Spacer(Modifier.height(16.dp))
                ShimmerBox(modifier = Modifier.fillMaxWidth().height(140.dp), cornerRadius = 18)
            } else if (entries.isEmpty()) {
                Text(
                    text = "Leaderboard will populate shortly...",
                    color = themeTextTertiary(),
                    fontSize = 13.sp,
                    fontFamily = InterFont,
                    modifier = Modifier.padding(vertical = 24.dp).align(Alignment.CenterHorizontally)
                )
            } else if (expanded) {
                Spacer(Modifier.height(18.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    rank2?.let {
                        PodiumUserItem(
                            entry = it,
                            rankColor = SilverRankColor,
                            rankDeep = SilverRankDeep,
                            avatarSize = 52.dp,
                            pedestalHeight = 98.dp,
                            isWinner = false,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    rank1?.let {
                        PodiumUserItem(
                            entry = it,
                            rankColor = GoldRankColor,
                            rankDeep = GoldRankDeep,
                            avatarSize = 64.dp,
                            pedestalHeight = 116.dp,
                            isWinner = true,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1.14f)
                        )
                    }

                    rank3?.let {
                        PodiumUserItem(
                            entry = it,
                            rankColor = BronzeRankColor,
                            rankDeep = BronzeRankDeep,
                            avatarSize = 48.dp,
                            pedestalHeight = 90.dp,
                            isWinner = false,
                            isCurrentUser = it.userId != null && it.userId == currentUserId,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                if (lowerRanks.isNotEmpty()) {
                    Spacer(Modifier.height(18.dp))
                    HorizontalDivider(color = themeBorder())
                    Spacer(Modifier.height(12.dp))

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
}

@Composable
private fun CardVectorBadge(badgeType: String) {
    val badgeLower = badgeType.lowercase()
    val isElite = badgeLower.contains("elite") || badgeLower == "gold_vip"
    val isVip = !isElite && badgeLower.contains("vip")
    if (!isElite && !isVip) return

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(5.dp))
            .background(
                if (isElite) Brush.linearGradient(listOf(EliteCyan, EliteViolet))
                else Brush.linearGradient(listOf(GoldRankColor, GoldRankDeep))
            )
            .padding(horizontal = 5.dp, vertical = 1.5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Icon(
                imageVector = if (isElite) Icons.Rounded.Diamond else Icons.Rounded.WorkspacePremium,
                contentDescription = null,
                tint = Color(0xFF0B0A10),
                modifier = Modifier.size(9.dp)
            )
            Text(
                text = if (isElite) "ELITE" else "VIP",
                fontSize = 8.sp,
                fontWeight = FontWeight.Black,
                color = Color(0xFF0B0A10),
                fontFamily = InterFont
            )
        }
    }
}

@Composable
private fun PodiumUserItem(
    entry: LeaderboardEntry,
    rankColor: Color,
    rankDeep: Color,
    avatarSize: Dp,
    pedestalHeight: Dp,
    isWinner: Boolean,
    isCurrentUser: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        if (isWinner) {
            Icon(
                imageVector = Icons.Rounded.EmojiEvents,
                contentDescription = null,
                tint = GoldRankColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.height(3.dp))
        } else {
            Text(
                text = "#${entry.rank}",
                fontSize = 11.sp,
                fontWeight = FontWeight.Black,
                color = rankColor,
                fontFamily = InterFont
            )
            Spacer(Modifier.height(2.dp))
        }

        Box(contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(avatarSize + 6.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(rankColor, rankDeep)))
            )

            if (!entry.avatarUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = entry.avatarUrl,
                    contentDescription = entry.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(themeBg())
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(avatarSize)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Color(0xFF2B2542), Color(0xFF171422)))),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = entry.name.take(1).uppercase(),
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isWinner) 19.sp else 15.sp,
                        fontFamily = InterFont
                    )
                }
            }

            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .offset(y = 7.dp)
                    .clip(RoundedCornerShape(50))
                    .background(rankColor)
                    .padding(horizontal = 7.dp, vertical = 1.dp)
            ) {
                Text(
                    text = "#${entry.rank}",
                    color = Color.Black,
                    fontWeight = FontWeight.Black,
                    fontSize = 9.sp,
                    fontFamily = InterFont
                )
            }
        }

        Spacer(Modifier.height(10.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(pedestalHeight)
                .clip(RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 12.dp, bottomEnd = 12.dp))
                .background(
                    Brush.verticalGradient(
                        listOf(
                            rankColor.copy(alpha = if (isWinner) 0.18f else 0.11f),
                            themeBg().copy(alpha = 0.5f)
                        )
                    )
                )
                .border(
                    1.dp,
                    rankColor.copy(alpha = if (isWinner) 0.55f else 0.25f),
                    RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp, bottomStart = 12.dp, bottomEnd = 12.dp)
                )
                .padding(horizontal = 6.dp, vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxSize()
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = if (isCurrentUser) "${entry.name} (YOU)" else entry.name,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = if (isWinner) 12.sp else 11.sp,
                        color = if (isCurrentUser) GoldRankColor else themeTextPrimary(),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        fontFamily = InterFont
                    )
                    Spacer(Modifier.height(3.dp))
                    CardVectorBadge(entry.badgeType)
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "${formatCardCoins(entry.coins)} coins",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = rankColor,
                        maxLines = 1,
                        fontFamily = InterFont
                    )
                    Text(
                        text = "${entry.watchedCount} watched",
                        fontSize = 9.sp,
                        color = themeTextTertiary(),
                        maxLines = 1,
                        fontFamily = InterFont
                    )
                }
            }
        }
    }
}

@Composable
private fun LeaderboardListRow(
    entry: LeaderboardEntry,
    isCurrentUser: Boolean
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (isCurrentUser) GoldRankColor.copy(alpha = 0.12f)
                else themeSurface()
            )
            .border(
                width = if (isCurrentUser) 1.dp else 0.8.dp,
                color = if (isCurrentUser) GoldRankColor.copy(alpha = 0.6f) else themeBorder(),
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Text(
            text = "#${entry.rank}",
            fontSize = 13.sp,
            fontWeight = FontWeight.ExtraBold,
            color = if (isCurrentUser) GoldRankColor else themeTextSecondary(),
            modifier = Modifier.width(30.dp),
            fontFamily = InterFont
        )

        if (!entry.avatarUrl.isNullOrEmpty()) {
            AsyncImage(
                model = entry.avatarUrl,
                contentDescription = entry.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(themeBg())
            )
        } else {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Brush.linearGradient(listOf(Color(0xFF2B2542), Color(0xFF171422)))),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.name.take(1).uppercase(),
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = InterFont
                )
            }
        }

        Spacer(Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = if (isCurrentUser) "${entry.name} (YOU)" else entry.name,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isCurrentUser) GoldRankColor else themeTextPrimary(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                    fontFamily = InterFont
                )
                CardVectorBadge(entry.badgeType)
            }

            Text(
                text = "${entry.watchedCount} watched",
                fontSize = 10.sp,
                color = themeTextTertiary(),
                fontFamily = InterFont
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = GoldRankColor.copy(alpha = 0.12f),
            border = BorderStroke(1.dp, GoldRankColor.copy(alpha = 0.3f))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Star,
                    contentDescription = null,
                    tint = GoldRankColor,
                    modifier = Modifier.size(11.dp)
                )
                Text(
                    text = formatCardCoins(entry.coins),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = GoldRankColor,
                    fontFamily = InterFont
                )
            }
        }
    }
}
