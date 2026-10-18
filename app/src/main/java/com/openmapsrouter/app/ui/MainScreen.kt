package com.openmapsrouter.app.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.openmapsrouter.app.utils.LocationHelper
import kotlinx.coroutines.launch
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.zIndex
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
    onSelectHistoryItem: (HistoryItem) -> Unit,
    onGetCurrentLocation: (Double, Double) -> Unit
) {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    var showSettingsWindow by remember { mutableStateOf(false) }
    var showNotInstalledDialog by remember { mutableStateOf(false) }
    var showConfirmClearDialog by remember { mutableStateOf(false) }

    val coroutineScope = rememberCoroutineScope()
    var isLocating by remember { mutableStateOf(false) }

    fun fetchLocation() {
        if (!LocationHelper.isLocationEnabled(context)) {
            Toast.makeText(context, "Please turn on Location / GPS in settings", Toast.LENGTH_SHORT).show()
            try {
                context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            } catch (_: Exception) {}
            return
        }

        isLocating = true
        coroutineScope.launch {
            try {
                val loc = LocationHelper.getCurrentLocation(context)
                if (loc != null) {
                    onGetCurrentLocation(loc.latitude, loc.longitude)
                } else {
                    Toast.makeText(context, "Could not acquire GPS location. Try again outdoors.", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, e.message ?: "Failed to get location", Toast.LENGTH_SHORT).show()
            } finally {
                isLocating = false
            }
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        if (fineGranted || coarseGranted) {
            fetchLocation()
        } else {
            Toast.makeText(context, "Location permission is required to get current location", Toast.LENGTH_SHORT).show()
        }
    }

    fun onGetCurrentLocationClicked() {
        if (isLocating) return
        val fine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

        if (fine || coarse) {
            fetchLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    if (showSettingsWindow) {
        BackHandler {
            showSettingsWindow = false
        }
        SettingsScreen(
            settings = settings,
            onUpdateSettings = onUpdateSettings,
            onClose = { showSettingsWindow = false }
        )
    } else {
        val scrollState = rememberScrollState()
        val pagerState = rememberPagerState(initialPage = activeTab) { 2 }

        // Keep pagerState and activeTab in sync
        LaunchedEffect(activeTab) {
            if (pagerState.currentPage != activeTab) {
                pagerState.animateScrollToPage(activeTab)
            }
        }

        LaunchedEffect(pagerState.settledPage) {
            if (activeTab != pagerState.settledPage) {
                onTabChange(pagerState.settledPage)
            }
        }

        Scaffold(
            topBar = {
                TopBar(
                    pagerState = pagerState,
                    onOpenSettings = { showSettingsWindow = true }
                )
            },
            containerColor = DarkBackground,
            contentWindowInsets = WindowInsets(0, 0, 0, 0)
        ) { innerPadding ->
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(DarkBackground),
                beyondViewportPageCount = 1,
                userScrollEnabled = (pagerState.settledPage == 0)
            ) { page ->
                when (page) {
                    0 -> {
                        // Tab 0: Search Places
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBackground)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .verticalScroll(scrollState)
                                    .padding(bottom = 80.dp)
                            ) {
                                // Clipboard detected banner
                                if (clipboardUrl != null) {
                                    ClipboardBanner(
                                        url = clipboardUrl,
                                        onConvert = {
                                            onDismissClipboard()
                                            onDeriveCoordinates(clipboardUrl)
                                        },
                                        onDismiss = onDismissClipboard
                                    )
                                }

                                // Search Places Card
                                SearchPlacesCard(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = onSearchQueryChange,
                                    suggestions = suggestions,
                                    isSearching = isSearching,
                                    isLoading = isLoading,
                                    onSelectSuggestion = onSelectSuggestion,
                                    onPerformSearch = onPerformSearch
                                )

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

                            // Bottom-Right "Get Current Location" Button (visible on Tab 0)
                            Surface(
                                onClick = { onGetCurrentLocationClicked() },
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .navigationBarsPadding()
                                    .padding(end = 16.dp, bottom = 16.dp)
                                    .zIndex(4f),
                                shape = RoundedCornerShape(50),
                                color = PureWhite,
                                shadowElevation = 8.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (isLocating) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            strokeWidth = 2.dp,
                                            color = PureBlack
                                        )
                                    } else {
                                        Icon(
                                            imageVector = Icons.Filled.MyLocation,
                                            contentDescription = "Current Location",
                                            tint = PureBlack,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Get Current Location",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PureBlack
                                    )
                                }
                            }
                        }
                    }
                    1 -> {
                        // Tab 1: Gmap WV (kept permanently in composition via beyondViewportPageCount)
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(DarkBackground)
                        ) {
                            GmapWebView(
                                modifier = Modifier.fillMaxSize(),
                                isActive = (pagerState.currentPage == 1),
                                onCoordinatesDerived = { result ->
                                    onSelectHistoryItem(
                                        HistoryItem(
                                            id = java.util.UUID.randomUUID().toString(),
                                            latitude = result.latitude,
                                            longitude = result.longitude,
                                            name = result.name,
                                            sourceMethod = result.sourceMethod,
                                            originalUrl = result.originalUrl,
                                            timestamp = System.currentTimeMillis()
                                        )
                                    )
                                },
                                onOpenOrganicMaps = { result ->
                                    OrganicMapsLauncher.launchOrganicMaps(
                                        context = context,
                                        result = result,
                                        preferredScheme = settings.preferredScheme,
                                        onNotInstalled = { showNotInstalledDialog = true }
                                    )
                                },
                                onOpenSystemMap = { result ->
                                    OrganicMapsLauncher.openInSystemMap(context, result)
                                }
                            )

                            // Left-edge swipe strip to slide back to Tab 0 smoothly
                            Box(
                                modifier = Modifier
                                    .align(Alignment.CenterStart)
                                    .fillMaxHeight()
                                    .width(36.dp)
                                    .zIndex(10f)
                                    .pointerInput(Unit) {
                                        detectHorizontalDragGestures { _, dragAmount ->
                                            if (dragAmount > 12f) {
                                                coroutineScope.launch {
                                                    pagerState.animateScrollToPage(0)
                                                }
                                            }
                                        }
                                    }
                            )
                        }
                    }
                }
            }
        }
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
fun TopBar(
    pagerState: PagerState,
    onOpenSettings: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var dragAccumulator by remember { mutableFloatStateOf(0f) }

    Surface(
        color = DarkBackground,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Mode Selector Segmented Tabs with sliding indicator pill
            Surface(
                color = DarkSurface,
                modifier = Modifier
                    .weight(1f)
                    .height(44.dp)
                    .draggable(
                        state = rememberDraggableState { delta ->
                            dragAccumulator += delta
                        },
                        orientation = Orientation.Horizontal,
                        onDragStopped = { velocity ->
                            coroutineScope.launch {
                                if (velocity < -250f || dragAccumulator < -30f) {
                                    pagerState.animateScrollToPage(1)
                                } else if (velocity > 250f || dragAccumulator > 30f) {
                                    pagerState.animateScrollToPage(0)
                                }
                                dragAccumulator = 0f
                            }
                        }
                    ),
                shape = RoundedCornerShape(50),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                ) {
                    val tabWidth = maxWidth / 2
                    val scrollFraction = (pagerState.currentPage + pagerState.currentPageOffsetFraction).coerceIn(0f, 1f)

                    // 1. Sliding pill indicator
                    Box(
                        modifier = Modifier
                            .offset(x = tabWidth * scrollFraction)
                            .width(tabWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(PureWhite)
                    )

                    // 2. Tab labels and click targets
                    Row(
                        modifier = Modifier.fillMaxSize(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Tab 0: Search Places
                        val tab0Color = lerp(PureBlack, TextSecondaryDark, scrollFraction)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(0)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Search,
                                    contentDescription = null,
                                    tint = tab0Color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Search Places",
                                    fontSize = 13.sp,
                                    fontWeight = if (scrollFraction < 0.5f) FontWeight.Bold else FontWeight.Medium,
                                    color = tab0Color,
                                    maxLines = 1
                                )
                            }
                        }

                        // Tab 1: Gmap WV
                        val tab1Color = lerp(TextSecondaryDark, PureBlack, scrollFraction)
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .clickable {
                                    coroutineScope.launch {
                                        pagerState.animateScrollToPage(1)
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Language,
                                    contentDescription = null,
                                    tint = tab1Color,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Gmap WV",
                                    fontSize = 13.sp,
                                    fontWeight = if (scrollFraction >= 0.5f) FontWeight.Bold else FontWeight.Medium,
                                    color = tab1Color,
                                    maxLines = 1
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            // Standalone circular Settings button beside Gmap WV
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .size(42.dp)
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
    onClear: () -> Unit
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
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy Coords", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onCopyOmLink,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(15.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Copy om://", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Share, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }

                OutlinedButton(
                    onClick = onOpenSystem,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Place, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("System Map", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
                }

                OutlinedButton(
                    onClick = onOpenOsm,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(50),
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark)
                ) {
                    Icon(Icons.Outlined.Public, contentDescription = null, modifier = Modifier.size(14.dp), tint = TextPrimaryDark)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("OSM Web", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
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
fun SettingsScreen(
    settings: AppSettings,
    onUpdateSettings: (AppSettings) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Top Header
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
                Text(
                    text = "Settings",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextPrimaryDark
                )

                IconButton(
                    onClick = onClose,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(DarkSurfaceElevated)
                        .border(1.dp, DarkBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Close Settings",
                        tint = PureWhite,
                        modifier = Modifier.size(19.dp)
                    )
                }
            }
        }

        // Settings Content
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
                .padding(bottom = 32.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Automation
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "AUTOMATION",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Open Organic Maps", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Immediately launch Organic Maps after deriving", fontSize = 12.sp, color = TextSecondaryDark)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
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

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = DarkBorderSubtle)
                    Spacer(modifier = Modifier.height(14.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Save History", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = TextPrimaryDark)
                            Spacer(modifier = Modifier.height(2.dp))
                            Text("Store recent places locally", fontSize = 12.sp, color = TextSecondaryDark)
                        }
                        Spacer(modifier = Modifier.width(12.dp))
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
                }
            }

            // Section 2: Link Interception (Android 12+)
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "LINK INTERCEPTION (OPEN BY DEFAULT)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "To prompt or automatically open maps.app.goo.gl links on Android 12+, enable supported links in system settings. Google app map previews will also offer OpenMapsRouter.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Button(
                        onClick = { OrganicMapsLauncher.openDefaultAppsSettings(context) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 50.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = PureWhite,
                            contentColor = PureBlack
                        ),
                        shape = RoundedCornerShape(50),
                        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f, fill = false)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp),
                                    tint = PureBlack
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Configure default links (Android 12+)",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureBlack
                                )
                            }
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                                contentDescription = "Open Settings",
                                modifier = Modifier.size(18.dp),
                                tint = PureBlack
                            )
                        }
                    }
                }
            }

            // Section 3: Search Provider / API Key
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "SEARCH PROVIDER (OPTIONAL GOOGLE API KEY)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Free live place search works out of the box with zero setup. If you have a Google Cloud Places API key, you can enter it here to search directly from Google's proprietary Places database.",
                        fontSize = 12.sp,
                        color = TextSecondaryDark,
                        lineHeight = 17.sp
                    )
                    Spacer(modifier = Modifier.height(14.dp))

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
                }
            }

            // Section 4: URL Scheme
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "URL SCHEME",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

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
                }
            }

            // Section 5: Store Links
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = DarkSurface),
                border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "DOWNLOAD ORGANIC MAPS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextSecondaryDark,
                        letterSpacing = 0.8.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.PLAY_STORE_URL) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("Google Play", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                        OutlinedButton(
                            onClick = { OrganicMapsLauncher.openUrl(context, OrganicMapsLauncher.FDROID_URL) },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(50),
                            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimaryDark),
                            contentPadding = PaddingValues(vertical = 12.dp)
                        ) {
                            Text("F-Droid", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
