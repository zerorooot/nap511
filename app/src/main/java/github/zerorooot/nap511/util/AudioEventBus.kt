package github.zerorooot.nap511.util

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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