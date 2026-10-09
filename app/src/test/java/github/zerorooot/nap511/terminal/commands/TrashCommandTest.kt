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

    /**
     * 测试通配符参数非法拦截 (trash *.bak)
     *
     * 架构契约：明确 trash 命令仅作为回收站内部数据管理工具（-l 查看、-r 还原、-c 清空），
     * 不支持直接通过操作数删除文件（文件删除与移入回收站统一归属 rm 命令）。
     * 当传入通配符展开的文件位置参数时，进行 Fail-Fast 拦截并友好指引使用 rm 命令。
     */
    @Test
    fun testTrashWildcardItems() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val bak1 = createMockFile("item1.bak", "701")
        val bak2 = createMockFile("item2.bak", "702")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(bak1, bak2), cid = "0", count = 2, order = "", path = emptyList())
        )

        val out = engine.executeStrings("trash *.bak", ctx)
        assertEquals(
            listOf("trash: 不支持位置参数，若需将文件移入回收站请使用 'rm' 命令"),
            out
        )

        // 验证原工作区文件未被误删，缓存保持完整
        assertEquals(2, ctx.fileCacheManager["0"]!!.fileBeanList.size)
    }

    /**
     * 测试位置参数与选项完整性校验
     * 覆盖：单文件位置参数、选项多余参数混用以及 -r 缺少参数场景
     */
    @Test
    fun testTrashPositionalArgumentsAndOptionValidation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 1. 单个位置参数拦截
        val outSingle = engine.executeStrings("trash single.txt", ctx)
        assertEquals(
            listOf("trash: 不支持位置参数，若需将文件移入回收站请使用 'rm' 命令"),
            outSingle
        )

        // 2. -l 携带多余位置参数拦截
        val outListExtra = engine.executeStrings("trash -l extra.txt", ctx)
        assertEquals(
            listOf("trash: 不支持位置参数，若需将文件移入回收站请使用 'rm' 命令"),
            outListExtra
        )

        // 3. -c 携带多余位置参数拦截
        val outCleanExtra = engine.executeStrings("trash -c extra.txt", ctx)
        assertEquals(
            listOf("trash: 不支持位置参数，若需将文件移入回收站请使用 'rm' 命令"),
            outCleanExtra
        )

        // 4. -r 携带多余位置参数拦截
        val outRevertExtra = engine.executeStrings("trash -r 8801 extra.txt", ctx)
        assertEquals(
            listOf("trash: 不支持位置参数，若需将文件移入回收站请使用 'rm' 命令"),
            outRevertExtra
        )

        // 5. -r 缺少必选参数拦截
        val outMissingArg = engine.executeStrings("trash -r", ctx)
        assertEquals(
            listOf("trash: option requires an argument -- r"),
            outMissingArg
        )
    }
}
