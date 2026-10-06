package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * grep 命令测试用例集合
 * 覆盖：基本过滤匹配、正则表达式与特殊字符转义、管道组合场景
 */
class GrepCommandTest {

    /**
     * 1. 基本过滤与选项测试
     */
    @Test
    fun testGrepBasicFunctions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看帮助文档
        val outHelp = engine.executeStrings("grep -h", ctx)
        assertTrue(outHelp.any { it.contains("grep") })

        // 基本字符串匹配
        val out02 = engine.executeStrings("echo hello | grep hell", ctx)
        assertEquals(listOf("hello"), out02)

        // 不匹配模式返回空
        val out03 = engine.executeStrings("echo hello | grep xyz", ctx)
        assertTrue(out03.isEmpty())

        // 默认区分大小写
        val out04 = engine.executeStrings("echo Hello | grep hello", ctx)
        assertTrue(out04.isEmpty())

        // -i 选项忽略大小写
        val out05 = engine.executeStrings("echo Hello | grep -i hello", ctx)
        assertEquals(listOf("Hello"), out05)

        // -v 选项反向选择（无匹配行时保留全部）
        val out06 = engine.executeStrings("echo hello | grep -v xyz", ctx)
        assertEquals(listOf("hello"), out06)

        // -v 选项排除匹配行
        val out07 = engine.executeStrings("echo hello | grep -v hell", ctx)
        assertTrue(out07.isEmpty())

        // -c 选项统计匹配行数
        val out08 = engine.executeStrings("echo 'a\nb\na' | grep -c a", ctx)
        assertEquals(listOf("2"), out08)

        // 多行匹配输出
        val out09 = engine.executeStrings("echo 'a\nb\na' | grep a", ctx)
        assertEquals(listOf("a", "a"), out09)

        // -c 选项无匹配时输出 0
        val out10 = engine.executeStrings("echo hello | grep -c xyz", ctx)
        assertEquals(listOf("0"), out10)
    }

    /**
     * 2. 正则表达式与特殊字符转义测试
     */
    @Test
    fun testGrepRegexAndEscaping() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 点号通配符匹配
        val out11 = engine.executeStrings("echo a.txt | grep 'a.txt'", ctx)
        assertEquals(listOf("a.txt"), out11)

        // 转义点号匹配字面量
        val out12 = engine.executeStrings("echo a.txt | grep 'a\\.txt'", ctx)
        assertEquals(listOf("a.txt"), out12)

        // 转义点号不匹配非点号字符
        val out13 = engine.executeStrings("echo axtxt | grep 'a\\.txt'", ctx)
        assertTrue(out13.isEmpty())

        // ^ 匹配行首
        val out14 = engine.executeStrings("echo hello | grep '^he'", ctx)
        assertEquals(listOf("hello"), out14)

        // $ 匹配行尾
        val out15 = engine.executeStrings("echo hello | grep 'lo\$'", ctx)
        assertEquals(listOf("hello"), out15)

        // .* 贪婪通配
        val out16 = engine.executeStrings("echo abc | grep 'a.*c'", ctx)
        assertEquals(listOf("abc"), out16)

        // [abc] 字符集匹配
        val out17 = engine.executeStrings("echo b | grep '[abc]'", ctx)
        assertEquals(listOf("b"), out17)

        // [^abc] 字符集取反
        val out18 = engine.executeStrings("echo d | grep '[^abc]'", ctx)
        assertEquals(listOf("d"), out18)

        // \+ 重复匹配
        val out19 = engine.executeStrings("echo aa | grep 'a\\+'", ctx)
        assertEquals(listOf("aa"), out19)

        // \? 可选匹配
        val out20 = engine.executeStrings("echo a | grep 'a\\?'", ctx)
        assertEquals(listOf("a"), out20)

        // 花括号限定重复次数
        val out21 = engine.executeStrings("echo aa | grep 'a\\{2\\}'", ctx)
        assertEquals(listOf("aa"), out21)

        // 竖线逻辑或
        val out22 = engine.executeStrings("echo cat | grep 'cat\\|dog'", ctx)
        assertEquals(listOf("cat"), out22)

        // 圆括号分组
        val out23 = engine.executeStrings("echo abc | grep '\\(ab\\)c'", ctx)
        assertEquals(listOf("abc"), out23)

        // 反斜杠字面量转义
        val out24 = engine.executeStrings("echo 'a\\b' | grep 'a\\\\b'", ctx)
        assertEquals(listOf("a\\b"), out24)

        // 美元符号字面量转义
        val out25 = engine.executeStrings("echo 'price\$1' | grep 'price\\\$1'", ctx)
        assertEquals(listOf("price\$1"), out25)

        // 惊叹号字面量
        val out26 = engine.executeStrings("echo 'hi!' | grep 'hi!'", ctx)
        assertEquals(listOf("hi!"), out26)

        // 井号字面量
        val out27 = engine.executeStrings("echo 'a#b' | grep 'a#b'", ctx)
        assertEquals(listOf("a#b"), out27)

        // 星号字面量转义
        val out28 = engine.executeStrings("echo 'a*b' | grep 'a\\*b'", ctx)
        assertEquals(listOf("a*b"), out28)

        // 问号字面量转义
        val out29 = engine.executeStrings("echo 'a?b' | grep 'a\\?b'", ctx)
        assertEquals(listOf("a?b"), out29)

        // 左方括号字面量转义
        val out30 = engine.executeStrings("echo 'a[b' | grep 'a\\[b'", ctx)
        assertEquals(listOf("a[b"), out30)

        // 中文字符正则匹配
        val out31 = engine.executeStrings("echo 测试 | grep 测", ctx)
        assertEquals(listOf("测试"), out31)

        // Emoji 表情正则匹配
        val out32 = engine.executeStrings("echo 😀 | grep 😀", ctx)
        assertEquals(listOf("😀"), out32)

        // 包含空格字符串匹配
        val out33 = engine.executeStrings("echo 'my file' | grep 'my file'", ctx)
        assertEquals(listOf("my file"), out33)

        // 制表符 Tab 匹配
        val out34 = engine.executeStrings("echo 'a\tb' | grep 'a\tb'", ctx)
        assertEquals(listOf("a\tb"), out34)

        // 空匹配模式匹配全行
        val out35 = engine.executeStrings("echo hello | grep ''", ctx)
        assertEquals(listOf("hello"), out35)

        // 缺失匹配模式参数
        val out36 = engine.executeStrings("grep", ctx)
        assertTrue(out36.isEmpty() || out36.any { it.contains("grep") })

        // 未知选项参数处理
        val out37 = engine.executeStrings("grep -x", ctx)
        assertTrue(out37.isEmpty() || out37.any { it.contains("grep") })

        // 复合选项 -i -v 结合
        val out38 = engine.executeStrings("echo Hello | grep -i -v hell", ctx)
        assertTrue(out38.isEmpty())

        // 复合选项 -i -c 结合
        val out39 = engine.executeStrings("echo 'A\na' | grep -i -c a", ctx)
        assertEquals(listOf("2"), out39)

        // 复合选项 -v -c 结合
        val out40 = engine.executeStrings("echo 'a\nb' | grep -v -c a", ctx)
        assertEquals(listOf("1"), out40)
    }

    /**
     * 3. 管道组合测试
     */
    @Test
    fun testGrepPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // find 管道传递接 grep
        val out41 = engine.executeStrings("find -name '*.txt' | grep test", ctx)
        assertTrue(out41.isEmpty() || out41.any { it.contains("test") })

        // ls 管道传递接 grep
        val out42 = engine.executeStrings("ls | grep txt", ctx)
        assertTrue(out42.isEmpty() || out42.any { it.contains("txt") })

        // grep 接 head 限制行数
        val out43 = engine.executeStrings("ls | grep txt | head", ctx)
        assertTrue(out43.size <= 10)

        // grep 接 tail 截取尾部
        val out44 = engine.executeStrings("ls | grep txt | tail", ctx)
        assertTrue(out44.size <= 10)

        // grep 接 wc -l 统计结果行数
        val out45 = engine.executeStrings("ls | grep txt | wc -l", ctx)
        assertEquals(1, out45.size)

        // grep 接 sort 结果排序
        val out46 = engine.executeStrings("echo 'b\na' | grep . | sort", ctx)
        assertEquals(listOf("a", "b"), out46)

        // grep 接 xargs 参数传递
        val out47 = engine.executeStrings("echo hello | grep hell | xargs echo", ctx)
        assertEquals(listOf("hello"), out47)

        // 多级管道 find | grep | head | wc -l
        val out48 = engine.executeStrings("find -name '*.txt' | grep test | head | wc -l", ctx)
        assertEquals(1, out48.size)

        // grep 处理转义字符管道
        val out49 = engine.executeStrings("echo 'a.txt' | grep 'a\\.txt' | wc -l", ctx)
        assertEquals(listOf("1"), out49)

        // grep 处理中文管道
        val out50 = engine.executeStrings("echo 测试 | grep 测 | wc -l", ctx)
        assertEquals(listOf("1"), out50)
    }
}
