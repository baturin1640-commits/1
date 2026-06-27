package com.autovideo.app

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableImmersiveMode()

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val systemDensity = LocalDensity.current
            val carDensity = remember(systemDensity.density, systemDensity.fontScale) {
                Density(
                    density = systemDensity.density,
                    fontScale = maxOf(systemDensity.fontScale, 1.35f),
                )
            }

            val folderPicker = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.StartActivityForResult(),
            ) { result ->
                if (result.resultCode != Activity.RESULT_OK) return@rememberLauncherForActivityResult
                val data = result.data ?: return@rememberLauncherForActivityResult
                val uri = data.data ?: return@rememberLauncherForActivityResult
                val requestedFlags = data.flags and
                    (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                val persistableFlags = if (requestedFlags == 0) {
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                } else {
                    requestedFlags
                }
                runCatching {
                    contentResolver.takePersistableUriPermission(uri, persistableFlags)
                }.onFailure {
                    Toast.makeText(
                        this@MainActivity,
                        "Не удалось сохранить доступ к накопителю",
                        Toast.LENGTH_LONG,
                    ).show()
                }
                viewModel.addSource(uri)
            }

            val permissionLauncher = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.RequestMultiplePermissions(),
            ) {
                viewModel.refresh()
            }

            LaunchedEffect(Unit) {
                val missing = MediaPermissions.missingPermissions(this@MainActivity)
                if (missing.isNotEmpty()) {
                    permissionLauncher.launch(missing)
                }
            }

            CompositionLocalProvider(LocalDensity provides carDensity) {
                AutoVideoTheme {
                    VideoAppRoot(
                        state = state,
                        onAddSource = {
                            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
                                addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
                            }
                            try {
                                if (intent.resolveActivity(packageManager) == null) {
                                    Toast.makeText(
                                        this@MainActivity,
                                        "На устройстве отсутствует системный выбор папки",
                                        Toast.LENGTH_LONG,
                                    ).show()
                                } else {
                                    folderPicker.launch(intent)
                                }
                            } catch (_: ActivityNotFoundException) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Системный выбор накопителя недоступен",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } catch (_: Throwable) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Не удалось открыть выбор накопителя",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        },
                        onRefresh = viewModel::refresh,
                        onRemoveSource = viewModel::removeSource,
                    )
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) enableImmersiveMode()
    }

    private fun enableImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }
}
