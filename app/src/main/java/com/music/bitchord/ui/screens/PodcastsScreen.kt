package com.music.bitchord.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.music.bitchord.data.podcasts.PodcastApi
import com.music.bitchord.data.podcasts.PodcastLibrary
import com.music.bitchord.data.podcasts.PodcastResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Podcast categories matching the reference implementation.
 * Each category has a list of search queries; a random one is picked per fetch.
 */
private val PODCAST_CATEGORIES = listOf(
    PodcastCategory("Top in India", "Most popular podcasts right now", listOf("Raj Shamani", "WTF is with Nikhil Kamath", "The Ranveer Show", "Finshots Daily", "Paisa Vaisa", "Cyrus Says", "The Seen and the Unseen", "The Musafir Stories", "The Ken")),
    PodcastCategory("Top in USA", "Trending across the globe", listOf("The Daily", "This American Life", "Huberman Lab", "The Joe Rogan Experience", "Business Wars", "The Ezra Klein Show", "SmartLess", "Call Her Daddy", "Freakonomics Radio")),
    PodcastCategory("Business & Finance", "Market insights and money talks", listOf("Business podcast", "Finance podcast", "Investing podcast", "Planet Money")),
    PodcastCategory("Startups & Founders", "Stories of building companies", listOf("Startup podcast", "Founder interviews", "Venture Capital podcast", "First Principles")),
    PodcastCategory("Technology", "Tech news and deep dives", listOf("Technology podcast", "Tech news", "Hard Fork", "Lex Fridman")),
    PodcastCategory("Health & Science", "Wellness and discoveries", listOf("Health podcast", "Science podcast", "Huberman Lab", "Peter Attia")),
    PodcastCategory("Self-Improvement", "Become a better you", listOf("Self improvement podcast", "Motivation", "On Purpose with Jay Shetty")),
    PodcastCategory("News & Politics", "Stay informed", listOf("News podcast", "Daily news", "The Daily", "Up First")),
    PodcastCategory("Culture & Society", "Conversations shaping our world", listOf("Culture podcast", "Society podcast", "The Ezra Klein Show")),
    PodcastCategory("Comedy", "Laugh out loud", listOf("Comedy podcast", "Standup comedy")),
    PodcastCategory("Deep-Dive Interviews", "Long-form conversations", listOf("Interview podcast", "The Tim Ferriss Show", "Long-form podcast")),
)

private data class PodcastCategory(
    val title: String,
    val subtitle: String,
    val queries: List<String>,
)

private data class PodcastRow(
    val title: String,
    val subtitle: String,
    val items: List<PodcastResult>,
)

@Composable
fun PodcastsScreen(
    onOpenPodcast: (String, String, String?, String?) -> Unit = { _, _, _, _ -> },
) {
    val scope = rememberCoroutineScope()
    var rows by remember { mutableStateOf<List<PodcastRow>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var loadedCount by remember { mutableIntStateOf(0) }
    var loadingMore by remember { mutableStateOf(false) }
    val savedPodcasts by PodcastLibrary.savedPodcasts.collectAsState()

    // Load first 3 categories on mount
    LaunchedEffect(Unit) {
        loading = true
        val initialRows = fetchCategoryRows(0, 3)
        rows = initialRows
        loadedCount = 3
        loading = false
    }

    fun loadMore() {
        if (loadingMore || loadedCount >= PODCAST_CATEGORIES.size) return
        loadingMore = true
        scope.launch {
            val moreRows = fetchCategoryRows(loadedCount, 3)
            rows = rows + moreRows
            loadedCount += 3
            loadingMore = false
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Mic,
                        contentDescription = null,
                        modifier = Modifier.size(28.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Text(
                    text = "Podcasts",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                )
            }
        }

        // Saved podcasts section
        if (savedPodcasts.isNotEmpty()) {
            item {
                Text(
                    text = "Your Saved Podcasts",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            item {
                PodcastHorizontalRow(
                    podcasts = savedPodcasts.map { it.toPodcastResult() },
                    onOpenPodcast = onOpenPodcast,
                )
            }
        }

        // Loading skeleton
        if (loading) {
            items(3) { rowIdx ->
                Column(modifier = Modifier.padding(vertical = 8.dp)) {
                    Column(modifier = Modifier.padding(horizontal = 20.dp)) {
                        Box(
                            modifier = Modifier
                                .width(200.dp)
                                .height(24.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                        Spacer(Modifier.height(6.dp))
                        Box(
                            modifier = Modifier
                                .width(260.dp)
                                .height(14.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        repeat(5) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .size(140.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                                Spacer(Modifier.height(8.dp))
                                Box(
                                    modifier = Modifier
                                        .width(100.dp)
                                        .height(14.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(MaterialTheme.colorScheme.surfaceVariant),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Category rows
        if (!loading) {
            itemsIndexed(rows) { _, row ->
                CategoryRow(
                    title = row.title,
                    subtitle = row.subtitle,
                    podcasts = row.items,
                    onOpenPodcast = onOpenPodcast,
                )
            }
        }

        // Load more button
        if (!loading && loadedCount < PODCAST_CATEGORIES.size && !loadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Button(
                        onClick = { loadMore() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onBackground,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                        shape = RoundedCornerShape(24.dp),
                    ) {
                        Text(
                            text = "Load More Categories",
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }

        // Loading more indicator
        if (loadingMore) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    title: String,
    subtitle: String,
    podcasts: List<PodcastResult>,
    onOpenPodcast: (String, String, String?, String?) -> Unit,
) {
    Column(modifier = Modifier.padding(vertical = 8.dp)) {
        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(12.dp))
        PodcastHorizontalRow(
            podcasts = podcasts,
            onOpenPodcast = onOpenPodcast,
        )
    }
}

@Composable
private fun PodcastHorizontalRow(
    podcasts: List<PodcastResult>,
    onOpenPodcast: (String, String, String?, String?) -> Unit,
) {
    androidx.compose.foundation.lazy.LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        items(podcasts, key = { it.itunesId }) { podcast ->
            PodcastCard(
                podcast = podcast,
                onClick = {
                    onOpenPodcast(
                        podcast.title,
                        podcast.feedUrl ?: podcast.webUrl ?: "",
                        podcast.artworkUrl,
                        podcast.itunesId,
                    )
                },
            )
        }
    }
}

@Composable
private fun PodcastCard(
    podcast: PodcastResult,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clickable(onClick = onClick),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(podcast.artworkAt(300))
                .crossfade(true)
                .build(),
            contentDescription = podcast.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(140.dp)
                .clip(RoundedCornerShape(10.dp)),
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = podcast.title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.W600,
            color = MaterialTheme.colorScheme.onBackground,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = podcast.author,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Fetch podcast rows for given category range. Matches the reference fetchRows() logic.
 * For IN/US categories: search top 5 queries, merge results.
 * For query categories: pick random query, search both IN and US.
 */
private suspend fun fetchCategoryRows(startIndex: Int, count: Int): List<PodcastRow> {
    return withContext(Dispatchers.IO) {
        val categories = PODCAST_CATEGORIES.drop(startIndex).take(count)

        categories.mapNotNull { cat ->
            try {
                val results = mutableListOf<PodcastResult>()

                if (cat.title == "Top in India" || cat.title == "Top in USA") {
                    val country = if (cat.title == "Top in India") "IN" else "US"
                    val queries = cat.queries.take(5)
                    for (query in queries) {
                        val searchResults = PodcastApi.search(query, country = country, limit = 5)
                        results.addAll(searchResults)
                    }
                } else {
                    val query = cat.queries.random()
                    val inResults = PodcastApi.search(query, country = "IN", limit = 10)
                    val usResults = PodcastApi.search(query, country = "US", limit = 10)
                    results.addAll(inResults)
                    results.addAll(usResults)
                }

                // Deduplicate by collectionId
                val unique = results.distinctBy { it.itunesId }.take(15)

                if (unique.isNotEmpty()) {
                    PodcastRow(
                        title = cat.title,
                        subtitle = cat.subtitle,
                        items = unique,
                    )
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}
