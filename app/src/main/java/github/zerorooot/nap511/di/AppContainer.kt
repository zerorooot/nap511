package github.zerorooot.nap511.di

import android.content.Context
import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import github.zerorooot.nap511.repository.AuthRepository
import github.zerorooot.nap511.repository.DataStoreSettingsRepository
import github.zerorooot.nap511.repository.DefaultAuthRepository
import github.zerorooot.nap511.repository.DefaultFileRepository
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinatorImpl
import github.zerorooot.nap511.repository.settingDataStore
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.OfflineService
import github.zerorooot.nap511.service.RepeatService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.util.ConfigKeyUtil
import github.zerorooot.nap511.util.network.NetworkClient
import github.zerorooot.nap511.util.network.UserSessionManager
import github.zerorooot.nap511.viewmodel.platform.AndroidAppStringProvider
import github.zerorooot.nap511.viewmodel.platform.AndroidToastProvider
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
import github.zerorooot.nap511.worker.UnzipQueueManager
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 应用级依赖注入容器抽象接口
 * 统管全局单例生命周期，支持在测试用例中注入 Fake 实例。
 */
interface AppContainer {
    val toastProvider: ToastProvider
    val stringProvider: AppStringProvider
    val fileRepository: FileRepository
    val subtitleRepository: SubtitleRepository
    val settingsRepository: SettingsRepository
    val authRepository: AuthRepository
    val fileService: FileService
    val repeatService: RepeatService
    val offlineService: OfflineService
    val pagingCoordinator: DirectoryPagingCoordinator
    val unzipQueueManager: UnzipQueueManager
}

/**
 * 生产环境默认依赖容器实现
 */
class DefaultAppContainer(private val application: Application) : AppContainer {

    private val serviceOkHttpClient: OkHttpClient by lazy {
        NetworkClient.sharedOkHttpClient.newBuilder()
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .addHeader("Cookie", UserSessionManager.cookie)
                    .addHeader("User-Agent", ConfigKeyUtil.USER_AGENT)
                    .build()
                chain.proceed(request)
            }
            .build()
    }

    private fun <T> createRetrofitService(baseUrl: String, serviceClass: Class<T>): T {
        return Retrofit.Builder()
            .baseUrl(baseUrl)
            .addConverterFactory(GsonConverterFactory.create())
            .client(serviceOkHttpClient)
            .build()
            .create(serviceClass)
    }

    override val toastProvider: ToastProvider by lazy {
        AndroidToastProvider(application)
    }
    override val stringProvider: AppStringProvider by lazy {
        AndroidAppStringProvider(application)
    }
    override val fileService: FileService by lazy {
        createRetrofitService("https://webapi.115.com/", FileService::class.java)
    }
    override val repeatService: RepeatService by lazy {
        createRetrofitService("https://aps.115.com/repeat/", RepeatService::class.java)
    }
    override val offlineService: OfflineService by lazy {
        createRetrofitService("https://115.com/", OfflineService::class.java)
    }
    override val fileRepository: FileRepository by lazy {
        DefaultFileRepository(
            fileService = fileService,
            offlineService = offlineService
        )
    }
    override val subtitleRepository: SubtitleRepository by lazy {
        SubtitleRepository(fileRepository = fileRepository)
    }
    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(application.settingDataStore)
    }
    override val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(settingsRepository)
    }
    override val pagingCoordinator: DirectoryPagingCoordinator by lazy {
        DirectoryPagingCoordinatorImpl(fileRepository = fileRepository)
    }
    override val unzipQueueManager: UnzipQueueManager by lazy {
        UnzipQueueManager(application)
    }
}

/**
 * CreationExtras 扩展属性：安全获取当前 Application 实例
 */
val CreationExtras.app: App
    get() = checkNotNull(this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]) as App

/**
 * CreationExtras 扩展属性：便捷获取全局 AppContainer
 */
val CreationExtras.appContainer: AppContainer
    get() = app.container

/**
 * Context 扩展属性：便捷获取全局 AppContainer
 * 采用多级安全探测链（优先匹配 this as? App，其次 applicationContext as? App），
 * 确保即使在没有底层 BaseContext 的 JVM 单元测试环境中，也能准确命中预设的测试容器。
 */
val Context.appContainer: AppContainer
    get() = (this as? App)?.container
        ?: (applicationContext as? App)?.container
        ?: (applicationContext as? Application ?: this as? Application)?.let { DefaultAppContainer(it) }
        ?: error("Cannot obtain AppContainer: Current Context is not associated with an Application")

/**
 * Context 扩展属性：便捷获取全局 ToastProvider 单例
 */
val Context.toastProvider: ToastProvider
    get() = appContainer.toastProvider

/**
 * Context 轻提示扩展函数：自动委托给全局 ToastProvider 单例，
 * 具备主线程调度保障与重复提示取消防抖特性。
 */
fun Context.toast(message: String, isLong: Boolean = false) {
    if (message.isBlank()) return
    toastProvider.showToast(message, isLong)
}
