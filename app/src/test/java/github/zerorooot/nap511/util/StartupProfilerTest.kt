package github.zerorooot.nap511.util

import github.zerorooot.nap511.BuildConfig
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * 验证 StartupProfiler 耗时分析工具的行为：
 * 1. 默认状态跟随 BuildConfig.DEBUG
 * 2. 禁用状态（Release 模式）下零开销，不产生打点记录与控制台输出
 * 3. 启用状态（Debug 模式）下正常记录阶段耗时与输出报表
 */
class StartupProfilerTest {

    private val originalOut = System.out
    private val testOut = ByteArrayOutputStream()

    @Before
    fun setUp() {
        System.setOut(PrintStream(testOut))
        StartupProfiler.resetForTesting()
        StartupProfiler.isEnabled = true
    }

    @After
    fun tearDown() {
        System.setOut(originalOut)
        StartupProfiler.resetForTesting()
        StartupProfiler.isEnabled = BuildConfig.DEBUG
    }

    @Test
    fun testDefaultIsEnabledFollowsBuildConfigDebug() {
        // 验证默认配置与 BuildConfig.DEBUG 一致
        assertEquals(BuildConfig.DEBUG, StartupProfiler.isEnabled)
    }

    @Test
    fun testDisabledModeProducesNoRecordsOrOutput() {
        StartupProfiler.isEnabled = false

        StartupProfiler.step("Test Step 1")
        StartupProfiler.step("Test Step 2", "with extra")
        StartupProfiler.finish("Test Finished")

        assertTrue("禁用模式下不应记录任何打点", StartupProfiler.getRecordsForTesting().isEmpty())
        assertTrue("禁用模式下不应向控制台输出任何内容", testOut.toString().isEmpty())
    }

    @Test
    fun testEnabledModeRecordsStepsAndPrintsBoard() {
        StartupProfiler.isEnabled = true

        StartupProfiler.step("App.onCreate start")
        StartupProfiler.step("MainActivity.onCreate", "extra info")
        StartupProfiler.finish("Startup Completed")

        val records = StartupProfiler.getRecordsForTesting()
        assertEquals(3, records.size)
        assertEquals("App.onCreate start", records[0].name)
        assertEquals("MainActivity.onCreate", records[1].name)
        assertEquals("extra info", records[1].extra)
        assertEquals("Startup Completed", records[2].name)

        val output = testOut.toString()
        assertTrue("输出应包含 Startup 标签", output.contains("[Startup]"))
        assertTrue("输出应包含看板标题", output.contains("启动耗时看板"))
        assertTrue("输出应包含步骤名称", output.contains("App.onCreate start"))
        assertTrue("输出应包含备注信息", output.contains("[extra info]"))
        assertTrue("输出应包含总计耗时", output.contains("总计耗时"))
    }
}
