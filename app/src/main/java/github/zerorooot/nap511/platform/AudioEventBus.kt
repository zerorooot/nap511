package github.zerorooot.nap511.platform

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 音频播放控制与前台 Service 状态事件总线
 */
object AudioEventBus {
    private val _events = MutableSharedFlow<AudioEvent>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    fun sendEvent(event: AudioEvent) {
        _events.tryEmit(event)
    }
}

sealed class AudioEvent {
    object SyncState : AudioEvent()
    object Stop : AudioEvent()
}
