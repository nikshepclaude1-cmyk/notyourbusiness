package com.music.bitchord.data.podcasts

import android.net.Uri
import android.util.Log
import com.music.bitchord.data.Http
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.Request

object PodcastApi {
    private const val TAG = "PodcastApi"
    private const val BASE_URL = "https://itunes.apple.com/search"
    private const val LOOKUP_URL = "https://itunes.apple.com/lookup"

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    /**
     * Search iTunes for podcasts. Matches reference: searchITunes(term, 'podcast', 'podcast', limit, country)
     */
    suspend fun search(term: String, country: String = "US", limit: Int = 50): List<PodcastResult> = withContext(Dispatchers.IO) {
        val url = Uri.parse(BASE_URL).buildUpon()
            .appendQueryParameter("term", term)
            .appendQueryParameter("media", "podcast")
            .appendQueryParameter("entity", "podcast")
            .appendQueryParameter("country", country)
            .appendQueryParameter("limit", limit.toString())
            .build()
            .toString()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val searchResponse = json.decodeFromString<PodcastSearchResponse>(body)
            searchResponse.results.map { it.toPodcast() }
        } catch (e: Exception) {
            Log.e(TAG, "Podcast search failed for '$term': ${e.message}")
            emptyList()
        }
    }

    /**
     * Look up episodes for a podcast. Matches reference: getPodcastEpisodes(collectionId, limit)
     * Uses iTunes Lookup API with entity=podcastEpisode.
     */
    suspend fun lookupEpisodes(collectionId: String, country: String = "US", limit: Int = 200): List<RawPodcastResult> = withContext(Dispatchers.IO) {
        val url = Uri.parse(LOOKUP_URL).buildUpon()
            .appendQueryParameter("id", collectionId)
            .appendQueryParameter("entity", "podcastEpisode")
            .appendQueryParameter("country", country)
            .appendQueryParameter("limit", limit.toString())
            .build()
            .toString()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val lookupResponse = json.decodeFromString<PodcastSearchResponse>(body)
            lookupResponse.results
        } catch (e: Exception) {
            Log.e(TAG, "Podcast lookup failed for $collectionId: ${e.message}")
            emptyList()
        }
    }

    /**
     * Look up a podcast by ID to get its metadata (feedUrl, etc.)
     */
    suspend fun lookupPodcast(itunesId: String): PodcastResult? = withContext(Dispatchers.IO) {
        val url = Uri.parse(LOOKUP_URL).buildUpon()
            .appendQueryParameter("id", itunesId)
            .build()
            .toString()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext null
            val lookupResponse = json.decodeFromString<PodcastSearchResponse>(body)
            lookupResponse.results.firstOrNull()?.toPodcast()
        } catch (e: Exception) {
            Log.e(TAG, "Podcast lookup failed for $itunesId: ${e.message}")
            null
        }
    }

    /**
     * Fetch episodes for a podcast from its RSS feed (fallback).
     */
    suspend fun fetchEpisodes(feedUrl: String): List<PodcastEpisode> {
        return RssParser.fetchEpisodes(feedUrl)
    }

    /**
     * Search for podcast episodes directly. Matches reference: searchPodcastEpisodes(term, limit)
     * Searches both IN and US storefronts and merges results.
     */
    suspend fun searchEpisodes(term: String, limit: Int = 50): List<PodcastEpisode> = withContext(Dispatchers.IO) {
        val url = Uri.parse(BASE_URL).buildUpon()
            .appendQueryParameter("term", term)
            .appendQueryParameter("media", "podcast")
            .appendQueryParameter("entity", "podcastEpisode")
            .appendQueryParameter("limit", limit.toString())
            .build()
            .toString()

        try {
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "UvyTunes/1.0")
                .get()
                .build()

            val response = Http.client.newCall(request).execute()
            val body = response.body?.string() ?: return@withContext emptyList()
            val searchResponse = json.decodeFromString<PodcastSearchResponse>(body)
            searchResponse.results
                .filter { it.wrapperType == "podcastEpisode" }
                .map { it.toEpisode() }
        } catch (e: Exception) {
            Log.e(TAG, "Episode search failed for '$term': ${e.message}")
            emptyList()
        }
    }
}

@Serializable
data class PodcastSearchResponse(
    val resultCount: Int = 0,
    val results: List<RawPodcastResult> = emptyList(),
)

@Serializable
data class RawPodcastResult(
    val wrapperType: String = "",
    val kind: String = "",
    val collectionId: Long = 0,
    val trackId: Long = 0,
    val collectionName: String = "",
    val trackName: String = "",
    val artistName: String = "",
    val artworkUrl600: String? = null,
    val artworkUrl100: String? = null,
    val feedUrl: String? = null,
    val trackViewUrl: String? = null,
    val primaryGenreName: String? = null,
    val trackCount: Int = 0,
    val trackTimeMillis: Long = 0,
    val releaseDate: String? = null,
    val description: String? = null,
    val shortDescription: String? = null,
    val episodeUrl: String? = null,
    val country: String? = null,
    val contentAdvisoryRating: String? = null,
    val artistIds: List<Long>? = null,
    val collectionIds: List<Long>? = null,
) {
    fun toPodcast() = PodcastResult(
        itunesId = collectionId.toString(),
        title = collectionName,
        author = artistName,
        artworkUrl = artworkUrl600 ?: artworkUrl100,
        feedUrl = feedUrl,
        webUrl = trackViewUrl,
        genre = primaryGenreName,
        episodeCount = trackCount,
    )

    fun toEpisode() = PodcastEpisode(
        title = trackName.ifBlank { "Untitled Episode" },
        description = (description ?: shortDescription ?: "").take(500),
        audioUrl = episodeUrl ?: "",
        audioType = "",
        pubDate = releaseDate ?: "",
        pubDateMillis = 0L,
        duration = formatDuration(trackTimeMillis),
        imageUrl = artworkUrl600 ?: artworkUrl100 ?: "",
        episodeNumber = 0,
        seasonNumber = 0,
        link = trackViewUrl ?: "",
    )

    private fun formatDuration(millis: Long): String {
        if (millis <= 0) return ""
        val totalSeconds = millis / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60
        return if (hours > 0) {
            String.format("%d:%02d:%02d", hours, minutes, seconds)
        } else {
            String.format("%d:%02d", minutes, seconds)
        }
    }
}

data class PodcastResult(
    val itunesId: String,
    val title: String,
    val author: String,
    val artworkUrl: String?,
    val feedUrl: String?,
    val webUrl: String?,
    val genre: String?,
    val episodeCount: Int,
) {
    fun artworkAt(px: Int): String? = artworkUrl?.let { url ->
        url.replace("600x600", "${px}x${px}")
            .replace("100x100", "${px}x${px}")
    }
}
