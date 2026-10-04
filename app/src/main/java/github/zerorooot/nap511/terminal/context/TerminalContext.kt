package github.zerorooot.nap511.terminal.context

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.FileOpener
import github.zerorooot.nap511.viewmodel.formatFileBeanList
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 目标路径解析结果类型
 */
sealed class ResolvedTarget {
    /** 目标是目录 */
    data class Directory(val cid: String, val path: String) : ResolvedTarget()

    /** 目标是普通文件 */
    data class File(val file: FileBean, val parentCid: String, val fullPath: String) :
        ResolvedTarget()
}

/**
 * 终端执行会话上下文
 * 维护当前终端的工作目录、115网盘仓库层接口、缓存管理器、路径解析及交互回调
 */
class TerminalContext(
    initialCid: String = "0",
    initialPath: String = "/",
    initialPathList: List<PathBean> = emptyList(),
    val fileRepository: FileRepository = FileRepository.getInstance(),
    val fileCacheManager: FileCacheManager = FileCacheManager,
    val onConfirmRequest: (suspend (prompt: String) -> Boolean)? = null,
    val onDirectoryChanged: ((cid: String, path: String) -> Unit)? = null,
    var fileOpener: FileOpener? = null
) {
    @Volatile
    var currentCid: String = initialCid
        private set

    @Volatile
    var currentPath: String = initialPath
        private set

    val pathList: List<PathBean>
        field = initialPathList.toMutableList()

    /**
     * 更新当前工作目录
     */
    fun updateDirectory(cid: String, path: String, newPathList: List<PathBean>? = null) {
        currentCid = cid
        currentPath = if (path.startsWith("/")) path else "/$path"
        if (newPathList != null) {
            pathList.clear()
            pathList.addAll(newPathList)
        } else {
            val index = pathList.indexOfFirst { it.cid == cid }
            if (index != -1) {
                val trimmedList = pathList.take(index + 1)
                pathList.clear()
                pathList.addAll(trimmedList)
            } else {
                val cachedPath = fileCacheManager.getDate(cid)?.path
                if (!cachedPath.isNullOrEmpty()) {
                    pathList.clear()
                    pathList.addAll(cachedPath)
                }
            }
        }
        onDirectoryChanged?.invoke(currentCid, currentPath)
    }

    /**
     * Cache-First 获取指定目录下的文件列表
     * 1. 优先从 FileCacheManager 读取
     * 2. 缓存未命中或 forceRefresh == true 时，请求 FileRepository.getFiles 并存入缓存
     */
    suspend fun listDirectory(
        cid: String = currentCid,
        forceRefresh: Boolean = false
    ): List<FileBean> =
        withContext(Dispatchers.IO) {
            if (!forceRefresh) {
                val cached = fileCacheManager[cid]
                if (cached != null) {
                    return@withContext cached.fileBeanList
                }
            }

            // 请求网络
            return@withContext try {
                val filesBean = fileRepository.getFiles(cid = cid)
                // 规范化 isFolder
                val normalizedList = formatFileBeanList(filesBean.fileBeanList)
                filesBean.fileBeanList = normalizedList
                fileCacheManager.put(cid, filesBean)
                normalizedList
            } catch (e: Exception) {
                // 如果网络请求失败且之前有旧缓存，尽量兜底返回
                fileCacheManager.getDate(cid)?.fileBeanList ?: emptyList()
            }
        }


    /**
     * 智能解析目标路径（支持识别普通文件与目录）
     * - 中间路径段必须全部为有效目录；
     * - 若路径末尾带 '/' 则仅匹配目录；
     * - 若路径末尾不带 '/'，优先匹配同名目录，其次匹配同名普通文件。
     *
     * @param target 目标路径（支持绝对路径、相对路径、~、..、. 等）
     * @return 匹配成功返回 ResolvedTarget，不存在则返回 null
     */
    suspend fun resolveTarget(target: String): ResolvedTarget? = withContext(Dispatchers.IO) {
        val trimmed = target.trim()
        if (trimmed.isEmpty() || trimmed == ".") {
            return@withContext ResolvedTarget.Directory(currentCid, currentPath)
        }

        if (trimmed == "/" || trimmed == "~" || trimmed == "/根目录" || trimmed == "/根目录/") {
            return@withContext ResolvedTarget.Directory("0", "/根目录")
        }

        val hasTrailingSlash = trimmed.endsWith("/")

        // 分割路径段
        val isAbsolute = trimmed.startsWith("/")
        var rawSegments = trimmed.split("/").filter { it.isNotEmpty() && it != "." }

        // 如果是绝对路径且第一段是 "根目录"，忽略该段（因为 CID "0" 就是 "根目录"）
        if (isAbsolute && rawSegments.firstOrNull() == "根目录") {
            rawSegments = rawSegments.drop(1)
        }

        if (rawSegments.isEmpty()) {
            return@withContext ResolvedTarget.Directory(
                if (isAbsolute) "0" else currentCid,
                if (isAbsolute) "/根目录" else currentPath
            )
        }

        val segments = rawSegments
        var startCid = if (isAbsolute) "0" else currentCid
        val currentSegments = if (isAbsolute) {
            mutableListOf("根目录")
        } else {
            val list = currentPath.split("/").filter { it.isNotEmpty() }.toMutableList()
            if (list.isEmpty()) mutableListOf("根目录") else list
        }

        for (i in segments.indices) {
            val segment = segments[i]
            val isLast = (i == segments.size - 1)

            if (segment == "..") {
                if (currentSegments.isNotEmpty()) {
                    currentSegments.removeAt(currentSegments.size - 1)
                }
                startCid = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) {
                    "0"
                } else {
                    findCidByPathSegments(currentSegments) ?: "0"
                }
                if (isLast) {
                    val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
                    return@withContext ResolvedTarget.Directory(startCid, resolvedPath)
                }
            } else {
                val files = listDirectory(startCid)
                if (isLast && !hasTrailingSlash) {
                    // 最后一级且未以 '/' 结尾：先匹配目录，再匹配文件
                    val folder = files.firstOrNull { it.isFolder && it.name == segment }
                    if (folder != null) {
                        currentSegments.add(segment)
                        val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
                        return@withContext ResolvedTarget.Directory(folder.categoryId, resolvedPath)
                    }
                    val file = files.firstOrNull { !it.isFolder && it.name == segment }
                    if (file != null) {
                        currentSegments.add(segment)
                        val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
                        return@withContext ResolvedTarget.File(file, startCid, resolvedPath)
                    }
                    return@withContext null
                } else {
                    // 中间层级或末尾带 '/'：必须为目录
                    val folder = files.firstOrNull { it.isFolder && it.name == segment }
                        ?: return@withContext null
                    startCid = folder.categoryId
                    currentSegments.add(segment)
                }
            }
        }

        val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
        ResolvedTarget.Directory(startCid, resolvedPath)
    }

    /**
     * 解析目标目录路径（支持 "/", "..", ".", 绝对路径与相对路径）
     * 仅当目标为有效目录时匹配成功；若目标为普通文件则返回 null
     *
     * @return 匹配成功返回 Pair(目标CID, 规范化后的绝对路径)，若不存在或非目录则返回 null
     */
    suspend fun resolvePath(target: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val resolved = resolveTarget(target) ?: return@withContext null
        when (resolved) {
            is ResolvedTarget.Directory -> Pair(resolved.cid, resolved.path)
            is ResolvedTarget.File -> null
        }
    }

    private suspend fun findCidByPathSegments(segments: List<String>): String? {
        if (segments.isEmpty() || segments == listOf("根目录")) {
            return "0"
        }
        if (segments.size <= pathList.size) {
            val isMatch = segments.indices.all { i ->
                if (i == 0 && segments[i] == "根目录") true
                else pathList[i].name == segments[i]
            }
            if (isMatch) {
                return pathList[segments.size - 1].cid
            }
        }
        var cid = "0"
        for (seg in segments) {
            if (seg == "根目录") continue
            val files = listDirectory(cid)
            val folder = files.firstOrNull { it.isFolder && it.name == seg } ?: return null
            cid = folder.categoryId
        }
        return cid
    }

    /**
     * 当执行了文件增删改（mkdir/rm/mv）时，使对应目录的缓存失效
     */
    suspend fun invalidateCache(cid: String = currentCid) {
        fileCacheManager.remove(cid)
    }

    /**
     * 交互式确认（例如 rm 未带 -y 时调用）
     */
    suspend fun confirm(prompt: String): Boolean {
        return onConfirmRequest?.invoke(prompt) ?: true
    }
}
