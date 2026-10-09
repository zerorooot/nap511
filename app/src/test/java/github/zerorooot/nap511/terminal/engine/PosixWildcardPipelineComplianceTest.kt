package github.zerorooot.nap511.terminal.engine

import github.zerorooot.nap511.terminal.commands.TestMockFileRepository
import github.zerorooot.nap511.terminal.commands.createMockFile
import github.zerorooot.nap511.terminal.commands.createTestContext
import github.zerorooot.nap511.terminal.commands.createTestEngine
import github.zerorooot.nap511.terminal.commands.executeStrings
import github.zerorooot.nap511.terminal.commands.putMockFiles
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * POSIX 标准通配符与管道合规性测试 (PosixWildcardPipelineComplianceTest)
 *
 * 验证依据：docs/1_TERMINAL_COMMANDS_REFACTORING_IMPLEMENTATION_PLAN.md
 * 1. POSIX 2.13.3 隐藏文件隔离：ls * 绝不能匹配 .hidden.txt；
 * 2. 掩码保护字面量：cat "*.txt" 及 cat \*.txt 严格作为字面量，不展开真实文件；
 * 3. 语法防御：ls |、| wc 与未闭合引号 cat "foo 拦截并报告 syntax error；
 * 4. 管道破裂熔断：echo -e "1\n2\n3\n4\n5" | head -n 2 消费 2 行后即刻短路，且无任何 CancellationException 红字错误；
 * 5. 参数注入防御：rm * 匹配以 '-' 开头的文件名时，严格作为位置参数传递，绝不注入为命令行选项 Flag。
 */
class PosixWildcardPipelineComplianceTest {

    @Test
    fun testLeadingDotIsolation() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        val normalFile = createMockFile("normal.txt", "101")
        val hiddenFile = createMockFile(".hidden.txt", "102")
        ctx.putMockFiles("0", listOf(normalFile, hiddenFile))

        // ls * 必须匹配 normal.txt，绝对严禁匹配 .hidden.txt
        val out = engine.executeStrings("ls *", ctx)
        assertTrue("输出应包含 normal.txt", out.contains("normal.txt"))
        assertFalse("输出绝对不能包含 .hidden.txt", out.contains(".hidden.txt"))

        // 显式 .* 模式才允许匹配隐藏文件
        val outHidden = engine.executeStrings("ls .*", ctx)
        assertTrue("显式 .* 模式应匹配 .hidden.txt", outHidden.contains(".hidden.txt"))
    }

    @Test
    fun testQuotedAndEscapedWildcardTreatedAsLiteral() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["201"] = "real file content"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = repo)

        val realFile = createMockFile("test.txt", "201", icoString = "txt")
        ctx.putMockFiles("0", listOf(realFile))

        // 1. 双引号包裹通配符: cat "*.txt" 不应展开，寻找字面量 "*.txt"（不存在报错）
        val outQuoted = engine.executeStrings("cat \"*.txt\"", ctx)
        assertTrue(outQuoted.any { it.contains("*.txt") && it.contains("No such file") })

        // 2. 单引号包裹通配符: cat '*.txt'
        val outSingleQuoted = engine.executeStrings("cat '*.txt'", ctx)
        assertTrue(outSingleQuoted.any { it.contains("*.txt") && it.contains("No such file") })

        // 3. 反斜杠转义通配符: cat \*.txt
        val outEscaped = engine.executeStrings("cat \\*.txt", ctx)
        assertTrue(outEscaped.any { it.contains("*.txt") && it.contains("No such file") })
    }

    @Test
    fun testPipelineSyntaxErrorsIntercepted() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 1. 悬挂尾部管道符: ls |
        val out1 = engine.executeStrings("ls |", ctx)
        assertTrue(out1.any { it.contains("syntax error") && it.contains("|") })

        // 2. 前置管道符: | wc
        val out2 = engine.executeStrings("| wc", ctx)
        assertTrue(out2.any { it.contains("syntax error") && it.contains("|") })

        // 3. 连续空管道符: echo a || cat
        val out3 = engine.executeStrings("echo a || cat", ctx)
        assertTrue(out3.any { it.contains("syntax error") && it.contains("|") })

        // 4. 未闭合双引号: cat "foo
        val out4 = engine.executeStrings("cat \"foo", ctx)
        assertTrue(out4.any { it.contains("syntax error") && it.contains("matching") })

        // 5. 未闭合单引号: echo 'bar
        val out5 = engine.executeStrings("echo 'bar", ctx)
        assertTrue(out5.any { it.contains("syntax error") && it.contains("matching") })
    }

    @Test
    fun testBrokenPipeCancellationSafety() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 管道熔断：head -n 2 仅消费前 2 行后关闭下游通道
        // 上游应当干净利落地停止发送，且没有任何包装为异常的红字输出
        val out = engine.executeStrings("echo '1\n2\n3\n4\n5' | head -n 2", ctx)
        assertEquals(listOf("1", "2"), out)
        assertFalse(out.any { it.contains("CancellationException") || it.contains("error:") })
    }

    @Test
    fun testWildcardExpansionPreventsOptionInjection() = runBlocking {
        var confirmPrompt = ""
        val repo = TestMockFileRepository()
        val engine = createTestEngine()
        val ctx = createTestContext(
            fileRepository = repo,
            onConfirmRequest = { prompt ->
                confirmPrompt = prompt
                true
            }
        )

        // 构造以 - 开头的文件名（如 -f）
        val dashFile = createMockFile("-f", "301")
        ctx.putMockFiles("0", listOf(dashFile))

        // 执行 rm *。在通配展开前 AST 选项已经固定。
        // * 展开出 "-f"，但作为位置参数传递，绝不能被当作 -f (强制删除) 标志！
        // 因此必须触发二次确认
        engine.executeStrings("rm *", ctx)
        assertTrue("必须弹出二次确认，证明 -f 未被注入为强制免确认 Flag", confirmPrompt.isNotEmpty())
        assertTrue(repo.deletedItems.any { it.second == "301" })
    }
}
