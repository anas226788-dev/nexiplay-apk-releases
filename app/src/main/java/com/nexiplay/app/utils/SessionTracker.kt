package com.nexiplay.app.utils

import com.nexiplay.app.data.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.auth.auth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.datetime.Clock

@Serializable
data class UserSession(
    val id: String? = null,
    val session_id: String,
    val user_id: String,
    val device_type: String,
    val duration_seconds: Int,
    val created_at: String? = null,
    val last_seen_at: String? = null
)

/**
 * Optimized SessionTracker:
 * Minimizes database writes to protect Supabase free-tier quotas.
 * - Tracks session time locally in memory.
 * - Sends initial session insert on app start.
 * - Sends final duration update on app exit/background.
 * - Gentle 10-minute fallback heartbeat only for long active streaming sessions.
 */
object SessionTracker {
    private var sessionJob: Job? = null
    private var sessionStartTime = 0L
    private var sessionId = java.util.UUID.randomUUID().toString()
    private const val HEARTBEAT_INTERVAL_MS = 10 * 60 * 1000L // 10 minutes fallback (reduced from 60s)

    fun startSession() {
        if (sessionJob?.isActive == true) return
        sessionStartTime = System.currentTimeMillis()
        sessionId = java.util.UUID.randomUUID().toString()
        val activeSessionId = sessionId
        
        sessionJob = CoroutineScope(Dispatchers.IO).launch {
            var user = SupabaseClient.main.auth.currentUserOrNull()
            var retries = 0
            while (user == null && retries < 15) {
                delay(2000) // Wait for Supabase to restore session or user to log in
                user = SupabaseClient.main.auth.currentUserOrNull()
                retries++
            }
            if (user == null) return@launch
            
            // 1. Initial session record on app launch
            try {
                SupabaseClient.main.from("user_sessions").insert(
                    UserSession(
                        session_id = activeSessionId,
                        user_id = user.id,
                        device_type = "android_app",
                        duration_seconds = 0
                    )
                )
                SupabaseClient.main.from("profiles").update(
                    mapOf("last_seen_at" to Clock.System.now().toString())
                ) {
                    filter { eq("id", user.id) }
                }
            } catch (e: Exception) {
                // Non-fatal analytics error
            }

            // 2. Low-frequency safety heartbeat (every 10 minutes, protects against ungraceful OS termination)
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                val currentUser = SupabaseClient.main.auth.currentUserOrNull() ?: break
                val currentDuration = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                try {
                    SupabaseClient.main.from("user_sessions").update(
                        buildJsonObject {
                            put("duration_seconds", currentDuration)
                            put("last_seen_at", Clock.System.now().toString())
                        }
                    ) {
                        filter { eq("session_id", activeSessionId) }
                    }
                    
                    SupabaseClient.main.from("profiles").update(
                        mapOf("last_seen_at" to Clock.System.now().toString())
                    ) {
                        filter { eq("id", currentUser.id) }
                    }
                } catch (e: Exception) {
                    // Non-fatal
                }
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        
        val finalDuration = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
        val currentSessionId = sessionId
        
        // Skip DB update for micro-sessions under 3 seconds
        if (finalDuration < 3) return
        
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val now = Clock.System.now().toString()
                SupabaseClient.main.from("user_sessions").update(
                    buildJsonObject {
                        put("duration_seconds", finalDuration)
                        put("last_seen_at", now)
                    }
                ) {
                    filter { eq("session_id", currentSessionId) }
                }
                
                val currentUser = SupabaseClient.main.auth.currentUserOrNull()
                if (currentUser != null) {
                    SupabaseClient.main.from("profiles").update(
                        mapOf("last_seen_at" to now)
                    ) {
                        filter { eq("id", currentUser.id) }
                    }
                }
            } catch (e: Exception) {
                // Non-fatal
            }
        }
    }
}
