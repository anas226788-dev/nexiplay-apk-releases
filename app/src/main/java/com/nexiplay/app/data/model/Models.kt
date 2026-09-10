package com.nexiplay.app.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Movie(
    val id: String,
    val title: String,
    val slug: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    val description: String? = null,
    val type: String, // "movie", "series", "anime"
    @SerialName("release_year") val releaseYear: Int? = null,
    val language: String? = null,
    val source: String? = null,
    @SerialName("cast_members") val castMembers: String? = null,
    val format: String? = null,
    val subtitle: String? = null,
    @SerialName("trailer_url") val trailerUrl: String? = null,
    @SerialName("is_running") val isRunning: Boolean? = false,
    @SerialName("last_episode") val lastEpisode: Int? = null,
    @SerialName("next_episode") val nextEpisode: Int? = null,
    @SerialName("is_trending") val isTrending: Boolean? = false,
    @SerialName("trending_rank") val trendingRank: Int? = null,
    @SerialName("banner_url_desktop") val bannerUrlDesktop: String? = null,
    @SerialName("banner_url_mobile") val bannerUrlMobile: String? = null,
    @SerialName("running_status") val runningStatus: String? = null,
    @SerialName("running_notice") val runningNotice: String? = null,
    @SerialName("next_episode_date") val nextEpisodeDate: String? = null,
    @SerialName("is_adult") val isAdult: Boolean? = false,
    @SerialName("ad_link") val adLink: String? = null,
    @SerialName("streaming_url") val streamingUrl: String? = null,
    @SerialName("tmdb_id") val tmdbId: String? = null,
    @SerialName("app_streaming_enabled") val appStreamingEnabled: Boolean? = true,
    @SerialName("notice_enabled") val noticeEnabled: Boolean? = false,
    @SerialName("notice_text") val noticeText: String? = null,
    @SerialName("admin_note") val adminNote: String? = null,
    @SerialName("is_pinned") val isPinned: Boolean? = false,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class Category(
    val id: String,
    val name: String,
    val slug: String,
)

@Serializable
data class Download(
    val id: String,
    @SerialName("movie_id") val movieId: String,
    val quality: String,
    @SerialName("file_size") val fileSize: String? = null,
    @SerialName("file_url") val fileUrl: String? = null,
)

@Serializable
data class DownloadLink(
    val id: String? = null,
    @SerialName("movie_id") val movieId: String? = null,
    val resolution: String,
    @SerialName("file_size") val fileSize: String? = null,
    @SerialName("mega_link") val megaLink: String? = null,
    @SerialName("gdrive_link") val gdriveLink: String? = null,
    @SerialName("mediafire_link") val mediafireLink: String? = null,
    @SerialName("terabox_link") val teraboxLink: String? = null,
    @SerialName("pcloud_link") val pcloudLink: String? = null,
    @SerialName("youtube_link") val youtubeLink: String? = null,
    @SerialName("pixeldrain_link") val pixeldrainLink: String? = null,
)

@Serializable
data class Season(
    val id: String,
    @SerialName("movie_id") val movieId: String,
    @SerialName("season_number") val seasonNumber: Int,
    @SerialName("season_title") val seasonTitle: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("season_zip_link") val seasonZipLink: String? = null,
)

@Serializable
data class Episode(
    val id: String,
    @SerialName("season_id") val seasonId: String,
    @SerialName("episode_number") val episodeNumber: Int,
    @SerialName("episode_title") val episodeTitle: String? = null,
    @SerialName("streaming_url") val streamingUrl: String? = null,
    @SerialName("streaming_url_toonplay") val streamingUrlToonplay: String? = null,
    @SerialName("streaming_url_animerulz") val streamingUrlAnimerulz: String? = null,
)

@Serializable
data class EpisodeDownloadLink(
    val id: String? = null,
    @SerialName("episode_id") val episodeId: String? = null,
    val resolution: String,
    @SerialName("file_size") val fileSize: String? = null,
    @SerialName("mega_link") val megaLink: String? = null,
    @SerialName("gdrive_link") val gdriveLink: String? = null,
    @SerialName("mediafire_link") val mediafireLink: String? = null,
    @SerialName("terabox_link") val teraboxLink: String? = null,
    @SerialName("pcloud_link") val pcloudLink: String? = null,
    @SerialName("youtube_link") val youtubeLink: String? = null,
    @SerialName("pixeldrain_link") val pixeldrainLink: String? = null,
)

@Serializable
data class Upcoming(
    val id: String,
    val title: String,
    val slug: String,
    @SerialName("poster_url") val posterUrl: String,
    val type: String,
    @SerialName("release_date") val releaseDate: String,
    val status: String,
    @SerialName("trailer_url") val trailerUrl: String? = null,
)

@Serializable
data class UpdateItem(
    val id: String,
    val title: String,
    val slug: String,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("content_type") val contentType: String? = "movie", // anime, series, movie
    @SerialName("update_type") val updateType: String? = "movie", // movie, season, episode
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    @SerialName("is_active") val isActive: Boolean? = true,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null,
)

@Serializable
data class Novel(
    val id: String = "",
    val title: String = "",
    val slug: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    val author: String? = null,
    val status: String? = null,
    @SerialName("total_chapters") val totalChapters: Int? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class NovelChapter(
    val id: String = "",
    @SerialName("novel_id") val novelId: String = "",
    val title: String = "",
    val slug: String = "",
    val content: String? = null,
    @SerialName("chapter_number") val chapterNumber: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
)

// ── Coin System Models ──

@Serializable
data class CoinBalance(
    @SerialName("user_id") val userId: String,
    val balance: Int = 0,
    @SerialName("total_earned") val totalEarned: Int = 0,
    @SerialName("total_spent") val totalSpent: Int = 0,
)

@Serializable
data class CoinTransaction(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    val amount: Int,
    val type: String,
    val description: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
)

@Serializable
data class CoinStreak(
    @SerialName("user_id") val userId: String,
    @SerialName("current_streak") val currentStreak: Int = 0,
    @SerialName("longest_streak") val longestStreak: Int = 0,
    @SerialName("last_login_date") val lastLoginDate: String? = null,
    @SerialName("last_daily_claim") val lastDailyClaim: String? = null,
)

@Serializable
data class UserProfile(
    val id: String,
    val email: String? = null,
    @SerialName("display_name") val displayName: String? = null,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("whatsapp_number") val whatsappNumber: String? = null,
    @SerialName("hide_nsfw") val hideNsfw: Boolean? = false,
    @SerialName("referral_code") val referralCode: String? = null,
    @SerialName("referred_by") val referredBy: String? = null,
    @SerialName("vip_badge") val vipBadge: String? = null,
    @SerialName("vip_badge_expires") val vipBadgeExpires: String? = null,
    @SerialName("ad_free_until") val adFreeUntil: String? = null,
)

@Serializable
data class StreamingRow(
    val id: String? = null,
    @SerialName("movie_id") val movieId: String,
    @SerialName("tmdb_id") val tmdbId: String? = null,
    @SerialName("imdb_id") val imdbId: String? = null,
    @SerialName("mal_id") val malId: String? = null,
    @SerialName("streaming_url") val streamingUrl: String? = null,
    @SerialName("streaming_url_toonplay") val streamingUrlToonplay: String? = null,
    @SerialName("streaming_url_animerulz") val streamingUrlAnimerulz: String? = null,
    @SerialName("toonplay_url") val toonplayUrl: String? = null,
    @SerialName("animerulz_url") val animerulzUrl: String? = null,
    @SerialName("is_disabled") val isDisabled: Boolean? = false,
    @SerialName("multi_scraper_config") val multiScraperConfig: String? = null,
)

@Serializable
data class EventMetadata(
    val title: String? = null,
    val slug: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    val type: String? = null,
    val source: String? = null
)

@Serializable
data class UserEvent(
    val id: String? = null,
    @SerialName("user_id") val userId: String,
    @SerialName("event_type") val eventType: String,
    @SerialName("movie_id") val movieId: String,
    @SerialName("episode_id") val episodeId: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("content_title") val contentTitle: String? = null,
    @SerialName("season_number") val seasonNumber: Int? = null,
    @SerialName("episode_number") val episodeNumber: Int? = null,
    val metadata: EventMetadata? = null,
    val provider: String? = null,
    val resolution: String? = null,
    @SerialName("duration_seconds") val durationSeconds: Int? = null,
    @SerialName("deleted_by_user") val deletedByUser: Boolean = false,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Comment(
    val id: String? = null,
    @SerialName("movie_id") val movieId: String,
    @SerialName("user_id") val userId: String,
    val name: String,
    val email: String,
    val message: String,
    @SerialName("parent_id") val parentId: String? = null,
    @SerialName("is_approved") val isApproved: Boolean = true,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class Notice(
    val id: String,
    val content: String,
    @SerialName("image_url") val imageUrl: String? = null,
    @SerialName("video_url") val videoUrl: String? = null,
    val type: String,
    val platform: String,
    val pages: String,
    @SerialName("movie_id") val movieId: String? = null,
    @SerialName("is_active") val isActive: Boolean,
    @SerialName("bg_color") val bgColor: String? = null,
    @SerialName("text_color") val textColor: String? = null,
    @SerialName("created_at") val createdAt: String? = null
)

@Serializable
data class LeaderboardEntry(
    val id: String? = null,
    val rank: Int,
    @SerialName("user_id") val userId: String? = null,
    val name: String,
    @SerialName("avatar_url") val avatarUrl: String? = null,
    @SerialName("badge_type") val badgeType: String = "none",
    val coins: Int = 0,
    @SerialName("watched_count") val watchedCount: Int = 0,
    @SerialName("is_fake") val isFake: Boolean = false,
    @SerialName("updated_at") val updatedAt: String? = null
)
