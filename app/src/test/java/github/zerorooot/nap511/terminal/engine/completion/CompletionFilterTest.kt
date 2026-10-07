package github.zerorooot.nap511.terminal.engine.completion

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.terminal.commands.cloud.UnzipCommand
import github.zerorooot.nap511.terminal.commands.file.CatCommand
import github.zerorooot.nap511.terminal.commands.file.CdCommand
import github.zerorooot.nap511.terminal.commands.stream.GrepCommand
import github.zerorooot.nap511.terminal.engine.CandidateType
import github.zerorooot.nap511.terminal.engine.CompletionEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionFilterTest {

    private val sampleFiles = listOf(
        FileBean(fileId = "1", name = "Documents", isFolder = true),
        FileBean(fileId = "2", name = "Downloads", isFolder = true),
        FileBean(fileId = "3", name = "video.mp4", isFolder = false),
        FileBean(fileId = "4", name = "readme.txt", isFolder = false),
        FileBean(fileId = "5", name = "code.py", isFolder = false),
        FileBean(fileId = "6", name = "archive.zip", isFolder = false),
        FileBean(fileId = "7", name = "data.tar.gz", isFolder = false),
        FileBean(fileId = "8", name = "image.png", isFolder = false)
    )

    @Test
    fun testCatCommandFiltersTextFilesAndRetainsDirectories() {
        val cat = CatCommand()
        // 1. cat 补全：应包含 Documents, Downloads, readme.txt, code.py，排除 video.mp4, archive.zip, image.png
        val parsed = CompletionEngine.parseContext("cat ", 4)
        val result = CompletionEngine.calculateCompletion(
            parsedContext = parsed,
            registeredCommands = listOf("cat"),
            commandFlagsMap = emptyMap(),
            directoryFiles = sampleFiles,
            completer = cat.completer
        )

        val names = result.candidates.map { it.name }.toSet()
        assertEquals(setOf("Documents", "Downloads", "readme.txt", "code.py"), names)

        // 验证候选类型
        val docCand = result.candidates.first { it.name == "Documents" }
        assertEquals(CandidateType.DIRECTORY, docCand.type)
        assertEquals("Documents/", docCand.displayText)

        val txtCand = result.candidates.first { it.name == "readme.txt" }
        assertEquals(CandidateType.FILE, txtCand.type)
        assertEquals("readme.txt", txtCand.displayText)
    }

    @Test
    fun testUnzipCommandFiltersArchivesAndRetainsDirectories() {
        val unzip = UnzipCommand()
        // unzip 补全：应包含 Documents, Downloads, archive.zip, data.tar.gz
        val parsed = CompletionEngine.parseContext("unzip ", 6)
        val result = CompletionEngine.calculateCompletion(
            parsedContext = parsed,
            registeredCommands = listOf("unzip"),
            commandFlagsMap = emptyMap(),
            directoryFiles = sampleFiles,
            completer = unzip.completer
        )

        val names = result.candidates.map { it.name }.toSet()
        assertEquals(setOf("Documents", "Downloads", "archive.zip", "data.tar.gz"), names)
    }

    @Test
    fun testCdCommandOnlyRetainsDirectories() {
        val cd = CdCommand()
        val parsed = CompletionEngine.parseContext("cd ", 3)
        val result = CompletionEngine.calculateCompletion(
            parsedContext = parsed,
            registeredCommands = listOf("cd"),
            commandFlagsMap = emptyMap(),
            directoryFiles = sampleFiles,
            completer = cd.completer
        )

        val names = result.candidates.map { it.name }.toSet()
        assertEquals(setOf("Documents", "Downloads"), names)
        assertTrue(result.candidates.all { it.isDirectory })
    }

    @Test
    fun testGrepCommandPositionalArgsDifferentiation() {
        val grep = GrepCommand()

        // 1. argIndex == 0 (pattern 模式串输入)：不补全路径文件
        val parsedPattern = CompletionEngine.parseContext("grep ", 5)
        assertEquals(0, parsedPattern.argIndex)
        val resultPattern = CompletionEngine.calculateCompletion(
            parsedContext = parsedPattern,
            registeredCommands = listOf("grep"),
            commandFlagsMap = emptyMap(),
            directoryFiles = sampleFiles,
            completer = grep.completer
        )
        assertTrue("grep 首个位置参数应为 pattern，不补全路径文件", resultPattern.candidates.isEmpty())

        // 2. argIndex >= 1 (文件参数输入)：过滤文本文件
        val parsedFile = CompletionEngine.parseContext("grep pattern ", 13)
        assertEquals(1, parsedFile.argIndex)
        val resultFile = CompletionEngine.calculateCompletion(
            parsedContext = parsedFile,
            registeredCommands = listOf("grep"),
            commandFlagsMap = emptyMap(),
            directoryFiles = sampleFiles,
            completer = grep.completer
        )
        val fileNames = resultFile.candidates.map { it.name }.toSet()
        assertEquals(setOf("Documents", "Downloads", "readme.txt", "code.py"), fileNames)
    }

    @Test
    fun testParseContextArgIndexCalculationWithFlags() {
        // 1. 包含简单 flag：grep -i pattern foo
        val parsed1 = CompletionEngine.parseContext("grep -i pattern foo", 19)
        assertEquals(1, parsed1.argIndex) // pattern 是 0，foo 是 1

        // 2. 带选项值的 flag：cat --max-size 100 my_file
        val parsed2 = CompletionEngine.parseContext(
            input = "cat --max-size 100 my_file",
            cursorPosition = 26,
            valueOptions = setOf("--max-size")
        )
        assertEquals(0, parsed2.argIndex) // --max-size 100 被识别为选项及参数，my_file 是位置参数 0

        // 3. 多位置参数：cat file1.txt file2.txt
        val parsed3 = CompletionEngine.parseContext("cat file1.txt file2.txt", 23)
        assertEquals(1, parsed3.argIndex)
    }

    @Test
    fun testDefaultCompleterRegistryFallback() {
        // 当未显式传递 completer 时，自动从 DefaultCompleterRegistry 解析
        val parsedCat = CompletionEngine.parseContext("cat r", 5)
        val resultCat = CompletionEngine.calculateCompletion(
            parsedContext = parsedCat,
            registeredCommands = listOf("cat"),
            commandFlagsMap = emptyMap(),
            directoryFiles = sampleFiles
        )
        assertEquals(1, resultCat.candidates.size)
        assertEquals("readme.txt", resultCat.candidates.first().name)

        val parsedUnzip = CompletionEngine.parseContext("unzip a", 7)
        val resultUnzip = CompletionEngine.calculateCompletion(
            parsedContext = parsedUnzip,
            registeredCommands = listOf("unzip"),
            commandFlagsMap = emptyMap(),
            directoryFiles = sampleFiles
        )
        assertEquals(1, resultUnzip.candidates.size)
        assertEquals("archive.zip", resultUnzip.candidates.first().name)
    }

    @Test
    fun testFileFiltersByExtensions() {
        // 测试按扩展名过滤 (例如仅允许 mp4 与 png)
        val videoOrImgFilter = FileFilters.byExtensions("mp4", "png", allowDirectories = true)
        val filtered = sampleFiles.filter { videoOrImgFilter.accept(it, 0) }.map { it.name }.toSet()
        assertEquals(setOf("Documents", "Downloads", "video.mp4", "image.png"), filtered)

        // 测试不保留目录的扩展名过滤
        val strictFilesOnly = FileFilters.byExtensions(setOf("mp4"), allowDirectories = false)
        val filteredStrict = sampleFiles.filter { strictFilesOnly.accept(it, 0) }.map { it.name }.toSet()
        assertEquals(setOf("video.mp4"), filteredStrict)
    }
}
