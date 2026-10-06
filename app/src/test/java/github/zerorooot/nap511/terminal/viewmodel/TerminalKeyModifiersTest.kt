package github.zerorooot.nap511.terminal.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalKeyModifiersTest {

    @Test
    fun testToggleAndReset() {
        val modifiers = TerminalKeyModifiers()
        assertFalse(modifiers.isCtrlActive)
        assertFalse(modifiers.isAltActive)

        // 开启 CTRL
        modifiers.toggleCtrl()
        assertTrue(modifiers.isCtrlActive)
        assertFalse(modifiers.isAltActive)

        // 开启 ALT 自动互斥关闭 CTRL
        modifiers.toggleAlt()
        assertFalse(modifiers.isCtrlActive)
        assertTrue(modifiers.isAltActive)

        // 重置
        modifiers.reset()
        assertFalse(modifiers.isCtrlActive)
        assertFalse(modifiers.isAltActive)
    }

    @Test
    fun testDispatchChar() {
        val modifiers = TerminalKeyModifiers()
        val actions = mutableListOf<String>()

        val handler = object : TerminalModifierActionHandler {
            override fun onCtrlC() { actions.add("Ctrl+C"); modifiers.reset() }
            override fun onCtrlU() { actions.add("Ctrl+U"); modifiers.reset() }
            override fun onCtrlK() { actions.add("Ctrl+K"); modifiers.reset() }
            override fun onCtrlW() { actions.add("Ctrl+W"); modifiers.reset() }
            override fun onCtrlL() { actions.add("Ctrl+L"); modifiers.reset() }
            override fun onCtrlA() { actions.add("Ctrl+A"); modifiers.reset() }
            override fun onCtrlE() { actions.add("Ctrl+E"); modifiers.reset() }
            override fun onCtrlD() { actions.add("Ctrl+D"); modifiers.reset() }
            override fun onAltB() { actions.add("Alt+B"); modifiers.reset() }
            override fun onAltF() { actions.add("Alt+F"); modifiers.reset() }
            override fun onAltD() { actions.add("Alt+D"); modifiers.reset() }
            override fun onAltBackspace() { actions.add("Alt+Backspace"); modifiers.reset() }
            override fun onAltDot() { actions.add("Alt+."); modifiers.reset() }
        }

        // 测试 Ctrl+C 分发
        modifiers.toggleCtrl()
        val handledC = modifiers.dispatchChar('c', handler)
        assertTrue(handledC)
        assertEquals(listOf("Ctrl+C"), actions)
        assertFalse(modifiers.isCtrlActive)

        // 测试 Alt+B 分发
        modifiers.toggleAlt()
        val handledB = modifiers.dispatchChar('B', handler)
        assertTrue(handledB)
        assertEquals(listOf("Ctrl+C", "Alt+B"), actions)
        assertFalse(modifiers.isAltActive)

        // 测试未知字符自动复位并返回 false
        modifiers.toggleCtrl()
        val handledUnknown = modifiers.dispatchChar('z', handler)
        assertFalse(handledUnknown)
        assertFalse(modifiers.isCtrlActive)
    }

    @Test
    fun testDispatchBackspace() {
        val modifiers = TerminalKeyModifiers()
        var backspaceHandled = false

        // 未开启 ALT 时不拦截
        val result1 = modifiers.dispatchBackspace { backspaceHandled = true }
        assertFalse(result1)
        assertFalse(backspaceHandled)

        // 开启 ALT 时成功拦截
        modifiers.toggleAlt()
        val result2 = modifiers.dispatchBackspace { backspaceHandled = true }
        assertTrue(result2)
        assertTrue(backspaceHandled)
    }
}
