package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 打印当前工作目录路径命令（pwd）
 *
 * 输出当前工作目录的规范路径及其在 115 网盘系统中的真实分类目录 ID（CID）。
 */
class PwdCommand : TerminalCommand {

    override val name: String = "pwd"

    override val description: String = "打印当前工作目录路径及 CID"

    override val usage: String = "pwd"

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        emitText("${ctx.currentPath} (cid: ${ctx.currentCid})")
    }
}
