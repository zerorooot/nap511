package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.util.FileCacheManager

/**
 * 待批量操作（删除/移动）目标实体统一元数据模型
 *
 * @property parentCid 待操作项原本所在的父目录 ID
 * @property fid 待操作项的唯一文件/目录标识符（fileId 或 categoryId）
 * @property displayName 显示名称（用于输出日志与错误信息）
 * @property isFolder 是否为文件夹
 */
data class BatchTargetItem(
    val parentCid: String,
    val fid: String,
    val displayName: String,
    val isFolder: Boolean
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
        if (items.isEmpty()) {
            return BatchOpResult(totalCount = 0, successCount = 0, failureCount = 0)
        }

        var successCount = 0
        var firstError = ""
        var anyChunkSucceeded = false

        try {
            val chunks = items.chunked(BATCH_CHUNK_SIZE)
            for (chunk in chunks) {
                // 组装 115 files/move 协议表单: pid + fid[0]..fid[N]
                val data = HashMap<String, String>().apply {
                    put("pid", targetDestCid)
                    chunk.forEachIndexed { index, item ->
                        put("fid[$index]", item.fid)
                    }
                }

                val res = ctx.fileRepository.move(data)
                if (res.state) {
                    anyChunkSucceeded = true
                    successCount += chunk.size

                    // 本地缓存多源同步：按源目录 parentCid 分组清理条目
                    val groupedBySourceParent = chunk.groupBy { it.parentCid }
                    for ((srcParentCid, srcItems) in groupedBySourceParent) {
                        if (srcParentCid.isNotEmpty()) {
                            FileCacheManager.removeItems(srcParentCid, srcItems.map { it.fid })
                        }
                    }
                } else {
                    firstError = res.error.ifEmpty { res.message.ifEmpty { "未知错误" } }
                    break // 熔断后续切片
                }
            }
        } finally {
            // 只要有任意切片移动成功，立即强制刷新目标目录缓存，从服务端拉取最新列表
            if (anyChunkSucceeded) {
                ctx.listDirectory(cid = targetDestCid, forceRefresh = true)
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
     * 对同一父目录 [parentCid] 下的一组待删除项执行删除并同步本地缓存
     *
     * @param ctx 终端执行上下文
     * @param parentCid 待删除项所在的父目录 ID
     * @param items 归属于该父目录的待删除目标列表
     * @return 删除操作结果
     */
    suspend fun deleteGroup(
        ctx: TerminalContext,
        parentCid: String,
        items: List<BatchTargetItem>
    ): BaseReturnMessage {
        if (items.isEmpty()) {
            return BaseReturnMessage(state = true, message = "无需删除")
        }

        // 1. 降级分支：单项目或 parentCid 为空时走单项删除
        if (items.size == 1 || parentCid.isEmpty()) {
            val single = items.first()
            val res = ctx.fileRepository.delete(pid = parentCid, fid = single.fid)
            if (res.state) {
                syncCacheAfterDelete(parentCid, listOf(single))
            }
            return res
        }

        // 2. 批量分支：按安全阈值切片并调用 deleteMultiple
        val chunks = items.chunked(BATCH_CHUNK_SIZE)
        var lastSuccessResult = BaseReturnMessage(state = true)

        for (chunk in chunks) {
            val data = HashMap<String, String>().apply {
                put("ignore_warn", "1")
                put("pid", parentCid)
                chunk.forEachIndexed { index, item ->
                    put("fid[$index]", item.fid)
                }
            }

            val res = ctx.fileRepository.deleteMultiple(data)
            if (res.state) {
                syncCacheAfterDelete(parentCid, chunk)
                lastSuccessResult = res
            } else {
                return res
            }
        }

        return lastSuccessResult
    }

    /**
     * 成功删除后的本地缓存双重同步：
     * 1. 父目录列表更新：就地从父目录缓存列表中批量剔除被删除条目并递减计数；
     * 2. 子目录级联清理：针对被删除的文件夹，显式递归清理自身及其所有子孙目录的内存与磁盘缓存。
     *
     * @param parentCid 待同步的父目录 ID
     * @param items 已成功从云端删除的项目列表
     */
    suspend fun syncCacheAfterDelete(
        parentCid: String,
        items: List<BatchTargetItem>
    ) {
        if (items.isEmpty()) return

        // 1. 就地从父目录缓存中批量剔除条目
        if (parentCid.isNotEmpty()) {
            val fids = items.map { it.fid }
            FileCacheManager.removeItems(parentCid, fids)
        }

        // 2. 针对所有被删除的文件夹，深度递归清理其子孙目录缓存并向外广播 FolderDeleted 事件
        items.filter { it.isFolder }.forEach { folderItem ->
            FileCacheManager.removeFolderRecursively(folderItem.fid)
        }
    }
}
