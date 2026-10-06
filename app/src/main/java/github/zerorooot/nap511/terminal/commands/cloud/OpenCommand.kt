package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import github.zerorooot.nap511.util.FileOpenResult
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 文件打开与目录跳转命令（open）
 *
 * 若目标为目录，直接跳转进入该网盘目录视图；
 * 若目标为文件，调用宿主注入的 FileOpener 根据文件类型自动分发调用对应的查看器（视频、图片、文本、PDF 等）。
 */
class OpenCommand : TerminalCommand {

    override val name: String = "open"

    override val description: String = "根据文件类型自动打开对应的查看器或预览页面"

    override val usage: String = "open <filename>"

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val fileName = ast.firstPositional
        if (fileName == null) {
            emitError("open: missing file operand")
            return@flow
        }

        val resolved = ctx.resolveTarget(fileName)
        if (resolved == null) {
            emitError("open: cannot find '$fileName': No such file or directory")
            return@flow
        }
        val fileOpener = ctx.fileOpener
        if (fileOpener == null) {
            emitError("open: 当前终端环境未配置文件打开器")
            return@flow
        }

        when (resolved) {
            is ResolvedTarget.Directory -> {
                emitText("已跳转至文件夹: $fileName")
                fileOpener.openFolder(resolved.cid)
                return@flow
            }

            is ResolvedTarget.File -> {
                val file = resolved.file
                emitText("正在准备打开: ${file.name}...")
                val siblings = ctx.listDirectory(resolved.parentCid)
                when (val result =
                    fileOpener.open(file, siblings, fromTerminal = true)) {
                    is FileOpenResult.Success -> {
                        emitText("open: ${result.message}")
                    }

                    is FileOpenResult.Failure -> {
                        emitError("open: ${result.message}")
                    }

                    is FileOpenResult.Unsupported -> {
                        emitError("open: 未能识别该文件的专用预览器 (${result.fileName})")
                    }
                }
            }
        }
    }
}
