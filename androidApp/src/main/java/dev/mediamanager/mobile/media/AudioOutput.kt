package dev.mediamanager.mobile.media

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class AudioRoute(val label: String) {
    SPEAKER("Phone speaker"),
    HEADPHONES("Headphones"),
    BLUETOOTH("Bluetooth"),
    USB("USB audio"),
    UNKNOWN("Unknown output"),
}

/** Which output the media stream is using right now, kept current by a device callback. */
class AudioOutputMonitor(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)
    private val _route = MutableStateFlow(current())
    val route: StateFlow<AudioRoute> = _route.asStateFlow()

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            _route.value = current()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            _route.value = current()
        }
    }

    fun start() {
        _route.value = current()
        runCatching { audioManager.registerAudioDeviceCallback(callback, null) }
    }

    fun stop() {
        runCatching { audioManager.unregisterAudioDeviceCallback(callback) }
    }

    fun current(): AudioRoute {
        val devices = runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrDefault(emptyList())
        val connected = devices.filter { it.isSink }
        return when {
            connected.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    it.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_BLE_SPEAKER
            } -> AudioRoute.BLUETOOTH

            connected.any {
                it.type == AudioDeviceInfo.TYPE_WIRED_HEADPHONES ||
                    it.type == AudioDeviceInfo.TYPE_WIRED_HEADSET
            } -> AudioRoute.HEADPHONES

            connected.any {
                it.type == AudioDeviceInfo.TYPE_USB_HEADSET ||
                    it.type == AudioDeviceInfo.TYPE_USB_DEVICE
            } -> AudioRoute.USB

            connected.any { it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER } -> AudioRoute.SPEAKER

            else -> AudioRoute.UNKNOWN
        }
    }
}

/** The phone's own media volume, which is what an in-app bar should move. */
class VolumeControl(context: Context) {
    private val audioManager = context.getSystemService(AudioManager::class.java)

    val max: Int get() = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    val current: Int get() = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)

    fun set(value: Int) {
        val clamped = value.coerceIn(0, max)
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, clamped, 0) }
    }
}
