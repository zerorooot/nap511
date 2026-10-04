package github.zerorooot.nap511.util

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.SubtitleItem

/**
 * 音频播放控制器抽象接口
 * 解耦文件打开业务逻辑与具体的 AudioViewModel 实现
 */
interface AudioPlayerController {
    /** 播放指定音频文件并关联伴随字幕 */
    fun playAudio(fileBean: FileBean, localSubtitles: List<SubtitleItem>)

    /** 暂停音频播放 */
    fun pauseAudio()
}

/**
 * 业务对话框调度抽象接口
 * 解耦非查看器类型文件（压缩包、种子）的弹窗触发逻辑
 */
interface FileDialogController {
    /** 打开/提交种子解析任务对话框 */
    fun openTorrent(fileBean: FileBean)

    /** 打开压缩包内容预览/解压对话框 */
    fun openZip(fileBean: FileBean)
}

/**
 * 查看器独立状态仓
 * 集中管理文本阅读、网页展示、图片全屏预览的瞬时数据源以及小文本文件的内存字节缓存，
 * 实现 Compose 单向数据流，使文件打开操作彻底脱离对 FileViewModel 的直接依赖。
 */
class MediaViewerStateHolder {
    /** 文本阅读器当前展示的字节数据 */
    var textBodyByteArray by mutableStateOf<ByteArray?>(null)

    /** 网页查看器当前展示的字节数据 */
    var webBodyByteArray by mutableStateOf<ByteArray?>(null)

    /** 全屏图片浏览器的图片序列列表 */
    var photoFileBeanList by mutableStateOf<List<FileBean>>(emptyList())

    /** 全屏图片浏览器当前聚焦的图片索引 */
    var photoIndexOf by mutableIntStateOf(0)

    /** 当前图片序列所属的目录 ID */
    var photoCid by mutableStateOf("0")

    /**
     * 小文件（文本/HTML）下载结果的内存字节缓存，避免重复网络 I/O
     */
    private val textFileCache = hashMapOf<FileBean, ByteArray>()

    /** 设置文本阅读数据 */
    fun setTextContent(bytes: ByteArray) {
        textBodyByteArray = bytes
    }

    /** 设置网页预览数据 */
    fun setWebContent(bytes: ByteArray) {
        webBodyByteArray = bytes
    }

    /** 设置图片浏览器数据集与索引 */
    fun setPhotoList(list: List<FileBean>, index: Int, cid: String = "0") {
        photoFileBeanList = list
        photoIndexOf = if (index in list.indices) index else 0
        photoCid = cid
    }

    /** 从内存缓存获取小文件字节 */
    @Synchronized
    fun getCachedBytes(file: FileBean): ByteArray? {
        return textFileCache[file]
    }

    /** 将小文件字节放入内存缓存 */
    @Synchronized
    fun putCachedBytes(file: FileBean, bytes: ByteArray) {
        textFileCache[file] = bytes
    }

    /** 清空所有瞬态预览数据 */
    fun clear() {
        textBodyByteArray = null
        webBodyByteArray = null
        photoFileBeanList = emptyList()
        photoIndexOf = 0
        photoCid = "0"
    }
}
