package com.autovideo.app

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.graphics.Bitmap
import android.media.AudioManager
import android.net.Uri
import android.net.http.SslError
import android.os.Build
import android.os.Message
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.PermissionRequest
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.consume
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

private const val RUTUBE_HOME = "https://rutube.ru/"

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RutubeScreen(
    onBack: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val activity = remember(context) { context.findRutubeActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val latestFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    val webView = remember(context) { runCatching { WebView(context) }.getOrNull() }
    val container = remember(context) { FrameLayout(context) }

    var popupWebView by remember { mutableStateOf<WebView?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }

    fun activeWebView(): WebView? = popupWebView ?: webView

    fun closePopup() {
        val popup = popupWebView ?: return
        container.removeView(popup)
        popup.stopLoading()
        popup.webChromeClient = null
        popup.webViewClient = WebViewClient()
        popup.removeAllViews()
        popup.destroy()
        popupWebView = null
        webView?.visibility = View.VISIBLE
    }

    fun leaveFullscreen() {
        fullscreenView?.let(container::removeView)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        activeWebView()?.visibility = View.VISIBLE
        latestFullscreenChanged(false)
    }

    fun reloadActivePage() {
        error = null
        loading = true
        activeWebView()?.apply {
            stopLoading()
            reload()
        }
    }

    LaunchedEffect(gestureFeedback) {
        if (gestureFeedback != null) {
            delay(1_100L)
            gestureFeedback = null
        }
    }

    BackHandler {
        when {
            fullscreenView != null -> leaveFullscreen()
            popupWebView?.canGoBack() == true -> popupWebView?.goBack()
            popupWebView != null -> closePopup()
            webView?.canGoBack() == true -> webView.goBack()
            else -> onBack()
        }
    }

    if (webView == null) {
        WebViewUnavailableScreen(
            message = "Системный WebView отсутствует или не запускается",
            onBack = onBack,
        )
        return
    }

    DisposableEffect(webView, lifecycleOwner) {
        configureRutubeWebView(webView)
        if (webView.parent == null) {
            container.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        fun showFullscreen(view: View, callback: WebChromeClient.CustomViewCallback) {
            if (fullscreenView != null) {
                callback.onCustomViewHidden()
                return
            }
            fullscreenView = view
            fullscreenCallback = callback
            webView.visibility = View.GONE
            popupWebView?.visibility = View.GONE
            container.addView(
                view,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
            latestFullscreenChanged(true)
        }

        fun createChromeClient(owner: WebView): WebChromeClient = object : WebChromeClient() {
            override fun onPermissionRequest(request: PermissionRequest) {
                val secureOrigin = request.origin.scheme.equals("https", ignoreCase = true)
                val protectedMedia = request.resources.filter {
                    it == PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID
                }.toTypedArray()
                if (secureOrigin && protectedMedia.isNotEmpty()) {
                    request.grant(protectedMedia)
                } else {
                    request.deny()
                }
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                showFullscreen(view, callback)
            }

            override fun onHideCustomView() {
                leaveFullscreen()
            }

            override fun onCloseWindow(window: WebView) {
                if (window === popupWebView) closePopup()
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                closePopup()
                val popup = WebView(view.context)
                configureRutubeWebView(popup)
                popup.webViewClient = createRutubeClient(
                    onLoading = { loading = it },
                    onError = { error = it },
                )
                popup.webChromeClient = createChromeClient(popup)
                CookieManager.getInstance().setAcceptThirdPartyCookies(popup, true)
                popupWebView = popup
                webView.visibility = View.GONE
                container.addView(
                    popup,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: run {
                    closePopup()
                    return false
                }
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.webViewClient = createRutubeClient(
            onLoading = { loading = it },
            onError = { error = it },
        )
        webView.webChromeClient = createChromeClient(webView)

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        if (webView.url == null) webView.loadUrl(RUTUBE_HOME)

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    activeWebView()?.onResume()
                    activeWebView()?.resumeTimers()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    activeWebView()?.onPause()
                    CookieManager.getInstance().flush()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            leaveFullscreen()
            closePopup()
            webView.stopLoading()
            webView.webChromeClient = null
            webView.webViewClient = WebViewClient()
            CookieManager.getInstance().flush()
            container.removeView(webView)
            webView.removeAllViews()
            webView.destroy()
            latestFullscreenChanged(false)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        Column(Modifier.fillMaxSize()) {
            AndroidView(
                factory = { container },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            )

            if (fullscreenView == null) {
                RutubeBottomControls(
                    popupOpen = popupWebView != null,
                    canGoBack = activeWebView()?.canGoBack() == true,
                    onBack = {
                        when {
                            popupWebView?.canGoBack() == true -> popupWebView?.goBack()
                            popupWebView != null -> closePopup()
                            webView.canGoBack() -> webView.goBack()
                            else -> onBack()
                        }
                    },
                    onHome = {
                        closePopup()
                        webView.loadUrl(RUTUBE_HOME)
                    },
                    onRefresh = ::reloadActivePage,
                )
            }
        }

        if (fullscreenView != null) {
            RutubeEdgeGestureZone(
                isVolume = true,
                audioManager = audioManager,
                activity = activity,
                onFeedback = { gestureFeedback = it },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .fillMaxWidth(0.22f),
            )
            RutubeEdgeGestureZone(
                isVolume = false,
                audioManager = audioManager,
                activity = activity,
                onFeedback = { gestureFeedback = it },
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .fillMaxWidth(0.22f),
            )
        }

        gestureFeedback?.let { value ->
            Text(
                value,
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xE8070610), RoundedCornerShape(18.dp))
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (loading && fullscreenView == null) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = AutoPurple,
            )
        }

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xF215101C), RoundedCornerShape(24.dp))
                    .padding(30.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, fontSize = 21.sp)
                Spacer(Modifier.height(14.dp))
                HeadUnitActionButton("Повторить", Icons.Rounded.Refresh, onClick = ::reloadActivePage)
            }
        }
    }
}

@Composable
private fun RutubeBottomControls(
    popupOpen: Boolean,
    canGoBack: Boolean,
    onBack: () -> Unit,
    onHome: () -> Unit,
    onRefresh: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF0A0811))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        HeadUnitIconButton(
            Icons.Rounded.ArrowBack,
            "Назад",
            onBack,
            enabled = canGoBack || popupOpen,
            size = 74.dp,
            iconSize = 42.dp,
            backgroundColor = Color(0xFF282331),
        )
        Spacer(Modifier.width(12.dp))
        HeadUnitIconButton(
            Icons.Rounded.Home,
            "Главная RUTUBE",
            onHome,
            size = 74.dp,
            iconSize = 42.dp,
            backgroundColor = Color(0xFF282331),
        )
        Spacer(Modifier.width(18.dp))
        Column(Modifier.weight(1f)) {
            Text("RUTUBE", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                if (popupOpen) "Авторизация открыта внутри приложения" else "Штатная страница без наложений",
                color = AutoMuted,
                fontSize = 14.sp,
            )
        }
        HeadUnitIconButton(
            Icons.Rounded.Refresh,
            "Обновить страницу",
            onRefresh,
            size = 74.dp,
            iconSize = 42.dp,
            backgroundColor = AutoPurple,
        )
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureRutubeWebView(view: WebView) {
    view.setBackgroundColor(android.graphics.Color.BLACK)
    view.setLayerType(View.LAYER_TYPE_HARDWARE, null)
    view.overScrollMode = View.OVER_SCROLL_NEVER
    view.isVerticalScrollBarEnabled = false
    view.isHorizontalScrollBarEnabled = false
    with(view.settings) {
        javaScriptEnabled = true
        domStorageEnabled = true
        databaseEnabled = true
        allowFileAccess = false
        allowContentAccess = false
        mediaPlaybackRequiresUserGesture = false
        mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        cacheMode = WebSettings.LOAD_DEFAULT
        setSupportMultipleWindows(true)
        javaScriptCanOpenWindowsAutomatically = true
        setSupportZoom(true)
        builtInZoomControls = true
        displayZoomControls = false
        useWideViewPort = false
        loadWithOverviewMode = false
        textZoom = 100
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
    }
}

private fun createRutubeClient(
    onLoading: (Boolean) -> Unit,
    onError: (String?) -> Unit,
): WebViewClient = object : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
        return handleRutubeNavigation(view, request.url)
    }

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        return handleRutubeNavigation(view, uri)
    }

    override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
        onLoading(true)
        onError(null)
    }

    override fun onPageFinished(view: WebView, url: String) {
        onLoading(false)
        CookieManager.getInstance().flush()
    }

    override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
        handler.cancel()
        onLoading(false)
        onError("Ошибка защищённого соединения. Страница не открыта.")
    }

    override fun onReceivedError(
        view: WebView,
        request: WebResourceRequest,
        resourceError: WebResourceError,
    ) {
        if (request.isForMainFrame) {
            onLoading(false)
            onError("RUTUBE не загрузился. Проверьте интернет и повторите обновление.")
        }
    }
}

private fun handleRutubeNavigation(view: WebView, target: Uri): Boolean {
    return when (target.scheme?.lowercase()) {
        "https" -> false
        "http" -> {
            view.loadUrl(target.buildUpon().scheme("https").build().toString())
            true
        }
        "intent" -> {
            val fallback = runCatching {
                Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME)
                    .getStringExtra("browser_fallback_url")
            }.getOrNull()
            val secureFallback = fallback?.let(Uri::parse)
            if (secureFallback?.scheme.equals("https", ignoreCase = true)) {
                view.loadUrl(secureFallback.toString())
            }
            true
        }
        "about", "data", "blob" -> false
        else -> true
    }
}

@Composable
private fun RutubeEdgeGestureZone(
    isVolume: Boolean,
    audioManager: AudioManager,
    activity: Activity?,
    onFeedback: (String) -> Unit,
    modifier: Modifier,
) {
    val context = LocalContext.current
    var accumulated by remember { mutableFloatStateOf(0f) }
    var startVolume by remember { mutableStateOf(0) }
    var startBrightness by remember { mutableFloatStateOf(0.5f) }

    Box(
        modifier = modifier.pointerInput(isVolume, audioManager, activity) {
            detectVerticalDragGestures(
                onDragStart = {
                    accumulated = 0f
                    startVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                    startBrightness = currentRutubeBrightness(context, activity)
                },
                onVerticalDrag = { change, dragAmount ->
                    change.consume()
                    accumulated += dragAmount
                    if (isVolume) {
                        val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                        val delta = (-accumulated / size.height.toFloat() * maxVolume).roundToInt()
                        val target = (startVolume + delta).coerceIn(0, maxVolume)
                        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
                        onFeedback("Громкость ${(target * 100f / maxVolume).roundToInt()}%")
                    } else {
                        val target = (startBrightness - accumulated / size.height.toFloat()).coerceIn(0.05f, 1f)
                        setRutubeBrightness(activity, target)
                        onFeedback("Яркость ${(target * 100f).roundToInt()}%")
                    }
                },
            )
        },
    )
}

private fun currentRutubeBrightness(context: Context, activity: Activity?): Float {
    val explicit = activity?.window?.attributes?.screenBrightness ?: -1f
    if (explicit >= 0f) return explicit.coerceIn(0.05f, 1f)
    val systemValue = runCatching {
        Settings.System.getInt(context.contentResolver, Settings.System.SCREEN_BRIGHTNESS)
    }.getOrDefault(128)
    return (systemValue / 255f).coerceIn(0.05f, 1f)
}

private fun setRutubeBrightness(activity: Activity?, value: Float) {
    val window = activity?.window ?: return
    val attributes = window.attributes
    attributes.screenBrightness = value.coerceIn(0.05f, 1f)
    window.attributes = attributes
}

@Composable
private fun WebViewUnavailableScreen(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AutoBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeadUnitIconButton(Icons.Rounded.ArrowBack, "Назад", onBack)
        Spacer(Modifier.weight(1f))
        Text(message, color = AutoText, fontSize = 25.sp)
        Text("Обновите системный компонент Android System WebView", color = AutoMuted, fontSize = 18.sp)
        Spacer(Modifier.weight(1f))
    }
}

private tailrec fun Context.findRutubeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findRutubeActivity()
    else -> null
}
