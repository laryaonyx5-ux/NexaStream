package com.nexastream.app

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Build
import android.net.Uri
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.KeyEvent
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.pm.PackageManager
import android.text.InputType
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import java.io.ByteArrayInputStream
import java.net.URI
import java.net.URLEncoder

class MainActivity : Activity() {
    private lateinit var root: FrameLayout
    private lateinit var prefs: SharedPreferences
    private var blockerEnabled = true
    private var currentTab = "Accueil"
    private var searchQuery = ""
    private var chatWebView: WebView? = null
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
        createNotificationChannel()
        FollowedLiveWorker.schedule(this)
        handleOAuthCallback(intent)
        showTab("Accueil")
        checkForFollowedLiveNotifications()
        intent?.getStringExtra("open_channel")?.takeIf { it.isNotBlank() }?.let { openLive(it) }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
        showTab("Paramètres")
        intent.getStringExtra("open_channel")?.takeIf { it.isNotBlank() }?.let { openLive(it) }
    }

    private fun handleOAuthCallback(intent: android.content.Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme != "nexastream" || uri.host != "auth") return

        val values = linkedMapOf<String, String>()
        uri.queryParameterNames.forEach { key ->
            uri.getQueryParameter(key)?.let { values[key] = it }
        }
        uri.fragment?.split("&")?.forEach { part ->
            val bits = part.split("=", limit = 2)
            if (bits.size == 2) {
                val key = java.net.URLDecoder.decode(bits[0], "UTF-8")
                val value = java.net.URLDecoder.decode(bits[1], "UTF-8")
                values[key] = value
            }
        }

        val expectedState = prefs.getString("twitch_oauth_state", null)
        prefs.edit().remove("twitch_oauth_state").apply()
        if (expectedState.isNullOrBlank() || values["state"] != expectedState) {
            toast("Retour de connexion Twitch invalide. Réessaie.")
            return
        }

        val error = values["error"]
        if (!error.isNullOrBlank()) {
            toast("Connexion Twitch refusée : " + error)
            return
        }

        val token = values["access_token"]
        if (token.isNullOrBlank()) {
            toast("Twitch n’a pas renvoyé de jeton. Vérifie la configuration OAuth.")
            return
        }

        prefs.edit().putString("twitch_access_token", token).apply()
        toast("Connexion Twitch reçue. Vérification du compte…")
        Thread {
            try {
                val api = TwitchApi(configuredClientId(), token)
                val user = api.currentUser()
                prefs.edit().putString("twitch_user_id", user.optString("id"))
                    .putString("twitch_user_name", user.optString("display_name")).apply()
                runOnUiThread {
                    toast("Connecté à Twitch : " + user.optString("display_name"))
                    showTab("Accueil")
                    checkForFollowedLiveNotifications()
                }
            } catch (e: Exception) {
                prefs.edit().remove("twitch_access_token").apply()
                runOnUiThread { toast("Connexion refusée : " + (e.message ?: "erreur Twitch")) }
            }
        }.start()
    }

    private fun configuredClientId(): String =
        prefs.getString("twitch_client_id", BuildConfig.TWITCH_CLIENT_ID)?.trim().orEmpty()

    private fun configureTwitchClientId() {
        val input = EditText(this).apply {
            hint = "Client ID Twitch"
            setText(prefs.getString("twitch_client_id", configuredClientId()))
            setSingleLine(true)
            inputType = InputType.TYPE_CLASS_TEXT
            setTextColor(Color.WHITE)
            setHintTextColor(muted)
        }
        val wrapper = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(8), dp(20), 0)
            addView(input, LinearLayout.LayoutParams(-1, dp(54)))
        }
        AlertDialog.Builder(this)
            .setTitle("Configurer Twitch")
            .setMessage("Client ID public de ton application Twitch. Il est normalement déjà intégré à NexaStream. Ne colle jamais de Client Secret ni ton mot de passe Twitch.")
            .setView(wrapper)
            .setNegativeButton("Annuler", null)
            .setNeutralButton("Effacer") { _, _ ->
                prefs.edit().remove("twitch_client_id").apply()
                toast("Client ID local effacé.")
                showTab("Paramètres")
            }
            .setPositiveButton("Enregistrer") { _, _ ->
                val id = input.text.toString().trim()
                if (id.isBlank()) {
                    prefs.edit().remove("twitch_client_id").apply()
                    toast("Client ID effacé.")
                } else {
                    prefs.edit().putString("twitch_client_id", id).apply()
                    toast("Client ID enregistré sur cet appareil.")
                }
                showTab("Paramètres")
            }
            .show()
    }

    private fun startTwitchOAuth() {
        val clientId = configuredClientId()
        if (clientId.isBlank()) {
            toast("Le Client ID Twitch n’est pas configuré.")
            configureTwitchClientId()
            return
        }

        // Twitch's implicit grant returns the user token in the redirect URI fragment.
        // The redirect URI must also be registered exactly in the Twitch developer console.
        val redirectUri = "nexastream://auth"
        val state = java.util.UUID.randomUUID().toString()
        prefs.edit().putString("twitch_oauth_state", state).apply()
        val scopes = "user:read:follows"
        val authUri = Uri.parse("https://id.twitch.tv/oauth2/authorize").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "token")
            .appendQueryParameter("scope", scopes)
            .appendQueryParameter("state", state)
            .build()

        try {
            toast("Ouverture de la connexion officielle Twitch…")
            startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, authUri))
        } catch (e: Exception) {
            toast("Impossible d’ouvrir Twitch : " + (e.message ?: "erreur"))
        }
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (currentTab != "Accueil") showTab("Accueil") else super.onBackPressed()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && currentTab != "Accueil") {
            showTab("Accueil")
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun showTab(tab: String) {
        chatWebView?.stopLoading()
        chatWebView?.destroy()
        chatWebView = null
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
        top.addView(TextView(this).apply {
            text = "⬡  NexaStream"
            textSize = 23f
            typeface = Typeface.create("sans-serif", Typeface.BOLD)
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, -2, 1f))
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
        label(hero, "NEXASTREAM • MODE NATIF", 11f, purple, true)
        label(hero, "Les lives, à ta façon.", 28f, Color.WHITE, true, top = 10)
        label(hero, "Une interface conçue pour naviguer sans ouvrir de pages web à chaque menu.", 15f, muted, top = 8)
        hero.addView(actionButton("Explorer les catégories  →", "Découvrir") { showTab("Découvrir") },
            LinearLayout.LayoutParams(-1, dp(52)).apply { topMargin = dp(18) })
        parent.addView(hero, fullWidth().apply { bottomMargin = dp(22) })

        actionCard(parent, "◎", "Se connecter à Twitch", "Connecte ton compte pour charger tes chaînes suivies") { startTwitchOAuth() }
        sectionTitle(parent, "En direct", "Les streams officiels Twitch, triés par audience")
        val liveContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(liveContainer, fullWidth())
        loadTopStreams(liveContainer)
        actionCard(parent, "◈", "Découvrir", "Parcourir les catégories dans l’interface NexaStream") { showTab("Découvrir") }
        actionCard(parent, "♡", "Mes chaînes suivies", "Retrouver ici les chaînes de ton compte") { showTab("Suivis") }
        actionCard(parent, "♧", "Centre d’alertes", "Préparer les notifications natives de nouveaux lives") { showTab("Notifications") }
    }

    private fun discoverContent(parent: LinearLayout) {
        label(parent, "DÉCOUVRIR", 12f, purple, true)
        label(parent, "Trouve ton prochain live", 25f, Color.WHITE, true, top = 6)
        label(parent, "Les catégories sont affichées dans NexaStream. Elles seront alimentées par les données officielles Twitch lorsque la connexion API sera configurée.", 14f, muted, top = 8, bottom = 16)
        val categories = listOf(
            Triple("🎮", "Jeux vidéo", "games"),
            Triple("🗣", "Just Chatting", "just-chatting"),
            Triple("🎵", "Musique", "music"),
            Triple("🏆", "Esport", "esports"),
            Triple("🎨", "Création", "art"),
            Triple("🔥", "Tendances", "trending")
        )
        categories.forEach { (icon, name, key) ->
            actionCard(parent, icon, name, "Ouvrir cette catégorie dans NexaStream") {
                prefs.edit().putString("selected_category", key).apply()
                showCategoryDetail(name, key)
            }
        }
    }

    private fun showCategoryDetail(name: String, key: String) {
        currentTab = "Catégorie"
        root.removeAllViews()
        root.addView(CircuitBackground(this), FrameLayout.LayoutParams(-1, -1))
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        shell.addView(actionButton("‹  Découvrir", "Retour aux catégories") { showTab("Découvrir") },
            LinearLayout.LayoutParams(-2, dp(46)))
        label(shell, name, 28f, Color.WHITE, true, top = 20)
        label(shell, "Lives de cette catégorie", 15f, muted, top = 6, bottom = 16)
        val categoryResults = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        shell.addView(categoryResults, fullWidth())
        loadSearchResults(name, categoryResults)
        shell.addView(actionButton("Configurer la connexion", "Paramètres") { showTab("Paramètres") },
            LinearLayout.LayoutParams(-1, dp(50)).apply { topMargin = dp(16) })
    }

    private fun searchContent(parent: LinearLayout) {
        label(parent, "RECHERCHE", 12f, purple, true)
        label(parent, "Que veux-tu regarder ?", 25f, Color.WHITE, true, top = 6)
        val field = EditText(this).apply {
            hint = "Chaîne, jeu ou catégorie"
            setHintTextColor(muted)
            setTextColor(Color.WHITE)
            textSize = 16f
            setSingleLine(true)
            setText(searchQuery)
            setPadding(dp(15), 0, dp(15), 0)
            background = rounded(panel, dp(15), Color.rgb(90, 55, 120), dp(1))
            imeOptions = android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH
        }
        parent.addView(field, fullWidth().apply { height = dp(54); topMargin = dp(18); bottomMargin = dp(12) })
        parent.addView(actionButton("Rechercher dans NexaStream  ⌕", "Rechercher") {
            searchQuery = field.text.toString().trim()
            if (searchQuery.isEmpty()) toast("Saisis un nom de chaîne, un jeu ou une catégorie.")
            else {
                prefs.edit().putString("last_search", searchQuery).apply()
                showSearchResults(searchQuery)
            }
        }, fullWidth())
        sectionTitle(parent, "Suggestions", "Exemples de recherches")
        listOf("Minecraft", "Just Chatting", "Musique", "Esport").forEach { term ->
            actionCard(parent, "⌕", term, "Rechercher dans NexaStream") { searchQuery = term; showSearchResults(term) }
        }
    }

    private fun showSearchResults(query: String) {
        currentTab = "Résultats"
        root.removeAllViews()
        root.addView(CircuitBackground(this), FrameLayout.LayoutParams(-1, -1))
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(18), dp(12), dp(18), dp(12)) }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        shell.addView(actionButton("‹  Recherche", "Retour à la recherche") { showTab("Recherche") },
            LinearLayout.LayoutParams(-2, dp(46)))
        label(shell, "Résultats", 27f, Color.WHITE, true, top = 18)
        label(shell, "« $query »", 15f, muted, top = 5, bottom = 14)
        val results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        shell.addView(results, fullWidth())
        loadSearchResults(query, results)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(NotificationChannel("nexastream_live", "Lives suivis", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Alertes lorsqu’une chaîne suivie démarre un live"
            })
        }
    }

    private fun checkForFollowedLiveNotifications() {
        val token = prefs.getString("twitch_access_token", null) ?: return
        val userId = prefs.getString("twitch_user_id", null) ?: return
        if (configuredClientId().isBlank()) return
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1701)
            return
        }
        Thread {
            try {
                val streams = TwitchApi(configuredClientId(), token).followedStreams(userId)
                val currentIds = streams.map { it.id }.toSet()
                val previous = prefs.getStringSet("known_live_ids", emptySet()) ?: emptySet()
                val newlyLive = if (previous.isEmpty()) emptyList() else streams.filter { it.id !in previous }
                newlyLive.forEachIndexed { index, stream ->
                    val intent = android.content.Intent(this, MainActivity::class.java).apply {
                        flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra("open_channel", stream.userName)
                    }
                    val pending = PendingIntent.getActivity(this, stream.id.hashCode(), intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
                    val notification = if (Build.VERSION.SDK_INT >= 26) android.app.Notification.Builder(this, "nexastream_live") else android.app.Notification.Builder(this)
                    notification.setSmallIcon(android.R.drawable.ic_media_play)
                        .setContentTitle(stream.userName + " est en direct")
                        .setContentText(stream.title)
                        .setContentIntent(pending)
                        .setAutoCancel(true)
                    (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).notify(stream.id.hashCode(), notification.build())
                }
                prefs.edit().putStringSet("known_live_ids", currentIds).apply()
            } catch (_: Exception) {
                // Keep the notification page usable when Twitch is temporarily unreachable.
            }
        }.start()
    }

    private fun loadTopStreams(container: LinearLayout) {
        val token = prefs.getString("twitch_access_token", null)
        if (configuredClientId().isBlank() || token.isNullOrBlank()) {
            emptyState(container, "◉", "Connecte ton compte Twitch", "Les vrais lives apparaîtront ici après configuration du Client ID et connexion officielle.")
            return
        }
        emptyState(container, "◉", "Chargement des lives…", "Récupération des données officielles Twitch.")
        Thread {
            try {
                val streams = TwitchApi(configuredClientId(), token).topStreams()
                runOnUiThread {
                    container.removeAllViews()
                    if (streams.isEmpty()) emptyState(container, "◉", "Aucun live retourné", "Twitch n’a renvoyé aucun live pour cette requête.")
                    streams.forEach { stream ->
                        actionCard(container, "●", stream.userName, stream.title + "\n" + stream.gameName + " • " + stream.viewerCount + " spectateurs") {
                            openLive(stream.userName)
                        }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { container.removeAllViews(); emptyState(container, "!", "Impossible de charger les lives", e.message ?: "Erreur réseau Twitch.") }
            }
        }.start()
    }

    private fun loadFollowedStreams(container: LinearLayout) {
        val token = prefs.getString("twitch_access_token", null)
        val userId = prefs.getString("twitch_user_id", null)
        if (token.isNullOrBlank() || userId.isNullOrBlank() || configuredClientId().isBlank()) {
            emptyState(container, "♡", "Aucun compte connecté", "Connecte-toi via Twitch pour afficher toutes tes chaînes suivies, en direct ou hors ligne.")
            return
        }
        emptyState(container, "♡", "Chargement des suivis…", "Récupération des chaînes suivies depuis Twitch.")
        Thread {
            try {
                val channels = TwitchApi(configuredClientId(), token).followedChannels(userId)
                runOnUiThread {
                    container.removeAllViews()
                    if (channels.isEmpty()) emptyState(container, "♡", "Aucune chaîne suivie", "Twitch n’a retourné aucune chaîne suivie pour ce compte.")
                    channels.forEach { channel ->
                        val subtitle = if (channel.isLive) "EN DIRECT • " + channel.gameName + " • " + channel.title
                            else "Hors ligne" + if (channel.title.isNotBlank()) " • " + channel.title else ""
                        actionCard(container, if (channel.isLive) "●" else "○", channel.displayName, subtitle) { openLive(channel.name) }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { container.removeAllViews(); emptyState(container, "!", "Erreur des suivis", e.message ?: "Vérifie les autorisations Twitch.") }
            }
        }.start()
    }

    private fun loadFollowedChannels(container: LinearLayout) {
        val token = prefs.getString("twitch_access_token", null)
        val userId = prefs.getString("twitch_user_id", null)
        if (token.isNullOrBlank() || userId.isNullOrBlank() || configuredClientId().isBlank()) {
            emptyState(container, "♡", "Aucun compte connecté", "Configure le Client ID puis connecte-toi via Twitch pour voir les chaînes suivies, en direct ou hors ligne.")
            return
        }
        emptyState(container, "♡", "Chargement des chaînes suivies…", "Récupération des abonnements de ton compte Twitch.")
        Thread {
            try {
                val channels = TwitchApi(configuredClientId(), token).followedChannels(userId)
                runOnUiThread {
                    container.removeAllViews()
                    if (channels.isEmpty()) emptyState(container, "♡", "Aucune chaîne suivie", "Twitch n’a retourné aucune chaîne suivie pour ce compte.")
                    channels.forEach { channel ->
                        val subtitle = if (channel.isLive) "EN DIRECT • " + channel.gameName + " • " + channel.title
                            else "Hors ligne" + if (channel.title.isNotBlank()) " • " + channel.title else ""
                        actionCard(container, if (channel.isLive) "●" else "○", channel.displayName, subtitle) { openLive(channel.name) }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { container.removeAllViews(); emptyState(container, "!", "Erreur des suivis", e.message ?: "Vérifie les autorisations Twitch.") }
            }
        }.start()
    }

    private fun loadSearchResults(query: String, container: LinearLayout) {
        val token = prefs.getString("twitch_access_token", null)
        if (token.isNullOrBlank() || configuredClientId().isBlank()) {
            emptyState(container, "⌕", "Connexion Twitch nécessaire", "Connecte-toi dans Réglages pour rechercher des chaînes et lives réels.")
            container.addView(actionButton("Connecter Twitch", "Connexion Twitch") { startTwitchOAuth() }, fullWidth())
            return
        }
        emptyState(container, "⌕", "Recherche en cours…", "Interrogation de l’API officielle Twitch.")
        Thread {
            try {
                val api = TwitchApi(configuredClientId(), token)
                val streams = api.searchStreams(query)
                val channels = if (streams.isEmpty()) api.searchChannels(query) else emptyList()
                runOnUiThread {
                    container.removeAllViews()
                    if (streams.isEmpty() && channels.isEmpty()) emptyState(container, "⌕", "Aucun résultat", "Twitch n’a retourné aucun résultat pour « $query ».")
                    streams.forEach { stream ->
                        actionCard(container, "●", stream.userName, stream.title + "\n" + stream.gameName + " • " + stream.viewerCount + " spectateurs") { openLive(stream.userName) }
                    }
                    channels.forEach { channel ->
                        actionCard(container, if (channel.isLive) "●" else "○", channel.displayName, (if (channel.isLive) "EN DIRECT • " else "Hors ligne • ") + channel.title) { openLive(channel.name) }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { container.removeAllViews(); emptyState(container, "!", "Recherche impossible", e.message ?: "Erreur réseau Twitch.") }
            }
        }.start()
    }

    private fun openLive(channel: String) {
        chatWebView?.stopLoading()
        chatWebView?.destroy()
        chatWebView = null
        currentTab = "Lecteur"
        root.removeAllViews()
        root.addView(CircuitBackground(this), FrameLayout.LayoutParams(-1, -1))
        val shell = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(10), dp(8), dp(10), dp(8)) }
        root.addView(shell, FrameLayout.LayoutParams(-1, -1))
        shell.addView(actionButton("‹  Retour", "Retour") { showTab("Accueil") }, LinearLayout.LayoutParams(-2, dp(44)))
        label(shell, "LIVE • $channel", 20f, Color.WHITE, true, top = 10, bottom = 8)
        label(shell, "Lecteur Twitch officiel et chat intégré. Appuie sur Lecture si le démarrage automatique est refusé.", 12f, muted, bottom = 8)

        val browser = WebView(this)
        chatWebView = browser
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.loadsImagesAutomatically = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = false
        browser.settings.setSupportMultipleWindows(false)
        browser.settings.mediaPlaybackRequiresUserGesture = true
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
            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                val host = request?.url?.host?.lowercase().orEmpty()
                return host !in setOf("www.twitch.tv", "twitch.tv", "player.twitch.tv", "m.twitch.tv", "id.twitch.tv", "api.twitch.tv", "static-cdn.jtvnw.net", "gql.twitch.tv")
            }
        }
        shell.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        val safeChannel = channel.lowercase().filter { it.isLetterOrDigit() || it == '_' }
        val page = """
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=1">
            <style>
              *{box-sizing:border-box}body{margin:0;background:#0a0810;color:#fff;font-family:Arial,sans-serif}
              .player{width:100%;aspect-ratio:16/9;min-height:220px;background:#000}
              iframe{border:0;width:100%;height:100%;display:block}
              .chat{height:48vh;min-height:260px;border-top:1px solid #38264b}
              .caption{padding:8px 10px;color:#c9b7dc;font-size:12px}
            </style></head><body>
            <div class="player"><iframe title="Lecteur Twitch officiel" allow="autoplay; fullscreen; picture-in-picture" allowfullscreen
              src="https://player.twitch.tv/?channel=$safeChannel&parent=www.twitch.tv&parent=twitch.tv&autoplay=false&muted=false&layout=video"></iframe></div>
            <div class="caption">Vidéo officielle Twitch • $safeChannel</div>
            <div class="chat"><iframe title="Chat Twitch officiel" allow="clipboard-write" src="https://www.twitch.tv/popout/$safeChannel/chat?popout="></iframe></div>
            </body></html>
        """.trimIndent()
        browser.loadDataWithBaseURL("https://www.twitch.tv/", page, "text/html", "UTF-8", null)
    }

    private fun followingContent(parent: LinearLayout) {
        label(parent, "TES CHAÎNES", 12f, purple, true)
        label(parent, "Tes suivis, au même endroit.", 25f, Color.WHITE, true, top = 6)
        label(parent, "Chaînes suivies actuellement en direct sur ton compte Twitch.", 14f, muted, top = 8, bottom = 18)
        val followContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(followContainer, fullWidth())
        loadFollowedChannels(followContainer)
        actionCard(parent, "◎", "Connexion officielle Twitch", "Connecter ou reconnecter ton compte") { startTwitchOAuth() }
    }

    private fun isKnownAdOrTracker(url: String): Boolean {
        val host = try { URI(url).host?.lowercase() ?: return false } catch (_: Exception) { return false }
        val blockedHosts = listOf("doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "adsystem.com", "adservice.google.com", "amazon-adsystem.com",
            "scorecardresearch.com", "quantserve.com", "analytics.tiktok.com", "adroll.com",
            "criteo.com", "criteo.net", "taboola.com", "outbrain.com", "adsafeprotected.com",
            "serving-sys.com", "adform.net", "smartadserver.com", "rubiconproject.com")
        return blockedHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun notificationsContent(parent: LinearLayout) {
        label(parent, "CENTRE DE NOTIFICATIONS", 12f, purple, true)
        label(parent, "Ne rate plus un live.", 25f, Color.WHITE, true, top = 6)
        label(parent, "Les alertes seront affichées comme de vraies notifications Android, avec le nom et l’avatar de la chaîne.", 14f, muted, top = 8, bottom = 16)
        val liveContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(liveContainer, fullWidth())
        loadFollowedStreams(liveContainer)
        actionCard(parent, "⚙", "Connexion et autorisations", "Gérer le compte Twitch et les notifications Android") { showTab("Paramètres") }
        label(parent, "La vérification actuelle se fait lorsque NexaStream est ouvert. Des alertes fiables même lorsque l’application est fermée nécessiteront un service de fond adapté ou une infrastructure EventSub.", 12f, muted, top = 8)
    }

    private fun settingsContent(parent: LinearLayout) {
        label(parent, "PERSONNALISATION", 12f, purple, true)
        label(parent, "Paramètres", 25f, Color.WHITE, true, top = 6)
        val connectedName = prefs.getString("twitch_user_name", null)
        actionCard(parent, "◎", if (connectedName.isNullOrBlank()) "Connexion officielle Twitch" else "Connecté : $connectedName", if (connectedName.isNullOrBlank()) "Se connecter via la page officielle Twitch" else "Appuie pour reconnecter le compte") { startTwitchOAuth() }
        actionCard(parent, "🔑", "Client ID Twitch", if (configuredClientId().isBlank()) "Non configuré — appuie pour le définir sans recompiler" else "Configuré sur cet appareil — appuie pour modifier") { configureTwitchClientId() }
        if (!connectedName.isNullOrBlank()) actionCard(parent, "↪", "Déconnecter Twitch", "Supprimer le jeton local de cet appareil") {
            prefs.edit().remove("twitch_access_token").remove("twitch_user_id").remove("twitch_user_name").apply()
            toast("Compte Twitch déconnecté de NexaStream.")
            showTab("Paramètres")
        }
        label(parent, "Connexion officielle", 18f, Color.WHITE, true, top = 12)
        label(parent, "La connexion utilise le code temporaire officiel Twitch : ouvre la page d’activation, saisis le code affiché et laisse NexaStream ouvert pendant la validation. Aucun mot de passe Twitch n’est saisi dans NexaStream et aucune URL de redirection n’est nécessaire.", 13f, muted, top = 6, bottom = 14)
        actionCard(parent, "🛡", "Filtre de domaines", if (blockerEnabled) "Activé — filtre les requêtes ciblées par NexaStream" else "Désactivé") {
            blockerEnabled = !blockerEnabled
            prefs.edit().putBoolean("ad_blocker_enabled", blockerEnabled).apply()
            showTab("Paramètres")
        }
        label(parent, "Le filtre sera appliqué aux requêtes réseau prises en charge par l’application. Les pubs vidéo intégrées aux flux Twitch ne peuvent pas être garanties bloquées, notamment lorsqu’elles utilisent la même infrastructure que la vidéo. L’objectif est de filtrer les domaines publicitaires généraux sans prétendre remplacer totalement uBlock Origin.", 13f, muted, top = 6, bottom = 14)
        actionCard(parent, "📺", "Android TV", "Cartes sélectionnables à la télécommande, focus visible") {
            toast("Utilise les flèches et OK de ta télécommande pour naviguer.")
        }
        actionCard(parent, "▶", "Lecteur officiel Twitch", "La lecture utilise le lecteur intégré autorisé de Twitch, pas une URL de flux extraite.") {
            toast("Ouvre un live depuis Accueil, Découvrir ou Suivis pour tester le lecteur intégré.")
        }
        actionCard(parent, "ℹ", "Version", "NexaStream • interface native en développement") {
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

    private fun emptyState(parent: LinearLayout, symbol: String, title: String, description: String) {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(dp(20), dp(22), dp(20), dp(22))
            background = rounded(Color.argb(235, 22, 16, 32), dp(20), Color.rgb(72, 43, 94), dp(1))
        }
        label(card, symbol, 34f, purple, true)
        label(card, title, 18f, Color.WHITE, true, top = 10)
        label(card, description, 13f, muted, top = 7)
        parent.addView(card, fullWidth().apply { bottomMargin = dp(12) })
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
    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()

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
