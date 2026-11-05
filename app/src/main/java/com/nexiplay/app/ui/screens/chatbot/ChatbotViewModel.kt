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
                // Step 1: Thinking / Understanding message
                addStep("thinking", "🧠 Understanding your message...")

                // Build history for context
                val history = getAIHistory()

                // Call API first (same as website)
                val response = ChatbotRepository.sendMessage(trimmed, history)

                // Step 2: ONLY show catalog search step if intent is "search"
                if (response.intent == "search") {
                    addStep("searching_local", "🔍 Searching Nexiplay catalog & database...")
                    delay(400)

                    val hasAvailable = response.visualOptions?.any { it.available } == true ||
                            (response.found == true && !response.results.isNullOrEmpty())

                    if (hasAvailable) {
                        addStep("validating", "✓ Found title in Nexiplay!")
                        delay(250)
                    } else if (response.autoRequested == true || response.requestSubmitted == true) {
                        addStep("submitting", "📥 Request auto-submitted to admin queue 🚀")
                        delay(350)
                    }
                }

                // If intent is "chat", NO search steps are shown! Instant conversational reply.

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

        // Map visual_options from modern /api/chat (or legacy results)
        val availableCards: List<ChatContentResult> = data.visualOptions?.mapNotNull { opt ->
            val cleanSlug = opt.slug?.takeIf { it.isNotBlank() }
                ?: opt.title.lowercase().replace("[^a-z0-9]+".toRegex(), "-").trim('-')
            val year = when (val ry = opt.releaseYear) {
                is kotlinx.serialization.json.JsonPrimitive -> ry.content.toIntOrNull()
                else -> null
            }
            ChatContentResult(
                title = opt.title,
                slug = cleanSlug,
                type = opt.type,
                releaseYear = year,
                posterUrl = opt.posterUrl
            )
        } ?: data.results ?: emptyList()

        if (data.intent == "search") {
            val mainReply = data.reply ?: if (availableCards.isNotEmpty()) {
                "Here is what I found on Nexiplay:"
            } else {
                "Could not find this title on Nexiplay."
            }

            addBotMessage(
                ChatMessage(
                    text = mainReply,
                    isBot = true,
                    role = "assistant",
                    agent = data.agent,
                    contentResults = availableCards.takeIf { it.isNotEmpty() },
                    requestSubmitted = data.autoRequested == true || data.requestSubmitted == true
                )
            )
        } else {
            // Conversational chat reply (Greetings like "hi", small talk, recommendations, etc.)
            addBotMessage(
                ChatMessage(
                    text = data.reply ?: "Hi! How can I assist you today with movies, series, anime, or novels? 😊",
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
