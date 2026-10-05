package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 工作目录切换命令（cd）
 *
 * 切换网盘当前工作目录。支持绝对路径、相对路径、上级目录（..）及根目录（~ 或 /）。
 */
class CdCommand : TerminalCommand {

    override val name: String = "cd"

    override val description: String = "切换网盘当前工作目录"

    override val usage: String = "cd [path]"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        // 若缺省目标路径，默认切换至根目录 "/"
        val target = cmdArgs.firstPositional ?: "/"
        val resolved = ctx.resolvePath(target)
        if (resolved == null) {
            emit("cd: no such file or directory: $target")
            return@flow
        }
        ctx.updateDirectory(resolved.first, resolved.second)
    }
}
