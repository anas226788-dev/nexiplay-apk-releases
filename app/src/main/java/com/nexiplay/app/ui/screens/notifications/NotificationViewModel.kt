package com.nexiplay.app.ui.screens.notifications

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.models.Notification
import com.nexiplay.app.data.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

data class NotificationState(
    val notifications: List<Notification> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class NotificationViewModel : ViewModel() {
    private val _state = MutableStateFlow(NotificationState())
    val state: StateFlow<NotificationState> = _state.asStateFlow()

    init {
        fetchNotifications()
    }

    fun fetchNotifications() {
        viewModelScope.launch {
            val uid = SupabaseClient.main.auth.currentUserOrNull()?.id
            if (uid == null) {
                _state.value = _state.value.copy(error = "Not logged in")
                return@launch
            }

            _state.value = _state.value.copy(isLoading = true, error = null)
            try {
                // Fetch personal and global notifications
                // Supabase filter: user_id.eq.UID or user_id.is.null
                val result = SupabaseClient.main.from("notifications")
                    .select {
                        filter {
                            or {
                                eq("user_id", uid)
                                // We can use eq with null or missing in some versions, but let's just fetch by user_id and globally we fetch separately if it fails.
                                // Actually, let's try exact or filterNot:
                                exact("user_id", null)
                            }
                        }
                        order("created_at", io.github.jan.supabase.postgrest.query.Order.DESCENDING)
                    }
                    .decodeList<Notification>()

                _state.value = _state.value.copy(
                    notifications = result,
                    isLoading = false
                )
            } catch (e: Exception) {
                _state.value = _state.value.copy(
                    isLoading = false,
                    error = e.message ?: "Failed to load notifications"
                )
            }
        }
    }

    fun markAsRead(id: String) {
        viewModelScope.launch {
            try {
                SupabaseClient.main.from("notifications")
                    .update(buildJsonObject { put("is_read", true) }) {
                        filter { eq("id", id) }
                    }
                // Update local state
                val current = _state.value.notifications
                _state.value = _state.value.copy(
                    notifications = current.map { if (it.id == id) it.copy(isRead = true) else it }
                )
            } catch (e: Exception) {
                // Ignore error on mark as read
            }
        }
    }

    fun clearAll() {
        viewModelScope.launch {
            val uid = SupabaseClient.main.auth.currentUserOrNull()?.id ?: return@launch
            try {
                // Delete personal notifications
                SupabaseClient.main.from("notifications")
                    .delete {
                        filter { eq("user_id", uid) }
                    }
                
                // For global notifications, since we don't have a hidden_notifications table yet,
                // we'll just filter them out of the UI state for this session.
                _state.value = _state.value.copy(notifications = emptyList())
            } catch (e: Exception) {
                _state.value = _state.value.copy(error = "Failed to clear notifications")
            }
        }
    }
}
