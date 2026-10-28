package com.nexastream.app

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TwitchStream(
    val id: String,
    val userId: String,
    val userName: String,
    val title: String,
    val gameName: String,
    val viewerCount: Int,
    val thumbnailUrl: String,
    val startedAt: String
)

data class TwitchChannel(
    val id: String,
    val name: String,
    val displayName: String,
    val title: String,
    val gameName: String,
    val isLive: Boolean,
    val thumbnailUrl: String
)

/** Minimal official Twitch Helix client. Calls must run off the main thread. */
class TwitchApi(private val clientId: String, private val accessToken: String) {
    private fun request(path: String): JSONObject {
        val connection = (URL("https://api.twitch.tv/helix/$path").openConnection() as HttpURLConnection)
        connection.requestMethod = "GET"
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.setRequestProperty("Client-Id", clientId)
        connection.setRequestProperty("Authorization", "Bearer $accessToken")
        return try {
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val body = stream.bufferedReader().use { it.readText() }
            if (code !in 200..299) throw IllegalStateException("Twitch API ($code): " + runCatching { JSONObject(body).optString("message") }.getOrDefault("requête refusée"))
            JSONObject(body)
        } finally {
            connection.disconnect()
        }
    }

    fun currentUser(): JSONObject {
        val rows = request("users").optJSONArray("data")
        if (rows == null || rows.length() == 0) throw IllegalStateException("Twitch n’a retourné aucun utilisateur.")
        return rows.getJSONObject(0)
    }

    fun topStreams(limit: Int = 20): List<TwitchStream> {
        val rows = request("streams?first=" + limit.coerceIn(1, 100)).optJSONArray("data") ?: return emptyList()
        return (0 until rows.length()).map { rows.getJSONObject(it).toStream() }
    }

    fun searchStreams(query: String, limit: Int = 20): List<TwitchStream> {
        val q = URLEncoder.encode(query, "UTF-8")
        val channels = request("search/channels?query=$q&first=" + limit.coerceIn(1, 100)).optJSONArray("data") ?: return emptyList()
        val liveIds = channels.let { arr -> (0 until arr.length()).map { arr.getJSONObject(it).optString("id") }.filter { it.isNotBlank() } }
        if (liveIds.isEmpty()) return emptyList()
        val streams = request("streams?first=100&user_id=" + liveIds.joinToString("&user_id=")).optJSONArray("data") ?: return emptyList()
        return (0 until streams.length()).map { streams.getJSONObject(it).toStream() }
    }

    fun followedStreams(userId: String): List<TwitchStream> {
        val followed = request("channels/followed?user_id=" + URLEncoder.encode(userId, "UTF-8") + "&first=100").optJSONArray("data") ?: return emptyList()
        val ids = (0 until followed.length()).map { followed.getJSONObject(it).optString("broadcaster_id") }.filter { it.isNotBlank() }
        if (ids.isEmpty()) return emptyList()
        val streams = request("streams?first=100&" + ids.joinToString("&") { "user_id=" + URLEncoder.encode(it, "UTF-8") }).optJSONArray("data") ?: return emptyList()
        return (0 until streams.length()).map { streams.getJSONObject(it).toStream() }
    }

    fun searchChannels(query: String, limit: Int = 20): List<TwitchChannel> {
        val q = URLEncoder.encode(query, "UTF-8")
        val rows = request("search/channels?query=$q&first=" + limit.coerceIn(1, 100)).optJSONArray("data") ?: return emptyList()
        return (0 until rows.length()).map { rows.getJSONObject(it).toChannel() }
    }

    private fun JSONObject.toStream() = TwitchStream(
        id = optString("id"), userId = optString("user_id"),
        userName = optString("user_name"), title = optString("title"),
        gameName = optString("game_name"), viewerCount = optInt("viewer_count"),
        thumbnailUrl = optString("thumbnail_url").replace("{width}", "640").replace("{height}", "360"),
        startedAt = optString("started_at")
    )

    private fun JSONObject.toChannel() = TwitchChannel(
        id = optString("id"), name = optString("broadcaster_login"),
        displayName = optString("display_name"), title = optString("title"),
        gameName = optString("game_name"), isLive = optBoolean("is_live"),
        thumbnailUrl = optString("thumbnail_url")
    )
}
