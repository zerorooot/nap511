package github.zerorooot.nap511.terminal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 物理键盘快捷键事件处理器单元测试
 */
class TerminalHardwareKeyActionsTest {

    @Test
    fun testCtrlShortcutsDispatch() {
        var ctrlCCalled = false
        var ctrlUCalled = false
        var ctrlKCalled = false
        var ctrlWCalled = false
        var ctrlLCalled = false
        var ctrlACalled = false
        var ctrlECalled = false
        var ctrlDCalled = false
        var ctrlLeftCalled = false
        var ctrlRightCalled = false

        val actions = TerminalHardwareKeyActions(
            onCtrlC = { ctrlCCalled = true },
            onCtrlU = { ctrlUCalled = true },
            onCtrlK = { ctrlKCalled = true },
            onCtrlW = { ctrlWCalled = true },
            onCtrlL = { ctrlLCalled = true },
            onCtrlA = { ctrlACalled = true },
            onCtrlE = { ctrlECalled = true },
            onCtrlD = { ctrlDCalled = true },
            onCtrlLeft = { ctrlLeftCalled = true },
            onCtrlRight = { ctrlRightCalled = true }
        )

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_C, actions)
        assertTrue("Ctrl+C 回调触发", ctrlCCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_U, actions)
        assertTrue("Ctrl+U 回调触发", ctrlUCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_K, actions)
        assertTrue("Ctrl+K 回调触发", ctrlKCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_W, actions)
        assertTrue("Ctrl+W 回调触发", ctrlWCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_L, actions)
        assertTrue("Ctrl+L 回调触发", ctrlLCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_A, actions)
        assertTrue("Ctrl+A 回调触发", ctrlACalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_E, actions)
        assertTrue("Ctrl+E 回调触发", ctrlECalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_D, actions)
        assertTrue("Ctrl+D 回调触发", ctrlDCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_LEFT, actions)
        assertTrue("Ctrl+Left 回调触发", ctrlLeftCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.CTRL_RIGHT, actions)
        assertTrue("Ctrl+Right 回调触发", ctrlRightCalled)
    }

    @Test
    fun testAltShortcutsDispatch() {
        var altBCalled = false
        var altFCalled = false
        var altDCalled = false
        var altBkspCalled = false
        var altDotCalled = false

        val actions = TerminalHardwareKeyActions(
            onAltB = { altBCalled = true },
            onAltF = { altFCalled = true },
            onAltD = { altDCalled = true },
            onAltBackspace = { altBkspCalled = true },
            onAltDot = { altDotCalled = true }
        )

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.ALT_B, actions)
        assertTrue("Alt+B 回调触发", altBCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.ALT_F, actions)
        assertTrue("Alt+F 回调触发", altFCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.ALT_D, actions)
        assertTrue("Alt+D 回调触发", altDCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.ALT_BACKSPACE, actions)
        assertTrue("Alt+Backspace 回调触发", altBkspCalled)

        TerminalHardwareKeyHandler.dispatchShortcut(TerminalShortcut.ALT_DOT, actions)
        assertTrue("Alt+Dot 回调触发", altDotCalled)
    }
}
