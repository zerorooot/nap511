package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.LocationBean
import java.util.concurrent.ConcurrentHashMap

/**
 * 列表滚动位置与点击索引状态容器（纯 JVM，无 UI / Compose 依赖）
 * 维护不同目录及数据源模式 (paginationKey) 下的滚动物理偏移和最后点击条目索引，
 * 保证切换目录与按返回键回退时能无缝恢复视觉焦点与交互位置。
 */
class ScrollPositionStore {
    /** 记录各数据源模式最后滚动到的条目下标与像素偏移 */
    val locations = ConcurrentHashMap<String, LocationBean>()

    /** 记录各数据源模式最后点击项的索引位置 (原 clickMap) */
    val clicks = ConcurrentHashMap<String, Int>()

    fun setLocation(key: String, location: LocationBean) {
        locations[key] = location
    }

    fun getLocation(key: String): LocationBean = locations[key] ?: LocationBean(0, 0)

    fun setClick(key: String, index: Int) {
        clicks[key] = index
    }

    fun getClick(key: String): Int = clicks[key] ?: 0

    /** 在条目发生增删变更时原子微调点击索引 */
    fun adjustClick(key: String, delta: Int) {
        clicks.compute(key) { _, current -> ((current ?: 0) + delta).coerceAtLeast(0) }
    }

    fun clear() {
        locations.clear()
        clicks.clear()
    }
}
