package com.music.bitchord.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.music.bitchord.R
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.model.ShelfItem
import com.music.bitchord.data.podcasts.PodcastLibrary
import com.music.bitchord.ui.PodcastViewModel
import kotlinx.coroutines.flow.distinctUntilChanged

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PodcastsScreen(
    onOpenPodcast: (title: String, feedUrl: String, artworkUrl: String, itunesId: String) -> Unit,
    onPlayEpisode: (com.music.bitchord.data.podcasts.PodcastEpisode) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(),
) {
    val vm: PodcastViewModel = viewModel()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val searchQuery by vm.searchQuery.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val pullRefreshState = rememberPullToRefreshState()
    var query by rememberSaveable { mutableStateOf("") }
    val context = LocalContext.current

    // Infinite scroll — load more when near the bottom
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }
            .distinctUntilChanged()
            .collect { layoutInfo ->
                val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                if (lastVisible >= layoutInfo.totalItemsCount - 3) {
                    vm.loadMore()
                }
            }
    }

    PullToRefreshBox(
        isRefreshing = state.loading && state.shelves.isNotEmpty(),
        onRefresh = {
            query = ""
            vm.refresh()
        },
        state = pullRefreshState,
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // Search bar
            TextField(
                value = query,
                onValueChange = {
                    query = it
                    vm.search(it)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = {
                    Text(
                        text = stringResource(R.string.search),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Rounded.Search,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = {
                            query = ""
                            vm.refresh()
                        }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
            )

            when {
                state.loading && state.shelves.isEmpty() -> {
                    // Initial loading
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(48.dp))
                        Text(
                            text = "Loading…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                }
                state.error != null && state.shelves.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(contentPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Mic,
                            contentDescription = null,
                            modifier = Modifier.size(64.dp),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = state.error ?: "Something went wrong",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                        )
                        IconButton(onClick = { vm.refresh() }) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = "Retry",
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(
                            top = contentPadding.calculateTopPadding(),
                            bottom = contentPadding.calculateBottomPadding() + 80.dp,
                        ),
                    ) {
                        // Saved podcasts section
                        item(key = "saved_header") {
                            val library = remember { PodcastLibrary }
                            val saved by library.savedPodcasts.collectAsStateWithLifecycle()
                            if (saved.isNotEmpty()) {
                                val savedShelf = HomeShelf(
                                    title = "Your Saved Podcasts",
                                    items = saved.map { sp ->
                                        ShelfItem(
                                            title = sp.title,
                                            subtitle = sp.author,
                                            thumbnailUrl = sp.artworkUrl,
                                            videoId = null,
                                            browseId = "podcast:${sp.itunesId}",
                                        )
                                    },
                                )
                                Shelf(
                                    shelf = savedShelf,
                                    onItemClick = { item ->
                                        val savedItem = saved.find { "podcast:${it.itunesId}" == item.browseId }
                                        if (savedItem != null) {
                                            onOpenPodcast(
                                                savedItem.title,
                                                savedItem.feedUrl ?: "",
                                                savedItem.artworkUrl ?: "",
                                                savedItem.itunesId,
                                            )
                                        }
                                    },
                                )
                            }
                        }

                        // Main shelves
                        items(
                            items = state.shelves,
                            key = { it.title },
                        ) { shelf ->
                            Shelf(
                                shelf = shelf,
                                onItemClick = { item ->
                                    handlePodcastItemClick(
                                        item = item,
                                        onOpenPodcast = onOpenPodcast,
                                    )
                                },
                            )
                        }

                        // Loading more indicator
                        if (state.loadingMore) {
                            item(key = "loading_more") {
                                CircularProgressIndicator(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp)
                                        .size(32.dp),
                                    strokeWidth = 2.dp,
                                )
                            }
                        }

                        // End of feed
                        if (!state.loading && !state.loadingMore && state.shelves.isNotEmpty()) {
                            item(key = "end") {
                                Text(
                                    text = "You're all caught up!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun handlePodcastItemClick(
    item: ShelfItem,
    onOpenPodcast: (title: String, feedUrl: String, artworkUrl: String, itunesId: String) -> Unit,
) {
    val browseId = item.browseId ?: return
    if (browseId.startsWith("podcast:")) {
        val itunesId = browseId.removePrefix("podcast:")
        onOpenPodcast(item.title, "", item.thumbnailUrl ?: "", itunesId)
    }
}
