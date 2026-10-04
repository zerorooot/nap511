package github.zerorooot.nap511.terminal.context

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.Route
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.FileCacheManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
    val onNavigate: ((Route) -> Unit)? = null,
    val onConfirmRequest: (suspend (prompt: String) -> Boolean)? = null,
    val onDirectoryChanged: ((cid: String, path: String) -> Unit)? = null
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
        }
        onDirectoryChanged?.invoke(currentCid, currentPath)
    }

    /**
     * Cache-First 获取指定目录下的文件列表
     * 1. 优先从 FileCacheManager 读取
     * 2. 缓存未命中或 forceRefresh == true 时，请求 FileRepository.getFiles 并存入缓存
     */
    suspend fun listDirectory(cid: String = currentCid, forceRefresh: Boolean = false): List<FileBean> =
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
                val normalizedList = filesBean.fileBeanList.map { file ->
                    val isFolder = file.fileId.isEmpty()
                    val actualFileId = if (isFolder) file.categoryId else file.fileId
                    file.copy(
                        isFolder = isFolder,
                        fileId = actualFileId
                    )
                }
                filesBean.fileBeanList = ArrayList(normalizedList)
                fileCacheManager.put(cid, filesBean)
                normalizedList
            } catch (e: Exception) {
                // 如果网络请求失败且之前有旧缓存，尽量兜底返回
                fileCacheManager.getDate(cid)?.fileBeanList ?: emptyList()
            }
        }

    /**
     * 解析目标路径（支持 "/", "..", ".", 绝对路径与相对路径）
     * @return 匹配成功返回 Pair(目标CID, 规范化后的绝对路径)，若不存在则返回 null
     */
    suspend fun resolvePath(target: String): Pair<String, String>? = withContext(Dispatchers.IO) {
        val trimmed = target.trim()
        if (trimmed.isEmpty() || trimmed == ".") {
            return@withContext Pair(currentCid, currentPath)
        }

        if (trimmed == "/" || trimmed == "~") {
            return@withContext Pair("0", "/")
        }

        // 处理 .. 上一级
        if (trimmed == "..") {
            if (currentCid == "0" || currentPath == "/") {
                return@withContext Pair("0", "/")
            }
            if (pathList.size >= 2) {
                val parent = pathList[pathList.size - 2]
                val parentPath = "/" + pathList.dropLast(1).joinToString("/") { it.name }.trimStart('/')
                return@withContext Pair(parent.cid, parentPath.ifEmpty { "/" })
            } else {
                // 默认退回根目录
                return@withContext Pair("0", "/")
            }
        }

        // 分割路径段
        val isAbsolute = trimmed.startsWith("/")
        val segments = trimmed.split("/").filter { it.isNotEmpty() && it != "." }

        var startCid = if (isAbsolute) "0" else currentCid
        val currentSegments = if (isAbsolute) mutableListOf() else currentPath.split("/").filter { it.isNotEmpty() }.toMutableList()

        for (segment in segments) {
            if (segment == "..") {
                if (currentSegments.isNotEmpty()) {
                    currentSegments.removeAt(currentSegments.size - 1)
                }
                // 需要解析出上级的 CID
                // 如果退回根
                startCid = if (currentSegments.isEmpty()) {
                    "0"
                } else {
                    // 从缓存链条中查找
                    findCidByPathSegments(currentSegments) ?: "0"
                }
            } else {
                val files = listDirectory(startCid)
                val folder = files.firstOrNull { it.isFolder && it.name == segment }
                    ?: return@withContext null // 路径未找到
                startCid = folder.categoryId
                currentSegments.add(segment)
            }
        }

        val resolvedPath = "/" + currentSegments.joinToString("/")
        Pair(startCid, resolvedPath)
    }

    private suspend fun findCidByPathSegments(segments: List<String>): String? {
        var cid = "0"
        for (seg in segments) {
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
