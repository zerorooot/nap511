package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * echo 命令测试用例集合
 * 覆盖：基本输出、转义字符与特殊标记、管道与组合场景
 */
class EchoCommandTest {

    /**
     * 1. 基本功能测试
     */
    @Test
    fun testEchoBasicFunctions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看帮助文档
        val outHelp = engine.executeStrings("echo -h", ctx)
        assertTrue(outHelp.any { it.contains("echo") })

        // 空参数输出换行
        val outEmpty = engine.executeStrings("echo", ctx)
        assertEquals(listOf(""), outEmpty)

        // 单字符串输出
        val outSingle = engine.executeStrings("echo hello", ctx)
        assertEquals(listOf("hello"), outSingle)

        // 多字符串合并输出
        val outMulti = engine.executeStrings("echo hello world", ctx)
        assertEquals(listOf("hello world"), outMulti)

        // 多个空格分隔参数处理
        val outSpaces = engine.executeStrings("echo hello    world", ctx)
        assertEquals(listOf("hello world"), outSpaces)

        // 保留前导空格（双引号）
        val outLeading = engine.executeStrings("echo \"  hello\"", ctx)
        assertEquals(listOf("  hello"), outLeading)

        // 保留尾随空格（双引号）
        val outTrailing = engine.executeStrings("echo \"hello  \"", ctx)
        assertEquals(listOf("hello  "), outTrailing)

        // 数字字符串输出
        val outNumber = engine.executeStrings("echo 123", ctx)
        assertEquals(listOf("123"), outNumber)

        // 中文字符串输出
        val outChinese = engine.executeStrings("echo 你好", ctx)
        assertEquals(listOf("你好"), outChinese)

        // Emoji 表情字符串输出
        val outEmoji = engine.executeStrings("echo 😀", ctx)
        assertEquals(listOf("😀"), outEmoji)
    }

    /**
     * 2. 转义字符与特殊标记测试
     */
    @Test
    fun testEchoEscapingAndSpecialCharacters() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 单引号包裹字符串
        val out11 = engine.executeStrings("echo 'hello'", ctx)
        assertEquals(listOf("hello"), out11)

        // 双引号包裹字符串
        val out12 = engine.executeStrings("echo \"hello\"", ctx)
        assertEquals(listOf("hello"), out12)

        // 单引号内部嵌套转义单引号
        val out13 = engine.executeStrings("echo 'it'\\''s'", ctx)
        assertTrue(out13.isNotEmpty())

        // 双引号内部嵌套转义双引号
        val out14 = engine.executeStrings("echo \"say\\\"hi\\\"\"", ctx)
        assertTrue(out14.isNotEmpty())

        // 反斜杠字面量输出
        val out15 = engine.executeStrings("echo 'a\\b'", ctx)
        assertEquals(listOf("a\\b"), out15)

        // 双反斜杠转义输出
        val out16 = engine.executeStrings("echo 'a\\\\b'", ctx)
        assertEquals(listOf("a\\\\b"), out16)

        // 单引号中的美元符号
        val out17 = engine.executeStrings("echo '\$HOME'", ctx)
        assertEquals(listOf("\$HOME"), out17)

        // 双引号中的环境变量解析
        val out18 = engine.executeStrings("echo \"\$HOME\"", ctx)
        assertTrue(out18.isNotEmpty())

        // 惊叹号字面量输出
        val out19 = engine.executeStrings("echo 'hi!'", ctx)
        assertEquals(listOf("hi!"), out19)

        // 井号字面量输出
        val out20 = engine.executeStrings("echo 'a#b'", ctx)
        assertEquals(listOf("a#b"), out20)

        // 通配符星号字面量输出
        val out21 = engine.executeStrings("echo '*.txt'", ctx)
        assertEquals(listOf("*.txt"), out21)

        // 通配符问号字面量输出
        val out22 = engine.executeStrings("echo '?.txt'", ctx)
        assertEquals(listOf("?.txt"), out22)

        // 方括号字面量输出
        val out23 = engine.executeStrings("echo '[test]'", ctx)
        assertEquals(listOf("[test]"), out23)

        // 反引号字面量输出
        val out24 = engine.executeStrings("echo '`whoami`'", ctx)
        assertEquals(listOf("`whoami`"), out24)

        // 命令替换语法字面量输出
        val out25 = engine.executeStrings("echo '\$(whoami)'", ctx)
        assertEquals(listOf("\$(whoami)"), out25)

        // 含制表符 Tab 的字符串输出
        val out26 = engine.executeStrings("echo 'a\tb'", ctx)
        assertEquals(listOf("a\tb"), out26)

        // 含换行符 \n 的字符串输出
        val out27 = engine.executeStrings("echo 'a\nb'", ctx)
        assertEquals(listOf("a\nb"), out27)

        // -n 选项不换行输出
        val out28 = engine.executeStrings("echo -n hello", ctx)
        assertTrue(out28.isNotEmpty())

        // -e 选项开启转义序列解析
        val out29 = engine.executeStrings("echo -e 'a\\nb'", ctx)
        assertTrue(out29.isNotEmpty())

        // -- 选项分隔符后输出
        val out30 = engine.executeStrings("echo -- -h", ctx)
        assertEquals(listOf("-- -h"), out30)
    }

    /**
     * 3. 管道组合测试
     */
    @Test
    fun testEchoPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // echo 接 grep 过滤
        val out31 = engine.executeStrings("echo hello | grep hell", ctx)
        assertEquals(listOf("hello"), out31)

        // echo 接 grep -v 反向过滤
        val out32 = engine.executeStrings("echo hello | grep -v x", ctx)
        assertEquals(listOf("hello"), out32)

        // echo 接 head 截取头部
        val out33 = engine.executeStrings("echo hello | head", ctx)
        assertEquals(listOf("hello"), out33)

        // echo 接 tail 截取尾部
        val out34 = engine.executeStrings("echo hello | tail", ctx)
        assertEquals(listOf("hello"), out34)

        // echo 接 wc 统计
        val out35 = engine.executeStrings("echo hello | wc", ctx)
        assertTrue(out35.isNotEmpty())

        // echo 接 wc -l 统计行数
        val out36 = engine.executeStrings("echo hello | wc -l", ctx)
        assertEquals(listOf("1"), out36)

        // echo 单行多词接 sort
        val out37 = engine.executeStrings("echo b a c | sort", ctx)
        assertEquals(listOf("b a c"), out37)

        // echo 多行接 sort 排序
        val out38 = engine.executeStrings("echo 'b\na\nc' | sort", ctx)
        assertEquals(listOf("a", "b", "c"), out38)

        // echo 接 xargs 参数传递
        val out39 = engine.executeStrings("echo hello | xargs echo", ctx)
        assertEquals(listOf("hello"), out39)

        // echo 接 grep 接 wc 组合管道
        val out40 = engine.executeStrings("echo hello | grep hell | wc -l", ctx)
        assertEquals(listOf("1"), out40)

        // echo 接 grep 处理转义字符
        val out41 = engine.executeStrings("echo 'a.txt' | grep 'a\\.txt'", ctx)
        assertEquals(listOf("a.txt"), out41)

        // echo 接 grep 处理中文字符
        val out42 = engine.executeStrings("echo 测试 | grep 测", ctx)
        assertEquals(listOf("测试"), out42)

        // echo 接 grep 处理美元符号
        val out43 = engine.executeStrings("echo 'price\$1' | grep 'price\\\$1'", ctx)
        assertEquals(listOf("price\$1"), out43)

        // echo 接 grep 处理惊叹号
        val out44 = engine.executeStrings("echo 'hi!' | grep 'hi!'", ctx)
        assertEquals(listOf("hi!"), out44)

        // echo 接 grep 处理井号
        val out45 = engine.executeStrings("echo 'a#b' | grep 'a#b'", ctx)
        assertEquals(listOf("a#b"), out45)

        // echo 多行接 head -n 截取
        val out46 = engine.executeStrings("echo '1\n2\n3' | head -n 2", ctx)
        assertEquals(listOf("1", "2"), out46)

        // echo 多行接 tail -n 截取
        val out47 = engine.executeStrings("echo '1\n2\n3' | tail -n 2", ctx)
        assertEquals(listOf("2", "3"), out47)

        // echo 空输出接 wc -l
        val out48 = engine.executeStrings("echo | wc -l", ctx)
        assertEquals(listOf("1"), out48)

        // echo 接多级管道 echo | grep | head | wc -l
        val out49 = engine.executeStrings("echo hello | grep h | head | wc -l", ctx)
        assertEquals(listOf("1"), out49)

        // echo 接 xargs 拼接参数输出
        val out50 = engine.executeStrings("echo 'a b c' | xargs echo", ctx)
        assertEquals(listOf("a b c"), out50)
    }
}
