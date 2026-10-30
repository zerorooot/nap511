package github.zerorooot.nap511.viewmodel.store

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import github.zerorooot.nap511.bean.ImageBean
import java.util.concurrent.ConcurrentHashMap

/**
 * 二级 Compose 响应式高清图内存缓存与并发加载安全容器
 */
class ImageCacheStore {
    /**
     * 二级 Compose SnapshotStateMap：
     * 外层按目录 cid 隔离，内层按文件 pickCode 触发列表单条目精准重组，
     * 避免单张高清图加载完成时引发整个 FileScreen 重组。
     */
    val imageBeanCache = mutableStateMapOf<String, SnapshotStateMap<String, ImageBean>>()

    /** 记录正在异步请求大图的 pickCode 集合，提供并发抢占保护 */
    private val loadingKeys = ConcurrentHashMap.newKeySet<String>()

    /**
     * 写入指定目录与 pickCode 的高清图缓存：
     * 采用二级 SnapshotStateMap 结构，外层按 cid 隔离目录，内层按 pickCode 细粒度通知对应条目重组，
     * 避免单张高清图加载完成时引发整个 FileScreen 重组。
     */
    fun put(cid: String, pickCode: String, bean: ImageBean) {
        val cidMap = imageBeanCache.getOrPut(cid) { mutableStateMapOf() }
        cidMap[pickCode] = bean
    }

    /** 获取指定目录与 pickCode 对应的高清图缓存 */
    fun get(cid: String, pickCode: String): ImageBean? = imageBeanCache[cid]?.get(pickCode)

    /** 尝试标记指定文件大图正在加载中，成功加入返回 true，已在加载则返回 false */
    fun tryMarkLoading(pickCode: String): Boolean = loadingKeys.add(pickCode)

    /** 移除大图加载标记 */
    fun removeLoading(pickCode: String) {
        loadingKeys.remove(pickCode)
    }

    /** 移除指定文件的大图缓存 */
    fun remove(cid: String, pickCode: String) {
        imageBeanCache[cid]?.remove(pickCode)
    }

    /**
     * 清空指定目录的高清图缓存：
     * 直接清空内层 SnapshotStateMap 条目而保留外层 Map 引用，减少不必要的外层容器重组。
     */
    fun clear(cid: String) {
        imageBeanCache[cid]?.clear()
    }
}
