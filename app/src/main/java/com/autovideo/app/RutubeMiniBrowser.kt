package com.autovideo.app

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.widget.Toast
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.core.content.ContextCompat

private const val RUTUBE_URL = "https://rutube.ru/"

fun openRutubeMiniBrowser(context: Context) {
    val rutubeUri = Uri.parse(RUTUBE_URL)
    val colorScheme = CustomTabColorSchemeParams.Builder()
        .setToolbarColor(Color.rgb(16, 10, 31))
        .setNavigationBarColor(Color.BLACK)
        .setNavigationBarDividerColor(Color.BLACK)
        .build()

    val customTab = CustomTabsIntent.Builder()
        .setDefaultColorSchemeParams(colorScheme)
        .setShowTitle(true)
        .setUrlBarHidingEnabled()
        .setShareState(CustomTabsIntent.SHARE_STATE_OFF)
        .setStartAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
        .setExitAnimations(context, android.R.anim.fade_in, android.R.anim.fade_out)
        .build()
        .apply {
            intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
            intent.putExtra(Intent.EXTRA_REFERRER, Uri.parse("android-app://${context.packageName}"))
        }

    try {
        customTab.launchUrl(context, rutubeUri)
    } catch (_: ActivityNotFoundException) {
        openRutubeFallback(context, rutubeUri)
    } catch (_: SecurityException) {
        openRutubeFallback(context, rutubeUri)
    } catch (_: Throwable) {
        openRutubeFallback(context, rutubeUri)
    }
}

private fun openRutubeFallback(context: Context, uri: Uri) {
    val fallback = Intent(Intent.ACTION_VIEW, uri).apply {
        addCategory(Intent.CATEGORY_BROWSABLE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        ContextCompat.startActivity(context, fallback, null)
    } catch (_: Throwable) {
        Toast.makeText(
            context,
            "На устройстве нет совместимого браузера для RUTUBE",
            Toast.LENGTH_LONG,
        ).show()
    }
}
