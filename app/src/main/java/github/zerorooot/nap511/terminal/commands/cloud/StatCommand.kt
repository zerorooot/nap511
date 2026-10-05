package github.zerorooot.nap511.terminal.commands.cloud

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
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
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        val targetName = cmdArgs.firstPositional
        if (targetName == null) {
            emit("stat: missing operand")
            return@flow
        }

        val file = when (val resolved = ctx.resolveTarget(targetName)) {
            is ResolvedTarget.File -> resolved.file
            is ResolvedTarget.Directory -> {
                // 优先使用 resolveTarget 获取的完整 folderBean，若无则使用解析所得真实目录名，防止末尾斜杠导致截断为空
                resolved.folderBean ?: FileBean(
                    name = resolved.name.ifEmpty {
                        targetName.trimEnd('/').substringAfterLast('/').ifEmpty { "根目录" }
                    },
                    categoryId = resolved.cid,
                    isFolder = true
                )
            }

            null -> {
                emit("stat: cannot stat '$targetName': No such file or directory")
                return@flow
            }
        }

        emit("  File: ${file.name}")
        emit("  Type: ${if (file.isFolder) "Directory" else "Regular File"}")
        emit("  Size: ${file.size} bytes (${file.sizeString.trim()})")
        emit("  CID:  ${file.categoryId}  |  FID: ${file.fileId}")
        if (file.pickCode.isNotEmpty()) {
            emit("  PickCode: ${file.pickCode}")
        }
        if (file.sha1.isNotEmpty()) {
            emit("  SHA-1:    ${file.sha1}")
        }
        if (file.modifiedTime.isNotEmpty()) {
            emit("  Modify:   ${file.modifiedTimeString}")
        }
        if (file.createTime.isNotEmpty()) {
            emit("  Created:  ${file.createTimeString}")
        }
    }
}
