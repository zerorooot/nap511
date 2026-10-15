package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.commands.file.BatchTargetItem
import github.zerorooot.nap511.terminal.commands.util.CommandMessages
import github.zerorooot.nap511.terminal.commands.util.MoveValidationResult
import github.zerorooot.nap511.terminal.commands.util.RemoveValidationResult
import github.zerorooot.nap511.terminal.commands.util.TargetItemResolver
import github.zerorooot.nap511.terminal.context.ResolvedTarget
import github.zerorooot.nap511.terminal.context.TerminalPathConstants
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * TargetItemResolver 单元测试
 * 全面覆盖元数据决议、边界判定（根目录、当前目录、祖先目录、. 与 ..）及移动与删除安全校验
 */
class TargetItemResolverTest {

    @Test
    fun testIsDotOrDotDot() {
        assertTrue(TargetItemResolver.isDotOrDotDot("."))
        assertTrue(TargetItemResolver.isDotOrDotDot(".."))
        assertTrue(TargetItemResolver.isDotOrDotDot("./"))
        assertTrue(TargetItemResolver.isDotOrDotDot("../"))
        assertTrue(TargetItemResolver.isDotOrDotDot("a/b/."))
        assertTrue(TargetItemResolver.isDotOrDotDot("a/b/.."))
        assertTrue(TargetItemResolver.isDotOrDotDot("a/b/./"))
        assertTrue(TargetItemResolver.isDotOrDotDot("a/b/../"))

        assertFalse(TargetItemResolver.isDotOrDotDot(""))
        assertFalse(TargetItemResolver.isDotOrDotDot("/"))
        assertFalse(TargetItemResolver.isDotOrDotDot("///"))
        assertFalse(TargetItemResolver.isDotOrDotDot("abc"))
        assertFalse(TargetItemResolver.isDotOrDotDot("a/b/c"))
        assertFalse(TargetItemResolver.isDotOrDotDot(".dotfile"))
        assertFalse(TargetItemResolver.isDotOrDotDot("..something"))
    }

    @Test
    fun testResolveToBatchItemForRoot() = runBlocking {
        val ctx = createTestContext()
        val item = TargetItemResolver.resolveToBatchItem(ctx, "/")
        assertNotNull(item)
        assertEquals(TerminalPathConstants.ROOT_CID, item!!.fid)
        // 核心契约：根目录 parentCid 严格为 null
        assertNull(item.parentCid)
        assertTrue(item.isFolder)
        assertTrue(TargetItemResolver.isRoot(item))
    }

    @Test
    fun testResolveToBatchItemForFileAndFolder() = runBlocking {
        val ctx = createTestContext()
        val f1 = createMockFile("test.txt", "101", categoryId = "0")
        val d1 = createMockFolder("my_folder", "201")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, d1), cid = "0", count = 2, order = "", path = emptyList())
        )

        val fileItem = TargetItemResolver.resolveToBatchItem(ctx, "test.txt")
        assertNotNull(fileItem)
        assertEquals("101", fileItem!!.fid)
        assertEquals("0", fileItem.parentCid)
        assertFalse(fileItem.isFolder)
        assertEquals("test.txt", fileItem.displayName)

        val dirItem = TargetItemResolver.resolveToBatchItem(ctx, "my_folder")
        assertNotNull(dirItem)
        assertEquals("201", dirItem!!.fid)
        assertEquals("0", dirItem.parentCid)
        assertTrue(dirItem.isFolder)
        assertEquals("my_folder", dirItem.displayName)
    }

    @Test
    fun testAncestorDetection() {
        val ctx = createTestContext()
        val p0 = PathBean("0", "根目录", "0")
        val p1 = PathBean("10", "dir1", "0")
        val p2 = PathBean("20", "dir2", "10")
        ctx.updateDirectory(listOf(p0, p1, p2))

        val ancestorItem = BatchTargetItem(parentCid = "0", fid = "10", displayName = "dir1", isFolder = true)
        val currentItem = BatchTargetItem(parentCid = "10", fid = "20", displayName = "dir2", isFolder = true)
        val otherItem = BatchTargetItem(parentCid = "20", fid = "30", displayName = "dir3", isFolder = true)
        val fileItem = BatchTargetItem(parentCid = "0", fid = "10", displayName = "file1", isFolder = false)

        assertTrue(TargetItemResolver.isAncestorOfCurrent(ctx, ancestorItem))
        assertFalse(TargetItemResolver.isAncestorOfCurrent(ctx, currentItem))
        assertFalse(TargetItemResolver.isAncestorOfCurrent(ctx, otherItem))
        // 普通文件不可作为祖先目录
        assertFalse(TargetItemResolver.isAncestorOfCurrent(ctx, fileItem))
    }

    @Test
    fun testValidateMove() {
        val ctx = createTestContext()
        val targetDestCid = "100"
        val destDir = ResolvedTarget.Directory(
            cid = targetDestCid,
            path = "/dest",
            parentCid = "0",
            name = "dest",
            folderBean = null,
            pathList = listOf(PathBean("0", "根目录", "0"), PathBean("100", "dest", "0"))
        )

        val validItem = BatchTargetItem(parentCid = "200", fid = "300", displayName = "file.txt", isFolder = false)
        val validRes = TargetItemResolver.validateMove(validItem, targetDestCid, destDir, ctx, "file.txt")
        assertTrue(validRes is MoveValidationResult.Valid)

        // 1. 点与点点拦截
        val dotRes = TargetItemResolver.validateMove(validItem, targetDestCid, destDir, ctx, ".")
        assertTrue(dotRes is MoveValidationResult.Error)
        assertEquals(CommandMessages.cannotMoveDotOrDotDot("mv", "."), (dotRes as MoveValidationResult.Error).message)

        // 2. 根目录拦截
        val rootItem = BatchTargetItem(parentCid = null, fid = "0", displayName = "根目录", isFolder = true)
        val rootRes = TargetItemResolver.validateMove(rootItem, targetDestCid, destDir, ctx, "/")
        assertTrue(rootRes is MoveValidationResult.Error)
        assertEquals(CommandMessages.cannotMoveRoot("mv", "/"), (rootRes as MoveValidationResult.Error).message)

        // 3. 当前工作目录拦截
        val subCtx = createTestContext()
        subCtx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("50", "sub", "0")))
        val cwdItem = BatchTargetItem(parentCid = "0", fid = subCtx.currentCid, displayName = "sub", isFolder = true)
        val cwdRes = TargetItemResolver.validateMove(cwdItem, targetDestCid, destDir, subCtx, "sub")
        assertTrue(cwdRes is MoveValidationResult.Error)
        assertEquals(CommandMessages.cannotMoveCwd("mv", "sub"), (cwdRes as MoveValidationResult.Error).message)

        // 4. 原地移动 same file 拦截
        val sameParentItem = BatchTargetItem(parentCid = targetDestCid, fid = "301", displayName = "f.txt", isFolder = false)
        val sameRes = TargetItemResolver.validateMove(sameParentItem, targetDestCid, destDir, ctx, "f.txt")
        assertTrue(sameRes is MoveValidationResult.Error)
        assertEquals(CommandMessages.sameFile("mv", "f.txt", "dest"), (sameRes as MoveValidationResult.Error).message)

        // 5. 自嵌套拦截
        val selfNestItem = BatchTargetItem(parentCid = "0", fid = targetDestCid, displayName = "dest", isFolder = true)
        val selfNestRes = TargetItemResolver.validateMove(selfNestItem, targetDestCid, destDir, ctx, "dest")
        assertTrue(selfNestRes is MoveValidationResult.Error)
        assertEquals(CommandMessages.selfNestingError("mv", "dest"), (selfNestRes as MoveValidationResult.Error).message)
    }

    @Test
    fun testValidateRemove() {
        val ctx = createTestContext()

        val validFile = BatchTargetItem(parentCid = "0", fid = "10", displayName = "a.txt", isFolder = false)
        val validFileRes = TargetItemResolver.validateRemove(validFile, recursive = false, ctx = ctx, rawPath = "a.txt")
        assertTrue(validFileRes is RemoveValidationResult.Valid)

        // 1. 点与点点
        val dotRes = TargetItemResolver.validateRemove(validFile, recursive = true, ctx = ctx, rawPath = "..")
        assertTrue(dotRes is RemoveValidationResult.Error)
        assertEquals(CommandMessages.refuseRemoveDotOrDotDot("rm", ".."), (dotRes as RemoveValidationResult.Error).message)

        // 2. 根目录
        val rootItem = BatchTargetItem(parentCid = null, fid = "0", displayName = "根目录", isFolder = true)
        val rootRes = TargetItemResolver.validateRemove(rootItem, recursive = true, ctx = ctx, rawPath = "/")
        assertTrue(rootRes is RemoveValidationResult.Error)
        assertEquals(CommandMessages.cannotRemoveRoot("rm", "/"), (rootRes as RemoveValidationResult.Error).message)

        // 3. 当前工作目录
        val subCtx = createTestContext()
        subCtx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("50", "sub", "0")))
        val cwdItem = BatchTargetItem(parentCid = "0", fid = subCtx.currentCid, displayName = "sub", isFolder = true)
        val cwdRes = TargetItemResolver.validateRemove(cwdItem, recursive = true, ctx = subCtx, rawPath = "sub")
        assertTrue(cwdRes is RemoveValidationResult.Error)
        assertEquals(CommandMessages.cannotRemoveCwd("rm", "sub"), (cwdRes as RemoveValidationResult.Error).message)

        // 4. 目录未加 -r
        val folderItem = BatchTargetItem(parentCid = "0", fid = "20", displayName = "sub", isFolder = true)
        val noRRes = TargetItemResolver.validateRemove(folderItem, recursive = false, ctx = ctx, rawPath = "sub")
        assertTrue(noRRes is RemoveValidationResult.Error)
        assertEquals(CommandMessages.isADirectory("rm", "sub"), (noRRes as RemoveValidationResult.Error).message)

        // 5. 目录携带 -r 成功
        val withRRes = TargetItemResolver.validateRemove(folderItem, recursive = true, ctx = ctx, rawPath = "sub")
        assertTrue(withRRes is RemoveValidationResult.Valid)

        // 6. parentCid 为空
        val noParentItem = BatchTargetItem(parentCid = null, fid = "99", displayName = "orphan", isFolder = false)
        val noParentRes = TargetItemResolver.validateRemove(noParentItem, recursive = false, ctx = ctx, rawPath = "orphan")
        assertTrue(noParentRes is RemoveValidationResult.Error)
        assertEquals(CommandMessages.cannotDetermineParent("rm", "orphan"), (noParentRes as RemoveValidationResult.Error).message)
    }
}
