package com.openmapsrouter.app.ui

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.webkit.GeolocationPermissions
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GpsFixed
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.openmapsrouter.app.data.CoordinateResult
import com.openmapsrouter.app.data.SourceMethod
import com.openmapsrouter.app.parser.MapsParser
import com.openmapsrouter.app.ui.theme.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun GmapWebView(
    modifier: Modifier = Modifier,
    onCoordinatesDerived: (CoordinateResult) -> Unit,
    onOpenOrganicMaps: (CoordinateResult) -> Unit,
    onOpenSystemMap: (CoordinateResult) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val mainHandler = remember { Handler(Looper.getMainLooper()) }

    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var canGoBack by remember { mutableStateOf(false) }
    var pageLoadProgress by remember { mutableIntStateOf(0) }
    var isPageLoading by remember { mutableStateOf(true) }

    var derivedResult by remember { mutableStateOf<CoordinateResult?>(null) }
    var isDeriving by remember { mutableStateOf(false) }

    fun processRawUrlOrText(raw: String) {
        if (raw.isBlank()) return
        isDeriving = true
        coroutineScope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    MapsParser.deriveCoordinates(raw)
                }
                derivedResult = result
                onCoordinatesDerived(result)
            } catch (e: Exception) {
                // If it was an unresolvable URL, try extracting simple @lat,lon if present
                val atPattern = Regex("""@(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)""")
                val match = atPattern.find(raw)
                if (match != null) {
                    val lat = match.groupValues[1].toDoubleOrNull()
                    val lon = match.groupValues[2].toDoubleOrNull()
                    if (lat != null && lon != null && MapsParser.isValidCoordinate(lat, lon)) {
                        val fallback = CoordinateResult(
                            latitude = lat,
                            longitude = lon,
                            name = "Selected Location",
                            sourceMethod = SourceMethod.WEBVIEW_INTERCEPT,
                            originalUrl = raw
                        )
                        derivedResult = fallback
                        onCoordinatesDerived(fallback)
                    }
                }
            } finally {
                isDeriving = false
            }
        }
    }

    fun extractFromCurrentWebView() {
        val wv = webViewInstance ?: return
        val currentUrl = wv.url
        if (!currentUrl.isNullOrBlank()) {
            processRawUrlOrText(currentUrl)
        } else {
            wv.evaluateJavascript("window.location.href") { value ->
                val clean = value?.trim('\"') ?: ""
                if (clean.isNotBlank()) {
                    processRawUrlOrText(clean)
                }
            }
        }
    }

    // Intercept hardware / system back navigation
    BackHandler(enabled = derivedResult != null || canGoBack) {
        if (derivedResult != null) {
            derivedResult = null
        } else if (canGoBack) {
            webViewInstance?.goBack()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Embedded WebView
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                WebView(ctx).apply {
                    webViewInstance = this
                    layoutParams = android.view.ViewGroup.LayoutParams(
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                        android.view.ViewGroup.LayoutParams.MATCH_PARENT
                    )

                    settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        setGeolocationEnabled(true)
                        useWideViewPort = true
                        loadWithOverviewMode = true
                        cacheMode = WebSettings.LOAD_DEFAULT
                        builtInZoomControls = true
                        displayZoomControls = false
                        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                    }

                    android.webkit.CookieManager.getInstance().setAcceptCookie(true)
                    android.webkit.CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                    // JavaScript Bridge to intercept Share API and map clicks
                    addJavascriptInterface(
                        object {
                            @JavascriptInterface
                            fun onShareIntercepted(url: String?, title: String?, text: String?) {
                                mainHandler.post {
                                    val candidate = when {
                                        !url.isNullOrBlank() -> url
                                        !text.isNullOrBlank() && (text.contains("http://") || text.contains("https://")) -> {
                                            Regex("""https?://\S+""").find(text)?.value ?: text
                                        }
                                        else -> text ?: ""
                                    }
                                    if (candidate.isNotBlank()) {
                                        processRawUrlOrText(candidate)
                                    }
                                }
                            }

                            @JavascriptInterface
                            fun onUrlChanged(url: String?) {
                                if (url.isNullOrBlank()) return
                                // Only auto-derive if URL explicitly points to a place or has explicit pin
                                if (url.contains("/place/") || url.contains("!3d") || url.contains("?q=")) {
                                    mainHandler.post {
                                        processRawUrlOrText(url)
                                    }
                                }
                            }
                        },
                        "AndroidBridge"
                    )

                    webChromeClient = object : WebChromeClient() {
                        override fun onGeolocationPermissionsShowPrompt(
                            origin: String,
                            callback: GeolocationPermissions.Callback
                        ) {
                            callback.invoke(origin, true, false)
                        }

                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            pageLoadProgress = newProgress
                            isPageLoading = newProgress < 100
                        }

                        override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                            android.util.Log.d("GmapWebView", "JS Console: ${consoleMessage?.message()}")
                            return super.onConsoleMessage(consoleMessage)
                        }
                    }

                    webViewClient = object : WebViewClient() {
                        private fun injectHelperJs(view: WebView) {
                            val js = """
                                (function() {
                                    if (!window.__omr_installed) {
                                        window.__omr_installed = true;
                                        // Intercept Web Share API
                                        const _origShare = navigator.share;
                                        navigator.share = function(data) {
                                            try {
                                                if (window.AndroidBridge && data) {
                                                    window.AndroidBridge.onShareIntercepted(
                                                        data.url || '',
                                                        data.title || '',
                                                        data.text || ''
                                                    );
                                                    return Promise.resolve();
                                                }
                                            } catch(e) {}
                                            if (_origShare) return _origShare.call(navigator, data);
                                            return Promise.resolve();
                                        };

                                        // Listen to user map clicks
                                        function notifyUrl() {
                                            try {
                                                if (window.AndroidBridge && window.location.href) {
                                                    window.AndroidBridge.onUrlChanged(window.location.href);
                                                }
                                            } catch(e) {}
                                        }
                                        document.addEventListener('click', function() {
                                            setTimeout(notifyUrl, 600);
                                            setTimeout(notifyUrl, 1400);
                                        }, true);
                                    }
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            super.onPageStarted(view, url, favicon)
                            view?.let { injectHelperJs(it) }
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun onPageFinished(view: WebView?, url: String?) {
                            super.onPageFinished(view, url)
                            view?.let { injectHelperJs(it) }
                            canGoBack = view?.canGoBack() == true
                        }

                        override fun doUpdateVisitedHistory(view: WebView?, url: String?, isReload: Boolean) {
                            super.doUpdateVisitedHistory(view, url, isReload)
                            canGoBack = view?.canGoBack() == true
                            if (url != null && (url.contains("/place/") || url.contains("!3d") || url.contains("?q="))) {
                                processRawUrlOrText(url)
                            }
                        }

                        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                            val uri = request?.url ?: return false
                            val urlStr = uri.toString()

                            // Intercept Intent scheme
                            if (urlStr.startsWith("intent:", ignoreCase = true)) {
                                try {
                                    val intent = Intent.parseUri(urlStr, Intent.URI_INTENT_SCHEME)
                                    val shareData = intent.getStringExtra(Intent.EXTRA_TEXT) ?: intent.dataString
                                    if (!shareData.isNullOrBlank()) {
                                        processRawUrlOrText(shareData)
                                    }
                                } catch (_: Exception) {}
                                return true
                            }

                            // Intercept geo: URI
                            if (urlStr.startsWith("geo:", ignoreCase = true)) {
                                processRawUrlOrText(urlStr)
                                return true
                            }

                            return false
                        }
                    }

                    loadUrl("https://www.google.com/maps")
                }
            },
            update = { wv ->
                canGoBack = wv.canGoBack()
            }
        )

        // Loading Progress Bar at top
        if (isPageLoading) {
            LinearProgressIndicator(
                progress = { pageLoadProgress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .align(Alignment.TopCenter),
                color = EmeraldGreen,
                trackColor = DarkSurface
            )
        }

        // Floating Quick Action: "Derive Pin / Center" Button in Top-Right
        Surface(
            onClick = { extractFromCurrentWebView() },
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 16.dp, end = 16.dp),
            shape = RoundedCornerShape(50),
            color = DarkSurfaceElevated,
            border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
            shadowElevation = 6.dp
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isDeriving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp,
                        color = PureWhite
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.GpsFixed,
                        contentDescription = "Derive Current Location",
                        tint = EmeraldGreen,
                        modifier = Modifier.size(15.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Derive Pin",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PureWhite
                )
            }
        }

        // SLIDE-UP ANIMATED BUTTONS AT BOTTOM CENTER
        AnimatedVisibility(
            visible = derivedResult != null,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioMediumBouncy,
                    stiffness = Spring.StiffnessMedium
                )
            ) + fadeIn(animationSpec = tween(200)),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = tween(200)
            ) + fadeOut(animationSpec = tween(150)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 24.dp, end = 24.dp, bottom = 20.dp)
        ) {
            derivedResult?.let { result ->
                Surface(
                    color = DarkSurfaceElevated,
                    shape = RoundedCornerShape(26.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                    shadowElevation = 14.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Header with status indicator & place title & dismiss button
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(EmeraldGreen)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Column {
                                    Text(
                                        text = result.name ?: "Derived Location",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = PureWhite,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = String.format(Locale.US, "%.5f, %.5f", result.latitude, result.longitude),
                                        fontSize = 11.sp,
                                        color = TextSecondaryDark
                                    )
                                }
                            }

                            IconButton(
                                onClick = { derivedResult = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Close,
                                    contentDescription = "Dismiss",
                                    tint = TextSecondaryDark,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // THE TWO CIRCULAR BUTTONS AT BOTTOM CENTER
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(36.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // 1. CIRCULAR BUTTON: Redirect to Organic Maps
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    onClick = {
                                        onOpenOrganicMaps(result)
                                    },
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(CircleShape),
                                    shape = CircleShape,
                                    color = PureWhite,
                                    shadowElevation = 6.dp
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Navigation,
                                            contentDescription = "Redirect to Organic Maps",
                                            tint = PureBlack,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "Organic Maps",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = PureWhite,
                                    textAlign = TextAlign.Center
                                )
                            }

                            // 2. CIRCULAR BUTTON: System Map
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Surface(
                                    onClick = {
                                        onOpenSystemMap(result)
                                    },
                                    modifier = Modifier
                                        .size(58.dp)
                                        .clip(CircleShape),
                                    shape = CircleShape,
                                    color = DarkSurface,
                                    border = androidx.compose.foundation.BorderStroke(1.dp, DarkBorder),
                                    shadowElevation = 6.dp
                                ) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Map,
                                            contentDescription = "Open in System Map",
                                            tint = PureWhite,
                                            modifier = Modifier.size(28.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "System Map",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = TextSecondaryDark,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
