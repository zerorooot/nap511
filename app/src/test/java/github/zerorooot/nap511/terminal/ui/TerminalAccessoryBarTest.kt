package github.zerorooot.nap511.terminal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 虚拟辅助按键栏模型驱动化单元测试
 */
class TerminalAccessoryBarTest {

    @Test
    fun testDefaultKeyRowsStructureAndLabels() {
        val actions = TerminalAccessoryActions()
        val rows = TerminalAccessoryDefaults.defaultKeyRows(
            actions = actions,
            isCtrlActive = false,
            isAltActive = false
        )

        assertEquals("必须包含两排按键", 2, rows.size)
        assertEquals("第一排必须包含 8 个按键", 8, rows[0].size)
        assertEquals("第二排必须包含 8 个按键", 8, rows[1].size)

        // 验证第一排标签与顺序
        val expectedRow1 = listOf("↹", "CTRL", "/", "-", "HOME", "↑", "END", "PAUP")
        assertEquals(expectedRow1, rows[0].map { it.label })

        // 验证第二排标签与顺序
        val expectedRow2 = listOf("≡", "ALT", "|", "*", "←", "↓", "→", "PGDN")
        assertEquals(expectedRow2, rows[1].map { it.label })
    }

    @Test
    fun testCtrlAndAltActivationState() {
        val actions = TerminalAccessoryActions()

        // 默认非激活状态
        val inactiveRows = TerminalAccessoryDefaults.defaultKeyRows(actions, isCtrlActive = false, isAltActive = false)
        val ctrlInactive = inactiveRows[0].first { it.label == "CTRL" }
        val altInactive = inactiveRows[1].first { it.label == "ALT" }
        assertTrue("CTRL 必须是强调按键", ctrlInactive.isAccent)
        assertFalse("CTRL 默认不激活", ctrlInactive.isActive)
        assertTrue("ALT 必须是强调按键", altInactive.isAccent)
        assertFalse("ALT 默认不激活", altInactive.isActive)

        // 激活状态验证
        val activeRows = TerminalAccessoryDefaults.defaultKeyRows(actions, isCtrlActive = true, isAltActive = true)
        val ctrlActive = activeRows[0].first { it.label == "CTRL" }
        val altActive = activeRows[1].first { it.label == "ALT" }
        assertTrue("CTRL 激活态正确传递", ctrlActive.isActive)
        assertTrue("ALT 激活态正确传递", altActive.isActive)
    }

    @Test
    fun testAutoRepeatOnlyOnHorizontalArrows() {
        val actions = TerminalAccessoryActions()
        val rows = TerminalAccessoryDefaults.defaultKeyRows(actions)

        val leftArrow = rows[1].first { it.label == "←" }
        val rightArrow = rows[1].first { it.label == "→" }
        assertTrue("左方向键必须支持长按连击 (autoRepeat)", leftArrow.autoRepeat)
        assertTrue("右方向键必须支持长按连击 (autoRepeat)", rightArrow.autoRepeat)

        // 其他按键不允许 autoRepeat
        val tabKey = rows[0].first { it.label == "↹" }
        val upArrow = rows[0].first { it.label == "↑" }
        val downArrow = rows[1].first { it.label == "↓" }
        assertFalse(tabKey.autoRepeat)
        assertFalse(upArrow.autoRepeat)
        assertFalse(downArrow.autoRepeat)
    }

    @Test
    fun testActionCallbacksInvoked() {
        var tabClicked = false
        var slashClicked = false
        var leftClicked = false

        val actions = TerminalAccessoryActions(
            onTab = { tabClicked = true },
            onSlash = { slashClicked = true },
            onArrowLeft = { leftClicked = true }
        )

        val rows = TerminalAccessoryDefaults.defaultKeyRows(actions)
        rows[0].first { it.label == "↹" }.onClick()
        rows[0].first { it.label == "/" }.onClick()
        rows[1].first { it.label == "←" }.onClick()

        assertTrue(tabClicked)
        assertTrue(slashClicked)
        assertTrue(leftClicked)
    }
}
