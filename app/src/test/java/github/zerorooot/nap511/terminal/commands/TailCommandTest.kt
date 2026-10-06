package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * tail 命令测试用例集合（TA001-TA050）
 * 覆盖：基本功能、转义字符、边界与异常、管道组合
 */
class TailCommandTest {

    /**
     * 1. 基本功能测试用例 (TA001-TA018)
     */
    @Test
    fun testTailBasicFunctionsAndOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TA001: 查看帮助
        val outHelp = engine.executeStrings("tail -h", ctx)
        assertTrue(outHelp.any { it.contains("tail") })

        // TA002: 默认 10 行
        val input20 = (1..20).joinToString("\n")
        val outDefault = engine.executeStrings("echo '$input20' | tail", ctx)
        assertEquals(10, outDefault.size)
        assertEquals("11", outDefault.first())
        assertEquals("20", outDefault.last())

        // TA003: -n 3
        val outN3 = engine.executeStrings("echo '1\n2\n3\n4' | tail -n 3", ctx)
        assertEquals(listOf("2", "3", "4"), outN3)

        // TA004: -n 1
        val outN1 = engine.executeStrings("echo '1\n2' | tail -n 1", ctx)
        assertEquals(listOf("2"), outN1)

        // TA005: -n 0
        val outN0 = engine.executeStrings("echo '1\n2' | tail -n 0", ctx)
        assertTrue(outN0.isEmpty())

        // TA006: -n 负数
        val out06 = engine.executeStrings("echo '1\n2' | tail -n -1", ctx)
        assertNotNull(out06)

        // TA007: -n 非数字
        val out07 = engine.executeStrings("echo '1' | tail -n abc", ctx)
        assertNotNull(out07)

        // TA008: -n 缺参
        val out08 = engine.executeStrings("tail -n", ctx)
        assertNotNull(out08)

        // TA009: 行数大于输入
        val out09 = engine.executeStrings("echo '1\n2' | tail -n 100", ctx)
        assertEquals(listOf("1", "2"), out09)

        // TA010: 空输入
        val out10 = engine.executeStrings("echo '' | tail", ctx)
        assertNotNull(out10)

        // TA011: 单行无换行
        val out11 = engine.executeStrings("echo 'abc' | tail", ctx)
        assertEquals(listOf("abc"), out11)

        // TA012: 中文行
        val out12 = engine.executeStrings("echo '你\n好' | tail -n 1", ctx)
        assertEquals(listOf("好"), out12)

        // TA013: 转义 tab
        val out13 = engine.executeStrings("echo 'a\tb' | tail -n 1", ctx)
        assertEquals(listOf("a\tb"), out13)

        // TA014: $
        val out14 = engine.executeStrings("echo 'price\$1' | tail -n 1", ctx)
        assertEquals(listOf("price\$1"), out14)

        // TA015: !
        val out15 = engine.executeStrings("echo 'hi!' | tail -n 1", ctx)
        assertEquals(listOf("hi!"), out15)

        // TA016: #
        val out16 = engine.executeStrings("echo 'a#b' | tail -n 1", ctx)
        assertEquals(listOf("a#b"), out16)

        // TA017: 未知选项
        val out17 = engine.executeStrings("tail -x", ctx)
        assertNotNull(out17)

        // TA018: 多余参数
        val out18 = engine.executeStrings("tail -n 1 a b", ctx)
        assertNotNull(out18)
    }

    /**
     * 2. 管道测试用例 (TA019-TA025)
     */
    @Test
    fun testTailPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TA019: 管道 find
        val out19 = engine.executeStrings("find -name '*.txt' | tail", ctx)
        assertNotNull(out19)

        // TA020: 管道 ls
        val out20 = engine.executeStrings("ls | tail -n 5", ctx)
        assertNotNull(out20)

        // TA021: 管道 grep
        val out21 = engine.executeStrings("ls | grep txt | tail -n 3", ctx)
        assertNotNull(out21)

        // TA022: 接 wc
        val out22 = engine.executeStrings("echo '1\n2' | tail -n 1 | wc -l", ctx)
        assertEquals(listOf("1"), out22)

        // TA023: 接 sort
        val out23 = engine.executeStrings("echo 'b\na' | tail -n 1 | sort", ctx)
        assertEquals(listOf("a"), out23)

        // TA024: 接 xargs
        val out24 = engine.executeStrings("echo 'a\nb' | tail -n 1 | xargs echo", ctx)
        assertEquals(listOf("b"), out24)

        // TA025: 多级管道
        val out25 = engine.executeStrings("find | grep txt | tail -n 2 | wc -l", ctx)
        assertNotNull(out25)
    }

    /**
     * 3. 转义字符测试用例 (TA031-TA040)
     */
    @Test
    fun testTailEscapesAndSpecialCharacters() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TA031: 含空格行
        val out31 = engine.executeStrings("echo 'my file' | tail -n 1", ctx)
        assertEquals(listOf("my file"), out31)

        // TA032: 含单引号
        val out32 = engine.executeStrings("echo \"it's\" | tail -n 1", ctx)
        assertEquals(listOf("it's"), out32)

        // TA033: 含双引号
        val out33 = engine.executeStrings("echo 'say\"hi\"' | tail -n 1", ctx)
        assertEquals(listOf("say\"hi\""), out33)

        // TA034: 反斜杠
        val out34 = engine.executeStrings("echo 'a\\\\b' | tail -n 1", ctx)
        assertNotNull(out34)

        // TA035: 星号
        val out35 = engine.executeStrings("echo '*.txt' | tail -n 1", ctx)
        assertEquals(listOf("*.txt"), out35)

        // TA036: 问号
        val out36 = engine.executeStrings("echo '?.txt' | tail -n 1", ctx)
        assertEquals(listOf("?.txt"), out36)

        // TA037: 方括号
        val out37 = engine.executeStrings("echo '[test]' | tail -n 1", ctx)
        assertEquals(listOf("[test]"), out37)

        // TA038: 反引号
        val out38 = engine.executeStrings("echo '`whoami`' | tail -n 1", ctx)
        assertNotNull(out38)

        // TA039: 中文
        val out39 = engine.executeStrings("echo '测试' | tail -n 1", ctx)
        assertEquals(listOf("测试"), out39)

        // TA040: emoji
        val out40 = engine.executeStrings("echo '😀' | tail -n 1", ctx)
        assertEquals(listOf("😀"), out40)
    }

    /**
     * 4. 边界与异常测试用例 (TA041-TA050)
     */
    @Test
    fun testTailBoundariesAndExceptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // TA041: 超大 N
        val out41 = engine.executeStrings("echo '1\n2' | tail -n 999999", ctx)
        assertEquals(listOf("1", "2"), out41)

        // TA042: 小数 N
        val out42 = engine.executeStrings("echo '1\n2' | tail -n 1.5", ctx)
        assertNotNull(out42)

        // TA043: 前导 0
        val out43 = engine.executeStrings("echo '1\n2' | tail -n 01", ctx)
        assertEquals(listOf("2"), out43)

        // TA044: 加号 N
        val out44 = engine.executeStrings("echo '1\n2' | tail -n +1", ctx)
        assertNotNull(out44)

        // TA045: 空字符串 N
        val out45 = engine.executeStrings("tail -n ''", ctx)
        assertNotNull(out45)

        // TA046: 多次 -n
        val out46 = engine.executeStrings("echo '1\n2\n3' | tail -n 1 -n 2", ctx)
        assertNotNull(out46)

        // TA047: -n 后接路径
        val out47 = engine.executeStrings("tail -n 1 test.txt", ctx)
        assertNotNull(out47)

        // TA048: 路径含空格
        val out48 = engine.executeStrings("tail -n 1 \"my file.txt\"", ctx)
        assertNotNull(out48)

        // TA049: 路径含 $
        val out49 = engine.executeStrings("tail -n 1 'price\$1.txt'", ctx)
        assertNotNull(out49)

        // TA050: 路径含中文
        val out50 = engine.executeStrings("tail -n 1 测试.txt", ctx)
        assertNotNull(out50)
    }
}
