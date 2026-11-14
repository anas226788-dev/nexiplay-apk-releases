package com.nexiplay.app.data.util

import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Pure URL selection, cooldown, and redirect classification helpers for Direct Link ads.
 * Kept free of Android framework dependencies so core monetization logic is unit-testable on JVM.
 */
object DirectLinkHelper {
    enum class OverlayUrlAction {
        LOAD_IN_WEBVIEW,
        OPEN_EXTERNAL,
        IGNORE
    }

    fun normalizeHttpUrl(raw: String?): String? {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("http://", ignoreCase = true)
        ) {
            trimmed
        } else {
            null
        }
    }

    fun collectDistinctHttpUrls(vararg rawUrls: String?): List<String> {
        val unique = LinkedHashSet<String>()
        for (raw in rawUrls) {
            normalizeHttpUrl(raw)?.let { unique.add(it) }
        }
        return unique.toList()
    }

    fun selectRotatingUrl(candidates: List<String>, counter: Int): String? {
        if (candidates.isEmpty()) return null
        val index = Math.floorMod(counter, candidates.size)
        return candidates[index]
    }

    fun shouldSkipDirectLinkGate(lastShownAtMs: Long, nowMs: Long, cooldownMs: Long): Boolean {
        if (cooldownMs <= 0L || lastShownAtMs <= 0L || nowMs < lastShownAtMs) return false
        return (nowMs - lastShownAtMs) < cooldownMs
    }

    fun classifyOverlayUrl(url: String?): OverlayUrlAction {
        val trimmed = url?.trim().orEmpty()
        if (trimmed.isBlank() ||
            trimmed.equals("about:blank", ignoreCase = true) ||
            trimmed.startsWith("javascript:", ignoreCase = true) ||
            trimmed.startsWith("data:", ignoreCase = true) ||
            trimmed.startsWith("blob:", ignoreCase = true)
        ) {
            return OverlayUrlAction.IGNORE
        }
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true)
        ) {
            return OverlayUrlAction.LOAD_IN_WEBVIEW
        }
        return OverlayUrlAction.OPEN_EXTERNAL
    }

    fun extractBrowserFallbackUrlFromIntentUri(intentUri: String?): String? {
        val raw = intentUri?.trim().orEmpty()
        if (!raw.startsWith("intent://", ignoreCase = true)) return null
        val fragment = raw.substringAfter("#Intent;", missingDelimiterValue = "")
        if (fragment.isBlank()) return null
        for (part in fragment.split(';')) {
            if (part.startsWith("S.browser_fallback_url=")) {
                val encoded = part.removePrefix("S.browser_fallback_url=")
                val decoded = runCatching {
                    URLDecoder.decode(encoded, StandardCharsets.UTF_8.name())
                }.getOrDefault(encoded)
                return normalizeHttpUrl(decoded)
            }
        }
        return null
    }
}
