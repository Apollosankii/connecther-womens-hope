package com.womanglobal.connecther

import android.annotation.SuppressLint
import android.os.Bundle
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.appbar.MaterialToolbar

/**
 * In-app-only viewer for provider verification documents shown to clients.
 * [android.view.WindowManager.LayoutParams.FLAG_SECURE] limits screenshots and screen capture;
 * [WebView] keeps the signed URL inside the app and blocks download prompts.
 */
class SecureProviderDocumentActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
            android.view.WindowManager.LayoutParams.FLAG_SECURE,
        )
        setContentView(R.layout.activity_secure_provider_document)

        val url = intent.getStringExtra(EXTRA_URL)?.trim().orEmpty()
        val schemeOk =
            url.startsWith("https://", ignoreCase = true) || url.startsWith("http://", ignoreCase = true)
        if (url.isBlank() || !schemeOk) {
            Toast.makeText(this, R.string.profile_documents_unavailable, Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.secureDocToolbar)
        intent.getStringExtra(EXTRA_TITLE)?.trim()?.takeIf { it.isNotEmpty() }?.let { toolbar.title = it }
        toolbar.setNavigationOnClickListener { onBackPressedDispatcher.onBackPressed() }

        val webView = findViewById<WebView>(R.id.secureDocWebView)
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
        }
        webView.setDownloadListener { _, _, _, _, _ ->
            Toast.makeText(this, R.string.secure_document_download_blocked, Toast.LENGTH_SHORT).show()
        }
        webView.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val scheme = request?.url?.scheme?.lowercase()
                return scheme != "http" && scheme != "https"
            }
        }
        webView.webChromeClient = WebChromeClient()
        webView.loadUrl(url)
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
