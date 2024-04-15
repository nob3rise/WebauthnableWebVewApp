package app.web.simplewebauthn.webauthnablewebvewapp

import android.app.Activity
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.OnBackPressedCallback
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import androidx.webkit.WebViewCompat
import androidx.webkit.WebViewFeature
import kotlinx.coroutines.CoroutineScope

class MainActivity : AppCompatActivity() {
    private var webView : WebView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val credentialManagerHandler = CredentialManagerHandler(this)
        webView = findViewById<WebView>(R.id.webview)
        webView?.let {
            val url = "https://simplewebauthn.web.app/"

            it.settings.javaScriptEnabled = true
            val listenerSupported = WebViewFeature.isFeatureSupported(
                WebViewFeature.WEB_MESSAGE_LISTENER
            )
            if (listenerSupported) {
                // Inject local JavaScript that calls Credential Manager.
                hookWebAuthnWithListener(this, lifecycleScope, credentialManagerHandler)
            } else {
                // Fallback routine for unsupported API levels.
                Log.i(PasskeyWebListener.TAG, "listenerSupported in this environment");
            }
            it.loadUrl(url)
        }

        onBackPressedDispatcher.addCallback(object : OnBackPressedCallback(
            true // default to enabled
        ) {
            override fun handleOnBackPressed() {
                webView?.goBack()
            }
        })
    }

    private fun hookWebAuthnWithListener(activity: Activity, coroutineScope: CoroutineScope, credentialManagerHandler: CredentialManagerHandler) {
        val passkeyWebListener = PasskeyWebListener(
            activity, coroutineScope, credentialManagerHandler
        )

        val webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                super.onPageStarted(view, url, favicon)
                // Handle page load events
                passkeyWebListener.onPageStarted();
                webView?.evaluateJavascript(PasskeyWebListener.INJECTED_VAL, null)
            }

            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                return super.shouldOverrideUrlLoading(view, request)
            }
        }

        webView?.let {
            val rules = setOf("*")
            if (WebViewFeature.isFeatureSupported(WebViewFeature.WEB_MESSAGE_LISTENER)) {
                WebViewCompat.addWebMessageListener(
                    it, PasskeyWebListener.INTERFACE_NAME, rules, passkeyWebListener
                )
            }
            webView?.webViewClient = webViewClient
        }
    }
}