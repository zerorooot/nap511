package github.zerorooot.nap511.ui.viewmodel.store

import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.runtime.snapshots.SnapshotStateMap
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.data.model.ImageBean
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

    /**
     * 事务级原子差量对齐：
     * 仅移除不在 validKeys 中的无效/过期/已删除项；保留现有有效项，避免刷新引发大面积跳闪。
     * 使用 Snapshot.withMutableSnapshot 确保批量修改单次提交，消除中间重组震荡。
     */
    fun syncValidKeys(cid: String, validKeys: Set<String>) {
        val map = imageBeanCache[cid] ?: return
        Snapshot.withMutableSnapshot {
            val toRemove = map.keys.filterNot { it in validKeys }
            if (toRemove.isNotEmpty()) {
                XLog.d("ImageCacheStore.syncValidKeys: cid=$cid, removing ${toRemove.size} stale/expired keys: $toRemove")
                toRemove.forEach { map.remove(it) }
            }
        }
    }

    /**
     * 跨目录移动/删除后按 pickCode 清除**任意 cid** 下的高清图缓存（单次原子提交）。
     *
     * 为什么不能只清某一固定 cid：大图缓存写在「加载时的 currentCid」下
     * （见 `FileMediaModule` 的大图加载链路），filter/搜索/星标视图下它与条目真实父目录不同，
     * 所以必须扫全部目录桶。
     *
     * 不处理 loadingKeys：大图加载的 finally 在成功/失败/取消三路径都会清标记，且 put() 写入前
     * 不复检该集合，扫它既不能阻止回写也无实际收益。极端并发下的 1 帧脏图由
     * `FileBrowserModule.getFiles` 的 syncValidKeys 自愈。
     *
     * 相比逐项调用 `remove(cid, pickCode)`（N 次 Snapshot 提交），这里一次装填全部键、
     * **一次** withMutableSnapshot 提交：快照更少、无中间可见态。
     */
    fun removeByPickCodes(pickCodes: Collection<String>) {
        val keys = pickCodes.filterTo(HashSet()) { it.isNotEmpty() }
        if (keys.isEmpty()) return
        XLog.d("ImageCacheStore.removeByPickCodes: keys=${keys.size}, buckets=${imageBeanCache.size}")
        Snapshot.withMutableSnapshot {
            imageBeanCache.values.forEach { inner -> keys.forEach { inner.remove(it) } }
        }
    }
}
