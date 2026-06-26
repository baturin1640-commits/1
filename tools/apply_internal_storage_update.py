from pathlib import Path
import re
import textwrap


def write(path: str, content: str) -> None:
    target = Path(path)
    target.parent.mkdir(parents=True, exist_ok=True)
    target.write_text(textwrap.dedent(content).lstrip(), encoding="utf-8")


def replace_once(path: str, old: str, new: str) -> None:
    target = Path(path)
    content = target.read_text(encoding="utf-8")
    if old not in content:
        raise RuntimeError(f"Expected fragment was not found in {path}: {old[:80]!r}")
    target.write_text(content.replace(old, new, 1), encoding="utf-8")


# Android permissions and launcher icon.
manifest_path = "app/src/main/AndroidManifest.xml"
manifest = Path(manifest_path).read_text(encoding="utf-8")
manifest = manifest.replace(
    '<manifest xmlns:android="http://schemas.android.com/apk/res/android">',
    '''<manifest xmlns:android="http://schemas.android.com/apk/res/android">\n\n    <uses-permission android:name="android.permission.READ_MEDIA_VIDEO" />\n    <uses-permission android:name="android.permission.READ_MEDIA_AUDIO" />\n    <uses-permission\n        android:name="android.permission.READ_EXTERNAL_STORAGE"\n        android:maxSdkVersion="32" />''',
    1,
)
manifest = manifest.replace(
    '    <application\n        android:allowBackup="false"',
    '    <application\n        android:allowBackup="false"\n        android:icon="@mipmap/ic_launcher"\n        android:roundIcon="@mipmap/ic_launcher_round"',
    1,
)
Path(manifest_path).write_text(manifest, encoding="utf-8")

# Merge removable storage and Android MediaStore into one library.
write(
    "app/src/main/java/com/autovideo/app/MainViewModel.kt",
    r'''
    package com.autovideo.app

    import android.app.Application
    import android.net.Uri
    import androidx.lifecycle.AndroidViewModel
    import androidx.lifecycle.viewModelScope
    import java.util.Locale
    import kotlinx.coroutines.Dispatchers
    import kotlinx.coroutines.Job
    import kotlinx.coroutines.flow.MutableStateFlow
    import kotlinx.coroutines.flow.StateFlow
    import kotlinx.coroutines.flow.asStateFlow
    import kotlinx.coroutines.launch
    import kotlinx.coroutines.withContext

    class MainViewModel(application: Application) : AndroidViewModel(application) {
        private val sourceStore = StorageSources(application)
        private val removableMediaScanner = MediaScanner(application)
        private val internalMediaScanner = InternalMediaScanner(application)
        private val mutableState = MutableStateFlow(LibraryUiState())
        private var currentScan: Job? = null

        val uiState: StateFlow<LibraryUiState> = mutableState.asStateFlow()

        init {
            refresh()
        }

        fun addSource(uri: Uri) {
            sourceStore.add(uri)
            refresh()
        }

        fun removeSource(uriString: String) {
            if (uriString == INTERNAL_SOURCE_URI) return
            sourceStore.remove(uriString)
            refresh()
        }

        fun refresh() {
            currentScan?.cancel()
            currentScan = viewModelScope.launch {
                mutableState.value = mutableState.value.copy(loading = true, error = null)
                try {
                    val result = withContext(Dispatchers.IO) {
                        val removable = removableMediaScanner.scan(sourceStore.all())
                        val internal = internalMediaScanner.scan(
                            MediaPermissions.access(getApplication()),
                        )

                        val sources = buildList {
                            internal.first?.let(::add)
                            addAll(removable.first)
                        }
                        val folders = (internal.second + removable.second).sortedWith(
                            compareBy<MediaFolder> {
                                it.sourceName.lowercase(Locale.getDefault())
                            }.thenBy {
                                it.name.lowercase(Locale.getDefault())
                            },
                        )
                        sources to folders
                    }

                    mutableState.value = LibraryUiState(
                        loading = false,
                        sources = result.first,
                        folders = result.second,
                    )
                } catch (throwable: Throwable) {
                    mutableState.value = mutableState.value.copy(
                        loading = false,
                        error = throwable.message ?: "Не удалось прочитать медиатеку",
                    )
                }
            }
        }
    }
    ''',
)

# Request internal media access on tablets and keep the folder picker for any directory or drive.
write(
    "app/src/main/java/com/autovideo/app/MainActivity.kt",
    r'''
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
                    viewModel.refresh()
                }

                val requestInternalAccess: () -> Unit = {
                    val missing = MediaPermissions.missingPermissions(this@MainActivity)
                    if (missing.isEmpty()) {
                        viewModel.refresh()
                    } else {
                        permissionLauncher.launch(missing)
                    }
                }

                LaunchedEffect(Unit) {
                    val missing = MediaPermissions.missingPermissions(this@MainActivity)
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing)
                    }
                }

                AutoVideoTheme {
                    AutoVideoApp(
                        state = state,
                        onAddSource = { folderPicker.launch(null) },
                        onRequestInternalAccess = requestInternalAccess,
                        onRefresh = viewModel::refresh,
                        onRemoveSource = viewModel::removeSource,
                    )
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
                systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
    ''',
)

# Add internal-memory actions to the selected interface.
auto_path = "app/src/main/java/com/autovideo/app/AutoVideoApp.kt"
auto = Path(auto_path).read_text(encoding="utf-8")
auto = auto.replace(
    "import androidx.compose.material.icons.rounded.Home\n",
    "import androidx.compose.material.icons.rounded.Home\nimport androidx.compose.material.icons.rounded.PhoneAndroid\n",
    1,
)
auto = auto.replace(
    "    onAddSource: () -> Unit,\n    onRefresh: () -> Unit,",
    "    onAddSource: () -> Unit,\n    onRequestInternalAccess: () -> Unit,\n    onRefresh: () -> Unit,",
    1,
)
auto = auto.replace(
    "                            onAddSource = onAddSource,\n                            onRefresh = onRefresh,",
    "                            onAddSource = onAddSource,\n                            onRequestInternalAccess = onRequestInternalAccess,\n                            onRefresh = onRefresh,",
    1,
)
auto = auto.replace(
    "                            onAddSource = onAddSource,\n                            onRefresh = onRefresh,\n                            onRemoveSource = onRemoveSource,",
    "                            onAddSource = onAddSource,\n                            onRequestInternalAccess = onRequestInternalAccess,\n                            onRefresh = onRefresh,\n                            onRemoveSource = onRemoveSource,",
    1,
)
auto = auto.replace(
    "    onAddSource: () -> Unit,\n    onRefresh: () -> Unit,\n    onRemoveSource: (String) -> Unit,\n) {",
    "    onAddSource: () -> Unit,\n    onRequestInternalAccess: () -> Unit,\n    onRefresh: () -> Unit,\n    onRemoveSource: (String) -> Unit,\n) {",
    1,
)
auto = auto.replace('Text("Носители", fontSize = 30.sp, fontWeight = FontWeight.Bold)',
                    'Text("Источники медиа", fontSize = 30.sp, fontWeight = FontWeight.Bold)', 1)
auto = auto.replace('Text("Подключайте флешки и внешние диски", color = AutoMuted)',
                    'Text("Внутренняя память, папки, флешки и внешние диски", color = AutoMuted)', 1)
auto = auto.replace(
    '''            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onAddSource,
                colors = ButtonDefaults.buttonColors(containerColor = AutoPurple),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить носитель")
            }''',
    '''            IconButton(onClick = onRefresh) {
                Icon(Icons.Rounded.Refresh, contentDescription = "Обновить")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onRequestInternalAccess,
                colors = ButtonDefaults.buttonColors(containerColor = AutoSurfaceHigh),
            ) {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Внутренняя память")
            }
            Spacer(Modifier.width(10.dp))
            Button(
                onClick = onAddSource,
                colors = ButtonDefaults.buttonColors(containerColor = AutoPurple),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Добавить папку или носитель")
            }''',
    1,
)
auto = auto.replace("            EmptySourcesCard(onAddSource)\n",
                    "            EmptySourcesCard(onRequestInternalAccess, onAddSource)\n", 1)
auto = auto.replace(
    '''                        Icon(
                            Icons.Rounded.Usb,
                            contentDescription = null,
                            tint = if (source.connected) AutoGreen else AutoMuted,
                            modifier = Modifier.size(32.dp),
                        )''',
    '''                        Icon(
                            imageVector = if (source.uriString == INTERNAL_SOURCE_URI) {
                                Icons.Rounded.PhoneAndroid
                            } else {
                                Icons.Rounded.Usb
                            },
                            contentDescription = null,
                            tint = if (source.connected) AutoGreen else AutoMuted,
                            modifier = Modifier.size(32.dp),
                        )''',
    1,
)
auto = auto.replace(
    '''                        IconButton(onClick = { onRemoveSource(source.uriString) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Удалить", tint = AutoMuted)
                        }''',
    '''                        if (source.uriString != INTERNAL_SOURCE_URI) {
                            IconButton(onClick = { onRemoveSource(source.uriString) }) {
                                Icon(
                                    Icons.Rounded.DeleteOutline,
                                    contentDescription = "Удалить",
                                    tint = AutoMuted,
                                )
                            }
                        }''',
    1,
)
new_empty_sources = r'''@Composable
private fun EmptySourcesCard(
    onRequestInternalAccess: () -> Unit,
    onAddSource: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(AutoSurfaceHigh)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(
            Icons.Rounded.PhoneAndroid,
            contentDescription = null,
            tint = AutoPurple,
            modifier = Modifier.size(48.dp),
        )
        Spacer(Modifier.height(14.dp))
        Text("Разрешите доступ к медиатеке", fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
        Text(
            "Приложение найдёт видео и аудио во внутренней памяти планшета",
            color = AutoMuted,
        )
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onRequestInternalAccess,
                colors = ButtonDefaults.buttonColors(containerColor = AutoPurple),
            ) {
                Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Разрешить доступ")
            }
            Button(
                onClick = onAddSource,
                colors = ButtonDefaults.buttonColors(containerColor = AutoSurface),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Выбрать папку или носитель")
            }
        }
    }
}
'''
auto, count = re.subn(
    r"@Composable\nprivate fun EmptySourcesCard\([\s\S]*\Z",
    new_empty_sources,
    auto,
    count=1,
)
if count != 1:
    raise RuntimeError("Could not replace EmptySourcesCard")
Path(auto_path).write_text(auto, encoding="utf-8")

# Update the home screen empty state and source icons.
home_path = "app/src/main/java/com/autovideo/app/HomeScreen.kt"
home = Path(home_path).read_text(encoding="utf-8")
home = home.replace(
    "import androidx.compose.material.icons.rounded.PlayArrow\n",
    "import androidx.compose.material.icons.rounded.PhoneAndroid\nimport androidx.compose.material.icons.rounded.PlayArrow\n",
    1,
)
home = home.replace(
    "    onAddSource: () -> Unit,\n    onRefresh: () -> Unit,",
    "    onAddSource: () -> Unit,\n    onRequestInternalAccess: () -> Unit,\n    onRefresh: () -> Unit,",
    1,
)
home = home.replace(
    '"Подключите флешку или внешний диск"',
    '"Разрешите внутреннюю память или подключите носитель"',
    1,
)
home = home.replace("SourceChip(source.name)", "SourceChip(source)", 1)
home = home.replace("NoMediaSource(onAddSource)",
                    "NoMediaSource(onRequestInternalAccess, onAddSource)", 1)
new_source_chip = r'''@Composable
private fun SourceChip(source: RemovableSource) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color(0xFF171126))
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = if (source.uriString == INTERNAL_SOURCE_URI) {
                Icons.Rounded.PhoneAndroid
            } else {
                Icons.Rounded.Usb
            },
            contentDescription = null,
            tint = AutoGreen,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(6.dp))
        Text(source.name, color = AutoMuted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun SectionHeader'''
home, count = re.subn(
    r"@Composable\nprivate fun SourceChip\([\s\S]*?@Composable\nprivate fun SectionHeader",
    new_source_chip,
    home,
    count=1,
)
if count != 1:
    raise RuntimeError("Could not replace SourceChip")
new_no_source = r'''@Composable
private fun NoMediaSource(
    onRequestInternalAccess: () -> Unit,
    onAddSource: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.PhoneAndroid,
                contentDescription = null,
                tint = AutoPurple,
                modifier = Modifier.size(64.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Добавьте медиатеку", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(
                "Разрешите внутреннюю память планшета или выберите любую папку/носитель",
                color = AutoMuted,
            )
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = onRequestInternalAccess,
                    colors = ButtonDefaults.buttonColors(containerColor = AutoPurple),
                ) {
                    Icon(Icons.Rounded.PhoneAndroid, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Внутренняя память")
                }
                Button(
                    onClick = onAddSource,
                    colors = ButtonDefaults.buttonColors(containerColor = AutoSurfaceHigh),
                ) {
                    Icon(Icons.Rounded.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Папка или носитель")
                }
            }
        }
    }
}

private fun currentTime'''
home, count = re.subn(
    r"@Composable\nprivate fun NoMediaSource\([\s\S]*?private fun currentTime",
    new_no_source,
    home,
    count=1,
)
if count != 1:
    raise RuntimeError("Could not replace NoMediaSource")
Path(home_path).write_text(home, encoding="utf-8")

# Bump application version.
build_path = "app/build.gradle.kts"
build = Path(build_path).read_text(encoding="utf-8")
build = build.replace("versionCode = 1", "versionCode = 2", 1)
build = build.replace('versionName = "0.1.0"', 'versionName = "0.2.0"', 1)
Path(build_path).write_text(build, encoding="utf-8")

# Documentation.
readme_path = "README.md"
readme = Path(readme_path).read_text(encoding="utf-8")n
