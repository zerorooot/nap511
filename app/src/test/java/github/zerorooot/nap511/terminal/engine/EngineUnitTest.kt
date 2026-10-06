package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EngineUnitTest {

    @Test
    fun testLexerTokenize() {
        val input = """ls -l "My Documents" 'Another Folder' file\ with\ space.txt"""
        val tokens = Lexer.tokenize(input)
        assertEquals(5, tokens.size)
        assertEquals("ls", tokens[0])
        assertEquals("-l", tokens[1])
        assertEquals("My Documents", tokens[2])
        assertEquals("Another Folder", tokens[3])
        assertEquals("file with space.txt", tokens[4])
    }

    @Test
    fun testLexerPipeline() {
        val input = """ls -l "Folder | Name" | grep "mp4" | wc -l"""
        val stages = Lexer.parsePipeline(input)
        assertEquals(3, stages.size)
        assertEquals("ls", stages[0].command)
        assertEquals(listOf("-l", "Folder | Name"), stages[0].args)
        assertEquals("grep", stages[1].command)
        assertEquals(listOf("mp4"), stages[1].args)
        assertEquals("wc", stages[2].command)
        assertEquals(listOf("-l"), stages[2].args)
    }

    @Test
    fun testGlobMatcher() {
        val candidates = listOf("movie.mp4", "music.mp3", "notes.txt", "video2.mp4", "pic.jpg")
        val matched = GlobMatcher.expand("*.mp4", candidates)
        assertEquals(listOf("movie.mp4", "video2.mp4"), matched)

        val singleMatch = GlobMatcher.expand("note?.txt", candidates)
        assertEquals(listOf("notes.txt"), singleMatch)

        val noMatch = GlobMatcher.expand("*.mkv", candidates)
        assertEquals(listOf("*.mkv"), noMatch)
    }

    @Test
    fun testCommandDslAndHelp() = runBlocking {
        val registry = CommandRegistry()
        registry.register("testcmd") {
            description = "测试命令"
            usage = "testcmd [options]"
            flag("-a", "全部")
            flag("-b", "选项B")
            execute { _, _, _ ->
                flow { emit(TerminalOutput("done")) }
            }
        }

        val def = registry.get("testcmd")
        assertNotNull(def)

        // 测试自动 -h
        val ctx = TerminalContext()
        val helpOutput = def!!.execute(ctx, listOf("-h"), kotlinx.coroutines.flow.emptyFlow()).toList()
        assertEquals(TerminalLineType.System.HELP, helpOutput.first().type)
        val fullHelpText = helpOutput.joinToString("\n") { it.text }
        assertTrue(fullHelpText.contains("测试命令"))
        assertTrue(fullHelpText.contains("-a"))
        assertTrue(fullHelpText.contains("-b"))

        // 测试全量帮助
        val allHelp = registry.buildAllHelpMessage()
        assertTrue(allHelp.contains("testcmd"))
    }

    @Test
    fun testPipelineFlowExecution() = runBlocking {
        val registry = CommandRegistry()
        registry.register("echo") {
            description = "回显"
            execute { _, args, _ ->
                flow { emit(TerminalOutput(args.joinToString(" "))) }
            }
        }
        registry.register("grep") {
            description = "过滤"
            execute { _, args, stdin ->
                flow {
                    val pattern = args.firstOrNull() ?: ""
                    stdin.collect { line ->
                        if (line.contains(pattern)) {
                            emit(TerminalOutput(line))
                        }
                    }
                }
            }
        }

        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        val result = engine.execute("echo 'hello world' | grep world", ctx).toList()
        assertEquals(1, result.size)
        assertEquals("hello world", result[0].text)

        val noResult = engine.execute("echo 'hello world' | grep foo", ctx).toList()
        assertEquals(0, noResult.size)
    }

    @Test
    fun testAutosuggestionGhostText() {
        val commands = listOf("ls", "cd", "pwd", "mkdir", "trash")
        val dirEntries = listOf("Documents", "Downloads", "Movies", "test.mp4")
        val history = listOf("ls -l", "cd Documents")

        // 1. 命令前缀匹配
        val ghost1 = AutosuggestionEngine.calculateGhostText(
            input = "mk",
            cursorPosition = 2,
            registeredCommands = commands,
            directoryEntries = dirEntries,
            history = emptyList()
        )
        assertEquals("dir", ghost1) // mkdir 补全 dir

        // 2. 目录文件名匹配
        val ghost2 = AutosuggestionEngine.calculateGhostText(
            input = "cd Mov",
            cursorPosition = 6,
            registeredCommands = commands,
            directoryEntries = dirEntries,
            history = emptyList()
        )
        assertEquals("ies", ghost2) // Movies 补全 ies

        // 3. 历史记录优先补全
        val ghost3 = AutosuggestionEngine.calculateGhostText(
            input = "ls ",
            cursorPosition = 3,
            registeredCommands = commands,
            directoryEntries = dirEntries,
            history = history
        )
        assertEquals("-l", ghost3) // 补全 -l
    }

    @Test
    fun testTerminalLineEditorCursorMovement() {
        val initial = androidx.compose.ui.text.input.TextFieldValue("hello world terminal", androidx.compose.ui.text.TextRange(6))
        
        // Home (Ctrl+A)
        val home = TerminalLineEditor.moveCursorHome(initial)
        assertEquals(0, home.selection.start)

        // End (Ctrl+E)
        val end = TerminalLineEditor.moveCursorEnd(initial)
        assertEquals(20, end.selection.start)

        // Alt+B / Ctrl+Left (跳到 hello 首字符 0)
        val wordBack = TerminalLineEditor.moveWordBackward(initial)
        assertEquals(0, wordBack.selection.start)

        // Alt+F / Ctrl+Right 从 0 跳过 hello 到 5
        val wordForward = TerminalLineEditor.moveWordForward(wordBack)
        assertEquals(5, wordForward.selection.start)
    }

    @Test
    fun testTerminalLineEditorWordNavigationWithSymbols() {
        // "cd /usr/local/bin" 长度 17，光标位于末尾 17
        val pathValue = androidx.compose.ui.text.input.TextFieldValue("cd /usr/local/bin", androidx.compose.ui.text.TextRange(17))

        // 1 次 moveWordBackward 跳到 "bin" 词首 (14)
        val pos1 = TerminalLineEditor.moveWordBackward(pathValue)
        assertEquals(14, pos1.selection.start)

        // 2 次 moveWordBackward 跳到 "local" 词首 (8)
        val pos2 = TerminalLineEditor.moveWordBackward(pos1)
        assertEquals(8, pos2.selection.start)

        // 3 次 moveWordBackward 跳到 "usr" 词首 (4)
        val pos3 = TerminalLineEditor.moveWordBackward(pos2)
        assertEquals(4, pos3.selection.start)

        // 4 次 moveWordBackward 跳到 "cd" 词首 (0)
        val pos4 = TerminalLineEditor.moveWordBackward(pos3)
        assertEquals(0, pos4.selection.start)

        // 从 0 开始 forward-word:
        // 1 次 moveWordForward 跳到 "cd" 词尾 (2)
        val fpos1 = TerminalLineEditor.moveWordForward(pos4)
        assertEquals(2, fpos1.selection.start)

        // 2 次 moveWordForward 跳到 "usr" 词尾 (7)
        val fpos2 = TerminalLineEditor.moveWordForward(fpos1)
        assertEquals(7, fpos2.selection.start)

        // 3 次 moveWordForward 跳到 "local" 词尾 (13)
        val fpos3 = TerminalLineEditor.moveWordForward(fpos2)
        assertEquals(13, fpos3.selection.start)

        // 4 次 moveWordForward 跳到 "bin" 词尾 (17)
        val fpos4 = TerminalLineEditor.moveWordForward(fpos3)
        assertEquals(17, fpos4.selection.start)
    }

    @Test
    fun testExtractNextWordFromGhostText() {
        assertEquals(" -l", TerminalLineEditor.extractNextWord(" -l /tmp"))
        assertEquals("dir", TerminalLineEditor.extractNextWord("dir"))
        assertEquals(" --help", TerminalLineEditor.extractNextWord(" --help"))
        assertEquals("", TerminalLineEditor.extractNextWord(""))
    }

    @Test
    fun testTerminalLineEditorDeletions() {
        // "mkdir -p test" 光标在 "test" 首字符 (索引 9)
        val tf = androidx.compose.ui.text.input.TextFieldValue("mkdir -p test", androidx.compose.ui.text.TextRange(9))

        // Ctrl+U (删至行首) -> 留下 "test"
        val deleteToStart = TerminalLineEditor.deleteToBeginning(tf)
        assertEquals("test", deleteToStart.text)
        assertEquals(0, deleteToStart.selection.start)

        // Ctrl+K (删至行尾) -> 留下 "mkdir -p "
        val deleteToEnd = TerminalLineEditor.deleteToEnd(tf)
        assertEquals("mkdir -p ", deleteToEnd.text)
        assertEquals(9, deleteToEnd.selection.start)

        // Ctrl+W (向前删词) -> 从索引 9 向前删 "-p" 及空格 -> 留下 "mkdir test"
        val deleteWordBack = TerminalLineEditor.deleteWordBackward(tf)
        assertEquals("mkdir test", deleteWordBack.text)

        // Alt+D (向后删词) -> 从索引 9 向后删 "test" -> 留下 "mkdir -p "
        val deleteWordForward = TerminalLineEditor.deleteWordForward(tf)
        assertEquals("mkdir -p ", deleteWordForward.text)
    }

    @Test
    fun testTerminalLineEditorExtractLastArgument() {
        val cmd1 = """cp -r "My Folder" /backup/dest"""
        assertEquals("/backup/dest", TerminalLineEditor.extractLastArgument(cmd1))

        val cmd2 = "ls -la"
        assertEquals("-la", TerminalLineEditor.extractLastArgument(cmd2))

        val cmd3 = "clear"
        assertEquals("clear", TerminalLineEditor.extractLastArgument(cmd3))
    }

    @Test
    fun testPipelineThreeStages() = runBlocking {
        val registry = CommandRegistry()
        registry.register("echo") {
            description = "回显"
            execute { _, args, _ ->
                flow { emit(TerminalOutput(args.joinToString(" "))) }
            }
        }
        registry.register("grep") {
            description = "过滤"
            execute { _, args, stdin ->
                flow {
                    val pattern = args.firstOrNull() ?: ""
                    stdin.collect { line ->
                        if (line.contains(pattern)) {
                            emit(TerminalOutput(line))
                        }
                    }
                }
            }
        }
        registry.register("wc") {
            description = "统计"
            execute { _, args, stdin ->
                flow {
                    var count = 0
                    stdin.collect { count++ }
                    emit(TerminalOutput(count.toString()))
                }
            }
        }

        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 验证三级管道: echo -> grep -> wc 正常闭环输出，无死锁
        val result = engine.execute("echo 'apple\nbanana\napricot' | grep ap | wc", ctx).toList()
        assertEquals(1, result.size)
        assertEquals("2", result[0].text)
        assertEquals(TerminalLineType.Output.TEXT, result[0].type)
    }

    @Test
    fun testPipelineUnknownCommandHandling() = runBlocking {
        val registry = CommandRegistry()
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        val output = engine.execute("unknown_cmd -a", ctx).toList()
        assertEquals(2, output.size)
        assertEquals(TerminalLineType.System.ERROR, output[0].type)
        assertTrue(output[0].text.contains("terminal: command not found: unknown_cmd"))
        assertEquals(TerminalLineType.System.HELP, output[1].type)
        assertTrue(output[1].text.contains("输入 '?' 或 'help'"))
    }

    @Test
    fun testPipelineEmptyInput() = runBlocking {
        val registry = CommandRegistry()
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        assertTrue(engine.execute("", ctx).toList().isEmpty())
        assertTrue(engine.execute("    ", ctx).toList().isEmpty())
    }
}
