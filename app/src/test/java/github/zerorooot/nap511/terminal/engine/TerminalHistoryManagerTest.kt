package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.commands.CommandRegistryFactory
import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class TerminalHistoryManagerTest {

    private lateinit var tempFile: File
    private lateinit var historyManager: TerminalHistoryManager

    @Before
    fun setup() {
        tempFile = File.createTempFile("test_history", ".txt")
        tempFile.delete()
        historyManager = TerminalHistoryManager(tempFile)
    }

    @After
    fun tearDown() {
        if (tempFile.exists()) {
            tempFile.delete()
        }
    }

    @Test
    fun testIsValidCommand() {
        val registered = setOf("ls", "cd", "cat", "grep", "clear")
        val isRegistered: (String) -> Boolean = { registered.contains(it) }

        // 1. 合法单命令与带参数
        assertTrue(historyManager.isValidCommand("ls -la", isRegistered))
        assertTrue(historyManager.isValidCommand("cd /tmp", isRegistered))

        // 2. 合法管道命令
        assertTrue(historyManager.isValidCommand("cat file.txt | grep 'pattern'", isRegistered))

        // 3. 非法未注册命令
        assertFalse(historyManager.isValidCommand("unknown_cmd", isRegistered))
        assertFalse(historyManager.isValidCommand("ls | unknown_cmd", isRegistered))

        // 4. 空命令与语法错误
        assertFalse(historyManager.isValidCommand("", isRegistered))
        assertFalse(historyManager.isValidCommand("   ", isRegistered))
    }

    @Test
    fun testAppendAndDeduplication() {
        // 首次写入
        assertTrue(historyManager.appendCommand("ls -l"))
        assertEquals(listOf("ls -l"), tempFile.readLines())

        // 连续重复写入 -> 应该被忽略 (ignoredups)
        assertFalse(historyManager.appendCommand("ls -l"))
        assertEquals(listOf("ls -l"), tempFile.readLines())

        // 写入新命令
        assertTrue(historyManager.appendCommand("cd /root"))
        assertEquals(listOf("ls -l", "cd /root"), tempFile.readLines())

        // 非连续重复写入 -> 允许写入
        assertTrue(historyManager.appendCommand("ls -l"))
        assertEquals(listOf("ls -l", "cd /root", "ls -l"), tempFile.readLines())
    }

    @Test
    fun testStreamHistory() = runBlocking {
        historyManager.appendCommand("mkdir demo")
        historyManager.appendCommand("cd demo")
        historyManager.appendCommand("touch test.txt")

        // 全量流式读取
        val allHistory = historyManager.streamHistory().toList()
        assertEquals(3, allHistory.size)
        assertEquals("   1  mkdir demo", allHistory[0])
        assertEquals("   2  cd demo", allHistory[1])
        assertEquals("   3  touch test.txt", allHistory[2])

        // 截取最近 2 条 (history 2)
        val limitedHistory = historyManager.streamHistory(limit = 2).toList()
        assertEquals(2, limitedHistory.size)
        assertEquals("   2  cd demo", limitedHistory[0])
        assertEquals("   3  touch test.txt", limitedHistory[1])
    }

    @Test
    fun testLoadRecentHistory() {
        for (i in 1..10) {
            historyManager.appendCommand("cmd_$i")
        }

        // 截取最近 4 条
        val recent = historyManager.loadRecentHistory(limit = 4)
        assertEquals(4, recent.size)
        assertEquals(listOf("cmd_7", "cmd_8", "cmd_9", "cmd_10"), recent)
    }

    @Test
    fun testClearHistory() = runBlocking {
        historyManager.appendCommand("ls")
        historyManager.appendCommand("pwd")
        assertEquals(2, historyManager.streamHistory().toList().size)

        assertTrue(historyManager.clearHistory())
        assertEquals(0, historyManager.streamHistory().toList().size)
    }

    @Test
    fun testHistoryCommandExecution() = runBlocking {
        var memoryCleared = false
        val registry = CommandRegistryFactory.createDefaultRegistry(historyManager) {
            memoryCleared = true
        }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        historyManager.appendCommand("echo hello")
        historyManager.appendCommand("ls -l")

        // 1. 测试常规 history
        val output1 = engine.execute("history", ctx).toList()
        assertEquals(2, output1.size)
        assertTrue(output1[0].contains("echo hello"))
        assertTrue(output1[1].contains("ls -l"))

        // 2. 测试 history 1 (只取最后一条)
        val output2 = engine.execute("history 1", ctx).toList()
        assertEquals(1, output2.size)
        assertTrue(output2[0].contains("ls -l"))

        // 3. 测试 history -c (清空)
        val output3 = engine.execute("history -c", ctx).toList()
        assertEquals(listOf("terminal: history cleared"), output3.map { it.text })
        assertTrue(memoryCleared)
        assertEquals(0, historyManager.streamHistory().toList().size)
    }
}
