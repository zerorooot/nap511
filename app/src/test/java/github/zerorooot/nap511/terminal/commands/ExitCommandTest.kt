package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ExitCommandTest {

    @Test
    fun testExitCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        val out = engine.executeStrings("exit", ctx)
        assertEquals(listOf("__TERMINAL_EXIT__"), out)
    }
}
