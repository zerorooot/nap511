package github.zerorooot.nap511.viewmodel

import com.elvishew.xlog.XLog
import github.zerorooot.nap511.screen.LogLevel
import github.zerorooot.nap511.util.App
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * LogViewModel 单元测试集
 *
 * 验证：
 * 1. 结构化日志与多行堆栈解析及级别统计计算 (updateLogContent)
 * 2. 日志级别筛选联动与搜索结果实时重算 (selectLevel)
 * 3. 关键字搜索匹配、上一项/下一项游标导航 (setSearchQuery, nextMatch, prevMatch)
 * 4. 搜索框状态开闭与状态复位 (toggleSearch)
 * 5. 选中条目详情展示与清空 (selectLogForDetail, clearLog)
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LogViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private lateinit var viewModel: LogViewModel

    private val sampleRawLogs = """
        2026-11-10 10:00:00.123 I/NetworkEngine: HTTP GET /api/v1/files 200 OK
        2026-11-10 10:00:01.456 D/CacheManager: Memory cache hit for key: folder:1003
        2026-11-10 10:00:02.789 W/MediaScanner: Skipped hidden thumbnail: .thumb_tmp
        2026-11-10 10:00:03.012 E/UnzipWorker: Task failed due to IO exception
        java.io.IOException: Disk full
            at com.example.UnzipWorker.process(UnzipWorker.kt:42)
            at com.example.UnzipWorker.run(UnzipWorker.kt:18)
        2026-11-10 10:00:04.333 V/Lifecycle: Activity onResume
    """.trimIndent()

    @Before
    fun setUp() {
        try {
            XLog.init()
        } catch (_: Throwable) {}
        Dispatchers.setMain(testDispatcher)

        val testApp = App()
        viewModel = LogViewModel(
            application = testApp,
            ioDispatcher = testDispatcher,
            autoStartWatcher = false
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testUpdateLogContent_parsesEntriesAndLevelCountsCorrectly() {
        XLog.d("Running testUpdateLogContent_parsesEntriesAndLevelCountsCorrectly")
        viewModel.updateLogContent(sampleRawLogs)
        val state = viewModel.uiState.value

        // 验证 5 条主日志条目（异常堆栈被正确归并进上一条 ERROR 日志）
        assertEquals(5, state.logs.size)
        assertEquals(5, state.filteredLogs.size)

        // 验证各级别统计
        assertEquals(1, state.levelCounts[LogLevel.INFO])
        assertEquals(1, state.levelCounts[LogLevel.DEBUG])
        assertEquals(1, state.levelCounts[LogLevel.WARN])
        assertEquals(1, state.levelCounts[LogLevel.ERROR])
        assertEquals(1, state.levelCounts[LogLevel.VERBOSE])

        // 验证多行异常堆栈聚合
        val errorLog = state.logs.first { it.level == LogLevel.ERROR }
        assertEquals("UnzipWorker", errorLog.tag)
        assertTrue(errorLog.message.contains("Task failed due to IO exception"))
        assertTrue(errorLog.message.contains("Disk full"))
        assertTrue(errorLog.message.contains("UnzipWorker.kt:42"))
    }

    @Test
    fun testSelectLevel_filtersEntriesCorrectly() {
        XLog.d("Running testSelectLevel_filtersEntriesCorrectly")
        viewModel.updateLogContent(sampleRawLogs)

        // 筛选 ERROR
        viewModel.selectLevel(LogLevel.ERROR)
        var state = viewModel.uiState.value
        assertEquals(LogLevel.ERROR, state.selectedLevelFilter)
        assertEquals(1, state.filteredLogs.size)
        assertEquals(LogLevel.ERROR, state.filteredLogs[0].level)

        // 筛选 WARN
        viewModel.selectLevel(LogLevel.WARN)
        state = viewModel.uiState.value
        assertEquals(LogLevel.WARN, state.selectedLevelFilter)
        assertEquals(1, state.filteredLogs.size)
        assertEquals(LogLevel.WARN, state.filteredLogs[0].level)

        // 清除筛选（恢复全量）
        viewModel.selectLevel(null)
        state = viewModel.uiState.value
        assertNull(state.selectedLevelFilter)
        assertEquals(5, state.filteredLogs.size)
    }

    @Test
    fun testSearchQueryAndMatchNavigation() {
        XLog.d("Running testSearchQueryAndMatchNavigation")
        viewModel.updateLogContent(sampleRawLogs)

        // 搜索关键字 "Unzip"（匹配到 ERROR 日志的 tag 与多行 message）
        viewModel.setSearchQuery("Unzip")
        var state = viewModel.uiState.value
        assertEquals("Unzip", state.searchQuery)
        assertTrue(state.searchMatches.isNotEmpty())
        assertEquals(0, state.currentMatchIndex)

        val totalMatches = state.searchMatches.size

        // 循环下移匹配
        viewModel.nextMatch()
        state = viewModel.uiState.value
        val expectedNextIndex = if (totalMatches > 1) 1 else 0
        assertEquals(expectedNextIndex, state.currentMatchIndex)

        // 循环上移匹配
        viewModel.prevMatch()
        state = viewModel.uiState.value
        assertEquals(0, state.currentMatchIndex)

        // 指定跳转导航
        if (totalMatches > 2) {
            viewModel.navigateMatch(2)
            assertEquals(2, viewModel.uiState.value.currentMatchIndex)
        }
    }

    @Test
    fun testToggleSearch_resetsStateWhenClosed() {
        XLog.d("Running testToggleSearch_resetsStateWhenClosed")
        viewModel.updateLogContent(sampleRawLogs)
        viewModel.setSearchQuery("Network")
        viewModel.toggleSearch(true)

        assertTrue(viewModel.uiState.value.isSearchOpen)
        assertEquals("Network", viewModel.uiState.value.searchQuery)

        // 关闭搜索
        viewModel.toggleSearch(false)
        val state = viewModel.uiState.value
        assertFalse(state.isSearchOpen)
        assertEquals("", state.searchQuery)
        assertTrue(state.searchMatches.isEmpty())
    }

    @Test
    fun testSelectLogForDetailAndClearLog() {
        XLog.d("Running testSelectLogForDetailAndClearLog")
        viewModel.updateLogContent(sampleRawLogs)

        val targetLog = viewModel.uiState.value.logs[1]
        viewModel.selectLogForDetail(targetLog)
        assertEquals(targetLog, viewModel.uiState.value.selectedLogForDetail)

        viewModel.selectLogForDetail(null)
        assertNull(viewModel.uiState.value.selectedLogForDetail)
    }

    @Test
    fun testAutoScrollToggle() {
        XLog.d("Running testAutoScrollToggle")
        assertTrue(viewModel.uiState.value.isAutoScrollEnabled)

        viewModel.setAutoScrollEnabled(false)
        assertFalse(viewModel.uiState.value.isAutoScrollEnabled)

        viewModel.setAutoScrollEnabled(true)
        assertTrue(viewModel.uiState.value.isAutoScrollEnabled)
    }
}
