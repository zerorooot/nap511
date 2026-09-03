package github.zerorooot.nap511.util

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface DialogEvent {

    /** OfflineTaskWorker 和 RecycleViewModel 中的文件刷新通知 */
    data class RefreshFileList(val cid: String) : DialogEvent
}

class DialogEventBus private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: DialogEventBus? = null

        fun getInstance(): DialogEventBus {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DialogEventBus().also { INSTANCE = it }
            }
        }
    }

    private val _events = MutableSharedFlow<DialogEvent>(
        replay = 0,
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )
    val events: SharedFlow<DialogEvent> = _events.asSharedFlow()

    suspend fun emit(event: DialogEvent) {
        _events.emit(event)
    }
}
