package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.commands.stream.EchoCommand
import github.zerorooot.nap511.terminal.commands.util.CommandArgs
import github.zerorooot.nap511.terminal.commands.util.CommandFormatUtil
import github.zerorooot.nap511.terminal.commands.util.SizeParser
import github.zerorooot.nap511.terminal.context.TerminalContext
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale
import github.zerorooot.nap511.terminal.viewmodel.*

/**
 * 终端命令高复用工具类及独立命令单元测试
 */
class CommandUtilsUnitTest {

    @Test
    fun testCommandArgsFlagAndOptions() {
        val args = CommandArgs(listOf("-l", "-a", "-n", "20", "-I{}", "--refresh", "file1.txt", "file2.txt"))

        // 测试 Flag 判断
        assertTrue(args.hasFlag("-l"))
        assertTrue(args.hasFlag("-a"))
        assertFalse(args.hasFlag("-v"))
        assertTrue(args.hasAny("-v", "-l"))

        // 测试带值选项提取
        assertEquals("20", args.getOption("-n"))
        assertEquals(20, args.getIntOption("-n"))
        assertEquals(10, args.getIntOption("-x", default = 10))

        // 测试紧贴选项提取（如 -I{}）
        assertEquals("{}", args.getOption("-I"))

        // 测试纯位置参数提取
        val fileArgs = CommandArgs(listOf("-l", "file1.txt", "file2.txt"))
        assertEquals(listOf("file1.txt", "file2.txt"), fileArgs.positionalArgs)
        assertEquals("file1.txt", fileArgs.firstPositional)
        assertEquals("file1.txt file2.txt", fileArgs.joinPositional())
    }

    @Test
    fun testSizeParser() {
        // 测试单位换算与操作符解析
        val plus100M = SizeParser.parse("+100M")
        assertNotNull(plus100M)
        assertEquals('+', plus100M!!.operator)
        assertEquals(100L * 1024L * 1024L, plus100M.targetBytes)
        assertTrue(SizeParser.matches(105L * 1024L * 1024L, plus100M))
        assertFalse(SizeParser.matches(90L * 1024L * 1024L, plus100M))

        val minus10k = SizeParser.parse("-10k")
        assertNotNull(minus10k)
        assertEquals('-', minus10k!!.operator)
        assertEquals(10L * 1024L, minus10k.targetBytes)
        assertTrue(SizeParser.matches(1024L, minus10k))
        assertFalse(SizeParser.matches(20 * 1024L, minus10k))

        val equal500 = SizeParser.parse("500b")
        assertNotNull(equal500)
        assertEquals('=', equal500!!.operator)
        assertEquals(500L, equal500.targetBytes)
        assertTrue(SizeParser.matches(500L, equal500))

        // 非法格式返回 null
        assertNull(SizeParser.parse(""))
        assertNull(SizeParser.parse("invalid"))
    }

    @Test
    fun testCommandFormatUtilTimestamp() {
        // 测试秒级与毫秒级时间戳格式化
        val secondTimestamp = "1672531199" // 2022-12-31 23:59:59 UTC
        val formatted = CommandFormatUtil.formatTimestamp(secondTimestamp, "yyyy-MM", Locale.US)
        assertFalse(formatted.isEmpty())
        assertTrue(formatted.startsWith("20"))

        // 非数字原样返回
        assertEquals("not_a_time", CommandFormatUtil.formatTimestamp("not_a_time"))
    }

    @Test
    fun testDirectCommandExecutionWithoutEngine() = runBlocking {
        // 验证低耦合：直接实例化具体命令进行测试，无需借助 PipelineEngine 或 CommandRegistry
        val echoCmd = EchoCommand()
        val ctx = TerminalContext()
        val result = echoCmd.execute(ctx, listOf("hello", "terminal", "refactor"), emptyFlow()).toList()
        assertEquals(listOf("hello terminal refactor"), result.map { it.text })
        assertEquals(TerminalLineType.Output.TEXT, result[0].type)
    }

    @Test
    fun testTerminalOutputDataCarrierAndDslEmitters() = runBlocking {
        // 1. 验证 TerminalOutput 属性与 CharSequence 委托
        val output = TerminalOutput("hello world", TerminalLineType.Output.FILE_ENTRY)
        assertEquals("hello world", output.text)
        assertEquals(TerminalLineType.Output.FILE_ENTRY, output.type)
        assertEquals(11, output.length)
        assertEquals('h', output[0])
        assertEquals("hello", output.subSequence(0, 5))
        assertEquals("hello world", output.toString())

        // 2. 验证 equals 与 hashCode
        val same = TerminalOutput("hello world", TerminalLineType.Output.FILE_ENTRY)
        val diffType = TerminalOutput("hello world", TerminalLineType.Output.TEXT)
        assertEquals(output, same)
        assertFalse(output == diffType)
        assertEquals(output.hashCode(), same.hashCode())

        // 3. 验证全部 FlowCollector DSL 扩展发射函数
        val emittedList = kotlinx.coroutines.flow.flow {
            emitText("text")
            emitFile("file.txt")
            emitPath("/dir/file.txt")
            emitLongListing("-rwxr-xr-x 100 2026 file.txt")
            emitFindCategory("[目录] docs (-)")
            emitAnsi("\u001B[31mError\u001B[0m")
            emitHelp("help docs")
            emitError("err msg")
            emitSystem("sys msg")
        }.toList()

        assertEquals(9, emittedList.size)
        assertEquals(TerminalLineType.Output.TEXT, emittedList[0].type)
        assertEquals("text", emittedList[0].text)

        assertEquals(TerminalLineType.Output.FILE_ENTRY, emittedList[1].type)
        assertEquals("file.txt", emittedList[1].text)

        assertEquals(TerminalLineType.Output.PATH_ENTRY, emittedList[2].type)
        assertEquals("/dir/file.txt", emittedList[2].text)

        assertEquals(TerminalLineType.Output.LONG_LISTING, emittedList[3].type)
        assertEquals(TerminalLineType.Output.FIND_CATEGORY, emittedList[4].type)
        assertEquals(TerminalLineType.Output.ANSI, emittedList[5].type)
        assertEquals(TerminalLineType.System.HELP, emittedList[6].type)
        assertEquals(TerminalLineType.System.ERROR, emittedList[7].type)
        assertEquals(TerminalLineType.System.INFO, emittedList[8].type)
    }
}
