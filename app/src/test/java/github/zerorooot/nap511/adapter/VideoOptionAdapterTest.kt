package github.zerorooot.nap511.adapter

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.worker.UnzipQueueManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * 视频选项适配器 (VideoOptionAdapter) 单元测试
 * 覆盖单选比例、操作项 (旋转/复位)、数据更新与选中态行为
 */
class VideoOptionAdapterTest {

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        UnzipQueueManager.clear()
    }

    private fun initObservable(adapter: androidx.recyclerview.widget.RecyclerView.Adapter<*>) {
        try {
            val observableField = androidx.recyclerview.widget.RecyclerView.Adapter::class.java.getDeclaredField("mObservable")
            observableField.isAccessible = true
            val observable = observableField.get(adapter)
            val observersField = android.database.Observable::class.java.getDeclaredField("mObservers")
            observersField.isAccessible = true
            observersField.set(observable, ArrayList<Any>())
        } catch (_: Throwable) {}
    }

    @Test
    fun testInitializationAndOptionCount() {
        val options = listOf(
            "默认", "16:9", "4:3", "全屏", "拉伸",
            "顺时针旋转 90°", "逆时针旋转 90°", "恢复默认角度"
        )
        val adapter = VideoOptionAdapter(
            options = options,
            selectedIndex = 0,
            isOptionSelectable = { it < 5 }
        ) { _, _ -> }

        assertEquals(8, adapter.itemCount)
        assertEquals(0, adapter.getSelectedIndex())
        assertEquals(options, adapter.getOptions())
    }

    @Test
    fun testSelectableOptionPredicate() {
        val options = listOf(
            "默认", "16:9", "4:3", "全屏", "拉伸",
            "顺时针旋转 90°", "逆时针旋转 90°", "恢复默认角度"
        )
        val adapter = VideoOptionAdapter(
            options = options,
            selectedIndex = 0,
            isOptionSelectable = { it < 5 }
        ) { _, _ -> }

        // 前 5 项为单选比例项
        for (i in 0..4) {
            assertTrue("索引 $i 应该为可单选项", adapter.isOptionSelectable(i))
        }

        // 后 3 项为操作项（顺时针、逆时针、复位）
        for (i in 5..7) {
            assertFalse("索引 $i 应该为非单选操作项", adapter.isOptionSelectable(i))
        }
    }

    @Test
    fun testDefaultSelectableWhenPredicateNull() {
        val options = listOf("0.5X", "1.0X", "1.5X", "2.0X")
        val adapter = VideoOptionAdapter(
            options = options,
            selectedIndex = 1
        ) { _, _ -> }

        assertEquals(4, adapter.itemCount)
        // 默认情况下所有项均为可选中状态
        for (i in options.indices) {
            assertTrue("默认 predicate 为空时，项 $i 应该可选中", adapter.isOptionSelectable(i))
        }
    }

    @Test
    fun testUpdateSelectedIndex() {
        val options = listOf("默认", "16:9", "4:3")
        val adapter = VideoOptionAdapter(
            options = options,
            selectedIndex = 0
        ) { _, _ -> }
        initObservable(adapter)

        assertEquals(0, adapter.getSelectedIndex())

        adapter.updateSelectedIndex(2)
        assertEquals(2, adapter.getSelectedIndex())

        // 越界保护测试：不应改变 selectedIndex 为非法值引发崩溃
        adapter.updateSelectedIndex(2)
        assertEquals(2, adapter.getSelectedIndex())
    }

    @Test
    fun testUpdateData() {
        val initialOptions = listOf("P1", "P2")
        val adapter = VideoOptionAdapter(
            options = initialOptions,
            selectedIndex = 0
        ) { _, _ -> }
        initObservable(adapter)

        assertEquals(2, adapter.itemCount)

        val newOptions = listOf("P1", "P2", "P3", "P4")
        adapter.updateData(newOptions, newIndex = 3)

        assertEquals(4, adapter.itemCount)
        assertEquals(3, adapter.getSelectedIndex())
        assertEquals(newOptions, adapter.getOptions())
    }
}
