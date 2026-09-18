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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import github.zerorooot.nap511.screen.components.MiddleEllipsisText
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

// ==================== 日志级别枚举与颜色设置 ====================
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
        fun fromCode(code: String): LogLevel {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

// ==================== 日志数据结构 ====================
data class LogEntry(
    val raw: String,
    val timestamp: String = "",
    val tag: String = "",
    val level: LogLevel = LogLevel.UNKNOWN,
    val message: String = raw,
    val uuid: String = UUID.randomUUID().toString()
)

/**
 * 搜索匹配项位置信息
 */
data class LogSearchMatch(
    val globalIndex: Int,
    val logIndex: Int,
    val startCharInRaw: Int,
    val length: Int
)

// ==================== 解析器 ====================
object LogParser {
    // XLog ClassicFlattener 格式: 2026-09-01 16:15:46.776 D/XLOG: message
    private val xlogPattern =
        Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+([VDIWEFA])/([^:]+):\s*(.*)$""")

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
                    LogEntry(raw = line, message = line)
                }
            }.toList()
    }
}

// ==================== UI 界面 ====================
@Composable
fun LogScreen(onClick: () -> Unit) {
    var rawLogText by remember { mutableStateOf(readLog()) }
    val parsedLogs by remember(rawLogText) { derivedStateOf { LogParser.parse(rawLogText) } }
    val clipboardManager = LocalClipboard.current

    val lazyListState = rememberLazyListState()
    val coroutine = rememberCoroutineScope()
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss")

    // 日志级别过滤状态
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }

    // 过滤后的日志列表
    val filteredLogs by remember(parsedLogs, selectedLevelFilter) {
        derivedStateOf {
            if (selectedLevelFilter == null) parsedLogs
            else parsedLogs.filter { it.level == selectedLevelFilter }
        }
    }

    // 各级别日志计数
    val levelCounts = remember(parsedLogs) {
        parsedLogs.groupingBy { it.level }.eachCount()
    }

    // 选中的日志条目（点击展示详情弹窗）
    var selectedLogForDetail by remember { mutableStateOf<LogEntry?>(null) }

    // LogScreen.kt 内部状态（默认勾选/开启）
    var isAutoScrollEnabled by remember { mutableStateOf(true) }

    // 自动轮询检测日志文件变动并更新
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

    // --- 搜索相关状态 ---
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    // 打开搜索栏时自动获取焦点唤起键盘
    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    // 计算所有匹配项的位置列表（基于当前过滤后的日志列表）
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

    // 搜索匹配项改变时重置当前焦点索引
    LaunchedEffect(searchMatches) {
        currentMatchIndex = 0
    }

    // 当选中的匹配项切换时，自动滚动 LazyColumn 到对应日志
    LaunchedEffect(currentMatchIndex, searchMatches) {
        if (searchMatches.isNotEmpty() && currentMatchIndex in searchMatches.indices) {
            val targetMatch = searchMatches[currentMatchIndex]
            lazyListState.animateScrollToItem(targetMatch.logIndex)
        }
    }

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
                isAutoScrollEnabled = true // 点击后重新开启自动追日志
                coroutine.launch {
                    if (filteredLogs.isNotEmpty()) lazyListState.animateScrollToItem(filteredLogs.lastIndex)
                }
            }

            MenuItemAction.CLEAR_LOG -> {
                File(App.instance.cacheDir, "log").delete()
                rawLogText = ""
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
                isAutoScrollEnabled = true // 刷新日志时也重置为开启
                coroutine.launch {
                    if (filteredLogs.isNotEmpty()) lazyListState.animateScrollToItem(filteredLogs.lastIndex)
                }
            }

            TopBarAction.DRAWER_MENU -> onClick.invoke()
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
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

        // 仅在存在日志时展示级别过滤条
        if (parsedLogs.isNotEmpty()) {
            LogFilterChipBar(
                totalCount = parsedLogs.size,
                levelCounts = levelCounts,
                selectedLevel = selectedLevelFilter,
                onSelectLevel = { selectedLevelFilter = it }
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (filteredLogs.isEmpty()) {
                LogEmptyState(
                    hasFilter = selectedLevelFilter != null,
                    onClearFilter = { selectedLevelFilter = null }
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
                                onRowClick = {
                                    selectedLogForDetail = it
                                },
                                onLongClick = { logEntry ->
                                    val text =
                                        listOf(logEntry.tag, logEntry.timestamp, logEntry.message)
                                            .filter { it.isNotBlank() }
                                            .joinToString("\n")
                                    clipboardManager.nativeClipboardManager.setPrimaryClip(
                                        ClipData.newPlainText(
                                            "logs",
                                            text
                                        )
                                    )
                                    App.instance.toast("日志已复制到剪切板")
                                }
                            )
                        }
                    }
                }
            }

            // 监听是否滚动到最底部
            val isAtBottom by remember(filteredLogs.size) {
                derivedStateOf {
                    val lastVisibleItem = lazyListState.layoutInfo.visibleItemsInfo.lastOrNull()
                    lastVisibleItem?.index == filteredLogs.lastIndex
                }
            }

            // 用户滑动时：滑离底部设为 false，划回底部自动恢复为 true
            LaunchedEffect(isAtBottom, lazyListState.isScrollInProgress) {
                if (lazyListState.isScrollInProgress) {
                    isAutoScrollEnabled = isAtBottom
                }
            }

            // 首次进入或过滤改变、自动跟踪开启时自动滚动到底部（仅在非搜索模式下）
            LaunchedEffect(filteredLogs.size, isAutoScrollEnabled) {
                if (isAutoScrollEnabled && filteredLogs.isNotEmpty() && !isSearchOpen) {
                    lazyListState.scrollToItem(filteredLogs.lastIndex)
                }
            }

            // 浮动“回到底部”小按钮
            androidx.compose.animation.AnimatedVisibility(
                visible = !isAtBottom && filteredLogs.isNotEmpty(),
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = 16.dp)
            ) {
                SmallFloatingActionButton(
                    onClick = {
                        isAutoScrollEnabled = true
                        coroutine.launch {
                            if (filteredLogs.isNotEmpty()) {
                                lazyListState.animateScrollToItem(filteredLogs.lastIndex)
                            }
                        }
                    },
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

    // 日志详情弹窗
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
                        text = detail.tag.ifBlank { "日志详情" },
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
                            text = "时间: ${detail.timestamp}",
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
                            color = if (detail.level == LogLevel.ERROR) detail.level.color else MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val text = listOf(detail.tag, detail.timestamp, detail.message)
                            .filter { it.isNotBlank() }
                            .joinToString("\n")
                        clipboardManager.nativeClipboardManager.setPrimaryClip(
                            ClipData.newPlainText("logs", text)
                        )
                        App.instance.toast("日志已复制到剪切板")
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

// ==================== 日志级别过滤胶囊条 ====================
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

// ==================== 空状态组件 ====================
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

// ==================== 单条日志渲染组件 ====================
@Composable
fun LogItemRow(
    logEntry: LogEntry,
    logIndex: Int = 0,
    searchQuery: String = "",
    searchMatches: List<LogSearchMatch> = emptyList(),
    currentMatchIndex: Int = 0,
    onRowClick: (LogEntry) -> Unit = {},
    onLongClick: (LogEntry) -> Unit
) {
    val isDark = isSystemInDarkTheme()
    val matchesForThisLog = remember(searchMatches, logIndex) {
        if (searchQuery.isBlank()) emptyList() else searchMatches.filter { it.logIndex == logIndex }
    }
    val isActiveLogEntry = remember(searchMatches, currentMatchIndex, logIndex) {
        if (searchQuery.isBlank()) false else searchMatches.getOrNull(currentMatchIndex)?.logIndex == logIndex
    }

    val annotatedTimestamp = remember(
        logEntry.timestamp,
        logEntry.raw,
        searchQuery,
        matchesForThisLog,
        currentMatchIndex
    ) {
        if (searchQuery.isBlank() || logEntry.timestamp.isEmpty()) {
            AnnotatedString(logEntry.timestamp)
        } else {
            val ts = logEntry.timestamp
            val tsStartInRaw = logEntry.raw.indexOf(ts)
            buildAnnotatedString {
                append(ts)
                if (tsStartInRaw != -1) {
                    matchesForThisLog.forEach { match ->
                        val startInTs = match.startCharInRaw - tsStartInRaw
                        val endInTs = startInTs + match.length
                        if (startInTs >= 0 && endInTs <= ts.length) {
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

    val cardBg = when {
        isActiveLogEntry -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.45f else 0.35f)
        logEntry.level == LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.22f else 0.15f)
        logEntry.level == LogLevel.WARN -> (if (isDark) Color(0xFF4E2A00) else Color(0xFFFFF3E0)).copy(
            alpha = if (isDark) 0.3f else 0.5f
        )

        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.3f)
    }

    val border = when {
        isActiveLogEntry -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
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
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 级别药丸徽标
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
                            modifier = Modifier.weight(1f, fill = false)
                        )
                    }

                    Spacer(modifier = Modifier.weight(1f))

                    if (logEntry.timestamp.isNotBlank()) {
                        Text(
                            text = annotatedTimestamp,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(5.dp))

                // 日志消息主体
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

// ==================== 文件读写辅助函数保持不变 ====================
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
