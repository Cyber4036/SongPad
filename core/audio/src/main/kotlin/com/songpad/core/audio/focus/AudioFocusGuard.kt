package com.songpad.core.audio.focus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * §7.4 audio focus + route changes. Ducking requests are IGNORED (a recording is never gated by a
 * notification ping). `startBluetoothSco()` is deprecated and not used; route changes come from
 * `AudioDeviceCallback` and `ACTION_AUDIO_BECOMING_NOISY`.
 */
@Singleton
class AudioFocusGuard @Inject constructor(@ApplicationContext private val context: Context) {
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val handler = Handler(Looper.getMainLooper())
    private var request: AudioFocusRequest? = null
    private var noisyReceiver: BroadcastReceiver? = null
    private var deviceCallback: AudioDeviceCallback? = null

    /** @param onLoss called with `permanent = true` for AUDIOFOCUS_LOSS, false for TRANSIENT. */
    fun request(onLoss: (permanent: Boolean) -> Unit): Boolean {
        abandon()
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAcceptsDelayedFocusGain(false)
            .setWillPauseWhenDucked(false)
            .setOnAudioFocusChangeListener({ change ->
                when (change) {
                    AudioManager.AUDIOFOCUS_LOSS -> onLoss(true)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> onLoss(false)
                    AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> Unit // ignore (§7.4)
                    AudioManager.AUDIOFOCUS_GAIN -> Unit // stay paused; the user resumes explicitly
                }
            }, handler)
            .build()
        request = req
        return audioManager.requestAudioFocus(req) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    fun abandon() { request?.let { audioManager.abandonAudioFocusRequest(it) }; request = null }

    fun registerRouteListeners(onBecomingNoisy: () -> Unit, onBluetoothLost: () -> Unit) {
        unregisterRouteListeners()
        noisyReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, i: Intent?) { if (i?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) onBecomingNoisy() }
        }.also { context.registerReceiver(it, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY)) }
        deviceCallback = object : AudioDeviceCallback() {
            override fun onAudioDevicesRemoved(removed: Array<out AudioDeviceInfo>) {
                if (removed.any { it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLE_HEADSET }) onBluetoothLost()
            }
        }.also { audioManager.registerAudioDeviceCallback(it, handler) }
    }

    fun unregisterRouteListeners() {
        noisyReceiver?.let { runCatching { context.unregisterReceiver(it) } }; noisyReceiver = null
        deviceCallback?.let { audioManager.unregisterAudioDeviceCallback(it) }; deviceCallback = null
    }
}
