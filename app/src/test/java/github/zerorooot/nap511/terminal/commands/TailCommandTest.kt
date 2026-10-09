package github.zerorooot.nap511.terminal.commands

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * tail 命令测试用例集合
 * 覆盖：基本功能与 -n 选项、管道组合、特殊字符与边界异常场景
 */
class TailCommandTest {

    /**
     * 1. 基本功能测试
     */
    @Test
    fun testTailBasicFunctionsAndOptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 查看帮助文档
        val outHelp = engine.executeStrings("tail -h", ctx)
        assertTrue(outHelp.any { it.contains("tail") })

        // 默认输出后 10 行
        val input20 = (1..20).joinToString("\n")
        val outDefault = engine.executeStrings("echo '$input20' | tail", ctx)
        assertEquals(10, outDefault.size)
        assertEquals("11", outDefault.first())
        assertEquals("20", outDefault.last())

        // -n 3 指定截取后 3 行
        val outN3 = engine.executeStrings("echo '1\n2\n3\n4' | tail -n 3", ctx)
        assertEquals(listOf("2", "3", "4"), outN3)

        // -n 1 指定截取末尾 1 行
        val outN1 = engine.executeStrings("echo '1\n2' | tail -n 1", ctx)
        assertEquals(listOf("2"), outN1)

        // -n 0 指定截取 0 行
        val outN0 = engine.executeStrings("echo '1\n2' | tail -n 0", ctx)
        assertTrue(outN0.isEmpty())

        // -n 负数选项处理
        val out06 = engine.executeStrings("echo '1\n2' | tail -n -1", ctx)
        assertNotNull(out06)

        // -n 非数字选项容错处理
        val out07 = engine.executeStrings("echo '1' | tail -n abc", ctx)
        assertNotNull(out07)

        // -n 缺失参数处理
        val out08 = engine.executeStrings("tail -n", ctx)
        assertNotNull(out08)

        // 请求行数大于输入总行数
        val out09 = engine.executeStrings("echo '1\n2' | tail -n 100", ctx)
        assertEquals(listOf("1", "2"), out09)

        // 空输入流处理
        val out10 = engine.executeStrings("echo '' | tail", ctx)
        assertNotNull(out10)

        // 单行无换行文本处理
        val out11 = engine.executeStrings("echo 'abc' | tail", ctx)
        assertEquals(listOf("abc"), out11)

        // 中文多行文本截取
        val out12 = engine.executeStrings("echo '你\n好' | tail -n 1", ctx)
        assertEquals(listOf("好"), out12)

        // 含制表符 Tab 文本截取
        val out13 = engine.executeStrings("echo 'a\tb' | tail -n 1", ctx)
        assertEquals(listOf("a\tb"), out13)

        // 含美元符号文本截取
        val out14 = engine.executeStrings("echo 'price\$1' | tail -n 1", ctx)
        assertEquals(listOf("price\$1"), out14)

        // 含惊叹号文本截取
        val out15 = engine.executeStrings("echo 'hi!' | tail -n 1", ctx)
        assertEquals(listOf("hi!"), out15)

        // 含井号文本截取
        val out16 = engine.executeStrings("echo 'a#b' | tail -n 1", ctx)
        assertEquals(listOf("a#b"), out16)

        // 未知选项处理（无输入流时正常结束）
        val out17 = engine.executeStrings("tail -x", ctx)
        assertNotNull(out17)

        // 多文件参数但文件不存在时的标准错误输出
        val out18 = engine.executeStrings("tail -n 1 a b", ctx)
        assertTrue(out18.any { it.contains("tail: a: No such file or directory") })
        assertTrue(out18.any { it.contains("tail: b: No such file or directory") })
    }

    /**
     * 2. 管道组合测试
     */
    @Test
    fun testTailPipelines() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 管道 find 输出接 tail
        val out19 = engine.executeStrings("find -name '*.txt' | tail", ctx)
        assertNotNull(out19)

        // 管道 ls 输出接 tail
        val out20 = engine.executeStrings("ls | tail -n 5", ctx)
        assertNotNull(out20)

        // 管道 grep 输出接 tail
        val out21 = engine.executeStrings("ls | grep txt | tail -n 3", ctx)
        assertNotNull(out21)

        // tail 输出接 wc -l 统计行数
        val out22 = engine.executeStrings("echo '1\n2' | tail -n 1 | wc -l", ctx)
        assertEquals(listOf("1"), out22)

        // tail 输出接 sort 排序
        val out23 = engine.executeStrings("echo 'b\na' | tail -n 1 | sort", ctx)
        assertEquals(listOf("a"), out23)

        // tail 输出接 xargs 参数传递
        val out24 = engine.executeStrings("echo 'a\nb' | tail -n 1 | xargs echo", ctx)
        assertEquals(listOf("b"), out24)

        // 多级管道 find | grep | tail | wc -l
        val out25 = engine.executeStrings("find | grep txt | tail -n 2 | wc -l", ctx)
        assertNotNull(out25)
    }

    /**
     * 3. 转义与特殊字符测试
     */
    @Test
    fun testTailEscapesAndSpecialCharacters() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 含空格文本处理
        val out31 = engine.executeStrings("echo 'my file' | tail -n 1", ctx)
        assertEquals(listOf("my file"), out31)

        // 含单引号文本处理
        val out32 = engine.executeStrings("echo \"it's\" | tail -n 1", ctx)
        assertEquals(listOf("it's"), out32)

        // 含双引号文本处理
        val out33 = engine.executeStrings("echo 'say\"hi\"' | tail -n 1", ctx)
        assertEquals(listOf("say\"hi\""), out33)

        // 含反斜杠文本处理
        val out34 = engine.executeStrings("echo 'a\\\\b' | tail -n 1", ctx)
        assertNotNull(out34)

        // 含星号文本处理
        val out35 = engine.executeStrings("echo '*.txt' | tail -n 1", ctx)
        assertEquals(listOf("*.txt"), out35)

        // 含问号文本处理
        val out36 = engine.executeStrings("echo '?.txt' | tail -n 1", ctx)
        assertEquals(listOf("?.txt"), out36)

        // 含方括号文本处理
        val out37 = engine.executeStrings("echo '[test]' | tail -n 1", ctx)
        assertEquals(listOf("[test]"), out37)

        // 含反引号文本处理
        val out38 = engine.executeStrings("echo '`whoami`' | tail -n 1", ctx)
        assertNotNull(out38)

        // 中文单行文本处理
        val out39 = engine.executeStrings("echo '测试' | tail -n 1", ctx)
        assertEquals(listOf("测试"), out39)

        // Emoji 表情文本处理
        val out40 = engine.executeStrings("echo '😀' | tail -n 1", ctx)
        assertEquals(listOf("😀"), out40)
    }

    /**
     * 4. 边界与异常测试
     */
    @Test
    fun testTailBoundariesAndExceptions() = runBlocking {
        val engine = createTestEngine()
        val ctx = createTestContext()

        // 超大 N 值截取
        val out41 = engine.executeStrings("echo '1\n2' | tail -n 999999", ctx)
        assertEquals(listOf("1", "2"), out41)

        // 小数 N 值容错处理
        val out42 = engine.executeStrings("echo '1\n2' | tail -n 1.5", ctx)
        assertNotNull(out42)

        // 前导 0 参数处理 (如 -n 01)
        val out43 = engine.executeStrings("echo '1\n2' | tail -n 01", ctx)
        assertEquals(listOf("2"), out43)

        // 带加号 N 值参数处理 (如 -n +1)
        val out44 = engine.executeStrings("echo '1\n2' | tail -n +1", ctx)
        assertNotNull(out44)

        // 空字符串 N 值参数处理
        val out45 = engine.executeStrings("tail -n ''", ctx)
        assertNotNull(out45)

        // 重复 -n 选项参数覆盖
        val out46 = engine.executeStrings("echo '1\n2\n3' | tail -n 1 -n 2", ctx)
        assertNotNull(out46)

        // -n 选项后接文件名参数
        val out47 = engine.executeStrings("tail -n 1 test.txt", ctx)
        assertNotNull(out47)

        // 路径含空格文件截取
        val out48 = engine.executeStrings("tail -n 1 \"my file.txt\"", ctx)
        assertNotNull(out48)

        // 路径含美元符号文件截取
        val out49 = engine.executeStrings("tail -n 1 'price\$1.txt'", ctx)
        assertNotNull(out49)

        // 路径含中文文件截取
        val out50 = engine.executeStrings("tail -n 1 测试.txt", ctx)
        assertNotNull(out50)
    }

    /**
     * 5. POSIX 多文件操作数与标头测试
     */
    @Test
    fun testTailMultiFilesHeaders() = runBlocking {
        val repo = TestMockFileRepository().apply {
            mockDownloadStreams["tail_101"] = "a\nb\nc"
            mockDownloadStreams["tail_102"] = "x\ny\nz"
        }
        val engine = createTestEngine()
        val ctx = createTestContext(fileRepository = repo)

        val f1 = createMockFile("f1.txt", "tail_101", icoString = "txt")
        val f2 = createMockFile("f2.txt", "tail_102", icoString = "txt")
        ctx.putMockFiles("0", listOf(f1, f2))

        // 单文件：无 ==> 标头
        val outSingle = engine.executeStrings("tail -n 2 f1.txt", ctx)
        assertEquals(listOf("b", "c"), outSingle)

        // 多文件：必须包含 ==> f1.txt <== 与 ==> f2.txt <== 标头，且块间有空行隔离
        val outMulti = engine.executeStrings("tail -n 2 f1.txt f2.txt", ctx)
        assertEquals(
            listOf(
                "==> f1.txt <==",
                "b",
                "c",
                "",
                "==> f2.txt <==",
                "y",
                "z"
            ),
            outMulti
        )
    }
}
