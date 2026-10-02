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
            .background(DarkBackground)
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

            // Mode Selector Segmented Tabs
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
            containerColor = DarkSurface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Organic Maps Not Found",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimaryDark
                )
            },
            text = {
                Text(
                    "Organic Maps is not installed on this device. Would you like to download it from the store?",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showNotInstalledDialog = false
                        OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.PLAY_STORE_URL)
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text("Download from Store", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showNotInstalledDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark, fontSize = 14.sp)
                }
            }
        )
    }

    // Confirm Clear Dialog
    if (showConfirmClearDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmClearDialog = false },
            containerColor = DarkSurface,
            shape = RoundedCornerShape(24.dp),
            title = {
                Text(
                    "Clear History",
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp,
                    color = TextPrimaryDark
                )
            },
            text = {
                Text(
                    "Are you sure you want to clear all converted location history?",
                    color = TextSecondaryDark,
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmClearDialog = false
                        onClearAllHistory()
                    },
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = ErrorDarkText,
                        contentColor = PureWhite
                    ),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp)
                ) {
                    Text("Clear All", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmClearDialog = false }) {
                    Text("Cancel", color = TextSecondaryDark, fontSize = 14.sp)
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
        color = DarkSurface,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(50),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(4.dp)
        ) {
            // Tab 0: Search Places
            Surface(
                color = if (activeTab == 0) PureWhite else Color.Transparent,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .clickable { onTabChange(0) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Search,
                        contentDescription = null,
                        tint = if (activeTab == 0) PureBlack else TextSecondaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Search Places",
                        fontSize = 14.sp,
                        fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 0) PureBlack else TextSecondaryDark
                    )
                }
            }

            // Tab 1: Convert Link
            Surface(
                color = if (activeTab == 1) PureWhite else Color.Transparent,
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .clickable { onTabChange(1) }
            ) {
                Row(
                    modifier = Modifier.padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = if (activeTab == 1) PureBlack else TextSecondaryDark,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Convert Link",
                        fontSize = 14.sp,
                        fontWeight = if (activeTab == 1) FontWeight.Bold else FontWeight.Medium,
                        color = if (activeTab == 1) PureBlack else TextSecondaryDark
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Search Location",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryDark
                )
                if (isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = PureWhite
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Pill-shaped search input field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = {
                    Text(
                        "Search place, store, or address...",
                        fontSize = 14.sp,
                        color = TextSecondaryDark
                    )
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = "Search",
                        tint = TextSecondaryDark,
                        modifier = Modifier.size(20.dp)
                    )
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchQueryChange("") }, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Clear", tint = TextSecondaryDark, modifier = Modifier.size(18.dp))
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
                shape = RoundedCornerShape(50),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureWhite,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurfaceElevated,
                    unfocusedContainerColor = DarkSurfaceElevated,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    cursorColor = PureWhite
                )
            )

            // Live Suggestions Dropdown
            if (suggestions.isNotEmpty()) {
                Spacer(modifier = Modifier.height(14.dp))
                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        suggestions.forEachIndexed { index, suggestion ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onSelectSuggestion(suggestion) }
                                    .padding(horizontal = 16.dp, vertical = 14.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGreenContainer)
                                        .border(1.dp, EmeraldGreenBorder, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Outlined.LocationOn,
                                        contentDescription = null,
                                        tint = EmeraldGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Spacer(modifier = Modifier.width(14.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = suggestion.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = TextPrimaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = suggestion.address,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Normal,
                                        color = TextSecondaryDark,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }

                            if (index < suggestions.lastIndex) {
                                HorizontalDivider(color = DarkBorderSubtle, modifier = Modifier.padding(horizontal = 16.dp))
                            }
                        }
                    }
                }
            }

            // Quick Samples when query is empty
            if (searchQuery.isEmpty() && suggestions.isEmpty()) {
                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorderSubtle)
                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "POPULAR LOCATIONS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondaryDark,
                    letterSpacing = 0.8.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
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
                            color = DarkSurfaceElevated,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    onSearchQueryChange(sample.substring(2).trim())
                                }
                        ) {
                            Text(
                                text = sample,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = TextPrimaryDark,
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
        color = DarkBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Map,
                        contentDescription = "Map Icon",
                        tint = PureWhite,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "OpenMapsRouter",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = TextPrimaryDark
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            color = EmeraldGreenContainer,
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreenBorder)
                        ) {
                            Text(
                                text = "OM",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = EmeraldGreen,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                            )
                        }
                    }
                    Text(
                        text = "Google Maps → Organic Maps",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        fontWeight = FontWeight.Normal
                    )
                }
            }

            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(DarkSurfaceElevated)
                    .border(1.dp, DarkBorder, CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = PureWhite,
                    modifier = Modifier.size(19.dp)
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
        colors = CardDefaults.cardColors(containerColor = DarkSurfaceElevated),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreenBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreenContainer)
                    .border(1.dp, EmeraldGreenBorder, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.ContentPaste,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(18.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Maps link found",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureWhite
                )
                Text(
                    text = url,
                    fontSize = 11.sp,
                    color = EmeraldGreen,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onConvert,
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureWhite,
                    contentColor = PureBlack
                ),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                shape = RoundedCornerShape(50),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Convert", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Dismiss", tint = TextSecondaryDark, modifier = Modifier.size(18.dp))
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
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Google Maps Link",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimaryDark
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onPaste() }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(Icons.Outlined.ContentPaste, contentDescription = null, tint = PureWhite, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Paste", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PureWhite)
                        }
                    }
                    if (inputUrl.isNotEmpty()) {
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = onClear, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Clear", tint = TextSecondaryDark, modifier = Modifier.size(18.dp))
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Input field
            OutlinedTextField(
                value = inputUrl,
                onValueChange = onInputUrlChange,
                placeholder = {
                    Text(
                        "Paste maps.app.goo.gl link, Google Maps URL, or lat,lon...",
                        fontSize = 13.sp,
                        color = TextSecondaryDark,
                        lineHeight = 18.sp
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 84.dp),
                shape = RoundedCornerShape(18.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PureWhite,
                    unfocusedBorderColor = DarkBorder,
                    focusedContainerColor = DarkSurfaceElevated,
                    unfocusedContainerColor = DarkSurfaceElevated,
                    focusedTextColor = TextPrimaryDark,
                    unfocusedTextColor = TextPrimaryDark,
                    cursorColor = PureWhite
                ),
                maxLines = 3
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Primary CTA Button: Pill-shaped Pure White with bold Black text
            Button(
                onClick = onSubmit,
                enabled = inputUrl.isNotBlank() && !isLoading,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureWhite,
                    contentColor = PureBlack,
                    disabledContainerColor = DarkSurfaceElevated,
                    disabledContentColor = TextMutedDark
                )
            ) {
                if (isLoading) {
                    CircularProgressIndicator(color = PureBlack, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Resolving & Deriving...", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = PureBlack)
                } else {
                    Icon(Icons.Filled.Explore, contentDescription = null, modifier = Modifier.size(20.dp), tint = if (inputUrl.isNotBlank()) PureBlack else TextMutedDark)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Derive Coordinates", fontSize = 15.sp, fontWeight = FontWeight.Bold, color = if (inputUrl.isNotBlank()) PureBlack else TextMutedDark)
                }
            }

            Spacer(modifier = Modifier.height(18.dp))
            HorizontalDivider(color = DarkBorderSubtle)
            Spacer(modifier = Modifier.height(12.dp))

            // Quick Samples
            Text(
                text = "QUICK SAMPLES TO TRY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondaryDark,
                letterSpacing = 0.8.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SAMPLE_LINKS.forEach { sample ->
                    Surface(
                        color = DarkSurfaceElevated,
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .clickable { onSelectSample(sample.url) }
                    ) {
                        Text(
                            text = sample.label,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = TextPrimaryDark,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
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
        colors = CardDefaults.cardColors(containerColor = ErrorDarkBg),
        shape = RoundedCornerShape(18.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, ErrorDarkBorder)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.ErrorOutline,
                contentDescription = null,
                tint = ErrorDarkText,
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                fontSize = 13.sp,
                color = ErrorDarkText,
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
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(24.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            // Status Header
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
                            .background(EmeraldGreenContainer)
                            .border(1.dp, EmeraldGreenBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Location Derived",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = TextPrimaryDark
                    )
                }

                Surface(
                    color = EmeraldGreenContainer,
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, EmeraldGreenBorder)
                ) {
                    Text(
                        text = result.sourceMethod.label,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = EmeraldGreen,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Place Name (if present)
            if (!result.name.isNullOrBlank()) {
                Text(
                    text = result.name,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark,
                    lineHeight = 24.sp
                )
                Spacer(modifier = Modifier.height(12.dp))
            }

            // High-Contrast Coordinates Box
            Surface(
                color = DarkSurfaceElevated,
                shape = RoundedCornerShape(18.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("LATITUDE", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondaryDark, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = String.format(Locale.US, "%.6f°", result.latitude),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }

                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(36.dp)
                            .background(DarkBorderSubtle)
                    )

                    Column {
                        Text("LONGITUDE", fontSize = 11.sp, fontWeight = FontWeight.Medium, color = TextSecondaryDark, letterSpacing = 0.5.sp)
                        Spacer(modifier = Modifier.height(3.dp))
                        Text(
                            text = String.format(Locale.US, "%.6f°", result.longitude),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimaryDark,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Primary CTA: Solid Pure White Pill Button with Bold Black Text
            Button(
                onClick = onOpenOrganicMaps,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PureWhite,
                    contentColor = PureBlack
                )
            ) {
                Icon(Icons.Filled.Navigation, contentDescription = null, modifier = Modifier.size(19.dp), tint = PureBlack)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Open in Organic Maps",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = PureBlack
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Secondary Action Buttons Row 1: Copy Coordinates & Copy OM Link
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCopyCoords,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Coords", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onCopyOmLink,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy om://", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Secondary Action Buttons Row 2: Share, System Map, OSM Web
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onOpenSystem,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("System", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                }

                OutlinedButton(
                    onClick = onOpenOsm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("OSM", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
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
        colors = CardDefaults.cardColors(containerColor = DarkSurface),
        shape = RoundedCornerShape(22.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.History, contentDescription = null, tint = TextPrimaryDark, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Recent Locations", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                }
                TextButton(onClick = onClearAll, contentPadding = PaddingValues(0.dp)) {
                    Text("Clear All", fontSize = 12.sp, color = TextSecondaryDark, fontWeight = FontWeight.Medium)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            items.take(8).forEachIndexed { index, item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectItem(item) }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
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
                                .background(DarkSurfaceElevated)
                                .border(1.dp, DarkBorder, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Place,
                                contentDescription = null,
                                tint = EmeraldGreen,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name ?: String.format(Locale.US, "%.5f, %.5f", item.latitude, item.longitude),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = TextPrimaryDark,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = String.format(Locale.US, "%.5f, %.5f", item.latitude, item.longitude),
                                fontSize = 11.sp,
                                color = TextSecondaryDark,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { onOpenItem(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Filled.Navigation, contentDescription = "Open", tint = PureWhite, modifier = Modifier.size(17.dp))
                        }
                        IconButton(onClick = { onCopyItem(item) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.ContentCopy, contentDescription = "Copy", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onDeleteItem(item.id) }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = TextMutedDark, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                if (index < items.take(8).lastIndex) {
                    HorizontalDivider(color = DarkBorderSubtle)
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
            shape = RoundedCornerShape(24.dp),
            color = DarkSurface,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Settings", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(DarkSurfaceElevated)
                            .border(1.dp, DarkBorder, CircleShape)
                    ) {
                        Icon(Icons.Filled.Close, contentDescription = "Close", tint = TextSecondaryDark, modifier = Modifier.size(16.dp))
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Automation
                Text("AUTOMATION", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Open Organic Maps", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Immediately launch Organic Maps after deriving", fontSize = 12.sp, color = TextSecondaryDark)
                    }
                    Switch(
                        checked = settings.autoOpenOrganicMaps,
                        onCheckedChange = { onUpdateSettings(settings.copy(autoOpenOrganicMaps = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = EmeraldGreen,
                            uncheckedThumbColor = TextSecondaryDark,
                            uncheckedTrackColor = DarkSurfaceElevated,
                            uncheckedBorderColor = DarkBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save History", fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text("Store recent places locally", fontSize = 12.sp, color = TextSecondaryDark)
                    }
                    Switch(
                        checked = settings.keepHistory,
                        onCheckedChange = { onUpdateSettings(settings.copy(keepHistory = it)) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = PureWhite,
                            checkedTrackColor = EmeraldGreen,
                            uncheckedThumbColor = TextSecondaryDark,
                            uncheckedTrackColor = DarkSurfaceElevated,
                            uncheckedBorderColor = DarkBorder
                        )
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // Link Interception & System Defaults
                Text("LINK INTERCEPTION (OPEN BY DEFAULT)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "To prompt or automatically open maps.app.goo.gl links on Android 12+, enable supported links in system settings. Google app map previews will also offer OpenMapsRouter.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = { OrganicMapsLauncher.openDefaultAppsSettings(context) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = PureWhite,
                        contentColor = PureBlack
                    ),
                    shape = RoundedCornerShape(50)
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = null, modifier = Modifier.size(16.dp), tint = PureBlack)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Configure Default Links (Android 12+)", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = PureBlack)
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // Search Provider / API Key
                Text("SEARCH PROVIDER (OPTIONAL GOOGLE API KEY)", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    "Free live place search works out of the box with zero setup. If you have a Google Cloud Places API key, you can enter it here to search directly from Google's proprietary Places database.",
                    fontSize = 12.sp,
                    color = TextSecondaryDark,
                    lineHeight = 16.sp
                )
                Spacer(modifier = Modifier.height(10.dp))

                var apiKeyInput by remember { mutableStateOf(settings.googleApiKey) }
                OutlinedTextField(
                    value = apiKeyInput,
                    onValueChange = {
                        apiKeyInput = it
                        onUpdateSettings(settings.copy(googleApiKey = it.trim()))
                    },
                    placeholder = { Text("AIzaSy... (leave blank for free search)", fontSize = 12.sp, color = TextMutedDark) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(50),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PureWhite,
                        unfocusedBorderColor = DarkBorder,
                        focusedContainerColor = DarkSurfaceElevated,
                        unfocusedContainerColor = DarkSurfaceElevated,
                        focusedTextColor = TextPrimaryDark,
                        unfocusedTextColor = TextPrimaryDark,
                        cursorColor = PureWhite
                    )
                )

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // Scheme Choice
                Text("URL SCHEME", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (settings.preferredScheme == "om") 1.5.dp else 1.dp,
                        if (settings.preferredScheme == "om") EmeraldGreen else DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onUpdateSettings(settings.copy(preferredScheme = "om")) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("om:// scheme (Recommended)", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Directly targets Organic Maps application", fontSize = 12.sp, color = TextSecondaryDark)
                        }
                        if (settings.preferredScheme == "om") {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(16.dp),
                    border = androidx.compose.foundation.BorderStroke(
                        if (settings.preferredScheme == "geo") 1.5.dp else 1.dp,
                        if (settings.preferredScheme == "geo") EmeraldGreen else DarkBorder
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { onUpdateSettings(settings.copy(preferredScheme = "geo")) }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("geo: scheme", fontSize = 14.sp, fontWeight = FontWeight.Bold, color = TextPrimaryDark)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Standard Geo URI (system map handler)", fontSize = 12.sp, color = TextSecondaryDark)
                        }
                        if (settings.preferredScheme == "geo") {
                            Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = EmeraldGreen, modifier = Modifier.size(20.dp))
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))
                HorizontalDivider(color = DarkBorderSubtle)
                Spacer(modifier = Modifier.height(16.dp))

                // Store Links
                Text("DOWNLOAD ORGANIC MAPS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondaryDark, letterSpacing = 0.8.sp)
                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = { OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.PLAY_STORE_URL) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                    ) {
                        Text("Google Play", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(
                        onClick = { OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.FDROID_URL) },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                    ) {
                        Text("F-Droid", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}
