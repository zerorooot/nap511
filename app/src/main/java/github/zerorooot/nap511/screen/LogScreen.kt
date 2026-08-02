package github.zerorooot.nap511.screen

import android.app.Application
import android.content.ContentValues
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.R
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.LocalDrawerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.BufferedInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

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
    val pidTid: String = "",
    val tag: String = "",
    val level: LogLevel = LogLevel.UNKNOWN,
    val message: String = raw
)

// ==================== 解析器 ====================
object LogParser {
    // 匹配类似: 2026-08-02 13:31:24.732 27099-27134 XLOG github.zerorooot.nap511 D save file list cache 69
    private val logPattern =
        Regex("""^(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2}\.\d{3})\s+(\d+-\d+)\s+(\S+)\s+(\S+)\s+([VDIWE])\s+(.*)$""")

    fun parse(rawLog: String): List<LogEntry> {
        if (rawLog.isBlank()) return emptyList()
        return rawLog.lineSequence()
            .filter { it.isNotBlank() }
            .map { line ->
                val match = logPattern.find(line.trim())
                if (match != null) {
                    val (time, pidTid, _, pkg, levelStr, msg) = match.destructured
                    // 仅保留时间部分（如 13:31:24.732），精简视图空间
                    val timeOnly = time.substringAfter(" ")
                    LogEntry(
                        raw = line,
                        timestamp = timeOnly,
                        pidTid = pidTid,
                        tag = pkg.substringAfterLast('.'), // 可选：仅显示包名尾缀或全称
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
fun LogScreen() {
    var rawLogText by remember { mutableStateOf(readLog()) }
    val parsedLogs by remember(rawLogText) { derivedStateOf { LogParser.parse(rawLogText) } }

    val lazyListState = rememberLazyListState()
    val horizontalScrollState = rememberScrollState()
    val coroutine = rememberCoroutineScope()
    val drawerState = LocalDrawerState.current
    val formatter = DateTimeFormatter.ofPattern("yyyy/MM/dd/HH/mm/ss")

    val appBarOnClick = { name: String ->
        when (name) {
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

            "ModalNavigationDrawerMenu" -> coroutine.launch { drawerState.open() }
            else -> {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        AppTopBarLogScreen(ConfigKeyUtil.LOG_SCREEN, appBarOnClick as (String) -> Unit)

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
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(horizontalScrollState)
            ) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(parsedLogs) { item ->
                        LogItemRow(logEntry = item)
                    }
                }
            }
        }
    }

    // 首次进入自动滚动至底部
    LaunchedEffect(parsedLogs.size) {
        if (parsedLogs.isNotEmpty()) {
            lazyListState.scrollToItem(parsedLogs.lastIndex)
        }
    }
}

// ==================== 单条日志渲染组件 ====================
@Composable
fun LogItemRow(logEntry: LogEntry) {
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (logEntry.timestamp.isNotEmpty()) {
                // 时间戳
                Text(
                    text = logEntry.timestamp,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Spacer(modifier = Modifier.width(8.dp))

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

                Spacer(modifier = Modifier.width(8.dp))
            }

            // 日志消息主体
            Text(
                text = logEntry.message,
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