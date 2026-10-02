package com.openmapsrouter.app

import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.lifecycle.lifecycleScope
import com.openmapsrouter.app.data.AppSettings
import com.openmapsrouter.app.data.CoordinateResult
import com.openmapsrouter.app.data.HistoryItem
import com.openmapsrouter.app.data.StorageManager
import com.openmapsrouter.app.parser.MapsParser
import com.openmapsrouter.app.search.PlaceSearchService
import com.openmapsrouter.app.search.PlaceSuggestion
import com.openmapsrouter.app.ui.MainScreen
import com.openmapsrouter.app.ui.theme.OpenMapsRouterTheme
import com.openmapsrouter.app.utils.OrganicMapsLauncher
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var storageManager: StorageManager
    private var lastCheckedClipboard: String = ""

    private var activeTabState = mutableStateOf(0) // 0: Search Places, 1: Convert Link
    private var searchQueryState = mutableStateOf("")
    private var suggestionsState = mutableStateOf<List<PlaceSuggestion>>(emptyList())
    private var isSearchingState = mutableStateOf(false)
    private var searchJob: Job? = null

    private var inputUrlState = mutableStateOf("")
    private var currentResultState = mutableStateOf<CoordinateResult?>(null)
    private var isLoadingState = mutableStateOf(false)
    private var errorMessageState = mutableStateOf<String?>(null)
    private var clipboardUrlState = mutableStateOf<String?>(null)
    private var historyState = mutableStateOf<List<HistoryItem>>(emptyList())
    private var settingsState = mutableStateOf(AppSettings())

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        storageManager = StorageManager(this)
        settingsState.value = storageManager.getSettings()
        historyState.value = storageManager.getHistory()

        handleIntent(intent)

        setContent {
            OpenMapsRouterTheme {
                MainScreen(
                    activeTab = activeTabState.value,
                    onTabChange = { activeTabState.value = it },
                    searchQuery = searchQueryState.value,
                    onSearchQueryChange = { onSearchQueryChanged(it) },
                    suggestions = suggestionsState.value,
                    isSearching = isSearchingState.value,
                    onSelectSuggestion = { onSuggestionSelected(it) },
                    onPerformSearch = { onPerformSearch(it) },
                    inputUrl = inputUrlState.value,
                    onInputUrlChange = {
                        inputUrlState.value = it
                        if (errorMessageState.value != null) {
                            errorMessageState.value = null
                        }
                    },
                    currentResult = currentResultState.value,
                    isLoading = isLoadingState.value,
                    errorMessage = errorMessageState.value,
                    clipboardUrl = clipboardUrlState.value,
                    onDismissClipboard = { clipboardUrlState.value = null },
                    history = historyState.value,
                    settings = settingsState.value,
                    onUpdateSettings = { newSettings ->
                        settingsState.value = newSettings
                        storageManager.saveSettings(newSettings)
                    },
                    onDeriveCoordinates = { rawText ->
                        processCoordinates(rawText)
                    },
                    onDeleteHistoryItem = { id ->
                        historyState.value = storageManager.deleteHistoryItem(id)
                    },
                    onClearAllHistory = {
                        storageManager.clearHistory()
                        historyState.value = emptyList()
                    },
                    onSelectHistoryItem = { item ->
                        currentResultState.value = item.toCoordinateResult()
                        inputUrlState.value = item.originalUrl
                    }
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkClipboard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent == null) return

        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val data = intent.dataString
                if (!data.isNullOrBlank()) {
                    activeTabState.value = 1
                    inputUrlState.value = data
                    processCoordinates(data)
                }
            }
            Intent.ACTION_SEND -> {
                if (intent.type == "text/plain") {
                    val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
                    if (!sharedText.isNullOrBlank()) {
                        activeTabState.value = 1
                        inputUrlState.value = sharedText
                        processCoordinates(sharedText)
                    }
                }
            }
        }
    }

    private fun checkClipboard() {
        try {
            val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val item = clipboard.primaryClip?.getItemAt(0)
            val text = item?.text?.toString()?.trim()

            if (!text.isNullOrBlank() && text != lastCheckedClipboard) {
                val hasUrl = MapsParser.extractUrlFromText(text)
                val isDirect = MapsParser.extractDirectCoordinates(text)

                if (hasUrl != null || isDirect != null) {
                    if (text.contains("maps.google.", ignoreCase = true) ||
                        text.contains("goo.gl/maps", ignoreCase = true) ||
                        text.contains("maps.app.goo.gl", ignoreCase = true) ||
                        text.contains("google.com/maps", ignoreCase = true) ||
                        isDirect != null
                    ) {
                        lastCheckedClipboard = text
                        clipboardUrlState.value = hasUrl ?: text
                    }
                }
            }
        } catch (e: Exception) {
            // Ignore clipboard errors
        }
    }

    private fun onSearchQueryChanged(query: String) {
        searchQueryState.value = query
        searchJob?.cancel()

        if (query.trim().length < 2) {
            suggestionsState.value = emptyList()
            isSearchingState.value = false
            return
        }

        isSearchingState.value = true
        searchJob = lifecycleScope.launch {
            delay(280) // Debounce typing
            try {
                val results = PlaceSearchService.fetchSuggestions(
                    query = query,
                    apiKey = settingsState.value.googleApiKey.ifBlank { null }
                )
                suggestionsState.value = results
            } catch (e: Exception) {
                suggestionsState.value = emptyList()
            } finally {
                isSearchingState.value = false
            }
        }
    }

    private fun onSuggestionSelected(suggestion: PlaceSuggestion) {
        suggestionsState.value = emptyList()
        searchQueryState.value = suggestion.name
        isLoadingState.value = true
        errorMessageState.value = null

        lifecycleScope.launch {
            try {
                val result = PlaceSearchService.resolvePlaceDetails(
                    suggestion = suggestion,
                    apiKey = settingsState.value.googleApiKey.ifBlank { null }
                )
                currentResultState.value = result

                if (settingsState.value.keepHistory) {
                    historyState.value = storageManager.addHistoryItem(result)
                }

                if (settingsState.value.autoOpenOrganicMaps) {
                    OrganicMapsLauncher.launchOrganicMaps(
                        context = this@MainActivity,
                        result = result,
                        preferredScheme = settingsState.value.preferredScheme
                    )
                }
            } catch (e: Exception) {
                errorMessageState.value = e.message ?: "Could not resolve coordinates for this location."
            } finally {
                isLoadingState.value = false
            }
        }
    }

    private fun onPerformSearch(query: String) {
        suggestionsState.value = emptyList()
        if (query.isBlank()) return
        processCoordinates(query)
    }

    private fun processCoordinates(rawText: String) {
        if (rawText.isBlank()) return

        isLoadingState.value = true
        errorMessageState.value = null

        lifecycleScope.launch {
            try {
                val result = MapsParser.deriveCoordinates(rawText)
                currentResultState.value = result

                if (settingsState.value.keepHistory) {
                    historyState.value = storageManager.addHistoryItem(result)
                }

                if (settingsState.value.autoOpenOrganicMaps) {
                    OrganicMapsLauncher.launchOrganicMaps(
                        context = this@MainActivity,
                        result = result,
                        preferredScheme = settingsState.value.preferredScheme
                    )
                }
            } catch (e: Exception) {
                errorMessageState.value = e.message ?: "Could not find coordinates for this location."
            } finally {
                isLoadingState.value = false
            }
        }
    }
}
