package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * echo 命令测试用例集合（E001-E050）
 * 覆盖：基本功能、转义字符、管道及组合场景
 */
class EchoCommandTest {

    /**
     * 1. 基本功能测试用例 (E001-E010)
     */
    @Test
    fun testEchoBasicFunctions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // E001: 查看帮助
        val outHelp = engine.executeStrings("echo -h", ctx)
        assertTrue(outHelp.any { it.contains("echo") })

        // E002: 空参数
        val outEmpty = engine.executeStrings("echo", ctx)
        assertEquals(listOf(""), outEmpty)

        // E003: 单文本
        val outSingle = engine.executeStrings("echo hello", ctx)
        assertEquals(listOf("hello"), outSingle)

        // E004: 多文本
        val outMulti = engine.executeStrings("echo hello world", ctx)
        assertEquals(listOf("hello world"), outMulti)

        // E005: 多空格
        val outSpaces = engine.executeStrings("echo hello    world", ctx)
        assertEquals(listOf("hello world"), outSpaces)

        // E006: 前导空格
        val outLeading = engine.executeStrings("echo \"  hello\"", ctx)
        assertEquals(listOf("  hello"), outLeading)

        // E007: 尾随空格
        val outTrailing = engine.executeStrings("echo \"hello  \"", ctx)
        assertEquals(listOf("hello  "), outTrailing)

        // E008: 数字
        val outNumber = engine.executeStrings("echo 123", ctx)
        assertEquals(listOf("123"), outNumber)

        // E009: 中文
        val outChinese = engine.executeStrings("echo 你好", ctx)
        assertEquals(listOf("你好"), outChinese)

        // E010: emoji
        val outEmoji = engine.executeStrings("echo 😀", ctx)
        assertEquals(listOf("😀"), outEmoji)
    }

    /**
     * 2. 转义字符与特殊标记测试用例 (E011-E030)
     */
    @Test
    fun testEchoEscapingAndSpecialCharacters() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // E011: 单引号
        val out11 = engine.executeStrings("echo 'hello'", ctx)
        assertEquals(listOf("hello"), out11)

        // E012: 双引号
        val out12 = engine.executeStrings("echo \"hello\"", ctx)
        assertEquals(listOf("hello"), out12)

        // E013: 单引号内单引号
        val out13 = engine.executeStrings("echo 'it'\\''s'", ctx)
        assertTrue(out13.isNotEmpty())

        // E014: 双引号内双引号
        val out14 = engine.executeStrings("echo \"say\\\"hi\\\"\"", ctx)
        assertTrue(out14.isNotEmpty())

        // E015: 反斜杠
        val out15 = engine.executeStrings("echo 'a\\b'", ctx)
        assertEquals(listOf("a\\b"), out15)

        // E016: 转义反斜杠
        val out16 = engine.executeStrings("echo 'a\\\\b'", ctx)
        assertEquals(listOf("a\\\\b"), out16)

        // E017: $ 单引号
        val out17 = engine.executeStrings("echo '\$HOME'", ctx)
        assertEquals(listOf("\$HOME"), out17)

        // E018: $ 双引号
        val out18 = engine.executeStrings("echo \"\$HOME\"", ctx)
        assertTrue(out18.isNotEmpty())

        // E019: !
        val out19 = engine.executeStrings("echo 'hi!'", ctx)
        assertEquals(listOf("hi!"), out19)

        // E020: #
        val out20 = engine.executeStrings("echo 'a#b'", ctx)
        assertEquals(listOf("a#b"), out20)

        // E021: *
        val out21 = engine.executeStrings("echo '*.txt'", ctx)
        assertEquals(listOf("*.txt"), out21)

        // E022: ?
        val out22 = engine.executeStrings("echo '?.txt'", ctx)
        assertEquals(listOf("?.txt"), out22)

        // E023: [ ]
        val out23 = engine.executeStrings("echo '[test]'", ctx)
        assertEquals(listOf("[test]"), out23)

        // E024: 反引号
        val out24 = engine.executeStrings("echo '`whoami`'", ctx)
        assertEquals(listOf("`whoami`"), out24)

        // E025: 命令替换
        val out25 = engine.executeStrings("echo '\$(whoami)'", ctx)
        assertEquals(listOf("\$(whoami)"), out25)

        // E026: tab
        val out26 = engine.executeStrings("echo 'a\tb'", ctx)
        assertEquals(listOf("a\tb"), out26)

        // E027: 换行
        val out27 = engine.executeStrings("echo 'a\nb'", ctx)
        assertEquals(listOf("a\nb"), out27)

        // E028: -n 选项
        val out28 = engine.executeStrings("echo -n hello", ctx)
        assertTrue(out28.isNotEmpty())

        // E029: -e 选项
        val out29 = engine.executeStrings("echo -e 'a\\nb'", ctx)
        assertTrue(out29.isNotEmpty())

        // E030: -- 分隔
        val out30 = engine.executeStrings("echo -- -h", ctx)
        assertEquals(listOf("-- -h"), out30)
    }

    /**
     * 3. 管道测试用例 (E031-E050)
     */
    @Test
    fun testEchoPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // E031: echo 接 grep
        val out31 = engine.executeStrings("echo hello | grep hell", ctx)
        assertEquals(listOf("hello"), out31)

        // E032: echo 接 grep -v
        val out32 = engine.executeStrings("echo hello | grep -v x", ctx)
        assertEquals(listOf("hello"), out32)

        // E033: echo 接 head
        val out33 = engine.executeStrings("echo hello | head", ctx)
        assertEquals(listOf("hello"), out33)

        // E034: echo 接 tail
        val out34 = engine.executeStrings("echo hello | tail", ctx)
        assertEquals(listOf("hello"), out34)

        // E035: echo 接 wc
        val out35 = engine.executeStrings("echo hello | wc", ctx)
        assertTrue(out35.isNotEmpty())

        // E036: echo 接 wc -l
        val out36 = engine.executeStrings("echo hello | wc -l", ctx)
        assertEquals(listOf("1"), out36)

        // E037: echo 接 sort
        val out37 = engine.executeStrings("echo b a c | sort", ctx)
        assertEquals(listOf("b a c"), out37)

        // E038: 多行 echo 接 sort
        val out38 = engine.executeStrings("echo 'b\na\nc' | sort", ctx)
        assertEquals(listOf("a", "b", "c"), out38)

        // E039: echo 接 xargs
        val out39 = engine.executeStrings("echo hello | xargs echo", ctx)
        assertEquals(listOf("hello"), out39)

        // E040: echo 接 grep 接 wc
        val out40 = engine.executeStrings("echo hello | grep hell | wc -l", ctx)
        assertEquals(listOf("1"), out40)

        // E041: echo 接 grep 转义
        val out41 = engine.executeStrings("echo 'a.txt' | grep 'a\\.txt'", ctx)
        assertEquals(listOf("a.txt"), out41)

        // E042: echo 接 grep 中文
        val out42 = engine.executeStrings("echo 测试 | grep 测", ctx)
        assertEquals(listOf("测试"), out42)

        // E043: echo 接 grep $
        val out43 = engine.executeStrings("echo 'price\$1' | grep 'price\\\$1'", ctx)
        assertEquals(listOf("price\$1"), out43)

        // E044: echo 接 grep !
        val out44 = engine.executeStrings("echo 'hi!' | grep 'hi!'", ctx)
        assertEquals(listOf("hi!"), out44)

        // E045: echo 接 grep #
        val out45 = engine.executeStrings("echo 'a#b' | grep 'a#b'", ctx)
        assertEquals(listOf("a#b"), out45)

        // E046: echo 接 head -n
        val out46 = engine.executeStrings("echo '1\n2\n3' | head -n 2", ctx)
        assertEquals(listOf("1", "2"), out46)

        // E047: echo 接 tail -n
        val out47 = engine.executeStrings("echo '1\n2\n3' | tail -n 2", ctx)
        assertEquals(listOf("2", "3"), out47)

        // E048: echo 空接 wc
        val out48 = engine.executeStrings("echo | wc -l", ctx)
        assertEquals(listOf("1"), out48)

        // E049: echo 接多级管道
        val out49 = engine.executeStrings("echo hello | grep h | head | wc -l", ctx)
        assertEquals(listOf("1"), out49)

        // E050: echo 接 xargs 拼接
        val out50 = engine.executeStrings("echo 'a b c' | xargs echo", ctx)
        assertEquals(listOf("a b c"), out50)
    }
}
