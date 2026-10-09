package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.commands.TestMockFileRepository
import github.zerorooot.nap511.terminal.commands.createMockFile
import github.zerorooot.nap511.terminal.commands.createMockFolder
import github.zerorooot.nap511.terminal.commands.createTestContext
import github.zerorooot.nap511.terminal.commands.putMockFiles
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式输入源解析器测试 (StreamSourceResolverTest)
 *
 * 验证：
 * 1. 空参数列表时自动回退为标准输入 stdin；
 * 2. 单个 "-" 显式声明时自动回退为标准输入 stdin；
 * 3. 混合多文件操作数与错误隔离：单个文件不存在、是目录或非文本文件时独立生成 StreamSourceItem.Error，
 *    不阻断其他合法文件的流式读取。
 */
class StreamSourceResolverTest {

    @Test
    fun testEmptyOrDashFallbackToStdin() = runBlocking {
        val ctx = createTestContext()
        val stdin = flowOf("line1", "line2")

        // 1. 空参数列表
        val sourcesEmpty = StreamSourceResolver.resolveSources(ctx, emptyList(), stdin)
        assertEquals(1, sourcesEmpty.size)
        assertTrue(sourcesEmpty[0] is StreamSourceItem.DataStream)
        assertEquals("stdin", sourcesEmpty[0].sourceName)
        val linesEmpty = (sourcesEmpty[0] as StreamSourceItem.DataStream).lines.toList()
        assertEquals(listOf("line1", "line2"), linesEmpty)

        // 2. 显式单个 "-"
        val sourcesDash = StreamSourceResolver.resolveSources(ctx, listOf("-"), stdin)
        assertEquals(1, sourcesDash.size)
        assertTrue(sourcesDash[0] is StreamSourceItem.DataStream)
        assertEquals("stdin", sourcesDash[0].sourceName)
        val linesDash = (sourcesDash[0] as StreamSourceItem.DataStream).lines.toList()
        assertEquals(listOf("line1", "line2"), linesDash)
    }

    @Test
    fun testMultiFileResolutionAndErrorIsolation() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["stream_101"] = "hello from file1\nline2"
        }
        val ctx = createTestContext(fileRepository = repo)

        val validFile = createMockFile("file1.txt", "stream_101", icoString = "txt")
        val dir = createMockFolder("subDir", "stream_102")
        ctx.putMockFiles("0", listOf(validFile, dir))

        // 请求 3 个源：合法文本文件、目录、不存在的文件
        val operands = listOf("file1.txt", "subDir", "missing.txt")
        val sources = StreamSourceResolver.resolveSources(ctx, operands, flowOf())

        assertEquals(3, sources.size)

        // 1. 合法文本文件：成功读取
        assertTrue(sources[0] is StreamSourceItem.DataStream)
        assertEquals("file1.txt", sources[0].sourceName)
        val fileLines = (sources[0] as StreamSourceItem.DataStream).lines.toList()
        assertEquals(listOf("hello from file1", "line2"), fileLines)

        // 2. 目录：产生错误，但不抛异常
        assertTrue(sources[1] is StreamSourceItem.Error)
        val dirErr = sources[1] as StreamSourceItem.Error
        assertEquals("subDir", dirErr.sourceName)
        assertTrue(dirErr.message.contains("Is a directory"))

        // 3. 不存在的文件：产生错误
        assertTrue(sources[2] is StreamSourceItem.Error)
        val missingErr = sources[2] as StreamSourceItem.Error
        assertEquals("missing.txt", missingErr.sourceName)
        assertTrue(missingErr.message.contains("No such file or directory"))
    }
}
