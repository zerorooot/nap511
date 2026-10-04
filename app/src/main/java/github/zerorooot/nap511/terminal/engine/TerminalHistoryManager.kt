package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.util.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import java.nio.charset.StandardCharsets

/**
 * 终端历史记录持久化与流式加载管理器
 *
 * 遵循低耦合高内聚设计，负责将用户正确输入的命令以 Linux 风格持久化到文本文件中，
 * 并提供流式加载、按需截取和历史清理功能。
 *
 * @param historyFile 持久化存储的目标文件，默认为 App filesDir 下的 terminal_history.txt
 */
class TerminalHistoryManager(
    val historyFile: File = File(
        runCatching { App.instance.filesDir }.getOrNull() ?: File("."),
        "terminal_history.txt"
    )
) {
    // 内存中记录最后一条成功追加的命令，用于高效 O(1) 的连续去重判定 (ignoredups)
    private var lastRecordedCommand: String? = null

    init {
        initLastRecordedCommand()
    }

    private fun initLastRecordedCommand() {
        if (historyFile.exists() && historyFile.length() > 0) {
            runCatching {
                historyFile.useLines { lines ->
                    lastRecordedCommand = lines.lastOrNull()
                }
            }
        }
    }

    /**
     * 校验命令是否为合法有效命令（纯函数，无副作用）
     *
     * 判定标准：
     * 1. 语法合法（经由 Lexer.parsePipeline 解析成功且阶段不为空）
     * 2. 管道中涉及的所有命令名均需属于已在系统注册的命令
     *
     * @param rawInput 用户输入的原始命令行
     * @param isRegistered 判断指定命令名是否已在系统中注册的回调函数
     */
    fun isValidCommand(rawInput: String, isRegistered: (String) -> Boolean): Boolean {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) return false

        val stages = runCatching { Lexer.parsePipeline(trimmed) }.getOrNull() ?: return false
        if (stages.isEmpty()) return false

        // 校验管道中的所有命令阶段名称是否均已注册
        return stages.all { isRegistered(it.command) }
    }

    /**
     * 向持久化文件中追加记录一条命令 (O(1) 追加模式)
     *
     * 特性：
     * 1. 忽略与上一条连续重复的命令 (ignoredups)
     * 2. 线程安全地写入 UTF-8 文件
     *
     * @return Boolean 是否成功写入（若与上一条重复则返回 false）
     */
    @Synchronized
    fun appendCommand(command: String): Boolean {
        val trimmed = command.trim()
        if (trimmed.isEmpty()) return false

        if (trimmed == lastRecordedCommand) {
            return false // 忽略连续重复输入
        }

        return runCatching {
            val parent = historyFile.parentFile
            if (parent != null && !parent.exists()) {
                parent.mkdirs()
            }
            if (!historyFile.exists()) {
                historyFile.createNewFile()
            }
            OutputStreamWriter(FileOutputStream(historyFile, true), StandardCharsets.UTF_8).use { writer ->
                writer.write(trimmed)
                writer.write("\n")
            }
            lastRecordedCommand = trimmed
            true
        }.getOrDefault(false)
    }

    /**
     * 异步追加记录（切换至 IO 线程）
     */
    suspend fun appendCommandAsync(command: String): Boolean = withContext(Dispatchers.IO) {
        appendCommand(command)
    }

    /**
     * 流式逐行读取历史记录，按序输出包含行号的格式化行
     * 采用 lineSequence 实现真正 O(1) 内存占用流式发射
     *
     * @param limit 限制输出最近的 N 条记录，默认全量输出
     * @return Flow<String> 格式化后的输出行，例如 "   1  ls -l"
     */
    fun streamHistory(limit: Int = Int.MAX_VALUE): Flow<String> = flow {
        if (!historyFile.exists() || historyFile.length() == 0L) {
            return@flow
        }

        val totalCount = runCatching {
            historyFile.useLines { it.count() }
        }.getOrDefault(0)

        if (totalCount == 0) return@flow

        val skipCount = (totalCount - limit).coerceAtLeast(0)

        historyFile.useLines { lines ->
            lines.forEachIndexed { index, line ->
                if (index >= skipCount) {
                    val lineNum = (index + 1).toString().padStart(4)
                    emit("$lineNum  $line")
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * 读取最近的 N 条历史命令（供终端启动时预热内存漫游队列使用）
     *
     * @param limit 数量限制，默认最近 1000 条
     */
    fun loadRecentHistory(limit: Int = 1000): List<String> {
        if (!historyFile.exists() || historyFile.length() == 0L) {
            return emptyList()
        }

        return runCatching {
            val total = historyFile.useLines { it.count() }
            val skip = (total - limit).coerceAtLeast(0)
            historyFile.useLines { lines ->
                lines.drop(skip).toList()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * 清空全部持久化历史记录
     */
    @Synchronized
    fun clearHistory(): Boolean {
        lastRecordedCommand = null
        return runCatching {
            if (historyFile.exists()) {
                historyFile.delete()
            }
            true
        }.getOrDefault(false)
    }
}
