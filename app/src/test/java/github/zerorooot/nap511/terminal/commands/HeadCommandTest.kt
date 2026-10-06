package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * head 命令测试用例集合
 * 覆盖：基本功能与 -n 选项、字符与格式处理、管道组合场景
 */
class HeadCommandTest {

    /**
     * 1. 基本功能与选项测试
     */
    @Test
    fun testHeadBasicFunctionsAndOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看帮助文档
        val outHelp = engine.executeStrings("head -h", ctx)
        assertTrue(outHelp.any { it.contains("head") })

        // 默认输出前 10 行
        val input20 = (1..20).joinToString("\n")
        val outDefault = engine.executeStrings("echo '$input20' | head", ctx)
        assertEquals(10, outDefault.size)
        assertEquals("1", outDefault.first())
        assertEquals("10", outDefault.last())

        // -n 3 指定输出前 3 行
        val outN3 = engine.executeStrings("echo '1\n2\n3\n4' | head -n 3", ctx)
        assertEquals(listOf("1", "2", "3"), outN3)

        // -n 1 指定输出前 1 行
        val outN1 = engine.executeStrings("echo '1\n2' | head -n 1", ctx)
        assertEquals(listOf("1"), outN1)

        // -n 0 指定输出 0 行
        val outN0 = engine.executeStrings("echo '1\n2' | head -n 0", ctx)
        assertTrue(outN0.isEmpty())

        // -n 负数选项处理
        val out06 = engine.executeStrings("echo '1\n2' | head -n -1", ctx)
        assertTrue(out06.size <= 2)

        // -n 非数字选项容错处理
        val out07 = engine.executeStrings("echo '1' | head -n abc", ctx)
        assertEquals(listOf("1"), out07)

        // -n 缺失参数处理
        val out08 = engine.executeStrings("head -n", ctx)
        assertTrue(out08.isEmpty() || out08.any { it.contains("head") })

        // 请求行数大于输入总行数
        val outN100 = engine.executeStrings("echo '1\n2' | head -n 100", ctx)
        assertEquals(listOf("1", "2"), outN100)

        // 空输入流处理
        val outEmpty = engine.executeStrings("echo -n '' | head", ctx)
        assertTrue(outEmpty.isEmpty() || outEmpty == listOf(""))

        // 单行无换行文本处理
        val outSingle = engine.executeStrings("echo -n 'abc' | head", ctx)
        assertEquals(listOf("abc"), outSingle)

        // 中文多行文本截取
        val outChinese = engine.executeStrings("echo '你\n好' | head -n 1", ctx)
        assertEquals(listOf("你"), outChinese)

        // 含制表符 Tab 文本截取
        val outTab = engine.executeStrings("echo 'a\tb' | head -n 1", ctx)
        assertEquals(listOf("a\tb"), outTab)

        // 含美元符号文本截取
        val outDollar = engine.executeStrings("echo 'price\$1' | head -n 1", ctx)
        assertEquals(listOf("price\$1"), outDollar)

        // 含惊叹号文本截取
        val outExcl = engine.executeStrings("echo 'hi!' | head -n 1", ctx)
        assertEquals(listOf("hi!"), outExcl)

        // 含井号文本截取
        val outHash = engine.executeStrings("echo 'a#b' | head -n 1", ctx)
        assertEquals(listOf("a#b"), outHash)

        // 未知选项容错处理
        val out17 = engine.executeStrings("head -x", ctx)
        assertTrue(out17.isEmpty() || out17.any { it.contains("head") })

        // 多余位置参数容错处理
        val out18 = engine.executeStrings("head -n 1 a b", ctx)
        assertTrue(out18.size <= 1)
    }

    /**
     * 2. 管道组合测试
     */
    @Test
    fun testHeadPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 管道 find 输出接 head
        val out19 = engine.executeStrings("find -name '*.txt' | head", ctx)
        assertTrue(out19.size <= 10)

        // 管道 ls 输出接 head
        val out20 = engine.executeStrings("ls | head -n 5", ctx)
        assertTrue(out20.size <= 5)

        // 管道 grep 输出接 head
        val out21 = engine.executeStrings("ls | grep txt | head -n 3", ctx)
        assertTrue(out21.size <= 3)

        // head 输出接 wc -l 统计行数
        val out22 = engine.executeStrings("echo '1\n2' | head -n 1 | wc -l", ctx)
        assertEquals(listOf("1"), out22)

        // head 输出接 sort 排序
        val out23 = engine.executeStrings("echo 'b\na' | head -n 1 | sort", ctx)
        assertEquals(listOf("b"), out23)

        // head 输出接 xargs 参数传递
        val out24 = engine.executeStrings("echo 'a\nb' | head -n 1 | xargs echo", ctx)
        assertEquals(listOf("a"), out24)

        // 多级管道 find | grep | head | wc -l
        val out25 = engine.executeStrings("find | grep txt | head -n 2 | wc -l", ctx)
        assertEquals(1, out25.size)
    }
}
