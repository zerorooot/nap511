package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.context.TerminalPath
import github.zerorooot.nap511.terminal.context.TerminalPathConstants
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 文件及目录元数据信息查看命令（stat）
 *
 * 输出文件或目录的真实名称、类型（目录或文件）、字节大小、CID/FID、提取码（PickCode）、SHA-1 哈希值及创建与修改时间。
 */
class StatCommand : TerminalCommand {

    override val name: String = "stat"

    override val description: String = "查看文件或目录的详细元数据"

    override val usage: String = "stat <filename>"

    override suspend fun execute(
        ctx: TerminalContext,
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Flow<TerminalOutput> = flow {
        val targetName = ast.firstPositional
        if (targetName == null) {
            emitError("stat: missing operand")
            return@flow
        }

        val file = when (val resolved = ctx.resolveTarget(targetName)) {
            is ResolvedTarget.File -> resolved.file
            is ResolvedTarget.Directory -> {
                // 优先使用 resolveTarget 获取的完整 folderBean，若无则使用解析所得真实目录名，防止末尾斜杠导致截断为空
                resolved.folderBean ?: FileBean(
                    name = resolved.name.ifEmpty {
                        TerminalPath.parse(targetName).targetName.ifEmpty { TerminalPathConstants.ROOT_NAME }
                    },
                    categoryId = resolved.cid,
                    isFolder = true
                )
            }

            null -> {
                emitError("stat: cannot stat '$targetName': No such file or directory")
                return@flow
            }
        }

        emitText("  File: ${file.name}")
        emitText("  Type: ${if (file.isFolder) "Directory" else "Regular File"}")
        emitText("  Size: ${file.size} bytes (${file.sizeString.trim()})")
        emitText("  CID:  ${file.categoryId}  |  FID: ${file.fileId}")
        if (file.pickCode.isNotEmpty()) {
            emitText("  PickCode: ${file.pickCode}")
        }
        if (file.sha1.isNotEmpty()) {
            emitText("  SHA-1:    ${file.sha1}")
        }
        if (file.modifiedTime.isNotEmpty()) {
            emitText("  Modify:   ${file.modifiedTimeString}")
        }
        if (file.createTime.isNotEmpty()) {
            emitText("  Created:  ${file.createTimeString}")
        }
    }
}
