package com.autovideo.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()
    private var allFilesPromptShown = false
    private var waitingForAllFilesSettings = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()

            val folderPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION,
                        )
                    }
                    viewModel.addSource(uri)
                }
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                requestAllFilesAccessOnce()
                viewModel.refresh()
            }

            LaunchedEffect(Unit) {
                val missing = MediaPermissions.missingPermissions(this@MainActivity)
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing)
                } else {
                    requestAllFilesAccessOnce()
                }
            }

            AutoVideoTheme {
                AutoVideoApp(
                    state = state,
                    onAddSource = { folderPicker.launch(null) },
                    onRefresh = viewModel::refresh,
                    onRemoveSource = viewModel::removeSource,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (waitingForAllFilesSettings) {
            waitingForAllFilesSettings = false
            viewModel.refresh()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    private fun requestAllFilesAccessOnce() {
        if (!FullStorageAccess.isRequired()) return
        if (FullStorageAccess.isGranted(this)) return
        if (allFilesPromptShown) return

        val intent = FullStorageAccess.settingsIntent(this) ?: return
        allFilesPromptShown = true
        waitingForAllFilesSettings = true
        runCatching { startActivity(intent) }
            .onFailure { waitingForAllFilesSettings = false }
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
