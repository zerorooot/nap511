package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * df 命令测试用例集合
 * 验证网盘空间查看帮助拦截与 -h 容量人性化选项的解析
 */
class DfCommandTest {

    @Test
    fun testDfCommandAndOptionH(): Unit = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 测试 df --help 输出帮助文档
        val helpOut = engine.executeStrings("df --help", ctx).joinToString("\n")
        assertTrue(helpOut.contains("命令名称: df"))
        assertTrue(helpOut.contains("人性化容量单位显示"))

        // 测试 df -h 不再被误拦截为帮助，而是正常执行 df 指令逻辑
        val dfOut = engine.executeStrings("df -h", ctx).joinToString("\n")
        assertFalse(dfOut.contains("命令名称: df"))
    }
}
