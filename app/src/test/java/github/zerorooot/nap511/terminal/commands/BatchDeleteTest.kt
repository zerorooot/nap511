package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.commands.file.BatchTargetItem
import github.zerorooot.nap511.terminal.commands.file.TerminalBatchFileOps
import github.zerorooot.nap511.terminal.commands.file.resolveEffectiveParentCid
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 批量删除与同目录合并优化完整测试套件
 *
 * 覆盖：
 * 1. FileBean 有效父目录 ID 决议规则（普通文件取 categoryId，目录取 parentId）；
 * 2. TerminalFileDeleter 批量聚合（deleteMultiple）与降级（单项 delete）；
 * 3. 递归删除目录时 FileCacheManager 级联清理子孙树缓存；
 * 4. RmCommand 同目录多文件批量删除执行与终端汇总展示；
 * 5. RmCommand 跨目录多文件自动分组删除；
 * 6. RmCommand 交互确认场景部分同意、部分拒绝的筛选批处理；
 * 7. FindCommand -delete 树形与全局搜索同目录批量移入回收站。
 */
class BatchDeleteTest {

    /**
     * 1. 验证 FileBean.resolveEffectiveParentCid 契约规则
     */
    @Test
    fun testResolveEffectiveParentCid() {
        // 普通文件：115 API 返回的 parentId 为空，categoryId 代表其所在的父目录 ID
        val fileBean = createMockFile("test.txt", "1001", categoryId = "555").copy(parentId = "")
        assertEquals("555", fileBean.resolveEffectiveParentCid())

        // 目录：categoryId 代表自身目录 ID，parentId 代表其上级父目录 ID
        val folderBean = createMockFolder("my_folder", "888").copy(parentId = "777")
        assertEquals("777", folderBean.resolveEffectiveParentCid())

        // 显式传入兜底父目录 ID 时，优先使用显式指定的父 ID
        assertEquals("999", fileBean.resolveEffectiveParentCid(fallbackParentCid = "999"))
        assertEquals("999", folderBean.resolveEffectiveParentCid(fallbackParentCid = "999"))
    }

    /**
     * 2. 验证 TerminalFileDeleter 针对同目录多项文件调用 deleteMultiple
     */
    @Test
    fun testTerminalFileDeleterBatchMultiple() = runBlocking {
        val mockRepo = createTestMockRepository()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f1 = createMockFile("a.txt", "1", categoryId = "100")
        val f2 = createMockFile("b.txt", "2", categoryId = "100")
        val f3 = createMockFile("c.txt", "3", categoryId = "100")

        ctx.fileCacheManager.put(
            "100",
            FilesBean(
                fileBeanList = arrayListOf(f1, f2, f3),
                cid = "100",
                count = 3,
                order = "",
                path = listOf(PathBean("100", "dir100", "0"))
            )
        )

        val items = listOf(
            BatchTargetItem("100", "1", "a.txt", isFolder = false),
            BatchTargetItem("100", "2", "b.txt", isFolder = false),
            BatchTargetItem("100", "3", "c.txt", isFolder = false)
        )

        val result = TerminalBatchFileOps.deleteGroup(ctx, "100", items)
        assertTrue(result.state)

        // 验证网络请求：调用了 1 次 deleteMultiple，包含了所有 3 个项
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("100", call["pid"])
        assertEquals("1", call["ignore_warn"])
        assertEquals("1", call["fid[0]"])
        assertEquals("2", call["fid[1]"])
        assertEquals("3", call["fid[2]"])

        // 验证缓存更新：父目录缓存中这 3 项已被清空
        val cached = ctx.fileCacheManager.get("100")
        assertEquals(0, cached?.fileBeanList?.size)
        assertEquals(0, cached?.count)
    }

    /**
     * 3. 验证 TerminalFileDeleter 在单项或 parentCid 为空时的自动降级行为
     */
    @Test
    fun testTerminalFileDeleterFallback() = runBlocking {
        val mockRepo = createTestMockRepository()
        val ctx = createTestContext(fileRepository = mockRepo)

        // 单项场景：降级为 delete(pid, fid)
        val singleItem = BatchTargetItem("100", "1", "single.txt", isFolder = false)
        val res1 = TerminalBatchFileOps.deleteGroup(ctx, "100", listOf(singleItem))
        assertTrue(res1.state)
        assertEquals(0, mockRepo.deletedMultipleCalls.size)
        assertEquals(1, mockRepo.deletedItems.size)
        assertEquals("100" to "1", mockRepo.deletedItems[0])

        // parentCid 为空场景：降级为单项删除
        val emptyPidItem1 = BatchTargetItem("", "2", "orphan1.txt", isFolder = false)
        val emptyPidItem2 = BatchTargetItem("", "3", "orphan2.txt", isFolder = false)
        val res2 = TerminalBatchFileOps.deleteGroup(ctx, "", listOf(emptyPidItem1, emptyPidItem2))
        assertTrue(res2.state)
        assertEquals(0, mockRepo.deletedMultipleCalls.size)
    }

    /**
     * 4. 验证删除目录时，子目录及深层孙目录缓存被递归深度清空
     */
    @Test
    fun testTerminalFileDeleterFolderRecursiveCachePurge() = runBlocking {
        val mockRepo = createTestMockRepository()
        val ctx = createTestContext(fileRepository = mockRepo)

        // 模拟目录层级：父目录 100 -> 子目录 200 -> 孙目录 300
        val folder200 = createMockFolder("sub_dir", "200")
        val folder300 = createMockFolder("deep_dir", "300")
        val deepFile = createMockFile("deep.txt", "400", categoryId = "300")

        ctx.fileCacheManager.put(
            "100",
            FilesBean(fileBeanList = arrayListOf(folder200), cid = "100", count = 1, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put(
            "200",
            FilesBean(fileBeanList = arrayListOf(folder300), cid = "200", count = 1, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put(
            "300",
            FilesBean(fileBeanList = arrayListOf(deepFile), cid = "300", count = 1, order = "", path = emptyList())
        )

        // 验证删除前各级缓存均存在
        assertTrue(ctx.fileCacheManager.containsKey("100"))
        assertTrue(ctx.fileCacheManager.containsKey("200"))
        assertTrue(ctx.fileCacheManager.containsKey("300"))

        val folderItem = BatchTargetItem("100", "200", "sub_dir", isFolder = true)
        val res = TerminalBatchFileOps.deleteGroup(ctx, "100", listOf(folderItem))
        assertTrue(res.state)

        // 验证父目录条目被剔除
        val parentCache = ctx.fileCacheManager.get("100")
        assertEquals(0, parentCache?.fileBeanList?.size)

        // 验证自身目录 200 与孙目录 300 的缓存均已被递归清理销毁
        assertFalse(ctx.fileCacheManager.containsKey("200"))
        assertFalse(ctx.fileCacheManager.containsKey("300"))
    }

    /**
     * 5. 测试 RmCommand 在同目录下批量删除文件的终端汇总与合并网络请求
     */
    @Test
    fun testRmCommandSameDirectoryBatch() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f1 = createMockFile("file1.txt", "101")
        val f2 = createMockFile("file2.txt", "102")
        val f3 = createMockFile("file3.txt", "103")

        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2, f3), cid = "0", count = 3, order = "", path = emptyList())
        )

        val out = engine.executeStrings("rm -f file1.txt file2.txt file3.txt", ctx)

        // 验证终端输出汇总信息
        assertEquals(listOf("rm: 已成功批量删除目录下的 3 个项目至回收站"), out)

        // 验证只发起了 1 次 deleteMultiple 网络请求
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("0", call["pid"])
        assertEquals("101", call["fid[0]"])
        assertEquals("102", call["fid[1]"])
        assertEquals("103", call["fid[2]"])
    }

    /**
     * 6. 测试 RmCommand 跨目录删除时的自动目录分组处理
     */
    @Test
    fun testRmCommandMultiDirectoryGrouping() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        // 目录结构：
        // 根目录("0") 下包含 dirA("10") 和 dirB("20")
        val dirA = createMockFolder("dirA", "10")
        val dirB = createMockFolder("dirB", "20")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(dirA, dirB), cid = "0", count = 2, order = "", path = emptyList())
        )

        // dirA 包含 a1.txt, a2.txt
        val a1 = createMockFile("a1.txt", "101", categoryId = "10")
        val a2 = createMockFile("a2.txt", "102", categoryId = "10")
        ctx.fileCacheManager.put(
            "10",
            FilesBean(fileBeanList = arrayListOf(a1, a2), cid = "10", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("10", "dirA", "0")))
        )

        // dirB 包含 b1.txt
        val b1 = createMockFile("b1.txt", "201", categoryId = "20")
        ctx.fileCacheManager.put(
            "20",
            FilesBean(fileBeanList = arrayListOf(b1), cid = "20", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("20", "dirB", "0")))
        )

        // 执行跨目录删除
        val out = engine.executeStrings("rm -f dirA/a1.txt dirA/a2.txt dirB/b1.txt", ctx)

        // dirA 的 2 个项目批量删除，dirB 的 1 个项目单项删除
        assertTrue(out.contains("rm: 已成功批量删除目录下的 2 个项目至回收站"))
        assertTrue(out.contains("rm: 已移入回收站 'b1.txt'"))

        // 验证网络请求：dirA 走 deleteMultiple，dirB 走单项 delete
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        assertEquals("10", mockRepo.deletedMultipleCalls[0]["pid"])
    }

    /**
     * 7. 测试 RmCommand 交互确认（未带 -f）中用户部分确认、部分取消的批处理行为
     */
    @Test
    fun testRmCommandInteractivePartialConfirm() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()

        // 模拟交互：确认 f1 和 f3，拒绝 f2
        val ctx = createTestContext(
            fileRepository = mockRepo,
            onConfirmRequest = { prompt ->
                !prompt.contains("f2.txt")
            }
        )

        val f1 = createMockFile("f1.txt", "1")
        val f2 = createMockFile("f2.txt", "2")
        val f3 = createMockFile("f3.txt", "3")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2, f3), cid = "0", count = 3, order = "", path = emptyList())
        )

        val out = engine.executeStrings("rm f1.txt f2.txt f3.txt", ctx)

        // 验证输出包含取消 f2 以及批量成功 2 个项目
        assertTrue(out.contains("rm: 已取消删除 'f2.txt'"))
        assertTrue(out.contains("rm: 已成功批量删除目录下的 2 个项目至回收站"))

        // 验证批量删除只提交了 1 和 3
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("1", call["fid[0]"])
        assertEquals("3", call["fid[1]"])
        assertEquals(null, call["fid[2]"])
    }

    /**
     * 8. 测试 FindCommand 递归树搜索下批量删除（-delete -f）
     */
    @Test
    fun testFindCommandTreeBatchDelete() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val log1 = createMockFile("app1.log", "101")
        val log2 = createMockFile("app2.log", "102")
        val other = createMockFile("doc.txt", "103")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(log1, log2, other), cid = "0", count = 3, order = "", path = emptyList())
        )

        val out = engine.executeStrings("find -name '*.log' -delete -f", ctx)

        // 验证输出
        assertEquals(listOf("find: 已成功删除 2 / 2 个项目至回收站"), out)

        // 验证触发了 1 次批量删除
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("0", call["pid"])
        assertEquals("101", call["fid[0]"])
        assertEquals("102", call["fid[1]"])
    }

    /**
     * 9. 测试 FindCommand 全局云端搜索下依据 categoryId 正确聚合批量删除
     */
    @Test
    fun testFindCommandGlobalBatchDelete() = runBlocking {
        val mockRepo = object : TestMockFileRepository() {
            override suspend fun search(
                cid: String,
                searchValue: String,
                aid: Int,
                asc: Int,
                limit: Int
            ): FilesBean {
                // 模拟全局搜索返回 2 个普通文件：其 parentId 为空，categoryId 为 "50"
                val f1 = createMockFile("target1.bak", "501", categoryId = "50").copy(parentId = "")
                val f2 = createMockFile("target2.bak", "502", categoryId = "50").copy(parentId = "")
                return FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = emptyList())
            }
        }

        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val out = engine.executeStrings("find -global -name '*.bak' -delete -f", ctx)

        // 验证成功删除
        assertEquals(listOf("find: 已成功删除 2 / 2 个项目至回收站"), out)

        // 验证正确使用 categoryId ("50") 作为 pid 组装 deleteMultiple
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("50", call["pid"])
        assertEquals("501", call["fid[0]"])
        assertEquals("502", call["fid[1]"])
    }
}
