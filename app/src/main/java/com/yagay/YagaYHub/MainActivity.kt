package com.yagay.YagaYHub

import android.content.ActivityNotFoundException
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
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.KeyStore
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
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

private data class HubApp(
    val name: String,
    val packageName: String,
    val repo: String?,
    val repoOwner: String = "yagay",
    val description: String,
    val installed: Boolean,
    val versionName: String?,
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
    var githubClientId by remember { mutableStateOf(loadGithubClientId(context)) }
    var deviceAuth by remember { mutableStateOf<DeviceAuthInfo?>(null) }
    var authPolling by remember { mutableStateOf(false) }
    var authStatus by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()

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
                TextButton(onClick = { showSettings = true }) {
                    Text("设置")
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
                            AppEntry(
                                app = app,
                                onClick = {
                                    when {
                                        app.launchIntent != null -> openApp(context, app)
                                        app.repo != null -> openUrl(
                                            context,
                                            "https://github.com/" + app.repoOwner + "/" + app.repo
                                        )
                                        app.installed -> openAppDetails(context, app.packageName)
                                    }
                                },
                                onLongClick = {
                                    when {
                                        app.installed -> openAppDetails(context, app.packageName)
                                        app.repo != null -> openUrl(
                                            context,
                                            "https://github.com/" + app.repoOwner + "/" + app.repo
                                        )
                                    }
                                },
                                onActionsClick = {
                                    app.repo?.let { repo ->
                                        openUrl(
                                            context,
                                            "https://github.com/" + app.repoOwner + "/" + repo + "/actions"
                                        )
                                    }
                                },
                                onArtifactClick = {
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
                                            scope.launch {
                                                Toast.makeText(
                                                    context,
                                                    "开始下载 " + app.name + "…",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                val result = withContext(Dispatchers.IO) {
                                                    downloadArtifactZip(
                                                        context = context,
                                                        owner = app.repoOwner,
                                                        repo = repo,
                                                        runId = runId,
                                                        artifactId = artifactId,
                                                        token = githubToken,
                                                    )
                                                }
                                                Toast.makeText(
                                                    context,
                                                    result.message,
                                                    Toast.LENGTH_LONG
                                                ).show()
                                            }
                                        }
                                    }
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (showSettings) {
        GitHubSettingsDialog(
            currentToken = githubToken,
            currentClientId = githubClientId,
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
    onDismiss: () -> Unit,
    onSaveToken: (String) -> Unit,
    onClearToken: () -> Unit,
    onAuthorize: (String) -> Unit,
) {
    var token by remember(currentToken) { mutableStateOf(currentToken) }
    var clientId by remember(currentClientId) { mutableStateOf(currentClientId) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub 设置") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    "推荐使用 GitHub App 账号授权；现有 Fine-grained Token 仍可继续使用。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = clientId,
                    onValueChange = { clientId = it.trim() },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("GitHub App Client ID") },
                    placeholder = { Text("Iv1.…") },
                )
                TextButton(
                    onClick = { onAuthorize(clientId.trim()) },
                    enabled = clientId.isNotBlank(),
                ) {
                    Text("使用 GitHub 账号授权")
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
private fun AppEntry(
    app: HubApp,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    onActionsClick: () -> Unit,
    onArtifactClick: () -> Unit,
) {
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.fillMaxWidth(),
        )
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
                        app.actionsStatus.label,
                        style = MaterialTheme.typography.labelSmall,
                        color = when (app.actionsStatus) {
                            ActionsStatus.FAILURE -> MaterialTheme.colorScheme.error
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
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
                    actionTime,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    DateTimeFormatter.ofPattern("MM-dd HH:mm")

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
        setRequestProperty("Accept", "application/vnd.github+json")
        setRequestProperty("User-Agent", "YagaYHub")
        setRequestProperty("X-GitHub-Api-Version", "2026-03-10")
        if (token.isNotBlank()) {
            setRequestProperty("Authorization", "Bearer " + token)
        }
    }

private data class DownloadResult(
    val success: Boolean,
    val message: String,
)

private fun downloadArtifactZip(
    context: Context,
    owner: String,
    repo: String,
    runId: Long,
    artifactId: Long,
    token: String,
): DownloadResult {
    var apiConnection: HttpURLConnection? = null
    var downloadConnection: HttpURLConnection? = null
    var outputUri: Uri? = null

    return try {
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

        val fileName = repo + "-" + runId + ".zip"
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/zip")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YagaYHub")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }

        outputUri = context.contentResolver.insert(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            values,
        ) ?: return DownloadResult(false, "无法创建下载文件")

        context.contentResolver.openOutputStream(outputUri)?.use { output ->
            streamConnection.inputStream.use { input ->
                input.copyTo(output)
            }
        } ?: return DownloadResult(false, "无法写入下载文件")

        context.contentResolver.update(
            outputUri,
            ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) },
            null,
            null,
        )

        DownloadResult(
            true,
            "已保存到 Downloads/YagaYHub/" + fileName,
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

private const val TOKEN_PREFS = "github_secure"
private const val TOKEN_IV = "token_iv"
private const val TOKEN_DATA = "token_data"
private const val GITHUB_CLIENT_ID = "github_client_id"
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
