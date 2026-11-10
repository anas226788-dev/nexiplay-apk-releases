package com.nexiplay.app.data.repository

import com.nexiplay.app.data.SupabaseClient
import com.nexiplay.app.data.model.ArcadeGame
import com.nexiplay.app.data.model.LiveChannel
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.RadioStation
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import org.json.JSONArray
import org.json.JSONObject

@Serializable
data class VaultDownloadResult(
    val success: Boolean = false,
    val url: String? = null,
    val directUrl: String? = null,
    val fileName: String? = null,
    val quality: String? = null,
    val error: String? = null,
    val source: String? = null
)

@Serializable
data class VibeAiResponse(
    val message: String = "",
    val ids: List<String> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val error: String? = null
)

@Serializable
data class RRStreamResult(
    val success: Boolean = false,
    val playbackUrl: String? = null,
    val embedUrl: String? = null,
    val title: String? = null,
    val format: String? = null,
    val error: String? = null
)

@Serializable
data class StreamtapeResult(
    val directUrl: String? = null,
    val embedUrl: String = ""
)

@Serializable
data class DesiDubStreamResult(
    val success: Boolean = false,
    val title: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
    val server: String? = null,
    val playbackUrl: String? = null,
    val embedUrl: String? = null,
    val notFound: Boolean? = null,
    val error: String? = null
)

object WebFeaturesRepository {

    const val BASE_WEB_URL = "https://nexiplay.vercel.app"

    // In-memory cache for resolved streams to ensure 0ms repeat loads
    private val rrStreamCache = java.util.concurrent.ConcurrentHashMap<String, RRStreamResult>()
    private val desidubStreamCache = java.util.concurrent.ConcurrentHashMap<String, DesiDubStreamResult>()

    private fun cleanDesiTitle(str: String): String {
        return str.replace(Regex("""\([^)]*\)"""), "")
            .replace(Regex("""\[[^\]]*\]"""), "")
            .replace(Regex("""Season\s*\d+(?:-\d+)?(?:\.)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""-\d{4}$"""), "")
            .replace(".", " ")
            .trim()
    }

    private fun httpPostForm(
        urlStr: String,
        formData: String,
        referer: String? = null,
        origin: String? = null,
        timeoutMs: Int = 8000
    ): String? {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
            setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
            referer?.let { setRequestProperty("Referer", it) }
            origin?.let { setRequestProperty("Origin", it) }
            setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
        }
        return try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(formData)
                writer.flush()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else null
        } catch (_: Exception) {
            null
        } finally {
            conn.disconnect()
        }
    }

    private suspend fun extractDesiDubOnDevice(
        title: String,
        slug: String,
        season: Int,
        episode: Int,
        serverId: String,
        onProgress: (Float, String) -> Unit
    ): DesiDubStreamResult? = withContext(Dispatchers.IO) {
        try {
            val cleanTitle = cleanDesiTitle(title.ifBlank { slug })
            if (cleanTitle.isBlank()) return@withContext null

            onProgress(0.55f, "Searching $cleanTitle on Nexi Deshi...")

            // 1. Search Anime via WP REST API
            val searchApiUrl = "https://www.desidubanime.me/wp-json/wp/v2/anime?search=" +
                URLEncoder.encode(cleanTitle, "UTF-8") + "&per_page=10"
            val restJson = httpGetHtml(searchApiUrl, referer = "https://www.desidubanime.me/", timeoutMs = 6000)

            var matchedSlug: String? = null
            var matchedLink: String? = null
            var matchedTitle: String? = null

            if (!restJson.isNullOrBlank() && restJson.trim().startsWith("[")) {
                try {
                    val arr = JSONArray(restJson.trim())
                    val candidates = mutableListOf<JSONObject>()
                    for (i in 0 until arr.length()) {
                        candidates.add(arr.getJSONObject(i))
                    }

                    val seasonMarker = "season-$season"
                    val seasonMarker2 = "${season}nd-season"
                    val seasonMarker3 = "${season}rd-season"
                    val seasonMarker4 = "${season}th-season"

                    val exactSeasonMatch = if (season > 1) {
                        candidates.firstOrNull {
                            val s = it.optString("slug", "").lowercase()
                            val t = it.optJSONObject("title")?.optString("rendered", "")?.lowercase() ?: ""
                            s.contains(seasonMarker) || s.contains(seasonMarker2) || s.contains(seasonMarker3) || s.contains(seasonMarker4) ||
                                t.contains("season $season") || t.contains("$season nd season") || t.contains("$season rd season") || t.contains("$season th season")
                        }
                    } else {
                        candidates.firstOrNull {
                            val s = it.optString("slug", "").lowercase()
                            (s.contains("season-1") || s.contains("1st-season")) && !s.contains("movie")
                        } ?: candidates.firstOrNull {
                            val s = it.optString("slug", "").lowercase()
                            !s.contains("season-") && !s.contains("2nd-") && !s.contains("3rd-") && !s.contains("movie")
                        }
                    }

                    val chosen = exactSeasonMatch ?: candidates.firstOrNull()
                    if (chosen != null) {
                        matchedSlug = chosen.optString("slug")
                        matchedLink = chosen.optString("link")
                        matchedTitle = chosen.optJSONObject("title")?.optString("rendered")
                    }
                } catch (_: Exception) {}
            }

            if (matchedSlug.isNullOrBlank()) {
                matchedSlug = cleanTitle.lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-')
            }

            onProgress(0.70f, "Locating Episode $episode player...")

            val titleSlug = matchedTitle?.let { cleanDesiTitle(it).lowercase().replace(Regex("""[^a-z0-9]+"""), "-").trim('-') }

            // 2. Candidate Watch URLs
            val candidateWatchUrls = buildList {
                if (!titleSlug.isNullOrBlank()) {
                    add("https://www.desidubanime.me/watch/$titleSlug-episode-$episode/")
                    add("https://www.desidubanime.me/watch/$titleSlug-season-$season-episode-$episode/")
                }
                add("https://www.desidubanime.me/watch/$matchedSlug-episode-$episode/")
                add("https://www.desidubanime.me/watch/$matchedSlug-$episode/")
                add("https://www.desidubanime.me/watch/$matchedSlug-season-$season-episode-$episode/")
            }

            var epHtml: String? = null
            for (cand in candidateWatchUrls) {
                val html = httpGetHtml(cand, referer = matchedLink ?: "https://www.desidubanime.me/", timeoutMs = 5000)
                if (!html.isNullOrBlank() && html.contains("<iframe")) {
                    epHtml = html
                    break
                }
            }

            // Fallback: parse anime post page to find watch link
            if (epHtml.isNullOrBlank() && !matchedLink.isNullOrBlank()) {
                val animePageHtml = httpGetHtml(matchedLink, referer = "https://www.desidubanime.me/", timeoutMs = 6000)
                if (!animePageHtml.isNullOrBlank()) {
                    val watchRegex = Regex("""href=["'](https?://(?:www\.)?desidubanime\.me/watch/[^"']+)["']""", RegexOption.IGNORE_CASE)
                    val allWatchUrls = watchRegex.findAll(animePageHtml).map { it.groupValues[1] }.distinct().toList()
                    val targetEpUrl = allWatchUrls.firstOrNull { u ->
                        val m = Regex("""(?:episode|ep)-(\d+)|-(\d+)/?$""", RegexOption.IGNORE_CASE).find(u)
                        val epFound = m?.groupValues?.getOrNull(1)?.toIntOrNull() ?: m?.groupValues?.getOrNull(2)?.toIntOrNull()
                        epFound == episode
                    }
                    if (targetEpUrl != null) {
                        epHtml = httpGetHtml(targetEpUrl, referer = matchedLink, timeoutMs = 5000)
                    } else if (allWatchUrls.isNotEmpty()) {
                        val sample = allWatchUrls.first()
                        val baseSlug = sample
                            .replace(Regex("""^https?://[^/]+/watch/""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""-episode-\d+.*$""", RegexOption.IGNORE_CASE), "")
                            .replace(Regex("""-\d+/?$"""), "")
                            .trim('/')
                        if (baseSlug.isNotBlank()) {
                            val reconstructed = "https://www.desidubanime.me/watch/$baseSlug-episode-$episode/"
                            epHtml = httpGetHtml(reconstructed, referer = matchedLink, timeoutMs = 5000)
                        }
                    }
                }
            }

            if (epHtml.isNullOrBlank()) return@withContext null

            // 3. Extract player iframe & page-level embed servers
            val iframeMatch = Regex("""<iframe[^>]+src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(epHtml)
            val iframeSrc = iframeMatch?.groupValues?.getOrNull(1)

            val serverMap = mutableMapOf<String, String>()

            // Extract embed-id attributes directly from the watch page (base64)
            val embedMatches = Regex("""embed-id=["']([^"']+)["']""", RegexOption.IGNORE_CASE).findAll(epHtml)
            for (m in embedMatches) {
                val raw = m.groupValues.getOrNull(1) ?: continue
                val parts = raw.split(":")
                if (parts.size >= 2) {
                    try {
                        val lblBytes = android.util.Base64.decode(parts[0], android.util.Base64.DEFAULT)
                        val valBytes = android.util.Base64.decode(parts[1], android.util.Base64.DEFAULT)
                        val label = String(lblBytes, Charsets.UTF_8).trim().lowercase()
                        var value = String(valBytes, Charsets.UTF_8).trim()
                        val valIframe = Regex("""src=["']([^"']+)["']""", RegexOption.IGNORE_CASE).find(value)
                        if (valIframe != null) value = valIframe.groupValues[1]

                        if (label.contains("vmoly") || value.contains("vidmoly")) {
                            serverMap["desidub_vidmoly"] = value
                        } else if (label.contains("mirror") || value.contains("filesforever")) {
                            serverMap["desidub_mirror"] = value
                        } else if (label.contains("ruby") || value.contains("nuvorel") || value.contains("rubystm")) {
                            serverMap["desidub_ruby"] = value
                        } else if (label.contains("p2p") || value.contains("p2pplay") || value.contains("rpmstream")) {
                            serverMap["desidub_p2p"] = value
                        } else if (label.contains("abyss") || value.contains("abyssplayer")) {
                            serverMap["desidub_abyss"] = value
                        }
                    } catch (_: Exception) {}
                }
            }

            if (!iframeSrc.isNullOrBlank() && !serverMap.containsKey("desidub_mirror")) {
                if (iframeSrc.contains("filesforever") || iframeSrc.contains("embed")) {
                    serverMap["desidub_mirror"] = iframeSrc
                }
            }

            // 4. Extract supplementary servers via embedhelper2.php
            val targetSidSrc = iframeSrc ?: serverMap["desidub_mirror"]
            if (targetSidSrc != null && (targetSidSrc.contains("filesforever.link/embed/") || targetSidSrc.contains("iqsmartgames.com/svid/"))) {
                val sid = Regex("""(?:embed|svid)/([a-zA-Z0-9_-]+)""").find(targetSidSrc)?.groupValues?.getOrNull(1)
                if (!sid.isNullOrBlank()) {
                    onProgress(0.85f, "Extracting high-speed video servers...")
                    try {
                        val helperUrl = "https://pro.iqsmartgames.com/embedhelper2.php"
                        val postBody = "sid=${URLEncoder.encode(sid, "UTF-8")}&UserFavSite=&currentDomain=%5B%5D"
                        val helperJson = httpPostForm(
                            urlStr = helperUrl,
                            formData = postBody,
                            referer = "https://pro.iqsmartgames.com/svid/$sid",
                            origin = "https://pro.iqsmartgames.com",
                            timeoutMs = 6000
                        )
                        if (!helperJson.isNullOrBlank()) {
                            val helperObj = JSONObject(helperJson)
                            val mresult = helperObj.optString("mresult")
                            if (mresult.isNotBlank()) {
                                val decodedBytes = android.util.Base64.decode(mresult, android.util.Base64.DEFAULT)
                                val decodedStr = String(decodedBytes, Charsets.UTF_8)
                                val ids = JSONObject(decodedStr)
                                val sources = helperObj.optJSONObject("sources")

                                val abys = ids.optString("abys").takeIf { it.isNotBlank() }
                                val flmn = ids.optString("flmn").takeIf { it.isNotBlank() }
                                val strmtp = ids.optString("strmtp").takeIf { it.isNotBlank() }
                                val flls = ids.optString("flls").takeIf { it.isNotBlank() } ?: ids.optString("smwh").takeIf { it.isNotBlank() }
                                val ddstm = ids.optString("ddstm").takeIf { it.isNotBlank() }

                                if (!serverMap.containsKey("desidub_abyss") && !abys.isNullOrBlank()) {
                                    val siteUrl = sources?.optJSONObject("abys")?.optString("siteUrl")
                                    if (!siteUrl.isNullOrBlank()) serverMap["desidub_abyss"] = "$siteUrl$abys"
                                }
                                if (!flmn.isNullOrBlank()) {
                                    val siteUrl = sources?.optJSONObject("flmn")?.optString("siteUrl")
                                    if (!siteUrl.isNullOrBlank()) serverMap["desidub_filemoon"] = "$siteUrl$flmn"
                                }
                                if (!strmtp.isNullOrBlank()) {
                                    val s = sources?.optJSONObject("strmtp")
                                    val siteUrl = s?.optString("siteUrl")
                                    val sfx = s?.optString("embed_suffix", "") ?: ""
                                    if (!siteUrl.isNullOrBlank()) serverMap["desidub_streamtape"] = "$siteUrl$strmtp$sfx"
                                }
                                if (!flls.isNullOrBlank()) {
                                    val siteUrl = sources?.optJSONObject("flls")?.optString("siteUrl")
                                        ?: sources?.optJSONObject("smwh")?.optString("siteUrl")
                                    if (!siteUrl.isNullOrBlank()) serverMap["desidub_earnvids"] = "$siteUrl$flls"
                                }
                                if (!ddstm.isNullOrBlank()) {
                                    val siteUrl = sources?.optJSONObject("ddstm")?.optString("siteUrl")
                                    if (!siteUrl.isNullOrBlank()) serverMap["desidub_doodstream"] = "$siteUrl$ddstm"
                                }
                            }
                        }
                    } catch (_: Exception) {}
                }
            }

            if (serverMap.isEmpty() && iframeSrc == null) return@withContext null

            // Prioritize reliable, direct-playing hosts:
            // 1. Vidmoly (fastest, clean embed player, zero bot barriers)
            // 2. Mirror (official native filesforever player)
            // 3. Ruby (nuvorel streamruby)
            // 4. Streamtape
            // 5. Doodstream
            // 6. P2P
            // 7. Filemoon / Abyss
            val primary = serverMap["desidub_vidmoly"]
                ?: serverMap["desidub_mirror"]
                ?: serverMap["desidub_ruby"]
                ?: serverMap["desidub_streamtape"]
                ?: serverMap["desidub_doodstream"]
                ?: serverMap["desidub_p2p"]
                ?: serverMap["desidub_filemoon"]
                ?: serverMap["desidub_abyss"]
                ?: iframeSrc
                ?: return@withContext null

            serverMap["desidub"] = primary
            if (!serverMap.containsKey("desidub_filemoon")) {
                serverMap["desidub_filemoon"] = serverMap["desidub_vidmoly"] ?: serverMap["desidub_mirror"] ?: primary
            }

            val targetUrl = serverMap[serverId] ?: primary
            val directVidmoly = if (targetUrl.contains("vidmoly", ignoreCase = true)) {
                extractDirectVidmolyM3u8OnDevice(targetUrl)
            } else null

            DesiDubStreamResult(
                success = true,
                title = matchedTitle ?: cleanTitle,
                playbackUrl = directVidmoly ?: targetUrl,
                embedUrl = targetUrl,
                server = serverId
            )
        } catch (_: Exception) {
            null
        }
    }

    private fun extractDirectVidmolyM3u8OnDevice(vidmolyUrl: String): String? {
        return try {
            val clean = if (vidmolyUrl.startsWith("//")) "https:$vidmolyUrl" else vidmolyUrl.trim()
            val html = httpGetHtml(clean, referer = "https://www.desidubanime.me/", timeoutMs = 5000) ?: return null
            val m3u8Match = Regex("""https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""", RegexOption.IGNORE_CASE).find(html)
            m3u8Match?.value?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    suspend fun resolveDesiDubStream(
        title: String,
        slug: String,
        id: String,
        season: Int,
        episode: Int,
        serverId: String = "desidub",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): DesiDubStreamResult? = withContext(Dispatchers.IO) {
        val cacheKey = "${slug.trim()}_${title.trim()}_${season}_${episode}_${serverId.trim()}"
        val cached = desidubStreamCache[cacheKey]
        if (cached != null) {
            onProgress(1.0f, "Stream ready, starting player...")
            return@withContext cached
        }

        onProgress(0.20f, "Connecting to Nexi Deshi Server...")
        val apiKey = "nxp_live_nexiplay_official_core_prod"
        val queryBuilder = StringBuilder("season=$season&episode=$episode&server=$serverId&apiKey=$apiKey")
        if (id.isNotBlank()) queryBuilder.append("&id=").append(URLEncoder.encode(id, "UTF-8"))
        if (slug.isNotBlank()) queryBuilder.append("&slug=").append(URLEncoder.encode(slug, "UTF-8"))
        if (title.isNotBlank()) queryBuilder.append("&title=").append(URLEncoder.encode(title, "UTF-8"))

        val apiUrl = "$BASE_WEB_URL/api/v1/anime/desidub-stream?$queryBuilder"
        try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Referer", "$BASE_WEB_URL/")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            }

            val code = conn.responseCode
            if (code in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                if (obj.optBoolean("success", false)) {
                    val rawPlayback = obj.optString("playbackUrl").takeIf { it.isNotBlank() }
                    val rawEmbed = obj.optString("embedUrl").takeIf { it.isNotBlank() }
                    val playback = if (rawPlayback?.startsWith("/") == true) "$BASE_WEB_URL$rawPlayback" else rawPlayback
                    val embed = if (rawEmbed?.startsWith("/") == true) "$BASE_WEB_URL$rawEmbed" else rawEmbed
                    if (playback != null || embed != null) {
                        val candidateEmbed = embed ?: playback!!
                        val directVidmoly = if (candidateEmbed.contains("vidmoly", ignoreCase = true)) {
                            extractDirectVidmolyM3u8OnDevice(candidateEmbed)
                        } else null
                        val res = DesiDubStreamResult(
                            success = true,
                            title = obj.optString("title").takeIf { it.isNotBlank() } ?: title,
                            playbackUrl = directVidmoly ?: playback,
                            embedUrl = candidateEmbed,
                            server = serverId
                        )
                        desidubStreamCache[cacheKey] = res
                        onProgress(1.0f, "Stream ready, starting player...")
                        return@withContext res
                    }
                }
            }
        } catch (_: Exception) {}

        // Fallback: On-Device Extraction directly from desidubanime.me
        onProgress(0.40f, "Searching Hindi/Deshi Dub stream on-device...")
        val onDevice = extractDesiDubOnDevice(title, slug, season, episode, serverId, onProgress)
        if (onDevice != null) {
            desidubStreamCache[cacheKey] = onDevice
            onProgress(1.0f, "Stream ready, starting player...")
            return@withContext onDevice
        }

        null
    }

    suspend fun fetchRRStreamApi(
        id: String,
        slug: String,
        season: Int,
        episode: Int
    ): RRStreamResult? = withContext(Dispatchers.IO) {
        val apiKey = "nxp_live_nexiplay_official_core_prod"
        val queryBuilder = StringBuilder("season=$season&episode=$episode&apiKey=$apiKey")
        if (id.isNotBlank()) queryBuilder.append("&id=").append(URLEncoder.encode(id, "UTF-8"))
        if (slug.isNotBlank()) queryBuilder.append("&slug=").append(URLEncoder.encode(slug, "UTF-8"))
        val apiUrl = "$BASE_WEB_URL/api/v1/anime/stream?$queryBuilder"
        try {
            val conn = (URL(apiUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Referer", "$BASE_WEB_URL/")
                setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36")
            }
            if (conn.responseCode in 200..299) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(body)
                if (obj.optBoolean("success", false)) {
                    fun isDead(u: String?) = u.isNullOrBlank() || u.contains("streamindia.co.in", ignoreCase = true) || u.contains("groovy.monster", ignoreCase = true)
                    val direct = obj.optString("directStreamUrl").takeIf { !isDead(it) }
                    val playback = obj.optString("playbackUrl").takeIf { !isDead(it) }
                    val embed = obj.optString("embedUrl").takeIf { !isDead(it) }
                    if (direct != null || playback != null || embed != null) {
                        return@withContext RRStreamResult(
                            success = true,
                            playbackUrl = direct ?: playback,
                            embedUrl = embed ?: playback,
                            title = obj.optString("title").takeIf { it.isNotBlank() },
                            format = obj.optString("format").takeIf { it.isNotBlank() }
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private fun httpGet(urlStr: String, timeoutMs: Int = 15000, headers: Map<String, String> = emptyMap()): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            headers.forEach { (k, v) -> setRequestProperty(k, v) }
            requestMethod = "GET"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            setRequestProperty("Accept", "application/json, text/plain, */*")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        }
        return try {
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Exception("HTTP $code: $err")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpPostJson(urlStr: String, bodyJson: String, timeoutMs: Int = 25000): String {
        val conn = (URL(urlStr).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = timeoutMs
            readTimeout = timeoutMs
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json")
            setRequestProperty(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
        }
        return try {
            OutputStreamWriter(conn.outputStream, "UTF-8").use { writer ->
                writer.write(bodyJson)
                writer.flush()
            }
            val code = conn.responseCode
            if (code in 200..299) {
                conn.inputStream.bufferedReader().use { it.readText() }
            } else {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
                throw Exception(err.ifBlank { "HTTP $code" })
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun httpGetHtml(urlStr: String, referer: String = urlStr, timeoutMs: Int = 10000): String? {
        var currentUrl = urlStr
        var currentReferer = referer
        var redirects = 0
        while (redirects < 6) {
            val conn = try {
                (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    instanceFollowRedirects = true
                    connectTimeout = timeoutMs
                    readTimeout = timeoutMs
                    setRequestProperty("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    setRequestProperty("Referer", currentReferer)
                    setRequestProperty(
                        "User-Agent",
                        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                    )
                }
            } catch (_: Exception) { return null }

            try {
                val code = conn.responseCode
                if (code in 300..399) {
                    val loc = conn.getHeaderField("Location")
                    if (!loc.isNullOrBlank()) {
                        val nextUrl = try {
                            URL(URL(currentUrl), loc).toString()
                        } catch (_: Exception) { loc }
                        currentReferer = currentUrl
                        currentUrl = nextUrl
                        redirects++
                        continue
                    }
                }
                if (code in 200..299) {
                    return conn.inputStream.bufferedReader().use { it.readText() }
                }
                return null
            } catch (_: Exception) {
                return null
            } finally {
                conn.disconnect()
            }
        }
        return null
    }

    /**
     * Probes a resolved download URL using a small GET range request (never HEAD,
     * as Cloudflare workers on vidvault return 500 on HEAD even for valid files).
     * Probes the Vercel proxy URL first (since *.workers.dev is frequently blocked by mobile ISPs
     * in South Asia and requires Referer: https://vidvault.to/), then falls back to directUrl.
     */
    private fun probeSingleUrl(targetUrl: String, referer: String): Boolean {
        val trimmed = targetUrl.trim()
        if (trimmed.isEmpty()) return false
        val lower = trimmed.lowercase()
        if (lower.contains("/direct/tv/") || lower.contains("/direct/movie/")) return false

        val conn = try {
            (URL(trimmed).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                instanceFollowRedirects = true
                connectTimeout = 8000
                readTimeout = 8000
                setRequestProperty("Range", "bytes=0-63")
                setRequestProperty("Referer", referer)
                setRequestProperty(
                    "User-Agent",
                    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"
                )
            }
        } catch (_: Exception) {
            return false
        }

        return try {
            val code = conn.responseCode
            val contentType = (conn.contentType ?: "").lowercase()
            code in 200..299 &&
                !contentType.startsWith("text/html") &&
                !contentType.startsWith("text/plain") &&
                !contentType.startsWith("application/json")
        } catch (_: Exception) {
            false
        } finally {
            conn.disconnect()
        }
    }

    private fun probeDirectDownloadUrl(proxyUrl: String?, directUrl: String, source: String? = null): Boolean {
        val cleanDirect = directUrl.trim()
        if (cleanDirect.isEmpty()) return false
        val lower = cleanDirect.lowercase()
        if (lower.contains("/direct/tv/") || lower.contains("/direct/movie/")) return false

        // If from ToonPlay or trusted cloud host (HubCloud, Mega, GDFlix, Google Drive, etc.), trust immediately
        if (source.equals("toonplay", ignoreCase = true) ||
            lower.contains("hubcloud.") || lower.contains("gdflix.") || lower.contains("fpgo.") || lower.contains("adhlinks.") ||
            lower.contains("drive.google.com") || lower.contains("mega.nz") ||
            lower.contains("mediafire.com") || lower.contains("pixeldrain.") ||
            lower.contains("terabox.") || lower.contains("pcloud.") ||
            lower.contains("workers.dev") || lower.contains("storage.")
        ) {
            return true
        }

        if (!proxyUrl.isNullOrBlank() && probeSingleUrl(proxyUrl, "$BASE_WEB_URL/")) {
            return true
        }
        return probeSingleUrl(cleanDirect, "https://vidvault.to/")
    }

    private const val PARX_API_BASE = "https://parxstream.vercel.app/api"

    private fun cleanParxTitle(rawTitle: String): String {
        return rawTitle
            .replace(Regex("""\(?\b(?:season|s)\s*\d+(?:\s*[-–to]+\s*\d+)?\)?""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[.*?\]"""), "")
            .replace(Regex("""\(?\b(?:19|20)\d{2}\b\)?"""), "")
            .replace(Regex("""\b(?:hindi|dubbed|dual\s*audio|multi\s*audio|english|japanese|subbed|4k|1080p|720p)\b""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[-–:]+$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun normalizeTitleForMatch(raw: String): String {
        return cleanParxTitle(raw)
            .lowercase()
            .replace(Regex("""[^a-z0-9\s]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun isParxTitleMatch(candidateTitle: String, targetTitle: String): Boolean {
        val cleanTarget = normalizeTitleForMatch(targetTitle)
        val cleanCandidate = normalizeTitleForMatch(candidateTitle)
        if (cleanTarget.isEmpty() || cleanCandidate.isEmpty()) return false
        if (cleanCandidate.contains(cleanTarget) || cleanTarget.contains(cleanCandidate)) return true
        val words = cleanTarget.split(" ").filter { it.length >= 2 }
        if (words.isEmpty()) return false
        val matched = words.count { cleanCandidate.contains(it) }
        return matched >= maxOf(1, kotlin.math.ceil(words.size * 0.7).toInt())
    }

    private fun matchesParxEpisode(epTitle: String, epNum: Int, seasonNum: Int?): Boolean {
        val t = epTitle.lowercase()
        if (seasonNum != null && Regex("""\bs0*${seasonNum}\s*e0*${epNum}\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)) {
            return true
        }
        return Regex("""\b(?:episode|ep)[\s\-_.:]*0*${epNum}\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            Regex("""\be0*${epNum}\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
            Regex("""^0*${epNum}$""").containsMatchIn(t.trim())
    }

    private fun pickBestParxStream(streamsArray: JsonArray, targetQualityNum: Int): String? {
        val links = streamsArray.mapNotNull { el ->
            val obj = el as? JsonObject ?: return@mapNotNull null
            val link = obj["link"]?.jsonPrimitive?.contentOrNull?.trim()?.takeIf { it.startsWith("http") } ?: return@mapNotNull null
            val server = obj["server"]?.jsonPrimitive?.contentOrNull.orEmpty()
            val q = obj["quality"]?.jsonPrimitive?.contentOrNull.orEmpty()
            Triple(link, server, q)
        }
        if (links.isEmpty()) return null

        val exactQ = links.firstOrNull { (_, srv, q) ->
            "$q $srv".contains(targetQualityNum.toString())
        }
        if (exactQ != null) return exactQ.first

        return links.firstOrNull { (l, _, _) -> l.contains("pixeldrain") && l.contains("download") }?.first
            ?: links.firstOrNull { (l, _, _) -> l.contains("googleusercontent.com") || l.contains("googlevideo") }?.first
            ?: links.firstOrNull { (l, _, _) -> l.contains("hakunaymatata.com") }?.first
            ?: links.firstOrNull { (l, _, _) -> l.contains("pixeldrain") }?.first
            ?: links.first().first
    }

    private fun resolveClientDirectParxDownload(
        rawTitle: String,
        isTv: Boolean,
        season: Int?,
        episode: Int?,
        quality: String
    ): VaultDownloadResult? {
        val cleanTitle = cleanParxTitle(rawTitle)
        if (cleanTitle.isEmpty()) return null

        val sNum = season ?: Regex("""\b(?:season|s)\s*(\d+)\b""", RegexOption.IGNORE_CASE)
            .find(rawTitle)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 1
        val eNum = episode ?: 1
        val qNum = Regex("""(2160|1080|720|480|360)""").find(quality)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: 720
        val safeBase = cleanTitle.replace(Regex("""[^a-zA-Z0-9_\- ]"""), "").trim().replace(Regex("""\s+"""), "_")

        // 1. Try 4khdhub & hdhub4u directly from Android phone (0 DB / 0 Vercel load)
        for (provider in listOf("4khdhub", "hdhub4u")) {
            try {
                val searchRaw = httpGet("$PARX_API_BASE/providers/$provider/search?q=${URLEncoder.encode(cleanTitle, "UTF-8")}&page=1", timeoutMs = 8000)
                val searchArr = json.parseToJsonElement(searchRaw) as? JsonArray ?: continue
                val matchedPost = searchArr.firstOrNull { el ->
                    val obj = el as? JsonObject ?: return@firstOrNull false
                    val postTitle = obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    isParxTitleMatch(postTitle, cleanTitle)
                } as? JsonObject ?: continue

                val postLink = matchedPost["link"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() } ?: continue
                val metaRaw = httpGet("$PARX_API_BASE/providers/$provider/meta?link=${URLEncoder.encode(postLink, "UTF-8")}", timeoutMs = 8500)
                val metaObj = json.parseToJsonElement(metaRaw) as? JsonObject ?: continue
                val linkList = metaObj["linkList"]?.let { it as? JsonArray }?.mapNotNull { it as? JsonObject } ?: continue
                if (linkList.isEmpty()) continue

                val seasonFiltered = if (isTv) {
                    val matches = linkList.filter { item ->
                        val t = item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                        Regex("""\b(?:season|s)\s*0*$sNum\b""", RegexOption.IGNORE_CASE).containsMatchIn(t) ||
                            !Regex("""\b(?:season|s)\s*\d+\b""", RegexOption.IGNORE_CASE).containsMatchIn(t)
                    }
                    matches.ifEmpty { linkList }
                } else {
                    linkList
                }

                val sortedGroups = seasonFiltered.sortedByDescending { item ->
                    val qText = "${item["quality"]?.jsonPrimitive?.contentOrNull.orEmpty()} ${item["title"]?.jsonPrimitive?.contentOrNull.orEmpty()}"
                    if (qText.contains(qNum.toString())) 1 else 0
                }

                for (group in sortedGroups.take(3)) {
                    var targetPageLink: String? = null
                    var streamType = if (isTv) "series" else "movie"

                    if (isTv) {
                        val directLinks = group["directLinks"]?.let { it as? JsonArray }?.mapNotNull { it as? JsonObject }.orEmpty()
                        if (directLinks.isNotEmpty()) {
                            val epObj = directLinks.firstOrNull { d ->
                                matchesParxEpisode(d["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), eNum, sNum)
                            } ?: directLinks.getOrNull(eNum - 1)
                            targetPageLink = epObj?.get("link")?.jsonPrimitive?.contentOrNull
                            streamType = epObj?.get("type")?.jsonPrimitive?.contentOrNull ?: "series"
                        }
                        if (targetPageLink.isNullOrBlank()) {
                            val epsUrl = group["episodesLink"]?.jsonPrimitive?.contentOrNull
                            if (!epsUrl.isNullOrBlank()) {
                                val epListRaw = httpGet("$PARX_API_BASE/providers/$provider/episodes?url=${URLEncoder.encode(epsUrl, "UTF-8")}", timeoutMs = 8000)
                                val epArr = json.parseToJsonElement(epListRaw) as? JsonArray
                                val epList = epArr?.mapNotNull { it as? JsonObject }.orEmpty()
                                val epObj = epList.firstOrNull { d ->
                                    matchesParxEpisode(d["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), eNum, sNum)
                                } ?: epList.getOrNull(eNum - 1)
                                targetPageLink = epObj?.get("link")?.jsonPrimitive?.contentOrNull
                            }
                        }
                    } else {
                        val directLinks = group["directLinks"]?.let { it as? JsonArray }?.mapNotNull { it as? JsonObject }.orEmpty()
                        if (directLinks.isNotEmpty()) {
                            targetPageLink = directLinks.first()["link"]?.jsonPrimitive?.contentOrNull
                            streamType = directLinks.first()["type"]?.jsonPrimitive?.contentOrNull ?: "movie"
                        } else {
                            targetPageLink = group["episodesLink"]?.jsonPrimitive?.contentOrNull
                        }
                    }

                    if (targetPageLink.isNullOrBlank()) continue
                    val streamRaw = httpGet(
                        "$PARX_API_BASE/providers/$provider/stream?link=${URLEncoder.encode(targetPageLink, "UTF-8")}&type=${URLEncoder.encode(streamType, "UTF-8")}",
                        timeoutMs = 9000
                    )
                    val streamArr = json.parseToJsonElement(streamRaw) as? JsonArray ?: continue
                    val bestLink = pickBestParxStream(streamArr, qNum)
                    if (!bestLink.isNullOrBlank()) {
                        val ext = if (bestLink.lowercase().contains(".mp4")) "mp4" else "mkv"
                        return VaultDownloadResult(
                            success = true,
                            source = "${provider}_direct",
                            url = bestLink,
                            directUrl = bestLink,
                            fileName = "${safeBase}${if (isTv) "_S${sNum}E${eNum}" else ""}_$quality.$ext",
                            quality = quality
                        )
                    }
                }
            } catch (_: Exception) {}
        }

        // 2. Try movieBoxWeb directly from Android phone (Direct MP4 CDN)
        try {
            val searchRaw = httpGet("$PARX_API_BASE/providers/movieBoxWeb/search?q=${URLEncoder.encode(cleanTitle, "UTF-8")}&page=1", timeoutMs = 8000)
            val searchArr = json.parseToJsonElement(searchRaw) as? JsonArray
            val matchedPost = searchArr?.firstOrNull { el ->
                val obj = el as? JsonObject ?: return@firstOrNull false
                isParxTitleMatch(obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), cleanTitle)
            } as? JsonObject
            val postLink = matchedPost?.get("link")?.jsonPrimitive?.contentOrNull
            if (!postLink.isNullOrBlank()) {
                val metaRaw = httpGet("$PARX_API_BASE/providers/movieBoxWeb/meta?link=${URLEncoder.encode(postLink, "UTF-8")}", timeoutMs = 8000)
                val metaObj = json.parseToJsonElement(metaRaw) as? JsonObject
                val linkList = metaObj?.get("linkList")?.let { it as? JsonArray }?.mapNotNull { it as? JsonObject }.orEmpty()
                val preferredGroup = linkList.sortedByDescending { g ->
                    val t = g["title"]?.jsonPrimitive?.contentOrNull.orEmpty()
                    if (Regex("hindi|multi|hin", RegexOption.IGNORE_CASE).containsMatchIn(t)) 1 else 0
                }.firstOrNull()

                if (preferredGroup != null) {
                    var streamPayload: String? = null
                    val streamType = if (isTv) "series" else "movie"
                    if (isTv) {
                        val epsUrl = preferredGroup["episodesLink"]?.jsonPrimitive?.contentOrNull
                        if (!epsUrl.isNullOrBlank()) {
                            val epListRaw = httpGet("$PARX_API_BASE/providers/movieBoxWeb/episodes?url=${URLEncoder.encode(epsUrl, "UTF-8")}", timeoutMs = 8000)
                            val epList = (json.parseToJsonElement(epListRaw) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                            val epObj = epList.firstOrNull { d ->
                                matchesParxEpisode(d["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), eNum, sNum)
                            } ?: epList.getOrNull(eNum - 1)
                            streamPayload = epObj?.get("link")?.jsonPrimitive?.contentOrNull
                        }
                    } else {
                        val directLinks = preferredGroup["directLinks"]?.let { it as? JsonArray }?.mapNotNull { it as? JsonObject }.orEmpty()
                        streamPayload = directLinks.firstOrNull()?.get("link")?.jsonPrimitive?.contentOrNull
                            ?: preferredGroup["episodesLink"]?.jsonPrimitive?.contentOrNull
                    }

                    if (!streamPayload.isNullOrBlank()) {
                        val streamRaw = httpGet(
                            "$PARX_API_BASE/providers/movieBoxWeb/stream?link=${URLEncoder.encode(streamPayload, "UTF-8")}&type=$streamType",
                            timeoutMs = 8500
                        )
                        val streamArr = json.parseToJsonElement(streamRaw) as? JsonArray
                        val bestLink = streamArr?.let { pickBestParxStream(it, qNum) }
                        if (!bestLink.isNullOrBlank()) {
                            return VaultDownloadResult(
                                success = true,
                                source = "moviebox_direct",
                                url = bestLink,
                                directUrl = bestLink,
                                fileName = "${safeBase}${if (isTv) "_S${sNum}E${eNum}" else ""}_$quality.mp4",
                                quality = quality
                            )
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        return null
    }

    suspend fun resolveParxDirectStream(
        provider: String,
        isTv: Boolean,
        tmdbId: String?,
        title: String,
        season: Int = 1,
        episode: Int = 1
    ): String? = withContext(Dispatchers.IO) {
        val cleanTitle = cleanParxTitle(title)
        val cleanTmdb = tmdbId?.trim()?.removePrefix("tmdb-")?.takeIf { it.isNotBlank() && !it.contains("-") }

        try {
            when (provider) {
                "cinepro" -> {
                    if (cleanTmdb != null) {
                        val endpoint = if (isTv) {
                            "$PARX_API_BASE/cinepro/tv/$cleanTmdb/$season/$episode"
                        } else {
                            "$PARX_API_BASE/cinepro/movie/$cleanTmdb"
                        }
                        val raw = httpGet(endpoint, timeoutMs = 8000)
                        val obj = json.parseToJsonElement(raw) as? JsonObject
                        val streams = obj?.get("streams") as? JsonArray
                        val link = streams?.mapNotNull { (it as? JsonObject)?.get("link")?.jsonPrimitive?.contentOrNull }
                            ?.firstOrNull { it.startsWith("http") }
                        if (!link.isNullOrBlank()) return@withContext link
                    }
                }
                "animesalt" -> {
                    if (cleanTitle.isNotEmpty()) {
                        val searchRaw = httpGet("$PARX_API_BASE/providers/animesalt/search?q=${URLEncoder.encode(cleanTitle, "UTF-8")}&page=1", timeoutMs = 8000)
                        val searchArr = json.parseToJsonElement(searchRaw) as? JsonArray
                        val matched = searchArr?.mapNotNull { it as? JsonObject }?.firstOrNull { obj ->
                            isParxTitleMatch(obj["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), cleanTitle)
                        }
                        val postLink = matched?.get("link")?.jsonPrimitive?.contentOrNull
                        if (!postLink.isNullOrBlank()) {
                            val metaRaw = httpGet("$PARX_API_BASE/providers/animesalt/meta?link=${URLEncoder.encode(postLink, "UTF-8")}", timeoutMs = 8000)
                            val metaObj = json.parseToJsonElement(metaRaw) as? JsonObject
                            val linkList = metaObj?.get("linkList")?.let { it as? JsonArray }?.mapNotNull { it as? JsonObject }.orEmpty()
                            val seasonGroup = linkList.firstOrNull { g ->
                                Regex("""\b(?:season|s)\s*0*$season\b""", RegexOption.IGNORE_CASE)
                                    .containsMatchIn(g["title"]?.jsonPrimitive?.contentOrNull.orEmpty())
                            } ?: linkList.firstOrNull()
                            val epsUrl = seasonGroup?.get("episodesLink")?.jsonPrimitive?.contentOrNull
                            if (!epsUrl.isNullOrBlank()) {
                                val epRaw = httpGet("$PARX_API_BASE/providers/animesalt/episodes?url=${URLEncoder.encode(epsUrl, "UTF-8")}", timeoutMs = 8000)
                                val epList = (json.parseToJsonElement(epRaw) as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
                                val epObj = epList.firstOrNull { e ->
                                    matchesParxEpisode(e["title"]?.jsonPrimitive?.contentOrNull.orEmpty(), episode, season)
                                } ?: epList.getOrNull(episode - 1)
                                val epLink = epObj?.get("link")?.jsonPrimitive?.contentOrNull
                                if (!epLink.isNullOrBlank()) {
                                    val streamRaw = httpGet("$PARX_API_BASE/providers/animesalt/stream?link=${URLEncoder.encode(epLink, "UTF-8")}&type=series", timeoutMs = 8000)
                                    val streamArr = json.parseToJsonElement(streamRaw) as? JsonArray
                                    val m3u8 = streamArr?.mapNotNull { (it as? JsonObject)?.get("link")?.jsonPrimitive?.contentOrNull }
                                        ?.firstOrNull { it.startsWith("http") }
                                    if (!m3u8.isNullOrBlank()) return@withContext m3u8
                                }
                            }
                        }
                    }
                }
                "movieBoxWeb", "4khdhub" -> {
                    val res = resolveClientDirectParxDownload(
                        rawTitle = title,
                        isTv = isTv,
                        season = season,
                        episode = episode,
                        quality = "1080p"
                    )
                    if (res != null && !res.directUrl.isNullOrBlank()) {
                        return@withContext res.directUrl
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

    // ── 1. Fast Direct MKV/MP4 Download Vault (100% Client-Side First -> Zero DB / Zero Vercel Load) ──

    suspend fun resolveVaultDownload(
        isTv: Boolean,
        targetId: String,
        season: Int? = null,
        episode: Int? = null,
        quality: String = "720p",
        title: String? = null,
        mediaType: String? = null,
        fallbackStreamUrls: List<String> = emptyList()
    ): VaultDownloadResult = withContext(Dispatchers.IO) {
        val cleanTargetId = targetId.trim().removePrefix("tmdb-")
        val effectiveType = mediaType ?: if (isTv) "tv" else "movie"

        // 1. Try 100% Client-Direct Multi-Provider Resolution from Android device first (0 Vercel & 0 DB load)
        if (!title.isNullOrBlank()) {
            val parxDirect = resolveClientDirectParxDownload(
                rawTitle = title,
                isTv = isTv,
                season = season,
                episode = episode,
                quality = quality
            )
            if (parxDirect != null && parxDirect.success && !parxDirect.directUrl.isNullOrBlank()) {
                return@withContext parxDirect
            }
        }

        // 2. Try Direct VidVault API from Android device (if numeric TMDB ID or tt IMDb ID) — 0 Vercel & 0 DB load
        val isValidVaultId = cleanTargetId.matches(Regex("""^(\d+|tt\d+)$""", RegexOption.IGNORE_CASE))
        if (isValidVaultId) {
            val qualitiesToTry = listOf(quality) + listOf("720p", "1080p", "480p").filter { !it.equals(quality, ignoreCase = true) }
            for (qTry in qualitiesToTry) {
                try {
                    val vaultPath = if (isTv && season != null && episode != null) {
                        "https://vidvault.to/download-link/tv/$cleanTargetId/$season/$episode/$qTry"
                    } else {
                        "https://vidvault.to/download-link/movie/$cleanTargetId/$qTry"
                    }
                    val rawVault = httpGet(
                        vaultPath,
                        headers = mapOf("Referer" to "https://vidvault.to/"),
                        timeoutMs = 5000
                    )
                    val vaultObj = json.parseToJsonElement(rawVault) as? JsonObject
                    val directVaultUrl = vaultObj?.get("url")?.jsonPrimitive?.contentOrNull?.trim()
                    if (!directVaultUrl.isNullOrBlank() && directVaultUrl.startsWith("http")) {
                        return@withContext VaultDownloadResult(
                            success = true,
                            source = "vidvault",
                            url = directVaultUrl,
                            directUrl = directVaultUrl,
                            quality = qTry
                        )
                    }
                } catch (_: Exception) {}
            }
        }

        // 3. Single Edge-Cached Fallback Request to /api/download/vault (e.g. for bundled ToonPlay catalog)
        if (cleanTargetId.isNotEmpty()) {
            try {
                val payload = buildJsonObject {
                    put("type", JsonPrimitive(effectiveType))
                    put("id", JsonPrimitive(cleanTargetId))
                    if (isTv && season != null) put("season", JsonPrimitive(season))
                    if (isTv && episode != null) put("episode", JsonPrimitive(episode))
                    put("quality", JsonPrimitive(quality))
                    if (!title.isNullOrBlank()) put("title", JsonPrimitive(title))
                }.toString()

                val raw = httpPostJson("$BASE_WEB_URL/api/download/vault", payload, timeoutMs = 10000)
                val parsed = json.decodeFromString<VaultDownloadResult>(raw)
                val direct = parsed.directUrl?.trim().orEmpty()
                val resolvedUrl = parsed.url?.let { u ->
                    if (u.startsWith("/")) "$BASE_WEB_URL$u" else u
                }

                if (parsed.success && (!resolvedUrl.isNullOrBlank() || direct.isNotBlank())) {
                    val targetDirect = direct.ifBlank { resolvedUrl.orEmpty() }
                    return@withContext parsed.copy(
                        url = resolvedUrl ?: targetDirect,
                        directUrl = targetDirect,
                        quality = parsed.quality ?: quality
                    )
                }
            } catch (_: Exception) {}
        }

        // Fallback: If VidVault's upstream worker is down (HTTP 500) or missing this title/episode,
        // resolve from episode/movie cloud stream/download sources (e.g. argon.razorshell.space/downlead/<FID>/)
        val sNum = season ?: 1
        val eNum = episode ?: 1
        val expandedCandidates = fallbackStreamUrls.flatMap { raw ->
            val t = raw.trim()
            if (t.startsWith("{")) {
                Regex("""https?://[^\s"'\\}]+""").findAll(t).map { it.value }.toList()
            } else {
                listOf(t)
            }
        }.distinct()

        for (rawCandidate in expandedCandidates) {
            val candidate = rawCandidate.trim()
            if (candidate.isEmpty() || candidate.contains("fastdlserver.site", ignoreCase = true)) continue
            val lower = candidate.lowercase()

            if (lower.endsWith(".mp4") || lower.endsWith(".mkv") || lower.contains(".mp4?") || lower.contains(".mkv?")) {
                return@withContext VaultDownloadResult(
                    success = true,
                    url = candidate,
                    directUrl = candidate,
                    quality = quality
                )
            }

            val razorMatch = Regex("""https?://([^/]*razorshell\.space)/(?:embed|downlead)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
                .find(candidate)
            if (razorMatch != null) {
                val host = razorMatch.groupValues[1]
                val fid = razorMatch.groupValues[2]
                return@withContext VaultDownloadResult(
                    success = true,
                    url = "https://$host/downlead/$fid/",
                    directUrl = null,
                    quality = quality
                )
            }

            if (lower.contains("codedew.com/zipper") || lower.contains("/zipper/") ||
                lower.contains("rareanimes.") || lower.contains("raretoons.")
            ) {
                try {
                    val resolvedEmbed = resolveWebEmbedUrl(
                        embedUrl = candidate,
                        episodeNum = eNum,
                        relativeEpisodeNum = eNum,
                        seasonNum = sNum
                    )
                    val resolvedRazor = Regex("""https?://([^/]*razorshell\.space)/(?:embed|downlead)/([A-Za-z0-9_-]+)""", RegexOption.IGNORE_CASE)
                        .find(resolvedEmbed)
                    if (resolvedRazor != null) {
                        val host = resolvedRazor.groupValues[1]
                        val fid = resolvedRazor.groupValues[2]
                        return@withContext VaultDownloadResult(
                            success = true,
                            url = "https://$host/downlead/$fid/",
                            directUrl = null,
                            quality = quality
                        )
                    }
                } catch (_: Exception) {}
            }

            if (lower.contains("drive.google.com") || lower.contains("mega.nz") ||
                lower.contains("mediafire.com") || lower.contains("pixeldrain.") ||
                lower.contains("terabox.") || lower.contains("pcloud.") ||
                lower.contains("workers.dev")
            ) {
                return@withContext VaultDownloadResult(
                    success = true,
                    url = candidate,
                    directUrl = null,
                    quality = quality
                )
            }
        }

        VaultDownloadResult(
            success = false,
            error = "Cloud direct download is not available for this title/episode right now."
        )
    }

    // ── 2. Live TV Channels (/api/live-tv/channels + Direct M3U Fallback) ──

    private val verifiedFallbacks = mapOf(
        "somoy-tv" to Pair("https://www.youtube.com/embed/live_stream?channel=UC24_68cW_zSgJb_4Kx4d1oA", "https://live-somoytv.b-cdn.net/somoytv/somoytv.stream/playlist.m3u8"),
        "jamuna-tv" to Pair("https://www.youtube.com/embed/live_stream?channel=UC4_rK296k1vE4K_iB8pI1bA", "https://edge.jamuna.tv/live/jamunatv/playlist.m3u8"),
        "channel-24" to Pair("https://www.youtube.com/embed/live_stream?channel=UC5m_xPqF1r_1m1X0L5p0Gvg", null),
        "ekattor-tv" to Pair("https://www.youtube.com/embed/live_stream?channel=UC_Tf6Z0m_m3e5qZf8Z1gJbw", null),
        "independent-tv" to Pair("https://www.youtube.com/embed/live_stream?channel=UC5x0k0fA_m9f4P2x9bQ0t3g", null),
        "dbc-news" to Pair("https://www.youtube.com/embed/live_stream?channel=UCLg3k-m6mR4j-1m9qZ2t4fw", null),
        "aaj-tak" to Pair("https://www.youtube.com/embed/live_stream?channel=UCt4t-jeY85JegMlZ-E5UWtA", null),
        "ndtv-india" to Pair("https://www.youtube.com/embed/live_stream?channel=UCZFMm1mMw0F81Z37aa5T_vA", null),
        "al-jazeera" to Pair(null, "https://live-hls-web-aje.getaj.net/AJE/03.m3u8"),
        "dw-english" to Pair(null, "https://dwamdstream102.akamaized.net/hls/live/2015525/dwstream102/index.m3u8"),
        "france-24" to Pair(null, "https://stream.france24.com/hls/live/2037568/F24_EN_LO_HLS/master_500.m3u8")
    )

    suspend fun fetchLiveChannels(countryCode: String): List<LiveChannel> = withContext(Dispatchers.IO) {
        val cleanCountry = countryCode.lowercase().trim().ifEmpty { "bd" }
        try {
            val raw = httpGet("$BASE_WEB_URL/api/live-tv/channels?country=${URLEncoder.encode(cleanCountry, "UTF-8")}", timeoutMs = 12000)
            val root = json.parseToJsonElement(raw).jsonObject
            val arr = root["channels"]?.jsonArray ?: JsonArray(emptyList())
            val list = arr.mapNotNull { el ->
                try {
                    json.decodeFromJsonElement(LiveChannel.serializer(), el)
                } catch (_: Exception) {
                    null
                }
            }
            if (list.isNotEmpty()) return@withContext list
        } catch (_: Exception) {
            // Fallback to direct iptv-org M3U parsing
        }

        try {
            val m3uUrl = "https://iptv-org.github.io/iptv/countries/$cleanCountry.m3u"
            val m3uText = httpGet(m3uUrl, timeoutMs = 15000)
            parseM3U(m3uText, cleanCountry.uppercase())
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun parseM3U(m3uText: String, countryCode: String): List<LiveChannel> {
        val lines = m3uText.lines()
        val channels = mutableListOf<LiveChannel>()
        var pendingName = "Live Channel"
        var pendingLogo = ""
        var pendingGroup = "General"
        var pendingId = ""
        var hasPending = false

        val logoRegex = Regex("tvg-logo=\"([^\"]+)\"")
        val groupRegex = Regex("group-title=\"([^\"]+)\"")
        val idRegex = Regex("tvg-id=\"([^\"]+)\"")

        for (rawLine in lines) {
            val line = rawLine.trim()
            if (line.startsWith("#EXTINF:")) {
                pendingLogo = logoRegex.find(line)?.groupValues?.getOrNull(1) ?: ""
                pendingGroup = groupRegex.find(line)?.groupValues?.getOrNull(1) ?: "General"
                val rawId = idRegex.find(line)?.groupValues?.getOrNull(1)
                val commaIdx = line.lastIndexOf(',')
                pendingName = if (commaIdx != -1) line.substring(commaIdx + 1).trim() else "Live Channel"
                pendingId = (rawId ?: pendingName).lowercase().replace(Regex("[^a-z0-9]+"), "-")
                hasPending = true
            } else if (line.isNotEmpty() && !line.startsWith("#") && hasPending) {
                var streamUrl = line
                var ytLive: String? = null
                var isTop = false

                for ((key, meta) in verifiedFallbacks) {
                    if (pendingId.contains(key) || pendingName.lowercase().contains(key)) {
                        if (meta.first != null) ytLive = meta.first
                        if (meta.second != null) streamUrl = meta.second!!
                        isTop = true
                    }
                }

                channels.add(
                    LiveChannel(
                        id = pendingId,
                        name = pendingName,
                        logo = pendingLogo,
                        group = pendingGroup,
                        country = countryCode,
                        streamUrl = streamUrl,
                        youtubeLiveUrl = ytLive,
                        isTop = isTop
                    )
                )
                hasPending = false
            }
        }

        return channels.sortedWith(compareByDescending<LiveChannel> { it.isTop }.thenBy { it.name })
    }

    // ── 4. Live Radio Stations (/api/live-tv/radio + Direct Radio-Browser Fallback) ──

    suspend fun fetchRadioStations(countryCode: String, searchQuery: String = ""): List<RadioStation> = withContext(Dispatchers.IO) {
        val cleanCountry = countryCode.uppercase().trim().ifEmpty { "BD" }
        val q = searchQuery.trim()
        try {
            val url = if (q.isNotEmpty()) {
                "$BASE_WEB_URL/api/live-tv/radio?q=${URLEncoder.encode(q, "UTF-8")}"
            } else {
                "$BASE_WEB_URL/api/live-tv/radio?country=${URLEncoder.encode(cleanCountry, "UTF-8")}"
            }
            val raw = httpGet(url, timeoutMs = 12000)
            val root = json.parseToJsonElement(raw).jsonObject
            val arr = root["stations"]?.jsonArray ?: JsonArray(emptyList())
            val list = arr.mapNotNull { el ->
                try {
                    json.decodeFromJsonElement(RadioStation.serializer(), el)
                } catch (_: Exception) {
                    null
                }
            }
            if (list.isNotEmpty()) return@withContext list
        } catch (_: Exception) {
            // Fallback to radio-browser mirror
        }

        val mirrors = listOf(
            "https://de1.api.radio-browser.info",
            "https://nl1.api.radio-browser.info",
            "https://all.api.radio-browser.info"
        )
        val endpoint = if (q.isNotEmpty()) {
            "/json/stations/search?name=${URLEncoder.encode(q, "UTF-8")}&limit=60&order=votes&reverse=true"
        } else {
            "/json/stations/bycountrycodeexact/$cleanCountry?limit=60&order=votes&reverse=true"
        }
        for (mirror in mirrors) {
            try {
                val raw = httpGet("$mirror$endpoint", timeoutMs = 10000)
                val arr = json.parseToJsonElement(raw).jsonArray
                val list = arr.mapNotNull { el ->
                    val obj = el.jsonObject
                    val urlResolved = obj["url_resolved"]?.jsonPrimitive?.content.orEmpty()
                    val urlOrig = obj["url"]?.jsonPrimitive?.content.orEmpty()
                    val finalUrl = urlResolved.ifBlank { urlOrig }
                    if (finalUrl.isBlank()) return@mapNotNull null
                    RadioStation(
                        stationuuid = obj["stationuuid"]?.jsonPrimitive?.content ?: finalUrl,
                        name = obj["name"]?.jsonPrimitive?.content?.trim().takeUnless { it.isNullOrBlank() } ?: "Live Radio",
                        url = urlOrig,
                        urlResolved = finalUrl,
                        favicon = obj["favicon"]?.jsonPrimitive?.content.orEmpty(),
                        tags = obj["tags"]?.jsonPrimitive?.content.orEmpty(),
                        country = obj["country"]?.jsonPrimitive?.content ?: cleanCountry,
                        countrycode = obj["countrycode"]?.jsonPrimitive?.content ?: cleanCountry,
                        bitrate = obj["bitrate"]?.jsonPrimitive?.intOrNull ?: 128,
                        codec = obj["codec"]?.jsonPrimitive?.content ?: "MP3"
                    )
                }
                if (list.isNotEmpty()) return@withContext list
            } catch (_: Exception) {
                continue
            }
        }
        emptyList()
    }

    // ── 5. Nexiplay Arcade Games (/api/games + GamePix Direct Fallback) ──

    @Volatile
    private var cachedArcadeGames: List<ArcadeGame> = emptyList()

    suspend fun fetchArcadeGames(forceRefresh: Boolean = false): List<ArcadeGame> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedArcadeGames.isNotEmpty()) {
            return@withContext cachedArcadeGames
        }

        try {
            val raw = httpGet("$BASE_WEB_URL/api/games?limit=1000&sort=popular", timeoutMs = 15000)
            val root = json.parseToJsonElement(raw).jsonObject
            val arr = root["games"]?.jsonArray ?: JsonArray(emptyList())
            val parsed = arr.mapNotNull { el ->
                try {
                    json.decodeFromJsonElement(ArcadeGame.serializer(), el)
                } catch (_: Exception) {
                    null
                }
            }
            if (parsed.isNotEmpty()) {
                cachedArcadeGames = parsed
                return@withContext parsed
            }
        } catch (_: Exception) {
            // Fallback to GamePix direct API
        }

        try {
            val raw = httpGet("https://games.gamepix.com/games", timeoutMs = 15000)
            val root = json.parseToJsonElement(raw).jsonObject
            val arr = root["data"]?.jsonArray ?: JsonArray(emptyList())
            val parsed = arr.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.content ?: obj["sid"]?.jsonPrimitive?.content ?: return@mapNotNull null
                val title = obj["title"]?.jsonPrimitive?.content?.trim() ?: "Game"
                val category = obj["category"]?.jsonPrimitive?.content?.trim() ?: "Arcade"
                val thumb = obj["thumbnailUrl"]?.jsonPrimitive?.content ?: obj["thumbnailUrl100"]?.jsonPrimitive?.content ?: ""
                val url = obj["url"]?.jsonPrimitive?.content ?: "https://play.gamepix.com/$id/embed?sid=1"
                ArcadeGame(
                    id = id,
                    title = title,
                    category = category,
                    categories = listOf(category),
                    thumbnailUrl = thumb,
                    thumbnailUrl100 = obj["thumbnailUrl100"]?.jsonPrimitive?.content ?: thumb,
                    url = url,
                    description = obj["description"]?.jsonPrimitive?.content ?: obj["desc_en"]?.jsonPrimitive?.content ?: "",
                    orientation = obj["orientation"]?.jsonPrimitive?.content ?: "landscape",
                    rkScore = obj["rkScore"]?.jsonPrimitive?.doubleOrNull ?: 0.75,
                    featured = obj["featured"]?.jsonPrimitive?.booleanOrNull ?: false
                )
            }
            if (parsed.isNotEmpty()) {
                cachedArcadeGames = parsed
                return@withContext parsed
            }
        } catch (_: Exception) {
        }

        emptyList()
    }

    // ── 6. AI Vibe Manager (/api/vibe-manager) ──

    suspend fun askVibeManager(
        prompt: String,
        profileId: String = "auto",
        format: String = "all"
    ): VibeAiResponse = withContext(Dispatchers.IO) {
        try {
            val body = buildJsonObject {
                put("prompt", JsonPrimitive(prompt))
                put("profileId", JsonPrimitive(profileId))
                put("format", JsonPrimitive(format))
            }.toString()
            val raw = httpPostJson("$BASE_WEB_URL/api/vibe-manager", body, timeoutMs = 25000)
            json.decodeFromString<VibeAiResponse>(raw)
        } catch (e: Exception) {
            VibeAiResponse(
                error = "Could not create an AI mix right now. Try selecting a vibe profile below!"
            )
        }
    }

    // ── 7. ToonPlay & Webpage Embed On-the-Fly Resolver ──

    suspend fun resolveToonplayStream(
        toonplayId: String,
        season: Int,
        episode: Int,
        cachedUrl: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val params = buildString {
                append("toonplay_id=${URLEncoder.encode(toonplayId, "UTF-8")}")
                append("&season=$season&episode=$episode")
                if (!cachedUrl.isNullOrBlank()) {
                    append("&cached_url=${URLEncoder.encode(cachedUrl, "UTF-8")}")
                }
            }
            val raw = httpGet("$BASE_WEB_URL/api/resolve-toonplay?$params", timeoutMs = 15000)
            val obj = json.parseToJsonElement(raw).jsonObject
            obj["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() } ?: cachedUrl
        } catch (_: Exception) {
            cachedUrl
        }
    }

    private fun extractIframesFromHtml(html: String, baseUrlStr: String): List<String> {
        val baseUri = try { URL(baseUrlStr) } catch (_: Exception) { null }
        val iframeRegex = Regex("""<iframe\b[^>]*(?:data-src|src)=["']([^"']+)["'][^>]*>""", RegexOption.IGNORE_CASE)
        return iframeRegex.findAll(html).mapNotNull { match ->
            val rawSrc = match.groupValues.getOrNull(1)
                ?.replace("&amp;", "&")
                ?.replace("&#038;", "&")
                ?.trim()
                .orEmpty()
            if (rawSrc.isEmpty()) return@mapNotNull null
            val abs = when {
                rawSrc.startsWith("//") -> "https:$rawSrc"
                rawSrc.startsWith("/") && baseUri != null -> "${baseUri.protocol}://${baseUri.host}$rawSrc"
                else -> rawSrc
            }
            val lower = abs.lowercase()
            if (lower.contains("/cdn-cgi/challenge-platform/") ||
                lower.contains("about:blank") ||
                lower.contains("googletagmanager.com") ||
                lower.contains("doubleclick.net") ||
                lower.contains("googlesyndication.com")
            ) {
                null
            } else {
                abs
            }
        }.toList()
    }

    private fun pickBestZipperFromHtml(chunk: String): String? {
        val anchorRegex = Regex(
            """<a\b[^>]*href=["'](https?://[^"']*zipper/[^"']*)["'][^>]*>([\s\S]*?)</a>""",
            RegexOption.IGNORE_CASE
        )
        val links = anchorRegex.findAll(chunk).map { m ->
            val href = m.groupValues[1].replace("&amp;", "&").replace("&#038;", "&").trim()
            val label = m.groupValues[2].replace(Regex("<[^>]+>"), "").trim().lowercase()
            href to label
        }.filter { it.first.isNotEmpty() }.toList()

        if (links.isEmpty()) return null
        return links.firstOrNull { it.second.contains("watchnow") }?.first
            ?: links.firstOrNull { it.second.contains("watchmultiquality") }?.first
            ?: links.firstOrNull {
                it.second.contains("watch") ||
                    it.second.contains("stream") ||
                    it.second.contains("player") ||
                    it.second.contains("online")
            }?.first
            ?: links.firstOrNull {
                val l = it.second
                !l.contains("hubcloud") &&
                    !l.contains("mega") &&
                    !l.contains("dlbeta") &&
                    !l.contains("gdrive") &&
                    !l.contains("drive") &&
                    !l.contains("japanese") &&
                    !l.contains("hepburn")
            }?.first
    }

    /**
     * Decodes JuicyCodes obfuscated JWPlayer payload (`_juicycodes(...)`) directly on-device
     * to extract the signed `.m3u8` stream URL tied to the user's client IP.
     */
    private fun decodeJuicyCodesM3u8(embedHtml: String): String? {
        return try {
            val jcMatch = Regex("""_juicycodes\(([\s\S]*?)\)""").find(embedHtml) ?: return null
            val rawArg = jcMatch.groupValues[1].replace(Regex("""["'\s+]"""), "")
            if (rawArg.length <= 3) return null

            val bodyPart = rawArg.dropLast(3)
            val saltPart = rawArg.takeLast(3)
            val saltStr = buildString {
                for (ch in saltPart) {
                    append(ch.code - 100)
                }
            }
            val salt = saltStr.toIntOrNull() ?: return null

            var b64 = bodyPart.replace('_', '+').replace('-', '/')
            val rem = b64.length % 4
            if (rem != 0) {
                b64 += "=".repeat(4 - rem)
            }
            val decodedBytes = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
            val decodedB64 = String(decodedBytes, Charsets.ISO_8859_1)

            val rot13 = buildString(decodedB64.length) {
                for (c in decodedB64) {
                    when (c) {
                        in 'A'..'Z' -> append(('A'.code + ((c.code - 'A'.code + 13) % 26)).toChar())
                        in 'a'..'z' -> append(('a'.code + ((c.code - 'a'.code + 13) % 26)).toChar())
                        else -> append(c)
                    }
                }
            }

            val symbols = charArrayOf('`', '%', '-', '+', '*', '$', '!', '_', '^', '=')
            val digits = StringBuilder(rot13.length)
            for (c in rot13) {
                val idx = symbols.indexOf(c)
                if (idx >= 0) digits.append(idx)
            }

            val out = StringBuilder(digits.length / 4)
            var i = 0
            while (i + 4 <= digits.length) {
                val chunk = digits.substring(i, i + 4).toIntOrNull() ?: break
                val charCode = (chunk % 1000) - salt
                if (charCode > 0) {
                    out.append(charCode.toChar())
                }
                i += 4
            }

            val jsConfig = out.toString()
            val cleanConfig = jsConfig.replace("\\/", "/")
            val streamMatch = Regex("""https?://[^\s"'<>]+\.(?:m3u8|mp4)[^\s"'<>]*""").find(cleanConfig)
                ?: Regex("""https?://[^\s"'<>]+\/stream\/[^\s"'<>]*""").find(cleanConfig)
            streamMatch?.value?.trim()?.takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            null
        }
    }

    private fun liveSearchRareAnimesPostOnDevice(title: String, seasonNum: Int): String? {
        val cleanTitle = cleanDesiTitle(title)
        if (cleanTitle.isBlank()) return null
        val searchUrl = "https://www.rareanimes.fans/?s=" + URLEncoder.encode(cleanTitle, "UTF-8")
        val html = httpGetHtml(searchUrl, referer = "https://rareanimes.fans/", timeoutMs = 6000) ?: return null

        val articleRegex = Regex("""<article\b[^>]*>([\s\S]*?)</article>""", RegexOption.IGNORE_CASE)
        val seasonRegex = Regex("""season\s*0*$seasonNum\b""", RegexOption.IGNORE_CASE)

        val articles = articleRegex.findAll(html).toList()
        for (a in articles) {
            val content = a.groupValues[1]
            val titleMatch = Regex("""<h2\b[^>]*>([\s\S]*?)</h2>""", RegexOption.IGNORE_CASE).find(content)
            val hrefMatch = Regex("""href=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE).find(content)
            if (titleMatch != null && hrefMatch != null) {
                val artTitle = titleMatch.groupValues[1].replace(Regex("<[^>]+>"), "").trim()
                val artUrl = hrefMatch.groupValues[1]
                if (seasonRegex.containsMatchIn(artTitle) || seasonRegex.containsMatchIn(artUrl)) {
                    return artUrl
                }
            }
        }

        for (a in articles) {
            val hrefMatch = Regex("""href=["'](https?://[^"']+)["']""", RegexOption.IGNORE_CASE).find(a.groupValues[1])
            if (hrefMatch != null) {
                return hrefMatch.groupValues[1]
            }
        }
        return null
    }

    /**
     * Multi-stage high-speed resolver for RR Nexiplay (RareAnimes / Codedew Zipper / Argon RazorShell).
     * 1. In-memory cache check (0ms)
     * 2. Direct playable URL check (0ms)
     * 3. High-speed backend API (/api/v1/anime/stream) (~300ms)
     * 4. Safe localized scraper fallback if the backend API returns no stream
     */
    suspend fun resolveRRStream(
        rawUrl: String,
        id: String,
        slug: String,
        season: Int,
        episode: Int,
        relativeEpisode: Int = episode,
        fallbackUrls: List<String> = emptyList(),
        title: String = "",
        onProgress: (Float, String) -> Unit = { _, _ -> }
    ): RRStreamResult? = withContext(Dispatchers.IO) {
        val cacheKey = "${id.trim()}_${slug.trim()}_${season}_${episode}"

        // 1. Check in-memory cache
        val cached = rrStreamCache[cacheKey]
        if (cached != null) {
            onProgress(1.0f, "Stream ready, starting player...")
            return@withContext cached
        }

        // 2. Check if rawUrl is already a direct playable stream (0ms)
        val lowerRaw = rawUrl.trim().lowercase()
        if (lowerRaw.contains(".m3u8") || lowerRaw.contains(".mp4") || lowerRaw.contains(".mkv") || lowerRaw.contains("workers.dev")) {
            val res = RRStreamResult(
                success = true,
                playbackUrl = rawUrl.trim(),
                embedUrl = rawUrl.trim(),
                format = "hls_stream"
            )
            rrStreamCache[cacheKey] = res
            onProgress(1.0f, "Stream ready, starting player...")
            return@withContext res
        }

        // 3. PRIORITY 1: On-Device Direct Season/Episode Scraper (resolves exact season post & client-IP signed HLS/MKV stream)
        onProgress(0.35f, "Connecting high-speed stream tunnel...")
        val basePostUrls = (fallbackUrls + listOf(rawUrl))
            .map { it.trim() }
            .filter { (it.contains("rareanimes.", ignoreCase = true) || it.contains("raretoons.", ignoreCase = true)) &&
                !it.contains("razorshell", ignoreCase = true) &&
                !it.contains("/embed/", ignoreCase = true) &&
                !it.contains("/embed/rr", ignoreCase = true)
            }
            .distinct()

        val urlSeasonRegex = Regex("""season-0*(\d+)\b""", RegexOption.IGNORE_CASE)
        val livePostUrls = buildList {
            for (u in basePostUrls) {
                val m = urlSeasonRegex.find(u)
                val parsedS = m?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (parsedS != null && parsedS != season) {
                    add(u.replace(urlSeasonRegex, "season-$season"))
                    val paddedS = season.toString().padStart(2, '0')
                    if (paddedS != season.toString()) {
                        add(u.replace(urlSeasonRegex, "season-$paddedS"))
                    }
                }
            }
            addAll(basePostUrls)
        }.distinct().toMutableList()

        if (livePostUrls.isEmpty() && (title.isNotBlank() || slug.isNotBlank())) {
            val queryTitle = title.ifBlank { slug }
            val foundPost = liveSearchRareAnimesPostOnDevice(queryTitle, season)
            if (!foundPost.isNullOrBlank()) {
                livePostUrls.add(foundPost)
            }
        }

        for (postUrl in livePostUrls) {
            try {
                val liveResolved = resolveWebEmbedUrl(
                    embedUrl = postUrl,
                    episodeNum = episode,
                    relativeEpisodeNum = relativeEpisode,
                    seasonNum = season
                ).trim()

                if (liveResolved.isNotBlank() && liveResolved != postUrl) {
                    val lowerLive = liveResolved.lowercase()
                    if (lowerLive.contains(".m3u8") || lowerLive.contains(".mp4") || lowerLive.contains(".mkv") || lowerLive.contains("workers.dev") || lowerLive.contains("flashzipper")) {
                        val res = RRStreamResult(
                            success = true,
                            playbackUrl = liveResolved,
                            embedUrl = liveResolved,
                            format = "hls_stream"
                        )
                        rrStreamCache[cacheKey] = res
                        onProgress(1.0f, "Stream ready, starting player...")
                        return@withContext res
                    }

                    if (lowerLive.contains("razorshell.space") || lowerLive.contains("seeking.buzz") || lowerLive.contains("codedew")) {
                        try {
                            val embedHtml = httpGetHtml(liveResolved, referer = "https://codedew.com/", timeoutMs = 4000)
                            if (!embedHtml.isNullOrBlank()) {
                                val directM3u8 = decodeJuicyCodesM3u8(embedHtml)
                                if (!directM3u8.isNullOrBlank()) {
                                    val res = RRStreamResult(
                                        success = true,
                                        playbackUrl = directM3u8,
                                        embedUrl = liveResolved,
                                        format = "hls_stream"
                                    )
                                    rrStreamCache[cacheKey] = res
                                    onProgress(1.0f, "Stream ready, starting player...")
                                    return@withContext res
                                }
                            }
                        } catch (_: Exception) {}
                    }

                    val res = RRStreamResult(
                        success = true,
                        playbackUrl = null,
                        embedUrl = liveResolved,
                        format = "embed"
                    )
                    rrStreamCache[cacheKey] = res
                    onProgress(1.0f, "Stream ready, starting player...")
                    return@withContext res
                }
            } catch (_: Exception) {}
        }

        // 4. Try other direct/zipper/embed candidates on-device
        onProgress(0.60f, "Checking alternate stream routes...")
        val otherCandidates = (listOf(rawUrl) + fallbackUrls)
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.contains("/embed/rr", ignoreCase = true) }
            .distinct()

        for (candidate in otherCandidates) {
            try {
                val lowerCandidate = candidate.lowercase()
                if (lowerCandidate.contains(".m3u8") || lowerCandidate.contains(".mp4") || lowerCandidate.contains(".mkv") || lowerCandidate.contains("workers.dev")) {
                    val res = RRStreamResult(
                        success = true,
                        playbackUrl = candidate,
                        embedUrl = candidate,
                        format = "hls_stream"
                    )
                    rrStreamCache[cacheKey] = res
                    onProgress(1.0f, "Stream ready, starting player...")
                    return@withContext res
                }

                val resolvedEmbed = resolveWebEmbedUrl(
                    embedUrl = candidate,
                    episodeNum = episode,
                    relativeEpisodeNum = relativeEpisode,
                    seasonNum = season
                ).trim()

                val lowerResolved = resolvedEmbed.lowercase()
                if (lowerResolved.contains(".m3u8") || lowerResolved.contains(".mp4") || lowerResolved.contains(".mkv") || lowerResolved.contains("workers.dev") || lowerResolved.contains("flashzipper")) {
                    val res = RRStreamResult(
                        success = true,
                        playbackUrl = resolvedEmbed,
                        embedUrl = resolvedEmbed,
                        format = "hls_stream"
                    )
                    rrStreamCache[cacheKey] = res
                    onProgress(1.0f, "Stream ready, starting player...")
                    return@withContext res
                }

                if (lowerResolved.contains("razorshell.space") || lowerResolved.contains("/embed/")) {
                    val embedHtml = httpGetHtml(
                        urlStr = resolvedEmbed,
                        referer = "https://codedew.com/",
                        timeoutMs = 5000
                    )
                    val directM3u8 = if (!embedHtml.isNullOrBlank()) {
                        decodeJuicyCodesM3u8(embedHtml)
                    } else null

                    if (directM3u8 != null && !directM3u8.contains("groovy.monster")) {
                        val res = RRStreamResult(
                            success = true,
                            playbackUrl = directM3u8,
                            embedUrl = resolvedEmbed,
                            format = "hls_stream"
                        )
                        rrStreamCache[cacheKey] = res
                        onProgress(1.0f, "Stream ready, starting player...")
                        return@withContext res
                    }
                }

                if (resolvedEmbed.isNotBlank() && resolvedEmbed != candidate && !lowerResolved.contains("razorshell.space")) {
                    val res = RRStreamResult(
                        success = true,
                        playbackUrl = null,
                        embedUrl = resolvedEmbed,
                        format = "embed"
                    )
                    rrStreamCache[cacheKey] = res
                    onProgress(1.0f, "Stream ready, starting player...")
                    return@withContext res
                }
            } catch (_: Exception) {}
        }

        // 5. PRIORITY 2 (FALLBACK): Backend API if on-device resolution could not reach upstream
        onProgress(0.80f, "Connecting backup stream tunnel...")
        val apiRes = fetchRRStreamApi(id = id, slug = slug, season = season, episode = episode)
        if (apiRes != null && (!apiRes.playbackUrl.isNullOrBlank() || !apiRes.embedUrl.isNullOrBlank())) {
            rrStreamCache[cacheKey] = apiRes
            onProgress(1.0f, "Stream ready, starting player...")
            return@withContext apiRes
        }

        null
    }

    suspend fun resolveStreamtapeLocally(url: String): StreamtapeResult = withContext(Dispatchers.IO) {
        val cleanUrl = url.trim()
        val lower = cleanUrl.lowercase()
        val embedUrl = when {
            lower.contains("/v/") -> cleanUrl.replace("/v/", "/e/")
            lower.contains("/e/") -> cleanUrl
            else -> {
                val parts = try { java.net.URI(cleanUrl).path.split("/").filter { it.isNotBlank() } } catch (_: Exception) { emptyList() }
                val id = parts.lastOrNull()
                if (!id.isNullOrBlank()) "https://streamtape.com/e/$id" else cleanUrl
            }
        }
        try {
            val html = httpGetHtml(embedUrl, referer = "https://streamtape.com/", timeoutMs = 8000)
            if (!html.isNullOrBlank()) {
                val match = Regex("""document\.getElementById\(['"](?:robotlink|botlink)['"]\)\.innerHTML\s*=\s*['"]([^'"]+)['"]\s*\+\s*\(([^)]+)\)\.substring\((\d+)\)(?:\.substring\((\d+)\))?""").find(html)
                if (match != null) {
                    val base = match.groupValues[1]
                    val partExpr = match.groupValues[2]
                    val litMatch = Regex("""['"]([^'"]+)['"]""").find(partExpr)
                    if (litMatch != null) {
                        val rawPart = litMatch.groupValues[1]
                        val sub1 = match.groupValues[3].toIntOrNull() ?: 0
                        var finalPart = if (sub1 < rawPart.length) rawPart.substring(sub1) else ""
                        val sub2 = match.groupValues.getOrNull(4)?.toIntOrNull()
                        if (sub2 != null && sub2 < finalPart.length) {
                            finalPart = finalPart.substring(sub2)
                        }
                        val fullUrl = "https:$base$finalPart&stream=1"
                        return@withContext StreamtapeResult(
                            directUrl = fullUrl,
                            embedUrl = embedUrl
                        )
                    }
                }
            }
        } catch (_: Exception) {}
        StreamtapeResult(directUrl = null, embedUrl = embedUrl)
    }

    suspend fun resolveWebEmbedUrl(
        embedUrl: String,
        episodeNum: Int = 1,
        relativeEpisodeNum: Int = episodeNum,
        seasonNum: Int = 1
    ): String = withContext(Dispatchers.IO) {
        var currentUrl = embedUrl.trim()
        if (currentUrl.isEmpty()) return@withContext embedUrl

        // Normalize bare numeric ToonStream IDs
        if (currentUrl.matches(Regex("""^\d+$"""))) {
            currentUrl = "https://toonstream.vip/?trembed=$currentUrl"
        }

        // 1. RareAnimes / RareToons post page -> extract episode's codedew zipper link
        var matchedExactEpisodeFromPost = false
        if (currentUrl.contains("rareanimes.", ignoreCase = true) ||
            currentUrl.contains("raretoons.", ignoreCase = true)
        ) {
            val postCandidates = buildList {
                val urlSeasonRegex = Regex("""season-0*(\d+)\b""", RegexOption.IGNORE_CASE)
                val m = urlSeasonRegex.find(currentUrl)
                val parsedS = m?.groupValues?.getOrNull(1)?.toIntOrNull()
                if (parsedS != null && parsedS != seasonNum) {
                    add(currentUrl.replace(urlSeasonRegex, "season-$seasonNum"))
                    val paddedS = seasonNum.toString().padStart(2, '0')
                    if (paddedS != seasonNum.toString()) {
                        add(currentUrl.replace(urlSeasonRegex, "season-$paddedS"))
                    }
                }
                add(currentUrl)
            }.distinct()

            val anyEpHeaderRegex = Regex("""(?:Episode|Ep\.?)\s*0*(\d+)\b""", RegexOption.IGNORE_CASE)

            for (candidatePostUrl in postCandidates) {
                try {
                    val postHtml = httpGetHtml(candidatePostUrl, referer = "https://rareanimes.net/", timeoutMs = 5000)
                    if (!postHtml.isNullOrBlank()) {
                        var matchedZipper: String? = null
                        val candidateEps = listOf(relativeEpisodeNum, episodeNum).distinct()
                        for (ep in candidateEps) {
                            val padded = ep.toString().padStart(2, '0')
                            val epHeaderRegex = Regex(
                                """(?:Episode|Ep\.?)\s*(?:0*$ep|$padded)\b""",
                                RegexOption.IGNORE_CASE
                            )
                            for (headerMatch in epHeaderRegex.findAll(postHtml)) {
                                val startIdx = headerMatch.range.first
                                val prefix = postHtml.substring((startIdx - 20).coerceAtLeast(0), startIdx).lowercase()
                                if (prefix.contains("href=") || prefix.contains("url=")) continue

                                val maxEndIdx = (startIdx + 2500).coerceAtMost(postHtml.length)
                                var endIdx = maxEndIdx
                                for (nextMatch in anyEpHeaderRegex.findAll(postHtml, headerMatch.range.last + 1)) {
                                    if (nextMatch.range.first >= maxEndIdx) break
                                    val nextPrefix = postHtml.substring((nextMatch.range.first - 20).coerceAtLeast(0), nextMatch.range.first).lowercase()
                                    if (nextPrefix.contains("href=") || nextPrefix.contains("url=")) continue
                                    val nextEpNum = nextMatch.groupValues.getOrNull(1)?.toIntOrNull()
                                    if (nextEpNum != null && nextEpNum != ep) {
                                        endIdx = nextMatch.range.first
                                        break
                                    }
                                }

                                val windowHtml = postHtml.substring(startIdx, endIdx)
                                val picked = pickBestZipperFromHtml(windowHtml)
                                if (!picked.isNullOrBlank()) {
                                    matchedZipper = picked
                                    matchedExactEpisodeFromPost = true
                                    break
                                }
                            }
                            if (!matchedZipper.isNullOrBlank()) break

                            val epZipperRegex1 = Regex(
                                """(?:Episode|Ep\.?)\s*(?:0*$ep|$padded)\b[\s\S]{1,800}?href=["'](https?://[^"']*zipper/[^"']*)["']""",
                                RegexOption.IGNORE_CASE
                            )
                            val epZipperRegex2 = Regex(
                                """href=["'](https?://[^"']*zipper/[^"']*)["'][^>]*>[\s\S]{0,160}?(?:Episode|Ep\.?)\s*(?:0*$ep|$padded)\b""",
                                RegexOption.IGNORE_CASE
                            )
                            val m = epZipperRegex1.find(postHtml) ?: epZipperRegex2.find(postHtml)
                            if (m != null) {
                                matchedZipper = m.groupValues[1].replace("&amp;", "&").trim()
                                matchedExactEpisodeFromPost = true
                                break
                            }
                        }

                        if (matchedZipper.isNullOrBlank()) {
                            matchedZipper = pickBestZipperFromHtml(postHtml)
                        }

                        if (matchedZipper.isNullOrBlank()) {
                            val anyZipper = Regex("""href=["'](https?://[^"']*zipper/[^"']*)["']""", RegexOption.IGNORE_CASE)
                                .find(postHtml)?.groupValues?.getOrNull(1)?.replace("&amp;", "&")?.trim()
                            if (!anyZipper.isNullOrBlank()) {
                                matchedZipper = anyZipper
                            }
                        }

                        if (!matchedZipper.isNullOrBlank()) {
                            currentUrl = matchedZipper
                            break
                        } else {
                            val directIframes = extractIframesFromHtml(postHtml, candidatePostUrl)
                            if (directIframes.isNotEmpty()) {
                                currentUrl = directIframes.first()
                                break
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
        }

        // 2. Codedew Zipper / StreamBeta page -> extract clean player iframe or direct stream URL
        if (currentUrl.contains("codedew.com/zipper", ignoreCase = true) ||
            currentUrl.contains("/zipper/", ignoreCase = true) ||
            currentUrl.contains("streambeta", ignoreCase = true)
        ) {
            try {
                val zipperHtml = httpGetHtml(currentUrl, referer = "https://rareanimes.net/", timeoutMs = 5000)
                if (!zipperHtml.isNullOrBlank()) {
                    val streamUrlMatch = Regex(""""stream_url"\s*:\s*"([^"]+)"""").find(zipperHtml)
                        ?: Regex(""""url"\s*:\s*"(https?:\\/\\/[^"]*workers\.dev[^"]*)"""").find(zipperHtml)
                        ?: Regex(""""url"\s*:\s*"(https?:\\/\\/[^"]*flashzipper[^"]*)"""").find(zipperHtml)
                    if (streamUrlMatch != null) {
                        return@withContext streamUrlMatch.groupValues[1].replace("\\/", "/")
                    }

                    val iframes = extractIframesFromHtml(zipperHtml, currentUrl)
                    val baseEmbedHost = iframes.firstOrNull()?.let {
                        try { java.net.URI(it).host } catch (_: Exception) { null }
                    } ?: "argon.razorshell.space"

                    // Parse all flat episode JSON objects inside Codedew's relatedData (supports any key order and both (s,e,id) & (season_num,episode_num,fid))
                    data class ZipperEpEntry(val id: String, val season: Int?, val episode: Int)
                    val idFieldRegex = Regex("\"(?:id|fid)\"\\s*:\\s*\"([A-Za-z0-9_-]+)\"")
                    val seasonFieldRegex = Regex("\"(?:s|season_num)\"\\s*:\\s*(\\d+)")
                    val epFieldRegex = Regex("\"(?:e|episode_num)\"\\s*:\\s*(\\d+)")

                    val entries = Regex("""\{[^{}]+\}""").findAll(zipperHtml).mapNotNull { objMatch ->
                        val block = objMatch.value
                        val idVal = idFieldRegex.find(block)?.groupValues?.getOrNull(1) ?: return@mapNotNull null
                        if (idVal.startsWith("sb_")) return@mapNotNull null
                        val epVal = epFieldRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull() ?: return@mapNotNull null
                        val sVal = seasonFieldRegex.find(block)?.groupValues?.getOrNull(1)?.toIntOrNull()
                        ZipperEpEntry(id = idVal, season = sVal, episode = epVal)
                    }.toList()

                    val candidateEps = listOf(relativeEpisodeNum, episodeNum).distinct()
                    for (ep in candidateEps) {
                        val exact = entries.firstOrNull { it.season == seasonNum && it.episode == ep }
                        if (exact != null) {
                            return@withContext "https://$baseEmbedHost/embed/${exact.id}"
                        }
                    }

                    // If the zipper link was already picked from the exact episode header on the target season's post,
                    // its primary iframe IS the exact episode — do not let a wrong season's relatedData entry override it!
                    if (matchedExactEpisodeFromPost && iframes.isNotEmpty()) {
                        return@withContext iframes.first()
                    }

                    for (ep in candidateEps) {
                        val anySeason = entries.firstOrNull { it.episode == ep }
                        if (anySeason != null) {
                            return@withContext "https://$baseEmbedHost/embed/${anySeason.id}"
                        }
                    }

                    if (iframes.isNotEmpty()) {
                        return@withContext iframes.first()
                    }
                }
            } catch (_: Exception) {}
        }

        // 3. Local recursive iframe unwrapping for ToonStream / WatchAnimeWorld / AnimeWorld / AnimeRulz
        var depth = 0
        while (depth < 4) {
            val lower = currentUrl.lowercase()
            val isToonstream = lower.contains("toonstream")
            val isAnimeWorld = lower.contains("watchanimeworld") || lower.contains("animeworld")
            val isWrapper = isToonstream || isAnimeWorld ||
                (lower.contains("animerulz") && !lower.contains("/embed/")) ||
                (lower.contains("animixstream") && !lower.contains("/embed/"))
            if (!isWrapper) break

            val html = httpGetHtml(currentUrl, referer = currentUrl, timeoutMs = 8000) ?: break
            val iframes = extractIframesFromHtml(html, currentUrl)
            if (iframes.isEmpty()) break

            val nextUrl = if (isToonstream) {
                iframes.firstOrNull { !it.lowercase().contains("toonstream") }
                    ?: iframes.firstOrNull { it.lowercase().contains("trembed=") }
                    ?: iframes.first()
            } else {
                iframes.first()
            }
            if (nextUrl == currentUrl) break
            currentUrl = nextUrl
            depth++
        }

        // 4. Fallback to web API resolver (handles MovieBox / SFlix / proxy-protected pages)
        val lowerFinal = currentUrl.lowercase()
        if (lowerFinal.contains("toonstream") ||
            lowerFinal.contains("watchanimeworld") ||
            lowerFinal.contains("animeworld") ||
            lowerFinal.contains("moviebox") ||
            lowerFinal.contains("sflix") ||
            lowerFinal.contains("aoneroom") ||
            lowerFinal.contains("netfilm") ||
            lowerFinal.contains("animekhor") ||
            lowerFinal.contains("miruro") ||
            lowerFinal.contains("aniclover")
        ) {
            try {
                val raw = httpGet("$BASE_WEB_URL/api/resolve-embed?url=${URLEncoder.encode(currentUrl, "UTF-8")}", timeoutMs = 12000)
                val obj = json.parseToJsonElement(raw).jsonObject
                val apiResolved = obj["url"]?.jsonPrimitive?.content?.takeIf { it.isNotBlank() }
                if (!apiResolved.isNullOrBlank()) {
                    return@withContext apiResolved
                }
            } catch (_: Exception) {}
        }

        currentUrl
    }
}
