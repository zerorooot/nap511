package github.zerorooot.nap511.viewmodel.platform

import java.util.concurrent.CopyOnWriteArrayList

/**
 * 测试替身：线程安全的内存 Toast 收集器
 */
class FakeToastProvider : ToastProvider {
    val messages = CopyOnWriteArrayList<String>()
    val resIds = CopyOnWriteArrayList<Int>()

    override fun showToast(message: String, isLong: Boolean) {
        messages.add(message)
    }

    override fun showToast(resId: Int) {
        resIds.add(resId)
        messages.add("resId:$resId")
    }

    fun clear() {
        messages.clear()
        resIds.clear()
    }
}
