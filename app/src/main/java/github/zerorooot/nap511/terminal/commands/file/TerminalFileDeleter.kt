package github.zerorooot.nap511.terminal.commands.file

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.util.FileCacheManager

/**
 * 待删除目标实体统一元数据模型
 *
 * @property parentCid 待删除项所在的父目录 ID
 * @property fid 待删除项的唯一文件/目录标识符（fileId 或 categoryId）
 * @property displayName 显示名称（用于输出日志与错误信息）
 * @property isFolder 是否为文件夹
 */
data class DeleteTargetItem(
    val parentCid: String,
    val fid: String,
    val displayName: String,
    val isFolder: Boolean
)

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
 * 终端批量/单项删除统一协调器
 *
 * 职责：
 * 1. 同目录多目标聚合：对于同一父目录下的多项删除操作，组装 `deleteMultiple` 批量协议单次发送，
 *    避免 N 次串行网络往返，并在文件数过多时按安全批次分块（Chunking）；
 * 2. 优雅降级：单项目或父目录 ID 缺失时无缝退化为 `delete(pid, fid)` 单项删除；
 * 3. 缓存双重级联清理：删除成功后，调用 [FileCacheManager.removeItems] 更新父目录条目，
 *    并对其中所有目录项显式调用 [FileCacheManager.removeFolderRecursively] 递归清理整棵子树缓存。
 */
object TerminalFileDeleter {

    /**
     * 单次 deleteMultiple 请求最大包含的项目数阈值，超出时切片分批发送，防止 HTTP 请求体过大
     */
    const val BATCH_CHUNK_SIZE = 100

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
        items: List<DeleteTargetItem>
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
        items: List<DeleteTargetItem>
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
