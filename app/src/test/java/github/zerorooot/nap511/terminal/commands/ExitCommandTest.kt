package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * exit 命令测试用例集合
 * 验证终端退出标记标志位的生成
 */
class ExitCommandTest {

    @Test
    fun testExitCommand() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()
        val out = engine.executeStrings("exit", ctx)
        assertEquals(listOf("__TERMINAL_EXIT__"), out)
    }
}
