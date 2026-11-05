package com.nexiplay.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// ── Violet Afterglow design tokens ──
val NexiRed = Color(0xFF9273F5)         // Primary violet CTA (legacy name retained)
val NexiRedDark = Color(0xFF5C46B2)
val NexiRedLight = Color(0xFFB09AFF)
val NexiGradientStart = Color(0xFFB09AFF)
val NexiGradientEnd = Color(0xFF5C46B2)

// ── Dark Surfaces (Navy-Black Foundation) ──
val DarkBg = Color(0xFF0D0B12)
val DarkSurface = Color(0xFF17141F)
val DarkSurface2 = Color(0xFF211D2B)
val DarkCard = Color(0xFF17141F)
val DarkBorder = Color(0x1AFFFFFF)      // 10% White Border
val DarkBorderStrong = Color(0x2EFFFFFF)// 18% White Border
val DarkInset = Color(0xFF100E16)

// ── Light Theme Tokens ──
val LightBg = Color(0xFFF7F5FA)          // Ultra-clean soft lavender-white
val LightSurface = Color(0xFFFFFFFF)     // Pure White
val LightCard = Color(0xFFFFFFFF)        // Pure White card
val LightBorder = Color(0xFFE6E1EE)      // Crisp soft border
val LightBorderStrong = Color(0xFFD6CEE3)

// ── Text Tokens ──
val TextWhite = Color(0xFFF4F0FA)
val TextGray = Color(0xFFADA6B9)
val TextDarkGray = Color(0xFF6B7280)    // Tertiary Text
val TextBlack = Color(0xFF120E1C)       // Deep navy-black (high contrast)
val TextLightGray = Color(0xFF6B6577)   // Readable secondary text for light mode

// ── Semantic & Accent Tokens ──
val SuccessGreen = Color(0xFF57D6C0)
val WarningYellow = Color(0xFFFFB878)
val InfoBlue = Color(0xFF3B82F6)
val ErrorRed = Color(0xFFFF7185)
val GoldVip = Color(0xFFFFB878)
val VipPurple = Color(0xFF9273F5)

// ── Coin Center Gold Tokens ──
val CoinGold = Color(0xFFFFB878)
val CoinGoldLight = Color(0xFFFFD6AA)
val CoinGoldDark = Color(0xFFD98A4B)
val CoinBg = Color(0xFF0D0B12)
val CoinCard = Color(0xFF17141F)
val CoinSurface = Color(0xFF100E16)
val CoinBorder = Color(0x33FFB878)
val CoinGoldPrimary = Color(0xFFFFB878)

// ── Reusable Gradient Brushes ──
val ClayRedGradient = Brush.verticalGradient(
    colors = listOf(NexiRedLight, NexiRed, NexiRedDark)
)

val ClayGoldGradient = Brush.verticalGradient(
    colors = listOf(CoinGoldLight, CoinGold, CoinGoldDark)
)

val GlassBorderBrush = Brush.verticalGradient(
    colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.05f))
)

val PosterFadeGradient = Brush.verticalGradient(
    colors = listOf(Color.Transparent, Color(0x8C0D0B12), Color(0xF50D0B12))
)

val GlassSurfaceGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF211D2B).copy(alpha = 0.85f), Color(0xFF0D0B12).copy(alpha = 0.92f))
)
