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

    fun requestDeviceCode(): JSONObject {
        val connection = (URL("https://id.twitch.tv/oauth2/device").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
            "&scopes=" + URLEncoder.encode("user:read:follows user:read:subscriptions", "UTF-8")
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val json = JSONObject(stream.bufferedReader().use { it.readText() })
            if (code !in 200..299) throw IllegalStateException(json.optString("message", "Twitch device authorization error"))
            json
        } finally { connection.disconnect() }
    }

    fun pollDeviceToken(deviceCode: String): JSONObject {
        val connection = (URL("https://id.twitch.tv/oauth2/token").openConnection() as HttpURLConnection)
        connection.requestMethod = "POST"
        connection.connectTimeout = 12000
        connection.readTimeout = 12000
        connection.doOutput = true
        connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        val body = "client_id=" + URLEncoder.encode(clientId, "UTF-8") +
            "&device_code=" + URLEncoder.encode(deviceCode, "UTF-8") +
            "&grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:device_code", "UTF-8")
        return try {
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val json = JSONObject(stream.bufferedReader().use { it.readText() })
            if (code !in 200..299) {
                val error = json.optString("message", json.optString("error", "Twitch token error"))
                throw IllegalStateException(error)
            }
            json
        } finally { connection.disconnect() }
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

    fun followedChannels(userId: String): List<TwitchChannel> {
        val followed = request("channels/followed?user_id=" + URLEncoder.encode(userId, "UTF-8") + "&first=100").optJSONArray("data") ?: return emptyList()
        val rows = (0 until followed.length()).map { followed.getJSONObject(it) }
        if (rows.isEmpty()) return emptyList()
        val ids = rows.map { it.optString("broadcaster_id") }.filter { it.isNotBlank() }
        val liveStreams = runCatching {
            val data = request("streams?first=100&" + ids.joinToString("&") { "user_id=" + URLEncoder.encode(it, "UTF-8") }).optJSONArray("data")
            if (data == null) emptyMap() else (0 until data.length()).map { data.getJSONObject(it) }.associateBy { it.optString("user_id") }
        }.getOrDefault(emptyMap())
        val channelDetails = runCatching {
            val data = request("channels?" + ids.joinToString("&") { "broadcaster_id=" + URLEncoder.encode(it, "UTF-8") }).optJSONArray("data")
            if (data == null) emptyMap() else (0 until data.length()).map { data.getJSONObject(it) }.associateBy { it.optString("broadcaster_id") }
        }.getOrDefault(emptyMap())
        return rows.map { row ->
            val id = row.optString("broadcaster_id")
            val live = liveStreams[id]
            val details = channelDetails[id]
            TwitchChannel(
                id = id,
                name = row.optString("broadcaster_login"),
                displayName = row.optString("broadcaster_name", row.optString("broadcaster_login")),
                title = live?.optString("title") ?: details?.optString("title").orEmpty(),
                gameName = live?.optString("game_name") ?: details?.optString("game_name").orEmpty(),
                isLive = live != null,
                thumbnailUrl = live?.optString("thumbnail_url").orEmpty()
            )
        }
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
