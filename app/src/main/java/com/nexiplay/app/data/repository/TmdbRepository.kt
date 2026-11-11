package com.nexiplay.app.data.repository

import com.nexiplay.app.data.model.Category
import com.nexiplay.app.data.model.Episode
import com.nexiplay.app.data.model.Movie
import com.nexiplay.app.data.model.MovieScreenshot
import com.nexiplay.app.data.model.Season
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.Request

data class TmdbDetailBundle(
    val movie: Movie,
    val seasons: List<Season>,
    val episodes: Map<String, List<Episode>>,
    val categories: List<Category>,
    val screenshots: List<MovieScreenshot>,
    val relatedMovies: List<Movie>,
    val ratingScore: String? = null,
    val duration: String? = null,
    val studioName: String? = null,
    val status: String? = null,
    val tagline: String? = null
)

/**
 * Kotlin port of `src/lib/tmdb.ts` from nexiplay-web-main.
 * Provides on-demand TMDB Movies, Series, Anime, Search, and Full Detail/Season/Episode hydration.
 */
object TmdbRepository {

    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    private const val TMDB_API_KEY = "59957f2abf22d1588ca52253cf83b859"

    private val httpClient = OkHttpClient.Builder()
        .connectionPool(ConnectionPool(8, 5, TimeUnit.MINUTES))
        .connectTimeout(7, TimeUnit.SECONDS)
        .readTimeout(7, TimeUnit.SECONDS)
        .build()

    private val STRIP_WORDS_REGEX = Regex(
        """\b(movie|film|season\s*\d+|s\d+|hindi|dubbed|bangla|dual\s*audio|\d{4})\b""",
        RegexOption.IGNORE_CASE
    )
    private val NON_ALPHANUM_REGEX = Regex("""[^a-z0-9]""")
    private val CLEAN_QUERY_REGEX = Regex(
        """\(\d{4}\)|\[.*?\]|season\s*\d+|s\d+|episode\s*\d+|ep\s*\d+|hindi|dubbed|bangla|dual\s*audio""",
        RegexOption.IGNORE_CASE
    )
    private val TRAILING_YEAR_REGEX = Regex("""-(\d{4})$""")
    private val WHITESPACE_REGEX = Regex("""\s+""")

    private val responseCache = ConcurrentHashMap<String, JsonObject>()
    private val detailBundleCache = ConcurrentHashMap<String, TmdbDetailBundle>()

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
        coerceInputValues = true
        isLenient = true
    }

    private val TMDB_GENRES: Map<String, Int> = mapOf(
        "action" to 28,
        "adventure" to 12,
        "animation" to 16,
        "comedy" to 35,
        "crime" to 80,
        "documentary" to 99,
        "drama" to 18,
        "family" to 10751,
        "fantasy" to 14,
        "history" to 36,
        "horror" to 27,
        "music" to 10402,
        "mystery" to 9648,
        "romance" to 10749,
        "science-fiction" to 878,
        "sci-fi" to 878,
        "scifi" to 878,
        "thriller" to 53,
        "war" to 10752,
        "western" to 37
    )

    fun normalizeTitle(title: String): String {
        return title
            .lowercase()
            .replace(STRIP_WORDS_REGEX, "")
            .replace(NON_ALPHANUM_REGEX, "")
            .trim()
    }

    fun deduplicateAgainstLocal(
        tmdbItems: List<Movie>,
        localItems: List<Movie>
    ): List<Movie> {
        val existingTmdbIds = HashSet<String>(localItems.size)
        val existingTitles = HashSet<String>(localItems.size)

        for (local in localItems) {
            local.tmdbId?.trim()?.takeIf { it.isNotEmpty() }?.let { existingTmdbIds.add(it) }
            val norm = normalizeTitle(local.title)
            if (norm.isNotEmpty()) existingTitles.add(norm)
        }

        val seenIds = HashSet<String>(tmdbItems.size)
        val result = ArrayList<Movie>(tmdbItems.size)
        for (item in tmdbItems) {
            val rawId = item.tmdbId ?: item.id.removePrefix("tmdb-")
            val norm = normalizeTitle(item.title)
            if (rawId in existingTmdbIds) continue
            if (norm.isNotEmpty() && norm in existingTitles) continue
            if (!seenIds.add(item.id)) continue
            if (item.posterUrl.isNullOrBlank()) continue
            result.add(item)
        }
        return result
    }

    private fun tmdbGet(endpoint: String, params: Map<String, String> = emptyMap()): JsonObject? {
        return try {
            val queryParts = ArrayList<String>(params.size + 2)
            queryParts.add("api_key=$TMDB_API_KEY")
            queryParts.add("language=en-US")
            for ((k, v) in params) {
                queryParts.add("${URLEncoder.encode(k, "UTF-8")}=${URLEncoder.encode(v, "UTF-8")}")
            }
            val urlStr = "$TMDB_BASE_URL$endpoint?${queryParts.joinToString("&")}"
            responseCache[urlStr]?.let { return it }

            val req = Request.Builder()
                .url(urlStr)
                .header("Accept", "application/json")
                .build()

            httpClient.newCall(req).execute().use { response ->
                if (!response.isSuccessful) return null
                val body = response.body?.string() ?: return null
                val parsed = json.parseToJsonElement(body).jsonObject
                if (responseCache.size > 150) {
                    responseCache.clear()
                }
                responseCache[urlStr] = parsed
                parsed
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun jsonToMovie(obj: JsonObject, defaultType: String): Movie? {
        val id = obj["id"]?.jsonPrimitive?.intOrNull ?: return null
        val title = obj["title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: obj["name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: obj["original_title"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: obj["original_name"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: return null

        val posterPath = obj["poster_path"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val backdropPath = obj["backdrop_path"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val date = obj["release_date"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: obj["first_air_date"]?.jsonPrimitive?.contentOrNull.orEmpty()
        val year = date.take(4).toIntOrNull()
        val vote = obj["vote_average"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val overview = obj["overview"]?.jsonPrimitive?.contentOrNull
        val lang = obj["original_language"]?.jsonPrimitive?.contentOrNull ?: "en"
        val isAdult = obj["adult"]?.jsonPrimitive?.booleanOrNull == true

        return Movie(
            id = "tmdb-$id",
            title = title,
            slug = "tmdb-$id",
            posterUrl = posterPath?.let { "https://image.tmdb.org/t/p/w342$it" },
            description = overview,
            type = defaultType,
            releaseYear = year,
            language = lang,
            bannerUrlDesktop = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
            bannerUrlMobile = backdropPath?.let { "https://image.tmdb.org/t/p/w780$it" },
            isAdult = isAdult,
            isTrending = vote >= 7.0,
            trendingBadge = vote >= 7.5,
            tmdbId = id.toString(),
            createdAt = date.ifBlank { null }
        )
    }

    suspend fun fetchTmdbMovies(page: Int = 1, genreSlug: String? = null): List<Movie> = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "page" to page.coerceAtLeast(1).toString(),
            "sort_by" to "popularity.desc",
            "include_adult" to "false"
        )
        genreSlug?.lowercase()?.let { TMDB_GENRES[it] }?.let { gId ->
            params["with_genres"] = gId.toString()
        }
        val root = tmdbGet("/discover/movie", params) ?: return@withContext emptyList()
        val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
        results.mapNotNull { el ->
            try {
                jsonToMovie(el.jsonObject, "movie")
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchTmdbSeries(page: Int = 1, genreSlug: String? = null): List<Movie> = withContext(Dispatchers.IO) {
        val params = mutableMapOf(
            "page" to page.coerceAtLeast(1).toString(),
            "sort_by" to "popularity.desc",
            "include_adult" to "false",
            "without_genres" to "16"
        )
        genreSlug?.lowercase()?.let { TMDB_GENRES[it] }?.let { gId ->
            params["with_genres"] = gId.toString()
        }
        val root = tmdbGet("/discover/tv", params) ?: return@withContext emptyList()
        val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
        results.mapNotNull { el ->
            try {
                jsonToMovie(el.jsonObject, "series")
            } catch (_: Exception) {
                null
            }
        }
    }

    suspend fun fetchTmdbAnime(page: Int = 1): List<Movie> = withContext(Dispatchers.IO) {
        val params = mapOf(
            "page" to page.coerceAtLeast(1).toString(),
            "sort_by" to "popularity.desc",
            "with_genres" to "16",
            "with_original_language" to "ja",
            "include_adult" to "false"
        )
        val root = tmdbGet("/discover/tv", params) ?: return@withContext emptyList()
        val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
        results.mapNotNull { el ->
            try {
                jsonToMovie(el.jsonObject, "anime")
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Fetches a fresh, randomized mix of TMDB Movies, Series, and Anime on every app launch/refresh
     * using HTTP/2 connection pooling (3 fast parallel requests).
     */
    suspend fun fetchFreshHomeMix(): Triple<List<Movie>, List<Movie>, List<Movie>> = coroutineScope {
        val moviePage = (1..10).random()
        val seriesPage = (1..10).random()
        val animePage = (1..10).random()

        val movDef = async(Dispatchers.IO) { fetchTmdbMovies(moviePage) }
        val serDef = async(Dispatchers.IO) { fetchTmdbSeries(seriesPage) }
        val aniDef = async(Dispatchers.IO) { fetchTmdbAnime(animePage) }

        val movies = movDef.await().shuffled()
        val series = serDef.await().shuffled()
        val anime = aniDef.await().shuffled()

        Triple(movies, series, anime)
    }

    suspend fun searchTmdb(query: String, page: Int = 1): List<Movie> = withContext(Dispatchers.IO) {
        if (query.trim().length < 2) return@withContext emptyList()
        val root = tmdbGet(
            "/search/multi",
            mapOf(
                "query" to query.trim(),
                "page" to page.toString(),
                "include_adult" to "false"
            )
        ) ?: return@withContext emptyList()

        val results = root["results"]?.jsonArray ?: JsonArray(emptyList())
        results.mapNotNull { el ->
            try {
                val obj = el.jsonObject
                val mediaType = obj["media_type"]?.jsonPrimitive?.contentOrNull ?: "movie"
                if (mediaType == "person") return@mapNotNull null
                val genreIds = obj["genre_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.intOrNull } ?: emptyList()
                val origLang = obj["original_language"]?.jsonPrimitive?.contentOrNull?.lowercase()
                val isAnime = genreIds.contains(16) && origLang in setOf("ja", "zh", "ko")
                val resolvedType = when {
                    isAnime -> "anime"
                    mediaType == "tv" -> "series"
                    else -> "movie"
                }
                jsonToMovie(obj, resolvedType)
            } catch (_: Exception) {
                null
            }
        }
    }

    /**
     * Resolves a TMDB title (`tmdb-12345`, explicit TMDB ID, or fallback search by title/slug) into a full DetailBundle
     * so ContentDetailScreen and WatchScreen work seamlessly for both virtual TMDB items and local Supabase items.
     */
    suspend fun fetchTmdbDetailBundle(
        type: String,
        rawSlug: String,
        explicitTmdbId: String? = null,
        fallbackTitle: String? = null
    ): TmdbDetailBundle? = withContext(Dispatchers.IO) {
        val cleanType = type.lowercase()
        val bundleKey = "${cleanType}_${rawSlug.lowercase()}_${explicitTmdbId.orEmpty()}"
        detailBundleCache[bundleKey]?.let { return@withContext it }

        val isTv = cleanType == "series" || cleanType == "anime" || cleanType == "tv"
        val media = if (isTv) "tv" else "movie"

        var tmdbId: Int? = explicitTmdbId?.trim()?.removePrefix("tmdb-")?.toIntOrNull()
        if (tmdbId == null) {
            if (rawSlug.startsWith("tmdb-")) {
                tmdbId = rawSlug.removePrefix("tmdb-").toIntOrNull()
            } else if (rawSlug.all { it.isDigit() }) {
                tmdbId = rawSlug.toIntOrNull()
            } else {
                val rawQuery = (fallbackTitle?.takeIf { it.isNotBlank() } ?: rawSlug.replace("-", " "))
                val cleanQuery = rawQuery
                    .replace(CLEAN_QUERY_REGEX, "")
                    .replace(TRAILING_YEAR_REGEX, "")
                    .replace(WHITESPACE_REGEX, " ")
                    .trim()
                if (cleanQuery.length >= 2) {
                    val mediaSearch = tmdbGet(
                        "/search/$media",
                        mapOf("query" to cleanQuery, "include_adult" to "false")
                    )
                    val firstMediaId = mediaSearch?.get("results")?.jsonArray
                        ?.firstOrNull()?.jsonObject?.get("id")?.jsonPrimitive?.intOrNull
                    tmdbId = firstMediaId ?: searchTmdb(cleanQuery, 1).firstOrNull()?.tmdbId?.toIntOrNull()
                }
            }
        }

        val finalId = tmdbId ?: return@withContext null

        val detailObj = tmdbGet(
            "/$media/$finalId",
            mapOf("append_to_response" to "credits,videos,external_ids,images,recommendations")
        ) ?: return@withContext null

        val genresArr = detailObj["genres"]?.jsonArray ?: JsonArray(emptyList())
        val categories = genresArr.mapNotNull { g ->
            val gObj = g.jsonObject
            val gId = gObj["id"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
            val gName = gObj["name"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
            Category(id = gId.toString(), name = gName, slug = gName.lowercase().replace(" ", "-"))
        }

        val origLang = (detailObj["original_language"]?.jsonPrimitive?.contentOrNull ?: "en").lowercase()
        val isAnime = categories.any { it.id == "16" } && origLang in setOf("ja", "zh", "ko")
        val resolvedType = when {
            isAnime || cleanType == "anime" -> "anime"
            isTv -> "series"
            else -> "movie"
        }

        val baseMovie = jsonToMovie(detailObj, resolvedType) ?: return@withContext null

        val castList = detailObj["credits"]?.jsonObject?.get("cast")?.jsonArray
            ?.take(8)
            ?.mapNotNull { it.jsonObject["name"]?.jsonPrimitive?.contentOrNull }
            ?.joinToString(", ")

        val imdbId = detailObj["imdb_id"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
            ?: detailObj["external_ids"]?.jsonObject?.get("imdb_id")?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val runtime = detailObj["runtime"]?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
        val tvRunTime = detailObj["episode_run_time"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }
            ?: detailObj["last_episode_to_air"]?.jsonObject?.get("runtime")?.jsonPrimitive?.intOrNull?.takeIf { it > 0 }

        val durationStr = when {
            runtime != null -> "$runtime mins"
            tvRunTime != null -> "${tvRunTime}m / ep"
            isTv -> "24m / ep"
            else -> "HD"
        }

        val voteAvg = detailObj["vote_average"]?.jsonPrimitive?.doubleOrNull ?: 0.0
        val ratingScoreStr = if (voteAvg > 0.0) String.format(java.util.Locale.US, "%.1f", voteAvg) else "8.5"

        val studioNameStr = detailObj["networks"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            ?: detailObj["production_companies"]?.jsonArray?.firstOrNull()?.jsonObject?.get("name")?.jsonPrimitive?.contentOrNull
            ?: "Original"

        val statusStr = detailObj["status"]?.jsonPrimitive?.contentOrNull ?: "Released"
        val taglineStr = detailObj["tagline"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }

        val trailerKey = detailObj["videos"]?.jsonObject?.get("results")?.jsonArray
            ?.map { it.jsonObject }
            ?.firstOrNull {
                it["site"]?.jsonPrimitive?.contentOrNull.equals("YouTube", ignoreCase = true) &&
                    (it["type"]?.jsonPrimitive?.contentOrNull.equals("Trailer", ignoreCase = true) ||
                        it["type"]?.jsonPrimitive?.contentOrNull.equals("Teaser", ignoreCase = true))
            }
            ?.get("key")?.jsonPrimitive?.contentOrNull

        val enrichedMovie = baseMovie.copy(
            imdbId = imdbId,
            castMembers = castList?.takeIf { it.isNotBlank() },
            format = durationStr,
            trailerUrl = trailerKey?.let { "https://www.youtube.com/watch?v=$it" }
        )

        val screenshots = detailObj["images"]?.jsonObject?.get("backdrops")?.jsonArray
            ?.take(8)
            ?.mapNotNull { b ->
                val path = b.jsonObject["file_path"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                MovieScreenshot(
                    id = "shot-$path",
                    movieId = enrichedMovie.id,
                    imageUrl = "https://image.tmdb.org/t/p/w780$path"
                )
            } ?: emptyList()

        val relatedMovies = detailObj["recommendations"]?.jsonObject?.get("results")?.jsonArray
            ?.take(10)
            ?.mapNotNull { r ->
                try {
                    jsonToMovie(r.jsonObject, resolvedType)
                } catch (_: Exception) {
                    null
                }
            } ?: emptyList()

        val seasons = mutableListOf<Season>()
        val episodesMap = mutableMapOf<String, List<Episode>>()

        if (isTv) {
            val seasonsArr = detailObj["seasons"]?.jsonArray ?: JsonArray(emptyList())
            for (sEl in seasonsArr) {
                val sObj = sEl.jsonObject
                val seasonNum = sObj["season_number"]?.jsonPrimitive?.intOrNull ?: continue
                if (seasonNum <= 0) continue
                val epCount = (sObj["episode_count"]?.jsonPrimitive?.intOrNull ?: 12).coerceIn(1, 200)
                val sId = "tmdb-s-$finalId-$seasonNum"
                val sPoster = sObj["poster_path"]?.jsonPrimitive?.contentOrNull?.let { "https://image.tmdb.org/t/p/w500$it" }
                seasons.add(
                    Season(
                        id = sId,
                        movieId = enrichedMovie.id,
                        seasonNumber = seasonNum,
                        seasonTitle = sObj["name"]?.jsonPrimitive?.contentOrNull ?: "Season $seasonNum",
                        posterUrl = sPoster
                    )
                )
                episodesMap[sId] = (1..epCount).map { epNum ->
                    Episode(
                        id = "tmdb-ep-$finalId-$seasonNum-$epNum",
                        seasonId = sId,
                        episodeNumber = epNum,
                        episodeTitle = "Episode $epNum"
                    )
                }
            }
        }

        val bundle = TmdbDetailBundle(
            movie = enrichedMovie,
            seasons = seasons,
            episodes = episodesMap,
            categories = categories,
            screenshots = screenshots,
            relatedMovies = relatedMovies,
            ratingScore = ratingScoreStr,
            duration = durationStr,
            studioName = studioNameStr,
            status = statusStr,
            tagline = taglineStr
        )
        if (detailBundleCache.size > 60) {
            detailBundleCache.clear()
        }
        detailBundleCache[bundleKey] = bundle
        bundle
    }
}
