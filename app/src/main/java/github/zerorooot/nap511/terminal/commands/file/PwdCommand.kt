package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitPath
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 打印当前工作目录路径命令（pwd）
 *
 * 输出当前工作目录的纯净绝对路径，遵循 Unix 标准，保证管道与参数传递的无缝互操作性。
 */
class PwdCommand : TerminalCommand {

    override val name: String = "pwd"

    override val description: String = "打印当前工作目录绝对路径"

    override val usage: String = "pwd"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        emitPath(ctx.currentPath)
    }
}
