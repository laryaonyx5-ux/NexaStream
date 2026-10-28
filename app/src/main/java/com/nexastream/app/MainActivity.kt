package com.nexastream.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Typeface
import android.util.TypedValue
import android.util.Rational
import android.content.pm.PackageManager
import java.io.ByteArrayInputStream
import java.net.URI

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences
    private var blockerEnabled = true
    private var webView: WebView? = null
    private var homeView: View? = null
    private var blockerIcon: TextView? = null
    private var blockerState: TextView? = null
    private var browserToolbar: View? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(10, 8, 16)
        window.navigationBarColor = Color.rgb(10, 8, 16)
        prefs = getSharedPreferences("nexastream_settings", MODE_PRIVATE)
        blockerEnabled = prefs.getBoolean("ad_blocker_enabled", true)
        root = FrameLayout(this)
        root.setBackgroundColor(Color.rgb(10, 8, 16))

        // Keep app content inside the usable screen area, below the status bar
        // and above Android's navigation bar, including Android 15 edge-to-edge.
        root.setOnApplyWindowInsetsListener { view, insets ->
            val bars = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                insets.getInsets(android.view.WindowInsets.Type.systemBars())
            } else {
                @Suppress("DEPRECATION")
                android.graphics.Insets.of(
                    insets.systemWindowInsetLeft,
                    insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight,
                    insets.systemWindowInsetBottom
                )
            }
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        setContentView(root)
        root.requestApplyInsets()
        showHome()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        browserToolbar?.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
    }

    private fun enterMiniPlayer() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && webView != null) {
            try {
                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (_: Exception) {
                android.widget.Toast.makeText(this, "Le mini-lecteur n’est pas disponible sur cet appareil.", android.widget.Toast.LENGTH_SHORT).show()
            }
        } else {
            android.widget.Toast.makeText(this, "Le mini-lecteur nécessite Android 8.0 ou plus.", android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    override fun onBackPressed() {
        val currentWeb = webView
        if (currentWeb != null && currentWeb.canGoBack()) {
            currentWeb.goBack()
        } else if (currentWeb != null) {
            showHome()
        } else {
            super.onBackPressed()
        }
    }

    private fun showHome() {
        webView?.destroy()
        webView = null
        root.removeAllViews()
        val backdrop = CircuitBackground(this)
        root.addView(backdrop, FrameLayout.LayoutParams(-1, -1))

        val page = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(22), dp(24), dp(22), dp(24))
        }
        root.addView(page, FrameLayout.LayoutParams(-1, -1))

        val logo = TextView(this).apply {
            text = "⬡"
            gravity = Gravity.CENTER
            textSize = 66f
            setTextColor(Color.rgb(174, 91, 255))
            typeface = Typeface.DEFAULT_BOLD
            background = rounded(Color.rgb(28, 12, 48), dp(38), Color.rgb(124, 42, 245), dp(2))
        }
        page.addView(logo, LinearLayout.LayoutParams(dp(94), dp(94)).apply { bottomMargin = dp(12) })

        val title = TextView(this).apply {
            text = "NexaStream"
            textSize = 30f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            gravity = Gravity.CENTER
            setTextColor(Color.WHITE)
        }
        page.addView(title)
        val subtitle = TextView(this).apply {
            text = "Twitch sans pub"
            textSize = 17f
            gravity = Gravity.CENTER
            setTextColor(Color.rgb(178, 139, 238))
            setPadding(0, dp(2), 0, dp(28))
        }
        page.addView(subtitle)

        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
        }
        page.addView(cards, LinearLayout.LayoutParams(-1, 0, 1f))

        val blockerCard = createCard().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        val blockerSymbol = TextView(this).apply {
            textSize = 34f
            gravity = Gravity.CENTER
            typeface = Typeface.DEFAULT_BOLD
        }
        blockerIcon = blockerSymbol
        blockerCard.addView(blockerSymbol, LinearLayout.LayoutParams(dp(58), -1))
        val blockerTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        blockerTexts.addView(TextView(this).apply {
            text = "Bloqueur de publicités"
            gravity = Gravity.START
            textSize = 16f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        val state = TextView(this).apply {
            gravity = Gravity.CENTER
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(10), dp(4), dp(10), dp(4))
        }
        blockerState = state
        blockerTexts.addView(state, LinearLayout.LayoutParams(-2, -2).apply { topMargin = dp(5) })
        blockerCard.addView(blockerTexts, LinearLayout.LayoutParams(0, -1, 1f))
        blockerCard.setOnClickListener {
            blockerEnabled = !blockerEnabled
            prefs.edit().putBoolean("ad_blocker_enabled", blockerEnabled).apply()
            updateBlockerVisuals()
        }
        cards.addView(blockerCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(8) })

        val twitchCard = createCard().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        twitchCard.addView(TextView(this).apply {
            text = "◎"
            gravity = Gravity.CENTER
            textSize = 40f
            typeface = Typeface.DEFAULT
            setTextColor(Color.rgb(174, 91, 255))
        }, LinearLayout.LayoutParams(dp(58), -1))
        val twitchTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        twitchTexts.addView(TextView(this).apply {
            text = "Twitch Web"
            gravity = Gravity.START
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        twitchTexts.addView(TextView(this).apply {
            text = "Ouvrir le site Twitch"
            gravity = Gravity.START
            textSize = 13f
            setTextColor(Color.rgb(178, 139, 238))
            setPadding(0, dp(4), 0, 0)
        })
        twitchCard.addView(twitchTexts, LinearLayout.LayoutParams(0, -1, 1f))
        twitchCard.setOnClickListener { openTwitch("Twitch Web") }
        cards.addView(twitchCard, LinearLayout.LayoutParams(-1, 0, 1f).apply { bottomMargin = dp(8) })

        val integratedCard = createCard().apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        integratedCard.addView(TextView(this).apply {
            text = "✦"
            gravity = Gravity.CENTER
            textSize = 40f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.rgb(174, 91, 255))
        }, LinearLayout.LayoutParams(dp(58), -1))
        val integratedTexts = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
        }
        integratedTexts.addView(TextView(this).apply {
            text = "Twitch intégré"
            gravity = Gravity.START
            textSize = 17f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        })
        integratedTexts.addView(TextView(this).apply {
            text = "Twitch Web + fonctions NexaStream"
            gravity = Gravity.START
            textSize = 12f
            setTextColor(Color.rgb(178, 139, 238))
            setPadding(0, dp(4), 0, 0)
        })
        integratedCard.addView(integratedTexts, LinearLayout.LayoutParams(0, -1, 1f))
        integratedCard.setOnClickListener { openTwitch("Twitch intégré") }
        cards.addView(integratedCard, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = TextView(this).apply {
            text = "Navigation web intégrée"
            gravity = Gravity.CENTER
            textSize = 12f
            setTextColor(Color.rgb(135, 119, 157))
            setPadding(0, dp(20), 0, dp(2))
        }
        page.addView(footer)
        homeView = page
        updateBlockerVisuals()
    }

    private fun createCard(): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(14), dp(10), dp(14))
        background = rounded(Color.argb(205, 16, 12, 25), dp(22), Color.rgb(151, 58, 255), dp(2))
        isFocusable = true
        isClickable = true
        elevation = dp(4).toFloat()
    }

    private fun updateBlockerVisuals() {
        blockerIcon?.apply {
            text = if (blockerEnabled) "🛡✓" else "🛡×"
            setTextColor(if (blockerEnabled) Color.rgb(44, 255, 142) else Color.rgb(255, 64, 78))
        }
        blockerState?.apply {
            text = if (blockerEnabled) "ACTIVÉ" else "DÉSACTIVÉ"
            setTextColor(if (blockerEnabled) Color.rgb(44, 255, 142) else Color.rgb(255, 82, 92))
            background = rounded(
                if (blockerEnabled) Color.argb(45, 0, 230, 110) else Color.argb(45, 255, 40, 65),
                dp(22),
                if (blockerEnabled) Color.rgb(0, 190, 91) else Color.rgb(230, 45, 63),
                dp(2)
            )
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun openTwitch(screenTitle: String = "Twitch Web") {
        root.removeAllViews()
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.rgb(10, 8, 16))
        }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                intArrayOf(Color.rgb(29, 16, 44), Color.rgb(12, 10, 18)))
        }
        val back = Button(this).apply {
            text = "‹ Accueil"
            setTextColor(Color.WHITE)
            background = rounded(Color.rgb(39, 21, 57), dp(16), Color.rgb(151, 58, 255), dp(1))
            setOnClickListener { showHome() }
        }
        toolbar.addView(back, LinearLayout.LayoutParams(-2, dp(44)))
        toolbar.addView(TextView(this).apply {
            text = "  $screenTitle"
            textSize = 18f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        if (screenTitle == "Twitch intégré") {
            val clipsButton = Button(this).apply {
                text = "Clips"
                textSize = 12f
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(39, 21, 57), dp(16), Color.rgb(151, 58, 255), dp(1))
                setOnClickListener { webView?.loadUrl("https://www.twitch.tv/directory/all/clips") }
            }
            toolbar.addView(clipsButton, LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(6) })
            val miniPlayer = Button(this).apply {
                text = "Mini-lecteur"
                textSize = 12f
                setTextColor(Color.WHITE)
                background = rounded(Color.rgb(39, 21, 57), dp(16), Color.rgb(151, 58, 255), dp(1))
                setOnClickListener { enterMiniPlayer() }
            }
            toolbar.addView(miniPlayer, LinearLayout.LayoutParams(-2, dp(44)).apply { marginStart = dp(8) })
        }
        browserToolbar = toolbar
        container.addView(toolbar, LinearLayout.LayoutParams(-1, -2))

        val browser = WebView(this)
        webView = browser
        browser.setBackgroundColor(Color.BLACK)
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.loadsImagesAutomatically = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = true
        browser.settings.setSupportMultipleWindows(false)
        browser.settings.cacheMode = WebSettings.LOAD_DEFAULT
        browser.settings.mediaPlaybackRequiresUserGesture = false
        browser.settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (blockerEnabled && isKnownAdOrTracker(url)) {
                    return WebResourceResponse("text/plain", "utf-8", 204, "Blocked by NexaStream", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                }
                return super.shouldInterceptRequest(view, request)
            }
        }
        container.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(container, FrameLayout.LayoutParams(-1, -1))
        browser.loadUrl("https://www.twitch.tv/")
    }

    private fun isKnownAdOrTracker(url: String): Boolean {
        val host = try { URI(url).host?.lowercase() ?: return false } catch (_: Exception) { return false }
        val blockedHosts = listOf(
            "doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "adsystem.com", "adservice.google.com", "amazon-adsystem.com",
            "scorecardresearch.com", "quantserve.com", "analytics.tiktok.com",
            "adnxs.com", "ads-twitter.com", "adsrvr.org", "adform.net",
            "taboola.com", "outbrain.com", "criteo.com", "rubiconproject.com",
            "pubmatic.com", "openx.net", "casalemedia.com", "advertising.com"
        )
        return blockedHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply {
            setColor(fill)
            cornerRadius = radius.toFloat()
            setStroke(strokeWidth, stroke)
        }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private class CircuitBackground(context: Context) : View(context) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(48, 163, 75, 255)
            style = Paint.Style.STROKE
            strokeWidth = 1.4f
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(25, 133, 45, 255)
            style = Paint.Style.STROKE
            strokeWidth = 5f
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(10, 8, 16))
            val w = width.toFloat()
            val h = height.toFloat()
            val p = Path().apply {
                moveTo(-w * .1f, h * .22f); lineTo(w * .18f, h * .22f); lineTo(w * .27f, h * .08f); lineTo(w * .44f, h * .08f)
                moveTo(w * .65f, 0f); lineTo(w * .65f, h * .14f); lineTo(w * .82f, h * .14f); lineTo(w * .94f, h * .28f); lineTo(w * 1.1f, h * .28f)
                moveTo(-w * .1f, h * .76f); lineTo(w * .15f, h * .76f); lineTo(w * .22f, h * .88f); lineTo(w * .42f, h * .88f)
                moveTo(w * .72f, h * .92f); lineTo(w * .82f, h * .78f); lineTo(w * 1.1f, h * .78f)
            }
            canvas.drawPath(p, glow)
            canvas.drawPath(p, line)
            for (i in 0..14) {
                val x = (i * 83 % (width.coerceAtLeast(1))).toFloat()
                val y = (i * 137 % (height.coerceAtLeast(1))).toFloat()
                canvas.drawCircle(x, y, 2.2f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.argb(55, 171, 83, 255) })
            }
        }
    }
}
