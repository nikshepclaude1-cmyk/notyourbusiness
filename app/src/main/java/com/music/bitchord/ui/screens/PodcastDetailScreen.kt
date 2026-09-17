package com.music.bitchord.ui.screens

import android.content.Intent
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Bookmark
import androidx.compose.material.icons.rounded.BookmarkBorder
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import com.music.bitchord.data.podcasts.PodcastEpisode
import com.music.bitchord.data.podcasts.PodcastLibrary
import com.music.bitchord.data.podcasts.PodcastResult
import com.music.bitchord.data.podcasts.RawPodcastResult
import kotlinx.coroutines.launch

/**
 * Podcast detail screen matching the reference PodcastView.tsx.
 * Uses iTunes Lookup API with entity=podcastEpisode (not RSS).
 * Shows numbered episode list with artwork header, save, and share.
 */
@Composable
fun PodcastDetailScreen(
    title: String,
    feedUrl: String,
    artworkUrl: String? = null,
    itunesId: String? = null,
    onBack: () -> Unit,
    onPlayEpisode: (PodcastEpisode) -> Unit,
) {
    var episodes by remember { mutableStateOf<List<PodcastEpisode>>(emptyList()) }
    var podcastDetails by remember { mutableStateOf<RawPodcastResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var isSaved by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    // Check if saved
    LaunchedEffect(itunesId) {
        isSaved = itunesId?.let { PodcastLibrary.isSaved(it) } ?: false
    }

    // Fetch episodes via iTunes Lookup API
    LaunchedEffect(itunesId) {
        if (itunesId.isNullOrBlank()) {
            error = "No podcast ID available"
            loading = false
            return@LaunchedEffect
        }

        loading = true
        error = null

        try {
            val results = PodcastApi.lookupEpisodes(itunesId, limit = 200)

            if (results.isNotEmpty()) {
                // First result is the podcast metadata, rest are episodes
                val meta = results.find { it.wrapperType == "track" && it.kind == "podcast" }
                    ?: results.firstOrNull { it.wrapperType == "track" }
                podcastDetails = meta

                val rawEpisodes = results.filter { it.wrapperType == "podcastEpisode" }
                episodes = rawEpisodes.mapIndexed { index, raw ->
                    PodcastEpisode(
                        title = raw.trackName.ifBlank { "Untitled Episode" },
                        description = (raw.description ?: raw.shortDescription ?: "").take(500),
                        audioUrl = raw.episodeUrl ?: "",
                        audioType = "",
                        pubDate = raw.releaseDate ?: "",
                        pubDateMillis = 0L,
                        duration = formatDuration(raw.trackTimeMillis),
                        imageUrl = raw.artworkUrl600 ?: raw.artworkUrl100 ?: artworkUrl ?: "",
                        episodeNumber = index + 1,
                        seasonNumber = 0,
                        link = raw.trackViewUrl ?: "",
                    )
                }
                loading = false
            } else {
                error = "No episodes found"
                loading = false
            }
        } catch (e: Exception) {
            error = "Failed to load episodes: ${e.message}"
            loading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        // Header with gradient background
        val displayArtwork = podcastDetails?.artworkUrl600
            ?: podcastDetails?.artworkUrl100
            ?: artworkUrl

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    MaterialTheme.colorScheme.surface.copy(alpha = 0.95f)
                )
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = "Back",
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Podcast",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        // Artwork + Info section
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (displayArtwork != null) {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(displayArtwork.replace("600x600", "300x300"))
                        .crossfade(true)
                        .build(),
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(120.dp)
                        .clip(RoundedCornerShape(12.dp)),
                )
                Spacer(Modifier.width(16.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = podcastDetails?.artistName ?: "",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.W600,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "${episodes.size} episodes",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    // Save button
                    IconButton(
                        onClick = {
                            if (itunesId != null) {
                                val podcast = PodcastResult(
                                    itunesId = itunesId,
                                    title = title,
                                    author = podcastDetails?.artistName ?: "",
                                    artworkUrl = displayArtwork,
                                    feedUrl = feedUrl,
                                    webUrl = null,
                                    genre = podcastDetails?.primaryGenreName,
                                    episodeCount = episodes.size,
                                )
                                PodcastLibrary.toggle(podcast)
                                isSaved = PodcastLibrary.isSaved(itunesId)
                            }
                        },
                    ) {
                        Icon(
                            if (isSaved) Icons.Rounded.Bookmark else Icons.Rounded.BookmarkBorder,
                            contentDescription = if (isSaved) "Remove from library" else "Save to library",
                            tint = if (isSaved) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    // Share button
                    IconButton(
                        onClick = {
                            val shareText = buildString {
                                append("Check out this podcast: $title")
                                if (podcastDetails?.artistName?.isNotBlank() == true) {
                                    append(" by ${podcastDetails!!.artistName}")
                                }
                                if (itunesId != null) {
                                    append("\nhttps://podcasts.apple.com/podcast/id$itunesId")
                                }
                            }
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, shareText)
                            }
                            context.startActivity(Intent.createChooser(intent, "Share podcast"))
                        },
                    ) {
                        Icon(
                            Icons.Rounded.Share,
                            contentDescription = "Share",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        // Column headers
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "#",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(36.dp),
            )
            Text(
                text = "Episode",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Duration",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        // Content
        when {
            loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator()
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = error ?: "Unknown error",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Spacer(Modifier.height(12.dp))
                        IconButton(onClick = {
                            loading = true
                            error = null
                            scope.launch {
                                try {
                                    val results = PodcastApi.lookupEpisodes(itunesId ?: "", limit = 200)
                                    val rawEpisodes = results.filter { it.wrapperType == "podcastEpisode" }
                                    episodes = rawEpisodes.mapIndexed { index, raw ->
                                        PodcastEpisode(
                                            title = raw.trackName.ifBlank { "Untitled Episode" },
                                            description = (raw.description ?: raw.shortDescription ?: "").take(500),
                                            audioUrl = raw.episodeUrl ?: "",
                                            pubDate = raw.releaseDate ?: "",
                                            duration = formatDuration(raw.trackTimeMillis),
                                            imageUrl = raw.artworkUrl600 ?: raw.artworkUrl100 ?: artworkUrl ?: "",
                                            episodeNumber = index + 1,
                                            link = raw.trackViewUrl ?: "",
                                        )
                                    }
                                    loading = false
                                } catch (e: Exception) {
                                    error = "Failed to load episodes"
                                    loading = false
                                }
                            }
                        }) {
                            Icon(Icons.Rounded.Refresh, "Retry")
                        }
                    }
                }
            }
            episodes.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "No episodes found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            else -> {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = 100.dp, start = 20.dp, end = 20.dp),
                ) {
                    itemsIndexed(episodes, key = { _, ep -> ep.audioUrl }) { index, episode ->
                        EpisodeRow(
                            episode = episode,
                            episodeNumber = index + 1,
                            isCurrentTrack = false,
                            onClick = { onPlayEpisode(episode) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun EpisodeRow(
    episode: PodcastEpisode,
    episodeNumber: Int,
    isCurrentTrack: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Episode number / play indicator
        Text(
            text = episodeNumber.toString(),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = if (isCurrentTrack) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(36.dp),
        )

        // Episode info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = episode.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.W500,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (episode.pubDate.isNotBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    text = episode.pubDate.take(10),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        // Duration
        if (episode.displayDuration.isNotBlank()) {
            Spacer(Modifier.width(12.dp))
            Text(
                text = episode.displayDuration,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

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
