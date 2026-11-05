package com.nexiplay.app.data.models

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Notification(
    val id: String = "",
    @SerialName("user_id") val userId: String? = null,
    val title: String,
    val message: String,
    val type: String = "general",
    @SerialName("is_read") val isRead: Boolean = false,
    @SerialName("created_at") val createdAt: String = "",
    @SerialName("deep_link") val deepLink: String? = null,
    @SerialName("action_url") val actionUrl: String? = null,
    @SerialName("content_type") val contentType: String? = null,
    val slug: String? = null,
    @SerialName("poster_url") val posterUrl: String? = null,
    @SerialName("badge_text") val badgeText: String? = null,
)
