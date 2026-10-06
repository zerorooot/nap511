package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.context.TerminalPath
import github.zerorooot.nap511.terminal.context.TerminalPathConstants
import github.zerorooot.nap511.terminal.context.currentCid
import github.zerorooot.nap511.terminal.engine.CommandFlag
import github.zerorooot.nap511.terminal.engine.archetype.MutationCommand
import github.zerorooot.nap511.terminal.engine.ast.CommandInvocationAst
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector

/**
 * 新建目录执行计划
 *
 * @property isParents 是否启用 -p 递归创建并忽略已存在目录
 * @property folderNames 待创建的目标目录名称列表
 */
data class MkdirPlan(
    val isParents: Boolean,
    val folderNames: List<String>
)

/**
 * 新建文件夹命令（mkdir）
 *
 * 继承 [MutationCommand]，在编译期提取 `-p` 标志与校验操作数列表。
 * 在当前目录或指定路径下新建文件夹；-p 模式下若目录已存在不报错，并支持自动递归创建所需的多级父目录。
 */
class MkdirCommand : MutationCommand<MkdirPlan>() {

    override val name: String = "mkdir"

    override val description: String = "在当前目录或指定路径下新建文件夹"

    override val usage: String = "mkdir [-p] <folder_name...>"

    override val flags: List<CommandFlag> = listOf(
        CommandFlag("-p", "若目录已存在不报错，并支持递归创建父目录")
    )

    override suspend fun compilePlan(
        ast: CommandInvocationAst,
        stdin: Flow<String>
    ): Result<MkdirPlan> {
        val folderNames = ast.rawPositionalValues
        if (folderNames.isEmpty()) {
            return Result.failure(Exception("missing operand"))
        }
        val isParents = ast.hasFlag("-p")
        return Result.success(MkdirPlan(isParents = isParents, folderNames = folderNames))
    }

    override suspend fun executePlan(
        ctx: TerminalContext,
        plan: MkdirPlan,
        collector: FlowCollector<TerminalOutput>
    ) {
        val isParents = plan.isParents
        for (rawName in plan.folderNames) {
            val parsed = TerminalPath.parse(rawName)
            if (parsed.isRoot || parsed.targetName.isEmpty()) {
                if (!isParents) {
                    collector.emitError("mkdir: cannot create directory '$rawName': File exists")
                }
                continue
            }

            if (isParents) {
                // -p 模式：支持逐层递归创建目录，若各层级已存在则直接沿用
                val workingPathList = if (parsed.isAbsolute) {
                    mutableListOf(TerminalPathConstants.ROOT_PATH_BEAN)
                } else {
                    ctx.currentPathList.toMutableList()
                }

                var createSuccess = true
                for (seg in parsed.segments) {
                    if (seg == "..") {
                        if (workingPathList.size > 1) {
                            workingPathList.removeAt(workingPathList.size - 1)
                        }
                        continue
                    }
                    val curCid = workingPathList.currentCid()
                    val existingFiles = ctx.listDirectory(curCid)
                    val existingFolder = existingFiles.firstOrNull { it.isFolder && it.name == seg }
                    if (existingFolder != null) {
                        // 目录已存在，步入该层级继续
                        workingPathList.add(PathBean(cid = existingFolder.categoryId, name = seg, pid = curCid))
                    } else {
                        val existingFile = existingFiles.firstOrNull { !it.isFolder && it.name == seg }
                        if (existingFile != null) {
                            collector.emitError("mkdir: cannot create directory '$rawName': File exists")
                            createSuccess = false
                            break
                        }
                        try {
                            // 调用 115 网盘接口创建文件夹
                            val res = ctx.fileRepository.createFolder(pid = curCid, folderName = seg)
                            if (res.state) {
                                val nextCid = res.cid.ifEmpty {
                                    val refreshed = ctx.listDirectory(curCid, forceRefresh = true)
                                    refreshed.firstOrNull { it.isFolder && it.name == seg }?.categoryId ?: ""
                                }
                                if (nextCid.isNotEmpty()) {
                                    // 就地追加到父目录缓存并预埋新目录缓存，零额外网络请求
                                    ctx.addCachedFolder(parentCid = curCid, folderName = seg, newCid = nextCid)
                                    workingPathList.add(PathBean(cid = nextCid, name = seg, pid = curCid))
                                } else {
                                    break
                                }
                            } else {
                                val err = res.error.ifEmpty { "创建失败" }
                                collector.emitError("mkdir: cannot create directory '$rawName': $err")
                                createSuccess = false
                                break
                            }
                        } catch (e: Exception) {
                            collector.emitError("mkdir: cannot create directory '$rawName': ${e.message}")
                            createSuccess = false
                            break
                        }
                    }
                }
                if (createSuccess) {
                    collector.emitText("mkdir: created directory '$rawName'")
                }
            } else {
                // 非 -p 模式：若带路径则必须在其已存在的父目录下创建
                val parentCid: String
                val folderName: String = parsed.targetName

                if (parsed.isMultiSegment || (parsed.isAbsolute && parsed.segments.isNotEmpty())) {
                    val parentTarget = parsed.parentPathString
                    val resolvedParent = ctx.resolveDirectory(parentTarget)
                    if (resolvedParent == null) {
                        collector.emitError("mkdir: cannot create directory '$rawName': No such file or directory")
                        continue
                    }
                    parentCid = resolvedParent.cid
                } else {
                    parentCid = ctx.currentCid
                }

                // 校验父目录下是否已存在同名文件夹或文件
                val existing = ctx.listDirectory(parentCid).firstOrNull { it.name == folderName }
                if (existing != null) {
                    collector.emitError("mkdir: cannot create directory '$rawName': File exists")
                    continue
                }

                try {
                    val res = ctx.fileRepository.createFolder(
                        pid = parentCid,
                        folderName = folderName
                    )
                    if (res.state) {
                        val newCid = res.cid
                        if (newCid.isNotEmpty()) {
                            // 就地向父目录追加新建目录并预埋空缓存，无需失效父目录缓存
                            ctx.addCachedFolder(parentCid = parentCid, folderName = folderName, newCid = newCid)
                        } else {
                            ctx.invalidateCache(parentCid)
                        }
                        collector.emitText("mkdir: created directory '$rawName'")
                    } else {
                        val err = res.error.ifEmpty { "创建失败" }
                        collector.emitError("mkdir: cannot create directory '$rawName': $err")
                    }
                } catch (e: Exception) {
                    collector.emitError("mkdir: cannot create directory '$rawName': ${e.message}")
                }
            }
        }
    }
}
