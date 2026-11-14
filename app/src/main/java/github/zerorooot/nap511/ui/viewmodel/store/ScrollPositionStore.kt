package github.zerorooot.nap511.ui.viewmodel.store

import github.zerorooot.nap511.data.model.LocationBean
import java.util.concurrent.ConcurrentHashMap

/**
 * 列表滚动位置与点击高亮标识状态容器（纯 JVM，无 UI / Compose 依赖）
 * 维护不同目录及数据源模式 (paginationKey) 下的滚动物理偏移和最后点击条目唯一标识，
 * 保证切换目录与按返回键回退时能无缝恢复视觉焦点与交互位置。
 */
class ScrollPositionStore {
    /** 记录各数据源模式最后滚动到的条目下标与像素偏移 */
    val locations = ConcurrentHashMap<String, LocationBean>()

    /** 记录各数据源模式最后点击项的唯一标识 (原 clicks 索引) */
    val clicks = ConcurrentHashMap<String, String>()

    fun setLocation(key: String, location: LocationBean) {
        locations[key] = location
    }

    fun getLocation(key: String): LocationBean = locations[key] ?: LocationBean(0, 0)

    fun setHighlight(key: String, uniqueId: String) {
        clicks[key] = uniqueId
    }

    fun getHighlight(key: String): String? = clicks[key]

    fun clearHighlight(key: String) {
        clicks.remove(key)
    }

    fun setClick(key: String, uniqueId: String) = setHighlight(key, uniqueId)

    fun getClick(key: String): String? = getHighlight(key)

    fun clear() {
        locations.clear()
        clicks.clear()
    }
}
