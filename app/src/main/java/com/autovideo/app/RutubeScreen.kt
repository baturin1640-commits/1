package com.autovideo.app

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslError
import android.os.Build
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
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun RutubeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val webViewResult = remember(context) { runCatching { WebView(context) } }
    val webView = webViewResult.getOrNull()
    val container = remember(context) { FrameLayout(context) }
    var loading by remember { mutableStateOf(true) }
    var progress by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var fullscreenView by remember { mutableStateOf<View?>(null) }
    var fullscreenCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun leaveFullscreen() {
        fullscreenView?.let(container::removeView)
        fullscreenView = null
        fullscreenCallback?.onCustomViewHidden()
        fullscreenCallback = null
        webView?.visibility = View.VISIBLE
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
            allowFileAccess = false
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
            setSupportMultipleWindows(false)
            builtInZoomControls = false
            displayZoomControls = false
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) safeBrowsingEnabled = true
        }

        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webView, false)
        }

        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val value = request.url.toString()
                if (OnlineUrlValidator.isRutubeUrl(value)) return false
                val secure = OnlineUrlValidator.normalizeSecureUrl(value) ?: return true
                runCatching {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(secure)))
                }
                return true
            }

            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                loading = true
                error = null
            }

            override fun onPageFinished(view: WebView, url: String) {
                loading = false
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
            }

            override fun onHideCustomView() {
                leaveFullscreen()
            }
        }

        if (webView.url == null) webView.loadUrl("https://rutube.ru/")

        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> webView.onResume()
                Lifecycle.Event.ON_PAUSE -> webView.onPause()
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
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(factory = { container }, modifier = Modifier.fillMaxSize())

        if (fullscreenView == null) {
            Row(
                modifier = Modifier.fillMaxWidth().background(Color(0xD0080710)).padding(12.dp),
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
                    Text("Загрузка $progress%", color = AutoMuted, fontSize = 12.sp)
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
        }

        if (loading && fullscreenView == null) {
            CircularProgressIndicator(Modifier.align(Alignment.Center), color = AutoPurple)
        }

        error?.let { message ->
            Column(
                modifier = Modifier.align(Alignment.Center).background(Color(0xEF15101C)).padding(24.dp),
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
private fun WebViewUnavailableScreen(message: String, onBack: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().background(AutoBackground).padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        HeadUnitIconButton(Icons.Rounded.ArrowBack, "Назад", onBack)
        Spacer(Modifier.weight(1f))
        Text(message, color = AutoText, fontSize = 20.sp)
        Text("Обновите системный компонент Android System WebView", color = AutoMuted, fontSize = 14.sp)
        Spacer(Modifier.weight(1f))
    }
}
