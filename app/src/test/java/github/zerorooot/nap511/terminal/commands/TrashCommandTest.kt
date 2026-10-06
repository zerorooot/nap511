package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.RecycleBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * trash 命令测试用例集合
 * 覆盖：回收站列表查看、还原操作（文件名/RID）、取消确认交互及异常边界处理
 */
class TrashCommandTest {

    /**
     * 测试清空操作确认取消交互
     */
    @Test
    fun testTrashConfirmationCancelled() = runBlocking {
        val engine = createTestEngine()
        val cancelCtx = createTestContext(onConfirmRequest = { false })

        // 回收站清空确认取消处理
        val out = engine.executeStrings("trash -c", cancelCtx)
        assertEquals(listOf("trash: 已取消清空操作"), out)
    }

    /**
     * 测试通过文件名和 RID 还原回收站项
     */
    @Test
    fun testTrashRevertByFileNameAndRid() = runBlocking {
        val recycleItems = listOf(
            RecycleBean(id = "8801", fileName = "project_backup.zip", isFolder = false, cid = "0"),
            RecycleBean(id = "8802", fileName = "notes.txt", isFolder = false, cid = "0")
        )

        val mockRepo = createTestMockRepository(recycleBeans = recycleItems)

        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        // 初始化根目录缓存（空）
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(), cid = "0", count = 0, order = "", path = emptyList()))

        // 通过文件名还原
        val outName = engine.executeStrings("trash -r project_backup.zip", ctx)
        assertEquals(listOf("trash: 已还原 'project_backup.zip' (rid: 8801)"), outName)
        assertEquals(listOf("8801"), mockRepo.revertedRids)
        assertTrue(ctx.fileCacheManager.containsKey("0"))
        assertEquals(1, ctx.fileCacheManager["0"]!!.fileBeanList.size)
        assertEquals("project_backup.zip", ctx.fileCacheManager["0"]!!.fileBeanList[0].name)
        assertEquals(1, ctx.fileCacheManager["0"]!!.count)

        // 通过 RID 直接还原
        val outRid = engine.executeStrings("trash -r 8802", ctx)
        assertEquals(listOf("trash: 已还原 '8802'"), outRid)
        assertEquals(listOf("8801", "8802"), mockRepo.revertedRids)

        // 试图还原不存在的文件报错提示
        val outNotFound = engine.executeStrings("trash -r non_existing.doc", ctx)
        assertEquals(listOf("trash: 未在回收站中找到 'non_existing.doc'"), outNotFound)
    }

    /**
     * 1. 基本功能测试
     */
    @Test
    fun testTrashBasicFunctions() = runBlocking {
        val recycleItems = listOf(
            RecycleBean(id = "101", fileName = "a.txt", isFolder = false, cid = "0")
        )
        val mockRepo = createTestMockRepository(recycleBeans = recycleItems)

        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        // 查看帮助文档
        val outHelp = engine.executeStrings("trash -h", ctx)
        assertTrue(outHelp.any { it.contains("trash") })

        // 查看回收站列表
        val outList = engine.executeStrings("trash", ctx)
        assertNotNull(outList)
    }
}
