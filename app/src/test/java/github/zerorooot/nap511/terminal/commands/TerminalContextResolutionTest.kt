package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalContextResolutionTest {

    @Test
    fun testResolveTargetWithRootPrefix() = runBlocking {
        val ctx = TerminalContext()

        // 模拟根目录下包含文件夹 "t1" (cid=100)
        val t1Folder = FileBean(
            name = "t1",
            categoryId = "100",
            isFolder = true
        )
        val rootFiles = FilesBean(
            fileBeanList = arrayListOf(t1Folder),
            cid = "0",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("0", rootFiles)

        // 模拟 t1 目录下包含文件夹 "Sample Dir Alpha" (cid=200)
        val subFolder = FileBean(
            name = "Sample Dir Alpha",
            categoryId = "200",
            isFolder = true
        )
        val t1Files = FilesBean(
            fileBeanList = arrayListOf(subFolder),
            cid = "100",
            count = 1,
            order = "",
            path = emptyList()
        )
        ctx.fileCacheManager.put("100", t1Files)

        // 测试解析包含 "/根目录" 前缀的绝对路径
        val resolved = ctx.resolveTarget("/根目录/t1/Sample Dir Alpha/")
        assertTrue(resolved is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir = resolved as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir.cid)
        assertEquals("/根目录/t1/Sample Dir Alpha", dir.path)
    }

    @Test
    fun testResolveTargetDirectoryMetadataAndTrailingSlash() = runBlocking {
        val ctx = TerminalContext()

        // 模拟根目录下包含文件夹 "t1" (cid="100")
        val t1Folder = FileBean(name = "t1", categoryId = "100", isFolder = true)
        ctx.fileCacheManager.put("0", FilesBean(
            fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()
        ))

        // 模拟 t1 目录下包含 "Sample Dir A" (cid="200") 和 "Sample Dir B(1)" (cid="201")
        val sub1 = FileBean(name = "Sample Dir A", categoryId = "200", isFolder = true)
        val sub2 = FileBean(name = "Sample Dir B(1)", categoryId = "201", isFolder = true)
        ctx.fileCacheManager.put("100", FilesBean(
            fileBeanList = arrayListOf(sub1, sub2), cid = "100", count = 2, order = "", path = emptyList()
        ))

        // 1. 绝对路径且带末尾斜杠
        val res1 = ctx.resolveTarget("/根目录/t1/Sample Dir A/")
        assertTrue(res1 is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir1 = res1 as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir1.cid)
        assertEquals("100", dir1.parentCid)
        assertEquals("Sample Dir A", dir1.name)
        assertEquals("/根目录/t1/Sample Dir A", dir1.path)

        // 2. 相对路径且带末尾斜杠及转义/空格
        val res2 = ctx.resolveTarget("t1/Sample Dir B(1)/")
        assertTrue(res2 is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir2 = res2 as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("201", dir2.cid)
        assertEquals("100", dir2.parentCid)
        assertEquals("Sample Dir B(1)", dir2.name)

        // 3. 在 t1 目录下直接解析当前目录下的子文件夹（带或不带斜杠）
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0")))
        val res3 = ctx.resolveTarget("Sample Dir A")
        assertTrue(res3 is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir3 = res3 as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir3.cid)
        assertEquals("100", dir3.parentCid)
        assertEquals("Sample Dir A", dir3.name)

        val res3Slash = ctx.resolveTarget("Sample Dir A/")
        assertTrue(res3Slash is github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory)
        val dir3Slash = res3Slash as github.zerorooot.nap511.terminal.context.ResolvedTarget.Directory
        assertEquals("200", dir3Slash.cid)
        assertEquals("100", dir3Slash.parentCid)
        assertEquals("Sample Dir A", dir3Slash.name)
    }
}
