package github.zerorooot.nap511.di

import com.elvishew.xlog.XLog
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import github.zerorooot.nap511.repository.AuthRepository
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.repository.SubtitleRepository
import github.zerorooot.nap511.repository.paging.DirectoryPagingCoordinator
import github.zerorooot.nap511.service.FileService
import github.zerorooot.nap511.service.OfflineService
import github.zerorooot.nap511.service.RepeatService
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.ToastProvider
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
 * 包括所有 Repository、Service、PagingCoordinator、Platform 抽象及单例 Manager。
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
    @Inject lateinit var pagingCoordinator: DirectoryPagingCoordinator
    @Inject lateinit var toastProvider: ToastProvider
    @Inject lateinit var stringProvider: AppStringProvider
    @Inject lateinit var fileService: FileService
    @Inject lateinit var repeatService: RepeatService
    @Inject lateinit var offlineService: OfflineService
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
        assertNotNull(pagingCoordinator)
        assertNotNull(toastProvider)
        assertNotNull(stringProvider)
        assertNotNull(fileService)
        assertNotNull(repeatService)
        assertNotNull(offlineService)
        assertNotNull(unzipQueueManager)
    }
}
