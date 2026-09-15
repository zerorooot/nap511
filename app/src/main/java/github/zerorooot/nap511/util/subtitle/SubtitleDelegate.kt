package github.zerorooot.nap511.util.subtitle

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.SubtitleItem
import github.zerorooot.nap511.bean.SubtitleStyleBean
import github.zerorooot.nap511.bean.SubtitleUiState
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

/**
 * 核心字幕管理代理类
 *
 * 托管 AudioViewModel 与 VideoViewModel 中的字幕与歌词业务逻辑，包括：
 * - 检索/搜索字幕（迅雷 API + 115 同目录字幕）
 * - 下载并解析/准备字幕文件
 * - 调整时间偏移量 (ms)
 * - 清除/移除字幕
 * - 上传字幕文件至 115 网盘
 * - 根据实时播放时刻定位字幕/歌词行
 */
class SubtitleDelegate(
    private val subtitleRepository: SubtitleRepository = SubtitleRepository.getInstance(),
    private val onStateChanged: ((SubtitleUiState) -> Unit)? = null
) {
    private val _uiState = MutableStateFlow(SubtitleUiState())
    val uiState: StateFlow<SubtitleUiState> = _uiState.asStateFlow()

    val state: SubtitleUiState get() = _uiState.value

    private fun updateState(update: SubtitleUiState.() -> SubtitleUiState) {
        _uiState.update(update)
        onStateChanged?.invoke(_uiState.value)
    }

    /**
     * 搜集/检索字幕（结合迅雷 API 与 115 同目录字幕）
     */
    fun loadSubtitles(
        scope: CoroutineScope,
        mediaName: String,
        searchKeyword: String = "",
        localSubtitles: List<SubtitleItem> = state.currentLocalSubtitles,
        mediaDurationMs: Long = 0L
    ) {
        updateState { copy(currentLocalSubtitles = localSubtitles) }
        val keyword = searchKeyword.ifBlank {
            if (mediaName.contains(".")) mediaName.substringBeforeLast(".") else mediaName
        }
        if (keyword.isBlank()) return

        updateState { copy(isSearchLoading = true, isLoading = true, defaultSearchKeyword = keyword) }
        scope.launch {
            runCatching {
                val result = subtitleRepository.getSubtitles(
                    searchKeyword = keyword,
                    oneOneFiveSubtitles = localSubtitles,
                    videoDurationMs = if (mediaDurationMs > 0) mediaDurationMs else 0L
                )
                updateState {
                    copy(
                        subtitles = result,
                        searchedSubtitle = true,
                        isSearchLoading = false,
                        isLoading = false
                    )
                }
            }.onFailure { e ->
                XLog.e("SubtitleDelegate: 检索字幕失败", e)
                updateState { copy(isSearchLoading = false, isLoading = false) }
            }
        }
    }

    /**
     * 选择并下载准备字幕
     *
     * @param scope CoroutineScope
     * @param cacheDirFile 缓存目录
     * @param item 选中的字幕数据项
     * @param currentPositionMs 当前播放时刻（毫秒），用于下载解析后立即重新计算歌词位置
     * @param onSuccess 成功回调，传递解析好的本地 SRT 文件
     */
    fun selectSubtitle(
        scope: CoroutineScope,
        cacheDirFile: File,
        item: SubtitleItem,
        currentPositionMs: Long? = null,
        onSuccess: ((File?) -> Unit)? = null
    ) {
        updateState { copy(isLoading = true) }
        scope.launch {
            val srtFile = subtitleRepository.downloadAndPrepareSubtitle(cacheDirFile, item)
            if (srtFile != null) {
                val parsedEntries = SrtParser.parse(srtFile)
                updateState {
                    copy(
                        selectedSubtitle = item,
                        entries = parsedEntries,
                        isLoading = false
                    )
                }
                if (currentPositionMs != null) {
                    updateSubtitleForPosition(currentPositionMs)
                }
                onSuccess?.invoke(srtFile)
            } else {
                updateState { copy(isLoading = false) }
                App.instance.toast("加载字幕失败: ${item.simpleName}")
                onSuccess?.invoke(null)
            }
        }
    }

    /**
     * 移除/清除当前绑定的字幕
     */
    fun removeSubtitle() {
        updateState {
            copy(
                selectedSubtitle = null,
                entries = emptyList(),
                currentText = "",
                currentIndex = -1
            )
        }
    }

    /**
     * 设置时间偏移量 (ms)
     */
    fun setSubtitleOffset(offsetMs: Long, currentPositionMs: Long? = null) {
        updateState { copy(offsetMs = offsetMs) }
        if (currentPositionMs != null) {
            updateSubtitleForPosition(currentPositionMs)
        }
    }

    /**
     * 微调时间偏移量 (ms)
     */
    fun addSubtitleOffset(deltaMs: Long, currentPositionMs: Long? = null) {
        val newOffset = state.offsetMs + deltaMs
        updateState { copy(offsetMs = newOffset) }
        if (currentPositionMs != null) {
            updateSubtitleForPosition(currentPositionMs)
        }
    }

    /**
     * 更新字幕外观样式 (适用于视频播放器)
     */
    fun updateSubtitleStyle(styleBean: SubtitleStyleBean) {
        updateState { copy(subtitleStyle = styleBean) }
    }

    /**
     * 上传字幕文件至 115 目录
     */
    fun uploadSubtitleTo115(
        scope: CoroutineScope,
        cacheDirFile: File,
        item: SubtitleItem,
        targetCid: String,
        onSuccess: (() -> Unit)? = null
    ) {
        if (targetCid.isBlank() || targetCid == "0") {
            App.instance.toast("无法获取当前媒体所在目录 ID")
            return
        }
        scope.launch {
            App.instance.toast("正在将字幕上传至 115 网盘...")
            runCatching {
                subtitleRepository.uploadSubtitleTo115(cacheDirFile, item, targetCid)
            }.onSuccess { result ->
                if (result.state) {
                    App.instance.toast("字幕上传成功！已保存到 115 当前目录")
                    onSuccess?.invoke()
                } else {
                    App.instance.toast("字幕上传失败: ${result.message}")
                }
            }.onFailure { e ->
                XLog.e("SubtitleDelegate: 上传字幕发生异常: ${item.name}", e)
                App.instance.toast("上传字幕发生异常: ${e.localizedMessage}")
            }
        }
    }

    /**
     * 根据当前播放时刻与偏移量，定位当前显示的字幕文本与行号
     */
    fun updateSubtitleForPosition(currentMs: Long) {
        val entries = state.entries
        if (entries.isEmpty()) {
            if (state.currentText.isNotEmpty() || state.currentIndex != -1) {
                updateState { copy(currentText = "", currentIndex = -1) }
            }
            return
        }

        val adjustedMs = currentMs + state.offsetMs

        val index = entries.indexOfLast { entry ->
            adjustedMs >= entry.startMs
        }

        if (index != -1) {
            val entry = entries[index]
            updateState { copy(currentText = entry.text, currentIndex = index) }
        } else {
            val text = entries.firstOrNull()?.text ?: ""
            updateState { copy(currentText = text, currentIndex = 0) }
        }
    }
}