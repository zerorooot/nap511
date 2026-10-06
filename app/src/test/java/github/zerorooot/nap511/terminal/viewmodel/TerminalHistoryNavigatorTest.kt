package github.zerorooot.nap511.terminal.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalHistoryNavigatorTest {

    @Test
    fun testHistoryNavigationFlow() {
        val navigator = TerminalHistoryNavigator()
        assertTrue(navigator.isEmpty)

        navigator.add("ls -la")
        navigator.add("cd /root")
        navigator.add("pwd")
        // 重复添加 pwd，相邻自动去重
        navigator.add("pwd")

        assertEquals(3, navigator.memoryHistory.size)
        assertEquals("pwd", navigator.lastCommand)

        // 第一次向上漫游：保存当前输入草稿 "draft" 并返回最后一条 "pwd"
        val cmd1 = navigator.navigateUp("draft")
        assertEquals("pwd", cmd1)
        assertEquals("draft", navigator.savedDraftInput)
        assertEquals(2, navigator.historyPointer)

        // 再次向上漫游：返回 "cd /root"
        val cmd2 = navigator.navigateUp("ignored")
        assertEquals("cd /root", cmd2)
        assertEquals(1, navigator.historyPointer)

        // 再次向上漫游：返回 "ls -la"
        val cmd3 = navigator.navigateUp("ignored")
        assertEquals("ls -la", cmd3)
        assertEquals(0, navigator.historyPointer)

        // 到达顶端后继续向上：依然保持在 "ls -la"
        val cmdTop = navigator.navigateUp("ignored")
        assertEquals("ls -la", cmdTop)

        // 向下漫游：返回 "cd /root"
        val down1 = navigator.navigateDown()
        assertEquals("cd /root", down1)

        // 向下漫游：返回 "pwd"
        val down2 = navigator.navigateDown()
        assertEquals("pwd", down2)

        // 向下到底部：恢复原始草稿 "draft"
        val downDraft = navigator.navigateDown()
        assertEquals("draft", downDraft)
        assertEquals(-1, navigator.historyPointer)

        // 到底部后继续向下：返回 null
        val downNull = navigator.navigateDown()
        assertNull(downNull)
    }

    @Test
    fun testLoadAndClear() {
        val navigator = TerminalHistoryNavigator()
        navigator.load(listOf("git status", "git log"))

        assertEquals(2, navigator.memoryHistory.size)
        assertEquals("git log", navigator.lastCommand)

        navigator.clear()
        assertTrue(navigator.isEmpty)
        assertNull(navigator.lastCommand)
        assertEquals(-1, navigator.historyPointer)
    }

    @Test
    fun testResetPointer() {
        val navigator = TerminalHistoryNavigator()
        navigator.add("echo 1")
        navigator.add("echo 2")

        // 漫游到第 0 项
        navigator.navigateUp("my draft")
        navigator.navigateUp("my draft")
        assertEquals(0, navigator.historyPointer)
        assertEquals("my draft", navigator.savedDraftInput)

        // 重置游标与草稿
        navigator.resetPointer()
        assertEquals(-1, navigator.historyPointer)
        assertEquals("", navigator.savedDraftInput)

        // 重新上翻应重新从末尾项 "echo 2" 开始
        val freshUp = navigator.navigateUp("new draft")
        assertEquals("echo 2", freshUp)
        assertEquals(1, navigator.historyPointer)
        assertEquals("new draft", navigator.savedDraftInput)
    }
}
