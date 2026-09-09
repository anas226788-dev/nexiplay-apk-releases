package com.nexiplay.app.ui.screens.chatbot

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nexiplay.app.data.model.ChatApiResponse
import com.nexiplay.app.data.model.ChatMessage
import com.nexiplay.app.data.model.ChatStreamStep
import com.nexiplay.app.data.model.ChatbotSettings
import com.nexiplay.app.data.repository.ChatbotRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ChatbotState(
    val messages: List<ChatMessage> = emptyList(),
    val isTyping: Boolean = false,
    val currentSteps: List<ChatStreamStep> = emptyList(),
    val activeAgent: String? = null,
    val settings: ChatbotSettings = ChatbotSettings(),
    val isSettingsLoaded: Boolean = false
)

class ChatbotViewModel : ViewModel() {

    private val _state = MutableStateFlow(ChatbotState())
    val state = _state.asStateFlow()

    private val welcomeMessage = "Hi! I'm Nexiplay AI Assistant. Ask me anything about movies, anime, series, or how to download — in any language! 😊\n\n💡 Tip: Type a movie/anime name and I'll find it for you!"

    init {
        loadSettings()
        initWelcomeMessage()
    }

    private fun loadSettings() {
        viewModelScope.launch {
            val settings = ChatbotRepository.fetchSettings()
            _state.value = _state.value.copy(settings = settings, isSettingsLoaded = true)
        }
    }

    private fun initWelcomeMessage() {
        if (_state.value.messages.isEmpty()) {
            _state.value = _state.value.copy(
                messages = listOf(
                    ChatMessage(
                        text = welcomeMessage,
                        isBot = true,
                        role = "assistant"
                    )
                )
            )
        }
    }

    fun sendMessage(userText: String) {
        if (userText.isBlank() || _state.value.isTyping) return

        val trimmed = userText.trim()

        // Add user message
        val userMsg = ChatMessage(text = trimmed, isBot = false, role = "user")
        _state.value = _state.value.copy(
            messages = _state.value.messages + userMsg,
            isTyping = true,
            currentSteps = emptyList()
        )

        viewModelScope.launch {
            try {
                // Step 1: Thinking
                addStep("thinking", "🧠 Analyzing your message...")
                delay(900)

                // Build history for context
                val history = getAIHistory()

                // Step 2: Searching
                addStep("searching_local", "🔍 Searching Nexiplay database...")

                // Call API
                val response = ChatbotRepository.sendMessage(trimmed, history)

                // Step 3: Show additional steps based on response
                if (response.intent == "search") {
                    if (response.found == true) {
                        addStep("validating", "🧠 Verifying results match your query...")
                        delay(500)
                    } else {
                        addStep("validating", "🧠 No exact match found in database...")
                        delay(500)
                        if (response.tmdbVerified != null) {
                            addStep("verifying_online", "🌐 Verifying online...")
                            delay(600)
                        }
                        if (response.requestSubmitted == true) {
                            addStep("submitting", "📥 Submitting request to admin...")
                            delay(600)
                        }
                        if (response.tmdbVerified == false) {
                            addStep("not_verified", "❌ Could not verify this title")
                            delay(400)
                        }
                    }
                }

                delay(500)

                // Process result
                handleResult(response)

            } catch (e: Exception) {
                e.printStackTrace()
                addBotMessage(
                    ChatMessage(
                        text = "Sorry, I'm having trouble right now. Please try again! 🙏",
                        isBot = true,
                        role = "assistant"
                    )
                )
            } finally {
                _state.value = _state.value.copy(isTyping = false, currentSteps = emptyList())
            }
        }
    }

    private fun handleResult(data: ChatApiResponse) {
        if (data.error != null && data.reply == null) {
            addBotMessage(ChatMessage(text = data.error, isBot = true, role = "assistant"))
            return
        }

        if (data.agent != null) {
            _state.value = _state.value.copy(activeAgent = data.agent)
        }

        // Search with results found
        if (data.intent == "search" && data.found == true && !data.results.isNullOrEmpty()) {
            addBotMessage(
                ChatMessage(
                    text = "✅ Found on Nexiplay:",
                    isBot = true,
                    role = "assistant",
                    agent = data.agent,
                    contentResults = data.results
                )
            )
            if (!data.reply.isNullOrBlank()) {
                addBotMessage(
                    ChatMessage(
                        text = data.reply,
                        isBot = true,
                        role = "assistant",
                        agent = data.agent
                    )
                )
            }
        }
        // Search not found
        else if (data.intent == "search" && data.found != true) {
            addBotMessage(
                ChatMessage(
                    text = data.reply ?: "Content not found.",
                    isBot = true,
                    role = "assistant",
                    agent = data.agent,
                    tmdbVerified = data.tmdbVerified,
                    tmdbInfo = data.tmdbInfo,
                    requestSubmitted = data.requestSubmitted,
                    isNotVerified = data.tmdbVerified == false
                )
            )
        }
        // General chat
        else {
            addBotMessage(
                ChatMessage(
                    text = data.reply ?: "I'm not sure how to respond to that.",
                    isBot = true,
                    role = "assistant",
                    agent = data.agent
                )
            )
        }
    }

    private fun addStep(step: String, message: String) {
        _state.value = _state.value.copy(
            currentSteps = _state.value.currentSteps + ChatStreamStep(step, message)
        )
    }

    private fun addBotMessage(msg: ChatMessage) {
        _state.value = _state.value.copy(
            messages = _state.value.messages + msg
        )
    }

    private fun getAIHistory(): List<Map<String, String>> {
        return _state.value.messages
            .filter { it.role != null }
            .map { mapOf("role" to (it.role ?: "user"), "content" to it.text) }
    }

    fun deleteChat() {
        _state.value = _state.value.copy(
            messages = listOf(
                ChatMessage(text = welcomeMessage, isBot = true, role = "assistant")
            ),
            activeAgent = null,
            currentSteps = emptyList()
        )
    }

    /**
     * Format agent name: strip "OpenRouter", "Groq", provider paths, and ":free" suffixes
     * so only the clean model name is displayed to users.
     */
    fun formatAgentName(name: String?): String {
        if (name.isNullOrBlank()) return ""
        var cleaned = name.trim()
        if (cleaned.startsWith("OpenRouter (", ignoreCase = true) && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(12, cleaned.length - 1).trim()
        } else if (cleaned.startsWith("Groq (", ignoreCase = true) && cleaned.endsWith(")")) {
            cleaned = cleaned.substring(6, cleaned.length - 1).trim()
        }
        if (cleaned.contains("/")) {
            cleaned = cleaned.substringAfterLast("/")
        }
        return cleaned
            .replace(Regex("^openrouter\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex("^groq\\s*", RegexOption.IGNORE_CASE), "")
            .replace(Regex(":free$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("-free$", RegexOption.IGNORE_CASE), "")
            .replace(Regex("\\s*\\(free\\)$", RegexOption.IGNORE_CASE), "")
            .trim()
    }
}
