package github.zerorooot.nap511.repository

import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

sealed interface DialogEvent {

    // ==================== FileViewModel 对话框 ====================

    /** 新建文件夹 */
    data object OpenCreateFolder : DialogEvent

    /** 搜索 */
    data object OpenSearch : DialogEvent

    /** 重命名 */
    data object OpenRenameFile : DialogEvent

    /** 文件信息 */
    data object OpenFileInfo : DialogEvent

    /** 文件排序 */
    data object OpenFileOrder : DialogEvent

    /** Aria2 配置 */
    data object OpenAria2Dialog : DialogEvent

    /** 在线解压（选择解压路径） */
    data object OpenUnzipDialog : DialogEvent

    /** 解压密码输入 */
    data object OpenUnzipPasswordDialog : DialogEvent

    /** 小文本查看 */
    data object OpenTextBodyDialog : DialogEvent

    /** 解压所有压缩包（提前输入密码） */
    data object OpenUnzipAllFileDialog : DialogEvent

    /** 创建种子选择文件对话框 */
    data object OpenCreateSelectTorrentFileDialog : DialogEvent


    // ==================== OfflineFileViewModel 对话框 ====================

    /** 离线任务详情 */
    data object OpenOfflineDialog : DialogEvent


    // ==================== RecycleViewModel 对话框 ====================

    /** 回收站密码 */
    data object OpenRecyclePasswordDialog : DialogEvent
}

class DialogEventRepository private constructor() {

    companion object {
        @Volatile
        private var INSTANCE: DialogEventRepository? = null

        fun getInstance(): DialogEventRepository {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DialogEventRepository().also { INSTANCE = it }
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
