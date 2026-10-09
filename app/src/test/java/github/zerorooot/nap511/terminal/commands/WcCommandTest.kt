package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * wc 命令测试用例集合
 * 覆盖：基本功能与选项（-l, -w, -c）、特殊字符处理、管道组合场景
 */
class WcCommandTest {

    /**
     * 1. 基本功能与选项测试
     */
    @Test
    fun testWcBasicFunctionsAndOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看帮助文档
        val outHelp = engine.executeStrings("wc -h", ctx)
        assertTrue(outHelp.any { it.contains("wc") })

        // 默认统计行数、词数与字符数
        val outDefault = engine.executeStrings("echo 'a b\nc' | wc", ctx)
        assertNotNull(outDefault)

        // -l 选项仅统计行数
        val outL = engine.executeStrings("echo 'a\nb' | wc -l", ctx)
        assertEquals(listOf("2"), outL)

        // -w 选项仅统计词数
        val outW = engine.executeStrings("echo 'a b c' | wc -w", ctx)
        assertEquals(listOf("3"), outW)

        // -c 选项仅统计字节/字符数
        val outC = engine.executeStrings("echo 'abc' | wc -c", ctx)
        assertNotNull(outC)

        // -l -w 组合统计行数与词数
        val outLW = engine.executeStrings("echo 'a b' | wc -l -w", ctx)
        assertNotNull(outLW)

        // -l -w -c 显式组合统计
        val outLWC = engine.executeStrings("echo 'a b' | wc -l -w -c", ctx)
        assertNotNull(outLWC)

        // 空输入流统计
        val outEmpty = engine.executeStrings("echo '' | wc", ctx)
        assertNotNull(outEmpty)

        // 单行无换行文本行数统计
        val outSingle = engine.executeStrings("echo 'abc' | wc -l", ctx)
        assertNotNull(outSingle)

        // 包含多空格的文本词数统计
        val outSpaces = engine.executeStrings("echo 'a   b' | wc -w", ctx)
        assertEquals(listOf("2"), outSpaces)

        // 包含前导空格的文本词数统计
        val outLeading = engine.executeStrings("echo '  a b' | wc -w", ctx)
        assertEquals(listOf("2"), outLeading)

        // 包含尾随空格的文本词数统计
        val outTrailing = engine.executeStrings("echo 'a b  ' | wc -w", ctx)
        assertEquals(listOf("2"), outTrailing)

        // 制表符 Tab 分隔词数统计
        val outTab = engine.executeStrings("echo 'a\tb' | wc -w", ctx)
        assertEquals(listOf("2"), outTab)

        // 中文词数统计
        val outCnWords = engine.executeStrings("echo '你好 世界' | wc -w", ctx)
        assertNotNull(outCnWords)

        // 中文行数统计
        val outCnLines = engine.executeStrings("echo '你好' | wc -l", ctx)
        assertEquals(listOf("1"), outCnLines)

        // Emoji 表情字符数统计
        val outEmoji = engine.executeStrings("echo '😀' | wc -c", ctx)
        assertNotNull(outEmoji)

        // 未知选项容错处理
        val outUnknown = engine.executeStrings("wc -x", ctx)
        assertNotNull(outUnknown)

        // 重复选项 -l -l 处理
        val outRepeat = engine.executeStrings("echo 'a' | wc -l -l", ctx)
        assertEquals(listOf("1"), outRepeat)

        // 选项不同顺序组合
        val outOrder = engine.executeStrings("echo 'a b' | wc -w -l", ctx)
        assertNotNull(outOrder)

        // 无参数输入容错处理
        val outNoArgs = engine.executeStrings("wc", ctx)
        assertNotNull(outNoArgs)
    }

    /**
     * 2. 转义与特殊字符测试
     */
    @Test
    fun testWcEscapesAndSpecialCharacters() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 含美元符号文本字符统计
        val out21 = engine.executeStrings("echo 'price\$1' | wc -c", ctx)
        assertNotNull(out21)

        // 含惊叹号文本字符统计
        val out22 = engine.executeStrings("echo 'hi!' | wc -c", ctx)
        assertNotNull(out22)

        // 含井号文本字符统计
        val out23 = engine.executeStrings("echo 'a#b' | wc -c", ctx)
        assertNotNull(out23)

        // 含反斜杠文本字符统计
        val out24 = engine.executeStrings("echo 'a\\\\b' | wc -c", ctx)
        assertNotNull(out24)

        // 含单引号文本字符统计
        val out25 = engine.executeStrings("echo \"it's\" | wc -c", ctx)
        assertNotNull(out25)

        // 含双引号文本字符统计
        val out26 = engine.executeStrings("echo 'say\"hi\"' | wc -c", ctx)
        assertNotNull(out26)

        // 含星号文本字符统计
        val out27 = engine.executeStrings("echo '*.txt' | wc -c", ctx)
        assertNotNull(out27)

        // 含问号文本字符统计
        val out28 = engine.executeStrings("echo '?.txt' | wc -c", ctx)
        assertNotNull(out28)

        // 含方括号文本字符统计
        val out29 = engine.executeStrings("echo '[test]' | wc -c", ctx)
        assertNotNull(out29)

        // 含反引号文本字符统计
        val out30 = engine.executeStrings("echo '`whoami`' | wc -c", ctx)
        assertNotNull(out30)
    }

    /**
     * 3. 管道组合测试
     */
    @Test
    fun testWcPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // ls 接 wc -l 统计文件数量
        val out31 = engine.executeStrings("ls | wc -l", ctx)
        assertNotNull(out31)

        // find 接 wc -l 统计搜索结果
        val out32 = engine.executeStrings("find -name '*.txt' | wc -l", ctx)
        assertNotNull(out32)

        // grep 接 wc -l 统计过滤行数
        val out33 = engine.executeStrings("ls | grep txt | wc -l", ctx)
        assertNotNull(out33)

        // history 接 wc -l 统计历史记录条数
        val out34 = engine.executeStrings("history | wc -l", ctx)
        assertNotNull(out34)

        // sort 接 wc -l 统计排序结果
        val out35 = engine.executeStrings("echo 'b\na' | sort | wc -l", ctx)
        assertEquals(listOf("2"), out35)

        // head 接 wc -l 统计截取行数
        val out36 = engine.executeStrings("echo '1\n2\n3' | head -n 2 | wc -l", ctx)
        assertEquals(listOf("2"), out36)

        // tail 接 wc -l 统计截取行数
        val out37 = engine.executeStrings("echo '1\n2\n3' | tail -n 2 | wc -l", ctx)
        assertEquals(listOf("2"), out37)

        // echo 接 wc 综合统计
        val out38 = engine.executeStrings("echo hello | wc", ctx)
        assertNotNull(out38)

        // xargs 接 wc 统计
        val out39 = engine.executeStrings("echo 'a b c' | xargs wc -w", ctx)
        assertNotNull(out39)

        // 多级管道 find | grep | head | wc -l
        val out40 = engine.executeStrings("find | grep txt | head | wc -l", ctx)
        assertNotNull(out40)

        // wc -l 输出接 xargs 参数传递
        val out41 = engine.executeStrings("echo 'a' | wc -l | xargs echo", ctx)
        assertEquals(listOf("1"), out41)

        // wc -l 输出接 grep 过滤
        val out42 = engine.executeStrings("echo 'a' | wc -l | grep 1", ctx)
        assertEquals(listOf("1"), out42)

        // wc -l 输出接 sort 排序
        val out43 = engine.executeStrings("echo 'a' | wc -l | sort", ctx)
        assertEquals(listOf("1"), out43)

        // wc 输出接 head 截取
        val out44 = engine.executeStrings("echo 'a' | wc | head", ctx)
        assertNotNull(out44)

        // wc 输出接 tail 截取
        val out45 = engine.executeStrings("echo 'a' | wc | tail", ctx)
        assertNotNull(out45)

        // wc 处理转义字符管道
        val out46 = engine.executeStrings("echo 'a\\\\.txt' | wc -c", ctx)
        assertNotNull(out46)

        // wc 处理中文管道
        val out47 = engine.executeStrings("echo '测试' | wc -l", ctx)
        assertEquals(listOf("1"), out47)

        // wc 处理 Emoji 管道
        val out48 = engine.executeStrings("echo '😀' | wc -l", ctx)
        assertEquals(listOf("1"), out48)

        // wc 空接 wc 多层统计
        val out49 = engine.executeStrings("echo '' | wc -l | wc -l", ctx)
        assertNotNull(out49)

        // wc 多选项组合接 wc
        val out50 = engine.executeStrings("echo 'a b' | wc -l -w | wc -w", ctx)
        assertNotNull(out50)
    }

    /**
     * 3. POSIX 多文件操作数与 total 汇总测试
     */
    @Test
    fun testWcMultiFilesTotal() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["101"] = "hello world\nline two"
            mockDownloadStreams["102"] = "third line"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = repo)

        val f1 = createMockFile("f1.txt", "101", icoString = "txt")
        val f2 = createMockFile("f2.txt", "102", icoString = "txt")
        ctx.putMockFiles("0", listOf(f1, f2))

        // 单文件：包含文件名，但不含 total
        val outSingle = engine.executeStrings("wc -l f1.txt", ctx)
        assertTrue(outSingle.any { it.contains("f1.txt") })
        assertFalse(outSingle.any { it.contains("total") })

        // 多文件：逐个输出并在末尾输出 total
        val outMulti = engine.executeStrings("wc -l f1.txt f2.txt", ctx)
        assertTrue(outMulti.any { it.contains("f1.txt") })
        assertTrue(outMulti.any { it.contains("f2.txt") })
        assertTrue(outMulti.any { it.contains("total") })
    }
}
