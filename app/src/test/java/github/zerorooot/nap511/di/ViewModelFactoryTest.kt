package github.zerorooot.nap511.di

import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.MutableCreationExtras
import kotlinx.coroutines.cancel
import com.elvishew.xlog.XLog
import github.zerorooot.nap511.terminal.viewmodel.TerminalViewModel
import github.zerorooot.nap511.util.App
import github.zerorooot.nap511.viewmodel.AudioViewModel
import github.zerorooot.nap511.viewmodel.FileViewModel
import github.zerorooot.nap511.viewmodel.LogViewModel
import github.zerorooot.nap511.viewmodel.LoginViewModel
import github.zerorooot.nap511.viewmodel.OfflineFileViewModel
import github.zerorooot.nap511.viewmodel.RecycleViewModel
import github.zerorooot.nap511.viewmodel.RepeatFileViewModel
import github.zerorooot.nap511.viewmodel.SettingViewModel
import github.zerorooot.nap511.viewmodel.VideoViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

import github.zerorooot.nap511.repository.SettingsRepository
import github.zerorooot.nap511.viewmodel.platform.AppStringProvider
import github.zerorooot.nap511.viewmodel.platform.FakeAppStringProvider

@OptIn(ExperimentalCoroutinesApi::class)
class ViewModelFactoryTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var app: App
    private lateinit var extras: MutableCreationExtras

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        Dispatchers.setMain(testDispatcher)

        val fakeSettingsRepo = github.zerorooot.nap511.viewmodel.platform.FakeSettingsRepository()
        val fakeAuthRepo = github.zerorooot.nap511.viewmodel.platform.FakeAuthRepository()

        val testApp = App()
        val defaultContainer = DefaultAppContainer(testApp)
        testApp.container = object : AppContainer by defaultContainer {
            override val stringProvider: AppStringProvider = FakeAppStringProvider()
            override val settingsRepository: SettingsRepository = fakeSettingsRepo
            override val authRepository = fakeAuthRepo
        }
        app = testApp

        extras = MutableCreationExtras()
        extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY] = testApp
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testFileViewModelFactory_createsInstance() {
        val vm = FileViewModel.Factory.create(FileViewModel::class.java, extras)
        assertNotNull(vm)
        assertNotNull(vm.fileRepository)
        assertNotNull(vm.pagingCoordinator)
    }

    @Test
    fun testAudioViewModelFactory_createsInstance() {
        val vm = AudioViewModel.Factory.create(AudioViewModel::class.java, extras)
        assertNotNull(vm)
        assertNotNull(vm.fileRepository)
        assertNotNull(vm.subtitleRepository)
    }

    @Test
    fun testVideoViewModelFactory_createsInstance() {
        val vm = VideoViewModel.Factory.create(VideoViewModel::class.java, extras)
        assertNotNull(vm)
        assertNotNull(vm.fileRepository)
        assertNotNull(vm.subtitleRepository)
    }

    @Test
    fun testOfflineFileViewModelFactory_createsInstance() {
        val vm = OfflineFileViewModel.Factory.create(OfflineFileViewModel::class.java, extras)
        assertNotNull(vm)
    }

    @Test
    fun testSettingViewModelFactory_createsInstance() {
        val vm = SettingViewModel.Factory.create(SettingViewModel::class.java, extras)
        assertNotNull(vm)
        assertNotNull(vm.uiState)
    }

    @Test
    fun testRecycleViewModelFactory_createsInstance() {
        val vm = RecycleViewModel.Factory.create(RecycleViewModel::class.java, extras)
        assertNotNull(vm)
    }

    @Test
    fun testRepeatFileViewModelFactory_createsInstance() {
        val vm = RepeatFileViewModel.Factory.create(RepeatFileViewModel::class.java, extras)
        assertNotNull(vm)
    }

    @Test
    fun testLoginViewModelFactory_createsInstance() {
        val vm = LoginViewModel.Factory.create(LoginViewModel::class.java, extras)
        assertNotNull(vm)
    }

    @Test
    fun testTerminalViewModelFactory_createsInstance() {
        val vm = TerminalViewModel.Factory.create(TerminalViewModel::class.java, extras)
        assertNotNull(vm)
        assertNotNull(vm.screenBuffer)
        vm.viewModelScope.cancel()
    }
}
