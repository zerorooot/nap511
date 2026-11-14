package github.zerorooot.nap511.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.elvishew.xlog.XLog
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.zerorooot.nap511.repository.settingDataStore
import github.zerorooot.nap511.viewmodel.platform.AndroidAppStringProvider
import github.zerorooot.nap511.viewmodel.platform.AndroidToastProvider
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
import javax.inject.Singleton

/**
 * 平台级组件与本地持久化依赖注入模块
 *
 * 负责绑定 Android 平台特定的 Toast 提示器、字符串资源提供者以及 DataStore 键值存储单例。
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlatformModule {

    @Binds
    @Singleton
    abstract fun bindToastProvider(impl: AndroidToastProvider): ToastProvider

    @Binds
    @Singleton
    abstract fun bindStringProvider(impl: AndroidAppStringProvider): AppStringProvider

    companion object {
        @Provides
        @Singleton
        fun provideSettingsDataStore(@ApplicationContext context: Context): DataStore<Preferences> {
            return context.settingDataStore
        }
    }
}
