package com.nexiplay.app.data.repository

import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.ChatApiResponse
import com.nexiplay.app.data.model.ChatbotSettings
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.postgrest.from
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ChatbotRepository {

    private const val CHAT_API_URL = "https://nexiplay.vercel.app/api/chat"

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
    }

    /**
     * Fetch chatbot settings from Supabase.
     */
    suspend fun fetchSettings(): ChatbotSettings {
        return withContext(Dispatchers.IO) {
            try {
                SupabaseClient.main.from("chatbot_settings")
                    .select()
                    .decodeSingleOrNull<ChatbotSettings>()
                    ?: ChatbotSettings()
            } catch (e: Exception) {
                e.printStackTrace()
                ChatbotSettings()
            }
        }
    }

    /**
     * Send a message to the chat API and get a response.
     * Uses the same /api/chat endpoint as the web app (non-streaming mode).
     */
    suspend fun sendMessage(
        message: String,
        history: List<Map<String, String>>
    ): ChatApiResponse {
        return withContext(Dispatchers.IO) {
            try {
                val url = URL(CHAT_API_URL)
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.connectTimeout = 30000
                connection.readTimeout = 60000
                connection.doOutput = true

                // Build request body
                val historyJson = buildString {
                    append("[")
                    history.forEachIndexed { index, entry ->
                        if (index > 0) append(",")
                        val role = entry["role"]?.replace("\"", "\\\"") ?: ""
                        val content = entry["content"]?.replace("\"", "\\\"")?.replace("\n", "\\n") ?: ""
                        append("""{"role":"$role","content":"$content"}""")
                    }
                    append("]")
                }

                val currentUser = SupabaseClient.main.auth.currentUserOrNull()
                val userIdStr = currentUser?.id?.let { "\"$it\"" } ?: "null"
                val userEmailStr = currentUser?.email?.let { "\"$it\"" } ?: "null"

                val requestBody = """{"message":"${message.replace("\"", "\\\"").replace("\n", "\\n")}","history":$historyJson,"streaming":false,"user_id":$userIdStr,"user_email":$userEmailStr}"""

                OutputStreamWriter(connection.outputStream, "UTF-8").use { writer ->
                    writer.write(requestBody)
                    writer.flush()
                }

                val responseCode = connection.responseCode
                val responseBody = if (responseCode in 200..299) {
                    connection.inputStream.bufferedReader().use { it.readText() }
                } else {
                    val errorBody = connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                    throw Exception("API error $responseCode: $errorBody")
                }

                connection.disconnect()

                json.decodeFromString<ChatApiResponse>(responseBody)
            } catch (e: Exception) {
                e.printStackTrace()
                ChatApiResponse(
                    reply = "Sorry, I'm having trouble connecting right now. Please check your internet and try again! 🙏",
                    intent = "general",
                    error = e.message
                )
            }
        }
    }
}
