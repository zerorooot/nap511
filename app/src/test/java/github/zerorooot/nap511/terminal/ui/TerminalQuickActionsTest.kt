package github.zerorooot.nap511.terminal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终端快捷操作模型化单元测试
 */
class TerminalQuickActionsTest {

    @Test
    fun testDefaultActionsListIntegrity() {
        var dismissed = false
        val callbacks = TerminalQuickActionCallbacks()

        val actions = TerminalQuickActionDefaults.defaultActions(
            onDismiss = { dismissed = true },
            callbacks = callbacks
        )

        assertEquals("默认必须包含 7 项快捷操作", 7, actions.size)

        val expectedIds = listOf("help", "ls", "df", "clear", "copy_all", "drawer", "exit")
        assertEquals(expectedIds, actions.map { it.id })

        // 验证分割线位置：仅侧边栏项 (drawer) 前放置分割线
        val drawerAction = actions.first { it.id == "drawer" }
        assertTrue("drawer 项前必须有分割线", drawerAction.hasDividerBefore)

        val otherActions = actions.filter { it.id != "drawer" }
        otherActions.forEach { action ->
            assertFalse("非 drawer 项 [${action.id}] 前不应有分割线", action.hasDividerBefore)
        }
    }

    @Test
    fun testActionCallbacksExecutionAndDismiss() {
        var dismissedCount = 0
        var executedCommand: String? = null
        var clearCalled = false
        var copyCalled = false
        var drawerCalled = false
        var closeCalled = false

        val callbacks = TerminalQuickActionCallbacks(
            onExecuteCommand = { executedCommand = it },
            onClearScreen = { clearCalled = true },
            onCopyAll = { copyCalled = true },
            onOpenDrawer = { drawerCalled = true },
            onCloseTerminal = { closeCalled = true }
        )

        val actions = TerminalQuickActionDefaults.defaultActions(
            onDismiss = { dismissedCount++ },
            callbacks = callbacks
        )

        // 1. 触发 help
        actions.first { it.id == "help" }.onClick()
        assertEquals("?", executedCommand)
        assertEquals(1, dismissedCount)

        // 2. 触发 ls
        actions.first { it.id == "ls" }.onClick()
        assertEquals("ls -l", executedCommand)
        assertEquals(2, dismissedCount)

        // 3. 触发 df
        actions.first { it.id == "df" }.onClick()
        assertEquals("df -h", executedCommand)
        assertEquals(3, dismissedCount)

        // 4. 触发 clear
        actions.first { it.id == "clear" }.onClick()
        assertTrue(clearCalled)
        assertEquals(4, dismissedCount)

        // 5. 触发 copy_all
        actions.first { it.id == "copy_all" }.onClick()
        assertTrue(copyCalled)
        assertEquals(5, dismissedCount)

        // 6. 触发 drawer
        actions.first { it.id == "drawer" }.onClick()
        assertTrue(drawerCalled)
        assertEquals(6, dismissedCount)

        // 7. 触发 exit
        actions.first { it.id == "exit" }.onClick()
        assertTrue(closeCalled)
        assertEquals(7, dismissedCount)
    }
}
