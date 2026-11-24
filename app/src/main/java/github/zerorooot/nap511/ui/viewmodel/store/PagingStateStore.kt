package github.zerorooot.nap511.ui.viewmodel.store

import java.util.concurrent.ConcurrentHashMap

/**
 * 触底分页状态与物理游标安全容器
 * 统一管理各目录与模式下的分页完成状态、服务端物理窗口偏移以及防高频触底并发重入锁。
 *
 * 【职责收窄】Folder 源的物理游标（physicalOffset）与完成态（isComplete）已下沉到
 * FilesBean 缓存本体（见 PagingCache），与缓存同生共死，彻底消灭「LRU 淘汰缓存但 offset/complete 残留」导致的跳页/永久漏页。
 * 本类中的 physicalOffsets / isLoadedCompleteMap 现仅服务 Search/Filter/Star 等非 Folder 源
 * （它们没有缓存回声、没有 LRU 淘汰、单一写入点，本就无此病灶）；tryAcquireFetch 等并发锁仍全源通用。
 * Folder 源即使有旧代码往这里写 offset/complete，也仅是无人读取的死数据，不影响正确性。
 */
class PagingStateStore {
    /** 记录各目录及数据源模式触底分页加载是否已完全加载完毕，避免重复触发无效请求与 Toast（Folder 源已改从缓存派生，此处仅 non-Folder 有效） */
    private val isLoadedCompleteMap = ConcurrentHashMap<String, Boolean>()

    /** 记录各数据源模式服务端请求的物理偏移量（严格按服务端返回 batch.size 累加，绝不等于去重后的 list.size，消灭游标漂移；Folder 源已下沉到 FilesBean） */
    private val physicalOffsets = ConcurrentHashMap<String, Int>()

    /** 记录正在执行触底拉取更多任务的 paginationKey，按模式及目录级提供原子互斥保障，防止高频触底引发并发重入 */
    private val fetchingMoreKeys = ConcurrentHashMap.newKeySet<String>()

    fun isComplete(key: String): Boolean = isLoadedCompleteMap[key] ?: false

    fun markComplete(key: String) {
        isLoadedCompleteMap[key] = true
    }

    /** 切换目录或下拉全量刷新时重置指定数据源键的分页状态 */
    fun reset(key: String) {
        isLoadedCompleteMap.remove(key)
        physicalOffsets.remove(key)
        fetchingMoreKeys.remove(key)
    }

    fun getOrInitOffset(key: String, fallback: Int): Int =
        physicalOffsets.getOrPut(key) { fallback }

    fun setOffset(key: String, offset: Int) {
        physicalOffsets[key] = offset
    }

    /** 尝试抢占触底拉取锁，成功返回 true，并发重入时返回 false */
    fun tryAcquireFetch(key: String): Boolean = fetchingMoreKeys.add(key)

    /** 释放指定数据源的拉取锁 */
    fun releaseFetch(key: String) {
        fetchingMoreKeys.remove(key)
    }

    /** 当前是否正处于触底加载更多状态 */
    val isFetchingMore: Boolean
        get() = fetchingMoreKeys.isNotEmpty()

    fun clear() {
        isLoadedCompleteMap.clear()
        physicalOffsets.clear()
        fetchingMoreKeys.clear()
    }
}
