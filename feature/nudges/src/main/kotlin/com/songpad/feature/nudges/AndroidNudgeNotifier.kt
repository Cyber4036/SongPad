package com.songpad.feature.nudges

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.songpad.core.model.nudge.Nudge
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §11.3 delivery: channel `songpad_nudges`, DEFAULT importance, separate from `songpad_recording`. Tapping
 * deep-links via `songpad://song/{id}` or `songpad://take/{id}`; :app resolves the URI and falls back to the
 * library if the target is gone. Returns false when POST_NOTIFICATIONS is missing or the channel is muted —
 * the history row was already written, so nudges degrade to in-app history only.
 */
@Singleton
class AndroidNudgeNotifier @Inject constructor(@ApplicationContext private val context: Context) : NudgeNotifier {

    override fun notify(nudge: Nudge): Boolean {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return false
        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) return false
        ensureChannel()

        val launch = context.packageManager.getLaunchIntentForPackage(context.packageName) ?: return false
        launch.action = Intent.ACTION_VIEW
        launch.data = deepLink(nudge)
        launch.putExtra(EXTRA_NUDGE_ID, nudge.id)
        launch.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        val pending = PendingIntent.getActivity(context, nudge.id.hashCode(), launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(nudge.title)
            .setContentText(nudge.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(nudge.body))
            .setContentIntent(pending)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        return runCatching { manager.notify(nudge.id.hashCode(), notification); true }.getOrDefault(false)
    }

    private fun ensureChannel() {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Gentle nudges", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Occasional reminders about ideas you started. Never more than one a day."
                },
            )
        }
    }

    companion object {
        const val CHANNEL_ID = "songpad_nudges"
        const val EXTRA_NUDGE_ID = "nudge_id"
        const val SCHEME = "songpad"

        fun deepLink(nudge: Nudge): Uri = when {
            nudge.targetSongId != null -> Uri.parse("$SCHEME://song/${nudge.targetSongId}")
            nudge.targetTakeId != null -> Uri.parse("$SCHEME://take/${nudge.targetTakeId}")
            else -> Uri.parse("$SCHEME://library")
        }
    }
}
