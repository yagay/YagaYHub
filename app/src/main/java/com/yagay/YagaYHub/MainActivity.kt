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
import java.io.RandomAccessFile
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicLongArray
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Sort
import androidx.compose.material.icons.outlined.ViewList
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private var chatBindingRevision by mutableIntStateOf(0)
    private var quickChatBindingRequest by mutableStateOf<QuickChatBindingRequest?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleChatBindingIntent(intent)
        setContent {
            YagaYHubTheme {
                Surface(Modifier.fillMaxSize()) {
                    HubScreen(
                        context = this,
                        chatBindingRevision = chatBindingRevision,
                        quickChatBindingRequest = quickChatBindingRequest,
                        onQuickChatBindingDismiss = {
                            quickChatBindingRequest = null
                        },
                        onQuickChatBindingSelected = { app ->
                            val repo = app.repo
                            val request = quickChatBindingRequest
                            if (repo != null && request != null) {
                                val repoKey = app.repoOwner + "/" + repo
                                saveChatBinding(
                                    context = this,
                                    repoKey = repoKey,
                                    title = request.title,
                                    url = request.url,
                                )
                                chatBindingRevision++
                                quickChatBindingRequest = null
                                syncChatBindingToYBrowser(
                                    context = this,
                                    repoKey = repoKey,
                                    project = app.name,
                                    url = request.url,
                                    title = request.title,
                                )
                                returnChatBindingToRequester(
                                    context = this,
                                    request = request,
                                    repoKey = repoKey,
                                    project = app.name,
                                    url = request.url,
                                    title = request.title,
                                )
                            }
                        },
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleChatBindingIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        chatBindingRevision++
    }

    private fun handleChatBindingIntent(intent: Intent?) {
        when (intent?.action) {
            ACTION_CHATGPT_BOUND -> {
                val repo = intent.getStringExtra(EXTRA_CHAT_BIND_REPO).orEmpty()
                val url = intent.getStringExtra(EXTRA_CHAT_BIND_URL).orEmpty()
                val title = intent.getStringExtra(EXTRA_CHAT_BIND_TITLE)
                    .orEmpty()
                    .ifBlank { "AI" }
                if (
                    repo.isBlank() ||
                    url.isBlank() ||
                    !isBindableAiPageUrl(url)
                ) {
                    return
                }

                saveChatBinding(
                    context = this,
                    repoKey = repo,
                    title = title,
                    url = url,
                )
                chatBindingRevision++
                Toast.makeText(
                    this,
                    "已绑定 AI · " + repo.substringAfter('/'),
                    Toast.LENGTH_SHORT,
                ).show()
            }
            ACTION_REQUEST_CHATGPT_BINDING -> {
                val url = intent.getStringExtra(EXTRA_CHAT_BIND_URL).orEmpty()
                val title = intent.getStringExtra(EXTRA_CHAT_BIND_TITLE)
                    .orEmpty()
                    .ifBlank { "AI" }
                if (isBindableAiPageUrl(url)) {
                    quickChatBindingRequest = QuickChatBindingRequest(
                        url = url,
                        title = title,
                        windowId = intent
                            .getStringExtra(EXTRA_AI_WINDOW_ID)
                            ?.takeIf { it.isNotBlank() },
                        requesterPackage = intent
                            .getStringExtra(EXTRA_BIND_REQUESTER_PACKAGE)
                            ?.takeIf { it.isNotBlank() },
                    )
                }
            }
        }
    }
}

class ChatBindingCommandReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        when (intent?.action) {
            ACTION_REMOVE_CHATGPT_BINDING -> {
                val repo = intent.getStringExtra(
                    EXTRA_CHAT_BIND_REPO
                ).orEmpty()
                val url = intent.getStringExtra(
                    EXTRA_CHAT_BIND_URL
                ).orEmpty()

                if (repo.isNotBlank()) {
                    removeChatBindingsByRepo(
                        context = context,
                        repoKey = repo,
                    )
                } else {
                    if (url.isBlank()) return
                    removeChatBinding(
                        context,
                        url,
                    )
                }
            }
            ACTION_CHATGPT_BOUND -> {
                val repo = intent.getStringExtra(EXTRA_CHAT_BIND_REPO).orEmpty()
                val url = intent.getStringExtra(EXTRA_CHAT_BIND_URL).orEmpty()
                val title = intent.getStringExtra(EXTRA_CHAT_BIND_TITLE)
                    .orEmpty()
                    .ifBlank { "AI" }
                if (
                    repo.isBlank() ||
                    url.isBlank() ||
                    !isBindableAiPageUrl(url)
                ) {
                    return
                }
                saveChatBinding(
                    context = context,
                    repoKey = repo,
                    title = title,
                    url = url,
                )
                Toast.makeText(
                    context,
                    "已绑定 AI · " + repo.substringAfter('/'),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }
}

private class DownloadCancelledException :
    RuntimeException("download-cancelled")

private class DownloadControl {
    private val monitor = Object()

    @Volatile
    var paused: Boolean = false
        private set

    @Volatile
    var cancelled: Boolean = false
        private set

    fun pause() {
        paused = true
    }

    fun resume() {
        synchronized(monitor) {
            paused = false
            monitor.notifyAll()
        }
    }

    fun cancel() {
        synchronized(monitor) {
            cancelled = true
            paused = false
            monitor.notifyAll()
        }
    }

    fun checkpoint() {
        synchronized(monitor) {
            while (paused && !cancelled) {
                monitor.wait(500L)
            }
            if (cancelled) {
                throw DownloadCancelledException()
            }
        }
    }
}

class ArtifactDownloadService : Service() {
    private val workers =
        ConcurrentHashMap<Long, Thread>()
    private val controls =
        ConcurrentHashMap<Long, DownloadControl>()
    private val taskStates =
        ConcurrentHashMap<Long, DownloadUiState>()
    private val serviceLock = Any()

    override fun onCreate() {
        super.onCreate()
        ensureDownloadNotificationChannel(this)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        val request =
            intent?.let(
                ArtifactDownloadRequest::fromIntent
            ) ?: return START_NOT_STICKY

        when (intent.action) {
            ACTION_DOWNLOAD_PAUSE -> {
                pauseDownload(request)
                return START_NOT_STICKY
            }

            ACTION_DOWNLOAD_RESUME -> {
                resumeDownload(request)
                return START_NOT_STICKY
            }

            ACTION_DOWNLOAD_CANCEL -> {
                cancelDownload(request)
                return START_NOT_STICKY
            }
        }

        startDownload(request)
        return START_NOT_STICKY
    }

    private fun startDownload(
        request: ArtifactDownloadRequest,
    ) {
        synchronized(serviceLock) {
            if (
                workers.containsKey(
                    request.artifactId
                )
            ) {
                return
            }

            val control = DownloadControl()
            controls[request.artifactId] =
                control

            val initialState =
                request.toDownloadUiState(
                    stage =
                        if (
                            hasArtifactResumeData(
                                this,
                                request,
                            )
                        ) {
                            "准备断点续传"
                        } else {
                            "准备后台下载"
                        },
                )
            publishState(
                request,
                initialState,
            )

            val worker = Thread {
                runDownloadTask(
                    request,
                    control,
                )
            }.apply {
                name =
                    "YagaYHub-Artifact-" +
                        request.artifactId
            }
            workers[request.artifactId] =
                worker

            if (workers.size == 1) {
                startForeground(
                    DOWNLOAD_NOTIFICATION_ID,
                    buildDownloadSummaryNotification(
                        this,
                        workers.size,
                    ),
                )
            } else {
                updateDownloadSummaryNotification(
                    this,
                    workers.size,
                )
            }
            worker.start()
        }
    }

    private fun pauseDownload(
        request: ArtifactDownloadRequest,
    ) {
        val control =
            controls[request.artifactId]
                ?: return
        control.pause()

        val state =
            taskStates[request.artifactId]
                ?: request.toDownloadUiState()
        publishState(
            request,
            state.copy(
                stage = "已暂停 · 断点已保留",
                running = true,
                paused = true,
                cancelled = false,
            ),
        )
    }

    private fun resumeDownload(
        request: ArtifactDownloadRequest,
    ) {
        val control =
            controls[request.artifactId]
        if (control != null) {
            control.resume()
            val state =
                taskStates[request.artifactId]
                    ?: request.toDownloadUiState()
            publishState(
                request,
                state.copy(
                    stage = "继续下载…",
                    running = true,
                    paused = false,
                    cancelled = false,
                ),
            )
            return
        }

        // The process/service may have been recreated while paused.
        // Starting the same request is safe because the range metadata and
        // partial file are keyed by artifact id.
        startDownload(request)
    }

    private fun cancelDownload(
        request: ArtifactDownloadRequest,
    ) {
        val control =
            controls[request.artifactId]
        if (control != null) {
            control.cancel()
            val state =
                taskStates[request.artifactId]
                    ?: request.toDownloadUiState()
            publishState(
                request,
                state.copy(
                    stage = "正在取消…",
                    running = true,
                    paused = false,
                    cancelled = true,
                ),
            )
            return
        }

        deleteArtifactResumeData(
            this,
            request,
        )
        val cancelledState =
            request.toDownloadUiState(
                stage = "已取消",
                running = false,
                cancelled = true,
                message =
                    "下载已取消，未完成断点已清理",
            )
        publishState(
            request,
            cancelledState,
        )
    }

    private fun publishState(
        request: ArtifactDownloadRequest,
        state: DownloadUiState,
    ) {
        taskStates[request.artifactId] =
            state
        saveDownloadUiState(this, state)
        if (state.running) {
            saveActiveDownloadState(
                this,
                state,
            )
        } else {
            removeActiveDownloadState(
                this,
                request.artifactId,
            )
        }
        broadcastDownloadState(
            this,
            state,
        )
        updateDownloadTaskNotification(
            this,
            request.artifactId,
            state,
        )
    }

    private fun runDownloadTask(
        request: ArtifactDownloadRequest,
        control: DownloadControl,
    ) {
        val token = loadGithubToken(this)
        val destinationTreeUri =
            loadDownloadDirectoryUri(this)
        val rootEnhanced =
            loadRootCleanupEnabled(this) &&
                hasRootAccess()
        var latestDownloaded = 0L
        var latestTotal =
            request.expectedSizeBytes

        val result =
            downloadArtifactZip(
                context = this,
                owner = request.owner,
                repo = request.repo,
                runId = request.runId,
                artifactId = request.artifactId,
                artifactName =
                    request.artifactName,
                token = token,
                destinationTreeUri =
                    destinationTreeUri,
                rootEnhancedCleanup =
                    rootEnhanced,
                expectedSizeBytes =
                    request.expectedSizeBytes,
                control = control,
                onProgress = {
                        downloaded,
                        total,
                        stage,
                    ->
                    latestDownloaded =
                        downloaded
                    latestTotal =
                        total ?: latestTotal
                    publishState(
                        request,
                        request.toDownloadUiState(
                            stage = stage,
                            downloadedBytes =
                                downloaded,
                            totalBytes =
                                latestTotal,
                            running = true,
                            paused =
                                control.paused,
                            cancelled =
                                control.cancelled,
                        ),
                    )
                },
            )

        val cancelled =
            control.cancelled ||
                result.cancelled
        if (cancelled) {
            deleteArtifactResumeData(
                this,
                request,
            )
        }

        val finalState =
            request.toDownloadUiState(
                stage =
                    when {
                        cancelled ->
                            "已取消"
                        result.success ->
                            "下载完成"
                        else ->
                            "下载失败 · 再次下载可断点重试"
                    },
                downloadedBytes =
                    latestDownloaded,
                totalBytes =
                    latestTotal,
                running = false,
                paused = false,
                cancelled = cancelled,
                message = result.message,
                apks =
                    result.extractedApks,
                fileUri =
                    result.outputUri
                        ?.toString(),
            )

        publishState(
            request,
            finalState,
        )
        if (result.success) {
            appendDownloadHistory(
                this,
                finalState,
            )
        }

        synchronized(serviceLock) {
            workers.remove(
                request.artifactId
            )
            controls.remove(
                request.artifactId
            )
            taskStates.remove(
                request.artifactId
            )

            if (workers.isEmpty()) {
                stopForeground(
                    STOP_FOREGROUND_REMOVE
                )
                stopSelf()
            } else {
                updateDownloadSummaryNotification(
                    this,
                    workers.size,
                )
            }
        }
    }

    override fun onDestroy() {
        controls.values.forEach {
            it.cancel()
        }
        controls.clear()
        workers.clear()
        taskStates.clear()
        super.onDestroy()
    }
}

private data class ArtifactDownloadRequest(
    val appName: String,
    val owner: String,
    val repo: String,
    val runId: Long,
    val artifactId: Long,
    val artifactName: String,
    val expectedSizeBytes: Long?,
) {
    fun toIntent(context: Context): Intent =
        Intent(context, ArtifactDownloadService::class.java).apply {
            putExtra(EXTRA_DOWNLOAD_APP_NAME, appName)
            putExtra(EXTRA_DOWNLOAD_OWNER, owner)
            putExtra(EXTRA_DOWNLOAD_REPO, repo)
            putExtra(EXTRA_DOWNLOAD_RUN_ID, runId)
            putExtra(EXTRA_DOWNLOAD_ARTIFACT_ID, artifactId)
            putExtra(EXTRA_DOWNLOAD_ARTIFACT_NAME, artifactName)
            expectedSizeBytes?.let {
                putExtra(EXTRA_DOWNLOAD_EXPECTED_SIZE, it)
            }
        }

    companion object {
        fun fromIntent(
            intent: Intent,
        ): ArtifactDownloadRequest? {
            val appName = intent.getStringExtra(
                EXTRA_DOWNLOAD_APP_NAME,
            ) ?: return null
            val owner = intent.getStringExtra(
                EXTRA_DOWNLOAD_OWNER,
            ) ?: return null
            val repo = intent.getStringExtra(
                EXTRA_DOWNLOAD_REPO,
            ) ?: return null
            val runId = intent.getLongExtra(
                EXTRA_DOWNLOAD_RUN_ID,
                -1L,
            )
            val artifactId = intent.getLongExtra(
                EXTRA_DOWNLOAD_ARTIFACT_ID,
                -1L,
            )
            if (runId <= 0L || artifactId <= 0L) {
                return null
            }
            return ArtifactDownloadRequest(
                appName = appName,
                owner = owner,
                repo = repo,
                runId = runId,
                artifactId = artifactId,
                artifactName = intent.getStringExtra(
                    EXTRA_DOWNLOAD_ARTIFACT_NAME,
                ).orEmpty().ifBlank {
                    repo + "-" + artifactId
                },
                expectedSizeBytes = intent.getLongExtra(
                    EXTRA_DOWNLOAD_EXPECTED_SIZE,
                    -1L,
                ).takeIf { it > 0L },
            )
        }
    }
}

private fun ArtifactDownloadRequest.toDownloadUiState(
    stage: String = "准备后台下载",
    downloadedBytes: Long = 0L,
    totalBytes: Long? = expectedSizeBytes,
    running: Boolean = true,
    paused: Boolean = false,
    cancelled: Boolean = false,
    message: String? = null,
    apks: List<ExtractedApk> = emptyList(),
    fileUri: String? = null,
): DownloadUiState =
    DownloadUiState(
        appName = appName,
        owner = owner,
        repo = repo,
        runId = runId,
        artifactId = artifactId,
        artifactName = artifactName,
        fileName =
            sanitizeArtifactZipName(
                repo,
                artifactName,
                artifactId,
            ),
        fileUri = fileUri,
        stage = stage,
        downloadedBytes = downloadedBytes,
        totalBytes = totalBytes,
        running = running,
        paused = paused,
        cancelled = cancelled,
        message = message,
        apks = apks,
    )

private fun DownloadUiState.toDownloadRequestOrNull():
    ArtifactDownloadRequest? {
    if (
        owner.isBlank() ||
        repo.isBlank() ||
        runId <= 0L ||
        artifactId <= 0L
    ) {
        return null
    }
    return ArtifactDownloadRequest(
        appName = appName,
        owner = owner,
        repo = repo,
        runId = runId,
        artifactId = artifactId,
        artifactName =
            artifactName.ifBlank {
                fileName
                    .removeSuffix(".zip")
                    .ifBlank {
                        repo + "-" +
                            artifactId
                    }
            },
        expectedSizeBytes =
            totalBytes?.takeIf {
                it > 0L
            },
    )
}

private fun controlArtifactDownload(
    context: Context,
    state: DownloadUiState,
    action: String,
) {
    val request =
        state.toDownloadRequestOrNull()
            ?: return
    val intent =
        request.toIntent(context)
            .setAction(action)
    ContextCompat.startForegroundService(
        context,
        intent,
    )
}

private fun artifactResumeKey(
    owner: String,
    repo: String,
    artifactId: Long,
): String =
    (
        owner + "_" +
            repo + "_" +
            artifactId
        )
        .replace(
            Regex(
                "[^A-Za-z0-9._-]"
            ),
            "_",
        )

private fun artifactResumeFiles(
    context: Context,
    request: ArtifactDownloadRequest,
): Pair<File, File> {
    val directory =
        File(
            context.filesDir,
            "artifact_parts",
        ).apply {
            mkdirs()
        }
    val key =
        artifactResumeKey(
            request.owner,
            request.repo,
            request.artifactId,
        )
    return File(
        directory,
        key + ".part",
    ) to File(
        directory,
        key + ".json",
    )
}

private fun hasArtifactResumeData(
    context: Context,
    request: ArtifactDownloadRequest,
): Boolean {
    val (part, meta) =
        artifactResumeFiles(
            context,
            request,
        )
    return part.exists() &&
        meta.exists() &&
        part.length() > 0L
}

private fun deleteArtifactResumeData(
    context: Context,
    request: ArtifactDownloadRequest,
) {
    val (part, meta) =
        artifactResumeFiles(
            context,
            request,
        )
    runCatching {
        part.delete()
    }
    runCatching {
        meta.delete()
    }
}

private data class ActionsArtifact(
    val id: Long,
    val name: String,
    val sizeBytes: Long,
)

private data class ArtifactSelection(
    val appName: String,
    val owner: String,
    val repo: String,
    val runId: Long,
    val artifacts: List<ActionsArtifact>,
)

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
    val repoUpdatedTime: String? = null,
    val repoPushedTime: String? = null,
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

private enum class AppSortMode(
    val label: String,
    val defaultAscending: Boolean,
) {
    DEFAULT("默认顺序", true),
    ACTIONS_TIME("Actions 更新时间", false),
    PROJECT_TIME("项目更新时间", false),
    PUSH_TIME("代码更新时间", false),
    NAME("名称", true),
    INSTALLED_TIME("本机更新时间", false),
    AI_COUNT("AI 绑定数量", false),
    AI_RECENT("AI 最近绑定", false),
}

private data class ChatBinding(
    val repoKey: String,
    val title: String,
    val url: String,
    val addedAt: Long = System.currentTimeMillis(),
)

private data class QuickChatBindingRequest(
    val url: String,
    val title: String,
    val windowId: String? = null,
    val requesterPackage: String? = null,
)

private data class DownloadUiState(
    val appName: String,
    val owner: String = "",
    val repo: String = "",
    val runId: Long = 0L,
    val artifactId: Long = 0L,
    val artifactName: String = "",
    val fileName: String = "",
    val fileUri: String? = null,
    val stage: String = "准备下载",
    val downloadedBytes: Long = 0L,
    val totalBytes: Long? = null,
    val running: Boolean = true,
    val paused: Boolean = false,
    val cancelled: Boolean = false,
    val message: String? = null,
    val apks: List<ExtractedApk> = emptyList(),
)

private data class DownloadHistoryEntry(
    val id: Long,
    val completedAt: Long,
    val state: DownloadUiState,
)

private val knownProjects = listOf(
    ProjectSpec("YagaYHub", "com.yagay.YagaYHub", "YagaYHub", "YagaY 应用统一入口与项目管理"),
    ProjectSpec("FloatLens", "com.yagay.floatlens", "FloatLens", "悬浮识别、截图与屏幕工具"),
    ProjectSpec("List Cleaner", "com.yagay.ListCleaner", "ListCleaner", "分享面板、组件与列表清理"),
    ProjectSpec("MiniWindowGuard", "com.yagay.MiniWindowGuard", "MiniWindowGuard", "后台播放与小窗增强"),
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
private fun HubScreen(
    context: Context,
    chatBindingRevision: Int,
    quickChatBindingRequest: QuickChatBindingRequest?,
    onQuickChatBindingDismiss: () -> Unit,
    onQuickChatBindingSelected: (HubApp) -> Unit,
) {
    var apps by remember { mutableStateOf(emptyList<HubApp>()) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(AppFilter.INSTALLED) }
    var refreshKey by remember { mutableStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }
    var initialLoadCompleted by remember { mutableStateOf(false) }
    var scrollToTopRevision by remember { mutableIntStateOf(0) }
    var isAutoRefreshing by remember { mutableStateOf(false) }
    var autoRefreshCursor by remember { mutableIntStateOf(0) }
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
    var sortMode by remember { mutableStateOf(loadSortMode(context)) }
    var sortAscending by remember {
        mutableStateOf(loadSortAscending(context, sortMode.defaultAscending))
    }
    var showSortDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var pendingInstallApk by remember { mutableStateOf<ExtractedApk?>(null) }
    var downloadUiState by remember { mutableStateOf(loadDownloadUiState(context)) }
    var downloadHistory by remember { mutableStateOf(loadDownloadHistory(context)) }
    var showDownloadHistory by remember { mutableStateOf(false) }
    var selectedHistoryEntry by remember {
        mutableStateOf<DownloadHistoryEntry?>(null)
    }
    var showDownloadPanel by remember {
        mutableStateOf(downloadUiState?.let { !it.running } == true)
    }
    var pendingDownloadRequests by remember {
        mutableStateOf<List<ArtifactDownloadRequest>>(emptyList())
    }
    var artifactSelection by remember { mutableStateOf<ArtifactSelection?>(null) }
    var bindingListApp by remember { mutableStateOf<HubApp?>(null) }
    var bindingUiRevision by remember { mutableIntStateOf(0) }
    val appListState = rememberLazyListState()
    val appGridState = rememberLazyGridState()
    val lifecycleOwner = LocalLifecycleOwner.current
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
        val pending = pendingDownloadRequests
        pendingDownloadRequests = emptyList()
        pending.forEach { request ->
            startArtifactDownloadService(context, request)
        }
        if (!granted && pending.isNotEmpty()) {
            Toast.makeText(
                context,
                "通知权限未授予；后台下载仍会继续，但通知栏进度可能不可见",
                Toast.LENGTH_LONG,
            ).show()
        }
    }

    val startSelectedArtifactDownload:
        (ArtifactSelection, ActionsArtifact) -> Unit = { selection, artifact ->
            val request = ArtifactDownloadRequest(
                appName = selection.appName,
                owner = selection.owner,
                repo = selection.repo,
                runId = selection.runId,
                artifactId = artifact.id,
                artifactName = artifact.name,
                expectedSizeBytes = artifact.sizeBytes.takeIf { it > 0L },
            )
            if (
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) !=
                    PackageManager.PERMISSION_GRANTED
            ) {
                val shouldRequestPermission =
                    pendingDownloadRequests.isEmpty()
                pendingDownloadRequests =
                    pendingDownloadRequests + request
                if (shouldRequestPermission) {
                    notificationPermissionLauncher.launch(
                        Manifest.permission.POST_NOTIFICATIONS
                    )
                }
                downloadUiState =
                    request.toDownloadUiState()
                showDownloadPanel = true
            } else {
                startArtifactDownloadService(context, request)
                downloadUiState =
                    request.toDownloadUiState()
                showDownloadPanel = true
            }
        }

    DisposableEffect(context) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(receiverContext: Context?, intent: Intent?) {
                if (intent?.action != ACTION_DOWNLOAD_STATE) return
                val state = downloadUiStateFromIntent(intent) ?: return
                downloadUiState = state
                if (!state.running) {
                    downloadHistory = loadDownloadHistory(context)
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
        val shouldScrollToTop =
            !initialLoadCompleted || isRefreshing
        try {
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
            if (shouldScrollToTop) {
                scrollToTopRevision++
            }
        } finally {
            initialLoadCompleted = true
            isRefreshing = false
        }
    }

    val requestRefresh: () -> Unit = {
        if (!isRefreshing && !isAutoRefreshing) {
            isRefreshing = true
            refreshKey++
        }
    }

    LaunchedEffect(githubToken, lifecycleOwner) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (true) {
                delay(
                    if (githubToken.isNotBlank()) {
                        AUTO_REFRESH_INTERVAL_AUTH_MS
                    } else {
                        AUTO_REFRESH_INTERVAL_ANON_MS
                    }
                )
                if (isRefreshing || isAutoRefreshing || apps.isEmpty()) continue

                isAutoRefreshing = true
                try {
                    val result = withContext(Dispatchers.IO) {
                        refreshDynamicGithubState(
                            apps = apps,
                            token = githubToken,
                            stableCursor = autoRefreshCursor,
                        )
                    }
                    apps = result.apps
                    autoRefreshCursor = result.nextStableCursor
                } finally {
                    isAutoRefreshing = false
                }
            }
        }
    }

    val bindingStats = remember(
        chatBindingRevision,
        bindingUiRevision,
    ) {
        buildAiBindingStats(loadAllChatBindings(context))
    }

    val visibleApps = remember(
        apps,
        query,
        filter,
        sortMode,
        sortAscending,
        bindingStats,
    ) {
        val q = query.trim().lowercase()
        val filtered = apps.filter { app ->
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
        sortHubApps(
            apps = filtered,
            mode = sortMode,
            ascending = sortAscending,
            bindingStats = bindingStats,
        )
    }

    LaunchedEffect(
        scrollToTopRevision,
        layoutMode,
        visibleApps.size,
    ) {
        if (scrollToTopRevision <= 0) return@LaunchedEffect
        if (layoutMode == LayoutMode.LIST) {
            appListState.scrollToItem(0, 0)
        } else {
            appGridState.scrollToItem(0, 0)
        }
    }

    if (showSortDialog) {
        AlertDialog(
            onDismissRequest = { showSortDialog = false },
            title = { Text("项目排序") },
            text = {
                Column {
                    AppSortMode.entries.forEach { mode ->
                        FilterChip(
                            selected = sortMode == mode,
                            onClick = {
                                sortMode = mode
                                sortAscending = mode.defaultAscending
                                saveSortSettings(
                                    context,
                                    sortMode,
                                    sortAscending,
                                )
                            },
                            label = { Text(mode.label) },
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "方向",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        FilterChip(
                            selected = sortAscending,
                            onClick = {
                                sortAscending = true
                                saveSortSettings(
                                    context,
                                    sortMode,
                                    true,
                                )
                            },
                            label = { Text("升序") },
                        )
                        FilterChip(
                            selected = !sortAscending,
                            onClick = {
                                sortAscending = false
                                saveSortSettings(
                                    context,
                                    sortMode,
                                    false,
                                )
                            },
                            label = { Text("降序") },
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSortDialog = false }) {
                    Text("完成")
                }
            },
        )
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("YagaYHub") },
            text = {
                Text("单击打开 · 长按详情 · Actions 一键构建入口")
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("知道了")
                }
            },
        )
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
                Text(
                    "YagaYHub",
                    modifier = Modifier
                        .weight(1f)
                        .clickable { showAboutDialog = true },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
                IconButton(
                    onClick = {
                        openChatPopup(
                            context = context,
                            url = null,
                        )
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.AutoAwesome,
                        contentDescription = "AI",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = { showSortDialog = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Sort,
                        contentDescription = "排序",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = {
                        layoutMode = if (layoutMode == LayoutMode.LIST) {
                            LayoutMode.GRID
                        } else {
                            LayoutMode.LIST
                        }
                        saveLayoutMode(context, layoutMode)
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        if (layoutMode == LayoutMode.LIST) {
                            Icons.Outlined.GridView
                        } else {
                            Icons.Outlined.ViewList
                        },
                        contentDescription = if (layoutMode == LayoutMode.LIST) {
                            "切换到网格"
                        } else {
                            "切换到列表"
                        },
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = requestRefresh,
                    enabled = !isRefreshing && !isAutoRefreshing,
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Refresh,
                        contentDescription = "刷新",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = {
                        downloadHistory = loadDownloadHistory(context)
                        showDownloadHistory = true
                    },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Download,
                        contentDescription = "下载",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = { showSettings = true },
                    modifier = Modifier.size(36.dp),
                ) {
                    Icon(
                        Icons.Outlined.Settings,
                        contentDescription = "设置",
                        modifier = Modifier.size(20.dp),
                    )
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
            PullToRefreshBox(
                isRefreshing = isRefreshing,
                onRefresh = requestRefresh,
                modifier = Modifier.fillMaxSize(),
            ) {
                if (visibleApps.isEmpty()) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
                    ) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(240.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    "没有匹配的 App",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                } else {
                    val itemContent: @Composable (HubApp) -> Unit = { app ->
                    val chatBindings = remember(
                        chatBindingRevision,
                        bindingUiRevision,
                        app.repoOwner,
                        app.repo,
                    ) {
                        app.repo?.let { repo ->
                            loadChatBindings(
                                context = context,
                                owner = app.repoOwner,
                                repo = repo,
                            )
                        }.orEmpty()
                    }
                    val chatBinding = chatBindings.firstOrNull()

                    val onClick: () -> Unit = {
                        when {
                            app.repo != null -> openUrl(
                                context,
                                "https://github.com/" + app.repoOwner + "/" + app.repo
                            )
                            app.launchIntent != null -> openApp(context, app)
                            app.installed -> openAppDetails(context, app.packageName)
                            else -> Unit
                        }
                    }
                    val onIconClick: () -> Unit = {
                        when {
                            app.launchIntent != null -> openApp(context, app)
                            app.installed -> Toast.makeText(
                                context,
                                "该应用没有可启动入口",
                                Toast.LENGTH_SHORT,
                            ).show()
                            else -> Toast.makeText(
                                context,
                                "该应用尚未安装",
                                Toast.LENGTH_SHORT,
                            ).show()
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
                    val onLatestActionClick: () -> Unit = {
                        app.repo?.let { repo ->
                            val base =
                                "https://github.com/" + app.repoOwner + "/" + repo
                            val url = app.latestRunId?.let { runId ->
                                base + "/actions/runs/" + runId
                            } ?: (base + "/actions")
                            openUrl(context, url)
                        }
                        Unit
                    }
                    val onArtifactClick: () -> Unit = {
                        val repo = app.repo
                        val runId = app.latestRunId
                        when {
                            repo == null -> {
                                Toast.makeText(context, "未配置 GitHub 仓库", Toast.LENGTH_SHORT).show()
                            }
                            app.actionsStatus != ActionsStatus.SUCCESS -> {
                                Toast.makeText(context, "最新一次 Actions 未成功，不抓取 ZIP", Toast.LENGTH_SHORT).show()
                            }
                            runId == null -> {
                                Toast.makeText(context, "最新成功构建没有可下载 ZIP，或产物已过期", Toast.LENGTH_SHORT).show()
                            }
                            githubToken.isBlank() -> {
                                Toast.makeText(context, "请先在设置中保存 GitHub Token", Toast.LENGTH_SHORT).show()
                                showSettings = true
                            }
                            else -> {
                                scope.launch {
                                    val artifacts = withContext(Dispatchers.IO) {
                                        fetchArtifactOptions(
                                            owner = app.repoOwner,
                                            repo = repo,
                                            runId = runId,
                                            token = githubToken,
                                        )
                                    }
                                    when (artifacts.size) {
                                        0 -> Toast.makeText(
                                            context,
                                            "最新成功构建没有可下载 ZIP，或产物已过期",
                                            Toast.LENGTH_SHORT,
                                        ).show()
                                        1 -> startSelectedArtifactDownload(
                                            ArtifactSelection(
                                                appName = app.name,
                                                owner = app.repoOwner,
                                                repo = repo,
                                                runId = runId,
                                                artifacts = artifacts,
                                            ),
                                            artifacts.first(),
                                        )
                                        else -> {
                                            artifactSelection = ArtifactSelection(
                                                appName = app.name,
                                                owner = app.repoOwner,
                                                repo = repo,
                                                runId = runId,
                                                artifacts = artifacts,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    val onChatClick: () -> Unit = {
                        if (app.repo != null) {
                            if (chatBindings.isEmpty()) {
                                Toast.makeText(
                                    context,
                                    "当前项目还没有绑定页面，请点击“绑定”添加",
                                    Toast.LENGTH_SHORT,
                                ).show()
                            } else {
                                bindingListApp = app
                            }
                        }
                    }
                    val onBindClick: () -> Unit = {
                        if (app.repo != null) {
                            startChatGptBinding(
                                context = context,
                                app = app,
                                currentBinding = null,
                            )
                        }
                    }

                    if (layoutMode == LayoutMode.LIST) {
                        AppListEntry(
                            app = app,
                            onClick = onClick,
                            onIconClick = onIconClick,
                            onLongClick = onLongClick,
                            onActionsClick = onActionsClick,
                            onLatestActionClick = onLatestActionClick,
                            onArtifactClick = onArtifactClick,
                            chatBinding = chatBinding,
                            chatBindingCount = chatBindings.size,
                            onChatClick = onChatClick,
                            onBindClick = onBindClick,
                        )
                    } else {
                        AppEntry(
                            app = app,
                            onClick = onClick,
                            onIconClick = onIconClick,
                            onLongClick = onLongClick,
                            onActionsClick = onActionsClick,
                            onLatestActionClick = onLatestActionClick,
                            onArtifactClick = onArtifactClick,
                            chatBinding = chatBinding,
                            chatBindingCount = chatBindings.size,
                            onChatClick = onChatClick,
                            onBindClick = onBindClick,
                        )
                    }
                }

                if (layoutMode == LayoutMode.LIST) {
                    LazyColumn(
                        state = appListState,
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
                            state = appGridState,
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
}

    artifactSelection?.let { selection ->
        var selectedArtifactIds by remember(selection) {
            mutableStateOf<Set<Long>>(emptySet())
        }
        AlertDialog(
            onDismissRequest = { artifactSelection = null },
            title = {
                Text(
                    "选择下载 ZIP · " + selection.appName,
                )
            },
            text = {
                LazyColumn(
                    modifier = Modifier.height(360.dp),
                    verticalArrangement =
                        Arrangement.spacedBy(6.dp),
                ) {
                    lazyItems(
                        items = selection.artifacts,
                        key = { it.id },
                    ) { artifact ->
                        val checked =
                            artifact.id in selectedArtifactIds
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    selectedArtifactIds =
                                        if (checked) {
                                            selectedArtifactIds -
                                                artifact.id
                                        } else {
                                            selectedArtifactIds +
                                                artifact.id
                                        }
                                },
                            shape = RoundedCornerShape(12.dp),
                            color =
                                MaterialTheme.colorScheme
                                    .surfaceContainer,
                        ) {
                            Row(
                                modifier = Modifier.padding(
                                    horizontal = 10.dp,
                                    vertical = 8.dp,
                                ),
                                verticalAlignment =
                                    Alignment.CenterVertically,
                            ) {
                                Checkbox(
                                    checked = checked,
                                    onCheckedChange = {
                                        selectedArtifactIds =
                                            if (checked) {
                                                selectedArtifactIds -
                                                    artifact.id
                                            } else {
                                                selectedArtifactIds +
                                                    artifact.id
                                            }
                                    },
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                ) {
                                    Text(
                                        if (
                                            artifact.name.endsWith(
                                                ".zip",
                                                ignoreCase = true,
                                            )
                                        ) {
                                            artifact.name
                                        } else {
                                            artifact.name + ".zip"
                                        },
                                        fontWeight =
                                            FontWeight.SemiBold,
                                        maxLines = 2,
                                        overflow =
                                            TextOverflow.Ellipsis,
                                    )
                                    if (artifact.sizeBytes > 0L) {
                                        Spacer(
                                            Modifier.height(3.dp),
                                        )
                                        Text(
                                            formatFileSize(
                                                artifact.sizeBytes,
                                            ),
                                            style =
                                                MaterialTheme.typography
                                                    .bodySmall,
                                            color =
                                                MaterialTheme.colorScheme
                                                    .onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val selected =
                            selection.artifacts.filter {
                                it.id in selectedArtifactIds
                            }
                        artifactSelection = null
                        selected.forEach { artifact ->
                            startSelectedArtifactDownload(
                                selection,
                                artifact,
                            )
                        }
                    },
                    enabled =
                        selectedArtifactIds.isNotEmpty(),
                ) {
                    Text(
                        "下载 " +
                            selectedArtifactIds.size +
                            " 个",
                    )
                }
            },
            dismissButton = {
                Row {
                    TextButton(
                        onClick = {
                            selectedArtifactIds =
                                if (
                                    selectedArtifactIds.size ==
                                    selection.artifacts.size
                                ) {
                                    emptySet()
                                } else {
                                    selection.artifacts
                                        .map { it.id }
                                        .toSet()
                                }
                        },
                    ) {
                        Text(
                            if (
                                selectedArtifactIds.size ==
                                selection.artifacts.size
                            ) {
                                "全不选"
                            } else {
                                "全选"
                            },
                        )
                    }
                    TextButton(
                        onClick = {
                            artifactSelection = null
                        },
                    ) {
                        Text("取消")
                    }
                }
            },
        )
    }

    bindingListApp?.let { app ->
        val repo = app.repo
        if (repo == null) {
            bindingListApp = null
        } else {
            val bindings = remember(
                app.repoOwner,
                repo,
                chatBindingRevision,
                bindingUiRevision,
            ) {
                loadChatBindings(
                    context = context,
                    owner = app.repoOwner,
                    repo = repo,
                )
            }

            AlertDialog(
                onDismissRequest = { bindingListApp = null },
                title = {
                    Text(
                        "AI · " + app.name +
                            if (bindings.isNotEmpty()) " (" + bindings.size + ")" else ""
                    )
                },
                text = {
                    if (bindings.isEmpty()) {
                        Text("当前没有绑定页面")
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(420.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            lazyItems(
                                items = bindings,
                                key = { it.url },
                            ) { binding ->
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(12.dp),
                                    color = MaterialTheme.colorScheme.surfaceContainer,
                                ) {
                                    Column(
                                        modifier = Modifier.padding(
                                            horizontal = 12.dp,
                                            vertical = 10.dp,
                                        ),
                                    ) {
                                        Text(
                                            binding.title,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Spacer(Modifier.height(3.dp))
                                        Text(
                                            if (binding.addedAt > 0L) {
                                                "绑定时间 · " + formatLocalTime(binding.addedAt)
                                            } else {
                                                "旧绑定"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.End,
                                            verticalAlignment = Alignment.CenterVertically,
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    removeChatBinding(
                                                        context = context,
                                                        url = binding.url,
                                                    )
                                                    removeChatBindingFromYBrowser(
                                                        context = context,
                                                        url = binding.url,
                                                    )
                                                    bindingUiRevision++
                                                },
                                            ) {
                                                Text("取消绑定")
                                            }
                                            TextButton(
                                                onClick = {
                                                    bindingListApp = null
                                                    openChatPopup(
                                                        context = context,
                                                        url = binding.url,
                                                        bindingRepoKey = binding.repoKey,
                                                        bindingProject = app.name,
                                                        bindingTitle = binding.title,
                                                    )
                                                },
                                            ) {
                                                Text("进入")
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            bindingListApp = null
                            startChatGptBinding(
                                context = context,
                                app = app,
                                currentBinding = null,
                            )
                        },
                    ) {
                        Text("添加绑定")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bindingListApp = null }) {
                        Text("关闭")
                    }
                },
            )
        }
    }

    if (quickChatBindingRequest != null) {
        val request = quickChatBindingRequest
        val bindingApps = apps
            .filter { it.repo != null }
            .distinctBy { (it.repoOwner + "/" + it.repo).lowercase() }

        AlertDialog(
            onDismissRequest = onQuickChatBindingDismiss,
            title = { Text("绑定当前 AI") },
            text = {
                Column {
                    Text(
                        request.title,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    if (bindingApps.isEmpty()) {
                        Text("正在读取项目…")
                    } else {
                        LazyColumn(
                            modifier = Modifier.height(360.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            lazyItems(
                                items = bindingApps,
                                key = { it.repoOwner + "/" + it.repo },
                            ) { app ->
                                val repo = app.repo ?: return@lazyItems
                                val selected = loadChatBindings(
                                    context = context,
                                    owner = app.repoOwner,
                                    repo = repo,
                                ).any { existing ->
                                    sameChatBindingUrl(existing.url, request.url)
                                }
                                Surface(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            onQuickChatBindingSelected(app)
                                        },
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.primaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.surfaceContainer
                                    },
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 12.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Column(Modifier.weight(1f)) {
                                            Text(
                                                app.name,
                                                fontWeight = FontWeight.SemiBold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                            )
                                            Text(
                                                app.repoOwner + "/" + repo,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                            )
                                        }
                                        if (selected) {
                                            Text(
                                                "当前已绑定",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = MaterialTheme.colorScheme.primary,
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = onQuickChatBindingDismiss) {
                    Text("取消")
                }
            },
        )
    }

    if (showDownloadHistory) {
        DownloadHistoryDialog(
            history = downloadHistory,
            activeState = downloadUiState?.takeIf { it.running },
            onDismiss = { showDownloadHistory = false },
            onOpenActive = {
                showDownloadHistory = false
                showDownloadPanel = true
            },
            onOpenHistory = { entry ->
                showDownloadHistory = false
                selectedHistoryEntry = entry
            },
            onClearHistory = {
                clearDownloadHistory(context)
                downloadHistory = emptyList()
            },
        )
    }

    selectedHistoryEntry?.let { entry ->
        DownloadPanel(
            state = entry.state,
            onDismiss = { selectedHistoryEntry = null },
            onInstall = { apk ->
                selectedHistoryEntry = null
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

    if (showDownloadPanel && downloadUiState != null) {
        DownloadPanel(
            state = downloadUiState!!,
            onDismiss = {
                showDownloadPanel = false
                if (downloadUiState?.running != true) {
                    clearDownloadUiState(context)
                    downloadUiState = null
                }
            },
            onInstall = { apk ->
                // Installing is the terminal action for a completed download.
                // Close immediately and forget the persisted completion panel so
                // an app update/restart cannot resurrect the old dialog.
                showDownloadPanel = false
                clearDownloadUiState(context)
                downloadUiState = null

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
    val state =
        request.toDownloadUiState()
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


private fun downloadTaskNotificationId(
    artifactId: Long,
): Int =
    20_000 + (artifactId xor (artifactId ushr 32))
        .toInt()
        .and(0x3fff)

private fun updateDownloadTaskNotification(
    context: Context,
    artifactId: Long,
    state: DownloadUiState,
) {
    context.getSystemService(NotificationManager::class.java)
        .notify(
            downloadTaskNotificationId(artifactId),
            buildDownloadNotification(context, state),
        )
}

private fun buildDownloadSummaryNotification(
    context: Context,
    activeCount: Int,
): Notification {
    val openAppIntent = Intent(
        context,
        MainActivity::class.java,
    ).apply {
        addFlags(
            Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP,
        )
    }
    val pendingIntent = PendingIntent.getActivity(
        context,
        1002,
        openAppIntent,
        PendingIntent.FLAG_UPDATE_CURRENT or
            PendingIntent.FLAG_IMMUTABLE,
    )
    return Notification.Builder(
        context,
        DOWNLOAD_CHANNEL_ID,
    )
        .setSmallIcon(android.R.drawable.stat_sys_download)
        .setContentTitle("YagaYHub · 并行下载")
        .setContentText("正在下载 " + activeCount + " 个任务")
        .setContentIntent(pendingIntent)
        .setOnlyAlertOnce(true)
        .setOngoing(true)
        .build()
}

private fun updateDownloadSummaryNotification(
    context: Context,
    activeCount: Int,
) {
    context.getSystemService(NotificationManager::class.java)
        .notify(
            DOWNLOAD_NOTIFICATION_ID,
            buildDownloadSummaryNotification(
                context,
                activeCount,
            ),
        )
}

private fun broadcastDownloadState(
    context: Context,
    state: DownloadUiState,
) {
    context.sendBroadcast(
        Intent(ACTION_DOWNLOAD_STATE).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_STATE_APP_NAME, state.appName)
            putExtra(EXTRA_STATE_OWNER, state.owner)
            putExtra(EXTRA_STATE_REPO, state.repo)
            putExtra(EXTRA_STATE_RUN_ID, state.runId)
            putExtra(EXTRA_STATE_ARTIFACT_ID, state.artifactId)
            putExtra(EXTRA_STATE_ARTIFACT_NAME, state.artifactName)
            putExtra(EXTRA_STATE_FILE_NAME, state.fileName)
            state.fileUri?.let {
                putExtra(EXTRA_STATE_FILE_URI, it)
            }
            putExtra(EXTRA_STATE_STAGE, state.stage)
            putExtra(EXTRA_STATE_DOWNLOADED, state.downloadedBytes)
            state.totalBytes?.let { putExtra(EXTRA_STATE_TOTAL, it) }
            putExtra(EXTRA_STATE_RUNNING, state.running)
            putExtra(EXTRA_STATE_PAUSED, state.paused)
            putExtra(EXTRA_STATE_CANCELLED, state.cancelled)
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
        owner = intent.getStringExtra(EXTRA_STATE_OWNER).orEmpty(),
        repo = intent.getStringExtra(EXTRA_STATE_REPO).orEmpty(),
        runId = intent.getLongExtra(EXTRA_STATE_RUN_ID, 0L),
        artifactId = intent.getLongExtra(EXTRA_STATE_ARTIFACT_ID, 0L),
        artifactName =
            intent.getStringExtra(
                EXTRA_STATE_ARTIFACT_NAME,
            ).orEmpty(),
        fileName =
            intent.getStringExtra(
                EXTRA_STATE_FILE_NAME,
            ).orEmpty(),
        fileUri =
            intent.getStringExtra(
                EXTRA_STATE_FILE_URI,
            ),
        stage = intent.getStringExtra(EXTRA_STATE_STAGE).orEmpty(),
        downloadedBytes = intent.getLongExtra(EXTRA_STATE_DOWNLOADED, 0L),
        totalBytes =
            intent.getLongExtra(
                EXTRA_STATE_TOTAL,
                -1L,
            ).takeIf { it > 0L },
        running =
            intent.getBooleanExtra(
                EXTRA_STATE_RUNNING,
                false,
            ),
        paused =
            intent.getBooleanExtra(
                EXTRA_STATE_PAUSED,
                false,
            ),
        cancelled =
            intent.getBooleanExtra(
                EXTRA_STATE_CANCELLED,
                false,
            ),
        message = intent.getStringExtra(EXTRA_STATE_MESSAGE),
        apks = apks,
    )
}

private fun saveDownloadUiState(context: Context, state: DownloadUiState) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(
            DOWNLOAD_STATE_JSON,
            downloadStateToJson(state).toString(),
        )
        .apply()
}

private fun downloadStateToJson(state: DownloadUiState): JSONObject =
    JSONObject().apply {
        put("appName", state.appName)
        put("owner", state.owner)
        put("repo", state.repo)
        put("runId", state.runId)
        put("artifactId", state.artifactId)
        put("artifactName", state.artifactName)
        put("fileName", state.fileName)
        put("fileUri", state.fileUri ?: JSONObject.NULL)
        put("stage", state.stage)
        put("downloadedBytes", state.downloadedBytes)
        put("totalBytes", state.totalBytes ?: JSONObject.NULL)
        put("running", state.running)
        put("paused", state.paused)
        put("cancelled", state.cancelled)
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

private fun downloadStateFromJson(json: JSONObject): DownloadUiState {
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
    return DownloadUiState(
        appName = json.optString("appName"),
        owner = json.optString("owner"),
        repo = json.optString("repo"),
        runId = json.optLong("runId", 0L),
        artifactId = json.optLong("artifactId", 0L),
        artifactName = json.optString("artifactName"),
        fileName = json.optString("fileName"),
        fileUri =
            if (json.isNull("fileUri")) {
                null
            } else {
                json.optString("fileUri")
                    .takeIf { it.isNotBlank() }
            },
        stage = json.optString("stage"),
        downloadedBytes = json.optLong("downloadedBytes", 0L),
        totalBytes =
            if (json.isNull("totalBytes")) {
                null
            } else {
                json.optLong("totalBytes")
            },
        running = json.optBoolean("running", false),
        paused = json.optBoolean("paused", false),
        cancelled = json.optBoolean("cancelled", false),
        message =
            if (json.isNull("message")) {
                null
            } else {
                json.optString("message")
            },
        apks = apks,
    )
}

private fun downloadHistoryKey(state: DownloadUiState): String {
    val downloadedFile =
        state.fileName
            .trim()
            .lowercase()
    if (downloadedFile.isNotBlank()) {
        return downloadedFile
    }

    // Legacy entries from older builds did not persist the downloaded ZIP
    // filename. Keep their old key only as a migration fallback.
    val legacyFile =
        state.apks
            .takeIf { it.isNotEmpty() }
            ?.let(::choosePrimaryApk)
            ?.name
            ?.trim()
            ?.lowercase()
            .orEmpty()
    return legacyFile.ifBlank {
        state.appName
            .trim()
            .lowercase()
    }
}

private fun appendDownloadHistory(
    context: Context,
    state: DownloadUiState,
) {
    if (state.running) return

    val now = System.currentTimeMillis()
    val key = downloadHistoryKey(state)
    val current = loadDownloadHistory(context)
    val merged = buildList {
        add(
            DownloadHistoryEntry(
                id = now,
                completedAt = now,
                state = state,
            )
        )
        current
            .filterNot { downloadHistoryKey(it.state) == key }
            .take(DOWNLOAD_HISTORY_LIMIT - 1)
            .forEach(::add)
    }
    saveDownloadHistory(context, merged)
}

private fun saveDownloadHistory(
    context: Context,
    history: List<DownloadHistoryEntry>,
) {
    val json = JSONArray().apply {
        history.take(DOWNLOAD_HISTORY_LIMIT).forEach { entry ->
            put(
                JSONObject()
                    .put("id", entry.id)
                    .put("completedAt", entry.completedAt)
                    .put("state", downloadStateToJson(entry.state))
            )
        }
    }
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(DOWNLOAD_HISTORY_JSON, json.toString())
        .apply()
}

private fun loadDownloadHistory(
    context: Context,
): List<DownloadHistoryEntry> {
    val raw = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getString(DOWNLOAD_HISTORY_JSON, null)
        ?: return emptyList()

    return runCatching {
        val array = JSONArray(raw)
        buildList {
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                val stateJson = item.optJSONObject("state") ?: continue
                add(
                    DownloadHistoryEntry(
                        id = item.optLong("id", index.toLong()),
                        completedAt = item.optLong("completedAt", 0L),
                        state = downloadStateFromJson(stateJson),
                    )
                )
            }
        }.sortedByDescending { it.completedAt }
    }.getOrDefault(emptyList())
}

private fun clearDownloadHistory(context: Context) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(DOWNLOAD_HISTORY_JSON)
        .apply()
}

private fun clearDownloadUiState(context: Context) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .remove(DOWNLOAD_STATE_JSON)
        .apply()
}

private fun loadDownloadUiState(context: Context): DownloadUiState? {
    val raw = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getString(DOWNLOAD_STATE_JSON, null)
        ?: return null
    return runCatching {
        downloadStateFromJson(JSONObject(raw))
    }.getOrNull()
}

@Composable
private fun DownloadHistoryDialog(
    history: List<DownloadHistoryEntry>,
    activeState: DownloadUiState?,
    onDismiss: () -> Unit,
    onOpenActive: () -> Unit,
    onOpenHistory: (DownloadHistoryEntry) -> Unit,
    onClearHistory: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("下载历史") },
        text = {
            LazyColumn(
                modifier = Modifier.height(420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (activeState != null) {
                    item("active-download") {
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable(onClick = onOpenActive),
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp,
                                ),
                            ) {
                                Text(
                                    activeState.appName,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    "正在下载 · " + activeState.stage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }

                if (history.isEmpty()) {
                    item("empty-history") {
                        Text(
                            if (activeState == null) "暂无下载历史" else "暂无已完成下载",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    lazyItems(
                        items = history,
                        key = { it.id },
                    ) { entry ->
                        val state = entry.state
                        val primaryName = state.apks.firstOrNull()?.name
                            ?: state.appName
                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onOpenHistory(entry) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer,
                        ) {
                            Column(
                                modifier = Modifier.padding(
                                    horizontal = 12.dp,
                                    vertical = 10.dp,
                                ),
                            ) {
                                Text(
                                    primaryName,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    state.appName + " · " +
                                        android.text.format.DateFormat.format(
                                            "MM-dd HH:mm",
                                            entry.completedAt,
                                        ),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (state.totalBytes != null) {
                                    Text(
                                        formatFileSize(state.totalBytes),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        dismissButton = {
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory) {
                    Text("清空历史")
                }
            }
        },
    )
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
    onIconClick: () -> Unit,
    onLongClick: () -> Unit,
    onActionsClick: () -> Unit,
    onLatestActionClick: () -> Unit,
    onArtifactClick: () -> Unit,
    chatBinding: ChatBinding?,
    chatBindingCount: Int,
    onChatClick: () -> Unit,
    onBindClick: () -> Unit,
) {
    val hasNewerActions = hasNewerActionsBuild(app)
    val installLabel = when {
        app.repoOnly -> "GitHub"
        !app.installed -> "未安装"
        app.launchIntent == null -> "模块"
        else -> "已安装"
    }
    val installColor = when {
        !app.installed && !app.repoOnly -> MaterialTheme.colorScheme.error
        app.repoOnly -> MaterialTheme.colorScheme.secondary
        app.launchIntent == null -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val actionsColor = when (app.actionsStatus) {
        ActionsStatus.SUCCESS -> MaterialTheme.colorScheme.primary
        ActionsStatus.FAILURE -> MaterialTheme.colorScheme.error
        ActionsStatus.RUNNING -> MaterialTheme.colorScheme.tertiary
        ActionsStatus.QUEUED -> MaterialTheme.colorScheme.secondary
        ActionsStatus.CANCELLED -> MaterialTheme.colorScheme.outline
        ActionsStatus.LOADING,
        ActionsStatus.NONE,
        ActionsStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier.clickable(onClick = onIconClick),
                ) {
                    AppIcon(app)
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(13.dp)
                            .clip(CircleShape)
                            .background(installColor),
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            app.name,
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(999.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(installColor),
                            )
                            Text(
                                installLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                            )
                        }
                    }

                    Text(
                        buildString {
                            when {
                                app.repoOnly -> append("GitHub 项目")
                                app.versionName.isNullOrBlank() -> append(
                                    if (app.installed) "已安装" else "等待安装"
                                )
                                else -> append("v").append(app.versionName)
                            }
                            app.installedUpdateTime?.let {
                                append(" · ").append(it)
                            }
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )

                    if (app.repo != null) {
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(7.dp))
                                .clickable(onClick = onLatestActionClick)
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(actionsColor),
                            )
                            Text(
                                buildString {
                                    append("Actions ")
                                    append(app.actionsStatus.label)
                                    app.latestActionTime?.let {
                                        append(" · ").append(it)
                                    }
                                    if (hasNewerActions) {
                                        append(" · 有新构建")
                                    }
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = if (
                                    app.actionsStatus == ActionsStatus.FAILURE
                                ) {
                                    MaterialTheme.colorScheme.error
                                } else if (hasNewerActions) {
                                    MaterialTheme.colorScheme.tertiary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                fontWeight = if (hasNewerActions) {
                                    FontWeight.SemiBold
                                } else {
                                    FontWeight.Normal
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }

            if (app.repo != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(onClick = onActionsClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Outlined.PlayArrow,
                            contentDescription = "打开最近一次 Actions",
                            modifier = Modifier.size(15.dp),
                            tint = actionsColor,
                        )
                        Text(
                            "Actions",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                        )
                    }

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(onClick = onArtifactClick)
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Outlined.Download,
                            contentDescription = "下载最新成功构建",
                            modifier = Modifier.size(15.dp),
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

                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(999.dp))
                            .background(MaterialTheme.colorScheme.surface)
                            .clickable(
                                onClick = if (chatBindingCount > 0) {
                                    onChatClick
                                } else {
                                    onBindClick
                                },
                            )
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = if (chatBindingCount > 0) {
                                "AI 绑定"
                            } else {
                                "添加 AI 绑定"
                            },
                            modifier = Modifier.size(15.dp),
                            tint = if (chatBinding != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.secondary
                            },
                        )
                        Text(
                            if (chatBindingCount > 0) {
                                "AI " + chatBindingCount
                            } else {
                                "AI +"
                            },
                            style = MaterialTheme.typography.labelMedium,
                            color = if (chatBinding != null) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            fontWeight = if (chatBinding != null) {
                                FontWeight.SemiBold
                            } else {
                                FontWeight.Medium
                            },
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
    onIconClick: () -> Unit,
    onLongClick: () -> Unit,
    onActionsClick: () -> Unit,
    onLatestActionClick: () -> Unit,
    onArtifactClick: () -> Unit,
    chatBinding: ChatBinding?,
    chatBindingCount: Int,
    onChatClick: () -> Unit,
    onBindClick: () -> Unit,
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
        Box(
            modifier = Modifier.clickable(onClick = onIconClick),
        ) {
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
                    "Actions " + actionTime,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onLatestActionClick)
                        .padding(horizontal = 2.dp, vertical = 1.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = when {
                        !app.installed && !app.repoOnly ->
                            MaterialTheme.colorScheme.error
                        hasNewerActions ->
                            Color(0xFFFFC107)
                        else ->
                            MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (
                        (!app.installed && !app.repoOnly) || hasNewerActions
                    ) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "AI " + chatBindingCount,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onChatClick)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chatBinding != null) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (chatBinding != null) {
                        FontWeight.SemiBold
                    } else {
                        FontWeight.Normal
                    },
                    maxLines = 1,
                )
                Text(
                    "绑定",
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(onClick = onBindClick)
                        .padding(horizontal = 5.dp, vertical = 3.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary,
                    fontWeight = FontWeight.SemiBold,
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
    val updatedTime: String?,
    val pushedTime: String?,
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
                        updatedTime = formatActionsTime(
                            item.optString("updated_at"),
                        ),
                        pushedTime = formatActionsTime(
                            item.optString("pushed_at"),
                        ),
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

    if (token.isNotBlank()) {
        loadPages(
            "https://api.github.com/user/repos?affiliation=owner&sort=updated",
            token,
        )
    } else {
        loadPages("https://api.github.com/users/yagay/repos?sort=updated", "")
    }

    return repositories.values.toList()
}

private fun mergeGithubRepositories(
    apps: List<HubApp>,
    repositories: List<GithubRepository>,
    resetActionsStatus: Boolean = true,
): List<HubApp> {
    val result = apps.toMutableList()
    val existingRepos = apps.mapNotNull { app ->
        val repo = app.repo ?: return@mapNotNull null
        (app.repoOwner + "/" + repo).lowercase()
    }.toMutableSet()

    repositories.forEach { repository ->
        val key = (repository.owner + "/" + repository.name).lowercase()
        val existingRepoIndex = result.indexOfFirst { app ->
            val repo = app.repo ?: return@indexOfFirst false
            (app.repoOwner + "/" + repo).lowercase() == key
        }

        if (existingRepoIndex >= 0) {
            val app = result[existingRepoIndex]
            result[existingRepoIndex] = app.copy(
                description = repository.description ?: app.description,
                repoUpdatedTime = repository.updatedTime,
                repoPushedTime = repository.pushedTime,
                actionsStatus = if (resetActionsStatus) {
                    ActionsStatus.LOADING
                } else {
                    app.actionsStatus
                },
            )
            existingRepos += key
            return@forEach
        }

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
                repoUpdatedTime = repository.updatedTime,
                repoPushedTime = repository.pushedTime,
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
                repoUpdatedTime = repository.updatedTime,
                repoPushedTime = repository.pushedTime,
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

private data class DynamicGithubRefreshResult(
    val apps: List<HubApp>,
    val nextStableCursor: Int,
)

private suspend fun refreshDynamicGithubState(
    apps: List<HubApp>,
    token: String,
    stableCursor: Int,
): DynamicGithubRefreshResult {
    val repositories = fetchOwnedRepositories(token)
    val merged = mergeGithubRepositories(
        apps = apps,
        repositories = repositories,
        resetActionsStatus = false,
    )

    fun repoKey(app: HubApp): String? {
        val repo = app.repo ?: return null
        return (app.repoOwner + "/" + repo).lowercase()
    }

    val previousByRepo = apps
        .mapNotNull { app -> repoKey(app)?.let { it to app } }
        .toMap()

    val changedRepoKeys = merged.mapNotNull { app ->
        val key = repoKey(app) ?: return@mapNotNull null
        val previous = previousByRepo[key]
        if (
            previous == null ||
            previous.repoUpdatedTime != app.repoUpdatedTime ||
            previous.repoPushedTime != app.repoPushedTime
        ) {
            key
        } else {
            null
        }
    }.toSet()

    val activeRepoKeys = merged.mapNotNull { app ->
        val key = repoKey(app) ?: return@mapNotNull null
        if (
            app.actionsStatus == ActionsStatus.LOADING ||
            app.actionsStatus == ActionsStatus.RUNNING ||
            app.actionsStatus == ActionsStatus.QUEUED
        ) {
            key
        } else {
            null
        }
    }.toSet()

    val stableRepos = merged
        .mapNotNull { app ->
            val key = repoKey(app) ?: return@mapNotNull null
            if (key in changedRepoKeys || key in activeRepoKeys) null else key
        }
        .distinct()

    val stableBatchSize = if (token.isNotBlank()) AUTO_ACTIONS_BATCH_SIZE else 0
    val stableBatch = if (stableRepos.isNotEmpty() && stableBatchSize > 0) {
        buildSet {
            repeat(minOf(stableBatchSize, stableRepos.size)) { offset ->
                add(stableRepos[(stableCursor + offset) % stableRepos.size])
            }
        }
    } else {
        emptySet()
    }

    val selectedRepoKeys = changedRepoKeys + activeRepoKeys + stableBatch

    val refreshed = coroutineScope {
        merged.map { app ->
            async {
                val key = repoKey(app)
                if (key == null || key !in selectedRepoKeys) {
                    app
                } else {
                    val repo = app.repo ?: return@async app
                    val info = fetchLatestActionsInfo(
                        owner = app.repoOwner,
                        repo = repo,
                        token = token,
                        knownRunId = app.latestRunId,
                        knownArtifactId = app.latestArtifactId,
                        knownArtifactSizeBytes = app.latestArtifactSizeBytes,
                    )
                    if (
                        info.status == ActionsStatus.UNKNOWN &&
                        app.actionsStatus != ActionsStatus.LOADING &&
                        app.actionsStatus != ActionsStatus.UNKNOWN
                    ) {
                        app
                    } else {
                        app.copy(
                            actionsStatus = info.status,
                            latestRunId = info.runId,
                            latestActionTime = info.actionTime,
                            latestArtifactId = info.artifactId,
                            latestArtifactSizeBytes = info.artifactSizeBytes,
                        )
                    }
                }
            }
        }.awaitAll()
    }

    val nextCursor = if (stableRepos.isEmpty() || stableBatchSize == 0) {
        0
    } else {
        (stableCursor + minOf(stableBatchSize, stableRepos.size)) % stableRepos.size
    }

    return DynamicGithubRefreshResult(
        apps = refreshed,
        nextStableCursor = nextCursor,
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
                val info = fetchLatestActionsInfo(
                    owner = app.repoOwner,
                    repo = app.repo,
                    token = token,
                    knownRunId = app.latestRunId,
                    knownArtifactId = app.latestArtifactId,
                    knownArtifactSizeBytes = app.latestArtifactSizeBytes,
                )
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

private fun fetchLatestActionsInfo(
    owner: String,
    repo: String,
    token: String,
    knownRunId: Long? = null,
    knownArtifactId: Long? = null,
    knownArtifactSizeBytes: Long? = null,
): LatestActionsInfo {
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

        // 同一个成功 run 已有 artifact 时直接复用，避免自动刷新重复请求产物接口。
        val artifactInfo = if (status == ActionsStatus.SUCCESS && runId != null) {
            if (runId == knownRunId && knownArtifactId != null) {
                knownArtifactId to (knownArtifactSizeBytes ?: 0L)
            } else {
                fetchLatestArtifactInfo(owner, repo, runId, token)
            }
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
): Pair<Long, Long>? =
    fetchArtifactOptions(
        owner = owner,
        repo = repo,
        runId = runId,
        token = token,
    ).firstOrNull()?.let { artifact ->
        artifact.id to artifact.sizeBytes
    }

private fun fetchArtifactOptions(
    owner: String,
    repo: String,
    runId: Long,
    token: String,
): List<ActionsArtifact> {
    var connection: HttpURLConnection? = null
    return try {
        connection = githubGet(
            "https://api.github.com/repos/" + owner + "/" + repo +
                "/actions/runs/" + runId + "/artifacts?per_page=100",
            token = token,
        )
        if (connection.responseCode !in 200..299) return emptyList()

        val body = connection.inputStream.bufferedReader().use { it.readText() }
        val artifacts = JSONObject(body).optJSONArray("artifacts")
            ?: return emptyList()

        buildList {
            for (index in 0 until artifacts.length()) {
                val artifact = artifacts.getJSONObject(index)
                if (artifact.optBoolean("expired", true)) continue
                val id = artifact.optLong("id")
                if (id <= 0L) continue
                add(
                    ActionsArtifact(
                        id = id,
                        name = artifact.optString("name")
                            .ifBlank { "artifact-" + id },
                        sizeBytes = artifact.optLong("size_in_bytes")
                            .coerceAtLeast(0L),
                    ),
                )
            }
        }
    } catch (_: Exception) {
        emptyList()
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
    val outputUri: Uri? = null,
    val cancelled: Boolean = false,
)

private data class TempArtifactDownload(
    val file: File,
    val metaFile: File,
    val totalBytes: Long?,
    val threadCount: Int,
    val resumed: Boolean,
)

private data class TempDownloadResult(
    val temp: TempArtifactDownload?,
    val message: String,
)

private data class RangeProbe(
    val supportsRange: Boolean,
    val totalBytes: Long?,
)

private data class DownloadSegment(
    val start: Long,
    val end: Long,
) {
    val length: Long
        get() = end - start + 1L
}

private fun resolveArtifactDownloadUrl(
    owner: String,
    repo: String,
    artifactId: Long,
    token: String,
): String? {
    val apiUrl =
        "https://api.github.com/repos/" + owner + "/" +
            repo + "/actions/artifacts/" + artifactId + "/zip"
    var connection: HttpURLConnection? = null
    return try {
        connection = githubGet(
            url = apiUrl,
            token = token,
            followRedirects = false,
        )
        when (connection.responseCode) {
            in 300..399 ->
                connection.getHeaderField("Location")
            in 200..299 ->
                apiUrl
            else ->
                null
        }
    } finally {
        connection?.disconnect()
    }
}

private fun openArtifactDataConnection(
    url: String,
    token: String,
    range: String? = null,
): HttpURLConnection =
    (URL(url).openConnection() as HttpURLConnection).apply {
        requestMethod = "GET"
        connectTimeout = 12_000
        readTimeout = 60_000
        instanceFollowRedirects = true
        useCaches = false
        defaultUseCaches = false
        setRequestProperty("Cache-Control", "no-store, no-cache")
        setRequestProperty("Pragma", "no-cache")
        setRequestProperty("User-Agent", "YagaYHub")
        range?.let {
            setRequestProperty("Range", it)
        }
        if (
            token.isNotBlank() &&
            runCatching {
                URL(url).host.equals(
                    "api.github.com",
                    ignoreCase = true,
                )
            }.getOrDefault(false)
        ) {
            setRequestProperty(
                "Authorization",
                "Bearer " + token,
            )
            setRequestProperty(
                "Accept",
                "application/vnd.github+json",
            )
        }
    }

private fun probeArtifactRange(
    owner: String,
    repo: String,
    artifactId: Long,
    token: String,
): RangeProbe? {
    repeat(DOWNLOAD_RETRY_COUNT) { attempt ->
        val url = resolveArtifactDownloadUrl(
            owner,
            repo,
            artifactId,
            token,
        )
        if (url.isNullOrBlank()) {
            if (attempt + 1 < DOWNLOAD_RETRY_COUNT) {
                Thread.sleep(700L * (attempt + 1L))
            }
            return@repeat
        }

        var connection: HttpURLConnection? = null
        try {
            connection = openArtifactDataConnection(
                url = url,
                token = token,
                range = "bytes=0-0",
            )
            val code = connection.responseCode
            if (code == HttpURLConnection.HTTP_PARTIAL) {
                val contentRange =
                    connection.getHeaderField("Content-Range")
                        .orEmpty()
                val total = contentRange
                    .substringAfterLast('/', "")
                    .toLongOrNull()
                return RangeProbe(
                    supportsRange = true,
                    totalBytes = total,
                )
            }
            if (code in 200..299) {
                return RangeProbe(
                    supportsRange = false,
                    totalBytes = connection.contentLengthLong
                        .takeIf { it > 0L },
                )
            }
        } catch (_: Exception) {
            Unit
        } finally {
            connection?.disconnect()
        }

        if (attempt + 1 < DOWNLOAD_RETRY_COUNT) {
            Thread.sleep(700L * (attempt + 1L))
        }
    }
    return null
}

private fun buildDownloadSegments(
    totalBytes: Long,
    threadCount: Int,
): List<DownloadSegment> {
    val size =
        (totalBytes + threadCount - 1L) / threadCount
    return (0 until threadCount).mapNotNull { index ->
        val start = index * size
        if (start >= totalBytes) {
            null
        } else {
            DownloadSegment(
                start = start,
                end = minOf(
                    totalBytes - 1L,
                    start + size - 1L,
                ),
            )
        }
    }
}

private fun loadResumeCounters(
    metaFile: File,
    artifactId: Long,
    totalBytes: Long,
    segments: List<DownloadSegment>,
): LongArray? {
    if (!metaFile.exists()) return null
    return runCatching {
        val json = JSONObject(metaFile.readText())
        if (
            json.optLong("artifactId") != artifactId ||
            json.optLong("totalBytes") != totalBytes
        ) {
            return@runCatching null
        }
        val array = json.optJSONArray("segments")
            ?: return@runCatching null
        if (array.length() != segments.size) {
            return@runCatching null
        }

        LongArray(segments.size) { index ->
            val item = array.getJSONObject(index)
            val segment = segments[index]
            if (
                item.optLong("start") != segment.start ||
                item.optLong("end") != segment.end
            ) {
                return@runCatching null
            }
            item.optLong("downloaded", 0L)
                .coerceIn(0L, segment.length)
        }
    }.getOrNull()
}

private fun saveResumeCounters(
    metaFile: File,
    artifactId: Long,
    totalBytes: Long,
    segments: List<DownloadSegment>,
    counters: AtomicLongArray,
) {
    val json = JSONObject()
        .put("artifactId", artifactId)
        .put("totalBytes", totalBytes)
        .put(
            "segments",
            JSONArray().apply {
                segments.forEachIndexed { index, segment ->
                    put(
                        JSONObject()
                            .put("start", segment.start)
                            .put("end", segment.end)
                            .put(
                                "downloaded",
                                counters.get(index),
                            )
                    )
                }
            },
        )
    metaFile.parentFile?.mkdirs()
    metaFile.writeText(json.toString())
}

private fun downloadArtifactToTemp(
    context: Context,
    owner: String,
    repo: String,
    artifactId: Long,
    token: String,
    expectedSizeBytes: Long?,
    onProgress: (Long, Long?, String) -> Unit,
): TempDownloadResult {
    val partDir = File(
        context.filesDir,
        "artifact_parts",
    ).apply { mkdirs() }
    val key = (
        owner + "_" + repo + "_" + artifactId
        )
        .replace(Regex("[^A-Za-z0-9._-]"), "_")
    val partFile = File(partDir, key + ".part")
    val metaFile = File(partDir, key + ".json")

    onProgress(
        0L,
        expectedSizeBytes,
        "检查断点与服务器分段支持…",
    )
    val probe = probeArtifactRange(
        owner,
        repo,
        artifactId,
        token,
    ) ?: return TempDownloadResult(
        temp = null,
        message = "无法连接 GitHub 下载地址",
    )

    val totalBytes =
        probe.totalBytes ?: expectedSizeBytes

    if (
        probe.supportsRange &&
        totalBytes != null &&
        totalBytes > 0L
    ) {
        val threadCount = minOf(
            DOWNLOAD_SEGMENT_THREADS,
            maxOf(
                1,
                (
                    (totalBytes + DOWNLOAD_MIN_SEGMENT_BYTES - 1L) /
                        DOWNLOAD_MIN_SEGMENT_BYTES
                    ).toInt(),
            ),
        )
        val segments = buildDownloadSegments(
            totalBytes,
            threadCount,
        )
        val restored = loadResumeCounters(
            metaFile,
            artifactId,
            totalBytes,
            segments,
        )
        val resumed = restored?.any { it > 0L } == true
        val counters = AtomicLongArray(segments.size)

        if (
            restored == null ||
            !partFile.exists()
        ) {
            partFile.delete()
            metaFile.delete()
            RandomAccessFile(partFile, "rw").use {
                it.setLength(totalBytes)
            }
        } else {
            RandomAccessFile(partFile, "rw").use {
                if (it.length() != totalBytes) {
                    it.setLength(totalBytes)
                }
            }
        }
        segments.indices.forEach { index ->
            counters.set(
                index,
                restored?.get(index) ?: 0L,
            )
        }
        saveResumeCounters(
            metaFile,
            artifactId,
            totalBytes,
            segments,
            counters,
        )

        val lock = Any()
        val errors = mutableListOf<String>()
        var lastProgressAt = 0L
        var lastMetaAt = 0L
        val latch = CountDownLatch(segments.size)

        fun publishProgress(force: Boolean = false) {
            synchronized(lock) {
                val now = System.currentTimeMillis()
                if (
                    force ||
                    now - lastProgressAt >= 120L
                ) {
                    var downloaded = 0L
                    for (index in segments.indices) {
                        downloaded += counters.get(index)
                    }
                    onProgress(
                        downloaded,
                        totalBytes,
                        if (resumed) {
                            "断点续传 · " +
                                segments.size +
                                " 线程…"
                        } else {
                            "多线程下载 · " +
                                segments.size +
                                " 线程…"
                        },
                    )
                    lastProgressAt = now
                }
                if (
                    force ||
                    now - lastMetaAt >= 500L
                ) {
                    runCatching {
                        saveResumeCounters(
                            metaFile,
                            artifactId,
                            totalBytes,
                            segments,
                            counters,
                        )
                    }
                    lastMetaAt = now
                }
            }
        }

        segments.forEachIndexed { index, segment ->
            Thread {
                try {
                    var retries = 0
                    while (
                        counters.get(index) < segment.length
                    ) {
                        val done = counters.get(index)
                        val from = segment.start + done
                        val url = resolveArtifactDownloadUrl(
                            owner,
                            repo,
                            artifactId,
                            token,
                        ) ?: throw IllegalStateException(
                            "无法刷新 GitHub 下载地址",
                        )
                        var connection: HttpURLConnection? = null
                        try {
                            connection = openArtifactDataConnection(
                                url = url,
                                token = token,
                                range =
                                    "bytes=" + from + "-" +
                                        segment.end,
                            )
                            val code = connection.responseCode
                            if (
                                code !=
                                HttpURLConnection.HTTP_PARTIAL
                            ) {
                                throw IllegalStateException(
                                    "服务器未返回 206 分段响应：" +
                                        code,
                                )
                            }

                            RandomAccessFile(
                                partFile,
                                "rw",
                            ).use { output ->
                                output.seek(from)
                                connection.inputStream.use { input ->
                                    val buffer =
                                        ByteArray(128 * 1024)
                                    while (
                                        counters.get(index) <
                                        segment.length
                                    ) {
                                        val remaining =
                                            segment.length -
                                                counters.get(index)
                                        val read = input.read(
                                            buffer,
                                            0,
                                            minOf(
                                                buffer.size.toLong(),
                                                remaining,
                                            ).toInt(),
                                        )
                                        if (read < 0) break
                                        output.write(
                                            buffer,
                                            0,
                                            read,
                                        )
                                        counters.addAndGet(
                                            index,
                                            read.toLong(),
                                        )
                                        publishProgress()
                                    }
                                }
                            }

                            if (
                                counters.get(index) <
                                segment.length
                            ) {
                                throw IllegalStateException(
                                    "分段连接提前结束",
                                )
                            }
                            retries = 0
                        } catch (error: Exception) {
                            retries++
                            if (
                                retries >= DOWNLOAD_RETRY_COUNT
                            ) {
                                throw error
                            }
                            Thread.sleep(
                                900L * retries,
                            )
                        } finally {
                            connection?.disconnect()
                        }
                    }
                } catch (error: Exception) {
                    synchronized(lock) {
                        errors += (
                            "分段 " + (index + 1) + ": " +
                                (
                                    error.message
                                        ?: "未知错误"
                                    )
                            )
                    }
                } finally {
                    publishProgress(force = true)
                    latch.countDown()
                }
            }.apply {
                name =
                    "YagaYHub-Range-" +
                        artifactId + "-" + index
                start()
            }
        }

        latch.await()
        publishProgress(force = true)

        if (errors.isNotEmpty()) {
            return TempDownloadResult(
                temp = null,
                message =
                    "下载中断，断点已保留：" +
                        errors.first(),
            )
        }

        val complete = segments.indices.all { index ->
            counters.get(index) >= segments[index].length
        }
        if (!complete) {
            return TempDownloadResult(
                temp = null,
                message = "下载未完整，断点已保留",
            )
        }

        return TempDownloadResult(
            temp = TempArtifactDownload(
                file = partFile,
                metaFile = metaFile,
                totalBytes = totalBytes,
                threadCount = segments.size,
                resumed = resumed,
            ),
            message = "下载完成",
        )
    }

    // 极少数不支持 Range 的服务器自动降级为单线程。
    partFile.delete()
    metaFile.delete()
    repeat(DOWNLOAD_RETRY_COUNT) { attempt ->
        val url = resolveArtifactDownloadUrl(
            owner,
            repo,
            artifactId,
            token,
        )
        if (url.isNullOrBlank()) {
            if (attempt + 1 < DOWNLOAD_RETRY_COUNT) {
                Thread.sleep(900L * (attempt + 1L))
            }
            return@repeat
        }

        var connection: HttpURLConnection? = null
        try {
            connection = openArtifactDataConnection(
                url,
                token,
            )
            if (connection.responseCode !in 200..299) {
                throw IllegalStateException(
                    "HTTP " + connection.responseCode,
                )
            }
            val total =
                connection.contentLengthLong
                    .takeIf { it > 0L }
                    ?: totalBytes
            var copied = 0L
            partFile.outputStream().buffered().use { output ->
                connection.inputStream.use { input ->
                    val buffer = ByteArray(128 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        copied += read
                        onProgress(
                            copied,
                            total,
                            "单线程下载（服务器不支持 Range）…",
                        )
                    }
                }
            }
            return TempDownloadResult(
                temp = TempArtifactDownload(
                    file = partFile,
                    metaFile = metaFile,
                    totalBytes = total,
                    threadCount = 1,
                    resumed = false,
                ),
                message = "下载完成",
            )
        } catch (_: Exception) {
            partFile.delete()
            if (attempt + 1 < DOWNLOAD_RETRY_COUNT) {
                Thread.sleep(900L * (attempt + 1L))
            }
        } finally {
            connection?.disconnect()
        }
    }

    return TempDownloadResult(
        temp = null,
        message =
            "下载失败；服务器不支持断点续传，重试仍失败",
    )
}

private fun sanitizeArtifactZipName(
    repo: String,
    artifactName: String,
    artifactId: Long,
): String {
    val cleanArtifact = artifactName
        .removeSuffix(".zip")
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .trim()
        .take(100)
        .ifBlank { artifactId.toString() }
    val cleanRepo = repo
        .replace(Regex("[\\/:*?\"<>|]"), "_")
        .trim()
        .take(60)
        .ifBlank { "artifact" }
    return cleanRepo + "-" + cleanArtifact + ".zip"
}

private fun downloadArtifactZip(
    context: Context,
    owner: String,
    repo: String,
    runId: Long,
    artifactId: Long,
    artifactName: String,
    token: String,
    destinationTreeUri: String,
    rootEnhancedCleanup: Boolean,
    expectedSizeBytes: Long? = null,
    onProgress: (Long, Long?, String) -> Unit =
        { _, _, _ -> },
): DownloadResult {
    var outputUri: Uri? = null

    return try {
        val tempResult = downloadArtifactToTemp(
            context = context,
            owner = owner,
            repo = repo,
            artifactId = artifactId,
            token = token,
            expectedSizeBytes = expectedSizeBytes,
            onProgress = onProgress,
        )
        val temp = tempResult.temp
            ?: return DownloadResult(
                false,
                tempResult.message,
            )

        val fileName = sanitizeArtifactZipName(
            repo,
            artifactName,
            artifactId,
        )
        val rootDirectory =
            if (rootEnhancedCleanup) {
                resolveDownloadPhysicalDirectory(
                    destinationTreeUri,
                )
            } else {
                null
            }

        val destination =
            if (destinationTreeUri.isBlank()) {
                prepareDefaultDownloadDestination(
                    context,
                    fileName,
                )
            } else {
                prepareTreeDownloadDestination(
                    context = context,
                    treeUriString = destinationTreeUri,
                    fileName = fileName,
                )
            } ?: return DownloadResult(
                false,
                "无法创建下载文件",
            )

        outputUri = destination.uri
        onProgress(
            temp.totalBytes ?: temp.file.length(),
            temp.totalBytes,
            "写入下载目录…",
        )
        context.contentResolver
            .openOutputStream(
                destination.uri,
                "w",
            )
            ?.use { output ->
                temp.file.inputStream()
                    .buffered()
                    .use { input ->
                        input.copyTo(
                            output,
                            256 * 1024,
                        )
                    }
            }
            ?: return DownloadResult(
                false,
                "无法写入下载文件",
            )

        destination.finish?.invoke()
        onProgress(
            temp.totalBytes ?: temp.file.length(),
            temp.totalBytes,
            "解压 APK…",
        )

        val extractedApks = extractApksFromZip(
            context = context,
            zipUri = destination.uri,
            destinationTreeUri = destinationTreeUri,
            rootDirectory =
                if (rootEnhancedCleanup) {
                    rootDirectory
                } else {
                    null
                },
        )

        if (
            rootEnhancedCleanup &&
            rootDirectory != null
        ) {
            onProgress(
                temp.totalBytes ?: temp.file.length(),
                temp.totalBytes,
                "Root 清理…",
            )
            rootCleanupDownloadDirectory(
                context = context,
                directory = rootDirectory,
                repo = repo,
                keepCurrentZip = true,
            )
        }

        temp.file.delete()
        temp.metaFile.delete()

        onProgress(
            temp.totalBytes ?: 0L,
            temp.totalBytes,
            "完成",
        )
        DownloadResult(
            success = true,
            message = buildString {
                append("已保存到 ")
                append(destination.displayPath)
                append(" · ")
                append(temp.threadCount)
                append(" 线程")
                if (temp.resumed) {
                    append(" · 已断点续传")
                }
                if (extractedApks.isNotEmpty()) {
                    append(" · 已解压 ")
                    append(extractedApks.size)
                    append(" 个 APK")
                } else {
                    append(" · ZIP 内未发现 APK")
                }
            },
            extractedApks = extractedApks,
            outputUri = destination.uri,
        )
    } catch (error: Exception) {
        outputUri?.let { uri ->
            runCatching {
                context.contentResolver.delete(
                    uri,
                    null,
                    null,
                )
            }
        }
        DownloadResult(
            false,
            "下载失败：" +
                (error.message ?: "未知错误") +
                "；断点文件已保留",
        )
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

private data class AiBindingStats(
    val count: Int,
    val latestAddedAt: Long?,
)

private fun buildAiBindingStats(
    bindings: List<ChatBinding>,
): Map<String, AiBindingStats> {
    return bindings
        .groupBy { normalizedRepoKey(it.repoKey) }
        .mapValues { (_, items) ->
            AiBindingStats(
                count = items.size,
                latestAddedAt = items
                    .maxOfOrNull { it.addedAt }
                    ?.takeIf { it > 0L },
            )
        }
}

private fun sortHubApps(
    apps: List<HubApp>,
    mode: AppSortMode,
    ascending: Boolean,
    bindingStats: Map<String, AiBindingStats>,
): List<HubApp> {
    if (apps.size <= 1) return apps

    fun stats(app: HubApp): AiBindingStats {
        val repo = app.repo ?: return AiBindingStats(0, null)
        return bindingStats[
            normalizedRepoKey(app.repoOwner + "/" + repo)
        ] ?: AiBindingStats(0, null)
    }

    fun compareNullableText(
        left: String?,
        right: String?,
    ): Int {
        if (left == null && right == null) return 0
        if (left == null) return 1
        if (right == null) return -1
        return if (ascending) {
            left.compareTo(right)
        } else {
            right.compareTo(left)
        }
    }

    fun compareNullableLong(
        left: Long?,
        right: Long?,
    ): Int {
        if (left == null && right == null) return 0
        if (left == null) return 1
        if (right == null) return -1
        return if (ascending) {
            left.compareTo(right)
        } else {
            right.compareTo(left)
        }
    }

    val comparator = Comparator<HubApp> { left, right ->
        val primary = when (mode) {
            AppSortMode.DEFAULT -> 0
            AppSortMode.ACTIONS_TIME ->
                compareNullableText(
                    left.latestActionTime,
                    right.latestActionTime,
                )
            AppSortMode.PROJECT_TIME ->
                compareNullableText(
                    left.repoUpdatedTime,
                    right.repoUpdatedTime,
                )
            AppSortMode.PUSH_TIME ->
                compareNullableText(
                    left.repoPushedTime,
                    right.repoPushedTime,
                )
            AppSortMode.NAME -> {
                val a = left.name.lowercase()
                val b = right.name.lowercase()
                if (ascending) a.compareTo(b) else b.compareTo(a)
            }
            AppSortMode.INSTALLED_TIME ->
                compareNullableText(
                    left.installedUpdateTime,
                    right.installedUpdateTime,
                )
            AppSortMode.AI_COUNT -> {
                val a = stats(left).count
                val b = stats(right).count
                if (ascending) a.compareTo(b) else b.compareTo(a)
            }
            AppSortMode.AI_RECENT ->
                compareNullableLong(
                    stats(left).latestAddedAt,
                    stats(right).latestAddedAt,
                )
        }

        if (primary != 0) {
            primary
        } else {
            left.name.lowercase()
                .compareTo(right.name.lowercase())
        }
    }

    return if (mode == AppSortMode.DEFAULT) {
        if (ascending) apps else apps.asReversed()
    } else {
        apps.sortedWith(comparator)
    }
}

private fun saveSortSettings(
    context: Context,
    mode: AppSortMode,
    ascending: Boolean,
) {
    context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(SORT_MODE, mode.name)
        .putBoolean(SORT_ASCENDING, ascending)
        .apply()
}

private fun loadSortMode(context: Context): AppSortMode {
    val saved = context.getSharedPreferences(TOKEN_PREFS, Context.MODE_PRIVATE)
        .getString(SORT_MODE, AppSortMode.DEFAULT.name)
    return runCatching {
        AppSortMode.valueOf(saved.orEmpty())
    }.getOrDefault(AppSortMode.DEFAULT)
}

private fun loadSortAscending(
    context: Context,
    fallback: Boolean,
): Boolean {
    val prefs = context.getSharedPreferences(
        TOKEN_PREFS,
        Context.MODE_PRIVATE,
    )
    return if (prefs.contains(SORT_ASCENDING)) {
        prefs.getBoolean(SORT_ASCENDING, fallback)
    } else {
        fallback
    }
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

private const val ACTION_CHATGPT_BOUND =
    "com.yagay.YagaYHub.action.CHATGPT_BOUND"
private const val ACTION_REQUEST_CHATGPT_BINDING =
    "com.yagay.YagaYHub.action.REQUEST_CHATGPT_BINDING"
private const val ACTION_REMOVE_CHATGPT_BINDING =
    "com.yagay.YagaYHub.action.REMOVE_CHATGPT_BINDING"
private const val YBROWSER_CHAT_BINDING_SYNC_ACTION =
    "com.yagay.YBrowser.action.CHATGPT_BINDING_SYNC"
private const val YBROWSER_CHAT_BINDING_REMOVE_ACTION =
    "com.yagay.YBrowser.action.CHATGPT_BINDING_REMOVE"
private const val YBROWSER_SELECT_CHAT_ACTION =
    "com.yagay.YBrowser.action.SELECT_CHATGPT_CHAT"
private const val YBROWSER_OPEN_BROWSER_ACTION =
    "com.yagay.YBrowser.action.OPEN_YAGAYHUB_BROWSER"
private const val EXTRA_CHAT_BIND_REPO = "com.yagay.YBrowser.extra.BIND_REPO"
private const val EXTRA_CHAT_BIND_PROJECT = "com.yagay.YBrowser.extra.BIND_PROJECT"
private const val EXTRA_CHAT_BIND_URL = "com.yagay.YBrowser.extra.BIND_URL"
private const val EXTRA_CHAT_BIND_TITLE = "com.yagay.YBrowser.extra.BIND_TITLE"
private const val EXTRA_CHAT_TARGETS_JSON =
    "com.yagay.YBrowser.extra.CHAT_TARGETS_JSON"
private const val CHAT_BINDINGS_PREFS = "chatgpt_bindings"
private const val CHAT_BINDINGS_LIST_KEY = "bindings_v2"
private const val CHAT_BINDINGS_MIGRATED_KEY = "bindings_v2_migrated"

private const val DOWNLOAD_CHANNEL_ID = "artifact_downloads"
private const val DOWNLOAD_NOTIFICATION_ID = 4107
private const val ACTION_DOWNLOAD_STATE = "com.yagay.YagaYHub.action.DOWNLOAD_STATE"

private const val EXTRA_DOWNLOAD_APP_NAME = "download_app_name"
private const val EXTRA_DOWNLOAD_OWNER = "download_owner"
private const val EXTRA_DOWNLOAD_REPO = "download_repo"
private const val EXTRA_DOWNLOAD_RUN_ID = "download_run_id"
private const val EXTRA_DOWNLOAD_ARTIFACT_ID = "download_artifact_id"
private const val EXTRA_DOWNLOAD_ARTIFACT_NAME = "download_artifact_name"
private const val EXTRA_DOWNLOAD_EXPECTED_SIZE = "download_expected_size"

private const val EXTRA_STATE_APP_NAME = "state_app_name"
private const val EXTRA_STATE_OWNER = "state_owner"
private const val EXTRA_STATE_REPO = "state_repo"
private const val EXTRA_STATE_RUN_ID = "state_run_id"
private const val EXTRA_STATE_ARTIFACT_ID = "state_artifact_id"
private const val EXTRA_STATE_ARTIFACT_NAME = "state_artifact_name"
private const val EXTRA_STATE_FILE_NAME = "state_file_name"
private const val EXTRA_STATE_FILE_URI = "state_file_uri"
private const val EXTRA_STATE_STAGE = "state_stage"
private const val EXTRA_STATE_DOWNLOADED = "state_downloaded"
private const val EXTRA_STATE_TOTAL = "state_total"
private const val EXTRA_STATE_RUNNING = "state_running"
private const val EXTRA_STATE_PAUSED = "state_paused"
private const val EXTRA_STATE_CANCELLED = "state_cancelled"
private const val EXTRA_STATE_MESSAGE = "state_message"
private const val EXTRA_STATE_APK_NAMES = "state_apk_names"
private const val EXTRA_STATE_APK_URIS = "state_apk_uris"

private const val AUTO_REFRESH_INTERVAL_AUTH_MS = 20_000L
private const val AUTO_REFRESH_INTERVAL_ANON_MS = 90_000L
private const val AUTO_ACTIONS_BATCH_SIZE = 5

private const val TOKEN_PREFS = "github_secure"
private const val TOKEN_IV = "token_iv"
private const val TOKEN_DATA = "token_data"
private const val GITHUB_CLIENT_ID = "github_client_id"
private const val GITHUB_CLIENT_SECRET_IV = "github_client_secret_iv"
private const val GITHUB_CLIENT_SECRET_DATA = "github_client_secret_data"
private const val DOWNLOAD_TREE_URI = "download_tree_uri"
private const val ROOT_CLEANUP_ENABLED = "root_cleanup_enabled"
private const val LAYOUT_MODE = "layout_mode"
private const val SORT_MODE = "sort_mode"
private const val SORT_ASCENDING = "sort_ascending"
private const val DOWNLOAD_STATE_JSON = "download_state_json"
private const val DOWNLOAD_HISTORY_JSON = "download_history_json"
private const val DOWNLOAD_HISTORY_LIMIT = 200
private const val DOWNLOAD_SEGMENT_THREADS = 4
private const val DOWNLOAD_RETRY_COUNT = 4
private const val DOWNLOAD_MIN_SEGMENT_BYTES = 2L * 1024L * 1024L
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

private fun isBindableAiPageUrl(url: String): Boolean {
    val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return false
    val scheme = uri.scheme?.lowercase() ?: return false
    return (scheme == "http" || scheme == "https") &&
        !uri.host.isNullOrBlank()
}

private fun normalizedRepoKey(repoKey: String): String =
    repoKey.trim().lowercase()

private fun loadAllChatBindings(context: Context): List<ChatBinding> {
    val prefs = context.getSharedPreferences(
        CHAT_BINDINGS_PREFS,
        Context.MODE_PRIVATE,
    )
    val stored = runCatching {
        JSONArray(prefs.getString(CHAT_BINDINGS_LIST_KEY, "[]"))
    }.getOrElse { JSONArray() }

    val bindings = buildList {
        for (index in 0 until stored.length()) {
            val item = stored.optJSONObject(index) ?: continue
            val repoKey = normalizedRepoKey(item.optString("repoKey"))
            val url = normalizeChatBindingUrl(item.optString("url"))
            if (repoKey.isBlank() || url.isBlank()) continue
            add(
                ChatBinding(
                    repoKey = repoKey,
                    title = item.optString("title").ifBlank { "AI" },
                    url = url,
                    addedAt = item.optLong("addedAt", 0L),
                ),
            )
        }
    }.toMutableList()

    // Migrate the legacy one-binding-per-project format once.
    if (!prefs.getBoolean(CHAT_BINDINGS_MIGRATED_KEY, false)) {
        prefs.all.forEach { (key, value) ->
            if (!key.endsWith(":url") || value !is String) return@forEach
            val repoKey = normalizedRepoKey(key.removeSuffix(":url"))
            val url = normalizeChatBindingUrl(value)
            if (repoKey.isBlank() || url.isBlank()) return@forEach
            if (bindings.none { sameChatBindingUrl(it.url, url) }) {
                bindings += ChatBinding(
                    repoKey = repoKey,
                    title = prefs.getString(repoKey + ":title", null)
                        ?.takeIf { it.isNotBlank() }
                        ?: "AI",
                    url = url,
                    addedAt = 0L,
                )
            }
        }
        saveAllChatBindings(context, bindings)
        prefs.edit().putBoolean(CHAT_BINDINGS_MIGRATED_KEY, true).apply()
    }

    return bindings
        .distinctBy { normalizeChatBindingUrl(it.url) }
        .sortedByDescending { it.addedAt }
}

private fun saveAllChatBindings(
    context: Context,
    bindings: List<ChatBinding>,
) {
    val array = JSONArray()
    bindings
        .distinctBy { normalizeChatBindingUrl(it.url) }
        .sortedByDescending { it.addedAt }
        .forEach { binding ->
            array.put(
                JSONObject()
                    .put("repoKey", normalizedRepoKey(binding.repoKey))
                    .put("title", binding.title.ifBlank { "AI" })
                    .put("url", normalizeChatBindingUrl(binding.url))
                    .put("addedAt", binding.addedAt),
            )
        }

    context.getSharedPreferences(CHAT_BINDINGS_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(CHAT_BINDINGS_LIST_KEY, array.toString())
        .apply()
}

private fun saveChatBinding(
    context: Context,
    repoKey: String,
    title: String,
    url: String,
) {
    val key = normalizedRepoKey(repoKey)
    val normalizedUrl = normalizeChatBindingUrl(url)
    if (key.isBlank() || normalizedUrl.isBlank()) return

    val existing = loadAllChatBindings(context)
    val sameBinding = existing.firstOrNull {
        it.repoKey == key && sameChatBindingUrl(it.url, normalizedUrl)
    }
    val addedAt = sameBinding?.addedAt?.takeIf { it > 0L }
        ?: System.currentTimeMillis()

    // One AI page can belong to only one project.
    val merged = buildList {
        add(
            ChatBinding(
                repoKey = key,
                title = title.ifBlank { "AI" },
                url = normalizedUrl,
                addedAt = addedAt,
            ),
        )
        existing
            .filterNot { sameChatBindingUrl(it.url, normalizedUrl) }
            .forEach(::add)
    }
    saveAllChatBindings(context, merged)
}

private fun loadChatBindings(
    context: Context,
    owner: String,
    repo: String,
): List<ChatBinding> {
    val repoKey = normalizedRepoKey(owner + "/" + repo)
    return loadAllChatBindings(context)
        .filter { it.repoKey == repoKey }
        .sortedByDescending { it.addedAt }
}

private fun loadChatBinding(
    context: Context,
    owner: String,
    repo: String,
): ChatBinding? = loadChatBindings(context, owner, repo).firstOrNull()

private fun removeChatBinding(
    context: Context,
    url: String,
) {
    val normalizedUrl = normalizeChatBindingUrl(url)
    if (normalizedUrl.isBlank()) return
    saveAllChatBindings(
        context,
        loadAllChatBindings(context)
            .filterNot { sameChatBindingUrl(it.url, normalizedUrl) },
    )
}

private fun removeChatBindingsByRepo(
    context: Context,
    repoKey: String,
) {
    val normalizedRepo =
        normalizedRepoKey(repoKey)
    if (normalizedRepo.isBlank()) return

    saveAllChatBindings(
        context,
        loadAllChatBindings(context)
            .filterNot {
                it.repoKey.equals(
                    normalizedRepo,
                    ignoreCase = true,
                )
            },
    )
}

private fun normalizeChatBindingUrl(url: String): String =
    url.trim().trimEnd('/')

private fun sameChatBindingUrl(left: String, right: String): Boolean =
    normalizeChatBindingUrl(left) == normalizeChatBindingUrl(right)

private fun projectNameForBinding(binding: ChatBinding): String {
    val repo = binding.repoKey.substringAfterLast('/')
    return knownProjects.firstOrNull {
        it.repo.equals(repo, ignoreCase = true)
    }?.name ?: repo.ifBlank { "AI" }
}

private fun chatTargetsJson(
    context: Context,
): String {
    val array = JSONArray()
    loadAllChatBindings(context)
        .sortedByDescending { it.addedAt }
        .forEach { binding ->
            array.put(
                JSONObject()
                    .put("repoKey", binding.repoKey)
                    .put("project", projectNameForBinding(binding))
                    .put("url", binding.url)
                    .put("title", binding.title)
                    .put("addedAt", binding.addedAt),
            )
        }
    return array.toString()
}

private fun startChatGptBinding(
    context: Context,
    app: HubApp,
    currentBinding: ChatBinding?,
) {
    val repo = app.repo ?: return
    val intent = Intent(YBROWSER_SELECT_CHAT_ACTION).apply {
        setClassName(
            YBROWSER_PACKAGE,
            YBROWSER_EMBEDDED_ACTIVITY,
        )
        putExtra(YBROWSER_EXTRA_YAGAYHUB_BINDING_MODE, true)
        putExtra(
            EXTRA_CHAT_BIND_REPO,
            app.repoOwner + "/" + repo,
        )
        putExtra(EXTRA_CHAT_BIND_PROJECT, app.name)
        currentBinding?.url
            ?.takeIf { it.isNotBlank() }
            ?.let { putExtra(YBROWSER_EXTRA_URL, it) }
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "请先安装或更新 YBrowser",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun removeChatBindingFromYBrowser(
    context: Context,
    url: String,
) {
    val intent = Intent(YBROWSER_CHAT_BINDING_REMOVE_ACTION).apply {
        setPackage(YBROWSER_PACKAGE)
        putExtra(EXTRA_CHAT_BIND_URL, url)
    }
    runCatching { context.sendBroadcast(intent) }
}

private fun syncChatBindingToYBrowser(
    context: Context,
    repoKey: String,
    project: String,
    url: String,
    title: String,
) {
    val intent = Intent(YBROWSER_CHAT_BINDING_SYNC_ACTION).apply {
        setPackage(YBROWSER_PACKAGE)
        putExtra(EXTRA_CHAT_BIND_REPO, repoKey)
        putExtra(EXTRA_CHAT_BIND_PROJECT, project)
        putExtra(EXTRA_CHAT_BIND_URL, url)
        putExtra(EXTRA_CHAT_BIND_TITLE, title)
    }
    runCatching { context.sendBroadcast(intent) }
}

private fun returnChatBindingToRequester(
    context: Context,
    request: QuickChatBindingRequest,
    repoKey: String,
    project: String,
    url: String,
    title: String,
) {
    if (
        request.requesterPackage != null &&
        request.requesterPackage != YBROWSER_PACKAGE
    ) {
        return
    }

    val intent =
        Intent(YBROWSER_OPEN_BROWSER_ACTION).apply {
            setClassName(
                YBROWSER_PACKAGE,
                YBROWSER_EMBEDDED_ACTIVITY,
            )
            putExtra(
                YBROWSER_EXTRA_URL,
                url,
            )
            putExtra(
                YBROWSER_EXTRA_YAGAYHUB_BINDING_MODE,
                true,
            )
            putExtra(
                YBROWSER_EXTRA_YAGAYHUB_COMPACT_MODE,
                false,
            )
            putExtra(
                YBROWSER_EXTRA_YAGAYHUB_EMBEDDED,
                true,
            )
            putExtra(
                EXTRA_AI_WINDOW_ID,
                request.windowId.orEmpty(),
            )
            putExtra(
                EXTRA_CHAT_BIND_REPO,
                repoKey,
            )
            putExtra(
                EXTRA_CHAT_BIND_PROJECT,
                project,
            )
            putExtra(
                EXTRA_CHAT_BIND_URL,
                url,
            )
            putExtra(
                EXTRA_CHAT_BIND_TITLE,
                title,
            )
            putExtra(
                EXTRA_CHAT_TARGETS_JSON,
                chatTargetsJson(context),
            )
            addFlags(
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP,
            )
        }

    runCatching {
        context.startActivity(intent)
    }.onFailure {
        Toast.makeText(
            context,
            "请先安装或更新 YBrowser",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun openChatPopup(
    context: Context,
    url: String?,
    bindingRepoKey: String? = null,
    bindingProject: String? = null,
    bindingTitle: String? = null,
) {
    val aiIntent = Intent(YBROWSER_OPEN_AI_ACTION).apply {
        setClassName(
            YBROWSER_PACKAGE,
            YBROWSER_AI_WORKSPACE_ACTIVITY,
        )

        url?.takeIf {
            it.isNotBlank()
        }?.let {
            putExtra(
                YBROWSER_EXTRA_URL,
                it,
            )
            putExtra(
                EXTRA_CHAT_BIND_URL,
                it,
            )
        }

        putExtra(
            EXTRA_CHAT_TARGETS_JSON,
            chatTargetsJson(context),
        )

        putExtra(
            YBROWSER_EXTRA_YAGAYHUB_EMBEDDED,
            true,
        )

        if (!bindingRepoKey.isNullOrBlank()) {
            putExtra(
                EXTRA_CHAT_BIND_REPO,
                bindingRepoKey,
            )
            putExtra(
                EXTRA_CHAT_BIND_PROJECT,
                bindingProject.orEmpty(),
            )
            putExtra(
                EXTRA_CHAT_BIND_TITLE,
                bindingTitle.orEmpty(),
            )
        }

        addFlags(
            Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP,
        )
    }

    try {
        context.startActivity(aiIntent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(
            context,
            "请先安装或更新 YBrowser",
            Toast.LENGTH_SHORT,
        ).show()
    }
}

private fun openUrl(
    context: Context,
    url: String,
) {
    val intent = Intent(YBROWSER_OPEN_BROWSER_ACTION).apply {
        setClassName(
            YBROWSER_PACKAGE,
            YBROWSER_EMBEDDED_ACTIVITY,
        )
        putExtra(YBROWSER_EXTRA_URL, url)
        putExtra(YBROWSER_EXTRA_YAGAYHUB_BINDING_MODE, true)
        putExtra(YBROWSER_EXTRA_YAGAYHUB_COMPACT_MODE, false)
        putExtra(YBROWSER_EXTRA_YAGAYHUB_EMBEDDED, true)
        putExtra(EXTRA_CHAT_TARGETS_JSON, chatTargetsJson(context))
    }
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, "请先安装或更新 YBrowser", Toast.LENGTH_SHORT).show()
    }
}

private const val EXTRA_BIND_REQUESTER_PACKAGE =
    "com.yagay.YBrowser.extra.BIND_REQUESTER_PACKAGE"
private const val EXTRA_AI_WINDOW_ID =
    "com.yagay.YBrowser.extra.AI_WINDOW_ID"

private const val YBROWSER_PACKAGE = "com.yagay.YBrowser"
private const val YBROWSER_OPEN_AI_ACTION =
    "com.yagay.YBrowser.action.OPEN_AI"
private const val YBROWSER_AI_WORKSPACE_ACTIVITY =
    "com.yagay.ybrowser.ai.AiWorkspaceActivity"
private const val YBROWSER_EMBEDDED_ACTIVITY =
    "com.yagay.YBrowser.YagaYHubEmbeddedActivity"
private const val YBROWSER_EXTRA_URL = "com.yagay.YBrowser.extra.URL"
private const val YBROWSER_EXTRA_YAGAYHUB_BINDING_MODE =
    "com.yagay.YBrowser.extra.YAGAYHUB_BINDING_MODE"
private const val YBROWSER_EXTRA_YAGAYHUB_COMPACT_MODE =
    "com.yagay.YBrowser.extra.YAGAYHUB_COMPACT_MODE"
private const val YBROWSER_EXTRA_YAGAYHUB_EMBEDDED =
    "com.yagay.YBrowser.extra.YAGAYHUB_EMBEDDED"

@Composable
private fun YagaYHubTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) darkColorScheme() else lightColorScheme(),
        content = content,
    )
}
