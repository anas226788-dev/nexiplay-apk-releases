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

object SessionTracker {
    private var sessionJob: Job? = null
    private var sessionStartTime = 0L
    private var sessionId = java.util.UUID.randomUUID().toString()

    fun startSession() {
        if (sessionJob?.isActive == true) return
        sessionStartTime = System.currentTimeMillis()
        
        sessionJob = CoroutineScope(Dispatchers.IO).launch {
            var user = SupabaseClient.main.auth.currentUserOrNull()
            while (user == null) {
                delay(2000) // Wait for Supabase to restore session or user to log in
                user = SupabaseClient.main.auth.currentUserOrNull()
            }
            
            // Initial insert
            try {
                SupabaseClient.main.from("user_sessions").insert(
                    UserSession(
                        session_id = sessionId,
                        user_id = user.id,
                        device_type = "android_app",
                        duration_seconds = 0
                    )
                )
                // Set online immediately
                SupabaseClient.main.from("profiles").update(
                    mapOf("last_seen_at" to Clock.System.now().toString())
                ) {
                    filter { eq("id", user.id) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }

            // Periodic update
            while(true) {
                delay(60000) // update every minute
                val currentDuration = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
                try {
                    SupabaseClient.main.from("user_sessions").update(
                        buildJsonObject {
                            put("duration_seconds", currentDuration)
                            put("last_seen_at", Clock.System.now().toString())
                        }
                    ) {
                        filter { eq("session_id", sessionId) }
                    }
                    
                    // Update profile's last_seen_at to show as Online
                    SupabaseClient.main.from("profiles").update(
                        mapOf("last_seen_at" to Clock.System.now().toString())
                    ) {
                        filter { eq("id", user.id) }
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun stopSession() {
        sessionJob?.cancel()
        sessionJob = null
        
        val finalDuration = ((System.currentTimeMillis() - sessionStartTime) / 1000).toInt()
        val currentSessionId = sessionId
        CoroutineScope(Dispatchers.IO).launch {
            try {
                SupabaseClient.main.from("user_sessions").update(
                    buildJsonObject {
                        put("duration_seconds", finalDuration)
                        put("last_seen_at", Clock.System.now().toString())
                    }
                ) {
                    filter { eq("session_id", currentSessionId) }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
