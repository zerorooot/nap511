package github.zerorooot.nap511.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.elvishew.xlog.XLog
import dagger.hilt.android.lifecycle.HiltViewModel
import github.zerorooot.nap511.screen.LogEntry
import github.zerorooot.nap511.screen.LogFileManager
import github.zerorooot.nap511.screen.LogLevel
import github.zerorooot.nap511.screen.LogParser
import github.zerorooot.nap511.screen.LogUiState
import github.zerorooot.nap511.util.findSearchMatches
import github.zerorooot.nap511.viewmodel.platform.AndroidToastProvider
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
import javax.inject.Inject
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.time.Duration.Companion.milliseconds

/**
 * 日志模块业务架构 ViewModel (LogViewModel)
 *
 * 承担职责：
 * 1. 磁盘文件变动监听与异步轮询（IO 协程），抗屏幕旋转与重组；
 * 2. 多行异常日志状态机解析与结构化映射；
 * 3. 日志级别筛选计算、搜索匹配位置索引与定位游标管理；
 * 4. 日志导出至 Downloads 目录与清空文件操作。
 *
 * @param application Android 应用程序上下文
 * @param ioDispatcher IO 协程调度器，支持单测注入模拟
 * @param autoStartWatcher 是否在初始化时自动启动文件监听轮询
 */
@HiltViewModel
class LogViewModel(
    application: Application,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val autoStartWatcher: Boolean = true,
    private val toastProvider: ToastProvider = AndroidToastProvider(application),
) : AndroidViewModel(application) {
    @Inject
    constructor(
        application: Application,
        toastProvider: ToastProvider,
    ) : this(
        application = application,
        ioDispatcher = Dispatchers.IO,
        autoStartWatcher = true,
        toastProvider = toastProvider,
    )

    private val _uiState = MutableStateFlow(LogUiState())
    val uiState: StateFlow<LogUiState> = _uiState.asStateFlow()
    // 创建 LogViewModel 时传入 Application Context 和 toastProvider 初始化 LogFileManager
    private val logFileManager = LogFileManager(application, toastProvider)

    init {
        if (autoStartWatcher) {
            startFileWatcher()
        }
    }

    /**
     * 启动本地日志文件变动监听轮询
     */
    fun startFileWatcher() {
        viewModelScope.launch(ioDispatcher) {
            val logFile = logFileManager.getLogFile()
            var lastModified = if (logFile.exists()) logFile.lastModified() else 0L
            var lastLength = if (logFile.exists()) logFile.length() else 0L

            // 首次全量加载
            loadLogContent()

            while (isActive) {
                delay(1000.milliseconds)
                if (logFile.exists()) {
                    val currentModified = logFile.lastModified()
                    val currentLength = logFile.length()
                    if (currentModified != lastModified || currentLength != lastLength) {
                        lastModified = currentModified
                        lastLength = currentLength
                        loadLogContent()
                    }
                }
            }
        }
    }

    /**
     * 从本地磁盘读取日志内容并解析更新
     */
    suspend fun loadLogContent() {
        val raw = try {
            logFileManager.readLogText()
        } catch (e: Exception) {
            XLog.e("LogViewModel readLogText error", e)
            ""
        }
        withContext(Dispatchers.Main.immediate) {
            updateLogContent(raw)
        }
    }

    /**
     * 结构化解析原始文本并更新 UI 状态
     *
     * @param rawText 原始日志全量字符串
     */
    fun updateLogContent(rawText: String) {
        val parsed = LogParser.parse(rawText)
        _uiState.update { state ->
            val levelCounts = parsed.groupingBy { it.level }.eachCount()
            val filtered = if (state.selectedLevelFilter == null) parsed
            else parsed.filter { it.level == state.selectedLevelFilter }
            val matches = findSearchMatches(filtered, state.searchQuery) { it.raw }
            val matchIndex = if (matches.isNotEmpty()) {
                state.currentMatchIndex.coerceIn(0, matches.lastIndex)
            } else 0

            state.copy(
                logs = parsed,
                filteredLogs = filtered,
                levelCounts = levelCounts,
                searchMatches = matches,
                currentMatchIndex = matchIndex
            )
        }
    }

    /**
     * 切换当前选中的日志级别过滤条件
     *
     * @param level 目标级别，为 null 表示不过滤（展示全部）
     */
    fun selectLevel(level: LogLevel?) {
        XLog.d("LogViewModel selectLevel: ${level?.name ?: "ALL"}")
        _uiState.update { state ->
            val filtered =
                if (level == null) state.logs else state.logs.filter { it.level == level }
            val matches = findSearchMatches(filtered, state.searchQuery) { it.raw }
            state.copy(
                selectedLevelFilter = level,
                filteredLogs = filtered,
                searchMatches = matches,
                currentMatchIndex = 0
            )
        }
    }

    /**
     * 更新搜索关键字并实时计算匹配项
     *
     * @param query 搜索关键字
     */
    fun setSearchQuery(query: String) {
        _uiState.update { state ->
            val matches = findSearchMatches(state.filteredLogs, query) { it.raw }
            state.copy(
                searchQuery = query,
                searchMatches = matches,
                currentMatchIndex = 0
            )
        }
    }

    /**
     * 切换搜索栏开关状态
     *
     * @param isOpen 是否展开搜索栏
     */
    fun toggleSearch(isOpen: Boolean) {
        XLog.d("LogViewModel toggleSearch: $isOpen")
        _uiState.update { state ->
            val query = if (!isOpen) "" else state.searchQuery
            val matches = if (!isOpen) emptyList() else findSearchMatches(
                state.filteredLogs,
                query
            ) { it.raw }
            state.copy(
                isSearchOpen = isOpen,
                searchQuery = query,
                searchMatches = matches,
                currentMatchIndex = 0
            )
        }
    }

    /**
     * 切换到下一个搜索匹配项
     */
    fun nextMatch() {
        _uiState.update { state ->
            if (state.searchMatches.isEmpty()) state
            else {
                val nextIndex = if (state.currentMatchIndex < state.searchMatches.lastIndex) {
                    state.currentMatchIndex + 1
                } else 0
                state.copy(currentMatchIndex = nextIndex)
            }
        }
    }

    /**
     * 切换到上一个搜索匹配项
     */
    fun prevMatch() {
        _uiState.update { state ->
            if (state.searchMatches.isEmpty()) state
            else {
                val prevIndex = if (state.currentMatchIndex > 0) {
                    state.currentMatchIndex - 1
                } else state.searchMatches.lastIndex
                state.copy(currentMatchIndex = prevIndex)
            }
        }
    }

    /**
     * 跳转至指定索引的匹配项
     */
    fun navigateMatch(index: Int) {
        _uiState.update { state ->
            if (state.searchMatches.isEmpty()) state
            else state.copy(currentMatchIndex = index.coerceIn(0, state.searchMatches.lastIndex))
        }
    }

    /**
     * 设置大屏或弹窗选中的单条日志详情
     */
    fun selectLogForDetail(entry: LogEntry?) {
        _uiState.update { it.copy(selectedLogForDetail = entry) }
    }

    /**
     * 设置是否开启跟随日志自动滚动到底部
     */
    fun setAutoScrollEnabled(enabled: Boolean) {
        _uiState.update { it.copy(isAutoScrollEnabled = enabled) }
    }

    /**
     * 导出当前全量日志到 Downloads 目录
     */
    fun exportLog() {
        val formatter = DateTimeFormatter.ofPattern("yyyy_MM_dd_HH_mm_ss")
        val fileName = "nap511_log_${LocalDateTime.now().format(formatter)}.txt"
        val content = _uiState.value.logs.joinToString("\n") { it.raw }
        XLog.i("LogViewModel exportLog: $fileName, total lines: ${_uiState.value.logs.size}")
        logFileManager.exportLogFile(fileName, content, viewModelScope)
    }

    /**
     * 清空本地日志文件并重置 UI 状态
     */
    fun clearLog() {
        XLog.i("LogViewModel clearLog executed")
        if (logFileManager.clearLogFile()) {
            _uiState.update {
                it.copy(
                    logs = emptyList(),
                    filteredLogs = emptyList(),
                    levelCounts = emptyMap(),
                    searchMatches = emptyList(),
                    selectedLogForDetail = null
                )
            }
        }
    }

    /**
     * 手动触发刷新日志内容
     */
    fun refreshLogs() {
        XLog.d("LogViewModel manual refreshLogs triggered")
        viewModelScope.launch(ioDispatcher) {
            loadLogContent()
        }
    }
}
