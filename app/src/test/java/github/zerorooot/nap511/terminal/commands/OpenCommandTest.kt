package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class OpenCommandTest {

    @Test
    fun testOpenCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val testFile = FileBean(name = "test.txt", fileId = "1", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(testFile), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 缺失参数
        val outEmpty = engine.executeStrings("open", ctx)
        assertEquals(listOf("open: missing file operand"), outEmpty)

        // 2. 文件不存在
        val outNotFound = engine.executeStrings("open no_such_file.mp4", ctx)
        assertEquals(listOf("open: cannot find 'no_such_file.mp4': No such file or directory"), outNotFound)

        // 3. 未配置文件打开器
        val outNoOpener = engine.executeStrings("open test.txt", ctx)
        assertEquals(listOf("open: 当前终端环境未配置文件打开器"), outNoOpener)
    }
}
