package github.zerorooot.nap511.terminal.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 终端吸底与滚动安全边界单元测试
 * 针对【关键机制 3 - safeScrollToBottom 防越界】与【关键机制 4B - autoScrollToBottom 跟随输出模式】建立自动化看护
 */
class TerminalScrollSafetyTest {

    @Test
    fun testCalculateTargetIndexZeroAndNegativeItems() {
        // 验证条目为 0 或负数时的极端边界，确保计算结果恒 >= 0，严防 IndexOutOfBoundsException
        assertEquals(0, TerminalScrollSafetyHelper.calculateTargetIndex(0))
        assertEquals(0, TerminalScrollSafetyHelper.calculateTargetIndex(-1))
        assertEquals(0, TerminalScrollSafetyHelper.calculateTargetIndex(-100))
    }

    @Test
    fun testCalculateTargetIndexPositiveItems() {
        assertEquals(0, TerminalScrollSafetyHelper.calculateTargetIndex(1))
        assertEquals(9, TerminalScrollSafetyHelper.calculateTargetIndex(10))
        assertEquals(999, TerminalScrollSafetyHelper.calculateTargetIndex(1000))
    }

    @Test
    fun testIsNearBottomCalculation() {
        // 空列表或 0 条目默认判定为底部
        assertTrue(TerminalScrollSafetyHelper.isNearBottom(0, 0))

        val total = 100
        // 倒数第 1 项 (99)、倒数第 2 项 (98)、倒数第 3 项 (97) 均处于吸附阈值区
        assertTrue(TerminalScrollSafetyHelper.isNearBottom(99, total, threshold = 3))
        assertTrue(TerminalScrollSafetyHelper.isNearBottom(98, total, threshold = 3))
        assertTrue(TerminalScrollSafetyHelper.isNearBottom(97, total, threshold = 3))

        // 离开底部 (倒数第 4 项及更早，如 96, 50, 0)，判定离开底部
        assertFalse(TerminalScrollSafetyHelper.isNearBottom(96, total, threshold = 3))
        assertFalse(TerminalScrollSafetyHelper.isNearBottom(50, total, threshold = 3))
        assertFalse(TerminalScrollSafetyHelper.isNearBottom(0, total, threshold = 3))
    }

    @Test
    fun testCalculatePageUpTargetBoundary() {
        // PageUp 向上翻页不能越界低于 0
        assertEquals(0, TerminalScrollSafetyHelper.calculatePageUpTarget(currentFirstIndex = 5, pageSize = 12))
        assertEquals(0, TerminalScrollSafetyHelper.calculatePageUpTarget(currentFirstIndex = 0, pageSize = 12))
        assertEquals(8, TerminalScrollSafetyHelper.calculatePageUpTarget(currentFirstIndex = 20, pageSize = 12))
    }

    @Test
    fun testCalculatePageDownTargetBoundary() {
        val total = 50
        val maxIndex = 49
        // PageDown 向下翻页不能超过 maxIndex
        assertEquals(maxIndex, TerminalScrollSafetyHelper.calculatePageDownTarget(currentFirstIndex = 45, totalItemsCount = total, pageSize = 12))
        assertEquals(maxIndex, TerminalScrollSafetyHelper.calculatePageDownTarget(currentFirstIndex = 49, totalItemsCount = total, pageSize = 12))
        assertEquals(22, TerminalScrollSafetyHelper.calculatePageDownTarget(currentFirstIndex = 10, totalItemsCount = total, pageSize = 12))
    }
}
