package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 流式命令、管道流转及诊断通道隔离测试套件
 * 覆盖：管道命令组合流转、-h / ? / help 帮助阻断拦截、命令输出语义类型校验及诊断通道隔离
 */
class StreamAndPipelineTest {

    @org.junit.Before
    fun setUp() {
        github.zerorooot.nap511.util.TextFileHelper.clearMemoryCache()
        runBlocking {
            github.zerorooot.nap511.util.FileCacheManager.clearAll()
        }
    }

    /**
     * 测试常用流式命令基础管道传递
     */
    @Test
    fun testStreamCommandsPipeline() = runBlocking {
        val engine = createTestEngine(historyList = listOf("ls", "cd Movies"))
        val ctx = createTestContext()

        // echo | grep 过滤匹配
        val out1 = engine.executeStrings("echo 'apple\nbanana\napricot' | grep ap", ctx)
        assertEquals(2, out1.size)
        assertTrue(out1.contains("apple"))
        assertTrue(out1.contains("apricot"))

        // sort -n 数字排序
        val out2 = engine.executeStrings("echo '3\n1\n2' | sort -n", ctx)
        assertEquals(listOf("1", "2", "3"), out2)

        // wc -l 统计行数
        val out3 = engine.executeStrings("echo 'one\ntwo\nthree' | wc -l", ctx)
        assertEquals(listOf("3"), out3)

        // wc (无参数时先输出各列名称，下一行输出各计数值)
        val outWcDefault = engine.executeStrings("echo 'one\ntwo\nthree' | wc", ctx)
        assertEquals(2, outWcDefault.size)
        assertTrue(outWcDefault[0].contains("Lines"))
        assertTrue(outWcDefault[0].contains("Words"))
        assertTrue(outWcDefault[0].contains("Chars"))
        assertTrue(outWcDefault[1].contains("3"))

        // head -n 2 截取头部前 2 行
        val out4 = engine.executeStrings("echo '1\n2\n3\n4' | head -n 2", ctx)
        assertEquals(listOf("1", "2"), out4)

        // tail -n 2 截取尾部后 2 行
        val out5 = engine.executeStrings("echo '1\n2\n3\n4' | tail -n 2", ctx)
        assertEquals(listOf("3", "4"), out5)

        // history 历史记录查看
        val out6 = engine.executeStrings("history", ctx)
        assertEquals(2, out6.size)
        assertTrue(out6[0].contains("ls"))
        assertTrue(out6[1].contains("cd Movies"))
    }

    /**
     * 测试帮助拦截器携带 TerminalLineType.System.HELP 语义类型
     */
    @Test
    fun testCommandHelpInterception() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.execute("ls -h", ctx).toList()
        assertTrue(out.isNotEmpty())
        assertEquals("帮助拦截必须在源头携带 TerminalLineType.System.HELP 语义类型", TerminalLineType.System.HELP, out.first().type)
        val text = out.joinToString("\n") { it.text }
        assertTrue(text.contains("ls"))
        assertTrue(text.contains("-l"))
        assertTrue(text.contains("-t"))
        assertTrue(text.contains("-S"))

        val findHelpList = engine.execute("find -h", ctx).toList()
        assertTrue(findHelpList.isNotEmpty())
        assertEquals("find -h 必须在源头携带 TerminalLineType.System.HELP 语义类型", TerminalLineType.System.HELP, findHelpList.first().type)
        val findHelp = findHelpList.joinToString("\n") { it.text }
        assertTrue(findHelp.contains("find"))
        assertTrue(findHelp.contains("-filter"))
        assertTrue(findHelp.contains("-name"))
    }

    /**
     * 测试 ? / man / help 帮助帮助指令问号响应
     */
    @Test
    fun testChineseQuestionMarkHelpCommand() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val outAsciiList = engine.execute("?", ctx).toList()
        val outChineseList = engine.execute("man", ctx).toList()
        val outHelpList = engine.execute("help", ctx).toList()

        assertEquals("help 汇总输出必须在源头携带 TerminalLineType.System.HELP 语义类型", TerminalLineType.System.HELP, outChineseList.first().type)
        val outAscii = outAsciiList.joinToString("\n") { it.text }
        val outChinese = outChineseList.joinToString("\n") { it.text }
        val outHelp = outHelpList.joinToString("\n") { it.text }

        assertTrue(outChinese.contains("可用命令列表"))
        assertEquals(outAscii, outChinese)
        assertEquals(outHelp, outChinese)
    }

    /**
     * 测试流式命令的扩展选项 (-i, -v, -c, -w, -r, clear)
     */
    @Test
    fun testStreamCommandsExtendedOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // grep -i 忽略大小写
        val grepI = engine.executeStrings("echo 'Apple\nbanana\nAPPLE' | grep -i apple", ctx)
        assertEquals(listOf("Apple", "APPLE"), grepI)

        // grep -v 反向匹配
        val grepV = engine.executeStrings("echo 'Apple\nbanana\nApple' | grep -v Apple", ctx)
        assertEquals(listOf("banana"), grepV)

        // grep -c 统计行数
        val grepC = engine.executeStrings("echo 'Apple\nbanana\nAPPLE' | grep -c Apple", ctx)
        assertEquals(listOf("1"), grepC)

        // wc -w 统计词数与 wc -c 统计字符数
        val wcW = engine.executeStrings("echo 'hello world test' | wc -w", ctx)
        assertEquals(listOf("3"), wcW)

        // sort 默认与 sort -r 逆序
        val sortAsc = engine.executeStrings("echo 'banana\napple\norange' | sort", ctx)
        assertEquals(listOf("apple", "banana", "orange"), sortAsc)

        val sortDesc = engine.executeStrings("echo 'banana\napple\norange' | sort -r", ctx)
        assertEquals(listOf("orange", "banana", "apple"), sortDesc)

        // clear 清屏控制命令
        val clearOut = engine.executeStrings("clear", ctx)
        assertEquals(listOf("__TERMINAL_CLEAR_SCREEN__"), clearOut)
    }

    /**
     * 测试不同命令输出行的语义类型 (Output.TEXT, FILE_ENTRY, LONG_LISTING, PATH_ENTRY, System.ERROR)
     */
    @Test
    fun testCommandOutputLineTypes() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 普通文本输出 echo -> Output.TEXT
        val echoOut = engine.execute("echo hello", ctx).toList()
        assertEquals(TerminalLineType.Output.TEXT, echoOut[0].type)

        // 文件列表 ls -> Output.FILE_ENTRY
        val file = createMockFile("test.txt", "1")
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file), cid = "0", count = 1, order = "", path = emptyList()))
        val lsOut = engine.execute("ls", ctx).toList()
        assertEquals(TerminalLineType.Output.FILE_ENTRY, lsOut[0].type)

        // 详细列表 ls -l -> 第一行 "total N" (Output.TEXT)，后续文件行 (Output.LONG_LISTING)
        val lsLongOut = engine.execute("ls -l", ctx).toList()
        assertEquals(2, lsLongOut.size)
        assertEquals(TerminalLineType.Output.TEXT, lsLongOut[0].type)
        assertEquals(TerminalLineType.Output.LONG_LISTING, lsLongOut[1].type)

        // 路径输出 find -> Output.PATH_ENTRY
        val findOut = engine.execute("find test.txt", ctx).toList()
        assertEquals(TerminalLineType.Output.PATH_ENTRY, findOut[0].type)

        // 错误输出 -> System.ERROR
        val errOut = engine.execute("rm", ctx).toList()
        assertEquals(TerminalLineType.System.ERROR, errOut[0].type)
    }

    /**
     * 测试诊断系统提示信息在管道中的隔离，防止污染下游数据处理
     */
    @Test
    fun testPipelineDiagnosticChannelIsolation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // find 针对空目录或无匹配项会 emitSystem("未找到匹配的项目")
        // 当管道连接到 wc -l 时，System.INFO 行不会进入下游，下游计数应为 0
        val outEmptyFindPipe = engine.executeStrings("find -suffix nonexistent | wc -l", ctx)
        assertEquals(listOf("0"), outEmptyFindPipe)
    }

    /**
     * 测试非流式/不消费 stdin 的命令防御 (pwd, cd, mkdir 忽略管道输入)
     */
    @Test
    fun testNonStreamingCommandsIgnoreStdin() = runBlocking {
        val mockRepo = createTestMockRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        // pwd 忽略管道输入
        val outPwd = engine.executeStrings("echo 'unwanted input' | pwd", ctx)
        assertEquals(listOf("/根目录"), outPwd)

        // cd 忽略管道输入，正常按显式参数切换
        engine.executeStrings("echo 'target_folder' | cd /", ctx)
        assertEquals(listOf("/根目录"), engine.executeStrings("pwd", ctx))

        // mkdir 忽略管道输入，正常按显式参数创建目录
        val outMkdir = engine.executeStrings("echo 'extra' | mkdir new_dir", ctx)
        assertTrue(outMkdir.any { it.contains("已创建文件夹") || it.contains("new_dir") })
    }

    /**
     * 测试管道中继阶段命令未找到时的防御性收尾
     */
    @Test
    fun testPipelineIntermediateStageFailureBehavior() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings("echo 'test' | invalid_command_xyz | wc -l", ctx)
        assertTrue(out.any { it.contains("terminal: command not found: invalid_command_xyz") })
    }

    /**
     * 测试混合标准输出与标准错误时的管道隔离 (混流隔离)
     */
    @Test
    fun testPipelineMixedStdoutAndStderrIsolation() = runBlocking {
        val mockRepo = TestMockFileRepository().apply {
            mockDownloadStreams["stream_good_501"] = "good text content"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f = createMockFile("good.txt", "stream_good_501")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f), cid = "0", count = 1, order = "", path = emptyList())
        )

        val outMatch = engine.executeStrings("cat good.txt absent.txt | grep good", ctx)
        assertEquals(listOf("good text content"), outMatch)

        val outErrorLeak = engine.executeStrings("cat good.txt absent.txt | grep -c 'No such file'", ctx)
        assertEquals(listOf("0"), outErrorLeak)
    }

    /**
     * 测试管道输入与显式位置文件参数的优先级行为 (优先读文件并安全忽略管道输入)
     */
    @Test
    fun testPipelineStdinVsPositionalArgumentPriority() = runBlocking {
        val mockRepo = TestMockFileRepository().apply {
            mockDownloadStreams["stream_disk_601"] = "file_body"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = mockRepo)

        val f = createMockFile("file.txt", "stream_disk_601")
        ctx.fileCacheManager.put(
            "0",
            FilesBean(fileBeanList = arrayListOf(f), cid = "0", count = 1, order = "", path = emptyList())
        )

        val out = engine.executeStrings("echo 'pipe_body' | cat file.txt", ctx)
        assertEquals(listOf("file_body"), out)
    }

    /**
     * 测试管道下游截断时的协程短路与 Broken Pipe 安全收尾
     */
    @Test
    fun testPipelineUpstreamCancellationOnBrokenPipe() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val out = engine.executeStrings("echo -e '1\\n2\\n3\\n4\\n5\\n6\\n7\\n8\\n9\\n10' | head -n 2 | wc -l", ctx)
        assertEquals(listOf("2"), out)
    }
}
