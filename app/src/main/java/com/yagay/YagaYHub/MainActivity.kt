package com.yagay.YagaYHub

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.Bundle
import android.provider.DocumentsContract
import android.provider.MediaStore
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.zip.ZipInputStream
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.browser.customtabs.CustomTabsIntent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items as lazyItems
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            YagaYHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    HubScreen(this)
                }
            }
        }
    }
}

class ArtifactDownloadService : Service() {
    @Volatile
    private var running = false
    private var worker: Thread? = null

    override fun onCreate() {
        super.onCreate()
        ensureDownloadNotificationChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (running || intent == null) return START_NOT_STICKY

        val request = ArtifactDownloadRequest.fromIntent(intent) ?: return START_NOT_STICKY
        running = true

        val initialState = DownloadUiState(
            appName = request.appName,
            stage = "准备后台下载",
            totalBytes = request.expectedSizeBytes,
            running = true,
        )
        saveDownloadUiState(this, initialState)
        broadcastDownloadState(this, initialState)
        startForeground(
            DOWNLOAD_NOTIFICATION_ID,
            buildDownloadNotification(this, initialState),
        )

        worker = Thread {
            val token = loadGithubToken(this)
            val destinationTreeUri = loadDownloadDirectoryUri(this)
            val rootEnhanced = loadRootCleanupEnabled(this) && hasRootAccess()
            var latestDownloaded = 0L
            var latestTotal = request.expectedSizeBytes

            val result = downloadArtifactZip(
                context = this,
                owner = request.owner,
                repo = request.repo,
                runId = request.runId,
                artifactId = request.artifactId,
                token = token,
                destinationTreeUri = destinationTreeUri,
                rootEnhancedCleanup = rootEnhanced,
                expectedSizeBytes = request.expectedSizeBytes,
                onProgress = { downloaded, total, stage ->
                    latestDownloaded = downloaded
                    latestTotal = total ?: latestTotal
                    val state = DownloadUiState(
                        appName = request.appName,
                        stage = stage,
                        downloadedBytes = downloaded,
                        totalBytes = latestTotal,
                        running = true,
                    )
                    broadcastDownloadState(this, state)
                    updateDownloadNotification(this, state)
                },
            )

            val finalState = DownloadUiState(
                appName = request.appName,
                stage = if (result.success) "下载完成" else "下载失败",
                downloadedBytes = latestDownloaded,
                totalBytes = latestTotal,
                running = false,
                message = result.message,
                apks = result.extractedApks,
            )
            saveDownloadUiState(this, finalState)
            broadcastDownloadState(this, finalState)
            updateDownloadNotification(this, finalState)
            running = false
            stopForeground(STOP_FOREGROUND_DETACH)
            stopSelf(startId)
        }.apply {
            name = "YagaYHub-ArtifactDownload"
            start()
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        worker = null
    }
}

private data class ArtifactDownloadRequest(
    val appName: String,
    val owner: String,
    val repo: String,
    val runId: Long,
    val artifactId: Long,
    val expectedSizeBytes: Long?,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, ArtifactDownloadService::class.java).apply {
            putExtra(EXTRA_DOWNLOAD_APP_NAME, appName)
            putExtra(EXTRA_DOWNLOAD_OWNER, owner)
            putExtra(EXTRA_DOWNLOAD_REPO, repo)
            putExtra(EXTRA_DOWNLOAD_RUN_ID, runId)
            putExtra(EXTRA_DOWNLOAD_ARTIFACT_ID, artifactId)
            expectedSizeBytes?.let { putExtra(EXTRA_DOWNLOAD_EXPECTED_SIZE, it) }
        }

    companion object {
        fun fromIntent(intent: Intent): ArtifactDownloadRequest? {
            val appName = intent.getStringExtra(EXTRA_DOWNLOAD_APP_NAME) ?: return null
            val owner = intent.getStringExtra(EXTRA_DOWNLOAD_OWNER) ?: return null
            val repo = intent.getStringExtra(EXTRA_DOWNLOAD_REPO) ?: return null
            val runId = intent.getLongExtra(EXTRA_DOWNLOAD_RUN_ID, -1L)
            val artifactId = intent.getLongExtra(EXTRA_DOWNLOAD_ARTIFACT_ID, -1L)
            if (runId <= 0L || artifactId <= 0L) return null
            return ArtifactDownloadRequest(
                appName = appName,
                owner = owner,
                repo = repo,
                runId = runId,
                artifactId = artifactId,
                expectedSizeBytes = intent.getLongExtra(
                    EXTRA_DOWNLOAD_EXPECTED_SIZE,
                    -1L,
                ).takeIf { it > 0L },
            )
        }
    }
}

private data class ProjectSpec(
    val name: String,
    val packageName: String,
    val repo: String,
    val description: String,
)

private enum class ActionsStatus(val label: String) {
    LOADING("读取中"),
    SUCCESS("成功"),
    FAILURE("失败"),
    RUNNING("运行中"),
    QUEUED("排队中"),
    CANCELLED("已取消"),
    NONE("无记录"),
    UNKNOWN("未知"),
}

private enum class RootStatus(val label: String) {
    NOT_CHECKED("未检测"),
    AVAILABLE("已授权"),
    UNAVAILABLE("不可用"),
}


private data class HubApp(
    val name: String,
    val packageName: String,
    val repo: String?,
    val repoOwner: String = "yagay",
    val description: String,
    val installed: Boolean,
    val versionName: String?,
    val installedUpdateTime: String? = null,
    val launchIntent: Intent?,
    val icon: Drawable?,
    val autoDiscovered: Boolean = false,
    val repoOnly: Boolean = false,
    val actionsStatus: ActionsStatus = ActionsStatus.NONE,
    val latestRunId: Long? = null,
    val latestActionTime: String? = null,
    val latestArtifactId: Long? = null,
    val latestArtifactSizeBytes: Long? = null,
)

private enum class AppFilter(val label: String) {
    ALL("全部"), INSTALLED("已安装"), NOT_INSTALLED("未安装"), GITHUB("GitHub")
}

private enum class LayoutMode(val label: String) {
    LIST("列表"),
    GRID("网格"),
}

private data class DownloadUiState(
    val appName: String,
    val stage: String = "准备下载",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val running: Boolean = true,
    val message: String? = null,
    val apks: List<ExtractedApk> = emptyList(),
)



private val knownProjects = listOf(
    ProjectSpec("FloatLens", "com.yagay.floatlens", "FloatLens", "悬浮识别、截图与屏幕工具"),
    ProjectSpec("List Cleaner", "com.yagay.ListCleaner", "ListCleaner", "分享面板、组件与列表清理"),
    ProjectSpec("MiniWindowGuard", "com.yagay.MiniWindowGuard", "MiniWindowGuard", "后台播放与小窗增强"),
    ProjectSpec("AIHub", "com.yagay.aihub", "AIHub", "多 AI 统一入口"),
    ProjectSpec("TaskManagerX", "com.rk.taskmanager", "TaskManagerX", "Root / LSPosed 任务管理工具"),
    ProjectSpec("Dual Signal", "com.yagay.dualsignal", "dualsingal", "双排信号状态栏模块"),
    ProjectSpec("ChromeX", "com.yagay.chromex", "ChromeX", "Chrome / Chromium 增强模块"),
    ProjectSpec("NFCExpertPro", "com.yagay.nfcdoorcard", "NFCExpertPro", "NFC 与门禁卡工具"),
    ProjectSpec("GboardHook", "com.chenyue404.gboardhook", "GboardHook", "Gboard 剪贴板增强模块"),
    ProjectSpec("NoOverlayWarning", "com.crossbowffs.nooverlaywarning", "NoOverlayWarning", "LSPosed 屏幕悬浮警告处理模块"),
    ProjectSpec("上班总时间", "com.example.workhours", "work", "工时、工资与假期管理"),
    ProjectSpec("Legado MD3", "io.legato.kazusa", "legado-with-MD3", "阅读 / Legado MD3 项目"),
    ProjectSpec("YBrowser", "com.yagay.YBrowser", "YBrowser", "WebView / GeckoView 双内核浏览器"),
)

@Composable
private fun HubScreen(context: Context) {
    var apps by remember { mutableStateOf(emptyList<HubApp>()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.INSTALLED) }
    var refreshKey by remember { mutableStateOf(0) }
    var showSettings by remember { mutableStateOf(false) }
    var githubToken by remember { mutableStateOf(loadGithubToken(context)) }
    var githubClientId by remember {
        mutableStateOf(
            loadGithubClientId(context).ifBlank {
                BuildConfig.GITHUB_OAUTH_CLIENT_ID
            }
        )
    }
    var githubClientSecret by remember {
        mutableStateOf(
            loadGithubClientSecret(context).ifBlank {
                BuildConfig.GITHUB_OAUTH_CLIENT_SECRET
            }
        )
    }
    var webAuthInProgress by remember { mutableStateOf(false) }
    var deviceAuth by remember { mutableStateOf<DeviceAuthInfo?>(null) }
    var authPolling by remember { mutableStateOf(false) }
    var authStatus by remember { mutableStateOf("") }
    var downloadTreeUri by remember { mutableStateOf(loadDownloadDirectoryUri(context)) }
    var rootCleanupEnabled by remember { mutableStateOf(loadRootCleanupEnabled(context)) }
    var rootStatus by remember { mutableStateOf(RootStatus.NOT_CHECKED) }
    var layoutMode by remember { mutableStateOf(loadLayoutMode(context)) }
    var pendingInstallApk by remember { mutableStateOf<ExtractedApk?>(null) }
    var downloadUiState by remember { mutableStateOf(loadDownloadUiState(context)) }
    var showDownloadPanel by remember {
        mutableStateOf(downloadUiState?.let { !it.running } == true)
    }
    var pendingDownloadRequest by remember { mutableStateOf<ArtifactDownloadRequest?>(null) }
    val scope = rememberCoroutineScope()
    val directoryPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            saveDownloadDirectoryUri(context, uri.toString())
            downloadTreeUri = uri.toString()
            Toast.makeText(context, "下载目录已更新", Toast.LENGTH_SHORT).show()
        }
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        pendingDownloadRequest?.let { request ->
            startArtifactDownloadService(context, request)
            if (!granted) {
                Toast.makeText(
                    context,
                    "通知权限未授予；后台下载仍会继续，但通知栏进度可能不可见",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
        pendingDownloadRequest = null
    }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_DOWNLOAD_STATE) return
                val state = downloadUiStateFromIntent(intent) ?: return
                downloadUiState = state
                if (!state.running) {
                    showDownloadPanel = true
                }
            }
        }
        val filter = IntentFilter(ACTION_DOWNLOAD_STATE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(receiver, filter)
        }
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
        }
    }

    val unknownSourcesLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val pending = pendingInstallApk
        if (pending != null) {
            if (
                Build.VERSION.SDK_INT < Build.VERSION_CODES.O ||
                context.packageManager.canRequestPackageInstalls()
            ) {
                openExtractedApk(context, pending)
                pendingInstallApk = null
            } else {
                Toast.makeText(
                    context,
                    "未授予安装未知应用权限，APK 已保留在下载目录",
                    Toast.LENGTH_LONG,
                ).show()
            }
        }
    }

    LaunchedEffect(rootCleanupEnabled) {
        if (rootCleanupEnabled) {
            rootStatus = withContext(Dispatchers.IO) {
                if (hasRootAccess()) RootStatus.AVAILABLE else RootStatus.UNAVAILABLE
            }
        } else {
            rootStatus = RootStatus.NOT_CHECKED
        }
    }

    LaunchedEffect(refreshKey, githubToken) {
        val loadedApps = loadHubApps(context)
        val mergedApps = withContext(Dispatchers.IO) {
            mergeGithubRepositories(
                apps = loadedApps,
                repositories = fetchOwnedRepositories(githubToken),
            )
        }
        apps = mergedApps
        apps = withContext(Dispatchers.IO) {
            loadActionsStatuses(mergedApps, githubToken)
        }
    }

    val visibleApps = remember(apps, query, filter) {
        val q = query.trim().lowercase()
        apps.filter { app ->
            val filterOk = when (filter) {
                AppFilter.ALL -> true
                AppFilter.INSTALLED -> app.installed
                AppFilter.NOT_INSTALLED -> !app.installed && !app.repoOnly
                AppFilter.GITHUB -> app.repo != null
            }
            val queryOk = q.isBlank() ||
                app.name.lowercase().contains(q) ||
                app.packageName.lowercase().contains(q) ||
                app.description.lowercase().contains(q)
            filterOk && queryOk
        }
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "YagaYHub",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "单击打开 · 长按详情 · Actions 一键构建入口",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextButton(onClick = { openUrl(context, "https://www.google.com/") }) {
                    Text("浏览器")
                }
                if (downloadUiState != null) {
                    TextButton(onClick = { showDownloadPanel = true }) {
                        Text("下载")
                    }
                }
                TextButton(onClick = { showSettings = true }) {
                    Text("设置")
                }
                TextButton(
                    onClick = {
                        layoutMode = if (layoutMode == LayoutMode.LIST) {
                            LayoutMode.GRID
                        } else {
                            LayoutMode.LIST
                        }
                        saveLayoutMode(context, layoutMode)
                    }
                ) {
                    Text(if (layoutMode == LayoutMode.LIST) "网格" else "列表")
                }
                IconButton(onClick = { refreshKey++ }) {
                    Icon(Icons.Outlined.Refresh, contentDescription = "刷新")
                }
            }

            Spacer(Modifier.height(6.dp))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                placeholder = { Text("搜索我的 App") },
                shape = RoundedCornerShape(16.dp),
            )

            Spacer(Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppFilter.entries.forEach { item ->
                    val count = when (item) {
                        AppFilter.ALL -> apps.size
                        AppFilter.INSTALLED -> apps.count { it.installed }
                        AppFilter.NOT_INSTALLED -> apps.count { !it.installed && !it.repoOnly }
                        AppFilter.GITHUB -> apps.count { it.repo != null }
                    }
                    FilterChip(
                        selected = filter == item,
                        onClick = { filter = item },
                        label = { Text("${item.label} $count") },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            if (visibleApps.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("没有匹配的 App", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                val itemContent: @Composable (HubApp) -> Unit = { app ->
                    val onClick: () -> Unit = {
                        when {
                            app.launchIntent != null -> openApp(context, app)
                            app.repo != null -> openUrl(
                                context,
                                "https://github.com/" + app.repoOwner + "/" + app.repo
                            )
                            app.installed -> openAppDetails(context, app.packageName)
                            else -> Unit
                        }
                    }
                    val onLongClick: () -> Unit = {
                        when {
                            app.installed -> openAppDetails(context, app.packageName)
                            app.repo != null -> openUrl(
                                context,
                                "https://github.com/" + app.repoOwner + "/" + app.repo
                            )
                            else -> Unit
                        }
                    }
                    val onActionsClick: () -> Unit = {
                        app.repo?.let { repo ->
                            openUrl(
                                context,
                                "https://github.com/" + app.repoOwner + "/" + repo + "/actions"
                            )
                        }
                        Unit
                    }
                    val onArtifactClick: () -> Unit = {
                        val repo = app.repo
                        val runId = app.latestRunId
                        val artifactId = app.latestArtifactId
                        when {
                            repo == null -> {
                                Toast.makeText(context, "未配置 GitHub 仓库", Toast.LENGTH_SHORT).show()
                            }
                            app.actionsStatus != ActionsStatus.SUCCESS -> {
                                Toast.makeText(context, "最新一次 Actions 未成功，不抓取 ZIP", Toast.LENGTH_SHORT).show()
                            }
                            runId == null || artifactId == null -> {
                                Toast.makeText(context, "最新成功构建没有可下载 ZIP，或产物已过期", Toast.LENGTH_SHORT).show()
                            }
                            githubToken.isBlank() -> {
                                Toast.makeText(context, "请先在设置中保存 GitHub Token", Toast.LENGTH_SHORT).show()
                                showSettings = true
                            }
                            else -> {
                                val request = ArtifactDownloadRequest(
                                    appName = app.name,
                                    owner = app.repoOwner,
                                    repo = repo,
                                    runId = runId,
                                    artifactId = artifactId,
                                    expectedSizeBytes = app.latestArtifactSizeBytes,
                                )
                                if (downloadUiState?.running == true) {
                                    Toast.makeText(
                                        context,
                                        "已有后台下载正在进行",
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                } else if (
                                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                                        PackageManager.PERMISSION_GRANTED
                                ) {
                                    pendingDownloadRequest = request
                                    notificationPermissionLauncher.launch(
                                        Manifest.permission.POST_NOTIFICATIONS
                                    )
                                } else {
                                    startArtifactDownloadService(context, request)
                                }
                                downloadUiState = DownloadUiState(
                                    appName = app.name,
                                    stage = "准备后台下载",
                                    totalBytes = app.latestArtifactSizeBytes,
                                    running = true,
                                )
                                showDownloadPanel = true
                            }
                        }
                    }

                    if (layoutMode == LayoutMode.LIST) {
                        AppListEntry(
                            app = app,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            onActionsClick = onActionsClick,
                            onArtifactClick = onArtifactClick,
                        )
                    } else {
                        AppEntry(
                            app = app,
                            onClick = onClick,
                            onLongClick = onLongClick,
                            onActionsClick = onActionsClick,
                            onArtifactClick = onArtifactClick,
                        )
                    }
                }

                if (layoutMode == LayoutMode.LIST) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        lazyItems(
                            items = visibleApps,
                            key = { it.packageName },
                        ) { app ->
                            itemContent(app)
                        }
                    }
                } else {
                    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
                        val columns = if (maxWidth < 430.dp) 4 else 5
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(columns),
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            items(visibleApps, key = { it.packageName }) { app ->
                                itemContent(app)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showDownloadPanel && downloadUiState != null) {
        DownloadPanel(
            state = downloadUiState!!,
            onDismiss = { showDownloadPanel = false },
            onInstall = { apk ->
                if (
                    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
                    !context.packageManager.canRequestPackageInstalls()
                ) {
                    pendingInstallApk = apk
                    unknownSourcesLauncher.launch(
                        Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:" + context.packageName),
                        )
                    )
                } else {
                    openExtractedApk(context, apk)
                }
            },
        )
    }

    if (showSettings) {
        GitHubSettingsDialog(
            currentToken = githubToken,
            currentClientId = githubClientId,
            currentClientSecret = githubClientSecret,
            webAuthInProgress = webAuthInProgress,
            downloadDirectoryLabel = downloadDirectoryLabel(downloadTreeUri),
            rootCleanupEnabled = rootCleanupEnabled,
            rootStatus = rootStatus,
            onDismiss = { showSettings = false },
            onSaveToken = { token ->
                saveGithubToken(context, token)
                githubToken = token
                showSettings = false
                Toast.makeText(context, "GitHub Token 已保存", Toast.LENGTH_SHORT).show()
            },
            onClearToken = {
                clearGithubToken(context)
                githubToken = ""
                Toast.makeText(context, "GitHub Token 已清除", Toast.LENGTH_SHORT).show()
            },
            onChooseDownloadDirectory = {
                directoryPicker.launch(null)
            },
            onResetDownloadDirectory = {
                clearDownloadDirectoryUri(context)
                downloadTreeUri = ""
                Toast.makeText(
                    context,
                    "已恢复默认目录 Downloads/YagaYHub",
                    Toast.LENGTH_SHORT
                ).show()
            },
            onRootCleanupChanged = { enabled ->
                rootCleanupEnabled = enabled
                saveRootCleanupEnabled(context, enabled)
                if (enabled) {
                    rootStatus = RootStatus.NOT_CHECKED
                    scope.launch {
                        rootStatus = withContext(Dispatchers.IO) {
                            if (hasRootAccess()) {
                                RootStatus.AVAILABLE
                            } else {
                                RootStatus.UNAVAILABLE
                            }
                        }
                        if (rootStatus == RootStatus.UNAVAILABLE) {
                            Toast.makeText(
                                context,
                                "未获得 Root，下载时将自动回退普通清理",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                } else {
                    rootStatus = RootStatus.NOT_CHECKED
                }
            },
            onWebLogin = { clientId, clientSecret ->
                saveGithubClientId(context, clientId)
                saveGithubClientSecret(context, clientSecret)
                githubClientId = clientId
                githubClientSecret = clientSecret
                showSettings = false
                webAuthInProgress = true
                scope.launch {
                    val session = withContext(Dispatchers.IO) {
                        createGithubWebAuthSession()
                    }
                    if (session == null) {
                        webAuthInProgress = false
                        Toast.makeText(
                            context,
                            "无法启动 GitHub 登录回调",
                            Toast.LENGTH_LONG,
                        ).show()
                    } else {
                        val authorizeUrl = buildGithubAuthorizeUrl(
                            clientId = clientId,
                            session = session,
                        )
                        try {
                            CustomTabsIntent.Builder()
                                .setShowTitle(true)
                                .build()
                                .launchUrl(context, Uri.parse(authorizeUrl))

                            val token: String? = withContext(Dispatchers.IO) {
                                completeGithubWebAuth(
                                    session = session,
                                    clientId = clientId,
                                    clientSecret = clientSecret,
                                )
                            }
                            webAuthInProgress = false
                            if (token.isNullOrBlank()) {
                                Toast.makeText(
                                    context,
                                    "GitHub 网页授权失败或已取消",
                                    Toast.LENGTH_LONG,
                                ).show()
                            } else {
                                saveGithubToken(context, token)
                                githubToken = token
                                refreshKey++
                                Toast.makeText(
                                    context,
                                    "GitHub 登录授权成功",
                                    Toast.LENGTH_LONG,
                                ).show()
                            }
                        } catch (_: Exception) {
                            session.close()
                            webAuthInProgress = false
                            Toast.makeText(
                                context,
                                "无法打开 GitHub 登录界面",
                                Toast.LENGTH_LONG,
                            ).show()
                        }
                    }
                }
            },
            onAuthorize = { clientId ->
                saveGithubClientId(context, clientId)
                githubClientId = clientId
                showSettings = false
                scope.launch {
                    val info = withContext(Dispatchers.IO) {
                        requestGithubDeviceCode(clientId)
                    }
                    if (info == null) {
                        Toast.makeText(
                            context,
                            "无法开始 GitHub 账号授权，请检查 Client ID 和 Device Flow 设置",
                            Toast.LENGTH_LONG
                        ).show()
                    } else {
                        deviceAuth = info
                        authStatus = "等待打开 GitHub 授权"
                    }
                }
            },
        )
    }

    deviceAuth?.let { info ->
        GithubDeviceAuthDialog(
            info = info,
            polling = authPolling,
            status = authStatus,
            onDismiss = {
                if (!authPolling) {
                    deviceAuth = null
                    authStatus = ""
                }
            },
            onOpenGithub = {
                copyText(context, info.userCode)
                openUrl(context, info.verificationUri)
                if (!authPolling) {
                    authPolling = true
                    authStatus = "等待 GitHub 授权…"
                    scope.launch {
                        val token = withContext(Dispatchers.IO) {
                            pollGithubDeviceToken(githubClientId, info)
                        }
                        authPolling = false
                        if (token.isNullOrBlank()) {
                            authStatus = "授权失败或已超时"
                        } else {
                            saveGithubToken(context, token)
                            githubToken = token
                            deviceAuth = null
                            authStatus = ""
                            Toast.makeText(
                                context,
                                "GitHub 账号授权成功",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
            },
        )
    }
}

@Composable
private fun GitHubSettingsDialog(
    currentToken: String,
    currentClientId: String,
    currentClientSecret: String,
    webAuthInProgress: Boolean,
    downloadDirectoryLabel: String,
    rootCleanupEnabled: Boolean,
    rootStatus: RootStatus,
    onDismiss: () -> Unit,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onChooseDownloadDirectory: () -> Unit,
    onResetDownloadDirectory: () -> Unit,
    onRootCleanupChanged: (Boolean) -> Unit,
    onWebLogin: (String, String) -> Unit,
    onAuthorize: (String) -> Unit,
) {
    var token by remember(currentToken) { mutableStateOf(currentToken) }
    var clientId by remember(currentClientId) { mutableStateOf(currentClientId) }
    var clientSecret by remember(currentClientSecret) {
        mutableStateOf(currentClientSecret)
    }
    var showGithubAdvanced by remember {
        mutableStateOf(false)
    }

    val githubAppConfigured =
        clientId.isNotBlank() && clientSecret.isNotBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub 设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "推荐使用 GitHub 网页登录授权。GitHub App Callback URL 请设置为 http://127.0.0.1/oauth/callback；Device Flow 和 Fine-grained Token 仍作为备用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "下载目录：" + downloadDirectoryLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = onChooseDownloadDirectory) {
                        Text("选择目录")
                    }
                    TextButton(onClick = onResetDownloadDirectory) {
                        Text("恢复默认")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Root 增强清理")
                        Text(
                            "状态：" + rootStatus.label + " · 定向删除旧 ZIP / 重复项 / 临时残留",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = rootCleanupEnabled,
                        onCheckedChange = onRootCleanupChanged,
                    )
                }
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    tonalElevation = 1.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            if (currentToken.isNotBlank()) {
                                "GitHub 账号：已登录"
                            } else if (githubAppConfigured) {
                                "GitHub 账号：未登录"
                            } else {
                                "GitHub 登录配置未内置"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )

                        TextButton(
                            onClick = {
                                onWebLogin(
                                    clientId.trim(),
                                    clientSecret.trim(),
                                )
                            },
                            enabled = githubAppConfigured && !webAuthInProgress,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (webAuthInProgress) {
                                    "正在登录 GitHub…"
                                } else {
                                    if (currentToken.isNotBlank()) {
                                        "切换 GitHub 账号"
                                    } else {
                                        "登录 GitHub"
                                    }
                                }
                            )
                        }

                        TextButton(
                            onClick = {
                                showGithubAdvanced = !showGithubAdvanced
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(
                                if (showGithubAdvanced) {
                                    "收起高级设置"
                                } else {
                                    "高级设置"
                                }
                            )
                        }
                    }
                }

                if (showGithubAdvanced) {
                    OutlinedTextField(
                        value = clientId,
                        onValueChange = { clientId = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("GitHub App Client ID") },
                        placeholder = { Text("Iv1.…") },
                    )
                    OutlinedTextField(
                        value = clientSecret,
                        onValueChange = { clientSecret = it.trim() },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("GitHub App Client Secret") },
                        placeholder = { Text("仅加密保存在本机") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                    TextButton(
                        onClick = {
                            onWebLogin(
                                clientId.trim(),
                                clientSecret.trim(),
                            )
                        },
                        enabled = githubAppConfigured && !webAuthInProgress,
                    ) {
                        Text(
                            if (
                                currentClientId.isBlank() ||
                                currentClientSecret.isBlank()
                            ) {
                                "保存配置并登录"
                            } else {
                                "使用当前配置重新登录"
                            }
                        )
                    }
                    TextButton(
                        onClick = { onAuthorize(clientId.trim()) },
                        enabled = clientId.isNotBlank() && !webAuthInProgress,
                    ) {
                        Text("Device Flow（备用）")
                    }
                    OutlinedTextField(
                        value = token,
                        onValueChange = { token = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text("Fine-grained token（备用）") },
                        placeholder = { Text("github_pat_…") },
                        visualTransformation = PasswordVisualTransformation(),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSaveToken(token.trim()) },
                enabled = token.isNotBlank(),
            ) {
                Text("保存 Token")
            }
        },
        dismissButton = {
            Row {
                if (currentToken.isNotBlank()) {
                    TextButton(onClick = onClearToken) {
                        Text("清除 Token")
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("关闭")
                }
            }
        },
    )
}

private fun startArtifactDownloadService(
    context: Context,
    request: ArtifactDownloadRequest,
) {
    val state = DownloadUiState(
        appName = request.appName,
        stage = "准备后台下载",
        totalBytes = request.expectedSizeBytes,
        running = true,
    )
    saveDownloadUiState(context, state)
    ContextCompat.startForegroundService(context, request.toIntent(context))
}

private fun ensureDownloadNotificationChannel(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
    val manager = context.getSystemService(NotificationManager::class.java)
    manager.createNotificationChannel(
        NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "YagaYHub 下载",
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = "GitHub Actions artifact 后台下载进度"
            setShowBadge(false)
        }
    )
}

private fun buildDownloadNotification(
    context: Context,
    state: DownloadUiState,
): Notification {
    val openAppIntent = Intent(context, MainActivity::class.java).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        1001,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )
    val total = state.totalBytes?.takeIf { it > 0L }
    val progressPercent = if (total != null) {
        ((state.downloadedBytes * 100L) / total)
            .coerceIn(0L, 100L)
            .toInt()
    } else {
        0
    }
    val sizeText = if (total != null) {
        formatFileSize(state.downloadedBytes) + " / " + formatFileSize(total)
    } else if (state.downloadedBytes > 0L) {
        formatFileSize(state.downloadedBytes)
    } else {
        ""
    }
    val contentText = buildString {
        append(state.stage)
        if (sizeText.isNotBlank()) {
            append(" · ")
            append(sizeText)
        }
    }

    return Notification.Builder(context, DOWNLOAD_CHANNEL_ID)
        .setSmallIcon(
            if (state.running) {
                android.R.drawable.stat_sys_download
            } else {
                android.R.drawable.stat_sys_download_done
            }
        )
        .setContentTitle("YagaYHub · " + state.appName)
        .setContentText(contentText)
        .setContentIntent(pendingIntent)
        .setOnlyAlertOnce(true)
        .setOngoing(state.running)
        .setAutoCancel(!state.running)
        .apply {
            when {
                state.running && total != null ->
                    setProgress(100, progressPercent, false)
                state.running ->
                    setProgress(0, 0, true)
                else ->
                    setProgress(0, 0, false)
            }
        }
        .build()
}

private fun updateDownloadNotification(
    context: Context,
    state: DownloadUiState,
) {
    context.getSystemService(NotificationManager::class.java)
        .notify(DOWNLOAD_NOTIFICATION_ID, buildDownloadNotification(context, state))
}

private fun broadcastDownloadState(
    context: Context,
    state: DownloadUiState,
) {
    context.sendBroadcast(
        Intent(ACTION_DOWNLOAD_STATE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_STATE_APP_NAME, state.appName)
            putExtra(EXTRA_STATE_STAGE, state.stage)
            putExtra(EXTRA_STATE_DOWNLOADED, state.downloadedBytes)
            state.totalBytes?.let { putExtra(EXTRA_STATE_TOTAL, it) }
            putExtra(EXTRA_STATE_RUNNING, state.running)
            state.message?.let { putExtra(EXTRA_STATE_MESSAGE, it) }
            putStringArrayListExtra(
                EXTRA_STATE_APK_NAMES,
                ArrayList(state.apks.map { it.name }),
            )
            putStringArrayListExtra(
                EXTRA_STATE_APK_URIS,
                ArrayList(state.apks.map { it.uri.toString() }),
            )
        }
    )
}

private fun downloadUiStateFromIntent(intent: Intent): DownloadUiState? {
    val appName = intent.getStringExtra(EXTRA_STATE_APP_NAME) ?: return null
    val names = intent.getStringArrayListExtra(EXTRA_STATE_APK_NAMES).orEmpty()
    val uris = intent.getStringArrayListExtra(EXTRA_STATE_APK_URIS).orEmpty()
    val apks = names.zip(uris).map { (name, uri) ->
        ExtractedApk(name, Uri.parse(uri))
    }
    return DownloadUiState(
        appName = appName,
        stage = intent.getStringExtra(EXTRA_STATE_STAGE).orEmpty(),
        downloadedBytes = intent.getLongExtra(EXTRA_STATE_DOWNLOADED, 0L),
        totalBytes = intent.getLongExtra(EXTRA_STATE_TOTAL, -1L).takeIf { it > 0L },
        running = intent.getBooleanExtra(EXTRA_STATE_RUNNING, false),
        message = intent.getStringExtra(EXTRA_STATE_MESSAGE),
        apks = apks,
    )
}

private fun saveDownloadUiState(context: Context, state: DownloadUiState) {
    val json = JSONObject().apply {
        put("appName", state.appName)
        put("stage", state.stage)
        put("downloadedBytes", state.downloadedBytes)
        put("totalBytes", state.totalBytes ?: JSONObject.NULL)
        put("running", state.running)
        put("message", state.message ?: JSONObject.NULL)
        put(
            "apks",
            JSONArray().apply {
                state.apks.forEach { apk ->
                    put(
                        JSONObject()
                            .put("name", apk.name)
                            .put("uri", apk.uri.toString())
                    )
                }
            }
        )
    }
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(DOWNLOAD_STATE_JSON, json.toString())
        .apply()
}

private fun loadDownloadUiState(context: Context): DownloadUiState? {
    val raw = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getString(DOWNLOAD_STATE_JSON, null)
        ?: return null
    return runCatching {
        val json = JSONObject(raw)
        val apkArray = json.optJSONArray("apks") ?: JSONArray()
        val apks = buildList {
            for (index in 0 until apkArray.length()) {
                val item = apkArray.getJSONObject(index)
                val name = item.optString("name")
                val uri = item.optString("uri")
                if (name.isNotBlank() && uri.isNotBlank()) {
                    add(ExtractedApk(name, Uri.parse(uri)))
                }
            }
        }
        DownloadUiState(
            appName = json.getString("appName"),
            stage = json.optString("stage"),
            downloadedBytes = json.optLong("downloadedBytes", 0L),
            totalBytes = if (json.isNull("totalBytes")) null else json.optLong("totalBytes"),
            running = json.optBoolean("running", false),
            message = if (json.isNull("message")) null else json.optString("message"),
            apks = apks,
        )
    }.getOrNull()
}

@Composable
private fun DownloadPanel(
    state: DownloadUiState,
    onDismiss: () -> Unit,
    onInstall: (ExtractedApk) -> Unit,
) {
    val total = state.totalBytes?.takeIf { it > 0L }
    val progress = if (total != null) {
        (state.downloadedBytes.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    } else {
        null
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载 · " + state.appName) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    state.stage,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )

                if (state.running) {
                    if (progress != null) {
                        LinearProgressIndicator(
                            progress = progress,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text(
                            (progress * 100).toInt().toString() + "% · " +
                                formatFileSize(state.downloadedBytes) + " / " +
                                formatFileSize(total ?: state.downloadedBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        Text(
                            formatFileSize(state.downloadedBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }

                state.message?.let { message ->
                    Text(
                        message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (state.apks.isNotEmpty()) {
                    Text(
                        "APK 选择列表",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                    )
                    state.apks.forEach { apk ->
                        Surface(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(10.dp),
                            tonalElevation = 1.dp,
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        apk.name,
                                        style = MaterialTheme.typography.bodyMedium,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                    if (apk == choosePrimaryApk(state.apks)) {
                                        Text(
                                            "推荐主 APK",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                }
                                TextButton(onClick = { onInstall(apk) }) {
                                    Text("Install")
                                }
                            }
                        }
                    }
                } else if (!state.running && state.message != null) {
                    Text(
                        "没有可安装的 APK",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(if (state.running) "后台运行" else "关闭")
            }
        },
    )
}

@Composable
private fun GithubDeviceAuthDialog(
    info: DeviceAuthInfo,
    polling: Boolean,
    status: String,
    onDismiss: () -> Unit,
    onOpenGithub: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub 账号授权") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("授权码")
                Text(
                    info.userCode,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    if (status.isBlank()) "复制授权码并打开 GitHub 完成授权。" else status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = onOpenGithub,
                enabled = !polling,
            ) {
                Text(if (polling) "等待授权…" else "复制并打开 GitHub")
            }
        },
        dismissButton = {
            if (!polling) {
                TextButton(onClick = onDismiss) {
                    Text("取消")
                }
            }
        },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppListEntry(
    app: HubApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onActionsClick: () -> Unit,
    onArtifactClick: () -> Unit,
) {
    val hasNewerActions = hasNewerActionsBuild(app)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(14.dp),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box {
                AppIcon(app)
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(13.dp)
                        .clip(CircleShape)
                        .background(
                            when {
                                app.repoOnly -> MaterialTheme.colorScheme.secondary
                                !app.installed -> MaterialTheme.colorScheme.outline
                                app.launchIntent == null -> MaterialTheme.colorScheme.tertiary
                                else -> MaterialTheme.colorScheme.primary
                            }
                        )
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    app.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        app.repoOnly -> "GitHub 项目"
                        !app.installed -> "未安装"
                        app.launchIntent == null && app.versionName.isNullOrBlank() -> "模块"
                        app.versionName.isNullOrBlank() -> "已安装"
                        app.launchIntent == null -> "模块 · " + app.versionName
                        else -> "版本 " + app.versionName
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (!app.installed && !app.repoOnly) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (!app.installed && !app.repoOnly) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                app.installedUpdateTime?.let { updateTime ->
                    Text(
                        "本机更新  " + updateTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
                app.latestActionTime?.let { actionTime ->
                    Text(
                        "Actions   " + actionTime,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasNewerActions) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (hasNewerActions) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 2,
                    )
                }
            }

            if (app.repo != null) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onActionsClick)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "GitHub Actions",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        !app.installed && !app.repoOnly ->
                                            MaterialTheme.colorScheme.error
                                        app.actionsStatus == ActionsStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                        app.actionsStatus == ActionsStatus.FAILURE -> MaterialTheme.colorScheme.error
                                        app.actionsStatus == ActionsStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                                        app.actionsStatus == ActionsStatus.QUEUED -> MaterialTheme.colorScheme.secondary
                                        app.actionsStatus == ActionsStatus.CANCELLED -> MaterialTheme.colorScheme.outline
                                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                        )
                        Text(
                            if (!app.installed && !app.repoOnly) {
                                "未安装"
                            } else {
                                app.actionsStatus.label
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = when {
                                !app.installed && !app.repoOnly ->
                                    MaterialTheme.colorScheme.error
                                app.actionsStatus == ActionsStatus.FAILURE ->
                                    MaterialTheme.colorScheme.error
                                else ->
                                    MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (!app.installed && !app.repoOnly) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Normal
                            },
                            maxLines = 1,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(onClick = onArtifactClick)
                            .padding(horizontal = 6.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "下载最新成功构建 ZIP",
                            modifier = Modifier.size(17.dp),
                            tint = if (app.latestArtifactId != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outline
                            },
                        )
                        Text(
                            app.latestArtifactSizeBytes?.let(::formatFileSize) ?: "ZIP",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppEntry(
    app: HubApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onActionsClick: () -> Unit,
    onArtifactClick: () -> Unit,
) {
    val hasNewerActions = hasNewerActionsBuild(app)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            )
            .padding(horizontal = 2.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box {
            AppIcon(app)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(13.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            app.repoOnly -> MaterialTheme.colorScheme.secondary
                            !app.installed -> MaterialTheme.colorScheme.outline
                            app.launchIntent == null -> MaterialTheme.colorScheme.tertiary
                            else -> MaterialTheme.colorScheme.primary
                        }
                    )
            )
        }

        Spacer(Modifier.height(6.dp))
        Text(
            app.name,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(
            when {
                app.repoOnly -> "GitHub 项目"
                !app.installed -> "未安装"
                app.launchIntent == null -> "模块"
                app.versionName.isNullOrBlank() -> "已安装"
                else -> app.versionName
            },
            style = MaterialTheme.typography.labelSmall,
            color = if (!app.installed && !app.repoOnly) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontWeight = if (!app.installed && !app.repoOnly) {
                FontWeight.SemiBold
            } else {
                FontWeight.Normal
            },
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
        app.installedUpdateTime?.let { updateTime ->
            Text(
                "本机更新 " + updateTime,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (app.repo != null) {
            Spacer(Modifier.height(2.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onActionsClick)
                        .padding(horizontal = 4.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    Icon(
                        Icons.Outlined.PlayArrow,
                        contentDescription = "GitHub Actions",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .clip(CircleShape)
                            .background(
                                when (app.actionsStatus) {
                                    ActionsStatus.SUCCESS -> MaterialTheme.colorScheme.primary
                                    ActionsStatus.FAILURE -> MaterialTheme.colorScheme.error
                                    ActionsStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
                                    ActionsStatus.QUEUED -> MaterialTheme.colorScheme.secondary
                                    ActionsStatus.CANCELLED -> MaterialTheme.colorScheme.outline
                                    ActionsStatus.LOADING,
                                    ActionsStatus.NONE,
                                    ActionsStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            ),
                    )
                    Text(
                        if (!app.installed && !app.repoOnly) {
                            "未安装"
                        } else {
                            app.actionsStatus.label
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = when {
                            !app.installed && !app.repoOnly ->
                                MaterialTheme.colorScheme.error
                            app.actionsStatus == ActionsStatus.FAILURE ->
                                MaterialTheme.colorScheme.error
                            else ->
                                MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        fontWeight = if (!app.installed && !app.repoOnly) {
                            FontWeight.SemiBold
                        } else {
                            FontWeight.Normal
                        },
                        maxLines = 1,
                    )
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onArtifactClick)
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = "下载最新成功构建 ZIP",
                        modifier = Modifier.size(18.dp),
                        tint = if (app.latestArtifactId != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        },
                    )
                    app.latestArtifactSizeBytes?.let { sizeBytes ->
                        Text(
                            formatFileSize(sizeBytes),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }
            app.latestActionTime?.let { actionTime ->
                Text(
                    "Actions " + actionTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (hasNewerActions) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (hasNewerActions) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun AppIcon(app: HubApp) {
    val bitmap = remember(app.icon) { app.icon?.toImageBitmap() }
    if (bitmap != null) {
        Image(
            painter = BitmapPainter(bitmap),
            contentDescription = app.name,
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(15.dp)),
        )
    } else {
        Box(
            modifier = Modifier
                .size(58.dp)
                .clip(RoundedCornerShape(15.dp))
                .background(MaterialTheme.colorScheme.secondaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                app.name.firstOrNull()?.uppercase() ?: "?",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
        }
    }
}

private fun loadHubApps(context: Context): List<HubApp> {
    val pm = context.packageManager
    val apps = knownProjects.map { loadKnownApp(pm, it) }.toMutableList()
    val existing = apps.mapTo(mutableSetOf()) { it.packageName }
    val launcherIntent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    val resolved = if (Build.VERSION.SDK_INT >= 33) {
        pm.queryIntentActivities(launcherIntent, PackageManager.ResolveInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.queryIntentActivities(launcherIntent, 0)
    }

    resolved.asSequence()
        .mapNotNull { it.activityInfo?.packageName }
        .distinct()
        .filter {
            it != context.packageName &&
                it !in existing &&
                it.startsWith("com.yagay.", ignoreCase = true)
        }
        .forEach { pkg ->
            val info = getPackageInfoCompat(pm, pkg)
            val applicationInfo = info?.applicationInfo
            val label = runCatching { applicationInfo?.loadLabel(pm)?.toString() }.getOrNull()
                ?.takeIf { it.isNotBlank() }
                ?: pkg.substringAfterLast('.')
            apps += HubApp(
                name = label,
                packageName = pkg,
                repo = null,
                description = "自动发现的 YagaY 应用",
                installed = info != null,
                versionName = info?.versionName,
                installedUpdateTime = info?.lastUpdateTime
                    ?.takeIf { it > 0L }
                    ?.let(::formatLocalTime),
                launchIntent = pm.getLaunchIntentForPackage(pkg),
                icon = runCatching { applicationInfo?.loadIcon(pm) }.getOrNull(),
                autoDiscovered = true,
            )
        }

    return apps.sortedWith(
        compareByDescending<HubApp> { it.installed }
            .thenByDescending { it.launchIntent != null }
            .thenBy { it.name.lowercase() }
    )
}

private fun loadKnownApp(pm: PackageManager, spec: ProjectSpec): HubApp {
    val info = getPackageInfoCompat(pm, spec.packageName)
    val applicationInfo = info?.applicationInfo
    val installedName = runCatching { applicationInfo?.loadLabel(pm)?.toString() }.getOrNull()
        ?.takeIf { it.isNotBlank() }
    return HubApp(
        name = installedName ?: spec.name,
        packageName = spec.packageName,
        repo = spec.repo,
        description = spec.description,
        installed = info != null,
        versionName = info?.versionName,
        installedUpdateTime = info?.lastUpdateTime
            ?.takeIf { it > 0L }
            ?.let(::formatLocalTime),
        launchIntent = if (info != null) pm.getLaunchIntentForPackage(spec.packageName) else null,
        icon = runCatching { applicationInfo?.loadIcon(pm) }.getOrNull(),
        actionsStatus = ActionsStatus.LOADING,
    )
}

private data class GithubRepository(
    val owner: String,
    val name: String,
    val description: String?,
    val isPrivate: Boolean,
)

private fun fetchOwnedRepositories(token: String): List<GithubRepository> {
    val repositories = linkedMapOf<String, GithubRepository>()

    fun loadPages(baseUrl: String, authToken: String) {
        for (page in 1..10) {
            var connection: HttpURLConnection? = null
            try {
                val separator = if ("?" in baseUrl) "&" else "?"
                connection = githubGet(
                    baseUrl + separator + "per_page=100&page=" + page,
                    token = authToken,
                )
                if (connection.responseCode !in 200..299) break
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                if (array.length() == 0) break

                for (index in 0 until array.length()) {
                    val item = array.getJSONObject(index)
                    val owner = item.optJSONObject("owner")?.optString("login").orEmpty()
                    val name = item.optString("name")
                    if (owner.isBlank() || name.isBlank()) continue
                    val key = (owner + "/" + name).lowercase()
                    repositories[key] = GithubRepository(
                        owner = owner,
                        name = name,
                        description = item.optString("description")
                            .takeIf { it.isNotBlank() && it != "null" },
                        isPrivate = item.optBoolean("private", false),
                    )
                }

                if (array.length() < 100) break
            } catch (_: Exception) {
                break
            } finally {
                connection?.disconnect()
            }
        }
    }

    loadPages("https://api.github.com/users/yagay/repos?sort=updated", "")

    if (token.isNotBlank()) {
        loadPages(
            "https://api.github.com/user/repos?affiliation=owner&sort=updated",
            token,
        )
    }

    return repositories.values.toList()
}

private fun mergeGithubRepositories(
    apps: List<HubApp>,
    repositories: List<GithubRepository>,
): List<HubApp> {
    val result = apps.toMutableList()
    val existingRepos = apps.mapNotNull { app ->
        val repo = app.repo ?: return@mapNotNull null
        (app.repoOwner + "/" + repo).lowercase()
    }.toMutableSet()

    repositories.forEach { repository ->
        val key = (repository.owner + "/" + repository.name).lowercase()
        if (key in existingRepos) return@forEach

        val matchingAppIndex = result.indexOfFirst { app ->
            app.repo == null && (
                app.name.equals(repository.name, ignoreCase = true) ||
                    app.packageName.substringAfterLast('.')
                        .equals(repository.name, ignoreCase = true)
            )
        }

        if (matchingAppIndex >= 0) {
            val app = result[matchingAppIndex]
            result[matchingAppIndex] = app.copy(
                repo = repository.name,
                repoOwner = repository.owner,
                description = repository.description ?: app.description,
                actionsStatus = ActionsStatus.LOADING,
            )
        } else {
            result += HubApp(
                name = repository.name,
                packageName = "github:" + repository.owner + "/" + repository.name,
                repo = repository.name,
                repoOwner = repository.owner,
                description = repository.description
                    ?: if (repository.isPrivate) "GitHub 私有项目" else "GitHub 项目",
                installed = false,
                versionName = null,
                launchIntent = null,
                icon = null,
                repoOnly = true,
                actionsStatus = ActionsStatus.LOADING,
            )
        }
        existingRepos += key
    }

    return result.sortedWith(
        compareByDescending<HubApp> { it.installed }
            .thenBy { it.repoOnly }
            .thenBy { it.name.lowercase() }
    )
}

private data class LatestActionsInfo(
    val status: ActionsStatus,
    val runId: Long?,
    val actionTime: String?,
    val artifactId: Long?,
    val artifactSizeBytes: Long?,
)

private suspend fun loadActionsStatuses(
    apps: List<HubApp>,
    token: String,
): List<HubApp> = coroutineScope {
    apps.map { app ->
        async {
            if (app.repo == null || (token.isBlank() && app.repoOnly)) {
                if (app.repoOnly && token.isBlank()) {
                    app.copy(actionsStatus = ActionsStatus.UNKNOWN)
                } else {
                    app
                }
            } else {
                val info = fetchLatestActionsInfo(app.repoOwner, app.repo, token)
                app.copy(
                    actionsStatus = info.status,
                    latestRunId = info.runId,
                    latestActionTime = info.actionTime,
                    latestArtifactId = info.artifactId,
                    latestArtifactSizeBytes = info.artifactSizeBytes,
                )
            }
        }
    }.awaitAll()
}

private fun fetchLatestActionsInfo(owner: String, repo: String, token: String): LatestActionsInfo {
    var connection: HttpURLConnection? = null
    return try {
        connection = githubGet(
            "https://api.github.com/repos/" + owner + "/" + repo + "/actions/runs?per_page=1",
            token = token,
        )
        if (connection.responseCode !in 200..299) {
            return LatestActionsInfo(ActionsStatus.UNKNOWN, null, null, null, null)
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val runs = JSONObject(body).optJSONArray("workflow_runs")
        if (runs == null || runs.length() == 0) {
            return LatestActionsInfo(ActionsStatus.NONE, null, null, null, null)
        }

        val run = runs.getJSONObject(0)
        val status = mapActionsStatus(run)
        val runId = run.optLong("id").takeIf { it > 0L }
        val actionTime = formatActionsTime(
            run.optString("run_started_at").ifBlank {
                run.optString("created_at").ifBlank { run.optString("updated_at") }
            }
        )

        // 只处理最新一次 Actions：只有最新一次成功，才继续请求 artifact。
        val artifactInfo = if (status == ActionsStatus.SUCCESS && runId != null) {
            fetchLatestArtifactInfo(owner, repo, runId, token)
        } else {
            null
        }

        LatestActionsInfo(
            status = status,
            runId = runId,
            actionTime = actionTime,
            artifactId = artifactInfo?.first,
            artifactSizeBytes = artifactInfo?.second,
        )
    } catch (_: Exception) {
        LatestActionsInfo(ActionsStatus.UNKNOWN, null, null, null, null)
    } finally {
        connection?.disconnect()
    }
}

private fun fetchLatestArtifactInfo(
    owner: String,
    repo: String,
    runId: Long,
    token: String,
): Pair<Long, Long>? {
    var connection: HttpURLConnection? = null
    return try {
        connection = githubGet(
            "https://api.github.com/repos/" + owner + "/" + repo +
                "/actions/runs/" + runId + "/artifacts?per_page=100",
            token = token,
        )
        if (connection.responseCode !in 200..299) return null

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val artifacts = JSONObject(body).optJSONArray("artifacts") ?: return null

        for (index in 0 until artifacts.length()) {
            val artifact = artifacts.getJSONObject(index)
            if (!artifact.optBoolean("expired", true)) {
                val id = artifact.optLong("id")
                val sizeBytes = artifact.optLong("size_in_bytes").coerceAtLeast(0L)
                if (id > 0L) return id to sizeBytes
            }
        }
        null
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private val actionsTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

private fun hasNewerActionsBuild(app: HubApp): Boolean {
    if (!app.installed || app.repoOnly) return false
    val installedTime = app.installedUpdateTime ?: return false
    val actionsTime = app.latestActionTime ?: return false

    return runCatching {
        val installed = LocalDateTime.parse(installedTime, actionsTimeFormatter)
        val actions = LocalDateTime.parse(actionsTime, actionsTimeFormatter)
        actions.isAfter(installed)
    }.getOrDefault(false)
}

private fun formatLocalTime(epochMillis: Long): String =
    Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(actionsTimeFormatter)

private fun formatActionsTime(value: String): String? {
    if (value.isBlank()) return null
    return runCatching {
        Instant.parse(value)
            .atZone(ZoneId.systemDefault())
            .format(actionsTimeFormatter)
    }.getOrNull()
}

private fun formatFileSize(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024.0) return String.format("%.1f MB", mb)
    val gb = mb / 1024.0
    return String.format("%.2f GB", gb)
}

private fun mapActionsStatus(run: JSONObject): ActionsStatus =
    when (run.optString("status")) {
        "queued", "waiting", "requested", "pending" -> ActionsStatus.QUEUED
        "in_progress" -> ActionsStatus.RUNNING
        "completed" -> when (run.optString("conclusion")) {
            "success" -> ActionsStatus.SUCCESS
            "failure", "timed_out", "action_required", "stale" -> ActionsStatus.FAILURE
            "cancelled", "skipped", "neutral" -> ActionsStatus.CANCELLED
            else -> ActionsStatus.UNKNOWN
        }
        else -> ActionsStatus.UNKNOWN
    }

private fun githubGet(
    url: String,
    token: String = "",
    followRedirects: Boolean = true,
): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 10_000
        readTimeout = 30_000
        instanceFollowRedirects = followRedirects
        useCaches = false
        defaultUseCaches = false
        setRequestProperty("Cache-Control", "no-store, no-cache")
        setRequestProperty("Pragma", "no-cache")
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "YagaYHub")
        setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        if (token.isNotBlank()) {
            setRequestProperty("Authorization", "Bearer " + token)
        }
    }

private data class ExtractedApk(
    val name: String,
    val uri: Uri,
)

private data class DownloadResult(
    val success: Boolean,
    val message: String,
    val extractedApks: List<ExtractedApk> = emptyList(),
)

private fun downloadArtifactZip(
    context: Context,
    owner: String,
    repo: String,
    runId: Long,
    artifactId: Long,
    token: String,
    destinationTreeUri: String,
    rootEnhancedCleanup: Boolean,
    expectedSizeBytes: Long? = null,
    onProgress: (Long, Long?, String) -> Unit = { _, _, _ -> },
): DownloadResult {
    var apiConnection: HttpURLConnection? = null
    var downloadConnection: HttpURLConnection? = null
    var outputUri: Uri? = null

    return try {
        onProgress(0L, expectedSizeBytes, "连接 GitHub…")
        apiConnection = githubGet(
            url = "https://api.github.com/repos/" + owner + "/" + repo +
                "/actions/artifacts/" + artifactId + "/zip",
            token = token,
            followRedirects = false,
        )

        val apiCode = apiConnection.responseCode
        val streamConnection = when {
            apiCode in 300..399 -> {
                val location = apiConnection.getHeaderField("Location")
                    ?: return DownloadResult(false, "GitHub 未返回 ZIP 下载地址")
                (URL(location).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 10_000
                    readTimeout = 60_000
                    instanceFollowRedirects = true
                    useCaches = false
                    defaultUseCaches = false
                    setRequestProperty("Cache-Control", "no-store, no-cache")
                    setRequestProperty("Pragma", "no-cache")
                }.also { downloadConnection = it }
            }
            apiCode in 200..299 -> apiConnection
            apiCode == 401 || apiCode == 403 ->
                return DownloadResult(false, "Token 无效或缺少 Actions 读取权限")
            else ->
                return DownloadResult(false, "下载失败：GitHub HTTP " + apiCode)
        }

        if (streamConnection !== apiConnection) {
            val downloadCode = streamConnection.responseCode
            if (downloadCode !in 200..299) {
                return DownloadResult(false, "ZIP 下载失败：HTTP " + downloadCode)
            }
        }

        val fileName = repo + ".zip"
        val rootDirectory = if (rootEnhancedCleanup) {
            resolveDownloadPhysicalDirectory(destinationTreeUri)
        } else {
            null
        }
        val rootCleanupApplied = rootDirectory?.let { directory ->
            rootCleanupDownloadDirectory(
                context = context,
                directory = directory,
                repo = repo,
                keepCurrentZip = false,
            )
        } == true

        val destination = if (destinationTreeUri.isBlank()) {
            prepareDefaultDownloadDestination(context, fileName)
        } else {
            prepareTreeDownloadDestination(
                context = context,
                treeUriString = destinationTreeUri,
                fileName = fileName,
            )
        } ?: return DownloadResult(false, "无法创建下载文件")

        outputUri = destination.uri
        val totalBytes = streamConnection.contentLengthLong
            .takeIf { it > 0L }
            ?: expectedSizeBytes
        var copiedBytes = 0L
        var lastProgressUpdate = 0L
        context.contentResolver.openOutputStream(destination.uri, "w")?.use { output ->
            streamConnection.inputStream.use { input ->
                val buffer = ByteArray(64 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    output.write(buffer, 0, read)
                    copiedBytes += read
                    val now = System.currentTimeMillis()
                    if (now - lastProgressUpdate >= 120L) {
                        onProgress(copiedBytes, totalBytes, "下载 ZIP…")
                        lastProgressUpdate = now
                    }
                }
                output.flush()
            }
        } ?: return DownloadResult(false, "无法写入下载文件")
        onProgress(copiedBytes, totalBytes, "ZIP 下载完成")

        destination.finish?.invoke()
        onProgress(copiedBytes, totalBytes, "解压 APK…")

        val extractedApks = extractApksFromZip(
            context = context,
            zipUri = destination.uri,
            destinationTreeUri = destinationTreeUri,
            rootDirectory = if (rootEnhancedCleanup) rootDirectory else null,
        )

        if (rootCleanupApplied && rootDirectory != null) {
            onProgress(copiedBytes, totalBytes, "Root 清理…")
            rootCleanupDownloadDirectory(
                context = context,
                directory = rootDirectory,
                repo = repo,
                keepCurrentZip = true,
            )
        }

        onProgress(copiedBytes, totalBytes, "完成")
        DownloadResult(
            success = true,
            message = buildString {
                append("已保存到 ")
                append(destination.displayPath)
                if (extractedApks.isNotEmpty()) {
                    append(" · 已解压 ")
                    append(extractedApks.size)
                    append(" 个 APK")
                } else {
                    append(" · ZIP 内未发现 APK")
                }
                if (rootCleanupApplied) append(" · Root 清理完成")
            },
            extractedApks = extractedApks,
        )
    } catch (e: Exception) {
        outputUri?.let { uri ->
            runCatching { context.contentResolver.delete(uri, null, null) }
        }
        DownloadResult(
            false,
            "下载失败：" + (e.message ?: "未知错误"),
        )
    } finally {
        if (downloadConnection !== apiConnection) {
            downloadConnection?.disconnect()
        }
        apiConnection?.disconnect()
    }
}

private fun extractApksFromZip(
    context: Context,
    zipUri: Uri,
    destinationTreeUri: String,
    rootDirectory: File?,
): List<ExtractedApk> {
    val resolver = context.contentResolver
    val extracted = mutableListOf<ExtractedApk>()
    val seenNames = mutableSetOf<String>()

    val input = resolver.openInputStream(zipUri) ?: return emptyList()
    ZipInputStream(input.buffered()).use { zip ->
        var entry = zip.nextEntry
        while (entry != null) {
            if (!entry.isDirectory && entry.name.endsWith(".apk", ignoreCase = true)) {
                val rawName = entry.name.substringAfterLast('/').substringAfterLast('\\')
                val safeName = sanitizeApkFileName(rawName)
                if (safeName.isNotBlank() && seenNames.add(safeName.lowercase())) {
                    if (rootDirectory != null) {
                        runRootCommand(
                            "rm -f -- " + shellQuote(File(rootDirectory, safeName).absolutePath)
                        )
                        runRootCommand("sync")
                    }

                    val destination = if (destinationTreeUri.isBlank()) {
                        prepareDefaultDownloadDestination(
                            context = context,
                            fileName = safeName,
                            mimeType = APK_MIME_TYPE,
                        )
                    } else {
                        prepareTreeDownloadDestination(
                            context = context,
                            treeUriString = destinationTreeUri,
                            fileName = safeName,
                            mimeType = APK_MIME_TYPE,
                        )
                    }

                    if (destination != null) {
                        val written = runCatching {
                            resolver.openOutputStream(destination.uri, "w")?.use { output ->
                                zip.copyTo(output)
                            } ?: error("无法写入 APK")
                            destination.finish?.invoke()
                            true
                        }.getOrElse {
                            runCatching { resolver.delete(destination.uri, null, null) }
                            false
                        }

                        if (written) {
                            extracted += ExtractedApk(
                                name = safeName,
                                uri = destination.uri,
                            )
                        }
                    }
                }
            }
            zip.closeEntry()
            entry = zip.nextEntry
        }
    }

    if (rootDirectory != null) {
        clearOwnDownloadCache(context)
        runRootCommand("sync")
    }
    return extracted
}

private fun sanitizeApkFileName(name: String): String {
    val cleaned = name
        .replace(Regex("[\\\\/:*?\"<>|]"), "_")
        .trim()
        .take(180)
    return if (cleaned.endsWith(".apk", ignoreCase = true)) cleaned else ""
}

private fun choosePrimaryApk(apks: List<ExtractedApk>): ExtractedApk {
    fun score(name: String): Int {
        val n = name.lowercase()
        var score = 0
        if ("release" in n) score += 100
        if ("universal" in n) score += 90
        if (n == "base.apk") score += 80
        if (n.startsWith("app-")) score += 60
        if ("debug" in n) score -= 20
        if ("split_" in n || "config." in n) score -= 100
        return score
    }
    return apks.maxByOrNull { score(it.name) } ?: apks.first()
}

private fun openExtractedApk(context: Context, apk: ExtractedApk) {
    val installIntent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(apk.uri, APK_MIME_TYPE)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        clipData = ClipData.newRawUri(apk.name, apk.uri)
    }

    runCatching {
        context.startActivity(installIntent)
    }.onFailure {
        Toast.makeText(
            context,
            "APK 已解压，但无法打开系统安装器：" + apk.name,
            Toast.LENGTH_LONG,
        ).show()
    }
}

private const val APK_MIME_TYPE = "application/vnd.android.package-archive"

private fun hasRootAccess(): Boolean {
    val result = runRootCommand("id")
    return result.exitCode == 0 && result.output.contains("uid=0")
}

private data class RootCommandResult(
    val exitCode: Int,
    val output: String,
)

private fun runRootCommand(command: String): RootCommandResult {
    return try {
        val process = ProcessBuilder("su", "-c", command)
            .redirectErrorStream(true)
            .start()
        val output = process.inputStream.bufferedReader().use { it.readText() }
        val exitCode = process.waitFor()
        RootCommandResult(exitCode, output)
    } catch (_: Exception) {
        RootCommandResult(-1, "")
    }
}

private fun shellQuote(value: String): String =
    "'" + value.replace("'", "'\\''") + "'"

private fun resolveDownloadPhysicalDirectory(treeUriString: String): File? {
    if (treeUriString.isBlank()) {
        return File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            "YagaYHub",
        )
    }

    val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return null
    if (treeUri.authority != "com.android.externalstorage.documents") return null

    val documentId = runCatching {
        DocumentsContract.getTreeDocumentId(treeUri)
    }.getOrNull() ?: return null
    val parts = documentId.split(":", limit = 2)
    val volume = parts.firstOrNull().orEmpty()
    val relative = parts.getOrNull(1).orEmpty()

    val base = when {
        volume.equals("primary", ignoreCase = true) ->
            Environment.getExternalStorageDirectory()
        volume.isNotBlank() -> File("/storage", volume)
        else -> return null
    }
    return if (relative.isBlank()) base else File(base, relative)
}

private fun rootCleanupDownloadDirectory(
    context: Context,
    directory: File,
    repo: String,
    keepCurrentZip: Boolean,
): Boolean {
    val listCommand = "find " + shellQuote(directory.absolutePath) +
        " -maxdepth 1 -type f 2>/dev/null"
    val listed = runRootCommand(listCommand)
    if (listed.exitCode != 0 && !directory.exists()) return false

    val stableName = repo + ".zip"
    val duplicatePrefix = repo + " ("
    val tempPrefixes = listOf(
        stableName + ".",
        "." + stableName,
        repo + ".tmp",
        repo + ".part",
    )

    val targets = listed.output
        .lineSequence()
        .map { it.trim() }
        .filter { it.isNotBlank() }
        .map(::File)
        .filter { file ->
            val name = file.name
            when {
                !keepCurrentZip && name == stableName -> true
                name.startsWith(duplicatePrefix) && name.endsWith(").zip") -> true
                tempPrefixes.any { prefix -> name.startsWith(prefix) } -> true
                else -> false
            }
        }
        .distinctBy { it.absolutePath }
        .toList()

    targets.forEach { file ->
        runRootCommand("rm -f -- " + shellQuote(file.absolutePath))
    }

    clearOwnDownloadCache(context)
    runRootCommand("sync")
    return true
}

private fun clearOwnDownloadCache(context: Context) {
    runCatching {
        context.cacheDir.listFiles()?.forEach { child ->
            child.deleteRecursively()
        }
    }
}

private fun saveRootCleanupEnabled(context: Context, enabled: Boolean) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putBoolean(ROOT_CLEANUP_ENABLED, enabled)
        .apply()
}

private fun loadRootCleanupEnabled(context: Context): Boolean =
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getBoolean(ROOT_CLEANUP_ENABLED, false)

private data class DownloadDestination(
    val uri: Uri,
    val displayPath: String,
    val finish: (() -> Unit)? = null,
)

private fun deleteDefaultDownloadByName(
    context: Context,
    fileName: String,
    relativePath: String,
    keepUri: Uri? = null,
) {
    val resolver = context.contentResolver
    resolver.query(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        arrayOf(MediaStore.MediaColumns._ID),
        MediaStore.MediaColumns.DISPLAY_NAME + "=? AND " +
            MediaStore.MediaColumns.RELATIVE_PATH + "=?",
        arrayOf(fileName, relativePath),
        null,
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
        while (cursor.moveToNext()) {
            val existingUri = Uri.withAppendedPath(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                cursor.getLong(idColumn).toString(),
            )
            if (keepUri == null || existingUri != keepUri) {
                runCatching { resolver.delete(existingUri, null, null) }
            }
        }
    }
}

private fun deleteTreeDocumentsByName(
    context: Context,
    treeUri: Uri,
    fileName: String,
    keepUri: Uri? = null,
) {
    val resolver = context.contentResolver
    val childrenUri = runCatching {
        DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
    }.getOrNull() ?: return

    resolver.query(
        childrenUri,
        arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ),
        null,
        null,
        null,
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID
        )
        val nameColumn = cursor.getColumnIndexOrThrow(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME
        )
        while (cursor.moveToNext()) {
            if (cursor.getString(nameColumn) != fileName) continue
            val existingUri = DocumentsContract.buildDocumentUriUsingTree(
                treeUri,
                cursor.getString(idColumn),
            )
            if (keepUri == null || existingUri != keepUri) {
                runCatching {
                    DocumentsContract.deleteDocument(resolver, existingUri)
                }
            }
        }
    }
}

private fun prepareDefaultDownloadDestination(
    context: Context,
    fileName: String,
    mimeType: String = "application/zip",
): DownloadDestination? {
    val resolver = context.contentResolver
    val relativePath = Environment.DIRECTORY_DOWNLOADS + "/YagaYHub/"

    // 先彻底删除同名旧文件和残留项，不保留旧 ZIP / pending 项。
    deleteDefaultDownloadByName(
        context = context,
        fileName = fileName,
        relativePath = relativePath,
    )

    val values = ContentValues().apply {
        put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
        put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
        put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
        put(MediaStore.MediaColumns.IS_PENDING, 1)
    }

    val uri = resolver.insert(
        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
        values,
    ) ?: return null

    return DownloadDestination(
        uri = uri,
        displayPath = "Downloads/YagaYHub/" + fileName,
        finish = {
            resolver.update(
                uri,
                ContentValues().apply {
                    put(MediaStore.MediaColumns.IS_PENDING, 0)
                },
                null,
                null,
            )
            deleteDefaultDownloadByName(
                context = context,
                fileName = fileName,
                relativePath = relativePath,
                keepUri = uri,
            )
        },
    )
}

private fun prepareTreeDownloadDestination(
    context: Context,
    treeUriString: String,
    fileName: String,
    mimeType: String = "application/zip",
): DownloadDestination? {
    val resolver = context.contentResolver
    val treeUri = runCatching { Uri.parse(treeUriString) }.getOrNull() ?: return null
    val parentDocumentUri = runCatching {
        DocumentsContract.buildDocumentUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
    }.getOrNull() ?: return null
    val childrenUri = runCatching {
        DocumentsContract.buildChildDocumentsUriUsingTree(
            treeUri,
            DocumentsContract.getTreeDocumentId(treeUri),
        )
    }.getOrNull() ?: return null

    // 自定义目录也先删除全部同名旧文件 / 残留项。
    deleteTreeDocumentsByName(
        context = context,
        treeUri = treeUri,
        fileName = fileName,
    )

    val uri = runCatching {
        DocumentsContract.createDocument(
            resolver,
            parentDocumentUri,
            mimeType,
            fileName,
        )
    }.getOrNull() ?: return null

    return DownloadDestination(
        uri = uri,
        displayPath = "自定义目录/" + fileName,
        finish = {
            deleteTreeDocumentsByName(
                context = context,
                treeUri = treeUri,
                fileName = fileName,
                keepUri = uri,
            )
        },
    )
}

private fun saveDownloadDirectoryUri(context: Context, uri: String) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(DOWNLOAD_TREE_URI, uri)
        .apply()
}

private fun loadDownloadDirectoryUri(context: Context): String =
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getString(DOWNLOAD_TREE_URI, "")
        .orEmpty()

private fun clearDownloadDirectoryUri(context: Context) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(DOWNLOAD_TREE_URI)
        .apply()
}

private fun downloadDirectoryLabel(treeUri: String): String =
    if (treeUri.isBlank()) {
        "Downloads/YagaYHub"
    } else {
        "自定义目录"
    }

private data class GithubWebAuthSession(
    val serverSocket: ServerSocket,
    val state: String,
    val codeVerifier: String,
    val codeChallenge: String,
    val redirectUri: String,
) {
    fun close() {
        runCatching { serverSocket.close() }
    }
}

private fun createGithubWebAuthSession(): GithubWebAuthSession? {
    return runCatching {
        val server = ServerSocket(
            0,
            1,
            InetAddress.getByName("127.0.0.1"),
        ).apply {
            soTimeout = 180_000
        }
        val verifier = randomUrlSafeString(64)
        val challenge = Base64.encodeToString(
            MessageDigest.getInstance("SHA-256")
                .digest(verifier.toByteArray(Charsets.US_ASCII)),
            Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
        )
        GithubWebAuthSession(
            serverSocket = server,
            state = randomUrlSafeString(32),
            codeVerifier = verifier,
            codeChallenge = challenge,
            redirectUri = "http://127.0.0.1:" + server.localPort + "/oauth/callback",
        )
    }.getOrNull()
}

private fun randomUrlSafeString(byteCount: Int): String {
    val bytes = ByteArray(byteCount)
    SecureRandom().nextBytes(bytes)
    return Base64.encodeToString(
        bytes,
        Base64.URL_SAFE or Base64.NO_WRAP or Base64.NO_PADDING,
    )
}

private fun buildGithubAuthorizeUrl(
    clientId: String,
    session: GithubWebAuthSession,
): String {
    fun enc(value: String): String = URLEncoder.encode(value, "UTF-8")
    return "https://github.com/login/oauth/authorize" +
        "?client_id=" + enc(clientId) +
        "&redirect_uri=" + enc(session.redirectUri) +
        "&state=" + enc(session.state) +
        "&code_challenge=" + enc(session.codeChallenge) +
        "&code_challenge_method=S256" +
        "&prompt=select_account"
}

private fun completeGithubWebAuth(
    session: GithubWebAuthSession,
    clientId: String,
    clientSecret: String,
): String? {
    return try {
        val socket = session.serverSocket.accept()
        socket.use { client ->
            val reader = client.getInputStream().bufferedReader()
            val requestLine = reader.readLine().orEmpty()
            while (true) {
                val line = reader.readLine() ?: break
                if (line.isBlank()) break
            }

            val target = requestLine
                .split(' ')
                .getOrNull(1)
                .orEmpty()
            val callbackUri = Uri.parse("http://127.0.0.1" + target)
            val code = callbackUri.getQueryParameter("code")
            val returnedState = callbackUri.getQueryParameter("state")
            val error = callbackUri.getQueryParameter("error")

            val token = if (
                error.isNullOrBlank() &&
                !code.isNullOrBlank() &&
                returnedState == session.state
            ) {
                exchangeGithubAuthorizationCode(
                    clientId = clientId,
                    clientSecret = clientSecret,
                    code = code,
                    redirectUri = session.redirectUri,
                    codeVerifier = session.codeVerifier,
                )
            } else {
                null
            }

            val response = if (token.isNullOrBlank()) {
                "HTTP/1.1 302 Found\r\n" +
                    "Location: yagayhub://oauth/complete?status=error\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
            } else {
                "HTTP/1.1 302 Found\r\n" +
                    "Location: yagayhub://oauth/complete?status=success\r\n" +
                    "Content-Length: 0\r\n" +
                    "Connection: close\r\n\r\n"
            }
            client.getOutputStream().use { output ->
                output.write(response.toByteArray(Charsets.US_ASCII))
                output.flush()
            }
            token
        }
    } catch (_: Exception) {
        null
    } finally {
        session.close()
    }
}

private fun exchangeGithubAuthorizationCode(
    clientId: String,
    clientSecret: String,
    code: String,
    redirectUri: String,
    codeVerifier: String,
): String? {
    val response = postGithubForm(
        "https://github.com/login/oauth/access_token",
        mapOf(
            "client_id" to clientId,
            "client_secret" to clientSecret,
            "code" to code,
            "redirect_uri" to redirectUri,
            "code_verifier" to codeVerifier,
        ),
    ) ?: return null
    return response.optString("access_token")
        .takeIf { it.isNotBlank() }
}

private data class DeviceAuthInfo(
    val deviceCode: String,
    val userCode: String,
    val verificationUri: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int,
)

private fun requestGithubDeviceCode(clientId: String): DeviceAuthInfo? {
    val response = postGithubForm(
        "https://github.com/login/device/code",
        mapOf("client_id" to clientId),
    ) ?: return null

    val deviceCode = response.optString("device_code")
    val userCode = response.optString("user_code")
    val verificationUri = response.optString("verification_uri")
    if (deviceCode.isBlank() || userCode.isBlank() || verificationUri.isBlank()) return null

    return DeviceAuthInfo(
        deviceCode = deviceCode,
        userCode = userCode,
        verificationUri = verificationUri,
        expiresInSeconds = response.optInt("expires_in", 900),
        intervalSeconds = response.optInt("interval", 5).coerceAtLeast(5),
    )
}

private suspend fun pollGithubDeviceToken(
    clientId: String,
    info: DeviceAuthInfo,
): String? {
    var interval = info.intervalSeconds
    val deadline = System.currentTimeMillis() + info.expiresInSeconds * 1000L

    while (System.currentTimeMillis() < deadline) {
        delay(interval * 1000L)
        val response = postGithubForm(
            "https://github.com/login/oauth/access_token",
            mapOf(
                "client_id" to clientId,
                "device_code" to info.deviceCode,
                "grant_type" to "urn:ietf:params:oauth:grant-type:device_code",
            ),
        ) ?: return null

        val accessToken = response.optString("access_token")
        if (accessToken.isNotBlank()) return accessToken

        when (response.optString("error")) {
            "authorization_pending" -> Unit
            "slow_down" -> interval += 5
            "expired_token", "access_denied", "incorrect_client_credentials",
            "incorrect_device_code", "device_flow_disabled" -> return null
            else -> return null
        }
    }
    return null
}

private fun postGithubForm(
    url: String,
    fields: Map<String, String>,
): JSONObject? {
    var connection: HttpURLConnection? = null
    return try {
        val body = fields.entries.joinToString("&") { entry ->
            URLEncoder.encode(entry.key, "UTF-8") + "=" +
                URLEncoder.encode(entry.value, "UTF-8")
        }

        connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 30_000
            doOutput = true
            setRequestProperty("Accept", "application/json")
            setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            setRequestProperty("User-Agent", "YagaYHub")
        }
        connection.outputStream.use { output ->
            output.write(body.toByteArray(Charsets.UTF_8))
        }
        if (connection.responseCode !in 200..299) return null
        val text = connection.inputStream.bufferedReader().use { it.readText() }
        JSONObject(text)
    } catch (_: Exception) {
        null
    } finally {
        connection?.disconnect()
    }
}

private fun copyText(context: Context, text: String) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    clipboard.setPrimaryClip(ClipData.newPlainText("GitHub authorization code", text))
    Toast.makeText(context, "授权码已复制", Toast.LENGTH_SHORT).show()
}

private fun saveGithubClientSecret(
    context: Context,
    clientSecret: String,
) {
    if (clientSecret.isBlank()) {
        clearGithubClientSecret(context)
        return
    }
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateTokenKey())
    val encrypted = cipher.doFinal(clientSecret.toByteArray(Charsets.UTF_8))

    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(
            GITHUB_CLIENT_SECRET_IV,
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
        )
        .putString(
            GITHUB_CLIENT_SECRET_DATA,
            Base64.encodeToString(encrypted, Base64.NO_WRAP),
        )
        .apply()
}

private fun loadGithubClientSecret(context: Context): String {
    return runCatching {
        val prefs = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        val iv = prefs.getString(GITHUB_CLIENT_SECRET_IV, null) ?: return ""
        val encrypted = prefs.getString(GITHUB_CLIENT_SECRET_DATA, null) ?: return ""

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateTokenKey(),
            GCMParameterSpec(
                128,
                Base64.decode(iv, Base64.NO_WRAP),
            ),
        )
        String(
            cipher.doFinal(
                Base64.decode(encrypted, Base64.NO_WRAP)
            ),
            Charsets.UTF_8,
        )
    }.getOrElse { "" }
}

private fun clearGithubClientSecret(context: Context) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(GITHUB_CLIENT_SECRET_IV)
        .remove(GITHUB_CLIENT_SECRET_DATA)
        .apply()
}

private fun saveGithubClientId(context: Context, clientId: String) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(GITHUB_CLIENT_ID, clientId)
        .apply()
}

private fun loadGithubClientId(context: Context): String =
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getString(GITHUB_CLIENT_ID, "")
        .orEmpty()

private fun saveGithubToken(context: Context, token: String) {
    val cipher = Cipher.getInstance("AES/GCM/NoPadding")
    cipher.init(Cipher.ENCRYPT_MODE, getOrCreateTokenKey())
    val encrypted = cipher.doFinal(token.toByteArray(Charsets.UTF_8))

    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(TOKEN_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
        .putString(TOKEN_DATA, Base64.encodeToString(encrypted, Base64.NO_WRAP))
        .apply()
}

private fun loadGithubToken(context: Context): String {
    return runCatching {
        val prefs = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        val iv = prefs.getString(TOKEN_IV, null) ?: return ""
        val encrypted = prefs.getString(TOKEN_DATA, null) ?: return ""

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            getOrCreateTokenKey(),
            GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
        )
        String(
            cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)),
            Charsets.UTF_8,
        )
    }.getOrElse { "" }
}

private fun clearGithubToken(context: Context) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(TOKEN_IV)
        .remove(TOKEN_DATA)
        .apply()
}

private fun getOrCreateTokenKey(): SecretKey {
    val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
    (keyStore.getKey(TOKEN_KEY_ALIAS, null) as? SecretKey)?.let { return it }

    val generator = KeyGenerator.getInstance(
        KeyProperties.KEY_ALGORITHM_AES,
        "AndroidKeyStore",
    )
    generator.init(
        KeyGenParameterSpec.Builder(
            TOKEN_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
    )
    return generator.generateKey()
}

private fun saveLayoutMode(context: Context, mode: LayoutMode) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(LAYOUT_MODE, mode.name)
        .apply()
}

private fun loadLayoutMode(context: Context): LayoutMode {
    val saved = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getString(LAYOUT_MODE, LayoutMode.LIST.name)
    return runCatching { LayoutMode.valueOf(saved.orEmpty()) }
        .getOrDefault(LayoutMode.LIST)
}

private const val DOWNLOAD_CHANNEL_ID = "artifact_downloads"
private const val DOWNLOAD_NOTIFICATION_ID = 4107
private const val ACTION_DOWNLOAD_STATE = "com.yagay.YagaYHub.action.DOWNLOAD_STATE"

private const val EXTRA_DOWNLOAD_APP_NAME = "download_app_name"
private const val EXTRA_DOWNLOAD_OWNER = "download_owner"
private const val EXTRA_DOWNLOAD_REPO = "download_repo"
private const val EXTRA_DOWNLOAD_RUN_ID = "download_run_id"
private const val EXTRA_DOWNLOAD_ARTIFACT_ID = "download_artifact_id"
private const val EXTRA_DOWNLOAD_EXPECTED_SIZE = "download_expected_size"

private const val EXTRA_STATE_APP_NAME = "state_app_name"
private const val EXTRA_STATE_STAGE = "state_stage"
private const val EXTRA_STATE_DOWNLOADED = "state_downloaded"
private const val EXTRA_STATE_TOTAL = "state_total"
private const val EXTRA_STATE_RUNNING = "state_running"
private const val EXTRA_STATE_MESSAGE = "state_message"
private const val EXTRA_STATE_APK_NAMES = "state_apk_names"
private const val EXTRA_STATE_APK_URIS = "state_apk_uris"

private const val TOKEN_PREFS = "github_secure"
private const val TOKEN_IV = "token_iv"
private const val TOKEN_DATA = "token_data"
private const val GITHUB_CLIENT_ID = "github_client_id"
private const val GITHUB_CLIENT_SECRET_IV = "github_client_secret_iv"
private const val GITHUB_CLIENT_SECRET_DATA = "github_client_secret_data"
private const val DOWNLOAD_TREE_URI = "download_tree_uri"
private const val ROOT_CLEANUP_ENABLED = "root_cleanup_enabled"
private const val LAYOUT_MODE = "layout_mode"
private const val DOWNLOAD_STATE_JSON = "download_state_json"
private const val TOKEN_KEY_ALIAS = "YagaYHubGitHubToken"

private fun getPackageInfoCompat(pm: PackageManager, packageName: String): PackageInfo? = runCatching {
    if (Build.VERSION.SDK_INT >= 33) {
        pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
    } else {
        @Suppress("DEPRECATION")
        pm.getPackageInfo(packageName, 0)
    }
}.getOrNull()

private fun Drawable.toImageBitmap(): ImageBitmap {
    if (this is BitmapDrawable) return bitmap.asImageBitmap()
    val width = intrinsicWidth.takeIf { it > 0 } ?: 96
    val height = intrinsicHeight.takeIf { it > 0 } ?: 96
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    return bitmap.asImageBitmap()
}

private fun openApp(context: Context, app: HubApp) {
    val intent = app.launchIntent ?: return
    runCatching {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }.onFailure {
        Toast.makeText(context, "无法打开 ${app.name}", Toast.LENGTH_SHORT).show()
    }
}

private fun openAppDetails(context: Context, packageName: String) {
    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.parse("package:$packageName")
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    runCatching { context.startActivity(intent) }
}

private fun openUrl(context: Context, url: String) {
    val intent = Intent(YBROWSER_ACTION).apply {
        setPackage(YBROWSER_PACKAGE)
        putExtra(YBROWSER_EXTRA_URL, url)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "请先安装 YBrowser", Toast.LENGTH_SHORT).show()
    }
}

private const val YBROWSER_PACKAGE = "com.yagay.YBrowser"
private const val YBROWSER_ACTION = "com.yagay.YBrowser.action.OPEN_URL"
private const val YBROWSER_EXTRA_URL = "com.yagay.YBrowser.extra.URL"

@Composable
private fun YagaYHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
