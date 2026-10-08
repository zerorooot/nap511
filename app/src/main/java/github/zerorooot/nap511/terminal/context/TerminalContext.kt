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
     * @param pathList 该目录对应的完整 PathBean 面包屑层级链表
     */
    data class Directory(
        val cid: String,
        val path: String,
        val parentCid: String? = null,
        val name: String = "",
        val folderBean: FileBean? = null,
        val pathList: List<PathBean>
    ) : ResolvedTarget()

    /**
     * 目标是普通文件
     *
     * @param file 文件元数据对象
     * @param parentCid 文件所在父级目录的分类 ID
     * @param fullPath 规范化后的完整路径
     * @param parentPathList 文件所在父级目录的 PathBean 面包屑层级链表
     */
    data class File(
        val file: FileBean,
        val parentCid: String,
        val fullPath: String,
        val parentPathList: List<PathBean>
    ) : ResolvedTarget()
}

/**
 * 终端执行会话上下文
 * 维护当前终端的工作目录、115网盘仓库层接口、缓存管理器、路径解析及交互回调
 */
class TerminalContext(
    initialPathList: List<PathBean> = emptyList(),
    val fileRepository: FileRepository = FileRepository.getInstance(),
    val fileCacheManager: FileCacheManager = FileCacheManager,
    val onConfirmRequest: (suspend (prompt: String) -> Boolean)? = null,
    val onDirectoryChanged: ((cid: String, path: String) -> Unit)? = null,
    var fileOpener: FileOpener? = null
) {
    val pathList: MutableList<PathBean> = initialPathList.toMutableList()

    @Volatile
    var currentCid: String = initialPathList.currentCid()
        private set

    @Volatile
    var currentPath: String = initialPathList.toDisplayPath()
        private set

    /**
     * 当前工作目录的面包屑路径链表（保证非空且包含根节点）
     */
    val currentPathList: List<PathBean>
        get() = if (pathList.isEmpty()) {
            listOf(TerminalPathConstants.ROOT_PATH_BEAN)
        } else {
            pathList.toList()
        }

    /**
     * 更新当前工作目录（由 List<PathBean> 提供路径与层级结构）
     */
    fun updateDirectory(newPathList: List<PathBean>) {
        pathList.clear()
        pathList.addAll(newPathList)
        currentCid = pathList.currentCid()
        currentPath = pathList.toDisplayPath()
        onDirectoryChanged?.invoke(currentCid, currentPath)
    }

    /**
     * 根据解析所得目录目标直接更新工作目录（保持 PathBean 面包屑链完整，无缝同步）
     */
    fun updateDirectory(target: ResolvedTarget.Directory) {
        updateDirectory(target.pathList)
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
                fileCacheManager.putAndNotify(cid, filesBean)
                normalizedList
            } catch (e: Exception) {
                // 如果网络请求失败且之前有旧缓存，尽量兜底返回
                fileCacheManager[cid]?.fileBeanList ?: emptyList()
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
        val parsed = TerminalPath.parse(target)
        val res = resolveTargetInternal(parsed)
        if (res != null) return@withContext res

        // 回退机制：若在非根目录下相对解析未命中，尝试从网盘根目录解析（支持如跨目录寻址）
        if (!parsed.isAbsolute && currentCid != TerminalPathConstants.ROOT_CID) {
            val absParsed = TerminalPath.parse("/${target.trim()}")
            return@withContext resolveTargetInternal(absParsed)
        }
        null
    }

    private suspend fun resolveTargetInternal(parsedPath: TerminalPath): ResolvedTarget? {
        // 空路径或 "." 表示当前工作目录
        if (parsedPath.isCurrentDirectory) {
            val name = if (currentPath == "/" || currentPath == "/根目录") {
                TerminalPathConstants.ROOT_NAME
            } else {
                currentPath.substringAfterLast('/')
            }
            return ResolvedTarget.Directory(
                cid = currentCid,
                path = currentPath,
                parentCid = currentPathList.parentCid(),
                name = name,
                folderBean = null,
                pathList = currentPathList
            )
        }

        // 根目录快捷表示
        if (parsedPath.isRoot) {
            val rootList = listOf(TerminalPathConstants.ROOT_PATH_BEAN)
            return ResolvedTarget.Directory(
                cid = TerminalPathConstants.ROOT_CID,
                path = TerminalPathConstants.ROOT_DISPLAY_PATH,
                parentCid = null,
                name = TerminalPathConstants.ROOT_NAME,
                folderBean = null,
                pathList = rootList
            )
        }

        // 初始化工作路径链：绝对路径以 root 开始，相对路径以当前目录副本开始
        val workingPathList = if (parsedPath.isAbsolute) {
            mutableListOf(TerminalPathConstants.ROOT_PATH_BEAN)
        } else {
            currentPathList.toMutableList()
        }

        val segments = parsedPath.segments
        var lastFolderBean: FileBean? = null

        for (i in segments.indices) {
            val segment = segments[i]
            val isLast = (i == segments.size - 1)

            if (segment == "..") {
                // 返回上一级目录
                if (workingPathList.size > 1) {
                    workingPathList.removeAt(workingPathList.size - 1)
                }
                lastFolderBean = null
                if (isLast) {
                    return ResolvedTarget.Directory(
                        cid = workingPathList.currentCid(),
                        path = workingPathList.toDisplayPath(),
                        parentCid = workingPathList.parentCid(),
                        name = workingPathList.currentName(),
                        folderBean = null,
                        pathList = workingPathList.toList()
                    )
                }
            } else {
                val curCid = workingPathList.currentCid()
                val files = listDirectory(curCid)
                if (isLast && !parsedPath.hasTrailingSlash) {
                    // 最后一级且未以 '/' 结尾：先匹配同名目录，若无则匹配同名文件
                    val folder = files.firstOrNull { it.isFolder && it.name == segment }
                    if (folder != null) {
                        workingPathList.add(PathBean(cid = folder.categoryId, name = folder.name, pid = curCid))
                        return ResolvedTarget.Directory(
                            cid = folder.categoryId,
                            path = workingPathList.toDisplayPath(),
                            parentCid = curCid,
                            name = folder.name,
                            folderBean = folder,
                            pathList = workingPathList.toList()
                        )
                    }
                    val file = files.firstOrNull { !it.isFolder && it.name == segment }
                    if (file != null) {
                        val fullPath = workingPathList.toDisplayPath() + "/" + file.name
                        return ResolvedTarget.File(
                            file = file,
                            parentCid = curCid,
                            fullPath = fullPath,
                            parentPathList = workingPathList.toList()
                        )
                    }
                    return null
                } else {
                    // 中间层级或末尾带 '/'：必须严格匹配为目录
                    val folder = files.firstOrNull { it.isFolder && it.name == segment }
                        ?: return null
                    lastFolderBean = folder
                    workingPathList.add(PathBean(cid = folder.categoryId, name = folder.name, pid = curCid))
                }
            }
        }

        return ResolvedTarget.Directory(
            cid = workingPathList.currentCid(),
            path = workingPathList.toDisplayPath(),
            parentCid = workingPathList.parentCid(),
            name = lastFolderBean?.name ?: workingPathList.currentName(),
            folderBean = lastFolderBean,
            pathList = workingPathList.toList()
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

    /**
     * 解析目标目录并返回强类型 [ResolvedTarget.Directory]
     * 若目标不存在或为普通文件，则返回 null
     */
    suspend fun resolveDirectory(target: String): ResolvedTarget.Directory? = withContext(Dispatchers.IO) {
        when (val resolved = resolveTarget(target)) {
            is ResolvedTarget.Directory -> resolved
            else -> null
        }
    }

    /**
     * 当执行了文件增删改（mkdir/rm/mv）时，使对应目录的缓存失效（备用降级）
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
