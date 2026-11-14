package github.zerorooot.nap511.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.zerorooot.nap511.data.repository.AuthRepository
import github.zerorooot.nap511.data.repository.DataStoreSettingsRepository
import github.zerorooot.nap511.data.repository.DefaultAuthRepository
import github.zerorooot.nap511.data.repository.DefaultFileRepository
import github.zerorooot.nap511.data.repository.FileRepository
import github.zerorooot.nap511.data.repository.RepeatRepository
import github.zerorooot.nap511.data.repository.DefaultRepeatRepository
import github.zerorooot.nap511.data.repository.SettingsRepository
import github.zerorooot.nap511.data.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.data.repository.paging.DirectoryPagingCoordinatorImpl
import javax.inject.Singleton

/**
 * 业务数据仓库依赖注入模块
 *
 * 遵循面向接口编程契约，将生产环境的具体 Repository 与 Coordinator 实现绑定至其抽象接口。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindFileRepository(impl: DefaultFileRepository): FileRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: DefaultAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindRepeatRepository(impl: DefaultRepeatRepository): RepeatRepository

    @Binds
    @Singleton
    abstract fun bindPagingCoordinator(impl: DirectoryPagingCoordinatorImpl): DirectoryPagingCoordinator
}
