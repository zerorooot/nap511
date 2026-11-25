package github.zerorooot.nap511.ui.viewmodel.store

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.ui.model.FileContentUiState
import github.zerorooot.nap511.ui.model.FileDialogUiState
import github.zerorooot.nap511.ui.model.FileListSource
import github.zerorooot.nap511.data.model.FileBean
import github.zerorooot.nap511.data.model.uniqueId
import github.zerorooot.nap511.data.model.OrderBean
import github.zerorooot.nap511.data.local.cache.FileCacheManager
import github.zerorooot.nap511.util.formatFileBeanList
import github.zerorooot.nap511.platform.AppStringProvider
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * 统一文件 UI 状态持有者（SSOT 唯一真相源）
 * 彻底隔离文件列表浏览状态流与弹窗独立交互状态流，
 * 封装线程安全的原语 Reducer 更新方法与便捷只读访问器。
 */
class FileUiStateHolder(stringProvider: AppStringProvider) {
    /** 1. 文件列表核心浏览与模式状态流 */
    private val _contentState = MutableStateFlow(
        createInitialContentState(stringProvider)
    )
    val contentState: StateFlow<FileContentUiState> = _contentState.asStateFlow()

    /** 2. 弹窗交互独立状态流（与列表展示彻底物理隔离） */
    private val _dialogState = MutableStateFlow(FileDialogUiState())
    val dialogState: StateFlow<FileDialogUiState> = _dialogState.asStateFlow()

    val currentContentState: FileContentUiState
        get() = _contentState.value

    val currentDialogState: FileDialogUiState
        get() = _dialogState.value

    /**
     * 唯一写列表入口：本地序与列表同生共死。
     *
     * 1. reducer 显式写了 localOrder（引用变化）→ 尊重，这是「应用本地序」的唯一途径。
     *    `data class copy()` 对未传字段保持原引用，故「写了」等价于「引用变了」，
     *    写入意图必须与列表变更写在同一 reducer 内，无法从外部隔空置位。
     * 2. 当前无本地序 → 无需判定。
     * 3. 同一个 source（同一分页流）且列表实例未换 / uniqueId 顺序未变
     *    → 就地变换，保留本地序。
     * 4. 其余情况（换了 source，或顺序变了）→ 本地序失效。
     *
     * 第 3 步必须同时校验 source：两个内容相同的目录会得到相同的 uniqueId 序列，
     * 只比顺序会漏清；反过来只比 source 会漏掉「同目录下列表被整体替换」。
     *
     * 前提：localOrder 非 null 时 fileBeanList 必然非空（见 FileContentUiState.localOrder）。
     */
    fun updateContentState(reducer: FileContentUiState.() -> FileContentUiState) {
        _contentState.update { old ->
            val new = old.reducer()
            when {
                new.localOrder !== old.localOrder -> new
                old.localOrder == null -> new
                new.source == old.source && (new.fileBeanList === old.fileBeanList ||
                        sameOrder(old.fileBeanList, new.fileBeanList)) -> new
                else -> {
                    // 只在真正清掉本地序时打日志：这是排错时最需要的一条线索，
                    // 正常就地变换不打，避免 46 处调用点把日志刷满。
                    XLog.d(
                        "FileUiStateHolder: 本地序失效并清空 localOrder=${old.localOrder}，" +
                                "sourceChanged=${new.source != old.source}，" +
                                "oldSize=${old.fileBeanList.size} newSize=${new.fileBeanList.size}，" +
                                "paginationKey=${old.source.paginationKey}"
                    )
                    new.copy(localOrder = null)
                }
            }
        }
    }

    /** 两个列表的 uniqueId 序列是否完全一致（只判顺序，不判内容）。 */
    private fun sameOrder(a: List<FileBean>, b: List<FileBean>): Boolean =
        a.size == b.size && a.indices.all { a[it].uniqueId == b[it].uniqueId }

    /** 弹窗状态更新减速器助手 */
    fun updateDialogState(reducer: FileDialogUiState.() -> FileDialogUiState) {
        _dialogState.update(reducer)
    }

    /** 设置下拉刷新状态 */
    fun setRefreshing(isRefreshing: Boolean) {
        _contentState.update { it.copy(isRefreshing = isRefreshing) }
    }

    /** 关闭当前弹窗并重置弹窗状态 */
    fun closeDialog() {
        _dialogState.update { FileDialogUiState() }
    }

    companion object {
        /**
         * 创建初始 UI 状态：
         * 【冷启动性能优化】：若 FileCacheManager 已经在预加载阶段将根目录 "0" 读入内存缓存，
         * 则直接首帧装填，避免启动后因异步读取产生空白列表闪烁及二次全量重组。
         */
        fun createInitialContentState(stringProvider: AppStringProvider): FileContentUiState {
            val folderSource = FileListSource.Folder("0", stringProvider)
            val cached = runCatching { FileCacheManager.getDate("0") }.getOrNull()
            if (cached != null) {
                XLog.d("FileUiStateHolder: Hit root memory cache on init, items=${cached.fileBeanList.size}")
                val formattedList = formatFileBeanList(cached.fileBeanList)
                return FileContentUiState(
                    source = folderSource,
                    fileBeanList = formattedList,
                    currentCid = "0",
                    totalCount = cached.count,
                    orderBean = OrderBean.from(cached),
                    pathList = cached.path,
                    fcMix = cached.fcMix, // 从根目录缓存读入 fcMix
                    isRefreshing = false,
                    isLongClickState = false,
                    appBarTitle = folderSource.appName
                )
            }
            return FileContentUiState(
                appBarTitle = stringProvider.defaultAppName,
                source = folderSource
            )
        }
    }
}
