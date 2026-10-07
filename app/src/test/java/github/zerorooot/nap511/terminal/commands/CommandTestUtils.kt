package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.CreateFolderMessage
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import github.zerorooot.nap511.util.FileCacheManager
import kotlinx.coroutines.flow.toList
import okhttp3.RequestBody
import java.io.InputStream

/**
 * 终端命令单元测试辅助函数与测试基础设施
 * 提供常用 Context、Engine、Mock 仓储及测试数据的快捷创建函数
 */

/**
 * 将命令管道执行的 Flow<TerminalOutput> 转换为纯文本列表 List<String>，便于断言比较
 */
internal suspend fun PipelineEngine.executeStrings(cmd: String, ctx: TerminalContext): List<String> =
    execute(cmd, ctx).toList().map { it.text }

/**
 * 快捷创建初始化的 TerminalContext
 *
 * @param rootCid 根目录 CID，默认为 "0"
 * @param rootName 根目录名称，默认为 "根目录"
 * @param fileRepository 绑定的文件仓库，默认为 null
 * @param onConfirmRequest 确认交互回调
 */
internal fun createTestContext(
    rootCid: String = "0",
    rootName: String = "根目录",
    fileRepository: FileRepository? = null,
    onConfirmRequest: (suspend (String) -> Boolean)? = null
): TerminalContext {
    val ctx = if (fileRepository != null) {
        TerminalContext(
            fileRepository = fileRepository,
            onConfirmRequest = onConfirmRequest
        )
    } else {
        TerminalContext(
            onConfirmRequest = onConfirmRequest
        )
    }
    ctx.updateDirectory(listOf(PathBean(rootCid, rootName, "0")))
    return ctx
}

/**
 * 快捷创建 PipelineEngine 引擎实例
 *
 * @param historyManager 历史记录管理器
 * @param historyList 预置的历史命令列表
 * @param onMemoryClear 清空历史回调
 */
internal fun createTestEngine(
    historyManager: TerminalHistoryManager? = null,
    historyList: List<String> = emptyList(),
    onMemoryClear: (() -> Unit)? = null
): PipelineEngine {
    val hm = historyManager ?: run {
        val tempFile = java.io.File.createTempFile("test_history", ".txt").apply {
            deleteOnExit()
            historyList.forEach { appendText("$it\n") }
        }
        TerminalHistoryManager(tempFile)
    }
    val registry = CommandRegistryFactory.createDefaultRegistry(hm, onMemoryClear ?: {})
    return PipelineEngine(registry)
}

/**
 * 快捷向 TerminalContext 的文件缓存中添加指定 CID 目录的文件列表
 */
internal suspend fun TerminalContext.putMockFiles(
    cid: String,
    files: List<FileBean>,
    path: List<PathBean> = listOf(PathBean("0", "根目录", "0"))
) {
    fileCacheManager.put(
        cid,
        FilesBean(
            fileBeanList = github.zerorooot.nap511.viewmodel.formatFileBeanList(files),
            cid = cid,
            count = files.size,
            order = "",
            path = path
        )
    )
}

/**
 * 快捷构建文件夹 FileBean
 */
internal fun createMockFolder(
    name: String,
    categoryId: String,
    modifiedTime: String = "1000"
): FileBean = FileBean(
    name = name,
    categoryId = categoryId,
    isFolder = true,
    modifiedTime = modifiedTime
)

/**
 * 快捷构建普通文件 FileBean
 */
internal fun createMockFile(
    name: String,
    fileId: String,
    size: String = "1024",
    categoryId: String = "0",
    modifiedTime: String = "1000",
    pickCode: String = "",
    sha1: String = "",
    icoString: String = name.substringAfterLast('.', ""),
    playLong: Double = 0.0,
    isVideo: Int = if (name.substringAfterLast('.', "").lowercase() in listOf("mp4", "mkv", "avi", "flv", "mov", "wmv", "rmvb", "webm", "ts")) 1 else 0
): FileBean = FileBean(
    name = name,
    fileId = fileId,
    categoryId = categoryId,
    size = size,
    isFolder = false,
    modifiedTime = modifiedTime,
    pickCode = pickCode,
    sha1 = sha1,
    icoString = icoString,
    playLong = playLong,
    isVideo = isVideo
)

/**
 * 创建通用 Mock FileRepository，用于驱动依赖 FileRepository 的测试用例
 */
internal fun createTestMockRepository(
    initialFolderId: Int = 1000,
    recycleBeans: List<RecycleBean> = emptyList(),
    onMove: ((Map<String, String>) -> Unit)? = null,
    onDelete: ((pid: String, fid: String) -> Unit)? = null,
    onRevert: ((rid: String) -> Unit)? = null
): TestMockFileRepository = TestMockFileRepository(
    initialFolderId = initialFolderId,
    recycleBeans = recycleBeans,
    onMove = onMove,
    onDelete = onDelete,
    onRevert = onRevert
)

internal open class TestMockFileRepository(
    initialFolderId: Int = 1000,
    private val recycleBeans: List<RecycleBean> = emptyList(),
    private val onMove: ((Map<String, String>) -> Unit)? = null,
    private val onDelete: ((pid: String, fid: String) -> Unit)? = null,
    private val onRevert: ((rid: String) -> Unit)? = null
) : FileRepository() {

    var counter: Int = initialFolderId
    val deletedItems = mutableListOf<Pair<String, String>>()
    val movedItems = mutableListOf<Map<String, String>>()
    val revertedRids = mutableListOf<String>()
    val mockDownloadStreams = mutableMapOf<String, String>()
    val directoryFilesMap = mutableMapOf<String, MutableList<FileBean>>()
    val folderPaths = mutableMapOf<String, List<PathBean>>()

    override fun getDownloadInputStream(pickCode: String, fileId: String): InputStream? {
        val content = mockDownloadStreams[fileId] ?: mockDownloadStreams[pickCode]
        return content?.byteInputStream(Charsets.UTF_8)
    }

    override suspend fun getFiles(
        cid: String,
        showDir: Int,
        aid: Int,
        asc: Int,
        naturalSort: Int,
        order: String,
        limit: Int,
        format: String
    ): FilesBean {
        val existing = FileCacheManager.getDate(cid)?.fileBeanList ?: emptyList()
        val created = directoryFilesMap[cid] ?: emptyList()
        val combined = (existing + created).distinctBy { if (it.isFolder) it.categoryId else it.fileId }
        val path = FileCacheManager.getDate(cid)?.path?.takeIf { it.isNotEmpty() }
            ?: folderPaths[cid]
            ?: if (cid == "0") listOf(PathBean("0", "根目录", "0")) else emptyList()
        return FilesBean(
            fileBeanList = ArrayList(combined),
            cid = cid,
            count = combined.size,
            order = order,
            path = path
        )
    }

    override suspend fun createFolder(pid: String, folderName: String): CreateFolderMessage {
        val newId = (counter++).toString()
        val newFolder = FileBean(
            name = folderName,
            categoryId = newId,
            fileId = "",
            isFolder = true
        )
        directoryFilesMap.getOrPut(pid) { mutableListOf() }.add(newFolder)
        val parentPath = folderPaths[pid] ?: FileCacheManager.getDate(pid)?.path?.takeIf { it.isNotEmpty() } ?: listOf(PathBean("0", "根目录", "0"))
        folderPaths[newId] = parentPath + PathBean(newId, folderName, pid)
        return CreateFolderMessage(state = true, cid = newId, fileId = newId, fileName = folderName)
    }

    override suspend fun delete(pid: String, fid: String): BaseReturnMessage {
        deletedItems.add(pid to fid)
        directoryFilesMap[pid]?.removeAll { it.fileId == fid || it.categoryId == fid }
        onDelete?.invoke(pid, fid)
        return BaseReturnMessage(state = true)
    }

    val deletedMultipleCalls = mutableListOf<Map<String, String>>()
    var onDeleteMultiple: ((Map<String, String>) -> Unit)? = null

    override suspend fun deleteMultiple(data: Map<String, String>): BaseReturnMessage {
        deletedMultipleCalls.add(data)
        val pid = data["pid"].orEmpty()
        val fids = data.filterKeys { it.startsWith("fid[") }.values.toSet()
        for (fid in fids) {
            deletedItems.add(pid to fid)
        }
        directoryFilesMap[pid]?.removeAll { it.fileId in fids || it.categoryId in fids }
        onDeleteMultiple?.invoke(data)
        return BaseReturnMessage(state = true)
    }

    override suspend fun move(body: Map<String, String>): BaseReturnMessage {
        movedItems.add(body)
        val targetPid = body["pid"]
        if (targetPid != null) {
            val fids = body.filterKeys { it.startsWith("fid[") }.values
            for (fid in fids) {
                for ((pid, list) in directoryFilesMap) {
                    val found = list.find { it.fileId == fid || it.categoryId == fid }
                    if (found != null) {
                        list.remove(found)
                        directoryFilesMap.getOrPut(targetPid) { mutableListOf() }.add(found.copy(categoryId = targetPid))
                    }
                }
            }
        }
        onMove?.invoke(body)
        return BaseReturnMessage(state = true)
    }

    override suspend fun rename(renameBean: RequestBody): BaseReturnMessage {
        return BaseReturnMessage(state = true)
    }

    override suspend fun recycleList(
        aid: String,
        cid: String,
        offset: String,
        limit: String
    ): RecycleInfo {
        return RecycleInfo(state = true, recycleBeanList = ArrayList(recycleBeans))
    }

    override suspend fun revert(rid: String): BaseReturnMessage {
        revertedRids.add(rid)
        val matched = recycleBeans.firstOrNull { it.id == rid }
        if (matched != null) {
            val restored = FileBean(
                name = matched.fileName,
                fileId = matched.id,
                categoryId = matched.cid,
                isFolder = matched.isFolder
            )
            directoryFilesMap.getOrPut(matched.cid) { mutableListOf() }.add(restored)
        }
        onRevert?.invoke(rid)
        return BaseReturnMessage(state = true)
    }

    override suspend fun recycleCleanAll(password: String): BaseReturnMessage {
        return BaseReturnMessage(state = true)
    }
}
