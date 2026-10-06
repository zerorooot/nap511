package github.zerorooot.nap511.terminal.context

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalPathTest {

    @Test
    fun testParseEmptyAndCurrentDir() {
        val emptyPath = TerminalPath.parse("")
        assertFalse(emptyPath.isAbsolute)
        assertTrue(emptyPath.isCurrentDirectory)
        assertFalse(emptyPath.isRoot)
        assertEquals("", emptyPath.targetName)
        assertEquals("", emptyPath.parentPathString)
        assertEquals(emptyList<String>(), emptyPath.segments)

        val dotPath = TerminalPath.parse(".")
        assertFalse(dotPath.isAbsolute)
        assertTrue(dotPath.isCurrentDirectory)
        assertFalse(dotPath.isRoot)
    }

    @Test
    fun testParseRootAliases() {
        val roots = listOf("/", "~", "/根目录", "/根目录/", "根目录")
        for (r in roots) {
            val parsed = TerminalPath.parse(r)
            assertTrue("'$r' 必须判定为绝对路径", parsed.isAbsolute)
            assertTrue("'$r' 必须判定为根目录", parsed.isRoot)
            assertEquals("根目录", parsed.targetName)
            assertEquals(emptyList<String>(), parsed.segments)
        }
    }

    @Test
    fun testParseAbsolutePaths() {
        val p1 = TerminalPath.parse("/根目录/Movies/Action/")
        assertTrue(p1.isAbsolute)
        assertTrue(p1.hasTrailingSlash)
        assertFalse(p1.isRoot)
        assertEquals(listOf("Movies", "Action"), p1.segments)
        assertEquals("Action", p1.targetName)
        assertEquals("/根目录/Movies", p1.parentPathString)

        val p2 = TerminalPath.parse("/a/b/c.txt")
        assertTrue(p2.isAbsolute)
        assertFalse(p2.hasTrailingSlash)
        assertEquals(listOf("a", "b", "c.txt"), p2.segments)
        assertEquals("c.txt", p2.targetName)
        assertEquals("/根目录/a/b", p2.parentPathString)

        val p3 = TerminalPath.parse("~/photos/vacation")
        assertTrue(p3.isAbsolute)
        assertEquals(listOf("photos", "vacation"), p3.segments)
        assertEquals("vacation", p3.targetName)
        assertEquals("/根目录/photos", p3.parentPathString)
    }

    @Test
    fun testParseRelativePaths() {
        val p1 = TerminalPath.parse("sub/nested/file.mp4")
        assertFalse(p1.isAbsolute)
        assertFalse(p1.hasTrailingSlash)
        assertEquals(listOf("sub", "nested", "file.mp4"), p1.segments)
        assertEquals("file.mp4", p1.targetName)
        assertEquals("sub/nested", p1.parentPathString)

        val p2 = TerminalPath.parse("folder/")
        assertFalse(p2.isAbsolute)
        assertTrue(p2.hasTrailingSlash)
        assertEquals(listOf("folder"), p2.segments)
        assertEquals("folder", p2.targetName)
        assertEquals("", p2.parentPathString)
    }

    @Test
    fun testSplitParentAndPrefix() {
        val (parent1, prefix1) = TerminalPath.splitParentAndPrefix("sub/mov")
        assertEquals("sub/", parent1)
        assertEquals("mov", prefix1)

        val (parent2, prefix2) = TerminalPath.splitParentAndPrefix("mov")
        assertEquals("", parent2)
        assertEquals("mov", prefix2)

        val (parent3, prefix3) = TerminalPath.splitParentAndPrefix("/根目录/dir/")
        assertEquals("/根目录/dir/", parent3)
        assertEquals("", prefix3)
    }

    @Test
    fun testPathBeanExtensions() {
        val rootList = listOf(TerminalPathConstants.ROOT_PATH_BEAN)
        assertEquals("/根目录", rootList.toDisplayPath())
        assertEquals("0", rootList.currentCid())
        assertEquals("根目录", rootList.currentName())
        assertNull(rootList.parentCid())

        // 步入子目录
        val list1 = rootList.cdDown("100", "Movies")
        assertEquals(2, list1.size)
        assertEquals("/根目录/Movies", list1.toDisplayPath())
        assertEquals("100", list1.currentCid())
        assertEquals("Movies", list1.currentName())
        assertEquals("0", list1.parentCid())

        // 步入二级子目录
        val list2 = list1.cdDown("200", "Action")
        assertEquals(3, list2.size)
        assertEquals("/根目录/Movies/Action", list2.toDisplayPath())
        assertEquals("200", list2.currentCid())
        assertEquals("Action", list2.currentName())
        assertEquals("100", list2.parentCid())

        // 返回上一级 (cd ..)
        val listUp = list2.cdUp()
        assertEquals(2, listUp.size)
        assertEquals("100", listUp.currentCid())
        assertEquals("/根目录/Movies", listUp.toDisplayPath())

        // 在根目录持续 cdUp 不会清空根节点
        val rootUp = rootList.cdUp()
        assertEquals(1, rootUp.size)
        assertEquals("0", rootUp.currentCid())
    }

    @Test
    fun testContextPathListTrackingThroughCd() = runBlocking {
        val ctx = TerminalContext()
        val dir1 = FileBean(name = "docs", categoryId = "10", isFolder = true)
        val dir2 = FileBean(name = "work", categoryId = "20", isFolder = true)

        ctx.fileCacheManager.put("0", FilesBean(arrayListOf(dir1), cid = "0", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("10", FilesBean(arrayListOf(dir2), cid = "10", count = 1, order = "", path = emptyList()))
        ctx.fileCacheManager.put("20", FilesBean(arrayListOf(), cid = "20", count = 0, order = "", path = emptyList()))

        // 1. 解析目标目录 docs/work 并检查 pathList
        val resolved = ctx.resolveDirectory("docs/work")
        assertNotNull(resolved)
        assertEquals("20", resolved!!.cid)
        assertEquals("10", resolved.parentCid)
        assertEquals("work", resolved.name)
        assertEquals(3, resolved.pathList.size)
        assertEquals(listOf("0", "10", "20"), resolved.pathList.map { it.cid })
        assertEquals(listOf("根目录", "docs", "work"), resolved.pathList.map { it.name })

        // 2. updateDirectory 接受 ResolvedTarget.Directory
        ctx.updateDirectory(resolved)
        assertEquals("20", ctx.currentCid)
        assertEquals("/根目录/docs/work", ctx.currentPath)
        assertEquals(3, ctx.currentPathList.size)

        // 3. 在当前目录下通过相对路径 ".." 回退
        val resolvedUp = ctx.resolveDirectory("..")
        assertNotNull(resolvedUp)
        assertEquals("10", resolvedUp!!.cid)
        assertEquals("docs", resolvedUp.name)
        assertEquals(2, resolvedUp.pathList.size)
        assertEquals(listOf("0", "10"), resolvedUp.pathList.map { it.cid })

        ctx.updateDirectory(resolvedUp)
        assertEquals("10", ctx.currentCid)
        assertEquals("/根目录/docs", ctx.currentPath)
    }
}
