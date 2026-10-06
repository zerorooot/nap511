package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StatCommandTest {

    @Test
    fun testStatCommandWithPathAndTrailingSlash() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        val subFolder = FileBean(name = "Sample Dir A", categoryId = "200", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "100", count = 1, order = "", path = emptyList()))

        val out = engine.executeStrings("stat '/根目录/t1/Sample Dir A/'", ctx)
        assertTrue(out.any { it.contains("File: Sample Dir A") })
        assertFalse(out.contains("  File: /"))
    }

    @Test
    fun testStatFileAndErrors() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val sampleFile = FileBean(
            name = "data.csv",
            fileId = "888",
            categoryId = "0",
            size = "2048",
            pickCode = "abcd1234efgh",
            sha1 = "1234567890abcdef",
            isFolder = false
        )
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(sampleFile), cid = "0", count = 1, order = "", path = emptyList()))

        // 1. 缺失参数
        val outEmpty = engine.executeStrings("stat", ctx)
        assertEquals(listOf("stat: missing operand"), outEmpty)

        // 2. 查看不存在的文件
        val outNotFound = engine.executeStrings("stat no_file", ctx)
        assertEquals(listOf("stat: cannot stat 'no_file': No such file or directory"), outNotFound)

        // 3. 查看普通文件元数据
        val outStat = engine.executeStrings("stat data.csv", ctx)
        assertTrue(outStat.any { it.contains("File: data.csv") })
        assertTrue(outStat.any { it.contains("Type: Regular File") })
        assertTrue(outStat.any { it.contains("2048 bytes") })
        assertTrue(outStat.any { it.contains("PickCode: abcd1234efgh") })
        assertTrue(outStat.any { it.contains("SHA-1:    1234567890abcdef") })
    }
}
