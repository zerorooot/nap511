package github.zerorooot.nap511.terminal.engine.completion

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.ParsedContext

/**
 * 终端命令补全器规范（Command Completer Interface）
 *
 * 遵循微内核扩展设计与开闭原则：
 * 1. 既支持基于 [PathFilter] 的轻量级声明式文件类型筛选；
 * 2. 也支持自定义动态数据源重写（如回收站命令直接拉取待还原文件清单）。
 */
interface CommandCompleter {

    /**
     * 获取用于指定位置参数的文件过滤器
     *
     * @param argIndex 当前补全的目标位置参数索引（从 0 开始）
     * @return 匹配的 [PathFilter]
     */
    fun getPathFilter(argIndex: Int): PathFilter = FileFilters.ALL

    /**
     * 解析并生成候选文件实体列表（支持自定义动态数据源重写）
     *
     * @param ctx 终端执行上下文
     * @param parsedContext 上下文解析元数据
     * @param defaultFiles 终端默认由当前路径拉取出的文件列表
     * @return 过滤或生成后的候选文件列表
     */
    suspend fun resolveCandidates(
        ctx: TerminalContext,
        parsedContext: ParsedContext,
        defaultFiles: List<FileBean>
    ): List<FileBean> {
        val argIndex = parsedContext.argIndex
        val filter = getPathFilter(argIndex)
        return defaultFiles.filter { filter.accept(it, argIndex) }
    }
}
