package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.engine.StreamTargetCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 管道流式处理与批量文件操作（Pipeline & xargs Batch Operations）完整测试套件
 *
 * 测试目标与架构契约：
 * 1. StreamTargetCollector 对 \n（换行符）与 \0（NUL 分隔符）的流式摄取与空格保护；
 * 2. MvCommand 支持 GNU 标准 `-t / --target-directory` 选项；
 * 3. `find ... -print0 | xargs -0 mv -t <dir>` 端到端单批聚合执行验证；
 * 4. `find ... -print0 | mv <dir>` 原生管道直连聚合移动；
 * 5. `find ... -print0 | rm -f` 原生管道直连批量删除；
 * 6. `find ... -print0 | unzip` 原生管道直连多压缩包批量提交。
 *
 * 注：所有用例文件名均已严格脱敏为通用名称（如 archive_alpha.zip、photo_spring.zip 等）。
 */
class PipelineBatchOpsTest {

    /**
     * 1. 验证 StreamTargetCollector 提取 NUL (\0) 定界与普通行定界流的能力
     */
    @Test
    fun testStreamTargetCollectorDelimiters() = runBlocking {
        // NUL 定界流（含文件名内嵌空格）
        val nullStream = flowOf(
            "archive alpha 2026.zip\u0000",
            "photo spring holiday.zip\u0000doc report.pdf\u0000"
        )
        val nullResults = StreamTargetCollector.collectFromStdin(nullStream)
        assertEquals(
            listOf("archive alpha 2026.zip", "photo spring holiday.zip", "doc report.pdf"),
            nullResults
        )

        // 普通换行定界流
        val lineStream = flowOf(
            "file1.txt\nfile 2.txt\r\n\nfile3.txt"
        )
        val lineResults = StreamTargetCollector.collectFromStdin(lineStream)
        assertEquals(
            listOf("file1.txt", "file 2.txt", "file3.txt"),
            lineResults
        )
    }

    /**
     * 2. 验证 MvCommand 支持 GNU 标准 -t / --target-directory 选项
     */
    @Test
    fun testMvCommandTargetDirectoryFlag() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f1 = createMockFile("item_alpha.zip", "1")
        val f2 = createMockFile("item_beta.zip", "2")
        val dirTarget = createMockFolder("target_dir", "900")

        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2, dirTarget), cid = "0", count = 3, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put(
            "900",
            FilesBean(fileBeanList = arrayListOf(), cid = "900", count = 0, order = "", path = emptyList())
        )

        // 使用 mv -t target_dir/ item_alpha.zip item_beta.zip
        val out = engine.executeStrings("mv -t target_dir/ item_alpha.zip item_beta.zip", ctx)
        assertEquals(listOf("mv: 已成功移动 2 个文件/文件夹至 'target_dir/'"), out)

        // 验证仅发起了 1 次批量移动请求，pid 为 target_dir 的 cid
        assertEquals(1, mockRepo.movedItems.size)
        val call = mockRepo.movedItems[0]
        assertEquals("900", call["pid"])
        assertEquals("1", call["fid[0]"])
        assertEquals("2", call["fid[1]"])
    }

    /**
     * 3. 验证 find -print0 | xargs -0 -t mv -t 真实场景：含空格文件名在 xargs 下单次批量聚合移动
     */
    @Test
    fun testFindPrint0XargsMvTargetDirectorySingleBatch() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        // 构造含空格的文件名（严格脱敏）
        val f1 = createMockFile("package alpha 2026.zip", "101")
        val f2 = createMockFile("package beta winter.zip", "102")
        val f3 = createMockFile("package gamma kimono.zip", "103")
        val other = createMockFile("unrelated_notes.txt", "104")
        val dirTarget = createMockFolder("smll", "888")

        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2, f3, other, dirTarget), cid = "0", count = 5, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put(
            "888",
            FilesBean(fileBeanList = arrayListOf(), cid = "888", count = 0, order = "", path = emptyList())
        )

        // 执行 find -print0 | xargs -0 -t mv -t smll/
        val out = engine.executeStrings("find -type f -name '*package*' -print0 | xargs -0 -t mv -t smll/", ctx)

        // 验证 xargs 打印了单次调用的命令回显，且 mv 一次性输出了 3 个文件的批量移动汇总
        assertTrue(out.any { it.startsWith("+ mv -t smll/") })
        assertTrue(out.any { it == "mv: 已成功移动 3 个文件/文件夹至 'smll/'" })

        // 核心验证：整条流水线仅发起了 1 次批量移动 HTTP 请求，彻底消灭 N 次串行！
        assertEquals(1, mockRepo.movedItems.size)
        val call = mockRepo.movedItems[0]
        assertEquals("888", call["pid"])
        assertEquals("101", call["fid[0]"])
        assertEquals("102", call["fid[1]"])
        assertEquals("103", call["fid[2]"])
    }

    /**
     * 4. 验证原生管道直连：find ... -print0 | mv smll/ 无需 xargs 即可单批聚合移动
     */
    @Test
    fun testFindDirectPipeToMvSingleBatch() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f1 = createMockFile("archive winter 01.zip", "201")
        val f2 = createMockFile("archive winter 02.zip", "202")
        val dirTarget = createMockFolder("dest_dir", "777")

        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2, dirTarget), cid = "0", count = 3, order = "", path = emptyList())
        )
        ctx.fileCacheManager.put(
            "777",
            FilesBean(fileBeanList = arrayListOf(), cid = "777", count = 0, order = "", path = emptyList())
        )

        // 直连管道：find -name '*archive*' -print0 | mv dest_dir/
        val out = engine.executeStrings("find -type f -name '*archive*' -print0 | mv dest_dir/", ctx)

        assertEquals(listOf("mv: 已成功移动 2 个文件/文件夹至 'dest_dir/'"), out)

        // 验证 1 次网络请求聚合移动
        assertEquals(1, mockRepo.movedItems.size)
        val call = mockRepo.movedItems[0]
        assertEquals("777", call["pid"])
        assertEquals("201", call["fid[0]"])
        assertEquals("202", call["fid[1]"])
    }

    /**
     * 5. 验证原生管道直连删除：find ... -print0 | rm -f 单批聚合删除
     */
    @Test
    fun testFindDirectPipeToRmBatchDelete() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val tmp1 = createMockFile("temp log 01.tmp", "301")
        val tmp2 = createMockFile("temp log 02.tmp", "302")
        val normal = createMockFile("important_data.dat", "303")

        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(tmp1, tmp2, normal), cid = "0", count = 3, order = "", path = emptyList())
        )

        // 直连管道：find -name '*.tmp' -print0 | rm -f
        val out = engine.executeStrings("find -type f -name '*.tmp' -print0 | rm -f", ctx)

        assertEquals(listOf("rm: 已成功批量删除目录下的 2 个项目至回收站"), out)

        // 验证发起 1 次 deleteMultiple 批量删除请求
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("0", call["pid"])
        assertEquals("301", call["fid[0]"])
        assertEquals("302", call["fid[1]"])
    }

    /**
     * 6. 验证原生管道直连解压：find ... -print0 | unzip 收集多压缩包统一单任务提交
     */
    @Test
    fun testFindDirectPipeToUnzipBatchSubmit() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val zip1 = createMockFile("photos 2026 set1.zip", "401").copy(pickCode = "PK001")
        val zip2 = createMockFile("photos 2026 set2.zip", "402").copy(pickCode = "PK002")

        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(zip1, zip2), cid = "0", count = 2, order = "", path = emptyList())
        )

        // 直连管道：find -name '*.zip' -print0 | unzip
        val out = engine.executeStrings("find -type f -name '*.zip' -print0 | unzip", ctx)

        // 验证单次命令统一收集了 2 个任务提交后台
        assertTrue(out.any { it.contains("正在提交 2 个解压任务至后台...") })
    }
}
