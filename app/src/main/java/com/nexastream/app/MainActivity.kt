package com.nexastream.app

import android.app.Activity
import android.app.AlertDialog
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
        handleOAuthCallback(intent)
        showTab("Accueil")
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleOAuthCallback(intent)
        showTab("Paramètres")
    }

    private fun handleOAuthCallback(intent: android.content.Intent?) {
        val fragment = intent?.data?.fragment ?: return
        val values = fragment.split("&").mapNotNull { part ->
            val bits = part.split("=", limit = 2)
            if (bits.size == 2) bits[bits[0]] = bits[1] else null
        }.toMap()
        val token = values["access_token"]
        if (!token.isNullOrBlank()) {
            prefs.edit().putString("twitch_access_token", token).apply()
            toast("Connexion Twitch reçue. Vérification du compte…")
            Thread {
                try {
                    val api = TwitchApi(BuildConfig.TWITCH_CLIENT_ID, token)
                    val user = api.currentUser()
                    prefs.edit().putString("twitch_user_id", user.optString("id"))
                        .putString("twitch_user_name", user.optString("display_name")).apply()
                    runOnUiThread { toast("Connecté à Twitch : " + user.optString("display_name")); showTab("Accueil") }
                } catch (e: Exception) {
                    prefs.edit().remove("twitch_access_token").apply()
                    runOnUiThread { toast("Connexion refusée : " + (e.message ?: "erreur Twitch")) }
                }
            }.start()
        } else if (!values["error"].isNullOrBlank()) {
            toast("Connexion Twitch annulée ou refusée : " + values["error"])
        }
    }

    private fun startTwitchOAuth() {
        val clientId = BuildConfig.TWITCH_CLIENT_ID
        if (clientId.isBlank()) {
            AlertDialog.Builder(this).setTitle("Connexion Twitch à configurer")
                .setMessage("Le Client ID de l’application Twitch n’est pas encore défini. Il faut enregistrer NexaStream dans le portail développeur Twitch avec l’URI de redirection exacte : nexastream://auth. Ensuite, ajouter TWITCH_CLIENT_ID dans la configuration Gradle de la compilation. Aucun mot de passe Twitch ne doit être saisi dans NexaStream.")
                .setPositiveButton("Compris", null).show()
            return
        }
        val redirect = URLEncoder.encode("nexastream://auth", "UTF-8")
        val scopes = URLEncoder.encode("user:read:follows user:read:subscriptions chat:read chat:edit", "UTF-8")
        val url = "https://id.twitch.tv/oauth2/authorize?client_id=" + URLEncoder.encode(clientId, "UTF-8") +
            "&redirect_uri=$redirect&response_type=token&scope=$scopes"
        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, Uri.parse(url)))
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
        emptyState(shell, "◈", "Aucun flux chargé pour l’instant", "La page est native et prête à recevoir les résultats Helix de la catégorie « $name ». L’API Twitch n’est pas encore autorisée dans cette version.")
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

    private fun loadTopStreams(container: LinearLayout) {
        val token = prefs.getString("twitch_access_token", null)
        if (BuildConfig.TWITCH_CLIENT_ID.isBlank() || token.isNullOrBlank()) {
            emptyState(container, "◉", "Connecte ton compte Twitch", "Les vrais lives apparaîtront ici après configuration du Client ID et connexion officielle.")
            return
        }
        emptyState(container, "◉", "Chargement des lives…", "Récupération des données officielles Twitch.")
        Thread {
            try {
                val streams = TwitchApi(BuildConfig.TWITCH_CLIENT_ID, token).topStreams()
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
        if (token.isNullOrBlank() || userId.isNullOrBlank() || BuildConfig.TWITCH_CLIENT_ID.isBlank()) {
            emptyState(container, "♡", "Aucun compte connecté", "Connecte-toi via Twitch pour afficher les chaînes suivies et leurs lives réels.")
            return
        }
        emptyState(container, "♡", "Chargement des suivis…", "Récupération des chaînes suivies qui sont en direct.")
        Thread {
            try {
                val streams = TwitchApi(BuildConfig.TWITCH_CLIENT_ID, token).followedStreams(userId)
                runOnUiThread {
                    container.removeAllViews()
                    if (streams.isEmpty()) emptyState(container, "♡", "Aucune chaîne suivie en direct", "Tes suivis sont connectés, mais aucun live n’a été retourné.")
                    streams.forEach { stream ->
                        actionCard(container, "●", stream.userName, stream.title + "\n" + stream.gameName + " • " + stream.viewerCount + " spectateurs") { openLive(stream.userName) }
                    }
                }
            } catch (e: Exception) {
                runOnUiThread { container.removeAllViews(); emptyState(container, "!", "Erreur des suivis", e.message ?: "Vérifie les autorisations Twitch.") }
            }
        }.start()
    }

    private fun loadSearchResults(query: String, container: LinearLayout) {
        val token = prefs.getString("twitch_access_token", null)
        if (token.isNullOrBlank() || BuildConfig.TWITCH_CLIENT_ID.isBlank()) {
            emptyState(container, "⌕", "Connexion Twitch nécessaire", "Connecte-toi dans Réglages pour rechercher des chaînes et lives réels.")
            container.addView(actionButton("Connecter Twitch", "Connexion Twitch") { startTwitchOAuth() }, fullWidth())
            return
        }
        emptyState(container, "⌕", "Recherche en cours…", "Interrogation de l’API officielle Twitch.")
        Thread {
            try {
                val api = TwitchApi(BuildConfig.TWITCH_CLIENT_ID, token)
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
        shell.addView(actionButton("‹  Retour", "Retour à l’accueil") { showTab("Accueil") }, LinearLayout.LayoutParams(-2, dp(44)))
        label(shell, "LIVE • $channel", 20f, Color.WHITE, true, top = 10, bottom = 8)
        label(shell, "Le lecteur vidéo dépend des options de lecture autorisées par Twitch. Le chat officiel est intégré ci-dessous.", 12f, muted, bottom = 8)
        val browser = WebView(this)
        chatWebView = browser
        browser.settings.javaScriptEnabled = true
        browser.settings.domStorageEnabled = true
        browser.settings.loadsImagesAutomatically = true
        browser.settings.javaScriptCanOpenWindowsAutomatically = true
        browser.settings.setSupportMultipleWindows(false)
        browser.settings.mediaPlaybackRequiresUserGesture = false
        CookieManager.getInstance().setAcceptCookie(true)
        CookieManager.getInstance().setAcceptThirdPartyCookies(browser, true)
        browser.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
                val url = request?.url?.toString() ?: return null
                if (blockerEnabled && isKnownAdOrTracker(url)) return WebResourceResponse("text/plain", "utf-8", 204, "Blocked by NexaStream", emptyMap(), ByteArrayInputStream(ByteArray(0)))
                return super.shouldInterceptRequest(view, request)
            }
        }
        shell.addView(browser, LinearLayout.LayoutParams(-1, 0, 1f))
        browser.loadUrl("https://www.twitch.tv/popout/" + Uri.encode(channel) + "/chat?popout=")
    }

    private fun followingContent(parent: LinearLayout) {
        label(parent, "TES CHAÎNES", 12f, purple, true)
        label(parent, "Tes suivis, au même endroit.", 25f, Color.WHITE, true, top = 6)
        label(parent, "Chaînes suivies actuellement en direct sur ton compte Twitch.", 14f, muted, top = 8, bottom = 18)
        val followContainer = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        parent.addView(followContainer, fullWidth())
        loadFollowedStreams(followContainer)
        actionCard(parent, "◎", "Connexion officielle Twitch", "Connecter ou reconnecter ton compte") { startTwitchOAuth() }
    }

    private fun isKnownAdOrTracker(url: String): Boolean {
        val host = try { URI(url).host?.lowercase() ?: return false } catch (_: Exception) { return false }
        val blockedHosts = listOf("doubleclick.net", "googlesyndication.com", "googleadservices.com",
            "adnxs.com", "adsystem.com", "adservice.google.com", "amazon-adsystem.com",
            "scorecardresearch.com", "quantserve.com", "analytics.tiktok.com")
        return blockedHosts.any { host == it || host.endsWith(".$it") }
    }

    private fun notificationsContent(parent: LinearLayout) {
        label(parent, "CENTRE DE NOTIFICATIONS", 12f, purple, true)
        label(parent, "Ne rate plus un live.", 25f, Color.WHITE, true, top = 6)
        label(parent, "Les alertes seront affichées comme de vraies notifications Android, avec le nom et l’avatar de la chaîne.", 14f, muted, top = 8, bottom = 16)
        emptyState(parent, "♧", "Notifications en préparation", "Les notifications réelles nécessitent l’autorisation du compte Twitch et un mécanisme fiable pour détecter les changements de statut des chaînes suivies. Aucune fausse alerte n’est créée.")
        actionCard(parent, "⚙", "Connexion et autorisations", "Consulter la configuration Twitch") { showTab("Paramètres") }
    }

    private fun settingsContent(parent: LinearLayout) {
        label(parent, "PERSONNALISATION", 12f, purple, true)
        label(parent, "Paramètres", 25f, Color.WHITE, true, top = 6)
        val connectedName = prefs.getString("twitch_user_name", null)
        actionCard(parent, "◎", if (connectedName.isNullOrBlank()) "Connexion officielle Twitch" else "Connecté : $connectedName", if (connectedName.isNullOrBlank()) "Se connecter via la page officielle Twitch" else "Appuie pour reconnecter le compte") { startTwitchOAuth() }
        if (!connectedName.isNullOrBlank()) actionCard(parent, "↪", "Déconnecter Twitch", "Supprimer le jeton local de cet appareil") {
            prefs.edit().remove("twitch_access_token").remove("twitch_user_id").remove("twitch_user_name").apply()
            toast("Compte Twitch déconnecté de NexaStream.")
            showTab("Paramètres")
        }
        label(parent, "Connexion officielle", 18f, Color.WHITE, true, top = 12)
        label(parent, "La connexion sera faite par Twitch OAuth, sans demander ton mot de passe à NexaStream. Pour activer cette étape, il faut enregistrer l’application dans le portail développeur Twitch et définir son Client ID et son URI de redirection. Je ne peux pas inventer ces identifiants.", 13f, muted, top = 6, bottom = 14)
        actionCard(parent, "🛡", "Filtre de domaines", if (blockerEnabled) "Activé — filtre les requêtes ciblées par NexaStream" else "Désactivé") {
            blockerEnabled = !blockerEnabled
            prefs.edit().putBoolean("ad_blocker_enabled", blockerEnabled).apply()
            showTab("Paramètres")
        }
        label(parent, "Le filtre sera appliqué aux requêtes réseau prises en charge par l’application. Les pubs vidéo intégrées aux flux Twitch ne peuvent pas être garanties bloquées, notamment lorsqu’elles utilisent la même infrastructure que la vidéo. L’objectif est de filtrer les domaines publicitaires généraux sans prétendre remplacer totalement uBlock Origin.", 13f, muted, top = 6, bottom = 14)
        actionCard(parent, "📺", "Android TV", "Cartes sélectionnables à la télécommande, focus visible") {
            toast("Utilise les flèches et OK de ta télécommande pour naviguer.")
        }
        actionCard(parent, "▶", "Lecteur natif", "Le lecteur live sera intégré ici après accès officiel aux flux autorisés") {
            toast("Le lecteur natif sera ajouté avec l’intégration du flux Twitch autorisé.")
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
