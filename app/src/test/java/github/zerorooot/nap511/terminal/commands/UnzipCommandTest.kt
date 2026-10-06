package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UnzipCommandTest {

    @Test
    fun testUnzipValidations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val folder = FileBean(name = "my_folder", categoryId = "10", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(folder), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 缺失参数
        val outEmpty = engine.executeStrings("unzip", ctx)
        assertEquals(listOf("unzip: missing file operand"), outEmpty)

        // 2. 对目录执行解压提示非压缩包
        val outDir = engine.executeStrings("unzip my_folder", ctx)
        assertTrue(outDir.any { it.contains("is a directory, not an archive") })

        // 3. 文件不存在
        val outNotExist = engine.executeStrings("unzip no_file.zip", ctx)
        assertTrue(outNotExist.any { it.contains("cannot find 'no_file.zip': No such file") })
    }
}
