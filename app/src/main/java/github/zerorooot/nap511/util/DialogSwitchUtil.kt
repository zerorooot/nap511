package github.zerorooot.nap511.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 打开对话框相关
 */
class DialogSwitchUtil {
    companion object {
        @Volatile
        private var INSTANCE: DialogSwitchUtil? = null
        fun getInstance(): DialogSwitchUtil {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: DialogSwitchUtil().also { INSTANCE = it }
            }
        }
    }

    /**
     * 创建选择种子文件对话框
     */
    var isOpenCreateSelectTorrentFileDialog by mutableStateOf(false)

    /**
     * 打开离线对话框
     */
    var isOpenOfflineDialog by mutableStateOf(false)

    /**
     * 垃圾站密码对话框
     */
    var isOpenRecyclePasswordDialog by mutableStateOf(false)
    /**
     * 新建文件夹
     */
    var isOpenCreateFolderDialog by mutableStateOf(false)

    /**
     * 重命名
     */
    var isOpenRenameFileDialog by mutableStateOf(false)

    /**
     *文件信息
     */
    var isOpenFileInfoDialog by mutableStateOf(false)

    /**
     *文件排序
     */
    var isOpenFileOrderDialog by mutableStateOf(false)

    /**
     *aria2
     */
    var isOpenAria2Dialog by mutableStateOf(false)


    /**
     *搜索
     */
    var isOpenSearchDialog by mutableStateOf(false)

    /**
     * 解压对话框
     */
    var isOpenUnzipDialog by mutableStateOf(false)

    /**
     *解压密码
     */
    var isOpenUnzipPasswordDialog by mutableStateOf(false)

    /**
     *小文本文件 [github.zerorooot.nap511.screen.TextBodyDialog]
     */
    var isOpenTextBodyDialog by mutableStateOf(false)

    /**
     *解压所有压缩包，提前输入密码，没有为空即可
     */
    var isOpenUnzipAllFileDialog by mutableStateOf(false)
}