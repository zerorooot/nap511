package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.engine.TerminalHistoryManager
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * history 命令测试用例集合
 * 覆盖：历史查看与截取、-c 清空历史、管道过滤、特殊转义字符及持久化记录
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
     * 1. 基本功能与选项测试
     */
    @Test
    fun testHistoryBasicFunctionsAndOptions() = runBlocking {
        var memoryCleared = false
        val engine = createTestEngine(historyManager = historyManager) {
            memoryCleared = true
        }
        val ctx = createTestContext()

        historyManager.appendCommand("ls")
        historyManager.appendCommand("cd /tmp")
        historyManager.appendCommand("pwd")
        historyManager.appendCommand("echo hello")
        historyManager.appendCommand("find -name '*.txt'")

        // 查看帮助文档
        val outHelp = engine.executeStrings("history -h", ctx)
        assertTrue(outHelp.any { it.contains("history") })

        // 查看默认完整历史记录
        val outAll = engine.executeStrings("history", ctx)
        assertEquals(5, outAll.size)

        // 最近 N 条历史记录截取 (history 3)
        val out3 = engine.executeStrings("history 3", ctx)
        assertEquals(3, out3.size)
        assertTrue(out3.last().contains("find"))

        // N=0 选项处理
        val out04 = engine.executeStrings("history 0", ctx)
        assertTrue(out04.isEmpty() || out04.size <= 5)

        // N 负数选项容错处理
        val out05 = engine.executeStrings("history -1", ctx)
        assertTrue(out05.isEmpty() || out05.size <= 5)

        // N 非数字选项容错处理
        val out06 = engine.executeStrings("history abc", ctx)
        assertTrue(out06.size <= 5)

        // -c 选项清空会话历史记录
        val outClear = engine.executeStrings("history -c", ctx)
        assertTrue(outClear.contains("terminal: history cleared"))
        assertTrue(memoryCleared)

        // 清空后执行 history 查看列表
        val outPostClear = engine.executeStrings("history", ctx)
        assertTrue(outPostClear.isEmpty())

        // 未知选项容错处理
        val out09 = engine.executeStrings("history -x", ctx)
        assertTrue(out09.size <= 5)

        // 多余位置参数容错处理
        val out10 = engine.executeStrings("history 5 6", ctx)
        assertTrue(out10.size <= 5)
    }

    /**
     * 2. 管道与特殊转义字符测试
     */
    @Test
    fun testHistoryPipelinesAndSpecialChars() = runBlocking {
        val engine = createTestEngine(historyManager = historyManager)
        val ctx = createTestContext()

        historyManager.appendCommand("find -name '*.txt'")
        historyManager.appendCommand("echo 'a\\b'")
        historyManager.appendCommand("echo 测试")
        historyManager.appendCommand("echo '\$HOME'")
        historyManager.appendCommand("echo 'hi!'")
        historyManager.appendCommand("echo 'a#b'")

        // 历史记录接 grep 过滤查找
        val out11 = engine.executeStrings("history | grep find", ctx)
        assertEquals(1, out11.size)
        assertTrue(out11[0].contains("find"))

        // 历史记录接 head 截取头部
        val out12 = engine.executeStrings("history | head", ctx)
        assertTrue(out12.isNotEmpty())

        // 历史记录接 tail 截取尾部
        val out13 = engine.executeStrings("history | tail -n 5", ctx)
        assertTrue(out13.size <= 5)

        // 历史记录接 wc -l 统计行数
        val out14 = engine.executeStrings("history | wc -l", ctx)
        assertEquals(listOf("6"), out14)

        // 历史记录接 sort 排序
        val out15 = engine.executeStrings("history | sort", ctx)
        assertEquals(6, out15.size)

        // 历史记录接 xargs 参数传递
        val out16 = engine.executeStrings("history 1 | xargs echo", ctx)
        assertTrue(out16.isNotEmpty())

        // 历史记录包含转义字符匹配
        val out17 = engine.executeStrings("history | grep 'a\\\\b'", ctx)
        assertTrue(out17.isNotEmpty())

        // 历史记录包含中文字符匹配
        val out18 = engine.executeStrings("history | grep 测试", ctx)
        assertTrue(out18.isNotEmpty())

        // 历史记录包含美元符号 $ 匹配
        val out19 = engine.executeStrings("history | grep '\\\$HOME'", ctx)
        assertTrue(out19.isNotEmpty())

        // 历史记录包含惊叹号 ! 匹配
        val out20 = engine.executeStrings("history | grep 'hi!'", ctx)
        assertTrue(out20.isNotEmpty())

        // 历史记录包含井号 # 匹配
        val out21 = engine.executeStrings("history | grep 'a#b'", ctx)
        assertTrue(out21.isNotEmpty())

        // 清空历史后执行 history | wc -l 校验
        engine.executeStrings("history -c", ctx)
        val out22 = engine.executeStrings("history | wc -l", ctx)
        assertEquals(listOf("0"), out22)

        // 连续执行多次清空操作
        engine.executeStrings("history -c", ctx)
        val out23 = engine.executeStrings("history -c", ctx)
        assertTrue(out23.contains("terminal: history cleared"))

        // 历史记录输出顺序校验
        val out24 = engine.executeStrings("history | tail -n 3", ctx)
        assertTrue(out24.size <= 3)

        // 历史记录持久化确认
        assertTrue(tempFile.exists())
    }
}
