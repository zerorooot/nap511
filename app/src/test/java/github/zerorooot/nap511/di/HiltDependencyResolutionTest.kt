package github.zerorooot.nap511.di

import com.elvishew.xlog.XLog
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import github.zerorooot.nap511.data.repository.AuthRepository
import github.zerorooot.nap511.data.repository.FileRepository
import github.zerorooot.nap511.data.repository.RepeatRepository
import github.zerorooot.nap511.data.repository.SettingsRepository
import github.zerorooot.nap511.data.repository.SubtitleRepository
import github.zerorooot.nap511.data.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.data.network.api.FileApi
import github.zerorooot.nap511.data.network.api.OfflineApi
import github.zerorooot.nap511.data.network.api.RepeatApi
import github.zerorooot.nap511.platform.AppStringProvider
import github.zerorooot.nap511.platform.ToastProvider
import github.zerorooot.nap511.worker.UnzipQueueManager
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.inject.Inject

/**
 * Hilt 依赖图完整性集成测试
 *
 * 通过 Robolectric 与 HiltTestApplication 验证全图依赖绑定：
 * 包括所有 Repository、API、PagingCoordinator、Platform 抽象及单例 Manager。
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class)
class HiltDependencyResolutionTest {

    @get:Rule
    var hiltRule = HiltAndroidRule(this)

    @Inject lateinit var fileRepository: FileRepository
    @Inject lateinit var authRepository: AuthRepository
    @Inject lateinit var settingsRepository: SettingsRepository
    @Inject lateinit var subtitleRepository: SubtitleRepository
    @Inject lateinit var repeatRepository: RepeatRepository
    @Inject lateinit var pagingCoordinator: DirectoryPagingCoordinator
    @Inject lateinit var toastProvider: ToastProvider
    @Inject lateinit var stringProvider: AppStringProvider
    @Inject lateinit var fileApi: FileApi
    @Inject lateinit var repeatApi: RepeatApi
    @Inject lateinit var offlineApi: OfflineApi
    @Inject lateinit var unzipQueueManager: UnzipQueueManager

    @Before
    fun init() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        hiltRule.inject()
    }

    @Test
    fun verifyDependencyGraphIntegrity() {
        assertNotNull(fileRepository)
        assertNotNull(authRepository)
        assertNotNull(settingsRepository)
        assertNotNull(subtitleRepository)
        assertNotNull(repeatRepository)
        assertNotNull(pagingCoordinator)
        assertNotNull(toastProvider)
        assertNotNull(stringProvider)
        assertNotNull(fileApi)
        assertNotNull(repeatApi)
        assertNotNull(offlineApi)
        assertNotNull(unzipQueueManager)
    }
}
