package github.zerorooot.nap511.screen

import android.app.Application
import android.content.ClipData
import android.content.ContentValues
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
import github.zerorooot.nap511.util.App
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

// ============================================================================
// 三、日志文本解析器 (LogParser)
// ============================================================================

/**
 * XLog 日志解析单例
 */
object LogParser {
    private val xlogPattern =
        Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}(?:\.\d{1,3})?)\s+([VDIWEFA])/([^:]+):\s*(.*)$""")

    /** 将原始多行日志文本解析为结构化实体列表 */
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
// 四、通用底层数据与文件管理 (LogFileManager)
// ============================================================================

/**
 * 日志文件管理与 I/O 门面对象
 *
 * 封装日志文件读取、变动检测、清空以及外部存储导出逻辑，与 UI 层解耦。
 */
object LogFileManager {
    /** 获取本地日志文件对象 */
    fun getLogFile(): File = File(App.instance.cacheDir, "log")

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

    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            LogDetailContent(
                logEntry = selectedLog
            )
        },
        confirmButton = {
            TextButton(
                onClick = { copyLogToClipboard(clipboardManager, selectedLog) }
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
    val clipboardManager = LocalClipboard.current

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
        if (searchQuery.isBlank()) emptyList() else searchMatches.filter { it.logIndex == logIndex }
    }

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
            if (logEntry.timestamp.isEmpty()) 0 else logEntry.raw.lastIndexOf(logEntry.message)
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

                // 日志消息正文
                Text(
                    text = annotatedMessage,
                    fontSize = 12.5.sp,
                    fontFamily = FontFamily.Monospace,
                    lineHeight = 17.sp,
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
// 九、页面主入口 (LogScreen)
// ============================================================================

/**
 * 日志查看页面主入口
 *
 * @param isDualPane 是否处于大屏双栏展示模式
 * @param onClick 抽屉菜单开启等外部点击回调
 */
@OptIn(ExperimentalMaterial3AdaptiveApi::class)
@Composable
fun LogScreen(isDualPane: Boolean = false, onClick: () -> Unit) {
    var rawLogText by remember { mutableStateOf(LogFileManager.readLogText()) }
    val parsedLogs by remember(rawLogText) { derivedStateOf { LogParser.parse(rawLogText) } }
    val clipboardManager = LocalClipboard.current

    val lazyListState = rememberLazyListState()
    val coroutine = rememberCoroutineScope()
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss")

    // 级别筛选状态与派生数据
    var selectedLevelFilter by remember { mutableStateOf<LogLevel?>(null) }
    val filteredLogs by remember(parsedLogs, selectedLevelFilter) {
        derivedStateOf {
            if (selectedLevelFilter == null) parsedLogs
            else parsedLogs.filter { it.level == selectedLevelFilter }
        }
    }
    val levelCounts = remember(parsedLogs) {
        parsedLogs.groupingBy { it.level }.eachCount()
    }

    var selectedLogForDetail by remember { mutableStateOf<LogEntry?>(null) }
    var isAutoScrollEnabled by remember { mutableStateOf(true) }

    // 协程轮询检测日志文件变动
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val logFile = LogFileManager.getLogFile()
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
                        val updatedContent = LogFileManager.readLogText()
                        withContext(Dispatchers.Main) {
                            rawLogText = updatedContent
                        }
                    }
                }
            }
        }
    }

    // 搜索状态管理
    var isSearchOpen by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var currentMatchIndex by remember { mutableIntStateOf(0) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(isSearchOpen) {
        if (isSearchOpen) {
            focusRequester.requestFocus()
        }
    }

    val searchMatches = remember(filteredLogs, searchQuery) {
        findSearchMatches(filteredLogs, searchQuery) { it.raw }
    }

    LaunchedEffect(searchMatches) {
        currentMatchIndex = 0
    }

    LaunchedEffect(currentMatchIndex, searchMatches) {
        if (searchMatches.isNotEmpty() && currentMatchIndex in searchMatches.indices) {
            val targetMatch = searchMatches[currentMatchIndex]
            lazyListState.animateScrollToItem(targetMatch.logIndex)
        }
    }

    // 顶部操作栏动作回调
    val appBarOnClick: (AppBarAction) -> Unit = { name ->
        when (name) {
            TopBarAction.SEARCH -> isSearchOpen = true
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
                LogFileManager.clearLogFile()
                rawLogText = ""
                selectedLogForDetail = null
            }

            MenuItemAction.EXPORT_LOG -> {
                LogFileManager.exportLogFile(
                    App.instance,
                    "${App.instance.getStringRes(R.string.app_name)}_${
                        LocalDateTime.now().format(formatter)
                    }_log.txt",
                    rawLogText,
                    coroutine
                )
            }

            MenuItemAction.REFRESH_LOG -> {
                rawLogText = LogFileManager.readLogText()
                isAutoScrollEnabled = true
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

        if (parsedLogs.isNotEmpty()) {
            LogFilterChipBar(
                totalCount = parsedLogs.size,
                levelCounts = levelCounts,
                selectedLevel = selectedLevelFilter,
                onSelectLevel = { selectedLevelFilter = it }
            )
        }

        // 统一的高内聚列表内容闭包，避免在双栏/单栏分支中重复编写
        val listContent: @Composable (Modifier) -> Unit = { modifier ->
            Box(modifier = modifier) {
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
                    selectedLog = selectedLogForDetail,
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

            selectedLogForDetail?.let { detail ->
                LogDetailDialog(
                    selectedLog = detail,
                    onDismiss = { selectedLogForDetail = null }
                )
            }
        }
    }
}

// ============================================================================
// 十、通用辅助工具函数（向后兼容导出）
// ============================================================================

/** 将单条结构化日志格式化并复制到系统剪切板 */
private fun copyLogToClipboard(clipboardManager: Clipboard, logEntry: LogEntry) {
    val text = listOf(logEntry.tag, logEntry.timestamp, logEntry.message)
        .filter { it.isNotBlank() }
        .joinToString("\n")
    clipboardManager.nativeClipboardManager.setPrimaryClip(
        ClipData.newPlainText("logs", text)
    )
    App.instance.toast("日志已复制到剪切板")
}
