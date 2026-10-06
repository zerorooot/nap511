package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.FileBean
import github.zerorooot.nap511.bean.FilesBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StreamAndPipelineTest {

    @Test
    fun testStreamCommandsPipeline() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { listOf("ls", "cd Movies") }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 测试 echo | grep
        val out1 = engine.executeStrings("echo 'apple\nbanana\napricot' | grep ap", ctx)
        assertEquals(2, out1.size)
        assertTrue(out1.contains("apple"))
        assertTrue(out1.contains("apricot"))

        // 测试 sort -r
        val out2 = engine.executeStrings("echo '3\n1\n2' | sort -n", ctx)
        assertEquals(listOf("1", "2", "3"), out2)

        // 测试 wc -l
        val out3 = engine.executeStrings("echo 'one\ntwo\nthree' | wc -l", ctx)
        assertEquals(listOf("3"), out3)

        // 测试 wc (无参数时先输出各列名称，下一行输出各计数值)
        val outWcDefault = engine.executeStrings("echo 'one\ntwo\nthree' | wc", ctx)
        assertEquals(2, outWcDefault.size)
        assertTrue(outWcDefault[0].contains("Lines"))
        assertTrue(outWcDefault[0].contains("Words"))
        assertTrue(outWcDefault[0].contains("Chars"))
        assertTrue(outWcDefault[1].contains("3"))

        // 测试 head -n 2
        val out4 = engine.executeStrings("echo '1\n2\n3\n4' | head -n 2", ctx)
        assertEquals(listOf("1", "2"), out4)

        // 测试 tail -n 2
        val out5 = engine.executeStrings("echo '1\n2\n3\n4' | tail -n 2", ctx)
        assertEquals(listOf("3", "4"), out5)

        // 测试 history
        val out6 = engine.executeStrings("history", ctx)
        assertEquals(2, out6.size)
        assertTrue(out6[0].contains("ls"))
        assertTrue(out6[1].contains("cd Movies"))
    }

    @Test
    fun testCommandHelpInterception() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

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

    @Test
    fun testChineseQuestionMarkHelpCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

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

    @Test
    fun testStreamCommandsExtendedOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. grep -i 忽略大小写
        val grepI = engine.executeStrings("echo 'Apple\nbanana\nAPPLE' | grep -i apple", ctx)
        assertEquals(listOf("Apple", "APPLE"), grepI)

        // 2. grep -v 反向匹配
        val grepV = engine.executeStrings("echo 'Apple\nbanana\nApple' | grep -v Apple", ctx)
        assertEquals(listOf("banana"), grepV)

        // 3. grep -c 统计行数
        val grepC = engine.executeStrings("echo 'Apple\nbanana\nAPPLE' | grep -c Apple", ctx)
        assertEquals(listOf("1"), grepC)

        // 4. wc -w 统计词数与 wc -c 统计字符数
        val wcW = engine.executeStrings("echo 'hello world test' | wc -w", ctx)
        assertEquals(listOf("3"), wcW)

        // 5. sort 默认与 sort -r 逆序
        val sortAsc = engine.executeStrings("echo 'banana\napple\norange' | sort", ctx)
        assertEquals(listOf("apple", "banana", "orange"), sortAsc)

        val sortDesc = engine.executeStrings("echo 'banana\napple\norange' | sort -r", ctx)
        assertEquals(listOf("orange", "banana", "apple"), sortDesc)

        // 6. clear 命令
        val clearOut = engine.executeStrings("clear", ctx)
        assertEquals(listOf("__TERMINAL_CLEAR_SCREEN__"), clearOut)
    }

    @Test
    fun testCommandOutputLineTypes() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // 1. 普通文本输出 echo -> Output.TEXT
        val echoOut = engine.execute("echo hello", ctx).toList()
        assertEquals(TerminalLineType.Output.TEXT, echoOut[0].type)

        // 2. 文件列表 ls -> Output.FILE_ENTRY
        val file = FileBean(name = "test.txt", fileId = "1", isFolder = false)
        ctx.fileCacheManager.put("0", FilesBean(fileBeanList = arrayListOf(file), cid = "0", count = 1, order = "", path = emptyList()))
        val lsOut = engine.execute("ls", ctx).toList()
        assertEquals(TerminalLineType.Output.FILE_ENTRY, lsOut[0].type)

        // 3. 详细列表 ls -l -> 第一行 "total N" (Output.TEXT)，后续文件行 (Output.LONG_LISTING)
        val lsLongOut = engine.execute("ls -l", ctx).toList()
        assertEquals(2, lsLongOut.size)
        assertEquals(TerminalLineType.Output.TEXT, lsLongOut[0].type)
        assertEquals(TerminalLineType.Output.LONG_LISTING, lsLongOut[1].type)

        // 4. 路径输出 find -> Output.PATH_ENTRY
        val findOut = engine.execute("find test.txt", ctx).toList()
        assertEquals(TerminalLineType.Output.PATH_ENTRY, findOut[0].type)

        // 5. 错误输出 -> System.ERROR
        val errOut = engine.execute("rm", ctx).toList()
        assertEquals(TerminalLineType.System.ERROR, errOut[0].type)
    }

    @Test
    fun testPipelineDiagnosticChannelIsolation() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()

        // find 针对空目录或无匹配项会 emitSystem("未找到匹配的项目")
        // 当管道连接到 wc -l 时，System.INFO 行不会进入下游，下游计数应为 0
        val outEmptyFindPipe = engine.executeStrings("find -suffix nonexistent | wc -l", ctx)
        assertEquals(listOf("0"), outEmptyFindPipe)
    }
}
