package github.zerorooot.nap511.terminal.commands.util

import github.zerorooot.nap511.terminal.commands.file.BatchTargetItem
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.context.TerminalPath
import github.zerorooot.nap511.terminal.context.TerminalPathConstants

/**
 * 移动操作安全校验结果密封接口
 */
sealed interface MoveValidationResult {
    /** 校验通过，允许执行移动 */
    data object Valid : MoveValidationResult

    /** 校验拦截失败，包含格式化诊断错误提示 */
    data class Error(val message: String) : MoveValidationResult
}

/**
 * 删除操作安全校验结果密封接口
 */
sealed interface RemoveValidationResult {
    /** 校验通过，允许执行删除 */
    data object Valid : RemoveValidationResult

    /** 校验拦截失败，包含格式化诊断错误提示 */
    data class Error(val message: String) : RemoveValidationResult
}

/**
 * 统一目标实体解析器与高危边界安全防御器
 *
 * 核心设计目标（高内聚、强防御）：
 * 1. 统一路径向强类型 [BatchTargetItem] 元数据模型的决议；
 * 2. 根目录契约：根目录（cid == "0"）的 parentCid 严格保持为 null，杜绝错误回退；
 * 3. 统一高危边界拦截：全面拦截 "."、".."、根目录 "/"、当前工作目录以及祖先目录破坏；
 * 4. 严格 POSIX 规范对齐：原地移动输出 same file 错误，未加 -r 删除目录输出 Is a directory 错误。
 */
object TargetItemResolver {

    /**
     * 将原始路径解析为 [BatchTargetItem] 统一元数据实体
     *
     * 核心契约：
     * 1. 普通文件：使用 parentCid，若为空则回退到 categoryId；
     * 2. 文件夹：若为根目录（cid == "0"），其 parentCid 严格保持为 null，杜绝错误回退；
     *    若为普通文件夹，依次尝试 parentCid、folderBean.parentId 及 pathList 面包屑上级 CID。
     *
     * @param ctx 终端执行上下文
     * @param rawPath 原始输入路径
     * @return 解析成功返回 [BatchTargetItem]，路径不存在时返回 null
     */
    suspend fun resolveToBatchItem(
        ctx: TerminalContext,
        rawPath: String
    ): BatchTargetItem? {
        val parsed = TerminalPath.parse(rawPath)
        val resolved = ctx.resolveTarget(rawPath) ?: return null

        return when (resolved) {
            is ResolvedTarget.File -> {
                val effectiveParentCid = resolved.parentCid.ifEmpty { resolved.file.categoryId }
                BatchTargetItem(
                    parentCid = effectiveParentCid.takeIf { it.isNotEmpty() },
                    fid = resolved.file.fileId,
                    displayName = resolved.file.name,
                    isFolder = false,
                    path = resolved.fullPath // 源头直接赋能完整路径，支持跨目录拓扑剪枝
                )
            }
            is ResolvedTarget.Directory -> {
                val isRoot = resolved.cid == TerminalPathConstants.ROOT_CID
                val effectiveParentCid = if (isRoot) {
                    null
                } else {
                    resolved.parentCid
                        ?: resolved.folderBean?.parentId?.takeIf { it.isNotEmpty() }
                        ?: resolved.pathList.dropLast(1).lastOrNull()?.cid
                }

                BatchTargetItem(
                    parentCid = effectiveParentCid,
                    fid = resolved.cid,
                    displayName = resolved.name.ifEmpty { parsed.targetName.ifEmpty { TerminalPathConstants.ROOT_NAME } },
                    isFolder = true,
                    path = resolved.path // 源头直接赋能完整路径，支持跨目录拓扑剪枝
                )
            }
        }
    }

    /**
     * 判定实体是否为根目录
     */
    fun isRoot(item: BatchTargetItem): Boolean = item.fid == TerminalPathConstants.ROOT_CID

    /**
     * 判定实体是否为当前终端会话所在的工作目录
     */
    fun isCurrentDirectory(ctx: TerminalContext, item: BatchTargetItem): Boolean = item.fid == ctx.currentCid

    /**
     * 是否为字面量 "." 或 ".."，或以此为末段的操作数（如 "a/b/." 或 "dir/.."）
     * 强化防护：前置过滤空串与全斜杠根路径
     */
    fun isDotOrDotDot(rawPath: String): Boolean {
        val trimmed = rawPath.trim()
        if (trimmed.isEmpty() || trimmed.all { it == '/' }) return false
        val stripped = trimmed.trimEnd('/')
        return stripped == "." || stripped == ".." || stripped.endsWith("/.") || stripped.endsWith("/..")
    }

    /**
     * 是否为当前工作目录的父级或祖先目录（当前会话链路中的上级目录）
     */
    fun isAncestorOfCurrent(ctx: TerminalContext, item: BatchTargetItem): Boolean {
        if (!item.isFolder) return false
        val ancestors = ctx.currentPathList.dropLast(1)
        return ancestors.any { it.cid == item.fid }
    }

    /**
     * 移动操作综合安全校验链
     *
     * 拦截场景：
     * 1. 移动字面量 "." 或 ".."；
     * 2. 移动根目录；
     * 3. 移动当前工作目录；
     * 4. 移动当前工作目录的父级或祖先目录；
     * 5. 原地移动校验（严格对齐 POSIX same file 规范，输出标准错误并短路）；
     * 6. 自嵌套与移入下级子目录校验。
     *
     * @param item 待移动的目标实体
     * @param targetDestCid 目标文件夹 CID
     * @param destDir 目标文件夹的解析实体
     * @param ctx 终端执行上下文
     * @param rawPath 待移动项的原始命令行输入路径
     * @param rawDestination 目标目录的命令行显示字符串（默认为目标目录名或路径）
     * @return [MoveValidationResult]
     */
    fun validateMove(
        item: BatchTargetItem,
        targetDestCid: String,
        destDir: ResolvedTarget.Directory,
        ctx: TerminalContext,
        rawPath: String,
        rawDestination: String = if (destDir.name.isNotEmpty()) destDir.name else destDir.path
    ): MoveValidationResult {
        if (isDotOrDotDot(rawPath)) {
            return MoveValidationResult.Error(CommandMessages.cannotMoveDotOrDotDot("mv", rawPath))
        }
        if (isRoot(item)) {
            return MoveValidationResult.Error(CommandMessages.cannotMoveRoot("mv", rawPath))
        }
        if (isCurrentDirectory(ctx, item)) {
            return MoveValidationResult.Error(CommandMessages.cannotMoveCwd("mv", rawPath))
        }
        if (isAncestorOfCurrent(ctx, item)) {
            return MoveValidationResult.Error(CommandMessages.cannotMoveAncestor("mv", rawPath))
        }
        // 原地移动校验（严格对齐 POSIX same file 规范，输出标准错误）
        if (item.parentCid != null && item.parentCid == targetDestCid) {
            return MoveValidationResult.Error(CommandMessages.sameFile("mv", rawPath, rawDestination))
        }
        // 自嵌套与移入下级子目录校验
        if (item.isFolder && (item.fid == destDir.cid || destDir.pathList.any { it.cid == item.fid })) {
            return MoveValidationResult.Error(CommandMessages.selfNestingError("mv", rawPath))
        }
        return MoveValidationResult.Valid
    }

    /**
     * 删除操作综合安全校验链（集成 POSIX -r 与高危祖先校验）
     *
     * 拦截场景：
     * 1. 删除字面量 "." 或 ".."；
     * 2. 删除根目录；
     * 3. 删除当前工作目录；
     * 4. 删除当前工作目录的父级或祖先目录；
     * 5. 目录项未携带 -r / -R 递归标志时拦截并输出 POSIX 标准 'Is a directory' 错误；
     * 6. 无法判定有效 parentCid 时防御性拦截。
     *
     * @param item 待删除的目标实体
     * @param recursive 是否开启了 -r / -R 递归开关
     * @param ctx 终端执行上下文
     * @param rawPath 待删除项的原始命令行输入路径
     * @return [RemoveValidationResult]
     */
    fun validateRemove(
        item: BatchTargetItem,
        recursive: Boolean,
        ctx: TerminalContext,
        rawPath: String
    ): RemoveValidationResult {
        if (isDotOrDotDot(rawPath)) {
            return RemoveValidationResult.Error(CommandMessages.refuseRemoveDotOrDotDot("rm", rawPath))
        }
        if (isRoot(item)) {
            return RemoveValidationResult.Error(CommandMessages.cannotRemoveRoot("rm", rawPath))
        }
        if (isCurrentDirectory(ctx, item)) {
            return RemoveValidationResult.Error(CommandMessages.cannotRemoveCwd("rm", rawPath))
        }
        if (isAncestorOfCurrent(ctx, item)) {
            return RemoveValidationResult.Error(CommandMessages.cannotRemoveAncestor("rm", rawPath))
        }
        if (item.isFolder && !recursive) {
            return RemoveValidationResult.Error(CommandMessages.isADirectory("rm", rawPath))
        }
        if (item.parentCid.isNullOrEmpty()) {
            return RemoveValidationResult.Error(CommandMessages.cannotDetermineParent("rm", rawPath))
        }
        return RemoveValidationResult.Valid
    }
}
