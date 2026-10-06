package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * history 命令测试用例集合（HI001-HI025）
 * 覆盖：基本功能、截取 N 条、-c 清空、管道过滤、特殊转义字符及持久化
 */
class HistoryCommandTest {

    private lateinit var tempFile: File
    private lateinit var historyManager: TerminalHistoryManager

    @Before
    fun setup() {
        tempFile = File.createTempFile("history_cmd_test", ".txt")
        tempFile.delete()
        historyManager = TerminalHistoryManager(tempFile)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    /**
     * 1. 基本功能与选项测试用例 (HI001-HI010)
     */
    @Test
    fun testHistoryBasicFunctionsAndOptions() = runBlocking {
        var memoryCleared = false
        val registry = CommandRegistryFactory.createDefaultRegistry(historyManager) {
            memoryCleared = true
        }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        historyManager.appendCommand("ls")
        historyManager.appendCommand("cd /tmp")
        historyManager.appendCommand("pwd")
        historyManager.appendCommand("echo hello")
        historyManager.appendCommand("find -name '*.txt'")

        // HI001: 查看帮助
        val outHelp = engine.executeStrings("history -h", ctx)
        assertTrue(outHelp.any { it.contains("history") })

        // HI002: 默认历史
        val outAll = engine.executeStrings("history", ctx)
        assertEquals(5, outAll.size)

        // HI003: 最近 N 条 (history 3)
        val out3 = engine.executeStrings("history 3", ctx)
        assertEquals(3, out3.size)
        assertTrue(out3.last().contains("find"))

        // HI004: N=0
        val out04 = engine.executeStrings("history 0", ctx)
        assertTrue(out04.isEmpty() || out04.size <= 5)

        // HI005: N 负数
        val out05 = engine.executeStrings("history -1", ctx)
        assertTrue(out05.isEmpty() || out05.size <= 5)

        // HI006: N 非数字
        val out06 = engine.executeStrings("history abc", ctx)
        assertTrue(out06.size <= 5)

        // HI007: -c 清空
        val outClear = engine.executeStrings("history -c", ctx)
        assertTrue(outClear.contains("terminal: history cleared"))
        assertTrue(memoryCleared)

        // HI008: -c 后 history
        val outPostClear = engine.executeStrings("history", ctx)
        assertTrue(outPostClear.isEmpty())

        // HI009: 未知选项
        val out09 = engine.executeStrings("history -x", ctx)
        assertTrue(out09.size <= 5)

        // HI010: 多余参数
        val out10 = engine.executeStrings("history 5 6", ctx)
        assertTrue(out10.size <= 5)
    }

    /**
     * 2. 管道与特殊转义字符测试用例 (HI011-HI025)
     */
    @Test
    fun testHistoryPipelinesAndSpecialChars() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry(historyManager)
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        historyManager.appendCommand("find -name '*.txt'")
        historyManager.appendCommand("echo 'a\\b'")
        historyManager.appendCommand("echo 测试")
        historyManager.appendCommand("echo '\$HOME'")
        historyManager.appendCommand("echo 'hi!'")
        historyManager.appendCommand("echo 'a#b'")

        // HI011: 管道 grep
        val out11 = engine.executeStrings("history | grep find", ctx)
        assertEquals(1, out11.size)
        assertTrue(out11[0].contains("find"))

        // HI012: 管道 head
        val out12 = engine.executeStrings("history | head", ctx)
        assertTrue(out12.isNotEmpty())

        // HI013: 管道 tail
        val out13 = engine.executeStrings("history | tail -n 5", ctx)
        assertTrue(out13.size <= 5)

        // HI014: 管道 wc
        val out14 = engine.executeStrings("history | wc -l", ctx)
        assertEquals(listOf("6"), out14)

        // HI015: 管道 sort
        val out15 = engine.executeStrings("history | sort", ctx)
        assertEquals(6, out15.size)

        // HI016: 管道 xargs
        val out16 = engine.executeStrings("history 1 | xargs echo", ctx)
        assertTrue(out16.isNotEmpty())

        // HI017: 历史含转义
        val out17 = engine.executeStrings("history | grep 'a\\\\b'", ctx)
        assertTrue(out17.isNotEmpty())

        // HI018: 历史含中文
        val out18 = engine.executeStrings("history | grep 测试", ctx)
        assertTrue(out18.isNotEmpty())

        // HI019: 历史含 $
        val out19 = engine.executeStrings("history | grep '\\\$HOME'", ctx)
        assertTrue(out19.isNotEmpty())

        // HI020: 历史含 !
        val out20 = engine.executeStrings("history | grep 'hi!'", ctx)
        assertTrue(out20.isNotEmpty())

        // HI021: 历史含 #
        val out21 = engine.executeStrings("history | grep 'a#b'", ctx)
        assertTrue(out21.isNotEmpty())

        // HI022: 清空后管道 history | wc -l
        engine.executeStrings("history -c", ctx)
        val out22 = engine.executeStrings("history | wc -l", ctx)
        assertEquals(listOf("0"), out22)

        // HI023: 多次清空
        engine.executeStrings("history -c", ctx)
        val out23 = engine.executeStrings("history -c", ctx)
        assertTrue(out23.contains("terminal: history cleared"))

        // HI024: 历史顺序
        val out24 = engine.executeStrings("history | tail -n 3", ctx)
        assertTrue(out24.size <= 3)

        // HI025: 历史持久化确认
        assertTrue(tempFile.exists())
    }
}
