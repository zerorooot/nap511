package github.zerorooot.nap511.terminal.viewmodel

import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import github.zerorooot.nap511.bean.AvatarBean
import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.bean.RecycleBean
import github.zerorooot.nap511.bean.RecycleInfo
import github.zerorooot.nap511.repository.FileRepository
import github.zerorooot.nap511.terminal.engine.CompletionResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class TerminalViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var viewModel: TerminalViewModel

    @Before
    fun setup() {
        github.zerorooot.nap511.terminal.engine.TerminalHistoryManager().historyFile.delete()
        java.io.File(System.getProperty("java.io.tmpdir"), "terminal_history.txt").delete()
        Dispatchers.setMain(testDispatcher)
        val avatar = AvatarBean(
            face = "",
            userName = "tester",
            userId = "1001"
        )
        viewModel = TerminalViewModel(
            avatarBean = avatar,
            mainDispatcher = testDispatcher
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testStickyModifiersToggle() {
        assertFalse(viewModel.isCtrlActive)
        assertFalse(viewModel.isAltActive)

        // 开启 CTRL
        viewModel.toggleCtrl()
        assertTrue(viewModel.isCtrlActive)
        assertFalse(viewModel.isAltActive)

        // 切换到 ALT，自动关闭 CTRL
        viewModel.toggleAlt()
        assertFalse(viewModel.isCtrlActive)
        assertTrue(viewModel.isAltActive)

        // 再次点击 ALT，关闭 ALT
        viewModel.toggleAlt()
        assertFalse(viewModel.isAltActive)

        // resetModifiers
        viewModel.toggleCtrl()
        assertTrue(viewModel.isCtrlActive)
        viewModel.resetModifiers()
        assertFalse(viewModel.isCtrlActive)
    }

    @Test
    fun testCtrlCIdleBehavior() {
        viewModel.onInputChange(TextFieldValue("git clone https://test.git"))
        assertEquals("git clone https://test.git", viewModel.inputState.text)

        viewModel.handleCtrlC()

        // 验证当前输入框被清空
        assertEquals("", viewModel.inputState.text)

        // 验证屏幕追加了带 ^C 的命令行
        val lastLine = viewModel.lines.lastOrNull()
        assertTrue(lastLine != null)
        assertTrue(lastLine!!.text.endsWith("git clone https://test.git^C"))
    }

    @Test
    fun testCtrlUAndCtrlK() {
        // "cd /root/config" 光标位于索引 3（即 '/' 处）
        viewModel.onInputChange(TextFieldValue("cd /root/config", selection = TextRange(3)))

        // Ctrl+U：清空光标前内容 -> 留下 "/root/config"
        viewModel.handleCtrlU()
        assertEquals("/root/config", viewModel.inputState.text)
        assertEquals(0, viewModel.inputState.selection.start)

        // Ctrl+K：清空光标后内容 -> 留下 ""
        viewModel.handleCtrlK()
        assertEquals("", viewModel.inputState.text)
    }

    @Test
    fun testCtrlW() {
        viewModel.onInputChange(TextFieldValue("rm -rf old_folder", selection = TextRange(17)))
        viewModel.handleCtrlW()
        assertEquals("rm -rf ", viewModel.inputState.text)
    }

    @Test
    fun testCtrlAAndCtrlE() {
        viewModel.onInputChange(TextFieldValue("ls -l /tmp", selection = TextRange(5)))

        viewModel.handleCtrlA()
        assertEquals(0, viewModel.inputState.selection.start)

        viewModel.handleCtrlE()
        assertEquals(10, viewModel.inputState.selection.start)
    }

    @Test
    fun testCtrlDBehavior() {
        var exitCalled = false
        val exitCallback = { exitCalled = true }

        // 1. 输入框为空时触发退出
        viewModel.onInputChange(TextFieldValue(""))
        viewModel.handleCtrlD(exitCallback)
        assertTrue(exitCalled)

        // 2. 输入框非空时向后删除字符
        viewModel.onInputChange(TextFieldValue("abc", selection = TextRange(1)))
        viewModel.handleCtrlD(exitCallback)
        assertEquals("ac", viewModel.inputState.text)
        assertEquals(1, viewModel.inputState.selection.start)
    }

    @Test
    fun testAltShortcuts() {
        // Alt+B 与 Alt+F
        viewModel.onInputChange(TextFieldValue("cp source target", selection = TextRange(16)))

        // Alt+B 跳到 "target"
        viewModel.handleAltB()
        assertEquals(10, viewModel.inputState.selection.start)

        // Alt+B 跳到 "source"
        viewModel.handleAltB()
        assertEquals(3, viewModel.inputState.selection.start)

        // Alt+F 跳到 "source" 词尾 (9)
        viewModel.handleAltF()
        assertEquals(9, viewModel.inputState.selection.start)

        // Alt+D 向后删词 -> 删除空格和 target
        viewModel.handleAltD()
        assertEquals("cp source", viewModel.inputState.text)
    }

    @Test
    fun testCtrlLeftAndRightShortcuts() {
        // "cat /var/log/syslog" 长度 19，光标处于 19
        viewModel.onInputChange(TextFieldValue("cat /var/log/syslog", selection = TextRange(19)))

        // handleCtrlLeft: 跳到 "syslog" 词首 (13)
        viewModel.handleCtrlLeft()
        assertEquals(13, viewModel.inputState.selection.start)

        // handleCtrlLeft: 跳到 "log" 词首 (9)
        viewModel.handleCtrlLeft()
        assertEquals(9, viewModel.inputState.selection.start)

        // handleCtrlRight: 跳到 "log" 词尾 (12)
        viewModel.handleCtrlRight()
        assertEquals(12, viewModel.inputState.selection.start)

        // 测试 sticky CTRL 与方向键触发
        viewModel.toggleCtrl()
        assertTrue(viewModel.isCtrlActive)
        viewModel.moveCursorLeft() // 应触发 handleCtrlLeft 跳到 "log" 词首 (9)
        assertFalse(viewModel.isCtrlActive) // 验证 CTRL 状态已复位
        assertEquals(9, viewModel.inputState.selection.start)

        viewModel.handleCtrlLeft() // 跳到 "var" 词首 (5)
        assertEquals(5, viewModel.inputState.selection.start)
    }

    @Test
    fun testCtrlRightWithGhostText() {
        viewModel.onInputChange(TextFieldValue("ls", selection = TextRange(2)))
        val ghost = " -la /tmp"
        val chunk = github.zerorooot.nap511.terminal.engine.TerminalLineEditor.extractNextWord(ghost)
        assertEquals(" -la", chunk)
    }

    @Test
    fun testSoftKeyboardInterceptionWhenCtrlActive() {
        viewModel.onInputChange(TextFieldValue("long draft text"))
        viewModel.toggleCtrl()
        assertTrue(viewModel.isCtrlActive)

        // 模拟软键盘输入字母 'c'（文本由 15 变 16，光标位置输入 'c'）
        viewModel.onInputChange(TextFieldValue("long draft textc", selection = TextRange(16)))

        // 验证 CTRL 粘滞模式自动复位
        assertFalse(viewModel.isCtrlActive)
        // 验证 Ctrl+C 被拦截执行：输入框被清空，历史行包含 ^C
        assertEquals("", viewModel.inputState.text)
        assertTrue(viewModel.lines.last().text.contains("^C"))
    }

    @Test
    fun testValidCommandValidation() {
        val manager = viewModel.historyManager
        // 验证系统命令合法
        assertTrue(manager.isValidCommand("help") { true })
        assertTrue(manager.isValidCommand("ls -l") { true })
        // 验证未知命令非法
        assertFalse(manager.isValidCommand("invalid_cmd") { false })
    }

    @Test
    fun testCandidateSelectionAndDismiss() {
        // 1. 测试直接选取补全候选（普通命令）
        viewModel.onInputChange(TextFieldValue("cle", selection = TextRange(3)))
        val candidate = github.zerorooot.nap511.terminal.engine.CompletionCandidate(
            name = "clear",
            displayText = "clear",
            insertText = "clear ",
            type = github.zerorooot.nap511.terminal.engine.CandidateType.COMMAND,
            isDirectory = false
        )
        viewModel.selectCandidate(candidate)
        assertEquals("clear ", viewModel.inputState.text)
        assertFalse(viewModel.isCompletionBarVisible)

        // 2. 测试输入变动时自动关闭补全栏
        viewModel.onInputChange(TextFieldValue("cd"))
        viewModel.dismissCompletionBar()
        assertFalse(viewModel.isCompletionBarVisible)
        assertEquals(0, viewModel.completionCandidates.size)
    }

    @Test
    fun testInitDirectoryIfNeededPreservesSessionAndResetsOnExit() {
        // 1. 第一次初始化进入 /MyFolder
        viewModel.initDirectoryIfNeeded(listOf(PathBean("100", "MyFolder", "0")))
        assertTrue(viewModel.isSessionInitialized)
        assertEquals("100", viewModel.currentCid)
        assertEquals("/MyFolder", viewModel.currentPath)

        // 模拟用户在终端执行操作，添加了行内容
        viewModel.lines.add(TerminalLine("custom test output", TerminalLineType.Output.TEXT))
        val countBefore = viewModel.lines.size

        // 2. 当前会话保持状态（如从 open 文件预览返回），不应被覆盖
        viewModel.initDirectoryIfNeeded(listOf(PathBean("200", "OtherFolder", "0")))
        assertEquals("100", viewModel.currentCid)
        assertEquals("/MyFolder", viewModel.currentPath)
        assertEquals(countBefore, viewModel.lines.size)

        // 3. 执行 exit 退出终端（调用 resetSession）
        viewModel.resetSession()
        assertFalse(viewModel.isSessionInitialized)
        assertEquals(0, viewModel.lines.size)

        // 4. exit 退出后重新进入，视同第一次进入，工作目录与新的 FileScreen 目录一致
        viewModel.initDirectoryIfNeeded(listOf(PathBean("200", "OtherFolder", "0")))
        assertTrue(viewModel.isSessionInitialized)
        assertEquals("200", viewModel.currentCid)
        assertEquals("/OtherFolder", viewModel.currentPath)
    }

    @Test
    fun testDualLinePromptFormatting() {
        assertEquals("tester@1001:/", viewModel.contextPromptText())
        assertEquals("tester@1001:/\n$ ", viewModel.promptText())

        // 验证切换目录后上下文同步更新
        viewModel.initDirectoryIfNeeded(listOf(PathBean("1", "电影", "0"), PathBean("123", "科幻", "1")))
        assertEquals("tester@1001:/电影/科幻", viewModel.contextPromptText())
        assertEquals("tester@1001:/电影/科幻\n$ ", viewModel.promptText())
    }

    @Test
    fun testCtrlCIdleDualLineFormat() {
        viewModel.onInputChange(TextFieldValue("pwd"))
        viewModel.handleCtrlC()

        // 验证历史屏幕记录为双行：第一行路径，第二行 $ 命令 + ^C
        val commandLine = viewModel.lines.lastOrNull { it.type == TerminalLineType.System.COMMAND }
        assertTrue(commandLine != null)
        assertEquals("tester@1001:/\n$ pwd^C", commandLine!!.text)
    }

    @Test
    fun testScrollbackBufferLimit() {
        viewModel.clearScreen()
        assertEquals(0, viewModel.lines.size)

        // 追加 2050 行输出，验证自动截断修剪至 MAX_SCROLLBACK_LINES (2000)
        val dummyLines = (1..2050).map { TerminalLine("line $it", TerminalLineType.Output.TEXT) }
        viewModel.appendTerminalLines(dummyLines)

        assertEquals(TerminalViewModel.MAX_SCROLLBACK_LINES, viewModel.lines.size)
        // 顶部最旧的 50 行被修剪，第一行应为第 51 行
        assertEquals("line 51", viewModel.lines.first().text)
        assertEquals("line 2050", viewModel.lines.last().text)

        // 单行追加再触发一次修剪
        viewModel.appendTerminalLine(TerminalLine("line 2051", TerminalLineType.Output.TEXT))
        assertEquals(TerminalViewModel.MAX_SCROLLBACK_LINES, viewModel.lines.size)
        assertEquals("line 52", viewModel.lines.first().text)
        assertEquals("line 2051", viewModel.lines.last().text)
    }

    @Test
    fun testStaleImeReplayInterceptionAfterSubmit() {
        val command = "find -name '*.mp4'"
        viewModel.onInputChange(TextFieldValue(command))
        assertEquals(command, viewModel.inputState.text)

        viewModel.submitInput()
        // 提交后输入框应已被清空
        assertEquals("", viewModel.inputState.text)

        // 模拟软键盘 IME 或软按键提交后传回的旧文本 replay
        viewModel.onInputChange(TextFieldValue(command))
        // 应拦截旧文本，输入框依然保持为空
        assertEquals("", viewModel.inputState.text)

        // 用户输入新字符，应正常更新
        viewModel.onInputChange(TextFieldValue("ls"))
        assertEquals("ls", viewModel.inputState.text)
    }

    @Test
    fun testTerminalLineTypeHelpStorageAndScrolling() {
        // 先清空初始化欢迎信息
        viewModel.clearScreen()
        assertEquals(0, viewModel.lines.size)

        // 验证 TerminalLineType.System.HELP 能够安全追加至终端行缓冲并在列表中正常保持
        val helpLine = TerminalLine("命令名称: find", TerminalLineType.System.HELP)
        viewModel.appendTerminalLine(helpLine)

        assertEquals(1, viewModel.lines.size)
        assertEquals(TerminalLineType.System.HELP, viewModel.lines.first().type)
        assertEquals("命令名称: find", viewModel.lines.first().text)
    }

    @Test
    fun testAppendTerminalLinesWithAllTypes() {
        viewModel.clearScreen()

        val allTypedLines = listOf(
            TerminalLine("plain text", TerminalLineType.Output.TEXT),
            TerminalLine("movie.mp4", TerminalLineType.Output.FILE_ENTRY),
            TerminalLine("/Movies/movie.mp4", TerminalLineType.Output.PATH_ENTRY),
            TerminalLine("-rwxr-xr-x 100 2026-10-05 file.txt", TerminalLineType.Output.LONG_LISTING),
            TerminalLine("[目录] docs (-)", TerminalLineType.Output.FIND_CATEGORY),
            TerminalLine("\u001B[31mError\u001B[0m", TerminalLineType.Output.ANSI),
            TerminalLine("help doc", TerminalLineType.System.HELP),
            TerminalLine("system banner", TerminalLineType.System.INFO),
            TerminalLine("error msg", TerminalLineType.System.ERROR),
            TerminalLine("prompt confirm", TerminalLineType.System.PROMPT),
            TerminalLine("user command", TerminalLineType.System.COMMAND)
        )

        viewModel.appendTerminalLines(allTypedLines)
        assertEquals(11, viewModel.lines.size)

        for (i in allTypedLines.indices) {
            assertEquals(allTypedLines[i].type, viewModel.lines[i].type)
            assertEquals(allTypedLines[i].text, viewModel.lines[i].text)
        }
    }

    @Test
    fun testTrashCompletionPredictsRecycleBin() = runBlocking {
        val testRepo = object : FileRepository() {
            override suspend fun recycleList(
                aid: String,
                cid: String,
                offset: String,
                limit: String
            ): RecycleInfo {
                return RecycleInfo(
                    state = true,
                    recycleBeanList = arrayListOf(
                        RecycleBean(id = "101", fileName = "document_in_trash.pdf", isFolder = false),
                        RecycleBean(id = "102", fileName = "video_in_trash.mp4", isFolder = false)
                    )
                )
            }
        }
        val vm = TerminalViewModel(
            fileRepository = testRepo,
            mainDispatcher = testDispatcher
        )

        // 输入 "trash -r doc" 并触发补全计算，光标位于末尾
        vm.onInputChange(TextFieldValue("trash -r doc", TextRange(12)))
        val completions = vm.computeCompletions()
        assertTrue(completions.isUniqueMatch)
        assertEquals(1, completions.candidates.size)
        assertEquals("document_in_trash.pdf", completions.candidates.first().name)
    }

    @Test
    fun testCatAndUnzipCompletionFilteringInViewModel() = runBlocking {
        val testRepo = object : FileRepository() {
            override suspend fun getFiles(
                cid: String,
                showDir: Int,
                aid: Int,
                asc: Int,
                naturalSort: Int,
                order: String,
                limit: Int,
                format: String
            ): FilesBean {
                return FilesBean(
                    fileBeanList = arrayListOf(
                        FileBean(fileId = "", categoryId = "1", name = "folder", isFolder = true),
                        FileBean(fileId = "2", name = "movie.mp4", isFolder = false),
                        FileBean(fileId = "3", name = "readme.txt", isFolder = false),
                        FileBean(fileId = "4", name = "bundle.zip", isFolder = false)
                    ),
                    cid = cid,
                    count = 4,
                    order = order,
                    path = emptyList()
                )
            }
        }
        val catPathList = listOf(PathBean("test_cat_cid_unique", "TestCat", "0"))
        val vm = TerminalViewModel(
            initialPathList = catPathList,
            fileRepository = testRepo,
            mainDispatcher = testDispatcher
        )

        // 1. cat 补全：仅包含 folder 和 readme.txt
        vm.onInputChange(TextFieldValue("cat ", TextRange(4)))
        val catComp = vm.computeCompletions()
        val catNames = catComp.candidates.map { it.name }.toSet()
        assertEquals(setOf("folder", "readme.txt"), catNames)

        // 2. unzip 补全：仅包含 folder 和 bundle.zip
        vm.onInputChange(TextFieldValue("unzip ", TextRange(6)))
        val unzipComp = vm.computeCompletions()
        val unzipNames = unzipComp.candidates.map { it.name }.toSet()
        assertEquals(setOf("folder", "bundle.zip"), unzipNames)

        // 3. cd 补全：仅包含 folder
        vm.onInputChange(TextFieldValue("cd ", TextRange(3)))
        val cdComp = vm.computeCompletions()
        val cdNames = cdComp.candidates.map { it.name }.toSet()
        assertEquals(setOf("folder"), cdNames)
    }

    @Test
    fun testGhostTextFilteringByCommand() = runBlocking {
        val testRepo = object : FileRepository() {
            override suspend fun getFiles(
                cid: String,
                showDir: Int,
                aid: Int,
                asc: Int,
                naturalSort: Int,
                order: String,
                limit: Int,
                format: String
            ): FilesBean {
                return FilesBean(
                    fileBeanList = arrayListOf(
                        FileBean(fileId = "1", name = "audio.mp3", isFolder = false),
                        FileBean(fileId = "2", name = "archive.zip", isFolder = false),
                        FileBean(fileId = "3", name = "article.txt", isFolder = false)
                    ),
                    cid = cid,
                    count = 3,
                    order = order,
                    path = emptyList()
                )
            }
        }
        val ghostPathList = listOf(PathBean("test_ghost_cid_unique", "TestGhost", "0"))
        val vm = TerminalViewModel(
            initialPathList = ghostPathList,
            fileRepository = testRepo,
            mainDispatcher = testDispatcher
        )
        vm.refreshCachedEntriesAsync(vm.currentCid)

        // 输入 "cat a" 时：当前目录下有 archive.zip, article.txt, audio.mp3
        // 幽灵文本应该且仅能建议 article.txt (后缀为 rticle.txt)，不能建议 archive.zip 或 audio.mp3
        vm.onInputChange(TextFieldValue("cat a", TextRange(5)))
        assertEquals("rticle.txt", vm.ghostText)

        // 输入 "unzip a" 时：幽灵文本应该建议 archive.zip (后缀为 rchive.zip)
        vm.onInputChange(TextFieldValue("unzip a", TextRange(7)))
        assertEquals("rchive.zip", vm.ghostText)
    }

    @Test
    fun testHistoryPointerResetOnCtrlCAndResetSession() {
        viewModel.historyNavigator.clear()
        viewModel.historyNavigator.add("help")
        viewModel.historyNavigator.add("pwd")

        // 向上漫游一次（此时应显示 pwd）
        viewModel.navigateHistoryUp()
        assertEquals("pwd", viewModel.inputState.text)

        // 再次向上漫游（显示 help）
        viewModel.navigateHistoryUp()
        assertEquals("help", viewModel.inputState.text)

        // 执行 Ctrl+C 取消行，历史指针应被重置
        viewModel.handleCtrlC()
        assertEquals("", viewModel.inputState.text)

        // 重新按向上漫游，应该重新从最后一条命令 "pwd" 开始，而非留在 "help"
        viewModel.navigateHistoryUp()
        assertEquals("pwd", viewModel.inputState.text)

        // 测试 resetSession 后漫游指针重置
        viewModel.navigateHistoryUp()
        assertEquals("help", viewModel.inputState.text)
        viewModel.resetSession()
        assertEquals("", viewModel.inputState.text)
        viewModel.navigateHistoryUp()
        assertEquals("pwd", viewModel.inputState.text)
    }

    @Test
    fun testPipelineInteractiveConfirmationStrictOrder() = runBlocking {
        val f1 = FileBean(name = "file1.zip", fileId = "101", isFolder = false)
        val f2 = FileBean(name = "file2.zip", fileId = "102", isFolder = false)

        val testRepo = object : FileRepository() {
            override suspend fun getFiles(
                cid: String,
                showDir: Int,
                aid: Int,
                asc: Int,
                naturalSort: Int,
                order: String,
                limit: Int,
                format: String
            ): FilesBean {
                return FilesBean(
                    fileBeanList = arrayListOf(f1, f2),
                    cid = "0",
                    count = 2,
                    order = "",
                    path = emptyList()
                )
            }
        }

        val vm = TerminalViewModel(
            fileRepository = testRepo,
            mainDispatcher = testDispatcher
        )
        vm.context.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f1, f2), cid = "0", count = 2, order = "", path = emptyList())
        )

        // 提交命令: ls | grep zip | xargs -t -I _ rm _
        vm.onInputChange(TextFieldValue("ls | grep zip | xargs -t -I _ rm _"))
        vm.submitInput()

        // 等待后台 IO 协程执行到达确认挂起点
        val start = System.currentTimeMillis()
        while (!vm.isWaitingConfirmation && System.currentTimeMillis() - start < 2000) {
            kotlinx.coroutines.delay(20)
        }

        // 第一次挂起交互确认 (针对 file1.zip)
        assertTrue(vm.isWaitingConfirmation)
        val linesAfterFirstPrompt = vm.lines.map { it.text }
        // 关键验证：+ rm file1.zip 必须严格出现在 确认提示 之前
        val indexOfEcho1 = linesAfterFirstPrompt.indexOfFirst { it.contains("+ rm file1.zip") }
        val indexOfPrompt1 = linesAfterFirstPrompt.indexOfFirst { it.contains("rm: 是否确认删除 'file1.zip'?") }
        assertTrue("indexOfEcho1 should be found", indexOfEcho1 >= 0)
        assertTrue("indexOfPrompt1 should be found", indexOfPrompt1 >= 0)
        assertTrue("+ rm file1.zip 必须排在 prompt1 之前", indexOfEcho1 < indexOfPrompt1)

        // 用户输入 n 取消
        vm.onInputChange(TextFieldValue("n"))
        vm.submitInput()

        // 等待后台 IO 协程执行到达第二次确认挂起点
        val start2 = System.currentTimeMillis()
        while (!vm.isWaitingConfirmation && System.currentTimeMillis() - start2 < 2000) {
            kotlinx.coroutines.delay(20)
        }

        // 第二次挂起交互确认 (针对 file2.zip)
        assertTrue(vm.isWaitingConfirmation)
        val linesAfterSecondPrompt = vm.lines.map { it.text }
        val indexOfCancel1 = linesAfterSecondPrompt.indexOfFirst { it.contains("rm: 已取消删除 'file1.zip'") }
        val indexOfEcho2 = linesAfterSecondPrompt.indexOfFirst { it.contains("+ rm file2.zip") }
        val indexOfPrompt2 = linesAfterSecondPrompt.indexOfFirst { it.contains("rm: 是否确认删除 'file2.zip'?") }

        assertTrue("indexOfCancel1 should be found", indexOfCancel1 >= 0)
        assertTrue("indexOfEcho2 should be found", indexOfEcho2 >= 0)
        assertTrue("indexOfPrompt2 should be found", indexOfPrompt2 >= 0)

        // 严格时序保证：prompt1 < n < cancel1 < echo2 < prompt2
        assertTrue("cancel1 必须排在 prompt1 之后", indexOfPrompt1 < indexOfCancel1)
        assertTrue("echo2 必须排在 cancel1 之后", indexOfCancel1 < indexOfEcho2)
        assertTrue("prompt2 必须排在 echo2 之后", indexOfEcho2 < indexOfPrompt2)

        // 用户再次输入 n 取消
        vm.onInputChange(TextFieldValue("n"))
        vm.submitInput()

        // 等待命令完全执行完毕
        val start3 = System.currentTimeMillis()
        while (vm.isExecuting && System.currentTimeMillis() - start3 < 2000) {
            kotlinx.coroutines.delay(20)
        }

        // 执行结束
        assertFalse(vm.isWaitingConfirmation)
        assertFalse(vm.isExecuting)

        val finalLines = vm.lines.map { it.text }
        val indexOfCancel2 = finalLines.indexOfFirst { it.contains("rm: 已取消删除 'file2.zip'") }
        assertTrue("indexOfCancel2 should be found", indexOfCancel2 >= 0)
        assertTrue("cancel2 必须排在 prompt2 之后", indexOfPrompt2 < indexOfCancel2)
    }
}
