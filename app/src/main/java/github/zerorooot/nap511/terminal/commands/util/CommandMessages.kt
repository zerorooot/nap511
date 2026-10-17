package github.zerorooot.nap511.terminal.commands.util

/**
 * 终端命令中英双语消息与提示规范工厂
 *
 * 规范约束：
 * 1. Unix / POSIX 固有静态错误（文件不存在、是目录、同一文件、缺少参数等）一律采用英文标准格式；
 * 2. 115 业务提示、操作成功回显及网络/服务端中文解释一律采用纯中文；
 * 3. 严格遵循 Unix 诊断信息规范前缀格式：`<cmd>: <context>: <reason>` 或 `<cmd>: <reason>`。
 */
object CommandMessages {
    // =========================================================================
    // 1. POSIX 标准规范报错模板（严格英文，对齐 Unix 标准行为）
    // =========================================================================

    /** 缺失必要操作数 */
    fun missingOperand(cmd: String): String = "$cmd: missing operand"

    /** 缺失文件操作数 */
    fun missingFileOperand(cmd: String): String = "$cmd: missing file operand"

    /** 参数过多 */
    fun tooManyArguments(cmd: String): String = "$cmd: too many arguments"

    /** 无法访问文件或目录（不存在） */
    fun cannotAccess(cmd: String, path: String): String = "$cmd: cannot access '$path': No such file or directory"

    /** 无法获取元数据状态（不存在） */
    fun cannotStat(cmd: String, path: String): String = "$cmd: cannot stat '$path': No such file or directory"

    /** 无法删除（不存在） */
    fun cannotRemove(cmd: String, path: String): String = "$cmd: cannot remove '$path': No such file or directory"

    /** 目标不是目录 */
    fun notADirectory(cmd: String, path: String): String = "$cmd: target '$path' is not a directory"

    private fun prefix(cmd: String): String = if (cmd.isEmpty()) "" else "$cmd: "

    /** 目标是目录（非递归删除目录时拦截报错） */
    fun isADirectory(cmd: String, path: String): String = "${prefix(cmd)}'$path': Is a directory"

    /** 目标不是文本文件 */
    fun notATextFile(cmd: String, path: String): String = "${prefix(cmd)}'$path': Not a text file"

    /** 目标文件/目录已存在 */
    fun fileExists(cmd: String, path: String): String = "$cmd: cannot create directory '$path': File exists"

    /** 源文件与目标为同一文件或同一目录下原地移动 */
    fun sameFile(cmd: String, src: String, dest: String): String = "$cmd: '$src' and '$dest' are the same file"

    /** 无法访问/打开指定文件（流式命令专用：cat/head/tail/wc/grep 等） */
    fun noSuchFile(cmd: String, path: String): String = "${prefix(cmd)}'$path': No such file or directory"

    /** 文件大小超限 */
    fun fileExceedsLimit(cmd: String, name: String, sizeStr: String, limitKb: Int): String =
        "${prefix(cmd)}'$name': File exceeds size limit ($sizeStr >= ${limitKb}KB)"

    /** 读取文件失败 */
    fun failedToReadFile(cmd: String, name: String, reason: String): String =
        "${prefix(cmd)}'$name': Failed to read file ($reason)"

    /** 命令未找到 */
    fun commandNotFound(cmd: String): String = "$cmd: command not found"

    // -------------------------------------------------------------------------
    // 高危与关键路径防御提示（严格英文）
    // -------------------------------------------------------------------------

    /** 禁止删除根目录 */
    fun cannotRemoveRoot(cmd: String, path: String): String = "$cmd: cannot remove '$path': Cannot remove root directory"

    /** 禁止删除当前工作目录 */
    fun cannotRemoveCwd(cmd: String, path: String): String = "$cmd: cannot remove '$path': Cannot remove current working directory"

    /** 禁止删除当前工作目录的父级或祖先目录 */
    fun cannotRemoveAncestor(cmd: String, path: String): String = "$cmd: cannot remove '$path': Cannot remove ancestor of current working directory"

    /** 拒绝删除 '.' 或 '..' 目录项 */
    fun refuseRemoveDotOrDotDot(cmd: String, path: String): String = "$cmd: refusing to remove '.' or '..' directory: skipping '$path'"

    /** 禁止移动根目录 */
    fun cannotMoveRoot(cmd: String, path: String): String = "$cmd: cannot move '$path': Cannot move root directory"

    /** 禁止移动当前工作目录 */
    fun cannotMoveCwd(cmd: String, path: String): String = "$cmd: cannot move '$path': Cannot move current working directory"

    /** 禁止移动当前工作目录的父级或祖先目录 */
    fun cannotMoveAncestor(cmd: String, path: String): String = "$cmd: cannot move '$path': Cannot move ancestor of current working directory"

    /** 禁止移动 '.' 或 '..' 目录 */
    fun cannotMoveDotOrDotDot(cmd: String, path: String): String = "$cmd: cannot move '$path': Cannot move '.' or '..' directory"

    /** 无法确定父目录（数据损坏或未解析到父级） */
    fun cannotDetermineParent(cmd: String, path: String): String = "$cmd: cannot remove '$path': Cannot determine parent directory"

    // =========================================================================
    // 2. 115 业务提示、操作成功回显与错误转译（统一规范中文）
    // =========================================================================

    // 删除与清理相关提示
    /** 单文件移入回收站成功回显 */
    fun movedToTrash(cmd: String, name: String): String = "$cmd: 已移入回收站 '$name'"

    /** 批量移入回收站全部成功回显（全新全局统一标准） */
    fun batchDeleteSuccess(cmd: String, count: Int): String = "$cmd: 已成功删除 $count 个项目至回收站"

    /** 批量移入回收站部分成功回显 */
    fun batchDeletePartial(cmd: String, success: Int, total: Int): String = "$cmd: 部分删除成功 ($success/$total) 至回收站"

    /** 批量移入回收站失败提示 */
    fun batchDeleteFailed(cmd: String, count: Int, error: String): String = "$cmd: 批量删除失败 ($count 个项目): $error"

    /**
     * 批量移入回收站历史成功回显（保留作为向后兼容别名，防止下游未适配调用方编译中断）
     */
    @Deprecated(
        message = "建议迁移至 batchDeleteSuccess 以支持跨目录原子汇总语义",
        replaceWith = ReplaceWith("batchDeleteSuccess(cmd, count)")
    )
    fun batchMovedToTrash(cmd: String, count: Int): String = "$cmd: 已成功批量删除目录下的 $count 个项目至回收站"

    /** 取消单项删除操作 */
    fun cancelDelete(cmd: String, name: String): String = "$cmd: 已取消删除 '$name'"

    /** 取消批量或通用操作 */
    fun cancelOperation(cmd: String): String = "$cmd: 已取消操作"

    // 移动与重命名相关回显
    /** 单项移动成功回显 */
    fun singleMoveSuccess(cmd: String, src: String, dest: String): String = "$cmd: '$src' -> '$dest'"

    /** 单项重命名成功回显 */
    fun renamed(cmd: String, src: String, newName: String): String = "$cmd: 已重命名 '$src' 为 '$newName'"

    /** 批量移动全部成功回显 */
    fun batchMoveSuccess(cmd: String, count: Int, dest: String): String = "$cmd: 已成功移动 $count 个文件/文件夹至 '$dest'"

    /** 批量移动部分成功回显 */
    fun batchMovePartial(cmd: String, success: Int, total: Int, dest: String): String = "$cmd: 部分移动成功 ($success/$total) 至 '$dest'"

    /** 批量移动失败提示 */
    fun batchMoveFailed(cmd: String, count: Int, error: String): String = "$cmd: 批量移动失败 ($count 个项目): $error"

    /** 自嵌套或移入子目录错误提示 */
    fun selfNestingError(cmd: String, src: String): String = "$cmd: 无法将目录 '$src' 移动至其自身或子目录下"

    /** 跨目录移动重命名失败后逆向回滚成功提示 */
    fun moveRollbackSuccess(cmd: String, src: String, dest: String): String = "$cmd: 重命名失败，已自动将文件回滚移回原目录 '$dest'"

    /** 跨目录移动重命名失败后逆向回滚失败警告提示 */
    fun moveRollbackFailed(cmd: String, src: String, dest: String, rollbackErr: String): String = "$cmd: 重命名失败且回滚失败: 文件已滞留在 '$dest' ($rollbackErr)"

    // 目录创建相关
    /** 目录创建成功回显 */
    fun createdDirectory(cmd: String, path: String): String = "$cmd: 已成功创建目录 '$path'"

    // 认证与网络环境提示
    /** 115 凭据失效提示 */
    fun authExpired(): String = "115 登录凭据已过期或未登录，请重新登录账号"
}
