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
    isActive: Boolean = true,
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
    BackHandler(enabled = isActive && (derivedResult != null || canGoBack)) {
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

                        // Emulate regular Chrome mobile by stripping '; wv' and 'Version/4.0'
                        // This prevents Google Maps from detecting WebView and prompting "Stay on web" / "Open app"
                        val defaultUa = userAgentString ?: ""
                        userAgentString = defaultUa
                            .replace("; wv", "")
                            .replace(Regex("""Version/\d+\.\d+\s*"""), "")
                    }

                    val cookieManager = android.webkit.CookieManager.getInstance()
                    cookieManager.setAcceptCookie(true)
                    cookieManager.setAcceptThirdPartyCookies(this, true)
                    cookieManager.setCookie("https://www.google.com", "CONSENT=YES+; path=/; domain=.google.com")
                    cookieManager.setCookie("https://www.google.com", "SOCS=CAISHAgBEhJnd3NfMjAyMzA4MTAtMF9SQzIaAmVuIAEaBgiAo_CmBg; path=/; domain=.google.com")
                    cookieManager.setCookie("https://www.google.com", "maps_open_app_promo=dismiss; path=/; domain=.google.com")
                    cookieManager.setCookie("https://maps.google.com", "maps_open_app_promo=dismiss; path=/; domain=.google.com")
                    cookieManager.flush()

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
                                    // 1. Hide "Google Maps" & "Open app" top banner, "Stay on web", and promotion modals via CSS
                                    try {
                                        window.localStorage.setItem('igmmstate', JSON.stringify({state: '1', timestamp: Date.now()}));
                                    } catch(e) {}

                                    try {
                                        if (!document.getElementById('__omr_suppress_style')) {
                                            const style = document.createElement('style');
                                            style.id = '__omr_suppress_style';
                                            style.textContent = `
                                                .ml-persistent-promo-banner,
                                                [class*="ml-persistent-promo-banner"],
                                                [jsaction*="promotion.openApp"],
                                                .app-promo-container,
                                                #app-promo,
                                                .waterfall-app-promo,
                                                .ml-promotion-action-sheet,
                                                .app-banner,
                                                [aria-label*="Open in app"],
                                                [aria-label*="Open app"],
                                                [aria-label*="Get app"],
                                                [aria-label*="Use app"],
                                                .open-in-app-banner,
                                                .app-bar,
                                                .ml-app-promo,
                                                .mobile-app-banner,
                                                .drawer-open-in-app,
                                                div[role="dialog"][aria-label*="app"],
                                                div[data-promo-id] {
                                                    display: none !important;
                                                    visibility: hidden !important;
                                                    height: 0 !important;
                                                    max-height: 0 !important;
                                                    opacity: 0 !important;
                                                    overflow: hidden !important;
                                                    pointer-events: none !important;
                                                }
                                                [style*="top: 45px"],
                                                [style*="top:45px"] {
                                                    top: 0px !important;
                                                }
                                            `;
                                            (document.head || document.documentElement).appendChild(style);
                                        }
                                    } catch(e) {}

                                    // 2. Hide top header / promo banner, remove top offset, and auto-click "Stay on web"
                                    function suppressBannersAndDismissPromos() {
                                        try {
                                            // Explicitly hide top banner container if created dynamically
                                            const promoBanners = document.querySelectorAll('.ml-persistent-promo-banner, [class*="persistent-promo-banner"], [jsaction*="promotion.openApp"]');
                                            for (let i = 0; i < promoBanners.length; i++) {
                                                const el = promoBanners[i];
                                                const banner = el.closest('.ml-persistent-promo-banner') || el;
                                                banner.style.setProperty('display', 'none', 'important');
                                                banner.style.setProperty('height', '0px', 'important');
                                                banner.style.setProperty('visibility', 'hidden', 'important');
                                            }

                                            // Eliminate top: 45px offset left by Google Maps banner placeholder
                                            const shifted = document.querySelectorAll('[style*="top"]');
                                            for (let i = 0; i < shifted.length; i++) {
                                                if (shifted[i].style.top === '45px') {
                                                    shifted[i].style.setProperty('top', '0px', 'important');
                                                }
                                            }

                                            // Auto-click "Stay on web" or "Use web" if prompt appears
                                            const candidates = document.querySelectorAll('button, a, span, div[role="button"], p');
                                            for (let i = 0; i < candidates.length; i++) {
                                                const el = candidates[i];
                                                const txt = (el.innerText || el.textContent || '').trim().toLowerCase();
                                                if (txt === 'stay on web' || txt === 'use the web' || txt === 'use web' || txt === 'continue on web' || txt === 'stay on the web' || txt.includes('stay on web') || txt.includes('use the web')) {
                                                    const btn = el.closest('button, [role="button"], a') || el;
                                                    btn.click();
                                                    break;
                                                }
                                            }
                                        } catch(e) {}
                                    }
                                    suppressBannersAndDismissPromos();
                                    const promoInterval = setInterval(suppressBannersAndDismissPromos, 150);
                                    setTimeout(function() { clearInterval(promoInterval); }, 10000);

                                    try {
                                        const observer = new MutationObserver(function() {
                                            suppressBannersAndDismissPromos();
                                        });
                                        if (document.body) {
                                            observer.observe(document.body, { childList: true, subtree: true });
                                        } else {
                                            document.addEventListener('DOMContentLoaded', function() {
                                                if (document.body) observer.observe(document.body, { childList: true, subtree: true });
                                            });
                                        }
                                    } catch(e) {}

                                    // 3. Intercept Web Share API & click listeners
                                    if (!window.__omr_installed) {
                                        window.__omr_installed = true;
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

                            val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: ""
                            val host = uri.host?.lowercase(Locale.ROOT) ?: ""

                            // Keep Google Maps and Google internal navigation strictly within the WebView
                            if (scheme == "http" || scheme == "https") {
                                if (host.contains("google.") || host.contains("gstatic.com") || host.contains("googleusercontent.com")) {
                                    view?.loadUrl(urlStr)
                                    return true
                                }
                                // External URLs: open in external browser
                                try {
                                    val browserIntent = Intent(Intent.ACTION_VIEW, uri)
                                    context.startActivity(browserIntent)
                                } catch (_: Exception) {}
                                return true
                            }

                            return false
                        }
                    }

                    loadUrl("https://www.google.com/maps?force=qpm&entry=ttu")
                }
            },
            update = { wv ->
                canGoBack = wv.canGoBack()
                if (isActive) {
                    wv.onResume()
                } else {
                    wv.onPause()
                }
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
