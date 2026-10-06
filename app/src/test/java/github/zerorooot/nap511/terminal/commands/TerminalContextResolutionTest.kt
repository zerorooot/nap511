package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TerminalContext 相对与绝对路径解析单元测试
 * 校验顶级根目录前缀修剪、包含空格与末尾斜杠路径解析及层级 CID 关联
 */
class TerminalContextResolutionTest {

    /**
     * 测试带有 "/根目录" 前缀的绝对路径解析
     */
    @Test
    fun testResolveTargetWithRootPrefix() = runBlocking {
        val ctx = createTestContext()

        // 模拟根目录下包含文件夹 "t1" (cid=100)
        val t1Folder = createMockFolder("t1", "100")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        // 模拟 t1 目录下包含文件夹 "Sample Dir Alpha" (cid=200)
        val subFolder = createMockFolder("Sample Dir Alpha", "200")
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(subFolder), cid = "100", count = 1, order = "", path = emptyList()))

        // 测试解析包含 "/根目录" 前缀的绝对路径
        val resolved = ctx.resolveTarget("/根目录/t1/Sample Dir Alpha/")
        assertTrue(resolved is ResolvedTarget.Directory)
        val dir = resolved as ResolvedTarget.Directory
        assertEquals("200", dir.cid)
        assertEquals("/根目录/t1/Sample Dir Alpha", dir.path)
    }

    /**
     * 测试目录元数据提取及带末尾斜杠路径解析
     */
    @Test
    fun testResolveTargetDirectoryMetadataAndTrailingSlash() = runBlocking {
        val ctx = createTestContext()

        // 模拟根目录下包含文件夹 "t1" (cid="100")
        val t1Folder = createMockFolder("t1", "100")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(t1Folder), cid = "0", count = 1, order = "", path = emptyList()))

        // 模拟 t1 目录下包含 "Sample Dir A" (cid="200") 和 "Sample Dir B(1)" (cid="201")
        val sub1 = createMockFolder("Sample Dir A", "200")
        val sub2 = createMockFolder("Sample Dir B(1)", "201")
        ctx.fileCacheManager.put("100", FilesBean(fileBeanList = arrayListOf(sub1, sub2), cid = "100", count = 2, order = "", path = emptyList()))

        // 1. 绝对路径且带末尾斜杠
        val res1 = ctx.resolveTarget("/根目录/t1/Sample Dir A/")
        assertTrue(res1 is ResolvedTarget.Directory)
        val dir1 = res1 as ResolvedTarget.Directory
        assertEquals("200", dir1.cid)
        assertEquals("100", dir1.parentCid)
        assertEquals("Sample Dir A", dir1.name)
        assertEquals("/根目录/t1/Sample Dir A", dir1.path)

        // 2. 相对路径且带末尾斜杠及空格/特殊符号
        val res2 = ctx.resolveTarget("t1/Sample Dir B(1)/")
        assertTrue(res2 is ResolvedTarget.Directory)
        val dir2 = res2 as ResolvedTarget.Directory
        assertEquals("201", dir2.cid)
        assertEquals("100", dir2.parentCid)
        assertEquals("Sample Dir B(1)", dir2.name)

        // 3. 在 t1 目录下直接解析当前目录下的子文件夹（带或不带斜杠）
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("100", "t1", "0")))
        val res3 = ctx.resolveTarget("Sample Dir A")
        assertTrue(res3 is ResolvedTarget.Directory)
        val dir3 = res3 as ResolvedTarget.Directory
        assertEquals("200", dir3.cid)
        assertEquals("100", dir3.parentCid)
        assertEquals("Sample Dir A", dir3.name)

        val res3Slash = ctx.resolveTarget("Sample Dir A/")
        assertTrue(res3Slash is ResolvedTarget.Directory)
    }
}
