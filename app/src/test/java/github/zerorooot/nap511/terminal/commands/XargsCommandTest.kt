package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class XargsCommandTest {

    @Test
    fun testXargsCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. 默认参数：echo 输出，相当于将输入作为 echo 参数输出
        val out1 = engine.executeStrings("echo 'a\nb\nc' | xargs", ctx)
        assertEquals(listOf("a b c"), out1)

        // 2. -n 1 逐个分批传递给 echo
        val out2 = engine.executeStrings("echo 'apple banana orange' | xargs -n 1 echo", ctx)
        assertEquals(listOf("apple", "banana", "orange"), out2)

        // 3. -I {} 占位符逐行替换测试
        val out3 = engine.executeStrings("echo 'folder1\nfolder2' | xargs -I {} echo move_{}_target", ctx)
        assertEquals(listOf("move_folder1_target", "move_folder2_target"), out3)

        // 4. -t 选项回显测试
        val out4 = engine.executeStrings("echo 'hello' | xargs -t echo", ctx)
        assertEquals(listOf("+ echo hello", "hello"), out4)
    }
}
