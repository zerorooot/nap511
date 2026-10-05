package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.bean.FileBean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CompletionEngineTest {

    @Test
    fun testLongestCommonPrefix() {
        assertEquals("", CompletionEngine.longestCommonPrefix(emptyList()))
        assertEquals("single", CompletionEngine.longestCommonPrefix(listOf("single")))
        assertEquals("Download_", CompletionEngine.longestCommonPrefix(listOf("Download_A", "Download_B", "Download_C")))
        assertEquals("movie", CompletionEngine.longestCommonPrefix(listOf("movie1.mp4", "movie2.mp4")))
        assertEquals("", CompletionEngine.longestCommonPrefix(listOf("apple", "banana")))
        // 大小写不敏感匹配
        assertEquals("doc", CompletionEngine.longestCommonPrefix(listOf("document", "Docx"), ignoreCase = true).lowercase())
    }

    @Test
    fun testEscapePath() {
        assertEquals("Downloads/", CompletionEngine.escapePath("Downloads", true))
        assertEquals("My\\ Movies/", CompletionEngine.escapePath("My Movies", true))
        assertEquals("photo.jpg ", CompletionEngine.escapePath("photo.jpg", false))
        assertEquals("my\\ photo.jpg ", CompletionEngine.escapePath("my photo.jpg", false))
        assertEquals("Folder/", CompletionEngine.escapePath("Folder/", true))
    }

    @Test
    fun testParseContext() {
        // 1. 命令名称输入上下文
        val ctx1 = CompletionEngine.parseContext("c", 1)
        assertEquals(CompletionContextType.COMMAND, ctx1.contextType)
        assertEquals("c", ctx1.prefix)
        assertEquals(0, ctx1.tokenStartIndex)

        // 2. cd 后面无参数（准备列出所有）
        val ctx2 = CompletionEngine.parseContext("cd ", 3)
        assertEquals(CompletionContextType.PATH, ctx2.contextType)
        assertEquals("cd", ctx2.commandName)
        assertEquals("", ctx2.parentPath)
        assertEquals("", ctx2.prefix)
        assertEquals(3, ctx2.tokenStartIndex)

        // 3. cd 后带前缀
        val ctx3 = CompletionEngine.parseContext("cd Mov", 6)
        assertEquals(CompletionContextType.PATH, ctx3.contextType)
        assertEquals("cd", ctx3.commandName)
        assertEquals("", ctx3.parentPath)
        assertEquals("Mov", ctx3.prefix)
        assertEquals(3, ctx3.tokenStartIndex)

        // 4. 参数 Flag 上下文
        val ctx4 = CompletionEngine.parseContext("ls -l", 5)
        assertEquals(CompletionContextType.FLAG, ctx4.contextType)
        assertEquals("ls", ctx4.commandName)
        assertEquals("-l", ctx4.prefix)

        // 5. 跨级子目录路径
        val ctx5 = CompletionEngine.parseContext("cd Movies/Action/sc", 19)
        assertEquals(CompletionContextType.PATH, ctx5.contextType)
        assertEquals("cd", ctx5.commandName)
        assertEquals("Movies/Action/", ctx5.parentPath)
        assertEquals("sc", ctx5.prefix)
        assertEquals(3, ctx5.tokenStartIndex)

        // 6. 带空格转义的路径
        val ctx6 = CompletionEngine.parseContext("cd My\\ Documents/fil", 20)
        assertEquals(CompletionContextType.PATH, ctx6.contextType)
        assertEquals("cd", ctx6.commandName)
        assertEquals("My Documents/", ctx6.parentPath)
        assertEquals("fil", ctx6.prefix)
        assertEquals(3, ctx6.tokenStartIndex)

        // 7. 管道符后命令
        val ctx7 = CompletionEngine.parseContext("echo hello | gr", 15)
        assertEquals(CompletionContextType.COMMAND, ctx7.contextType)
        assertEquals("gr", ctx7.prefix)
    }

    @Test
    fun testCalculateCompletionWithCdFilter() {
        val files = listOf(
            FileBean(fileId = "1", name = "Documents", isFolder = true),
            FileBean(fileId = "2", name = "Downloads", isFolder = true),
            FileBean(fileId = "3", name = "movie.mp4", isFolder = false),
            FileBean(fileId = "4", name = "notes.txt", isFolder = false)
        )

        // 测试 cd：应该自动过滤掉普通文件，只返回文件夹
        val parsedCd = CompletionEngine.parseContext("cd D", 4)
        val resCd = CompletionEngine.calculateCompletion(
            parsedContext = parsedCd,
            registeredCommands = listOf("cd", "ls"),
            commandFlagsMap = emptyMap(),
            directoryFiles = files
        )

        assertEquals(2, resCd.candidates.size)
        assertTrue(resCd.candidates.all { it.isDirectory })
        assertEquals("Do", resCd.longestCommonPrefix)
        assertFalse(resCd.isUniqueMatch)

        // 测试 open：不过滤普通文件
        val parsedOpen = CompletionEngine.parseContext("open m", 6)
        val resOpen = CompletionEngine.calculateCompletion(
            parsedContext = parsedOpen,
            registeredCommands = listOf("open"),
            commandFlagsMap = emptyMap(),
            directoryFiles = files
        )

        assertEquals(1, resOpen.candidates.size)
        assertEquals("movie.mp4", resOpen.candidates[0].name)
        assertTrue(resOpen.isUniqueMatch)
    }

    @Test
    fun testApplyCandidate() {
        // 1. 唯一命令补全
        val parsedCmd = CompletionEngine.parseContext("cle", 3)
        val (text1, cursor1) = CompletionEngine.applyCandidate("cle", parsedCmd, "clear")
        assertEquals("clear ", text1)
        assertEquals(6, cursor1)

        // 2. 文件夹补全
        val parsedDir = CompletionEngine.parseContext("cd D", 4)
        val (text2, cursor2) = CompletionEngine.applyCandidate("cd D", parsedDir, "Documents", isDirectory = true)
        assertEquals("cd Documents/", text2)
        assertEquals(13, cursor2)

        // 3. 带空格的跨级子目录文件夹补全
        val parsedSubDir = CompletionEngine.parseContext("cd My\\ Documents/sub", 20)
        val (text3, cursor3) = CompletionEngine.applyCandidate(
            "cd My\\ Documents/sub",
            parsedSubDir,
            "Secret Folder",
            isDirectory = true
        )
        assertEquals("cd My\\ Documents/Secret\\ Folder/", text3)
        assertEquals(32, cursor3)
    }

    @Test
    fun testPartialEscapePath() {
        // 部分公共前缀补全时不应追加未转义空格或尾部斜杠
        val partialFile = CompletionEngine.escapePath("Sample File ", isDirectory = false, isPartial = true)
        assertEquals("Sample\\ File\\ ", partialFile)

        val completedFile = CompletionEngine.escapePath("Sample File ", isDirectory = false, isPartial = false)
        assertEquals("Sample\\ File\\  ", completedFile)

        val partialDir = CompletionEngine.escapePath("My Folder", isDirectory = true, isPartial = true)
        assertEquals("My\\ Folder", partialDir)
    }

    @Test
    fun testLcpPartialCompletionAndCyclingWithoutDuplication() {
        val files = listOf(
            FileBean(fileId = "1", name = "Sample File Alpha.zip", isFolder = false),
            FileBean(fileId = "2", name = "Sample File Beta.zip", isFolder = false),
            FileBean(fileId = "3", name = "Sample File Gamma.zip", isFolder = false)
        )

        // 初始输入 "ls "
        val initialInput = "ls "
        val parsed1 = CompletionEngine.parseContext(initialInput, initialInput.length)
        val compResult = CompletionEngine.calculateCompletion(
            parsedContext = parsed1,
            registeredCommands = listOf("ls"),
            commandFlagsMap = emptyMap(),
            directoryFiles = files
        )

        assertEquals(3, compResult.candidates.size)
        val lcp = compResult.longestCommonPrefix
        assertEquals("Sample File ", lcp)
        assertFalse(compResult.isUniqueMatch)

        // 第一次 Tab：延伸 LCP（isPartial = true）
        val (lcpText, lcpCursor) = CompletionEngine.applyCandidate(
            originalText = initialInput,
            parsedContext = parsed1,
            candidateToInsert = lcp,
            isDirectory = false,
            isPartial = true
        )
        // 验证没有多余的尾部未转义空格
        assertEquals("ls Sample\\ File\\ ", lcpText)
        assertEquals(lcpText.length, lcpCursor)

        // 基于 LCP 补全后的新文本解析基础上下文
        val baseParsed = CompletionEngine.parseContext(lcpText, lcpCursor)
        assertEquals(3, baseParsed.tokenStartIndex)
        assertEquals(lcpText.length, baseParsed.tokenEndIndex)
        assertEquals("Sample File ", baseParsed.prefix)

        // 第二次 Tab：轮转第 1 个候选（应替换整个 LCP，绝不能重复前缀）
        val (cand1Text, _) = CompletionEngine.applyCandidate(
            originalText = lcpText,
            parsedContext = baseParsed,
            candidateToInsert = compResult.candidates[0].name,
            isDirectory = compResult.candidates[0].isDirectory,
            isPartial = false
        )
        assertEquals("ls Sample\\ File\\ Alpha.zip ", cand1Text)

        // 第三次 Tab：轮转第 2 个候选
        val (cand2Text, _) = CompletionEngine.applyCandidate(
            originalText = lcpText,
            parsedContext = baseParsed,
            candidateToInsert = compResult.candidates[1].name,
            isDirectory = compResult.candidates[1].isDirectory,
            isPartial = false
        )
        assertEquals("ls Sample\\ File\\ Beta.zip ", cand2Text)

        // 第四次 Tab：轮转第 3 个候选
        val (cand3Text, _) = CompletionEngine.applyCandidate(
            originalText = lcpText,
            parsedContext = baseParsed,
            candidateToInsert = compResult.candidates[2].name,
            isDirectory = compResult.candidates[2].isDirectory,
            isPartial = false
        )
        assertEquals("ls Sample\\ File\\ Gamma.zip ", cand3Text)
    }

    @Test
    fun testFlagCompletionWithoutParameterExamples() {
        val flagsMap = mapOf(
            "head" to listOf(CommandFlag("-n <NUM>", "指定行数")),
            "find" to listOf(
                CommandFlag("-name <pattern>", ""),
                CommandFlag("-type <f|d>", ""),
                CommandFlag("-suffix <ext>", ""),
                CommandFlag("-filter <type>", ""),
                CommandFlag("-maxdepth <N>", ""),
                CommandFlag("-global", "")
            )
        )

        // 1. head -n <NUM> 唯一匹配
        val parsedHead = CompletionEngine.parseContext("head -", 6)
        val resHead = CompletionEngine.calculateCompletion(
            parsedContext = parsedHead,
            registeredCommands = listOf("head"),
            commandFlagsMap = flagsMap,
            directoryFiles = emptyList()
        )

        assertEquals(1, resHead.candidates.size)
        val candHead = resHead.candidates[0]
        assertEquals("-n", candHead.name)
        assertEquals("-n <NUM>", candHead.displayText)
        assertEquals("-n ", candHead.insertText)
        assertEquals("-n", resHead.longestCommonPrefix)
        assertTrue(resHead.isUniqueMatch)

        // 补全到文本框中，验证不含例子 <NUM>
        val (textHead, cursorHead) = CompletionEngine.applyCandidate("head -", parsedHead, candHead.name)
        assertEquals("head -n ", textHead)
        assertEquals(8, cursorHead)

        // 2. find -name <pattern> 前缀匹配
        val parsedFind = CompletionEngine.parseContext("find -nam", 9)
        val resFind = CompletionEngine.calculateCompletion(
            parsedContext = parsedFind,
            registeredCommands = listOf("find"),
            commandFlagsMap = flagsMap,
            directoryFiles = emptyList()
        )

        assertEquals(1, resFind.candidates.size)
        val candFind = resFind.candidates[0]
        assertEquals("-name", candFind.name)
        assertEquals("-name <pattern>", candFind.displayText)
        assertEquals("-name ", candFind.insertText)

        val (textFind, cursorFind) = CompletionEngine.applyCandidate("find -nam", parsedFind, candFind.name)
        assertEquals("find -name ", textFind)
        assertEquals(11, cursorFind)
    }
}
