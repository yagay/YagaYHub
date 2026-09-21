package com.yagay.YagaYHub

import android.content.ActivityNotFoundException
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
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

    LaunchedEffect(refreshKey) {
        apps = loadHubApps(context)
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
                            )
                        }
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
            Text(
                "Actions",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onActionsClick)
                    .padding(horizontal = 6.dp, vertical = 3.dp),
            )
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
    )
}

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
