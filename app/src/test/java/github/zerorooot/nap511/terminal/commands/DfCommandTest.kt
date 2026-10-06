package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DfCommandTest {

    @Test
    fun testDfCommandAndOptionH(): Unit = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 测试 df --help 输出帮助文档
        val helpOut = engine.executeStrings("df --help", ctx).joinToString("\n")
        assertTrue(helpOut.contains("命令名称: df"))
        assertTrue(helpOut.contains("人性化容量单位显示"))

        // 测试 df -h 不再被误拦截为帮助，而是正常执行 df 指令逻辑
        val dfOut = engine.executeStrings("df -h", ctx).joinToString("\n")
        assertFalse(dfOut.contains("命令名称: df"))
    }
}
