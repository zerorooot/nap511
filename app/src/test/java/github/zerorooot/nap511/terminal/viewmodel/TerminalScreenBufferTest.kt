package github.zerorooot.nap511.terminal.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalScreenBufferTest {

    @Test
    fun testAppendSingleLineAndTruncation() {
        val buffer = TerminalScreenBuffer(maxScrollbackLines = 5)
        assertTrue(buffer.isEmpty)
        assertEquals(0, buffer.size)

        for (i in 1..5) {
            buffer.appendLine(TerminalLine("line $i", TerminalLineType.Output.TEXT))
        }
        assertEquals(5, buffer.size)
        assertFalse(buffer.isEmpty)
        assertEquals("line 1", buffer.lines.first().text)
        assertEquals("line 5", buffer.lines.last().text)

        // 追加第 6 行，触发头部最旧行修剪截断
        buffer.appendLine(TerminalLine("line 6", TerminalLineType.Output.TEXT))
        assertEquals(5, buffer.size)
        assertEquals("line 2", buffer.lines.first().text)
        assertEquals("line 6", buffer.lines.last().text)
    }

    @Test
    fun testBatchAppendLinesAndTruncation() {
        val buffer = TerminalScreenBuffer(maxScrollbackLines = 10)

        // 批量加入 15 行
        val lines = (1..15).map { TerminalLine("batch $it", TerminalLineType.Output.TEXT) }
        buffer.appendLines(lines)

        assertEquals(10, buffer.size)
        assertEquals("batch 6", buffer.lines.first().text)
        assertEquals("batch 15", buffer.lines.last().text)

        // 追加空列表无副作用
        buffer.appendLines(emptyList())
        assertEquals(10, buffer.size)
    }

    @Test
    fun testClearAndGetAllText() {
        val buffer = TerminalScreenBuffer(maxScrollbackLines = 10)
        buffer.appendLine(TerminalLine("hello", TerminalLineType.Output.TEXT))
        buffer.appendLine(TerminalLine("world", TerminalLineType.Output.TEXT))

        assertEquals("hello\nworld", buffer.getAllText())

        buffer.clear()
        assertTrue(buffer.isEmpty)
        assertEquals("", buffer.getAllText())
    }
}
