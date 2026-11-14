package github.zerorooot.nap511.screen

import android.app.Application
import android.content.ClipData
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.lazy.LazyListState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.adaptive.ExperimentalMaterial3AdaptiveApi
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import github.zerorooot.nap511.viewmodel.LogViewModel
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
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.nativeClipboardManager
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
import github.zerorooot.nap511.viewmodel.platform.AndroidToastProvider
import github.zerorooot.nap511.viewmodel.platform.LocalToastProvider
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.LogSearchMatch
import github.zerorooot.nap511.util.buildSearchHighlightedText
import github.zerorooot.nap511.util.findSearchMatches
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
// 一、日志级别枚举与视觉样式扩展
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
        /** 根据单字符代码安全解析为对应的 [LogLevel]，未匹配时回退为 [UNKNOWN] */
        fun fromCode(code: String): LogLevel {
            return entries.find { it.code.equals(code, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

/** 对应级别的左边缘 Accent 强调指示条颜色 */
val LogLevel.accentColor: Color
    get() = when (this) {
        LogLevel.ERROR -> Color(0xFFE53935)
        LogLevel.WARN -> Color(0xFFFFA726)
        LogLevel.INFO -> Color(0xFF4CAF50)
        LogLevel.DEBUG -> Color(0xFF03A9F4)
        LogLevel.VERBOSE -> Color(0xFF9E9E9E)
        LogLevel.UNKNOWN -> Color(0xFF757575)
    }

/** 根据深浅色模式与选中状态，获取日志卡片背景容器颜色 */
@Composable
fun LogLevel.getCardContainerColor(isSearchActive: Boolean, isPaneSelected: Boolean): Color {
    val isDark = isSystemInDarkTheme()
    return when {
        isSearchActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.45f else 0.35f)
        isPaneSelected -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = if (isDark) 0.35f else 0.25f)
        this == LogLevel.ERROR -> MaterialTheme.colorScheme.errorContainer.copy(alpha = if (isDark) 0.22f else 0.15f)
        this == LogLevel.WARN -> (if (isDark) Color(0xFF4E2A00) else Color(0xFFFFF3E0)).copy(alpha = if (isDark) 0.3f else 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = if (isDark) 0.35f else 0.3f)
    }
}

/** 根据状态获取日志卡片边框样式 */
@Composable
fun LogLevel.getCardBorder(isSearchActive: Boolean, isPaneSelected: Boolean): BorderStroke {
    return when {
        isSearchActive -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        isPaneSelected -> BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.8f))
        this == LogLevel.ERROR -> BorderStroke(
            0.8.dp,
            MaterialTheme.colorScheme.error.copy(alpha = 0.4f)
        )

        this == LogLevel.WARN -> BorderStroke(0.8.dp, Color(0xFFFFA726).copy(alpha = 0.4f))
        else -> BorderStroke(0.5.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
 * 日志模块不可变 UI 状态载体 (LogUiState)
 *
 * @property logs 全量解析后的结构化日志条目
 * @property filteredLogs 经过级别过滤后的展示日志条目
 * @property levelCounts 各级别的统计数量映射
 * @property selectedLevelFilter 当前选中的日志级别过滤条件，为 null 表示全部
 * @property selectedLogForDetail 详情面板或弹窗当前选中的日志条目
 * @property isAutoScrollEnabled 是否开启自动滚动到底部
 * @property isSearchOpen 是否展开搜索顶栏
 * @property searchQuery 当前搜索关键字
 * @property searchMatches 搜索匹配项集合
 * @property currentMatchIndex 当前选中的匹配项索引
 * @property isLoading 是否正在异步加载/刷新
 */
@Immutable
data class LogUiState(
    val logs: List<LogEntry> = emptyList(),
    val filteredLogs: List<LogEntry> = emptyList(),
    val levelCounts: Map<LogLevel, Int> = emptyMap(),
    val selectedLevelFilter: LogLevel? = null,
    val selectedLogForDetail: LogEntry? = null,
    val isAutoScrollEnabled: Boolean = true,
    val isSearchOpen: Boolean = false,
    val searchQuery: String = "",
    val searchMatches: List<LogSearchMatch> = emptyList(),
    val currentMatchIndex: Int = 0,
    val isLoading: Boolean = false
)

/**
 * 日志页面纯 UI 交互动作集合 (LogScreenActions)
 *
 * 聚合所有用户操作事件，避免 Composable 函数参数过度膨胀。
 */
@Immutable
data class LogScreenActions(
    val onSelectLevel: (LogLevel?) -> Unit,
    val onSelectLog: (LogEntry) -> Unit,
    val onCopyLog: (LogEntry) -> Unit,
    val onClearFilter: () -> Unit,
    val onSearchToggle: (Boolean) -> Unit,
    val onSearchQueryChange: (String) -> Unit,
    val onPrevMatch: () -> Unit,
    val onNextMatch: () -> Unit,
    val onNavigateMatch: (Int) -> Unit = {},
    val onAutoScrollChange: (Boolean) -> Unit,
    val onScrollToTop: () -> Unit,
    val onScrollToBottom: () -> Unit,
    val onExportLog: () -> Unit,
    val onClearLog: () -> Unit,
    val onRefreshLog: () -> Unit,
    val onDismissDetail: () -> Unit,
    val onDrawerClick: () -> Unit
)

// ============================================================================
// 三、日志文本解析器 (LogParser)
// ============================================================================

/**
 * XLog 日志解析单例
 */
object LogParser {
    private val xlogPattern =
        Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+([VDIWEFA])/([^:]+):\s*(.*)$""")

    /**
     * 将原始多行日志文本解析为结构化实体列表
     *
     * 【多行日志聚合支持】：
     * 当日志内容中包含换行符（如通过 XLog.e("msg", error) 打印的异常堆栈信息）时，
     * 异常堆栈的每一行（如 at com.xxx... 或 Caused by:...）并非新日志的起始行。
     * 本解析器采用状态机累加方式：遇到符合 xlogPattern 的行作为新日志条目的 Header；
     * 后续所有不符合 Header 的换行行均视为当前日志的内容延续（Body），自动拼接进上一条日志中。
     * 从而避免将一次完整的异常拆分成数十个无 Tag、无时间、级别为 UNKNOWN 的碎卡片。
     */
    fun parse(rawLog: String): List<LogEntry> {
        if (rawLog.isBlank()) return emptyList()

        val entries = mutableListOf<LogEntry>()
        var currentEntry: LogEntry? = null
        val rawBuilder = StringBuilder()
        val msgBuilder = StringBuilder()

        for (line in rawLog.lineSequence()) {
            if (line.isBlank()) continue
            val trimmed = line.trim()
            val xlogMatch = xlogPattern.find(trimmed)

            if (xlogMatch != null) {
                // 1. 遇到新日志起始行：先将上一条已累加完整的日志归档保存
                if (currentEntry != null) {
                    entries.add(
                        currentEntry.copy(
                            raw = rawBuilder.toString(),
                            message = msgBuilder.toString()
                        )
                    )
                    rawBuilder.setLength(0)
                    msgBuilder.setLength(0)
                }

                // 2. 初始化新条目并记录首行内容
                val (time, levelStr, tagStr, msg) = xlogMatch.destructured
                rawBuilder.append(line)
                msgBuilder.append(msg)
                currentEntry = LogEntry(
                    raw = "",
                    timestamp = time,
                    tag = tagStr.replace("-XLOG", ""),
                    level = LogLevel.fromCode(levelStr),
                    message = ""
                )
            } else {
                // 3. 非标准起始行：属于上一条日志的后续换行内容（如异常堆栈 at xxx、Caused by 等）
                if (currentEntry != null) {
                    rawBuilder.append("\n").append(line)
                    msgBuilder.append("\n").append(line)
                } else {
                    // 文件开头若存在未匹配到格式的孤立非标准行，作为兜底独立条目
                    entries.add(LogEntry(raw = line, message = line))
                }
            }
        }

        // 4. 循环结束后，保存最后一条处于构建中的日志条目
        if (currentEntry != null) {
            entries.add(
                currentEntry.copy(
                    raw = rawBuilder.toString(),
                    message = msgBuilder.toString()
                )
            )
        }

        return entries
    }
}

// ============================================================================
// 四、通用底层数据与文件管理 (LogFileManager)
// ============================================================================

/**
 * 日志文件管理与 I/O 门面对象
 *
 * 封装日志文件读取、变动检测、清空以及外部存储导出逻辑，与 UI 层解耦。
 */
class LogFileManager(
    context: Context,
    private val toastProvider: ToastProvider = AndroidToastProvider(context)
) {
    // 统一使用 applicationContext，防止外部误传 Activity Context 导致内存泄漏
    private val appContext: Context = context.applicationContext ?: context
    /** 获取本地日志文件对象 */
    fun getLogFile(): File = File(appContext.cacheDir, "log")

    /** 读取本地日志全量文本 */
    fun readLogText(): String {
        val file = getLogFile()
        if (!file.exists() || file.length() == 0L) return ""
        return try {
            readInputStreamAsString(FileInputStream(file))
        } catch (_: Exception) {
            ""
        }
    }

    /** 清空日志文件 */
    fun clearLogFile(): Boolean {
        return try {
            getLogFile().delete()
        } catch (_: Exception) {
            false
        }
    }

    /** 将日志文本导出至系统 Downloads 目录 */
    fun exportLogFile(
        fileName: String,
        content: String,
        coroutine: CoroutineScope
    ) {
        coroutine.launch(Dispatchers.IO) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = appContext.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Downloads.DISPLAY_NAME, fileName)
                    put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                    put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                }
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                uri?.let {
                    resolver.openOutputStream(it)?.use { outputStream ->
                        outputStream.write(content.toByteArray())
                        toastProvider.showToast("日志文件已保存至Downloads目录，文件名为：$fileName", true)
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

    /** 聚合输入流为文本 */
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
}

// ============================================================================
// 五、可复用的 UI 基础组件 (Badge, DetailContent, Dialog)
// ============================================================================

/**
 * 统一日志级别药丸徽标组件
 *
 * @param level 级别枚举
 * @param useFullName 是否展示全拼（如 "Debug"），默认为 false 仅展示单字符（如 "D"）
 */
@Composable
fun LogLevelBadge(
    level: LogLevel,
    modifier: Modifier = Modifier,
    useFullName: Boolean = false
) {
    Box(
        modifier = modifier
            .background(
                color = level.bgColor,
                shape = RoundedCornerShape(4.dp)
            )
            .padding(
                horizontal = if (useFullName) 6.dp else 5.dp,
                vertical = if (useFullName) 2.dp else 1.dp
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (useFullName) level.label else level.code,
            fontSize = if (useFullName) 11.sp else 10.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = level.color
        )
    }
}

/**
 * 日志详情核心内容展示组件 (LogDetailContent)
 *
 * 大屏独立详情面板 (LogDetailPane) 与手机端弹窗 (LogDetailDialog) 100% 复用的视图模板。
 */
@Composable
fun LogDetailContent(
    logEntry: LogEntry,
    modifier: Modifier = Modifier
) {
    val isDark = isSystemInDarkTheme()

    Column(modifier = modifier.fillMaxWidth()) {
        // 头部行：级别徽标 + Tag + 快捷复制按钮
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LogLevelBadge(level = logEntry.level, useFullName = true)

            Spacer(modifier = Modifier.width(8.dp))

            Text(
                text = logEntry.tag.ifBlank { "系统日志" },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
//            IconButton(onClick = onCopy) {
//                Icon(
//                    imageVector = Icons.Default.ContentCopy,
//                    contentDescription = "复制日志",
//                    tint = MaterialTheme.colorScheme.onSurfaceVariant
//                )
//            }
        }

        // 完整时间戳展示
        if (logEntry.timestamp.isNotBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "记录时间: ${logEntry.timestamp}",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        Spacer(modifier = Modifier.height(10.dp))

        // 日志正文与堆栈代码块（支持手势长按拖动自由划选局部文本）
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
                .weight(1f, fill = false)
        ) {
            SelectionContainer {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(12.dp)
                ) {
                    Text(
                        text = logEntry.message,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.5.sp,
                        lineHeight = 18.sp,
                        color = if (logEntry.level == LogLevel.ERROR) logEntry.level.color
                        else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

/**
 * 手机竖屏模式下的日志详情弹窗
 */
@Composable
fun LogDetailDialog(
    selectedLog: LogEntry,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboard.current
    val toastProvider = LocalToastProvider.current

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            LogDetailContent(
                logEntry = selectedLog
            )
        },
        confirmButton = {
            TextButton(
                onClick = { copyLogToClipboard(clipboardManager, selectedLog, toastProvider) }
            ) {
                Text("复制全部")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        }
    )
}

/**
 * 大屏双栏常驻详情面板
 */
@Composable
fun LogDetailPane(
    selectedLog: LogEntry?,
    modifier: Modifier = Modifier
) {
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
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            LogDetailContent(
                logEntry = selectedLog,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

// ============================================================================
// 六、单条日志卡片渲染 (LogItemRow)
// ============================================================================

/**
 * 单条日志卡片渲染组件
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
    val matchesForThisLog = remember(searchMatches, logIndex) {
        if (searchQuery.isBlank()) emptyList() else searchMatches.filter { it.itemIndex == logIndex }
    }

    val isActiveLogEntry = remember(searchMatches, currentMatchIndex, logIndex) {
        if (searchQuery.isBlank()) false else searchMatches.getOrNull(currentMatchIndex)?.itemIndex == logIndex
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

    // 搜索高亮：时间戳
    val annotatedTimestamp = remember(
        displayTimestamp,
        logEntry.raw,
        searchQuery,
        matchesForThisLog,
        currentMatchIndex
    ) {
        val tsStartInRaw = logEntry.raw.indexOf(displayTimestamp)
        buildSearchHighlightedText(
            text = displayTimestamp,
            searchQuery = searchQuery,
            matches = matchesForThisLog,
            textStartInRaw = tsStartInRaw,
            currentMatchIndex = currentMatchIndex
        )
    }

    // 搜索高亮：消息主体
    val annotatedMessage = remember(
        logEntry.message,
        logEntry.raw,
        searchQuery,
        matchesForThisLog,
        currentMatchIndex
    ) {
        val msgStartInRaw =
            if (logEntry.timestamp.isEmpty()) 0 else logEntry.raw.lastIndexOf(logEntry.message).coerceAtLeast(0)
        buildSearchHighlightedText(
            text = logEntry.message,
            searchQuery = searchQuery,
            matches = matchesForThisLog,
            textStartInRaw = msgStartInRaw,
            currentMatchIndex = currentMatchIndex
        )
    }

    val cardBg = logEntry.level.getCardContainerColor(isActiveLogEntry, isSelectedInDualPane)
    val border = logEntry.level.getCardBorder(isActiveLogEntry, isSelectedInDualPane)
    val accentColor = logEntry.level.accentColor

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
                // Header 行：Badge + Tag + Timestamp
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    LogLevelBadge(level = logEntry.level)

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
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    Spacer(modifier = Modifier.width(8.dp))

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

                // 日志消息正文（列表项最多展示 4 行，超长时显示省略号以保持卡片紧凑整洁；点击卡片可查看完整堆栈与调用详情）
                Text(
                    text = annotatedMessage,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    color = if (logEntry.level == LogLevel.ERROR) logEntry.level.color
                    else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

// ============================================================================
// 七、空状态与过滤条组件
// ============================================================================

/**
 * 顶部水平可滚动的日志级别筛选 Chip 条
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
        LogLevel.ERROR,
        LogLevel.WARN,
        LogLevel.INFO,
        LogLevel.DEBUG,
        LogLevel.VERBOSE
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

/**
 * 质感空状态引导视图
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
// 八、日志列表视图组件 (LogListView)
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

    // 悬浮“回到底部”小按钮
    AnimatedVisibility(
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
// 九、无状态 UI 组件 (LogScreenContent) 与页面主入口 (LogScreen)
// ============================================================================

/**
 * 纯无状态日志中心内容组件 (LogScreenContent)
 *
 * 100% 剥离 ViewModel 与本地文件 I/O 依赖，支持 Android Studio @Preview 与纯 UI 自动化测试。
 *
 * @param uiState 当前日志展示与过滤状态
 * @param actions 界面交互与事件回调集合
 * @param isDualPane 是否处于大屏双栏展示模式
 * @param lazyListState 日志列表滚动状态，默认自动记忆
 * @param modifier 外部修饰符
 */
@Composable
fun LogScreenContent(
    uiState: LogUiState,
    actions: LogScreenActions,
    modifier: Modifier = Modifier,
    isDualPane: Boolean = false,
    lazyListState: LazyListState = rememberLazyListState(),
) {
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(uiState.isSearchOpen) {
        if (uiState.isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    LaunchedEffect(uiState.currentMatchIndex, uiState.searchMatches) {
        if (uiState.searchMatches.isNotEmpty() && uiState.currentMatchIndex in uiState.searchMatches.indices) {
            val targetMatch = uiState.searchMatches[uiState.currentMatchIndex]
            lazyListState.animateScrollToItem(targetMatch.itemIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (uiState.isSearchOpen) {
            TopAppBarSearch(
                searchQuery = uiState.searchQuery,
                onSearchQueryChange = actions.onSearchQueryChange,
                onCloseSearch = { actions.onSearchToggle(false) },
                matchCount = uiState.searchMatches.size,
                currentMatchIndex = uiState.currentMatchIndex,
                onPrevMatch = actions.onPrevMatch,
                onNextMatch = actions.onNextMatch,
                placeholderText = "搜索日志...",
                focusRequester = focusRequester
            )
        } else {
            AppTopBarLogScreen(
                title = ConfigKeyUtil.LOG_SCREEN,
                onClick = { name ->
                    when (name) {
                        TopBarAction.SEARCH -> actions.onSearchToggle(true)
                        MenuItemAction.SCROLL_TOP -> actions.onScrollToTop()
                        MenuItemAction.SCROLL_BOTTOM -> actions.onScrollToBottom()
                        MenuItemAction.CLEAR_LOG -> actions.onClearLog()
                        MenuItemAction.EXPORT_LOG -> actions.onExportLog()
                        MenuItemAction.REFRESH_LOG -> actions.onRefreshLog()
                        TopBarAction.DRAWER_MENU -> actions.onDrawerClick()
                        else -> {}
                    }
                }
            )
        }

        if (uiState.logs.isNotEmpty()) {
            LogFilterChipBar(
                totalCount = uiState.logs.size,
                levelCounts = uiState.levelCounts,
                selectedLevel = uiState.selectedLevelFilter,
                onSelectLevel = actions.onSelectLevel
            )
        }

        // 统一的高内聚列表内容闭包，避免在双栏/单栏分支中重复编写
        val listContent: @Composable (Modifier) -> Unit = { listModifier ->
            Box(modifier = listModifier) {
                LogListView(
                    filteredLogs = uiState.filteredLogs,
                    selectedLevelFilter = uiState.selectedLevelFilter,
                    onClearFilter = actions.onClearFilter,
                    lazyListState = lazyListState,
                    searchQuery = uiState.searchQuery,
                    searchMatches = uiState.searchMatches,
                    currentMatchIndex = uiState.currentMatchIndex,
                    selectedLogId = uiState.selectedLogForDetail?.uuid,
                    isAutoScrollEnabled = uiState.isAutoScrollEnabled,
                    isSearchOpen = uiState.isSearchOpen,
                    onAutoScrollChange = actions.onAutoScrollChange,
                    onRowClick = actions.onSelectLog,
                    onLongClick = actions.onCopyLog,
                    onScrollToBottom = actions.onScrollToBottom
                )
            }
        }

        if (isDualPane) {
            // ==================== 大屏双栏模式 (List-Detail) ====================
            Row(modifier = Modifier.fillMaxSize()) {
                // 左侧栏：日志列表
                listContent(
                    Modifier
                        .weight(1.1f)
                        .fillMaxHeight()
                )

                VerticalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                    thickness = 1.dp
                )

                // 右侧栏：常驻详情面板
                LogDetailPane(
                    selectedLog = uiState.selectedLogForDetail,
                    modifier = Modifier
                        .weight(1.3f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                )
            }
        } else {
            // ==================== 手机竖屏单栏模式 ====================
            listContent(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            )

            uiState.selectedLogForDetail?.let { detail ->
                LogDetailDialog(
                    selectedLog = detail,
                    onDismiss = actions.onDismissDetail
                )
            }
        }
    }
}

/**
 * 日志查看页面主入口 (Stateful Container)
 *
 * 绑定 [LogViewModel]，负责日志文件监听轮询、后台解析、导出等生命周期与业务状态调度。
 *
 * @param isDualPane 是否处于大屏双栏展示模式
 * @param viewModel 日志业务 ViewModel，默认自动从 Compose 上下文获取
 * @param onClick 抽屉菜单开启等外部点击回调
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LogScreen(
    isDualPane: Boolean = false,
    viewModel: LogViewModel,
    onClick: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val clipboardManager = LocalClipboard.current
    val toastProvider = LocalToastProvider.current
    val lazyListState = rememberLazyListState()
    val coroutine = rememberCoroutineScope()

    val actions = remember(viewModel, onClick, toastProvider) {
        LogScreenActions(
            onSelectLevel = { viewModel.selectLevel(it) },
            onSelectLog = { viewModel.selectLogForDetail(it) },
            onCopyLog = { copyLogToClipboard(clipboardManager, it, toastProvider) },
            onClearFilter = { viewModel.selectLevel(null) },
            onSearchToggle = { viewModel.toggleSearch(it) },
            onSearchQueryChange = { viewModel.setSearchQuery(it) },
            onPrevMatch = { viewModel.prevMatch() },
            onNextMatch = { viewModel.nextMatch() },
            onNavigateMatch = { viewModel.navigateMatch(it) },
            onAutoScrollChange = { viewModel.setAutoScrollEnabled(it) },
            onScrollToTop = {
                coroutine.launch {
                    if (uiState.filteredLogs.isNotEmpty()) lazyListState.animateScrollToItem(0)
                }
            },
            onScrollToBottom = {
                viewModel.setAutoScrollEnabled(true)
                coroutine.launch {
                    if (uiState.filteredLogs.isNotEmpty()) {
                        lazyListState.animateScrollToItem(uiState.filteredLogs.lastIndex)
                    }
                }
            },
            onExportLog = { viewModel.exportLog() },
            onClearLog = { viewModel.clearLog() },
            onRefreshLog = {
                viewModel.refreshLogs()
                viewModel.setAutoScrollEnabled(true)
                coroutine.launch {
                    if (uiState.filteredLogs.isNotEmpty()) {
                        lazyListState.animateScrollToItem(uiState.filteredLogs.lastIndex)
                    }
                }
            },
            onDismissDetail = { viewModel.selectLogForDetail(null) },
            onDrawerClick = onClick
        )
    }

    LogScreenContent(
        uiState = uiState,
        actions = actions,
        isDualPane = isDualPane,
        lazyListState = lazyListState
    )
}

// ============================================================================
// 十、通用辅助工具函数（向后兼容导出）
// ============================================================================

/** 将单条结构化日志格式化并复制到系统剪切板 */
private fun copyLogToClipboard(clipboardManager: Clipboard, logEntry: LogEntry, toastProvider: ToastProvider) {
    val text = listOf(logEntry.tag, logEntry.timestamp, logEntry.message)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    clipboardManager.nativeClipboardManager.setPrimaryClip(
        ClipData.newPlainText("logs", text)
    )
    toastProvider.showToast("日志已复制到剪切板")
}
