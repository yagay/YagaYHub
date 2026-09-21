package com.yagay.YagaYHub

import android.content.ActivityNotFoundException
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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.security.KeyStore
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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
    val description: String,
    val installed: Boolean,
    val versionName: String?,
    val launchIntent: Intent?,
    val icon: Drawable?,
    val autoDiscovered: Boolean = false,
    val actionsStatus: ActionsStatus = ActionsStatus.NONE,
    val latestRunId: Long? = null,
    val latestArtifactId: Long? = null,
    val latestArtifactSizeBytes: Long? = null,
)

private enum class AppFilter(val label: String) {
    ALL("全部"), INSTALLED("已安装"), NOT_INSTALLED("未安装")
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
    val scope = rememberCoroutineScope()

    LaunchedEffect(refreshKey, githubToken) {
        val loadedApps = loadHubApps(context)
        apps = loadedApps
        apps = withContext(Dispatchers.IO) {
            loadActionsStatuses(loadedApps, githubToken)
        }
    }

    val visibleApps = remember(apps, query, filter) {
        val q = query.trim().lowercase()
        apps.filter { app ->
            val filterOk = when (filter) {
                AppFilter.ALL -> true
                AppFilter.INSTALLED -> app.installed
                AppFilter.NOT_INSTALLED -> !app.installed
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
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AppFilter.entries.forEach { item ->
                    val count = when (item) {
                        AppFilter.ALL -> apps.size
                        AppFilter.INSTALLED -> apps.count { it.installed }
                        AppFilter.NOT_INSTALLED -> apps.count { !it.installed }
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
                                        app.repo != null -> openUrl(context, "https://github.com/yagay/${app.repo}")
                                        app.installed -> openAppDetails(context, app.packageName)
                                    }
                                },
                                onLongClick = {
                                    when {
                                        app.installed -> openAppDetails(context, app.packageName)
                                        app.repo != null -> openUrl(context, "https://github.com/yagay/${app.repo}")
                                    }
                                },
                                onActionsClick = {
                                    app.repo?.let { repo ->
                                        openUrl(context, "https://github.com/yagay/$repo/actions")
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
        GitHubTokenDialog(
            currentToken = githubToken,
            onDismiss = { showSettings = false },
            onSave = { token ->
                saveGithubToken(context, token)
                githubToken = token
                showSettings = false
                Toast.makeText(context, "GitHub Token 已保存", Toast.LENGTH_SHORT).show()
            },
            onClear = {
                clearGithubToken(context)
                githubToken = ""
                showSettings = false
                Toast.makeText(context, "GitHub Token 已清除", Toast.LENGTH_SHORT).show()
            },
        )
    }
}

@Composable
private fun GitHubTokenDialog(
    currentToken: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
    onClear: () -> Unit,
) {
    var token by remember(currentToken) { mutableStateOf(currentToken) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("GitHub Token") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Token 只保存在本机，并使用 Android Keystore 加密。需要 Actions 读取权限。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    label = { Text("Fine-grained token") },
                    placeholder = { Text("github_pat_…") },
                    visualTransformation = PasswordVisualTransformation(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(token.trim()) },
                enabled = token.isNotBlank(),
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            Row {
                if (currentToken.isNotBlank()) {
                    TextButton(onClick = onClear) {
                        Text("清除")
                    }
                }
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

private data class LatestActionsInfo(
    val status: ActionsStatus,
    val runId: Long?,
    val artifactId: Long?,
    val artifactSizeBytes: Long?,
)

private suspend fun loadActionsStatuses(
    apps: List<HubApp>,
    token: String,
): List<HubApp> = coroutineScope {
    apps.map { app ->
        async {
            if (app.repo == null) {
                app
            } else {
                val info = fetchLatestActionsInfo(app.repo, token)
                app.copy(
                    actionsStatus = info.status,
                    latestRunId = info.runId,
                    latestArtifactId = info.artifactId,
                    latestArtifactSizeBytes = info.artifactSizeBytes,
                )
            }
        }
    }.awaitAll()
}

private fun fetchLatestActionsInfo(repo: String, token: String): LatestActionsInfo {
    var connection: HttpURLConnection? = null
    return try {
        connection = githubGet(
            "https://api.github.com/repos/yagay/" + repo + "/actions/runs?per_page=1",
            token = token,
        )
        if (connection.responseCode !in 200..299) {
            return LatestActionsInfo(ActionsStatus.UNKNOWN, null, null, null)
        }

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val runs = JSONObject(body).optJSONArray("workflow_runs")
        if (runs == null || runs.length() == 0) {
            return LatestActionsInfo(ActionsStatus.NONE, null, null, null)
        }

        val run = runs.getJSONObject(0)
        val status = mapActionsStatus(run)
        val runId = run.optLong("id").takeIf { it > 0L }

        // 只处理最新一次 Actions：只有最新一次成功，才继续请求 artifact。
        val artifactInfo = if (status == ActionsStatus.SUCCESS && runId != null) {
            fetchLatestArtifactInfo(repo, runId, token)
        } else {
            null
        }

        LatestActionsInfo(
            status = status,
            runId = runId,
            artifactId = artifactInfo?.first,
            artifactSizeBytes = artifactInfo?.second,
        )
    } catch (_: Exception) {
        LatestActionsInfo(ActionsStatus.UNKNOWN, null, null, null)
    } finally {
        connection?.disconnect()
    }
}

private fun fetchLatestArtifactInfo(
    repo: String,
    runId: Long,
    token: String,
): Pair<Long, Long>? {
    var connection: HttpURLConnection? = null
    return try {
        connection = githubGet(
            "https://api.github.com/repos/yagay/" + repo +
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
        setRequestProperty("X-GitHub-Api-Version", "2022-11-28")
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
            url = "https://api.github.com/repos/yagay/" + repo +
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
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/zip")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/YagaYHub")
            put(MediaStore.Downloads.IS_PENDING, 1)
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
            ContentValues().apply { put(MediaStore.Downloads.IS_PENDING, 0) },
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
