package github.zerorooot.nap511.repository.paging

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FileListSource
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.OrderBean
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.util.FileCacheManager
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.viewmodel.formatFileBeanList
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds

/**
 * 统一目录分页与缓存协同器标准实现
 */
class DirectoryPagingCoordinatorImpl(
    private val fileRepository: FileRepository = FileRepository.getInstance(),
    private val fileCacheManager: FileCacheManager = FileCacheManager
) : DirectoryPagingCoordinator {

    private fun logD(msg: String) = runCatching { XLog.d(msg) }
    private fun logI(msg: String) = runCatching { XLog.i(msg) }
    private fun logW(msg: String) = runCatching { XLog.w(msg) }
    private fun logE(msg: String, tr: Throwable? = null) = runCatching {
        if (tr != null) XLog.e(msg, tr) else XLog.e(msg)
    }

    private val flightMutex = Mutex()
    private val inFlightBatchTasks = ConcurrentHashMap<String, Deferred<List<FileBean>>>()

    override suspend fun fetchNextPage(
        source: FileListSource,
        currentList: List<FileBean>,
        currentPhysicalOffset: Int,
        currentTotalCount: Int,
        order: OrderBean,
        fcMix: Int
    ): PageAppendResult = withContext(Dispatchers.IO) {
        val limit = UserSessionManager.requestLimitCount

        logD("DirectoryPagingCoordinator.fetchNextPage start: source=$source, offset=$currentPhysicalOffset, limit=$limit, order=$order, fcMix=$fcMix")

        // 跨端消峰协同：若当前后台正在对该 Folder 进行全量批处理拉取，UI 步进直接等待其批处理完成并合并全集
        if (source is FileListSource.Folder) {
            val batchKey =
                "batch:${source.cid}:${order.type}:${order.asc}:$fcMix:false:${PagingOptions.MAX_PAGES_LIMIT}"
            val runningBatch = inFlightBatchTasks[batchKey]
            if (runningBatch != null) {
                logI("DirectoryPagingCoordinator: fetchNextPage detected in-flight batch task for cid=${source.cid}, joining...")
                val allFiles = runningBatch.await()
                return@withContext PageAppendResult(
                    rawNewItems = emptyList(),
                    combinedList = allFiles,
                    latestTotalCount = allFiles.size,
                    nextPhysicalOffset = allFiles.size,
                    isComplete = true
                )
            }
        }

        // 1. 根据数据源多态发起分页网络请求
        val res = when (source) {
            is FileListSource.Folder -> fileRepository.getFiles(
                cid = source.cid,
                order = order.type,
                asc = order.asc,
                offset = currentPhysicalOffset,
                fcMix = fcMix,
                limit = limit
            )

            is FileListSource.Search -> fileRepository.search(
                cid = source.cid,
                searchValue = source.keyword,
                offset = currentPhysicalOffset,
                limit = limit
            )

            is FileListSource.Filter -> fileRepository.filterFile(
                cid = source.cid,
                type = source.filterType,
                order = order.type,
                asc = order.asc,
                offset = currentPhysicalOffset,
                limit = limit,
                fcMix = fcMix
            )
        }

        val rawNewItems = res.fileBeanList
        val formattedNewItems = formatFileBeanList(rawNewItems)
        val nextPhysicalOffset = currentPhysicalOffset + rawNewItems.size

        // 2. 规范化去重合并（保证 key 唯一，杜绝 Compose 崩溃）
        val combinedList = (currentList + formattedNewItems).distinctBy { item ->
            item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
        }

        val latestTotalCount = if (res.count > 0) res.count else currentTotalCount

        // 3. 双重完备性校验（单批次不足 limit 或已满足总条目数）
        val isEndByBatchSize = rawNewItems.size < limit
        val isEndByCount = latestTotalCount > 0 && combinedList.size >= latestTotalCount
        val isComplete = isEndByBatchSize || isEndByCount

        // 4. 仅 Folder 模式就地更新内存与持久化缓存
        if (source is FileListSource.Folder) {
            val cachedData = fileCacheManager.getDate(source.cid)
            val updatedFilesBean = (cachedData ?: res).copy(
                fileBeanList = ArrayList(combinedList),
                count = latestTotalCount,
                order = order.type,
                isAsc = order.asc,
                fcMix = fcMix
            )
            fileCacheManager[source.cid] = updatedFilesBean
            logD("DirectoryPagingCoordinator: Updated cache for cid=${source.cid}, cachedSize=${combinedList.size}, count=$latestTotalCount, isComplete=$isComplete")
        }

        logD("DirectoryPagingCoordinator.fetchNextPage done: rawCount=${rawNewItems.size}, combinedCount=${combinedList.size}, nextOffset=$nextPhysicalOffset, isComplete=$isComplete")

        PageAppendResult(
            rawNewItems = formattedNewItems,
            combinedList = combinedList,
            latestTotalCount = latestTotalCount,
            nextPhysicalOffset = nextPhysicalOffset,
            isComplete = isComplete
        )
    }

    override suspend fun fetchDirectory(options: PagingOptions): List<FileBean> = supervisorScope {
        val key = options.batchFlightKey
        val task = flightMutex.withLock {
            inFlightBatchTasks[key]?.let {
                logI("DirectoryPagingCoordinator.fetchDirectory: Single-Flight hit for key=$key")
                return@withLock it
            }
            val deferred = async(Dispatchers.IO) {
                try {
                    executePagingLoop(options)
                } finally {
                    flightMutex.withLock { inFlightBatchTasks.remove(key) }
                }
            }
            inFlightBatchTasks[key] = deferred
            deferred
        }
        task.await()
    }

    private suspend fun executePagingLoop(options: PagingOptions): List<FileBean> {
        val cid = options.cid
        val oldCache = fileCacheManager[cid]

        // 1. 三位一体缓存匹配门禁（非强刷 + 缓存非空 + 满足 isFullyValid）
        if (!options.forceRefresh && oldCache != null && oldCache.isFullyValid(
                options.order,
                options.fcMix
            )
        ) {
            logD("DirectoryPagingCoordinator: Cache gate hit for cid=$cid, items=${oldCache.fileBeanList.size}")
            return oldCache.fileBeanList
        }

        // 2. 零时间衰减：未命中完整缓存时，一律从 0 开始全新分页拉取
        val stagedRawList = ArrayList<FileBean>()
        val limit = UserSessionManager.requestLimitCount
        var currentOffset = 0
        var pageCount = 0
        var latestTotalCount = 0
        var lastPathList = oldCache?.path ?: emptyList()

        logD("DirectoryPagingCoordinator: Starting paging loop for cid=$cid, maxPages=${options.maxPages}")

        try {
            while (true) {
                currentCoroutineContext().ensureActive()
                pageCount++

                val response = fileRepository.getFiles(
                    cid = cid,
                    offset = currentOffset,
                    limit = limit,
                    order = options.order.type,
                    asc = options.order.asc,
                    fcMix = options.fcMix
                )

                val rawBatch = response.fileBeanList
                latestTotalCount = response.count
                if (response.path.isNotEmpty()) {
                    lastPathList = response.path
                }

                stagedRawList.addAll(rawBatch)
                currentOffset += rawBatch.size // 物理游标递增

                logD("DirectoryPagingCoordinator: Page $pageCount loaded ${rawBatch.size} items (total staged: ${stagedRawList.size}/$latestTotalCount)")

                if (pageCount >= options.maxPages) {
                    logD("DirectoryPagingCoordinator: Reached maxPages=${options.maxPages}, terminating loop")
                    break
                }

                // 进度回调反馈
                if (pageCount >= 3) {
                    options.onProgress?.invoke(stagedRawList.size, latestTotalCount)
                }

                // 终止判断
                if (rawBatch.size < limit ||
                    (latestTotalCount > 0 && stagedRawList.size >= latestTotalCount) ||
                    rawBatch.isEmpty() ||
                    pageCount >= PagingOptions.MAX_PAGES_LIMIT
                ) {
                    break
                }

                // 防风控微延时
                delay(50L.milliseconds)
            }

            // 规范化与去重合并
            val formattedList = formatFileBeanList(stagedRawList)
            val deduplicatedList = formattedList.distinctBy { item ->
                item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
            }

            val finalFilesBean = FilesBean(
                fileBeanList = ArrayList(deduplicatedList),
                cid = cid,
                count = if (latestTotalCount > 0) latestTotalCount else deduplicatedList.size,
                order = options.order.type,
                isAsc = options.order.asc,
                fcMix = options.fcMix,
                path = lastPathList
            )

            // 事务性分阶段落盘提交：
            // 若为多页全量模式（大于 FAST_MODE_PAGES），落盘并广播 LocalUiUpdated，驱动 UI 统一响应刷新；
            // 若仅为快速短路单页模式（FAST_MODE_PAGES），仅写入缓存不向 UI 广播更新，避免干扰前台页面
            if (options.maxPages > PagingOptions.FAST_MODE_PAGES) {
                fileCacheManager.putAndNotify(cid, finalFilesBean)
                logI("DirectoryPagingCoordinator: Full directory paging completed and committed for cid=$cid (${deduplicatedList.size} items)")
            } else {
                fileCacheManager.put(cid, finalFilesBean)
                logD("DirectoryPagingCoordinator: Fast mode paging completed for cid=$cid (${deduplicatedList.size} items)")
            }

            return deduplicatedList
        } catch (e: Exception) {
            if (e is CancellationException) {
                logW("DirectoryPagingCoordinator: Paging loop cancelled for cid=$cid")
                throw e
            }
            logE("DirectoryPagingCoordinator: Paging loop error for cid=$cid", e)
            // 异常安全降级：若拉取中断，保留旧缓存兜底，绝不使用破损残卷冲毁旧数据
            return oldCache?.fileBeanList ?: emptyList()
        }
    }

    override fun fetchDirectoryStream(options: PagingOptions): Flow<List<FileBean>> = flow {
        var currentOffset = 0
        var pageCount = 0
        val limit = UserSessionManager.requestLimitCount
        val stagedStreamList = ArrayList<FileBean>()
        var latestCount = 0
        var lastPath = emptyList<github.zerorooot.nap511.bean.PathBean>()
        var isFullyConsumed = false

        logD("DirectoryPagingCoordinator.fetchDirectoryStream start: cid=${options.cid}")

        try {
            while (pageCount < options.maxPages) {
                currentCoroutineContext().ensureActive()
                val batch = fileRepository.getFiles(
                    cid = options.cid,
                    offset = currentOffset,
                    limit = limit,
                    order = options.order.type,
                    asc = options.order.asc,
                    fcMix = options.fcMix
                )
                latestCount = batch.count
                if (batch.path.isNotEmpty()) lastPath = batch.path

                val formatted = formatFileBeanList(batch.fileBeanList)
                stagedStreamList.addAll(batch.fileBeanList)
                currentOffset += batch.fileBeanList.size

                emit(formatted)

                if (batch.fileBeanList.size < limit ||
                    (latestCount > 0 && stagedStreamList.size >= latestCount) ||
                    batch.fileBeanList.isEmpty()
                ) {
                    isFullyConsumed = true
                    break
                }
                pageCount++
                delay(50L.milliseconds)
            }
        } finally {
            // Best-Effort 旁路缓存沉淀：若流式完整消费完成且达到完备性，自动提交缓存供后续 UI 直接使用
            if (isFullyConsumed) {
                val deduplicated = formatFileBeanList(stagedStreamList).distinctBy { item ->
                    item.fileId.ifEmpty { item.categoryId.ifEmpty { item.pickCode } }
                }
                val finalBean = FilesBean(
                    fileBeanList = ArrayList(deduplicated),
                    cid = options.cid,
                    count = if (latestCount > 0) latestCount else deduplicated.size,
                    order = options.order.type,
                    isAsc = options.order.asc,
                    fcMix = options.fcMix,
                    path = lastPath
                )
                fileCacheManager.putAndNotify(options.cid, finalBean)
                logI("DirectoryPagingCoordinator: Stream fully consumed, cached ${deduplicated.size} items for cid=${options.cid}")
            }
        }
    }

    companion object {
        @Volatile
        private var instance: DirectoryPagingCoordinator? = null

        fun getInstance(): DirectoryPagingCoordinator {
            return instance ?: synchronized(this) {
                instance ?: DirectoryPagingCoordinatorImpl().also { instance = it }
            }
        }
    }
}
