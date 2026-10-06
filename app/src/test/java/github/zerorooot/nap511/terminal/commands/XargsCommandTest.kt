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
 * xargs 命令测试用例集合（X001-X080）
 * 覆盖：基本功能、转义字符、管道组合、异常与边界
 */
class XargsCommandTest {

    @Test
    fun testXargsCommand() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // 1. 默认参数：echo 输出，相当于将输入作为 echo 参数输出
        val out1 = engine.executeStrings("echo 'a\nb\nc' | xargs", ctx)
        assertEquals(listOf("a b c"), out1)

        // 2. -n 1 逐个分批传递给 echo
        val out2 = engine.executeStrings("echo 'apple banana orange' | xargs -n 1 echo", ctx)
        assertEquals(listOf("apple", "banana", "orange"), out2)

        // 3. -I {} 占位符逐行替换测试
        val out3 = engine.executeStrings("echo 'folder1\nfolder2' | xargs -I {} echo move_{}_target", ctx)
        assertEquals(listOf("move_folder1_target", "move_folder2_target"), out3)

        // 4. -t 选项回显测试
        val out4 = engine.executeStrings("echo 'hello' | xargs -t echo", ctx)
        assertEquals(listOf("+ echo hello", "hello"), out4)
    }

    /**
     * 1. 基本功能测试用例 (X001-X020)
     */
    @Test
    fun testXargsBasicFunctions() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // X001: 查看帮助
        val outHelp = engine.executeStrings("xargs -h", ctx)
        assertTrue(outHelp.any { it.contains("xargs") })

        // X002: 默认 echo
        val out02 = engine.executeStrings("echo 'a\nb' | xargs", ctx)
        assertEquals(listOf("a b"), out02)

        // X003: 默认命令
        val out03 = engine.executeStrings("echo 'a b' | xargs echo", ctx)
        assertEquals(listOf("a b"), out03)

        // X004: -n 1
        val out04 = engine.executeStrings("echo 'a\nb' | xargs -n 1 echo", ctx)
        assertEquals(listOf("a", "b"), out04)

        // X005: -n 2
        val out05 = engine.executeStrings("echo 'a\nb\nc' | xargs -n 2 echo", ctx)
        assertEquals(listOf("a b", "c"), out05)

        // X006: -I {}
        val out06 = engine.executeStrings("echo 'a\nb' | xargs -I {} echo [{}]", ctx)
        assertEquals(listOf("[a]", "[b]"), out06)

        // X007: -t 打印
        val out07 = engine.executeStrings("echo 'a' | xargs -t echo", ctx)
        assertTrue(out07.contains("+ echo a"))

        // X008: -I 与 -n 组合
        val out08 = engine.executeStrings("echo 'a\nb' | xargs -I {} -n 1 echo {}", ctx)
        assertNotNull(out08)

        // X009: 无输入
        val out09 = engine.executeStrings("echo '' | xargs echo", ctx)
        assertNotNull(out09)

        // X010: 单行多词
        val out10 = engine.executeStrings("echo 'a b c' | xargs echo", ctx)
        assertEquals(listOf("a b c"), out10)

        // X011: 多行
        val out11 = engine.executeStrings("echo 'a\nb' | xargs echo", ctx)
        assertEquals(listOf("a b"), out11)

        // X012: 含空格参数
        val out12 = engine.executeStrings("echo 'my file' | xargs echo", ctx)
        assertNotNull(out12)

        // X013: 含引号参数
        val out13 = engine.executeStrings("echo \"it's\" | xargs echo", ctx)
        assertNotNull(out13)

        // X014: 含双引号参数
        val out14 = engine.executeStrings("echo 'say\"hi\"' | xargs echo", ctx)
        assertNotNull(out14)

        // X015: 初始参数
        val out15 = engine.executeStrings("echo 'a' | xargs echo prefix", ctx)
        assertEquals(listOf("prefix a"), out15)

        // X016: 指定命令
        val out16 = engine.executeStrings("echo 'a' | xargs ls", ctx)
        assertNotNull(out16)

        // X017: 命令不存在
        val out17 = engine.executeStrings("echo 'a' | xargs notexist", ctx)
        assertNotNull(out17)

        // X018: 空命令
        val out18 = engine.executeStrings("echo 'a' | xargs ''", ctx)
        assertNotNull(out18)

        // X019: 未知选项
        val out19 = engine.executeStrings("xargs -x", ctx)
        assertNotNull(out19)

        // X020: -n 缺参
        val out20 = engine.executeStrings("xargs -n", ctx)
        assertNotNull(out20)
    }

    /**
     * 2. 转义字符测试用例 (X021-X040)
     */
    @Test
    fun testXargsEscapesAndSpecialCharacters() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // X021: $
        val out21 = engine.executeStrings("echo 'price\$1' | xargs echo", ctx)
        assertEquals(listOf("price\$1"), out21)

        // X022: !
        val out22 = engine.executeStrings("echo 'hi!' | xargs echo", ctx)
        assertEquals(listOf("hi!"), out22)

        // X023: #
        val out23 = engine.executeStrings("echo 'a#b' | xargs echo", ctx)
        assertEquals(listOf("a#b"), out23)

        // X024: 反斜杠
        val out24 = engine.executeStrings("echo 'a\\\\b' | xargs echo", ctx)
        assertNotNull(out24)

        // X025: 星号
        val out25 = engine.executeStrings("echo '*.txt' | xargs echo", ctx)
        assertEquals(listOf("*.txt"), out25)

        // X026: 问号
        val out26 = engine.executeStrings("echo '?.txt' | xargs echo", ctx)
        assertEquals(listOf("?.txt"), out26)

        // X027: 方括号
        val out27 = engine.executeStrings("echo '[test]' | xargs echo", ctx)
        assertEquals(listOf("[test]"), out27)

        // X028: 反引号
        val out28 = engine.executeStrings("echo '`whoami`' | xargs echo", ctx)
        assertNotNull(out28)

        // X029: 命令替换
        val out29 = engine.executeStrings("echo '\$(whoami)' | xargs echo", ctx)
        assertNotNull(out29)

        // X030: 中文
        val out30 = engine.executeStrings("echo '测试' | xargs echo", ctx)
        assertEquals(listOf("测试"), out30)

        // X031: emoji
        val out31 = engine.executeStrings("echo '😀' | xargs echo", ctx)
        assertEquals(listOf("😀"), out31)

        // X032: tab
        val out32 = engine.executeStrings("echo 'a\tb' | xargs echo", ctx)
        assertEquals(listOf("a b"), out32)

        // X033: 换行
        val out33 = engine.executeStrings("echo 'a\nb' | xargs -I {} echo {}", ctx)
        assertEquals(listOf("a", "b"), out33)

        // X034: 单引号
        val out34 = engine.executeStrings("echo \"it's\" | xargs -I {} echo {}", ctx)
        assertEquals(listOf("it's"), out34)

        // X035: 双引号
        val out35 = engine.executeStrings("echo 'say\"hi\"' | xargs -I {} echo {}", ctx)
        assertEquals(listOf("say\"hi\""), out35)

        // X036: 全角空格
        val out36 = engine.executeStrings("echo 'a　b' | xargs echo", ctx)
        assertNotNull(out36)

        // X037: 中文引号
        val out37 = engine.executeStrings("echo '“测试”' | xargs echo", ctx)
        assertEquals(listOf("“测试”"), out37)

        // X038: emoji 组合
        val out38 = engine.executeStrings("echo '👨‍👩‍👧' | xargs echo", ctx)
        assertEquals(listOf("👨‍👩‍👧"), out38)

        // X039: 前导空格
        val out39 = engine.executeStrings("echo '  a' | xargs echo", ctx)
        assertEquals(listOf("a"), out39)

        // X040: 尾随空格
        val out40 = engine.executeStrings("echo 'a  ' | xargs echo", ctx)
        assertEquals(listOf("a"), out40)
    }

    /**
     * 3. 管道与组合测试用例 (X041-X060)
     */
    @Test
    fun testXargsPipelinesAndCombinations() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // X041: find 接 xargs
        val out41 = engine.executeStrings("find -name '*.txt' | xargs echo", ctx)
        assertNotNull(out41)

        // X042: find 接 xargs -n 1
        val out42 = engine.executeStrings("find -name '*.txt' | xargs -n 1 echo", ctx)
        assertNotNull(out42)

        // X043: find 接 xargs -I {}
        val out43 = engine.executeStrings("find -name '*.txt' | xargs -I {} echo [{}]", ctx)
        assertNotNull(out43)

        // X044: ls 接 xargs
        val out44 = engine.executeStrings("ls | xargs echo", ctx)
        assertNotNull(out44)

        // X045: grep 接 xargs
        val out45 = engine.executeStrings("ls | grep txt | xargs echo", ctx)
        assertNotNull(out45)

        // X046: sort 接 xargs
        val out46 = engine.executeStrings("echo 'b\na' | sort | xargs echo", ctx)
        assertEquals(listOf("a b"), out46)

        // X047: head 接 xargs
        val out47 = engine.executeStrings("echo '1\n2' | head -n 1 | xargs echo", ctx)
        assertEquals(listOf("1"), out47)

        // X048: tail 接 xargs
        val out48 = engine.executeStrings("echo '1\n2' | tail -n 1 | xargs echo", ctx)
        assertEquals(listOf("2"), out48)

        // X049: wc 接 xargs
        val out49 = engine.executeStrings("echo 'a' | wc -l | xargs echo", ctx)
        assertEquals(listOf("1"), out49)

        // X050: history 接 xargs
        val out50 = engine.executeStrings("history | xargs echo", ctx)
        assertNotNull(out50)

        // X051: xargs 接 wc
        val out51 = engine.executeStrings("echo 'a b' | xargs echo | wc -w", ctx)
        assertEquals(listOf("2"), out51)

        // X052: xargs 接 grep
        val out52 = engine.executeStrings("echo 'a\nb' | xargs echo | grep a", ctx)
        assertEquals(listOf("a b"), out52)

        // X053: xargs 接 head
        val out53 = engine.executeStrings("echo 'a\nb' | xargs echo | head", ctx)
        assertEquals(listOf("a b"), out53)

        // X054: xargs 接 tail
        val out54 = engine.executeStrings("echo 'a\nb' | xargs echo | tail", ctx)
        assertEquals(listOf("a b"), out54)

        // X055: xargs 接 sort
        val out55 = engine.executeStrings("echo 'b a' | xargs -n 1 echo | sort", ctx)
        assertEquals(listOf("a", "b"), out55)

        // X056: find 接 xargs rm
        val out56 = engine.executeStrings("find -name '*.tmp' | xargs rm -f", ctx)
        assertNotNull(out56)

        // X057: ls 接 grep 接 xargs rm
        val out57 = engine.executeStrings("ls | grep tmp | xargs rm -f", ctx)
        assertNotNull(out57)

        // X058: find 接 xargs stat
        val out58 = engine.executeStrings("find -name 'test.txt' | xargs stat", ctx)
        assertNotNull(out58)

        // X059: find 接 xargs open
        val out59 = engine.executeStrings("find -name 'test.txt' | xargs open", ctx)
        assertNotNull(out59)

        // X060: 多级管道
        val out60 = engine.executeStrings("find | grep txt | sort | head | xargs stat", ctx)
        assertNotNull(out60)
    }

    /**
     * 4. 异常与边界测试用例 (X061-X080)
     */
    @Test
    fun testXargsExceptionsAndBoundaries() = runBlocking {
        val registry = CommandRegistryFactory.createDefaultRegistry { emptyList() }
        val engine = PipelineEngine(registry)
        val ctx = TerminalContext()
        ctx.updateDirectory(listOf(PathBean("0", "根目录", "0")))

        // X061: -I 缺参
        val out61 = engine.executeStrings("xargs -I", ctx)
        assertNotNull(out61)

        // X062: -I 空占位符
        val out62 = engine.executeStrings("xargs -I '' echo", ctx)
        assertNotNull(out62)

        // X063: -n 0
        val out63 = engine.executeStrings("echo 'a' | xargs -n 0 echo", ctx)
        assertNotNull(out63)

        // X064: -n 负数
        val out64 = engine.executeStrings("echo 'a' | xargs -n -1 echo", ctx)
        assertNotNull(out64)

        // X065: -n 非数字
        val out65 = engine.executeStrings("echo 'a' | xargs -n abc echo", ctx)
        assertNotNull(out65)

        // X066: -n 超大
        val out66 = engine.executeStrings("echo 'a' | xargs -n 999999 echo", ctx)
        assertEquals(listOf("a"), out66)

        // X067: -I 与 -n 冲突
        val out67 = engine.executeStrings("echo 'a' | xargs -I {} -n 1 echo", ctx)
        assertNotNull(out67)

        // X068: -t 与 -I
        val out68 = engine.executeStrings("echo 'a' | xargs -t -I {} echo {}", ctx)
        assertTrue(out68.contains("+ echo a"))

        // X069: 重复 -n
        val out69 = engine.executeStrings("echo 'a' | xargs -n 1 -n 2 echo", ctx)
        assertNotNull(out69)

        // X070: -- 分隔
        val out70 = engine.executeStrings("echo 'a' | xargs -- echo", ctx)
        assertEquals(listOf("a"), out70)

        // X076: 空输入接命令
        val out76 = engine.executeStrings("echo '' | xargs echo", ctx)
        assertNotNull(out76)

        // X077: 输入仅空白
        val out77 = engine.executeStrings("echo '   ' | xargs echo", ctx)
        assertNotNull(out77)

        // X078: 输入含换行与空格
        val out78 = engine.executeStrings("echo 'a b\nc d' | xargs -n 1 echo", ctx)
        assertEquals(listOf("a", "b", "c", "d"), out78)

        // X079: -I 多占位符
        val out79 = engine.executeStrings("echo 'a' | xargs -I {} echo {} {}", ctx)
        assertEquals(listOf("a a"), out79)

        // X080: -I 占位符含特殊字符
        val out80 = engine.executeStrings("echo 'a' | xargs -I '@@' echo '@@'", ctx)
        assertEquals(listOf("a"), out80)
    }
}
