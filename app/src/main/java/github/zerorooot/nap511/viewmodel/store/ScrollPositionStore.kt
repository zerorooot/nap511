package github.zerorooot.nap511.viewmodel.store

import github.zerorooot.nap511.bean.LocationBean
import github.zerorooot.nap511.util.LazyScrollState
import java.util.concurrent.ConcurrentHashMap

/**
 * 列表滚动位置与点击索引状态容器
 * 维护不同目录 (cid) 及展示模式下的滚动物理偏移和最后点击条目索引，
 * 保证切换目录与按返回键回退时能无缝恢复视觉焦点与交互位置。
 */
class ScrollPositionStore {
    /** 记录各目录最后滚动到的条目下标与像素偏移 */
    val locations = ConcurrentHashMap<String, LocationBean>()

    /** 记录各目录最后点击项的索引位置 (原 clickMap) */
    val clicks = ConcurrentHashMap<String, Int>()

    fun setLocation(key: String, state: LazyScrollState) {
        locations[key] = LocationBean(state.firstVisibleItemIndex, state.firstVisibleItemScrollOffset)
    }

    fun getLocation(key: String): LocationBean = locations[key] ?: LocationBean(0, 0)

    fun setClick(cid: String, index: Int) {
        clicks[cid] = index
    }

    fun getClick(cid: String): Int = clicks[cid] ?: 0

    /** 在条目发生增删变更时原子微调点击索引 */
    fun adjustClick(cid: String, delta: Int) {
        clicks.compute(cid) { _, current -> (current ?: 0) + delta }
    }

    fun clear() {
        locations.clear()
        clicks.clear()
    }
}
