package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class PwdCommandTest {

    @Test
    fun testPwdCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val out = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/"), out)

        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("99", "work", "0")))
        val outWork = engine.executeStrings("pwd", ctx)
        assertEquals(listOf("/根目录/work"), outWork)
    }
}
