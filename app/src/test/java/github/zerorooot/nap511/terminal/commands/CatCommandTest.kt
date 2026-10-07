package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * cat 命令单元测试用例集
 *
 * 覆盖：
 * 1. 帮助信息输出
 * 2. 管道标准输入透传 (stdin) 及格式化选项 (-n, -b, -s)
 * 3. 网盘单个文本文件与多个文本文件读取
 * 4. 多文件连续行号递增 (-n)
 * 5. 非文本类型文件安全拦截
 * 6. 大小超限拦截与 --max-size 临时阈值覆盖
 * 7. 目录与文件不存在的错误处理
 * 8. 多文件批处理中的容错策略（遇错不中断后续文件）
 * 9. 空文件边界测试
 */
class CatCommandTest {

    @Test
    fun testCatHelp() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val helpOut = engine.executeStrings("cat -h", ctx)
        assertTrue(helpOut.any { it.contains("cat") })
        assertTrue(helpOut.any { it.contains("连接文件") })
    }

    @Test
    fun testCatStdinBasic() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val output = engine.executeStrings("echo -e 'hello\\nworld' | cat", ctx)
        assertEquals(listOf("hello", "world"), output)
    }

    @Test
    fun testCatStdinNumberAll() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val output = engine.executeStrings("echo -e 'first\\nsecond' | cat -n", ctx)
        assertEquals(2, output.size)
        assertEquals("     1\tfirst", output[0])
        assertEquals("     2\tsecond", output[1])
    }

    @Test
    fun testCatStdinNumberNonBlank() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // -b 仅对非空行编号，空行原样保留且不消耗行号
        val output = engine.executeStrings("echo -e 'line1\\n\\nline2' | cat -b", ctx)
        assertEquals(3, output.size)
        assertEquals("     1\tline1", output[0])
        assertEquals("", output[1])
        assertEquals("     2\tline2", output[2])
    }

    @Test
    fun testCatStdinSqueezeBlank() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // -s 压缩连续的空白行，保留单个空白行
        val output = engine.executeStrings("echo -e 'a\\n\\n\\n\\nb' | cat -s", ctx)
        assertEquals(listOf("a", "", "b"), output)
    }

    @Test
    fun testCatSingleFile() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["101"] = "Hello 115 Terminal!\nWelcome to cat command."
        }
        val ctx = createTestContext(fileRepository = repo)
        val file = createMockFile(
            name = "notes.txt",
            fileId = "101",
            size = "50",
            pickCode = "p101"
        )
        ctx.putMockFiles("0", listOf(file))

        val engine = createTestEngine()
        val output = engine.executeStrings("cat notes.txt", ctx)

        assertEquals(listOf("Hello 115 Terminal!", "Welcome to cat command."), output)
    }

    @Test
    fun testCatMultipleFilesWithGlobalLineNumbering() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["201"] = "file1_line1\nfile1_line2"
            mockDownloadStreams["202"] = "file2_line1\nfile2_line2"
        }
        val ctx = createTestContext(fileRepository = repo)
        val file1 = createMockFile(name = "file1.txt", fileId = "201", size = "50")
        val file2 = createMockFile(name = "file2.txt", fileId = "202", size = "50")
        ctx.putMockFiles("0", listOf(file1, file2))

        val engine = createTestEngine()
        val output = engine.executeStrings("cat -n file1.txt file2.txt", ctx)

        assertEquals(4, output.size)
        assertEquals("     1\tfile1_line1", output[0])
        assertEquals("     2\tfile1_line2", output[1])
        assertEquals("     3\tfile2_line1", output[2])
        assertEquals("     4\tfile2_line2", output[3])
    }

    @Test
    fun testCatRejectNonTextFile() = runBlocking {
        val repo = TestMockFileRepository()
        val ctx = createTestContext(fileRepository = repo)
        val video = createMockFile(name = "movie.mp4", fileId = "301", size = "1024")
        ctx.putMockFiles("0", listOf(video))

        val engine = createTestEngine()
        val output = engine.executeStrings("cat movie.mp4", ctx)

        assertTrue(output.any { it.contains("Not a text file") })
    }

    @Test
    fun testCatSizeLimitRejectionAndCustomOverride() = runBlocking {
        // 构造超过默认 200KB 限制的大文件 (300KB = 307200 字节)
        val bigSize = (300 * 1024).toString()
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["401"] = "large file content"
        }
        val ctx = createTestContext(fileRepository = repo)
        val bigFile = createMockFile(name = "large.log", fileId = "401", size = bigSize)
        ctx.putMockFiles("0", listOf(bigFile))

        val engine = createTestEngine()

        // 1. 默认大小限制下（200KB），应被拦截
        val rejectOut = engine.executeStrings("cat large.log", ctx)
        assertTrue(rejectOut.any { it.contains("File exceeds size limit") })

        // 2. 通过 --max-size 500 提高单文件上限至 500KB，应放行
        val allowOut = engine.executeStrings("cat --max-size 500 large.log", ctx)
        assertEquals(listOf("large file content"), allowOut)
    }

    @Test
    fun testCatDirectoryError() = runBlocking {
        val ctx = createTestContext()
        val folder = createMockFolder(name = "documents", categoryId = "501")
        ctx.putMockFiles("0", listOf(folder))

        val engine = createTestEngine()
        val output = engine.executeStrings("cat documents", ctx)

        assertTrue(output.any { it.contains("Is a directory") })
    }

    @Test
    fun testCatFileNotFound() = runBlocking {
        val ctx = createTestContext()
        ctx.putMockFiles("0", emptyList())

        val engine = createTestEngine()
        val output = engine.executeStrings("cat missing.txt", ctx)

        assertTrue(output.any { it.contains("No such file or directory") })
    }

    @Test
    fun testCatMultiFileTolerance() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["601"] = "good1"
            mockDownloadStreams["602"] = "good2"
        }
        val ctx = createTestContext(fileRepository = repo)
        val file1 = createMockFile(name = "a.txt", fileId = "601", size = "10")
        val file2 = createMockFile(name = "b.txt", fileId = "602", size = "10")
        ctx.putMockFiles("0", listOf(file1, file2))

        val engine = createTestEngine()
        // a.txt 存在，non_exist.txt 不存在，b.txt 存在
        val output = engine.executeStrings("cat a.txt non_exist.txt b.txt", ctx)

        // 验证 a.txt 正常输出，non_exist.txt 输出报错，b.txt 仍继续正常输出
        assertTrue(output.contains("good1"))
        assertTrue(output.any { it.contains("non_exist.txt: No such file or directory") })
        assertTrue(output.contains("good2"))
    }

    @Test
    fun testCatEmptyFile() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["701"] = ""
        }
        val ctx = createTestContext(fileRepository = repo)
        val emptyFile = createMockFile(name = "empty.txt", fileId = "701", size = "0")
        ctx.putMockFiles("0", listOf(emptyFile))

        val engine = createTestEngine()
        val output = engine.executeStrings("cat empty.txt", ctx)

        assertTrue(output.isEmpty())
    }
}
