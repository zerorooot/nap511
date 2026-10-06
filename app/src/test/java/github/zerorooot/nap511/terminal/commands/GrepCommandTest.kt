package github.zerorooot.nap511.terminal.commands

import github.zerorooot.nap511.bean.PathBean
import github.zerorooot.nap511.terminal.context.TerminalContext
import github.zerorooot.nap511.terminal.engine.PipelineEngine
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * grep 命令测试用例集合（G001-G050）
 * 覆盖：基本功能、正则与转义、管道组合
 */
class GrepCommandTest {

    /**
     * 1. 基本功能测试用例 (G001-G010)
     */
    @Test
    fun testGrepBasicFunctions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // G001: 查看帮助
        val outHelp = engine.executeStrings("grep -h", ctx)
        assertTrue(outHelp.any { it.contains("grep") })

        // G002: 基本匹配
        val out02 = engine.executeStrings("echo hello | grep hell", ctx)
        assertEquals(listOf("hello"), out02)

        // G003: 不匹配
        val out03 = engine.executeStrings("echo hello | grep xyz", ctx)
        assertTrue(out03.isEmpty())

        // G004: 大小写敏感
        val out04 = engine.executeStrings("echo Hello | grep hello", ctx)
        assertTrue(out04.isEmpty())

        // G005: -i 忽略大小写
        val out05 = engine.executeStrings("echo Hello | grep -i hello", ctx)
        assertEquals(listOf("Hello"), out05)

        // G006: -v 反向（无匹配行时全选）
        val out06 = engine.executeStrings("echo hello | grep -v xyz", ctx)
        assertEquals(listOf("hello"), out06)

        // G007: -v 排除
        val out07 = engine.executeStrings("echo hello | grep -v hell", ctx)
        assertTrue(out07.isEmpty())

        // G008: -c 计数
        val out08 = engine.executeStrings("echo 'a\nb\na' | grep -c a", ctx)
        assertEquals(listOf("2"), out08)

        // G009: 多行匹配
        val out09 = engine.executeStrings("echo 'a\nb\na' | grep a", ctx)
        assertEquals(listOf("a", "a"), out09)

        // G010: 无匹配 -c
        val out10 = engine.executeStrings("echo hello | grep -c xyz", ctx)
        assertEquals(listOf("0"), out10)
    }

    /**
     * 2. 正则与转义测试用例 (G011-G040)
     */
    @Test
    fun testGrepRegexAndEscaping() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // G011: 点号通配
        val out11 = engine.executeStrings("echo a.txt | grep 'a.txt'", ctx)
        assertEquals(listOf("a.txt"), out11)

        // G012: 转义点号
        val out12 = engine.executeStrings("echo a.txt | grep 'a\\.txt'", ctx)
        assertEquals(listOf("a.txt"), out12)

        // G013: 转义点号不匹配
        val out13 = engine.executeStrings("echo axtxt | grep 'a\\.txt'", ctx)
        assertTrue(out13.isEmpty())

        // G014: ^ 行首
        val out14 = engine.executeStrings("echo hello | grep '^he'", ctx)
        assertEquals(listOf("hello"), out14)

        // G015: $ 行尾
        val out15 = engine.executeStrings("echo hello | grep 'lo\$'", ctx)
        assertEquals(listOf("hello"), out15)

        // G016: .*
        val out16 = engine.executeStrings("echo abc | grep 'a.*c'", ctx)
        assertEquals(listOf("abc"), out16)

        // G017: [abc]
        val out17 = engine.executeStrings("echo b | grep '[abc]'", ctx)
        assertEquals(listOf("b"), out17)

        // G018: [^abc]
        val out18 = engine.executeStrings("echo d | grep '[^abc]'", ctx)
        assertEquals(listOf("d"), out18)

        // G019: \+
        val out19 = engine.executeStrings("echo aa | grep 'a\\+'", ctx)
        assertEquals(listOf("aa"), out19)

        // G020: \?
        val out20 = engine.executeStrings("echo a | grep 'a\\?'", ctx)
        assertEquals(listOf("a"), out20)

        // G021: 花括号
        val out21 = engine.executeStrings("echo aa | grep 'a\\{2\\}'", ctx)
        assertEquals(listOf("aa"), out21)

        // G022: 竖线或
        val out22 = engine.executeStrings("echo cat | grep 'cat\\|dog'", ctx)
        assertEquals(listOf("cat"), out22)

        // G023: 分组
        val out23 = engine.executeStrings("echo abc | grep '\\(ab\\)c'", ctx)
        assertEquals(listOf("abc"), out23)

        // G024: 反斜杠
        val out24 = engine.executeStrings("echo 'a\\b' | grep 'a\\\\b'", ctx)
        assertEquals(listOf("a\\b"), out24)

        // G025: $ 字面量
        val out25 = engine.executeStrings("echo 'price\$1' | grep 'price\\\$1'", ctx)
        assertEquals(listOf("price\$1"), out25)

        // G026: !
        val out26 = engine.executeStrings("echo 'hi!' | grep 'hi!'", ctx)
        assertEquals(listOf("hi!"), out26)

        // G027: #
        val out27 = engine.executeStrings("echo 'a#b' | grep 'a#b'", ctx)
        assertEquals(listOf("a#b"), out27)

        // G028: * 字面量
        val out28 = engine.executeStrings("echo 'a*b' | grep 'a\\*b'", ctx)
        assertEquals(listOf("a*b"), out28)

        // G029: ? 字面量
        val out29 = engine.executeStrings("echo 'a?b' | grep 'a\\?b'", ctx)
        assertEquals(listOf("a?b"), out29)

        // G030: [ 字面量
        val out30 = engine.executeStrings("echo 'a[b' | grep 'a\\[b'", ctx)
        assertEquals(listOf("a[b"), out30)

        // G031: 中文
        val out31 = engine.executeStrings("echo 测试 | grep 测", ctx)
        assertEquals(listOf("测试"), out31)

        // G032: emoji
        val out32 = engine.executeStrings("echo 😀 | grep 😀", ctx)
        assertEquals(listOf("😀"), out32)

        // G033: 空格
        val out33 = engine.executeStrings("echo 'my file' | grep 'my file'", ctx)
        assertEquals(listOf("my file"), out33)

        // G034: tab
        val out34 = engine.executeStrings("echo 'a\tb' | grep 'a\tb'", ctx)
        assertEquals(listOf("a\tb"), out34)

        // G035: 空 pattern
        val out35 = engine.executeStrings("echo hello | grep ''", ctx)
        assertEquals(listOf("hello"), out35)

        // G036: pattern 缺参
        val out36 = engine.executeStrings("grep", ctx)
        assertTrue(out36.isEmpty() || out36.any { it.contains("grep") })

        // G037: 未知选项
        val out37 = engine.executeStrings("grep -x", ctx)
        assertTrue(out37.isEmpty() || out37.any { it.contains("grep") })

        // G038: 选项组合 -iv
        val out38 = engine.executeStrings("echo Hello | grep -iv hell", ctx)
        assertTrue(out38.isEmpty())

        // G039: -i -c
        val out39 = engine.executeStrings("echo 'A\na' | grep -ic a", ctx)
        assertEquals(listOf("2"), out39)

        // G040: -v -c
        val out40 = engine.executeStrings("echo 'a\nb' | grep -vc a", ctx)
        assertEquals(listOf("1"), out40)
    }

    /**
     * 3. 管道测试用例 (G041-G050)
     */
    @Test
    fun testGrepPipelines() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // G041: find 接 grep
        val out41 = engine.executeStrings("find -name '*.txt' | grep test", ctx)
        assertTrue(out41.isEmpty() || out41.any { it.contains("test") })

        // G042: ls 接 grep
        val out42 = engine.executeStrings("ls | grep txt", ctx)
        assertTrue(out42.isEmpty() || out42.any { it.contains("txt") })

        // G043: grep 接 head
        val out43 = engine.executeStrings("ls | grep txt | head", ctx)
        assertTrue(out43.size <= 10)

        // G044: grep 接 tail
        val out44 = engine.executeStrings("ls | grep txt | tail", ctx)
        assertTrue(out44.size <= 10)

        // G045: grep 接 wc
        val out45 = engine.executeStrings("ls | grep txt | wc -l", ctx)
        assertEquals(1, out45.size)

        // G046: grep 接 sort
        val out46 = engine.executeStrings("echo 'b\na' | grep . | sort", ctx)
        assertEquals(listOf("a", "b"), out46)

        // G047: grep 接 xargs
        val out47 = engine.executeStrings("echo hello | grep hell | xargs echo", ctx)
        assertEquals(listOf("hello"), out47)

        // G048: 多级管道
        val out48 = engine.executeStrings("find -name '*.txt' | grep test | head | wc -l", ctx)
        assertEquals(1, out48.size)

        // G049: grep 转义管道
        val out49 = engine.executeStrings("echo 'a.txt' | grep 'a\\.txt' | wc -l", ctx)
        assertEquals(listOf("1"), out49)

        // G050: grep 中文管道
        val out50 = engine.executeStrings("echo 测试 | grep 测 | wc -l", ctx)
        assertEquals(listOf("1"), out50)
    }
}
