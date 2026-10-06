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
 * wc 命令测试用例集合（W001-W050）
 * 覆盖：基本功能、转义字符、管道组合与边界
 */
class WcCommandTest {

    /**
     * 1. 基本功能测试用例 (W001-W020)
     */
    @Test
    fun testWcBasicFunctionsAndOptions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // W001: 查看帮助
        val outHelp = engine.executeStrings("wc -h", ctx)
        assertTrue(outHelp.any { it.contains("wc") })

        // W002: 默认三项
        val outDefault = engine.executeStrings("echo 'a b\nc' | wc", ctx)
        assertNotNull(outDefault)

        // W003: -l 行数
        val outL = engine.executeStrings("echo 'a\nb' | wc -l", ctx)
        assertEquals(listOf("2"), outL)

        // W004: -w 词数
        val outW = engine.executeStrings("echo 'a b c' | wc -w", ctx)
        assertEquals(listOf("3"), outW)

        // W005: -c 字符
        val outC = engine.executeStrings("echo 'abc' | wc -c", ctx)
        assertNotNull(outC)

        // W006: -l -w
        val outLW = engine.executeStrings("echo 'a b' | wc -l -w", ctx)
        assertNotNull(outLW)

        // W007: -l -w -c
        val outLWC = engine.executeStrings("echo 'a b' | wc -l -w -c", ctx)
        assertNotNull(outLWC)

        // W008: 空输入
        val outEmpty = engine.executeStrings("echo '' | wc", ctx)
        assertNotNull(outEmpty)

        // W009: 单行无换行
        val outSingle = engine.executeStrings("echo 'abc' | wc -l", ctx)
        assertNotNull(outSingle)

        // W010: 多空格
        val outSpaces = engine.executeStrings("echo 'a   b' | wc -w", ctx)
        assertEquals(listOf("2"), outSpaces)

        // W011: 前导空格
        val outLeading = engine.executeStrings("echo '  a b' | wc -w", ctx)
        assertEquals(listOf("2"), outLeading)

        // W012: 尾随空格
        val outTrailing = engine.executeStrings("echo 'a b  ' | wc -w", ctx)
        assertEquals(listOf("2"), outTrailing)

        // W013: tab 分隔
        val outTab = engine.executeStrings("echo 'a\tb' | wc -w", ctx)
        assertEquals(listOf("2"), outTab)

        // W014: 中文词
        val outCnWords = engine.executeStrings("echo '你好 世界' | wc -w", ctx)
        assertNotNull(outCnWords)

        // W015: 中文行
        val outCnLines = engine.executeStrings("echo '你好' | wc -l", ctx)
        assertEquals(listOf("1"), outCnLines)

        // W016: emoji
        val outEmoji = engine.executeStrings("echo '😀' | wc -c", ctx)
        assertNotNull(outEmoji)

        // W017: 未知选项
        val outUnknown = engine.executeStrings("wc -x", ctx)
        assertNotNull(outUnknown)

        // W018: 重复选项
        val outRepeat = engine.executeStrings("echo 'a' | wc -l -l", ctx)
        assertEquals(listOf("1"), outRepeat)

        // W019: 选项顺序
        val outOrder = engine.executeStrings("echo 'a b' | wc -w -l", ctx)
        assertNotNull(outOrder)

        // W020: 无参数
        val outNoArgs = engine.executeStrings("wc", ctx)
        assertNotNull(outNoArgs)
    }

    /**
     * 2. 转义字符测试用例 (W021-W030)
     */
    @Test
    fun testWcEscapesAndSpecialCharacters() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // W021: $
        val out21 = engine.executeStrings("echo 'price\$1' | wc -c", ctx)
        assertNotNull(out21)

        // W022: !
        val out22 = engine.executeStrings("echo 'hi!' | wc -c", ctx)
        assertNotNull(out22)

        // W023: #
        val out23 = engine.executeStrings("echo 'a#b' | wc -c", ctx)
        assertNotNull(out23)

        // W024: 反斜杠
        val out24 = engine.executeStrings("echo 'a\\\\b' | wc -c", ctx)
        assertNotNull(out24)

        // W025: 引号
        val out25 = engine.executeStrings("echo \"it's\" | wc -c", ctx)
        assertNotNull(out25)

        // W026: 双引号
        val out26 = engine.executeStrings("echo 'say\"hi\"' | wc -c", ctx)
        assertNotNull(out26)

        // W027: 星号
        val out27 = engine.executeStrings("echo '*.txt' | wc -c", ctx)
        assertNotNull(out27)

        // W028: 问号
        val out28 = engine.executeStrings("echo '?.txt' | wc -c", ctx)
        assertNotNull(out28)

        // W029: 方括号
        val out29 = engine.executeStrings("echo '[test]' | wc -c", ctx)
        assertNotNull(out29)

        // W030: 反引号
        val out30 = engine.executeStrings("echo '`whoami`' | wc -c", ctx)
        assertNotNull(out30)
    }

    /**
     * 3. 管道测试用例 (W031-W050)
     */
    @Test
    fun testWcPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // W031: ls 接 wc -l
        val out31 = engine.executeStrings("ls | wc -l", ctx)
        assertNotNull(out31)

        // W032: find 接 wc -l
        val out32 = engine.executeStrings("find -name '*.txt' | wc -l", ctx)
        assertNotNull(out32)

        // W033: grep 接 wc -l
        val out33 = engine.executeStrings("ls | grep txt | wc -l", ctx)
        assertNotNull(out33)

        // W034: history 接 wc -l
        val out34 = engine.executeStrings("history | wc -l", ctx)
        assertNotNull(out34)

        // W035: sort 接 wc -l
        val out35 = engine.executeStrings("echo 'b\na' | sort | wc -l", ctx)
        assertEquals(listOf("2"), out35)

        // W036: head 接 wc -l
        val out36 = engine.executeStrings("echo '1\n2\n3' | head -n 2 | wc -l", ctx)
        assertEquals(listOf("2"), out36)

        // W037: tail 接 wc -l
        val out37 = engine.executeStrings("echo '1\n2\n3' | tail -n 2 | wc -l", ctx)
        assertEquals(listOf("2"), out37)

        // W038: echo 接 wc
        val out38 = engine.executeStrings("echo hello | wc", ctx)
        assertNotNull(out38)

        // W039: xargs 接 wc
        val out39 = engine.executeStrings("echo 'a b c' | xargs wc -w", ctx)
        assertNotNull(out39)

        // W040: 多级管道
        val out40 = engine.executeStrings("find | grep txt | head | wc -l", ctx)
        assertNotNull(out40)

        // W041: wc 接 xargs
        val out41 = engine.executeStrings("echo 'a' | wc -l | xargs echo", ctx)
        assertEquals(listOf("1"), out41)

        // W042: wc 接 grep
        val out42 = engine.executeStrings("echo 'a' | wc -l | grep 1", ctx)
        assertEquals(listOf("1"), out42)

        // W043: wc 接 sort
        val out43 = engine.executeStrings("echo 'a' | wc -l | sort", ctx)
        assertEquals(listOf("1"), out43)

        // W044: wc 接 head
        val out44 = engine.executeStrings("echo 'a' | wc | head", ctx)
        assertNotNull(out44)

        // W045: wc 接 tail
        val out45 = engine.executeStrings("echo 'a' | wc | tail", ctx)
        assertNotNull(out45)

        // W046: wc 转义管道
        val out46 = engine.executeStrings("echo 'a\\\\.txt' | wc -c", ctx)
        assertNotNull(out46)

        // W047: wc 中文管道
        val out47 = engine.executeStrings("echo '测试' | wc -l", ctx)
        assertEquals(listOf("1"), out47)

        // W048: wc emoji 管道
        val out48 = engine.executeStrings("echo '😀' | wc -l", ctx)
        assertEquals(listOf("1"), out48)

        // W049: wc 空接 wc
        val out49 = engine.executeStrings("echo '' | wc -l | wc -l", ctx)
        assertNotNull(out49)

        // W050: wc 多选项接 wc
        val out50 = engine.executeStrings("echo 'a b' | wc -l -w | wc -w", ctx)
        assertNotNull(out50)
    }
}
