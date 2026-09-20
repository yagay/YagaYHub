package com.yagay.YagaYHub

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.DownloadListener
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.core.view.setPadding

class BrowserActivity : ComponentActivity() {

    private lateinit var webView: WebView
    private lateinit var addressBar: EditText
    private lateinit var progressBar: ProgressBar

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        webView = WebView(this)
        addressBar = EditText(this)
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)

        setContentView(buildContentView())
        configureWebView()

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.canGoBack()) webView.goBack() else finish()
            }
        })

        val initialUrl = intent.getStringExtra(EXTRA_URL)
            ?: intent.dataString
            ?: HOME_URL
        loadInput(initialUrl)
    }

    private fun buildContentView(): View {
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(6))
        }

        fun toolButton(text: String, action: () -> Unit) = Button(this).apply {
            this.text = text
            minWidth = 0
            minimumWidth = 0
            setPadding(dp(8))
            setOnClickListener { action() }
        }

        toolbar.addView(toolButton("‹") { if (webView.canGoBack()) webView.goBack() })
        toolbar.addView(toolButton("›") { if (webView.canGoForward()) webView.goForward() })
        toolbar.addView(toolButton("⌂") { webView.loadUrl(HOME_URL) })
        toolbar.addView(toolButton("↻") { webView.reload() })

        addressBar.apply {
            isSingleLine = true
            hint = "搜索或输入网址"
            setSelectAllOnFocus(true)
            setOnEditorActionListener { _, _, _ ->
                loadInput(text.toString())
                true
            }
        }
        toolbar.addView(
            addressBar,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )

        toolbar.addView(toolButton("前往") { loadInput(addressBar.text.toString()) })
        toolbar.addView(toolButton("分享") { shareCurrentPage() })
        toolbar.addView(toolButton("外部") { openExternal(webView.url ?: return@toolButton) })

        progressBar.max = 100
        root.addView(toolbar)
        root.addView(
            progressBar,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(3))
        )
        root.addView(
            webView,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                0,
                1f
            )
        )
        return root
    }

    @Suppress("SetJavaScriptEnabled")
    private fun configureWebView() {
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)

        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            loadsImagesAutomatically = true
            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            useWideViewPort = true
            loadWithOverviewMode = true
            builtInZoomControls = true
            displayZoomControls = false
            mediaPlaybackRequiresUserGesture = true
            javaScriptCanOpenWindowsAutomatically = true
            setSupportMultipleWindows(true)
        }

        webView.webViewClient = InternalWebViewClient()
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                progressBar.progress = newProgress
                progressBar.visibility = if (newProgress in 1..99) View.VISIBLE else View.GONE
            }

            override fun onReceivedTitle(view: WebView?, title: String?) {
                if (!title.isNullOrBlank()) this@BrowserActivity.title = title
            }

            override fun onCreateWindow(
                view: WebView?,
                isDialog: Boolean,
                isUserGesture: Boolean,
                resultMsg: android.os.Message?
            ): Boolean {
                if (resultMsg == null) return false
                val popup = WebView(this@BrowserActivity)
                popup.settings.javaScriptEnabled = true
                popup.webViewClient = object : WebViewClient() {
                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return true
                        handleNavigation(url)
                        popup.destroy()
                        return true
                    }

                    @Deprecated("Deprecated in Java")
                    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                        if (!url.isNullOrBlank()) handleNavigation(url)
                        popup.destroy()
                        return true
                    }
                }
                val transport = resultMsg.obj as? WebView.WebViewTransport ?: return false
                transport.webView = popup
                resultMsg.sendToTarget()
                return true
            }
        }

        webView.setDownloadListener(DownloadListener { url, userAgent, contentDisposition, mimeType, _ ->
            runCatching {
                val fileName = android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType)
                val request = DownloadManager.Request(Uri.parse(url))
                    .setMimeType(mimeType)
                    .addRequestHeader("User-Agent", userAgent)
                    .addRequestHeader("Cookie", CookieManager.getInstance().getCookie(url))
                    .setTitle(fileName)
                    .setDescription("YagaYHub Browser")
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                    .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                getSystemService(DownloadManager::class.java).enqueue(request)
                Toast.makeText(this, "开始下载：$fileName", Toast.LENGTH_SHORT).show()
            }.onFailure {
                openExternal(url)
            }
        })
    }

    private inner class InternalWebViewClient : WebViewClient() {
        override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
            return handleNavigation(request?.url?.toString())
        }

        @Deprecated("Deprecated in Java")
        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
            return handleNavigation(url)
        }

        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
            if (!url.isNullOrBlank()) addressBar.setText(url)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            if (!url.isNullOrBlank()) addressBar.setText(url)
        }
    }

    private fun handleNavigation(url: String?): Boolean {
        if (url.isNullOrBlank()) return true
        val uri = runCatching { Uri.parse(url) }.getOrNull() ?: return true
        return when (uri.scheme?.lowercase()) {
            "http", "https", "about", "data", "javascript" -> {
                if (webView.url != url) webView.loadUrl(url)
                true
            }
            else -> {
                openExternal(url)
                true
            }
        }
    }

    private fun loadInput(rawInput: String) {
        val input = rawInput.trim()
        if (input.isBlank()) return

        val target = when {
            input.startsWith("http://", true) ||
                input.startsWith("https://", true) ||
                input.startsWith("about:", true) -> input
            looksLikeUrl(input) -> "https://$input"
            else -> SEARCH_URL + Uri.encode(input)
        }
        addressBar.setText(target)
        webView.loadUrl(target)
    }

    private fun looksLikeUrl(input: String): Boolean {
        return !input.contains(' ') &&
            (input.contains('.') || input.startsWith("localhost") || input.matches(Regex("""\d{1,3}(\.\d{1,3}){3}.*""")))
    }

    private fun shareCurrentPage() {
        val url = webView.url ?: return
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        startActivity(Intent.createChooser(send, "分享网页"))
    }

    private fun openExternal(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, "没有应用可以处理这个链接", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroy() {
        (webView.parent as? ViewGroup)?.removeView(webView)
        webView.stopLoading()
        webView.webChromeClient = null
        webView.webViewClient = WebViewClient()
        webView.destroy()
        super.onDestroy()
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    companion object {
        const val EXTRA_URL = "url"
        private const val HOME_URL = "https://www.google.com/"
        private const val SEARCH_URL = "https://www.google.com/search?q="

        fun intent(context: Context, url: String? = null): Intent {
            return Intent(context, BrowserActivity::class.java).apply {
                if (!url.isNullOrBlank()) putExtra(EXTRA_URL, url)
            }
        }
    }
}
