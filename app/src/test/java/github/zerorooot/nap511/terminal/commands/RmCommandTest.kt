package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * rm 命令测试用例集合
 * 覆盖：参数校验、根目录/工作目录安全保护、交互式确认与取消、转义与特殊字符文件名删除
 */
class RmCommandTest {

    /**
     * 测试参数校验与路径防护
     */
    @Test
    fun testRmValidation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // rm 缺失参数报错处理
        val rmEmpty = engine.executeStrings("rm", ctx)
        assertEquals(listOf("rm: missing operand"), rmEmpty)

        // rm 尝试删除根目录受拦截保护
        val rmRoot = engine.executeStrings("rm /", ctx)
        assertTrue(rmRoot[0].contains("Cannot remove root directory"))

        // rm 尝试删除不存在的路径报错
        val rmNotFound = engine.executeStrings("rm 't1/non_existent/'", ctx)
        assertTrue(rmNotFound[0].contains("cannot remove 't1/non_existent/': No such file or directory"))

        // rm -f 强行删除不存在的文件提示不可用
        val rmNotFoundForce = engine.executeStrings("rm -f notexist_file.txt", ctx)
        assertEquals(listOf("rm: cannot remove 'notexist_file.txt': No such file or directory"), rmNotFoundForce)
    }

    /**
     * 测试删除确认提示与工作目录保护
     */
    @Test
    fun testRmConfirmationAndSafety() = runBlocking {
        val engine = createTestEngine()

        // 确认交互回调取消删除操作
        val cancelCtx = createTestContext(onConfirmRequest = { false })

        val targetFolder = createMockFolder("important_dir", "10")
        cancelCtx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(targetFolder), cid = "0", count = 1, order = "", path = emptyList()))

        val outCancel = engine.executeStrings("rm important_dir", cancelCtx)
        assertEquals(listOf("rm: 已取消删除 'important_dir'"), outCancel)

        // 尝试删除当前工作目录 '.' 受保护阻退
        val outCurrent = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrent[0].contains("Cannot remove root directory") || outCurrent[0].contains("Cannot remove current working directory"))

        // 在子目录下尝试删除当前工作目录
        cancelCtx.updateDirectory(listOf(PathBean("0", "根目录", "0"), PathBean("10", "important_dir", "0")))
        val outCurrentSub = engine.executeStrings("rm .", cancelCtx)
        assertTrue(outCurrentSub[0].contains("Cannot remove current working directory"))
    }

    /**
     * 测试路径转义、中文与 Emoji 文件名删除
     */
    @Test
    fun testRmPathAndEscaping() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val fSpaced = createMockFile("my file.txt", "1")
        val fSpaced2 = createMockFile("my file2.txt", "12")
        val fChinese = createMockFile("测试.txt", "2")
        val fEmoji = createMockFile("😀.png", "3")
        val fAbs = createMockFile("abs.txt", "4")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(fSpaced, fSpaced2, fChinese, fEmoji, fAbs), cid = "0", count = 5, order = "", path = emptyList()))

        // 带空格文件名使用双引号包裹删除
        val outQuoted = engine.executeStrings("rm -f \"my file.txt\"", ctx)
        assertEquals(listOf("rm: 已移入回收站 'my file.txt'"), outQuoted)

        // 带空格文件名使用反斜杠转义删除
        val outEscaped = engine.executeStrings("rm -f my\\ file2.txt", ctx)
        assertEquals(listOf("rm: 已移入回收站 'my file2.txt'"), outEscaped)

        // 中文文件名删除
        val outChinese = engine.executeStrings("rm -f 测试.txt", ctx)
        assertEquals(listOf("rm: 已移入回收站 '测试.txt'"), outChinese)

        // Emoji 表情文件名删除
        val outEmoji = engine.executeStrings("rm -f '😀.png'", ctx)
        assertEquals(listOf("rm: 已移入回收站 '😀.png'"), outEmoji)
    }

    /**
     * 测试当前目录下的通配符批量删除 (rm -f *.log)
     */
    @Test
    fun testRmWildcardCurrentDirectory() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val log1 = createMockFile("temp1.log", "101")
        val log2 = createMockFile("temp2.log", "102")
        val keep = createMockFile("keep.txt", "103")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf<FileBean>(log1, log2, keep), cid = "0", count = 3, order = "", path = emptyList())
        )

        val out = engine.executeStrings("rm -f *.log", ctx)
        assertEquals(listOf("rm: 已成功批量删除目录下的 2 个项目至回收站"), out)
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("0", call["pid"])
        assertTrue(call.values.contains("101"))
        assertTrue(call.values.contains("102"))
        assertFalse(call.values.contains("103"))
    }

    /**
     * 测试子目录前缀通配符批量删除 (rm -f build/`*.tmp`)
     */
    @Test
    fun testRmWildcardSubDirectoryPrefix() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val buildFolder = createMockFolder("build", "50")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf<FileBean>(buildFolder), cid = "0", count = 1, order = "", path = listOf(PathBean("0", "根目录", "0")))
        )

        val tmpA = createMockFile("a.tmp", "501")
        val tmpB = createMockFile("b.tmp", "502")
        ctx.fileCacheManager.put(
            "50",
            FilesBean(fileBeanList = arrayListOf<FileBean>(tmpA, tmpB), cid = "50", count = 2, order = "", path = listOf(PathBean("0", "根目录", "0"), PathBean("50", "build", "0")))
        )

        val out = engine.executeStrings("rm -f build/*.tmp", ctx)
        assertEquals(listOf("rm: 已成功批量删除目录下的 2 个项目至回收站"), out)
        assertEquals(1, mockRepo.deletedMultipleCalls.size)
        val call = mockRepo.deletedMultipleCalls[0]
        assertEquals("50", call["pid"])
        assertTrue(call.values.contains("501"))
        assertTrue(call.values.contains("502"))
    }

    /**
     * 测试通配符无匹配保留字面量回退 (rm *.bak)
     */
    @Test
    fun testRmWildcardNomatch() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val f = createMockFile("data.txt", "1")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf<FileBean>(f), cid = "0", count = 1, order = "", path = emptyList())
        )

        val out = engine.executeStrings("rm *.bak", ctx)
        assertTrue(out.any { it.contains("cannot remove '*.bak': No such file or directory") })
    }

    /**
     * 测试引号保护通配符不被展开误删 (rm -f "*.txt")
     */
    @Test
    fun testRmWildcardQuotedProtection() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f = createMockFile("file.txt", "1")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf<FileBean>(f), cid = "0", count = 1, order = "", path = emptyList())
        )

        val out = engine.executeStrings("rm -f \"*.txt\"", ctx)
        assertTrue(out.any { it.contains("cannot remove '*.txt': No such file or directory") })
        // 验证物理文件 file.txt 未被删除
        assertTrue(mockRepo.deletedItems.isEmpty())
        assertTrue(mockRepo.deletedMultipleCalls.isEmpty())
    }
}
