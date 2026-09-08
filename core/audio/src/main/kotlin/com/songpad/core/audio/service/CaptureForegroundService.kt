package com.songpad.core.audio.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat

/**
 * §7.5 `microphone`-typed FGS. Started ONLY from a visible screen (RECORD_AUDIO is while-in-use). It keeps
 * the process alive while the screen locks or the app backgrounds; it does not own the stream — the engine
 * does. Manifest entry lives in :core:audio's manifest and merges into :app.
 */
class CaptureForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) { ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE); stopSelf(); return START_NOT_STICKY }
        ensureChannel()
        ServiceCompat.startForeground(this, NOTIFICATION_ID, buildNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        return START_NOT_STICKY // §7.4: on process death nothing restarts capture; bytes are already on disk
    }

    private fun buildNotification(): Notification {
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setContentTitle("Recording")
            .setContentText("SongPad is capturing audio")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .apply { if (open != null) setContentIntent(open) }
            .build()
    }

    private fun ensureChannel() {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            // §11.3: the recording channel must be at least LOW and separate from nudges.
            nm.createNotificationChannel(NotificationChannel(CHANNEL_ID, "Recording", NotificationManager.IMPORTANCE_LOW).apply { setShowBadge(false) })
        }
    }

    companion object {
        const val CHANNEL_ID = "songpad_recording"
        const val NOTIFICATION_ID = 0x5047
        private const val ACTION_STOP = "com.songpad.core.audio.action.STOP_CAPTURE"
        private const val EXTRA_TAKE_ID = "take_id"

        fun start(context: Context, takeId: String) {
            context.startForegroundService(Intent(context, CaptureForegroundService::class.java).putExtra(EXTRA_TAKE_ID, takeId))
        }

        fun stop(context: Context) {
            context.startService(Intent(context, CaptureForegroundService::class.java).setAction(ACTION_STOP))
        }
    }
}
