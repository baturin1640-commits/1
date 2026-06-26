package com.autovideo.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings

object FullStorageAccess {
    fun isRequired(): Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R

    fun isGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            MediaPermissions.access(context).any
        }
    }

    fun settingsIntent(context: Context): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        val appIntent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        )
        if (appIntent.resolveActivity(context.packageManager) != null) return appIntent

        val fallback = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        return fallback.takeIf { it.resolveActivity(context.packageManager) != null }
    }
}
