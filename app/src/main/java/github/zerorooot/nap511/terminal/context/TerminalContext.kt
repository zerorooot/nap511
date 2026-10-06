package github.zerorooot.nap511.terminal.context

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
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
    /**
     * 目标是目录
     *
     * @param cid 目录自身的分类 ID（Category ID）
     * @param path 规范化后的绝对路径（如 "/根目录/t1/sub"）
     * @param parentCid 父级目录的分类 ID（用于 delete、move 等需要指定父目录的 API 操作）
     * @param name 目录名称（如 "sub"）
     * @param folderBean 该目录对应的 FileBean 元数据对象（若有）
     */
    data class Directory(
        val cid: String,
        val path: String,
        val parentCid: String? = null,
        val name: String = "",
        val folderBean: FileBean? = null
    ) : ResolvedTarget()

    /**
     * 目标是普通文件
     *
     * @param file 文件元数据对象
     * @param parentCid 文件所在父级目录的分类 ID
     * @param fullPath 规范化后的完整路径
     */
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
        val res = resolveTargetInternal(target)
        if (res != null) return@withContext res

        val trimmed = target.trim()
        if (!trimmed.startsWith("/") && !trimmed.startsWith("~") && currentCid != "0") {
            return@withContext resolveTargetInternal("/$trimmed")
        }
        null
    }

    private suspend fun resolveTargetInternal(target: String): ResolvedTarget? {
        val trimmed = target.trim()
        // 空路径或 "." 表示当前工作目录
        if (trimmed.isEmpty() || trimmed == ".") {
            val name = if (currentPath == "/" || currentPath == "/根目录") "根目录" else currentPath.substringAfterLast('/')
            return ResolvedTarget.Directory(currentCid, currentPath, parentCid = null, name = name)
        }

        // 根目录快捷表示
        if (trimmed == "/" || trimmed == "~" || trimmed == "/根目录" || trimmed == "/根目录/") {
            return ResolvedTarget.Directory("0", "/根目录", parentCid = null, name = "根目录")
        }

        val hasTrailingSlash = trimmed.endsWith("/")

        // 路径切分并判断是否为绝对路径（以 "/"、"~" 或 "根目录" 开头）
        val isAbsolute = trimmed.startsWith("/") || trimmed == "根目录" || trimmed.startsWith("根目录/")
        var rawSegments = trimmed.split("/").filter { it.isNotEmpty() && it != "." }

        // 如果第一段是 "根目录"，忽略该段（因为 CID "0" 对应网盘根目录）
        if (rawSegments.firstOrNull() == "根目录") {
            rawSegments = rawSegments.drop(1)
        }

        if (rawSegments.isEmpty()) {
            val fallbackName = if (isAbsolute) "根目录" else (currentPath.split("/")
                .lastOrNull { it.isNotEmpty() } ?: "根目录")
            return ResolvedTarget.Directory(
                if (isAbsolute) "0" else currentCid,
                if (isAbsolute) "/根目录" else currentPath,
                parentCid = null,
                name = fallbackName
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

        // 记录上一级的 CID 与最后一级的 FileBean，用于构造最终 Directory 对象的 parentCid 和 name
        var lastParentCid: String? = null
        var lastFolderBean: FileBean? = null

        for (i in segments.indices) {
            val segment = segments[i]
            val isLast = (i == segments.size - 1)

            if (segment == "..") {
                // 返回上一级目录
                if (currentSegments.size > 1) {
                    currentSegments.removeAt(currentSegments.size - 1)
                }
                startCid = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) {
                    "0"
                } else {
                    findCidByPathSegments(currentSegments) ?: "0"
                }
                lastParentCid = null
                lastFolderBean = null
                if (isLast) {
                    val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
                    return ResolvedTarget.Directory(
                        cid = startCid,
                        path = resolvedPath,
                        parentCid = null,
                        name = currentSegments.lastOrNull() ?: "根目录",
                        folderBean = null
                    )
                }
            } else {
                val files = listDirectory(startCid)
                if (isLast && !hasTrailingSlash) {
                    // 最后一级且未以 '/' 结尾：先匹配同名目录，若无则匹配同名文件
                    val folder = files.firstOrNull { it.isFolder && it.name == segment }
                    if (folder != null) {
                        currentSegments.add(segment)
                        val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
                        return ResolvedTarget.Directory(
                            cid = folder.categoryId,
                            path = resolvedPath,
                            parentCid = startCid,
                            name = folder.name,
                            folderBean = folder
                        )
                    }
                    val file = files.firstOrNull { !it.isFolder && it.name == segment }
                    if (file != null) {
                        currentSegments.add(segment)
                        val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
                        return ResolvedTarget.File(file, startCid, resolvedPath)
                    }
                    return null
                } else {
                    // 中间层级或末尾带 '/'：必须严格匹配为目录
                    val folder = files.firstOrNull { it.isFolder && it.name == segment }
                        ?: return null
                    lastParentCid = startCid
                    lastFolderBean = folder
                    startCid = folder.categoryId
                    currentSegments.add(segment)
                }
            }
        }

        val resolvedPath = if (currentSegments.isEmpty() || currentSegments == listOf("根目录")) "/根目录" else "/" + currentSegments.joinToString("/")
        return ResolvedTarget.Directory(
            cid = startCid,
            path = resolvedPath,
            parentCid = lastParentCid,
            name = lastFolderBean?.name ?: (currentSegments.lastOrNull() ?: "根目录"),
            folderBean = lastFolderBean
        )
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
     * 当执行了文件增删改（mkdir/rm/mv）时，使对应目录的缓存失效（备用降级）
     */
    suspend fun invalidateCache(cid: String = currentCid) {
        fileCacheManager.remove(cid)
    }

    /**
     * 就地从父目录缓存中移除指定文件或目录，并递减计数；若删除项为目录，则级联清理其子孙目录缓存
     */
    suspend fun removeCachedFile(parentCid: String, fid: String, isFolder: Boolean? = null) {
        val cache = fileCacheManager[parentCid]
        var targetItem: FileBean? = null
        if (cache != null) {
            targetItem = cache.fileBeanList.firstOrNull { it.fileId == fid || it.categoryId == fid }
            if (targetItem != null) {
                cache.fileBeanList.remove(targetItem)
                cache.count = (cache.count - 1).coerceAtLeast(0)
            }
        }
        val actualIsFolder = isFolder ?: (targetItem?.isFolder ?: false)
        if (actualIsFolder) {
            val targetFolderCid = targetItem?.categoryId?.ifEmpty { fid } ?: fid
            removeFolderCacheRecursively(targetFolderCid)
        }
    }

    /**
     * 递归清理目录及其所有子孙目录在 FileCacheManager 中的缓存（对齐 FileViewModel.removeFolderCacheRecursively 策略）
     */
    suspend fun removeFolderCacheRecursively(folderCid: String) {
        suspend fun walk(cid: String) {
            val list = fileCacheManager[cid]?.fileBeanList ?: emptyList()
            for (item in list) {
                if (item.isFolder) {
                    val subFolderCid = item.categoryId.ifEmpty { item.fileId }
                    if (subFolderCid.isNotEmpty()) {
                        walk(subFolderCid)
                    }
                }
            }
            fileCacheManager.remove(cid)
        }
        walk(folderCid)
    }

    /**
     * 就地重命名父目录缓存中的文件或子目录，若为目录则同步更新子目录自身缓存中的面包屑末级名称
     */
    suspend fun renameCachedFile(parentCid: String, fid: String, newName: String) {
        val cache = fileCacheManager[parentCid] ?: return
        val index = cache.fileBeanList.indexOfFirst { it.fileId == fid || it.categoryId == fid }
        if (index >= 0) {
            val oldBean = cache.fileBeanList[index]
            cache.fileBeanList[index] = oldBean.copy(name = newName)
            if (oldBean.isFolder) {
                val folderCid = oldBean.categoryId.ifEmpty { fid }
                fileCacheManager[folderCid]?.let { subCache ->
                    subCache.path.lastOrNull()?.let { it.name = newName }
                }
            }
        }
    }

    /**
     * 就地向父目录缓存中追加新创建的目录，并预埋新目录自身的空缓存
     */
    suspend fun addCachedFolder(parentCid: String, folderName: String, newCid: String) {
        val parentCache = fileCacheManager[parentCid]
        if (parentCache != null) {
            if (parentCache.fileBeanList.none { it.isFolder && it.name == folderName }) {
                val newFolderBean = FileBean(
                    name = folderName,
                    categoryId = newCid,
                    fileId = newCid,
                    isFolder = true
                )
                parentCache.fileBeanList.add(newFolderBean)
                parentCache.count = (parentCache.count + 1)
            }
        }
        // 预埋新目录自身的空缓存（若未创建）
        if (!fileCacheManager.containsKey(newCid)) {
            val parentPathList = parentCache?.path ?: emptyList()
            val newPathList = parentPathList + PathBean(cid = newCid, name = folderName, pid = parentCid)
            val newFilesBean = FilesBean(
                fileBeanList = arrayListOf(),
                cid = newCid,
                count = 0,
                order = "",
                path = newPathList
            )
            fileCacheManager.put(newCid, newFilesBean)
        }
    }

    /**
     * 就地向父目录缓存追加普通文件条目
     */
    suspend fun addCachedFile(parentCid: String, fileBean: FileBean) {
        val cache = fileCacheManager[parentCid] ?: return
        if (cache.fileBeanList.none { !it.isFolder && it.name == fileBean.name }) {
            cache.fileBeanList.add(fileBean)
            cache.count = (cache.count + 1)
        }
    }

    /**
     * 就地处理跨目录移动：从源父目录移出，并添加到目标目录缓存中
     */
    suspend fun moveCachedFile(srcParentCid: String, targetCid: String, fid: String) {
        val srcCache = fileCacheManager[srcParentCid]
        val targetBean = srcCache?.fileBeanList?.find { it.fileId == fid || it.categoryId == fid }
        if (srcCache != null && targetBean != null) {
            srcCache.fileBeanList.remove(targetBean)
            srcCache.count = (srcCache.count - 1).coerceAtLeast(0)
        }
        if (targetBean != null) {
            if (targetBean.isFolder) {
                val folderCid = targetBean.categoryId.ifEmpty { fid }
                addCachedFolder(targetCid, targetBean.name, folderCid)
                // 若移动的是文件夹，由于其完整层级路径发生变更，清理被移动文件夹本身的缓存以保证下次进入时重新生成正确面包屑
                fileCacheManager.remove(folderCid)
            } else {
                val movedBean = targetBean.copy(categoryId = targetCid)
                addCachedFile(targetCid, movedBean)
            }
        }
    }

    /**
     * 交互式确认（例如 rm 未带 -y 时调用）
     */
    suspend fun confirm(prompt: String): Boolean {
        return onConfirmRequest?.invoke(prompt) ?: true
    }
}
