package com.nexastream.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.os.Build
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

/**
 * Best-effort background poll. Android/WorkManager controls exact timing;
 * Twitch tokens from the implicit OAuth flow can expire and require login again.
 */
class FollowedLiveWorker(context: Context, params: WorkerParameters) : Worker(context, params) {
    override fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences("nexastream_settings", Context.MODE_PRIVATE)
        val token = prefs.getString("twitch_access_token", null) ?: return Result.success()
        val userId = prefs.getString("twitch_user_id", null) ?: return Result.success()
        val clientId = prefs.getString("twitch_client_id", BuildConfig.TWITCH_CLIENT_ID)?.trim().orEmpty()
        if (clientId.isBlank()) return Result.success()

        return try {
            val streams = TwitchApi(clientId, token).followedStreams(userId)
            val currentIds = streams.map { it.id }.toSet()
            val previous = prefs.getStringSet("known_live_ids", emptySet())?.toSet() ?: emptySet()
            if (previous.isNotEmpty()) {
                streams.filter { it.id !in previous }.forEach { stream ->
                    postNotification(stream.userName, stream.title, stream.id.hashCode())
                }
            }
            prefs.edit().putStringSet("known_live_ids", currentIds).apply()
            Result.success()
        } catch (_: Exception) {
            // Retry temporary network/API errors; an expired token may need user re-authentication.
            Result.retry()
        }
    }

    private fun postNotification(channel: String, title: String, notificationId: Int) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(
                "nexastream_live", "Lives suivis", NotificationManager.IMPORTANCE_DEFAULT
            ).apply { description = "Alertes lorsqu’une chaîne suivie démarre un live" })
        }
        val intent = android.content.Intent(applicationContext, MainActivity::class.java).apply {
            flags = android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pending = PendingIntent.getActivity(
            applicationContext, notificationId, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(applicationContext, "nexastream_live")
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(applicationContext)
        }
        builder.setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("$channel est en direct")
            .setContentText(title)
            .setContentIntent(pending)
            .setAutoCancel(true)
        manager.notify(notificationId, builder.build())
    }

    companion object {
        private const val UNIQUE_WORK_NAME = "nexastream_followed_live_poll"

        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<FollowedLiveWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request
            )
        }
    }
}
