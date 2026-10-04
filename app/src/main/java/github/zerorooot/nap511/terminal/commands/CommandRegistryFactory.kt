package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow

/**
 * 终端命令注册工厂
 */
object CommandRegistryFactory {

    /**
     * 创建默认命令注册表，接入流式持久化历史管理器
     *
     * @param historyManager 持久化历史管理器
     * @param onClearMemoryHistory 清空内存会话历史的回调函数
     */
    fun createDefaultRegistry(
        historyManager: TerminalHistoryManager,
        onClearMemoryHistory: () -> Unit = {}
    ): CommandRegistry {
        val registry = CommandRegistry()

        // 注册流式工具命令
        StreamCommands.registerAll(registry)

        // 注册网盘基础文件命令
        FileCommands.registerAll(registry)

        // 注册网盘特色命令
        CloudCommands.registerAll(registry)

        // 注册 history 命令 (支持流式输出、-c 清空与 <N> 最近记录截取)
        registry.register("history") {
            description = "查看命令历史记录"
            usage = "history [-c | <N>]"
            flag("-c", "清空持久化历史记录")
            execute { _, args, _ ->
                flow {
                    if (args.contains("-c")) {
                        historyManager.clearHistory()
                        onClearMemoryHistory()
                        emit("terminal: history cleared")
                        return@flow
                    }

                    // 检查是否指定了数量截取 <N>，例如: history 20
                    val limitArg = args.firstOrNull { it.toIntOrNull() != null }?.toIntOrNull()
                    val limit = if (limitArg != null && limitArg > 0) limitArg else Int.MAX_VALUE

                    emitAll(historyManager.streamHistory(limit))
                }
            }
        }

        return registry
    }

    /**
     * 兼容性构造方法
     */
    fun createDefaultRegistry(historyProvider: () -> List<String>): CommandRegistry {
        val tempFile = java.io.File.createTempFile("legacy_history", ".txt").apply {
            deleteOnExit()
            val list = runCatching { historyProvider() }.getOrDefault(emptyList())
            list.forEach { appendText("$it\n") }
        }
        return createDefaultRegistry(TerminalHistoryManager(tempFile))
    }
}
