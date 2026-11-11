package github.zerorooot.nap511.di

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import github.zerorooot.nap511.repository.AuthRepository
import github.zerorooot.nap511.repository.DataStoreSettingsRepository
import github.zerorooot.nap511.repository.DefaultAuthRepository
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinatorImpl
import github.zerorooot.nap511.repository.settingDataStore
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.RepeatService
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.platform.AndroidAppStringProvider
import github.zerorooot.nap511.viewmodel.platform.AndroidToastProvider
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.ToastProvider

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
    val pagingCoordinator: DirectoryPagingCoordinator
}

/**
 * 生产环境默认依赖容器实现
 */
class DefaultAppContainer(private val application: Application) : AppContainer {

    override val toastProvider: ToastProvider by lazy {
        AndroidToastProvider(application)
    }
    override val stringProvider: AppStringProvider by lazy {
        AndroidAppStringProvider(application)
    }
    override val fileRepository: FileRepository by lazy {
        FileRepository.getInstance()
    }
    override val subtitleRepository: SubtitleRepository by lazy {
        SubtitleRepository.getInstance()
    }
    override val settingsRepository: SettingsRepository by lazy {
        DataStoreSettingsRepository(application.settingDataStore)
    }
    override val authRepository: AuthRepository by lazy {
        DefaultAuthRepository(settingsRepository)
    }
    override val fileService: FileService by lazy {
        FileService.getInstance()
    }
    override val repeatService: RepeatService by lazy {
        RepeatService.getInstance()
    }
    override val pagingCoordinator: DirectoryPagingCoordinator by lazy {
        DirectoryPagingCoordinatorImpl.getInstance()
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
