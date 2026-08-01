package com.nexiplay.app.data.util

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Mirrors the web app's /api/resolve-toonplay server-side API.
 * Resolves a Toonplay/AnimeSalt series ID + season + episode into
 * a direct .m3u8 streaming URL that ExoPlayer can play natively.
 *
 * Resolution steps:
 * 1. Test cached_url (if provided) with a HEAD request
 * 2. Fetch series info from AnimeSalt API
 * 3. Find the target episode
 * 4. Extract the video player URL
 * 5. Get the actual .m3u8 stream URL
 */
object ToonplayResolver {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36"
    private const val REFERER = "https://toonplay.in/"
    private const val TIMEOUT = 10_000

    data class ResolveResult(
        val url: String,
        val source: String, // "cache", "fresh"
        val languages: List<String> = emptyList(),
        val error: String? = null
    )

    /**
     * Main entry point. Call from a coroutine (IO dispatcher).
     *
     * @param toonplayId   AnimeSalt series ID, e.g. "series-tomo-chan-is-a-girl"
     * @param season       Season number (default 1)
     * @param episode      Episode number (required)
     * @param cachedUrl    A previously cached .m3u8 URL to test first
     */
    suspend fun resolve(
        toonplayId: String?,
        season: Int = 1,
        episode: Int,
        cachedUrl: String? = null
    ): ResolveResult = withContext(Dispatchers.IO) {

        // ── Step 0: Test cached URL ──
        if (!cachedUrl.isNullOrBlank()) {
            try {
                val conn = URL(cachedUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "HEAD"
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty("Referer", REFERER)
                conn.connectTimeout = 5000
                conn.readTimeout = 5000
                if (conn.responseCode in 200..299) {
                    conn.disconnect()
                    return@withContext ResolveResult(url = cachedUrl, source = "cache")
                }
                conn.disconnect()
            } catch (_: Exception) {
                // cache expired, continue
            }
        }

        if (toonplayId.isNullOrBlank() || episode <= 0) {
            return@withContext ResolveResult(url = cachedUrl ?: "", source = "fallback", error = "Missing toonplayId or episode")
        }

        try {
            // ── Step 1: Get series info from AnimeSalt ──
            val infoUrl = "https://animesalt.streamindia.co.in/api/info?id=${URLEncoder.encode(toonplayId, "UTF-8")}"
            val infoJson = httpGet(infoUrl)
            val anime = infoJson.optJSONObject("anime")
                ?: return@withContext fallback(cachedUrl, "Anime not found on AnimeSalt")

            // ── Step 2: Find the target episode ──
            val seasonsList = anime.optJSONArray("seasonsList") ?: org.json.JSONArray()
            var targetEp: JSONObject? = null

            // First try exact season match
            for (i in 0 until seasonsList.length()) {
                val s = seasonsList.getJSONObject(i)
                val sNum = (s.optString("season", "1")).toIntOrNull() ?: 1
                if (sNum == season) {
                    val eps = s.optJSONArray("episodes") ?: continue
                    for (j in 0 until eps.length()) {
                        val ep = eps.getJSONObject(j)
                        if (ep.optInt("number", -1) == episode) {
                            targetEp = ep
                            break
                        }
                    }
                    break
                }
            }

            // Fallback: search all seasons
            if (targetEp == null) {
                for (i in 0 until seasonsList.length()) {
                    val s = seasonsList.getJSONObject(i)
                    val eps = s.optJSONArray("episodes") ?: continue
                    for (j in 0 until eps.length()) {
                        val ep = eps.getJSONObject(j)
                        if (ep.optInt("number", -1) == episode) {
                            targetEp = ep
                            break
                        }
                    }
                    if (targetEp != null) break
                }
            }

            if (targetEp == null) {
                return@withContext fallback(cachedUrl, "Episode S${season}E${episode} not found")
            }

            // ── Step 3: Extract video player URL ──
            val epId = targetEp.optString("id", "")
            val episodeUrl = if (epId.startsWith("http")) epId else "https://animesalt.ac/$epId"

            val extractUrl = "https://anime.streamindia.co.in/api/extract?url=${URLEncoder.encode(episodeUrl, "UTF-8")}"
            val extractJson = httpGet(extractUrl)
            val playerUrl = extractJson.optJSONObject("data")?.optString("videoPlayerUrl", null)
                ?: return@withContext fallback(cachedUrl, "No video player URL found")

            // ── Step 4: Get the actual .m3u8 stream URL ──
            val streamUrl = "https://extract.streamindia.co.in/api?url=${URLEncoder.encode(playerUrl, "UTF-8")}"
            val streamJson = httpGet(streamUrl)
            val files = streamJson.optJSONObject("files")
                ?: return@withContext fallback(cachedUrl, "No streaming files found")

            // Prefer Hindi > English > Japanese > first available
            val m3u8 = files.optString("hin", null)
                ?: files.optString("eng", null)
                ?: files.optString("jpn", null)
                ?: run {
                    val keys = files.keys()
                    if (keys.hasNext()) files.optString(keys.next(), null) else null
                }

            if (m3u8.isNullOrBlank()) {
                return@withContext fallback(cachedUrl, "No streaming files found")
            }

            val languages = mutableListOf<String>()
            val keys = files.keys()
            while (keys.hasNext()) languages.add(keys.next())

            ResolveResult(url = m3u8, source = "fresh", languages = languages)
        } catch (e: Exception) {
            fallback(cachedUrl, e.message ?: "Unknown error")
        }
    }

    private fun fallback(cachedUrl: String?, error: String): ResolveResult {
        return if (!cachedUrl.isNullOrBlank()) {
            ResolveResult(url = cachedUrl, source = "fallback", error = error)
        } else {
            ResolveResult(url = "", source = "error", error = error)
        }
    }

    private fun httpGet(urlString: String): JSONObject {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Referer", REFERER)
        conn.setRequestProperty("Origin", "https://toonplay.in")
        conn.connectTimeout = TIMEOUT
        conn.readTimeout = TIMEOUT
        conn.instanceFollowRedirects = true

        val responseCode = conn.responseCode
        val stream = if (responseCode in 200..299) conn.inputStream else conn.errorStream
        val body = BufferedReader(InputStreamReader(stream)).use { it.readText() }
        conn.disconnect()

        if (responseCode !in 200..299) {
            throw Exception("HTTP $responseCode: $body")
        }
        return JSONObject(body)
    }
}
