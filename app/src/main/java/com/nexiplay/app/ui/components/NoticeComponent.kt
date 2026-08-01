package com.nexiplay.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.core.text.HtmlCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import coil3.compose.AsyncImage
import com.nexiplay.app.data.model.Notice
import com.nexiplay.app.ui.theme.*
import kotlinx.coroutines.delay

// ── Helper: Parse colors from notice ──
private fun parseColor(hex: String?, default: Color): Color {
    return try {
        if (hex?.startsWith("#") == true) Color(android.graphics.Color.parseColor(hex))
        else default
    } catch (e: Exception) {
        default
    }
}

private fun getPlainText(html: String): String {
    return HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_COMPACT).toString().trim()
}

private fun extractYoutubeVideoId(url: String): String {
    return try {
        val pattern = "(?<=watch\\?v=|/videos/|embed/|youtu\\.be/|/v/|/e/|shorts/)[^#&?]*"
        val compiledPattern = java.util.regex.Pattern.compile(pattern)
        val matcher = compiledPattern.matcher(url)
        if (matcher.find()) {
            matcher.group()
        } else {
            url.substringAfterLast("/").substringBefore("?").substringBefore("&")
        }
    } catch (e: Exception) {
        url.substringAfterLast("/").substringBefore("?").substringBefore("&")
    }
}

@Composable
fun NoticeVideoPlayer(videoUrl: String) {
    val context = LocalContext.current
    val isYoutube = videoUrl.contains("youtube.com") || videoUrl.contains("youtu.be")
    
    if (isYoutube) {
        val videoId = extractYoutubeVideoId(videoUrl)
        AndroidView(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(10.dp)),
            factory = { ctx ->
                android.webkit.WebView(ctx).apply {
                    setBackgroundColor(android.graphics.Color.BLACK)
                    webChromeClient = android.webkit.WebChromeClient()
                    webViewClient = android.webkit.WebViewClient()
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mediaPlaybackRequiresUserGesture = false
                    settings.loadWithOverviewMode = true
                    settings.useWideViewPort = true
                    
                    val embedHtml = """
                        <!DOCTYPE html>
                        <html>
                        <head>
                            <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
                            <style>
                                * { margin: 0; padding: 0; box-sizing: border-box; }
                                html, body { width: 100%; height: 100%; background-color: #000000; overflow: hidden; }
                                iframe { width: 100%; height: 100%; border: none; }
                            </style>
                        </head>
                        <body>
                            <iframe src="https://www.youtube.com/embed/$videoId?autoplay=1&mute=0&enablejsapi=1&rel=0&playsinline=1" 
                                    allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share" 
                                    allowfullscreen></iframe>
                        </body>
                        </html>
                    """.trimIndent()
                    loadDataWithBaseURL("https://www.youtube.com", embedHtml, "text/html", "UTF-8", null)
                }
            }
        )
    } else {
        var exoPlayer by remember { mutableStateOf<ExoPlayer?>(null) }

        DisposableEffect(videoUrl) {
            val player = ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(videoUrl))
                prepare()
                playWhenReady = true
                repeatMode = Player.REPEAT_MODE_ALL
                volume = 1f // Unmuted so users can hear it
            }
            exoPlayer = player
            onDispose {
                player.release()
            }
        }

        exoPlayer?.let { player ->
            AndroidView(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(10.dp)),
                factory = {
                    PlayerView(context).apply {
                        this.player = player
                        this.useController = true
                        this.setShowNextButton(false)
                        this.setShowPreviousButton(false)
                        this.resizeMode = AspectRatioFrameLayout.RESIZE_MODE_ZOOM
                    }
                }
            )
        }
    }
}

@Composable
fun RichNoticeContent(notice: Notice, textColor: Color, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        // Video or Image
        if (!notice.videoUrl.isNullOrEmpty()) {
            NoticeVideoPlayer(videoUrl = notice.videoUrl)
            Spacer(Modifier.height(12.dp))
        } else if (!notice.imageUrl.isNullOrEmpty()) {
            AsyncImage(
                model = notice.imageUrl,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 200.dp)
                    .clip(RoundedCornerShape(10.dp)),
                contentScale = ContentScale.Crop
            )
            Spacer(Modifier.height(12.dp))
        }

        // HTML Content (AndroidView TextView)
        val htmlContent = notice.content
        AndroidView(
            modifier = Modifier.fillMaxWidth(),
            factory = { context ->
                android.widget.TextView(context).apply {
                    this.setTextColor(textColor.toArgb())
                    this.textSize = 14f // sp
                    this.movementMethod = android.text.method.LinkMovementMethod.getInstance()
                }
            },
            update = { textView ->
                textView.text = HtmlCompat.fromHtml(htmlContent, HtmlCompat.FROM_HTML_MODE_COMPACT)
            }
        )
    }
}

// ════════════════════════════════════════════════════════════════
// 1. INLINE Notice (embedded inside scroll content)
// ════════════════════════════════════════════════════════════════
@Composable
fun InlineNotice(notice: Notice, onDismiss: () -> Unit = {}, modifier: Modifier = Modifier) {
    val bgColor = parseColor(notice.bgColor, Color(0xFF1E1E1E))
    val textColor = parseColor(notice.textColor, Color.White)
    val plainText = getPlainText(notice.content)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(bgColor)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Info,
            contentDescription = null,
            tint = textColor,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(10.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            RichNoticeContent(notice = notice, textColor = textColor)
        }
        IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
            Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor.copy(alpha = 0.6f), modifier = Modifier.size(16.dp))
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 2. TOP BAR Notice (sticky at the top)
// ════════════════════════════════════════════════════════════════
@Composable
fun TopBarNotice(notice: Notice, onDismiss: () -> Unit = {}) {
    val bgColor = parseColor(notice.bgColor, NexiRed)
    val textColor = parseColor(notice.textColor, Color.White)
    val plainText = getPlainText(notice.content)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .zIndex(10f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plainText,
                color = textColor,
                fontFamily = InterFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 3. BOTTOM BAR Notice (sticky at the bottom)
// ════════════════════════════════════════════════════════════════
@Composable
fun BottomBarNotice(notice: Notice, onDismiss: () -> Unit = {}) {
    val bgColor = parseColor(notice.bgColor, NexiRed)
    val textColor = parseColor(notice.textColor, Color.White)
    val plainText = getPlainText(notice.content)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plainText,
                color = textColor,
                fontFamily = InterFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(8.dp))
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 4. POPUP Notice (modal dialog)
// ════════════════════════════════════════════════════════════════
@Composable
fun PopupNotice(notice: Notice, onDismiss: () -> Unit) {
    val textColor = parseColor(notice.textColor, Color.White)
    val plainText = getPlainText(notice.content)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkCard)
                .padding(20.dp)
        ) {
            Column {
                // Close button
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = "Close", tint = TextGray, modifier = Modifier.size(20.dp))
                    }
                }
                RichNoticeContent(notice = notice, textColor = textColor)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 5. FULLSCREEN Notice (full screen takeover)
// ════════════════════════════════════════════════════════════════
@Composable
fun FullscreenNotice(notice: Notice, onDismiss: () -> Unit) {
    val textColor = parseColor(notice.textColor, Color.White)
    val plainText = getPlainText(notice.content)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.95f)),
            contentAlignment = Alignment.Center
        ) {
            // Close button top-right
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.15f), CircleShape)
            ) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                RichNoticeContent(notice = notice, textColor = textColor)
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 6. MARQUEE Notice (scrolling text)
// ════════════════════════════════════════════════════════════════
@Composable
fun MarqueeNotice(notice: Notice, onDismiss: () -> Unit = {}, isBottom: Boolean = false) {
    val bgColor = parseColor(notice.bgColor, NexiRed)
    val textColor = parseColor(notice.textColor, Color.White)
    val plainText = getPlainText(notice.content)

    val screenWidth = LocalConfiguration.current.screenWidthDp.toFloat()
    val infiniteTransition = rememberInfiniteTransition(label = "marquee")
    val offset by infiniteTransition.animateFloat(
        initialValue = screenWidth,
        targetValue = -screenWidth * 2,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "marqueeScroll"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(bgColor)
            .padding(vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = plainText,
                color = textColor,
                fontFamily = InterFont,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                modifier = Modifier
                    .weight(1f)
                    .offset(x = offset.dp)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.size(20.dp)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(14.dp))
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// 7. TOAST Notice (snackbar-style, auto-dismiss)
// ════════════════════════════════════════════════════════════════
@Composable
fun ToastNotice(notice: Notice, onDismiss: () -> Unit) {
    val bgColor = parseColor(notice.bgColor, DarkCard)
    val textColor = parseColor(notice.textColor, Color.White)
    val plainText = getPlainText(notice.content)

    var visible by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        delay(5000)
        visible = false
        delay(300)
        onDismiss()
    }

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(bgColor)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = NexiRed,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = plainText,
                    color = textColor,
                    fontFamily = InterFont,
                    fontSize = 13.sp,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = { visible = false; onDismiss() }, modifier = Modifier.size(20.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = textColor.copy(alpha = 0.5f), modifier = Modifier.size(14.dp))
                }
            }
        }
    }
}

// ════════════════════════════════════════════════════════════════
// Legacy wrapper (keeps old call sites working)
// ════════════════════════════════════════════════════════════════
@Composable
fun AppNotice(notice: Notice, modifier: Modifier = Modifier) {
    InlineNotice(notice = notice, modifier = modifier)
}
