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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RutubeScreen(
    onBack: () -> Unit,
    onFullscreenChanged: (Boolean) -> Unit = {},
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestFullscreenChanged by rememberUpdatedState(onFullscreenChanged)
    val webViewResult = remember(context) { runCatching { WebView(context) } }
    val webView = webViewResult.getOrNull()
    val container = remember(context) { FrameLayout(context) }
    val activity = remember(context) { context.findRutubeActivity() }
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }
    var gestureFeedback by remember { mutableStateOf<String?>(null) }

    fun leaveFullscreen() {
        fullscreenView?.let(container::removeView)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        webView?.visibility = View.VISIBLE
        latestFullscreenChanged(false)
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
        if (webView.parent == null) {
            container.addView(
                webView,
                FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                ),
            )
        }

        with(webView.settings) {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(true)
            javaScriptCanOpenWindowsAutomatically = true
            builtInZoomControls = false
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, true)
        }

        fun openInsideApp(target: Uri): Boolean {
            return when (target.scheme?.lowercase()) {
                "https" -> {
                    webView.loadUrl(target.toString())
                    true
                }
                "http" -> {
                    webView.loadUrl(target.buildUpon().scheme("https").build().toString())
                    true
                }
                "intent" -> {
                    val intent = runCatching {
                        Intent.parseUri(target.toString(), Intent.URI_INTENT_SCHEME)
                    }.getOrNull()
                    val fallback = intent?.getStringExtra("browser_fallback_url")
                    val fallbackUri = fallback?.let(Uri::parse)
                    if (fallbackUri?.scheme.equals("https", ignoreCase = true)) {
                        webView.loadUrl(fallbackUri.toString())
                    }
                    true
                }
                "about" -> false
                else -> true
            }
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val target = request.url
                return when (target.scheme?.lowercase()) {
                    "https" -> false
                    "http" -> {
                        view.loadUrl(target.buildUpon().scheme("https").build().toString())
                        true
                    }
                    "intent" -> openInsideApp(target)
                    "about" -> false
                    else -> true
                }
            }

            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val target = runCatching { Uri.parse(url) }.getOrNull() ?: return true
                return when (target.scheme?.lowercase()) {
                    "https" -> false
                    "http" -> {
                        view.loadUrl(target.buildUpon().scheme("https").build().toString())
                        true
                    }
                    "intent" -> openInsideApp(target)
                    "about" -> false
                    else -> true
                }
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loading = true
                error = null
            }

            override fun onPageFinished(view: WebView, url: String) {
                loading = false
                CookieManager.getInstance().flush()
            }

            override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, sslError: SslError) {
                handler.cancel()
                loading = false
                error = "Ошибка защищённого соединения. Страница не открыта."
            }

            override fun onReceivedError(
                view: WebView,
                request: WebResourceRequest,
                resourceError: WebResourceError,
            ) {
                if (request.isForMainFrame) {
                    loading = false
                    error = "RUTUBE сейчас недоступен. Проверьте интернет и повторите попытку."
                }
            }
        }

        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                progress = newProgress.coerceIn(0, 100)
                loading = newProgress < 100
            }

            override fun onCreateWindow(
                view: WebView,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: Message,
            ): Boolean {
                val child = WebView(view.context)
                child.settings.javaScriptEnabled = true
                child.settings.domStorageEnabled = true
                child.settings.allowFileAccess = false
                child.settings.allowContentAccess = false
                child.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(childView: WebView, request: WebResourceRequest): Boolean {
                        val target = request.url
                        when {
                            target.scheme.equals("https", ignoreCase = true) -> webView.loadUrl(target.toString())
                            target.scheme.equals("http", ignoreCase = true) -> {
                                webView.loadUrl(target.buildUpon().scheme("https").build().toString())
                            }
                        }
                        childView.stopLoading()
                        childView.destroy()
                        return true
                    }

                    override fun onPageStarted(childView: WebView, url: String, favicon: Bitmap?) {
                        val target = runCatching { Uri.parse(url) }.getOrNull()
                        when {
                            target?.scheme.equals("https", ignoreCase = true) -> webView.loadUrl(url)
                            target?.scheme.equals("http", ignoreCase = true) -> {
                                webView.loadUrl(target!!.buildUpon().scheme("https").build().toString())
                            }
                        }
                        childView.stopLoading()
                        childView.destroy()
                    }
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = child
                resultMsg.sendToTarget()
                return true
            }

            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                if (fullscreenView != null) {
                    callback.onCustomViewHidden()
                    return
                }
                fullscreenView = view
                fullscreenCallback = callback
                webView.visibility = View.GONE
                container.addView(
                    view,
                    FrameLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    ),
                )
                latestFullscreenChanged(true)
            }

            override fun onHideCustomView() {
                leaveFullscreen()
            }
        }

        if (webView.url == null) webView.loadUrl("https://rutube.ru/")

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> {
                    webView.onPause()
                    CookieManager.getInstance().flush()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)

        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            leaveFullscreen()
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
        AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())

        if (fullscreenView == null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xD0080710))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                HeadUnitIconButton(
                    Icons.Rounded.ArrowBack,
                    "Назад",
                    onClick = {
                        if (webView.canGoBack()) webView.goBack() else onBack()
                    },
                    size = 60.dp,
                    iconSize = 32.dp,
                    backgroundColor = Color(0x99000000),
                )
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("RUTUBE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Text(
                        if (loading) "Загрузка $progress%" else "Вход и просмотр внутри приложения",
                        color = AutoMuted,
                        fontSize = 12.sp,
                    )
                }
                HeadUnitIconButton(
                    Icons.Rounded.Refresh,
                    "Обновить",
                    onClick = { webView.reload() },
                    size = 60.dp,
                    iconSize = 32.dp,
                    backgroundColor = Color(0x99000000),
                )
            }
        } else {
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
                    .background(Color(0xE8070610), RoundedCornerShape(16.dp))
                    .padding(horizontal = 18.dp, vertical = 12.dp),
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )
        }

        if (loading && fullscreenView == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = AutoPurple)
        }

        error?.let { message ->
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .background(Color(0xEF15101C))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(message, color = Color.White, fontSize = 16.sp)
                Spacer(Modifier.padding(5.dp))
                HeadUnitActionButton("Повторить", Icons.Rounded.Refresh, onClick = { webView.reload() })
            }
        }
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
    var startVolume by remember { mutableIntStateOf(0) }
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
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeadUnitIconButton(Icons.Rounded.ArrowBack, "Назад", onBack)
        Spacer(Modifier.weight(1f))
        Text(message, color = AutoText, fontSize = 20.sp)
        Text("Обновите системный компонент Android System WebView", color = AutoMuted, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
    }
}

private tailrec fun Context.findRutubeActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findRutubeActivity()
    else -> null
}
