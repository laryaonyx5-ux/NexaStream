package com.nexastream.app

import android.annotation.SuppressLint
import android.app.Activity
import android.app.PictureInPictureParams
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.util.Rational
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.io.ByteArrayInputStream
import java.net.URI

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences
    private var blockerEnabled = true
    private var webView: WebView? = null
    private var browserToolbar: View? = null
    private var currentTab = "Accueil"

    private val purple = Color.rgb(169, 91, 255)
    private val muted = Color.rgb(183, 164, 207)
    private val bg = Color.rgb(10, 8, 16)
    private val panel = Color.rgb(25, 18, 36)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = bg
        window.navigationBarColor = bg
        prefs = getSharedPreferences("nexastream_settings", MODE_PRIVATE)
        blockerEnabled = prefs.getBoolean("ad_blocker_enabled", true)
        root = FrameLayout(this).apply {
            setBackgroundColor(bg)
            setOnApplyWindowInsetsListener { view, insets ->
                view.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop,
                    insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
                insets
            }
        }
        setContentView(root)
        root.requestApplyInsets()
        showTab("Accueil")
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: android.content.res.Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        browserToolbar?.visibility = if (isInPictureInPictureMode) View.GONE else View.VISIBLE
    }

    private fun enterMiniPlayer() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O && webView != null) {
            try { enterPictureInPictureMode(PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()) }
            catch (_: Exception) { toast("Le mini-lecteur n’est pas disponible sur cet appareil.") }
        } else toast("Ouvre d’abord un live pour utiliser le mini-lecteur.")
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        val browser = webView
        if (browser != null && browser.canGoBack()) browser.goBack()
        else if (browser != null) showTab("Accueil")
        else if (currentTab != "Accueil") showTab("Accueil")
        else super.onBackPressed()
    }

    private fun showTab(tab: String) {
        webView?.stopLoading()
        webView?.destroy()
        webView = null
        browserToolbar = null
        currentTab = tab
        root.removeAllViews()
        root.addView(CircuitBackground(this), FrameLayout.LayoutParams(-1, -1))

        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        val top = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(10))
        }
        val brand = TextView(this).apply {
            text = "⬡  NexaStream"
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }
        top.addView(brand, LinearLayout.LayoutParams(0, -2, 1f))
        top.addView(actionButton("⌕", "Rechercher") { showTab("Recherche") }.apply { textSize = 25f },
            LinearLayout.LayoutParams(dp(50), dp(46)))
        shell.addView(top)

        val scroll = ScrollView(this).apply { isFillViewport = true; isFocusable = false }
        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(4), dp(18), dp(18))
        }
        scroll.addView(content)
        shell.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        when (tab) {
            "Accueil" -> homeContent(content)
            "Découvrir" -> discoverContent(content)
            "Recherche" -> searchContent(content)
            "Suivis" -> followingContent(content)
            "Notifications" -> notificationsContent(content)
            "Paramètres" -> settingsContent(content)
        }
        shell.addView(bottomNav(tab), LinearLayout.LayoutParams(-1, -2))
    }

    private fun homeContent(parent: LinearLayout) {
        val hero = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(24), dp(20), dp(22))
            background = gradientCard()
        }
        label(hero, "TON UNIVERS, EN DIRECT", 11f, purple, true)
        label(hero, "Les lives, à ta façon.", 28f, Color.WHITE, true, top = 10)
        label(hero, "Une expérience Twitch repensée pour mobile et Android TV.", 15f, muted, top = 8)
        hero.addView(actionButton("▶   Explorer les lives", "Découvrir") { showTab("Découvrir") },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
        parent.addView(hero, fullWidth().apply { bottomMargin = dp(22) })

        sectionTitle(parent, "Accès rapide", "Tes portes d’entrée")
        actionCard(parent, "◉", "Ouvrir Twitch", "Se connecter ou regarder sur le site officiel") { openTwitch("Twitch officiel", "https://www.twitch.tv/") }
        actionCard(parent, "⌕", "Découvrir les lives", "Parcourir les catégories Twitch") { openTwitch("Découvrir Twitch", "https://www.twitch.tv/directory") }
        actionCard(parent, "♡", "Mes suivis", "Préparer l’espace de tes chaînes suivies") { showTab("Suivis") }
        sectionTitle(parent, "À propos de NexaStream", "Une interface native, en construction")
        label(parent, "Les menus et la navigation sont intégrés à l’application. Les lives et les données du compte nécessitent les connexions officielles de Twitch ; aucune chaîne ou notification fictive n’est affichée.", 13f, muted)
    }

    private fun discoverContent(parent: LinearLayout) {
        label(parent, "DÉCOUVRIR", 12f, purple, true)
        label(parent, "Trouve ton prochain live", 25f, Color.WHITE, true, top = 6)
        label(parent, "Choisis une catégorie pour l’ouvrir sur Twitch. Les résultats en direct viennent du service officiel, pas de données inventées.", 14f, muted, top = 8, bottom = 16)
        val categories = listOf(
            Triple("🎮", "Jeux vidéo", "https://www.twitch.tv/directory/category/games"),
            Triple("🗣", "Just Chatting", "https://www.twitch.tv/directory/category/just-chatting"),
            Triple("🎵", "Musique", "https://www.twitch.tv/directory/category/music"),
            Triple("🏆", "Esport", "https://www.twitch.tv/directory"),
            Triple("🎨", "Création", "https://www.twitch.tv/directory"),
            Triple("🔥", "Tendances", "https://www.twitch.tv/directory")
        )
        categories.forEach { (icon, name, url) -> actionCard(parent, icon, name, "Voir les chaînes et les lives sur Twitch") { openTwitch(name, url) } }
        actionButton("Ouvrir tout l’annuaire Twitch  ↗", "Annuaire Twitch") { openTwitch("Annuaire Twitch", "https://www.twitch.tv/directory") }
            .also { parent.addView(it, fullWidth().apply { topMargin = dp(10) }) }
    }

    private fun searchContent(parent: LinearLayout) {
        label(parent, "RECHERCHE", 12f, purple, true)
        label(parent, "Qui veux-tu regarder ?", 25f, Color.WHITE, true, top = 6)
        val field = EditText(this).apply {
            hint = "Chaîne, jeu ou catégorie"
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(panel, dp(15), Color.rgb(90, 55, 120), dp(1))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
            setOnEditorActionListener { _, _, _ ->
                val query = text.toString().trim()
                if (query.isNotEmpty()) openTwitch("Recherche", "https://www.twitch.tv/search?term=" + Uri.encode(query))
                true
            }
        }
        parent.addView(field, fullWidth().apply { height = dp(54); topMargin = dp(18); bottomMargin = dp(12) })
        parent.addView(actionButton("Rechercher sur Twitch  ⌕", "Rechercher") {
            val query = field.text.toString().trim()
            if (query.isEmpty()) toast("Saisis un nom de chaîne, un jeu ou une catégorie.")
            else openTwitch("Recherche", "https://www.twitch.tv/search?term=" + Uri.encode(query))
        }, fullWidth())
        sectionTitle(parent, "Idées", "Pour commencer")
        listOf("Minecraft", "Just Chatting", "Musique", "Esport").forEach { term ->
            actionCard(parent, "⌕", term, "Rechercher « $term » sur Twitch") {
                openTwitch("Recherche : $term", "https://www.twitch.tv/search?term=" + Uri.encode(term))
            }
        }
    }

    private fun followingContent(parent: LinearLayout) {
        label(parent, "TES CHAÎNES", 12f, purple, true)
        label(parent, "Tes suivis, au même endroit.", 25f, Color.WHITE, true, top = 6)
        label(parent, "Connecte-toi via Twitch pour retrouver ton compte. La synchronisation native des abonnements nécessite la connexion OAuth officielle ; cet écran ne prétend pas déjà y avoir accès.", 14f, muted, top = 8, bottom = 18)
        actionCard(parent, "◎", "Se connecter à Twitch", "Ouvrir le site officiel pour te connecter") { openTwitch("Connexion Twitch", "https://www.twitch.tv/login") }
        actionCard(parent, "♡", "Voir mes chaînes suivies", "Ouvrir la liste officielle des chaînes suivies") { openTwitch("Chaînes suivies", "https://www.twitch.tv/directory/following") }
        label(parent, "Quand l’accès officiel sera configuré, cette page pourra afficher les chaînes dans des cartes natives et alimenter les notifications.", 13f, muted, top = 14)
    }

    private fun notificationsContent(parent: LinearLayout) {
        label(parent, "CENTRE DE NOTIFICATIONS", 12f, purple, true)
        label(parent, "Ne rate plus un live.", 25f, Color.WHITE, true, top = 6)
        label(parent, "NexaStream est conçu pour réunir ici les alertes de tes chaînes suivies.", 14f, muted, top = 8, bottom = 16)
        actionCard(parent, "♧", "Connexion Twitch requise", "Les alertes réelles demandent l’accès autorisé à ton compte et aux événements Twitch.") { openTwitch("Connexion Twitch", "https://www.twitch.tv/login") }
        actionCard(parent, "◉", "Tester les lives sur Twitch", "Consulter les lives de tes chaînes suivies") { openTwitch("Chaînes suivies", "https://www.twitch.tv/directory/following") }
        label(parent, "Important : les notifications natives de nouveaux lives ne sont pas encore actives dans cette version. Il faudra brancher l’authentification et le mécanisme officiel de suivi des événements ; je ne simule pas de fausses alertes.", 13f, muted, top = 14)
    }

    private fun settingsContent(parent: LinearLayout) {
        label(parent, "PERSONNALISATION", 12f, purple, true)
        label(parent, "Paramètres", 25f, Color.WHITE, true, top = 6)
        actionCard(parent, "🛡", "Filtre de publicités connu", if (blockerEnabled) "Activé — filtre certains domaines publicitaires" else "Désactivé") {
            blockerEnabled = !blockerEnabled
            prefs.edit().putBoolean("ad_blocker_enabled", blockerEnabled).apply()
            showTab("Paramètres")
        }
        label(parent, "Ce filtre bloque certains domaines connus dans le navigateur intégré. Il ne garantit pas le blocage des publicités vidéo Twitch : elles peuvent être diffusées depuis la même infrastructure que le contenu.", 13f, muted, top = 8, bottom = 14)
        actionCard(parent, "▶", "Mini-lecteur", "Disponible pendant la lecture dans le navigateur intégré") { openTwitch("Twitch intégré", "https://www.twitch.tv/") }
        actionCard(parent, "📺", "Mode Android TV", "Navigation pensée pour télécommande et pavé directionnel") {
            toast("Utilise les flèches de la télécommande pour sélectionner les cartes.")
        }
        actionCard(parent, "ℹ", "Version", "NexaStream — aperçu natif en développement") {
            toast("NexaStream • branche de test patch-1")
        }
    }

    private fun bottomNav(selected: String): View {
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(4), dp(5), dp(4), dp(5))
            background = rounded(Color.rgb(17, 12, 25), dp(18), Color.rgb(48, 32, 64), dp(1))
        }
        val tabs = listOf("Accueil" to "⌂", "Découvrir" to "◈", "Suivis" to "♡", "Notifications" to "♧", "Paramètres" to "⚙")
        tabs.forEach { (name, symbol) ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                isFocusable = true
                isClickable = true
                setPadding(dp(2), dp(6), dp(2), dp(6))
                background = if (name == selected) rounded(Color.rgb(55, 28, 78), dp(13), purple, dp(1)) else rounded(Color.TRANSPARENT, dp(13), Color.TRANSPARENT, 0)
                setOnClickListener { showTab(name) }
            }
            item.addView(TextView(this).apply {
                text = symbol; textSize = 22f; gravity = Gravity.CENTER
                setTextColor(if (name == selected) purple else muted)
            })
            item.addView(TextView(this).apply {
                text = when (name) { "Notifications" -> "Alertes"; "Paramètres" -> "Réglages"; else -> name }
                textSize = 9f; gravity = Gravity.CENTER
                setTextColor(if (name == selected) Color.WHITE else muted)
            })
            bar.addView(item, LinearLayout.LayoutParams(0, dp(56), 1f).apply { marginStart = dp(2); marginEnd = dp(2) })
        }
        return bar
    }

    private fun sectionTitle(parent: LinearLayout, title: String, subtitle: String) {
        label(parent, title, 19f, Color.WHITE, true, top = 6)
        label(parent, subtitle, 12f, muted, top = 3, bottom = 10)
    }

    private fun actionCard(parent: LinearLayout, symbol: String, title: String, description: String, action: () -> Unit) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(14), dp(12), dp(14), dp(12))
            background = rounded(Color.argb(235, 22, 16, 32), dp(18), Color.rgb(72, 43, 94), dp(1))
            isFocusable = true
            isClickable = true
            setOnClickListener { action() }
            setOnFocusChangeListener { v, focused ->
                v.background = rounded(if (focused) Color.rgb(49, 28, 66) else Color.argb(235, 22, 16, 32),
                    dp(18), if (focused) purple else Color.rgb(72, 43, 94), dp(if (focused) 2 else 1))
            }
        }
        card.addView(TextView(this).apply {
            text = symbol; textSize = 28f; gravity = Gravity.CENTER; setTextColor(purple)
        }, LinearLayout.LayoutParams(dp(48), dp(48)))
        val texts = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), 0, 0, 0) }
        label(texts, title, 15f, Color.WHITE, true)
        label(texts, description, 12f, muted, top = 4)
        card.addView(texts, LinearLayout.LayoutParams(0, -2, 1f))
        card.addView(TextView(this).apply { text = "›"; textSize = 27f; setTextColor(muted) })
        parent.addView(card, fullWidth().apply { bottomMargin = dp(9) })
    }

    private fun label(parent: LinearLayout, text: String, size: Float, color: Int, bold: Boolean = false, top: Int = 0, bottom: Int = 0) {
        val view = TextView(this).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            if (bold) typeface = Typeface.create("sans-serif", Typeface.BOLD)
        }
        parent.addView(view, LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(top); bottomMargin = dp(bottom) })
    }

    private fun actionButton(text: String, description: String, action: () -> Unit) = Button(this).apply {
        this.text = text
        contentDescription = description
        textSize = 14f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(Color.WHITE)
        isAllCaps = false
        background = rounded(Color.rgb(112, 45, 190), dp(16), Color.rgb(191, 128, 255), dp(1))
        isFocusable = true
        setOnClickListener { action() }
        setOnFocusChangeListener { v, focused ->
            v.background = rounded(if (focused) Color.rgb(143, 67, 226) else Color.rgb(112, 45, 190),
                dp(16), if (focused) Color.WHITE else Color.rgb(191, 128, 255), dp(1))
        }
    }

    private fun fullWidth() = LinearLayout.LayoutParams(-1, -2)
    private fun gradientCard() = GradientDrawable(GradientDrawable.Orientation.TL_BR,
        intArrayOf(Color.rgb(48, 22, 72), Color.rgb(21, 14, 34), Color.rgb(38, 18, 54))).apply {
        cornerRadius = dp(24).toFloat()
        setStroke(dp(1), Color.rgb(128, 64, 182))
    }
    private fun rounded(fill: Int, radius: Int, stroke: Int, strokeWidth: Int): GradientDrawable =
        GradientDrawable().apply { setColor(fill); cornerRadius = radius.toFloat(); if (strokeWidth > 0) setStroke(strokeWidth, stroke) }
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_SHORT).show()

    @SuppressLint("SetJavaScriptEnabled")
    private fun openTwitch(screenTitle: String, url: String) {
        webView?.destroy()
        root.removeAllViews()
        val container = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setBackgroundColor(bg) }
        val toolbar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(5), dp(10), dp(5))
            background = GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, intArrayOf(Color.rgb(29, 16, 44), Color.rgb(12, 10, 18)))
        }
        toolbar.addView(actionButton("‹ Accueil", "Retour à l’accueil") { showTab("Accueil") },
            LinearLayout.LayoutParams(-2, dp(44)))
        toolbar.addView(TextView(this).apply {
            text = screenTitle; textSize = 16f; typeface = Typeface.DEFAULT_BOLD
            setTextColor(Color.WHITE); setPadding(dp(10), 0, 0, 0)
        }, LinearLayout.LayoutParams(0, -2, 1f))
        toolbar.addView(actionButton("Mini-lecteur", "Activer le mini-lecteur") { enterMiniPlayer() },
            LinearLayout.LayoutParams(-2, dp(44)))
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
                val requestUrl = request?.url?.toString() ?: return null
                if (blockerEnabled && isKnownAdOrTracker(requestUrl))
                    return WebResourceResponse("text/plain", "utf-8", 204, "Blocked by NexaStream", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                return super.shouldInterceptRequest(view, request)
            }
        }
        container.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(container, FrameLayout.LayoutParams(-1, -1))
        browser.loadUrl(url)
    }

    private fun isKnownAdOrTracker(url: String): Boolean {
        val host = try { URI(url).host?.lowercase() ?: return false } catch (_: Exception) { return false }
        val blockedHosts = listOf("doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "adsystem.com", "adservice.google.com", "amazon-adsystem.com",
            "scorecardresearch.com", "quantserve.com", "analytics.tiktok.com")
        return blockedHosts.any { host == it || host.endsWith(".$it") }
    }

    private class CircuitBackground(context: Context) : View(context) {
        private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(35, 163, 75, 255); style = Paint.Style.STROKE; strokeWidth = 1.4f
        }
        private val glow = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(20, 133, 45, 255); style = Paint.Style.STROKE; strokeWidth = 5f
        }
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            canvas.drawColor(Color.rgb(10, 8, 16))
            val w = width.toFloat(); val h = height.toFloat()
            val path = Path().apply {
                moveTo(-w * .1f, h * .22f); lineTo(w * .18f, h * .22f); lineTo(w * .27f, h * .08f); lineTo(w * .44f, h * .08f)
                moveTo(w * .65f, 0f); lineTo(w * .65f, h * .14f); lineTo(w * .82f, h * .14f); lineTo(w * .94f, h * .28f); lineTo(w * 1.1f, h * .28f)
                moveTo(-w * .1f, h * .76f); lineTo(w * .15f, h * .76f); lineTo(w * .22f, h * .88f); lineTo(w * .42f, h * .88f)
                moveTo(w * .72f, h * .92f); lineTo(w * .82f, h * .78f); lineTo(w * 1.1f, h * .78f)
            }
            canvas.drawPath(path, glow); canvas.drawPath(path, line)
        }
    }
}
