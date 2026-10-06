package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * head 命令测试用例集合（H001-H025）
 * 覆盖：基本功能、-n 选项边界、转义字符、管道组合
 */
class HeadCommandTest {

    /**
     * 1. 基本功能与选项测试用例 (H001-H018)
     */
    @Test
    fun testHeadBasicFunctionsAndOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // H001: 查看帮助
        val outHelp = engine.executeStrings("head -h", ctx)
        assertTrue(outHelp.any { it.contains("head") })

        // H002: 默认 10 行
        val input20 = (1..20).joinToString("\n")
        val outDefault = engine.executeStrings("echo '$input20' | head", ctx)
        assertEquals(10, outDefault.size)
        assertEquals("1", outDefault.first())
        assertEquals("10", outDefault.last())

        // H003: -n 3
        val outN3 = engine.executeStrings("echo '1\n2\n3\n4' | head -n 3", ctx)
        assertEquals(listOf("1", "2", "3"), outN3)

        // H004: -n 1
        val outN1 = engine.executeStrings("echo '1\n2' | head -n 1", ctx)
        assertEquals(listOf("1"), outN1)

        // H005: -n 0
        val outN0 = engine.executeStrings("echo '1\n2' | head -n 0", ctx)
        assertTrue(outN0.isEmpty())

        // H006: -n 负数
        val out06 = engine.executeStrings("echo '1\n2' | head -n -1", ctx)
        assertTrue(out06.size <= 2)

        // H007: -n 非数字
        val out07 = engine.executeStrings("echo '1' | head -n abc", ctx)
        assertEquals(listOf("1"), out07)

        // H008: -n 缺参
        val out08 = engine.executeStrings("head -n", ctx)
        assertTrue(out08.isEmpty() || out08.any { it.contains("head") })

        // H009: 行数大于输入
        val outN100 = engine.executeStrings("echo '1\n2' | head -n 100", ctx)
        assertEquals(listOf("1", "2"), outN100)

        // H010: 空输入
        val outEmpty = engine.executeStrings("echo -n '' | head", ctx)
        assertTrue(outEmpty.isEmpty() || outEmpty == listOf(""))

        // H011: 单行无换行
        val outSingle = engine.executeStrings("echo -n 'abc' | head", ctx)
        assertEquals(listOf("abc"), outSingle)

        // H012: 中文行
        val outChinese = engine.executeStrings("echo '你\n好' | head -n 1", ctx)
        assertEquals(listOf("你"), outChinese)

        // H013: 转义字符
        val outTab = engine.executeStrings("echo 'a\tb' | head -n 1", ctx)
        assertEquals(listOf("a\tb"), outTab)

        // H014: $
        val outDollar = engine.executeStrings("echo 'price\$1' | head -n 1", ctx)
        assertEquals(listOf("price\$1"), outDollar)

        // H015: !
        val outExcl = engine.executeStrings("echo 'hi!' | head -n 1", ctx)
        assertEquals(listOf("hi!"), outExcl)

        // H016: #
        val outHash = engine.executeStrings("echo 'a#b' | head -n 1", ctx)
        assertEquals(listOf("a#b"), outHash)

        // H017: 未知选项
        val out17 = engine.executeStrings("head -x", ctx)
        assertTrue(out17.isEmpty() || out17.any { it.contains("head") })

        // H018: 多余参数
        val out18 = engine.executeStrings("head -n 1 a b", ctx)
        assertTrue(out18.size <= 1)
    }

    /**
     * 2. 管道测试用例 (H019-H025)
     */
    @Test
    fun testHeadPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // H019: 管道 find
        val out19 = engine.executeStrings("find -name '*.txt' | head", ctx)
        assertTrue(out19.size <= 10)

        // H020: 管道 ls
        val out20 = engine.executeStrings("ls | head -n 5", ctx)
        assertTrue(out20.size <= 5)

        // H021: 管道 grep
        val out21 = engine.executeStrings("ls | grep txt | head -n 3", ctx)
        assertTrue(out21.size <= 3)

        // H022: 接 wc
        val out22 = engine.executeStrings("echo '1\n2' | head -n 1 | wc -l", ctx)
        assertEquals(listOf("1"), out22)

        // H023: 接 sort
        val out23 = engine.executeStrings("echo 'b\na' | head -n 1 | sort", ctx)
        assertEquals(listOf("b"), out23)

        // H024: 接 xargs
        val out24 = engine.executeStrings("echo 'a\nb' | head -n 1 | xargs echo", ctx)
        assertEquals(listOf("a"), out24)

        // H025: 多级管道
        val out25 = engine.executeStrings("find | grep txt | head -n 2 | wc -l", ctx)
        assertEquals(1, out25.size)
    }
}
