package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.terminal.engine.TerminalCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.CommandFlag
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 新建文件夹命令（mkdir）
 *
 * 在当前目录或指定路径下新建文件夹。
 * 支持 -p 参数：若目录已存在不报错，并支持自动递归创建所需的多级父目录。
 */
class MkdirCommand : TerminalCommand {

    override val name: String = "mkdir"

    override val description: String = "在当前目录或指定路径下新建文件夹"

    override val usage: String = "mkdir [-p] <folder_name...>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-p", "若目录已存在不报错，并支持递归创建父目录")
    )

    override suspend fun execute(
        ctx: TerminalContext,
        args: List<String>,
        stdin: Flow<String>
    ): Flow<String> = flow {
        val cmdArgs = CommandArgs(args)
        val isParents = cmdArgs.hasFlag("-p")
        val folderNames = cmdArgs.positionalArgs

        if (folderNames.isEmpty()) {
            emit("mkdir: missing operand")
            return@flow
        }

        for (rawName in folderNames) {
            val cleanTarget = rawName.trim().trimEnd('/')
            if (cleanTarget.isEmpty() || cleanTarget == "/" || cleanTarget == "/根目录") {
                if (!isParents) {
                    emit("mkdir: cannot create directory '$rawName': File exists")
                }
                continue
            }

            if (isParents) {
                // -p 模式：支持逐层递归创建目录，若各层级已存在则直接沿用
                val isAbsolute = cleanTarget.startsWith("/")
                var rawSegments = cleanTarget.split("/").filter { it.isNotEmpty() && it != "." }
                if (isAbsolute && rawSegments.firstOrNull() == "根目录") {
                    rawSegments = rawSegments.drop(1)
                }
                if (rawSegments.isEmpty()) continue

                var curCid = if (isAbsolute) "0" else ctx.currentCid
                var createSuccess = true

                for (seg in rawSegments) {
                    if (seg == "..") {
                        val resolved = ctx.resolvePath(seg)
                        if (resolved != null) {
                            curCid = resolved.first
                        }
                        continue
                    }
                    val existingFiles = ctx.listDirectory(curCid)
                    val existingFolder = existingFiles.firstOrNull { it.isFolder && it.name == seg }
                    if (existingFolder != null) {
                        // 目录已存在，步入该层级继续
                        curCid = existingFolder.categoryId
                    } else {
                        val existingFile = existingFiles.firstOrNull { !it.isFolder && it.name == seg }
                        if (existingFile != null) {
                            emit("mkdir: cannot create directory '$rawName': File exists")
                            createSuccess = false
                            break
                        }
                        try {
                            // 调用 115 网盘接口创建文件夹
                            val res = ctx.fileRepository.createFolder(pid = curCid, folderName = seg)
                            if (res.state) {
                                ctx.invalidateCache(curCid)
                                val nextCid = res.cid.ifEmpty {
                                    val refreshed = ctx.listDirectory(curCid, forceRefresh = true)
                                    refreshed.firstOrNull { it.isFolder && it.name == seg }?.categoryId ?: ""
                                }
                                if (nextCid.isNotEmpty()) {
                                    curCid = nextCid
                                } else {
                                    break
                                }
                            } else {
                                val err = res.error.ifEmpty { "创建失败" }
                                emit("mkdir: cannot create directory '$rawName': $err")
                                createSuccess = false
                                break
                            }
                        } catch (e: Exception) {
                            emit("mkdir: cannot create directory '$rawName': ${e.message}")
                            createSuccess = false
                            break
                        }
                    }
                }
                if (createSuccess) {
                    emit("mkdir: created directory '$rawName'")
                }
            } else {
                // 非 -p 模式：若带路径则必须在其已存在的父目录下创建
                val parentCid: String
                val folderName: String

                if (cleanTarget.contains("/")) {
                    val parentPath = cleanTarget.substringBeforeLast('/')
                    folderName = cleanTarget.substringAfterLast('/')
                    val effectiveParentPath = parentPath.ifEmpty { "/" }
                    val resolvedParent = ctx.resolvePath(effectiveParentPath)
                    if (resolvedParent == null) {
                        emit("mkdir: cannot create directory '$rawName': No such file or directory")
                        continue
                    }
                    parentCid = resolvedParent.first
                } else {
                    parentCid = ctx.currentCid
                    folderName = cleanTarget
                }

                // 校验父目录下是否已存在同名文件夹或文件
                val existing = ctx.listDirectory(parentCid).firstOrNull { it.name == folderName }
                if (existing != null) {
                    emit("mkdir: cannot create directory '$rawName': File exists")
                    continue
                }

                try {
                    val res = ctx.fileRepository.createFolder(
                        pid = parentCid,
                        folderName = folderName
                    )
                    if (res.state) {
                        ctx.invalidateCache(parentCid)
                        emit("mkdir: created directory '$rawName'")
                    } else {
                        val err = res.error.ifEmpty { "创建失败" }
                        emit("mkdir: cannot create directory '$rawName': $err")
                    }
                } catch (e: Exception) {
                    emit("mkdir: cannot create directory '$rawName': ${e.message}")
                }
            }
        }
    }
}
