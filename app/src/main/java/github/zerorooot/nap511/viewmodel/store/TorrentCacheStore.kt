package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.TorrentFileBean
import java.util.concurrent.ConcurrentHashMap

/**
 * 种子元数据解析内存缓存容器
 * 缓存已解析的种子文件列表与详细信息，避免对同一种子文件哈希 (sha1) 重复发起网络解析。
 */
class TorrentCacheStore {
    private val cache = ConcurrentHashMap<String, TorrentFileBean>()

    operator fun get(sha1: String): TorrentFileBean? = cache[sha1]

    operator fun set(sha1: String, value: TorrentFileBean) {
        cache[sha1] = value
    }

    fun containsKey(sha1: String): Boolean = cache.containsKey(sha1)

    fun remove(sha1: String): TorrentFileBean? = cache.remove(sha1)

    fun clear() {
        cache.clear()
    }
}
