package com.womanglobal.connecther

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Build
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.google.android.material.appbar.MaterialToolbar
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

/**
 * In-app-only viewer for provider verification documents shown to clients.
 * [android.view.WindowManager.LayoutParams.FLAG_SECURE] limits screenshots and screen capture;
 * [WebView] keeps the signed URL inside the app and blocks download prompts.
 *
 * PDFs and common Office types do not render in a plain WebView (blank screen); we load them
 * through Google’s embedded viewer. [WindowCompat.setDecorFitsSystemWindows] avoids the toolbar
 * sitting under the status bar on SDK 35+ edge-to-edge defaults.
 */
class SecureProviderDocumentActivity : AppCompatActivity() {

    private val extensionsForEmbeddedViewer = listOf(
        ".pdf", ".doc", ".docx", ".ppt", ".pptx", ".xls", ".xlsx", ".odt", ".ods", ".odp",
    )

    /** Supabase signed URLs keep the file extension before `?`; WebView alone won’t paint PDFs. */
    private fun viewerUrlForSignedStorageLink(raw: String): String {
        val pathLower = raw.substringBefore('?').lowercase()
        val useViewer = extensionsForEmbeddedViewer.any { pathLower.endsWith(it) }
        if (!useViewer) return raw
        return runCatching {
            val enc = URLEncoder.encode(raw, StandardCharsets.UTF_8.name())
            "https://docs.google.com/gviewer?embedded=true&url=$enc"
        }.getOrDefault(raw)
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        // Target SDK 35: default edge-to-edge draws the toolbar under status icons; opt into fitting.
        WindowCompat.setDecorFitsSystemWindows(window, true)
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContentView(R.layout.activity_secure_provider_document)

        val urlRaw = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        val schemeOk =
            urlRaw.startsWith("https://", ignoreCase = true) || urlRaw.startsWith("http://", ignoreCase = true)
        if (urlRaw.isBlank() || !schemeOk) {
            Toast.makeText(this, R.string.profile_documents_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.secureDocToolbar)
        intent.getStringExtra(EXTRA_TITLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { toolbar.title = it }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val progress = findViewById<ProgressBar>(R.id.secureDocProgress)
        val webView = findViewById<WebView>(R.id.secureDocWebView)
        CookieManager.getInstance().setAcceptCookie(true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true)
        }
        webView.setOnLongClickListener { true }
        webView.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            loadWithOverviewMode = true
            useWideViewPort = true
            builtInZoomControls = true
            displayZoomControls = false
            allowFileAccess = false
            allowContentAccess = false
            setSupportMultipleWindows(false)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
        }
        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, R.string.secure_document_download_blocked, Toast.LENGTH_SHORT).show()
        }
        webView.webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = true
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                progress.visibility = View.GONE
            }

            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }

            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    progress.visibility = View.GONE
                    Toast.makeText(this@SecureProviderDocumentActivity, R.string.secure_document_load_failed, Toast.LENGTH_LONG).show()
                }
            }

            override fun onReceivedHttpError(
                view: WebView?,
                request: WebResourceRequest?,
                errorResponse: WebResourceResponse?,
            ) {
                val code = errorResponse?.statusCode ?: return
                if (request?.isForMainFrame == true && code >= 400) {
                    progress.visibility = View.GONE
                    Toast.makeText(this@SecureProviderDocumentActivity, R.string.secure_document_load_failed, Toast.LENGTH_LONG).show()
                }
            }
        }
        webView.webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                if (newProgress in 1..99) {
                    progress.isIndeterminate = false
                    progress.progress = newProgress
                }
            }
        }

        val loadUrl = viewerUrlForSignedStorageLink(urlRaw)
        webView.loadUrl(loadUrl)
    }

    override fun onDestroy() {
        findViewById<WebView>(R.id.secureDocWebView)?.let { wv ->
            wv.stopLoading()
            (wv.parent as? android.view.ViewGroup)?.removeView(wv)
            wv.destroy()
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_URL = "secure_doc_url"
        const val EXTRA_TITLE = "secure_doc_title"
    }
}
