package com.songpad.core.audio.piano

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.songpad.core.model.error.PianoError
import com.songpad.core.model.error.domainFailure
import com.songpad.core.model.piano.PianoSamplerEngine
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Loads the vendored piano samples (assets/piano/, see scripts/vendor_piano_samples.sh) and drives
 * the native Oboe sampler through [NativePiano]. §8.1: loading starts at screen-open, not at the
 * Piano-mode toggle, so [loadAsync] should be called once when the capture screen appears.
 *
 * ADR-0035 (amends §8.1): while armed, the output route is watched. On headphones there is no acoustic
 * path from speaker to mic, so the sampler is mixed digitally into the capture stream; on the speaker it
 * is not, because the mic already hears it and mixing as well would double every note.
 */
@Singleton
class PianoSamplerEngineImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : PianoSamplerEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val native = NativePiano()

    private val _loaded = MutableStateFlow(false)
    override val loaded: StateFlow<Boolean> = _loaded.asStateFlow()

    private var loadStarted = false

    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var routeWatching = false
    private val routeCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = updateCaptureMix()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = updateCaptureMix()
    }

    override fun loadAsync() {
        if (loadStarted) return
        loadStarted = true
        scope.launch {
            runCatching { loadRegionsFromAssets() }
                .onSuccess { _loaded.value = true }
            // A failed load leaves `loaded` false forever; noteOn/noteOff stay harmless no-ops
            // (findRegionForNote has nothing loaded to match), so Piano mode is silently inert
            // rather than crashing the capture screen over a missing/corrupt asset pack.
        }
    }

    private fun loadRegionsFromAssets() {
        val assets = context.assets
        val manifest = JSONObject(assets.open(MANIFEST_PATH).bufferedReader().use { it.readText() })
        val regions = manifest.getJSONArray("regions")
        for (i in 0 until regions.length()) {
            val region = regions.getJSONObject(i)
            val fileName = region.getString("file")
            val pcmBytes = assets.open("$ASSETS_DIR/$fileName").use { it.readBytes() }
            val pcm = ShortArray(pcmBytes.size / 2)
            ByteBuffer.wrap(pcmBytes).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(pcm)
            native.loadRegion(
                lokey = region.getInt("lokey"),
                hikey = region.getInt("hikey"),
                rootNote = region.getInt("rootNote"),
                looped = region.getBoolean("looped"),
                loopStartFrame = region.getLong("loopStartFrame"),
                loopEndFrame = region.getLong("loopEndFrame"),
                pcm = pcm,
            )
        }
    }

    override fun arm(): Result<Unit> {
        val openResult = native.open()
        if (openResult != 0) return domainFailure(PianoError.StreamOpenFailed("open result $openResult"))
        val startResult = native.start()
        if (startResult != 0) return domainFailure(PianoError.StreamOpenFailed("start result $startResult"))
        startRouteWatch()
        return Result.success(Unit)
    }

    override fun disarm() {
        stopRouteWatch()
        native.allNotesOff()
        native.stop()
    }

    private fun startRouteWatch() {
        if (routeWatching) return
        routeWatching = true
        // Registering fires onAudioDevicesAdded with the current devices, which sets the initial mix state.
        audioManager.registerAudioDeviceCallback(routeCallback, mainHandler)
        updateCaptureMix()
    }

    private fun stopRouteWatch() {
        if (!routeWatching) return
        routeWatching = false
        audioManager.unregisterAudioDeviceCallback(routeCallback)
        native.setCaptureMix(false)
    }

    private fun updateCaptureMix() {
        if (!routeWatching) return
        native.setCaptureMix(audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).any { it.type in HEADPHONE_TYPES })
    }

    override fun noteOn(note: Int, velocity: Int) = native.noteOn(note, velocity)
    override fun noteOff(note: Int) = native.noteOff(note)

    override fun release() {
        stopRouteWatch()
        native.close()
    }

    companion object {
        private const val ASSETS_DIR = "piano"
        private const val MANIFEST_PATH = "$ASSETS_DIR/manifest.json"

        /** Output routes with no acoustic path back to the built-in mic. */
        private val HEADPHONE_TYPES = setOf(
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
            AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_HEARING_AID,
        )
    }
}
