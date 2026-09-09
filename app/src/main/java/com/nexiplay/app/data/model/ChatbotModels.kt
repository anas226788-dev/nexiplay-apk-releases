package com.nexiplay.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Chatbot Settings (from Supabase chatbot_settings table) ──
@Serializable
data class ChatbotSettings(
    @SerialName("is_enabled") val isEnabled: Boolean = true,
    @SerialName("bot_name") val botName: String? = "NexiBot AI",
    @SerialName("welcome_message") val welcomeMessage: String? = null
)

// ── Content result from search ──
@Serializable
data class ChatContentResult(
    val title: String,
    val slug: String,
    val type: String,
    @SerialName("release_year") val releaseYear: Int? = null,
    @SerialName("poster_url") val posterUrl: String? = null
)

// ── TMDB verification info ──
@Serializable
data class ChatTMDBInfo(
    val title: String? = null,
    val type: String? = null,
    val year: String? = null,
    val poster: String? = null
)

// ── Full API response from /api/chat ──
@Serializable
data class ChatApiResponse(
    val reply: String? = null,
    val intent: String? = null,
    val found: Boolean? = null,
    val results: List<ChatContentResult>? = null,
    val requestSubmitted: Boolean? = null,
    val tmdbVerified: Boolean? = null,
    val tmdbInfo: ChatTMDBInfo? = null,
    val agent: String? = null,
    val error: String? = null,
    val isFallback: Boolean? = null
)

// ── Chat message (local UI state) ──
data class ChatMessage(
    val text: String,
    val isBot: Boolean,
    val role: String? = null, // "user" or "assistant"
    val agent: String? = null,
    // Rich content for bot messages
    val contentResults: List<ChatContentResult>? = null,
    val tmdbVerified: Boolean? = null,
    val tmdbInfo: ChatTMDBInfo? = null,
    val requestSubmitted: Boolean? = null,
    val isNotVerified: Boolean = false
)

// ── Step indicator during processing ──
data class ChatStreamStep(
    val step: String,
    val message: String
)
