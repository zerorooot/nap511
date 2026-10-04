package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.engine.CommandRegistry
import kotlinx.coroutines.flow.flow

/**
 * 终端命令注册工厂
 */
object CommandRegistryFactory {

    fun createDefaultRegistry(historyProvider: () -> List<String>): CommandRegistry {
        val registry = CommandRegistry()

        // 注册流式工具命令
        StreamCommands.registerAll(registry)

        // 注册网盘基础文件命令
        FileCommands.registerAll(registry)

        // 注册网盘特色命令
        CloudCommands.registerAll(registry)

        // 注册 history 命令
        registry.register("history") {
            description = "查看命令历史记录"
            usage = "history"
            execute { _, _, _ ->
                flow {
                    val history = historyProvider()
                    history.forEachIndexed { index, cmd ->
                        emit("${(index + 1).toString().padStart(4)}  $cmd")
                    }
                }
            }
        }

        return registry
    }
}
