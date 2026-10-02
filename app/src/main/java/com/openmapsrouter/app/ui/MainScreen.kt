package com.openmapsrouter.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.openmapsrouter.app.data.AppSettings
import com.openmapsrouter.app.data.CoordinateResult
import com.openmapsrouter.app.data.HistoryItem
import com.openmapsrouter.app.data.SourceMethod
import com.openmapsrouter.app.search.PlaceSuggestion
import com.openmapsrouter.app.ui.theme.*
import com.openmapsrouter.app.utils.OrganicMapsLauncher
import java.util.Locale

private data class SampleLink(val label: String, val url: String)

private val SAMPLE_LINKS = listOf(
    SampleLink("🗼 Eiffel Tower", "https://www.google.com/maps/place/Eiffel+Tower/@48.8583701,2.2944813,17z"),
    SampleLink(
        "🌉 Golden Gate",
        "https://www.google.com/maps/place/Golden+Gate+Bridge/@37.8199286,-122.4804438,17z/data=!3m1!4b1!4m6!3m5!1s0x808586de63b85841:0xbf00e706b3c2a64d!8m2!3d37.8199286!4d-122.478667"
    ),
    SampleLink("🗽 Statue of Liberty", "https://maps.google.com/?q=Statue+of+Liberty@40.689249,-74.044500"),
    SampleLink("⛩️ Fushimi Inari", "https://www.google.com/maps/@34.967140,135.772671,16z"),
    SampleLink("📍 Direct Coords", "51.500729, -0.124625")
)

@Composable
fun MainScreen(
    activeTab: Int,
    onTabChange: (Int) -> Unit,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    suggestions: List<PlaceSuggestion>,
    isSearching: Boolean,
    onSelectSuggestion: (PlaceSuggestion) -> Unit,
    onPerformSearch: (String) -> Unit,
    inputUrl: String,
    onInputUrlChange: (String) -> Unit,
    currentResult: CoordinateResult?,
    isLoading: Boolean,
    errorMessage: String?,
    clipboardUrl: String?,
    onDismissClipboard: () -> Unit,
    history: List<HistoryItem>,
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onDeriveCoordinates: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearAllHistory: () -> Unit,
    onSelectHistoryItem: (HistoryItem) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showSettingsDialog by remember { mutableStateOf(false) }
    var showNotInstalledDialog by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Background)
    ) {
        // Top Header with status bar padding
        Header(
            onOpenSettings = { showSettingsDialog = true }
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(bottom = 32.dp)
        ) {
            // Clipboard detected banner
            if (clipboardUrl != null) {
                ClipboardBanner(
                    url = clipboardUrl,
                    onConvert = {
                        onTabChange(1)
                        onInputUrlChange(clipboardUrl)
                        onDismissClipboard()
                        onDeriveCoordinates(clipboardUrl)
                    },
                    onDismiss = onDismissClipboard
                )
            }

            // Mode Selector Tabs (Search Places vs Convert Link)
            ModeTabBar(
                activeTab = activeTab,
                onTabChange = onTabChange
            )

            // Active Tab Content
            if (activeTab == 0) {
                SearchPlacesCard(
                    searchQuery = searchQuery,
                    onSearchQueryChange = onSearchQueryChange,
                    suggestions = suggestions,
                    isSearching = isSearching,
                    isLoading = isLoading,
                    onSelectSuggestion = onSelectSuggestion,
                    onPerformSearch = onPerformSearch
                )
            } else {
                InputCard(
                    inputUrl = inputUrl,
                    onInputUrlChange = onInputUrlChange,
                    isLoading = isLoading,
                    onSubmit = { onDeriveCoordinates(inputUrl) },
                    onPaste = {
                        val clip = clipboardManager.getText()?.text
                        if (!clip.isNullOrBlank()) {
                            onInputUrlChange(clip.trim())
                        }
                    },
                    onClear = {
                        onInputUrlChange("")
                    },
                    onSelectSample = { sample ->
                        onInputUrlChange(sample)
                        onDeriveCoordinates(sample)
                    }
                )
            }

            // Error banner
            if (errorMessage != null) {
                ErrorCard(message = errorMessage)
            }

            // Result Card
            if (currentResult != null) {
                ResultCard(
                    result = currentResult,
                    preferredScheme = settings.preferredScheme,
                    onOpenOrganicMaps = {
                        OrganicMapsLauncher.launchOrganicMaps(
                            context = context,
                            result = currentResult,
                            preferredScheme = settings.preferredScheme,
                            onNotInstalled = { showNotInstalledDialog = true }
                        )
                    },
                    onCopyCoords = { OrganicMapsLauncher.copyCoordinatesToClipboard(context, currentResult) },
                    onCopyOmLink = { OrganicMapsLauncher.copyOmLinkToClipboard(context, currentResult) },
                    onOpenSystem = { OrganicMapsLauncher.openInSystemMap(context, currentResult) },
                    onOpenOsm = { OrganicMapsLauncher.openInOsmWeb(context, currentResult) },
                    onShare = { OrganicMapsLauncher.shareCoordinates(context, currentResult) }
                )
            }

            // History Section
            if (history.isNotEmpty()) {
                HistorySection(
                    items = history,
                    preferredScheme = settings.preferredScheme,
                    onSelectItem = onSelectHistoryItem,
                    onOpenItem = { item ->
                        OrganicMapsLauncher.launchOrganicMaps(
                            context = context,
                            result = item.toCoordinateResult(),
                            preferredScheme = settings.preferredScheme,
                            onNotInstalled = { showNotInstalledDialog = true }
                        )
                    },
                    onCopyItem = { item ->
                        OrganicMapsLauncher.copyCoordinatesToClipboard(context, item.toCoordinateResult())
                    },
                    onDeleteItem = onDeleteHistoryItem,
                    onClearAll = { showConfirmClearDialog = true }
                )
            }
        }
    }

    // Settings Dialog
    if (showSettingsDialog) {
        SettingsDialog(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onDismiss = { showSettingsDialog = false }
        )
    }

    // Not Installed Dialog
    if (showNotInstalledDialog) {
        AlertDialog(
            onDismissRequest = { showNotInstalledDialog = false },
            title = { Text("Organic Maps Not Found", fontWeight = FontWeight.Bold) },
            text = {
                Text("Organic Maps is not installed on this device. Would you like to download it from the store?")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotInstalledDialog = false
                        OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.PLAY_STORE_URL)
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = OrganicGreen)
                ) {
                    Text("Download from Play Store")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotInstalledDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }

    // Confirm Clear Dialog
    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            title = { Text("Clear History", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to clear all converted location history?") },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClearDialog = false
                        onClearAllHistory()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53E3E))
                ) {
                    Text("Clear All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("Cancel", color = TextSecondary)
                }
            }
        )
    }
}

@Composable
fun ModeTabBar(
    activeTab: Int,
    onTabChange: (Int) -> Unit
) {
    Surface(
        color = SurfaceWhite,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Tab 0: Search Places
            Surface(
                color = if (activeTab == 0) OrganicGreenLight else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                border = if (activeTab == 0) androidx.compose.foundation.BorderStroke(1.dp, OrganicGreenBorder) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabChange(0) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (activeTab == 0) OrganicGreen else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Search Places",
                        fontSize = 13.sp,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 0) OrganicGreen else TextSecondary
                    )
                }
            }

            // Tab 1: Convert Link
            Surface(
                color = if (activeTab == 1) OrganicGreenLight else Color.Transparent,
                shape = RoundedCornerShape(8.dp),
                border = if (activeTab == 1) androidx.compose.foundation.BorderStroke(1.dp, OrganicGreenBorder) else null,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onTabChange(1) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = if (activeTab == 1) OrganicGreen else TextSecondary,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Convert Link",
                        fontSize = 13.sp,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 1) OrganicGreen else TextSecondary
                    )
                }
            }
        }
    }
}

@Composable
fun SearchPlacesCard(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    suggestions: List<PlaceSuggestion>,
    isSearching: Boolean,
    isLoading: Boolean,
    onSelectSuggestion: (PlaceSuggestion) -> Unit,
    onPerformSearch: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search Google Maps Places",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = OrganicGreen
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search place, business, address (e.g. Starbucks, Taj Mahal)...",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                },
                leadingIcon = {
                    Icon(Icons.Filled.Search, contentDescription = "Search", tint = OrganicGreen)
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Search
                ),
                keyboardActions = KeyboardActions(
                    onSearch = { onPerformSearch(searchQuery) }
                ),
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrganicGreen,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background
                )
            )

            // Live Suggestions Dropdown List
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                Surface(
                    color = Background,
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        suggestions.forEachIndexed { index, suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSuggestion(suggestion) }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(OrganicGreenLight),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = OrganicGreen,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = suggestion.name,
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = suggestion.address,
                                        fontSize = 12.sp,
                                        color = TextSecondary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (index < suggestions.lastIndex) {
                                HorizontalDivider(color = BorderSubtle, modifier = Modifier.padding(horizontal = 12.dp))
                            }
                        }
                    }
                }
            }

            // Quick Samples when query is empty
            if (searchQuery.isEmpty() && suggestions.isEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(10.dp))

                Text(
                    text = "Popular Places to Search:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val sampleQueries = listOf(
                        "🗼 Eiffel Tower Paris",
                        "🌉 Golden Gate Bridge",
                        "🗽 Statue of Liberty",
                        "🕌 Taj Mahal Agra",
                        "☕ Starbucks Times Square",
                        "🚉 Guwahati Railway Station"
                    )
                    sampleQueries.forEach { sample ->
                        Surface(
                            color = Color(0xFFF1F5F9),
                            shape = RoundedCornerShape(20.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                            modifier = Modifier.clickable {
                                onSearchQueryChange(sample.substring(2).trim())
                            }
                        ) {
                            Text(
                                text = sample,
                                fontSize = 11.sp,
                                color = TextPrimary,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun Header(onOpenSettings: () -> Unit) {
    Surface(
        color = SurfaceWhite,
        modifier = Modifier.fillMaxWidth(),
        shadowElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(OrganicGreen),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = "Map Icon",
                        tint = SurfaceWhite,
                        modifier = Modifier.size(24.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "OpenMapsRouter",
                            fontSize = 19.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            color = OrganicGreenLight,
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, OrganicGreenBorder)
                        ) {
                            Text(
                                text = "OM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = OrganicGreen,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Google Maps → Organic Maps",
                        fontSize = 12.sp,
                        color = TextSecondary,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(Background)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = TextSecondary,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun ClipboardBanner(
    url: String,
    onConvert: () -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = OrganicGreenLight),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, OrganicGreenBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(OrganicGreenBorder),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    tint = OrganicGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Maps link found",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = OrganicGreenDark
                )
                Text(
                    text = url,
                    fontSize = 11.sp,
                    color = OrganicGreen,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConvert,
                colors = ButtonDefaults.buttonColors(containerColor = OrganicGreen),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text("Convert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextSecondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

@Composable
fun InputCard(
    inputUrl: String,
    onInputUrlChange: (String) -> Unit,
    isLoading: Boolean,
    onSubmit: () -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onSelectSample: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Google Maps Link",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimary
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = OrganicGreenLight,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.clickable { onPaste() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = OrganicGreen, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = OrganicGreen)
                        }
                    }
                    if (inputUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        IconButton(onClick = onClear, modifier = Modifier.size(26.dp)) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Clear", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Input field
            OutlinedTextField(
                value = inputUrl,
                onValueChange = onInputUrlChange,
                placeholder = {
                    Text(
                        "Paste maps.app.goo.gl link, Google Maps URL, or lat,lon...",
                        fontSize = 13.sp,
                        color = TextMuted
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 80.dp),
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = OrganicGreen,
                    unfocusedBorderColor = BorderLight,
                    focusedContainerColor = Background,
                    unfocusedContainerColor = Background
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(14.dp))

            // Derive Button
            Button(
                onClick = onSubmit,
                enabled = inputUrl.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = OrganicGreen,
                    disabledContainerColor = BorderLight
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = SurfaceWhite, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Resolving & Deriving...", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                } else {
                    Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Derive Coordinates", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            HorizontalDivider(color = BorderSubtle)
            Spacer(modifier = Modifier.height(10.dp))

            // Quick Samples
            Text(
                text = "Quick Samples to Try:",
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SAMPLE_LINKS.forEach { sample ->
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(20.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                        modifier = Modifier.clickable { onSelectSample(sample.url) }
                    ) {
                        Text(
                            text = sample.label,
                            fontSize = 11.sp,
                            color = TextPrimary,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ErrorCard(message: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(containerColor = ErrorBg),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorBorder)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = ErrorText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = ErrorText,
                lineHeight = 18.sp
            )
        }
    }
}

@Composable
fun ResultCard(
    result: CoordinateResult,
    preferredScheme: String,
    onOpenOrganicMaps: () -> Unit,
    onCopyCoords: () -> Unit,
    onCopyOmLink: () -> Unit,
    onOpenSystem: () -> Unit,
    onOpenOsm: () -> Unit,
    onShare: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, OrganicGreenBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(OrganicGreenLight),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = OrganicGreen,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Location Derived",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                }

                Surface(
                    color = OrganicGreenLight,
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, OrganicGreenBorder)
                ) {
                    Text(
                        text = result.sourceMethod.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = OrganicGreenDark,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Place Name (if present)
            if (!result.name.isNullOrBlank()) {
                Text(
                    text = result.name,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary,
                    lineHeight = 22.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Coordinates Box
            Surface(
                color = Background,
                shape = RoundedCornerShape(12.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("LATITUDE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 0.5.sp)
                        Text(
                            text = String.format(Locale.US, "%.6f°", result.latitude),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(32.dp)
                            .background(BorderLight)
                    )

                    Column {
                        Text("LONGITUDE", fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 0.5.sp)
                        Text(
                            text = String.format(Locale.US, "%.6f°", result.longitude),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimary,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Big CTA: Open in Organic Maps
            Button(
                onClick = onOpenOrganicMaps,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = OrganicGreen)
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open in Organic Maps",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Action Buttons Row 1: Copy Coordinates & Copy OM Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyCoords,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy Coords", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onCopyOmLink,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy om://", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Action Buttons Row 2: Share, System Map, OSM Web
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenSystem,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("System Map", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onOpenOsm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("OSM Web", fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun HistorySection(
    items: List<HistoryItem>,
    preferredScheme: String,
    onSelectItem: (HistoryItem) -> Unit,
    onOpenItem: (HistoryItem) -> Unit,
    onCopyItem: (HistoryItem) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, BorderLight),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = TextPrimary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Recent Locations", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                }
                TextButton(onClick = onClearAll, contentPadding = PaddingValues(0.dp)) {
                    Text("Clear All", fontSize = 12.sp, color = TextSecondary)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            items.take(8).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSelectItem(item) }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(OrganicGreenLight),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = OrganicGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name ?: String.format(Locale.US, "%.5f, %.5f", item.latitude, item.longitude),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = String.format(Locale.US, "%.5f, %.5f", item.latitude, item.longitude),
                                fontSize = 11.sp,
                                color = TextSecondary,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onOpenItem(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Navigation, contentDescription = "Open", tint = OrganicGreen, modifier = Modifier.size(18.dp))
                        }
                        IconButton(onClick = { onCopyItem(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = TextSecondary, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDeleteItem(item.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = TextMuted, modifier = Modifier.size(18.dp))
                        }
                    }
                }

                if (index < items.take(8).lastIndex) {
                    HorizontalDivider(color = BorderSubtle)
                }
            }
        }
    }
}

@Composable
fun SettingsDialog(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = SurfaceWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings & Configuration", fontSize = 18.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondary)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Automation
                Text("BEHAVIOR", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Open Organic Maps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Immediately launch Organic Maps after deriving", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = settings.autoOpenOrganicMaps,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoOpenOrganicMaps = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SurfaceWhite, checkedTrackColor = OrganicGreen)
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save History", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary)
                        Text("Store recent places locally", fontSize = 11.sp, color = TextSecondary)
                    }
                    Switch(
                        checked = settings.keepHistory,
                        onCheckedChange = { onUpdateSettings(settings.copy(keepHistory = it)) },
                        colors = SwitchDefaults.colors(checkedThumbColor = SurfaceWhite, checkedTrackColor = OrganicGreen)
                    )
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                // Link Interception & System Defaults
                Text("LINK INTERCEPTION (OPEN BY DEFAULT)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "To prompt or automatically open maps.app.goo.gl links on Android 12+, enable supported links in system settings. Google app map previews will also offer OpenMapsRouter.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = { OrganicMapsLauncher.openDefaultAppsSettings(context) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Configure Default Links (Android 12+)", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                // Search Provider / API Key
                Text("SEARCH PROVIDER (OPTIONAL GOOGLE API KEY)", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Free live place search works out of the box with zero setup. If you have a Google Cloud Places API key, you can enter it here to search directly from Google's proprietary Places database.",
                    fontSize = 11.sp,
                    color = TextSecondary,
                    lineHeight = 15.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                var apiKeyInput by remember { mutableStateOf(settings.googleApiKey) }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        onUpdateSettings(settings.copy(googleApiKey = it.trim()))
                    },
                    placeholder = { Text("AIzaSy... (leave blank for free search)", fontSize = 12.sp, color = TextMuted) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = OrganicGreen,
                        unfocusedBorderColor = BorderLight
                    )
                )

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                // Scheme Choice
                Text("URL SCHEME", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = if (settings.preferredScheme == "om") OrganicGreenLight else Background,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (settings.preferredScheme == "om") OrganicGreenBorder else BorderLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateSettings(settings.copy(preferredScheme = "om")) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("om:// scheme (Recommended)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Directly targets Organic Maps application", fontSize = 11.sp, color = TextSecondary)
                        }
                        if (settings.preferredScheme == "om") {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OrganicGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Surface(
                    color = if (settings.preferredScheme == "geo") OrganicGreenLight else Background,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (settings.preferredScheme == "geo") OrganicGreenBorder else BorderLight
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onUpdateSettings(settings.copy(preferredScheme = "geo")) }
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("geo: scheme", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
                            Text("Standard Geo URI (system map handler)", fontSize = 11.sp, color = TextSecondary)
                        }
                        if (settings.preferredScheme == "geo") {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = OrganicGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                HorizontalDivider(color = BorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                // Store Links
                Text("DOWNLOAD ORGANIC MAPS", fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, color = TextSecondary, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.PLAY_STORE_URL) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Google Play", fontSize = 11.sp)
                    }
                    OutlinedButton(
                        onClick = { OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.FDROID_URL) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("F-Droid", fontSize = 11.sp)
                    }
                }
            }
        }
    }
}
