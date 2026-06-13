package github.zerorooot.nap511.factory

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel

//class CookieViewModelFactory(
//    private val cookie: String,
//    private val application: Application,
//) : ViewModelProvider.Factory {
//    override fun <T : ViewModel> create(modelClass: Class<T>): T {
//        return modelClass.getDeclaredConstructor(String::class.java, Application::class.java)
//            .newInstance(cookie, application) as T
//    }
//}
/**
 * ViewModel 工厂，提供 [FileViewModel]、[OfflineFileViewModel] 和 [RecycleViewModel] 的实例。
 *
 * 重构历史：
 * - 移除了 Application 依赖（OfflineFileViewModel / RecycleViewModel 不再需要）
 * - FileViewModel 改为 Context 依赖（比 Application 更轻量）
 */
class CookieViewModelFactory(
    private val cookie: String,
    private val context: Context,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return when (modelClass) {
            FileViewModel::class.java ->
                FileViewModel(cookie, context) as T

            OfflineFileViewModel::class.java ->
                OfflineFileViewModel(cookie) as T

            RecycleViewModel::class.java ->
                RecycleViewModel(cookie) as T

            else -> throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }
}