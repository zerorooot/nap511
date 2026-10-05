package github.zerorooot.nap511.terminal.commands.system

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandRegistry
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 帮助信息展示命令（help，别名：?，man）
 *
 * 列出当前终端所有已注册的命令、简明说明及快捷键指南。
 *
 * @param registrySupplier 获取当前 CommandRegistry 的提供者函数，避免直接强依赖特定实例
 */
class HelpCommand(
    private val registrySupplier: () -> CommandRegistry
) : TerminalCommand {

    override val name: String = "help"

    override val description: String = "显示所有可用命令及简介"

    override val usage: String = "help"

    override val aliases: List<String> = listOf("?", "man")

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        // 调用注册表的 buildAllHelpMessage 汇总全部已注册命令及按键帮助
        emit(registrySupplier().buildAllHelpMessage())
    }
}
