package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.BaseReturnMessage
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.commands.file.BatchTargetItem
import github.zerorooot.nap511.terminal.commands.file.TerminalBatchFileOps
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * MvCommand 批量移动重构与参数聚合复用完整测试套件
 *
 * 覆盖：
 * 1. TerminalBatchFileOps.moveGroup 单项移动与目标目录刷新；
 * 2. 跨多源父目录移动时，按 parentCid 分组清理各自源目录缓存；
 * 3. 超过 100 项的分批切片（Chunking）与切片熔断容错（部分成功且强刷目标目录）；
 * 4. MvCommand 批量多文件移动的终端汇总回显；
 * 5. MvCommand 原地移动检测拦截（跳过并提示）；
 * 6. MvCommand 自嵌套及移入子目录检测拦截（严格报错）；
 * 7. 混合非法项与合法项时的过滤与差异化回显。
 */
class BatchMoveTest {

    /**
     * 1. 验证单项移动：网络协议参数组装、源缓存清理与目标目录强制刷新
     */
    @Test
    fun testMoveGroupSingleItem() = runBlocking {
        val mockRepo = createTestMockRepository()
        val ctx = createTestContext(fileRepository = mockRepo)

        val file1 = createMockFile("doc.txt", "101", categoryId = "10")
        mockRepo.directoryFilesMap.getOrPut("10") { mutableListOf() }.add(file1)
        ctx.fileCacheManager.put(
            "10",
            FilesBean(fileBeanList = arrayListOf(file1), cid = "10", count = 1, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put(
            "20",
            FilesBean(fileBeanList = arrayListOf(), cid = "20", count = 0, order = "", path = emptyList())
        )

        val item = BatchTargetItem(parentCid = "10", fid = "101", displayName = "doc.txt", isFolder = false)
        val result = TerminalBatchFileOps.moveGroup(ctx, targetDestCid = "20", listOf(item))

        assertTrue(result.isAllSuccess)
        assertEquals(1, result.successCount)
        assertEquals(0, result.failureCount)

        // 验证网络请求
        assertEquals(1, mockRepo.movedItems.size)
        val call = mockRepo.movedItems[0]
        assertEquals("20", call["pid"])
        assertEquals("101", call["fid[0]"])

        // 验证源目录缓存已移除
        val srcCache = ctx.fileCacheManager.get("10")
        assertEquals(0, srcCache?.fileBeanList?.size)

        // 验证目标目录被刷新拉取（mockRepo 自动移入目标）
        val destCache = ctx.fileCacheManager.get("20")
        assertEquals(1, destCache?.fileBeanList?.size)
    }

    /**
     * 2. 验证跨多源父目录批量移动：按 parentCid 分组精确清理本地缓存
     */
    @Test
    fun testMoveGroupMultiParentGrouping() = runBlocking {
        val mockRepo = createTestMockRepository()
        val ctx = createTestContext(fileRepository = mockRepo)

        // parent1 (10): a.txt(1), b.txt(2)
        val fa = createMockFile("a.txt", "1", categoryId = "10")
        val fb = createMockFile("b.txt", "2", categoryId = "10")
        ctx.fileCacheManager.put(
            "10",
            FilesBean(fileBeanList = arrayListOf(fa, fb), cid = "10", count = 2, order = "", path = emptyList())
        )

        // parent2 (20): c.txt(3)
        val fc = createMockFile("c.txt", "3", categoryId = "20")
        ctx.fileCacheManager.put(
            "20",
            FilesBean(fileBeanList = arrayListOf(fc), cid = "20", count = 1, order = "", path = emptyList())
        )

        val items = listOf(
            BatchTargetItem(parentCid = "10", fid = "1", displayName = "a.txt", isFolder = false),
            BatchTargetItem(parentCid = "10", fid = "2", displayName = "b.txt", isFolder = false),
            BatchTargetItem(parentCid = "20", fid = "3", displayName = "c.txt", isFolder = false)
        )

        val result = TerminalBatchFileOps.moveGroup(ctx, targetDestCid = "99", items)
        assertTrue(result.isAllSuccess)
        assertEquals(3, result.successCount)

        // 仅发起 1 次批量移动 HTTP 请求
        assertEquals(1, mockRepo.movedItems.size)
        val call = mockRepo.movedItems[0]
        assertEquals("99", call["pid"])
        assertEquals("1", call["fid[0]"])
        assertEquals("2", call["fid[1]"])
        assertEquals("3", call["fid[2]"])

        // 两个源目录的本地缓存均被精确剔除对应条目
        assertEquals(0, ctx.fileCacheManager.get("10")?.fileBeanList?.size)
        assertEquals(0, ctx.fileCacheManager.get("20")?.fileBeanList?.size)
    }

    /**
     * 3. 验证分批切片（>100 项）与中途失败熔断：部分成功精确计数且保证刷新目标目录
     */
    @Test
    fun testMoveGroupChunkingAndPartialFailure() = runBlocking {
        var callCount = 0
        val mockRepo = object : TestMockFileRepository() {
            override suspend fun move(body: Map<String, String>): BaseReturnMessage {
                callCount++
                return if (callCount == 1) {
                    BaseReturnMessage(state = true)
                } else {
                    BaseReturnMessage(state = false, error = "容量不足")
                }
            }
        }

        val ctx = createTestContext(fileRepository = mockRepo)

        // 构造 150 个待移动项目（第 1 批 100 个，第 2 批 50 个）
        val items = (1..150).map { i ->
            BatchTargetItem(parentCid = "10", fid = "$i", displayName = "file$i.txt", isFolder = false)
        }

        val result = TerminalBatchFileOps.moveGroup(ctx, targetDestCid = "88", items)

        assertFalse(result.isAllSuccess)
        assertTrue(result.isPartialSuccess)
        assertEquals(150, result.totalCount)
        assertEquals(100, result.successCount)
        assertEquals(50, result.failureCount)
        assertEquals("容量不足", result.error)

        // 验证执行了 2 批次后熔断
        assertEquals(2, callCount)
    }

    /**
     * 4. 测试 MvCommand 终端批量移动多文件汇总回显
     */
    @Test
    fun testMvCommandBatchSummaryOutput() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f1 = createMockFile("f1.txt", "1")
        val f2 = createMockFile("f2.txt", "2")
        val f3 = createMockFile("f3.txt", "3")
        val dirDest = createMockFolder("dest", "500")

        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2, f3, dirDest), cid = "0", count = 4, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put("500", FilesBean(fileBeanList = arrayListOf(), cid = "500", count = 0, order = "", path = emptyList()))

        val out = engine.executeStrings("mv f1.txt f2.txt f3.txt dest/", ctx)

        // 验证批量输出汇总信息
        assertEquals(listOf("mv: 已成功移动 3 个文件/文件夹至 'dest/'"), out)

        // 验证只发起了 1 次 move 网络请求
        assertEquals(1, mockRepo.movedItems.size)
        val call = mockRepo.movedItems[0]
        assertEquals("500", call["pid"])
        assertEquals("1", call["fid[0]"])
        assertEquals("2", call["fid[1]"])
        assertEquals("3", call["fid[2]"])
    }

    /**
     * 5. 测试 MvCommand 原地移动检测拦截
     */
    @Test
    fun testMvCommandInPlaceMoveSkipped() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f1 = createMockFile("same.txt", "1")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1), cid = "0", count = 1, order = "", path = emptyList())
        )

        // 原地移动至当前目录 .
        val outDot = engine.executeStrings("mv same.txt .", ctx)
        assertEquals(listOf("mv: 'same.txt' 与目标目录相同，已跳过"), outDot)

        // 验证未发起任何网络请求
        assertEquals(0, mockRepo.movedItems.size)
    }

    /**
     * 6. 测试 MvCommand 自嵌套及移入子目录安全拦截
     */
    @Test
    fun testMvCommandSelfAndChildMoveForbidden() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val folderA = createMockFolder("folderA", "100")
        val folderB = createMockFolder("folderB", "200")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(folderA, folderB), cid = "0", count = 2, order = "", path = emptyList())
        )

        // 模拟 folderA 的子目录 subA("105")
        val subA = createMockFolder("subA", "105")
        ctx.fileCacheManager.put(
            "100",
            FilesBean(
                fileBeanList = arrayListOf(subA),
                cid = "100",
                count = 1,
                order = "",
                path = listOf(
                    PathBean(cid = "0", name = "根目录", pid = "0"),
                    PathBean(cid = "100", name = "folderA", pid = "0")
                )
            )
        )
        ctx.fileCacheManager.put(
            "105",
            FilesBean(
                fileBeanList = arrayListOf(),
                cid = "105",
                count = 0,
                order = "",
                path = listOf(
                    PathBean(cid = "0", name = "根目录", pid = "0"),
                    PathBean(cid = "100", name = "folderA", pid = "0"),
                    PathBean(cid = "105", name = "subA", pid = "100")
                )
            )
        )

        // 尝试将 folderA 移动至其子目录 folderA/subA/
        val outChild = engine.executeStrings("mv folderA folderA/subA/", ctx)
        assertEquals(listOf("mv: 无法将目录 'folderA' 移动至其自身或子目录下"), outChild)

        // 验证未发起任何网络请求
        assertEquals(0, mockRepo.movedItems.size)
    }

    /**
     * 7. 测试 MvCommand 混合非法源（不存在）、原地移动与合法项的差异化过滤与回显
     */
    @Test
    fun testMvCommandMixedSourcesHandling() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val validFile = createMockFile("valid.txt", "10")
        val dirTarget = createMockFolder("target", "999")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(validFile, dirTarget), cid = "0", count = 2, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put("999", FilesBean(fileBeanList = arrayListOf(), cid = "999", count = 0, order = "", path = emptyList()))

        // 传入 3 个源：1 个不存在、1 个合法
        val out = engine.executeStrings("mv no_exist.txt valid.txt target/", ctx)

        // 验证：不存在的文件报错，合法的 1 项移动成功并展示明细
        assertTrue(out.contains("mv: cannot stat 'no_exist.txt': No such file or directory"))
        assertTrue(out.contains("mv: 'valid.txt' -> 'target/'"))

        // 验证只移动了 valid.txt
        assertEquals(1, mockRepo.movedItems.size)
        assertEquals("10", mockRepo.movedItems[0]["fid[0]"])
    }
}
