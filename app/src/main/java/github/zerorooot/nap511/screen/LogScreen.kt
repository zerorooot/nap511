package github.zerorooot.nap511.screen

import android.app.Application
import android.content.ClipData
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Article
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.screen.components.AppBarAction
import github.zerorooot.nap511.screen.components.AppTopBarLogScreen
import github.zerorooot.nap511.screen.components.MenuItemAction
import github.zerorooot.nap511.screen.components.TopAppBarSearch
import github.zerorooot.nap511.screen.components.TopBarAction
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import my.nanihadesuka.compose.LazyColumnScrollbar
import my.nanihadesuka.compose.ScrollbarSettings
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.time.Duration.Companion.milliseconds

// ============================================================================
// 一、日志级别枚举与视觉色彩定义
// ============================================================================

/**
 * 日志级别枚举
 *
 * @property code 级别简写单字符（如 V, D, I, W, E）
 * @property label 级别完整名称（如 Verbose, Debug, Info, Warn, Error）
 * @property color 级别主题前景色（用于药丸徽章文字、左侧 Accent 指示条等）
 * @property bgColor 级别药丸徽章的半透明背景色
 */
enum class LogLevel(
    val code: String,
    val label: String,
    val color: Color,
    val bgColor: Color
) {
    VERBOSE("V", "Verbose", Color(0xFF9E9E9E), Color(0x1F9E9E9E)),
    DEBUG("D", "Debug", Color(0xFF0288D1), Color(0x1F0288D1)),
    INFO("I", "Info", Color(0xFF388E3C), Color(0x1F388E3C)),
    WARN("W", "Warn", Color(0xFFF57C00), Color(0x1FF57C00)),
    ERROR("E", "Error", Color(0xFFD32F2F), Color(0x1FD32F2F)),
    UNKNOWN("?", "其他", Color(0xFF757575), Color(0x1F757575));

    companion object {
        /**
         * 根据单字符代码安全解析为对应的 [LogLevel]，未匹配时回退为 [UNKNOWN]
         */
        fun fromCode(code: String): LogLevel {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

// ============================================================================
// 二、日志数据模型与搜索匹配模型
// ============================================================================

/**
 * 结构化日志实体类
 *
 * @property raw 原始单行日志完整文本
 * @property timestamp 完整时间戳（如 "2026-09-01 16:15:46.776"）
 * @property tag 日志标签/模块名（已过滤内部 -XLOG 后缀）
 * @property level 日志级别枚举
 * @property message 日志消息主体或异常堆栈内容
 * @property uuid 唯一标识符，用作 LazyColumn 的稳定渲染 key
 */
data class LogEntry(
    val raw: String,
    val timestamp: String = "",
    val tag: String = "",
    val level: LogLevel = LogLevel.UNKNOWN,
    val message: String = raw,
    val uuid: String = UUID.randomUUID().toString()
)

/**
 * 搜索匹配项位置模型
 *
 * @property globalIndex 全局匹配项序号（从 0 开始自增，用于 "1/10" 导航）
 * @property logIndex 匹配项所属日志条目在当前列表中的索引
 * @property startCharInRaw 匹配关键字在 raw 字符串中的起始字符下标
 * @property length 匹配关键字的字符长度
 */
data class LogSearchMatch(
    val globalIndex: Int,
    val logIndex: Int,
    val startCharInRaw: Int,
    val length: Int
)

// ============================================================================
// 三、日志文本解析器
// ============================================================================

/**
 * XLog 日志解析单例
 *
 * 专门解析 XLog ClassicFlattener 格式，典型输出行如下：
 * 2026-09-01 16:15:46.776 D/XLOG: message content
 */
object LogParser {
    // 正则捕获组说明：
    // 组1: 日期时间 (yyyy-MM-dd HH:mm:ss 或带毫秒 .SSS)
    // 组2: 级别单字符 [VDIWEFA]
    // 组3: Tag 模块标签名称
    // 组4: 日志消息主体
    private val xlogPattern =
        Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+([VDIWEFA])/([^:]+):\s*(.*)$""")

    /**
     * 将原始多行日志字符串流式拆分并解析为结构化列表
     */
    fun parse(rawLog: String): List<LogEntry> {
        if (rawLog.isBlank()) return emptyList()
        return rawLog.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val trimmed = line.trim()
                val xlogMatch = xlogPattern.find(trimmed)
                if (xlogMatch != null) {
                    val (time, levelStr, tagStr, msg) = xlogMatch.destructured
                    LogEntry(
                        raw = line,
                        timestamp = time,
                        tag = tagStr.replace("-XLOG", ""),
                        level = LogLevel.fromCode(levelStr),
                        message = msg
                    )
                } else {
                    // 非标准格式时回退为普通条目
                    LogEntry(raw = line, message = line)
                }
            }.toList()
    }
}

// ============================================================================
// 四、主界面组件 (LogScreen)
// ============================================================================

/**
 * 日志查看页面主入口
 *
 * 支持：
 * 1. 响应式大屏自适应：手机竖屏单栏流式列表；平板/折叠屏双栏 List-Detail 面板。
 * 2. 实时轮询文件变动自动追随更新。
 * 3. 级别胶囊筛选（All, Error, Warn, Info, Debug, Verbose）与计数联动。
 * 4. 全局关键字高亮定位与上一个/下一个遍历。
 * 5. 悬浮一键回到底部按钮。
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LogScreen(isDualPane: Boolean, onClick: () -> Unit) {
    // 1. 数据与解析状态
    var rawLogText by remember { mutableStateOf(readLog()) }
    val parsedLogs by remember(rawLogText) { derivedStateOf { LogParser.parse(rawLogText) } }
    val clipboardManager = LocalClipboard.current

    val lazyListState = rememberLazyListState()
    val coroutine = rememberCoroutineScope()
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss")

    // 3. 级别筛选状态
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    // 4. 根据选中级别派生的当前显示日志列表
    val filteredLogs by remember(parsedLogs, selectedLevelFilter) {
        derivedStateOf {
            if (selectedLevelFilter == null) parsedLogs
            else parsedLogs.filter { it.level == selectedLevelFilter }
        }
    }

    // 5. 各级别日志数量统计（用于在筛选胶囊中展示计数）
    val levelCounts = remember(parsedLogs) {
        parsedLogs.groupingBy { it.level }.eachCount()
    }

    // 6. 当前选中的日志条目（单栏下用于弹出 Dialog，大屏双栏下常驻右侧面板展示）
    var selectedLogForDetail by remember { mutableStateOf<LogEntry?>(null) }

    // 7. 自动滚动跟踪开关（默认开启；当用户主动上滑翻阅时暂停，滑到底部恢复）
    var isAutoScrollEnabled by remember { mutableStateOf(true) }

    // 8. 协程轮询检测日志文件变动（每秒检查一次文件更新时间或大小）
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val logFile = File(App.instance.cacheDir, "log")
            var lastModified = if (logFile.exists()) logFile.lastModified() else 0L
            var lastLength = if (logFile.exists()) logFile.length() else 0L

            while (isActive) {
                delay(1000.milliseconds)
                if (logFile.exists()) {
                    val currentModified = logFile.lastModified()
                    val currentLength = logFile.length()
                    if (currentModified != lastModified || currentLength != lastLength) {
                        lastModified = currentModified
                        lastLength = currentLength
                        val updatedContent = readLog()
                        withContext(Dispatchers.Main) {
                            rawLogText = updatedContent
                        }
                    }
                }
            }
        }
    }

    // 9. 搜索相关状态管理
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    // 唤起搜索栏时自动请求焦点弹起输入法
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    // 实时计算搜索匹配项列表（基于当前过滤后的数据集）
    val searchMatches = remember(filteredLogs, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val list = mutableListOf<LogSearchMatch>()
            var globalIdx = 0
            filteredLogs.forEachIndexed { lIdx, logEntry ->
                val text = logEntry.raw
                var startIndex = 0
                while (startIndex < text.length) {
                    val foundIndex = text.indexOf(searchQuery, startIndex, ignoreCase = true)
                    if (foundIndex == -1) break
                    list.add(LogSearchMatch(globalIdx++, lIdx, foundIndex, searchQuery.length))
                    startIndex = foundIndex + searchQuery.length
                }
            }
            list
        }
    }

    // 当搜索结果变化时，重置当前焦点项序号
    LaunchedEffect(searchMatches) {
        currentMatchIndex = 0
    }

    // 当前选中的搜索项改变时，列表平滑滚动定位到对应日志条目
    LaunchedEffect(currentMatchIndex, searchMatches) {
        if (searchMatches.isNotEmpty() && currentMatchIndex in searchMatches.indices) {
            val targetMatch = searchMatches[currentMatchIndex]
            lazyListState.animateScrollToItem(targetMatch.logIndex)
        }
    }

    // 10. 顶部导航与菜单动作回调
    val appBarOnClick: (AppBarAction) -> Unit = { name ->
        when (name) {
            TopBarAction.SEARCH -> {
                isSearchOpen = true
            }

            MenuItemAction.SCROLL_TOP -> {
                coroutine.launch {
                    if (filteredLogs.isNotEmpty()) lazyListState.animateScrollToItem(0)
                }
            }

            MenuItemAction.SCROLL_BOTTOM -> {
                isAutoScrollEnabled = true
                coroutine.launch {
                    if (filteredLogs.isNotEmpty()) lazyListState.animateScrollToItem(filteredLogs.lastIndex)
                }
            }

            MenuItemAction.CLEAR_LOG -> {
                File(App.instance.cacheDir, "log").delete()
                rawLogText = ""
                selectedLogForDetail = null
            }

            MenuItemAction.EXPORT_LOG -> {
                writeToPublicExternalStorage(
                    App.instance,
                    "${App.instance.getStringRes(R.string.app_name)}_${
                        LocalDateTime.now().format(formatter)
                    }_log.txt",
                    rawLogText,
                    coroutine
                )
            }

            MenuItemAction.REFRESH_LOG -> {
                rawLogText = readLog()
                isAutoScrollEnabled = true
                coroutine.launch {
                    if (filteredLogs.isNotEmpty()) lazyListState.animateScrollToItem(filteredLogs.lastIndex)
                }
            }

            TopBarAction.DRAWER_MENU -> onClick.invoke()
            else -> {}
        }
    }

    // 页面主骨架：根据 isDualPane 动态分支为大屏双栏或单栏
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 顶部应用栏或搜索栏
        if (isSearchOpen) {
            TopAppBarSearch(
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onCloseSearch = {
                    isSearchOpen = false
                    searchQuery = ""
                },
                matchCount = searchMatches.size,
                currentMatchIndex = currentMatchIndex,
                onPrevMatch = {
                    if (searchMatches.isNotEmpty()) {
                        currentMatchIndex =
                            if (currentMatchIndex > 0) currentMatchIndex - 1 else searchMatches.lastIndex
                    }
                },
                onNextMatch = {
                    if (searchMatches.isNotEmpty()) {
                        currentMatchIndex =
                            if (currentMatchIndex < searchMatches.lastIndex) currentMatchIndex + 1 else 0
                    }
                },
                placeholderText = "搜索日志...",
                focusRequester = focusRequester
            )
        } else {
            AppTopBarLogScreen(ConfigKeyUtil.LOG_SCREEN, appBarOnClick)
        }

        // 日志级别过滤胶囊条（仅在有日志时呈现）
        if (parsedLogs.isNotEmpty()) {
            LogFilterChipBar(
                totalCount = parsedLogs.size,
                levelCounts = levelCounts,
                selectedLevel = selectedLevelFilter,
                onSelectLevel = { selectedLevelFilter = it }
            )
        }

        // 主体内容区域
        if (isDualPane) {
            // ==================== 大屏双栏模式 (List-Detail) ====================
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧栏：日志列表 (占 45%~48% 宽度权重)
                Box(
                    modifier = Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                ) {
                    LogListView(
                        filteredLogs = filteredLogs,
                        selectedLevelFilter = selectedLevelFilter,
                        onClearFilter = { selectedLevelFilter = null },
                        lazyListState = lazyListState,
                        searchQuery = searchQuery,
                        searchMatches = searchMatches,
                        currentMatchIndex = currentMatchIndex,
                        selectedLogId = selectedLogForDetail?.uuid,
                        isAutoScrollEnabled = isAutoScrollEnabled,
                        isSearchOpen = isSearchOpen,
                        onAutoScrollChange = { isAutoScrollEnabled = it },
                        onRowClick = { selectedLogForDetail = it },
                        onLongClick = { logEntry ->
                            copyLogToClipboard(clipboardManager, logEntry)
                        },
                        onScrollToBottom = {
                            isAutoScrollEnabled = true
                            coroutine.launch {
                                if (filteredLogs.isNotEmpty()) {
                                    lazyListState.animateScrollToItem(filteredLogs.lastIndex)
                                }
                            }
                        }
                    )
                }

                // 中间垂直分割线
                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 1.dp
                )

                // 右侧栏：日志详细与堆栈分析常驻面板 (占 52%~55% 宽度权重)
                Box(
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                ) {
                    LogDetailPane(
                        selectedLog = selectedLogForDetail,
                        onClose = { selectedLogForDetail = null }
                    )
                }
            }
        } else {
            // ==================== 手机竖屏单栏模式 ====================
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                LogListView(
                    filteredLogs = filteredLogs,
                    selectedLevelFilter = selectedLevelFilter,
                    onClearFilter = { selectedLevelFilter = null },
                    lazyListState = lazyListState,
                    searchQuery = searchQuery,
                    searchMatches = searchMatches,
                    currentMatchIndex = currentMatchIndex,
                    selectedLogId = selectedLogForDetail?.uuid,
                    isAutoScrollEnabled = isAutoScrollEnabled,
                    isSearchOpen = isSearchOpen,
                    onAutoScrollChange = { isAutoScrollEnabled = it },
                    onRowClick = { selectedLogForDetail = it },
                    onLongClick = { logEntry ->
                        copyLogToClipboard(clipboardManager, logEntry)
                    },
                    onScrollToBottom = {
                        isAutoScrollEnabled = true
                        coroutine.launch {
                            if (filteredLogs.isNotEmpty()) {
                                lazyListState.animateScrollToItem(filteredLogs.lastIndex)
                            }
                        }
                    }
                )
            }

            // 单栏模式下的轻触详情弹窗
            selectedLogForDetail?.let { detail ->
                AlertDialog(
                    onDismissRequest = { selectedLogForDetail = null },
                    title = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = detail.level.bgColor,
                                        shape = RoundedCornerShape(4.dp)
                                    )
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = detail.level.code,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = FontFamily.Monospace,
                                    color = detail.level.color
                                )
                            }
                            Text(
                                text = if (detail.tag.isNotBlank()) detail.tag else "日志详情",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    },
                    text = {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .verticalScroll(rememberScrollState())
                        ) {
                            if (detail.timestamp.isNotBlank()) {
                                Text(
                                    text = "完整时间: ${detail.timestamp}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                            }
                            SelectionContainer {
                                Text(
                                    text = detail.message,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.5.sp,
                                    lineHeight = 17.sp,
                                    color = if (detail.level == LogLevel.ERROR) detail.level.color
                                    else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                copyLogToClipboard(clipboardManager, detail)
                            }
                        ) {
                            Text("复制全部")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedLogForDetail = null }) {
                            Text("关闭")
                        }
                    }
                )
            }
        }
    }
}

// ============================================================================
// 五、日志列表与滚动视图组件 (LogListView)
// ============================================================================

/**
 * 封装日志列表、滚动条、空状态与回到底部 FAB 的统一视图
 */
@Composable
private fun LogListView(
    filteredLogs: List<LogEntry>,
    selectedLevelFilter: LogLevel?,
    onClearFilter: () -> Unit,
    lazyListState: androidx.compose.foundation.lazy.LazyListState,
    searchQuery: String,
    searchMatches: List<LogSearchMatch>,
    currentMatchIndex: Int,
    selectedLogId: String?,
    isAutoScrollEnabled: Boolean,
    isSearchOpen: Boolean,
    onAutoScrollChange: (Boolean) -> Unit,
    onRowClick: (LogEntry) -> Unit,
    onLongClick: (LogEntry) -> Unit,
    onScrollToBottom: () -> Unit
) {
    if (filteredLogs.isEmpty()) {
        LogEmptyState(
            hasFilter = selectedLevelFilter != null,
            onClearFilter = onClearFilter
        )
    } else {
        LazyColumnScrollbar(
            state = lazyListState,
            settings = ScrollbarSettings.Default.copy(
                thumbUnselectedColor = MaterialTheme.colorScheme.inversePrimary
            )
        ) {
            LazyColumn(
                state = lazyListState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 12.dp,
                    end = 12.dp,
                    top = 6.dp,
                    bottom = 72.dp
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                itemsIndexed(items = filteredLogs, key = { _, item ->
                    item.uuid
                }) { index, item ->
                    LogItemRow(
                        logEntry = item,
                        logIndex = index,
                        searchQuery = searchQuery,
                        searchMatches = searchMatches,
                        currentMatchIndex = currentMatchIndex,
                        isSelectedInDualPane = (item.uuid == selectedLogId),
                        onRowClick = onRowClick,
                        onLongClick = onLongClick
                    )
                }
            }
        }
    }

    // 检测当前视口是否已位于列表底部
    val isAtBottom by remember(filteredLogs.size) {
        derivedStateOf {
            val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisibleItem?.index == filteredLogs.lastIndex
        }
    }

    // 用户滚动时动态同步自动滚动状态：离底设为 false，回底恢复为 true
    LaunchedEffect(isAtBottom, lazyListState.isScrollInProgress) {
        if (lazyListState.isScrollInProgress) {
            onAutoScrollChange(isAtBottom)
        }
    }

    // 列表尺寸更新且处于自动追随状态时滚动至末尾（非搜索模式下）
    LaunchedEffect(filteredLogs.size, isAutoScrollEnabled) {
        if (isAutoScrollEnabled && filteredLogs.isNotEmpty() && !isSearchOpen) {
            lazyListState.scrollToItem(filteredLogs.lastIndex)
        }
    }

    // 悬浮“回到底部”小按钮（仅在上滑未到底部时平滑浮现）
    androidx.compose.animation.AnimatedVisibility(
        visible = !isAtBottom && filteredLogs.isNotEmpty(),
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = Modifier
            .fillMaxSize()
            .padding(end = 16.dp, bottom = 16.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomEnd) {
            SmallFloatingActionButton(
                onClick = onScrollToBottom,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "回到底部",
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "回到底部",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
    }
}

// ============================================================================
// 六、日志级别过滤胶囊栏 (LogFilterChipBar)
// ============================================================================

/**
 * 顶部水平可滚动的日志级别筛选 Chip 条
 *
 * @param totalCount 总日志数
 * @param levelCounts 各级别统计 Map
 * @param selectedLevel 当前选中的筛选级别（null 表示全部）
 * @param onSelectLevel 选中级别切换回调
 */
@Composable
fun LogFilterChipBar(
    totalCount: Int,
    levelCounts: Map<LogLevel, Int>,
    selectedLevel: LogLevel?,
    onSelectLevel: (LogLevel?) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()
    val levels = listOf(
        null,
        LogLevel.DEBUG,
        LogLevel.VERBOSE,
        LogLevel.ERROR,
        LogLevel.WARN,
        LogLevel.INFO,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        levels.forEach { level ->
            val isSelected = (selectedLevel == level)
            val count = if (level == null) totalCount else (levelCounts[level] ?: 0)
            val label = level?.label ?: "全部"

            FilterChip(
                selected = isSelected,
                onClick = {
                    // 若已选中则点击切回全部，未选中则选中当前级别
                    if (isSelected && level != null) {
                        onSelectLevel(null)
                    } else {
                        onSelectLevel(level)
                    }
                },
                label = {
                    Text(
                        text = "$label $count",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                },
                leadingIcon = if (level != null && !isSelected) {
                    {
                        // 未选中状态下展示对应级别的彩色状态小圆点
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(level.color, CircleShape)
                        )
                    }
                } else null,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = when (level) {
                        LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer
                        LogLevel.WARN -> if (isDark) Color(0xFF4E2A00) else Color(0xFFFFE0B2)
                        else -> MaterialTheme.colorScheme.secondaryContainer
                    },
                    selectedLabelColor = when (level) {
                        LogLevel.ERROR -> MaterialTheme.colorScheme.onErrorContainer
                        LogLevel.WARN -> if (isDark) Color(0xFFFFCC80) else Color(0xFFE65100)
                        else -> MaterialTheme.colorScheme.onSecondaryContainer
                    }
                ),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.height(32.dp)
            )
        }
    }
}

// ============================================================================
// 七、大屏双栏独立详情面板 (LogDetailPane)
// ============================================================================

/**
 * 大屏双栏常驻详情面板
 *
 * 位于平板/横屏右半屏，展示选中日志条目的完整信息、完整时间、模块来源及完整异常堆栈。
 */
@Composable
fun LogDetailPane(
    selectedLog: LogEntry?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val clipboardManager = LocalClipboard.current
    val isDark = isSystemInDarkTheme()

    if (selectedLog == null) {
        // 未选中任何条目时的友好引导视图
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.Article,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }
                Text(
                    text = "未选中日志条目",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "点击左侧列表中的条目，在此查看完整堆栈与调用详情",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // 详情面板头部：级别徽章、模块 Tag 与快捷复制按钮
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .background(
                            color = selectedLog.level.bgColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = selectedLog.level.label,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = selectedLog.level.color
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                Text(
                    text = if (selectedLog.tag.isNotBlank()) selectedLog.tag else "系统日志",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 一键复制按钮
                IconButton(
                    onClick = {
                        copyLogToClipboard(clipboardManager, selectedLog)
                    }
                ) {
                    Icon(
                        imageVector = Icons.Default.ContentCopy,
                        contentDescription = "复制日志",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // 完整时间戳展示
            if (selectedLog.timestamp.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "记录时间: ${selectedLog.timestamp}",
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
            Spacer(modifier = Modifier.height(10.dp))

            // 日志正文与堆栈代码块（支持局部长按自由选择文本）
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (isDark) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                border = BorderStroke(
                    0.5.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                SelectionContainer {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        Text(
                            text = selectedLog.message,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.5.sp,
                            lineHeight = 18.sp,
                            color = if (selectedLog.level == LogLevel.ERROR) selectedLog.level.color
                            else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }
    }
}

// ============================================================================
// 八、单条日志卡片渲染 (LogItemRow)
// ============================================================================

/**
 * 单条日志卡片渲染组件
 *
 * 针对手机竖屏窄屏优化排版：
 * 1. 时间戳采用精简展示（仅展示 HH:mm:ss.SSS，减少近半字符占用）；
 * 2. Header 行将 Badge 与时间戳作为两端锚点，中间 Tag 独享剩余可用宽度，彻底解决 Tag 被挤压截断问题；
 * 3. 左侧通过 drawBehind 绘制优雅的圆角 Accent 级别指示条。
 */
@Composable
fun LogItemRow(
    logEntry: LogEntry,
    logIndex: Int = 0,
    searchQuery: String = "",
    searchMatches: List<LogSearchMatch> = emptyList(),
    currentMatchIndex: Int = 0,
    isSelectedInDualPane: Boolean = false,
    onRowClick: (LogEntry) -> Unit = {},
    onLongClick: (LogEntry) -> Unit
) {
    val isDark = isSystemInDarkTheme()

    // 提取属于本条日志的所有搜索匹配项
    val matchesForThisLog = remember(searchMatches, logIndex) {
        if (searchQuery.isBlank()) emptyList() else searchMatches.filter { it.logIndex == logIndex }
    }

    // 判定本条日志是否包含当前全局激活的搜索焦点项
    val isActiveLogEntry = remember(searchMatches, currentMatchIndex, logIndex) {
        if (searchQuery.isBlank()) false else searchMatches.getOrNull(currentMatchIndex)?.logIndex == logIndex
    }

    // 精炼时间戳计算：搜索匹配了日期时展示完整日期，日常查看时默认展示紧凑时间 (HH:mm:ss.SSS)
    val displayTimestamp = remember(logEntry.timestamp, searchQuery) {
        val datePart =
            if (logEntry.timestamp.contains(" ")) logEntry.timestamp.substringBefore(" ") else ""
        if (searchQuery.isNotBlank() && datePart.contains(searchQuery, ignoreCase = true)) {
            logEntry.timestamp
        } else if (logEntry.timestamp.contains(" ")) {
            logEntry.timestamp.substringAfter(" ")
        } else {
            logEntry.timestamp
        }
    }

    // 构建带搜索高亮样式的精简时间戳 AnnotatedString
    val annotatedTimestamp = remember(
        displayTimestamp,
        logEntry.raw,
        searchQuery,
        matchesForThisLog,
        currentMatchIndex
    ) {
        if (searchQuery.isBlank() || displayTimestamp.isEmpty()) {
            AnnotatedString(displayTimestamp)
        } else {
            val tsStartInRaw = logEntry.raw.indexOf(displayTimestamp)
            buildAnnotatedString {
                append(displayTimestamp)
                if (tsStartInRaw != -1) {
                    matchesForThisLog.forEach { match ->
                        val startInTs = match.startCharInRaw - tsStartInRaw
                        val endInTs = startInTs + match.length
                        if (startInTs >= 0 && endInTs <= displayTimestamp.length) {
                            val isActive = (match.globalIndex == currentMatchIndex)
                            addStyle(
                                style = SpanStyle(
                                    background = if (isActive) Color(0xFFFF9800) else Color(
                                        0xFFFFE082
                                    ),
                                    color = Color.Black
                                ),
                                start = startInTs,
                                end = endInTs
                            )
                        }
                    }
                }
            }
        }
    }

    // 构建带搜索高亮样式的消息主体 AnnotatedString
    val annotatedMessage = remember(
        logEntry.message,
        logEntry.raw,
        searchQuery,
        matchesForThisLog,
        currentMatchIndex
    ) {
        if (searchQuery.isBlank() || logEntry.message.isEmpty()) {
            AnnotatedString(logEntry.message)
        } else {
            val msg = logEntry.message
            val msgStartInRaw =
                if (logEntry.timestamp.isEmpty()) 0 else logEntry.raw.lastIndexOf(msg)
            buildAnnotatedString {
                append(msg)
                if (msgStartInRaw != -1) {
                    matchesForThisLog.forEach { match ->
                        val startInMsg = match.startCharInRaw - msgStartInRaw
                        val endInMsg = startInMsg + match.length
                        if (startInMsg >= 0 && endInMsg <= msg.length) {
                            val isActive = (match.globalIndex == currentMatchIndex)
                            addStyle(
                                style = SpanStyle(
                                    background = if (isActive) Color(0xFFFF9800) else Color(
                                        0xFFFFE082
                                    ),
                                    color = Color.Black
                                ),
                                start = startInMsg,
                                end = endInMsg
                            )
                        }
                    }
                } else {
                    var startIndex = 0
                    while (startIndex < msg.length) {
                        val foundIndex = msg.indexOf(searchQuery, startIndex, ignoreCase = true)
                        if (foundIndex == -1) break
                        addStyle(
                            style = SpanStyle(
                                background = Color(0xFFFFE082),
                                color = Color.Black
                            ),
                            start = foundIndex,
                            end = foundIndex + searchQuery.length
                        )
                        startIndex = foundIndex + searchQuery.length
                    }
                }
            }
        }
    }

    // 卡片背景底色计算（Error/Warn 带有微弱柔和色晕，搜索激活态有高亮）
    val cardBg = when {
        isActiveLogEntry -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.45f else 0.35f)
        isSelectedInDualPane -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.25f)
        logEntry.level == LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.22f else 0.15f)
        logEntry.level == LogLevel.WARN -> (if (isDark) Color(0xFF4E2A00) else Color(0xFFFFF3E0)).copy(
            alpha = if (isDark) 0.3f else 0.5f
        )

        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.3f)
    }

    // 卡片外边框样式
    val border = when {
        isActiveLogEntry -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isSelectedInDualPane -> BorderStroke(
            1.5.dp,
            MaterialTheme.colorScheme.primary.copy(alpha = 0.8f)
        )

        logEntry.level == LogLevel.ERROR -> BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        )

        logEntry.level == LogLevel.WARN -> BorderStroke(
            0.8.dp,
            Color(0xFFFFA726).copy(alpha = 0.4f)
        )

        else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
    }

    // 卡片左侧 Accent 指示线颜色
    val accentColor = when (logEntry.level) {
        LogLevel.ERROR -> MaterialTheme.colorScheme.error
        LogLevel.WARN -> Color(0xFFFFA726)
        LogLevel.INFO -> Color(0xFF4CAF50)
        LogLevel.DEBUG -> Color(0xFF03A9F4)
        LogLevel.VERBOSE -> Color(0xFF9E9E9E)
        LogLevel.UNKNOWN -> MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = cardBg,
        border = border,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onRowClick(logEntry) },
                onLongClick = { onLongClick(logEntry) }
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .drawBehind {
                    // 左边缘 Accent 指示条绘制
                    val barWidth = 3.5.dp.toPx()
                    drawRoundRect(
                        color = accentColor,
                        topLeft = Offset.Zero,
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(barWidth / 2, barWidth / 2)
                    )
                }
                .padding(start = 10.dp, end = 10.dp, top = 8.dp, bottom = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // ==================== 卡片头部行（解决 Tag 挤压的关键布局） ====================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 1. 级别药丸徽标 (Badge) - 自然宽度测量
                    Box(
                        modifier = Modifier
                            .background(
                                color = logEntry.level.bgColor,
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(horizontal = 5.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = logEntry.level.code,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = logEntry.level.color
                        )
                    }

                    // 2. Tag 模块名称（赋予 weight(1f)，独享中间全部可用空间，彻底杜绝折叠挤压）
                    if (logEntry.tag.isNotBlank()) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = logEntry.tag,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // 若无 Tag，使用 Spacer 占满中间空间以保持时间戳右对齐
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // 3. 精简时间戳（右对齐展示，自然宽度测量）
                    if (displayTimestamp.isNotBlank()) {
                        Text(
                            text = annotatedTimestamp,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                            maxLines = 1
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // ==================== 日志消息主体 ====================
                Text(
                    text = annotatedMessage,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                    color = if (logEntry.level == LogLevel.ERROR) {
                        logEntry.level.color
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }
        }
    }
}

// ============================================================================
// 九、空状态组件 (LogEmptyState)
// ============================================================================

/**
 * 质感空状态引导视图
 *
 * @param hasFilter 当前是否处于特定级别筛选状态下
 * @param onClearFilter 点击“查看全部日志”时的回调
 */
@Composable
fun LogEmptyState(
    hasFilter: Boolean,
    onClearFilter: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(32.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(68.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.Article,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(34.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (hasFilter) "该级别下暂无日志" else "暂无日志记录",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (hasFilter) "可点击下方按钮或上方筛选条查看全部日志" else "应用运行产生的系统与网络日志将在此实时显示",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f),
                textAlign = TextAlign.Center
            )
            if (hasFilter) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onClearFilter) {
                    Text("查看全部日志")
                }
            }
        }
    }
}

// ============================================================================
// 十、通用辅助工具函数
// ============================================================================

/**
 * 将单条结构化日志格式化并复制到系统剪切板
 */
private fun copyLogToClipboard(
    clipboardManager: androidx.compose.ui.platform.Clipboard,
    logEntry: LogEntry
) {
    val text = listOf(logEntry.tag, logEntry.timestamp, logEntry.message)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    clipboardManager.nativeClipboardManager.setPrimaryClip(
        ClipData.newPlainText("logs", text)
    )
    App.instance.toast("日志已复制到剪切板")
}

/**
 * 读取应用缓存目录下的 log 文件全部内容
 */
fun readLog(): String {
    return try {
        readInputStreamAsString(
            FileInputStream(
                File(App.instance.cacheDir, "log")
            )
        )
    } catch (_: Exception) {
        ""
    }
}

/**
 * 导出日志文件至系统公共外部存储（Downloads 目录）
 *
 * 兼容 Android 10 (Q) 及以上通过 MediaStore 写入，以及 Android 9 及以下通过传统文件写入。
 */
fun writeToPublicExternalStorage(
    applicationContext: Application,
    fileName: String,
    content: String,
    coroutine: CoroutineScope
) {
    coroutine.launch(Dispatchers.IO) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val resolver = applicationContext.contentResolver
            val values = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                    App.instance.toast("导出成功，日志文件保存至Downloads目录，文件名为:$fileName")
                    XLog.i("FileWrite File written to Downloads: $uri")
                }
            }
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            try {
                file.writeText(content)
                XLog.i("FileWrite File written to: ${file.absolutePath}")
            } catch (e: IOException) {
                e.printStackTrace()
                XLog.e("FileWrite Error writing file: $e")
            }
        }
    }
}

/**
 * 将输入流字节流高效聚合转换为字符串
 */
fun readInputStreamAsString(`in`: InputStream): String {
    val bis = BufferedInputStream(`in`)
    val buf = ByteArrayOutputStream()
    var result = bis.read()
    while (result != -1) {
        val b = result.toByte()
        buf.write(b.toInt())
        result = bis.read()
    }
    return buf.toString()
}
