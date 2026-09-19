package com.music.bitchord.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.music.bitchord.data.model.HomeShelf
import com.music.bitchord.data.podcasts.PodcastLibrary
import com.music.bitchord.data.podcasts.PodcastRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PodcastViewModel(application: Application) : AndroidViewModel(application) {

    private val _uiState = MutableStateFlow(PodcastUiState())
    val uiState: StateFlow<PodcastUiState> = _uiState.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    init {
        loadFeed()
    }

    fun loadFeed() {
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val shelves = PodcastRepository.loadPodcastFeed()
                _uiState.update {
                    it.copy(shelves = shelves, loading = false, loadedCount = shelves.size)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "Failed to load podcasts")
                }
            }
        }
    }

    fun search(query: String) {
        _searchQuery.value = query
        if (query.isBlank()) {
            loadFeed()
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(loading = true, error = null) }
            try {
                val results = PodcastRepository.searchPodcasts(query)
                _uiState.update {
                    it.copy(shelves = results, loading = false, loadedCount = results.size)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(loading = false, error = e.message ?: "Search failed")
                }
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (current.loadingMore || current.loading) return
        // iTunes categories are preloaded; no pagination needed for now
    }

    fun refresh() {
        _searchQuery.value = ""
        loadFeed()
    }
}

data class PodcastUiState(
    val shelves: List<HomeShelf> = emptyList(),
    val loading: Boolean = true,
    val loadingMore: Boolean = false,
    val error: String? = null,
    val loadedCount: Int = 0,
)
