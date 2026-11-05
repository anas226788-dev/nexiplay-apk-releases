package com.nexiplay.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Precision
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.ui.theme.*

// Pre-allocated static shapes & brushes for zero-allocation 120fps scrolling
private val CardShape = RoundedCornerShape(14.dp)
private val BadgeShape = RoundedCornerShape(5.dp)
private val CardBgColor = Color(0xFF111116)
private val CardBorderColor = Color.White.copy(alpha = 0.09f)
private val PosterBgColor = Color(0xFF17161D)
private val InfoBoxBgColor = Color(0xFF121217)
private val HairlineColor = Color.White.copy(alpha = 0.05f)
private val HdBadgeBg = Color(0xFF111116).copy(alpha = 0.85f)
private val HdBadgeBorder = Color.White.copy(alpha = 0.15f)
private val PlayPillBg = Color(0xFF111116).copy(alpha = 0.82f)
private val PlayPillBorder = Color.White.copy(alpha = 0.2f)

private val PosterVignetteBrush = Brush.verticalGradient(
    colors = listOf(
        Color.Transparent,
        Color(0xFF111116).copy(alpha = 0.85f)
    )
)

private val PinnedBadgeBrush = Brush.horizontalGradient(
    colors = listOf(Color(0xFFFFD08A), Color(0xFFFFAD66))
)

/**
 * Remembers a downsampled Coil ImageRequest so full-resolution posters are never decoded
 * at 2000x3000px during LazyRow / LazyVerticalGrid scrolling.
 */
@Composable
fun rememberPosterImageRequest(
    url: String?,
    widthPx: Int = 320,
    heightPx: Int = 480
): ImageRequest? {
    val context = LocalContext.current
    return remember(url, widthPx, heightPx) {
        if (url.isNullOrBlank()) null
        else {
            val trimmed = url.trim()
            val resolvedUrl = if (trimmed.startsWith("http://") && !trimmed.contains("localhost")) {
                trimmed.replaceFirst("http://", "https://")
            } else trimmed
            ImageRequest.Builder(context)
                .data(resolvedUrl)
                .size(widthPx, heightPx)
                .precision(Precision.INEXACT)
                .crossfade(true)
                .build()
        }
    }
}

/**
 * Website-Exact MovieCard (Carousel / Horizontal Row)
 * Matches `src/components/MovieCard.tsx` from nexiplay-web-main
 */
@Composable
fun MovieCard(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageRequest = rememberPosterImageRequest(movie.posterUrl, 300, 450)
    val typeUpper = remember(movie.type) { movie.type.ifBlank { "MOVIE" }.uppercase() }

    val effectiveCardBg = if (isDark) CardBgColor else Color.White
    val effectiveCardBorder = if (isDark) CardBorderColor else themeBorder()
    val effectiveInfoBg = if (isDark) InfoBoxBgColor else Color.White
    val effectiveTitleColor = if (isDark) Color(0xFFF3F2F7) else TextBlack
    val effectiveSubColor = if (isDark) Color(0xFF92909A) else TextLightGray
    val effectiveHairline = if (isDark) HairlineColor else themeBorder()

    Column(
        modifier = modifier
            .width(142.dp)
            .clip(CardShape)
            .background(effectiveCardBg)
            .border(1.dp, effectiveCardBorder, CardShape)
            .bounceClick(onClick = onClick)
    ) {
        // 2:3 Poster Image Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(204.dp)
                .background(PosterBgColor)
        ) {
            // High-aesthetic branded placeholder when loading or offline
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.Movies,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF32313D) else Color(0xFFCCC8D6),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = movie.title,
                        fontFamily = InterFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFF6E6C7A) else Color(0xFF9E9AA8),
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Bottom Subtle Gradient Vignette
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .align(Alignment.BottomCenter)
                    .background(PosterVignetteBrush)
            )

            // Top Badges Row (Status on Left, HD / 18+ on Right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(7.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MovieStatusBadge(movie = movie)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movie.isAdult == true) {
                        Box(
                            modifier = Modifier
                                .background(NexiRed.copy(alpha = 0.92f), BadgeShape)
                                .border(0.5.dp, Color.White.copy(alpha = 0.25f), BadgeShape)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "18+",
                                fontFamily = InterFont,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextWhite
                            )
                        }
                    }

                    // Website HD Badge
                    Box(
                        modifier = Modifier
                            .background(HdBadgeBg, BadgeShape)
                            .border(0.5.dp, HdBadgeBorder, BadgeShape)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "HD",
                            fontFamily = InterFont,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE4E2E8),
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            // Subtle Floating Play Pill on Bottom-Right of Poster
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .size(24.dp)
                    .background(PlayPillBg, CircleShape)
                    .border(0.8.dp, PlayPillBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NexiSvgIcons.PlayFilled,
                    contentDescription = "Watch",
                    tint = Color(0xFFB09AFF),
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        // Top hairline divider between poster and info box
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(effectiveHairline)
        )

        // Website Bottom Metadata Container
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(effectiveInfoBg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = movie.title,
                fontFamily = InterFont,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = effectiveTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = typeUpper,
                    fontFamily = InterFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = effectiveSubColor,
                    letterSpacing = 0.4.sp
                )
                if (movie.releaseYear != null) {
                    Text(
                        text = "•",
                        fontFamily = InterFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5D5B66)
                    )
                    Text(
                        text = movie.releaseYear.toString(),
                        fontFamily = InterFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF92909A)
                    )
                }
            }
        }
    }
}

/**
 * Website-Exact MovieCardGrid (Browse, Search, Watchlist Grids)
 * Matches `src/components/MovieCard.tsx` in responsive grid layout
 */
@Composable
fun MovieCardGrid(
    movie: Movie,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val imageRequest = rememberPosterImageRequest(movie.posterUrl, 300, 450)
    val typeUpper = remember(movie.type) { movie.type.ifBlank { "MOVIE" }.uppercase() }

    val effectiveCardBg = if (isDark) CardBgColor else Color.White
    val effectiveCardBorder = if (isDark) CardBorderColor else themeBorder()
    val effectiveInfoBg = if (isDark) InfoBoxBgColor else Color.White
    val effectiveTitleColor = if (isDark) Color(0xFFF3F2F7) else TextBlack
    val effectiveSubColor = if (isDark) Color(0xFF92909A) else TextLightGray
    val effectiveHairline = if (isDark) HairlineColor else themeBorder()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(effectiveCardBg)
            .border(1.dp, effectiveCardBorder, CardShape)
            .bounceClick(onClick = onClick)
    ) {
        // 2:3 Aspect Ratio Poster Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.67f)
                .background(PosterBgColor)
        ) {
            // High-aesthetic branded placeholder when loading or offline
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.padding(8.dp)
                ) {
                    Icon(
                        imageVector = NexiSvgIcons.Movies,
                        contentDescription = null,
                        tint = if (isDark) Color(0xFF32313D) else Color(0xFFCCC8D6),
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = movie.title,
                        fontFamily = InterFont,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isDark) Color(0xFF6E6C7A) else Color(0xFF9E9AA8),
                        maxLines = 2,
                        textAlign = TextAlign.Center,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (imageRequest != null) {
                AsyncImage(
                    model = imageRequest,
                    contentDescription = movie.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            // Bottom Subtle Vignette
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .align(Alignment.BottomCenter)
                    .background(PosterVignetteBrush)
            )

            // Top Badges
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(7.dp)
                    .align(Alignment.TopStart),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                MovieStatusBadge(movie = movie)

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (movie.isAdult == true) {
                        Box(
                            modifier = Modifier
                                .background(NexiRed.copy(alpha = 0.92f), BadgeShape)
                                .border(0.5.dp, Color.White.copy(alpha = 0.25f), BadgeShape)
                                .padding(horizontal = 5.dp, vertical = 2.dp)
                        ) {
                            Text(
                                text = "18+",
                                fontFamily = InterFont,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextWhite
                            )
                        }
                    }

                    Box(
                        modifier = Modifier
                            .background(HdBadgeBg, BadgeShape)
                            .border(0.5.dp, HdBadgeBorder, BadgeShape)
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = "HD",
                            fontFamily = InterFont,
                            fontSize = 8.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFE4E2E8),
                            letterSpacing = 0.4.sp
                        )
                    }
                }
            }

            // Bottom Play Indicator
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(7.dp)
                    .size(24.dp)
                    .background(PlayPillBg, CircleShape)
                    .border(0.8.dp, PlayPillBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = NexiSvgIcons.PlayFilled,
                    contentDescription = "Watch",
                    tint = Color(0xFFB09AFF),
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(effectiveHairline)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(effectiveInfoBg)
                .padding(horizontal = 10.dp, vertical = 8.dp)
        ) {
            Text(
                text = movie.title,
                fontFamily = InterFont,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = effectiveTitleColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                lineHeight = 16.sp,
            )

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(
                    text = typeUpper,
                    fontFamily = InterFont,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = effectiveSubColor,
                    letterSpacing = 0.4.sp
                )
                if (movie.releaseYear != null) {
                    Text(
                        text = "•",
                        fontFamily = InterFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF5D5B66)
                    )
                    Text(
                        text = movie.releaseYear.toString(),
                        fontFamily = InterFont,
                        fontSize = 9.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF92909A)
                    )
                }
            }
        }
    }
}

/**
 * Website-exact top-left status badge using 100% custom SVG icons (zero emojis)
 */
@Composable
private fun MovieStatusBadge(movie: Movie) {
    val isPinned = movie.isPinned == true || movie.adminNote == "pinned"
    val isHot = movie.trendingBadge == true || movie.isTrending == true

    when {
        isPinned -> {
            Row(
                modifier = Modifier
                    .background(PinnedBadgeBrush, BadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = NexiSvgIcons.Pin,
                    contentDescription = "Pinned",
                    tint = Color(0xFF211D2B),
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = "PINNED",
                    fontFamily = InterFont,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF211D2B),
                    letterSpacing = 0.4.sp
                )
            }
        }
        isHot -> {
            Row(
                modifier = Modifier
                    .background(Color(0xFFFF8A32), BadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Icon(
                    imageVector = NexiSvgIcons.Flame,
                    contentDescription = "Trending",
                    tint = Color(0xFF241205),
                    modifier = Modifier.size(9.dp)
                )
                Text(
                    text = "TRENDING",
                    fontFamily = InterFont,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF241205),
                    letterSpacing = 0.4.sp
                )
            }
        }
        movie.isRunning == true -> {
            Row(
                modifier = Modifier
                    .background(Color(0xFF111116).copy(alpha = 0.88f), BadgeShape)
                    .border(0.5.dp, SuccessGreen.copy(alpha = 0.55f), BadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(5.dp)
                        .background(SuccessGreen, CircleShape)
                )
                Text(
                    text = "AIRING",
                    fontFamily = InterFont,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = SuccessGreen,
                    letterSpacing = 0.4.sp
                )
            }
        }
        else -> {
            Box(
                modifier = Modifier
                    .background(Color(0xFF54F28B), BadgeShape)
                    .padding(horizontal = 6.dp, vertical = 2.5.dp)
            ) {
                Text(
                    text = "NEW",
                    fontFamily = InterFont,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF092313),
                    letterSpacing = 0.4.sp
                )
            }
        }
    }
}
