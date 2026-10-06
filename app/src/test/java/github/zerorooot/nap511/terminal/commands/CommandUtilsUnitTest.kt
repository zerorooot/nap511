package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.commands.stream.EchoCommand
import github.zerorooot.nap511.terminal.commands.util.CommandFormatUtil
import github.zerorooot.nap511.terminal.commands.util.SizeParser
import github.zerorooot.nap511.terminal.engine.Token
import github.zerorooot.nap511.terminal.engine.ast.CommandAstParser
import github.zerorooot.nap511.terminal.viewmodel.TerminalLineType
import github.zerorooot.nap511.terminal.viewmodel.TerminalOutput
import github.zerorooot.nap511.terminal.viewmodel.emitAnsi
import github.zerorooot.nap511.terminal.viewmodel.emitError
import github.zerorooot.nap511.terminal.viewmodel.emitFile
import github.zerorooot.nap511.terminal.viewmodel.emitFindCategory
import github.zerorooot.nap511.terminal.viewmodel.emitHelp
import github.zerorooot.nap511.terminal.viewmodel.emitLongListing
import github.zerorooot.nap511.terminal.viewmodel.emitPath
import github.zerorooot.nap511.terminal.viewmodel.emitSystem
import github.zerorooot.nap511.terminal.viewmodel.emitText
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

/**
 * 终端命令工具类及数据载体底层单元测试
 *
 * 覆盖：
 * 1. CommandAstParser 与 CommandInvocationAst 抽象语法树解析与提取器
 * 2. SizeParser 文件大小解析器（支持 +100M, -10k, 500b 及匹配比较）
 * 3. CommandFormatUtil 时间戳格式化工具
 * 4. 纯 AST 命令直接执行（不依赖 Engine）
 * 5. TerminalOutput 数据载体与 DSL 发射函数集
 */
class CommandUtilsUnitTest {

    private fun tokensOf(vararg texts: String): List<Token> = texts.map { Token(it) }

    /**
     * 测试 CommandAstParser 参数与选项提取解析器
     */
    @Test
    fun testCommandAstParserFlagAndOptions() {
        val ast = CommandAstParser.parse(
            "test",
            tokensOf("-l", "-a", "-n", "20", "-I{}", "--refresh", "file1.txt", "file2.txt"),
            allowedValueOptions = setOf("-n", "-I")
        )

        // 判断 Flag 存在性
        assertTrue(ast.hasFlag("-l"))
        assertTrue(ast.hasFlag("-a"))
        assertFalse(ast.hasFlag("-v"))
        assertTrue(ast.hasAny("-v", "-l"))
        assertTrue(ast.hasFlag("--refresh"))

        // 提取带值选项与数值转换
        assertEquals("20", ast.getOption("-n"))
        assertEquals(20, ast.getIntOption("-n"))
        assertEquals(10, ast.getIntOption("-x", default = 10))

        // 提取紧贴选项（如 -I{}）
        assertEquals("{}", ast.getOption("-I"))

        // 提取纯位置参数
        val fileAst = CommandAstParser.parse("ls", tokensOf("-l", "file1.txt", "file2.txt"))
        assertEquals(listOf("file1.txt", "file2.txt"), fileAst.rawPositionalValues)
        assertEquals("file1.txt", fileAst.firstPositional)
        assertEquals("file1.txt file2.txt", fileAst.rawPositionalValues.joinToString(" "))
    }

    /**
     * 测试 CommandAstParser 对 "--"（选项结束符）的标准 POSIX 支持
     */
    @Test
    fun testCommandAstParserEndOptionsDelimiter() {
        // 1. 基本 "--" 分隔：之前是选项，之后即使以 "-" 开头也是位置参数
        val ast1 = CommandAstParser.parse(
            "test",
            tokensOf("-l", "-a", "--", "-f", "-n", "20", "file.txt"),
            allowedValueOptions = setOf("-n")
        )
        assertTrue(ast1.hasDelimiter)
        assertTrue(ast1.hasFlag("-l"))
        assertTrue(ast1.hasFlag("-a"))
        // "--" 之后的 -f 不应被识别为 Flag，-n 不应被识别为 Option
        assertFalse(ast1.hasFlag("-f"))
        assertFalse(ast1.hasAny("-f", "-rf"))
        assertNull(ast1.getOption("-n"))
        // 选项结束符之后的所有参数均作为位置参数保留，"--" 本身被剔除
        assertEquals(listOf("-f", "-n", "20", "file.txt"), ast1.rawPositionalValues)
        assertEquals("-f", ast1.firstPositional)
        assertEquals("-f -n 20 file.txt", ast1.rawPositionalValues.joinToString(" "))

        // 2. "--" 之前包含位置参数，"--" 之后也包含位置参数
        val ast2 = CommandAstParser.parse("mv", tokensOf("src", "--", "-dest"))
        assertTrue(ast2.hasDelimiter)
        assertEquals(listOf("src", "-dest"), ast2.rawPositionalValues)
        assertEquals("src", ast2.firstPositional)

        // 3. 仅有 "--" 选项结束符
        val ast3 = CommandAstParser.parse("test", tokensOf("--"))
        assertTrue(ast3.hasDelimiter)
        assertTrue(ast3.positionalArgs.isEmpty())
        assertNull(ast3.firstPositional)

        // 4. 重复 "--"：第一个作为选项结束符，后续 "--" 作为位置参数
        val ast4 = CommandAstParser.parse("test", tokensOf("-l", "--", "--", "-a"))
        assertTrue(ast4.hasFlag("-l"))
        assertFalse(ast4.hasFlag("-a"))
        assertEquals(listOf("--", "-a"), ast4.rawPositionalValues)

        // 5. 不包含 "--" 时的正常解析
        val ast5 = CommandAstParser.parse("ls", tokensOf("-l", "file.txt"))
        assertFalse(ast5.hasDelimiter)
        assertTrue(ast5.hasFlag("-l"))
        assertEquals(listOf("file.txt"), ast5.rawPositionalValues)
    }

    /**
     * 测试 SizeParser 大小解析器及尺寸匹配
     */
    @Test
    fun testSizeParser() {
        // 大于模式 (+100M)
        val plus100M = SizeParser.parse("+100M")
        assertNotNull(plus100M)
        assertEquals('+', plus100M!!.operator)
        assertEquals(100L * 1024L * 1024L, plus100M.targetBytes)
        assertTrue(SizeParser.matches(105L * 1024L * 1024L, plus100M))
        assertFalse(SizeParser.matches(90L * 1024L * 1024L, plus100M))

        // 小于模式 (-10k)
        val minus10k = SizeParser.parse("-10k")
        assertNotNull(minus10k)
        assertEquals('-', minus10k!!.operator)
        assertEquals(10L * 1024L, minus10k.targetBytes)
        assertTrue(SizeParser.matches(1024L, minus10k))
        assertFalse(SizeParser.matches(20 * 1024L, minus10k))

        // 精确匹配模式 (500b)
        val equal500 = SizeParser.parse("500b")
        assertNotNull(equal500)
        assertEquals('=', equal500!!.operator)
        assertEquals(500L, equal500.targetBytes)
        assertTrue(SizeParser.matches(500L, equal500))

        // 非法输入解析
        assertNull(SizeParser.parse(""))
        assertNull(SizeParser.parse("invalid"))
    }

    /**
     * 测试 CommandFormatUtil 时间戳格式化工具
     */
    @Test
    fun testCommandFormatUtilTimestamp() {
        // 秒级时间戳格式化
        val secondTimestamp = "1672531199" // 2022-12-31 23:59:59 UTC
        val formatted = CommandFormatUtil.formatTimestamp(secondTimestamp, "yyyy-MM", Locale.US)
        assertFalse(formatted.isEmpty())
        assertTrue(formatted.startsWith("20"))

        // 非数字原样返回
        assertEquals("not_a_time", CommandFormatUtil.formatTimestamp("not_a_time"))
    }

    /**
     * 测试独立命令低耦合直接执行（纯 AST 调用，无需 PipelineEngine 与 CommandRegistry）
     */
    @Test
    fun testDirectCommandExecutionWithoutEngine() = runBlocking {
        val echoCmd = EchoCommand()
        val ctx = createTestContext()
        val ast = CommandAstParser.parse(
            echoCmd.name,
            tokensOf("hello", "terminal", "refactor")
        )
        val result = echoCmd.execute(ctx, ast, emptyFlow()).toList()
        assertEquals(listOf("hello terminal refactor"), result.map { it.text })
        assertEquals(TerminalLineType.Output.TEXT, result[0].type)
    }

    /**
     * 测试 TerminalOutput 数据载体与 FlowCollector DSL 扩展发射函数
     */
    @Test
    fun testTerminalOutputDataCarrierAndDslEmitters() = runBlocking {
        // TerminalOutput 属性与 CharSequence 接口委托
        val output = TerminalOutput("hello world", TerminalLineType.Output.FILE_ENTRY)
        assertEquals("hello world", output.text)
        assertEquals(TerminalLineType.Output.FILE_ENTRY, output.type)
        assertEquals(11, output.length)
        assertEquals('h', output[0])
        assertEquals("hello", output.subSequence(0, 5))
        assertEquals("hello world", output.toString())

        // 值相等性与哈希校验
        val same = TerminalOutput("hello world", TerminalLineType.Output.FILE_ENTRY)
        val diffType = TerminalOutput("hello world", TerminalLineType.Output.TEXT)
        assertEquals(output, same)
        assertFalse(output == diffType)
        assertEquals(output.hashCode(), same.hashCode())

        // FlowCollector DSL 扩展发射函数发射测试
        val emittedList = flow {
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
