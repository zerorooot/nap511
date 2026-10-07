package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.terminal.commands.stream.EchoCommand
import github.zerorooot.nap511.terminal.commands.util.CommandFormatUtil
import github.zerorooot.nap511.terminal.commands.util.SizeParser
import github.zerorooot.nap511.terminal.engine.Lexer
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
    private fun quotedToken(text: String): Token = Token(text, isQuoted = true)

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
     * 测试 CommandAstParser 对引号包裹的参数与单独 "-"（标准输入）的安全隔离机制
     */
    @Test
    fun testCommandAstParserQuotedTokensAndSingleHyphen() {
        // 1. 引号包裹的参数即使以 "-" 或 "--" 开头，也必须严格视为位置参数，绝不能识别为 Flag 或 Option
        val ast1 = CommandAstParser.parse(
            "rm",
            listOf(
                quotedToken("-rf"),
                quotedToken("-l"),
                quotedToken("--output"),
                quotedToken("-n"),
                Token("target.txt", isQuoted = false)
            ),
            allowedValueOptions = setOf("-n")
        )
        assertFalse(ast1.hasFlag("-rf"))
        assertFalse(ast1.hasFlag("-r"))
        assertFalse(ast1.hasFlag("-f"))
        assertFalse(ast1.hasFlag("-l"))
        assertFalse(ast1.hasFlag("--output"))
        assertNull(ast1.getOption("-n"))
        assertEquals(listOf("-rf", "-l", "--output", "-n", "target.txt"), ast1.rawPositionalValues)
        assertTrue(ast1.positionalArgs[0].isQuoted)
        assertTrue(ast1.positionalArgs[1].isQuoted)
        assertTrue(ast1.positionalArgs[2].isQuoted)
        assertTrue(ast1.positionalArgs[3].isQuoted)
        assertFalse(ast1.positionalArgs[4].isQuoted)

        // 2. 单独的 "-" 常在 Unix 命令行代表 stdin（如 cat -），必须作为位置参数而非 Flag
        val ast2 = CommandAstParser.parse("cat", tokensOf("-"))
        assertTrue(ast2.flags.isEmpty())
        assertEquals(listOf("-"), ast2.rawPositionalValues)
        assertEquals("-", ast2.firstPositional)

        // 3. 空参数列表
        val emptyAst = CommandAstParser.parse("empty", emptyList())
        assertTrue(emptyAst.flags.isEmpty())
        assertTrue(emptyAst.options.isEmpty())
        assertTrue(emptyAst.positionalArgs.isEmpty())
        assertNull(emptyAst.firstPositional)
        assertFalse(emptyAst.hasDelimiter)
    }

    /**
     * 测试 CommandAstParser 复合短开关展开与多重 Flag 识别
     */
    @Test
    fun testCommandAstParserCombinedFlagsExpansion() {
        // 1. 双开关复合：-rf 自动分解为 -r, -f，并保留 -rf 自身
        val ast1 = CommandAstParser.parse("rm", tokensOf("-rf", "dir"))
        assertTrue(ast1.hasFlag("-r"))
        assertTrue(ast1.hasFlag("-f"))
        assertTrue(ast1.hasFlag("-rf"))
        assertFalse(ast1.hasFlag("-d"))
        assertEquals(listOf("dir"), ast1.rawPositionalValues)

        // 2. 三重开关复合：-xvf 自动分解为 -x, -v, -f，并保留 -xvf
        val ast2 = CommandAstParser.parse("tar", tokensOf("-xvf", "archive.tar.gz"))
        assertTrue(ast2.hasFlag("-x"))
        assertTrue(ast2.hasFlag("-v"))
        assertTrue(ast2.hasFlag("-f"))
        assertTrue(ast2.hasFlag("-xvf"))

        // 3. 多个复合开关共存：-la -rf
        val ast3 = CommandAstParser.parse("test", tokensOf("-la", "-rf"))
        assertTrue(ast3.hasFlag("-l"))
        assertTrue(ast3.hasFlag("-a"))
        assertTrue(ast3.hasFlag("-r"))
        assertTrue(ast3.hasFlag("-f"))
        assertTrue(ast3.hasFlag("-la"))
        assertTrue(ast3.hasFlag("-rf"))
    }

    /**
     * 测试 CommandAstParser 键值选项与长选项等号语法
     */
    @Test
    fun testCommandAstParserKeyValueAndEqualSignOptions() {
        // 1. 紧贴式短选项：-n50, -I{}
        val ast1 = CommandAstParser.parse(
            "xargs",
            tokensOf("-n50", "-I{}", "echo"),
            allowedValueOptions = setOf("-n", "-I")
        )
        assertEquals("50", ast1.getOption("-n"))
        assertEquals(50, ast1.getIntOption("-n"))
        assertEquals("{}", ast1.getOption("-I"))
        assertEquals(listOf("echo"), ast1.rawPositionalValues)

        // 2. 长选项等号赋值：--format=json, --prefix=/opt/data
        val ast2 = CommandAstParser.parse(
            "export",
            tokensOf("--format=json", "--prefix=/opt/data")
        )
        assertEquals("json", ast2.getOption("--format"))
        assertEquals("/opt/data", ast2.getOption("--prefix"))

        // 3. 长选项等号后包含等号字符：--filter=key=val
        val ast3 = CommandAstParser.parse(
            "query",
            tokensOf("--filter=key=val")
        )
        assertEquals("key=val", ast3.getOption("--filter"))

        // 4. 长选项等号后为空：--empty=
        val ast4 = CommandAstParser.parse(
            "test",
            tokensOf("--empty=")
        )
        assertEquals("", ast4.getOption("--empty"))

        // 5. 带值长选项分离式支持：--suffix apk
        val ast5 = CommandAstParser.parse(
            "find",
            tokensOf("--suffix", "apk"),
            allowedValueOptions = setOf("--suffix")
        )
        assertEquals("apk", ast5.getOption("--suffix"))

        // 6. 带值长选项处于末尾且无后续参数时降级为 Flag，不发生数组越界
        val ast6 = CommandAstParser.parse(
            "find",
            tokensOf("--suffix"),
            allowedValueOptions = setOf("--suffix")
        )
        assertTrue(ast6.hasFlag("--suffix"))
        assertNull(ast6.getOption("--suffix"))
    }

    /**
     * 测试 CommandInvocationAst 辅助提取方法与数值容错边界
     */
    @Test
    fun testCommandInvocationAstHelperMethods() {
        val ast = CommandAstParser.parse(
            "test",
            tokensOf("-n", "42", "-s", "invalid_number", "-a", "--verbose"),
            allowedValueOptions = setOf("-n", "-s")
        )

        // 1. getIntOption 正常解析
        assertEquals(42, ast.getIntOption("-n"))
        assertEquals(42, ast.getIntOption("-n", default = 10))

        // 2. getIntOption 解析非数字字符串时返回 default 或 null
        assertNull(ast.getIntOption("-s"))
        assertEquals(99, ast.getIntOption("-s", default = 99))

        // 3. getIntOption 不存在的选项返回 default 或 null
        assertNull(ast.getIntOption("-missing"))
        assertEquals(100, ast.getIntOption("-missing", default = 100))

        // 4. getIntOption 多别名优先命中有效选项
        assertEquals(42, ast.getIntOption("-x", "-y", "-n"))

        // 5. getOption 多别名优先命中
        assertEquals("42", ast.getOption("-x", "-n"))
        assertNull(ast.getOption("-x", "-y"))

        // 6. hasFlag 与 hasAny
        assertTrue(ast.hasFlag("-a"))
        assertTrue(ast.hasFlag("--verbose"))
        assertFalse(ast.hasFlag("-b"))
        assertTrue(ast.hasAny("-b", "-c", "-a"))
        assertFalse(ast.hasAny("-b", "-c"))
    }

    /**
     * 测试 Lexer 词法分词器在引号、转义与边界字符下的解析
     */
    @Test
    fun testLexerTokenizeQuoteAndEscapeEdgeCases() {
        // 1. 空参数解析："" 与 '' 应该产出 text="" 且 isQuoted=true 的 Token
        val emptyTokens = Lexer.tokenizeWithQuoteInfo("echo \"\" ''")
        assertEquals(3, emptyTokens.size)
        assertEquals("echo", emptyTokens[0].text)
        assertFalse(emptyTokens[0].isQuoted)
        assertEquals("", emptyTokens[1].text)
        assertTrue(emptyTokens[1].isQuoted)
        assertEquals("", emptyTokens[2].text)
        assertTrue(emptyTokens[2].isQuoted)

        // 2. 空格反斜杠转义：dir\ name
        val escapedSpaceTokens = Lexer.tokenizeWithQuoteInfo("ls dir\\ name")
        assertEquals(2, escapedSpaceTokens.size)
        assertEquals("dir name", escapedSpaceTokens[1].text)
        assertFalse(escapedSpaceTokens[1].isQuoted)

        // 3. 双引号内嵌单引号与单引号内嵌双引号
        val nestedQuotes = Lexer.tokenizeWithQuoteInfo("echo \"it's fine\" 'say \"hello\"'")
        assertEquals(3, nestedQuotes.size)
        assertEquals("it's fine", nestedQuotes[1].text)
        assertTrue(nestedQuotes[1].isQuoted)
        assertEquals("say \"hello\"", nestedQuotes[2].text)
        assertTrue(nestedQuotes[2].isQuoted)

        // 4. 转义双引号：\"
        val escapedQuoteTokens = Lexer.tokenizeWithQuoteInfo("echo \"foo\\\"bar\"")
        assertEquals(2, escapedQuoteTokens.size)
        assertEquals("foo\"bar", escapedQuoteTokens[1].text)
        assertTrue(escapedQuoteTokens[1].isQuoted)

        // 5. 未闭合引号容错：到字符串末尾自动闭合
        val unclosedTokens = Lexer.tokenizeWithQuoteInfo("echo \"unclosed text")
        assertEquals(2, unclosedTokens.size)
        assertEquals("unclosed text", unclosedTokens[1].text)
        assertTrue(unclosedTokens[1].isQuoted)

        // 6. 末尾未消费反斜杠保留
        val trailingSlash = Lexer.tokenizeWithQuoteInfo("cmd\\")
        assertEquals(1, trailingSlash.size)
        assertEquals("cmd\\", trailingSlash[0].text)

        // 7. tokenize 纯文本转换验证
        val pureStrings = Lexer.tokenize("git commit -m 'initial refactor'")
        assertEquals(listOf("git", "commit", "-m", "initial refactor"), pureStrings)
    }

    /**
     * 测试 Lexer 管道符切分中的边界隔离
     */
    @Test
    fun testLexerPipelineSplittingEdgeCases() {
        // 1. 引号内部包含管道符：绝不能误切分管道阶段
        val stages1 = Lexer.parsePipeline("echo \"hello | world\" | grep hello")
        assertEquals(2, stages1.size)
        assertEquals("echo", stages1[0].command)
        assertEquals(listOf("hello | world"), stages1[0].args)
        assertEquals(listOf(Token("hello | world", isQuoted = true)), stages1[0].tokens)
        assertEquals("grep", stages1[1].command)
        assertEquals(listOf("hello"), stages1[1].args)

        // 2. 转义管道符：\| 视为普通参数
        val stages2 = Lexer.parsePipeline("echo a \\| b")
        assertEquals(1, stages2.size)
        assertEquals("echo", stages2[0].command)
        assertEquals(listOf("a", "|", "b"), stages2[0].args)

        // 3. 多阶段管道串联：4 级流水线
        val stages3 = Lexer.parsePipeline("cat file.txt | grep error | sort -r | head -n 5")
        assertEquals(4, stages3.size)
        assertEquals("cat", stages3[0].command)
        assertEquals("grep", stages3[1].command)
        assertEquals("sort", stages3[2].command)
        assertEquals("head", stages3[3].command)

        // 4. 冗余管道符与空白行容错过滤
        val stages4 = Lexer.parsePipeline("  |  ls -l  |   | grep txt |  ")
        assertEquals(2, stages4.size)
        assertEquals("ls", stages4[0].command)
        assertEquals("grep", stages4[1].command)

        // 5. 空串或纯空白输入
        assertTrue(Lexer.parsePipeline("").isEmpty())
        assertTrue(Lexer.parsePipeline("   \t  \n  ").isEmpty())
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

        // 精确匹配模式 (500b, 500c, 纯数字)
        val equal500 = SizeParser.parse("500b")
        assertNotNull(equal500)
        assertEquals('=', equal500!!.operator)
        assertEquals(500L, equal500.targetBytes)
        assertTrue(SizeParser.matches(500L, equal500))

        val equal500c = SizeParser.parse("500c")
        assertNotNull(equal500c)
        assertEquals(500L, equal500c!!.targetBytes)

        val bareNumber = SizeParser.parse("1024")
        assertNotNull(bareNumber)
        assertEquals('=', bareNumber!!.operator)
        assertEquals(1024L, bareNumber.targetBytes)

        // 单位大小写支持与 G 级单位
        val upper1G = SizeParser.parse("+1G")
        assertNotNull(upper1G)
        assertEquals(1024L * 1024L * 1024L, upper1G!!.targetBytes)

        val upperK = SizeParser.parse("-50K")
        assertNotNull(upperK)
        assertEquals(50L * 1024L, upperK!!.targetBytes)

        // 临界边界值比较（严格大于 / 严格小于 / 精确等于）
        val strictlyPlus = SizeParser.parse("+100")!!
        assertFalse(SizeParser.matches(100L, strictlyPlus))
        assertTrue(SizeParser.matches(101L, strictlyPlus))
        assertFalse(SizeParser.matches(99L, strictlyPlus))

        val strictlyMinus = SizeParser.parse("-100")!!
        assertFalse(SizeParser.matches(100L, strictlyMinus))
        assertFalse(SizeParser.matches(101L, strictlyMinus))
        assertTrue(SizeParser.matches(99L, strictlyMinus))

        val strictlyEqual = SizeParser.parse("100")!!
        assertEquals('=', strictlyEqual.operator)
        assertEquals(100L, strictlyEqual.targetBytes)
        assertTrue(SizeParser.matches(100L, strictlyEqual))
        assertFalse(SizeParser.matches(101L, strictlyEqual))
        assertFalse(SizeParser.matches(99L, strictlyEqual))

        // 非法输入解析
        assertNull(SizeParser.parse(""))
        assertNull(SizeParser.parse("   "))
        assertNull(SizeParser.parse("="))
        assertNull(SizeParser.parse("=100"))
        assertNull(SizeParser.parse("+"))
        assertNull(SizeParser.parse("-"))
        assertNull(SizeParser.parse("+k"))
        assertNull(SizeParser.parse("-M"))
        assertNull(SizeParser.parse("100x"))
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
