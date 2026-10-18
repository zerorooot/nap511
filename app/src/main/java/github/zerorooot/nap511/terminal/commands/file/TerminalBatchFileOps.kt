package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.commands.util.TerminalExceptionHandler
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.util.FileCacheManager
import kotlinx.coroutines.CancellationException

/**
 * 待批量操作（删除/移动）目标实体统一元数据模型
 *
 * @property parentCid 待操作项原本所在的父目录 ID，根目录或未知时显式为 null
 * @property fid 待操作项的唯一文件/目录标识符（fileId 或 categoryId）
 * @property displayName 显示名称（用于输出日志与错误信息）
 * @property isFolder 是否为文件夹
 * @property path 文件或目录的完整路径（用于内存拓扑剪枝，默认为空）
 */
data class BatchTargetItem(
    val parentCid: String?,
    val fid: String,
    val displayName: String,
    val isFolder: Boolean,
    val path: String = ""
)

/**
 * 批量操作执行汇总结果
 *
 * 用于细粒度描述切片批量操作的执行结果，提供成功数、失败数及错误描述，支持精准的终端回显与缓存维护。
 *
 * @property totalCount 计划执行的总条目数
 * @property successCount 成功执行的条目数
 * @property failureCount 失败或未执行的条目数
 * @property error 首个失败批次的错误信息
 */
data class BatchOpResult(
    val totalCount: Int,
    val successCount: Int,
    val failureCount: Int,
    val error: String = ""
) {
    /** 是否全部成功 */
    val isAllSuccess: Boolean get() = failureCount == 0 && totalCount > 0

    /** 是否全部失败 */
    val isAllFailed: Boolean get() = successCount == 0 && totalCount > 0

    /** 是否部分成功（存在成功切片，但后续切片熔断失败） */
    val isPartialSuccess: Boolean get() = successCount > 0 && failureCount > 0

    /** 向后兼容 BaseReturnMessage.state 契约（等价于 isAllSuccess） */
    val state: Boolean get() = isAllSuccess
}

/**
 * [FileBean] 的有效父目录 ID 计算扩展
 *
 * 核心事实与 115 数据契约：
 * 1. 当 [FileBean] 为普通文件时，115 API 通常不返回 parentId（即 pid 为空字符串），
 *    此时其 categoryId 恰恰代表该文件归属的父目录 ID；
 * 2. 当 [FileBean] 为目录时，其 categoryId 代表的是自身目录 ID，
 *    此时其 parentId 才代表上级父目录 ID。
 *
 * @param fallbackParentCid 外部已知或已解析的兜底父目录 ID
 * @return 归一化后的有效父目录 ID
 */
fun FileBean.resolveEffectiveParentCid(fallbackParentCid: String = ""): String {
    return when {
        fallbackParentCid.isNotEmpty() -> fallbackParentCid
        isFolder -> parentId
        else -> categoryId.ifEmpty { parentId }
    }
}

/**
 * 终端批量文件操作统一协调器（删除与移动）
 *
 * 核心设计目标（高内聚、低耦合、高复用）：
 * 1. 协议聚合：对齐 115 官方批量协议（`deleteMultiple` / `move`），单次 HTTP 请求处理多个条目，避免逐项串行往返；
 * 2. 分块切片（Chunking）：内置安全批次阈值 [BATCH_CHUNK_SIZE]（100 项），防止单次请求报文过大超时；
 * 3. 强一致性缓存维护：
 *    - 移动操作：各切片移动成功后，按源目录 [parentCid] 分组清理本地条目；在 finally 中保证只要有任意批次成功，
 *      必然强制刷新目标目录 [targetDestCid] 缓存，杜绝 UI 列表不同步；
 *    - 删除操作：双重级联清理，更新父目录列表并对子文件夹深度递归剔除缓存；
 * 4. 容错与熔断：单批失败及时终止后续批次，向上层返回精细的 [BatchOpResult]。
 */
object TerminalBatchFileOps {

    /**
     * 单次请求最大包含的项目数阈值，超出时切片分批发送，防止 HTTP 请求体过大
     */
    const val BATCH_CHUNK_SIZE = 100

    /**
     * 批量将目标文件/文件夹移动至 [targetDestCid] 目录，并同步本地缓存
     *
     * @param ctx 终端执行上下文
     * @param targetDestCid 目标文件夹 ID
     * @param items 待移动的目标实体列表（可来自不同的源目录）
     * @return 细粒度批量移动执行结果 [BatchOpResult]
     */
    suspend fun moveGroup(
        ctx: TerminalContext,
        targetDestCid: String,
        items: List<BatchTargetItem>
    ): BatchOpResult {
        // 1. 预先按 fid 去重，防止重复操作数引起数量虚高与多余网络请求
        val uniqueItems = items.distinctBy { it.fid }
        if (uniqueItems.isEmpty()) {
            return BatchOpResult(totalCount = 0, successCount = 0, failureCount = 0)
        }

        var successCount = 0
        var firstError = ""
        var anyChunkSucceeded = false

        try {
            val chunks = uniqueItems.chunked(BATCH_CHUNK_SIZE)
            for (chunk in chunks) {
                // 组装 115 files/move 协议表单: pid + fid[0]..fid[N]
                val data = HashMap<String, String>().apply {
                    put("pid", targetDestCid)
                    chunk.forEachIndexed { index, item ->
                        put("fid[$index]", item.fid)
                    }
                }

                try {
                    val res = ctx.fileRepository.move(data)
                    if (res.state) {
                        anyChunkSucceeded = true
                        successCount += chunk.size

                        // 本地缓存多源同步：按源目录 parentCid 分组清理条目
                        val groupedBySourceParent = chunk.groupBy { it.parentCid }
                        for ((srcParentCid, srcItems) in groupedBySourceParent) {
                            if (!srcParentCid.isNullOrEmpty()) {
                                FileCacheManager.removeItems(srcParentCid, srcItems.map { it.fid })
                            }
                        }
                    } else {
                        firstError = TerminalExceptionHandler.extractErrorMessage(res, "未知错误")
                        break // 业务失败熔断后续切片
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    // 隔离网络非致命异常（如超时），保全已成功切片统计，防击穿打穿外层
                    firstError = TerminalExceptionHandler.extractErrorMessage(e)
                    break // 网络异常熔断后续切片
                }
            }
        } finally {
            // 只要有任意切片移动成功，立即强制刷新目标目录缓存，从服务端拉取最新列表
            if (anyChunkSucceeded) {
                ctx.listDirectory(cid = targetDestCid, forceRefresh = true)
            }
        }

        return BatchOpResult(
            totalCount = uniqueItems.size,
            successCount = successCount,
            failureCount = uniqueItems.size - successCount,
            error = firstError
        )
    }

    /**
     * 待删除项通用内存拓扑剪枝 (Topological Pruning)
     *
     * 针对通配符展开或搜索递归中同时包含父目录及其内部任意层级子孙项的场景：
     * 1. 预先按 FID 去重：杜绝用户重复参数输入或通配符多次命中造成的数量虚高与多余网络请求；
     * 2. 绑定 FID 排除自身：将待删除目录的 FID 与前缀路径配对，使用 item.fid != folderFid 排除自身，
     *    彻底根除目录路径末尾带斜杠时的“自剪枝”Bug；
     * 3. 路径前缀判定：若某条目的完整 path 落在某个非自身的待删除文件夹前缀下，判定为深层子孙项予以剪枝；
     * 4. 父级 ID 判定：若某条目的直接 parentCid 落在待删除文件夹 ID 集合中，予以剪枝（独立全量提取 folderFids 防漏剪）。
     * 避免云端网盘级联删除父目录后子孙项产生冗余网络请求或二次删除 404 虚假报错。
     */
    fun pruneBatchItems(items: List<BatchTargetItem>): List<BatchTargetItem> {
        // 1. 预先按 fid 去重，防止重复操作数引起数量虚高与多余网络请求
        val uniqueItems = items.distinctBy { it.fid }
        if (uniqueItems.size <= 1) return uniqueItems

        // 2. 收集所有作为待删除项的文件夹 (fid to pathPrefix) 对，明确排除自身
        val folderEntries = uniqueItems
            .filter { it.isFolder && it.path.isNotEmpty() }
            .map { it.fid to (if (it.path.endsWith("/")) it.path else "${it.path}/") }

        // 独立于 path 是否为空进行全量提取，确保在缺失路径元数据时 ID 剪枝依然 100% 生效
        val folderFids = uniqueItems.filter { it.isFolder }.map { it.fid }.toSet()

        return uniqueItems.filter { item ->
            // 1. 路径前缀判定：若某条目的完整 path 落在某个非自身的待删除文件夹前缀下，判定为深层子孙项予以剪枝
            if (item.path.isNotEmpty()) {
                val isDescendant = folderEntries.any { (fFid, prefix) ->
                    item.fid != fFid && item.path.startsWith(prefix)
                }
                if (isDescendant) return@filter false
            }

            // 2. 直接父级 ID 判定
            if (!item.parentCid.isNullOrEmpty() && item.parentCid in folderFids) {
                return@filter false
            }

            true
        }
    }

    /**
     * 对同一父目录 [parentCid] 下的一组待删除项执行删除并同步本地缓存
     *
     * @param ctx 终端执行上下文
     * @param parentCid 待删除项所在的父目录 ID，根目录或未知时可为 null
     * @param items 归属于该父目录的待删除目标列表
     * @return 细粒度批量删除执行结果 [BatchOpResult]
     */
    suspend fun deleteGroup(
        ctx: TerminalContext,
        parentCid: String?,
        items: List<BatchTargetItem>
    ): BatchOpResult {
        if (items.isEmpty()) {
            return BatchOpResult(totalCount = 0, successCount = 0, failureCount = 0)
        }

        val effectivePid = parentCid.orEmpty()

        // 1. parentCid 为空时的降级遍历分支（修复原代码只删首项、空PID传参与缓存遗漏的多重缺陷）
        if (effectivePid.isEmpty()) {
            var successCount = 0
            var firstError = ""
            for (item in items) {
                val itemPid = item.parentCid.orEmpty()
                try {
                    // 优先使用 item 自身记录的 parentCid 作为有效 pid 发起云端删除，避免空串引发服务端异常
                    val res = ctx.fileRepository.delete(pid = itemPid, fid = item.fid)
                    if (res.state) {
                        successCount++
                        // 优先使用 item 自身的 parentCid 进行精准缓存剔除，避免空串导致缓存失效
                        syncCacheAfterDelete(itemPid, listOf(item))
                    } else {
                        if (firstError.isEmpty()) {
                            firstError = TerminalExceptionHandler.extractErrorMessage(res, "未知错误")
                        }
                    }
                } catch (e: Throwable) {
                    if (e is CancellationException) throw e
                    if (firstError.isEmpty()) {
                        firstError = TerminalExceptionHandler.extractErrorMessage(e)
                    }
                    break // 熔断后续条目，返回已成功的数量
                }
            }
            return BatchOpResult(
                totalCount = items.size,
                successCount = successCount,
                failureCount = items.size - successCount,
                error = firstError
            )
        }

        // 2. 单项删除分支（有有效 parentCid）
        if (items.size == 1) {
            val single = items.first()
            return try {
                val res = ctx.fileRepository.delete(pid = effectivePid, fid = single.fid)
                if (res.state) {
                    syncCacheAfterDelete(effectivePid, listOf(single))
                    BatchOpResult(totalCount = 1, successCount = 1, failureCount = 0)
                } else {
                    val err = TerminalExceptionHandler.extractErrorMessage(res, "未知错误")
                    BatchOpResult(totalCount = 1, successCount = 0, failureCount = 1, error = err)
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                val err = TerminalExceptionHandler.extractErrorMessage(e)
                BatchOpResult(totalCount = 1, successCount = 0, failureCount = 1, error = err)
            }
        }

        // 3. 批量删除分支：按安全阈值 100 条切片分批调用 deleteMultiple
        val chunks = items.chunked(BATCH_CHUNK_SIZE)
        var successCount = 0
        var firstError = ""

        for (chunk in chunks) {
            val data = HashMap<String, String>().apply {
                put("ignore_warn", "1")
                put("pid", effectivePid)
                chunk.forEachIndexed { index, item ->
                    put("fid[$index]", item.fid)
                }
            }

            try {
                val res = ctx.fileRepository.deleteMultiple(data)
                if (res.state) {
                    successCount += chunk.size
                    syncCacheAfterDelete(effectivePid, chunk)
                } else {
                    firstError = TerminalExceptionHandler.extractErrorMessage(res, "未知错误")
                    break // 业务失败熔断后续切片，返回已成功的数量
                }
            } catch (e: Throwable) {
                if (e is CancellationException) throw e
                // 隔离网络非致命异常（如超时），保全已成功切片统计，防击穿打穿外层
                firstError = TerminalExceptionHandler.extractErrorMessage(e)
                break // 网络异常熔断后续切片，返回已成功的数量
            }
        }

        return BatchOpResult(
            totalCount = items.size,
            successCount = successCount,
            failureCount = items.size - successCount,
            error = firstError
        )
    }

    /**
     * 成功删除后的本地缓存双重同步：
     * 1. 父目录列表更新：按各条目自身记录的 parentCid 分组清理对应目录的本地缓存，
     *    若项本身无 parentCid 则回退至 fallbackParentCid，确保跨目录批量删除时子目录缓存同步清除；
     * 2. 子目录级联清理：针对被删除的文件夹，显式递归清理自身及其所有子孙目录的内存与磁盘缓存。
     *
     * @param fallbackParentCid 待同步的兜底父目录 ID
     * @param items 已成功从云端删除的项目列表
     */
    suspend fun syncCacheAfterDelete(
        fallbackParentCid: String?,
        items: List<BatchTargetItem>
    ) {
        if (items.isEmpty()) return

        // 1. 本地缓存多源同步：按条目自身记录的 parentCid 分组清理条目（对齐 moveGroup 模式）
        val groupedByParent = items.groupBy { it.parentCid?.takeIf { p -> p.isNotEmpty() } ?: fallbackParentCid }
        for ((targetParentCid, groupItems) in groupedByParent) {
            if (!targetParentCid.isNullOrEmpty()) {
                val fids = groupItems.map { it.fid }
                FileCacheManager.removeItems(targetParentCid, fids)
            }
        }

        // 2. 针对所有被删除的文件夹，深度递归清理其子孙目录缓存并向外广播 FolderDeleted 事件
        items.filter { it.isFolder }.forEach { folderItem ->
            FileCacheManager.removeFolderRecursively(folderItem.fid)
        }
    }
}
