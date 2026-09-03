package github.zerorooot.nap511.screen

import android.app.Application
import android.content.ClipData
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

// ==================== 日志级别枚举与颜色设置 ====================
enum class LogLevel(
    val code: String,
    val color: Color,
    val bgColor: Color
) {
    VERBOSE("V", Color(0xFF9E9E9E), Color(0x1F9E9E9E)),
    DEBUG("D", Color(0xFF0288D1), Color(0x1F0288D1)),
    INFO("I", Color(0xFF388E3C), Color(0x1F388E3C)),
    WARN("W", Color(0xFFF57C00), Color(0x1FF57C00)),
    ERROR("E", Color(0xFFD32F2F), Color(0x1FD32F2F)),
    UNKNOWN("?", Color(0xFF757575), Color(0x1F757575));

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
                    //  val timeOnly = time.substringAfter(" ")
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

    // 计算所有匹配项的位置列表
    val searchMatches = remember(parsedLogs, searchQuery) {
        if (searchQuery.isBlank()) emptyList()
        else {
            val list = mutableListOf<LogSearchMatch>()
            var globalIdx = 0
            parsedLogs.forEachIndexed { lIdx, logEntry ->
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

    val appBarOnClick: (String) -> Unit = { name ->
        when (name) {
            "搜索" -> {
                isSearchOpen = true
            }

            "滚动顶部" -> {
                coroutine.launch {
                    if (parsedLogs.isNotEmpty()) lazyListState.animateScrollToItem(0)
                }
            }

            "滚动底部" -> {
                coroutine.launch {
                    if (parsedLogs.isNotEmpty()) lazyListState.animateScrollToItem(parsedLogs.lastIndex)
                }
            }

            "清空日志" -> {
                File(App.instance.cacheDir, "log").delete()
                rawLogText = ""
            }

            "导出日志" -> {
                writeToPublicExternalStorage(
                    App.instance,
                    "${App.instance.getStringRes(R.string.app_name)}_${
                        LocalDateTime.now().format(formatter)
                    }_log.txt",
                    rawLogText,
                    coroutine
                )
            }

            "刷新日志" -> {
                rawLogText = readLog()
                coroutine.launch {
                    if (parsedLogs.isNotEmpty()) lazyListState.animateScrollToItem(parsedLogs.lastIndex)
                }
            }

            "ModalNavigationDrawerMenu" -> onClick.invoke()
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

        if (parsedLogs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "暂无日志",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
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
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(items = parsedLogs, key = { _, item ->
                        item.uuid
                    }) { index, item ->
                        LogItemRow(
                            logEntry = item,
                            logIndex = index,
                            searchQuery = searchQuery,
                            searchMatches = searchMatches,
                            currentMatchIndex = currentMatchIndex
                        ) {
                            clipboardManager.nativeClipboardManager.setPrimaryClip(
                                ClipData.newPlainText(
                                    "logs",
                                    it.message
                                )
                            )
                            App.instance.toast("日志已复制到剪切板")
                        }
                    }
                }
            }
        }
    }

    // 首次进入自动滚动至底部（仅在非搜索模式下）
    LaunchedEffect(parsedLogs.size) {
        if (parsedLogs.isNotEmpty() && !isSearchOpen) {
            lazyListState.scrollToItem(parsedLogs.lastIndex)
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
    onLongClick: (LogEntry) -> Unit
) {
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

    Surface(
        shape = RoundedCornerShape(6.dp),
        color = if (isActiveLogEntry) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        },
        border = if (isActiveLogEntry) {
            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
        } else null,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = {},
                onLongClick = { onLongClick.invoke(logEntry) }
            )
    ) {
        Column(modifier = Modifier.padding(6.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 日志级别 Tag Badge
                Box(
                    modifier = Modifier
                        .background(
                            color = logEntry.level.bgColor,
                            shape = RoundedCornerShape(4.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = logEntry.level.code,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = logEntry.level.color
                    )
                }
                Column(
                    modifier = Modifier.padding(start = 8.dp),
                    verticalArrangement = Arrangement.spacedBy((-8).dp) // 通过负间距拉近两个 Text 的距离
                ) {
                    Text(
                        text = logEntry.tag,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = annotatedTimestamp,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }
            }
            // 日志消息主体
            Text(
                text = annotatedMessage,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 17.sp,
                color = if (logEntry.level == LogLevel.ERROR) {
                    LogLevel.ERROR.color
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
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
                    XLog.d("FileWrite File written to Downloads: $uri")
                }
            }
        } else {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                fileName
            )
            try {
                file.writeText(content)
                XLog.d("FileWrite File written to: ${file.absolutePath}")
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
