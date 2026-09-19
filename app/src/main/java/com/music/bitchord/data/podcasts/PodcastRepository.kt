package com.music.bitchord.data.podcasts

import com.music.bitchord.data.innertube.Innertube
import com.music.bitchord.data.innertube.InnertubeParser
import com.music.bitchord.data.model.BrowseType
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.SearchFilter
import com.music.bitchord.data.model.SearchResult
import com.music.bitchord.data.model.ShelfItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext

/**
 * Unified podcast data source: Innertube (YouTube Music) + iTunes.
 * Produces HomeShelf rows compatible with the main-page UI pattern.
 */
object PodcastRepository {

    private val ITUNES_CATEGORIES = listOf(
        "Trending Podcasts" to listOf("news", "comedy", "true crime", "tech", "business"),
        "Popular in India" to listOf("hindi podcast", "indian stories", "bollywood", "cricket", "spiritual"),
        "Entertainment" to listOf("movie reviews", "celebrity", "gaming", "anime", "music"),
        "Society & Culture" to listOf("relationships", "self help", "philosophy", "history", "storytelling"),
        "Education" to listOf("science", "learning", "language", "coding", "math"),
    )

    /**
     * Load the full podcast home feed: iTunes category rows + Innertube supplements.
     */
    suspend fun loadPodcastFeed(): List<HomeShelf> = coroutineScope {
        val itunesShelves = async { loadITunesCategoryShelves() }
        val innertubeShelves = async { loadInnertubePodcastShelves() }

        val itunes = runCatching { itunesShelves.await() }.getOrDefault(emptyList())
        val innertube = runCatching { innertubeShelves.await() }.getOrDefault(emptyList())

        (innertube + itunes).distinctBy { it.title.lowercase() }
    }

    /**
     * Search podcasts from both sources and merge into HomeShelf rows.
     */
    suspend fun searchPodcasts(query: String): List<HomeShelf> = coroutineScope {
        val itunesJob = async { searchITunes(query) }
        val innertubeJob = async { searchInnertube(query) }

        val itunes = runCatching { itunesJob.await() }.getOrDefault(emptyList())
        val innertube = runCatching { innertubeJob.await() }.getOrDefault(emptyList())

        buildList {
            if (innertube.isNotEmpty()) add(HomeShelf(title = "YouTube Music", items = innertube))
            if (itunes.isNotEmpty()) add(HomeShelf(title = "Apple Podcasts", items = itunes))
        }
    }

    // ---- iTunes category shelves ------------------------------------------------

    private suspend fun loadITunesCategoryShelves(): List<HomeShelf> = withContext(Dispatchers.IO) {
        ITUNES_CATEGORIES.mapNotNull { (title, queries) ->
            try {
                val results = mutableListOf<ShelfItem>()
                for (query in queries.take(3)) {
                    val podcasts = PodcastApi.search(query, limit = 5)
                    results.addAll(podcasts.map { it.toShelfItem() })
                }
                if (results.isNotEmpty()) {
                    HomeShelf(title = title, items = results.distinctBy { it.title })
                } else null
            } catch (_: Exception) { null }
        }
    }

    // ---- Innertube podcast shelves ----------------------------------------------

    private suspend fun loadInnertubePodcastShelves(): List<HomeShelf> = withContext(Dispatchers.IO) {
        val queries = listOf("podcasts", "popular podcasts", "trending podcasts", "podcast episodes")
        queries.mapNotNull { query ->
            try {
                val response = Innertube.search(query, SearchFilter.ALL.params)
                val results = InnertubeParser.parseSearchPage(response, includeVideos = false)
                val podcastItems = results.rows.filterIsInstance<SearchResult.Browse>()
                    .filter { it.item.type == BrowseType.OTHER }
                    .map { it.item.toShelfItem() }
                    .take(20)

                if (podcastItems.isNotEmpty()) {
                    HomeShelf(title = "YouTube Music: $query", items = podcastItems)
                } else null
            } catch (_: Exception) { null }
        }
    }

    // ---- Search -----------------------------------------------------------------

    private suspend fun searchITunes(query: String): List<ShelfItem> = withContext(Dispatchers.IO) {
        try {
            PodcastApi.search(query, limit = 20).map { it.toShelfItem() }
        } catch (_: Exception) { emptyList() }
    }

    private suspend fun searchInnertube(query: String): List<ShelfItem> = withContext(Dispatchers.IO) {
        try {
            val response = Innertube.search(query, SearchFilter.ALL.params)
            val results = InnertubeParser.parseSearchPage(response, includeVideos = false)
            results.rows.filterIsInstance<SearchResult.Browse>()
                .filter { it.item.type == BrowseType.OTHER }
                .map { it.item.toShelfItem() }
                .take(20)
        } catch (_: Exception) { emptyList() }
    }

    // ---- Mappers ----------------------------------------------------------------

    private fun PodcastResult.toShelfItem() = ShelfItem(
        title = title,
        subtitle = "$author ${genre?.let { "· $it" } ?: ""}",
        thumbnailUrl = artworkUrl,
        videoId = null,
        browseId = "podcast:$itunesId",
    )

    private fun com.music.bitchord.data.model.BrowseItem.toShelfItem() = ShelfItem(
        title = title,
        subtitle = subtitle ?: "",
        thumbnailUrl = thumbnailUrl,
        videoId = null,
        browseId = browseId,
    )
}
